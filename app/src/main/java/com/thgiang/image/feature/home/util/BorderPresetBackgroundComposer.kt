package com.thgiang.image.feature.home.util
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.thgiang.image.core.model.BorderPresetStyle
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

internal suspend fun composeBorderPresetBackgroundBitmap(
    foreground: Bitmap,
    style: BorderPresetStyle
): Bitmap = withContext(Dispatchers.Default) {
    val preparedForeground = normalizeForegroundForCompositing(foreground)

    val width = preparedForeground.width
    val height = preparedForeground.height

    val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)

    val (outerColor, innerColor) = when (style) {
        BorderPresetStyle.SOLID -> Pair(
            android.graphics.Color.parseColor("#0F1117"),
            android.graphics.Color.parseColor("#FFFFFF")
        )

        BorderPresetStyle.WHITE -> Pair(
            android.graphics.Color.parseColor("#E8E8E8"),
            android.graphics.Color.parseColor("#FFFFFF")
        )

        BorderPresetStyle.GRADIENT -> Pair(
            android.graphics.Color.parseColor("#2B1907"),
            android.graphics.Color.parseColor("#FFF7E8")
        )

        BorderPresetStyle.NEON -> Pair(
            android.graphics.Color.parseColor("#0C2B3B"),
            android.graphics.Color.parseColor("#7EF2FF")
        )
    }

    val outerWidthPx = ((min(width, height) * 0.024f).toInt()).coerceIn(12, 34)
    val innerWidthPx = (outerWidthPx * 0.58f).toInt().coerceAtLeast(6)

    // Chỉ vẽ viền contour, không vẽ nền (rect/gradient) — tránh "tự tạo nền background không cần thiết".
    // Nền để trong suốt; chỉ có outer ring + inner ring + foreground.

    val outerRing = createOutlineRingBitmap(preparedForeground, outerColor, outerWidthPx)
    val innerRing = createOutlineRingBitmap(preparedForeground, innerColor, innerWidthPx)

    canvas.drawBitmap(outerRing, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    canvas.drawBitmap(innerRing, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    canvas.drawBitmap(
        preparedForeground,
        0f,
        0f,
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    )

    outerRing.recycle()
    innerRing.recycle()
    if (preparedForeground !== foreground) preparedForeground.recycle()
    output
}

private fun createOutlineRingBitmap(
    foreground: Bitmap,
    outlineColor: Int,
    outlineWidthPx: Int
): Bitmap {
    val width = foreground.width
    val height = foreground.height

    val ringBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val ringCanvas = Canvas(ringBitmap)

    // SRC_IN cần dest có alpha: kết quả = src_alpha * dest_color. Nếu không tô nền trước,
    // canvas trong suốt (dest_alpha=0) → viền không hiện trên ảnh đã xoá phông.
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = outlineColor
        alpha = 255
    }
    ringCanvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fillPaint)

    val spreadPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = PorterDuffColorFilter(outlineColor, PorterDuff.Mode.SRC_IN)
        alpha = 255
    }

    for (radius in outlineWidthPx downTo 1) {
        val angleStep = when {
            radius <= 6 -> 18
            radius <= 14 -> 12
            else -> 8
        }
        var angle = 0
        while (angle < 360) {
            val rad = Math.toRadians(angle.toDouble())
            val dx = (cos(rad) * radius).toFloat()
            val dy = (sin(rad) * radius).toFloat()
            ringCanvas.drawBitmap(foreground, dx, dy, spreadPaint)
            angle += angleStep
        }
    }

    val cutoutPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    }
    ringCanvas.drawBitmap(foreground, 0f, 0f, cutoutPaint)
    cutoutPaint.xfermode = null

    return ringBitmap
}




