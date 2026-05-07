package com.thgiang.image.feature.home.ui.components

import android.graphics.Bitmap

object NativeSeamlessBlender {
    init {
        runCatching { System.loadLibrary("portraitblend") }
    }

    external fun nativeSeamlessBlend(
        srcBitmap: Bitmap,
        dstBitmap: Bitmap,
        maskBitmap: Bitmap,
        outBitmap: Bitmap
    )
}
