package com.thgiang.image.studio.ui.editor

import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize

// ============ Core Data Models v2 ============

/**
 * Immutable viewport với validation và helper methods
 */
data class EditorViewport(
    val offset: Offset = Offset.Zero,
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val flippedH: Boolean = false,
    val flippedV: Boolean = false
) : java.io.Serializable {
    init {
        require(scale > 0) { "Scale must be positive" }
    }
    
    companion object {
        val IDENTITY = EditorViewport()
    }
    
    fun isIdentity(): Boolean = this == IDENTITY
    
    fun withScale(newScale: Float): EditorViewport = copy(scale = newScale.coerceIn(0.1f, 5f))
    
    fun withOffset(newOffset: Offset): EditorViewport = copy(offset = newOffset)
    
    fun withRotation(newRotation: Float): EditorViewport {
        var normalized = newRotation % 360f
        if (normalized < 0) normalized += 360f
        return copy(rotation = normalized)
    }
}

data class EditorAppearance(
    val shadowIntensity: Float = 0.3f,
    val alpha: Float = 1f
) : java.io.Serializable {
    init {
        require(shadowIntensity in 0f..1f) { "Shadow intensity must be in 0..1" }
        require(alpha in 0f..1f) { "Alpha must be in 0..1" }
    }
}

data class EditorTemplate(
    val assetPath: String = "",
    val originalSize: IntSize = IntSize.Zero,
    val loaded: Boolean = false
) : java.io.Serializable

data class EditorProduct(
    val originalUri: Uri? = null,
    val foregroundUri: Uri? = null,
    val isBackgroundRemoved: Boolean = false,
    val baseSize: IntSize = IntSize.Zero,
    val processing: Boolean = false
) : java.io.Serializable {
    constructor() : this(null, null, false, IntSize.Zero, false)
}

// ============ Tool & Config ============

sealed class EditorTool(val iconName: String) : java.io.Serializable {
    data object Replace : EditorTool("photo")
    data object Layout : EditorTool("drag_indicator")
    data object Rotate : EditorTool("refresh")
    data object Shadow : EditorTool("wb_sunny")
    data object Transparency : EditorTool("opacity")
    data object Crop : EditorTool("crop_square")
    
    companion object {
        val ALL = listOf(Replace, Layout, Rotate, Shadow, Transparency, Crop)
    }
}

enum class CropRatio(
    val label: String, 
    val widthRatio: Float, 
    val heightRatio: Float
) : java.io.Serializable {
    RATIO_1_1("1:1", 1f, 1f),
    RATIO_3_4("3:4", 3f, 4f),
    RATIO_4_3("4:3", 4f, 3f),
    RATIO_9_16("9:16", 9f, 16f),
    RATIO_16_9("16:9", 16f, 9f);
    
    val aspectRatio: Float get() = widthRatio / heightRatio
    
    fun calculateSize(maxWidth: Float, maxHeight: Float): IntSize {
        val targetAspect = aspectRatio
        val containerAspect = maxWidth / maxHeight
        
        return if (targetAspect > containerAspect) {
            val w = maxWidth.toInt()
            val h = (w / targetAspect).toInt()
            IntSize(w, h)
        } else {
            val h = maxHeight.toInt()
            val w = (h * targetAspect).toInt()
            IntSize(w, h)
        }
    }
    
    companion object {
        fun fromAspectRatio(ratio: Float): CropRatio {
            return entries.minByOrNull { kotlin.math.abs(it.aspectRatio - ratio) } ?: RATIO_1_1
        }
    }
}

// ============ History ============

data class TransformSnapshot(
    val viewport: EditorViewport,
    val appearance: EditorAppearance,
    val cropRatio: CropRatio
) : java.io.Serializable {
    /**
     * Check if this snapshot is visually equivalent to another
     * (allows small floating point differences)
     */
    fun isEquivalent(other: TransformSnapshot, epsilon: Float = 0.01f): Boolean {
        return viewport.scale == other.viewport.scale &&
               kotlin.math.abs(viewport.offset.x - other.viewport.offset.x) < epsilon &&
               kotlin.math.abs(viewport.offset.y - other.viewport.offset.y) < epsilon &&
               kotlin.math.abs(viewport.rotation - other.viewport.rotation) < epsilon &&
               viewport.flippedH == other.viewport.flippedH &&
               viewport.flippedV == other.viewport.flippedV &&
               kotlin.math.abs(appearance.shadowIntensity - other.appearance.shadowIntensity) < epsilon &&
               kotlin.math.abs(appearance.alpha - other.appearance.alpha) < epsilon &&
               cropRatio == other.cropRatio
    }
}

