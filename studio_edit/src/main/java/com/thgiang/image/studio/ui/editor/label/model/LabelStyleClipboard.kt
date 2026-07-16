package com.thgiang.image.studio.ui.editor.label.model

import com.thgiang.image.core.domain.model.template.CloudGradient
import com.thgiang.image.studio.ui.editor.model.EditorAppearance

/**
 * Clipboard container holding the visual style of a shape/label layer.
 * Used for the "Copy Style" / "Paste Style" feature.
 */
data class LabelStyleClipboard(
    val shapeColorArgb: Int,
    val fillGradient: CloudGradient?,
    val strokeColorArgb: Int?,
    val strokeWidthPx: Float,
    val strokeDashArray: List<Float>,
    val strokeDashGapPx: Float,
    val cornerRadiusX: Float?,
    val cornerRadiusY: Float?,
    val appearance: EditorAppearance,
)
