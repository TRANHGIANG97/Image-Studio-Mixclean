package com.thgiang.image.studio.ui.editor.model

import androidx.compose.ui.geometry.Offset
import com.thgiang.image.studio.ui.editor.label.geometry.EditorShapeGeometry
import java.util.UUID

val EditorLayer.isVectorContentLayer: Boolean
    get() = type == LayerType.SHAPE ||
        type == LayerType.TEXT ||
        type == LayerType.SHAPE_TEXT

fun List<EditorLayer>.groupMembers(layer: EditorLayer): List<EditorLayer> {
    val gid = layer.groupId ?: return listOf(layer)
    return filter { it.groupId == gid }
}

fun List<EditorLayer>.frameInGroup(layer: EditorLayer): EditorLayer? {
    val gid = layer.groupId ?: return null
    return firstOrNull { it.groupId == gid && it.groupRole == LayerGroupRole.FRAME }
}

fun List<EditorLayer>.labelInGroup(layer: EditorLayer): EditorLayer? {
    val gid = layer.groupId ?: return null
    return firstOrNull { it.groupId == gid && it.groupRole == LayerGroupRole.LABEL }
}

fun List<EditorLayer>.isSelectedAsGroup(selectedLayerId: String?, candidateId: String): Boolean {
    if (selectedLayerId == null) return false
    if (selectedLayerId == candidateId) return true
    val selected = find { it.id == selectedLayerId } ?: return false
    val gid = selected.groupId ?: return false
    val candidate = find { it.id == candidateId } ?: return false
    return candidate.groupId == gid
}

fun EditorLayer.shouldShowInObjectList(): Boolean =
    groupRole != LayerGroupRole.FRAME

fun EditorLayer.splitToGroup(): List<EditorLayer> {
    val gid = groupId ?: UUID.randomUUID().toString()
    val frameId = when (groupRole) {
        LayerGroupRole.FRAME -> id
        else -> UUID.randomUUID().toString()
    }
    val labelId = when (groupRole) {
        LayerGroupRole.LABEL -> id
        LayerGroupRole.FRAME -> UUID.randomUUID().toString()
        null -> id
    }

    val frame = copy(
        id = frameId,
        type = LayerType.SHAPE,
        groupId = gid,
        groupRole = LayerGroupRole.FRAME,
        text = "",
        textForm = TextFormEffect(),
        textColorGradient = null,
    )
    val label = copy(
        id = labelId,
        type = LayerType.TEXT,
        groupId = gid,
        groupRole = LayerGroupRole.LABEL,
        shapeColorArgb = 0x00FFFFFF,
        fillGradient = null,
        strokeColorArgb = null,
        strokeWidthPx = 0f,
        strokeDashArray = emptyList(),
        strokeDashGapPx = 6f,
        appearance = appearance.copy(shadowIntensity = 0f),
    )
    return listOf(frame, label)
}

object EditorLayerNormalizer {
    /**
     * Defensive against Gson/R8 draft restores where [layers] may contain
     * LinkedTreeMap (or other) elements instead of [EditorLayer].
     */
    fun normalize(layers: List<EditorLayer>): List<EditorLayer> {
        val raw: List<*> = try {
            layers
        } catch (e: ClassCastException) {
            emptyList<Any>()
        }
        val result = ArrayList<EditorLayer>(raw.size)
        for (item in raw) {
            val layer = item as? EditorLayer ?: continue
            try {
                result.addAll(layer.normalizeLayer())
            } catch (e: Exception) {
                // Skip corrupt layer rather than crashing editor open.
            }
        }
        return result
    }
}

private fun EditorLayer.normalizeLayer(): List<EditorLayer> {
    when (type) {
        LayerType.SHAPE, LayerType.TEXT -> return listOf(this)
        LayerType.SHAPE_TEXT -> Unit
        else -> return listOf(this)
    }

    val isTextOnly = EditorShapeGeometry.isTextOnlyShape(shapeType)
    val isFrameOnly = text.isBlank() && !isTextOnly
    if (isFrameOnly) return listOf(copy(type = LayerType.SHAPE))
    if (isTextOnly || !hasVisibleFrameGeometry) return listOf(copy(type = LayerType.TEXT))
    if (groupId != null && groupRole != null) {
        return when (groupRole) {
            LayerGroupRole.FRAME -> listOf(copy(type = LayerType.SHAPE))
            LayerGroupRole.LABEL -> listOf(copy(type = LayerType.TEXT))
        }
    }
    return splitToGroup()
}

object LayerGroupSync {
    /**
     * Apply [transform] to the selected layer.
     * For FRAME+LABEL groups, also sync [EditorViewport] and box size onto the sibling (I3).
     */
    fun apply(
        layers: List<EditorLayer>,
        selectedId: String,
        transform: (EditorLayer) -> EditorLayer,
    ): List<EditorLayer> {
        val selected = layers.find { it.id == selectedId } ?: return layers
        val updatedSelected = transform(selected)
        val gid = selected.groupId
        if (gid == null || selected.groupRole == null) {
            return layers.map { if (it.id == selectedId) updatedSelected else it }
        }
        return layers.map { layer ->
            when {
                layer.id == selectedId -> updatedSelected
                layer.groupId == gid -> layer.copy(
                    viewport = updatedSelected.viewport,
                    shapeWidthPx = updatedSelected.shapeWidthPx,
                    shapeHeightPx = updatedSelected.shapeHeightPx,
                    // I3+: both members share frame geometry (shapeType used for fit + render).
                    shapeType = updatedSelected.shapeType,
                    cornerRadiusX = updatedSelected.cornerRadiusX,
                    cornerRadiusY = updatedSelected.cornerRadiusY,
                    pathData = updatedSelected.pathData,
                    polygonPoints = updatedSelected.polygonPoints,
                )
                else -> layer
            }
        }
    }

    /** Force both group members to share transform + box (call after fit). */
    fun syncGroupGeometry(
        layers: List<EditorLayer>,
        groupId: String,
        sourceId: String,
    ): List<EditorLayer> {
        val source = layers.find { it.id == sourceId } ?: return layers
        return layers.map { layer ->
            if (layer.groupId != groupId) layer
            else layer.copy(
                viewport = source.viewport,
                shapeWidthPx = source.shapeWidthPx,
                shapeHeightPx = source.shapeHeightPx,
                shapeType = source.shapeType,
                cornerRadiusX = source.cornerRadiusX,
                cornerRadiusY = source.cornerRadiusY,
                pathData = source.pathData,
                polygonPoints = source.polygonPoints,
            )
        }
    }
}

object LayerGroupOps {
    /**
     * Within a FRAME+LABEL group, resolve which sibling should be selected for the active tool.
     * Standalone layers are returned unchanged.
     */
    fun retargetForTool(
        layers: List<EditorLayer>,
        selectedId: String?,
        preferFrame: Boolean,
    ): String? {
        val layer = layers.find { it.id == selectedId } ?: return selectedId
        if (layer.groupId == null || layer.groupRole == null) return selectedId
        val target = if (preferFrame) {
            layers.frameInGroup(layer)
        } else {
            layers.labelInGroup(layer)
        }
        return target?.id ?: selectedId
    }

    fun duplicate(
        layers: List<EditorLayer>,
        selectedId: String,
    ): Pair<List<EditorLayer>, String?> {
        val layer = layers.find { it.id == selectedId } ?: return layers to null
        val group = layer.groupId?.let { gid ->
            layers.filter { it.groupId == gid }
        } ?: listOf(layer)
        val newGroupId = if (group.size > 1) UUID.randomUUID().toString() else null
        val idMap = group.associate { it.id to UUID.randomUUID().toString() }
        val offset = Offset(50f, 50f)
        val duplicated = group.map { member ->
            member.copy(
                id = idMap.getValue(member.id),
                groupId = newGroupId,
                viewport = member.viewport.withOffset(member.viewport.offset + offset),
            )
        }
        val primaryId = duplicated
            .firstOrNull { it.groupRole == LayerGroupRole.LABEL }
            ?.id
            ?: duplicated.first().id
        return (layers + duplicated) to primaryId
    }

    fun deleteIds(layers: List<EditorLayer>, selectedId: String): Set<String> {
        val layer = layers.find { it.id == selectedId } ?: return emptySet()
        val gid = layer.groupId
        return if (gid != null) {
            layers.filter { it.groupId == gid }.map { it.id }.toSet()
        } else {
            setOf(selectedId)
        }
    }

    fun collapseHits(
        hits: List<EditorLayer>,
        allLayers: List<EditorLayer>,
        preferFrame: Boolean = false,
    ): List<EditorLayer> {
        val seenGroups = mutableSetOf<String>()
        val result = mutableListOf<EditorLayer>()
        for (hit in hits) {
            val gid = hit.groupId
            if (gid == null) {
                result += hit
                continue
            }
            if (gid in seenGroups) continue
            seenGroups += gid
            val preferred = if (preferFrame) {
                allLayers.frameInGroup(hit) ?: hit
            } else {
                allLayers.labelInGroup(hit) ?: hit
            }
            result += preferred
        }
        return result
    }
}
