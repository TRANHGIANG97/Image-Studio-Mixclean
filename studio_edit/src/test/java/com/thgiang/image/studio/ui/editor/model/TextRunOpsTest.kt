package com.thgiang.image.studio.ui.editor.model

import com.thgiang.image.studio.ui.editor.label.panel.applyTo
import com.thgiang.image.studio.ui.editor.label.panel.textStyleTemplates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextRunOpsTest {

    @Test
    fun applyStyle_splitsAndBoldsRange() {
        val spans = listOf(EditorTextSpan("Hello world", fontWeight = "normal"))
        val next = TextRunOps.applyStyle(
            spans,
            start = 0,
            end = 5,
            patch = TextSpanStylePatch(fontWeight = "bold"),
        )
        assertEquals(2, next.size)
        assertEquals("Hello", next[0].text)
        assertEquals("bold", next[0].fontWeight)
        assertEquals(" world", next[1].text)
        assertEquals("normal", next[1].fontWeight)
    }

    @Test
    fun reflow_appendKeepsLastStyle() {
        val spans = listOf(
            EditorTextSpan("Hi", fontWeight = "bold"),
            EditorTextSpan("!", fontWeight = "normal"),
        )
        val next = TextRunOps.reflow(spans, "Hi!", "Hi!?")
        assertEquals("Hi!?", next.joinToString("") { it.text })
        assertEquals("normal", next.last().fontWeight)
    }

    @Test
    fun reflow_deletePrefixTruncates() {
        val spans = listOf(EditorTextSpan("abcdef", fontStyle = "italic"))
        val next = TextRunOps.reflow(spans, "abcdef", "abcd")
        assertEquals(listOf("abcd"), next.map { it.text })
        assertEquals("italic", next.first().fontStyle)
    }

    @Test
    fun normalize_mergesAdjacentSameStyle() {
        val spans = listOf(
            EditorTextSpan("ab", fontWeight = "bold"),
            EditorTextSpan("cd", fontWeight = "bold"),
        )
        val next = TextRunOps.normalize(spans)
        assertEquals(1, next.size)
        assertEquals("abcd", next[0].text)
    }

    @Test
    fun withTextSpans_updatesFlatFieldsFromPrimary() {
        val layer = EditorLayer(text = "x", fontWeight = "normal")
        val updated = layer.withTextSpans(
            listOf(
                EditorTextSpan("Hi", fontWeight = "bold", colorArgb = 0xFFFF0000.toInt()),
                EditorTextSpan("!", fontWeight = "normal"),
            ),
        )
        assertEquals("Hi!", updated.text)
        assertEquals("bold", updated.fontWeight)
        assertEquals(0xFFFF0000.toInt(), updated.textColorArgb)
        assertTrue(updated.textSpans.size >= 2)
    }

    @Test
    fun textStyleTemplate_applyTo_preservesDistinctRunStyles() {
        val layer = EditorLayer(text = "Hello!").withTextSpans(
            listOf(
                EditorTextSpan("Hello", fontWeight = "bold", underline = true, fontFamily = "a"),
                EditorTextSpan("!", fontWeight = "normal", underline = false, fontFamily = "b"),
            ),
        )
        val template = textStyleTemplates.first()
        val next = template.applyTo(layer)
        assertEquals("Hello!", next.text)
        // Template patches weight/color uniformly but must not drop per-run underline/fontFamily.
        assertEquals(2, next.textSpans.size)
        assertEquals("Hello", next.textSpans[0].text)
        assertEquals("!", next.textSpans[1].text)
        assertEquals(true, next.textSpans[0].underline)
        assertEquals(false, next.textSpans[1].underline)
        assertEquals("a", next.textSpans[0].fontFamily)
        assertEquals("b", next.textSpans[1].fontFamily)
        assertEquals(template.textColorArgb, next.textColorArgb)
    }
}
