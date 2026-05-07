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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import com.thgiang.image.core.data.backgroundremove.BackgroundRemoverRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

@Composable
fun CleanPreviewImage(
    uri: Uri,
    colorMatrix: ColorMatrix,
    backgroundRemoverRepository: BackgroundRemoverRepository?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
) {
    val context = LocalContext.current

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
        AsyncImage(
            model = uri,
            contentDescription = null,
            colorFilter = ColorFilter.colorMatrix(colorMatrix),
            modifier = Modifier.matchParentSize(),
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
