package com.thgiang.image.studio.ui.editor.label

import android.content.Context
import com.thgiang.image.studio.ui.editor.model.*
import com.thgiang.image.studio.ui.editor.label.model.*
import com.thgiang.image.studio.ui.editor.label.factory.*
import kotlinx.coroutines.flow.MutableSharedFlow

class LabelViewModelDelegate(
    private val context: Context,
    private val layerFactory: EditorLayerFactory,
    private val shapeFitFlow: MutableSharedFlow<Unit>,
    private val readState: () -> EditorState,
    private val updateState: (EditorState.() -> EditorState) -> Unit,
    private val requestHistoryPush: () -> Unit,
    private val pushHistory: () -> Unit,
) {
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

    fun addShapeTextLayer(shapeType: ShapeType, templateWidth: Float) {
        val layer = layerFactory
            .createShapeTextLayer(templateWidth, shapeType)
            .withShapeFittedToText(context)
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

    fun confirmAddLabel(shapeType: ShapeType, templateWidth: Float) {
        val layer = layerFactory
            .createShapeTextLayer(templateWidth, shapeType)
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

    // ── Shape creation ───────────────────────────────────────────

    fun addShapeLayer(shapeType: ShapeType, templateWidth: Float) {
        val layer = layerFactory.createShapeLayer(templateWidth, shapeType)
        val layerId = layer.id
        updateState {
            copy(
                layers = layers + layer,
                selectedLayerId = layerId,
                selectedTool = EditorTool.Shape,
            )
        }
        pushHistory()
    }

    fun confirmAddShape(shapeType: ShapeType, templateWidth: Float) {
        val layer = layerFactory.createShapeLayer(templateWidth, shapeType)
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

    fun dismissShapeTool() {
        updateState { copy(selectedTool = null) }
    }

    fun updateShapeText(text: String) {
        updateActiveLayer { it.copy(text = text) }
        shapeFitFlow.tryEmit(Unit)
        requestHistoryPush()
    }

    fun updateTextSize(sizeSp: Float) {
        updateActiveTextLayer { it.copy(textSizeSp = sizeSp.coerceIn(1f, 500f)) }
        requestHistoryPush()
    }

    fun updateTextFontFamily(fontFamily: String?) {
        updateActiveTextLayer { it.copy(fontFamily = fontFamily?.takeIf { f -> f.isNotBlank() }) }
        requestHistoryPush()
    }

    fun updateTextBold(bold: Boolean) {
        updateActiveTextLayer { it.copy(fontWeight = if (bold) "bold" else "normal") }
        requestHistoryPush()
    }

    fun updateTextItalic(italic: Boolean) {
        updateActiveTextLayer { it.copy(fontStyle = if (italic) "italic" else "normal") }
        requestHistoryPush()
    }

    fun updateTextUnderline(underline: Boolean) {
        updateActiveLayer { it.copy(underline = underline) }
        requestHistoryPush()
    }

    fun updateTextLinethrough(linethrough: Boolean) {
        updateActiveLayer { it.copy(linethrough = linethrough) }
        requestHistoryPush()
    }

    fun updateTextAlign(align: String) {
        updateActiveLayer { it.copy(textAlign = align) }
        requestHistoryPush()
    }

    fun updateLineHeight(multiplier: Float) {
        updateActiveTextLayer { it.copy(lineHeight = multiplier.coerceIn(0.5f, 3f)) }
        requestHistoryPush()
    }

    fun updateCharSpacing(spacing: Float) {
        updateActiveTextLayer { it.copy(charSpacing = spacing.coerceIn(-20f, 80f)) }
        requestHistoryPush()
    }

    fun updateTextTransform(transform: String?) {
        updateActiveTextLayer { it.copy(textTransform = transform) }
        requestHistoryPush()
    }

    fun applyLabelTypographyPreset(fontWeight: String, textSizeSp: Float, textTransform: String?) {
        updateActiveTextLayer {
            it.copy(
                fontWeight = fontWeight,
                textSizeSp = textSizeSp.coerceIn(1f, 500f),
                textTransform = textTransform,
            )
        }
        requestHistoryPush()
    }

    fun updateShapeColor(argb: Int) {
        updateActiveLayer { it.copy(shapeColorArgb = argb, fillGradient = null) }
        requestHistoryPush()
    }

    fun updateTextColor(argb: Int) {
        updateActiveLayer { it.copy(textColorArgb = argb, textColorGradient = null) }
        requestHistoryPush()
    }

    fun updateShapeType(shapeType: ShapeType) {
        updateActiveTextLayer { layer -> layer.applyShapeTypeChange(shapeType) }
        requestHistoryPush()
    }

    fun updateFillGradient(gradient: com.thgiang.image.core.domain.model.template.CloudGradient?) {
        updateActiveLayer { it.copy(fillGradient = gradient) }
        requestHistoryPush()
    }

    fun updateTextColorGradient(gradient: com.thgiang.image.core.domain.model.template.CloudGradient?) {
        updateActiveLayer { it.copy(textColorGradient = gradient) }
        requestHistoryPush()
    }

    fun updateStrokeColor(argb: Int) {
        updateActiveLayer { it.copy(strokeColorArgb = argb) }
        requestHistoryPush()
    }

    fun updateStrokeWidth(widthPx: Float) {
        updateActiveTextLayer { it.copy(strokeWidthPx = widthPx.coerceIn(0f, 20f)) }
        requestHistoryPush()
    }

    fun updateCornerRadius(radiusPx: Float) {
        updateActiveLayer {
            val r = radiusPx.coerceAtLeast(0f)
            it.copy(cornerRadiusX = r, cornerRadiusY = r)
        }
        requestHistoryPush()
    }

    fun syncShapeSize(widthPx: Float, heightPx: Float) {
        updateActiveLayer {
            it.copy(
                shapeWidthPx = widthPx.coerceAtLeast(60f),
                shapeHeightPx = heightPx.coerceAtLeast(30f),
            )
        }
    }

    private inline fun updateActiveLayer(crossinline block: (EditorLayer) -> EditorLayer) {
        val state = readState()
        val layerId = state.selectedLayerId ?: return
        updateState {
            val newLayers = layers.map { l ->
                if (l.id == layerId) block(l) else l
            }
            copy(layers = newLayers)
        }
    }

    private inline fun updateActiveTextLayer(crossinline block: (EditorLayer) -> EditorLayer) {
        updateActiveLayer { block(it) }
        shapeFitFlow.tryEmit(Unit)
    }
}
