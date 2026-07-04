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
        appearance = appearance.copy(shadowIntensity = 0f),
    )
    return listOf(frame, label)
}

object EditorLayerNormalizer {
    fun normalize(layers: List<EditorLayer>): List<EditorLayer> =
        layers.flatMap { it.normalizeLayer() }
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
    fun apply(
        layers: List<EditorLayer>,
        selectedId: String,
        transform: (EditorLayer) -> EditorLayer,
    ): List<EditorLayer> {
        val selected = layers.find { it.id == selectedId } ?: return layers
        val transformed = transform(selected)
        val gid = selected.groupId ?: return layers.map { if (it.id == selectedId) transformed else it }

        return layers.map { layer ->
            when {
                layer.id == selectedId -> transformed
                layer.groupId == gid -> syncSibling(transformed, layer, selected)
                else -> layer
            }
        }
    }

    private fun syncSibling(
        primary: EditorLayer,
        sibling: EditorLayer,
        original: EditorLayer,
    ): EditorLayer {
        var synced = sibling.copy(viewport = primary.viewport)
        if (
            primary.shapeWidthPx != original.shapeWidthPx ||
            primary.shapeHeightPx != original.shapeHeightPx
        ) {
            synced = synced.copy(
                shapeWidthPx = primary.shapeWidthPx,
                shapeHeightPx = primary.shapeHeightPx,
            )
        }
        if (primary.shapeType != original.shapeType) {
            synced = synced.copy(
                shapeType = primary.shapeType,
                cornerRadiusX = primary.cornerRadiusX,
                cornerRadiusY = primary.cornerRadiusY,
                pathData = primary.pathData,
                polygonPoints = primary.polygonPoints,
            )
        }
        return synced
    }
}

object LayerGroupOps {
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

    fun collapseHits(hits: List<EditorLayer>, allLayers: List<EditorLayer>): List<EditorLayer> {
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
            val label = allLayers.labelInGroup(hit)
            result += label ?: hit
        }
        return result
    }
}
