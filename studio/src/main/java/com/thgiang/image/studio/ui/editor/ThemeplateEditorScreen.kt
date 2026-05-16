package com.thgiang.image.studio.ui.editor

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.thgiang.image.studio.R
import com.thgiang.image.studio.model.StudioThemeplate
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeplateEditorScreen(
    themeplate: StudioThemeplate,
    onBack: () -> Unit,
    onDone: (Uri?) -> Unit = {},
    viewModel: ThemeplateEditorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()
    val canRedo by viewModel.canRedo.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Snackbar effects
    LaunchedEffect(state.exportResult) {
        state.exportResult?.let { uri ->
            snackbarHostState.showSnackbar("Đã lưu ảnh")
            onDone(uri)
        }
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Long)
        }
    }

    LaunchedEffect(themeplate.assetPath) {
        viewModel.onEvent(EditorEvent.LoadTemplate(themeplate.assetPath))
    }

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.onEvent(EditorEvent.SetProductImage(it)) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(themeplate.titleResId)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.onEvent(EditorEvent.Undo) },
                        enabled = canUndo
                    ) {
                        Icon(
                            Icons.Default.Undo,
                            contentDescription = "Undo",
                            tint = if (canUndo) LocalContentColor.current 
                                   else LocalContentColor.current.copy(alpha = 0.38f)
                        )
                    }
                    IconButton(
                        onClick = { viewModel.onEvent(EditorEvent.Redo) },
                        enabled = canRedo
                    ) {
                        Icon(
                            Icons.Default.Redo,
                            contentDescription = "Redo",
                            tint = if (canRedo) LocalContentColor.current
                                   else LocalContentColor.current.copy(alpha = 0.38f)
                        )
                    }
                    
                    if (state.isExporting) {
                        StudioLottieLoader(
                            modifier = Modifier
                                .size(48.dp)
                                .padding(end = 8.dp)
                        )
                    } else {
                        IconButton(
                            onClick = {
                                viewModel.onEvent(EditorEvent.Export(themeplate.assetPath))
                            },
                            enabled = state.canExport
                        ) {
                            Icon(
                                Icons.Default.Done,
                                contentDescription = "Done",
                                tint = if (state.canExport) 
                                    MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            EditorBottomToolbar(
                selectedTool = state.selectedTool,
                onToolSelected = { viewModel.onEvent(EditorEvent.SelectTool(it)) },
                onReplaceImage = { pickImageLauncher.launch("image/*") },
                modifier = Modifier.fillMaxWidth().navigationBarsPadding()
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(if (isSystemInDarkTheme()) Color(0xFF1A1A1A) else Color(0xFFF0EDE8))
        ) {
            if (state.template.loaded && state.template.originalSize.width > 0) {
                EditorCanvasV2(
                    templateAssetPath = themeplate.assetPath,
                    templateSize = state.template.originalSize,
                    product = state.product,
                    viewport = state.viewport,
                    appearance = state.appearance,
                    onGesture = { delta ->
                        if (delta.pan != Offset.Zero) {
                            viewModel.onEvent(EditorEvent.UpdateOffset(delta.pan))
                        }
                        if (delta.scale != 1f) {
                            viewModel.onEvent(EditorEvent.UpdateScale(delta.scale))
                        }
                        if (delta.rotation != 0f) {
                            viewModel.onEvent(EditorEvent.UpdateRotation(delta.rotation))
                        }
                    },
                    onGestureEnd = { viewModel.onEvent(EditorEvent.CommitTransform) },
                    onPickImage = { pickImageLauncher.launch("image/*") },
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            // Controls panel with improved animation
            AnimatedVisibility(
                visible = state.product.isBackgroundRemoved,
                enter = slideInVertically { it } + fadeIn(tween(250)),
                exit = slideOutVertically { it } + fadeOut(tween(200)),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                EditorControlsV2(
                    tool = state.selectedTool,
                    shadowIntensity = state.appearance.shadowIntensity,
                    alpha = state.appearance.alpha,
                    cropRatio = state.cropRatio,
                    onShadowIntensityChange = { 
                        viewModel.onEvent(EditorEvent.UpdateShadow(it)) 
                    },
                    onAlphaChange = { 
                        viewModel.onEvent(EditorEvent.UpdateAlpha(it)) 
                    },
                    onCropRatioChange = { 
                        viewModel.onEvent(EditorEvent.SelectCropRatio(it)) 
                    },
                    onRotateLeft = { 
                        viewModel.onEvent(EditorEvent.UpdateRotation(-90f))
                        viewModel.onEvent(EditorEvent.CommitTransform)
                    },
                    onRotateRight = { 
                        viewModel.onEvent(EditorEvent.UpdateRotation(90f))
                        viewModel.onEvent(EditorEvent.CommitTransform)
                    },
                    onFlipHorizontal = { 
                        viewModel.onEvent(EditorEvent.FlipHorizontal)
                        viewModel.onEvent(EditorEvent.CommitTransform)
                    },
                    onFlipVertical = { 
                        viewModel.onEvent(EditorEvent.FlipVertical)
                        viewModel.onEvent(EditorEvent.CommitTransform)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * EditorCanvas v2 - Reduced recompositions with derivedStateOf and stable keys
 */
@Composable
private fun EditorCanvasV2(
    templateAssetPath: String,
    templateSize: androidx.compose.ui.unit.IntSize,
    product: EditorProduct,
    viewport: EditorViewport,
    appearance: EditorAppearance,
    onGesture: (GestureDelta) -> Unit,
    onGestureEnd: () -> Unit,
    onPickImage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    
    BoxWithConstraints(modifier = modifier) {
        val currentMaxWidth = maxWidth
        val currentMaxHeight = maxHeight
        
        val calculatedScale by remember(templateSize, currentMaxWidth, currentMaxHeight) {
            derivedStateOf {
                with(density) {
                    val templateW = templateSize.width.toDp()
                    val templateH = templateSize.height.toDp()
                    kotlin.math.min(
                        currentMaxWidth / templateW,
                        currentMaxHeight / templateH
                    ).coerceAtMost(1.2f)
                }
            }
        }
        
        val displayWidth = with(density) { templateSize.width.toDp() } * calculatedScale
        val displayHeight = with(density) { templateSize.height.toDp() } * calculatedScale

        Box(
            modifier = Modifier.size(displayWidth, displayHeight),
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
                    displayScale = calculatedScale,
                    templateSize = templateSize,
                    onGesture = onGesture,
                    onGestureEnd = onGestureEnd
                )
            } else {
                PickImagePlaceholder(
                    onClick = onPickImage,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            if (product.processing) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        StudioLottieLoader(
                            modifier = Modifier.fillMaxWidth(0.72f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioLottieLoader(
    modifier: Modifier = Modifier
) {
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

@Composable
private fun ProductLayerV2(
    product: EditorProduct,
    viewport: EditorViewport,
    appearance: EditorAppearance,
    displayScale: Float,
    templateSize: androidx.compose.ui.unit.IntSize,
    onGesture: (GestureDelta) -> Unit,
    onGestureEnd: () -> Unit
) {
    val density = LocalDensity.current
    
    val actualSize by remember(product.baseSize) {
        derivedStateOf {
            androidx.compose.ui.unit.IntSize(
                product.baseSize.width,
                product.baseSize.height
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
    
    val graphicsSpec by remember(viewport, appearance) {
        derivedStateOf {
            object {
                val alpha = appearance.alpha
                val shadowAlpha = appearance.shadowIntensity * 0.3f
                val shadowElevation = appearance.shadowIntensity * 20f
                val scaleX = if (viewport.flippedH) -1f else 1f
                val scaleY = if (viewport.flippedV) -1f else 1f
                val rotation = viewport.rotation
            }
        }
    }

    val boxWidth = with(density) { (actualSize.width * viewport.scale).toInt().toDp() }
    val boxHeight = with(density) { (actualSize.height * viewport.scale).toInt().toDp() }

    Box(
        modifier = Modifier
            .size(boxWidth, boxHeight)
            .offset { displayOffset }
    ) {
        if (appearance.shadowIntensity > 0.05f) {
            SubcomposeAsyncImage(
                model = product.foregroundUri,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .offset(8.dp * displayScale, 8.dp * displayScale)
                    .graphicsLayer {
                        alpha = graphicsSpec.shadowAlpha
                        scaleX = graphicsSpec.scaleX
                        scaleY = graphicsSpec.scaleY
                        rotationZ = graphicsSpec.rotation
                        shadowElevation = graphicsSpec.shadowElevation
                        ambientShadowColor = Color.Black
                        spotShadowColor = Color.Black
                    },
                contentScale = ContentScale.Fit,
                loading = { Box(Modifier.fillMaxSize().background(Color.Transparent)) }
            )
        }

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
                },
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

        BoundingBoxOverlayV6(
            modifier = Modifier
                .align(Alignment.Center)
                .requiredSize(
                    width = boxWidth + 240.dp,
                    height = boxHeight + 240.dp
                ),
            contentWidth = product.baseSize.width.toFloat(),
            contentHeight = product.baseSize.height.toFloat(),
            viewport = viewport,
            displayScale = displayScale,
            templateSize = templateSize,
            onGesture = onGesture,
            onGestureEnd = onGestureEnd
        )
    }
}

@Composable
private fun PickImagePlaceholder(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .shadow(8.dp, CircleShape)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_replace_product),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(36.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.studio_tap_to_pick_product),
                color = Color.White,
                style = MaterialTheme.typography.labelLarge
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
    val tools = remember {
        listOf(
            EditorTool.Replace to Icons.Default.Photo,
            EditorTool.Layout to Icons.Default.DragIndicator,
            EditorTool.Rotate to Icons.Default.Refresh,
            EditorTool.Shadow to Icons.Default.WbSunny,
            EditorTool.Transparency to Icons.Default.Opacity,
            EditorTool.Crop to Icons.Default.CropSquare
        )
    }

    Surface(
        tonalElevation = 3.dp,
        modifier = modifier
    ) {
        val scrollState = androidx.compose.foundation.rememberScrollState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tools.forEach { (tool, icon) ->
                val isSelected = selectedTool == tool
                ToolButton(
                    tool = tool,
                    icon = icon,
                    isSelected = isSelected,
                    onClick = {
                        if (tool == EditorTool.Replace) onReplaceImage()
                        else onToolSelected(tool)
                    }
                )
            }
        }
    }
}

@Composable
private fun ToolButton(
    tool: EditorTool,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val labelRes = when (tool) {
        EditorTool.Replace -> R.string.studio_tool_replace
        EditorTool.Layout -> R.string.studio_tool_layout
        EditorTool.Rotate -> R.string.studio_tool_rotateflip
        EditorTool.Shadow -> R.string.studio_tool_shadow
        EditorTool.Transparency -> R.string.studio_tool_transparency
        EditorTool.Crop -> R.string.studio_tool_crop
    }

    val containerColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        animationSpec = tween(200),
        label = "toolContainer"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EditorControlsV2(
    tool: EditorTool,
    shadowIntensity: Float,
    alpha: Float,
    cropRatio: CropRatio,
    onShadowIntensityChange: (Float) -> Unit,
    onAlphaChange: (Float) -> Unit,
    onCropRatioChange: (CropRatio) -> Unit,
    onRotateLeft: () -> Unit,
    onRotateRight: () -> Unit,
    onFlipHorizontal: () -> Unit,
    onFlipVertical: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        tonalElevation = 2.dp,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            when (tool) {
                EditorTool.Replace -> {
                    Text(
                        text = stringResource(R.string.studio_replace_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                EditorTool.Layout -> {
                    Text(
                        text = stringResource(R.string.studio_layout_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                EditorTool.Rotate -> {
                    EditorLayoutControls(
                        onRotateLeft = onRotateLeft,
                        onRotateRight = onRotateRight,
                        onFlipHorizontal = onFlipHorizontal,
                        onFlipVertical = onFlipVertical
                    )
                }
                EditorTool.Shadow -> {
                    DebouncedSlider(
                        label = stringResource(R.string.studio_shadow_label, (shadowIntensity * 100).toInt()),
                        value = shadowIntensity,
                        onValueChange = onShadowIntensityChange,
                        valueRange = 0f..1f
                    )
                }
                EditorTool.Transparency -> {
                    DebouncedSlider(
                        label = stringResource(R.string.studio_transparency_label, (alpha * 100).toInt()),
                        value = alpha,
                        onValueChange = onAlphaChange,
                        valueRange = 0.1f..1f
                    )
                }
                EditorTool.Crop -> {
                    Text(
                        text = stringResource(R.string.studio_crop_label),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CropRatio.entries.forEach { ratio ->
                            CropRatioButton(
                                ratio = ratio,
                                isSelected = cropRatio == ratio,
                                onClick = { onCropRatioChange(ratio) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DebouncedSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>
) {
    var localValue by remember(value) { mutableFloatStateOf(value) }
    
    LaunchedEffect(localValue) {
        delay(50)
        if (localValue != value) {
            onValueChange(localValue)
        }
    }
    
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = localValue,
            onValueChange = { localValue = it },
            onValueChangeFinished = { onValueChange(localValue) },
            valueRange = valueRange
        )
    }
}

@Composable
private fun CropRatioButton(
    ratio: CropRatio,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent,
        animationSpec = tween(150),
        label = "cropBg"
    )

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(backgroundColor)
            .then(
                if (isSelected) Modifier.border(1.5f.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp)) else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            text = ratio.label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

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
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
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
