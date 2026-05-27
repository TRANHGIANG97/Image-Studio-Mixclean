package com.toshiba.modnet

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HybridBackgroundRemover(
    context: Context,
) : AutoCloseable {
    private companion object {
        private const val LAB_PREVIEW_MAX_SIDE = 1080
    }

    private val appContext = context.applicationContext
    private val isNet = IsNetRemover(appContext)
    private val mlKit = MlKitFallbackRemover(appContext)

    suspend fun initialize() = withContext(Dispatchers.IO) {
        // No pre-initialization needed
    }

    suspend fun prepare(
        backgroundModels: Set<BackgroundMaskModel>,
        coreModels: Set<CoreMaskModel>,
    ) = withContext(Dispatchers.IO) {
        // No pre-initialization needed
    }

    suspend fun compareBackgrounds(
        bitmap: Bitmap,
        backgroundModels: Set<BackgroundMaskModel>,
        coreModels: Set<CoreMaskModel>,
    ): FusionLabCompareResult = withContext(Dispatchers.Default) {
        val totalStart = SystemClock.elapsedRealtime()
        val source = bitmap.ensureArgb8888CopyIfNeeded()
        val selectedBackgrounds = backgroundModels.ifEmpty { setOf(BackgroundMaskModel.U2NETP) }
        val selectedCores = coreModels

        val coreResults = selectedCores.associateWith { model ->
            runCatching { buildCoreMask(source, model) }.getOrNull()
        }
        val backgroundResults = selectedBackgrounds.map { model ->
            model to try {
                buildBackgroundMask(source, model)
            } catch (error: Throwable) {
                failedBackgroundMask(source, model, error)
            }
        }

        val results = mutableListOf<FusionLabResult>()
        for ((backgroundModel, background) in backgroundResults) {
            if (selectedCores.isEmpty()) {
                results += FusionLabResult(
                    title = backgroundModel.label,
                    result = resultFromMask(
                        source = source,
                        mask = background.mask,
                        confidencePercent = background.confidencePercent,
                        route = background.route,
                        timing = MaskTiming(
                            totalMs = background.elapsedMs,
                            modNetMs = background.elapsedMs,
                        ),
                    ),
                )
                continue
            }

            for (coreModel in selectedCores) {
                val core = coreResults[coreModel]
                results += if (core != null && core.mask.foregroundCoverage() >= 0.003f) {
                    val fusionStart = SystemClock.elapsedRealtime()
                    val fusedMask = if (
                        coreModel == CoreMaskModel.ML_KIT &&
                        backgroundModel != BackgroundMaskModel.ML_KIT
                    ) {
                        NativeMaskFusion.fuseCoreFirstDetail(
                            source = source,
                            detailMask = background.mask,
                            coreMask = core.mask,
                        )
                    } else {
                        NativeMaskFusion.fuse(
                            modNetMask = background.mask,
                            coreMask = core.mask,
                            erodeRadius = if (coreModel == CoreMaskModel.ML_KIT) 3 else 2,
                            coreThreshold = 0.86f,
                            modnetWeakThreshold = 0.48f,
                        )
                    }
                    val fusionMs = SystemClock.elapsedRealtime() - fusionStart
                    FusionLabResult(
                        title = "${backgroundModel.label} + ${coreModel.label}",
                        result = resultFromMask(
                            source = source,
                            mask = fusedMask,
                            confidencePercent = ((background.confidencePercent + core.confidencePercent) / 2).coerceIn(0, 100),
                            route = "${background.route} + ${core.route} + native_fusion",
                            timing = MaskTiming(
                                totalMs = background.elapsedMs + core.elapsedMs + fusionMs,
                                modNetMs = background.elapsedMs,
                                coreMs = core.elapsedMs,
                                fusionMs = fusionMs,
                            ),
                        ),
                    )
                } else {
                    FusionLabResult(
                        title = "${backgroundModel.label} + ${coreModel.label}",
                        result = resultFromMask(
                            source = source,
                            mask = background.mask,
                            confidencePercent = background.confidencePercent,
                            route = "${background.route} + ${coreModel.label.lowercase()}_unavailable",
                            timing = MaskTiming(
                                totalMs = background.elapsedMs,
                                modNetMs = background.elapsedMs,
                            ),
                        ),
                    )
                }
            }
        }

        FusionLabCompareResult(
            results = results,
            totalMs = SystemClock.elapsedRealtime() - totalStart,
        )
    }

    private suspend fun buildBackgroundMask(
        source: Bitmap,
        model: BackgroundMaskModel,
    ): MaskModelResult {
        val start = SystemClock.elapsedRealtime()
        val (mask, confidencePercent) = when (model) {
            BackgroundMaskModel.ML_KIT -> {
                val mask = mlKit.getMask(source)
                    ?: throw IllegalStateException("ML Kit subject segmentation is not available")
                mask to mask.calculateCertaintyPercent()
            }
            BackgroundMaskModel.U2NETP -> {
                val result = isNet.getCoreMask(
                    bitmap = source,
                    modelAsset = model.assetName,
                    inputSize = requireNotNull(model.inputSize)
                ) ?: throw IllegalStateException("${model.assetName} is not available")
                result.mask to result.confidencePercent
            }
        }
        return MaskModelResult(
            mask = mask,
            confidencePercent = confidencePercent,
            route = "${model.label}(${model.assetName})",
            elapsedMs = SystemClock.elapsedRealtime() - start,
        )
    }

    private suspend fun buildCoreMask(
        source: Bitmap,
        model: CoreMaskModel,
    ): MaskModelResult? {
        val start = SystemClock.elapsedRealtime()
        return when (model) {
            CoreMaskModel.ML_KIT -> {
                val mask = mlKit.getMask(source) ?: return null
                MaskModelResult(
                    mask = mask,
                    confidencePercent = mask.calculateCertaintyPercent(),
                    route = "mlkit_subject_segmentation",
                    elapsedMs = SystemClock.elapsedRealtime() - start,
                )
            }
        }
    }

    private fun failedBackgroundMask(
        source: Bitmap,
        model: BackgroundMaskModel,
        error: Throwable,
    ): MaskModelResult {
        val emptyMask = Mask(source.width, source.height, FloatArray(source.width * source.height))
        return MaskModelResult(
            mask = emptyMask,
            confidencePercent = 0,
            route = "${model.label}_failed(${error.message})",
            elapsedMs = 0,
        )
    }

    private fun resultFromMask(
        source: Bitmap,
        mask: Mask,
        confidencePercent: Int,
        route: String,
        timing: MaskTiming,
    ): RemoveBgResult {
        val fullCutout = applyMaskToImage(source, mask)
        val displayCutout = fullCutout.scaledForLabPreview(LAB_PREVIEW_MAX_SIDE)
        if (displayCutout !== fullCutout && !fullCutout.isRecycled) {
            fullCutout.recycle()
        }

        val fullMaskPreview = mask.toPreviewBitmap()
        val displayMaskPreview = fullMaskPreview.scaledForLabPreview(LAB_PREVIEW_MAX_SIDE)
        if (displayMaskPreview !== fullMaskPreview && !fullMaskPreview.isRecycled) {
            fullMaskPreview.recycle()
        }

        return RemoveBgResult(
            bitmap = displayCutout,
            confidencePercent = confidencePercent,
            route = route,
            timing = timing,
            maskPreview = displayMaskPreview,
        )
    }

    override fun close() {
        isNet.close()
        mlKit.close()
    }
}

private fun Bitmap.scaledForLabPreview(maxSide: Int): Bitmap {
    val currentMaxSide = maxOf(width, height)
    if (currentMaxSide <= maxSide) return this

    val scale = maxSide.toFloat() / currentMaxSide.toFloat()
    val targetWidth = (width * scale).toInt().coerceAtLeast(1)
    val targetHeight = (height * scale).toInt().coerceAtLeast(1)

    return Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true).also {
        it.setHasAlpha(hasAlpha())
    }
}
