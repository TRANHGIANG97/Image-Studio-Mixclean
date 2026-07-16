package com.thgiang.image.studio.ui.editor.mapper
import com.thgiang.image.studio.ui.editor.mapper.*

import com.thgiang.image.studio.ui.editor.model.*

import android.graphics.LinearGradient
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import com.thgiang.image.core.domain.model.template.CloudGradient
import com.thgiang.image.core.domain.model.template.CloudGradientCoords
import com.thgiang.image.core.domain.model.template.CloudGradientStop
import com.thgiang.image.core.domain.model.template.ColorArgbParser
import kotlin.math.max

object EditorGradientMapper {

    fun shapeFillOpacity(shapeColorArgb: Int): Float =
        ((shapeColorArgb ushr 24) and 0xFF) / 255f

    fun toComposeBrush(
        gradient: CloudGradient?,
        width: Float,
        height: Float,
        fallbackColor: Color,
        opacityMultiplier: Float = 1f,
    ): Brush {
        val parsed = parseStops(gradient) ?: return SolidColor(fallbackColor)
        val (colors, offsets) = parsed
        val coords = gradient!!.coords
        val scaledColors = if (opacityMultiplier >= 0.999f) {
            colors
        } else {
            colors.map { color ->
                color.copy(alpha = (color.alpha * opacityMultiplier).coerceIn(0f, 1f))
            }
        }

        val colorStops = scaledColors.zip(offsets) { color, offset -> offset to color }.toTypedArray()

        return if (gradient.type.equals("radial", ignoreCase = true)) {
            val cx = coords.x2 * width
            val cy = coords.y2 * height
            val radius = (coords.r2 ?: 0.5f) * max(width, height)
            Brush.radialGradient(
                colorStops = colorStops,
                center = Offset(cx, cy),
                radius = radius.coerceAtLeast(1f),
            )
        } else {
            Brush.linearGradient(
                colorStops = colorStops,
                start = Offset(coords.x1 * width, coords.y1 * height),
                end = Offset(coords.x2 * width, coords.y2 * height),
            )
        }
    }

    fun toAndroidShader(
        gradient: CloudGradient?,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        fallbackColorArgb: Int,
    ): Shader? {
        val parsed = parseStops(gradient) ?: return null
        val (colors, offsets) = parsed
        val colorInts = colors.map { it.toArgb() }.toIntArray()
        val positions = offsets.toFloatArray()
        val coords = gradient!!.coords

        return if (gradient.type.equals("radial", ignoreCase = true)) {
            val cx = left + coords.x2 * width
            val cy = top + coords.y2 * height
            val radius = (coords.r2 ?: 0.5f) * max(width, height)
            RadialGradient(
                cx,
                cy,
                radius.coerceAtLeast(1f),
                colorInts,
                positions,
                Shader.TileMode.CLAMP,
            )
        } else {
            LinearGradient(
                left + coords.x1 * width,
                top + coords.y1 * height,
                left + coords.x2 * width,
                top + coords.y2 * height,
                colorInts,
                positions,
                Shader.TileMode.CLAMP,
            )
        }
    }

    private fun parseStops(gradient: CloudGradient?): Pair<List<Color>, List<Float>>? {
        if (gradient == null || gradient.colorStops.isEmpty()) return null
        val parsed = gradient.colorStops.mapNotNull { stop ->
            ColorArgbParser.parseOrNull(stop.color)?.let { argb ->
                stop.offset.coerceIn(0f, 1f) to Color(argb)
            }
        }
        if (parsed.isEmpty()) return null
        val offsets = parsed.map { it.first }
        val colors = parsed.map { it.second }
        val resolvedColors = if (colors.size == 1) listOf(colors.first(), colors.first()) else colors
        val resolvedOffsets = if (offsets.size == 1) {
            listOf(0f, 1f)
        } else {
            offsets.take(resolvedColors.size)
        }
        return resolvedColors to resolvedOffsets
    }

    fun argbToCssHex(argb: Int): String {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return String.format("#%06X", (r shl 16) or (g shl 8) or b)
    }

    fun parseStopArgb(gradient: CloudGradient?, index: Int, fallbackArgb: Int): Int {
        val stop = gradient?.colorStops?.getOrNull(index) ?: return fallbackArgb
        return com.thgiang.image.core.domain.model.template.ColorArgbParser
            .parseOrNull(stop.color) ?: fallbackArgb
    }

    fun linearGradientAngleDegrees(gradient: CloudGradient?): Float {
        if (gradient == null) return 0f
        val coords = gradient.coords
        val dx = coords.x2 - coords.x1
        val dy = coords.y2 - coords.y1
        val degrees = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
        return (degrees + 360f) % 360f
    }

    fun buildLinearGradient(
        color1Argb: Int,
        color2Argb: Int,
        angleDegrees: Float,
        stop1Offset: Float = 0f,
        stop2Offset: Float = 1f,
    ): CloudGradient {
        val angleRad = Math.toRadians(angleDegrees.toDouble())
        val cosA = kotlin.math.cos(angleRad).toFloat()
        val sinA = kotlin.math.sin(angleRad).toFloat()
        return CloudGradient(
            type = "linear",
            colorStops = listOf(
                CloudGradientStop(stop1Offset, argbToCssHex(color1Argb)),
                CloudGradientStop(stop2Offset, argbToCssHex(color2Argb)),
            ),
            coords = CloudGradientCoords(
                x1 = 0.5f - 0.5f * cosA,
                y1 = 0.5f - 0.5f * sinA,
                x2 = 0.5f + 0.5f * cosA,
                y2 = 0.5f + 0.5f * sinA,
            ),
        )
    }
}
