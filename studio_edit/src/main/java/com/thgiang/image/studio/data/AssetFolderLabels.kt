package com.thgiang.image.studio.data

/**
 * Tab labels derived from Media Library folder slugs.
 * Examples: materials_icon → "materials Icon", backgrounds_ecommerce → "backgrounds Ecommerce"
 */
object AssetFolderLabels {

    fun materialsTabLabel(folder: String): String =
        prefixTabLabel(folder, prefix = "materials_", displayPrefix = "materials")

    fun backgroundTabLabel(folder: String): String =
        prefixTabLabel(folder, prefix = "backgrounds_", displayPrefix = "backgrounds")

    fun isMaterialsStickerFolder(folder: String): Boolean {
        val normalized = folder.trim().lowercase()
        if (!normalized.startsWith("materials_")) return false
        if (normalized == "backgrounds" || normalized.startsWith("backgrounds_")) return false
        val suffix = normalized.removePrefix("materials_")
        if (suffix == "backgrounds" || suffix.startsWith("backgrounds_")) return false
        return true
    }

    /** Mobile background tabs — backgrounds_* only (no root "backgrounds" / "Tất cả"). */
    fun isBackgroundTabFolder(folder: String): Boolean {
        val normalized = folder.trim().lowercase()
        return normalized.startsWith("backgrounds_")
    }

    private fun prefixTabLabel(folder: String, prefix: String, displayPrefix: String): String {
        val normalized = folder.trim().lowercase()
        if (!normalized.startsWith(prefix)) return normalized
        val suffix = normalized.removePrefix(prefix)
        if (suffix.isBlank()) return displayPrefix
        val titled = suffix
            .split('_', '-')
            .filter { it.isNotBlank() }
            .joinToString(" ") { part ->
                part.replaceFirstChar { char -> char.titlecase() }
            }
        return "$displayPrefix $titled"
    }
}
