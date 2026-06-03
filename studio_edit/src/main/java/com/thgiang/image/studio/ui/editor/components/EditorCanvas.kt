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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import coil.compose.AsyncImage
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.thgiang.image.studio.R
import com.thgiang.image.studio.ui.editor.*
import com.thgiang.image.studio.ui.editor.theme.LocalEditorTokens

@Composable
fun EditorCanvasV2(
    templateAssetPath: String,
    templateSize: IntSize = IntSize(1000, 1000),
    layers: List<EditorLayer>,
    selectedLayerId: String?,
    onGesture: (GestureDelta) -> Unit,
    onGestureEnd: () -> Unit,
    onPickImage: () -> Unit,
    onSelectLayer: (String?) -> Unit,
    showOverlay: Boolean = false,
    viewportPadding: PaddingValues = PaddingValues(),
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val tokens = LocalEditorTokens.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            // ── Premium Clean Light: flat #EBEBEB workspace background ──
            .background(tokens.moduleBackground)
            .padding(viewportPadding),
        contentAlignment = Alignment.Center
    ) {
        val templateWidth  = with(density) { templateSize.width.toDp() }
        val templateHeight = with(density) { templateSize.height.toDp() }
        val calculatedScale = with(density) {
            kotlin.math.min(
                maxWidth  / templateWidth,
                maxHeight / templateHeight
            ).coerceAtMost(1.2f)
        }

        val displayWidth  = templateWidth  * calculatedScale
        val displayHeight = templateHeight * calculatedScale

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(layers, selectedLayerId, templateSize) {
                    detectTapGestures(onTap = { tap ->
                        var hitId: String? = null
                        val displayWidthPx  = with(density) { displayWidth.toPx() }
                        val displayHeightPx = with(density) { displayHeight.toPx() }
                        val templateLeftPx  = (size.width  - displayWidthPx)  / 2f
                        val templateTopPx   = (size.height - displayHeightPx) / 2f

                        // Hit-test từ layer trên cùng xuống dưới cùng
                        for (layer in layers.reversed()) {
                            if (!layer.product.isBackgroundRemoved || layer.product.foregroundUri == null) continue
                            val croppedSize     = layer.cropRatio.calculateSize(layer.product.baseSize.width.toFloat(), layer.product.baseSize.height.toFloat())
                            val objectWidthPx   = (croppedSize.width  * layer.viewport.scale * calculatedScale) + (EditorConfig.BB_PADDING_PX * 2f)
                            val objectHeightPx  = (croppedSize.height * layer.viewport.scale * calculatedScale) + (EditorConfig.BB_PADDING_PX * 2f)
                            val objectCenterX   = templateLeftPx + displayWidthPx  / 2f + (layer.viewport.offset.x * calculatedScale)
                            val objectCenterY   = templateTopPx  + displayHeightPx / 2f + (layer.viewport.offset.y * calculatedScale)
                            val dx = tap.x - objectCenterX
                            val dy = tap.y - objectCenterY
                            val angleRad   = Math.toRadians(-layer.viewport.rotation.toDouble())
                            val rotatedDx  = dx * kotlin.math.cos(angleRad) - dy * kotlin.math.sin(angleRad)
                            val rotatedDy  = dx * kotlin.math.sin(angleRad) + dy * kotlin.math.cos(angleRad)
                            val withinObject = kotlin.math.abs(rotatedDx) <= (objectWidthPx  / 2f) &&
                                kotlin.math.abs(rotatedDy) <= (objectHeightPx / 2f)

                            if (withinObject) {
                                hitId = layer.id
                                break
                            }
                        }
                        onSelectLayer(hitId)
                    })
                }
        ) {


            // ── Artboard card — template on white card with soft shadow ──
            Box(
                modifier = Modifier
                    .size(displayWidth, displayHeight)
                    .align(Alignment.Center)
                    .shadow(
                        elevation = 16.dp,
                        shape = RoundedCornerShape(16.dp),
                        ambientColor = Color.Black.copy(alpha = 0.10f),
                        spotColor   = Color.Black.copy(alpha = 0.08f)
                    )
                    .clip(RoundedCornerShape(16.dp))
                    .background(tokens.artboard),
                contentAlignment = Alignment.Center
            ) {
                // Template image fills the artboard
                val backgroundModel = remember(templateAssetPath) {
                    if (templateAssetPath.startsWith("http://") || 
                        templateAssetPath.startsWith("https://") || 
                        templateAssetPath.startsWith("content://") || 
                        templateAssetPath.startsWith("file://")) {
                        templateAssetPath
                    } else {
                        "file:///android_asset/$templateAssetPath"
                    }
                }
                AsyncImage(
                    model = backgroundModel,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )

                if (layers.isEmpty()) {
                    PickImagePlaceholder(
                        onClick = onPickImage,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    layers.forEach { layer ->
                        if (layer.product.isBackgroundRemoved && layer.product.foregroundUri != null) {
                            ProductLayerV2(
                                product = layer.product,
                                viewport = layer.viewport,
                                appearance = layer.appearance,
                                cropRatio = layer.cropRatio,
                                displayScale = calculatedScale,
                                templateSize = templateSize,
                                onGesture = { delta -> 
                                    if (layer.id == selectedLayerId) onGesture(delta)
                                },
                                onGestureEnd = {
                                    if (layer.id == selectedLayerId) onGestureEnd()
                                },
                                showOverlay = showOverlay,
                                showBoundingBox = layer.id == selectedLayerId,
                                onBoundingBoxVisible = { /* Not used */ },
                                onPickImage = onPickImage
                            )
                        } else if (layer.product.processing) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.White.copy(alpha = 0.75f)),
                                contentAlignment = Alignment.Center
                            ) {
                                StudioLottieLoader(modifier = Modifier.size(120.dp))
                            }
                        }
                    }
                }
            }

        }
    }
}

@Composable
fun StudioLottieLoader(modifier: Modifier = Modifier) {
    val composition by rememberLottieComposition(
        LottieCompositionSpec.Asset("animation/animation.lottie")
    )
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = LottieConstants.IterateForever,
        isPlaying = true
    )
    LottieAnimation(
        composition = composition,
        progress = { progress },
        modifier = modifier
    )
}
