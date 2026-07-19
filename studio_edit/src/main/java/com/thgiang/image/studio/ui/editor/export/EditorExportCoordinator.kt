package com.thgiang.image.studio.ui.editor.export
import com.thgiang.image.studio.ui.editor.*

import android.content.Context
import com.thgiang.image.studio.ui.editor.model.*
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.compose.ui.unit.IntSize
import com.thgiang.image.core.data.save.ImageSaveRepository
import com.thgiang.image.core.util.MemoryUtil
import com.thgiang.image.studio.R
import com.thgiang.image.studio.data.TemplateDraftRepository
import com.thgiang.image.studio.util.openAssetSourceInputStream
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

sealed interface ExportOutcome {
    data class Success(val uri: Uri) : ExportOutcome
    data class Failure(val message: String) : ExportOutcome
}

sealed interface SaveDraftOutcome {
    data class Success(val draftId: String, val savedAt: Long) : SaveDraftOutcome
    data class Failure(val error: Throwable) : SaveDraftOutcome
}

@ViewModelScoped
class EditorExportCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val renderer: EditorRenderer,
    private val imageSaveRepository: ImageSaveRepository,
    private val templateDraftRepository: TemplateDraftRepository,
) {
    suspend fun export(state: EditorState, templateAssetPath: String): ExportOutcome {
        val templateSize = state.template.originalSize
        if (templateSize.width == 0 || templateSize.height == 0) {
            return ExportOutcome.Failure(context.getString(R.string.studio_error_unknown))
        }
        val exportSize = MemoryUtil.clampExportSize(
            templateSize.width,
            templateSize.height,
            context,
        )
        if (!MemoryUtil.canAllocateExportBitmap(exportSize.width, exportSize.height, context)) {
            return ExportOutcome.Failure(context.getString(R.string.studio_error_not_enough_memory))
        }

        val isTransparentBlank = templateAssetPath.isBlank() &&
            android.graphics.Color.alpha(state.template.backgroundColorArgb) == 0

        val result = renderer.renderLayers(
            EditorRenderer.MultiLayerRenderRequest(
                templateAssetPath = templateAssetPath,
                templateSize = androidx.compose.ui.unit.IntSize(exportSize.width, exportSize.height),
                layers = renderableLayers(state.layers),
                backgroundColorArgb = state.template.backgroundColorArgb,
            ),
        )

        return result.fold(
            onSuccess = { bitmap ->
                try {
                    val uri = withContext(Dispatchers.IO) {
                        imageSaveRepository.saveBitmap(bitmap, transparent = isTransparentBlank).getOrNull()
                    }
                    if (uri != null) {
                        ExportOutcome.Success(uri)
                    } else {
                        ExportOutcome.Failure(context.getString(R.string.studio_error_save_image))
                    }
                } finally {
                    if (!bitmap.isRecycled) bitmap.recycle()
                }
            },
            onFailure = { e ->
                val message = if (MemoryUtil.isOutOfMemoryError(e)) {
                    context.getString(R.string.studio_error_not_enough_memory)
                } else {
                    context.getString(R.string.studio_error_render_failed, e.message ?: "")
                }
                ExportOutcome.Failure(message)
            },
        )
    }

    suspend fun saveDraft(
        state: EditorState,
        draftId: String?,
        templateAssetPath: String,
        templateThumbnailUrl: String? = null,
        cloudTemplateId: String? = null,
    ): SaveDraftOutcome = withContext(Dispatchers.IO) {
        val templateSize = state.template.originalSize
        if (templateSize.width == 0 || templateSize.height == 0) {
            return@withContext SaveDraftOutcome.Failure(IllegalStateException("Template not loaded"))
        }

        runCatching {
            val name = "Template_${System.currentTimeMillis()}"
            val thumbnailUrl = templateThumbnailUrl?.takeIf { it.isNotBlank() }
                ?: state.template.thumbnailUrl?.takeIf { it.isNotBlank() }
            val newDraftId = templateDraftRepository.saveDraft(
                draftId = draftId,
                name = name,
                state = state,
                templateAssetPath = templateAssetPath,
                templateObjectAssetPath = state.template.objectAssetPath,
                templateThumbnailUrl = thumbnailUrl,
                cloudTemplateId = cloudTemplateId,
            )
            saveDraftPreview(state, templateAssetPath, newDraftId, thumbnailUrl)
            SaveDraftOutcome.Success(newDraftId, System.currentTimeMillis())
        }.getOrElse { e -> SaveDraftOutcome.Failure(e) }
    }

    private suspend fun saveDraftPreview(
        state: EditorState,
        templateAssetPath: String,
        draftId: String,
        thumbnailUrl: String?,
    ) {
        val templateSize = state.template.originalSize
        if (templateSize.width == 0 || templateSize.height == 0) {
            saveThumbnailFromUrl(thumbnailUrl, draftId)
            return
        }

        val maxPreviewDim = 512
        val scale = minOf(
            maxPreviewDim.toFloat() / templateSize.width,
            maxPreviewDim.toFloat() / templateSize.height,
            1f,
        )
        val previewW = (templateSize.width * scale).toInt().coerceAtLeast(1)
        val previewH = (templateSize.height * scale).toInt().coerceAtLeast(1)

        if (!MemoryUtil.canAllocateExportBitmap(previewW, previewH, context)) {
            saveThumbnailFromUrl(thumbnailUrl, draftId)
            return
        }

        val isTransparentBlank = templateAssetPath.isBlank() &&
            android.graphics.Color.alpha(state.template.backgroundColorArgb) == 0

        val rendered = renderer.renderLayers(
            EditorRenderer.MultiLayerRenderRequest(
                templateAssetPath = templateAssetPath,
                templateSize = androidx.compose.ui.unit.IntSize(previewW, previewH),
                layers = renderableLayers(state.layers),
                backgroundColorArgb = state.template.backgroundColorArgb,
            ),
        ).getOrNull()

        if (rendered == null) {
            saveThumbnailFromUrl(thumbnailUrl, draftId)
            return
        }

        try {
            writePreviewBitmap(draftId, rendered, forcePng = isTransparentBlank)
        } finally {
            if (!rendered.isRecycled) rendered.recycle()
        }
    }

    private fun writePreviewBitmap(draftId: String, bitmap: Bitmap, forcePng: Boolean = false) {
        val previewFile = File(context.filesDir, "drafts/$draftId/preview.png")
        previewFile.parentFile?.mkdirs()
        previewFile.outputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, if (forcePng) 100 else 85, output)
        }
        templateDraftRepository.updateThumbnailPath(draftId, previewFile.absolutePath)
    }

    private fun saveThumbnailFromUrl(thumbnailUrl: String?, draftId: String) {
        val url = thumbnailUrl?.takeIf { it.isNotBlank() } ?: return
        try {
            context.openAssetSourceInputStream(url)?.use { input ->
                val decoded = BitmapFactory.decodeStream(input) ?: return
                try {
                    val maxDim = 512
                    val scale = minOf(
                        maxDim.toFloat() / decoded.width,
                        maxDim.toFloat() / decoded.height,
                        1f,
                    )
                    val preview = if (scale < 1f) {
                        Bitmap.createScaledBitmap(
                            decoded,
                            (decoded.width * scale).toInt().coerceAtLeast(1),
                            (decoded.height * scale).toInt().coerceAtLeast(1),
                            true,
                        )
                    } else {
                        decoded
                    }
                    writePreviewBitmap(draftId, preview)
                    if (preview !== decoded && !preview.isRecycled) {
                        preview.recycle()
                    }
                } finally {
                    if (!decoded.isRecycled) decoded.recycle()
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("EditorExportCoordinator", "Failed to save thumbnail from URL: $url", e)
        }
    }

    private fun renderableLayers(layers: List<EditorLayer>): List<EditorLayer> {
        return layers.filter { layer ->
            layer.isVectorContentLayer ||
                layer.type == LayerType.SHADOW_REGION ||
                !layer.product.foregroundUriString.isNullOrBlank()
        }
    }

    /**
     * Flatten a vector/shape layer to an IMAGE layer before user grouping.
     * Renders on a transparent full-canvas bitmap so transforms are baked in.
     */
    suspend fun rasterizeLayerForGrouping(
        layer: EditorLayer,
        templateWidth: Int,
        templateHeight: Int,
    ): EditorLayer? {
        if (layer.type == LayerType.IMAGE && !layer.product.foregroundUriString.isNullOrBlank()) {
            return layer
        }
        if (!layer.isVectorContentLayer && layer.type != LayerType.SHADOW_REGION) {
            return layer
        }
        if (templateWidth <= 0 || templateHeight <= 0) return null

        // Grouping must rasterize into the exact logical template size. Unlike
        // renderLayers(), renderLayerSubset() does not clamp/downsample output;
        // declaring a downsampled bitmap as template-sized causes the second
        // composite render to scale/crop rotated clones incorrectly.
        val rendered = renderer.renderLayerSubset(
            layers = listOf(layer),
            canvasSize = IntSize(templateWidth, templateHeight),
            backgroundColorArgb = Color.TRANSPARENT,
        ).getOrNull() ?: return null

        return try {
            val uri = withContext(Dispatchers.IO) {
                imageSaveRepository.saveBitmap(rendered, transparent = true).getOrNull()
            } ?: return null
            val uriString = uri.toString()
            layer.copy(
                type = LayerType.IMAGE,
                product = EditorProduct(
                    originalUriString = uriString,
                    foregroundUriString = uriString,
                    baseWidth = templateWidth,
                    baseHeight = templateHeight,
                    isBackgroundRemoved = true,
                ),
                shapeWidthPx = templateWidth.toFloat(),
                shapeHeightPx = templateHeight.toFloat(),
                viewport = layer.viewport.copy(
                    offsetX = 0f,
                    offsetY = 0f,
                    scale = 1f,
                    rotation = 0f,
                    flippedH = false,
                    flippedV = false,
                ),
                cropRatio = CropRatio.ORIGINAL,
            )
        } finally {
            if (!rendered.isRecycled) rendered.recycle()
        }
    }

    suspend fun rasterizeGroupComposite(
        members: List<EditorLayer>,
        templateSize: IntSize,
        orientedBounds: OrientedLayerContentBounds,
    ): String? = withContext(Dispatchers.IO) {
        if (templateSize.width <= 0 || templateSize.height <= 0) return@withContext null
        val bitmap = renderer.renderLayerSubset(
            layers = members,
            canvasSize = templateSize,
            backgroundColorArgb = Color.TRANSPARENT,
        ).getOrNull() ?: return@withContext null
        try {
            val output = cropGroupCompositeBitmap(bitmap, templateSize, orientedBounds)
            val dir = File(context.cacheDir, "group_composites")
            dir.mkdirs()
            val file = File(dir, "group_${UUID.randomUUID()}.png")
            file.outputStream().use { out ->
                output.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            if (output !== bitmap && !output.isRecycled) {
                output.recycle()
            }
            Uri.fromFile(file).toString()
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    /** Crops the rendered template bitmap to the group envelope (axis-aligned). */
    internal fun cropGroupCompositeBitmap(
        source: Bitmap,
        templateSize: IntSize,
        orientedBounds: OrientedLayerContentBounds,
    ): Bitmap {
        val cropRect = orientedBounds.bounds.toCanvasIntRect(source.width, source.height)
        val left = cropRect.left.coerceIn(0, (source.width - 1).coerceAtLeast(0))
        val top = cropRect.top.coerceIn(0, (source.height - 1).coerceAtLeast(0))
        val width = cropRect.width.coerceIn(1, source.width - left)
        val height = cropRect.height.coerceIn(1, source.height - top)
        return Bitmap.createBitmap(source, left, top, width, height)
    }
}
