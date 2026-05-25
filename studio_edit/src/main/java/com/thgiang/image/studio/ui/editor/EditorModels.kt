package com.thgiang.image.studio.ui.editor

import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize

// ============ Core Data Models v2 ============

/**
 * Immutable viewport với validation và helper methods
 */
data class EditorViewport(
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val flippedH: Boolean = false,
    val flippedV: Boolean = false
) : java.io.Serializable {
    val offset: Offset get() = Offset(offsetX, offsetY)

    init {
        require(scale > 0) { "Scale must be positive" }
    }
    
    companion object {
        val IDENTITY = EditorViewport()
    }
    
    fun isIdentity(): Boolean = this == IDENTITY
    
    fun withScale(newScale: Float): EditorViewport = copy(scale = newScale.coerceIn(0.1f, 5f))
    
    fun withOffset(newOffset: Offset): EditorViewport = copy(offsetX = newOffset.x, offsetY = newOffset.y)
    
    fun withRotation(newRotation: Float): EditorViewport {
        var normalized = newRotation % 360f
        if (normalized < 0) normalized += 360f
        return copy(rotation = normalized)
    }
}

data class EditorAppearance(
    val shadowIntensity: Float = 0.3f,
    val alpha: Float = 1f,
    val shadowAngle: Float = 45f,
    val shadowDistance: Float = 12f,
    val shadowColorArgb: Int = 0xFF000000.toInt()
) : java.io.Serializable {
    init {
        require(shadowIntensity in 0f..1f) { "Shadow intensity must be in 0..1" }
        require(alpha in 0f..1f) { "Alpha must be in 0..1" }
    }
}

fun shadowOpacityFromIntensity(intensity: Float): Float {
    return (0.10f + (intensity.coerceIn(0f, 1f) * 0.80f)).coerceIn(0.10f, 0.90f)
}

fun shadowBlurRadiusFromIntensity(intensity: Float): Float {
    return (18f - (intensity.coerceIn(0f, 1f) * 12f)).coerceAtLeast(4f)
}

data class EditorTemplate(
    val assetPath: String = "",
    val originalWidth: Int = 0,
    val originalHeight: Int = 0,
    val loaded: Boolean = false
) : java.io.Serializable {
    val originalSize: IntSize get() = IntSize(originalWidth, originalHeight)
}

data class EditorProduct(
    val originalUriString: String? = null,
    val foregroundUriString: String? = null,
    val isBackgroundRemoved: Boolean = false,
    val baseWidth: Int = 0,
    val baseHeight: Int = 0,
    val processing: Boolean = false
) : java.io.Serializable {
    val originalUri: Uri? get() = originalUriString?.let { Uri.parse(it) }
    val foregroundUri: Uri? get() = foregroundUriString?.let { Uri.parse(it) }
    val baseSize: IntSize get() = IntSize(baseWidth, baseHeight)
    
    constructor() : this(null, null, false, 0, 0, false)
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
        val ALL = listOf(Replace, Rotate, Shadow, Transparency, Crop)
    }
}

enum class CropRatio(
    val label: String, 
    val widthRatio: Float, 
    val heightRatio: Float
) : java.io.Serializable {
    ORIGINAL("Gốc", 0f, 0f),
    RATIO_1_1("1:1", 1f, 1f),
    RATIO_3_4("3:4", 3f, 4f),
    RATIO_4_3("4:3", 4f, 3f),
    RATIO_9_16("9:16", 9f, 16f),
    RATIO_16_9("16:9", 16f, 9f);
    
    val aspectRatio: Float get() = if (this == ORIGINAL) 1f else widthRatio / heightRatio
    
    fun calculateSize(maxWidth: Float, maxHeight: Float): IntSize {
        if (this == ORIGINAL) {
            return IntSize(maxWidth.toInt(), maxHeight.toInt())
        }
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
            return entries.filter { it != ORIGINAL }.minByOrNull { kotlin.math.abs(it.aspectRatio - ratio) } ?: RATIO_1_1
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

