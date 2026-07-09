package com.thgiang.image.studio.ui.editor.model

/** UX phase for label layers — see Canva-style flow (Committed / Selected / Editing). */
enum class LabelInteractionPhase {
    /** Text on canvas only — no frame, no BB (ảnh 3). */
    Committed,
    /** BB with handles, no keyboard (ảnh 4). */
    Selected,
    /** Thin text frame + keyboard, no BB (ảnh 2 / 5). */
    Editing,
}

object LabelInteractionState {
    fun phase(selectedLayerId: String?, editingLayerId: String?): LabelInteractionPhase =
        when {
            editingLayerId != null -> LabelInteractionPhase.Editing
            selectedLayerId != null -> LabelInteractionPhase.Selected
            else -> LabelInteractionPhase.Committed
        }

    /** Keep [selectedLayerId] and [editingLayerId] consistent with existing layers. */
    fun normalize(
        selectedLayerId: String?,
        editingLayerId: String?,
        layers: List<EditorLayer>,
    ): Pair<String?, String?> {
        val validIds = layers.map { it.id }.toSet()
        var edit = editingLayerId?.takeIf { it in validIds }
        var sel = selectedLayerId?.takeIf { it in validIds }
        if (edit != null) {
            sel = edit
        } else if (sel == null) {
            edit = null
        }
        return sel to edit
    }

    fun onSelectLayer(layerId: String?): Pair<String?, String?> =
        if (layerId == null) null to null else layerId to null

    fun onStartTextEdit(layerId: String): Pair<String?, String?> = layerId to layerId

    fun onDeselectLayer(): Pair<String?, String?> = null to null

    fun onFinishTextEdit(
        selectedLayerId: String?,
        editingLayerId: String?,
    ): Pair<String?, String?> = selectedLayerId to null
}
