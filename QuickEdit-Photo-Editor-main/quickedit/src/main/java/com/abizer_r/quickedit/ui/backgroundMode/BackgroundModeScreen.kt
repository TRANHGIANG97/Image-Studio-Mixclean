package com.abizer_r.quickedit.ui.backgroundMode

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import kotlin.math.min
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Gradient
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import com.thgiang.image.core.model.PresetStyle
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
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

    var toolbarVisible by remember { mutableStateOf(false) }
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
        val (topToolBar, mainImage, bottomToolbar, overlay) = createRefs()

        // Main Image Preview
        val aspectRatio = immutableBitmap.bitmap.width.toFloat() / immutableBitmap.bitmap.height.toFloat()
        var previewPxSize by remember { mutableStateOf(IntSize.Zero) }
        val density = LocalDensity.current

        Box(
            modifier = Modifier
                .constrainAs(mainImage) {
                    top.linkTo(parent.top)
                    bottom.linkTo(bottomToolbar.top)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                    width = Dimension.wrapContent
                    height = Dimension.wrapContent
                }
                .padding(top = topToolbarHeight, bottom = 120.dp)
                .aspectRatio(aspectRatio)
                .onSizeChanged { previewPxSize = it },
            contentAlignment = Alignment.Center
        ) {
            // Background layer (fixed)
            val bgBitmap = state.backgroundBitmap
            if (bgBitmap != null) {
                Image(
                    modifier = Modifier.fillMaxSize(),
                    bitmap = bgBitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit
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
            val fgBitmap = state.foregroundBitmap
            if (fgBitmap != null && bgBitmap != null) {
                val compScale = min(
                    bgBitmap.width.toFloat() / fgBitmap.width,
                    bgBitmap.height.toFloat() / fgBitmap.height
                )
                val drawW = fgBitmap.width * compScale
                val drawH = fgBitmap.height * compScale

                Image(
                    bitmap = fgBitmap.asImageBitmap(),
                    modifier = Modifier
                        .offset {
                            val pxPerUnit = if (previewPxSize.width > 0)
                                previewPxSize.width.toFloat() / bgBitmap.width else 0f
                            IntOffset(
                                x = (((bgBitmap.width - drawW) / 2f + state.foregroundOffsetX) * pxPerUnit).toInt(),
                                y = (((bgBitmap.height - drawH) / 2f + state.foregroundOffsetY) * pxPerUnit).toInt()
                            )
                        }
                        .size(with(density) { drawW.toDp() }, with(density) { drawH.toDp() })
                        .pointerInput(fgBitmap) {
                            detectDragGestures { _, dragAmount ->
                                if (previewPxSize.width > 0) {
                                    val pxPerUnit = previewPxSize.width.toFloat() / bgBitmap.width
                                    viewModel.updateForegroundOffset(
                                        dragAmount.x / pxPerUnit,
                                        dragAmount.y / pxPerUnit
                                    )
                                }
                            }
                        },
                    contentDescription = null,
                    contentScale = ContentScale.Fit
                )
            }

            if (state.isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color.White)
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                // Tab Content
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when (state.currentTab) {
                        BackgroundModeViewModel.BackgroundTab.IMAGE -> {
                            Button(
                                onClick = onPickImageRequest,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.pick_image))
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

                // Tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(TOOLBAR_HEIGHT_EXTRA_LARGE),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BackgroundTabItem(
                        icon = Icons.Default.Image,
                        label = stringResource(R.string.pick_image),
                        isSelected = state.currentTab == BackgroundModeViewModel.BackgroundTab.IMAGE,
                        onClick = { viewModel.setTab(BackgroundModeViewModel.BackgroundTab.IMAGE) }
                    )
                    BackgroundTabItem(
                        icon = Icons.Default.ColorLens,
                        label = stringResource(R.string.color),
                        isSelected = state.currentTab == BackgroundModeViewModel.BackgroundTab.COLOR,
                        onClick = { viewModel.setTab(BackgroundModeViewModel.BackgroundTab.COLOR) }
                    )
                    BackgroundTabItem(
                        icon = Icons.Default.Gradient,
                        label = stringResource(R.string.gradient),
                        isSelected = state.currentTab == BackgroundModeViewModel.BackgroundTab.GRADIENT,
                        onClick = { viewModel.setTab(BackgroundModeViewModel.BackgroundTab.GRADIENT) }
                    )
                    BackgroundTabItem(
                        icon = Icons.Default.ColorLens, // Using ColorLens as placeholder for Presets
                        label = stringResource(R.string.presets),
                        isSelected = state.currentTab == BackgroundModeViewModel.BackgroundTab.PRESET,
                        onClick = { viewModel.setTab(BackgroundModeViewModel.BackgroundTab.PRESET) }
                    )
                }
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
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(colors) { colorInt ->
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color(colorInt))
                    .border(
                        width = 2.dp,
                        color = if (selectedColor == colorInt) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.1f),
                        shape = CircleShape
                    )
                    .clickable { onColorSelected(colorInt) }
            )
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
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(gradients) { grad ->
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = grad.map { Color(it) },
                            start = Offset.Zero,
                            end = Offset.Infinite
                        )
                    )
                    .border(
                        width = 2.dp,
                        color = if (selectedGradient?.contentEquals(grad) == true) MaterialTheme.colorScheme.primary else Color.Transparent,
                        shape = CircleShape
                    )
                    .clickable { onGradientSelected(grad) }
            )
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
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(presets) { style ->
            val isSelected = selectedPreset == style
            Column(
                modifier = Modifier
                    .width(72.dp)
                    .clickable { onPresetSelected(style) },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
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
                            width = 2.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        val isLight = style == PresetStyle.CLEAN || style == PresetStyle.LIQUID_GLASS || style == PresetStyle.LAVENDER_DAWN
                        Icon(Icons.Default.Check, contentDescription = null, tint = if (isLight) Color.Black else Color.White)
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
