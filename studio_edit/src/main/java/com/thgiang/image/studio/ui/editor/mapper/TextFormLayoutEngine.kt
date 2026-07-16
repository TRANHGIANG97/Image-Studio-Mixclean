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
import com.thgiang.image.studio.ui.editor.model.TextFormEffect
import com.thgiang.image.studio.ui.editor.model.TextFormPreset
import com.thgiang.image.studio.util.FontDownloader
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
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
        val measurePaint = buildTextPaint(layer, textSizePx, alpha, context, Paint.Style.FILL)

        val glyphs = computeGlyphs(
            text = displayText,
            preset = layer.textForm.preset,
            amount = layer.textForm.normalizedAmount(),
            reversePath = layer.textForm.reversePath,
            boxWidth = width,
            boxHeight = height,
            paint = measurePaint,
            charSpacingEm = layer.charSpacing,
            textSizePx = textSizePx,
        )

        EditorTextRenderMapper.drawGlyphsOnCanvas(
            canvas = canvas,
            glyphs = glyphs,
            layer = layer,
            left = left,
            top = top,
            renderScale = renderScale,
            context = context,
            alpha = alpha,
            rotationDeg = layer.viewport.rotation,
            gradientLeft = gradientLeft,
            gradientTop = gradientTop,
            gradientWidth = gradientWidth,
            gradientHeight = gradientHeight,
        )
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
            TextFormCategory.FOLLOW_PATH -> centerGlyphsInBox(
                glyphs = transformFollowPath(
                    preset = preset,
                    centers = centers,
                    startX = startX,
                    totalWidth = totalWidth,
                    boxWidth = boxWidth,
                    boxHeight = boxHeight,
                    amount = amount,
                    reversePath = reversePath,
                ),
                boxWidth = boxWidth,
                boxHeight = boxHeight,
                fontMetrics = fm,
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

    /**
     * Maps slider amount (0..MAX_AMOUNT) to warp/path curvature (0..1).
     * At 0 the text stays flat with natural glyph spacing; only deformation scales up.
     */
    private fun curvatureFactor(amount: Float): Float =
        (amount / TextFormEffect.MAX_AMOUNT).coerceIn(0f, 1f)

    private fun transformFollowPath(
        preset: TextFormPreset,
        centers: List<Pair<Float, Float>>,
        startX: Float,
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
        val curve = curvatureFactor(amount)
        if (curve <= 0.001f) return centers.map { it to 0f }

        return when (preset) {
            TextFormPreset.PATH_WAVE -> {
                val amplitude = boxHeight * 0.22f * curve
                val cycles = 1.5f
                val safeWidth = totalWidth.coerceAtLeast(1f)
                centers.mapIndexed { i, (x, y) ->
                    val u = ((x - startX) / safeWidth).coerceIn(0f, 1f)
                    val waveY = y + amplitude * sin(2f * PI.toFloat() * cycles * u)
                    val rot = Math.toDegrees(
                        (amplitude * 2f * PI.toFloat() * cycles * cos(2f * PI.toFloat() * cycles * u) / safeWidth).toDouble(),
                    ).toFloat().coerceIn(-45f, 45f) * curve
                    (x to waveY) to rot
                }
            }

            TextFormPreset.PATH_ARC_UP -> {
                val sweep = PI.toFloat() * curve
                val radius = totalWidth / sweep.coerceAtLeast(0.001f)
                // Circle center sits below the frame; upper arc peak aligns with vertical center at full bend.
                val arcCenterY = cy + radius
                placeAlongArc(
                    centers = centers,
                    startX = startX,
                    totalWidth = totalWidth,
                    arcCenterX = cx,
                    arcCenterY = arcCenterY,
                    radius = radius,
                    sweepRadians = sweep,
                    startAngle = PI.toFloat(),
                    flipVertical = false,
                    reversePath = reversePath,
                    blend = curve,
                )
            }

            TextFormPreset.PATH_ARC_DOWN -> {
                val sweep = PI.toFloat() * curve
                val radius = totalWidth / sweep.coerceAtLeast(0.001f)
                val arcCenterY = cy - radius
                placeAlongArc(
                    centers = centers,
                    startX = startX,
                    totalWidth = totalWidth,
                    arcCenterX = cx,
                    arcCenterY = arcCenterY,
                    radius = radius,
                    sweepRadians = sweep,
                    startAngle = 0f,
                    flipVertical = false,
                    reversePath = reversePath,
                    blend = curve,
                )
            }

            TextFormPreset.PATH_CIRCLE -> {
                val sweep = 2f * PI.toFloat() * curve
                // Circumference 2πr; spacing widens totalWidth, intensity opens the ring further.
                val intensityExpansion = 1f + curve * 1.5f
                val radius = (totalWidth / (2f * PI.toFloat())).coerceAtLeast(1f) * intensityExpansion
                placeAlongArc(
                    centers = centers,
                    startX = startX,
                    totalWidth = totalWidth,
                    arcCenterX = cx,
                    arcCenterY = cy,
                    radius = radius,
                    sweepRadians = sweep,
                    startAngle = -PI.toFloat() / 2f - sweep / 2f,
                    flipVertical = false,
                    reversePath = reversePath,
                    blend = curve,
                )
            }

            else -> centers.map { it to 0f }
        }
    }

    /**
     * Places glyphs along a circular arc using advance-based horizontal position (u),
     * then blends toward flat layout so low intensity reduces bend — not letter spacing.
     */
    private fun placeAlongArc(
        centers: List<Pair<Float, Float>>,
        startX: Float,
        totalWidth: Float,
        arcCenterX: Float,
        arcCenterY: Float,
        radius: Float,
        sweepRadians: Float,
        startAngle: Float,
        flipVertical: Boolean,
        reversePath: Boolean,
        blend: Float,
    ): List<Pair<Pair<Float, Float>, Float>> {
        val safeWidth = totalWidth.coerceAtLeast(1f)
        val verticalSign = if (flipVertical) -1f else 1f

        return centers.map { flat ->
            val u = ((flat.first - startX) / safeWidth).coerceIn(0f, 1f)
            val t = if (reversePath) 1f - u else u
            val angle = startAngle + sweepRadians * t
            val px = arcCenterX + radius * cos(angle)
            val py = arcCenterY + radius * sin(angle) * verticalSign
            val tangent = angle + PI.toFloat() / 2f * verticalSign
            val rot = Math.toDegrees(tangent.toDouble()).toFloat() * blend
            val x = flat.first + (px - flat.first) * blend
            val y = flat.second + (py - flat.second) * blend
            (x to y) to rot
        }
    }

    /** Centers warped glyph baselines inside the shape box using font-metric extents. */
    private fun centerGlyphsInBox(
        glyphs: List<Pair<Pair<Float, Float>, Float>>,
        boxWidth: Float,
        boxHeight: Float,
        fontMetrics: Paint.FontMetrics,
    ): List<Pair<Pair<Float, Float>, Float>> {
        if (glyphs.isEmpty()) return glyphs

        var minX = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var minY = Float.MAX_VALUE
        var maxY = Float.MIN_VALUE
        val halfWidth = max(-fontMetrics.ascent, fontMetrics.descent)

        glyphs.forEach { (point, _) ->
            val (x, baselineY) = point
            minX = min(minX, x - halfWidth)
            maxX = max(maxX, x + halfWidth)
            minY = min(minY, baselineY + fontMetrics.ascent)
            maxY = max(maxY, baselineY + fontMetrics.descent)
        }

        val dx = (boxWidth - (maxX - minX)) / 2f - minX
        val dy = (boxHeight - (maxY - minY)) / 2f - minY
        if (kotlin.math.abs(dx) < 0.5f && kotlin.math.abs(dy) < 0.5f) return glyphs

        return glyphs.map { (point, rotation) ->
            (point.first + dx to point.second + dy) to rotation
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
        val curve = curvatureFactor(amount)
        if (curve <= 0.001f) return centers.map { it to 0f }

        val n = centers.size
        val safeWidth = totalWidth.coerceAtLeast(1f)

        fun uAt(index: Int): Float {
            if (n <= 1) return 0.5f
            val x = centers[index].first
            return ((x - startX) / safeWidth).coerceIn(0f, 1f)
        }

        return centers.mapIndexed { i, (cx, cy) ->
            val u = uAt(i)
            val v = 0.5f
            val (dx, dy) = warpDelta(preset, u, v, totalWidth, boxHeight, curve)
            val newX = cx + dx
            val newY = cy + dy

            val uPrev = uAt((i - 1).coerceAtLeast(0))
            val uNext = uAt((i + 1).coerceAtMost(n - 1))
            val (_, dyPrev) = warpDelta(preset, uPrev, v, totalWidth, boxHeight, curve)
            val (_, dyNext) = warpDelta(preset, uNext, v, totalWidth, boxHeight, curve)
            val slope = if (uNext - uPrev != 0f) {
                (dyNext - dyPrev) / ((uNext - uPrev) * safeWidth)
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
        curve: Float,
    ): Pair<Float, Float> {
        val arch = 4f * u * (1f - u)
        return when (preset) {
            TextFormPreset.WARP_ARCH_UP ->
                0f to (-height * 0.38f * curve * arch)
            TextFormPreset.WARP_ARCH_DOWN ->
                0f to (height * 0.38f * curve * arch)
            TextFormPreset.WARP_BULGE -> {
                val bulge = sin(u * PI.toFloat()) * curve
                val dx = (u - 0.5f) * width * 0.08f * bulge
                val dy = -height * 0.12f * bulge
                dx to dy
            }
            TextFormPreset.WARP_WAVE ->
                0f to (height * 0.22f * curve * sin(u * 2f * PI.toFloat() * 2f))
            TextFormPreset.WARP_FLAG -> {
                val wave = sin(u * PI.toFloat() * 3f) * curve
                (width * 0.06f * wave) to (height * 0.08f * wave)
            }
            TextFormPreset.WARP_RISE ->
                0f to (-height * 0.35f * curve * (u - 0.5f))
            TextFormPreset.WARP_FALL ->
                0f to (height * 0.35f * curve * (u - 0.5f))
            TextFormPreset.WARP_CHEVRON_UP -> {
                val chevron = if (u < 0.5f) u * 2f else (1f - u) * 2f
                0f to (-height * 0.32f * curve * chevron)
            }
            TextFormPreset.WARP_CHEVRON_DOWN -> {
                val chevron = if (u < 0.5f) u * 2f else (1f - u) * 2f
                0f to (height * 0.32f * curve * chevron)
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

    /** Union of glyph draw positions in local box space (matches [computeGlyphs] coordinates). */
    fun glyphContentBounds(glyphs: List<GlyphDrawSpec>, textSizePx: Float): RectF? {
        if (glyphs.isEmpty()) return null

        var left = Float.MAX_VALUE
        var top = Float.MAX_VALUE
        var right = Float.MIN_VALUE
        var bottom = Float.MIN_VALUE
        val half = textSizePx * 0.55f
        glyphs.forEach { glyph ->
            left = minOf(left, glyph.x - half)
            top = minOf(top, glyph.y - textSizePx)
            right = maxOf(right, glyph.x + half)
            bottom = maxOf(bottom, glyph.y + textSizePx * 0.2f)
        }
        return RectF(left, top, right, bottom)
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
        return glyphContentBounds(glyphs, textSizePx)
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
