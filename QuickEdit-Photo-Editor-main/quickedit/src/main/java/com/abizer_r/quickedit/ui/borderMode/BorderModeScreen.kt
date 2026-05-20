package com.abizer_r.quickedit.ui.borderMode

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abizer_r.quickedit.R
import com.abizer_r.quickedit.ui.common.AnimatedToolbarContainer
import com.abizer_r.quickedit.ui.common.LoadingView
import com.abizer_r.quickedit.ui.common.StablePreviewStage
import com.abizer_r.quickedit.ui.common.bottomToolbarModifier
import com.abizer_r.quickedit.ui.common.topToolbarModifier
import com.abizer_r.quickedit.ui.drawMode.bottomToolbarExtension.CustomSliderItem
import com.abizer_r.quickedit.ui.editorScreen.bottomToolbar.TOOLBAR_HEIGHT_SMALL
import com.abizer_r.quickedit.utils.BorderGradientDirection
import com.abizer_r.quickedit.utils.BorderGradientPreset
import com.abizer_r.quickedit.utils.BorderGradientPresets
import com.abizer_r.quickedit.utils.BorderPreset
import com.abizer_r.quickedit.utils.BorderUtils
import com.abizer_r.quickedit.utils.ColorUtils
import com.abizer_r.quickedit.utils.ImmutableList
import com.abizer_r.quickedit.utils.other.anim.AnimUtils
import com.abizer_r.quickedit.utils.other.bitmap.ImmutableBitmap
import com.abizer_r.quickedit.utils.textMode.colorList.ColorListFullWidth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private const val BORDER_PREVIEW_DEBOUNCE_MS = 120L
private const val BORDER_PREVIEW_BASE_MAX_DIMENSION = 1024

@Composable
fun BorderModeScreen(
    modifier: Modifier = Modifier,
    immutableBitmap: ImmutableBitmap,
    onDoneClicked: (bitmap: Bitmap) -> Unit,
    onBackPressed: () -> Unit,
    initialGradientPresetId: String? = null,
) {
    val lifeCycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    val viewModel: BorderModeViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle(
        lifecycleOwner = lifeCycleOwner
    )

    LaunchedEffect(initialGradientPresetId) {
        viewModel.applyInitialGradientPreset(initialGradientPresetId)
    }

    val bitmap = immutableBitmap.bitmap
    var borderedBitmap by remember(bitmap) { mutableStateOf<Bitmap?>(null) }

    val topToolbarHeight = TOOLBAR_HEIGHT_SMALL
    var toolbarVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        toolbarVisible = true
    }

    suspend fun renderBorder(previewMaxDimension: Int?): Bitmap? {
        return withContext(Dispatchers.Default) {
            BorderUtils.applyBorderToBitmap(
                bitmap = bitmap,
                borderColorArgb = state.borderColorArgb,
                borderGradientPreset = state.borderGradientPreset,
                borderWidthPx = state.borderThickness.roundToInt(),
                borderPreset = state.borderPreset,
                previewMaxDimension = previewMaxDimension
            ).getOrNull()
        }
    }

    LaunchedEffect(bitmap, state.borderColorArgb, state.borderGradientPreset, state.borderThickness, state.borderPreset, toolbarVisible) {
        if (!toolbarVisible) return@LaunchedEffect

        delay(BORDER_PREVIEW_DEBOUNCE_MS)
        if (!toolbarVisible) return@LaunchedEffect

        viewModel.beginBorderProcessing()
        try {
            val previewMaxDimension = resolvePreviewMaxDimension(bitmap, state.borderPreset)
            borderedBitmap = renderBorder(previewMaxDimension) ?: bitmap
        } finally {
            viewModel.endBorderProcessing()
        }
    }

    val onCloseClickedLambda: () -> Unit = {
        coroutineScope.launch {
            toolbarVisible = false
            delay(AnimUtils.TOOLBAR_COLLAPSE_ANIM_DURATION_FAST.toLong())
            onBackPressed()
        }
    }

    val onDoneClickedLambda: () -> Unit = {
        coroutineScope.launch {
            toolbarVisible = false
            delay(AnimUtils.TOOLBAR_COLLAPSE_ANIM_DURATION_FAST.toLong())

            viewModel.beginBorderProcessing()
            try {
                val finalBm = renderBorder(null)
                if (finalBm != null) {
                    onDoneClicked(finalBm)
                } else {
                    onBackPressed()
                }
            } finally {
                viewModel.endBorderProcessing()
            }
        }
    }

    val onResetClickedLambda: () -> Unit = {
        viewModel.resetBorderSettings()
    }

    BackHandler {
        onCloseClickedLambda()
    }

    ConstraintLayout(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val (topToolBar, imageBox, bottomToolBar, overlay) = createRefs()

        AnimatedToolbarContainer(
            toolbarVisible = toolbarVisible,
            modifier = topToolbarModifier(topToolBar)
        ) {
            BorderTopToolbar(
                modifier = Modifier,
                toolbarHeight = topToolbarHeight,
                onCloseClicked = onCloseClickedLambda,
                onResetClicked = onResetClickedLambda,
                onDoneClicked = onDoneClickedLambda
            )
        }

        val displayBitmap = borderedBitmap ?: bitmap
        val aspectRatio = displayBitmap.width.toFloat() / displayBitmap.height.toFloat()

        StablePreviewStage(
            modifier = Modifier
                .constrainAs(imageBox) {
                    top.linkTo(topToolBar.bottom)
                    bottom.linkTo(bottomToolBar.top)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                    width = Dimension.fillToConstraints
                    height = Dimension.fillToConstraints
                }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            aspectRatio = aspectRatio
        ) {
            Image(
                modifier = Modifier.fillMaxSize(),
                bitmap = displayBitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit
            )
        }

        AnimatedToolbarContainer(
            toolbarVisible = toolbarVisible,
            modifier = bottomToolbarModifier(bottomToolBar)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                shadowElevation = 8.dp,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.border_style),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(BorderPreset.values()) { preset ->
                            BorderPresetChip(
                                preset = preset,
                                selected = state.borderPreset == preset,
                                onClick = { viewModel.updateBorderPreset(preset) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = stringResource(id = R.string.solid_color),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    ColorListFullWidth(
                        modifier = Modifier,
                        colorList = ImmutableList(ColorUtils.defaultColorList),
                        selectedColor = Color(state.borderColorArgb),
                        onItemClicked = { _, color ->
                            viewModel.updateBorderColor(color.toArgb())
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = stringResource(id = R.string.gradient),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    BorderGradientSelector(
                        selectedPreset = state.borderGradientPreset,
                        onPresetSelected = { viewModel.updateBorderGradientPreset(it) }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    CustomSliderItem(
                        modifier = Modifier.padding(horizontal = 0.dp),
                        sliderValue = state.borderThickness,
                        sliderLabel = stringResource(id = R.string.width),
                        minValue = 1f,
                        maxValue = 81f,
                        onValueChange = { viewModel.updateBorderThickness(it) }
                    )
                }
            }
        }

        if (state.isApplyingBorder) {
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
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable { },
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

@Composable
private fun BorderModeToggleRow(
    isGradientMode: Boolean,
    onSolidClick: () -> Unit,
    onGradientClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        BorderToggleChip(
            text = stringResource(id = R.string.border_mode_solid),
            selected = !isGradientMode,
            onClick = onSolidClick,
            modifier = Modifier.weight(1f)
        )
        BorderToggleChip(
            text = stringResource(id = R.string.gradient),
            selected = isGradientMode,
            onClick = onGradientClick,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun BorderToggleChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(backgroundColor)
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(999.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}

@Composable
private fun BorderGradientSelector(
    selectedPreset: BorderGradientPreset?,
    onPresetSelected: (BorderGradientPreset) -> Unit
) {
    val presets = BorderGradientPresets.modernPresets

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(presets, key = { it.id }) { preset ->
            val isSelected = selectedPreset?.id == preset.id
            Column(
                modifier = Modifier
                    .width(108.dp)
                    .clickable { onPresetSelected(preset) },
                horizontalAlignment = Alignment.Start
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(buildBorderGradientBrush(preset))
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(18.dp)
                        )
                ) {
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = gradientPresetLabel(preset),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun gradientPresetLabel(preset: BorderGradientPreset): String {
    return when (preset.id) {
        "aurora_mist" -> stringResource(id = R.string.border_gradient_aurora_mist)
        "neon_bloom" -> stringResource(id = R.string.border_gradient_neon_bloom)
        "sunset_pulse" -> stringResource(id = R.string.border_gradient_sunset_pulse)
        "velvet_sky" -> stringResource(id = R.string.border_gradient_velvet_sky)
        "ocean_drive" -> stringResource(id = R.string.border_gradient_ocean_drive)
        "peach_cloud" -> stringResource(id = R.string.border_gradient_peach_cloud)
        "midnight_fade" -> stringResource(id = R.string.border_gradient_midnight_fade)
        "ember_glass" -> stringResource(id = R.string.border_gradient_ember_glass)
        else -> preset.title
    }
}

@Composable
private fun buildBorderGradientBrush(preset: BorderGradientPreset): Brush {
    val colors = remember(preset.id) { preset.colors.map { Color(it) } }
    return remember(preset.id, preset.direction) {
        when (preset.direction) {
            BorderGradientDirection.TOP_LEFT_TO_BOTTOM_RIGHT -> Brush.linearGradient(
                colors = colors,
                start = Offset.Zero,
                end = Offset(600f, 600f)
            )
            BorderGradientDirection.BOTTOM_LEFT_TO_TOP_RIGHT -> Brush.linearGradient(
                colors = colors,
                start = Offset(0f, 600f),
                end = Offset(600f, 0f)
            )
        }
    }
}

@Composable
private fun BorderTopToolbar(
    modifier: Modifier,
    toolbarHeight: androidx.compose.ui.unit.Dp,
    onCloseClicked: () -> Unit,
    onResetClicked: () -> Unit,
    onDoneClicked: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .height(toolbarHeight),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButtonWithSpacing(
            icon = Icons.Default.Close,
            contentDescription = stringResource(id = R.string.back),
            onClick = onCloseClicked
        )

        Spacer(modifier = Modifier.weight(1f))

        IconButtonWithSpacing(
            icon = Icons.Rounded.RestartAlt,
            contentDescription = stringResource(id = R.string.reset_border),
            onClick = onResetClicked
        )

        IconButtonWithSpacing(
            icon = Icons.Default.Check,
            contentDescription = stringResource(id = R.string.done),
            onClick = onDoneClicked,
            endPadding = 12.dp
        )
    }
}

@Composable
private fun IconButtonWithSpacing(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    endPadding: androidx.compose.ui.unit.Dp = 0.dp
) {
    androidx.compose.material3.IconButton(
        onClick = onClick,
        modifier = Modifier.padding(start = 8.dp, end = endPadding)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun BorderPresetChip(
    preset: BorderPreset,
    selected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(backgroundColor)
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(999.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }

        Text(
            text = presetLabel(preset),
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}

@Composable
private fun presetLabel(preset: BorderPreset): String {
    return when (preset) {
        BorderPreset.SOLID -> stringResource(id = R.string.border_preset_solid)
        BorderPreset.SOFT -> stringResource(id = R.string.border_preset_soft)
        BorderPreset.DOUBLE -> stringResource(id = R.string.border_preset_double)
        BorderPreset.OUTLINE -> stringResource(id = R.string.border_preset_outline)
    }
}

private fun resolvePreviewMaxDimension(
    bitmap: Bitmap,
    preset: BorderPreset
): Int {
    val maxSide = maxOf(bitmap.width, bitmap.height)
    val base = when {
        maxSide >= 7000 -> 384
        maxSide >= 5000 -> 512
        maxSide >= 3500 -> 640
        maxSide >= 2500 -> 768
        maxSide >= 1800 -> 896
        else -> BORDER_PREVIEW_BASE_MAX_DIMENSION
    }

    return if (preset == BorderPreset.SOFT || preset == BorderPreset.DOUBLE) {
        (base - 128).coerceAtLeast(384)
    } else {
        base
    }
}
