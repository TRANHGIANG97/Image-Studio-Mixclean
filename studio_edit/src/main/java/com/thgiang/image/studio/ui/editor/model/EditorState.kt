package com.thgiang.image.studio.ui.editor.model

data class EditorState(
    val template: EditorTemplate = EditorTemplate(),
    val layers: List<EditorLayer> = emptyList(),
    val selectedLayerId: String? = null,
    /** Multi-select set (Phase 5). Always includes [selectedLayerId] when non-null. */
    val selectedLayerIds: Set<String> = emptySet(),
    /** Layer currently in inline text-edit mode (thin frame, no BB). */
    val editingLayerId: String? = null,
    /** Inline caret / selection while editing (exclusive end). */
    val inlineSelectionStart: Int = 0,
    val inlineSelectionEnd: Int = 0,
    val selectedTool: EditorTool? = null,
    val isExporting: Boolean = false,
    val isSavingDraft: Boolean = false,
    val exportResult: android.net.Uri? = null,
    val draftSavedAt: Long? = null,
    val errorMessage: String? = null,
    val showOverlay: Boolean = false,
    val showBoundingBox: Boolean = false
) : java.io.Serializable {
    val canExport: Boolean
        get() = layers.any { it.type == LayerType.IMAGE && it.product.foregroundUriString != null } && !isExporting && template.loaded

    val labelPhase: LabelInteractionPhase
        get() = LabelInteractionState.phase(selectedLayerId, editingLayerId)
}
