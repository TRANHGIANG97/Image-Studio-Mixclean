package com.thgiang.image.admin.util

import android.net.Uri
import com.thgiang.image.core.domain.model.template.CloudTemplate
import java.io.File

/**
 * Utility for template validation and import normalization.
 * Extracted from AdminDashboardScreen to enable unit testing.
 */
object TemplateValidator {

    /**
     * Resolves a relative path to an absolute [File] relative to the json file location.
     * Returns null for content://, http(s)://, or blank paths.
     */
    fun resolveTemplatePath(path: String?, jsonFile: File): File? {
        if (path.isNullOrBlank()) return null
        return when {
            path.startsWith("file://") -> Uri.parse(path).path?.let(::File)
            path.startsWith("content://") || path.startsWith("http://") || path.startsWith("https://") -> null
            else -> File(jsonFile.parentFile, path)
        }
    }

    /**
     * Validates a [CloudTemplate] and returns a list of issue descriptions (empty = valid).
     */
    fun validateTemplate(template: CloudTemplate, jsonFile: File): List<String> {
        return buildList {
            if (template.templateId.isBlank()) add("Missing templateId")
            if (template.categoryId.isBlank()) add("Missing category")
            if (template.metadata.title.isBlank()) add("Missing title")
            if (template.canvas.baseWidth <= 0 || template.canvas.baseHeight <= 0) add("Invalid canvas size")
            val backgroundFile = resolveTemplatePath(template.canvas.backgroundUrl, jsonFile)
            if (template.canvas.backgroundUrl.isNullOrBlank()) add("Missing background")
            if (backgroundFile != null && !backgroundFile.exists()) add("Background file missing")
            if (template.layers.isEmpty()) add("No layers")
            if (template.layers.none { it.type == "PLACEHOLDER_OBJECT" }) add("No placeholder object")
            template.layers.forEachIndexed { index, layer ->
                val imagePath = layer.payload.imageUrl ?: layer.payload.defaultImageUrl
                val imageFile = resolveTemplatePath(imagePath, jsonFile)
                if (imagePath.isNullOrBlank()) add("Layer ${index + 1} missing image")
                if (imageFile != null && !imageFile.exists()) add("Layer ${index + 1} file missing")
                if (layer.transform.scale <= 0f) add("Layer ${index + 1} invalid scale")
            }
        }
    }

    /**
     * Normalizes a path for local storage: converts relative paths to file:// URIs,
     * while keeping absolute URIs unchanged.
     */
    fun normalizePath(path: String?, bundleDir: File): String? {
        if (path.isNullOrBlank()) return path
        return when {
            path.startsWith("file://") || path.startsWith("content://") ||
            path.startsWith("http://") || path.startsWith("https://") -> path
            else -> Uri.fromFile(File(bundleDir, path)).toString()
        }
    }

    /**
     * Normalizes an imported [CloudTemplate] so all relative paths become absolute [Uri] strings
     * pointing to files inside [bundleDir].
     */
    fun normalizeImportedTemplate(template: CloudTemplate, bundleDir: File): CloudTemplate {
        return template.copy(
            metadata = template.metadata.copy(
                thumbnailUrl = normalizePath(template.metadata.thumbnailUrl, bundleDir).orEmpty()
            ),
            canvas = template.canvas.copy(
                backgroundUrl = normalizePath(template.canvas.backgroundUrl, bundleDir)
            ),
            layers = template.layers.map { layer ->
                val imageUrl = normalizePath(
                    layer.payload.imageUrl ?: layer.payload.defaultImageUrl, bundleDir
                )
                layer.copy(
                    payload = layer.payload.copy(
                        defaultImageUrl = imageUrl,
                        imageUrl = imageUrl
                    )
                )
            }
        )
    }
}
