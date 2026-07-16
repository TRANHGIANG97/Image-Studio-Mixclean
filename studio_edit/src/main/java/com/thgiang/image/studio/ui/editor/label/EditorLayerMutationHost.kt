package com.thgiang.image.studio.ui.editor.label

import com.thgiang.image.studio.ui.editor.model.EditorLayer
import com.thgiang.image.studio.ui.editor.model.EditorState
import com.thgiang.image.studio.ui.editor.model.LayerGroupSync
import com.thgiang.image.studio.ui.editor.model.isFrameLayer
import com.thgiang.image.studio.ui.editor.model.isLabelLayer

/**
 * Shared mutation helpers for label/shape delegates (Document fallback path).
 *
 * Box sizing must be applied synchronously in the mutation block (or via Document
 * [com.thgiang.image.studio.ui.editor.document.layout.LayoutEngine]) — there is no
 * debounced `shapeFitFlow` dual-path.
 */
open class EditorLayerMutationHost(
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

    protected inline fun updateActiveLabelLayer(
        noinline block: (EditorLayer) -> EditorLayer,
    ) {
        updateActiveLayerWhen({ it.isLabelLayer }, block)
        requestHistoryPush()
    }

    protected inline fun updateActiveFrameLayer(
        noinline block: (EditorLayer) -> EditorLayer,
    ) {
        val state = readState()
        val selectedId = state.selectedLayerId ?: return
        val selectedLayer = state.layers.find { it.id == selectedId } ?: return

        // If the selected layer is a label in a group, mutate the sibling FRAME and sync geometry (I3).
        val groupId = selectedLayer.groupId
        if (selectedLayer.isLabelLayer && groupId != null) {
            val frameLayer = state.layers.find { it.groupId == groupId && it.isFrameLayer }
            if (frameLayer != null) {
                val newLayers = LayerGroupSync.apply(state.layers, frameLayer.id, block)
                updateState { copy(layers = newLayers) }
                requestHistoryPush()
                return
            }
        }

        updateActiveLayerWhen({ it.id == selectedId }, block)
        requestHistoryPush()
    }
}
