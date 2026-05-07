package com.thgiang.image.core.util
import android.graphics.Bitmap
import android.graphics.Color

object BitmapAlphaUtils {

    fun sanitizeTransparent(bitmap: Bitmap): Bitmap {

        val w = bitmap.width
        val h = bitmap.height

        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        for (i in pixels.indices) {
            val a = Color.alpha(pixels[i])

            if (a == 0) {
                pixels[i] = Color.TRANSPARENT
            }
        }

        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
        bitmap.setHasAlpha(true)

        return bitmap
    }
}



