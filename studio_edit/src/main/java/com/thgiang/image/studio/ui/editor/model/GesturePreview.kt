package com.thgiang.image.studio.ui.editor.model

/**
 * Ephemeral layer transforms while the user is dragging — kept outside [EditorState.layers]
 * so gesture ticks do not recompose the full editor chrome or re-persist state every 16ms.
 */
data class GesturePreview(
    val anchorLayerId: String,
    val layers: List<EditorLayer>,
) {
    fun layersForRender(base: List<EditorLayer>): List<EditorLayer> = layers
}
