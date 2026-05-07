package com.thgiang.image.feature.home.ui.components

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.thgiang.image.core.util.AdvancedBlurTransformation

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

    val request by remember(model, r) {
        derivedStateOf {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ImageRequest.Builder(context)
                    .data(model)
                    .build()
            } else {
                ImageRequest.Builder(context)
                    .data(model)
                    .transformations(AdvancedBlurTransformation(r))
                    .build()
            }
        }
    }

    val blurModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) modifier.blur(r.dp) else modifier

    AsyncImage(
        model = request,
        contentDescription = null,
        modifier = blurModifier,
        contentScale = contentScale
    )
}

