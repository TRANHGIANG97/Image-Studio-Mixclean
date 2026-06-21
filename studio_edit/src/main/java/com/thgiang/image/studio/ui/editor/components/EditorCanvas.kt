package com.thgiang.image.studio.ui.editor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import kotlin.math.roundToInt
import android.graphics.BitmapFactory
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.thgiang.image.studio.R
import com.thgiang.image.studio.ui.editor.*
import com.thgiang.image.studio.ui.editor.theme.LocalEditorTokens
import com.thgiang.image.studio.util.openAssetSourceInputStream
import com.thgiang.image.studio.util.toAssetModel
import coil.compose.SubcomposeAsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun EditorCanvasV2(
    templateAssetPath: String,
    templateBackgroundColor: Color = Color.White,
    templateSize: IntSize = IntSize(1000, 1000),
    layers: List<EditorLayer>,
    selectedLayerId: String?,
    onGesture: (GestureDelta) -> Unit,
    onGestureEnd: () -> Unit,
    onPickImage: () -> Unit,
    onSelectLayer: (String?) -> Unit,
    onShapeTextCommit: (String) -> Unit = {},
    showOverlay: Boolean = false,
    viewportPadding: PaddingValues = PaddingValues(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val tokens = LocalEditorTokens.current

    // ── Canvas-level pinch-to-zoom + pan ──────────────────
    var canvasScale by remember { mutableFloatStateOf(1f) }
    var canvasOffset by remember { mutableStateOf(Offset.Zero) }
    val isZoomedOrPanned by remember {
        derivedStateOf { canvasScale != 1f || canvasOffset != Offset.Zero }
    }
    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        canvasScale = (canvasScale * zoomChange).coerceIn(0.2f, 5f)
        canvasOffset += panChange
    }
    var inlineEditingLayerId by remember { mutableStateOf<String?>(null) }
    var layerPickerHits by remember { mutableStateOf<List<EditorLayer>?>(null) }

    LaunchedEffect(selectedLayerId) {
        if (inlineEditingLayerId != selectedLayerId) {
            inlineEditingLayerId = null
        }
    }

    if (layerPickerHits != null) {
        LayerPickerSheet(
            hitLayers = layerPickerHits!!,
            allLayers = layers,
            selectedLayerId = selectedLayerId,
            onSelectLayer = { id ->
                layerPickerHits = null
                onSelectLayer(id)
            },
            onDismiss = { layerPickerHits = null },
        )
    }

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
                // Canvas-level multi-touch zoom/pan trên outer box
                .transformable(transformableState)
                .pointerInput(layers, selectedLayerId, templateSize, layerPickerHits) {
                    detectTapGestures(onTap = { tap ->
                        val displayWidthPx = with(density) { displayWidth.toPx() }
                        val displayHeightPx = with(density) { displayHeight.toPx() }
                        val selectionHitPaddingPx = with(density) { 24.dp.toPx() }
                        val templateLeftPx = (size.width - displayWidthPx) / 2f
                        val templateTopPx = (size.height - displayHeightPx) / 2f

                        val hitContext = LayerHitTestContext(
                            tapX = tap.x,
                            tapY = tap.y,
                            displayWidthPx = displayWidthPx,
                            displayHeightPx = displayHeightPx,
                            templateLeftPx = templateLeftPx,
                            templateTopPx = templateTopPx,
                            calculatedScale = calculatedScale,
                            selectionHitPaddingPx = selectionHitPaddingPx,
                        )
                        val hits = LayerHitTest.hitLayersAtPoint(layers, hitContext)
                        when {
                            hits.isEmpty() -> onSelectLayer(null)
                            hits.size == 1 -> onSelectLayer(hits.first().id)
                            else -> layerPickerHits = hits
                        }
                    })
                }
        ) {

            // ── Artboard card — template on white card with soft shadow ──
            Box(
                modifier = Modifier
                    .size(displayWidth, displayHeight)
                    .align(Alignment.Center)
                    // Apply canvas-level zoom/pan transform trên artboard
                    .graphicsLayer(
                        scaleX = canvasScale,
                        scaleY = canvasScale,
                        translationX = canvasOffset.x,
                        translationY = canvasOffset.y
                    )
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
                val isBlankBackground = templateAssetPath.isBlank()
                val model = remember(templateAssetPath) { templateAssetPath.toAssetModel() }

                when {
                    !isBlankBackground && templateAssetPath != "null" -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(templateBackgroundColor)
                        ) {
                            SubcomposeAsyncImage(
                                model = model,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit,
                                loading = {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        StudioLottieLoader(modifier = Modifier.size(96.dp))
                                    }
                                },
                                error = { state ->
                                     android.util.Log.e(
                                         "EditorCanvas",
                                         "Failed to load background template image. path: $templateAssetPath, resolvedModel: $model, error: ${state.result.throwable.message}",
                                         state.result.throwable
                                     )
                                     Box(
                                         modifier = Modifier.fillMaxSize(),
                                         contentAlignment = Alignment.Center
                                     ) {
                                         Icon(
                                             imageVector = Icons.Filled.Error,
                                             contentDescription = "Asset load error",
                                             tint = Color(0xFFEF4444)
                                         )
                                     }
                                 }
                            )
                        }
                    }
                    isBlankBackground || templateAssetPath == "null" -> {
                        // Clean solid white background for blank templates
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(templateBackgroundColor)
                        )
                    }
                }

                if (layers.isEmpty()) {
                    PickImagePlaceholder(
                        onClick = onPickImage,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    layers.forEach { layer ->
                        if (!layer.isVisible) return@forEach
                        when (layer.type) {
                            LayerType.SHAPE_TEXT -> {
                                ShapeTextLayer(
                                    layer         = layer,
                                    displayScale  = calculatedScale,
                                    templateSize  = templateSize,
                                    onGesture     = { delta ->
                                        if (layer.id == selectedLayerId && !layer.isLocked) onGesture(delta)
                                    },
                                    onGestureEnd  = {
                                        if (layer.id == selectedLayerId && !layer.isLocked) onGestureEnd()
                                    },
                                    showBoundingBox = layer.id == selectedLayerId,
                                    isLocked      = layer.isLocked,
                                    isInlineEditing = layer.id == inlineEditingLayerId,
                                    onRequestInlineEdit = { inlineEditingLayerId = layer.id },
                                    onCommitInlineEdit = { text ->
                                        onShapeTextCommit(text)
                                        inlineEditingLayerId = null
                                    },
                                    modifier      = Modifier.align(Alignment.Center)
                                )
                            }
                            LayerType.IMAGE -> {
                                if (layer.product.isBackgroundRemoved && layer.product.foregroundUri != null) {
                                    ProductLayerV2(
                                        product = layer.product,
                                        viewport = layer.viewport,
                                        appearance = layer.appearance,
                                        cropRatio = layer.cropRatio,
                                        displayScale = calculatedScale,
                                        templateSize = templateSize,
                                        layerBlendMode = layer.blendMode,
                                        onGesture = { delta ->
                                            if (layer.id == selectedLayerId && !layer.isLocked) onGesture(delta)
                                        },
                                        onGestureEnd = {
                                            if (layer.id == selectedLayerId && !layer.isLocked) onGestureEnd()
                                        },
                                        showOverlay = showOverlay,
                                        showBoundingBox = layer.id == selectedLayerId,
                                        isLocked = layer.isLocked,
                                        strokeColorArgb = layer.resolveStrokeColorArgb(),
                                        strokeWidthPx = layer.resolveStrokeWidthPx(),
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
                            LayerType.SHADOW_REGION -> {
                                ShadowRegionLayer(
                                    layer = layer,
                                    displayScale = calculatedScale,
                                    templateSize = templateSize,
                                    onGesture = { delta ->
                                        if (layer.id == selectedLayerId && !layer.isLocked) onGesture(delta)
                                    },
                                    onGestureEnd = {
                                        if (layer.id == selectedLayerId && !layer.isLocked) onGestureEnd()
                                    },
                                    showBoundingBox = layer.id == selectedLayerId,
                                    isLocked = layer.isLocked,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }
                        }
                    }
                }

            }

        }

        // ── Reset Zoom button — floating bottom-left ──────
        if (isZoomedOrPanned) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 92.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.White.copy(alpha = 0.92f))
                    .clickable {
                        canvasScale = 1f
                        canvasOffset = Offset.Zero
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.RestartAlt,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = tokens.textPrimary
                    )
                    Text(
                        text = stringResource(R.string.studio_reset_zoom),
                        fontSize = 11.sp,
                        color = tokens.textPrimary,
                        fontWeight = FontWeight.Medium
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

/**
 * Checkerboard brush for transparent backgrounds.
 * Copy từ QuickEdit — dùng làm nền dưới layer đã xóa nền.
 */
@Composable
fun rememberCheckerboardBrush(): ShaderBrush {
    val density = LocalDensity.current
    val tilePx = with(density) { 8.dp.toPx().toInt().coerceAtLeast(1) }
    val size = tilePx * 2

    val bmp = remember(tilePx) {
        val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint().apply { isAntiAlias = false }

        paint.color = android.graphics.Color.parseColor("#F2F2F2")
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)

        paint.color = android.graphics.Color.parseColor("#E1E1E1")
        canvas.drawRect(0f, 0f, tilePx.toFloat(), tilePx.toFloat(), paint)
        canvas.drawRect(tilePx.toFloat(), tilePx.toFloat(), size.toFloat(), size.toFloat(), paint)
        bitmap
    }

    return remember(bmp) {
        ShaderBrush(ImageShader(bmp.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
    }
}

@Composable
fun ShadowRegionLayer(
    layer: EditorLayer,
    displayScale: Float,
    templateSize: IntSize,
    onGesture: (GestureDelta) -> Unit,
    onGestureEnd: () -> Unit,
    showBoundingBox: Boolean = false,
    isLocked: Boolean = false,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val baseW = layer.product.baseSize.width.toFloat()
    val baseH = layer.product.baseSize.height.toFloat()

    val widthDp = with(density) { (baseW * layer.viewport.scale * displayScale).toInt().coerceAtLeast(1).toDp() }
    val heightDp = with(density) { (baseH * layer.viewport.scale * displayScale).toInt().coerceAtLeast(1).toDp() }
    val displayOffset = remember(layer.viewport.offset, displayScale) {
        IntOffset(
            (layer.viewport.offset.x * displayScale).roundToInt(),
            (layer.viewport.offset.y * displayScale).roundToInt()
        )
    }
    val color = Color(layer.appearance.shadowColorArgb)
    val rawIntensity = layer.appearance.shadowIntensity
    val intensity = if (rawIntensity > 0f) rawIntensity else 0.70f
    val alpha = (layer.appearance.alpha * intensity).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .requiredSize(widthDp, heightDp)
            .offset { displayOffset }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = layer.viewport.rotation }
        ) {
            val radiusX = size.width * 0.52f
            val radiusY = size.height * 0.92f
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(
                        color.copy(alpha = (color.alpha * alpha * 0.95f).coerceIn(0f, 1f)),
                        color.copy(alpha = (color.alpha * alpha * 0.55f).coerceIn(0f, 1f)),
                        color.copy(alpha = 0f)
                    ),
                    center = Offset(size.width / 2f, size.height * 0.55f),
                    radius = maxOf(radiusX, radiusY)
                )
            )
        }

        BoundingBoxOverlayV6(
            modifier = Modifier
                .align(Alignment.Center)
                .requiredSize(
                    width = widthDp + 80.dp,
                    height = heightDp + 80.dp
                ),
            contentWidth = baseW,
            contentHeight = baseH,
            viewport = layer.viewport,
            displayScale = displayScale,
            templateSize = templateSize,
            onGesture = onGesture,
            onGestureEnd = onGestureEnd,
            showBoundingBox = showBoundingBox,
            onBoundingBoxVisible = {},
            isLocked = isLocked
        )
    }
}
