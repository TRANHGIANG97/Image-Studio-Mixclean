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
                .pointerInput(product, viewport, templateSize, showBoundingBox, cropRatio) {
                    detectTapGestures(onTap = { tap ->
                        if (!product.isBackgroundRemoved || product.foregroundUri == null) {
                            onBoundingBoxVisible(false)
                            return@detectTapGestures
                        }

                        val displayWidthPx  = with(density) { displayWidth.toPx() }
                        val displayHeightPx = with(density) { displayHeight.toPx() }
                        val templateLeftPx  = (size.width  - displayWidthPx)  / 2f
                        val templateTopPx   = (size.height - displayHeightPx) / 2f
                        val croppedSize     = cropRatio.calculateSize(product.baseSize.width.toFloat(), product.baseSize.height.toFloat())
                        val objectWidthPx   = croppedSize.width  * viewport.scale * calculatedScale
                        val objectHeightPx  = croppedSize.height * viewport.scale * calculatedScale
                        val objectCenterX   = templateLeftPx + displayWidthPx  / 2f + (viewport.offset.x * calculatedScale)
                        val objectCenterY   = templateTopPx  + displayHeightPx / 2f + (viewport.offset.y * calculatedScale)
                        val dx = tap.x - objectCenterX
                        val dy = tap.y - objectCenterY
                        val angleRad   = Math.toRadians(-viewport.rotation.toDouble())
                        val rotatedDx  = dx * kotlin.math.cos(angleRad) - dy * kotlin.math.sin(angleRad)
                        val rotatedDy  = dx * kotlin.math.sin(angleRad) + dy * kotlin.math.cos(angleRad)
                        val withinObject = kotlin.math.abs(rotatedDx) <= (objectWidthPx  / 2f) &&
                            kotlin.math.abs(rotatedDy) <= (objectHeightPx / 2f)

                        onBoundingBoxVisible(withinObject)
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
                        onBoundingBoxVisible = onBoundingBoxVisible,
                        onPickImage = onPickImage
                    )
                } else if (product.processing) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White.copy(alpha = 0.75f)),
                        contentAlignment = Alignment.Center
                    ) {
                        StudioLottieLoader(modifier = Modifier.size(120.dp))
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
