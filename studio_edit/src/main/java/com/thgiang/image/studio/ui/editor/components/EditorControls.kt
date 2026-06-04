package com.thgiang.image.studio.ui.editor.components

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.toArgb
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.thgiang.image.studio.R
import com.thgiang.image.studio.ui.editor.*
import com.thgiang.image.studio.ui.editor.theme.EditorTokens
import com.thgiang.image.studio.ui.editor.theme.LocalEditorTokens
import com.thgiang.image.core.design.components.PrecisionSlider
import com.thgiang.image.core.design.components.PrecisionSliderColors
import kotlin.math.abs
import kotlin.math.roundToInt

// ── Shadow Sub-Tabs ──────────────────────────────────────────

enum class ShadowSubTab(val labelResId: Int) {
    Intensity(R.string.studio_shadow_tab_intensity),
    Angle(R.string.studio_shadow_tab_angle),
    Distance(R.string.studio_shadow_tab_distance),
    Color(R.string.studio_shadow_tab_color)
}

// ── Shadow Preset Data (Single Source of Truth) ──────────────

data class ShadowPresetData(
    val key: String,
    val labelResId: Int,
    val intensity: Float,
    val angle: Float,
    val distance: Float
)

private val ShadowPresets = listOf(
    ShadowPresetData("none",   R.string.studio_shadow_preset_none,     0f,   0f,   0f),
    ShadowPresetData("light",  R.string.studio_shadow_preset_light,    0.25f, 120f, 8f),
    ShadowPresetData("medium", R.string.studio_shadow_preset_medium,   0.45f, 135f, 15f),
    ShadowPresetData("strong", R.string.studio_shadow_preset_strong,   0.7f,  135f, 22f),
    ShadowPresetData("natural",R.string.studio_shadow_preset_natural,  0.4f,  90f,  12f),
    ShadowPresetData("dramatic", R.string.studio_shadow_preset_dramatic, 0.8f, 210f, 30f)
)

private fun presetMatches(preset: ShadowPresetData, appearance: EditorAppearance): Boolean =
    abs(appearance.shadowIntensity - preset.intensity) < 0.01f &&
    abs(appearance.shadowAngle - preset.angle) < 1f &&
    abs(appearance.shadowDistance - preset.distance) < 0.5f

// ── Main Controls Panel ──────────────────────────────────────

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun EditorControlsV2(
    tool: EditorTool?,
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
    val tokens = LocalEditorTokens.current
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 12.dp,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    ambientColor = Color.Black.copy(alpha = 0.07f),
                    spotColor   = Color.Black.copy(alpha = 0.05f)
                )
                .border(
                    width = 1.dp,
                    color = Color(0xFFE0E0E0),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                )
                .background(
                    color  = Color.White,
                    shape  = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                )
                .padding(bottom = 8.dp)
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
                            .padding(horizontal = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                    when (targetTool) {
                            is EditorTool.Rotate -> {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    EditorLayoutControls(
                                        onEvent = onLayoutEvent
                                    )
                                }
                            }

                            is EditorTool.Sticker -> {
                                StickerPicker(
                                    onStickerSelected = { assetPath ->
                                        onLayoutEvent(EditorEvent.AddSticker(assetPath))
                                    }
                                )
                            }

                            is EditorTool.Shadow -> {
                                var selectedSubTab by rememberSaveable { mutableStateOf(ShadowSubTab.Intensity) }
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    ShadowTabRow(
                                        selectedTab = selectedSubTab,
                                        onTabSelected = { selectedSubTab = it }
                                    )

                                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                                        val isTablet = maxWidth >= 400.dp
                                        val cardHPadding = if (isTablet) 14.dp else 8.dp

                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(Color(0xFFF8F8F8))
                                                .border(0.5.dp, Color(0xFFE5E7EB), RoundedCornerShape(12.dp))
                                                .padding(horizontal = cardHPadding, vertical = 8.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            val sliderColors = remember(tokens) { tokens.toSliderColors() }

                                            // ── Intensity ──
                                            AnimatedVisibility(
                                                visible = selectedSubTab == ShadowSubTab.Intensity,
                                                enter = fadeIn(animationSpec = tween(150)),
                                                exit = fadeOut(animationSpec = tween(150))
                                            ) {
                                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    PrecisionSlider(
                                                        label = stringResource(R.string.studio_shadow_tab_intensity),
                                                        value = appearance.shadowIntensity,
                                                        valueRange = 0f..1f,
                                                        onValueChange = onUpdateShadow,
                                                        valueFormatter = { "${(it * 100).roundToInt()}%" },
                                                        colors = sliderColors
                                                    )

                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .horizontalScroll(rememberScrollState())
                                                            .padding(vertical = 2.dp),
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        ShadowPresets.forEach { preset ->
                                                            val isSelected = presetMatches(preset, appearance)
                                                            ShadowPresetCard(
                                                                preset = preset,
                                                                selected = isSelected,
                                                                onClick = {
                                                                    onUpdateShadow(preset.intensity)
                                                                    onUpdateShadowAngle(preset.angle)
                                                                    onUpdateShadowDistance(preset.distance)
                                                                }
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            // ── Angle ──
                                            AnimatedVisibility(
                                                visible = selectedSubTab == ShadowSubTab.Angle,
                                                enter = fadeIn(animationSpec = tween(150)),
                                                exit = fadeOut(animationSpec = tween(150))
                                            ) {
                                                PrecisionSlider(
                                                    label = stringResource(R.string.studio_shadow_tab_angle),
                                                    value = appearance.shadowAngle,
                                                    valueRange = 0f..360f,
                                                    onValueChange = onUpdateShadowAngle,
                                                    valueFormatter = { "${it.roundToInt()}°" },
                                                    colors = sliderColors
                                                )
                                            }

                                            // ── Distance ──
                                            AnimatedVisibility(
                                                visible = selectedSubTab == ShadowSubTab.Distance,
                                                enter = fadeIn(animationSpec = tween(150)),
                                                exit = fadeOut(animationSpec = tween(150))
                                            ) {
                                                PrecisionSlider(
                                                    label = stringResource(R.string.studio_shadow_tab_distance),
                                                    value = appearance.shadowDistance,
                                                    valueRange = 0f..40f,
                                                    onValueChange = onUpdateShadowDistance,
                                                    valueFormatter = { it.roundToInt().toString() },
                                                    colors = sliderColors
                                                )
                                            }

                                            // ── Color ──
                                            AnimatedVisibility(
                                                visible = selectedSubTab == ShadowSubTab.Color,
                                                enter = fadeIn(animationSpec = tween(150)),
                                                exit = fadeOut(animationSpec = tween(150))
                                            ) {
                                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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

                            is EditorTool.Transparency -> {
                                val sliderColors = remember(tokens) { tokens.toSliderColors() }
                                PrecisionSlider(
                                    label = stringResource(R.string.studio_tool_transparency),
                                    value = appearance.alpha,
                                    valueRange = 0.1f..1f,
                                    onValueChange = onUpdateAlpha,
                                    valueFormatter = { "${(it * 100).roundToInt()}%" },
                                    colors = sliderColors
                                )
                            }

                            is EditorTool.Crop -> {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = stringResource(R.string.studio_crop_label),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = tokens.textPrimary
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

                            else -> {}
                        }
                    }
                }
            }
        }
    }
}

// ── Preset Card ──────────────────────────────────────────────

@Composable
private fun ShadowPresetCard(
    preset: ShadowPresetData,
    selected: Boolean,
    onClick: () -> Unit,
    tokens: EditorTokens = LocalEditorTokens.current
) {
    val borderColor = if (selected) tokens.accent else tokens.borderSubtle
    val borderWidth = if (selected) 1.5.dp else 0.5.dp
    val cardBg = if (preset.key == "none") {
        Color(0xFFF0F0F0)
    } else {
        if (selected) tokens.accentSoft else Color(0xFFF8F8F8)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(72.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(cardBg)
                .border(borderWidth, borderColor, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val r = size.width * 0.22f

                if (preset.key == "none") {
                    // Light card: use dark dashed circle
                    drawCircle(
                        Color(0xFF9CA3AF), r + 1.dp.toPx(), Offset(cx, cy),
                        style = Stroke(1.5.dp.toPx())
                    )
                    // X mark
                    val d = r * 0.55f
                    drawLine(Color(0xFF9CA3AF), Offset(cx - d, cy - d), Offset(cx + d, cy + d), 1.5.dp.toPx())
                    drawLine(Color(0xFF9CA3AF), Offset(cx + d, cy - d), Offset(cx - d, cy + d), 1.5.dp.toPx())
                } else {
                    val angleRad = Math.toRadians(preset.angle.toDouble())
                    val dist = preset.distance * 0.015f
                    val sx = (kotlin.math.cos(angleRad) * dist).toFloat()
                    val sy = (kotlin.math.sin(angleRad) * dist).toFloat()

                    // Soft drop shadow with radial gradient
                    val shadowBrush = androidx.compose.ui.graphics.Brush.radialGradient(
                        colors = listOf(Color.Black.copy(alpha = preset.intensity * 0.7f), Color.Transparent),
                        center = Offset(cx + r * sx, cy + r * sy),
                        radius = r * 1.5f
                    )
                    drawCircle(
                        brush = shadowBrush,
                        radius = r * 1.5f,
                        center = Offset(cx + r * sx, cy + r * sy)
                    )

                    // 3D Metallic/Glossy Sphere
                    val sphereBrush = androidx.compose.ui.graphics.Brush.radialGradient(
                        colors = listOf(Color.White, Color(0xFF8A939E), Color(0xFF3A424A)),
                        center = Offset(cx - r * 0.25f, cy - r * 0.25f),
                        radius = r
                    )
                    drawCircle(
                        brush = sphereBrush,
                        radius = r,
                        center = Offset(cx, cy)
                    )
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = stringResource(preset.labelResId),
            color = if (selected) tokens.textPrimary else tokens.textSecondary,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
            ),
            maxLines = 1,
            softWrap = false
        )
    }
}

// ── Color Swatch ─────────────────────────────────────────────

@Composable
private fun ShadowColorSwatch(
    currentColorArgb: Int,
    onSelectColor: (Int) -> Unit,
    tokens: EditorTokens = LocalEditorTokens.current
) {
    val shadowColors = remember {
        listOf(
            Color.Black,
            Color(0xFF2C2C2C),
            Color(0xFF1E3A8A).copy(alpha = 0.8f),
            Color(0xFF14532D).copy(alpha = 0.8f),
            Color(0xFF581C87).copy(alpha = 0.8f)
        )
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
        ShadowCustomColorChip(onClick = { /* TODO: open color picker */ })
    }
}

@Composable
private fun ShadowColorChip(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    tokens: EditorTokens = LocalEditorTokens.current
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                border = BorderStroke(
                    width = if (selected) 2.dp else 0.5.dp,
                    color = if (selected) tokens.accent else tokens.borderSubtle
                ),
                shape = CircleShape
            )
            .clickable(onClick = onClick)
    )
}

@Composable
private fun ShadowCustomColorChip(
    onClick: () -> Unit,
    tokens: EditorTokens = LocalEditorTokens.current
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(tokens.surfaceFloating)
            .border(BorderStroke(0.5.dp, tokens.borderDefault), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "+",
            color = tokens.textSecondary,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
            fontWeight = FontWeight.Light
        )
    }
}

// ── Panel Handle ─────────────────────────────────────────────

@Composable
private fun PanelHandle(tokens: EditorTokens = LocalEditorTokens.current) {
    Box(
        modifier = Modifier
            .padding(top = 8.dp, bottom = 4.dp)
            .size(32.dp, 4.dp)
            .background(
                color = Color(0xFFD1D5DB),
                shape = RoundedCornerShape(2.dp)
            )
    )
}

// ── Tokens → PrecisionSliderColors mapping ─────────────────────

private fun EditorTokens.toSliderColors() = PrecisionSliderColors(
    labelColor = textPrimary,
    labelActiveColor = accent,
    valuePillBackground = surfaceFloating,
    valuePillTextColor = textPrimary,
    trackColor = surfaceFloating,
    trackActiveColor = accent,
    thumbColor = Color.White,
    thumbGlowColor = accent,
    rangeLabelColor = textSecondary,
    borderColor = borderSubtle,
)

// ── Crop Ratio Button ────────────────────────────────────────

@Composable
private fun CropRatioButton(
    ratio: CropRatio,
    selected: Boolean,
    onClick: () -> Unit,
    tokens: EditorTokens = LocalEditorTokens.current
) {
    val borderColor = if (selected) tokens.accent else Color(0xFFE5E7EB)
    val borderWidth = if (selected) 2.dp else 1.dp
    val containerBg = if (selected) tokens.accentSoft else Color(0xFFF8F8F8)
    val contentColor = if (selected) tokens.accent else tokens.textSecondary

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .then(
                if (ratio == CropRatio.ORIGINAL) {
                    Modifier.widthIn(min = 76.dp)
                } else {
                    Modifier.width(76.dp)
                }
            )
            .height(84.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(containerBg)
            .border(borderWidth, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        // Miniature ratio representation frame
        Box(
            modifier = Modifier
                .height(32.dp)
                .width(60.dp),
            contentAlignment = Alignment.Center
        ) {
            val boxModifier = Modifier
                .border(
                    width = 1.5.dp,
                    color = contentColor.copy(alpha = if (selected) 0.85f else 0.5f),
                    shape = RoundedCornerShape(3.dp)
                )
                .background(contentColor.copy(alpha = 0.05f))

            when (ratio) {
                CropRatio.ORIGINAL -> {
                    // Nested double rectangles representing original layout fit
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .border(1.dp, contentColor.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .align(Alignment.Center)
                                .border(1.5.dp, contentColor, RoundedCornerShape(2.dp))
                        )
                    }
                }
                CropRatio.RATIO_1_1 -> {
                    Box(modifier = boxModifier.size(22.dp))
                }
                CropRatio.RATIO_3_4 -> {
                    Box(modifier = boxModifier.size(18.dp, 24.dp))
                }
                CropRatio.RATIO_4_3 -> {
                    Box(modifier = boxModifier.size(24.dp, 18.dp))
                }
                CropRatio.RATIO_9_16 -> {
                    Box(modifier = boxModifier.size(14.dp, 26.dp))
                }
                CropRatio.RATIO_16_9 -> {
                    Box(modifier = boxModifier.size(26.dp, 14.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = if (ratio == CropRatio.ORIGINAL) {
                stringResource(R.string.studio_crop_ratio_original)
            } else {
                ratio.label
            },
            color = contentColor,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            ),
            maxLines = 1
        )
    }
}

// ── Layout Controls ──────────────────────────────────────────

@Composable
private fun StickerPicker(
    onStickerSelected: (String) -> Unit,
    tokens: EditorTokens = LocalEditorTokens.current
) {
    val context = LocalContext.current
    val stickerAssets = remember(context) {
        context.assets.list("sticker")
            ?.filter { it.endsWith(".png", ignoreCase = true) }
            ?.sortedWith(compareBy { asset ->
                asset.substringBeforeLast('.').toIntOrNull() ?: Int.MAX_VALUE
            })
            .orEmpty()
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.studio_tool_sticker),
            style = MaterialTheme.typography.titleSmall,
            color = tokens.textPrimary
        )

        if (stickerAssets.isEmpty()) {
            Text(
                text = stringResource(R.string.studio_gallery_no_templates),
                color = tokens.textSecondary,
                style = MaterialTheme.typography.bodySmall
            )
            return@Column
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            stickerAssets.forEach { assetName ->
                val assetPath = "sticker/$assetName"
                StickerThumb(
                    context = context,
                    assetPath = assetPath,
                    onClick = { onStickerSelected(assetPath) }
                )
            }
        }
    }
}

@Composable
private fun StickerThumb(
    context: Context,
    assetPath: String,
    onClick: () -> Unit,
    tokens: EditorTokens = LocalEditorTokens.current
) {
    Box(
        modifier = Modifier
            .size(76.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFF8F8F8))
            .border(1.dp, tokens.borderSubtle, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data("file:///android_asset/$assetPath")
                .crossfade(true)
                .build(),
            contentDescription = assetPath,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
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
    modifier: Modifier = Modifier,
    tokens: EditorTokens = LocalEditorTokens.current
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
                tint = tokens.textPrimary,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
            color = tokens.textPrimary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            minLines = 2,
            maxLines = 2
        )
    }
}

// Utility to convert color to Int ARGB for storage
private fun Color.toArgbInt(): Int = this.toArgb()

// ── Shadow Tab Row ───────────────────────────────────────────

@Composable
fun ShadowTabRow(
    selectedTab: ShadowSubTab,
    onTabSelected: (ShadowSubTab) -> Unit,
    modifier: Modifier = Modifier,
    tokens: EditorTokens = LocalEditorTokens.current
) {
    Column(modifier = modifier.fillMaxWidth()) {
        val scrollState = rememberScrollState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ShadowSubTab.values().forEach { tab ->
                val isSelected = selectedTab == tab
                val contentColor = if (isSelected) tokens.textPrimary else tokens.textSecondary
                val iconColor = if (isSelected) tokens.textPrimary else tokens.textSecondary

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onTabSelected(tab) }
                        )
                        .padding(top = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier.padding(horizontal = 4.dp)
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
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = stringResource(tab.labelResId),
                            color = contentColor,
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .height(1.5.dp)
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(if (isSelected) tokens.accent else Color.Transparent)
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(tokens.borderDefault)
        )
    }
}
