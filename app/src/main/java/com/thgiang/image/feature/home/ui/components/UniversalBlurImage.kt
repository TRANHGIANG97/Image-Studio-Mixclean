package com.thgiang.image.feature.home.ui.components

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest

@Composable
fun UniversalBlurImage(
    imageUrl: String,
    blurRadius: Float,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
) {
    UniversalBlurImage(
        model = imageUrl,
        blurRadius = blurRadius,
        modifier = modifier,
        contentScale = contentScale
    )
}

@Composable
fun UniversalBlurImage(
    model: Any?,
    blurRadius: Float,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
) {
    val context = LocalContext.current
    val r = blurRadius.coerceIn(0f, 25f)

    // FIX: ImageRequest only depends on `model` (not `r`).
    // This prevents Coil from re-loading the image on every slider move.
    // Blur is applied via Compose modifier (GPU, zero-cost) instead.
    val request = remember(model) {
        ImageRequest.Builder(context)
            .data(model)
            .build()
    }

    // On Android 12+ (API 31+): Compose modifier.blur() uses RenderEffect → GPU, instant.
    // On Android < 12: modifier.blur() also works via RenderNode (API 29+) or falls back gracefully.
    // Either way, changing blurRadius only triggers a cheap redraw, NOT a Coil reload.
    val blurredModifier = if (r > 0f) modifier.blur(r.dp) else modifier

    AsyncImage(
        model = request,
        contentDescription = null,
        modifier = blurredModifier,
        contentScale = contentScale
    )
}
