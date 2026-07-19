package com.thgiang.image.studio.ui.editor.load
import com.thgiang.image.studio.ui.editor.*
import com.thgiang.image.studio.ui.editor.mapper.*

import android.content.Context
import com.thgiang.image.studio.ui.editor.model.*
import com.thgiang.image.studio.ui.editor.model.EditorLayerNormalizer
import android.graphics.BitmapFactory
import com.thgiang.image.core.domain.logging.AppLogger
import com.thgiang.image.core.domain.model.template.CloudTemplate
import com.thgiang.image.studio.data.CloudTemplateRemoteRepository
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
    private val logger: AppLogger,
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
        val editorLayers = mapCloudLayers(cloudTemplate)
        return LoadedEditorTemplate(
            template = cloudTemplateShell(cloudTemplate).copy(loaded = true),
            layers = editorLayers,
            selectedLayerId = editorLayers.firstOrNull { layer -> layer.product.isSample }?.id,
        )
    }

    /** Canvas metadata only — lets the editor paint background while layers/fonts load. */
    fun cloudTemplateShell(cloudTemplate: CloudTemplate): EditorTemplate {
        val backgroundUrl = cloudTemplate.canvas.backgroundUrl.orEmpty()
        val thumbnailUrl = cloudTemplate.metadata.thumbnailUrl
            .takeIf { it.isNotBlank() }
            ?: backgroundUrl.takeIf { it.isNotBlank() }

        return EditorTemplate(
            assetPath = backgroundUrl,
            originalWidth = cloudTemplate.canvas.baseWidth,
            originalHeight = cloudTemplate.canvas.baseHeight,
            backgroundColorArgb = cloudTemplate.canvas.backgroundColorArgb ?: 0xFFFFFFFF.toInt(),
            loaded = false,
            thumbnailUrl = thumbnailUrl,
        )
    }

    suspend fun mapCloudLayers(cloudTemplate: CloudTemplate): List<EditorLayer> {
        val density = context.resources.displayMetrics.scaledDensity.coerceAtLeast(1f)
        val editorLayers = EditorLayerNormalizer.normalize(
            CloudLayerToEditorMapper.mapLayers(cloudTemplate, density, logger),
        )

        if (editorLayers.isEmpty()) {
            logger.logEvent(
                "template_render_empty",
                mapOf("templateId" to cloudTemplate.templateId),
            )
        }

        FontDownloader.preloadTemplateFonts(
            context = context,
            families = editorLayers
                .filter { it.isLabelLayer }
                .map { it.fontFamily },
        )

        return editorLayers
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
