package com.thgiang.image.studio.ui.editor.load

import android.content.Context
import android.graphics.BitmapFactory
import com.thgiang.image.core.domain.model.template.CloudTemplate
import com.thgiang.image.studio.data.CloudTemplateRemoteRepository
import com.thgiang.image.studio.ui.editor.CloudLayerToEditorMapper
import com.thgiang.image.studio.ui.editor.EditorLayer
import com.thgiang.image.studio.ui.editor.EditorTemplate
import com.thgiang.image.studio.ui.editor.LayerType
import com.thgiang.image.studio.util.FontDownloader
import com.thgiang.image.studio.util.openAssetSourceInputStream
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class LoadedEditorTemplate(
    val template: EditorTemplate,
    val layers: List<EditorLayer> = emptyList(),
    val selectedLayerId: String? = null,
)

@Singleton
class EditorTemplateLoader @Inject constructor(
    private val cloudTemplateRepository: CloudTemplateRemoteRepository,
    @ApplicationContext private val context: Context,
) {
    suspend fun fetchCloudTemplate(templateId: String): CloudTemplate = withContext(Dispatchers.IO) {
        cloudTemplateRepository.fetchTemplateById(templateId)
    }

    suspend fun fetchAndBuild(templateId: String): LoadedEditorTemplate {
        val cloudTemplate = fetchCloudTemplate(templateId)
        return buildFromCloud(cloudTemplate)
    }

    suspend fun buildFromCloud(cloudTemplate: CloudTemplate): LoadedEditorTemplate {
        val backgroundUrl = cloudTemplate.canvas.backgroundUrl.orEmpty()
        val density = context.resources.displayMetrics.scaledDensity.coerceAtLeast(1f)
        val editorLayers = CloudLayerToEditorMapper.mapLayers(cloudTemplate, density)

        FontDownloader.preloadTemplateFonts(
            context = context,
            families = editorLayers
                .filter { it.type == LayerType.SHAPE_TEXT }
                .map { it.fontFamily },
        )

        return LoadedEditorTemplate(
            template = EditorTemplate(
                assetPath = backgroundUrl,
                originalWidth = cloudTemplate.canvas.baseWidth,
                originalHeight = cloudTemplate.canvas.baseHeight,
                backgroundColorArgb = cloudTemplate.canvas.backgroundColorArgb ?: 0xFFFFFFFF.toInt(),
                loaded = true,
            ),
            layers = editorLayers,
            selectedLayerId = editorLayers.firstOrNull { layer -> layer.product.isSample }?.id,
        )
    }

    suspend fun probeLocalAsset(assetPath: String): LoadedEditorTemplate = withContext(Dispatchers.IO) {
        val input = context.openAssetSourceInputStream(assetPath)
            ?: throw IllegalStateException("Failed to open stream for $assetPath")

        input.use { stream ->
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(stream, null, opts)
            if (opts.outWidth <= 0 || opts.outHeight <= 0) {
                throw IllegalStateException("Invalid image bounds for $assetPath")
            }

            LoadedEditorTemplate(
                template = EditorTemplate(
                    assetPath = assetPath,
                    originalWidth = opts.outWidth,
                    originalHeight = opts.outHeight,
                    loaded = true,
                ),
            )
        }
    }

    fun openLocalAssetStream(path: String) = context.openAssetSourceInputStream(path)
}
