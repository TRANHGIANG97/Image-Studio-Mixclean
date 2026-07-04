package com.thgiang.image.studio.ui.editor.label

import android.content.Context
import com.thgiang.image.core.domain.model.template.CloudGradient
import com.thgiang.image.studio.ui.editor.label.factory.EditorLayerFactory
import com.thgiang.image.studio.ui.editor.label.model.applyShapeTypeChange
import com.thgiang.image.studio.ui.editor.label.model.withShapeFittedToText
import com.thgiang.image.studio.ui.editor.model.EditorLayer
import com.thgiang.image.studio.ui.editor.model.EditorState
import com.thgiang.image.studio.ui.editor.model.EditorTool
import com.thgiang.image.studio.ui.editor.model.ShapeType
import com.thgiang.image.studio.ui.editor.model.TextFormEffect
import com.thgiang.image.studio.ui.editor.model.TextFormPreset
import com.thgiang.image.studio.ui.editor.model.LayerGroupRole
import com.thgiang.image.studio.ui.editor.model.isLabelLayer
import com.thgiang.image.studio.ui.editor.model.withPreset
import kotlinx.coroutines.flow.MutableSharedFlow

class LabelViewModelDelegate(
    private val context: Context,
    private val layerFactory: EditorLayerFactory,
    shapeFitFlow: MutableSharedFlow<Unit>,
    readState: () -> EditorState,
    updateState: (EditorState.() -> EditorState) -> Unit,
    requestHistoryPush: () -> Unit,
    pushHistory: () -> Unit,
) : EditorLayerMutationHost(shapeFitFlow, readState, updateState, requestHistoryPush, pushHistory) {

    fun addTextLayer(templateWidth: Float) {
        val layer = layerFactory.createTextLayer(templateWidth)
        val layerId = layer.id
        updateState {
            copy(
                layers = layers + layer,
                selectedLayerId = layerId,
                selectedTool = EditorTool.Label,
            )
        }
        pushHistory()
    }

    private fun fitAndSyncGroup(groupLayers: List<EditorLayer>): List<EditorLayer> {
        val label = groupLayers.firstOrNull { it.isLabelLayer } ?: return groupLayers
        val fittedLabel = label.withShapeFittedToText(context)
        return groupLayers.map { layer ->
            if (layer.isLabelLayer) {
                fittedLabel
            } else {
                layer.copy(
                    shapeWidthPx = fittedLabel.shapeWidthPx,
                    shapeHeightPx = fittedLabel.shapeHeightPx,
                    shapeType = fittedLabel.shapeType,
                    cornerRadiusX = fittedLabel.cornerRadiusX,
                    cornerRadiusY = fittedLabel.cornerRadiusY,
                )
            }
        }
    }

    private fun primarySelectionId(layers: List<EditorLayer>): String =
        layers.firstOrNull { it.groupRole == LayerGroupRole.LABEL }?.id
            ?: layers.firstOrNull { it.isLabelLayer }?.id
            ?: layers.first().id

    fun addShapeTextLayer(shapeType: ShapeType, templateWidth: Float) {
        val group = fitAndSyncGroup(layerFactory.createShapeTextGroup(templateWidth, shapeType))
        val layerId = primarySelectionId(group)
        updateState {
            copy(
                layers = layers + group,
                selectedLayerId = layerId,
                selectedTool = EditorTool.Label,
            )
        }
        pushHistory()
    }

    fun confirmAddLabel(shapeType: ShapeType, templateWidth: Float) {
        val group = fitAndSyncGroup(layerFactory.createShapeTextGroup(templateWidth, shapeType))
        val layerId = primarySelectionId(group)
        updateState {
            copy(
                layers = layers + group,
                selectedLayerId = layerId,
                selectedTool = null,
            )
        }
        pushHistory()
    }

    fun confirmAddLabelWithText(text: String, templateWidth: Float) {
        val layer = layerFactory
            .createTextLayer(templateWidth)
            .copy(text = text.ifBlank { "Label" })
            .withShapeFittedToText(context)
        val layerId = layer.id
        updateState {
            copy(
                layers = layers + layer,
                selectedLayerId = layerId,
                selectedTool = null,
            )
        }
        pushHistory()
    }

    fun dismissLabelTool() {
        updateState { copy(selectedTool = null) }
    }

    fun updateShapeText(text: String) {
        updateActiveLabelLayer { it.copy(text = text) }
    }

    fun updateTextSize(sizeSp: Float) {
        updateActiveLabelLayer {
            it.copy(
                textSizeSp = sizeSp.coerceIn(1f, 500f),
                viewport = it.viewport.withScale(1f),
            )
        }
    }

    fun updateTextFontFamily(fontFamily: String?) {
        updateActiveLabelLayer {
            it.copy(fontFamily = fontFamily?.takeIf { f -> f.isNotBlank() })
        }
    }

    fun updateTextBold(bold: Boolean) {
        updateActiveLabelLayer { it.copy(fontWeight = if (bold) "bold" else "normal") }
    }

    fun updateTextItalic(italic: Boolean) {
        updateActiveLabelLayer { it.copy(fontStyle = if (italic) "italic" else "normal") }
    }

    fun updateTextUnderline(underline: Boolean) {
        updateActiveLabelLayer { it.copy(underline = underline) }
    }

    fun updateTextLinethrough(linethrough: Boolean) {
        updateActiveLabelLayer { it.copy(linethrough = linethrough) }
    }

    fun updateTextAlign(align: String) {
        updateActiveLabelLayer { it.copy(textAlign = align) }
    }

    fun updateLineHeight(multiplier: Float) {
        updateActiveLabelLayer { it.copy(lineHeight = multiplier.coerceIn(0.5f, 3f)) }
    }

    fun updateCharSpacing(spacing: Float) {
        updateActiveLabelLayer { it.copy(charSpacing = spacing.coerceIn(-20f, 80f)) }
    }

    fun updateTextTransform(transform: String?) {
        updateActiveLabelLayer { it.copy(textTransform = transform) }
    }

    fun applyTextFormPreset(preset: TextFormPreset) {
        updateActiveLabelLayer { layer ->
            val defaultAmount = when (preset) {
                TextFormPreset.NONE -> 0.5f
                else -> layer.textForm.amount.takeIf { it > 0.01f } ?: 0.55f
            }
            layer.copy(textForm = layer.textForm.withPreset(preset).copy(amount = defaultAmount))
        }
    }

    fun updateTextFormAmount(amount: Float) {
        updateActiveLabelLayer {
            it.copy(textForm = it.textForm.copy(amount = amount.coerceIn(0f, 1f)))
        }
    }

    fun resetTextForm() {
        updateActiveLabelLayer { it.copy(textForm = TextFormEffect()) }
    }

    fun applyLabelTypographyPreset(fontWeight: String, textSizeSp: Float, textTransform: String?) {
        updateActiveLabelLayer {
            it.copy(
                fontWeight = fontWeight,
                textSizeSp = textSizeSp.coerceIn(1f, 500f),
                textTransform = textTransform,
            )
        }
    }

    fun updateTextColor(argb: Int) {
        updateActiveLabelLayer { it.copy(textColorArgb = argb, textColorGradient = null) }
    }

    fun updateTextColorGradient(gradient: CloudGradient?) {
        updateActiveLabelLayer { it.copy(textColorGradient = gradient) }
    }

    fun updateShapeType(shapeType: ShapeType) {
        updateActiveLabelLayer { layer -> layer.applyShapeTypeChange(shapeType) }
    }

    fun syncShapeSize(widthPx: Float, heightPx: Float) {
        updateActiveLabelLayer {
            it.copy(
                shapeWidthPx = widthPx.coerceAtLeast(60f),
                shapeHeightPx = heightPx.coerceAtLeast(30f),
            )
        }
    }
}
