package com.thgiang.image.feature.home.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.thgiang.image.core.data.backgroundremove.BackgroundRemoverRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

@Composable
fun PortraitBlurImage(
    uri: Uri,
    blurRadius: Float,
    backgroundRemoverRepository: BackgroundRemoverRepository?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    darkenAlpha: Float = 0.20f,
    vignette: Boolean = true
) {
    val context = LocalContext.current
    val r by remember(blurRadius) { derivedStateOf { blurRadius.coerceIn(0f, 25f) } }
    val overlayAlpha by remember(darkenAlpha) { derivedStateOf { darkenAlpha.coerceIn(0f, 1f) } }

    var foreground by remember(uri) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(uri) {
        foreground = null

        val decoded = decodeBitmapForMlKit(context, uri, maxSidePx = 1600) ?: return@LaunchedEffect
        val repo = backgroundRemoverRepository
        if (repo == null) {
            decoded.recycle()
            return@LaunchedEffect
        }

        repo.getForegroundBitmap(decoded)
            .onSuccess { fg ->
                foreground = fg
            }
            .onFailure {
                decoded.recycle()
            }

        if (!decoded.isRecycled) decoded.recycle()
    }

    DisposableEffect(uri) {
        onDispose {
            foreground?.let { if (!it.isRecycled) it.recycle() }
            foreground = null
        }
    }

    Box(modifier = modifier) {
        UniversalBlurImage(
            model = uri,
            blurRadius = r,
            modifier = Modifier
                .matchParentSize()
                .drawWithCache {
                    val a = overlayAlpha
                    if (a <= 0f) {
                        onDrawWithContent { drawContent() }
                    } else if (!vignette) {
                        val overlay = Color.Black.copy(alpha = a)
                        onDrawWithContent {
                            drawContent()
                            drawRect(color = overlay, blendMode = BlendMode.SrcOver)
                        }
                    } else {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val radiusPx = (kotlin.math.min(size.width, size.height) * 0.72f).coerceAtLeast(1f)
                        val brush = Brush.radialGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0f),
                                Color.Black.copy(alpha = a * 0.65f),
                                Color.Black.copy(alpha = a)
                            ),
                            center = center,
                            radius = radiusPx
                        )
                        onDrawWithContent {
                            drawContent()
                            drawRect(brush = brush, blendMode = BlendMode.SrcOver)
                        }
                    }
                },
            contentScale = contentScale
        )

        foreground?.let { fg ->
            if (!fg.isRecycled) {
                Image(
                    bitmap = fg.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                    contentScale = contentScale
                )
            }
        }
    }
}

private suspend fun decodeBitmapForMlKit(
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
