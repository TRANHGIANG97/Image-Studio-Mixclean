package com.abizer_r.quickedit.ui.backgroundMode

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
        val currentBitmap = state.processedBitmap ?: immutableBitmap.bitmap
        val aspectRatio = currentBitmap.width.toFloat() / currentBitmap.height.toFloat()

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
                .aspectRatio(aspectRatio),
            contentAlignment = Alignment.Center
        ) {
            Image(
                modifier = Modifier.fillMaxSize(),
                bitmap = currentBitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit
            )

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
                    state.processedBitmap?.let { onDoneClicked(it) }
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
        // Sunrise
        intArrayOf(AndroidColor.parseColor("#FF512F"), AndroidColor.parseColor("#DD2476")),
        // Ocean
        intArrayOf(AndroidColor.parseColor("#2193b0"), AndroidColor.parseColor("#6dd5ed")),
        // Aurora
        intArrayOf(AndroidColor.parseColor("#00b09b"), AndroidColor.parseColor("#96c93d")),
        // Dusk
        intArrayOf(AndroidColor.parseColor("#a8c0ff"), AndroidColor.parseColor("#3f2b96")),
        // Rose
        intArrayOf(AndroidColor.parseColor("#e91e63"), AndroidColor.parseColor("#f06292")),
        // Cyber
        intArrayOf(AndroidColor.parseColor("#8E2DE2"), AndroidColor.parseColor("#4A00E0")),
        // Deep Sea
        intArrayOf(AndroidColor.parseColor("#2C3E50"), AndroidColor.parseColor("#4CA1AF")),
        // Lush
        intArrayOf(AndroidColor.parseColor("#56ab2f"), AndroidColor.parseColor("#a8e063")),
        // Kye Meh
        intArrayOf(AndroidColor.parseColor("#833ab4"), AndroidColor.parseColor("#fd1d1d"), AndroidColor.parseColor("#fcb045")),
        // Royal Blue
        intArrayOf(AndroidColor.parseColor("#24c6dc"), AndroidColor.parseColor("#514a9d")),
        // Soft Grass
        intArrayOf(AndroidColor.parseColor("#c1dfc4"), AndroidColor.parseColor("#deecdd"))
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
                        Brush.verticalGradient(grad.map { Color(it) })
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
                                PresetStyle.NOIR -> Color.Black
                                PresetStyle.CLEAN -> Color.White
                                PresetStyle.AURORA -> Color(0xFF1A1C3A)
                                PresetStyle.DUOTONE -> Color(0xFF5F4B8B)
                                PresetStyle.NEON_GRID -> Color(0xFF040912)
                                PresetStyle.LIQUID_GLASS -> Color(0xFFF6F4FF)
                                PresetStyle.SUNSET_FILM -> Color(0xFF8A3E6B)
                                PresetStyle.CARBON_X -> Color(0xFF0A0A0D)
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
                        Icon(Icons.Default.Check, contentDescription = null, tint = if (style == PresetStyle.CLEAN) Color.Black else Color.White)
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
