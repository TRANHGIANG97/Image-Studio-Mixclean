package com.abizer_r.quickedit.ui.borderMode

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Gradient
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Style
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.abizer_r.quickedit.ui.editorScreen.bottomToolbar.TOOLBAR_HEIGHT_SMALL
import com.abizer_r.quickedit.utils.BorderGradientDirection
import com.abizer_r.quickedit.utils.BorderGradientPreset
import com.abizer_r.quickedit.utils.BorderGradientPresets
import com.abizer_r.quickedit.utils.BorderPreset
import com.abizer_r.quickedit.utils.BorderUtils
import com.abizer_r.quickedit.utils.ColorUtils
import com.abizer_r.quickedit.utils.other.anim.AnimUtils
import com.abizer_r.quickedit.utils.other.bitmap.ImmutableBitmap
import com.abizer_r.quickedit.utils.textMode.colorList.SelectableColor
import com.thgiang.image.core.design.components.PrecisionSlider
import com.thgiang.image.core.design.components.PrecisionSliderDefaults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private const val BORDER_PREVIEW_DEBOUNCE_MS = 120L
private const val BORDER_PREVIEW_BASE_MAX_DIMENSION = 1024

private enum class BorderPanelTab {
    COLOR,
    GRADIENT,
    STYLE,
    BLUR
}

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
                borderBlurRadiusPx = state.borderBlurRadius.roundToInt(),
                borderPreset = state.borderPreset,
                previewMaxDimension = previewMaxDimension
            ).getOrNull()
        }
    }

    LaunchedEffect(bitmap, state.borderColorArgb, state.borderGradientPreset, state.borderThickness, state.borderBlurRadius, state.borderPreset, toolbarVisible) {
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
                .padding(horizontal = 10.dp, vertical = 10.dp),
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
                tonalElevation = 8.dp,
                shadowElevation = 10.dp,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            ) {
                BorderControlsPanel(
                    isGradientMode = state.borderGradientPreset != null,
                    selectedPreset = state.borderPreset,
                    selectedColor = Color(state.borderColorArgb),
                    selectedGradientPreset = state.borderGradientPreset,
                    width = state.borderThickness,
                    blurRadius = state.borderBlurRadius,
                    onSolidModeClick = { viewModel.enableBorderSolidMode() },
                    onGradientModeClick = { viewModel.enableBorderGradientMode() },
                    onPresetSelected = { viewModel.updateBorderPreset(it) },
                    onColorSelected = { viewModel.updateBorderColor(it.toArgb()) },
                    onGradientPresetSelected = { viewModel.updateBorderGradientPreset(it) },
                    onWidthChanged = { viewModel.updateBorderThickness(it) },
                    onBlurRadiusChanged = { viewModel.updateBorderBlurRadius(it) }
                )
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
private fun BorderControlsPanel(
    isGradientMode: Boolean,
    selectedPreset: BorderPreset,
    selectedColor: Color,
    selectedGradientPreset: BorderGradientPreset?,
    width: Float,
    blurRadius: Float,
    onSolidModeClick: () -> Unit,
    onGradientModeClick: () -> Unit,
    onPresetSelected: (BorderPreset) -> Unit,
    onColorSelected: (Color) -> Unit,
    onGradientPresetSelected: (BorderGradientPreset) -> Unit,
    onWidthChanged: (Float) -> Unit,
    onBlurRadiusChanged: (Float) -> Unit
) {
    var selectedTab by remember {
        mutableStateOf(if (isGradientMode) BorderPanelTab.GRADIENT else BorderPanelTab.COLOR)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        BorderPanelTabs(
            selectedTab = selectedTab,
            onTabSelected = { tab ->
                selectedTab = tab
                when (tab) {
                    BorderPanelTab.COLOR -> onSolidModeClick()
                    BorderPanelTab.GRADIENT -> onGradientModeClick()
                    BorderPanelTab.BLUR -> onPresetSelected(BorderPreset.SOFT)
                    BorderPanelTab.STYLE -> Unit
                }
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        when (selectedTab) {
            BorderPanelTab.COLOR -> {
                BorderSectionTitle(text = stringResource(id = R.string.color))
                Spacer(modifier = Modifier.height(4.dp))
                BorderColorSelector(
                    selectedColor = selectedColor,
                    onColorSelected = onColorSelected
                )
            }
            BorderPanelTab.GRADIENT -> {
                BorderSectionTitle(text = stringResource(id = R.string.gradient))
                Spacer(modifier = Modifier.height(4.dp))
                BorderGradientSelector(
                    selectedPreset = selectedGradientPreset,
                    onPresetSelected = onGradientPresetSelected
                )
            }
            BorderPanelTab.STYLE -> {
                BorderSectionTitle(text = stringResource(id = R.string.border_style))
                Spacer(modifier = Modifier.height(4.dp))
                BorderStyleSelector(
                    selectedPreset = selectedPreset,
                    onPresetSelected = onPresetSelected
                )
            }
            BorderPanelTab.BLUR -> {
                BorderSectionTitle(text = stringResource(id = R.string.border_blur))
                Spacer(modifier = Modifier.height(4.dp))
                BorderBlurSelector(
                    selectedPreset = selectedPreset,
                    onPresetSelected = onPresetSelected
                )
                Spacer(modifier = Modifier.height(6.dp))
                BorderValueSlider(
                    modifier = Modifier.padding(bottom = 2.dp),
                    value = blurRadius,
                    label = stringResource(id = R.string.border_blur_amount),
                    minValue = 1f,
                    maxValue = 24f,
                    valueSuffix = "px",
                    onValueChange = onBlurRadiusChanged
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        BorderValueSlider(
            modifier = Modifier.padding(bottom = 4.dp),
            value = width,
            label = stringResource(id = R.string.width),
            minValue = 1f,
            maxValue = 22f,
            valueSuffix = "px",
            onValueChange = onWidthChanged
        )
    }
}

@Composable
private fun BorderPanelTabs(
    selectedTab: BorderPanelTab,
    onTabSelected: (BorderPanelTab) -> Unit
) {
    val tabs = listOf(
        BorderPanelTab.COLOR,
        BorderPanelTab.GRADIENT,
        BorderPanelTab.STYLE,
        BorderPanelTab.BLUR
    )

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(end = 4.dp)
    ) {
        items(tabs) { tab ->
            BorderPanelTabChip(
                icon = borderTabIcon(tab),
                text = borderTabLabel(tab),
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) }
            )
        }
    }
}

@Composable
private fun borderTabLabel(tab: BorderPanelTab): String {
    return when (tab) {
        BorderPanelTab.COLOR -> stringResource(id = R.string.color)
        BorderPanelTab.GRADIENT -> stringResource(id = R.string.gradient)
        BorderPanelTab.STYLE -> stringResource(id = R.string.border_style)
        BorderPanelTab.BLUR -> stringResource(id = R.string.border_blur)
    }
}

private fun borderTabIcon(tab: BorderPanelTab): ImageVector {
    return when (tab) {
        BorderPanelTab.COLOR -> Icons.Default.Palette
        BorderPanelTab.GRADIENT -> Icons.Default.Gradient
        BorderPanelTab.STYLE -> Icons.Default.Style
        BorderPanelTab.BLUR -> Icons.Default.BlurOn
    }
}

@Composable
private fun BorderPanelTabChip(
    icon: ImageVector,
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        Color.Transparent
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    } else {
        Color.Transparent
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun BorderStyleSelector(
    selectedPreset: BorderPreset,
    onPresetSelected: (BorderPreset) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(end = 6.dp)
    ) {
        items(BorderPreset.values()) { preset ->
            BorderPresetTile(
                preset = preset,
                selected = selectedPreset == preset,
                onClick = { onPresetSelected(preset) }
            )
        }
    }
}

@Composable
private fun BorderBlurSelector(
    selectedPreset: BorderPreset,
    onPresetSelected: (BorderPreset) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BorderBlurCard(
            title = stringResource(id = R.string.border_blur_none),
            selected = selectedPreset != BorderPreset.SOFT,
            preset = BorderPreset.SOLID,
            onClick = { onPresetSelected(BorderPreset.SOLID) }
        )
        BorderBlurCard(
            title = stringResource(id = R.string.border_blur_soft),
            selected = selectedPreset == BorderPreset.SOFT,
            preset = BorderPreset.SOFT,
            onClick = { onPresetSelected(BorderPreset.SOFT) }
        )
    }
}

@Composable
private fun BorderBlurCard(
    title: String,
    selected: Boolean,
    preset: BorderPreset,
    onClick: () -> Unit
) {
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val backgroundColor = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
    }

    Row(
        modifier = Modifier
            .widthIn(min = 104.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(backgroundColor)
            .border(
                width = 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.24f),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BorderPresetPreview(
            preset = preset,
            color = contentColor
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun BorderSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun BorderColorSelector(
    selectedColor: Color,
    onColorSelected: (Color) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f))
            .padding(horizontal = 4.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(end = 4.dp)
    ) {
        itemsIndexed(ColorUtils.defaultColorList) { index, color ->
            SelectableColor(
                modifier = Modifier.padding(
                    start = if (index == 0) 2.dp else 0.dp,
                    end = 2.dp,
                    top = 2.dp,
                    bottom = 2.dp
                ),
                itemColor = color,
                itemSize = 24.dp,
                isSelected = selectedColor == color,
                selectedBorderWidth = 2.dp,
                selectedBorderColor = MaterialTheme.colorScheme.primary,
                onClick = onColorSelected
            )
        }
    }
}

@Composable
private fun BorderValueSlider(
    modifier: Modifier = Modifier,
    value: Float,
    label: String,
    minValue: Float,
    maxValue: Float,
    valueSuffix: String,
    onValueChange: (Float) -> Unit
) {
    val coercedValue = value.coerceIn(minValue, maxValue)
    LaunchedEffect(coercedValue, value) {
        if (coercedValue != value) {
            onValueChange(coercedValue)
        }
    }

    val sliderColors = PrecisionSliderDefaults.colors(
        labelColor = MaterialTheme.colorScheme.onSurface,
        labelActiveColor = MaterialTheme.colorScheme.primary,
        valuePillBackground = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        valuePillTextColor = MaterialTheme.colorScheme.onSurface,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
        trackActiveColor = MaterialTheme.colorScheme.primary,
        thumbColor = Color.White,
        thumbGlowColor = MaterialTheme.colorScheme.primary,
        rangeLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SliderStepButton(
            icon = Icons.Default.Remove,
            enabled = coercedValue > minValue,
            onClick = { onValueChange((coercedValue - 1f).coerceIn(minValue, maxValue)) }
        )

        PrecisionSlider(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 6.dp),
            label = label,
            value = coercedValue,
            onValueChange = { onValueChange(it.coerceIn(minValue, maxValue)) },
            valueRange = minValue..maxValue,
            valueFormatter = { "${it.roundToInt()}$valueSuffix" },
            colors = sliderColors
        )

        SliderStepButton(
            icon = Icons.Default.Add,
            enabled = coercedValue < maxValue,
            onClick = { onValueChange((coercedValue + 1f).coerceIn(minValue, maxValue)) }
        )
    }
}

@Composable
private fun SliderStepButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 0.72f else 0.34f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = if (enabled) 0.28f else 0.12f),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.32f),
            modifier = Modifier.size(15.dp)
        )
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
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.56f)
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.primary
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
            .padding(horizontal = 11.dp, vertical = 6.dp),
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
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(end = 8.dp)
    ) {
        items(presets, key = { it.id }) { preset ->
            val isSelected = selectedPreset?.id == preset.id
            Column(
                modifier = Modifier
                    .width(84.dp)
                    .clickable { onPresetSelected(preset) },
                horizontalAlignment = Alignment.Start
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(35.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(buildBorderGradientBrush(preset))
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(12.dp)
                        )
                ) {
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                                .padding(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(10.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = gradientPresetLabel(preset),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.widthIn(max = 84.dp)
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
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .height(toolbarHeight)
    ) {
        Text(
            modifier = Modifier.align(Alignment.Center),
            text = stringResource(id = R.string.border),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold
        )

        Row(
            modifier = Modifier.fillMaxSize(),
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
private fun BorderPresetTile(
    preset: BorderPreset,
    selected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.46f)
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    }

    Column(
        modifier = Modifier
            .width(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(backgroundColor)
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BorderPresetPreview(
            preset = preset,
            color = contentColor
        )

        Spacer(modifier = Modifier.height(3.dp))

        Text(
            text = presetLabel(preset),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun BorderPresetPreview(
    preset: BorderPreset,
    color: Color
) {
    Canvas(modifier = Modifier.size(22.dp)) {
        val strokeColor = color
        val strokeWidth = 3.dp.toPx()
        val inset = strokeWidth
        val corner = 8.dp.toPx()
        when (preset) {
            BorderPreset.SOLID -> {
                drawRoundRect(
                    color = strokeColor,
                    topLeft = Offset(inset, inset),
                    size = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner),
                    style = Stroke(width = strokeWidth)
                )
            }
            BorderPreset.SOFT -> {
                drawCircle(
                    color = strokeColor.copy(alpha = 0.18f),
                    radius = size.minDimension * 0.46f,
                    center = center
                )
                drawCircle(
                    color = strokeColor.copy(alpha = 0.42f),
                    radius = size.minDimension * 0.34f,
                    center = center,
                    style = Stroke(width = strokeWidth)
                )
            }
            BorderPreset.DOUBLE -> {
                drawRoundRect(
                    color = strokeColor.copy(alpha = 0.55f),
                    topLeft = Offset(inset, inset),
                    size = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner),
                    style = Stroke(width = strokeWidth * 0.75f)
                )
                val innerInset = 8.dp.toPx()
                drawRoundRect(
                    color = strokeColor,
                    topLeft = Offset(innerInset, innerInset),
                    size = androidx.compose.ui.geometry.Size(size.width - innerInset * 2, size.height - innerInset * 2),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner * 0.7f, corner * 0.7f),
                    style = Stroke(width = strokeWidth * 0.75f)
                )
            }
            BorderPreset.OUTLINE -> {
                drawRoundRect(
                    color = strokeColor,
                    topLeft = Offset(inset, inset),
                    size = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }
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
