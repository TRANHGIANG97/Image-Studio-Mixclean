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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                .background(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                )
                .padding(bottom = 16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PanelHandle()

                AnimatedContent(
                    targetState = tool,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(150)) with fadeOut(animationSpec = tween(150))
                    },
                    label = "ControlsAnimation"
                ) { targetTool ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
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
                                    // Sub-tab selection bar
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        ShadowSubTab.values().forEach { subTab ->
                                            val isSelected = selectedSubTab == subTab
                                            ShadowPresetChip(
                                                label = stringResource(subTab.labelResId),
                                                selected = isSelected,
                                                onClick = { selectedSubTab = subTab }
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    // Sub-tab content with AnimatedContent for smooth transition
                                    AnimatedContent(
                                        targetState = selectedSubTab,
                                        transitionSpec = {
                                            fadeIn(animationSpec = tween(150)) with fadeOut(animationSpec = tween(150))
                                        },
                                        label = "ShadowSubTabAnimation"
                                    ) { currentTab ->
                                        Box(modifier = Modifier.fillMaxWidth()) {
                                            when (currentTab) {
                                                ShadowSubTab.Intensity -> {
                                                    CompactMetricSlider(
                                                        label = stringResource(
                                                            R.string.studio_shadow_label,
                                                            (appearance.shadowIntensity * 100).roundToInt()
                                                        ),
                                                        value = appearance.shadowIntensity,
                                                        valueRange = 0f..1f,
                                                        onValueChange = onUpdateShadow
                                                    )
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
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            .padding(vertical = 12.dp)
            .size(36.dp, 4.dp)
            .background(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(2.dp)
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
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = value,
            valueRange = valueRange,
            onValueChange = onValueChange,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.height(32.dp)
        )
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
            icon = ImageVector.vectorResource(id = R.drawable.ic_rotate_left),
            label = stringResource(R.string.studio_layout_rotate_left),
            onClick = { onEvent(EditorEvent.UpdateRotation(-90f)) },
            modifier = Modifier.weight(1f)
        )
        LayoutActionButton(
            icon = ImageVector.vectorResource(id = R.drawable.ic_rotate_right),
            label = stringResource(R.string.studio_layout_rotate_right),
            onClick = { onEvent(EditorEvent.UpdateRotation(90f)) },
            modifier = Modifier.weight(1f)
        )
        LayoutActionButton(
            icon = ImageVector.vectorResource(id = R.drawable.ic_flip_horizontal),
            label = stringResource(R.string.studio_layout_flip_horizontal),
            onClick = { onEvent(EditorEvent.FlipHorizontal) },
            modifier = Modifier.weight(1f)
        )
        LayoutActionButton(
            icon = ImageVector.vectorResource(id = R.drawable.ic_flip_vertical),
            label = stringResource(R.string.studio_layout_flip_vertical),
            onClick = { onEvent(EditorEvent.FlipVertical) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun LayoutActionButton(
    icon: ImageVector,
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
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
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
