package com.thgiang.image.studio.ui.editor.canvas
import com.thgiang.image.studio.ui.editor.canvas.*
import com.thgiang.image.studio.ui.editor.mapper.*

import androidx.compose.animation.*
import com.thgiang.image.studio.ui.editor.model.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.thgiang.image.core.design.theme.AuroraCoral
import com.thgiang.image.studio.R
import androidx.compose.ui.res.stringResource
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
    layer: EditorLayer,
    displayScale: Float,
    templateSize: IntSize,
    layerBlendMode: String? = null,
    onGesture: (GestureDelta) -> Unit,
    onGestureEnd: () -> Unit,
    showOverlay: Boolean = false,
    showBoundingBox: Boolean = false,
    onBoundingBoxVisible: (Boolean) -> Unit = {},
    onPickImage: (layerId: String) -> Unit = {},
    allLayers: List<EditorLayer> = emptyList(),
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val product = layer.product
    val viewport = layer.viewport
    val appearance = layer.appearance
    val cropRatio = layer.cropRatio
    val isLocked = layer.isLocked
    val strokeColorArgb = layer.strokeColorArgb
    val strokeWidthPx = layer.strokeWidthPx
    
    val actualSize by remember(layer.shapeWidthPx, layer.shapeHeightPx, cropRatio) {
        derivedStateOf {
            cropRatio.calculateSize(
                layer.shapeWidthPx,
                layer.shapeHeightPx
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
        val shadowElevation = appearance.resolvedShadowBlurRadius() * displayScale
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

    val originalWidth = with(density) { (actualSize.width * viewport.scale * displayScale).toInt().toDp() }
    val originalHeight = with(density) { (actualSize.height * viewport.scale * displayScale).toInt().toDp() }

    // Decode at the displayed pixel size instead of full resolution (OOM risk on low-end
    // devices). INEXACT lets Coil reuse larger cached bitmaps while the layer is scaled.
    val imageContext = LocalContext.current
    val targetPxWidth = (actualSize.width * viewport.scale * displayScale).roundToInt().coerceAtLeast(1)
    val targetPxHeight = (actualSize.height * viewport.scale * displayScale).roundToInt().coerceAtLeast(1)
    val foregroundRequest = remember(product.foregroundUri, targetPxWidth, targetPxHeight) {
        ImageRequest.Builder(imageContext)
            .data(product.foregroundUri)
            .size(targetPxWidth, targetPxHeight)
            .precision(Precision.INEXACT)
            .crossfade(true)
            .build()
    }
    val displayStrokeWidth = strokeWidthPx * viewport.scale * displayScale
    val hasStroke = strokeWidthPx > 0f && strokeColorArgb != null
    val blurRadius = appearance.resolvedShadowBlurRadius() * displayScale

    val cropShape = remember(actualSize) {
        object : androidx.compose.ui.graphics.Shape {
            override fun createOutline(
                size: Size,
                layoutDirection: androidx.compose.ui.unit.LayoutDirection,
                density: androidx.compose.ui.unit.Density
            ): androidx.compose.ui.graphics.Outline {
                if (actualSize.width <= 0 || actualSize.height <= 0) {
                    return androidx.compose.ui.graphics.Outline.Rectangle(
                        androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height)
                    )
                }
                val scaleW = size.width / actualSize.width
                val scaleH = size.height / actualSize.height
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

    val paddingExtra = 40.dp
    val paddingExtraPx = with(density) { paddingExtra.toPx() }

    Box(
        modifier = modifier
            .requiredSize(originalWidth + paddingExtra * 2, originalHeight + paddingExtra * 2)
            .offset {
                displayOffset
            }
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .requiredSize(originalWidth, originalHeight)
        ) {
            // Shadow Layer
            if (appearance.shadowIntensity > 0.05f) {
                SubcomposeAsyncImage(
                    model = foregroundRequest,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = graphicsSpec.shadowAlpha
                            rotationZ = graphicsSpec.rotation
                            if (blurRadius > 0.5f) {
                                renderEffect = BlurEffect(
                                    radiusX = blurRadius,
                                    radiusY = blurRadius,
                                    edgeTreatment = TileMode.Decal,
                                )
                            }
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
                model = foregroundRequest,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (hasStroke) {
                            Modifier.drawBehind {
                                val inset = displayStrokeWidth / 2f
                                drawRect(
                                    color = Color(strokeColorArgb!!),
                                    topLeft = Offset(inset, inset),
                                    size = Size(
                                        width = size.width - displayStrokeWidth,
                                        height = size.height - displayStrokeWidth,
                                    ),
                                    style = Stroke(width = displayStrokeWidth),
                                )
                            }
                        } else {
                            Modifier
                        }
                    )
                    .graphicsLayer {
                        alpha = graphicsSpec.alpha
                        rotationZ = graphicsSpec.rotation
                        blendMode = EditorBlendModeMapper.toComposeBlendMode(layerBlendMode)
                        compositingStrategy = if (EditorBlendModeMapper.needsOffscreenCompositing(layerBlendMode)) {
                            CompositingStrategy.Offscreen
                        } else {
                            CompositingStrategy.Auto
                        }
                    }
                    .clip(cropShape),
                contentScale = ContentScale.Fit,
                loading = {
                    Box(Modifier.fillMaxSize().background(Color.Transparent))
                },
                error = { state ->
                    android.util.Log.e(
                        "ProductLayer",
                        "Failed to load foreground image. URL/Uri: ${product.foregroundUri}, error: ${state.result.throwable.message}",
                        state.result.throwable
                    )
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
                            rotationZ = graphicsSpec.rotation
                        }
                        .clip(cropShape),
                    contentScale = ContentScale.Fit,
                    colorFilter = ColorFilter.tint(AuroraCoral.copy(alpha = 0.55f))
                )
            }
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
            lockAspectRatio = false,
            onGesture = onGesture,
            onGestureEnd = onGestureEnd,
            showBoundingBox = showBoundingBox,
            onBoundingBoxVisible = onBoundingBoxVisible,
            isLocked = isLocked,
            otherLayers = allLayers.filter { it.id != layer.id }
        )

        // 2-directional horizontal arrow replace button, compact, transparent background
        if (product.isSample && !product.processing && !isLocked) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(36.dp)
                    .background(Color.Transparent)
                    .clickable { onPickImage(layer.id) },
                contentAlignment = Alignment.Center
            ) {
                // Drop shadow for prominence
                Icon(
                    imageVector = Icons.Default.SwapHoriz,
                    contentDescription = null,
                    tint = Color.Black.copy(alpha = 0.6f),
                    modifier = Modifier.size(30.dp).offset(y = 1.dp)
                )
                // Main icon
                Icon(
                    imageVector = Icons.Default.SwapHoriz,
                    contentDescription = stringResource(R.string.studio_action_replace),
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}
