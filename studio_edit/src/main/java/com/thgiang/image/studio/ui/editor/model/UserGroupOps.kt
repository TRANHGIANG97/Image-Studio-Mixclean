package com.thgiang.image.studio.ui.editor.model

import java.util.UUID
import kotlin.math.roundToInt

sealed class LayerListItem {
    data class Single(val layer: EditorLayer) : LayerListItem()
    data class Group(
        val userGroupId: String,
        val members: List<EditorLayer>,
        val composite: EditorLayer? = null,
    ) : LayerListItem()
}

/**
 * User-facing layer groups (distinct from FRAME+LABEL [groupId] composites).
 *
 * New groups are **container-based**: members remain on canvas with shared [userGroupId].
 * Legacy rasterized groups ([UserGroupRole.COMPOSITE] + [UserGroupBundle]) are still
 * supported for draft migration.
 */
object UserGroupOps {

    data class GroupPrepareResult(
        val groupId: String,
        val memberIds: Set<String>,
        val orderedMemberIds: List<String>,
    )

    /** All layer ids that should be selected when [layerId] is picked. */
    fun selectionMembers(layers: List<EditorLayer>, layerId: String): Set<String> {
        val layer = layers.find { it.id == layerId } ?: return setOf(layerId)
        if (layer.userGroupRole == UserGroupRole.COMPOSITE) {
            return setOf(layerId)
        }
        val frameLabel = SelectionState.expandGroup(layers, layerId)
        val userGroup = expandUserGroup(layers, layerId)
        return frameLabel + userGroup
    }

    fun expandUserGroup(layers: List<EditorLayer>, layerId: String): Set<String> {
        val layer = layers.find { it.id == layerId } ?: return setOf(layerId)
        val ugid = layer.userGroupId ?: return setOf(layerId)
        if (layer.userGroupRole == UserGroupRole.COMPOSITE) return setOf(layerId)
        return layers.filter { it.userGroupId == ugid }.map { it.id }.toSet()
    }

    fun canGroup(layers: List<EditorLayer>, ids: Set<String>): Boolean {
        val roots = SelectionState.selectionRoots(layers, ids)
        if (roots.size < 2) return false
        if (roots.any { id -> layers.find { it.id == id }?.userGroupRole == UserGroupRole.COMPOSITE }) {
            return false
        }
        val memberIds = roots.flatMap { selectionMembers(layers, it).toList() }.toSet()
        val distinctUserGroups = memberIds.mapNotNull { id ->
            layers.find { it.id == id }?.userGroupId
        }.distinct()
        return memberIds.size >= 2 && distinctUserGroups.size != 1
    }

    fun canUngroup(
        layers: List<EditorLayer>,
        ids: Set<String>,
        maps: UserGroupMaps = UserGroupMaps(),
    ): Boolean {
        val groupIds = ids.mapNotNull { id -> layers.find { it.id == id }?.userGroupId }.toSet()
        if (groupIds.isEmpty()) return false
        return groupIds.any { groupId ->
            GroupTransformOps.isContainerGroup(groupId, maps) ||
                GroupTransformOps.isLegacyCompositeGroup(groupId, maps) ||
                layers.any { it.userGroupId == groupId && it.userGroupRole == UserGroupRole.COMPOSITE }
        }
    }

    fun prepareGroup(layers: List<EditorLayer>, ids: Set<String>): GroupPrepareResult? {
        if (!canGroup(layers, ids)) return null
        val roots = SelectionState.selectionRoots(layers, ids)
        val memberIds = roots.flatMap { selectionMembers(layers, it).toList() }.toSet()
        val consolidated = consolidateMemberOrder(layers, memberIds)
        val orderedMembers = consolidated.filter { it.id in memberIds }
        val groupId = UUID.randomUUID().toString()
        return GroupPrepareResult(
            groupId = groupId,
            memberIds = memberIds,
            orderedMemberIds = orderedMembers.map { it.id },
        )
    }

    /**
     * Container group: tag members with [userGroupId], keep layers on canvas unchanged.
     */
    fun applyContainerGroup(
        layers: List<EditorLayer>,
        maps: UserGroupMaps,
        prep: GroupPrepareResult,
    ): Pair<List<EditorLayer>, UserGroupMaps> {
        val group = EditorUserGroup(
            id = prep.groupId,
            memberIds = prep.orderedMemberIds,
        )
        val updatedLayers = layers.map { layer ->
            if (layer.id in prep.memberIds) {
                layer.copy(userGroupId = prep.groupId, userGroupRole = null)
            } else {
                layer
            }
        }
        return updatedLayers to maps.copy(groups = maps.groups + (prep.groupId to group))
    }

    fun computeMemberContentBounds(members: List<EditorLayer>): LayerContentBounds? =
        computeUnionLayerBoundsForGrouping(members)

    fun computeMemberOrientedContentBounds(members: List<EditorLayer>): OrientedLayerContentBounds? =
        computeWorldAabbEnvelope(members)?.let { OrientedLayerContentBounds(it, rotationDeg = 0f) }

    /** Legacy raster composite — retained for migration tests only. */
    fun buildCompositeLayer(
        groupId: String,
        compositeLayerId: String,
        compositeUri: String,
        orientedBounds: OrientedLayerContentBounds,
    ): EditorLayer {
        val contentBounds = orientedBounds.bounds
        val w = contentBounds.width.roundToInt().coerceAtLeast(1)
        val h = contentBounds.height.roundToInt().coerceAtLeast(1)
        return EditorLayer(
            id = compositeLayerId,
            type = LayerType.IMAGE,
            userGroupId = groupId,
            userGroupRole = UserGroupRole.COMPOSITE,
            product = EditorProduct(
                originalUriString = compositeUri,
                foregroundUriString = compositeUri,
                baseWidth = w,
                baseHeight = h,
            ),
            shapeWidthPx = contentBounds.width,
            shapeHeightPx = contentBounds.height,
            cropRatio = CropRatio.ORIGINAL,
            viewport = EditorViewport(
                offsetX = contentBounds.centerX,
                offsetY = contentBounds.centerY,
                scale = 1f,
                rotation = orientedBounds.rotationDeg,
            ),
        )
    }

    fun ungroup(
        layers: List<EditorLayer>,
        maps: UserGroupMaps,
        ids: Set<String>,
    ): Pair<List<EditorLayer>, UserGroupMaps> {
        val groupIds = ids.mapNotNull { id ->
            layers.find { it.id == id }?.userGroupId
        }.toSet()
        if (groupIds.isEmpty()) return layers to maps

        var resultLayers = layers
        var remainingGroups = maps.groups.toMutableMap()
        var remainingBundles = maps.bundles.toMutableMap()

        for (groupId in groupIds) {
            when {
                groupId in remainingGroups -> {
                    resultLayers = resultLayers.map { layer ->
                        if (layer.userGroupId == groupId) {
                            layer.copy(userGroupId = null, userGroupRole = null)
                        } else {
                            layer
                        }
                    }
                    remainingGroups.remove(groupId)
                }
                groupId in remainingBundles -> {
                    val bundle = remainingBundles.remove(groupId)!!
                    resultLayers = resultLayers.filter { it.id != bundle.compositeLayerId }
                    val restored = bundle.memberSnapshots.map { snapshot ->
                        snapshot.copy(userGroupId = null, userGroupRole = null)
                    }
                    val insertAt = bundle.insertIndex.coerceIn(0, resultLayers.size)
                    resultLayers = resultLayers.toMutableList().apply {
                        addAll(insertAt, restored)
                    }
                }
                else -> {
                    resultLayers = resultLayers.map { layer ->
                        if (layer.userGroupId == groupId) {
                            layer.copy(userGroupId = null, userGroupRole = null)
                        } else {
                            layer
                        }
                    }
                }
            }
        }
        return resultLayers to UserGroupMaps(
            groups = remainingGroups,
            bundles = remainingBundles,
        )
    }

    fun duplicateUserGroup(
        layers: List<EditorLayer>,
        maps: UserGroupMaps,
        userGroupId: String,
    ): Triple<List<EditorLayer>, UserGroupMaps, String?> {
        val container = maps.groups[userGroupId]
        if (container != null) {
            val members = container.memberIds.mapNotNull { id -> layers.find { it.id == id } }
            if (members.isEmpty()) return Triple(layers, maps, null)

            val newGroupId = UUID.randomUUID().toString()
            val offset = androidx.compose.ui.geometry.Offset(50f, 50f)
            var result = layers
            val newMemberIds = mutableListOf<String>()
            for (member in members) {
                val (dupLayers, pid) = LayerGroupOps.duplicate(result, member.id)
                result = dupLayers.map { layer ->
                    if (layer.id == pid) {
                        layer.copy(
                            userGroupId = newGroupId,
                            userGroupRole = null,
                            viewport = layer.viewport.withOffset(layer.viewport.offset + offset),
                        )
                    } else {
                        layer
                    }
                }
                pid?.let { newMemberIds += it }
            }
            val newMaps = maps.copy(
                groups = maps.groups + (
                    newGroupId to EditorUserGroup(
                        id = newGroupId,
                        memberIds = newMemberIds,
                    )
                    ),
            )
            return Triple(result, newMaps, newMemberIds.firstOrNull())
        }

        val bundle = maps.bundles[userGroupId]
        if (bundle != null) {
            val composite = layers.find { it.id == bundle.compositeLayerId }
                ?: return Triple(layers, maps, null)
            val newGroupId = UUID.randomUUID().toString()
            val newCompositeId = UUID.randomUUID().toString()
            val offset = androidx.compose.ui.geometry.Offset(50f, 50f)
            val duplicatedSnapshots = bundle.memberSnapshots.map { member ->
                member.copy(
                    id = UUID.randomUUID().toString(),
                    userGroupId = null,
                    userGroupRole = null,
                    viewport = member.viewport.withOffset(member.viewport.offset + offset),
                )
            }
            val newComposite = composite.copy(
                id = newCompositeId,
                userGroupId = newGroupId,
                viewport = composite.viewport.withOffset(composite.viewport.offset + offset),
            )
            val newBundle = UserGroupBundle(
                groupId = newGroupId,
                memberSnapshots = duplicatedSnapshots,
                compositeLayerId = newCompositeId,
                insertIndex = layers.size,
            )
            return Triple(
                layers + newComposite,
                maps.copy(bundles = maps.bundles + (newGroupId to newBundle)),
                newCompositeId,
            )
        }

        val members = layers.filter { it.userGroupId == userGroupId }
        if (members.isEmpty()) return Triple(layers, maps, null)

        val newUserGroupId = UUID.randomUUID().toString()
        var result = layers
        var primaryId: String? = null
        val newMemberIds = mutableListOf<String>()
        for (member in members) {
            val (dupLayers, pid) = LayerGroupOps.duplicate(result, member.id)
            result = dupLayers.map { layer ->
                if (layer.id == pid) layer.copy(userGroupId = newUserGroupId) else layer
            }
            pid?.let { newMemberIds += it }
            if (primaryId == null) primaryId = pid
        }
        return Triple(
            result,
            maps.copy(
                groups = maps.groups + (
                    newUserGroupId to EditorUserGroup(
                        id = newUserGroupId,
                        memberIds = newMemberIds,
                    )
                    ),
            ),
            primaryId,
        )
    }

    fun consolidateMemberOrder(layers: List<EditorLayer>, memberIds: Set<String>): List<EditorLayer> {
        if (memberIds.isEmpty()) return layers
        val members = layers.filter { it.id in memberIds }
        if (members.isEmpty()) return layers
        val without = layers.filter { it.id !in memberIds }
        val lastIndex = layers.indexOfLast { it.id in memberIds }
        val insertAt = (lastIndex - members.size + 1).coerceIn(0, without.size)
        return without.toMutableList().apply { addAll(insertAt, members) }
    }

    fun buildLayerListItems(
        layers: List<EditorLayer>,
        maps: UserGroupMaps = UserGroupMaps(),
    ): List<LayerListItem> {
        val items = mutableListOf<LayerListItem>()
        val consumedGroups = mutableSetOf<String>()

        for (layer in layers.filter { it.shouldShowInObjectList() }) {
            when {
                layer.userGroupRole == UserGroupRole.COMPOSITE -> {
                    val bundle = layer.userGroupId?.let { maps.bundles[it] }
                    items += LayerListItem.Group(
                        userGroupId = layer.userGroupId.orEmpty(),
                        members = bundle?.memberSnapshots.orEmpty(),
                        composite = layer,
                    )
                }
                layer.userGroupId != null -> {
                    val groupId = layer.userGroupId!!
                    if (groupId in consumedGroups) continue
                    consumedGroups += groupId
                    val members = layers.filter {
                        it.userGroupId == groupId && it.shouldShowInObjectList()
                    }
                    items += LayerListItem.Group(
                        userGroupId = groupId,
                        members = members,
                        composite = null,
                    )
                }
                else -> items += LayerListItem.Single(layer)
            }
        }
        return items
    }

    fun firstMemberId(
        layers: List<EditorLayer>,
        groupId: String,
        maps: UserGroupMaps,
    ): String? {
        maps.groups[groupId]?.memberIds?.firstOrNull()?.let { return it }
        maps.bundles[groupId]?.compositeLayerId?.let { return it }
        return layers.firstOrNull { it.userGroupId == groupId }?.id
    }
}
