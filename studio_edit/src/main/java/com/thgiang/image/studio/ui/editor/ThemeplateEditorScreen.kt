package com.thgiang.image.studio.ui.editor

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage

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
    onRequireExportAd: ((() -> Unit) -> Unit)? = null,
    viewModel: ThemeplateEditorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()
    val canRedo by viewModel.canRedo.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Snackbar effects
    LaunchedEffect(state.exportResult) {
        state.exportResult?.let { uri ->
            snackbarHostState.showSnackbar("ÄÃ£ lÆ°u áº£nh")
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

    val onToolSelected = remember(state.selectedTool) {
        { tool: EditorTool ->
            viewModel.onEvent(EditorEvent.SelectTool(tool))
        }
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
                                val exportAction = {
                                    viewModel.onEvent(EditorEvent.Export(themeplate.assetPath))
                                }
                                onRequireExportAd?.invoke(exportAction) ?: exportAction()
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
                onToolSelected = onToolSelected,
                onReplaceImage = { pickImageLauncher.launch("image/*") },
                modifier = Modifier.fillMaxWidth().navigationBarsPadding()
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        val controlPanelHeight = remember(state.selectedTool) {
            when (state.selectedTool) {
                EditorTool.Rotate -> 128.dp
                EditorTool.Shadow -> 236.dp
                EditorTool.Transparency -> 156.dp
                EditorTool.Crop -> 156.dp
                EditorTool.Replace,
                EditorTool.Layout -> 110.dp
            }
        }

        ConstraintLayout(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(if (isSystemInDarkTheme()) Color(0xFF1A1A1A) else Color(0xFFF0EDE8))
        ) {
            val (canvasRef, controlsRef) = createRefs()

            if (state.template.loaded && state.template.originalSize.width > 0) {
                EditorCanvasV2(
                    templateAssetPath = themeplate.assetPath,
                    templateSize = state.template.originalSize,
                    product = state.product,
                    viewport = state.viewport,
                    appearance = state.appearance,
                    cropRatio = state.cropRatio,
                    onGesture = { delta ->
                        viewModel.onEvent(EditorEvent.UpdateGesture(delta))
                    },
                    onGestureEnd = { viewModel.onEvent(EditorEvent.CommitTransform) },
                    onPickImage = { pickImageLauncher.launch("image/*") },
                    onBoundingBoxVisible = { visible ->
                        viewModel.onEvent(EditorEvent.SetBoundingBoxVisible(visible))
                    },
                    showOverlay = state.showOverlay,
                    showBoundingBox = state.showBoundingBox,
                    modifier = Modifier.constrainAs(canvasRef) {
                        top.linkTo(parent.top)
                        start.linkTo(parent.start)
                        end.linkTo(parent.end)
                        bottom.linkTo(if (state.product.isBackgroundRemoved) controlsRef.top else parent.bottom)
                        width = Dimension.fillToConstraints
                        height = Dimension.fillToConstraints
                    }
                )
            }

            AnimatedVisibility(
                visible = state.product.isBackgroundRemoved,
                enter = slideInVertically { it } + fadeIn(tween(250)),
                exit = slideOutVertically { it } + fadeOut(tween(200)),
                modifier = Modifier.constrainAs(controlsRef) {
                    bottom.linkTo(parent.bottom)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                    width = Dimension.fillToConstraints
                }
            ) {
                EditorControlsV2(
                    tool = state.selectedTool,
                    shadowIntensity = state.appearance.shadowIntensity,
                    shadowAngle = state.appearance.shadowAngle,
                    shadowDistance = state.appearance.shadowDistance,
                    shadowColorArgb = state.appearance.shadowColorArgb,
                    alpha = state.appearance.alpha,
                    cropRatio = state.cropRatio,
                    onShadowIntensityChange = {
                        viewModel.onEvent(EditorEvent.UpdateShadow(it))
                    },
                    onShadowAngleChange = {
                        viewModel.onEvent(EditorEvent.UpdateShadowAngle(it))
                    },
                    onShadowDistanceChange = {
                        viewModel.onEvent(EditorEvent.UpdateShadowDistance(it))
                    },
                    onShadowColorChange = {
                        viewModel.onEvent(EditorEvent.UpdateShadowColor(it))
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(controlPanelHeight)
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
    BoxWithConstraints(modifier = modifier) {
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
                .matchParentSize()
                .pointerInput(product, viewport, templateSize, showBoundingBox, cropRatio) {
                    detectTapGestures(onTap = { tap ->
                        if (!product.isBackgroundRemoved || product.foregroundUri == null) {
                            onBoundingBoxVisible(false)
                            return@detectTapGestures
                        }

                        val templateLeftPx = (size.width - with(density) { displayWidth.toPx() }) / 2f
                        val templateTopPx = (size.height - with(density) { displayHeight.toPx() }) / 2f
                        val croppedSize = cropRatio.calculateSize(product.baseSize.width.toFloat(), product.baseSize.height.toFloat())
                        val boxWidthPx = croppedSize.width * viewport.scale
                        val boxHeightPx = croppedSize.height * viewport.scale
                        val objectLeftPx = templateLeftPx + (viewport.offset.x * calculatedScale)
                        val objectTopPx = templateTopPx + (viewport.offset.y * calculatedScale)
                        val withinObject = tap.x in objectLeftPx..(objectLeftPx + boxWidthPx) &&
                            tap.y in objectTopPx..(objectTopPx + boxHeightPx)

                        if (withinObject) {
                            onBoundingBoxVisible(true)
                        }
                    })
                }
        )

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
                    cropRatio = cropRatio,
                    displayScale = calculatedScale,
                    templateSize = templateSize,
                    onGesture = onGesture,
                    onGestureEnd = onGestureEnd,
                    showOverlay = showOverlay,
                    showBoundingBox = showBoundingBox
                )
            } else {
                PickImagePlaceholder(
                    onClick = onPickImage,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            if (product.processing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
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
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(28.dp),
            strokeWidth = 2.5.dp
        )
    }
}

@Composable
private fun ProductLayerV2(
    product: EditorProduct,
    viewport: EditorViewport,
    appearance: EditorAppearance,
    cropRatio: CropRatio,
    displayScale: Float,
    templateSize: androidx.compose.ui.unit.IntSize,
    onGesture: (GestureDelta) -> Unit,
    onGestureEnd: () -> Unit,
    showOverlay: Boolean = false,
    showBoundingBox: Boolean = false
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
    
    val graphicsSpec by remember(viewport, appearance) {
        derivedStateOf {
            object {
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
            }
        }
    }

    val originalWidth = with(density) { (product.baseSize.width * viewport.scale).toInt().toDp() }
    val originalHeight = with(density) { (product.baseSize.height * viewport.scale).toInt().toDp() }

    val cropShape = remember(actualSize, product.baseSize) {
        object : androidx.compose.ui.graphics.Shape {
            override fun createOutline(
                size: androidx.compose.ui.geometry.Size,
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
            .size(originalWidth, originalHeight)
            .offset { displayOffset }
    ) {
        if (appearance.shadowIntensity > 0.05f) {
            SubcomposeAsyncImage(
                model = product.foregroundUri,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(cropShape)
                    .offset {
                        IntOffset(
                            (graphicsSpec.shadowDx * displayScale).roundToInt(),
                            (graphicsSpec.shadowDy * displayScale).roundToInt()
                        )
                    }
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
                colorFilter = ColorFilter.tint(graphicsSpec.shadowColor),
                loading = { Box(Modifier.fillMaxSize().background(Color.Transparent)) }
            )
        }

        SubcomposeAsyncImage(
            model = product.foregroundUri,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .clip(cropShape)
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
                    .clip(cropShape)
                    .graphicsLayer {
                        scaleX = graphicsSpec.scaleX
                        scaleY = graphicsSpec.scaleY
                        rotationZ = graphicsSpec.rotation
                    },
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(Color(0xFFFF2D55).copy(alpha = 0.6f))
            )
        }

        BoundingBoxOverlayV6(
            modifier = Modifier
                .align(Alignment.Center)
                .requiredSize(
                    width = originalWidth + 240.dp,
                    height = originalHeight + 240.dp
                ),
            contentWidth = actualSize.width.toFloat(),
            contentHeight = actualSize.height.toFloat(),
            viewport = viewport,
            displayScale = displayScale,
            templateSize = templateSize,
            onGesture = onGesture,
            onGestureEnd = onGestureEnd,
            showBoundingBox = showBoundingBox
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
        tonalElevation = 4.dp,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        modifier = modifier
    ) {
        val scrollState = androidx.compose.foundation.rememberScrollState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
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
            .clip(RoundedCornerShape(16.dp))
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
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
    shadowAngle: Float,
    shadowDistance: Float,
    shadowColorArgb: Int,
    alpha: Float,
    cropRatio: CropRatio,
    onShadowIntensityChange: (Float) -> Unit,
    onShadowAngleChange: (Float) -> Unit,
    onShadowDistanceChange: (Float) -> Unit,
    onShadowColorChange: (Int) -> Unit,
    onAlphaChange: (Float) -> Unit,
    onCropRatioChange: (CropRatio) -> Unit,
    onRotateLeft: () -> Unit,
    onRotateRight: () -> Unit,
    onFlipHorizontal: () -> Unit,
    onFlipVertical: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        tonalElevation = 4.dp,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PanelHandle()
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
                    var shadowTabIndex by rememberSaveable { mutableIntStateOf(0) }
                    val shadowColorOptions = remember {
                        listOf(
                            ShadowColorOption("Đen", 0xFF000000.toInt()),
                            ShadowColorOption("Xám", 0xFF5D5D5D.toInt()),
                            ShadowColorOption("Nâu", 0xFF5D4631.toInt()),
                            ShadowColorOption("Xanh", 0xFF314E5D.toInt()),
                            ShadowColorOption("Tím", 0xFF5C3A66.toInt())
                        )
                    }
                    val selectedShadowColor = shadowColorOptions.firstOrNull { it.argb == shadowColorArgb }
                        ?: shadowColorOptions.first()
                    val onShadowIntensityChangeImmediate = remember(onShadowIntensityChange) {
                        { value: Float ->
                            onShadowIntensityChange(value)
                        }
                    }
                    val onShadowAngleChangeImmediate = remember(onShadowAngleChange) {
                        { value: Float ->
                            onShadowAngleChange(value)
                        }
                    }
                    val onShadowDistanceChangeImmediate = remember(onShadowDistanceChange) {
                        { value: Float ->
                            onShadowDistanceChange(value)
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            shadowColorOptions.forEach { option ->
                                ShadowColorSwatch(
                                    colorArgb = option.argb,
                                    selected = shadowColorArgb == option.argb,
                                    onClick = { onShadowColorChange(option.argb) }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Màu bóng",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = selectedShadowColor.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ShadowPresetChip(
                                label = "Độ bóng",
                                selected = shadowTabIndex == 0,
                                onClick = { shadowTabIndex = 0 }
                            )
                            ShadowPresetChip(
                                label = "Góc ánh sáng",
                                selected = shadowTabIndex == 1,
                                onClick = { shadowTabIndex = 1 }
                            )
                            ShadowPresetChip(
                                label = "Khoảng cách bóng",
                                selected = shadowTabIndex == 2,
                                onClick = { shadowTabIndex = 2 }
                            )
                        }

                        CompactMetricSlider(
                            label = when (shadowTabIndex) {
                                0 -> stringResource(R.string.studio_shadow_label, (shadowIntensity * 100).toInt())
                                1 -> stringResource(R.string.studio_shadow_angle_label, shadowAngle.toInt())
                                else -> stringResource(R.string.studio_shadow_distance_label, shadowDistance.toInt())
                            },
                            value = when (shadowTabIndex) {
                                0 -> shadowIntensity
                                1 -> shadowAngle
                                else -> shadowDistance
                            },
                            onValueChange = { newValue ->
                                when (shadowTabIndex) {
                                    0 -> onShadowIntensityChangeImmediate(newValue)
                                    1 -> onShadowAngleChangeImmediate(newValue)
                                    else -> onShadowDistanceChangeImmediate(newValue)
                                }
                            },
                            valueRange = when (shadowTabIndex) {
                                0 -> 0f..1f
                                1 -> 0f..360f
                                else -> 0f..50f
                            }
                        )

                        Text(
                            text = when (shadowTabIndex) {
                                0 -> "Điều chỉnh độ đậm của bóng."
                                1 -> "Xác định hướng chiếu sáng."
                                else -> "Tăng hoặc giảm độ lệch của bóng."
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
                EditorTool.Transparency -> {
                    var selectedOpacityPreset by rememberSaveable { mutableIntStateOf(-1) }
                    val opacityPresets = remember {
                        listOf(
                            "Mờ" to 0.35f,
                            "Vừa" to 0.65f,
                            "Đậm" to 0.90f
                        )
                    }

                    fun applyOpacityPreset(value: Float) {
                        onAlphaChange(value)
                    }

                    val onAlphaChangeWithPreset = remember(onAlphaChange) {
                        { value: Float ->
                            selectedOpacityPreset = -1
                            onAlphaChange(value)
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            opacityPresets.forEachIndexed { index, (label, presetValue) ->
                                ShadowColorChip(
                                    label = label,
                                    colorArgb = when (index) {
                                        0 -> 0xFFBDBDBD.toInt()
                                        1 -> 0xFF7E7E7E.toInt()
                                        else -> 0xFF3E3E3E.toInt()
                                    },
                                    selected = selectedOpacityPreset == index,
                                    onClick = {
                                        selectedOpacityPreset = index
                                        applyOpacityPreset(presetValue)
                                    }
                                )
                            }
                        }

                        CompactMetricSlider(
                            label = stringResource(R.string.studio_transparency_label, (alpha * 100).toInt()),
                            value = alpha,
                            onValueChange = onAlphaChangeWithPreset,
                            valueRange = 0.1f..1f
                        )
                    }
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

private data class ShadowQuickPreset(
    val label: String,
    val intensity: Float,
    val angle: Float,
    val distance: Float
)

private data class ShadowColorOption(
    val label: String,
    val argb: Int
)

@Composable
private fun ShadowPresetChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
        animationSpec = tween(180),
        label = "shadowPresetBg"
    )

    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = backgroundColor,
        tonalElevation = if (selected) 2.dp else 0.dp,
        border = if (selected) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
        )
    }
}

@Composable
private fun ShadowColorChip(
    label: String,
    colorArgb: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
        },
        animationSpec = tween(180),
        label = "shadowColorBg"
    )

    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = backgroundColor,
        tonalElevation = if (selected) 2.dp else 0.dp,
        border = if (selected) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.20f))
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
        }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(Color(colorArgb))
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = contentColor
            )
        }
    }
}

@Composable
private fun ShadowColorSwatch(
    colorArgb: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(34.dp)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = Color(colorArgb),
        tonalElevation = if (selected) 2.dp else 0.dp,
        border = if (selected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
        }
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                )
            }
        }
    }
}

@Composable
private fun PanelHandle() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 42.dp, height = 4.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f))
        )
    }
}

@Composable
private fun CompactMetricSlider(
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = localValue,
            onValueChange = { localValue = it },
            onValueChangeFinished = { onValueChange(localValue) },
            valueRange = valueRange,
            modifier = Modifier.padding(top = 2.dp)
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

