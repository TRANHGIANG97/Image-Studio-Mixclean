package com.abizer_r.quickedit.ui.backgroundMode

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import kotlin.math.min
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.Gradient
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Gradient
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import com.thgiang.image.core.model.PresetStyle
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abizer_r.quickedit.R
// ToolBarBackgroundColor removed from imports
import com.abizer_r.quickedit.ui.common.AnimatedToolbarContainer
import com.abizer_r.quickedit.ui.common.LoadingView
import com.abizer_r.quickedit.ui.common.bottomToolbarModifier
import com.abizer_r.quickedit.ui.common.topToolbarModifier
import com.abizer_r.quickedit.ui.editorScreen.bottomToolbar.TOOLBAR_HEIGHT_EXTRA_LARGE
import com.abizer_r.quickedit.ui.editorScreen.bottomToolbar.TOOLBAR_HEIGHT_SMALL
import com.abizer_r.quickedit.utils.other.bitmap.ImmutableBitmap

@Composable
fun BackgroundModeScreen(
    modifier: Modifier = Modifier,
    immutableBitmap: ImmutableBitmap,
    onDoneClicked: (bitmap: Bitmap) -> Unit,
    onBackPressed: () -> Unit,
    onPickImageRequest: () -> Unit,
    pickedImage: Bitmap? = null
) {
    val viewModel: BackgroundModeViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(immutableBitmap) {
        viewModel.setInitialBitmap(immutableBitmap.bitmap)
    }

    LaunchedEffect(pickedImage) {
        if (pickedImage != null) {
            viewModel.applyImageBackground(pickedImage)
        }
    }

    val topToolbarHeight = TOOLBAR_HEIGHT_SMALL
    val bottomToolbarHeight = TOOLBAR_HEIGHT_EXTRA_LARGE + 60.dp // Extra space for tabs

    var toolbarVisible by remember { mutableStateOf(true) }
    var showRatioSelector by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        toolbarVisible = true
    }

    BackHandler {
        toolbarVisible = false
        onBackPressed()
    }

    ConstraintLayout(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val (topToolBar, mainImage, bottomToolbar, overlay, ratioOverlay) = createRefs()

        // Main Image Preview
        var previewPxSize by remember { mutableStateOf(IntSize.Zero) }
        val density = LocalDensity.current
        val bgBitmap = state.backgroundBitmap
        val fgBitmap = state.foregroundBitmap
        val canvasAspectRatio = remember(bgBitmap, state.selectedRatio, immutableBitmap.bitmap) {
            bgBitmap?.let { it.width.toFloat() / it.height.toFloat() }
                ?: if (state.selectedRatio.widthRatio > 0f && state.selectedRatio.heightRatio > 0f) {
                    state.selectedRatio.widthRatio / state.selectedRatio.heightRatio
                } else {
                    immutableBitmap.bitmap.width.toFloat() / immutableBitmap.bitmap.height.toFloat()
                }
        }
        val pxPerUnit = remember(previewPxSize, bgBitmap) {
            val bg = bgBitmap
            if (bg != null && previewPxSize.width > 0 && previewPxSize.height > 0) {
                min(
                    previewPxSize.width.toFloat() / bg.width.toFloat(),
                    previewPxSize.height.toFloat() / bg.height.toFloat()
                )
            } else {
                0f
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .constrainAs(mainImage) {
                    top.linkTo(topToolBar.bottom)
                    if (showRatioSelector) {
                        bottom.linkTo(ratioOverlay.top)
                    } else {
                        bottom.linkTo(bottomToolbar.top)
                    }
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                    width = Dimension.fillToConstraints
                    height = Dimension.fillToConstraints
                }
                .clipToBounds(),
            contentAlignment = Alignment.Center
        ) {
            val boundsRatio = if (maxHeight > 0.dp) maxWidth / maxHeight else canvasAspectRatio
            val canvasModifier = if (canvasAspectRatio > boundsRatio) {
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(canvasAspectRatio)
            } else {
                Modifier
                    .fillMaxHeight()
                    .aspectRatio(canvasAspectRatio, matchHeightConstraintsFirst = true)
            }

            Box(
                modifier = canvasModifier
                    .align(Alignment.Center)
                    .onSizeChanged { previewPxSize = it }
                    .pointerInput(pxPerUnit, bgBitmap, fgBitmap, state.isProcessing) {
                        detectTransformGestures { _, pan, zoomChange, rotationChange ->
                            if (
                                pxPerUnit <= 0f ||
                                bgBitmap == null ||
                                fgBitmap == null ||
                                state.isProcessing
                            ) return@detectTransformGestures

                            if (pan != Offset.Zero) {
                                viewModel.updateForegroundOffset(
                                    dx = pan.x / pxPerUnit,
                                    dy = pan.y / pxPerUnit
                                )
                            }
                            if (zoomChange != 1f || rotationChange != 0f) {
                                viewModel.updateForegroundTransform(
                                    zoomChange = zoomChange,
                                    rotationChange = rotationChange
                                )
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                // Background layer (fixed)
                if (bgBitmap != null) {
                    Image(
                        modifier = Modifier.fillMaxSize(),
                        bitmap = bgBitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds
                    )
                } else {
                    // Fallback: show original image before any background is set
                    Image(
                        modifier = Modifier.fillMaxSize(),
                        bitmap = immutableBitmap.bitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit
                    )
                }

                // Foreground overlay (draggable on top of background)
                if (fgBitmap != null && bgBitmap != null) {
                    val compScale = min(
                        bgBitmap.width.toFloat() / fgBitmap.width,
                        bgBitmap.height.toFloat() / fgBitmap.height
                    )
                    val drawW = fgBitmap.width * compScale
                    val drawH = fgBitmap.height * compScale
                    val previewDrawW = drawW * state.foregroundScale * pxPerUnit
                    val previewDrawH = drawH * state.foregroundScale * pxPerUnit

                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    x = (state.foregroundOffsetX * pxPerUnit).toInt(),
                                    y = (state.foregroundOffsetY * pxPerUnit).toInt()
                                )
                            }
                            .graphicsLayer(
                                rotationZ = state.foregroundRotation,
                                scaleX = if (state.foregroundFlippedH) -1f else 1f,
                                scaleY = if (state.foregroundFlippedV) -1f else 1f
                            )
                            .size(
                                width = with(density) { previewDrawW.toDp() },
                                height = with(density) { previewDrawH.toDp() }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            modifier = Modifier.fillMaxSize(),
                            bitmap = fgBitmap.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit
                        )

                        // Pink Overlay (Subject Mask)
                        AnimatedVisibility(
                            visible = state.showOverlay,
                            enter = fadeIn(),
                            exit = fadeOut(),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Image(
                                modifier = Modifier.fillMaxSize(),
                                bitmap = fgBitmap.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                colorFilter = ColorFilter.tint(Color(0xFFFF2D55).copy(alpha = 0.6f))
                            )
                        }
                    }
                }

                if (state.isProcessing) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        LoadingView(
                            modifier = Modifier.fillMaxSize(),
                            progressBarSize = 96.dp,
                            progressBarColor = Color.White
                        )
                    }
                }
            }
        }

        // Top Toolbar
        AnimatedToolbarContainer(
            toolbarVisible = toolbarVisible,
            modifier = topToolbarModifier(topToolBar)
        ) {
                Row(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surface)
                        .statusBarsPadding()
                        .height(topToolbarHeight),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        toolbarVisible = false
                        onBackPressed()
                    }) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                    }
                    Text(
                        text = stringResource(R.string.background),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                    IconButton(onClick = { showRatioSelector = !showRatioSelector }) {
                        Icon(
                            Icons.Default.AspectRatio,
                            contentDescription = "Chọn Tỉ lệ",
                            tint = if (showRatioSelector) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = viewModel::resetForegroundPosition) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = viewModel::toggleForegroundFlipHorizontal) {
                        Icon(
                            imageVector = ImageVector.vectorResource(id = R.drawable.ic_flip_horizontal),
                            contentDescription = stringResource(R.string.flip_horizontal),
                            tint = if (state.foregroundFlippedH) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = viewModel::toggleForegroundFlipVertical) {
                        Icon(
                            imageVector = ImageVector.vectorResource(id = R.drawable.ic_flip_vertical),
                            contentDescription = stringResource(R.string.flip_vertical),
                            tint = if (state.foregroundFlippedV) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = {
                        viewModel.getFinalBitmap()?.let { onDoneClicked(it) }
                    }) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
        }

        // Bottom Controls
        AnimatedToolbarContainer(
            toolbarVisible = toolbarVisible,
            modifier = bottomToolbarModifier(bottomToolbar)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                tonalElevation = 8.dp,
                shadowElevation = 16.dp,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    // Tab Content
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            when (state.currentTab) {
                                BackgroundModeViewModel.BackgroundTab.IMAGE -> {
                                    Button(
                                        onClick = onPickImageRequest,
                                        shape = RoundedCornerShape(16.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        modifier = Modifier.height(56.dp).padding(horizontal = 16.dp)
                                    ) {
                                        Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(stringResource(R.string.pick_image), fontWeight = FontWeight.Bold)
                                    }
                                }
                                BackgroundModeViewModel.BackgroundTab.COLOR -> {
                                    ColorSelector(
                                        selectedColor = state.selectedColor,
                                        onColorSelected = { viewModel.applyColorBackground(it) }
                                    )
                                }
                                BackgroundModeViewModel.BackgroundTab.GRADIENT -> {
                                    GradientSelector(
                                        selectedGradient = state.selectedGradient,
                                        onGradientSelected = { viewModel.applyGradientBackground(it) }
                                    )
                                }
                                BackgroundModeViewModel.BackgroundTab.PRESET -> {
                                    PresetSelector(
                                        selectedPreset = state.selectedPresetStyle,
                                        onPresetSelected = { viewModel.applyPresetBackground(it) }
                                    )
                                }
                            }
                        }
                    }

                    // Tabs
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BackgroundTabItem(
                            icon = Icons.Outlined.Image,
                            selectedIcon = Icons.Default.Image,
                            label = stringResource(R.string.pick_image),
                            isSelected = state.currentTab == BackgroundModeViewModel.BackgroundTab.IMAGE,
                            onClick = { 
                                showRatioSelector = false
                                viewModel.setTab(BackgroundModeViewModel.BackgroundTab.IMAGE) 
                            }
                        )
                        BackgroundTabItem(
                            icon = Icons.Outlined.ColorLens,
                            selectedIcon = Icons.Default.ColorLens,
                            label = stringResource(R.string.color),
                            isSelected = state.currentTab == BackgroundModeViewModel.BackgroundTab.COLOR,
                            onClick = { 
                                showRatioSelector = false
                                viewModel.setTab(BackgroundModeViewModel.BackgroundTab.COLOR) 
                            }
                        )
                        BackgroundTabItem(
                            icon = Icons.Outlined.Gradient,
                            selectedIcon = Icons.Default.Gradient,
                            label = stringResource(R.string.gradient),
                            isSelected = state.currentTab == BackgroundModeViewModel.BackgroundTab.GRADIENT,
                            onClick = { 
                                showRatioSelector = false
                                viewModel.setTab(BackgroundModeViewModel.BackgroundTab.GRADIENT) 
                            }
                        )
                        BackgroundTabItem(
                            icon = Icons.Outlined.ColorLens,
                            selectedIcon = Icons.Default.ColorLens,
                            label = stringResource(R.string.presets),
                            isSelected = state.currentTab == BackgroundModeViewModel.BackgroundTab.PRESET,
                            onClick = { 
                                showRatioSelector = false
                                viewModel.setTab(BackgroundModeViewModel.BackgroundTab.PRESET) 
                            }
                        )
                    }
                }
            }
        }

        // Ratio Selector Overlay
        AnimatedVisibility(
            visible = showRatioSelector,
            enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
            modifier = Modifier
                .constrainAs(ratioOverlay) {
                    bottom.linkTo(bottomToolbar.top)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                }
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                tonalElevation = 8.dp,
                shadowElevation = 16.dp,
                shape = RoundedCornerShape(24.dp)
            ) {
                RatioSelector(
                    selectedRatio = state.selectedRatio,
                    onRatioSelected = { viewModel.setCanvasRatio(it) }
                )
            }
        }

        // Warning Overlay for no Alpha
        if (!state.hasAlpha) {
            Box(
                modifier = Modifier
                    .constrainAs(overlay) {
                        width = Dimension.fillToConstraints
                        height = Dimension.fillToConstraints
                        top.linkTo(parent.top)
                        bottom.linkTo(parent.bottom)
                        start.linkTo(parent.start)
                        end.linkTo(parent.end)
                    }
                    .background(Color.Black.copy(alpha = 0.8f))
                    .clickable { /* Block interaction */ },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = null,
                        tint = Color.Red,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.remove_bg_warning),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = onBackPressed) {
                        Text(stringResource(R.string.back))
                    }
                }
            }
        }
    }
}

@Composable
fun BackgroundTabItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(80.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun ColorSelector(
    selectedColor: Int?,
    onColorSelected: (Int) -> Unit
) {
    val colors = listOf(
        AndroidColor.WHITE, AndroidColor.BLACK,
        AndroidColor.parseColor("#F5F5F7"), // Apple Gray
        AndroidColor.parseColor("#E5E5EA"), // Light Gray
        AndroidColor.parseColor("#F2F2F7"), // Off White
        AndroidColor.parseColor("#FFD60A"), // Apple Yellow
        AndroidColor.parseColor("#FF9500"), // Apple Orange
        AndroidColor.parseColor("#FF3B30"), // Apple Red
        AndroidColor.parseColor("#AF52DE"), // Apple Purple
        AndroidColor.parseColor("#5856D6"), // Apple Indigo
        AndroidColor.parseColor("#007AFF"), // Apple Blue
        AndroidColor.parseColor("#34C759"), // Apple Green
        AndroidColor.parseColor("#E0F2F1"), // Mint Pastel
        AndroidColor.parseColor("#F3E5F5"), // Lavender Pastel
        AndroidColor.parseColor("#FFF3E0"), // Peach Pastel
        AndroidColor.parseColor("#E1F5FE"), // Sky Blue Pastel
        AndroidColor.parseColor("#2C3E50"), // Midnight Blue
        AndroidColor.parseColor("#1B1B1B")  // Rich Black
    )

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(colors) { colorInt ->
            val isSelected = selectedColor == colorInt
            val size by animateDpAsState(if (isSelected) 64.dp else 52.dp)
            
            Box(
                modifier = Modifier
                    .size(64.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(size)
                        .shadow(if (isSelected) 8.dp else 2.dp, CircleShape)
                        .clip(CircleShape)
                        .background(Color(colorInt))
                        .border(
                            width = 3.dp,
                            color = if (isSelected) Color.White else Color.Transparent,
                            shape = CircleShape
                        )
                        .clickable { onColorSelected(colorInt) }
                )
            }
        }
    }
}

@Composable
fun GradientSelector(
    selectedGradient: IntArray?,
    onGradientSelected: (IntArray) -> Unit
) {
    val gradients = listOf(
        // Peach Sky — blue → purple → pink
        intArrayOf(0xFF667EEA.toInt(), 0xFF764BA2.toInt(), 0xFFF093FB.toInt()),
        // Rose Garden — deep purple → pink → orange
        intArrayOf(0xFF5B2C8F.toInt(), 0xFFC0486C.toInt(), 0xFFF4A261.toInt()),
        // Golden Sunset — coral → yellow → teal
        intArrayOf(0xFFFF6B6B.toInt(), 0xFFFFE66D.toInt(), 0xFF4ECDC4.toInt()),
        // Lavender Dawn — lavender → pink → peach
        intArrayOf(0xFFA18CD1.toInt(), 0xFFFBC2EB.toInt(), 0xFFF6D365.toInt()),
        // Aqua Breeze — bright blue → cyan → mint
        intArrayOf(0xFF4FACFE.toInt(), 0xFF00F2FE.toInt(), 0xFF43E97B.toInt()),
        // Sunset Blaze — orange → pink → magenta
        intArrayOf(0xFFFC4A1A.toInt(), 0xFFF7B733.toInt(), 0xFFF093FB.toInt()),
        // Twilight — indigo → purple → warm yellow
        intArrayOf(0xFF4158D0.toInt(), 0xFFC850C0.toInt(), 0xFFFFCC70.toInt()),
        // Strawberry — red → pink → sky blue
        intArrayOf(0xFFFF0844.toInt(), 0xFFFFB199.toInt(), 0xFF00B4DB.toInt()),
        // Emerald — forest green → lime → yellow
        intArrayOf(0xFF11998E.toInt(), 0xFF38EF7D.toInt(), 0xFFF6D365.toInt()),
        // Tropical — bright cyan → blue → deep navy
        intArrayOf(0xFF00D2FF.toInt(), 0xFF3A7BD5.toInt(), 0xFF003973.toInt()),
        // Sakura — pink → lavender → sky blue
        intArrayOf(0xFFFF9A9E.toInt(), 0xFFFECFEF.toInt(), 0xFFA8EDEA.toInt()),
        // Amber Glow — golden amber → orange → coral
        intArrayOf(0xFFFFD93D.toInt(), 0xFFFF6B35.toInt(), 0xFFFF3B3B.toInt()),
        // Oceanic — deep blue → teal → cyan
        intArrayOf(0xFF0F2027.toInt(), 0xFF203A43.toInt(), 0xFF2C5364.toInt()),
        // Minty — soft teal → mint → pale green
        intArrayOf(0xFF11998E.toInt(), 0xFFA8E6CF.toInt(), 0xFFDCEDC1.toInt())
    )

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(gradients) { grad ->
            val isSelected = selectedGradient?.contentEquals(grad) == true
            val size by animateDpAsState(if (isSelected) 64.dp else 52.dp)

            Box(
                modifier = Modifier
                    .size(64.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(size)
                        .shadow(if (isSelected) 8.dp else 2.dp, CircleShape)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = grad.map { Color(it) },
                                start = Offset.Zero,
                                end = Offset.Infinite
                            )
                        )
                        .border(
                            width = 3.dp,
                            color = if (isSelected) Color.White else Color.Transparent,
                            shape = CircleShape
                        )
                        .clickable { onGradientSelected(grad) }
                )
            }
        }
    }
}

@Composable
fun PresetSelector(
    selectedPreset: PresetStyle?,
    onPresetSelected: (PresetStyle) -> Unit
) {
    val presets = PresetStyle.values()

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(presets) { style ->
            val isSelected = selectedPreset == style
            val size by animateDpAsState(if (isSelected) 64.dp else 52.dp)

            Column(
                modifier = Modifier
                    .width(72.dp)
                    .clickable { onPresetSelected(style) },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(size)
                            .shadow(if (isSelected) 8.dp else 2.dp, RoundedCornerShape(12.dp))
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                when (style) {
                                    PresetStyle.NOIR -> Color(0xFF0D1019)
                                    PresetStyle.CLEAN -> Color.White
                                    PresetStyle.AURORA -> Color(0xFF1A1C3A)
                                    PresetStyle.DUOTONE -> Color(0xFF5F4B8B)
                                    PresetStyle.NEON_GRID -> Color(0xFF040912)
                                    PresetStyle.LIQUID_GLASS -> Color(0xFFF6F4FF)
                                    PresetStyle.SUNSET_FILM -> Color(0xFF8A3E6B)
                                    PresetStyle.CARBON_X -> Color(0xFF0A0A0D)
                                    PresetStyle.ROSE_GARDEN -> Color(0xFF8B3A8F)
                                    PresetStyle.PEACH_SKY -> Color(0xFF764ba2)
                                    PresetStyle.GOLDEN_SUNSET -> Color(0xFFE86868)
                                    PresetStyle.LAVENDER_DAWN -> Color(0xFFd4a5d4)
                                    PresetStyle.AQUA_BREEZE -> Color(0xFF4facfe)
                                }
                            )
                            .border(
                                width = 3.dp,
                                color = if (isSelected) Color.White else Color.Transparent,
                                shape = RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            val isLight = style == PresetStyle.CLEAN || style == PresetStyle.LIQUID_GLASS || style == PresetStyle.LAVENDER_DAWN
                            Icon(Icons.Default.Check, contentDescription = null, tint = if (isLight) Color.Black else Color.White)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = style.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun BackgroundTabItem(
    icon: ImageVector,
    selectedIcon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val color by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        animationSpec = tween(300),
        label = "tabColor"
    )

    Column(
        modifier = Modifier
            .width(88.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (isSelected) selectedIcon else icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(26.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center
        )
        
        // Sliding indicator
        AnimatedVisibility(
            visible = isSelected,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .size(width = 24.dp, height = 3.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

@Composable
fun RatioSelector(
    selectedRatio: BackgroundModeViewModel.CanvasRatio,
    onRatioSelected: (BackgroundModeViewModel.CanvasRatio) -> Unit
) {
    val ratios = BackgroundModeViewModel.CanvasRatio.values()

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(ratios) { ratio ->
            val isSelected = selectedRatio == ratio
            val color by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            
            Column(
                modifier = Modifier
                    .width(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onRatioSelected(ratio) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Ratio Icon representation
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .border(
                            width = 2.dp,
                            color = color,
                            shape = RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (ratio == BackgroundModeViewModel.CanvasRatio.RATIO_ORIGINAL) {
                        Icon(Icons.Default.Image, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                    } else {
                        // Create a visual box that roughly matches the ratio
                        val w = if (ratio.widthRatio > ratio.heightRatio) 24.dp else (24.dp * ratio.widthRatio / ratio.heightRatio)
                        val h = if (ratio.heightRatio > ratio.widthRatio) 24.dp else (24.dp * ratio.heightRatio / ratio.widthRatio)
                        Box(
                            modifier = Modifier
                                .size(w, h)
                                .background(color.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                .border(1.dp, color, RoundedCornerShape(4.dp))
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = ratio.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal
                )
            }
        }
    }
}
