package com.thgiang.image.studio.ui.editor.model

/**
 * Non-destructive user group — members stay as individual [EditorLayer]s on the canvas.
 * Transforms are applied to each member (multi-select) while grouped; no rasterization.
 */
data class EditorUserGroup(
    val id: String,
    val memberIds: List<String>,
) : java.io.Serializable

data class UserGroupMaps(
    val groups: Map<String, EditorUserGroup> = emptyMap(),
    /** Legacy rasterized groups — kept for draft migration only. */
    val bundles: Map<String, UserGroupBundle> = emptyMap(),
) : java.io.Serializable
