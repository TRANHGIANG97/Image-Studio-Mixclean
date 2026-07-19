package com.thgiang.image.studio.ui.editor.model

/**
 * Envelope and membership helpers for container-based user groups.
 */
object GroupTransformOps {

    fun membersOf(
        layers: List<EditorLayer>,
        group: EditorUserGroup,
    ): List<EditorLayer> = group.memberIds.mapNotNull { id -> layers.find { it.id == id } }

    fun envelope(
        layers: List<EditorLayer>,
        group: EditorUserGroup,
    ): LayerContentBounds? = computeWorldAabbEnvelope(membersOf(layers, group))

    fun envelope(
        layers: List<EditorLayer>,
        memberIds: Collection<String>,
    ): LayerContentBounds? {
        val members = memberIds.mapNotNull { id -> layers.find { it.id == id } }
        return computeWorldAabbEnvelope(members)
    }

    fun isContainerGroup(
        groupId: String?,
        maps: UserGroupMaps,
    ): Boolean = groupId != null && groupId in maps.groups

    fun isLegacyCompositeGroup(
        groupId: String?,
        maps: UserGroupMaps,
    ): Boolean = groupId != null && groupId in maps.bundles
}
