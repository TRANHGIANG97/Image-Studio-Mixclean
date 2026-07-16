package com.thgiang.image.studio.ui.editor.canvas
import com.thgiang.image.studio.ui.editor.canvas.*
import com.thgiang.image.studio.ui.editor.mapper.*

import androidx.compose.foundation.background
import com.thgiang.image.studio.ui.editor.model.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
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
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.PointerEventPass
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun EditorCanvasV2(
    templateAssetPath: String,
    templateBackgroundColor: Color = Color.White,
    templateSize: IntSize = IntSize(1000, 1000),
    layers: List<EditorLayer>,
    selectedLayerId: String?,
    selectedLayerIds: Set<String> = emptySet(),
    isCropToolActive: Boolean = false,
    onGesture: (GestureDelta) -> Unit,
    onGestureEnd: () -> Unit,
    onPickImage: (layerId: String?) -> Unit,
    onSelectLayer: (String?) -> Unit,
    onShapeTextCommit: (String) -> Unit = {},
    onSyncShapeSize: (widthPx: Float, heightPx: Float) -> Unit = { _, _ -> },
    showOverlay: Boolean = false,
    viewportPadding: PaddingValues = PaddingValues(),
    isLabelToolActive: Boolean = false,
    isShapeToolActive: Boolean = false,
    editingLayerId: String? = null,
    onEvent: (EditorEvent) -> Unit = {},
    onGestureActiveChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val tokens = LocalEditorTokens.current

    val defaultPlaceholder = stringResource(R.string.studio_text_default_placeholder)

    val coroutineScope = rememberCoroutineScope()
    // ── Canvas-level pinch-to-zoom + pan ──────────────────
    val canvasScaleAnim = remember { Animatable(1f) }
    val canvasScale = canvasScaleAnim.value
    val canvasOffsetAnim = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val canvasOffset = canvasOffsetAnim.value
    val isZoomedOrPanned by remember {
        derivedStateOf { canvasScale != 1f || canvasOffset != Offset.Zero }
    }

    // Backup states for text zoom focus
    var backupScale by remember { mutableFloatStateOf(1f) }
    var backupOffset by remember { mutableStateOf(Offset.Zero) }
    var hasBackup by remember { mutableStateOf(false) }

    var layerPickerHits by remember { mutableStateOf<List<EditorLayer>?>(null) }
    var quickActionsOffset by remember { mutableStateOf(Offset.Zero) }
    var isLayerGestureActive by remember { mutableStateOf(false) }
    val reportGestureActive: (Boolean) -> Unit = { active ->
        isLayerGestureActive = active
        onGestureActiveChanged(active)
    }

    // Helper: switch inline editing to a specific label layer
    val onStartTextEdit: (String) -> Unit = { layerId ->
        onEvent(EditorEvent.StartTextEdit(layerId))
    }

    fun isLayerSelected(layerId: String): Boolean =
        SelectionState.isSelected(layers, selectedLayerId, selectedLayerIds, layerId)

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

    val currentLayers by rememberUpdatedState(layers)
    val currentSelectedLayerId by rememberUpdatedState(selectedLayerId)
    val currentSelectedLayerIds by rememberUpdatedState(selectedLayerIds)
    val currentIsCropToolActive by rememberUpdatedState(isCropToolActive)
        val currentIsLabelToolActive by rememberUpdatedState(isLabelToolActive)
        val currentIsShapeToolActive by rememberUpdatedState(isShapeToolActive)
        val currentCanvasScale by rememberUpdatedState(canvasScale)
        val currentCanvasOffset by rememberUpdatedState(canvasOffset)
        val currentIsLayerGestureActive by rememberUpdatedState(isLayerGestureActive)
        val currentEditingLayerId by rememberUpdatedState(editingLayerId)

        val isImeVisible = WindowInsets.isImeVisible
        val imeHeight = WindowInsets.ime.getBottom(density)

        LaunchedEffect(editingLayerId, isImeVisible, imeHeight) {
            if (editingLayerId != null && isImeVisible) {
                delay(350)
                if (editingLayerId == null) return@LaunchedEffect
                val activeLayer = layers.firstOrNull { it.id == editingLayerId }
                if (activeLayer != null) {
                    // 1. Capture current zoom & pan as backup if not already saved
                    if (!hasBackup) {
                        backupScale = canvasScaleAnim.value
                        backupOffset = canvasOffsetAnim.value
                        hasBackup = true
                    }

                    // 2. Center of text box in template space
                    val layerCenterTemplate = activeLayer.viewport.offset

                    // 3. Zoom level for focus (e.g. 1.6x)
                    val targetScale = 1.6f

                    // 4. Calculate display coordinates to push it to center of visible area
                    val targetOffsetX = -(layerCenterTemplate.x * calculatedScale * targetScale)
                    val targetOffsetY = -(layerCenterTemplate.y * calculatedScale * targetScale)

                    // 5. Run smooth spring animation for premium feel
                    launch {
                        canvasScaleAnim.animateTo(
                            targetValue = targetScale,
                            animationSpec = androidx.compose.animation.core.spring(
                                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                                stiffness = androidx.compose.animation.core.Spring.StiffnessLow
                            )
                        )
                    }
                    launch {
                        canvasOffsetAnim.animateTo(
                            targetValue = Offset(targetOffsetX, targetOffsetY),
                            animationSpec = androidx.compose.animation.core.spring(
                                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                                stiffness = androidx.compose.animation.core.Spring.StiffnessLow
                            )
                        )
                    }
                }
            } else if (editingLayerId == null) {
                // Restore back to original viewport state
                if (hasBackup) {
                    launch {
                        canvasScaleAnim.animateTo(
                            targetValue = backupScale,
                            animationSpec = androidx.compose.animation.core.spring(
                                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                                stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                            )
                        )
                    }
                    launch {
                        canvasOffsetAnim.animateTo(
                            targetValue = backupOffset,
                            animationSpec = androidx.compose.animation.core.spring(
                                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                                stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                            )
                        )
                    }
                    hasBackup = false
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                // Two-finger pinch always zooms/pans the whole canvas (even with a selection).
                // Single-finger is left to the bounding-box overlay when a layer is selected.
                .pointerInput(Unit) {
                    val velocityTracker = androidx.compose.ui.input.pointer.util.VelocityTracker()

                    awaitEachGesture {
                        coroutineScope.launch { canvasOffsetAnim.stop() }

                        var zoom = 1f
                        var pan = Offset.Zero
                        var pastTouchSlop = false
                        val touchSlop = viewConfiguration.touchSlop
                        var didCanvasTransform = false

                        val down = awaitFirstDown(requireUnconsumed = false)
                        velocityTracker.resetTracking()
                        velocityTracker.addPosition(down.uptimeMillis, down.position)

                        while (true) {
                            // Final pass: BB handles 1-finger first; we still see 2-finger pinches.
                            val event = awaitPointerEvent(PointerEventPass.Final)
                            val pressed = event.changes.filter { it.pressed }
                            val activePointers = pressed.size

                            if (activePointers == 0) {
                                if (didCanvasTransform && event.changes.size == 1 && pastTouchSlop) {
                                    val velocity = velocityTracker.calculateVelocity()
                                    if (kotlin.math.hypot(velocity.x, velocity.y) > 200f) {
                                        coroutineScope.launch {
                                            canvasOffsetAnim.animateDecay(
                                                initialVelocity = Offset(velocity.x, velocity.y),
                                                animationSpec = exponentialDecay()
                                            )
                                        }
                                    }
                                }
                                break
                            }

                            // With a selection, ignore 1-finger so corner/body drags stay on the object.
                            // Keep waiting so a second finger can start canvas zoom.
                            if (activePointers < 2) {
                                if (currentSelectedLayerId != null || currentIsLayerGestureActive) {
                                    continue
                                }
                                if (event.changes.any { it.isConsumed }) break
                            }

                            if (activePointers == 1) {
                                val change = pressed.first()
                                velocityTracker.addPosition(change.uptimeMillis, change.position)
                            }

                            val panChange = event.calculatePan()
                            val zoomChange = event.calculateZoom()

                            if (!pastTouchSlop) {
                                zoom *= zoomChange
                                pan += panChange
                                val centroidSize = event.calculateCentroidSize(useCurrent = false)
                                val zoomMotion = kotlin.math.abs(1f - zoom) * centroidSize
                                val panMotion = pan.getDistance()

                                if (zoomMotion > touchSlop || panMotion > touchSlop) {
                                    pastTouchSlop = true
                                }
                            }

                            if (pastTouchSlop) {
                                if (zoomChange != 1f || panChange != Offset.Zero) {
                                    didCanvasTransform = true
                                    coroutineScope.launch {
                                        canvasScaleAnim.snapTo(
                                            (canvasScaleAnim.value * zoomChange).coerceIn(0.12f, 5.8f)
                                        )
                                        canvasOffsetAnim.snapTo(canvasOffsetAnim.value + panChange)
                                    }
                                }
                                event.changes.forEach { it.consume() }
                            }
                        }

                        // Bounce back if canvas scale is outside hard limits [0.2f, 5.0f]
                        val currentScale = canvasScaleAnim.value
                        if (currentScale < 0.2f || currentScale > 5.0f) {
                            val targetScale = currentScale.coerceIn(0.2f, 5.0f)
                            coroutineScope.launch {
                                canvasScaleAnim.animateTo(
                                    targetValue = targetScale,
                                    animationSpec = androidx.compose.animation.core.spring(
                                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                                        stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                                    )
                                )
                            }
                            if (targetScale <= 1.0f) {
                                coroutineScope.launch {
                                    canvasOffsetAnim.animateTo(
                                        targetValue = Offset.Zero,
                                        animationSpec = androidx.compose.animation.core.spring(
                                            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                                            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { tap ->
                            if (currentEditingLayerId != null || currentIsLayerGestureActive) return@detectTapGestures
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            val untransformedTapX = cx + (tap.x - cx - currentCanvasOffset.x) / currentCanvasScale
                            val untransformedTapY = cy + (tap.y - cy - currentCanvasOffset.y) / currentCanvasScale

                            val displayWidthPx = with(density) { displayWidth.toPx() }
                            val displayHeightPx = with(density) { displayHeight.toPx() }
                            val selectionHitPaddingPx = with(density) { 24.dp.toPx() }
                            val templateLeftPx = (size.width - displayWidthPx) / 2f
                            val templateTopPx = (size.height - displayHeightPx) / 2f

                            val hitContext = LayerHitTestContext(
                                tapX = untransformedTapX,
                                tapY = untransformedTapY,
                                displayWidthPx = displayWidthPx,
                                displayHeightPx = displayHeightPx,
                                templateLeftPx = templateLeftPx,
                                templateTopPx = templateTopPx,
                                calculatedScale = calculatedScale,
                                selectionHitPaddingPx = selectionHitPaddingPx,
                            )
                            val rawHits = LayerHitTest.hitLayersAtPoint(currentLayers, hitContext)
                            val preferFrame = currentIsShapeToolActive ||
                                (!currentIsLabelToolActive &&
                                    currentLayers.find { it.id == currentSelectedLayerId }?.isFrameLayer == true)
                            val hits = LayerGroupOps.collapseHits(rawHits, currentLayers, preferFrame = preferFrame)
                            when {
                                hits.isEmpty() -> onSelectLayer(null)
                                hits.size == 1 -> {
                                    val hit = hits.first()
                                    val selectId = when {
                                        preferFrame && hit.groupId != null ->
                                            currentLayers.frameInGroup(hit)?.id ?: hit.id
                                        else -> hit.id
                                    }
                                    onSelectLayer(selectId)
                                }
                                else -> layerPickerHits = hits
                            }
                        },
                        onDoubleTap = { tap ->
                            if (currentEditingLayerId != null || currentIsLayerGestureActive) return@detectTapGestures
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            val untransformedTapX = cx + (tap.x - cx - currentCanvasOffset.x) / currentCanvasScale
                            val untransformedTapY = cy + (tap.y - cy - currentCanvasOffset.y) / currentCanvasScale

                            val displayWidthPx = with(density) { displayWidth.toPx() }
                            val displayHeightPx = with(density) { displayHeight.toPx() }
                            val selectionHitPaddingPx = with(density) { 24.dp.toPx() }
                            val templateLeftPx = (size.width - displayWidthPx) / 2f
                            val templateTopPx = (size.height - displayHeightPx) / 2f

                            val hitContext = LayerHitTestContext(
                                tapX = untransformedTapX,
                                tapY = untransformedTapY,
                                displayWidthPx = displayWidthPx,
                                displayHeightPx = displayHeightPx,
                                templateLeftPx = templateLeftPx,
                                templateTopPx = templateTopPx,
                                calculatedScale = calculatedScale,
                                selectionHitPaddingPx = selectionHitPaddingPx,
                            )
                            val doubleHits = LayerGroupOps.collapseHits(
                                LayerHitTest.hitLayersAtPoint(currentLayers, hitContext),
                                currentLayers,
                                preferFrame = currentIsShapeToolActive,
                            )
                            if (doubleHits.size == 1 && doubleHits.first().isLabelLayer) {
                                onStartTextEdit(doubleHits.first().id)
                            } else {
                                // Double tap on canvas background to zoom or reset
                                coroutineScope.launch {
                                    if (isZoomedOrPanned) {
                                        // Reset zoom/pan smoothly using spring animation
                                        launch {
                                            canvasScaleAnim.animateTo(
                                                targetValue = 1f,
                                                animationSpec = androidx.compose.animation.core.spring(
                                                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                                                    stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                                                )
                                            )
                                        }
                                        launch {
                                            canvasOffsetAnim.animateTo(
                                                targetValue = Offset.Zero,
                                                animationSpec = androidx.compose.animation.core.spring(
                                                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                                                    stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                                                )
                                            )
                                        }
                                    } else {
                                        // Zoom in to 2.0x centered at the tapped position
                                        val targetScale = 2f
                                        val targetOffsetX = -(tap.x - cx) * targetScale
                                        val targetOffsetY = -(tap.y - cy) * targetScale
                                        
                                        launch {
                                            canvasScaleAnim.animateTo(
                                                targetValue = targetScale,
                                                animationSpec = androidx.compose.animation.core.spring(
                                                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                                                    stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                                                )
                                            )
                                        }
                                        launch {
                                            canvasOffsetAnim.animateTo(
                                                targetValue = Offset(targetOffsetX, targetOffsetY),
                                                animationSpec = androidx.compose.animation.core.spring(
                                                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                                                    stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        onLongPress = { tap ->
                            if (currentEditingLayerId != null || currentIsLayerGestureActive) return@detectTapGestures
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            val untransformedTapX = cx + (tap.x - cx - currentCanvasOffset.x) / currentCanvasScale
                            val untransformedTapY = cy + (tap.y - cy - currentCanvasOffset.y) / currentCanvasScale

                            val displayWidthPx = with(density) { displayWidth.toPx() }
                            val displayHeightPx = with(density) { displayHeight.toPx() }
                            val selectionHitPaddingPx = with(density) { 24.dp.toPx() }
                            val templateLeftPx = (size.width - displayWidthPx) / 2f
                            val templateTopPx = (size.height - displayHeightPx) / 2f

                            val hitContext = LayerHitTestContext(
                                tapX = untransformedTapX,
                                tapY = untransformedTapY,
                                displayWidthPx = displayWidthPx,
                                displayHeightPx = displayHeightPx,
                                templateLeftPx = templateLeftPx,
                                templateTopPx = templateTopPx,
                                calculatedScale = calculatedScale,
                                selectionHitPaddingPx = selectionHitPaddingPx,
                            )
                            val longPreferFrame = currentIsShapeToolActive ||
                                (!currentIsLabelToolActive &&
                                    currentLayers.find { it.id == currentSelectedLayerId }?.isFrameLayer == true)
                            val longHits = LayerGroupOps.collapseHits(
                                LayerHitTest.hitLayersAtPoint(currentLayers, hitContext),
                                currentLayers,
                                preferFrame = longPreferFrame,
                            )
                            if (longHits.size == 1) {
                                onEvent(EditorEvent.ToggleLayerSelection(longHits.first().id))
                            }
                        },
                    )
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
                                contentScale = ContentScale.Crop,
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
                        onClick = { onPickImage(null) },
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    layers.forEach { layer ->
                        if (!layer.isVisible) return@forEach
                        key(layer.id) {
                        when {
                            layer.isVectorContentLayer -> {
                                val isSelected = isLayerSelected(layer.id)
                                val isEditingThisLayer = editingLayerId == layer.id && layer.shouldRenderLabelContent
                                ShapeTextLayer(
                                    layer         = layer,
                                    displayScale  = calculatedScale,
                                    templateSize  = templateSize,
                                    onGesture     = { delta ->
                                        if (isSelected && !layer.isLocked) onGesture(delta)
                                    },
                                    onGestureEnd  = {
                                        if (isSelected && !layer.isLocked) onGestureEnd()
                                    },
                                    showBoundingBox = isSelected && !isEditingThisLayer,
                                    isLocked      = layer.isLocked,
                                    isInlineEditing = isEditingThisLayer,
                                    onRequestInlineEdit = { onStartTextEdit(layer.id) },
                                    onTapToSelect = { onSelectLayer(layer.id) },
                                    onCommitInlineEdit = { text ->
                                        onShapeTextCommit(text.ifBlank { defaultPlaceholder })
                                        onEvent(EditorEvent.FinishTextEdit)
                                    },
                                    onUpdateInlineEdit = { text ->
                                        onShapeTextCommit(text)
                                    },
                                    onInlineSelectionChange = { start, end ->
                                        onEvent(EditorEvent.UpdateInlineTextSelection(start, end))
                                    },
                                    onSyncShapeSize = { widthPx, heightPx ->
                                        if (isSelected && !layer.isLocked) {
                                            onSyncShapeSize(widthPx, heightPx)
                                        }
                                    },
                                    allLayers = layers,
                                    onGestureActiveChanged = reportGestureActive,
                                    modifier      = Modifier.align(Alignment.Center)
                                )
                            }
                            layer.type == LayerType.IMAGE -> {
                                val isSelected = isLayerSelected(layer.id)
                                val showCropOverlay = isCropToolActive && isSelected && layer.id == selectedLayerId
                                if (layer.product.foregroundUri != null) {
                                    ProductLayerV2(
                                        layer = layer,
                                        displayScale = calculatedScale,
                                        templateSize = templateSize,
                                        layerBlendMode = layer.blendMode,
                                        suppressLiveShadowBlur = isLayerGestureActive && isSelected,
                                        onGestureActiveChanged = reportGestureActive,
                                        onGesture = { delta ->
                                            if (isSelected && !layer.isLocked && !isCropToolActive) onGesture(delta)
                                        },
                                        onGestureEnd = {
                                            if (isSelected && !layer.isLocked && !isCropToolActive) onGestureEnd()
                                        },
                                        showOverlay = showOverlay && isSelected,
                                        showBoundingBox = isSelected && !showCropOverlay && editingLayerId == null,
                                        onBoundingBoxVisible = { visible ->
                                            if (visible) onSelectLayer(layer.id)
                                        },
                                        onPickImage = onPickImage,
                                        allLayers = layers,
                                        modifier = Modifier.align(Alignment.Center)
                                    )
                                    if (showCropOverlay) {
                                        CropOverlay(
                                            layer = layer,
                                            displayScale = calculatedScale,
                                            onCropPan = { delta -> onEvent(EditorEvent.UpdateCropPan(delta)) },
                                            onCropPanEnd = { onEvent(EditorEvent.CommitCrop) },
                                            modifier = Modifier.align(Alignment.Center),
                                        )
                                    }
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
                            layer.type == LayerType.SHADOW_REGION -> {
                                val isSelected = isLayerSelected(layer.id)
                                ShadowRegionLayer(
                                    layer = layer,
                                    displayScale = calculatedScale,
                                    templateSize = templateSize,
                                    onGesture = { delta ->
                                        if (isSelected && !layer.isLocked) onGesture(delta)
                                    },
                                    onGestureEnd = {
                                        if (isSelected && !layer.isLocked) onGestureEnd()
                                    },
                                    showBoundingBox = isSelected,
                                    isLocked = layer.isLocked,
                                    allLayers = layers,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }
                        }
                        }
                    }
                }

                // ── Top-most Replace Buttons for Sample/Replaceable layers ──
                // Hide while inline text edit is active — center replace icon steals taps from the text move handle.
                if (editingLayerId == null) {
                layers.forEach { layer ->
                    if (layer.product.isSample && !layer.product.processing && !layer.isLocked && layer.isVisible) {
                        val actualSize = layer.cropRatio.calculateSize(layer.shapeWidthPx, layer.shapeHeightPx)
                        val originalWidth = with(density) { (actualSize.width * layer.viewport.scale * calculatedScale).toInt().toDp() }
                        val originalHeight = with(density) { (actualSize.height * layer.viewport.scale * calculatedScale).toInt().toDp() }
                        val displayOffset = IntOffset(
                            (layer.viewport.offsetX * calculatedScale).roundToInt(),
                            (layer.viewport.offsetY * calculatedScale).roundToInt()
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .requiredSize(originalWidth, originalHeight)
                                .offset { displayOffset }
                        ) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(36.dp)
                                    .clickable { onPickImage(layer.id) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SwapHoriz,
                                    contentDescription = stringResource(R.string.studio_action_replace),
                                    tint = Color(0xFF2F6DE1),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
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
                        coroutineScope.launch {
                            launch { canvasScaleAnim.animateTo(1f) }
                            launch { canvasOffsetAnim.animateTo(Offset.Zero) }
                        }
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
        // ── Floating Quick Actions (Shape) ───────────────────────
        val activeLayer = layers.find { it.id == selectedLayerId }
        if (activeLayer != null) {
            val canvasTop = ((maxHeight - displayHeight) / 2f).coerceAtLeast(0.dp)
            ShapeQuickActionsBar(
                layer = activeLayer,
                visible = true,
                onEvent = onEvent,
                quickActionsOffset = quickActionsOffset,
                onQuickActionsOffsetChange = { quickActionsOffset = it },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = canvasTop)
                    .offset { IntOffset(quickActionsOffset.x.roundToInt(), quickActionsOffset.y.roundToInt()) }
                    .zIndex(10f)
            )
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
    allLayers: List<EditorLayer> = emptyList(),
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val baseW = layer.shapeWidthPx
    val baseH = layer.shapeHeightPx

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
            lockAspectRatio = false,
            onGesture = onGesture,
            onGestureEnd = onGestureEnd,
            showBoundingBox = showBoundingBox,
            onBoundingBoxVisible = {},
            isLocked = isLocked,
            otherLayers = allLayers.filter { it.id != layer.id }
        )
    }
}
