package com.thgiang.image.studio.ui.editor.shape

import com.thgiang.image.core.domain.model.template.CloudGradient
import com.thgiang.image.studio.ui.editor.label.EditorLayerMutationHost
import com.thgiang.image.studio.ui.editor.label.factory.EditorLayerFactory
import com.thgiang.image.studio.ui.editor.label.model.applyShapeTypeChange
import com.thgiang.image.studio.ui.editor.model.EditorLayer
import com.thgiang.image.studio.ui.editor.model.EditorState
import com.thgiang.image.studio.ui.editor.model.EditorTool
import com.thgiang.image.studio.ui.editor.model.ShapeType
import kotlinx.coroutines.flow.MutableSharedFlow

class ShapeViewModelDelegate(
    private val layerFactory: EditorLayerFactory,
    shapeFitFlow: MutableSharedFlow<Unit>,
    readState: () -> EditorState,
    updateState: (EditorState.() -> EditorState) -> Unit,
    requestHistoryPush: () -> Unit,
    pushHistory: () -> Unit,
) : EditorLayerMutationHost(shapeFitFlow, readState, updateState, requestHistoryPush, pushHistory) {

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
                selectedTool = EditorTool.Shape,
            )
        }
        pushHistory()
    }

    fun dismissShapeTool() {
        updateState { copy(selectedTool = null) }
    }

    fun updateShapeColor(argb: Int) {
        updateActiveFrameLayer {
            val alpha = (argb ushr 24) and 0xFF
            val targetShape = if (alpha > 0) {
                if (it.shapeType == ShapeType.TEXT_ONLY) ShapeType.CARD else it.shapeType
            } else {
                ShapeType.TEXT_ONLY
            }
            it.copy(
                shapeColorArgb = argb,
                fillGradient = null,
                shapeType = targetShape
            )
        }
    }

    fun updateFillGradient(gradient: CloudGradient?) {
        updateActiveFrameLayer {
            val targetShape = if (gradient != null) {
                if (it.shapeType == ShapeType.TEXT_ONLY) ShapeType.CARD else it.shapeType
            } else {
                it.shapeType
            }
            it.copy(fillGradient = gradient, shapeType = targetShape)
        }
    }

    fun updateShapeType(shapeType: ShapeType) {
        updateActiveFrameLayer { layer -> layer.applyShapeTypeChange(shapeType) }
    }

    fun updateStrokeColor(argb: Int) {
        updateActiveFrameLayer {
            val alpha = (argb ushr 24) and 0xFF
            it.copy(strokeColorArgb = if (alpha == 0) null else argb)
        }
    }

    fun updateStrokeWidth(widthPx: Float) {
        val width = widthPx.coerceIn(0f, 20f)
        updateActiveFrameLayer {
            it.copy(
                strokeWidthPx = width,
                strokeColorArgb = if (width <= 0f) null else it.strokeColorArgb,
            )
        }
    }

    fun updateStrokeDash(dashArray: List<Float>) {
        updateActiveFrameLayer { it.copy(strokeDashArray = dashArray) }
    }

    fun updateCornerRadius(radiusPx: Float) {
        updateActiveFrameLayer {
            val r = radiusPx.coerceAtLeast(0f)
            it.copy(cornerRadiusX = r, cornerRadiusY = r)
        }
    }

    fun syncShapeSize(widthPx: Float, heightPx: Float) {
        updateActiveFrameLayer {
            it.copy(
                shapeWidthPx = widthPx.coerceAtLeast(60f),
                shapeHeightPx = heightPx.coerceAtLeast(30f),
            )
        }
    }
}
