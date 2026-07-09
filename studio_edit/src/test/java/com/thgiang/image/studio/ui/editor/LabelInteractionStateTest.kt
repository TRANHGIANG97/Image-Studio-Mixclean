package com.thgiang.image.studio.ui.editor

import com.thgiang.image.studio.ui.editor.model.EditorLayer
import com.thgiang.image.studio.ui.editor.model.LabelInteractionPhase
import com.thgiang.image.studio.ui.editor.model.LabelInteractionState
import com.thgiang.image.studio.ui.editor.model.LayerGroupRole
import com.thgiang.image.studio.ui.editor.model.LayerType
import org.junit.Assert.assertEquals
import org.junit.Test

class LabelInteractionStateTest {

    private val layers = listOf(
        EditorLayer(id = "label-1", type = LayerType.TEXT, groupRole = LayerGroupRole.LABEL),
        EditorLayer(id = "frame-1", type = LayerType.SHAPE),
    )

    @Test
    fun `phase is Committed when nothing selected`() {
        assertEquals(LabelInteractionPhase.Committed, LabelInteractionState.phase(null, null))
    }

    @Test
    fun `phase is Selected when layer selected but not editing`() {
        assertEquals(LabelInteractionPhase.Selected, LabelInteractionState.phase("label-1", null))
    }

    @Test
    fun `phase is Editing when editingLayerId set`() {
        assertEquals(LabelInteractionPhase.Editing, LabelInteractionState.phase("label-1", "label-1"))
    }

    @Test
    fun `onSelectLayer clears editing`() {
        val (sel, edit) = LabelInteractionState.onSelectLayer("label-1")
        assertEquals("label-1", sel)
        assertEquals(null, edit)
    }

    @Test
    fun `onSelectLayer null deselects`() {
        val (sel, edit) = LabelInteractionState.onSelectLayer(null)
        assertEquals(null, sel)
        assertEquals(null, edit)
    }

    @Test
    fun `onStartTextEdit selects same layer`() {
        val (sel, edit) = LabelInteractionState.onStartTextEdit("label-1")
        assertEquals("label-1", sel)
        assertEquals("label-1", edit)
    }

    @Test
    fun `onFinishTextEdit keeps selection clears editing`() {
        val (sel, edit) = LabelInteractionState.onFinishTextEdit("label-1", "label-1")
        assertEquals("label-1", sel)
        assertEquals(null, edit)
    }

    @Test
    fun `normalize drops stale layer ids`() {
        val (sel, edit) = LabelInteractionState.normalize("gone", "gone", layers)
        assertEquals(null, sel)
        assertEquals(null, edit)
    }

    @Test
    fun `normalize editing forces selected to same id`() {
        val (sel, edit) = LabelInteractionState.normalize("frame-1", "label-1", layers)
        assertEquals("label-1", sel)
        assertEquals("label-1", edit)
    }
}
