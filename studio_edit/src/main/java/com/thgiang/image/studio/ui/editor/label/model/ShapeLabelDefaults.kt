package com.thgiang.image.studio.ui.editor.label.model
import com.thgiang.image.studio.ui.editor.label.model.*

import com.thgiang.image.studio.ui.editor.model.*

/** Default visible outline for shape labels (accent blue, 5px at 1:1 template scale). */
object ShapeLabelDefaults {
    const val BORDER_COLOR_ARGB: Int = 0xFF1565C0.toInt()
    const val BORDER_WIDTH_PX: Float = 2f
    const val DEFAULT_TEXT_SIZE_SP: Float = 65f
    const val MAX_TEXT_SIZE_SP: Float = 500f
}

fun EditorLayer.applyShapeTypeChange(shapeType: ShapeType): EditorLayer {
    val cleared = when (shapeType) {
        ShapeType.PATH, ShapeType.POLYGON -> this
        else -> copy(pathData = null, polygonPoints = emptyList())
    }

    return when (shapeType) {
        ShapeType.TEXT_ONLY -> cleared.copy(
            shapeType = ShapeType.TEXT_ONLY,
            shapeColorArgb = 0x00FFFFFF,
            fillGradient = null,
            strokeColorArgb = null,
            strokeWidthPx = 0f,
            cornerRadiusX = null,
            cornerRadiusY = null,
        )
        ShapeType.LINE -> cleared.copy(
            shapeType = ShapeType.LINE,
            shapeColorArgb = 0x00FFFFFF,
            strokeColorArgb = 0xFF424242.toInt(),
            strokeWidthPx = 6f,
        )
        ShapeType.CARD -> cleared.withDefaultShapeBorderIfNeeded().copy(
            shapeType = ShapeType.CARD,
            cornerRadiusX = 0f,
            cornerRadiusY = 0f,
        )
        ShapeType.PILL -> cleared.withDefaultShapeBorderIfNeeded().copy(
            shapeType = ShapeType.PILL,
        )
        else -> cleared.withDefaultShapeBorderIfNeeded().copy(shapeType = shapeType)
    }
}

private fun EditorLayer.withDefaultShapeBorderIfNeeded(): EditorLayer {
    val needsDefault = this.shapeType == ShapeType.TEXT_ONLY ||
        strokeColorArgb == null ||
        strokeWidthPx <= 0f
    return if (needsDefault) {
        copy(
            strokeColorArgb = ShapeLabelDefaults.BORDER_COLOR_ARGB,
            strokeWidthPx = ShapeLabelDefaults.BORDER_WIDTH_PX,
        )
    } else {
        this
    }
}
