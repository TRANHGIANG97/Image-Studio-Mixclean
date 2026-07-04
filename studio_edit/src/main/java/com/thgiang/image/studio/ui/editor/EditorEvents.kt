package com.thgiang.image.studio.ui.editor
import com.thgiang.image.studio.ui.editor.*
import com.thgiang.image.studio.ui.editor.canvas.*

import com.thgiang.image.studio.ui.editor.model.*

import android.net.Uri
import androidx.compose.ui.geometry.Offset

import com.thgiang.image.core.domain.model.template.CloudGradient
import com.thgiang.image.core.domain.model.template.CloudTemplate

sealed class EditorEvent {
    data class LoadTemplate(val assetPath: String, val objectSourceAssetPath: String? = null) : EditorEvent()
    data class LoadCloudTemplate(val cloudTemplate: CloudTemplate) : EditorEvent()
    data class LoadCloudTemplateById(val templateId: String) : EditorEvent()
    data class SetProductImage(val uri: Uri, val replaceLayerId: String? = null) : EditorEvent()
    data class AddSticker(val assetPath: String) : EditorEvent()

    // ── Label events ──────────────────────────────────────────────────────
    /** Add a new plain TEXT layer at the canvas center */
    data object AddTextLayer : EditorEvent()
    /** Add a new SHAPE_TEXT layer at the canvas center (keeps Label tool active) */
    data class AddShapeTextLayer(val shapeType: ShapeType) : EditorEvent()
    /** Add label with chosen shape and exit label tool */
    data class ConfirmAddLabel(val shapeType: ShapeType) : EditorEvent()
    /** Add text-only label with custom text */
    data class ConfirmAddLabelText(val text: String) : EditorEvent()
    /** Exit label tool without adding a layer */
    data object DismissLabelTool : EditorEvent()

    // ── Shape events (độc lập với Label) ──────────────────────────────────
    /** Add a decorative shape layer (no text) and keep Shape tool active */
    data class AddShapeLayer(val shapeType: ShapeType) : EditorEvent()
    /** Add shape and exit shape tool */
    data class ConfirmAddShape(val shapeType: ShapeType) : EditorEvent()
    /** Exit shape tool without adding a layer */
    data object DismissShapeTool : EditorEvent()
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

    data class UpdateTextBold(val bold: Boolean) : EditorEvent()
    data class UpdateTextItalic(val italic: Boolean) : EditorEvent()
    data class UpdateTextUnderline(val underline: Boolean) : EditorEvent()
    data class UpdateTextLinethrough(val linethrough: Boolean) : EditorEvent()
    data class UpdateTextAlign(val align: String) : EditorEvent()
    data class UpdateLineHeight(val multiplier: Float) : EditorEvent()
    data class UpdateCharSpacing(val spacing: Float) : EditorEvent()
    data class UpdateTextTransform(val transform: String?) : EditorEvent()
    data class UpdateFillGradient(val gradient: CloudGradient?) : EditorEvent()
    data class UpdateTextColorGradient(val gradient: CloudGradient?) : EditorEvent()
    data class ApplyLabelTypographyPreset(
        val fontWeight: String,
        val textSizeSp: Float,
        val textTransform: String?,
    ) : EditorEvent()
    data class UpdateStrokeColor(val argb: Int) : EditorEvent()
    data class UpdateStrokeWidth(val widthPx: Float) : EditorEvent()
    data class UpdateStrokeDash(val dashArray: List<Float>) : EditorEvent()
    data class UpdateCornerRadius(val radiusPx: Float) : EditorEvent()
    /** Keep shape dimensions in sync with rendered text (no history entry). */
    data class SyncShapeSize(val widthPx: Float, val heightPx: Float) : EditorEvent()

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
    data class UpdateShadowBlur(val blurPx: Float?) : EditorEvent()
    data class UpdateElevation(val intensity: Float) : EditorEvent()
    data class UpdateDepthSize(val sizePx: Float) : EditorEvent()
    data class UpdateDepthColor(val argb: Int?) : EditorEvent()
    data class UpdateExtrusionAngle(val angle: Float) : EditorEvent()
    data class UpdateElevationStyle(val style: ShapeElevationStyle) : EditorEvent()
    data class UpdateElevationTarget(val target: ElevationTarget) : EditorEvent()
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
    data object MoveLayerToTop : EditorEvent()
    data object MoveLayerToBottom : EditorEvent()
    /** Toggle lock state of the currently selected layer */
    data object ToggleLayerLock : EditorEvent()
    /** Align selected layer to canvas: left / centerH / right / top / centerV / bottom */
    data class AlignLayer(val alignment: LayerAlignment) : EditorEvent()
    /** Copy the current layer's style (fill, stroke, shadow) to clipboard */
    data object CopyLabelStyle : EditorEvent()
    /** Paste clipboard style onto the currently selected layer */
    data object PasteLabelStyle : EditorEvent()
    data class ApplyTextFormPreset(val preset: TextFormPreset) : EditorEvent()
    data class UpdateTextFormAmount(val amount: Float) : EditorEvent()
    data object ResetTextForm : EditorEvent()
    data object SaveDraft : EditorEvent()
    data class Export(val templateAssetPath: String) : EditorEvent()
}
