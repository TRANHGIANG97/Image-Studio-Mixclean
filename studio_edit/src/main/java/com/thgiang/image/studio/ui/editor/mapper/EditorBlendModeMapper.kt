package com.thgiang.image.studio.ui.editor.mapper
import com.thgiang.image.studio.ui.editor.mapper.*

import com.thgiang.image.studio.ui.editor.model.*

import android.graphics.BlendMode as AndroidBlendMode
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.os.Build
import androidx.compose.ui.graphics.BlendMode as ComposeBlendMode

object EditorBlendModeMapper {
    /**
     * Explicit whitelist mapping the Web contract blend-mode strings to Compose blend modes.
     * `null`/blank/"normal" and any unknown value map to `null` → render normal.
     * Raw strings never reach the renderer.
     */
    fun toComposeBlendModeOrNull(blendMode: String?): ComposeBlendMode? = when (blendMode?.lowercase()) {
        "multiply" -> ComposeBlendMode.Multiply
        "screen" -> ComposeBlendMode.Screen
        "overlay" -> ComposeBlendMode.Overlay
        "darken" -> ComposeBlendMode.Darken
        "lighten" -> ComposeBlendMode.Lighten
        "color-dodge" -> ComposeBlendMode.ColorDodge
        "color-burn" -> ComposeBlendMode.ColorBurn
        "hard-light" -> ComposeBlendMode.Hardlight
        "soft-light" -> ComposeBlendMode.Softlight
        "difference" -> ComposeBlendMode.Difference
        "exclusion" -> ComposeBlendMode.Exclusion
        "hue" -> ComposeBlendMode.Hue
        "saturation" -> ComposeBlendMode.Saturation
        "color" -> ComposeBlendMode.Color
        "luminosity" -> ComposeBlendMode.Luminosity
        "linear-dodge", "lighter" -> ComposeBlendMode.Plus
        else -> null
    }

    fun toComposeBlendMode(blendMode: String?): ComposeBlendMode =
        toComposeBlendModeOrNull(blendMode) ?: ComposeBlendMode.SrcOver

    fun applyToPaint(paint: Paint, blendMode: String?) {
        if (blendMode.isNullOrBlank() || blendMode.equals("normal", ignoreCase = true)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            toAndroidBlendMode(blendMode)?.let { paint.blendMode = it }
        } else {
            toPorterDuffMode(blendMode)?.let { paint.xfermode = PorterDuffXfermode(it) }
        }
    }

    fun createLayerPaint(blendMode: String?, alpha: Int = 255): Paint =
        Paint().apply {
            this.alpha = alpha
            applyToPaint(this, blendMode)
        }

    // Whitelist-based: unknown blend modes render normal, so they must not force an
    // offscreen buffer either — same behavior as "normal".
    fun needsOffscreenCompositing(blendMode: String?): Boolean =
        toComposeBlendModeOrNull(blendMode) != null

    private fun toAndroidBlendMode(blendMode: String): AndroidBlendMode? = when (blendMode.lowercase()) {
        "multiply" -> AndroidBlendMode.MULTIPLY
        "screen" -> AndroidBlendMode.SCREEN
        "overlay" -> AndroidBlendMode.OVERLAY
        "darken" -> AndroidBlendMode.DARKEN
        "lighten" -> AndroidBlendMode.LIGHTEN
        "color-dodge" -> AndroidBlendMode.COLOR_DODGE
        "color-burn" -> AndroidBlendMode.COLOR_BURN
        "hard-light" -> AndroidBlendMode.HARD_LIGHT
        "soft-light" -> AndroidBlendMode.SOFT_LIGHT
        "difference" -> AndroidBlendMode.DIFFERENCE
        "exclusion" -> AndroidBlendMode.EXCLUSION
        "hue" -> AndroidBlendMode.HUE
        "saturation" -> AndroidBlendMode.SATURATION
        "color" -> AndroidBlendMode.COLOR
        "luminosity" -> AndroidBlendMode.LUMINOSITY
        "linear-dodge", "lighter" -> AndroidBlendMode.PLUS
        else -> null
    }

    private fun toPorterDuffMode(blendMode: String): PorterDuff.Mode? = when (blendMode.lowercase()) {
        "multiply" -> PorterDuff.Mode.MULTIPLY
        "screen" -> PorterDuff.Mode.SCREEN
        "overlay" -> PorterDuff.Mode.OVERLAY
        "darken" -> PorterDuff.Mode.DARKEN
        "lighten" -> PorterDuff.Mode.LIGHTEN
        "difference" -> PorterDuff.Mode.XOR
        else -> null
    }
}

inline fun android.graphics.Canvas.withBlendLayer(
    blendMode: String?,
    alpha: Float,
    block: android.graphics.Canvas.() -> Unit,
) {
    val alphaInt = (alpha * 255f).toInt().coerceIn(0, 255)
    if (!EditorBlendModeMapper.needsOffscreenCompositing(blendMode)) {
        if (alphaInt >= 255) {
            block()
            return
        }
        // Normal blend still needs saveLayer so appearance.alpha is applied.
        val opacityPaint = Paint().apply { this.alpha = alphaInt }
        val save = saveLayer(null, opacityPaint)
        try {
            block()
        } finally {
            restoreToCount(save)
        }
        return
    }
    val layerPaint = EditorBlendModeMapper.createLayerPaint(blendMode, alphaInt)
    val save = saveLayer(null, layerPaint)
    try {
        block()
    } finally {
        restoreToCount(save)
    }
}
