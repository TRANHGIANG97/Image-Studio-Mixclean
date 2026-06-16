package com.thgiang.image.studio.ui.editor

import android.net.Uri
import androidx.compose.ui.geometry.Offset

import com.thgiang.image.core.domain.model.template.CloudTemplate

sealed class EditorEvent {
    data class LoadTemplate(val assetPath: String, val objectSourceAssetPath: String? = null) : EditorEvent()
    data class LoadCloudTemplate(val cloudTemplate: CloudTemplate) : EditorEvent()
    data class LoadCloudTemplateById(val templateId: String) : EditorEvent()
    data class SetProductImage(val uri: Uri, val replaceLayerId: String? = null) : EditorEvent()
    data class AddSticker(val assetPath: String) : EditorEvent()

    // ── Shape & Text layer events ─────────────────────────────────────────
    /** Add a new plain TEXT layer at the canvas center */
    data object AddTextLayer : EditorEvent()
    /** Add a new SHAPE_TEXT layer at the canvas center */
    data class AddShapeTextLayer(val shapeType: ShapeType) : EditorEvent()
    /** Update the text content of the selected SHAPE_TEXT layer */
    data class UpdateShapeText(val text: String) : EditorEvent()
    /** Update the fill color (ARGB) of the selected SHAPE_TEXT layer */
    data class UpdateShapeColor(val argb: Int) : EditorEvent()
    /** Update the text color (ARGB) of the selected SHAPE_TEXT layer */
    data class UpdateTextColor(val argb: Int) : EditorEvent()
    /** Update the text size (SP) of the selected SHAPE_TEXT layer */
    data class UpdateTextSize(val sizeSp: Float) : EditorEvent()
    /** Update the font family of the selected text layer */
    data class UpdateTextFontFamily(val fontFamily: String?) : EditorEvent()
    /** Change the shape type of the selected SHAPE_TEXT layer */
    data class UpdateShapeType(val shapeType: ShapeType) : EditorEvent()

    data class UpdateGesture(val delta: GestureDelta) : EditorEvent()
    data class UpdateOffset(val delta: Offset) : EditorEvent()
    data class SetOffset(val offset: Offset) : EditorEvent()
    data class UpdateScale(val factor: Float) : EditorEvent()
    data class SetScale(val scale: Float) : EditorEvent()
    data class UpdateRotation(val delta: Float) : EditorEvent()
    data class SetRotation(val degrees: Float) : EditorEvent()
    data object FlipHorizontal : EditorEvent()
    data object FlipVertical : EditorEvent()
    data class UpdateShadow(val intensity: Float) : EditorEvent()
    data class UpdateShadowAngle(val angle: Float) : EditorEvent()
    data class UpdateShadowDistance(val distance: Float) : EditorEvent()
    data class UpdateShadowColor(val argb: Int) : EditorEvent()
    data class UpdateAlpha(val alpha: Float) : EditorEvent()
    data class SetBoundingBoxVisible(val visible: Boolean) : EditorEvent()
    data class SelectTool(val tool: EditorTool) : EditorEvent()
    data class SelectCropRatio(val ratio: CropRatio) : EditorEvent()
    data class SelectLayer(val layerId: String?) : EditorEvent()
    data object DuplicateLayer : EditorEvent()
    data object DeleteLayer : EditorEvent()
    data object CommitTransform : EditorEvent()
    data object Undo : EditorEvent()
    data object Redo : EditorEvent()
    data object MoveLayerUp : EditorEvent()
    data object MoveLayerDown : EditorEvent()
    data object SaveDraft : EditorEvent()
    data class Export(val templateAssetPath: String) : EditorEvent()
}
