package com.thgiang.image.studio.ui.editor.export
import com.thgiang.image.studio.ui.editor.*

import android.content.Context
import com.thgiang.image.studio.ui.editor.model.*
import android.net.Uri
import com.thgiang.image.core.data.save.ImageSaveRepository
import com.thgiang.image.studio.R
import com.thgiang.image.studio.data.TemplateDraftRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

        val result = renderer.renderLayers(
            EditorRenderer.MultiLayerRenderRequest(
                templateAssetPath = templateAssetPath,
                templateSize = templateSize,
                layers = state.layers.filter {
                    it.isVectorContentLayer ||
                        it.type == LayerType.SHADOW_REGION ||
                        it.product.foregroundUri != null
                },
                backgroundColorArgb = state.template.backgroundColorArgb,
            ),
        )

        return result.fold(
            onSuccess = { bitmap ->
                val uri = withContext(Dispatchers.IO) {
                    imageSaveRepository.saveBitmap(bitmap).getOrNull()
                }
                if (uri != null) {
                    ExportOutcome.Success(uri)
                } else {
                    ExportOutcome.Failure(context.getString(R.string.studio_error_save_image))
                }
            },
            onFailure = { e ->
                ExportOutcome.Failure(
                    context.getString(R.string.studio_error_render_failed, e.message ?: ""),
                )
            },
        )
    }

    suspend fun saveDraft(
        state: EditorState,
        draftId: String?,
        templateAssetPath: String,
    ): SaveDraftOutcome = withContext(Dispatchers.IO) {
        val templateSize = state.template.originalSize
        if (templateSize.width == 0 || templateSize.height == 0) {
            return@withContext SaveDraftOutcome.Failure(IllegalStateException("Template not loaded"))
        }

        runCatching {
            val name = "Template_${System.currentTimeMillis()}"
            val newDraftId = templateDraftRepository.saveDraft(
                draftId = draftId,
                name = name,
                state = state,
                templateAssetPath = templateAssetPath,
                templateObjectAssetPath = null,
            )
            SaveDraftOutcome.Success(newDraftId, System.currentTimeMillis())
        }.getOrElse { e -> SaveDraftOutcome.Failure(e) }
    }
}
