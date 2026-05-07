package com.abizer_r.quickedit.utils.effectsMode

import android.content.Context
import android.graphics.Bitmap
import jp.co.cyberagent.android.gpuimage.GPUImage
import jp.co.cyberagent.android.gpuimage.filter.GPUImageGaussianBlurFilter

object BitmapBlurFilter {
    fun apply(context: Context, original: Bitmap): Bitmap {
        val gpuImage = GPUImage(context)
        gpuImage.setImage(original)
        val filter = GPUImageGaussianBlurFilter(5.0f)
        gpuImage.setFilter(filter)
        return gpuImage.bitmapWithFilterApplied
    }
}
