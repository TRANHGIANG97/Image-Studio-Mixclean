package com.thgiang.image.feature.home.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.net.Uri
import com.thgiang.image.core.data.backgroundremove.BackgroundRemoverRepository
import com.thgiang.image.core.util.blurBitmapForPortraitExport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import kotlin.math.min

suspend fun renderPortraitStylePreviewBitmap(
    context: Context,
    uri: Uri,
    blurRadius: Float,
    darkenAlpha: Float,
    vignette: Boolean,
    backgroundRemoverRepository: BackgroundRemoverRepository,
    maxSidePx: Int = 1600
): Bitmap? {
    val decoded = withContext(Dispatchers.IO) {
        decodeBitmapForPortraitExport(context, uri, maxSidePx)
    } ?: return null

    val repo = backgroundRemoverRepository
    val fg = withContext(Dispatchers.Default) {
        repo.getForegroundBitmap(decoded)
    }.getOrElse {
        if (!decoded.isRecycled) decoded.recycle()
        return null
    }

    val blurred = withContext(Dispatchers.Default) {
        blurBitmapForPortraitExport(decoded, blurRadius)
    }
    if (blurred !== decoded && !decoded.isRecycled) decoded.recycle()

    val layered = applyDarkenVignetteToBitmap(blurred, darkenAlpha, vignette)
    if (layered !== blurred && !blurred.isRecycled) blurred.recycle()

    val result = withContext(Dispatchers.Default) {
        compositeForegroundOverBackground(layered, fg)
    }
    if (!layered.isRecycled) layered.recycle()
    if (!fg.isRecycled) fg.recycle()

    return result
}

private fun compositeForegroundOverBackground(background: Bitmap, foreground: Bitmap): Bitmap {
    val w = background.width
    val h = background.height
    val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)
    canvas.drawBitmap(background, 0f, 0f, null)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    canvas.drawBitmap(foreground, 0f, 0f, paint)
    return result
}

private fun applyDarkenVignetteToBitmap(base: Bitmap, darkenAlpha: Float, vignette: Boolean): Bitmap {
    val a = darkenAlpha.coerceIn(0f, 1f)
    if (a <= 0f) return base

    val out = base.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(out)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    if (!vignette) {
        paint.color = android.graphics.Color.argb((a * 255).toInt().coerceIn(0, 255), 0, 0, 0)
        canvas.drawRect(0f, 0f, out.width.toFloat(), out.height.toFloat(), paint)
    } else {
        val w = out.width.toFloat()
        val h = out.height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val radius = (min(w, h) * 0.72f).coerceAtLeast(1f)
        val shader = RadialGradient(
            cx,
            cy,
            radius,
            intArrayOf(
                android.graphics.Color.argb(0, 0, 0, 0),
                android.graphics.Color.argb((a * 0.65f * 255).toInt().coerceIn(0, 255), 0, 0, 0),
                android.graphics.Color.argb((a * 255).toInt().coerceIn(0, 255), 0, 0, 0)
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        paint.shader = shader
        canvas.drawRect(0f, 0f, w, h, paint)
    }
    return out
}

private suspend fun decodeBitmapForPortraitExport(
    context: Context,
    uri: Uri,
    maxSidePx: Int
): Bitmap? = withContext(Dispatchers.IO) {
    fun open(): InputStream? = runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()

    val optsBounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    open()?.use { BitmapFactory.decodeStream(it, null, optsBounds) }
    val srcW = optsBounds.outWidth
    val srcH = optsBounds.outHeight
    if (srcW <= 0 || srcH <= 0) return@withContext null

    val largest = maxOf(srcW, srcH)
    var sample = 1
    while (largest / sample > maxSidePx) sample *= 2

    val opts = BitmapFactory.Options().apply {
        inJustDecodeBounds = false
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    open()?.use { BitmapFactory.decodeStream(it, null, opts) }
}
