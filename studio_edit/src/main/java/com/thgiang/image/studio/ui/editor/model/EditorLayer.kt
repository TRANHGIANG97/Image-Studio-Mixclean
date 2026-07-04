package com.thgiang.image.studio.ui.editor.model

import com.thgiang.image.core.domain.model.template.CloudGradient
import androidx.compose.ui.unit.IntSize

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
    val shapeColorArgb: Int = 0x00FFFFFF,
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
    /** Word-style text path / warp transform (Follow Path + Warp). */
    val textForm: TextFormEffect = TextFormEffect(),
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

    /** Shared id for frame + label siblings (Phase 3). */
    val groupId: String? = null,
    /** Role inside a [groupId] composite. */
    val groupRole: LayerGroupRole? = null,

    // ── Common transform & appearance ───────────────────────
    val viewport: EditorViewport = EditorViewport(),
    val appearance: EditorAppearance = EditorAppearance(),
    val isLocked: Boolean = false,
    val isVisible: Boolean = true
) : java.io.Serializable

val EditorLayer.isShadowRegion: Boolean
    get() = type == LayerType.SHADOW_REGION

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
