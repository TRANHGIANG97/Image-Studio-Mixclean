package com.thgiang.image.studio.ui.editor.label

import com.thgiang.image.studio.ui.editor.model.EditorLayer
import com.thgiang.image.studio.ui.editor.model.EditorLayerNormalizer
import com.thgiang.image.studio.ui.editor.model.EditorState
import com.thgiang.image.studio.ui.editor.model.LayerGroupSync
import com.thgiang.image.studio.ui.editor.model.isFrameLayer
import com.thgiang.image.studio.ui.editor.model.isLabelLayer
import com.thgiang.image.studio.ui.editor.model.LayerType
import kotlinx.coroutines.flow.MutableSharedFlow

open class EditorLayerMutationHost(
    protected val shapeFitFlow: MutableSharedFlow<Unit>,
    protected val readState: () -> EditorState,
    protected val updateState: (EditorState.() -> EditorState) -> Unit,
    protected val requestHistoryPush: () -> Unit,
    protected val pushHistory: () -> Unit,
) {
    protected fun updateActiveLayer(block: (EditorLayer) -> EditorLayer) {
        val state = readState()
        val layerId = state.selectedLayerId ?: return
        val newLayers = LayerGroupSync.apply(state.layers, layerId, block)
        updateState { copy(layers = newLayers) }
    }

    protected inline fun updateActiveLayerWhen(
        crossinline predicate: (EditorLayer) -> Boolean,
        noinline block: (EditorLayer) -> EditorLayer,
    ) {
        updateActiveLayer { layer ->
            if (predicate(layer)) block(layer) else layer
        }
    }

    protected inline fun updateActiveLabelLayer(noinline block: (EditorLayer) -> EditorLayer) {
        updateActiveLayerWhen({ it.isLabelLayer }, block)
        shapeFitFlow.tryEmit(Unit)
        requestHistoryPush()
    }

    protected inline fun updateActiveFrameLayer(noinline block: (EditorLayer) -> EditorLayer) {
        val state = readState()
        val selectedId = state.selectedLayerId ?: return
        val selectedLayer = state.layers.find { it.id == selectedId } ?: return

        // If the selected layer is a label in a group, find and update the sibling frame layer
        val groupId = selectedLayer.groupId
        if (selectedLayer.isLabelLayer && groupId != null) {
            val frameLayer = state.layers.find { it.groupId == groupId && it.isFrameLayer }
            if (frameLayer != null) {
                val updatedFrame = block(frameLayer)
                val newLayers = state.layers.map { if (it.id == frameLayer.id) updatedFrame else it }
                updateState { copy(layers = newLayers) }
                requestHistoryPush()
                return
            }
        }

        // Otherwise (standalone frame layer, text layer, etc.), update the selected layer directly
        updateActiveLayerWhen({ it.id == selectedId }, block)
        requestHistoryPush()
    }
}
