package com.thgiang.image.studio.ui.editor.label

import android.content.Context
import com.thgiang.image.core.domain.model.template.CloudGradient
import com.thgiang.image.studio.R
import com.thgiang.image.studio.ui.editor.label.factory.EditorLayerFactory
import com.thgiang.image.studio.ui.editor.label.model.ShapeLabelDefaults
import com.thgiang.image.studio.ui.editor.label.model.applyShapeTypeChange
import com.thgiang.image.studio.ui.editor.label.model.withShapeFittedForInlineEdit
import com.thgiang.image.studio.ui.editor.label.model.withShapeFittedToText
import com.thgiang.image.studio.ui.editor.label.model.withShapeHeightFittedToText
import com.thgiang.image.studio.ui.editor.label.model.withTextFormShapeFitted
import com.thgiang.image.studio.ui.editor.label.panel.applyTo
import com.thgiang.image.studio.ui.editor.label.panel.findTextStyleTemplate
import com.thgiang.image.studio.ui.editor.mapper.EditorGradientMapper
import com.thgiang.image.studio.ui.editor.model.EditorLayer
import com.thgiang.image.studio.ui.editor.model.EditorState
import com.thgiang.image.studio.ui.editor.model.EditorTool
import com.thgiang.image.studio.ui.editor.model.LayerGroupRole
import com.thgiang.image.studio.ui.editor.model.ShapeType
import com.thgiang.image.studio.ui.editor.model.TextFormEffect
import com.thgiang.image.studio.ui.editor.model.TextFormPreset
import com.thgiang.image.studio.ui.editor.model.TextRunOps
import com.thgiang.image.studio.ui.editor.model.TextSpanStylePatch
import com.thgiang.image.studio.ui.editor.model.isLabelLayer
import com.thgiang.image.studio.ui.editor.model.withPreset
import com.thgiang.image.studio.ui.editor.model.withTextSpans

/**
 * Legacy fallback when DocumentSession is disabled.
 * Fit is applied synchronously in mutation blocks; LayoutEngine owns Document path sizing.
 */
class LabelViewModelDelegate(
    private val context: Context,
    private val layerFactory: EditorLayerFactory,
    readState: () -> EditorState,
    updateState: (EditorState.() -> EditorState) -> Unit,
    requestHistoryPush: () -> Unit,
    pushHistory: () -> Unit,
) : EditorLayerMutationHost(readState, updateState, requestHistoryPush, pushHistory) {

    fun addTextLayer(templateWidth: Float) {
        val defaultText = context.getString(R.string.studio_text_default_placeholder)
        val layer = layerFactory.createTextLayer(templateWidth, defaultText).withShapeFittedToText(context)
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
            .withReflowedText(text.ifBlank { "Label" })
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

    private fun fitLabelLayer(layer: EditorLayer): EditorLayer =
        if (layer.textForm.isActive) layer.withTextFormShapeFitted(context)
        else layer.withShapeHeightFittedToText(context)

    private fun EditorLayer.withReflowedText(newText: String): EditorLayer {
        val spans = TextRunOps.reflow(TextRunOps.effectiveSpans(this), text, newText)
        return withTextSpans(spans)
    }

    private fun EditorLayer.withFullSpanStyle(patch: TextSpanStylePatch): EditorLayer {
        val spans = TextRunOps.applyStyle(
            TextRunOps.effectiveSpans(this),
            0,
            text.length,
            patch,
        )
        return withTextSpans(spans)
    }

    fun updateShapeText(text: String) {
        updateActiveLayerWhen({ it.isLabelLayer }) { layer ->
            val updated = layer.withReflowedText(text)
            if (readState().editingLayerId == layer.id) {
                updated.withShapeFittedForInlineEdit(context)
            } else {
                fitLabelLayer(updated)
            }
        }
        requestHistoryPush()
    }

    fun insertTextNewline() {
        updateActiveLayerWhen({ it.isLabelLayer }) { layer ->
            fitLabelLayer(layer.withReflowedText(layer.text + "\n"))
        }
        requestHistoryPush()
    }

    fun updateTextSize(sizeSp: Float) {
        updateActiveLabelLayer {
            val newSize = sizeSp.coerceIn(1f, ShapeLabelDefaults.MAX_TEXT_SIZE_SP)
            it.copy(
                textSizeSp = newSize,
                viewport = it.viewport.withScale(1f),
            )
        }
    }

    fun updateTextFontFamily(fontFamily: String?) {
        updateActiveLabelLayer {
            fitLabelLayer(
                it.withFullSpanStyle(
                    TextSpanStylePatch(fontFamily = fontFamily?.takeIf { f -> f.isNotBlank() }),
                ).copy(fontFamily = fontFamily?.takeIf { f -> f.isNotBlank() }),
            )
        }
    }

    fun updateTextBold(bold: Boolean) {
        updateActiveLabelLayer {
            fitLabelLayer(
                it.withFullSpanStyle(TextSpanStylePatch(fontWeight = if (bold) "bold" else "normal")),
            )
        }
    }

    fun updateTextItalic(italic: Boolean) {
        updateActiveLabelLayer {
            fitLabelLayer(
                it.withFullSpanStyle(TextSpanStylePatch(fontStyle = if (italic) "italic" else "normal")),
            )
        }
    }

    fun updateTextUnderline(underline: Boolean) {
        updateActiveLabelLayer {
            fitLabelLayer(it.withFullSpanStyle(TextSpanStylePatch(underline = underline)))
        }
    }

    fun updateTextLinethrough(linethrough: Boolean) {
        updateActiveLabelLayer {
            fitLabelLayer(it.withFullSpanStyle(TextSpanStylePatch(linethrough = linethrough)))
        }
    }

    fun updateTextAlign(align: String) {
        updateActiveLabelLayer { fitLabelLayer(it.copy(textAlign = align)) }
    }

    fun updateLineHeight(multiplier: Float) {
        updateActiveLabelLayer { fitLabelLayer(it.copy(lineHeight = multiplier.coerceIn(0.5f, 3f))) }
    }

    fun updateCharSpacing(spacing: Float) {
        updateActiveLabelLayer { fitLabelLayer(it.copy(charSpacing = spacing.coerceIn(-20f, 80f))) }
    }

    fun updateTextTransform(transform: String?) {
        updateActiveLabelLayer { layer ->
            layer.copy(textTransform = transform).withShapeHeightFittedToText(context)
        }
    }

    fun applyTextFormPreset(preset: TextFormPreset) {
        updateActiveLabelLayer { layer ->
            val defaultAmount = when (preset) {
                TextFormPreset.NONE -> 0.5f
                else -> layer.textForm.amount.takeIf { it > 0.01f } ?: 0.55f
            }
            val updated = layer.copy(textForm = layer.textForm.withPreset(preset).copy(amount = defaultAmount))
            if (preset == TextFormPreset.NONE) {
                updated.withShapeFittedToText(context)
            } else {
                updated.withTextFormShapeFitted(context)
            }
        }
    }

    fun updateTextFormAmount(amount: Float) {
        updateActiveLabelLayer {
            it.copy(textForm = it.textForm.copy(amount = amount.coerceIn(0f, TextFormEffect.MAX_AMOUNT)))
                .withTextFormShapeFitted(context)
        }
    }

    fun resetTextForm() {
        updateActiveLabelLayer {
            it.copy(textForm = TextFormEffect()).withShapeFittedToText(context)
        }
    }

    fun applyLabelTypographyPreset(fontWeight: String, textSizeSp: Float, textTransform: String?) {
        updateActiveLabelLayer {
            fitLabelLayer(
                it.withFullSpanStyle(TextSpanStylePatch(fontWeight = fontWeight)).copy(
                    textSizeSp = textSizeSp.coerceIn(1f, ShapeLabelDefaults.MAX_TEXT_SIZE_SP),
                    textTransform = textTransform,
                ),
            )
        }
    }

    fun applyTextStyleTemplate(templateId: String) {
        val template = findTextStyleTemplate(templateId) ?: return
        updateActiveLabelLayer { layer ->
            template.applyTo(layer)
        }
    }

    fun updateTextColor(argb: Int) {
        updateActiveLabelLayer {
            it.withFullSpanStyle(TextSpanStylePatch(colorArgb = argb))
                .copy(textColorArgb = argb, textColorGradient = null)
        }
    }

    fun updateTextColorGradient(gradient: CloudGradient?) {
        updateActiveLabelLayer { layer ->
            val startArgb = gradient?.let {
                EditorGradientMapper.parseStopArgb(it, 0, layer.textColorArgb)
            }
            layer.copy(
                textColorGradient = gradient,
                textColorArgb = startArgb ?: layer.textColorArgb,
            )
        }
    }

    fun updateShapeType(shapeType: ShapeType) {
        // Prefer FRAME sibling so fill/stroke defaults land on the frame layer; geometry syncs to label.
        updateActiveFrameLayer { layer -> layer.applyShapeTypeChange(shapeType) }
    }

    fun syncShapeSize(widthPx: Float, heightPx: Float) {
        updateActiveLayerWhen({ it.isLabelLayer }) { layer ->
            val sized = layer.copy(
                shapeWidthPx = widthPx.coerceAtLeast(60f),
                shapeHeightPx = heightPx.coerceAtLeast(30f),
            )
            if (layer.textForm.isActive) sized else sized.withShapeHeightFittedToText(context)
        }
        requestHistoryPush()
    }
}
