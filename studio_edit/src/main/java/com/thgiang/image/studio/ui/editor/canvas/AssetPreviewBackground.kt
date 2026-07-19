package com.thgiang.image.studio.ui.editor.canvas

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * Light checkerboard for transparent sticker thumbnails.
 * High contrast with dark icons (black SVG/PNG) and still readable for white assets.
 * (Dark navy grid was indistinguishable from black stickers.)
 */
@Composable
fun rememberAssetPreviewCheckerboardBrush(): ShaderBrush {
    val density = LocalDensity.current
    val tilePx = with(density) { 8.dp.toPx().toInt().coerceAtLeast(1) }
    val size = tilePx * 2

    val bmp = remember(tilePx) {
        val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint().apply { isAntiAlias = false }

        // Slate-50 base + Slate-300 squares — standard light transparency preview
        paint.color = android.graphics.Color.parseColor("#F8FAFC")
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)

        paint.color = android.graphics.Color.parseColor("#CBD5E1")
        canvas.drawRect(0f, 0f, tilePx.toFloat(), tilePx.toFloat(), paint)
        canvas.drawRect(tilePx.toFloat(), tilePx.toFloat(), size.toFloat(), size.toFloat(), paint)
        bitmap
    }

    return remember(bmp) {
        ShaderBrush(ImageShader(bmp.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
    }
}

@Composable
fun Modifier.assetPreviewCheckerboardBackground(): Modifier {
    val checkerboard = rememberAssetPreviewCheckerboardBrush()
    return drawBehind { drawRect(brush = checkerboard) }
}
