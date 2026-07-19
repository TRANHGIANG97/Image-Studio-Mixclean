package com.thgiang.image.studio.ui.editor.model

/**
 * Preserves original member layers for [UserGroupOps.ungroup] restore.
 * The visible [compositeLayerId] is an IMAGE layer rasterized from members.
 */
data class UserGroupBundle(
    val groupId: String,
    val memberSnapshots: List<EditorLayer>,
    val compositeLayerId: String,
    val insertIndex: Int,
) : java.io.Serializable

enum class UserGroupRole {
    /** Single visible IMAGE layer representing a user group on canvas and in the layer list. */
    COMPOSITE,
}
