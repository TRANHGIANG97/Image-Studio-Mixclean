package com.thgiang.image.admin.repository

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.gson.Gson
import com.thgiang.image.studio.ui.editor.*
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

/**
 * Handles export of editor state to JSON and ZIP bundle formats.
 * Extracted from TemplateBuilderViewModel for testability and separation of concerns.
 */
class TemplateExportManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fileRepository: TemplateFileRepository,
    private val renderer: EditorRenderer
) {

    private val gson = Gson()

    // ── Validation ──

    fun validateForExport(
        title: String,
        categoryId: String,
        template: EditorTemplate,
        layers: List<EditorLayer>
    ): List<String> {
        val exportableLayers = layers.filter { it.product.foregroundUriString != null }
        return buildList {
            if (title.isBlank()) add("Nhập tên template")
            if (categoryId.isBlank()) add("Nhập ID danh mục")
            if (!template.loaded || template.assetPath.isBlank()) add("Chọn ảnh nền")
            if (template.originalSize.width <= 0 || template.originalSize.height <= 0) add("Ảnh nền không hợp lệ")
            if (exportableLayers.isEmpty()) add("Thêm ít nhất một layer")
            if (exportableLayers.none { it.product.isSample }) add("Thêm ít nhất một vật mẫu")
            exportableLayers.forEachIndexed { index, layer ->
                val imageUrl = layer.product.foregroundUriString ?: layer.product.originalUriString
                if (imageUrl.isNullOrBlank()) add("Layer ${index + 1} thiếu ảnh")
                if (layer.viewport.scale <= 0f) add("Layer ${index + 1} có scale không hợp lệ")
            }
        }
    }

    // ── JSON Export ──

    fun exportToJson(
        title: String,
        categoryId: String,
        template: EditorTemplate,
        layers: List<EditorLayer>
    ): Result<File> {
        val validationErrors = validateForExport(title, categoryId, template, layers)
        if (validationErrors.isNotEmpty()) {
            return Result.failure(IllegalStateException(validationErrors.joinToString("\n")))
        }

        return runCatching {
            val exportableLayers = layers.filter { it.product.foregroundUriString != null }
            val cloudLayers = buildCloudLayers(exportableLayers, template.originalSize.width, template.originalSize.height)
            val cloudTemplate = buildCloudTemplate(
                templateId = "TPL_" + java.util.UUID.randomUUID().toString().take(8),
                categoryId = categoryId.trim(),
                title = title.trim(),
                template = template,
                layers = exportableLayers,
                cloudLayers = cloudLayers,
                backgroundUrl = template.assetPath
            )

            val json = gson.toJson(cloudTemplate)
            val file = File(context.getExternalFilesDir(null), "template_${System.currentTimeMillis()}.json")
            file.writeText(json)
            file
        }
    }

    // ── Bundle Export ──

    data class BundleExportResult(
        val bundleName: String,
        val zipFileName: String
    )

    suspend fun exportToBundle(
        title: String,
        categoryId: String,
        template: EditorTemplate,
        layers: List<EditorLayer>
    ): Result<BundleExportResult> {
        val validationErrors = validateForExport(title, categoryId, template, layers)
        if (validationErrors.isNotEmpty()) {
            return Result.failure(IllegalStateException(validationErrors.joinToString("\n")))
        }

        return runCatching {
            val templateId = "TPL_" + java.util.UUID.randomUUID().toString().take(8)
            val exportableLayers = layers.filter { it.product.foregroundUriString != null }
            val bundleDir = File(context.getExternalFilesDir(null), "template_bundles/$templateId")
            val assetsDir = File(bundleDir, "assets")
            val localJsonFile = File(bundleDir, "template.json")
            val portableJsonFile = File(bundleDir, "template_portable.json")
            val zipFile = File(context.getExternalFilesDir(null), "template_bundles/$templateId.zip")

            // Prepare directories
            if (bundleDir.exists()) bundleDir.deleteRecursively()
            bundleDir.mkdirs()
            assetsDir.mkdirs()

            // Copy background
            val bgExt = fileRepository.extensionFromPath(template.assetPath, "jpg")
            val backgroundFile = File(assetsDir, "background.$bgExt")
            fileRepository.copyPathToFile(template.assetPath, backgroundFile)

            // Copy layers
            val layerFiles = exportableLayers.mapIndexed { index, layer ->
                val imageUrl = layer.product.foregroundUriString ?: layer.product.originalUriString
                    ?: throw IllegalStateException("Layer ${index + 1} is missing image")
                val ext = fileRepository.extensionFromPath(imageUrl, "png")
                val layerFile = File(assetsDir, "layer_${index + 1}.$ext")
                fileRepository.copyPathToFile(imageUrl, layerFile)
                layerFile
            }

            // Generate thumbnail
            val thumbnailFile = File(assetsDir, "thumbnail.png")
            val renderResult = renderer.renderLayers(
                EditorRenderer.MultiLayerRenderRequest(
                    templateAssetPath = template.assetPath,
                    templateSize = template.originalSize,
                    layers = exportableLayers
                )
            )
            renderResult.getOrNull()?.let { bitmap ->
                val maxSide = 720
                val scale = minOf(
                    maxSide.toFloat() / bitmap.width.toFloat(),
                    maxSide.toFloat() / bitmap.height.toFloat(),
                    1f
                )
                val thumb = if (scale < 1f) {
                    Bitmap.createScaledBitmap(
                        bitmap,
                        (bitmap.width * scale).toInt().coerceAtLeast(1),
                        (bitmap.height * scale).toInt().coerceAtLeast(1),
                        true
                    )
                } else {
                    bitmap
                }
                fileRepository.writeBitmapPng(thumb, thumbnailFile)
                if (thumb !== bitmap) thumb.recycle()
            } ?: fileRepository.copyPathToFile(template.assetPath, thumbnailFile)

            // Build JSONs
            fun buildTemplateJson(portable: Boolean): com.thgiang.image.core.domain.model.template.CloudTemplate {
                val bgUrl = if (portable) "assets/${backgroundFile.name}" else Uri.fromFile(backgroundFile).toString()
                val thumbUrl = if (portable) "assets/${thumbnailFile.name}" else Uri.fromFile(thumbnailFile).toString()
                val cloudLayers = exportableLayers.mapIndexed { index, layer ->
                    val lf = layerFiles[index]
                    val imgUrl = if (portable) "assets/${lf.name}" else Uri.fromFile(lf).toString()
                    buildCloudLayer(layer, index, imgUrl, template.originalSize.width, template.originalSize.height)
                }

                return buildCloudTemplate(
                    templateId = templateId,
                    categoryId = categoryId.trim(),
                    title = title.trim(),
                    template = template,
                    layers = exportableLayers,
                    cloudLayers = cloudLayers,
                    backgroundUrl = bgUrl,
                    thumbnailUrl = thumbUrl
                )
            }

            localJsonFile.writeText(gson.toJson(buildTemplateJson(portable = false)))
            portableJsonFile.writeText(gson.toJson(buildTemplateJson(portable = true)))
            File(bundleDir, "README.txt").writeText(
                "Use template.json on this device. Use template_portable.json with the bundled assets folder when importing elsewhere."
            )

            fileRepository.zipDirectory(bundleDir, zipFile)

            BundleExportResult(
                bundleName = bundleDir.name,
                zipFileName = zipFile.name
            )
        }
    }

    // ── Private helpers ──

    private fun buildCloudLayers(
        layers: List<EditorLayer>,
        templateWidth: Int,
        templateHeight: Int
    ): List<com.thgiang.image.core.domain.model.template.CloudLayer> {
        return layers.mapIndexed { index, layer ->
            val imageUrl = layer.product.foregroundUriString ?: layer.product.originalUriString
            buildCloudLayer(layer, index, imageUrl ?: "", templateWidth, templateHeight)
        }
    }

    private fun buildCloudLayer(
        layer: EditorLayer,
        index: Int,
        imageUrl: String,
        templateWidth: Int,
        templateHeight: Int
    ): com.thgiang.image.core.domain.model.template.CloudLayer {
        return com.thgiang.image.core.domain.model.template.CloudLayer(
            layerId = layer.id,
            type = if (layer.product.isSample) "PLACEHOLDER_OBJECT" else "DECORATION",
            zIndex = index,
            transform = com.thgiang.image.core.domain.model.template.CloudTransform(
                anchorX = 0.5f + (layer.viewport.offset.x / templateWidth.toFloat()),
                anchorY = 0.5f + (layer.viewport.offset.y / templateHeight.toFloat()),
                scale = layer.viewport.scale,
                rotation = layer.viewport.rotation
            ),
            payload = com.thgiang.image.core.domain.model.template.CloudPayload(
                defaultImageUrl = layer.product.originalUriString,
                imageUrl = imageUrl,
                shadowIntensity = layer.appearance.shadowIntensity,
                shadowAngle = layer.appearance.shadowAngle,
                shadowDistance = layer.appearance.shadowDistance,
                alpha = layer.appearance.alpha,
                shadowColorArgb = layer.appearance.shadowColorArgb,
                cropRatio = layer.cropRatio.name,
                flippedH = layer.viewport.flippedH,
                flippedV = layer.viewport.flippedV,
                baseWidth = layer.product.baseWidth,
                baseHeight = layer.product.baseHeight
            )
        )
    }

    private fun buildCloudTemplate(
        templateId: String,
        categoryId: String,
        title: String,
        template: EditorTemplate,
        layers: List<EditorLayer>,
        cloudLayers: List<com.thgiang.image.core.domain.model.template.CloudLayer>,
        backgroundUrl: String,
        thumbnailUrl: String? = null
    ): com.thgiang.image.core.domain.model.template.CloudTemplate {
        return com.thgiang.image.core.domain.model.template.CloudTemplate(
            templateId = templateId,
            categoryId = categoryId,
            metadata = com.thgiang.image.core.domain.model.template.TemplateMetadata(
                title = title,
                thumbnailUrl = thumbnailUrl ?: backgroundUrl,
                updatedAt = System.currentTimeMillis()
            ),
            canvas = com.thgiang.image.core.domain.model.template.TemplateCanvas(
                baseWidth = template.originalSize.width,
                baseHeight = template.originalSize.height,
                aspectRatio = reducedAspectRatio(template.originalSize.width, template.originalSize.height),
                backgroundUrl = backgroundUrl
            ),
            layers = cloudLayers
        )
    }

    private fun reducedAspectRatio(width: Int, height: Int): String {
        fun gcd(a: Int, b: Int): Int {
            return if (b == 0) kotlin.math.abs(a) else gcd(b, a % b)
        }
        val divisor = gcd(width, height).coerceAtLeast(1)
        return "${width / divisor}:${height / divisor}"
    }
}
