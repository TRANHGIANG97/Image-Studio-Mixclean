package com.thgiang.image.studio.ui.editor.label.panel

import androidx.annotation.StringRes
import com.thgiang.image.core.domain.model.template.CloudGradient
import com.thgiang.image.core.domain.model.template.CloudGradientCoords
import com.thgiang.image.core.domain.model.template.CloudGradientStop
import com.thgiang.image.studio.R
import com.thgiang.image.studio.ui.editor.label.model.ShapeLabelDefaults
import com.thgiang.image.studio.ui.editor.model.EditorLayer
import com.thgiang.image.studio.ui.editor.model.ElevationTarget
import com.thgiang.image.studio.ui.editor.model.ShapeElevationStyle
import com.thgiang.image.studio.ui.editor.model.TextFormEffect
import com.thgiang.image.studio.ui.editor.model.TextFormPreset
import com.thgiang.image.studio.ui.editor.model.TextRunOps
import com.thgiang.image.studio.ui.editor.model.TextSpanStylePatch
import com.thgiang.image.studio.ui.editor.model.withPreset
import com.thgiang.image.studio.ui.editor.model.withTextSpans
import kotlin.math.abs

/**
 * One-tap text style bundle: fill, text color, typography, shadow, elevation, and artistic form.
 * Does not include [EditorLayer.text], size, or box dimensions.
 */
data class TextStyleTemplate(
    val id: String,
    @StringRes val nameRes: Int,
    val textColorArgb: Int,
    val textColorGradient: CloudGradient? = null,
    val shapeColorArgb: Int = ShapeLabelDefaults.TRANSPARENT_FILL_ARGB,
    val fillGradient: CloudGradient? = null,
    val strokeColorArgb: Int? = null,
    val strokeWidthPx: Float = 0f,
    val fontWeight: String? = null,
    val fontStyle: String? = null,
    val textTransform: String? = null,
    val shadowIntensity: Float = 0f,
    val shadowColorArgb: Int = 0xFF000000.toInt(),
    val shadowBlur: Float? = null,
    val shadowDistance: Float = 12f,
    val shadowAngle: Float = 45f,
    val elevationIntensity: Float = 0f,
    val elevationStyle: ShapeElevationStyle = ShapeElevationStyle.RAISED,
    val depthSizePx: Float? = null,
    val elevationShadowBlur: Float? = null,
    val textFormPreset: TextFormPreset = TextFormPreset.NONE,
    val textFormAmount: Float = 0.55f,
    val charSpacing: Float = 0f,
    /** Chip preview — text glyph color (solid). */
    val previewTextArgb: Int = textColorArgb,
    /** Chip preview — background fill behind sample text. */
    val previewFillArgb: Int = shapeColorArgb,
)

private fun linearGradient(start: String, end: String): CloudGradient = CloudGradient(
    type = "linear",
    colorStops = listOf(
        CloudGradientStop(0f, start),
        CloudGradientStop(1f, end),
    ),
    coords = CloudGradientCoords(x1 = 0f, y1 = 0f, x2 = 1f, y2 = 0f),
)

internal val textStyleTemplates: List<TextStyleTemplate> = listOf(
    TextStyleTemplate(
        id = "classic",
        nameRes = R.string.studio_text_template_classic,
        shapeColorArgb = 0xE6000000.toInt(),
        textColorArgb = 0xFFFFFFFF.toInt(),
        fontWeight = "bold",
        shadowIntensity = 0.28f,
        shadowBlur = 8f,
        shadowDistance = 6f,
        previewTextArgb = 0xFFFFFFFF.toInt(),
        previewFillArgb = 0xE6000000.toInt(),
    ),
    TextStyleTemplate(
        id = "gradient",
        nameRes = R.string.studio_text_template_gradient,
        textColorArgb = 0xFF42A5F5.toInt(),
        textColorGradient = linearGradient("#FF42A5F5", "#FFAB47BC"),
        shapeColorArgb = ShapeLabelDefaults.TRANSPARENT_FILL_ARGB,
        shadowIntensity = 0.18f,
        shadowBlur = 10f,
        shadowDistance = 4f,
        previewTextArgb = 0xFF7E57C2.toInt(),
        previewFillArgb = 0x33FFFFFF,
    ),
    TextStyleTemplate(
        id = "sale",
        nameRes = R.string.studio_text_template_sale,
        shapeColorArgb = 0xFFE53935.toInt(),
        textColorArgb = 0xFFFFFFFF.toInt(),
        fontWeight = "bold",
        textTransform = "uppercase",
        shadowIntensity = 0.12f,
        elevationIntensity = 0.4f,
        depthSizePx = 10f,
        elevationShadowBlur = 6f,
        previewTextArgb = 0xFFFFFFFF.toInt(),
        previewFillArgb = 0xFFE53935.toInt(),
    ),
    TextStyleTemplate(
        id = "arc",
        nameRes = R.string.studio_text_template_arc,
        shapeColorArgb = 0xFFFFF8E1.toInt(),
        textColorArgb = 0xFF7B1FA2.toInt(),
        fontStyle = "italic",
        textFormPreset = TextFormPreset.PATH_ARC_UP,
        textFormAmount = 1.2f,
        shadowIntensity = 0.15f,
        shadowBlur = 6f,
        previewTextArgb = 0xFF7B1FA2.toInt(),
        previewFillArgb = 0xFFFFF8E1.toInt(),
    ),
    TextStyleTemplate(
        id = "outline",
        nameRes = R.string.studio_text_template_outline,
        shapeColorArgb = ShapeLabelDefaults.TRANSPARENT_FILL_ARGB,
        strokeColorArgb = 0xFF1565C0.toInt(),
        strokeWidthPx = 3f,
        textColorArgb = 0xFF1565C0.toInt(),
        fontWeight = "bold",
        previewTextArgb = 0xFF1565C0.toInt(),
        previewFillArgb = 0x33FFFFFF,
    ),
    TextStyleTemplate(
        id = "neon",
        nameRes = R.string.studio_text_template_neon,
        shapeColorArgb = 0xFF0D0221.toInt(),
        textColorArgb = 0xFF39FF14.toInt(),
        fontWeight = "bold",
        shadowIntensity = 0.55f,
        shadowColorArgb = 0xFF39FF14.toInt(),
        shadowBlur = 18f,
        shadowDistance = 0f,
        previewTextArgb = 0xFF39FF14.toInt(),
        previewFillArgb = 0xFF0D0221.toInt(),
    ),
    TextStyleTemplate(
        id = "soft",
        nameRes = R.string.studio_text_template_soft,
        shapeColorArgb = 0xFFF3E5F5.toInt(),
        textColorArgb = 0xFF6A1B9A.toInt(),
        fontStyle = "italic",
        shadowIntensity = 0.2f,
        shadowBlur = 14f,
        shadowDistance = 8f,
        previewTextArgb = 0xFF6A1B9A.toInt(),
        previewFillArgb = 0xFFF3E5F5.toInt(),
    ),
    TextStyleTemplate(
        id = "poster",
        nameRes = R.string.studio_text_template_poster,
        shapeColorArgb = 0xFFFF6F00.toInt(),
        textColorArgb = 0xFFFFFFFF.toInt(),
        fontWeight = "bold",
        textTransform = "uppercase",
        charSpacing = 4f,
        elevationIntensity = 0.55f,
        depthSizePx = 14f,
        elevationShadowBlur = 8f,
        previewTextArgb = 0xFFFFFFFF.toInt(),
        previewFillArgb = 0xFFFF6F00.toInt(),
    ),
    TextStyleTemplate(
        id = "minimal",
        nameRes = R.string.studio_text_template_minimal,
        shapeColorArgb = ShapeLabelDefaults.TRANSPARENT_FILL_ARGB,
        textColorArgb = 0xFF212121.toInt(),
        fontWeight = "normal",
        shadowIntensity = 0f,
        elevationIntensity = 0f,
        previewTextArgb = 0xFF212121.toInt(),
        previewFillArgb = 0x33FFFFFF,
    ),
    TextStyleTemplate(
        id = "gold",
        nameRes = R.string.studio_text_template_gold,
        textColorArgb = 0xFFFFC107.toInt(),
        textColorGradient = linearGradient("#FFFFD54F", "#FFFF8F00"),
        shapeColorArgb = 0xFF212121.toInt(),
        fontWeight = "bold",
        shadowIntensity = 0.35f,
        shadowBlur = 10f,
        shadowDistance = 5f,
        previewTextArgb = 0xFFFFC107.toInt(),
        previewFillArgb = 0xFF212121.toInt(),
    ),
    TextStyleTemplate(
        id = "retro",
        nameRes = R.string.studio_text_template_retro,
        shapeColorArgb = 0xFFFFECB3.toInt(),
        textColorArgb = 0xFFBF360C.toInt(),
        fontWeight = "bold",
        textTransform = "uppercase",
        charSpacing = 2f,
        shadowIntensity = 0.22f,
        shadowBlur = 4f,
        shadowDistance = 3f,
        previewTextArgb = 0xFFBF360C.toInt(),
        previewFillArgb = 0xFFFFECB3.toInt(),
    ),
    TextStyleTemplate(
        id = "ocean",
        nameRes = R.string.studio_text_template_ocean,
        textColorArgb = 0xFF0277BD.toInt(),
        textColorGradient = linearGradient("#FF4FC3F7", "#FF01579B"),
        shapeColorArgb = ShapeLabelDefaults.TRANSPARENT_FILL_ARGB,
        fontStyle = "italic",
        shadowIntensity = 0.2f,
        shadowBlur = 12f,
        previewTextArgb = 0xFF0288D1.toInt(),
        previewFillArgb = 0x33FFFFFF,
    ),
)

internal fun findTextStyleTemplate(id: String): TextStyleTemplate? =
    textStyleTemplates.firstOrNull { it.id == id }

internal fun TextStyleTemplate.matches(layer: EditorLayer): Boolean {
    val appearance = layer.appearance
    if (layer.textColorArgb != textColorArgb) return false
    if (layer.shapeColorArgb != shapeColorArgb) return false
    if (layer.textColorGradient != textColorGradient) return false
    if (layer.fillGradient != fillGradient) return false
    if (layer.strokeColorArgb != strokeColorArgb) return false
    if (abs(layer.strokeWidthPx - strokeWidthPx) > 0.5f) return false
    if (fontWeight != null && layer.fontWeight != fontWeight) return false
    if (fontStyle != null && layer.fontStyle != fontStyle) return false
    if (layer.textTransform != textTransform) return false
    if (layer.textForm.preset != textFormPreset) return false
    if (textFormPreset != TextFormPreset.NONE &&
        abs(layer.textForm.amount - textFormAmount) > 0.15f
    ) {
        return false
    }
    if (abs(layer.charSpacing - charSpacing) > 0.5f) return false
    if (abs(appearance.shadowIntensity - shadowIntensity) > 0.06f) return false
    if (appearance.shadowColorArgb != shadowColorArgb) return false
    if (appearance.shadowBlur != shadowBlur) return false
    if (abs(appearance.elevationIntensity - elevationIntensity) > 0.06f) return false
    if (appearance.elevationTarget != ElevationTarget.TEXT) return false
    if (appearance.depthSizePx != depthSizePx) return false
    if (appearance.elevationShadowBlur != elevationShadowBlur) return false
    return true
}

internal fun EditorLayer.matchedTextStyleTemplate(): TextStyleTemplate? =
    textStyleTemplates.firstOrNull { it.matches(this) }

internal fun TextStyleTemplate.applyTo(layer: EditorLayer): EditorLayer {
    val textForm = if (textFormPreset == TextFormPreset.NONE) {
        TextFormEffect()
    } else {
        layer.textForm.withPreset(textFormPreset).copy(amount = textFormAmount)
    }
    // Patch typography/color across existing runs — do not collapse multi-run text.
    val styled = layer.withTextSpans(
        TextRunOps.applyStyle(
            TextRunOps.effectiveSpans(layer),
            0,
            layer.text.length,
            TextSpanStylePatch(
                fontWeight = fontWeight ?: layer.fontWeight,
                fontStyle = fontStyle ?: layer.fontStyle,
                colorArgb = textColorArgb,
            ),
        ),
    )
    return styled.copy(
        textColorGradient = textColorGradient,
        shapeColorArgb = shapeColorArgb,
        fillGradient = fillGradient,
        strokeColorArgb = strokeColorArgb,
        strokeWidthPx = strokeWidthPx,
        textTransform = textTransform,
        charSpacing = charSpacing,
        textForm = textForm,
        appearance = layer.appearance.copy(
            shadowIntensity = shadowIntensity,
            shadowColorArgb = shadowColorArgb,
            shadowBlur = shadowBlur,
            shadowDistance = shadowDistance,
            shadowAngle = shadowAngle,
            elevationIntensity = elevationIntensity,
            elevationStyle = elevationStyle,
            depthSizePx = depthSizePx,
            elevationShadowBlur = elevationShadowBlur,
            elevationTarget = ElevationTarget.TEXT,
        ),
    )
}
