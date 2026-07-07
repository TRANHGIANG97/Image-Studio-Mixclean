package com.thgiang.image.studio.ui.editor.mapper

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Paint.Align
import android.graphics.RectF
import android.text.TextPaint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import com.thgiang.image.studio.ui.editor.model.EditorLayer
import com.thgiang.image.studio.ui.editor.model.TextFormCategory
import com.thgiang.image.studio.ui.editor.model.TextFormPreset
import com.thgiang.image.studio.util.FontDownloader
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

data class GlyphDrawSpec(
    val char: String,
    val x: Float,
    val y: Float,
    val rotationDeg: Float,
)

object TextFormLayoutEngine {

    fun DrawScope.drawTextForm(
        layer: EditorLayer,
        renderScale: Float,
        context: Context,
        gradientLeft: Float = 0f,
        gradientTop: Float = 0f,
        gradientWidth: Float = size.width,
        gradientHeight: Float = size.height,
    ) {
        if (!layer.textForm.isActive) return
        drawIntoCanvas { composeCanvas ->
            drawOnCanvas(
                canvas = composeCanvas.nativeCanvas,
                layer = layer,
                left = 0f,
                top = 0f,
                width = size.width,
                height = size.height,
                renderScale = renderScale,
                context = context,
                gradientLeft = gradientLeft,
                gradientTop = gradientTop,
                gradientWidth = gradientWidth,
                gradientHeight = gradientHeight,
            )
        }
    }

    fun drawOnCanvas(
        canvas: Canvas,
        layer: EditorLayer,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        renderScale: Float,
        context: Context,
        alpha: Int = (layer.appearance.alpha * 255f).toInt().coerceIn(0, 255),
        gradientLeft: Float = left,
        gradientTop: Float = top,
        gradientWidth: Float = width,
        gradientHeight: Float = height,
    ) {
        if (!layer.textForm.isActive) return
        val displayText = EditorTextStyleMapper.applyTextTransform(layer.text, layer.textTransform)
        if (displayText.isBlank()) return

        val textSizePx = layer.textSizeSp * renderScale
        val fillPaint = buildTextPaint(layer, textSizePx, alpha, context, Paint.Style.FILL).apply {
            EditorGradientMapper.toAndroidShader(
                layer.textColorGradient,
                gradientLeft,
                gradientTop,
                gradientWidth,
                gradientHeight,
                layer.textColorArgb,
            )?.let { shader = it }
        }

        val glyphs = computeGlyphs(
            text = displayText,
            preset = layer.textForm.preset,
            amount = layer.textForm.normalizedAmount(),
            reversePath = layer.textForm.reversePath,
            boxWidth = width,
            boxHeight = height,
            paint = fillPaint,
            charSpacingEm = layer.charSpacing,
            textSizePx = textSizePx,
        )

        canvas.save()
        canvas.translate(left, top)
        glyphs.forEach { spec ->
            canvas.save()
            canvas.translate(spec.x, spec.y)
            canvas.rotate(spec.rotationDeg)
            canvas.drawText(spec.char, 0f, 0f, fillPaint)
            canvas.restore()
        }
        canvas.restore()
    }

    fun computeGlyphs(
        text: String,
        preset: TextFormPreset,
        amount: Float,
        reversePath: Boolean,
        boxWidth: Float,
        boxHeight: Float,
        paint: TextPaint,
        charSpacingEm: Float,
        textSizePx: Float,
    ): List<GlyphDrawSpec> {
        if (preset == TextFormPreset.NONE || text.isBlank()) return emptyList()

        val chars = text.map { it.toString() }
        if (chars.isEmpty()) return emptyList()

        val advances = FloatArray(chars.size)
        paint.getTextWidths(text, advances)
        val letterSpacingPx = EditorTextStyleMapper.resolveLetterSpacingEm(charSpacingEm, textSizePx)

        var totalWidth = 0f
        for (i in advances.indices) {
            totalWidth += advances[i]
            if (i < advances.lastIndex) totalWidth += letterSpacingPx
        }
        totalWidth = totalWidth.coerceAtLeast(textSizePx * 0.5f)

        val fm = paint.fontMetrics
        val baselineY = boxHeight / 2f - (fm.ascent + fm.descent) / 2f
        val startX = (boxWidth - totalWidth) / 2f

        val centers = ArrayList<Pair<Float, Float>>(chars.size)
        var x = startX
        for (i in chars.indices) {
            centers += (x + advances[i] / 2f) to baselineY
            x += advances[i] + letterSpacingPx
        }

        val transformed = when (preset.category) {
            TextFormCategory.FOLLOW_PATH -> transformFollowPath(
                preset = preset,
                centers = centers,
                totalWidth = totalWidth,
                boxWidth = boxWidth,
                boxHeight = boxHeight,
                amount = amount,
                reversePath = reversePath,
            )
            TextFormCategory.WARP -> transformWarp(
                preset = preset,
                centers = centers,
                totalWidth = totalWidth,
                startX = startX,
                boxHeight = boxHeight,
                amount = amount,
            )
            else -> centers.map { it to 0f }
        }

        return transformed.mapIndexed { index, (point, rotation) ->
            GlyphDrawSpec(
                char = chars[index],
                x = point.first,
                y = point.second,
                rotationDeg = rotation,
            )
        }
    }

    /** Glyphs for the active layer (canvas / export / elevation). */
    fun computeLayerGlyphs(
        layer: EditorLayer,
        boxWidth: Float,
        boxHeight: Float,
        renderScale: Float,
        context: Context,
    ): List<GlyphDrawSpec> {
        if (!layer.textForm.isActive) return emptyList()
        val displayText = EditorTextStyleMapper.applyTextTransform(layer.text, layer.textTransform)
        if (displayText.isBlank()) return emptyList()
        val textSizePx = layer.textSizeSp * renderScale
        val paint = buildTextPaint(layer, textSizePx, 255, context, Paint.Style.FILL)
        return computeGlyphs(
            text = displayText,
            preset = layer.textForm.preset,
            amount = layer.textForm.normalizedAmount(),
            reversePath = layer.textForm.reversePath,
            boxWidth = boxWidth,
            boxHeight = boxHeight,
            paint = paint,
            charSpacingEm = layer.charSpacing,
            textSizePx = textSizePx,
        )
    }

    /** Compact preview for preset icons (local 0..1 space scaled to icon size). */
    fun computePreviewGlyphs(
        preset: TextFormPreset,
        amount: Float,
        iconWidth: Float,
        iconHeight: Float,
    ): List<GlyphDrawSpec> {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = iconHeight * 0.28f
            textAlign = Align.CENTER
        }
        return computeGlyphs(
            text = "abcde",
            preset = preset,
            amount = amount.coerceIn(0.15f, 1f),
            reversePath = false,
            boxWidth = iconWidth,
            boxHeight = iconHeight,
            paint = paint,
            charSpacingEm = 0f,
            textSizePx = paint.textSize,
        )
    }

    private fun transformFollowPath(
        preset: TextFormPreset,
        centers: List<Pair<Float, Float>>,
        totalWidth: Float,
        boxWidth: Float,
        boxHeight: Float,
        amount: Float,
        reversePath: Boolean,
    ): List<Pair<Pair<Float, Float>, Float>> {
        val n = centers.size
        if (n == 0) return emptyList()

        val cx = boxWidth / 2f
        val cy = boxHeight / 2f
        val intensity = amount.coerceIn(0f, 1f)

        return when (preset) {
            TextFormPreset.PATH_WAVE -> {
                val amplitude = boxHeight * 0.22f * intensity
                val cycles = 1.5f
                centers.mapIndexed { i, (x, y) ->
                    val u = if (n <= 1) 0.5f else i.toFloat() / (n - 1)
                    val waveY = y + amplitude * sin(2f * PI.toFloat() * cycles * u)
                    val rot = Math.toDegrees(
                        (amplitude * 2f * PI.toFloat() * cycles * cos(2f * PI.toFloat() * cycles * u) / totalWidth).toDouble(),
                    ).toFloat().coerceIn(-45f, 45f)
                    (x to waveY) to rot
                }
            }

            TextFormPreset.PATH_ARC_UP -> {
                val radius = (totalWidth / PI.toFloat()).coerceAtLeast(boxHeight * 0.15f) * (0.55f + intensity * 0.65f)
                val arcCenterY = cy + radius * 0.35f
                arcAlongSemicircle(centers, cx, arcCenterY, radius, up = true, reversePath)
            }

            TextFormPreset.PATH_ARC_DOWN -> {
                val radius = (totalWidth / PI.toFloat()).coerceAtLeast(boxHeight * 0.15f) * (0.55f + intensity * 0.65f)
                val arcCenterY = cy - radius * 0.35f
                arcAlongSemicircle(centers, cx, arcCenterY, radius, up = false, reversePath)
            }

            TextFormPreset.PATH_CIRCLE -> {
                val radius = minOf(boxWidth, boxHeight) * 0.32f * (0.45f + intensity * 0.55f)
                circleAlongRing(centers, cx, cy, radius, fullCircle = true, reversePath)
            }

            TextFormPreset.PATH_RING -> {
                val radius = minOf(boxWidth, boxHeight) * 0.28f * (0.35f + intensity * 0.5f)
                circleAlongRing(centers, cx, cy, radius, fullCircle = false, reversePath)
            }

            else -> centers.map { it to 0f }
        }
    }

    private fun arcAlongSemicircle(
        centers: List<Pair<Float, Float>>,
        centerX: Float,
        centerY: Float,
        radius: Float,
        up: Boolean,
        reversePath: Boolean,
    ): List<Pair<Pair<Float, Float>, Float>> {
        val n = centers.size
        val startAngle = if (up) PI.toFloat() else 0f
        val endAngle = if (up) 0f else PI.toFloat()
        val step = if (n <= 1) 0f else (endAngle - startAngle) / (n - 1)

        return centers.mapIndexed { i, _ ->
            val t = if (reversePath) (n - 1 - i) else i
            val angle = startAngle + step * t
            val px = centerX + radius * cos(angle)
            val py = centerY + radius * sin(angle) * if (up) -1f else 1f
            val tangent = angle + (if (up) -PI.toFloat() / 2f else PI.toFloat() / 2f)
            val rot = Math.toDegrees(tangent.toDouble()).toFloat()
            (px to py) to rot
        }
    }

    private fun circleAlongRing(
        centers: List<Pair<Float, Float>>,
        centerX: Float,
        centerY: Float,
        radius: Float,
        fullCircle: Boolean,
        reversePath: Boolean,
    ): List<Pair<Pair<Float, Float>, Float>> {
        val n = centers.size
        val sweep = if (fullCircle) 2f * PI.toFloat() else PI.toFloat() * 1.35f
        val start = -PI.toFloat() / 2f - sweep / 2f
        val step = if (n <= 1) 0f else sweep / (n - 1)

        return centers.mapIndexed { i, _ ->
            val t = if (reversePath) (n - 1 - i) else i
            val angle = start + step * t
            val px = centerX + radius * cos(angle)
            val py = centerY + radius * sin(angle)
            val rot = Math.toDegrees((angle + PI.toFloat() / 2f).toDouble()).toFloat()
            (px to py) to rot
        }
    }

    private fun transformWarp(
        preset: TextFormPreset,
        centers: List<Pair<Float, Float>>,
        totalWidth: Float,
        startX: Float,
        boxHeight: Float,
        amount: Float,
    ): List<Pair<Pair<Float, Float>, Float>> {
        val intensity = amount.coerceIn(0f, 1f)
        val n = centers.size

        fun uAt(index: Int): Float =
            if (n <= 1) 0.5f else index.toFloat() / (n - 1)

        return centers.mapIndexed { i, (cx, cy) ->
            val u = uAt(i)
            val v = 0.5f
            val (dx, dy) = warpDelta(preset, u, v, totalWidth, boxHeight, intensity)
            val newX = cx + dx
            val newY = cy + dy

            val uPrev = uAt((i - 1).coerceAtLeast(0))
            val uNext = uAt((i + 1).coerceAtMost(n - 1))
            val (_, dyPrev) = warpDelta(preset, uPrev, v, totalWidth, boxHeight, intensity)
            val (_, dyNext) = warpDelta(preset, uNext, v, totalWidth, boxHeight, intensity)
            val slope = if (uNext - uPrev != 0f) {
                (dyNext - dyPrev) / ((uNext - uPrev) * totalWidth.coerceAtLeast(1f))
            } else {
                0f
            }
            val rot = Math.toDegrees(atan2(slope.toDouble(), 1.0)).toFloat().coerceIn(-60f, 60f)
            (newX to newY) to rot
        }
    }

    private fun warpDelta(
        preset: TextFormPreset,
        u: Float,
        v: Float,
        width: Float,
        height: Float,
        intensity: Float,
    ): Pair<Float, Float> {
        val arch = 4f * u * (1f - u)
        return when (preset) {
            TextFormPreset.WARP_ARCH_UP ->
                0f to (-height * 0.38f * intensity * arch)
            TextFormPreset.WARP_ARCH_DOWN ->
                0f to (height * 0.38f * intensity * arch)
            TextFormPreset.WARP_BULGE -> {
                val bulge = sin(u * PI.toFloat()) * intensity
                val dx = (u - 0.5f) * width * 0.08f * bulge
                val dy = -height * 0.12f * bulge
                dx to dy
            }
            TextFormPreset.WARP_WAVE ->
                0f to (height * 0.22f * intensity * sin(u * 2f * PI.toFloat() * 2f))
            TextFormPreset.WARP_FLAG -> {
                val wave = sin(u * PI.toFloat() * 3f) * intensity
                (width * 0.06f * wave) to (height * 0.08f * wave)
            }
            TextFormPreset.WARP_RISE ->
                0f to (-height * 0.35f * intensity * (u - 0.5f))
            TextFormPreset.WARP_FALL ->
                0f to (height * 0.35f * intensity * (u - 0.5f))
            TextFormPreset.WARP_INFLATE -> {
                val scale = 1f + intensity * 0.35f * (1f - 2f * kotlin.math.abs(u - 0.5f))
                0f to (-height * 0.08f * (scale - 1f))
            }
            TextFormPreset.WARP_DEFLATE -> {
                val scale = 1f - intensity * 0.3f * (1f - 2f * kotlin.math.abs(u - 0.5f))
                0f to (height * 0.08f * (1f - scale))
            }
            TextFormPreset.WARP_CHEVRON_UP -> {
                val chevron = if (u < 0.5f) u * 2f else (1f - u) * 2f
                0f to (-height * 0.32f * intensity * chevron)
            }
            TextFormPreset.WARP_CHEVRON_DOWN -> {
                val chevron = if (u < 0.5f) u * 2f else (1f - u) * 2f
                0f to (height * 0.32f * intensity * chevron)
            }
            else -> 0f to 0f
        }
    }

    fun measureBounds(
        layer: EditorLayer,
        boxWidth: Float,
        boxHeight: Float,
        renderScale: Float,
        context: Context,
    ): RectF {
        val extents = measureGlyphExtents(layer, boxWidth, boxHeight, renderScale, context)
            ?: return RectF(0f, 0f, boxWidth, boxHeight)
        return RectF(
            extents.left.coerceAtLeast(0f),
            extents.top.coerceAtLeast(0f),
            extents.right.coerceAtMost(boxWidth),
            extents.bottom.coerceAtMost(boxHeight),
        )
    }

    /** Unclamped glyph union used for text-form shape fitting. */
    fun measureGlyphExtents(
        layer: EditorLayer,
        boxWidth: Float,
        boxHeight: Float,
        renderScale: Float,
        context: Context,
    ): RectF? {
        if (!layer.textForm.isActive) return null
        val textSizePx = layer.textSizeSp * renderScale
        val paint = buildTextPaint(layer, textSizePx, 255, context, Paint.Style.FILL)
        val displayText = EditorTextStyleMapper.applyTextTransform(layer.text, layer.textTransform)
        val glyphs = computeGlyphs(
            text = displayText,
            preset = layer.textForm.preset,
            amount = layer.textForm.normalizedAmount(),
            reversePath = layer.textForm.reversePath,
            boxWidth = boxWidth,
            boxHeight = boxHeight,
            paint = paint,
            charSpacingEm = layer.charSpacing,
            textSizePx = textSizePx,
        )
        if (glyphs.isEmpty()) return null

        var left = Float.MAX_VALUE
        var top = Float.MAX_VALUE
        var right = Float.MIN_VALUE
        var bottom = Float.MIN_VALUE
        val half = textSizePx * 0.55f
        glyphs.forEach { g ->
            left = minOf(left, g.x - half)
            top = minOf(top, g.y - textSizePx)
            right = maxOf(right, g.x + half)
            bottom = maxOf(bottom, g.y + textSizePx * 0.2f)
        }
        return RectF(left, top, right, bottom)
    }

    private fun buildTextPaint(
        layer: EditorLayer,
        textSizePx: Float,
        alpha: Int,
        context: Context,
        style: Paint.Style,
    ): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = layer.textColorArgb
        this.alpha = alpha
        textSize = textSizePx
        this.style = style
        textAlign = Align.CENTER
        letterSpacing = EditorTextStyleMapper.resolveLetterSpacingEm(layer.charSpacing, textSizePx)
        layer.fontFamily?.let { family ->
            val customTf = kotlinx.coroutines.runBlocking {
                FontDownloader.getTypeface(context, family)
            }
            EditorTextStyleMapper.configureTextPaint(
                paint = this,
                fontWeight = layer.fontWeight,
                fontStyle = layer.fontStyle,
                underline = layer.underline,
                linethrough = layer.linethrough,
                baseTypeface = customTf,
            )
        } ?: EditorTextStyleMapper.configureTextPaint(
            paint = this,
            fontWeight = layer.fontWeight,
            fontStyle = layer.fontStyle,
            underline = layer.underline,
            linethrough = layer.linethrough,
        )
    }
}
