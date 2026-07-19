package com.thgiang.image.studio.ui.editor.export
import com.thgiang.image.studio.ui.editor.*

import android.content.Context
import com.thgiang.image.studio.ui.editor.model.*
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
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
                        imageSaveRepository.saveBitmap(bitmap).getOrNull()
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
            writePreviewBitmap(draftId, rendered)
        } finally {
            if (!rendered.isRecycled) rendered.recycle()
        }
    }

    private fun writePreviewBitmap(draftId: String, bitmap: Bitmap) {
        val previewFile = File(context.filesDir, "drafts/$draftId/preview.png")
        previewFile.parentFile?.mkdirs()
        previewFile.outputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 85, output)
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
}
