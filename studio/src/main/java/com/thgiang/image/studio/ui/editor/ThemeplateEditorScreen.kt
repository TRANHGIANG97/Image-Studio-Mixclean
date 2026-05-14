package com.thgiang.image.studio.ui.editor

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.PhotoSizeSelectLarge
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbCloudy
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.thgiang.image.studio.R
import com.thgiang.image.studio.model.StudioThemeplate
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeplateEditorScreen(
    themeplate: StudioThemeplate,
    onBack: () -> Unit,
    onDone: (Uri?) -> Unit = {},
    viewModel: ThemeplateEditorViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.setProductImage(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(themeplate.titleResId))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.studio_back))
                    }
                },
                actions = {
                    if (state.isExporting) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(24.dp).padding(end = 12.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(
                            onClick = {
                                viewModel.exportFinalImage(
                                    templateAssetPath = themeplate.assetPath,
                                    onSuccess = { uri ->
                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                message = context.getString(R.string.studio_export_success),
                                                duration = SnackbarDuration.Short
                                            )
                                        }
                                        onDone(uri)
                                    },
                                    onError = { msg ->
                                        scope.launch {
                                            snackbarHostState.showSnackbar(msg)
                                        }
                                    }
                                )
                            },
                            enabled = state.isBackgroundRemoved
                        ) {
                            Icon(
                                Icons.Default.Done,
                                contentDescription = stringResource(R.string.studio_done),
                                tint = if (state.isBackgroundRemoved) MaterialTheme.colorScheme.onSurface
                                       else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            EditorBottomToolbar(
                selectedTool = state.selectedTool,
                onToolSelected = viewModel::selectTool,
                onReplaceImage = { pickImageLauncher.launch("image/*") },
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Canvas area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(
                        if (isSystemInDarkTheme()) Color(0xFF1A1A1A)
                        else Color(0xFFF0EDE8)
                    )
            ) {
                // Template background
                AsyncImage(
                    model = "file:///android_asset/${themeplate.assetPath}",
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .aspectRatio(state.cropRatio.width / state.cropRatio.height),
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.Center
                )

                // Product image overlay (if selected)
                if (state.productImageUri != null) {
                    val displayUri = state.foregroundCacheUri ?: state.productImageUri

                    // Combined container: gesture handlers on product itself
                    // Product container: 300dp base size for predictable scaling and interaction
                    Box(
                        modifier = Modifier
                            .size(300.dp)
                            .align(Alignment.Center)
                    ) {
                        // Shadow layer — behind product
                        if (state.shadowIntensity > 0.05f) {
                            AsyncImage(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        translationX = state.offsetX + 8.dp.toPx()
                                        translationY = state.offsetY + 8.dp.toPx()
                                        scaleX = state.scaleX * (if (state.flippedH) -1f else 1f)
                                        scaleY = state.scaleY * (if (state.flippedV) -1f else 1f)
                                        rotationZ = state.rotation
                                        alpha = state.shadowIntensity * 0.3f
                                    },
                                model = displayUri,
                                contentDescription = null,
                                contentScale = ContentScale.Fit
                            )
                        }

                        // Product image — receives touch gestures (drag + zoom + rotate)
                        AsyncImage(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    translationX = state.offsetX
                                    translationY = state.offsetY
                                    scaleX = state.scaleX * (if (state.flippedH) -1f else 1f)
                                    scaleY = state.scaleY * (if (state.flippedV) -1f else 1f)
                                    rotationZ = state.rotation
                                    alpha = state.alpha
                                }
                                .onGloballyPositioned { coords ->
                                    Log.d("BoundingBox", "onGloballyPositioned: size=${coords.size}")
                                    viewModel.updateContentSize(
                                        coords.size.width.toFloat(),
                                        coords.size.height.toFloat()
                                    )
                                }
                                .pointerInput(Unit) {
                                    detectTransformGestures { _, pan, zoom, rotation ->
                                        viewModel.updateOffset(pan.x, pan.y)
                                        if (zoom != 1f) {
                                            viewModel.updateScaleUniform(zoom)
                                        }
                                        if (rotation != 0f) {
                                            viewModel.updateRotation(rotation)
                                        }
                                    }
                                },
                            model = displayUri,
                            contentDescription = null,
                            contentScale = ContentScale.Fit
                        )

                        // Bounding box overlay — shown when product is loaded and bg removed
                        if (state.isBackgroundRemoved && state.contentWidth > 0f) {
                            BoundingBoxOverlay(
                                modifier = Modifier.fillMaxSize(),
                                offsetX = state.offsetX,
                                offsetY = state.offsetY,
                                scaleX = state.scaleX,
                                scaleY = state.scaleY,
                                rotation = state.rotation,
                                contentWidth = state.contentWidth,
                                contentHeight = state.contentHeight,
                                onDragInside = { dx, dy -> viewModel.updateOffset(dx, dy) },
                                onScaleAbsolute = { s -> viewModel.setScale(s) },
                                onRotateAbsolute = { r -> viewModel.setRotation(r) }
                            )
                        }
                    }
                } else {
                    // Placeholder with modern replace icon
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .clickable { pickImageLauncher.launch("image/*") }
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .shadow(6.dp, CircleShape)
                                    .clip(CircleShape)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_replace_product),
                                    contentDescription = null,
                                    tint = Color.Unspecified
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = stringResource(R.string.studio_tap_to_pick_product),
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }

                // Processing overlay for background removal
                if (state.isProcessing) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.Black.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            androidx.compose.material3.CircularProgressIndicator(
                                color = Color.White
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.studio_processing),
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            // Contextual controls based on selected tool
            EditorControls(
                tool = state.selectedTool,
                shadowIntensity = state.shadowIntensity,
                alpha = state.alpha,
                cropRatio = state.cropRatio,
                onShadowIntensityChange = viewModel::updateShadowIntensity,
                onAlphaChange = viewModel::updateAlpha,
                onCropRatioChange = viewModel::selectCropRatio,
                onReplaceImage = { pickImageLauncher.launch("image/*") },
                onRotateLeft = { viewModel.updateRotation(-90f) },
                onRotateRight = { viewModel.updateRotation(90f) },
                onFlipHorizontal = viewModel::flipHorizontal,
                onFlipVertical = viewModel::flipVertical,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun EditorBottomToolbar(
    selectedTool: EditorTool,
    onToolSelected: (EditorTool) -> Unit,
    onReplaceImage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tools = listOf(
        EditorTool.REPLACE to Icons.Default.Photo,
        EditorTool.LAYOUT to Icons.Default.DragIndicator,
        EditorTool.ROTATE to Icons.Default.Refresh,
        EditorTool.SHADOW to Icons.Default.WbSunny,
        EditorTool.TRANSPARENCY to Icons.Default.Opacity,
        EditorTool.CROP to Icons.Default.CropSquare
    )

    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        tools.forEach { (tool, icon) ->
            val isSelected = selectedTool == tool
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        if (tool == EditorTool.REPLACE) onReplaceImage()
                        else onToolSelected(tool)
                    }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = when (tool) {
                        EditorTool.REPLACE -> stringResource(R.string.studio_tool_replace)
                        EditorTool.LAYOUT -> stringResource(R.string.studio_tool_layout)
                        EditorTool.ROTATE -> stringResource(R.string.studio_tool_rotateflip)
                        EditorTool.SHADOW -> stringResource(R.string.studio_tool_shadow)
                        EditorTool.TRANSPARENCY -> stringResource(R.string.studio_tool_transparency)
                        EditorTool.CROP -> stringResource(R.string.studio_tool_crop)
                    },
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EditorControls(
    tool: EditorTool,
    shadowIntensity: Float,
    alpha: Float,
    cropRatio: CropRatio,
    onShadowIntensityChange: (Float) -> Unit,
    onAlphaChange: (Float) -> Unit,
    onCropRatioChange: (CropRatio) -> Unit,
    onReplaceImage: () -> Unit,
    onRotateLeft: () -> Unit,
    onRotateRight: () -> Unit,
    onFlipHorizontal: () -> Unit,
    onFlipVertical: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        when (tool) {
            EditorTool.REPLACE -> {
                Text(
                    text = stringResource(R.string.studio_replace_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            EditorTool.LAYOUT -> {
                Text(
                    text = stringResource(R.string.studio_layout_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            EditorTool.ROTATE -> {
                EditorLayoutControls(
                    onRotateLeft = onRotateLeft,
                    onRotateRight = onRotateRight,
                    onFlipHorizontal = onFlipHorizontal,
                    onFlipVertical = onFlipVertical
                )
            }
            EditorTool.SHADOW -> {
                Text(
                    text = stringResource(R.string.studio_shadow_label, (shadowIntensity * 100).toInt()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = shadowIntensity,
                    onValueChange = onShadowIntensityChange,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
            EditorTool.TRANSPARENCY -> {
                Text(
                    text = stringResource(R.string.studio_transparency_label, (alpha * 100).toInt()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = alpha,
                    onValueChange = onAlphaChange,
                    valueRange = 0.1f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
            EditorTool.CROP -> {
                Text(
                    text = stringResource(R.string.studio_crop_label),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CropRatio.entries.forEach { ratio ->
                        val isSelected = cropRatio == ratio
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    else Color.Transparent
                                )
                                .then(
                                    if (isSelected) Modifier.border(
                                        1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)
                                    ) else Modifier
                                )
                                .clickable { onCropRatioChange(ratio) }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = ratio.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

private enum class HandleType { TL, TR, BL, BR, TOP, RIGHT, BOTTOM, LEFT, ROTATE, INSIDE, NONE }

@Composable
private fun BoundingBoxOverlay(
    modifier: Modifier,
    offsetX: Float,
    offsetY: Float,
    scaleX: Float,
    scaleY: Float,
    rotation: Float,
    contentWidth: Float,
    contentHeight: Float,
    onDragInside: (Float, Float) -> Unit,
    onScaleAbsolute: (Float) -> Unit,
    onRotateAbsolute: (Float) -> Unit
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    var activeHandle by remember { mutableStateOf(HandleType.NONE) }
    var isInteracting by remember { mutableStateOf(false) }

    // Initial state capture to prevent jitter
    var startScale by remember { mutableFloatStateOf(1f) }
    var startRotation by remember { mutableFloatStateOf(0f) }
    var startTouchAngle by remember { mutableFloatStateOf(0f) }
    var startTouchDist by remember { mutableFloatStateOf(1f) }
    var lastSnappedAngle by remember { mutableFloatStateOf(-1f) }

    val cw = contentWidth * abs(scaleX)
    val ch = contentHeight * abs(scaleY)

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { touch ->
                        isInteracting = true
                        val cx = size.width / 2f + offsetX
                        val cy = size.height / 2f + offsetY
                        val local = toLocal(touch, cx, cy, 0f, 0f, rotation)
                        
                        val hw = cw / 2f
                        val hh = ch / 2f
                        val radius = 40f
                        
                        startScale = scaleX
                        startRotation = rotation
                        startTouchAngle = Math.toDegrees(Math.atan2((touch.y - cy).toDouble(), (touch.x - cx).toDouble())).toFloat()
                        startTouchDist = Offset(touch.x - cx, touch.y - cy).getDistance(Offset.Zero).coerceAtLeast(1f)

                        activeHandle = when {
                            local.getDistance(Offset(0f, hh + 40.dp.toPx())) < radius + 20f -> HandleType.ROTATE
                            local.getDistance(Offset(-hw, -hh)) < radius -> HandleType.TL
                            local.getDistance(Offset(hw, -hh)) < radius -> HandleType.TR
                            local.getDistance(Offset(-hw, hh)) < radius -> HandleType.BL
                            local.getDistance(Offset(hw, hh)) < radius -> HandleType.BR
                            local.x in -hw..hw && local.y in -hh..hh -> HandleType.INSIDE
                            else -> HandleType.NONE
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val cx = size.width / 2f + offsetX
                        val cy = size.height / 2f + offsetY
                        val touch = change.position
                        
                        when (activeHandle) {
                            HandleType.INSIDE -> onDragInside(dragAmount.x, dragAmount.y)
                            HandleType.TL, HandleType.TR, HandleType.BL, HandleType.BR -> {
                                val currentDist = Offset(touch.x - cx, touch.y - cy).getDistance(Offset.Zero)
                                val newScale = startScale * (currentDist / startTouchDist)
                                onScaleAbsolute(newScale)
                            }
                            HandleType.ROTATE -> {
                                val currentAngle = Math.toDegrees(Math.atan2((touch.y - cy).toDouble(), (touch.x - cx).toDouble())).toFloat()
                                val angleDelta = currentAngle - startTouchAngle
                                val targetRotation = startRotation + angleDelta
                                
                                val snapped = snapAngle(targetRotation, listOf(0f, 45f, 90f, 135f, 180f, 225f, 270f, 315f, 360f), 5f)
                                if (snapped != targetRotation && snapped != lastSnappedAngle) {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    lastSnappedAngle = snapped
                                } else if (snapped == targetRotation) {
                                    lastSnappedAngle = -1f
                                }
                                onRotateAbsolute(snapped)
                            }
                            else -> {}
                        }
                    },
                    onDragEnd = {
                        isInteracting = false
                        activeHandle = HandleType.NONE
                    }
                )
            }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = offsetX
                    translationY = offsetY
                    rotationZ = rotation
                }
        ) {
            val hw = cw / 2f
            val hh = ch / 2f
            val cx = size.width / 2f
            val cy = size.height / 2f

            // Border
            drawRoundRect(Color.Black.copy(0.1f), Offset(cx - hw - 1, cy - hh - 1), Size(cw + 2, ch + 2), style = Stroke(2.dp.toPx()))
            drawRoundRect(Color.White, Offset(cx - hw, cy - hh), Size(cw, ch), style = Stroke(1.dp.toPx()))

            // Grid
            if (isInteracting) {
                val gA = 0.3f
                for (i in 1..2) {
                    drawLine(Color.White.copy(gA), Offset(cx - hw + i * (cw / 3), cy - hh), Offset(cx - hw + i * (cw / 3), cy + hh), 1f)
                    drawLine(Color.White.copy(gA), Offset(cx - hw, cy - hh + i * (ch / 3)), Offset(cx + hw, cy - hh + i * (ch / 3)), 1f)
                }
            }

            // 3. Rotation Handle
            drawLine(Color.White, Offset(cx, cy + hh), Offset(cx, cy + hh + 20.dp.toPx()), 1.dp.toPx())
            val handleAccentColor = Color(0xFF4A90D9)
            drawCircle(Color.White, 10.dp.toPx(), Offset(cx, cy + hh + 30.dp.toPx()))
            drawCircle(handleAccentColor, 10.dp.toPx(), Offset(cx, cy + hh + 30.dp.toPx()), style = Stroke(2.dp.toPx()))

            // 4. Corners
            listOf(Offset(-hw, -hh), Offset(hw, -hh), Offset(-hw, hh), Offset(hw, hh)).forEach { p ->
                drawCircle(Color.White, 6.dp.toPx(), Offset(cx + p.x, cy + p.y))
                drawCircle(Color.LightGray.copy(alpha = 0.5f), 6.dp.toPx(), Offset(cx + p.x, cy + p.y), style = Stroke(1.dp.toPx()))
            }
        }
    }
}

private fun toLocal(touch: Offset, cx: Float, cy: Float, offX: Float, offY: Float, rot: Float): Offset {
    val tx = touch.x - cx - offX
    val ty = touch.y - cy - offY
    val rad = Math.toRadians((-rot).toDouble())
    val cos = Math.cos(rad).toFloat()
    val sin = Math.sin(rad).toFloat()
    return Offset(tx * cos - ty * sin, tx * sin + ty * cos)
}



private fun snapAngle(angle: Float, snapPoints: List<Float>, threshold: Float): Float {
    val normalized = ((angle % 360f) + 360f) % 360f
    for (snap in snapPoints) {
        if (abs(normalized - snap) <= threshold) return snap
        if (abs(normalized - (snap + 360f)) <= threshold) return snap
    }
    return normalized
}

private fun Offset.getDistance(other: Offset): Float {
    val dx = x - other.x
    val dy = y - other.y
    return Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
}

private fun abs(v: Float) = if (v < 0f) -v else v

@Composable
private fun EditorLayoutControls(
    onRotateLeft: () -> Unit,
    onRotateRight: () -> Unit,
    onFlipHorizontal: () -> Unit,
    onFlipVertical: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        LayoutActionButton(
            icon = ImageVector.vectorResource(id = R.drawable.ic_rotate_left),
            label = stringResource(R.string.studio_layout_rotate_left),
            onClick = onRotateLeft
        )
        LayoutActionButton(
            icon = ImageVector.vectorResource(id = R.drawable.ic_rotate_right),
            label = stringResource(R.string.studio_layout_rotate_right),
            onClick = onRotateRight
        )
        LayoutActionButton(
            icon = ImageVector.vectorResource(id = R.drawable.ic_flip_horizontal),
            label = stringResource(R.string.studio_layout_flip_horizontal),
            onClick = onFlipHorizontal
        )
        LayoutActionButton(
            icon = ImageVector.vectorResource(id = R.drawable.ic_flip_vertical),
            label = stringResource(R.string.studio_layout_flip_vertical),
            onClick = onFlipVertical
        )
    }
}

@Composable
private fun LayoutActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}