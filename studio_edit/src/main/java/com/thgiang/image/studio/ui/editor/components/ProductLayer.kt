package com.thgiang.image.studio.ui.editor.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.thgiang.image.core.design.theme.AuroraCoral
import com.thgiang.image.studio.ui.editor.*
import kotlin.math.roundToInt

data class EditorGraphicsSpec(
    val alpha: Float,
    val shadowAlpha: Float,
    val shadowElevation: Float,
    val shadowDx: Float,
    val shadowDy: Float,
    val scaleX: Float,
    val scaleY: Float,
    val rotation: Float,
    val shadowColor: Color
)

@Composable
fun ProductLayerV2(
    product: EditorProduct,
    viewport: EditorViewport,
    appearance: EditorAppearance,
    cropRatio: CropRatio,
    displayScale: Float,
    templateSize: IntSize,
    onGesture: (GestureDelta) -> Unit,
    onGestureEnd: () -> Unit,
    showOverlay: Boolean = false,
    showBoundingBox: Boolean = false,
    onBoundingBoxVisible: (Boolean) -> Unit = {},
    onPickImage: () -> Unit = {},
    isLocked: Boolean = false
) {
    val density = LocalDensity.current
    
    val actualSize by remember(product.baseSize, cropRatio) {
        derivedStateOf {
            cropRatio.calculateSize(
                product.baseSize.width.toFloat(),
                product.baseSize.height.toFloat()
            )
        }
    }
    
    val displayOffset by remember(viewport.offset, displayScale) {
        derivedStateOf {
            IntOffset(
                (viewport.offset.x * displayScale).roundToInt(),
                (viewport.offset.y * displayScale).roundToInt()
            )
        }
    }
    
    val graphicsSpec = remember(viewport, appearance) {
        val alpha = appearance.alpha
        val shadowAlpha = alpha * shadowOpacityFromIntensity(appearance.shadowIntensity)
        val shadowElevation = appearance.shadowIntensity * 20f
        val shadowAngleRad = Math.toRadians(appearance.shadowAngle.toDouble())
        val shadowDx = (appearance.shadowDistance * kotlin.math.cos(shadowAngleRad)).toFloat()
        val shadowDy = (appearance.shadowDistance * kotlin.math.sin(shadowAngleRad)).toFloat()
        val scaleX = if (viewport.flippedH) -1f else 1f
        val scaleY = if (viewport.flippedV) -1f else 1f
        val rotation = viewport.rotation
        val shadowColor = Color(appearance.shadowColorArgb)
        EditorGraphicsSpec(
            alpha = alpha,
            shadowAlpha = shadowAlpha,
            shadowElevation = shadowElevation,
            shadowDx = shadowDx,
            shadowDy = shadowDy,
            scaleX = scaleX,
            scaleY = scaleY,
            rotation = rotation,
            shadowColor = shadowColor
        )
    }

    val originalWidth = with(density) { (product.baseSize.width * viewport.scale * displayScale).toInt().toDp() }
    val originalHeight = with(density) { (product.baseSize.height * viewport.scale * displayScale).toInt().toDp() }

    val cropShape = remember(actualSize, product.baseSize) {
        object : androidx.compose.ui.graphics.Shape {
            override fun createOutline(
                size: Size,
                layoutDirection: androidx.compose.ui.unit.LayoutDirection,
                density: androidx.compose.ui.unit.Density
            ): androidx.compose.ui.graphics.Outline {
                if (product.baseSize.width <= 0 || product.baseSize.height <= 0) {
                    return androidx.compose.ui.graphics.Outline.Rectangle(
                        androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height)
                    )
                }
                val scaleW = size.width / product.baseSize.width
                val scaleH = size.height / product.baseSize.height
                val cw = actualSize.width.toFloat() * scaleW
                val ch = actualSize.height.toFloat() * scaleH
                val left = (size.width - cw) / 2f
                val top = (size.height - ch) / 2f
                return androidx.compose.ui.graphics.Outline.Rectangle(
                    androidx.compose.ui.geometry.Rect(left, top, left + cw, top + ch)
                )
            }
        }
    }

    Box(
        modifier = Modifier
            .requiredSize(originalWidth, originalHeight)
            .offset { displayOffset }
    ) {
        // Shadow Layer
        if (appearance.shadowIntensity > 0.05f) {
            SubcomposeAsyncImage(
                model = product.foregroundUri,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = graphicsSpec.shadowAlpha
                        scaleX = graphicsSpec.scaleX
                        scaleY = graphicsSpec.scaleY
                        rotationZ = graphicsSpec.rotation
                        shadowElevation = graphicsSpec.shadowElevation
                        ambientShadowColor = Color.Black
                        spotShadowColor = Color.Black
                    }
                    .clip(cropShape)
                    .offset {
                        IntOffset(
                            (graphicsSpec.shadowDx * displayScale).roundToInt(),
                            (graphicsSpec.shadowDy * displayScale).roundToInt()
                        )
                    },
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(graphicsSpec.shadowColor),
                loading = { Box(Modifier.fillMaxSize().background(Color.Transparent)) }
            )
        }

        // Foreground Image Layer
        SubcomposeAsyncImage(
            model = product.foregroundUri,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = graphicsSpec.alpha
                    scaleX = graphicsSpec.scaleX
                    scaleY = graphicsSpec.scaleY
                    rotationZ = graphicsSpec.rotation
                }
                .clip(cropShape),
            contentScale = ContentScale.Fit,
            loading = { 
                Box(Modifier.fillMaxSize().background(Color.Transparent)) 
            },
            error = {
                Box(
                    Modifier.fillMaxSize().background(Color.Red.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = "Error loading image",
                        tint = Color.Red.copy(alpha = 0.5f)
                    )
                }
            }
        )

        // Pink Overlay (Subject Mask)
        AnimatedVisibility(
            visible = showOverlay,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            AsyncImage(
                model = product.foregroundUri,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = graphicsSpec.scaleX
                        scaleY = graphicsSpec.scaleY
                        rotationZ = graphicsSpec.rotation
                    }
                    .clip(cropShape),
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(AuroraCoral.copy(alpha = 0.55f))
            )
        }

        // Bounding Box Overlay
        BoundingBoxOverlayV6(
            modifier = Modifier
                .align(Alignment.Center)
                .requiredSize(
                    width = originalWidth + 80.dp,
                    height = originalHeight + 80.dp
                ),
            contentWidth = actualSize.width.toFloat(),
            contentHeight = actualSize.height.toFloat(),
            viewport = viewport,
            displayScale = displayScale,
            templateSize = templateSize,
            onGesture = onGesture,
            onGestureEnd = onGestureEnd,
            showBoundingBox = showBoundingBox,
            onBoundingBoxVisible = onBoundingBoxVisible,
            isLocked = isLocked
        )

        // Pink "Replace" Button for sample object
        if (product.isSample && !product.processing && !isLocked) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(72.dp)
                    .background(Color(0xFFFF2D55), shape = CircleShape)
                    .border(2.dp, Color.White, shape = CircleShape)
                    .clickable { onPickImage() },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(id = com.thgiang.image.studio.R.drawable.ic_replace_product),
                        contentDescription = "Thay thế",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Thay thế",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
