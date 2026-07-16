package com.thgiang.image.studio.ui.editor.model

import com.thgiang.image.studio.ui.editor.label.geometry.EditorShapeGeometry
import com.thgiang.image.studio.ui.editor.mapper.resolveStrokeWidthPx

/**
 * Domain split for vector layers (Phase 0–2).
 * - [isFrameLayer]: Khung tool — shape geometry.
 * - [isLabelLayer]: Nhãn tool — text content.
 */
val EditorLayer.isFrameLayer: Boolean
    get() = when {
        groupRole == LayerGroupRole.FRAME -> true
        type == LayerType.SHAPE -> true
        type == LayerType.SHAPE_TEXT ->
            text.isBlank() && !EditorShapeGeometry.isTextOnlyShape(shapeType)
        else -> false
    }

val EditorLayer.isLabelLayer: Boolean
    get() = when {
        groupRole == LayerGroupRole.LABEL -> true
        type == LayerType.TEXT -> true
        type == LayerType.SHAPE_TEXT -> !isFrameLayer
        else -> false
    }

/** Layer has drawable shape geometry (not TEXT_ONLY). */
val EditorLayer.hasVisibleFrameGeometry: Boolean
    get() = !EditorShapeGeometry.isTextOnlyShape(shapeType)

/** TEXT_ONLY label with user-applied fill or stroke — render decor without layout refit. */
val EditorLayer.hasTextOnlyBackgroundDecor: Boolean
    get() {
        if (!EditorShapeGeometry.isTextOnlyShape(shapeType)) return false
        val fillAlpha = (shapeColorArgb ushr 24) and 0xFF
        if (fillAlpha > 0 || fillGradient != null) return true
        val stroke = strokeColorArgb
        if (stroke != null && resolveStrokeWidthPx() > 0f && ((stroke ushr 24) and 0xFF) > 0) return true
        return false
    }

/** Shape type used when drawing background decor on TEXT_ONLY layers. */
val EditorLayer.backgroundDecorShapeType: ShapeType
    get() = if (hasTextOnlyBackgroundDecor) ShapeType.CARD else shapeType

/** Canvas/export: draw shape fill, stroke, frame shadow/elevation. */
val EditorLayer.shouldRenderFrameContent: Boolean
    get() = when {
        type == LayerType.SHAPE -> true
        groupRole == LayerGroupRole.FRAME -> true
        type == LayerType.SHAPE_TEXT && isFrameLayer -> true
        (type == LayerType.TEXT || type == LayerType.SHAPE_TEXT) && !EditorShapeGeometry.isTextOnlyShape(shapeType) -> true
        else -> false
    }

/** Canvas/export: draw text, text form, text elevation. */
val EditorLayer.shouldRenderLabelContent: Boolean
    get() = when {
        type == LayerType.TEXT -> true
        groupRole == LayerGroupRole.LABEL -> true
        type == LayerType.SHAPE_TEXT && isLabelLayer -> true
        else -> false
    }

fun EditorLayer.preferredEditorTool(): EditorTool? = when {
    isFrameLayer -> EditorTool.Shape
    isLabelLayer -> EditorTool.Label
    else -> null
}
