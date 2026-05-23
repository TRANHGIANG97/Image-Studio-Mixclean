package com.thgiang.image.studio.ui.editor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import coil.compose.AsyncImage
import com.thgiang.image.studio.R
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
    BoxWithConstraints(
        modifier = modifier.background(
            Brush.radialGradient(
                colors = listOf(Color(0xFF0E4D50), Color(0xFF071011), Color.Black),
                center = Offset.Zero,
                radius = 1300f
            )
        ),
        contentAlignment = Alignment.Center
    ) {
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
            Canvas(modifier = Modifier.matchParentSize()) {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF1D6A67).copy(alpha = 0.62f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.30f, size.height * 0.44f),
                        radius = size.width * 0.46f
                    )
                )
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xB06E451D),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.18f, size.height * 0.50f),
                        radius = size.width * 0.33f
                    )
                )
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.28f)
                        ),
                        startY = size.height * 0.56f,
                        endY = size.height
                    )
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 16.dp, top = 16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x7F1E1F24))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "${(viewport.scale * 100).toInt()}%",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp)
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 16.dp, top = 16.dp)
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x7F1E1F24))
                    .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.Icon(
                    painter = painterResource(R.drawable.ic_tool_crop),
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(16.dp)
                )
            }

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
