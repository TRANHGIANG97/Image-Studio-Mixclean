package com.thgiang.image.studio.ui.editor.model

import com.thgiang.image.studio.ui.editor.label.geometry.EditorShapeGeometry

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

/** Canvas/export: draw shape fill, stroke, frame shadow/elevation. */
val EditorLayer.shouldRenderFrameContent: Boolean
    get() = when {
        type == LayerType.SHAPE -> true
        groupRole == LayerGroupRole.FRAME -> true
        type == LayerType.SHAPE_TEXT && isFrameLayer -> true
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
