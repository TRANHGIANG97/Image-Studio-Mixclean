package com.thgiang.image.studio.ui.editor.shape

import com.thgiang.image.core.domain.model.template.CloudGradient
import com.thgiang.image.studio.ui.editor.label.EditorLayerMutationHost
import com.thgiang.image.studio.ui.editor.label.factory.EditorLayerFactory
import com.thgiang.image.studio.ui.editor.label.model.applyShapeTypeChange
import com.thgiang.image.studio.ui.editor.mapper.EditorStrokeMapper
import com.thgiang.image.studio.ui.editor.model.EditorState
import com.thgiang.image.studio.ui.editor.model.EditorTool
import com.thgiang.image.studio.ui.editor.model.ShapeType

/** Legacy fallback when DocumentSession is disabled. */
class ShapeViewModelDelegate(
    private val layerFactory: EditorLayerFactory,
    readState: () -> EditorState,
    updateState: (EditorState.() -> EditorState) -> Unit,
    requestHistoryPush: () -> Unit,
    pushHistory: () -> Unit,
) : EditorLayerMutationHost(readState, updateState, requestHistoryPush, pushHistory) {

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
            it.copy(shapeColorArgb = argb, fillGradient = null)
        }
    }

    fun updateShapeFillOpacity(alpha: Float) {
        val alphaByte = (alpha.coerceIn(0.1f, 1f) * 255f).toInt().coerceIn(0, 255)
        updateActiveFrameLayer { layer ->
            layer.copy(shapeColorArgb = (alphaByte shl 24) or (layer.shapeColorArgb and 0x00FFFFFF))
        }
    }

    fun updateFillGradient(gradient: CloudGradient?) {
        updateActiveFrameLayer {
            it.copy(fillGradient = gradient)
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
        val gap = EditorStrokeMapper.extractDashGap(dashArray)
        updateActiveFrameLayer { it.copy(strokeDashArray = dashArray, strokeDashGapPx = gap) }
    }

    fun updateStrokeDashGap(gapPx: Float) {
        val gap = gapPx.coerceIn(0f, 40f)
        updateActiveFrameLayer { layer ->
            val updatedArray = if (layer.strokeDashArray.size >= 2) {
                layer.strokeDashArray.mapIndexed { index, length ->
                    if (index % 2 == 1) gap else length
                }
            } else {
                layer.strokeDashArray
            }
            layer.copy(strokeDashArray = updatedArray, strokeDashGapPx = gap)
        }
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
