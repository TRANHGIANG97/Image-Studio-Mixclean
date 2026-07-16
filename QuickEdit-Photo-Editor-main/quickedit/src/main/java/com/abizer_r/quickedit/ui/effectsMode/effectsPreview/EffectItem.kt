package com.abizer_r.quickedit.ui.effectsMode.effectsPreview

import android.graphics.Bitmap
import java.util.UUID

sealed class EffectRecipe {
    data object Original : EffectRecipe()
    data object Grayscale : EffectRecipe()
    data object Blur : EffectRecipe()
    data class Acv(val assetPath: String) : EffectRecipe()
}

data class EffectItem(
    val id: String = UUID.randomUUID().toString(),
    /** Main-stage preview (may be downscaled for non-original recipes to avoid OOM). */
    val ogBitmap: Bitmap,
    /** Thumbnail strip preview. */
    val previewBitmap: Bitmap,
    val label: String,
    val recipe: EffectRecipe = EffectRecipe.Original,
)
