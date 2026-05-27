package com.thgiang.image.core.data.backgroundremove

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import com.abizer_r.quickedit.backgroundremove.ModNetBackgroundRemoverRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.math.roundToInt
import kotlin.math.sqrt

class AdaptiveHybridBackgroundRemoverRepository(
    private val context: Context,
    private val modNetRepository: ModNetBackgroundRemoverRepository,
    private val mlKitRepository: MlKitBackgroundRemoverRepository,
    private val fusionResolution: Int = 512,
    private val erodeRadius: Int = 3,
    private val mlkitThreshold: Float = 0.86f,
    private val modnetWeakThreshold: Float = 0.48f,
    private val enableHybridFusion: Boolean = true,
    private val maxDecodeSize: Int = 2048,
    private val postProcessMode: MaskPostProcessor.Mode = MaskPostProcessor.Mode.SAFE,
    private val enableAggressivePostProcessForHybrid: Boolean = false,
    private val enablePersonPriorPrune: Boolean = false,
) : BackgroundRemoverRepository {

    private companion object {
        private const val TAG = "AdaptiveHybridBg"
        private const val MAX_FUSION_SIDE = 768
        private const val NO_SUBJECT_MLKIT_COVERAGE = 0.005f
        private const val LOW_RES_ASSIST_MIN_PIXELS = 900_000
        private const val LOW_RES_ASSIST_MAX_SIDE = 1280
        private const val INFERENCE_TIMEOUT_MS = 30_000L

        init {
            runCatching {
                System.loadLibrary("bg_refiner")
            }.onFailure {
                Log.e(TAG, "Failed to load bg_refiner", it)
            }
        }
    }

    @Volatile
    private var lastUsedMlKit: Boolean = false

    // For analytics/debug only. Do not use this for logic decisions.
    // Use MaskDecision.route for thread-safe routing.
    @Volatile
    private var lastRoute: String = "none"

    private data class MaskDecision(
        val mask: PortraitConfidenceMask,
        val route: String,
        val usedMlKit: Boolean,
        val mlKitGuide: PortraitConfidenceMask? = null,
        val quality: MaskQualityAnalyzer.MaskQuality? = null
    )

    private external fun nativeFuseMasks(
        modnetMask: FloatArray,
        mlkitMask: FloatArray,
        width: Int,
        height: Int,
        erodeRadius: Int,
        mlkitThreshold: Float,
        modnetWeakThreshold: Float,
    ): FloatArray?

    override suspend fun removeBackground(imageUri: Uri): Result<BackgroundRemovalOutput> = runCatching {
        val totalStartMs = SystemClock.elapsedRealtime()
        val original = BitmapDecodeUtils.loadBitmapFromUri(context, imageUri, maxDecodeSize)
            ?: error("Cannot decode image: $imageUri")

        val foreground = try {
            getForegroundBitmap(original).getOrThrow()
        } finally {
            if (!original.isRecycled) original.recycle()
        }

        Log.d(
            TAG,
            "removeBackground: finished uri=$imageUri, route=${consumeLastRoute()}, totalMs=${elapsedMs(totalStartMs)}"
        )

        BackgroundRemovalOutput(
            foregroundToDisplay = foreground.copy(Bitmap.Config.ARGB_8888, true).also {
                it.setHasAlpha(true)
            },
            foregroundToSave = foreground,
        )
    }

    override suspend fun getForegroundBitmap(bitmap: Bitmap): Result<Bitmap> = runCatching {
        val source = bitmap.ensureSoftwareArgb8888()
        val maskSource = source.upscaleSmallBitmapForMaskIfNeeded()

        try {
            val totalStartMs = SystemClock.elapsedRealtime()
            val decision = getBestMaskInternal(maskSource)
            val rawMask = MaskMath.ensureMaskSize(
                decision.mask,
                source.width,
                source.height
            )
            val mlKitGuide = decision.mlKitGuide?.let {
                MaskMath.ensureMaskSize(it, source.width, source.height)
            }

            val selectedMode = selectPostProcessMode(decision.route)

            val postStartMs = SystemClock.elapsedRealtime()
            val processedMask = postProcessWithFallback(
                source = source,
                rawMask = rawMask,
                mlKitGuide = mlKitGuide,
                selectedMode = selectedMode,
                route = decision.route
            )
            val postProcessMs = elapsedMs(postStartMs)

            Log.d(
                TAG,
                "getForegroundBitmap: route=${decision.route}, postProcessMode=$selectedMode, " +
                    "postMs=$postProcessMs, totalMs=${elapsedMs(totalStartMs)}, " +
                    "coverageBefore=${MaskMath.foregroundCoverage(rawMask)}, " +
                    "coverageAfter=${MaskMath.foregroundCoverage(processedMask)}"
            )

            MaskMath.applyMaskToBitmap(source, processedMask)
        } finally {
            if (maskSource !== source && !maskSource.isRecycled) {
                maskSource.recycle()
            }
            if (source !== bitmap && !source.isRecycled) {
                source.recycle()
            }
        }
    }

    override suspend fun getPortraitConfidenceMask(bitmap: Bitmap): Result<PortraitConfidenceMask> = runCatching {
        val source = bitmap.ensureSoftwareArgb8888()
        val maskSource = source.upscaleSmallBitmapForMaskIfNeeded()

        try {
            val totalStartMs = SystemClock.elapsedRealtime()
            val decision = getBestMaskInternal(maskSource)
            val rawMask = MaskMath.ensureMaskSize(
                decision.mask,
                source.width,
                source.height
            )
            val mlKitGuide = decision.mlKitGuide?.let {
                MaskMath.ensureMaskSize(it, source.width, source.height)
            }

            val selectedMode = selectPostProcessMode(decision.route)

            val postStartMs = SystemClock.elapsedRealtime()
            val processedMask = postProcessWithFallback(
                source = source,
                rawMask = rawMask,
                mlKitGuide = mlKitGuide,
                selectedMode = selectedMode,
                route = decision.route
            )
            Log.d(
                TAG,
                "getPortraitConfidenceMask: route=${decision.route}, postProcessMode=$selectedMode, " +
                    "postMs=${elapsedMs(postStartMs)}, totalMs=${elapsedMs(totalStartMs)}, " +
                    "coverageBefore=${MaskMath.foregroundCoverage(rawMask)}, " +
                    "coverageAfter=${MaskMath.foregroundCoverage(processedMask)}"
            )
            processedMask
        } finally {
            if (maskSource !== source && !maskSource.isRecycled) {
                maskSource.recycle()
            }
            if (source !== bitmap && !source.isRecycled) {
                source.recycle()
            }
        }
    }

    override fun consumeSelfieFallbackWarning(): Boolean {
        val used = lastUsedMlKit || mlKitRepository.consumeSelfieFallbackWarning()
        lastUsedMlKit = false
        return used
    }

    fun consumeLastRoute(): String {
        val route = lastRoute
        lastRoute = "none"
        return route
    }

    override fun close() {
        runCatching { modNetRepository.close() }
        runCatching { mlKitRepository.close() }
    }

    private fun selectPostProcessMode(route: String): MaskPostProcessor.Mode {
        return if (
            enableAggressivePostProcessForHybrid &&
            (route == "hybrid_fusion" || route == "mlkit_only_quality_fail")
        ) {
            MaskPostProcessor.Mode.AGGRESSIVE
        } else {
            postProcessMode
        }
    }

    private suspend fun getBestMaskInternal(bitmap: Bitmap): MaskDecision {
        lastUsedMlKit = false
        lastRoute = "modnet_pending"

        val modNetStartMs = SystemClock.elapsedRealtime()
        val modNetMaskResult = runCatching {
            withTimeout(INFERENCE_TIMEOUT_MS) {
                withContext(Dispatchers.Default) {
                    modNetRepository.getPortraitConfidenceMask(bitmap)
                }
            }
        }.getOrElse { throwable ->
            Result.failure(throwable)
        }
        Log.d(TAG, "ModNet mask step: success=${modNetMaskResult.isSuccess}, ms=${elapsedMs(modNetStartMs)}")

        val decision = if (modNetMaskResult.isFailure) {
            Log.w(TAG, "ModNet failed, falling back to ML Kit", modNetMaskResult.exceptionOrNull())
            fallbackToMlKit(bitmap, route = "mlkit_fallback_modnet_failed")
        } else {
            val modNetMask = MaskMath.ensureMaskSize(
                modNetMaskResult.getOrThrow(),
                bitmap.width,
                bitmap.height
            )

            val quality = MaskQualityAnalyzer.analyze(modNetMask)
            Log.d(
                TAG,
                "Quality recommendation=${quality.recommendation}, center=${quality.centerDensity}, " +
                    "holes=${quality.centerHoleRatio}, coverage=${quality.coverageRatio}, " +
                    "edge=${quality.edgeNoiseScore}, lowerWeak=${quality.lowerWeakGapRatio}",
            )

            when (quality.recommendation) {
                MaskQualityAnalyzer.Recommendation.USE_MODNET_ONLY -> {
                    MaskDecision(modNetMask, route = "modnet_only", usedMlKit = false, quality = quality)
                }

                MaskQualityAnalyzer.Recommendation.USE_MLKIT_ONLY -> {
                    fallbackToMlKit(bitmap, route = "mlkit_only_quality_fail")
                }

                MaskQualityAnalyzer.Recommendation.FUSE_HYBRID -> {
                    if (!enableHybridFusion) {
                        MaskDecision(modNetMask, route = "modnet_only_fusion_disabled", usedMlKit = false, quality = quality)
                    } else {
                        performFusion(bitmap, modNetMask, route = "hybrid_fusion")
                    }
                }
            }
        }

        lastUsedMlKit = decision.usedMlKit
        lastRoute = decision.route
        return decision
    }

    private suspend fun fallbackToMlKit(
        bitmap: Bitmap,
        route: String,
    ): MaskDecision = withContext(Dispatchers.Default) {
        val startMs = SystemClock.elapsedRealtime()
        val mlMask = withTimeout(INFERENCE_TIMEOUT_MS) {
            mlKitRepository.getPortraitConfidenceMask(bitmap).getOrThrow()
        }
        val normalized = MaskMath.ensureMaskSize(mlMask, bitmap.width, bitmap.height)
        val mlCoverage = MaskMath.foregroundCoverage(normalized, threshold = 0.5f)
        Log.d(TAG, "ML Kit fallback step: route=$route, coverage=$mlCoverage, ms=${elapsedMs(startMs)}")

        if (mlCoverage < NO_SUBJECT_MLKIT_COVERAGE) {
            Log.w(TAG, "No subject detected after ML Kit fallback. Returning transparent mask.")
            MaskDecision(
                mask = PortraitConfidenceMask(bitmap.width, bitmap.height, FloatArray(bitmap.width * bitmap.height)),
                route = "no_subject_after_mlkit",
                usedMlKit = true,
                mlKitGuide = normalized
            )
        } else {
            MaskDecision(
                mask = normalized,
                route = route,
                usedMlKit = true,
                mlKitGuide = normalized
            )
        }
    }

    private suspend fun performFusion(
        bitmap: Bitmap,
        modNetMask: PortraitConfidenceMask,
        route: String,
    ): MaskDecision = withContext(Dispatchers.Default) {
        val totalStartMs = SystemClock.elapsedRealtime()
        val mlKitStartMs = SystemClock.elapsedRealtime()
        val mlKitMask = withTimeout(INFERENCE_TIMEOUT_MS) {
            mlKitRepository.getPortraitConfidenceMask(bitmap).getOrThrow()
        }
        val normalizedMlKit = MaskMath.ensureMaskSize(mlKitMask, bitmap.width, bitmap.height)

        val mlCoverage = MaskMath.foregroundCoverage(normalizedMlKit, threshold = 0.5f)
        val modCoverage = MaskMath.foregroundCoverage(modNetMask, threshold = 0.5f)
        Log.d(TAG, "ML Kit fusion step: coverage=$mlCoverage, ms=${elapsedMs(mlKitStartMs)}")
        if (mlCoverage < NO_SUBJECT_MLKIT_COVERAGE) {
            Log.d(TAG, "ML Kit mask nearly empty during fusion. Keeping ModNet.")
            return@withContext MaskDecision(
                mask = modNetMask,
                route = "modnet_only_mlkit_empty",
                usedMlKit = true,
                mlKitGuide = normalizedMlKit
            )
        }
        if (!isMlKitGuideReliable(modNetMask, normalizedMlKit)) {
            Log.w(
                TAG,
                "ML Kit guide unreliable for fusion. Keeping ModNet. modCoverage=$modCoverage, mlCoverage=$mlCoverage"
            )
            return@withContext MaskDecision(
                mask = modNetMask,
                route = "modnet_only_mlkit_unreliable",
                usedMlKit = true,
                mlKitGuide = normalizedMlKit
            )
        }

        val fusionMaxSide = resolveFusionMaxSide(bitmap)
        val resizeStartMs = SystemClock.elapsedRealtime()
        val smallModNet = MaskMath.resizeToMaxSide(
            modNetMask,
            fusionMaxSide
        )

        val smallMlKit = MaskMath.resizeBilinear(
            normalizedMlKit,
            smallModNet.width,
            smallModNet.height
        )
        Log.d(
            TAG,
            "Fusion resize: source=${bitmap.width}x${bitmap.height}, fusion=${smallModNet.width}x${smallModNet.height}, " +
                "maxSide=$fusionMaxSide, ms=${elapsedMs(resizeStartMs)}"
        )

        require(smallModNet.values.size == smallMlKit.values.size) {
            "Fusion mask size mismatch: mod=${smallModNet.values.size}, ml=${smallMlKit.values.size}"
        }

        val nativeStartMs = SystemClock.elapsedRealtime()
        val fusedSmallValues = nativeFuseMasks(
            smallModNet.values,
            smallMlKit.values,
            smallModNet.width,
            smallModNet.height,
            erodeRadius,
            mlkitThreshold,
            modnetWeakThreshold,
        )
        Log.d(TAG, "Native fusion step: success=${fusedSmallValues != null}, ms=${elapsedMs(nativeStartMs)}")

        val safeFusedSmallValues = fusedSmallValues ?: run {
            Log.e(TAG, "nativeFuseMasks returned null. Keeping ModNet mask.")
            return@withContext MaskDecision(
                mask = modNetMask,
                route = "modnet_only_native_fusion_failed",
                usedMlKit = true,
                mlKitGuide = normalizedMlKit
            )
        }

        if (safeFusedSmallValues.size != smallModNet.width * smallModNet.height) {
            Log.e(TAG, "Native fused mask size mismatch: ${safeFusedSmallValues.size}")
            return@withContext MaskDecision(
                mask = modNetMask,
                route = "modnet_only_native_size_mismatch",
                usedMlKit = true,
                mlKitGuide = normalizedMlKit
            )
        }

        val fusedSmall = PortraitConfidenceMask(
            smallModNet.width,
            smallModNet.height,
            safeFusedSmallValues
        )

        Log.d(TAG, "Fusion total: route=$route, ms=${elapsedMs(totalStartMs)}")
        MaskDecision(
            mask = MaskMath.resizeBilinear(fusedSmall, bitmap.width, bitmap.height),
            route = route,
            usedMlKit = true,
            mlKitGuide = normalizedMlKit
        )
    }

    private fun resolveFusionMaxSide(bitmap: Bitmap): Int {
        val sourceMaxSide = maxOf(bitmap.width, bitmap.height)
        val requested = fusionResolution.coerceAtMost(MAX_FUSION_SIDE).coerceAtLeast(1)

        return when {
            sourceMaxSide <= requested -> sourceMaxSide.coerceAtLeast(1)
            sourceMaxSide >= 2_000 -> maxOf(requested, MAX_FUSION_SIDE)
            else -> requested
        }
    }

    private fun elapsedMs(startMs: Long): Long {
        return SystemClock.elapsedRealtime() - startMs
    }

    private suspend fun postProcessWithFallback(
        source: Bitmap,
        rawMask: PortraitConfidenceMask,
        mlKitGuide: PortraitConfidenceMask?,
        selectedMode: MaskPostProcessor.Mode,
        route: String
    ): PortraitConfidenceMask = withContext(Dispatchers.Default) {
        val rawCoverage = MaskMath.foregroundCoverage(rawMask, threshold = 0.5f)
        val allowGuidePrune = enablePersonPriorPrune &&
            selectedMode == MaskPostProcessor.Mode.AGGRESSIVE &&
            mlKitGuide != null &&
            isMlKitGuideReliable(rawMask, mlKitGuide)

        val processed = MaskPostProcessor.postProcess(
            bitmap = source,
            mask = rawMask,
            options = MaskPostProcessor.Options(
                mode = selectedMode,
                mlKitGuideMask = if (allowGuidePrune) mlKitGuide else null,
                enablePersonPriorPrune = allowGuidePrune,
                enableResidualArtifactPrune = allowGuidePrune,
                enableThinColorResiduePrune = allowGuidePrune,
                enableGreenLeakAssist = false
            )
        )

        val processedCoverage = MaskMath.foregroundCoverage(processed, threshold = 0.5f)
        val minSafeCoverage = when (selectedMode) {
            MaskPostProcessor.Mode.AGGRESSIVE -> rawCoverage * 0.86f
            MaskPostProcessor.Mode.SAFE -> rawCoverage * 0.92f
        }

        if (rawCoverage > 0.04f && processedCoverage < minSafeCoverage) {
            Log.w(
                TAG,
                "Post-process coverage guard rollback. route=$route, mode=$selectedMode, " +
                    "rawCoverage=$rawCoverage, processedCoverage=$processedCoverage, allowGuidePrune=$allowGuidePrune"
            )
            MaskPostProcessor.postProcess(
                bitmap = source,
                mask = rawMask,
                options = MaskPostProcessor.Options(
                    mode = MaskPostProcessor.Mode.SAFE,
                    enableGreenLeakAssist = false,
                    enablePersonPriorPrune = false,
                    enableResidualArtifactPrune = false,
                    enableThinColorResiduePrune = false
                )
            )
        } else {
            processed
        }
    }

    private fun isMlKitGuideReliable(
        baseMask: PortraitConfidenceMask,
        mlKitGuide: PortraitConfidenceMask
    ): Boolean {
        val normalizedGuide = MaskMath.ensureMaskSize(mlKitGuide, baseMask.width, baseMask.height)
        val baseCoverage = MaskMath.foregroundCoverage(baseMask, threshold = 0.5f)
        val guideCoverage = MaskMath.foregroundCoverage(normalizedGuide, threshold = 0.5f)

        if (baseCoverage < 0.04f) return guideCoverage >= NO_SUBJECT_MLKIT_COVERAGE
        if (guideCoverage < 0.06f) return false
        if (guideCoverage > 0.82f) return false
        if (guideCoverage < baseCoverage * 0.62f) return false
        if (guideCoverage > baseCoverage * 1.55f) return false

        return true
    }

    private fun Bitmap.ensureSoftwareArgb8888(): Bitmap {
        require(!isRecycled) { "Bitmap is recycled" }

        return if (config == Bitmap.Config.ARGB_8888) {
            this
        } else {
            copy(Bitmap.Config.ARGB_8888, true).also {
                it.setHasAlpha(true)
            }
        }
    }

    private fun Bitmap.upscaleSmallBitmapForMaskIfNeeded(): Bitmap {
        val pixels = width * height
        val maxSide = maxOf(width, height)

        if (pixels >= LOW_RES_ASSIST_MIN_PIXELS || maxSide >= LOW_RES_ASSIST_MAX_SIDE) {
            return this
        }

        val scaleForPixels = sqrt(LOW_RES_ASSIST_MIN_PIXELS.toFloat() / pixels)
        val scaleForMaxSide = LOW_RES_ASSIST_MAX_SIDE.toFloat() / maxSide
        val scale = minOf(scaleForPixels, scaleForMaxSide)

        if (scale <= 1.05f) {
            return this
        }

        val targetWidth = (width * scale).roundToInt().coerceAtLeast(width + 1)
        val targetHeight = (height * scale).roundToInt().coerceAtLeast(height + 1)

        Log.d(TAG, "Low-res mask assist: ${width}x$height -> ${targetWidth}x$targetHeight")

        return Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true).also {
            it.setHasAlpha(hasAlpha())
        }
    }
}
