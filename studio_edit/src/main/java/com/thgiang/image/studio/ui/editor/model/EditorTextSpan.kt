package com.thgiang.image.studio.ui.editor.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.thgiang.image.studio.ui.editor.mapper.EditorTextStyleMapper
import java.io.Serializable

/**
 * One styled run inside a label (Canva-like rich text).
 * Null style fields inherit from the parent [EditorLayer] flat defaults.
 */
data class EditorTextSpan(
    val text: String,
    val fontWeight: String? = null,
    val fontStyle: String? = null,
    val colorArgb: Int? = null,
    val underline: Boolean? = null,
    val linethrough: Boolean? = null,
    val fontFamily: String? = null,
) : Serializable

data class TextSpanStylePatch(
    val fontWeight: String? = null,
    val fontStyle: String? = null,
    val colorArgb: Int? = null,
    val clearColor: Boolean = false,
    val underline: Boolean? = null,
    val linethrough: Boolean? = null,
    val fontFamily: String? = null,
)

object TextRunOps {

    fun effectiveSpans(layer: EditorLayer): List<EditorTextSpan> {
        val joined = layer.textSpans.joinToString("") { it.text }
        return if (layer.textSpans.isNotEmpty() && joined == layer.text) {
            normalize(layer.textSpans)
        } else {
            listOf(baseSpan(layer, layer.text))
        }
    }

    fun baseSpan(layer: EditorLayer, text: String = layer.text): EditorTextSpan =
        EditorTextSpan(
            text = text,
            fontWeight = layer.fontWeight,
            fontStyle = layer.fontStyle,
            colorArgb = layer.textColorArgb,
            underline = layer.underline,
            linethrough = layer.linethrough,
            fontFamily = layer.fontFamily,
        )

    fun normalize(spans: List<EditorTextSpan>): List<EditorTextSpan> {
        if (spans.isEmpty()) return emptyList()
        val out = mutableListOf<EditorTextSpan>()
        for (span in spans) {
            if (span.text.isEmpty()) continue
            val last = out.lastOrNull()
            if (last != null && sameStyle(last, span)) {
                out[out.lastIndex] = last.copy(text = last.text + span.text)
            } else {
                out += span
            }
        }
        return out.ifEmpty { listOf(EditorTextSpan("")) }
    }

    fun applyStyle(
        spans: List<EditorTextSpan>,
        start: Int,
        end: Int,
        patch: TextSpanStylePatch,
    ): List<EditorTextSpan> {
        val total = spans.joinToString("") { it.text }
        val s = start.coerceIn(0, total.length)
        val e = end.coerceIn(0, total.length)
        if (s >= e) {
            return normalize(spans.map { applyPatch(it, patch) })
        }
        val split = splitAt(spans, listOf(s, e))
        var cursor = 0
        val next = split.map { span ->
            val spanStart = cursor
            val spanEnd = cursor + span.text.length
            cursor = spanEnd
            if (spanStart >= s && spanEnd <= e) applyPatch(span, patch) else span
        }
        return normalize(next)
    }

    /**
     * Reflow styles when the plain string changes (typing / delete).
     * Prefers prefix/suffix preservation; otherwise keeps style of the first span.
     */
    fun reflow(
        spans: List<EditorTextSpan>,
        oldText: String,
        newText: String,
    ): List<EditorTextSpan> {
        if (newText == oldText) return normalize(spans)
        if (newText.isEmpty()) return listOf((spans.firstOrNull() ?: EditorTextSpan("")).copy(text = ""))
        val normalized = normalize(spans).ifEmpty { listOf(EditorTextSpan(oldText)) }
        val baseStyle = normalized.first().copy(text = "")

        if (newText.startsWith(oldText)) {
            val delta = newText.substring(oldText.length)
            val last = normalized.last().copy(text = normalized.last().text + delta)
            return normalize(normalized.dropLast(1) + last)
        }
        if (oldText.startsWith(newText)) {
            return truncateToLength(normalized, newText.length)
        }
        // Common case: edit inside — map by scanning old runs against shared prefix/suffix.
        var prefix = 0
        val maxPrefix = minOf(oldText.length, newText.length)
        while (prefix < maxPrefix && oldText[prefix] == newText[prefix]) prefix++
        var oldSuffix = 0
        var newSuffix = 0
        while (
            oldSuffix < oldText.length - prefix &&
            newSuffix < newText.length - prefix &&
            oldText[oldText.length - 1 - oldSuffix] == newText[newText.length - 1 - newSuffix]
        ) {
            oldSuffix++
            newSuffix++
        }
        val midNew = newText.substring(prefix, newText.length - newSuffix)
        val head = truncateToLength(normalized, prefix)
        val tail = dropPrefixLength(normalized, oldText.length - oldSuffix)
        val midStyle = spanAt(normalized, prefix.coerceAtMost(oldText.length)) ?: baseStyle
        val mid = if (midNew.isEmpty()) emptyList() else listOf(midStyle.copy(text = midNew))
        return normalize(head + mid + tail)
    }

    fun toAnnotatedString(layer: EditorLayer): AnnotatedString {
        val spans = effectiveSpans(layer)
        return buildAnnotatedString {
            spans.forEach { span ->
                val start = length
                append(span.text)
                val weight = span.fontWeight ?: layer.fontWeight
                val style = span.fontStyle ?: layer.fontStyle
                val color = span.colorArgb ?: layer.textColorArgb
                val underline = span.underline ?: layer.underline
                val linethrough = span.linethrough ?: layer.linethrough
                val decorations = buildList {
                    if (underline) add(TextDecoration.Underline)
                    if (linethrough) add(TextDecoration.LineThrough)
                }
                addStyle(
                    SpanStyle(
                        color = Color(color),
                        fontWeight = if (EditorTextStyleMapper.isBoldWeight(weight)) {
                            FontWeight.Bold
                        } else {
                            FontWeight.Normal
                        },
                        fontStyle = if (EditorTextStyleMapper.isItalicStyle(style)) {
                            FontStyle.Italic
                        } else {
                            FontStyle.Normal
                        },
                        textDecoration = when (decorations.size) {
                            0 -> TextDecoration.None
                            1 -> decorations[0]
                            else -> TextDecoration.combine(decorations)
                        },
                    ),
                    start,
                    length,
                )
            }
        }
    }

    fun selectionIsBold(layer: EditorLayer, start: Int, end: Int): Boolean {
        val spans = spansCovering(layer, start, end)
        return spans.isNotEmpty() && spans.all {
            EditorTextStyleMapper.isBoldWeight(it.fontWeight ?: layer.fontWeight)
        }
    }

    fun selectionIsItalic(layer: EditorLayer, start: Int, end: Int): Boolean {
        val spans = spansCovering(layer, start, end)
        return spans.isNotEmpty() && spans.all {
            EditorTextStyleMapper.isItalicStyle(it.fontStyle ?: layer.fontStyle)
        }
    }

    private fun spansCovering(layer: EditorLayer, start: Int, end: Int): List<EditorTextSpan> {
        val spans = effectiveSpans(layer)
        val s = start.coerceAtLeast(0)
        val e = if (end > start) end else (start + 1).coerceAtMost(layer.text.length)
        var cursor = 0
        return spans.filter { span ->
            val a = cursor
            val b = cursor + span.text.length
            cursor = b
            a < e && b > s
        }
    }

    private fun sameStyle(a: EditorTextSpan, b: EditorTextSpan): Boolean =
        a.fontWeight == b.fontWeight &&
            a.fontStyle == b.fontStyle &&
            a.colorArgb == b.colorArgb &&
            a.underline == b.underline &&
            a.linethrough == b.linethrough &&
            a.fontFamily == b.fontFamily

    private fun applyPatch(span: EditorTextSpan, patch: TextSpanStylePatch): EditorTextSpan =
        span.copy(
            fontWeight = patch.fontWeight ?: span.fontWeight,
            fontStyle = patch.fontStyle ?: span.fontStyle,
            colorArgb = when {
                patch.clearColor -> null
                patch.colorArgb != null -> patch.colorArgb
                else -> span.colorArgb
            },
            underline = patch.underline ?: span.underline,
            linethrough = patch.linethrough ?: span.linethrough,
            fontFamily = patch.fontFamily ?: span.fontFamily,
        )

    private fun splitAt(spans: List<EditorTextSpan>, cuts: List<Int>): List<EditorTextSpan> {
        val sortedCuts = cuts.distinct().sorted()
        val out = mutableListOf<EditorTextSpan>()
        var cursor = 0
        for (span in spans) {
            var remaining = span.text
            var local = 0
            while (remaining.isNotEmpty()) {
                val abs = cursor + local
                val nextCut = sortedCuts.firstOrNull { it > abs && it < cursor + span.text.length }
                if (nextCut == null) {
                    out += span.copy(text = remaining)
                    break
                }
                val take = nextCut - abs
                out += span.copy(text = remaining.substring(0, take))
                remaining = remaining.substring(take)
                local += take
            }
            cursor += span.text.length
        }
        return out
    }

    private fun truncateToLength(spans: List<EditorTextSpan>, length: Int): List<EditorTextSpan> {
        if (length <= 0) return emptyList()
        val out = mutableListOf<EditorTextSpan>()
        var left = length
        for (span in spans) {
            if (left <= 0) break
            if (span.text.length <= left) {
                out += span
                left -= span.text.length
            } else {
                out += span.copy(text = span.text.substring(0, left))
                left = 0
            }
        }
        return out
    }

    private fun dropPrefixLength(spans: List<EditorTextSpan>, drop: Int): List<EditorTextSpan> {
        if (drop <= 0) return spans
        var left = drop
        val out = mutableListOf<EditorTextSpan>()
        for (span in spans) {
            if (left <= 0) {
                out += span
                continue
            }
            if (span.text.length <= left) {
                left -= span.text.length
            } else {
                out += span.copy(text = span.text.substring(left))
                left = 0
            }
        }
        return out
    }

    private fun spanAt(spans: List<EditorTextSpan>, index: Int): EditorTextSpan? {
        var cursor = 0
        for (span in spans) {
            val end = cursor + span.text.length
            if (index < end || (index == end && span.text.isNotEmpty())) return span
            cursor = end
        }
        return spans.lastOrNull()
    }
}

fun EditorLayer.withTextSpans(spans: List<EditorTextSpan>): EditorLayer {
    val normalized = TextRunOps.normalize(spans)
    val text = normalized.joinToString("") { it.text }
    val primary = normalized.firstOrNull()
    return copy(
        text = text,
        textSpans = normalized,
        fontWeight = primary?.fontWeight ?: fontWeight,
        fontStyle = primary?.fontStyle ?: fontStyle,
        textColorArgb = primary?.colorArgb ?: textColorArgb,
        underline = primary?.underline ?: underline,
        linethrough = primary?.linethrough ?: linethrough,
        fontFamily = primary?.fontFamily ?: fontFamily,
    )
}
