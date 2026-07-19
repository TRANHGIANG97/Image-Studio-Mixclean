package com.thgiang.image.studio.ui.editor.model

data class TransformSnapshot(
    val layers: List<EditorLayer>,
    val userGroups: Map<String, EditorUserGroup> = emptyMap(),
    val userGroupBundles: Map<String, UserGroupBundle> = emptyMap(),
) : java.io.Serializable {
    val userGroupMaps: UserGroupMaps
        get() = UserGroupMaps(groups = userGroups, bundles = userGroupBundles)
    /**
     * Check if this snapshot is visually equivalent to another
     * (allows small floating point differences)
     */
    fun isEquivalent(other: TransformSnapshot, epsilon: Float = 0.01f): Boolean {
        if (layers.size != other.layers.size) return false
        for (i in layers.indices) {
            val a = layers[i]
            val b = other.layers[i]
            if (a.id != b.id) return false
            if (a.type != b.type) return false
            if (a.cropRatio != b.cropRatio) return false
            if (a.viewport.scale != b.viewport.scale) return false
            if (kotlin.math.abs(a.viewport.offset.x - b.viewport.offset.x) >= epsilon) return false
            if (kotlin.math.abs(a.viewport.offset.y - b.viewport.offset.y) >= epsilon) return false
            if (kotlin.math.abs(a.viewport.rotation - b.viewport.rotation) >= epsilon) return false
            if (a.viewport.flippedH != b.viewport.flippedH) return false
            if (a.viewport.flippedV != b.viewport.flippedV) return false
            if (kotlin.math.abs(a.appearance.shadowIntensity - b.appearance.shadowIntensity) >= epsilon) return false
            if (kotlin.math.abs(a.appearance.alpha - b.appearance.alpha) >= epsilon) return false
            if (a.isLocked != b.isLocked) return false
            if (a.isVisible != b.isVisible) return false
            // Shape-text specific
            if (a.isVectorContentLayer) {
                if (a.text != b.text) return false
                if (a.textColorArgb != b.textColorArgb) return false
                if (kotlin.math.abs(a.textSizeSp - b.textSizeSp) >= epsilon) return false
                if (a.fontFamily != b.fontFamily) return false
                if (a.shapeType != b.shapeType) return false
                if (a.shapeColorArgb != b.shapeColorArgb) return false
            }
        }
        return true
    }
}
