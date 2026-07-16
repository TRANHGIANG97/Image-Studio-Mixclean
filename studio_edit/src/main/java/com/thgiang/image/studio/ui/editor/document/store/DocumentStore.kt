package com.thgiang.image.studio.ui.editor.document.store

import android.content.Context
import com.thgiang.image.studio.ui.editor.document.adapter.EditorLayerBridge
import com.thgiang.image.studio.ui.editor.document.command.DocumentCommand
import com.thgiang.image.studio.ui.editor.document.command.DocumentReducer
import com.thgiang.image.studio.ui.editor.document.layout.LayoutEngine
import com.thgiang.image.studio.ui.editor.document.model.DocumentSnapshot
import com.thgiang.image.studio.ui.editor.document.model.SceneNode
import com.thgiang.image.studio.ui.editor.document.rules.LayoutIntent
import com.thgiang.image.studio.ui.editor.model.EditorLayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single mutation entry (I1). Holds immutable snapshots + undo stack (I7).
 *
 * [shadowLegacyLayers] mirrors document → EditorLayer list for strangler UI.
 */
class DocumentStore(
    private val appContext: Context,
    initial: DocumentSnapshot = DocumentSnapshot(),
) {
    private val _snapshot = MutableStateFlow(initial)
    val snapshot: StateFlow<DocumentSnapshot> = _snapshot.asStateFlow()

    private val undoStack = ArrayDeque<DocumentSnapshot>()
    private val redoStack = ArrayDeque<DocumentSnapshot>()

    private val _legacyLayers = MutableStateFlow<List<EditorLayer>>(emptyList())
    val legacyLayers: StateFlow<List<EditorLayer>> = _legacyLayers.asStateFlow()

    /** Last layout snapshots keyed by node id (I6). */
    private val _layouts = MutableStateFlow<Map<String, LayoutEngine.TextLayoutSnapshot>>(emptyMap())
    val layouts: StateFlow<Map<String, LayoutEngine.TextLayoutSnapshot>> = _layouts.asStateFlow()

    private val _warnings = MutableStateFlow<List<String>>(emptyList())
    val warnings: StateFlow<List<String>> = _warnings.asStateFlow()

    fun current(): DocumentSnapshot = _snapshot.value

    fun loadFromLegacyLayers(
        layers: List<EditorLayer>,
        templateWidth: Float,
        templateHeight: Float,
        selectedLayerId: String?,
        pushHistory: Boolean = false,
    ) {
        val doc = EditorLayerBridge.fromEditorLayers(
            layers = layers,
            templateWidth = templateWidth,
            templateHeight = templateHeight,
            selectedLayerId = selectedLayerId,
        )
        if (pushHistory) {
            commit(doc, measureAll = false)
        } else {
            _snapshot.value = doc
            _legacyLayers.value = layers
            _layouts.value = emptyMap()
            undoStack.clear()
            redoStack.clear()
        }
    }

    fun dispatch(command: DocumentCommand, recordHistory: Boolean = false) {
        val before = _snapshot.value
        val reduced = DocumentReducer.reduce(before, command)
        var next = reduced.snapshot
        val layoutMap = _layouts.value.toMutableMap()
        val intent = reduced.layoutIntent

        if (intent != null && reduced.layoutNodeIds.isNotEmpty()) {
            val legacy = EditorLayerBridge.toEditorLayers(next, _legacyLayers.value)
            for (nodeId in reduced.layoutNodeIds) {
                val node = next.node(nodeId) ?: continue
                val bridge = EditorLayerBridge.findBridgeLayer(legacy, node) ?: continue
                val (updatedNode, snap) = LayoutEngine.measureNode(appContext, node, bridge, intent)
                next = next.copy(
                    nodes = next.nodes.map { if (it.id == nodeId) updatedNode else it },
                )
                layoutMap[nodeId] = snap
            }
            // Re-sync legacy from measured nodes
            _legacyLayers.value = EditorLayerBridge.toEditorLayers(next, legacy)
        } else {
            _legacyLayers.value = EditorLayerBridge.toEditorLayers(next, _legacyLayers.value)
        }

        _layouts.value = layoutMap
        _warnings.value = reduced.warnings
        if (recordHistory) {
            undoStack.addLast(before)
            if (undoStack.size > MAX_HISTORY) undoStack.removeFirst()
            redoStack.clear()
        }
        _snapshot.value = next
    }

    fun undo(): Boolean {
        val prev = undoStack.removeLastOrNull() ?: return false
        redoStack.addLast(_snapshot.value)
        _snapshot.value = prev
        _legacyLayers.value = EditorLayerBridge.toEditorLayers(prev, _legacyLayers.value)
        return true
    }

    fun redo(): Boolean {
        val next = redoStack.removeLastOrNull() ?: return false
        undoStack.addLast(_snapshot.value)
        _snapshot.value = next
        _legacyLayers.value = EditorLayerBridge.toEditorLayers(next, _legacyLayers.value)
        return true
    }

    fun canUndo(): Boolean = undoStack.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    private fun commit(doc: DocumentSnapshot, measureAll: Boolean) {
        undoStack.addLast(_snapshot.value)
        redoStack.clear()
        var next = doc
        if (measureAll) {
            val legacy = EditorLayerBridge.toEditorLayers(next, _legacyLayers.value)
            val layoutMap = mutableMapOf<String, LayoutEngine.TextLayoutSnapshot>()
            next.nodes.forEach { node ->
                if (node is SceneNode.LegacyPassthrough) return@forEach
                val bridge = EditorLayerBridge.findBridgeLayer(legacy, node) ?: return@forEach
                val (updated, snap) = LayoutEngine.measureNode(
                    appContext, node, bridge, LayoutIntent.StyleOrCaseChange,
                )
                next = next.copy(nodes = next.nodes.map { if (it.id == node.id) updated else it })
                layoutMap[node.id] = snap
            }
            _layouts.value = layoutMap
            _legacyLayers.value = EditorLayerBridge.toEditorLayers(next, legacy)
        }
        _snapshot.value = next
    }

    companion object {
        private const val MAX_HISTORY = 80
    }
}
