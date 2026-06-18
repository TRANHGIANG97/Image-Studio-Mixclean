package com.thgiang.image.studio.ui.editor

import android.net.Uri
import com.thgiang.image.core.domain.model.template.CloudGradient
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize

// ============ Core Data Models v2 ============

/**
 * EditorState v2 - Tổng hợp trạng thái editor, immutable
 */
data class EditorState(
    val template: EditorTemplate = EditorTemplate(),
    val layers: List<EditorLayer> = emptyList(),
    val selectedLayerId: String? = null,
    val selectedTool: EditorTool? = null,
    val isExporting: Boolean = false,
    val isSavingDraft: Boolean = false,
    val exportResult: android.net.Uri? = null,
    val draftSavedAt: Long? = null,
    val errorMessage: String? = null,
    val showOverlay: Boolean = false,
    val showBoundingBox: Boolean = false
) : java.io.Serializable {
    val canExport: Boolean
        get() = layers.any { it.product.isBackgroundRemoved } && !isExporting && template.loaded
}

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
    val shadowColorArgb: Int = 0xFF000000.toInt(),
    /** When set, overrides [shadowBlurRadiusFromIntensity] (from admin_web shadowBlur). */
    val shadowBlur: Float? = null,
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

fun EditorAppearance.resolvedShadowBlurRadius(): Float =
    shadowBlur?.coerceAtLeast(0f) ?: shadowBlurRadiusFromIntensity(shadowIntensity)

fun shadowOffset(angle: Float, distance: Float): Pair<Float, Float> {
    val rad = Math.toRadians(angle.toDouble())
    return (distance * kotlin.math.cos(rad)).toFloat() to (distance * kotlin.math.sin(rad)).toFloat()
}

data class EditorTemplate(
    val assetPath: String = "",
    val originalWidth: Int = 0,
    val originalHeight: Int = 0,
    val backgroundColorArgb: Int = 0xFFFFFFFF.toInt(),
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
    val processing: Boolean = false,
    val isSample: Boolean = false
) : java.io.Serializable {
    val originalUri: Uri? get() = originalUriString?.let { Uri.parse(it) }
    val foregroundUri: Uri? get() = foregroundUriString?.let { Uri.parse(it) }
    val baseSize: IntSize get() = IntSize(baseWidth, baseHeight)
    
    constructor() : this(null, null, false, 0, 0, false, false)
}

// ============ Shape & Text Layer Types ============

enum class LayerType : java.io.Serializable {
    IMAGE, SHAPE_TEXT, SHADOW_REGION
}

enum class ShapeType : java.io.Serializable {
    /** Fully rounded ends (stadium shape) */
    PILL,
    /** Standard rectangle with slightly rounded corners */
    CARD,
    /** Teardrop / speech bubble — 3 rounded corners + 1 sharp pointer at bottom-left */
    TEARDROP,
    /** Perfect circle */
    CIRCLE,
    /** 5-pointed star */
    STAR,
    /** Flat-topped regular hexagon */
    HEXAGON,
    /** Equilateral triangle pointing up */
    TRIANGLE,
    /** Horizontal line (stroke only) */
    LINE,
    /** Rhombus / diamond */
    DIAMOND,
    /** Arrow from Fabric path */
    ARROW,
    /** Arbitrary SVG path */
    PATH,
    /** Polygon from exported point list */
    POLYGON,
}

// ============ Core Layer ============

data class EditorLayer(
    val id: String = java.util.UUID.randomUUID().toString(),
    val type: LayerType = LayerType.IMAGE,

    // ── IMAGE layer properties ──────────────────────────────
    val product: EditorProduct = EditorProduct(),
    val cropRatio: CropRatio = CropRatio.ORIGINAL,

    // ── SHAPE_TEXT layer properties ─────────────────────────
    /** Displayed text content */
    val text: String = "Label",
    /** Text color as ARGB int */
    val textColorArgb: Int = 0xFFFFFFFF.toInt(),
    /** Text size in SP */
    val textSizeSp: Float = 16f,
    /** Shape type (Pill, Card, Teardrop) */
    val shapeType: ShapeType = ShapeType.PILL,
    /** Fill color of the shape as ARGB int */
    val shapeColorArgb: Int = 0xFFE53935.toInt(),
    /** Logical width of the shape in template-pixels (before scale) */
    val shapeWidthPx: Float = 240f,
    /** Logical height of the shape in template-pixels (before scale) */
    val shapeHeightPx: Float = 100f,
    /** Custom font family name */
    val fontFamily: String? = null,
    val fontWeight: String? = null,
    val fontStyle: String? = null,
    val textAlign: String? = null,
    val underline: Boolean = false,
    val linethrough: Boolean = false,
    val lineHeight: Float? = null,
    val charSpacing: Float = 0f,
    val textBackgroundColorArgb: Int? = null,
    val textTransform: String? = null,
    /** Corner radius in template pixels (from cloud rx/ry) */
    val cornerRadiusX: Float? = null,
    val cornerRadiusY: Float? = null,
    val blendMode: String? = null,
    val strokeColorArgb: Int? = null,
    val strokeWidthPx: Float = 0f,
    val strokeDashArray: List<Float> = emptyList(),
    val fillGradient: CloudGradient? = null,
    val textColorGradient: CloudGradient? = null,
    val pathData: String? = null,
    val polygonPoints: List<Float> = emptyList(),

    // ── Common transform & appearance ───────────────────────
    val viewport: EditorViewport = EditorViewport(),
    val appearance: EditorAppearance = EditorAppearance(),
    val isLocked: Boolean = false
) : java.io.Serializable

val EditorLayer.isShadowRegion: Boolean
    get() = type == LayerType.SHADOW_REGION

// ============ Tool & Config ============

sealed class EditorTool(val iconName: String) : java.io.Serializable {
    data object Replace : EditorTool("photo")
    data object Sticker : EditorTool("sticker")
    data object Label : EditorTool("label")
    data object Layout : EditorTool("drag_indicator")
    data object Rotate : EditorTool("refresh")
    data object Shadow : EditorTool("wb_sunny")
    data object Transparency : EditorTool("opacity")
    data object Crop : EditorTool("crop_square")
    data object Duplicate : EditorTool("content_copy")
    data object Delete : EditorTool("delete")
    
    companion object {
        val ALL = listOf(Replace, Sticker, Label, Rotate, Shadow, Transparency, Crop, Duplicate, Delete)
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
    val layers: List<EditorLayer>
) : java.io.Serializable {
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
            // Shape-text specific
            if (a.type == LayerType.SHAPE_TEXT) {
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
