package com.thgiang.image.studio.ui.editor.model

/**
 * Multi-select helpers (Phase 5). Keeps [EditorState.selectedLayerId] as the gesture/tool
 * anchor while [EditorState.selectedLayerIds] holds the full selection set.
 */
object SelectionState {

    fun expandGroup(layers: List<EditorLayer>, layerId: String): Set<String> {
        val layer = layers.find { it.id == layerId } ?: return setOf(layerId)
        val gid = layer.groupId ?: return setOf(layerId)
        return layers.filter { it.groupId == gid }.map { it.id }.toSet()
    }

    fun effectiveIds(state: EditorState): Set<String> =
        when {
            state.selectedLayerIds.isNotEmpty() -> state.selectedLayerIds
            state.selectedLayerId != null -> setOf(state.selectedLayerId)
            else -> emptySet()
        }

    fun isSelected(
        layers: List<EditorLayer>,
        selectedLayerId: String?,
        selectedLayerIds: Set<String>,
        candidateId: String,
    ): Boolean {
        if (candidateId in selectedLayerIds) return true
        if (selectedLayerIds.isEmpty() && selectedLayerId != null) {
            return layers.isSelectedAsGroup(selectedLayerId, candidateId)
        }
        return false
    }

    fun selectionRoots(layers: List<EditorLayer>, ids: Set<String>): List<String> {
        val seenGroups = mutableSetOf<String>()
        val roots = mutableListOf<String>()
        for (id in ids) {
            val layer = layers.find { it.id == id } ?: continue
            val gid = layer.groupId
            if (gid != null) {
                if (gid in seenGroups) continue
                seenGroups += gid
            }
            roots += id
        }
        return roots
    }

    fun normalize(
        selectedLayerId: String?,
        selectedLayerIds: Set<String>,
        layers: List<EditorLayer>,
    ): Pair<String?, Set<String>> {
        val validIds = layers.map { it.id }.toSet()
        val filtered = selectedLayerIds.filter { it in validIds }.toSet()
        val primary = selectedLayerId?.takeIf { it in validIds }
        val merged = when {
            primary != null -> expandGroup(layers, primary) + filtered
            filtered.isNotEmpty() -> filtered.flatMap { expandGroup(layers, it) }.toSet()
            else -> emptySet()
        }
        val anchor = primary ?: merged.firstOrNull()
        return anchor to merged
    }

    fun singleSelect(layers: List<EditorLayer>, layerId: String?): Pair<String?, Set<String>> =
        if (layerId == null) null to emptySet()
        else layerId to expandGroup(layers, layerId)

    fun toggle(
        layers: List<EditorLayer>,
        currentIds: Set<String>,
        anchorId: String?,
        layerId: String,
    ): Pair<String?, Set<String>> {
        val expanded = expandGroup(layers, layerId)
        val base = if (currentIds.isNotEmpty()) currentIds
        else anchorId?.let { expandGroup(layers, it) } ?: emptySet()

        val newIds = if (expanded.any { it in base }) base - expanded else base + expanded
        if (newIds.isEmpty()) return null to emptySet()
        val anchor = if (anchorId != null && anchorId in newIds) anchorId else newIds.first()
        return anchor to newIds
    }

    fun deleteIds(layers: List<EditorLayer>, ids: Set<String>): Set<String> =
        ids.flatMap { LayerGroupOps.deleteIds(layers, it) }.toSet()
}
