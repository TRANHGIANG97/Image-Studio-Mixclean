package com.thgiang.image.studio.ui.editor.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.toArgb
import com.thgiang.image.studio.R
import com.thgiang.image.studio.ui.editor.*
import kotlin.math.roundToInt

enum class ShadowSubTab(val labelResId: Int) {
    Intensity(R.string.studio_shadow_tab_intensity),
    Angle(R.string.studio_shadow_tab_angle),
    Distance(R.string.studio_shadow_tab_distance),
    Color(R.string.studio_shadow_tab_color)
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun EditorControlsV2(
    tool: EditorTool,
    appearance: EditorAppearance,
    cropRatio: CropRatio,
    onUpdateShadow: (Float) -> Unit,
    onUpdateShadowAngle: (Float) -> Unit,
    onUpdateShadowDistance: (Float) -> Unit,
    onUpdateShadowColor: (Int) -> Unit,
    onUpdateAlpha: (Float) -> Unit,
    onSelectCropRatio: (CropRatio) -> Unit,
    onLayoutEvent: (EditorEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp)
                )
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xF0272D31),
                            Color(0xF112171B)
                        )
                    ),
                    shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp)
                )
                .padding(bottom = 14.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PanelHandle()

                AnimatedContent(
                    targetState = tool,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(150)) togetherWith fadeOut(animationSpec = tween(150))
                    },
                    label = "ControlsAnimation"
                ) { targetTool ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        when (targetTool) {
                            EditorTool.Layout -> {
                                Text(
                                    text = stringResource(R.string.studio_layout_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }

                            EditorTool.Shadow -> {
                                var selectedSubTab by rememberSaveable { mutableStateOf(ShadowSubTab.Intensity) }
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    ShadowTabRow(
                                        selectedTab = selectedSubTab,
                                        onTabSelected = { selectedSubTab = it }
                                    )

                                    AnimatedContent(
                                        targetState = selectedSubTab,
                                        transitionSpec = {
                                            fadeIn(animationSpec = tween(150)) togetherWith fadeOut(animationSpec = tween(150))
                                        },
                                        label = "ShadowSubTabAnimation"
                                    ) { currentTab ->
                                        Box(modifier = Modifier.fillMaxWidth()) {
                                            when (currentTab) {
                                                ShadowSubTab.Intensity -> {
                                                    Column(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(24.dp))
                                                            .background(Color(0xD423282D))
                                                            .padding(horizontal = 14.dp, vertical = 18.dp),
                                                        verticalArrangement = Arrangement.spacedBy(20.dp)
                                                    ) {
                                                        CompactMetricSlider(
                                                            label = stringResource(
                                                                R.string.studio_shadow_label,
                                                                (appearance.shadowIntensity * 100).roundToInt()
                                                            ),
                                                            value = appearance.shadowIntensity,
                                                            valueRange = 0f..1f,
                                                            onValueChange = onUpdateShadow
                                                        )
                                                        
                                                        // 3D Sphere preset list
                                                        val presets = ShadowPreset.values()
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .horizontalScroll(rememberScrollState())
                                                                .padding(vertical = 4.dp),
                                                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                                                        ) {
                                                            presets.forEach { p ->
                                                                val isPresetSelected = when (p) {
                                                                    ShadowPreset.None -> appearance.shadowIntensity == 0f
                                                                    ShadowPreset.Light -> appearance.shadowIntensity == 0.25f && appearance.shadowAngle == 120f && appearance.shadowDistance == 8f
                                                                    ShadowPreset.Medium -> appearance.shadowIntensity == 0.45f && appearance.shadowAngle == 135f && appearance.shadowDistance == 15f
                                                                    ShadowPreset.Strong -> appearance.shadowIntensity == 0.7f && appearance.shadowAngle == 135f && appearance.shadowDistance == 22f
                                                                    ShadowPreset.Natural -> appearance.shadowIntensity == 0.4f && appearance.shadowAngle == 90f && appearance.shadowDistance == 12f
                                                                    ShadowPreset.Dramatic -> appearance.shadowIntensity == 0.8f && appearance.shadowAngle == 210f && appearance.shadowDistance == 30f
                                                                }
                                                                
                                                                SpherePresetItem(
                                                                    preset = p,
                                                                    selected = isPresetSelected,
                                                                    onClick = {
                                                                        when (p) {
                                                                            ShadowPreset.None -> {
                                                                                onUpdateShadow(0f)
                                                                                onUpdateShadowDistance(0f)
                                                                            }
                                                                            ShadowPreset.Light -> {
                                                                                onUpdateShadow(0.25f)
                                                                                onUpdateShadowAngle(120f)
                                                                                onUpdateShadowDistance(8f)
                                                                            }
                                                                            ShadowPreset.Medium -> {
                                                                                onUpdateShadow(0.45f)
                                                                                onUpdateShadowAngle(135f)
                                                                                onUpdateShadowDistance(15f)
                                                                            }
                                                                            ShadowPreset.Strong -> {
                                                                                onUpdateShadow(0.7f)
                                                                                onUpdateShadowAngle(135f)
                                                                                onUpdateShadowDistance(22f)
                                                                            }
                                                                            ShadowPreset.Natural -> {
                                                                                onUpdateShadow(0.4f)
                                                                                onUpdateShadowAngle(90f)
                                                                                onUpdateShadowDistance(12f)
                                                                            }
                                                                            ShadowPreset.Dramatic -> {
                                                                                onUpdateShadow(0.8f)
                                                                                onUpdateShadowAngle(210f)
                                                                                onUpdateShadowDistance(30f)
                                                                            }
                                                                        }
                                                                    }
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                                ShadowSubTab.Angle -> {
                                                    CompactMetricSlider(
                                                        label = stringResource(
                                                            R.string.studio_shadow_angle_label,
                                                            appearance.shadowAngle.roundToInt()
                                                        ),
                                                        value = appearance.shadowAngle,
                                                        valueRange = 0f..360f,
                                                        onValueChange = onUpdateShadowAngle
                                                    )
                                                }
                                                ShadowSubTab.Distance -> {
                                                    CompactMetricSlider(
                                                        label = stringResource(
                                                            R.string.studio_shadow_distance_label,
                                                            appearance.shadowDistance.roundToInt()
                                                        ),
                                                        value = appearance.shadowDistance,
                                                        valueRange = 0f..40f,
                                                        onValueChange = onUpdateShadowDistance
                                                    )
                                                }
                                                ShadowSubTab.Color -> {
                                                    Column {
                                                        Text(
                                                            text = stringResource(R.string.studio_shadow_tab_color),
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = if (isSystemInDarkTheme()) Color(0xFF8E909A) else MaterialTheme.colorScheme.onSurfaceVariant,
                                                            modifier = Modifier.padding(bottom = 6.dp)
                                                        )
                                                        ShadowColorSwatch(
                                                            currentColorArgb = appearance.shadowColorArgb,
                                                            onSelectColor = onUpdateShadowColor
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            EditorTool.Transparency -> {
                                CompactMetricSlider(
                                    label = stringResource(
                                        R.string.studio_transparency_label,
                                        (appearance.alpha * 100).roundToInt()
                                    ),
                                    value = appearance.alpha,
                                    valueRange = 0.1f..1f,
                                    onValueChange = onUpdateAlpha
                                )
                            }

                            EditorTool.Crop -> {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = stringResource(R.string.studio_crop_label),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        CropRatio.values().forEach { ratio ->
                                            CropRatioButton(
                                                ratio = ratio,
                                                selected = cropRatio == ratio,
                                                onClick = { onSelectCropRatio(ratio) }
                                            )
                                        }
                                    }
                                }
                            }

                            EditorTool.Rotate -> {
                                EditorLayoutControls(
                                    onEvent = onLayoutEvent
                                )
                            }

                            else -> {}
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShadowPresetChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer 
                         else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer 
                       else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = contentColor,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun ShadowColorSwatch(
    currentColorArgb: Int,
    onSelectColor: (Int) -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val shadowColors = remember(isDark) {
        if (isDark) {
            listOf(
                Color.Black,
                Color(0xFF2C2C2C),
                Color(0xFF1E3A8A).copy(alpha = 0.8f),
                Color(0xFF14532D).copy(alpha = 0.8f),
                Color(0xFF581C87).copy(alpha = 0.8f)
            )
        } else {
            listOf(
                Color.Black,
                Color(0xFF4A4A4A),
                Color(0xFF1E3A8A).copy(alpha = 0.5f),
                Color(0xFF14532D).copy(alpha = 0.5f),
                Color(0xFF581C87).copy(alpha = 0.5f)
            )
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        shadowColors.forEach { color ->
            val argb = color.toArgbInt()
            val isSelected = currentColorArgb == argb
            ShadowColorChip(
                color = color,
                selected = isSelected,
                onClick = { onSelectColor(argb) }
            )
        }
    }
}

@Composable
private fun ShadowColorChip(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                border = BorderStroke(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else Color.LightGray.copy(alpha = 0.5f)
                ),
                shape = CircleShape
            )
            .clickable(onClick = onClick)
    )
}

@Composable
private fun PanelHandle() {
    Box(
        modifier = Modifier
            .padding(top = 12.dp, bottom = 8.dp)
            .size(48.dp, 5.dp)
            .background(
                color = Color.White.copy(alpha = 0.32f),
                shape = RoundedCornerShape(3.dp)
            )
    )
}

@Composable
private fun CompactMetricSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val parts = remember(label) { label.split(": ") }
    val title = parts.getOrNull(0) ?: label
    val valueStr = parts.getOrNull(1)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                color = Color.White,
                fontWeight = FontWeight.Normal
            )
            if (valueStr != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xCC171C20))
                        .padding(horizontal = 24.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = valueStr,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp),
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        Slider(
            value = value,
            valueRange = valueRange,
            onValueChange = onValueChange,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color(0xFF8E7BF2),
                inactiveTrackColor = Color.Black.copy(alpha = 0.82f)
            ),
            modifier = Modifier.height(28.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = valueRange.start.roundToInt().toString(),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp),
                color = Color(0xFFB9BEC4)
            )
            Text(
                text = valueRange.endInclusive.roundToInt().toString(),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp),
                color = Color(0xFFB9BEC4)
            )
        }
    }
}

@Composable
private fun CropRatioButton(
    ratio: CropRatio,
    selected: Boolean,
    onClick: () -> Unit
) {
    val containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer 
                         else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer 
                       else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = ratio.label,
            color = contentColor,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun EditorLayoutControls(
    onEvent: (EditorEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        LayoutActionButton(
            iconRes = R.drawable.ic_rotate_left,
            label = stringResource(R.string.studio_layout_rotate_left),
            onClick = { onEvent(EditorEvent.UpdateRotation(-90f)) },
            modifier = Modifier.weight(1f)
        )
        LayoutActionButton(
            iconRes = R.drawable.ic_rotate_right,
            label = stringResource(R.string.studio_layout_rotate_right),
            onClick = { onEvent(EditorEvent.UpdateRotation(90f)) },
            modifier = Modifier.weight(1f)
        )
        LayoutActionButton(
            iconRes = R.drawable.ic_flip_horizontal,
            label = stringResource(R.string.studio_layout_flip_horizontal),
            onClick = { onEvent(EditorEvent.FlipHorizontal) },
            modifier = Modifier.weight(1f)
        )
        LayoutActionButton(
            iconRes = R.drawable.ic_flip_vertical,
            label = stringResource(R.string.studio_layout_flip_vertical),
            onClick = { onEvent(EditorEvent.FlipVertical) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun LayoutActionButton(
    iconRes: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp)
    ) {
        Box(modifier = Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = label,
                tint = Color.Unspecified,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            minLines = 2,
            maxLines = 2
        )
    }
}

// Utility to convert color to Int ARGB for storage
private fun Color.toArgbInt(): Int = this.toArgb()

enum class ShadowPreset(val labelResId: Int) {
    None(R.string.studio_shadow_preset_none),
    Light(R.string.studio_shadow_preset_light),
    Medium(R.string.studio_shadow_preset_medium),
    Strong(R.string.studio_shadow_preset_strong),
    Natural(R.string.studio_shadow_preset_natural),
    Dramatic(R.string.studio_shadow_preset_dramatic)
}

@Composable
fun ShadowTabRow(
    selectedTab: ShadowSubTab,
    onTabSelected: (ShadowSubTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ShadowSubTab.values().forEach { tab ->  
            val isSelected = selectedTab == tab
            val contentColor = if (isSelected) Color.White else Color(0xFF9EA4AA)
            val iconColor = if (isSelected) Color.White else Color(0xFF9EA4AA)

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onTabSelected(tab) }
                    )
                    .padding(vertical = 8.dp, horizontal = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val iconRes = when (tab) {
                        ShadowSubTab.Intensity -> if (isSelected) R.drawable.ic_tool_shadow_selected else R.drawable.ic_tool_shadow
                        ShadowSubTab.Angle -> R.drawable.ic_tool_shadow_angle
                        ShadowSubTab.Distance -> R.drawable.ic_tool_shadow_distance
                        ShadowSubTab.Color -> R.drawable.ic_tool_shadow_color
                    }
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        tint = if (tab == ShadowSubTab.Intensity) Color.Unspecified else iconColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(tab.labelResId),
                        color = contentColor,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
                Spacer(modifier = Modifier.height(9.dp))
                Box(
                    modifier = Modifier
                        .height(3.dp)
                        .width(58.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(if (isSelected) Color(0xFF4B91FF) else Color.Transparent)
                )
            }
        }
    }
}

@Composable
fun SpherePresetItem(
    preset: ShadowPreset,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (selected) Color(0xFF4B91FF) else Color.Transparent
    val cardBackground = if (preset == ShadowPreset.None) Color(0xFF20272C) else Color(0xFFF6F7F8)
    val textColor = if (selected) Color(0xFF4B91FF) else Color.White.copy(alpha = 0.86f)
    val textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .width(86.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
                Box(
            modifier = Modifier
                .size(82.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(cardBackground)
                .border(2.dp, borderColor, RoundedCornerShape(16.dp))
                .padding(6.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val radius = w * 0.28f

                if (preset == ShadowPreset.None) {
                    val center = Offset(w / 2f, h / 2f)
                    drawCircle(
                        color = Color(0xFFDDE2E6),
                        radius = radius,
                        style = Stroke(width = 2.dp.toPx())
                    )
                    drawLine(
                        color = Color(0xFFDDE2E6),
                        start = Offset(center.x - radius * 0.72f, center.y + radius * 0.72f),
                        end = Offset(center.x + radius * 0.72f, center.y - radius * 0.72f),
                        strokeWidth = 2.dp.toPx(),
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                } else {
                    val shadowCenter = Offset(w / 2f, h / 2f)
                    val shadowOffset = when (preset) {
                        ShadowPreset.Light -> Offset(w * 0.08f, h * 0.08f)
                        ShadowPreset.Medium -> Offset(w * 0.12f, h * 0.12f)
                        ShadowPreset.Strong -> Offset(w * 0.16f, h * 0.16f)
                        ShadowPreset.Natural -> Offset(0f, h * 0.15f)
                        ShadowPreset.Dramatic -> Offset(w * 0.22f, h * 0.22f)
                        else -> Offset.Zero
                    }
                    val shadowAlpha = when (preset) {
                        ShadowPreset.Light -> 0.35f
                        ShadowPreset.Medium -> 0.45f
                        ShadowPreset.Strong -> 0.65f
                        ShadowPreset.Natural -> 0.4f
                        ShadowPreset.Dramatic -> 0.75f
                        else -> 0f
                    }
                    val shadowRadius = radius * when (preset) {
                        ShadowPreset.Light -> 1.0f
                        ShadowPreset.Medium -> 1.1f
                        ShadowPreset.Strong -> 1.2f
                        ShadowPreset.Natural -> 1.1f
                        ShadowPreset.Dramatic -> 1.4f
                        else -> 1.0f
                    }

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.Black.copy(alpha = shadowAlpha), Color.Transparent),
                            center = shadowCenter + shadowOffset,
                            radius = shadowRadius
                        ),
                        center = shadowCenter + shadowOffset,
                        radius = shadowRadius
                    )

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFFFFFFF),
                                Color(0xFFE5E6EB),
                                Color(0xFFB0B3BC),
                                Color(0xFF5A5D64)
                            ),
                            center = Offset(w * 0.42f, h * 0.42f),
                            radius = radius * 1.3f
                        ),
                        center = Offset(w / 2f, h / 2f),
                        radius = radius
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(7.dp))
        Text(
            text = stringResource(preset.labelResId),
            color = textColor,
            style = textStyle,
            maxLines = 1
        )
    }
}
