package com.thgiang.image.studio.ui.editor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import coil.compose.AsyncImage
import com.thgiang.image.studio.ui.editor.*

@Composable
fun EditorCanvasV2(
    templateAssetPath: String,
    templateSize: IntSize = IntSize(1000, 1000),
    product: EditorProduct,
    viewport: EditorViewport,
    appearance: EditorAppearance,
    cropRatio: CropRatio,
    onGesture: (GestureDelta) -> Unit,
    onGestureEnd: () -> Unit,
    onPickImage: () -> Unit,
    onBoundingBoxVisible: (Boolean) -> Unit,
    showOverlay: Boolean = false,
    showBoundingBox: Boolean = false,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val templateWidth = with(density) { templateSize.width.toDp() }
        val templateHeight = with(density) { templateSize.height.toDp() }
        val calculatedScale = with(density) {
            kotlin.math.min(
                maxWidth / templateWidth,
                maxHeight / templateHeight
            ).coerceAtMost(1.2f)
        }

        val displayWidth = templateWidth * calculatedScale
        val displayHeight = templateHeight * calculatedScale

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(product, viewport, templateSize, showBoundingBox, cropRatio) {
                    detectTapGestures(onTap = { tap ->
                        if (!product.isBackgroundRemoved || product.foregroundUri == null) {
                            onBoundingBoxVisible(false)
                            return@detectTapGestures
                        }

                        val displayWidthPx = with(density) { displayWidth.toPx() }
                        val displayHeightPx = with(density) { displayHeight.toPx() }
                        val templateLeftPx = (size.width - displayWidthPx) / 2f
                        val templateTopPx = (size.height - displayHeightPx) / 2f
                        val croppedSize = cropRatio.calculateSize(product.baseSize.width.toFloat(), product.baseSize.height.toFloat())
                        val objectWidthPx = croppedSize.width * viewport.scale * calculatedScale
                        val objectHeightPx = croppedSize.height * viewport.scale * calculatedScale
                        val objectCenterX = templateLeftPx + displayWidthPx / 2f + (viewport.offset.x * calculatedScale)
                        val objectCenterY = templateTopPx + displayHeightPx / 2f + (viewport.offset.y * calculatedScale)
                        val dx = tap.x - objectCenterX
                        val dy = tap.y - objectCenterY
                        val angleRad = Math.toRadians(-viewport.rotation.toDouble())
                        val rotatedDx = dx * kotlin.math.cos(angleRad) - dy * kotlin.math.sin(angleRad)
                        val rotatedDy = dx * kotlin.math.sin(angleRad) + dy * kotlin.math.cos(angleRad)
                        val withinObject = kotlin.math.abs(rotatedDx) <= (objectWidthPx / 2f) &&
                            kotlin.math.abs(rotatedDy) <= (objectHeightPx / 2f)

                        if (withinObject) {
                            onBoundingBoxVisible(true)
                        } else {
                            onBoundingBoxVisible(false)
                        }
                    })
                }
        ) {
            Box(
                modifier = Modifier
                    .size(displayWidth, displayHeight)
                    .align(Alignment.Center),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = "file:///android_asset/$templateAssetPath",
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )

                if (product.isBackgroundRemoved && product.foregroundUri != null) {
                    ProductLayerV2(
                        product = product,
                        viewport = viewport,
                        appearance = appearance,
                        cropRatio = cropRatio,
                        displayScale = calculatedScale,
                        templateSize = templateSize,
                        onGesture = onGesture,
                        onGestureEnd = onGestureEnd,
                        showOverlay = showOverlay,
                        showBoundingBox = showBoundingBox,
                        onBoundingBoxVisible = onBoundingBoxVisible
                    )
                } else if (product.processing) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            StudioLottieLoader()
                        }
                    }
                } else {
                    PickImagePlaceholder(
                        onClick = onPickImage,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }
}

@Composable
fun StudioLottieLoader(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            strokeWidth = 3.dp,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
