package com.thgiang.image.studio.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.compose.SubcomposeAsyncImage

@Composable
fun CloudFirstSubcomposeAsyncImage(
    sourcePath: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    loading: @Composable () -> Unit,
    error: @Composable () -> Unit
) {
    val model = remember(sourcePath) { sourcePath?.toCloudResolvedAssetUrl() }

    if (model == null) {
        error()
        return
    }

    SubcomposeAsyncImage(
        model = model,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        loading = { _ -> loading() },
        error = { _ ->
            error()
        }
    )
}
