package com.thgiang.image.core.data.backgroundremove

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.common.moduleinstall.InstallStatusListener
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.common.moduleinstall.ModuleInstallStatusUpdate.InstallState.STATE_CANCELED
import com.google.android.gms.common.moduleinstall.ModuleInstallStatusUpdate.InstallState.STATE_COMPLETED
import com.google.android.gms.common.moduleinstall.ModuleInstallStatusUpdate.InstallState.STATE_FAILED
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenter
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import java.nio.FloatBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.ByteBuffer
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Background remover backed by ML Kit Subject Segmentation.
 *
 * Subject Segmentation when the device and heap allow it.
 * Selfie Segmentation (bundled, lighter) is used proactively on weak devices or when Subject is unsafe.
 */
class MlKitBackgroundRemoverRepository(
    private val context: Context,
    private val maxDecodeSize: Int = 2048,
    private val createDisplayCopy: Boolean = true,
    @Suppress("unused") private val alphaSmoothingRadius: Float = 1.5f
) : BackgroundRemoverRepository {

    private companion object {
        /** ML Kit recommends ≥512×512; default upscale target for quality. */
        private const val MIN_PRE_UPSCALE_PIXELS_DEFAULT = 500_000
        private const val MIN_PRE_UPSCALE_PIXELS_LOW_RAM = 262_144 // 512×512
        private const val SUBJECT_MODULE_INSTALL_TIMEOUT_MS = 15_000L
        private const val TAG = "MlKitRemover"
    }

    private val minPreUpscalePixels: Int
        get() = when {
            MlKitDeviceSupport.shouldForceSelfiePrimary(context) -> 0
            maxDecodeSize <= 1536 -> MIN_PRE_UPSCALE_PIXELS_LOW_RAM
            else -> MIN_PRE_UPSCALE_PIXELS_DEFAULT
        }

    @Volatile
    private var selfieFallbackUsedInLastOperation: Boolean = false

    @Volatile
    private var playServicesUpdateRecommended: Boolean = false

    @Volatile
    private var subjectModuleUnavailableInLastOperation: Boolean = false

    private val segmenterMutex = Mutex()

    private fun blockedPlanError(): Nothing {
        if (!SelfieFallbackSegmenter.isNativeLibraryAvailable(context)) {
            error(MlKitDeviceSupport.ERROR_DEVICE_UNSUPPORTED)
        }
        if (MlKitDeviceSupport.isSegmentationUnsupportedOnRelease(context)) {
            error(MlKitDeviceSupport.ERROR_DEVICE_UNSUPPORTED)
        }
        error(MlKitMemoryBudget.ERROR_INSUFFICIENT_HEAP)
    }

    private suspend fun <T> runSegmentationWithTimeout(block: suspend () -> T): T {
        val timeoutMs = MlKitDeviceSupport.segmentationTimeoutMs(context)
        if (timeoutMs == Long.MAX_VALUE) {
            return block()
        }
        return try {
            withTimeout(timeoutMs) { block() }
        } catch (_: TimeoutCancellationException) {
            error(MlKitDeviceSupport.ERROR_SEGMENTATION_TIMEOUT)
        }
    }

    private val segmenter: SubjectSegmenter by lazy {
        // Only enable confidence mask — foreground bitmap doubles GMS native memory use.
        val options = SubjectSegmenterOptions.Builder()
            .enableForegroundConfidenceMask()
            .build()
        SubjectSegmentation.getClient(options)
    }

    override suspend fun removeBackground(imageUri: Uri): Result<BackgroundRemovalOutput> = runCatching {
        resetOperationFlags()
        Log.d(TAG, "removeBackground: start uri=$imageUri")
        val decodeCap = MlKitMemoryBudget.resolveMaxProcessSide(
            policyCap = maxDecodeSize,
            sourceWidth = Int.MAX_VALUE,
            sourceHeight = Int.MAX_VALUE,
        )
        val original = BitmapDecodeUtils.loadBitmapFromUri(context, imageUri, decodeCap)
            ?: error("Cannot decode selected image (OOM or invalid image)")
        Log.d(
            TAG,
            "removeBackground: decoded original=${original.width}x${original.height} decodeCap=$decodeCap",
        )

        val foreground = runCatching {
            getForegroundBitmapInternal(original)
        }.getOrElse { e ->
            original.recycle()
            throw e
        }
        Log.d(TAG, "removeBackground: foreground ready=${foreground.width}x${foreground.height}")
        original.recycle()

        val display = if (createDisplayCopy && MlKitMemoryBudget.canAllocateDisplayCopy(foreground)) {
            foreground.copy(Bitmap.Config.ARGB_8888, true)
        } else {
            foreground
        }

        BackgroundRemovalOutput(
            foregroundToDisplay = display,
            foregroundToSave = foreground
        )
    }

    override suspend fun getForegroundBitmap(bitmap: Bitmap): Result<Bitmap> = runCatching {
        resetOperationFlags()
        validateBitmap(bitmap)
        val opStart = System.currentTimeMillis()
        MlKitDebugDiagnostics.logStage(
            context, "getForegroundBitmap_enter",
            "thread=${Thread.currentThread().name} ${bitmap.width}x${bitmap.height}",
        )
        MlKitDebugDiagnostics.logRemovalStart(
            context, "getForegroundBitmap", bitmap.width, bitmap.height, maxDecodeSize,
        )
        Log.d(TAG, "getForegroundBitmap: input=${bitmap.width}x${bitmap.height} hasAlpha=${bitmap.hasAlpha()}")
        try {
            withTimeout(MlKitDeviceSupport.interactiveRemoveBgTimeoutMs(context)) {
                getForegroundBitmapInternal(bitmap)
            }
        } catch (e: TimeoutCancellationException) {
            throw IllegalStateException(MlKitDeviceSupport.ERROR_SEGMENTATION_TIMEOUT, e)
        } catch (e: Throwable) {
            MlKitDebugDiagnostics.logRemovalFailure(
                context, "getForegroundBitmap", e, System.currentTimeMillis() - opStart,
            )
            throw e
        }
    }

    override suspend fun getPortraitConfidenceMask(bitmap: Bitmap): Result<PortraitConfidenceMask> =
        runCatching {
            resetOperationFlags()
            validateBitmap(bitmap)
            val mask = withContext(Dispatchers.Default) {
                val policyCap = MlKitMemoryBudget.resolveMaxProcessSide(
                    policyCap = maxDecodeSize,
                    sourceWidth = bitmap.width,
                    sourceHeight = bitmap.height,
                )
                val plan = MlKitSegmentationRouter.choosePlan(
                    context,
                    bitmap.width,
                    bitmap.height,
                    policyCap,
                )
                runSegmentationWithTimeout {
                    runSegmentationMask(bitmap, plan)
                }
            }
            PortraitConfidenceMask(mask.width, mask.height, mask.data.copyOf())
        }

    override fun close() {
        runCatching { segmenter.close() }
        runCatching { SelfieFallbackSegmenter.close() }
    }

    override fun consumeSelfieFallbackWarning(): Boolean {
        val used = selfieFallbackUsedInLastOperation
        selfieFallbackUsedInLastOperation = false
        return used
    }

    override fun consumePlayServicesUpdateRecommended(): Boolean {
        val recommended = playServicesUpdateRecommended
        playServicesUpdateRecommended = false
        return recommended
    }

    private fun resetOperationFlags() {
        selfieFallbackUsedInLastOperation = false
        playServicesUpdateRecommended = false
        subjectModuleUnavailableInLastOperation = false
    }

    private fun markPlayServicesUpdateRecommended(reason: Throwable? = null) {
        playServicesUpdateRecommended = true
        Log.w(TAG, "Recommend Google Play services update", reason)
    }

    private suspend fun getForegroundBitmapInternal(bitmap: Bitmap): Bitmap = withContext(Dispatchers.Default) {
        val totalStart = System.currentTimeMillis()
        MlKitDebugDiagnostics.logStage(context, "internal_start", "thread=${Thread.currentThread().name}")
        MlKitMemoryBudget.prepareForMlKit()

        val outputWidth = bitmap.width
        val outputHeight = bitmap.height
        val policyMaxSide = MlKitMemoryBudget.resolveMaxProcessSide(
            policyCap = maxDecodeSize,
            sourceWidth = outputWidth,
            sourceHeight = outputHeight,
        )
        val plan = MlKitSegmentationRouter.choosePlan(context, outputWidth, outputHeight, policyMaxSide)
        if (plan == MlKitSegmentationRouter.Plan.BLOCKED) {
            blockedPlanError()
        }
        val maxSide = MlKitSegmentationRouter.maxWorkSideForPlan(plan, policyMaxSide)
            ?: error(MlKitMemoryBudget.ERROR_INSUFFICIENT_HEAP)
        MlKitDebugDiagnostics.logRouting(context, outputWidth, outputHeight, policyMaxSide, plan.name, maxSide)
        Log.d(
            TAG,
            "getForegroundBitmapInternal: input=${outputWidth}x$outputHeight plan=$plan maxSide=$maxSide " +
                "freeHeap=${MlKitMemoryBudget.freeHeapBytes() / (1024 * 1024)}MB",
        )

        val capped = MlKitMemoryBudget.downscaleToMaxSide(bitmap, maxSide)
        var processedBitmap = capped.bitmap
        var ownsProcessedBitmap = capped.ownsBitmap
        MlKitDebugDiagnostics.logBitmap(context, "after_downscale", processedBitmap)

        val pixels = processedBitmap.width * processedBitmap.height
        val needsUpscale = pixels < minPreUpscalePixels
        if (needsUpscale) {
            MlKitDebugDiagnostics.logStage(
                context, "pre_upscale",
                "pixels=$pixels < min=$minPreUpscalePixels — upscaling for ML quality",
            )
            val scale = sqrt(minPreUpscalePixels.toFloat() / pixels)
            var newWidth = (processedBitmap.width * scale).toInt().coerceAtLeast(processedBitmap.width + 1)
            var newHeight = (processedBitmap.height * scale).toInt().coerceAtLeast(processedBitmap.height + 1)
            val upscaleMax = maxOf(newWidth, newHeight)
            if (upscaleMax > maxSide) {
                val shrink = maxSide.toFloat() / upscaleMax
                newWidth = (newWidth * shrink).toInt().coerceAtLeast(1)
                newHeight = (newHeight * shrink).toInt().coerceAtLeast(1)
            }
            val upscaled = Bitmap.createScaledBitmap(processedBitmap, newWidth, newHeight, true)
            if (ownsProcessedBitmap && processedBitmap !== bitmap && !processedBitmap.isRecycled) {
                processedBitmap.recycle()
            }
            processedBitmap = upscaled
            ownsProcessedBitmap = true
            MlKitDebugDiagnostics.logBitmap(context, "after_upscale", processedBitmap)
        }

        val segmentationStart = System.currentTimeMillis()
        try {
            val mask = runSegmentationWithTimeout {
                runSegmentationMask(processedBitmap, plan)
            }
            val segmentationMs = System.currentTimeMillis() - segmentationStart
            MlKitDebugDiagnostics.logStage(
                context, "segmentation_done", "plan=$plan mask=${mask.width}x${mask.height}", segmentationMs,
            )
            val maskStart = System.currentTimeMillis()
            val foreground = applyMaskToImage(processedBitmap, mask)
            Log.d(
                TAG,
                "getForegroundBitmapInternal: applyMaskToImage took ${System.currentTimeMillis() - maskStart}ms",
            )
            Log.d(
                TAG,
                "getForegroundBitmapInternal: total ${System.currentTimeMillis() - segmentationStart}ms " +
                    "work=${processedBitmap.width}x${processedBitmap.height}",
            )

            val result = if (foreground.width == outputWidth && foreground.height == outputHeight) {
                foreground
            } else {
                MlKitDebugDiagnostics.logStage(
                    context, "restore_size",
                    "${foreground.width}x${foreground.height} → ${outputWidth}x$outputHeight",
                )
                runCatching {
                    Bitmap.createScaledBitmap(foreground, outputWidth, outputHeight, true).also { restored ->
                        if (restored !== foreground && !foreground.isRecycled) {
                            foreground.recycle()
                        }
                    }
                }.getOrElse { error ->
                    Log.w(
                        TAG,
                        "OOM restoring ${outputWidth}x$outputHeight; returning work size " +
                            "${foreground.width}x${foreground.height}",
                        error,
                    )
                    foreground
                }
            }
            MlKitDebugDiagnostics.logRemovalSuccess(
                context = context,
                planName = plan.name,
                inputWidth = outputWidth,
                inputHeight = outputHeight,
                workWidth = processedBitmap.width,
                workHeight = processedBitmap.height,
                outputWidth = result.width,
                outputHeight = result.height,
                segmentationMs = segmentationMs,
                totalMs = System.currentTimeMillis() - totalStart,
                selfiePrimary = plan == MlKitSegmentationRouter.Plan.SELFIE_PRIMARY,
                selfieFallback = selfieFallbackUsedInLastOperation &&
                    plan != MlKitSegmentationRouter.Plan.SELFIE_PRIMARY,
            )
            result
        } finally {
            if (ownsProcessedBitmap && processedBitmap !== bitmap && !processedBitmap.isRecycled) {
                processedBitmap.recycle()
            }
        }
    }

    private fun readMask(buffer: java.nio.FloatBuffer, bitmapWidth: Int, bitmapHeight: Int): Mask {
        buffer.rewind()
        val count = buffer.remaining()
        val values = FloatArray(count)
        buffer.get(values)
        return if (count == bitmapWidth * bitmapHeight) {
            Mask(bitmapWidth, bitmapHeight, values)
        } else {
            val side = kotlin.math.sqrt(count.toDouble()).toInt()
            require(side * side == count) { "Unexpected ML Kit mask size: $count" }
            Mask(side, side, values).resizeBilinear(bitmapWidth, bitmapHeight)
        }
    }

    private fun canFallbackToSelfie(bitmap: Bitmap): Boolean {
        if (MlKitDeviceSupport.shouldAvoidSelfieForQuality(context)) return false
        return MlKitMemoryBudget.canRunSelfieSegmentation(bitmap.width, bitmap.height)
    }

    private suspend fun runSegmentationMask(
        bitmap: Bitmap,
        plan: MlKitSegmentationRouter.Plan,
    ): Mask {
        val waitStart = SystemClock.elapsedRealtime()
        return segmenterMutex.withLock {
            val waitMs = SystemClock.elapsedRealtime() - waitStart
            if (waitMs >= 250L) {
                MlKitDebugDiagnostics.logStage(
                    context,
                    stage = "mutex_wait",
                    detail = "waited=${waitMs}ms plan=$plan",
                )
            }
            runSegmentationMaskUnlocked(bitmap, plan)
        }
    }

    /**
     * Runs under [segmenterMutex]. Subject, Selfie, fallback, and module installation
     * must be single-flight because they share native ML resources and memory.
     */
    private suspend fun runSegmentationMaskUnlocked(
        bitmap: Bitmap,
        plan: MlKitSegmentationRouter.Plan,
    ): Mask {
        MlKitDebugDiagnostics.logStage(
            context, "runSegmentationMask",
            "plan=$plan bitmap=${bitmap.width}x${bitmap.height}",
        )
        when (plan) {
            MlKitSegmentationRouter.Plan.BLOCKED -> blockedPlanError()
            MlKitSegmentationRouter.Plan.SELFIE_PRIMARY -> {
                markSelfieSegmentationUsed(primary = true)
                return runSelfieMask(bitmap, priorError = null)
            }
            MlKitSegmentationRouter.Plan.SUBJECT -> {
                val subjectResult = runCatching { runSubjectSegmentationMask(bitmap) }
                if (subjectResult.isSuccess) {
                    return subjectResult.getOrThrow()
                }
                val error = subjectResult.exceptionOrNull()
                Log.w(TAG, "Subject plan failed; trying Selfie if safe", error)
                if (canFallbackToSelfie(bitmap)) {
                    markSelfieSegmentationUsed(primary = false)
                    return runSelfieMask(bitmap, error)
                }
                if (!MlKitDeviceSupport.isDebugBuild(context)) {
                    error(MlKitDeviceSupport.ERROR_DEVICE_UNSUPPORTED)
                }
                throw error ?: IllegalStateException(MlKitMemoryBudget.ERROR_INSUFFICIENT_HEAP)
            }
        }
    }

    private fun markSelfieSegmentationUsed(primary: Boolean) {
        selfieFallbackUsedInLastOperation = true
        if (primary) {
            Log.i(TAG, "Using Selfie segmenter (primary — device/heap tier)")
        }
    }

    private suspend fun runSubjectSegmentationMask(bitmap: Bitmap): Mask {
        MlKitDebugDiagnostics.logStage(context, "subject_start", "bitmap=${bitmap.width}x${bitmap.height}")
        var lastError: Throwable? = null
        val scales = MlKitMemoryBudget.segmentationRetryScales(bitmap.width, bitmap.height)
        MlKitDebugDiagnostics.logStage(context, "subject_scales", scales.joinToString())
        for (scale in scales) {
            val workBitmap = if (scale >= 0.99f) {
                bitmap
            } else {
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).toInt().coerceAtLeast(64),
                    (bitmap.height * scale).toInt().coerceAtLeast(64),
                    true,
                )
            }
            val ownsWorkBitmap = workBitmap !== bitmap
            val refusal = MlKitMemoryBudget.subjectRefusalReason(workBitmap.width, workBitmap.height)
            if (refusal != null) {
                Log.w(TAG, "Skip ML Kit scale=$scale: $refusal")
                if (ownsWorkBitmap && !workBitmap.isRecycled) workBitmap.recycle()
                continue
            }
            try {
                val mlStart = System.currentTimeMillis()
                MlKitDebugDiagnostics.logStage(
                    context, "subject_process",
                    "scale=$scale size=${workBitmap.width}x${workBitmap.height} — awaiting GMS…",
                )
                val input = InputImage.fromBitmap(workBitmap, 0)
                val result = processSafely(input)
                val buffer = result.foregroundConfidenceMask
                    ?: error("Foreground confidence mask not available")
                Log.d(
                    TAG,
                    "SubjectSegmenter OK scale=$scale size=${workBitmap.width}x${workBitmap.height} " +
                        "took ${System.currentTimeMillis() - mlStart}ms",
                )
                val mask = readMask(buffer, workBitmap.width, workBitmap.height)
                return if (scale < 0.99f) {
                    mask.resizeBilinear(bitmap.width, bitmap.height)
                } else {
                    mask
                }
            } catch (e: Throwable) {
                lastError = e
                Log.w(
                    TAG,
                    "SubjectSegmenter failed scale=$scale size=${workBitmap.width}x${workBitmap.height}",
                    e,
                )
                if (MlKitMemoryBudget.isOutOfMemory(e)) {
                    // Skip larger scales — heap is exhausted.
                    break
                }
            } finally {
                if (ownsWorkBitmap && !workBitmap.isRecycled) workBitmap.recycle()
            }
        }

        if (!canFallbackToSelfie(bitmap)) {
            if (MlKitDeviceSupport.shouldAvoidSelfieForQuality(context)) {
                throw lastError ?: IllegalStateException(MlKitDeviceSupport.ERROR_DEVICE_UNSUPPORTED)
            }
            if (!MlKitDeviceSupport.isDebugBuild(context)) {
                throw lastError ?: IllegalStateException(MlKitDeviceSupport.ERROR_DEVICE_UNSUPPORTED)
            }
            throw lastError ?: IllegalStateException(MlKitMemoryBudget.ERROR_INSUFFICIENT_HEAP)
        }
        markSelfieSegmentationUsed(primary = false)
        return runSelfieMask(bitmap, lastError)
    }

    private suspend fun runSelfieMask(bitmap: Bitmap, priorError: Throwable?): Mask {
        MlKitDebugDiagnostics.logStage(
            context, "selfie_start",
            "bitmap=${bitmap.width}x${bitmap.height} priorError=${priorError?.message}",
        )
        if (!SelfieFallbackSegmenter.isSupported(context)) {
            MlKitDebugDiagnostics.logStage(
                context, "selfie_unavailable",
                "libxeno_native.so missing in ${context.applicationInfo.nativeLibraryDir}",
            )
            if (subjectModuleUnavailableInLastOperation ||
                GooglePlayServicesHelper.isUpdateRecommended(context) ||
                GooglePlayServicesHelper.isPlayServicesRelatedFailure(priorError)
            ) {
                markPlayServicesUpdateRecommended(priorError)
            }
            throw priorError ?: error(MlKitDeviceSupport.ERROR_DEVICE_UNSUPPORTED)
        }
        if (priorError != null) {
            Log.w(TAG, "Falling back to SelfieSegmenter after Subject failure", priorError)
        }
        if (subjectModuleUnavailableInLastOperation ||
            GooglePlayServicesHelper.isUpdateRecommended(context)
        ) {
            markPlayServicesUpdateRecommended(priorError)
        }

        val selfieCapMax = if (MlKitDeviceSupport.shouldForceSelfiePrimary(context)) {
            MlKitDeviceSupport.X86_EMULATOR_ML_CAP
        } else {
            1280
        }
        val selfieCap = MlKitMemoryBudget.largestRunnableSelfieSide(
            MlKitMemoryBudget.resolveMaxProcessSide(
                policyCap = maxDecodeSize,
                sourceWidth = bitmap.width,
                sourceHeight = bitmap.height,
            ).coerceAtMost(selfieCapMax),
        )
        if (selfieCap == null) {
            throw priorError ?: IllegalStateException(MlKitMemoryBudget.ERROR_INSUFFICIENT_HEAP)
        }
        val fallbackBitmap = MlKitMemoryBudget.downscaleToMaxSide(bitmap, selfieCap)
        MlKitDebugDiagnostics.logBitmap(context, "selfie_work", fallbackBitmap.bitmap)
        try {
            val mlStart = System.currentTimeMillis()
            MlKitDebugDiagnostics.logStage(
                context, "selfie_process",
                "size=${fallbackBitmap.bitmap.width}x${fallbackBitmap.bitmap.height} — running bundled model…",
            )
            val input = InputImage.fromBitmap(fallbackBitmap.bitmap, 0)
            val resultBuffer = SelfieFallbackSegmenter.process(context, input)
            Log.d(
                TAG,
                "SelfieSegmenter took ${System.currentTimeMillis() - mlStart}ms " +
                    "size=${fallbackBitmap.bitmap.width}x${fallbackBitmap.bitmap.height}",
            )
            val mask = readMask(resultBuffer.asFloatBuffer(), fallbackBitmap.bitmap.width, fallbackBitmap.bitmap.height)
            return if (fallbackBitmap.bitmap.width != bitmap.width || fallbackBitmap.bitmap.height != bitmap.height) {
                mask.resizeBilinear(bitmap.width, bitmap.height)
            } else {
                mask
            }
        } finally {
            if (fallbackBitmap.ownsBitmap && !fallbackBitmap.bitmap.isRecycled) {
                fallbackBitmap.bitmap.recycle()
            }
        }
    }

    private suspend fun processSafely(input: InputImage) = run {
        MlKitDebugDiagnostics.logStage(context, "subject_module_check", "checking Play services module…")
        val moduleReady = ensureSubjectSegmenterModuleAvailable()
        MlKitDebugDiagnostics.logStage(
            context, "subject_module_result", "ready=$moduleReady unavailable=$subjectModuleUnavailableInLastOperation",
        )
        if (!moduleReady) {
            subjectModuleUnavailableInLastOperation = true
            markPlayServicesUpdateRecommended()
            error("SubjectSegmenter Play services module is not available")
        }
        runCatching { segmenter.process(input).await() }
            .getOrElse { error ->
                MlKitDebugDiagnostics.logStage(
                    context, "subject_process_fail", error.message ?: error::class.java.simpleName,
                )
                if (GooglePlayServicesHelper.isPlayServicesRelatedFailure(error)) {
                    markPlayServicesUpdateRecommended(error)
                }
                throw error
            }
    }

    private suspend fun ensureSubjectSegmenterModuleAvailable(): Boolean {
        return runCatching {
            val moduleInstallClient = ModuleInstall.getClient(context)
            val availability = moduleInstallClient.areModulesAvailable(segmenter).await()
            if (availability.areModulesAvailable()) {
                Log.d(TAG, "SubjectSegmenter module already available")
                return true
            }

            Log.d(TAG, "SubjectSegmenter module missing; requesting install")
            MlKitDebugDiagnostics.logStage(
                context, "subject_module_install",
                "timeout=${SUBJECT_MODULE_INSTALL_TIMEOUT_MS}ms",
            )
            val installed = requestSubjectSegmenterModuleInstall(moduleInstallClient)
            Log.d(TAG, "SubjectSegmenter module install completed=$installed")
            if (!installed) {
                subjectModuleUnavailableInLastOperation = true
                markPlayServicesUpdateRecommended()
            }
            installed
        }.getOrElse { e ->
            Log.e(TAG, "SubjectSegmenter module availability/install check failed", e)
            subjectModuleUnavailableInLastOperation = true
            markPlayServicesUpdateRecommended(e)
            false
        }
    }

    private suspend fun requestSubjectSegmenterModuleInstall(
        moduleInstallClient: com.google.android.gms.common.moduleinstall.ModuleInstallClient
    ): Boolean {
        return withTimeoutOrNull(SUBJECT_MODULE_INSTALL_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                var listener: InstallStatusListener? = null

                fun finish(installed: Boolean) {
                    listener?.let { moduleInstallClient.unregisterListener(it) }
                    if (continuation.isActive) continuation.resume(installed)
                }

                listener = InstallStatusListener { update ->
                    when (update.installState) {
                        STATE_COMPLETED -> finish(true)
                        STATE_CANCELED, STATE_FAILED -> finish(false)
                    }
                }

                continuation.invokeOnCancellation {
                    listener?.let { moduleInstallClient.unregisterListener(it) }
                }

                val request = ModuleInstallRequest.newBuilder()
                    .addApi(segmenter)
                    .setListener(listener)
                    .build()

                moduleInstallClient.installModules(request)
                    .addOnSuccessListener { response ->
                        if (response.areModulesAlreadyInstalled()) {
                            finish(true)
                        } else {
                            Log.d(TAG, "SubjectSegmenter module install request accepted")
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "SubjectSegmenter module install request failed", e)
                        finish(false)
                    }
            }
        } ?: false
    }



    private fun validateBitmap(bitmap: Bitmap) {
        require(!bitmap.isRecycled) { "Bitmap is already recycled" }
        require(bitmap.config == Bitmap.Config.ARGB_8888) {
            "Bitmap config must be ARGB_8888, actual: ${bitmap.config}"
        }
    }

    private suspend fun decodeBitmapWithResize(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, this)
                }
                var sample = 1
                while (outWidth / sample > maxDecodeSize || outHeight / sample > maxDecodeSize) {
                    sample *= 2
                }
                inSampleSize = sample
                inJustDecodeBounds = false
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
        }.getOrNull()
    }
}
