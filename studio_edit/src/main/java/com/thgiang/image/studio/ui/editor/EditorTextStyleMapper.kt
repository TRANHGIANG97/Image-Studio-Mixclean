package com.thgiang.image.studio.ui.editor

import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration

object EditorTextStyleMapper {
    fun applyTextTransform(text: String, transform: String?): String = when (transform?.lowercase()) {
        "uppercase" -> text.uppercase()
        "lowercase" -> text.lowercase()
        "capitalize" -> text.split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { char -> char.titlecase() }
        }
        else -> text
    }

    fun resolveComposeFontWeight(fontWeight: String?): FontWeight {
        val normalized = fontWeight?.lowercase()?.trim().orEmpty()
        return when {
            normalized in setOf("bold", "700", "800", "900") -> FontWeight.Bold
            normalized in setOf("600", "semibold", "semi-bold") -> FontWeight.SemiBold
            normalized in setOf("500", "medium") -> FontWeight.Medium
            normalized in setOf("300", "light") -> FontWeight.Light
            normalized in setOf("100", "200", "thin") -> FontWeight.Thin
            normalized in setOf("400", "normal", "") -> FontWeight.Normal
            normalized.toIntOrNull() != null -> {
                when (val value = normalized.toInt()) {
                    in 700..1000 -> FontWeight.Bold
                    in 500..599 -> FontWeight.Medium
                    in 300..499 -> FontWeight.Light
                    else -> FontWeight.Normal
                }
            }
            else -> FontWeight.Normal
        }
    }

    fun resolveComposeFontStyle(fontStyle: String?): FontStyle =
        if (fontStyle.equals("italic", ignoreCase = true)) FontStyle.Italic else FontStyle.Normal

    fun resolveComposeTextAlign(textAlign: String?): TextAlign = when (textAlign?.lowercase()) {
        "left", "start" -> TextAlign.Start
        "right", "end" -> TextAlign.End
        "justify" -> TextAlign.Justify
        else -> TextAlign.Center
    }

    fun resolveLayoutAlignment(textAlign: String?): android.text.Layout.Alignment = when (textAlign?.lowercase()) {
        "left", "start" -> android.text.Layout.Alignment.ALIGN_NORMAL
        "right", "end" -> android.text.Layout.Alignment.ALIGN_OPPOSITE
        else -> android.text.Layout.Alignment.ALIGN_CENTER
    }

    fun resolveTextDecoration(underline: Boolean, linethrough: Boolean): TextDecoration? {
        return when {
            underline && linethrough -> TextDecoration.combine(
                listOf(TextDecoration.Underline, TextDecoration.LineThrough),
            )
            underline -> TextDecoration.Underline
            linethrough -> TextDecoration.LineThrough
            else -> null
        }
    }

    fun resolveLineSpacingMultiplier(lineHeight: Float?): Float =
        lineHeight?.coerceIn(0.5f, 3f) ?: 1f

    fun resolveLetterSpacingEm(charSpacing: Float, textSizePx: Float): Float {
        if (charSpacing == 0f || textSizePx <= 0f) return 0f
        return charSpacing / textSizePx
    }

    fun configureTextPaint(
        paint: TextPaint,
        fontWeight: String?,
        fontStyle: String?,
        underline: Boolean,
        linethrough: Boolean,
        baseTypeface: Typeface? = null,
    ) {
        val style = when {
            isBold(fontWeight) && fontStyle.equals("italic", ignoreCase = true) -> Typeface.BOLD_ITALIC
            isBold(fontWeight) -> Typeface.BOLD
            fontStyle.equals("italic", ignoreCase = true) -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        paint.typeface = Typeface.create(baseTypeface ?: Typeface.DEFAULT, style)
        paint.isFakeBoldText = false
        paint.isUnderlineText = underline
        if (linethrough) {
            paint.flags = paint.flags or Paint.STRIKE_THRU_TEXT_FLAG
        }
    }

    fun isBoldWeight(fontWeight: String?): Boolean = isBold(fontWeight)

    fun isItalicStyle(fontStyle: String?): Boolean =
        fontStyle.equals("italic", ignoreCase = true)

    private fun isBold(fontWeight: String?): Boolean {
        val normalized = fontWeight?.lowercase()?.trim().orEmpty()
        return when {
            normalized == "bold" -> true
            normalized.toIntOrNull()?.let { it >= 600 } == true -> true
            else -> false
        }
    }
}
