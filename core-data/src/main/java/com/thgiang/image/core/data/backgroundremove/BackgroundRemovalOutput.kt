package com.thgiang.image.core.data.backgroundremove

import android.graphics.Bitmap

data class BackgroundRemovalOutput(
    val foregroundToDisplay: Bitmap,
    val foregroundToSave: Bitmap
)
