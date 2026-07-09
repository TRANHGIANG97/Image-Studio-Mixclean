@file:Suppress("OPT_IN_USAGE", "OPT_IN_USAGE_ERROR")
package com.thgiang.image.studio.ui.editor.label.panel
import com.thgiang.image.studio.ui.editor.label.geometry.*
import com.thgiang.image.studio.ui.editor.label.panel.*
import com.thgiang.image.studio.ui.editor.panel.*
import com.thgiang.image.studio.ui.editor.mapper.*

import android.annotation.SuppressLint
import com.thgiang.image.studio.ui.editor.label.model.ShapeLabelDefaults
import com.thgiang.image.studio.ui.editor.model.EditorLayer
import com.thgiang.image.studio.ui.editor.model.LayerViewportScale
import com.thgiang.image.studio.ui.editor.model.ElevationTarget
import com.thgiang.image.studio.ui.editor.model.ShapeType
import com.thgiang.image.studio.ui.editor.model.isLabelLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.IconButton
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatAlignRight
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thgiang.image.core.design.components.PrecisionSlider
import com.thgiang.image.studio.R
import com.thgiang.image.studio.ui.editor.EditorEvent
import com.thgiang.image.studio.ui.editor.panel.toSliderColors
import com.thgiang.image.studio.ui.editor.theme.EditorTokens
import com.thgiang.image.studio.util.FontDownloader
import com.thgiang.image.studio.util.FontManifestEntry
import kotlinx.coroutines.launch

// ── Presets ─────────────────────────────────────────────

internal data class TypographyPreset(
    val labelRes: Int,
    val fontWeight: String,
    val textSizeSp: Float,
    val textTransform: String?,
)

private val presets = listOf(
    TypographyPreset(R.string.studio_label_preset_title, "bold", 80f, null),
    TypographyPreset(R.string.studio_label_preset_subtitle, "600", 60f, null),
    TypographyPreset(R.string.studio_label_preset_caption, "normal", 40f, null),
    TypographyPreset(R.string.studio_label_preset_uppercase, "bold", 54f, "uppercase"),
)

enum class LabelEditTab {
    EDIT,       // Sửa
    LABEL,      // Nhãn
    FONT,       // Font
    SIZE,       // Size
    TEXT_STYLE, // Kiểu văn bản (Đề mục/Đề mục phụ/Nội dung)
    FORMAT,     // Định dạng
    ALIGN,      // Căn lề
    BG_COLOR,   // Màu nền
    TEXT_COLOR, // Màu chữ
    ELEVATION,  // Độ nổi
    TEXT_FORM,  // Hình dạng chữ (path / warp)
    SHAPE       // Khung shape
}

// ── Main Section ────────────────────────────────────────

/**
 * Tab ordering inspired by Microsoft Word:
 * - [labelTextFirstTabs]: Text-first (Label tool)
 * - [LabelEditTab.values()]: Default (all tabs in enum order)
 */
internal val labelTextFirstTabs: List<LabelEditTab> = listOf(
    LabelEditTab.EDIT,
    LabelEditTab.FONT,
    LabelEditTab.SIZE,
    LabelEditTab.TEXT_STYLE,
    LabelEditTab.FORMAT,
    LabelEditTab.ALIGN,
    LabelEditTab.TEXT_COLOR,
    LabelEditTab.ELEVATION,
    LabelEditTab.TEXT_FORM,
)

@SuppressLint("UnrememberedMutableInteractionSource")
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LabelEditSection(
    layer: EditorLayer,
    tokens: EditorTokens,
    onLayoutEvent: (EditorEvent) -> Unit,
    tabOrder: List<LabelEditTab> = LabelEditTab.values().toList(),
    showTabBar: Boolean = true,
    canvasFirstMode: Boolean = false,
    activeTabExternal: LabelEditTab? = null,
    onActiveTabChange: (LabelEditTab) -> Unit = {},
) {
    var showFontPicker by remember { mutableStateOf(false) }
    var manifestFonts by remember { mutableStateOf<List<FontManifestEntry>>(emptyList()) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val sliderColors = remember(tokens) { tokens.toSliderColors() }

    var activeTabInternal by rememberSaveable(layer.id) { mutableStateOf(tabOrder.firstOrNull() ?: LabelEditTab.FONT) }
    val activeTab = activeTabExternal ?: activeTabInternal
    fun setActiveTab(tab: LabelEditTab) {
        if (activeTabExternal == null) {
            activeTabInternal = tab
        }
        onActiveTabChange(tab)
    }

    // Tool-first mode: EDIT tab opens inline canvas editor — no duplicate panel TextField.
    if (!canvasFirstMode) {
        LaunchedEffect(activeTab, layer.id) {
            if (activeTab == LabelEditTab.EDIT) {
                onLayoutEvent(EditorEvent.RequestTextEdit(layer.id))
            }
        }
    }

    val isBold = EditorTextStyleMapper.isBoldWeight(layer.fontWeight)
    val isItalic = EditorTextStyleMapper.isItalicStyle(layer.fontStyle)
    val currentAlign = layer.textAlign?.lowercase() ?: "center"
    val currentTransform = layer.textTransform?.lowercase()

    val editableShapes = remember(layer.shapeType) {
        val base = defaultCreateShapes.toMutableList()
        if (layer.shapeType == ShapeType.PATH && !base.contains(ShapeType.PATH)) {
            base.add(ShapeType.PATH)
        }
        if (layer.shapeType == ShapeType.POLYGON && !base.contains(ShapeType.POLYGON)) {
            base.add(ShapeType.POLYGON)
        }
        base
    }

    LaunchedEffect(Unit) {
        manifestFonts = FontDownloader.getAvailableFonts()
    }

    FontPickerBottomSheet(
        visible = showFontPicker,
        fonts = manifestFonts,
        selectedFamilySlug = layer.fontFamily ?: "Outfit",
        tokens = tokens,
        onDismiss = { showFontPicker = false },
        onSelect = { slug ->
            onLayoutEvent(EditorEvent.UpdateTextFontFamily(slug))
            scope.launch { FontDownloader.getTypeface(context, slug) }
        },
    )

    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        if (showTabBar) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabOrder.forEach { tab ->
                    val isSelected = activeTab == tab
                    val tabTitle = labelTabTitle(tab)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) tokens.accentSoft else Color(0xFFF5F5F5))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    if (tab == LabelEditTab.EDIT) {
                                        if (canvasFirstMode) {
                                            onLayoutEvent(EditorEvent.StartTextEdit(layer.id))
                                        } else {
                                            setActiveTab(LabelEditTab.EDIT)
                                            onLayoutEvent(EditorEvent.RequestTextEdit(layer.id))
                                        }
                                    } else {
                                        setActiveTab(tab)
                                    }
                                },
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            if (tab == LabelEditTab.EDIT) {
                                Icon(
                                    imageVector = Icons.Filled.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = tokens.textSecondary,
                                )
                            }
                            Text(
                                text = tabTitle,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) tokens.accent else tokens.textSecondary,
                            )
                        }
                    }
                }
            }
        }

        when (activeTab) {
            LabelEditTab.EDIT -> Unit
            LabelEditTab.LABEL -> Unit
            LabelEditTab.FONT -> {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = stringResource(R.string.studio_label_tab_font),
                        fontSize = 12.sp,
                        color = tokens.textSecondary,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                    FontFamilyChip(
                        fontFamily = layer.fontFamily ?: "Outfit",
                        tokens = tokens,
                        onClick = { showFontPicker = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            LabelEditTab.SIZE -> {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = stringResource(R.string.studio_label_tab_size),
                        fontSize = 12.sp,
                        color = tokens.textSecondary,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                    SizeControl(
                        layerId = layer.id,
                        value = LayerViewportScale.effectiveTextSizeSp(layer),
                        onValueChange = { onLayoutEvent(EditorEvent.UpdateTextSize(it)) },
                        sliderColors = sliderColors,
                        tokens = tokens,
                    )
                    if (layer.shapeType != ShapeType.TEXT_ONLY) {
                        Spacer(modifier = Modifier.height(4.dp))
                        ShapeSizeSection(
                            layer = layer,
                            tokens = tokens,
                            onLayoutEvent = onLayoutEvent,
                        )
                    }
                }
            }
            LabelEditTab.TEXT_STYLE -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.studio_label_document_styles),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = tokens.textSecondary,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )

                    val stylePresets = listOf(
                        Triple(stringResource(R.string.studio_label_style_title), presets[0], androidx.compose.ui.text.TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold)),
                        Triple(stringResource(R.string.studio_label_style_subtitle), presets[1], androidx.compose.ui.text.TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold)),
                        Triple(stringResource(R.string.studio_label_style_body), presets[2], androidx.compose.ui.text.TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal))
                    )

                    stylePresets.forEach { (title, preset, previewStyle) ->
                        val isSelected = layer.fontWeight == preset.fontWeight && layer.textSizeSp == preset.textSizeSp
                        val bgCol = if (isSelected) Color(0xFFF3F4F6) else Color.Transparent

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(bgCol)
                                .clickable {
                                    onLayoutEvent(
                                        EditorEvent.ApplyLabelTypographyPreset(
                                            preset.fontWeight,
                                            preset.textSizeSp,
                                            preset.textTransform
                                        )
                                    )
                                }
                                .padding(vertical = 10.dp, horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = tokens.textPrimary,
                                    modifier = Modifier
                                        .padding(end = 12.dp)
                                        .size(18.dp),
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .padding(end = 12.dp)
                                        .size(18.dp)
                                )
                            }

                            Text(
                                text = title,
                                style = previewStyle.copy(color = tokens.textPrimary),
                                modifier = Modifier.weight(1f),
                            )

                            Icon(
                                imageVector = Icons.Filled.ChevronRight,
                                contentDescription = null,
                                tint = tokens.textSecondary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
            LabelEditTab.FORMAT -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FormatToggle(
                        icon = Icons.Filled.FormatBold,
                        selected = isBold,
                        tokens = tokens,
                        onClick = { onLayoutEvent(EditorEvent.UpdateTextBold(!isBold)) },
                    )
                    FormatToggle(
                        icon = Icons.Filled.FormatItalic,
                        selected = isItalic,
                        tokens = tokens,
                        onClick = { onLayoutEvent(EditorEvent.UpdateTextItalic(!isItalic)) },
                    )
                    FormatToggle(
                        icon = Icons.Filled.FormatUnderlined,
                        selected = layer.underline,
                        tokens = tokens,
                        onClick = { onLayoutEvent(EditorEvent.UpdateTextUnderline(!layer.underline)) },
                    )
                    FormatToggle(
                        icon = Icons.Filled.FormatStrikethrough,
                        selected = layer.linethrough,
                        tokens = tokens,
                        onClick = { onLayoutEvent(EditorEvent.UpdateTextLinethrough(!layer.linethrough)) },
                    )
                }
            }
            LabelEditTab.ALIGN -> {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AlignToggle(
                            icon = Icons.AutoMirrored.Filled.FormatAlignLeft,
                            selected = currentAlign in setOf("left", "start"),
                            tokens = tokens,
                            onClick = { onLayoutEvent(EditorEvent.UpdateTextAlign("left")) },
                        )
                        AlignToggle(
                            icon = Icons.Filled.FormatAlignCenter,
                            selected = currentAlign == "center" || currentAlign == "justify",
                            tokens = tokens,
                            onClick = { onLayoutEvent(EditorEvent.UpdateTextAlign("center")) },
                        )
                        AlignToggle(
                            icon = Icons.AutoMirrored.Filled.FormatAlignRight,
                            selected = currentAlign in setOf("right", "end"),
                            tokens = tokens,
                            onClick = { onLayoutEvent(EditorEvent.UpdateTextAlign("right")) },
                        )

                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .width(1.dp)
                                .height(24.dp)
                                .background(tokens.borderSubtle)
                        )

                        PresetChip(
                            label = "T",
                            preset = presets[0],
                            tokens = tokens,
                            onClick = { onLayoutEvent(EditorEvent.ApplyLabelTypographyPreset(presets[0].fontWeight, presets[0].textSizeSp, presets[0].textTransform)) },
                        )
                        PresetChip(
                            label = "t",
                            fontSize = 13.sp,
                            preset = presets[2],
                            tokens = tokens,
                            onClick = { onLayoutEvent(EditorEvent.ApplyLabelTypographyPreset(presets[2].fontWeight, presets[2].textSizeSp, presets[2].textTransform)) },
                        )
                    }

                    val transformChips = listOf(
                        Triple(R.string.studio_label_transform_none, null, null),
                        Triple(R.string.studio_label_transform_upper, "uppercase", "uppercase"),
                        Triple(R.string.studio_label_transform_lower, "lowercase", "lowercase"),
                        Triple(R.string.studio_label_transform_capitalize, "capitalize", "capitalize"),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        transformChips.forEach { (labelRes, transform, _) ->
                            val isSelected = if (transform == null) {
                                currentTransform.isNullOrBlank() || currentTransform == "none"
                            } else {
                                currentTransform == transform
                            }
                            LabelChip(
                                label = stringResource(labelRes),
                                selected = isSelected,
                                tokens = tokens,
                                onClick = { onLayoutEvent(EditorEvent.UpdateTextTransform(transform)) },
                            )
                        }
                    }
                }
            }
            LabelEditTab.BG_COLOR -> {
                LabelGradientSection(
                    layer = layer,
                    tokens = tokens,
                    onLayoutEvent = onLayoutEvent,
                    showMode = LabelColorTab.Background,
                )
            }
            LabelEditTab.TEXT_COLOR -> {
                LabelGradientSection(
                    layer = layer,
                    tokens = tokens,
                    onLayoutEvent = onLayoutEvent,
                    showMode = LabelColorTab.Text,
                )
            }
            LabelEditTab.ELEVATION -> {
                if (layer.isLabelLayer && layer.supportsTextElevation) {
                    ShapeElevationSection(
                        appearance = layer.appearance,
                        fillColorArgb = layer.resolveTextElevationColorArgb(),
                        tokens = tokens,
                        onLayoutEvent = onLayoutEvent,
                        elevationTarget = ElevationTarget.TEXT,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.studio_label_elevation_requires_text),
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.textSecondary,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                    )
                }
            }
            LabelEditTab.TEXT_FORM -> {
                TextFormSection(
                    layer = layer,
                    tokens = tokens,
                    onLayoutEvent = onLayoutEvent,
                )
            }
            LabelEditTab.SHAPE -> {
                LabelShapeSection(
                    layer = layer,
                    editableShapes = editableShapes,
                    tokens = tokens,
                    onLayoutEvent = onLayoutEvent,
                )
            }
        }


    }
}

// ── Subcomponents ────────────────────────────────────────

@SuppressLint("UnrememberedMutableInteractionSource")
@Composable
private fun FontFamilyChip(
    fontFamily: String,
    tokens: EditorTokens,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFF5F5F5))
            .border(0.5.dp, tokens.borderSubtle, RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = MutableInteractionSource(),
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = fontFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = tokens.textPrimary,
                maxLines = 1,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "▼",
                fontSize = 8.sp,
                color = tokens.textSecondary,
            )
        }
    }
}

@Composable
private fun SizeControl(
    layerId: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    sliderColors: com.thgiang.image.core.design.components.PrecisionSliderColors,
    tokens: EditorTokens,
) {
    var localValue by remember(layerId) { mutableFloatStateOf(value) }
    var lastEmitted by remember(layerId) { mutableFloatStateOf(value) }

    LaunchedEffect(value, layerId) {
        if (kotlin.math.abs(value - lastEmitted) > 0.5f) {
            localValue = value
            lastEmitted = value
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFF5F5F5))
            .border(0.5.dp, tokens.borderSubtle, RoundedCornerShape(8.dp))
            .padding(start = 8.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
    ) {
        Text(
            text = "${localValue.toInt()}",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = tokens.textPrimary,
            modifier = Modifier.width(32.dp),
        )
        PrecisionSlider(
            label = "",
            value = localValue,
            valueRange = 1f..ShapeLabelDefaults.MAX_TEXT_SIZE_SP,
            onValueChange = {
                localValue = it
                lastEmitted = it
                onValueChange(it)
            },
            valueFormatter = { "${it.toInt()}px" },
            colors = sliderColors,
            modifier = Modifier.weight(1f),
        )
    }
}


@SuppressLint("UnrememberedMutableInteractionSource")
@Composable
private fun FormatToggle(
    icon: ImageVector,
    selected: Boolean,
    tokens: EditorTokens,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) tokens.accentSoft else Color.Transparent)
            .clickable(
                interactionSource = MutableInteractionSource(),
                indication = null,
                onClick = onClick,
            )
            .padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = if (selected) tokens.accent else tokens.textSecondary,
        )
    }
}

@Composable
private fun AlignToggle(
    icon: ImageVector,
    selected: Boolean,
    tokens: EditorTokens,
    onClick: () -> Unit,
) = FormatToggle(icon = icon, selected = selected, tokens = tokens, onClick = onClick)

@SuppressLint("UnrememberedMutableInteractionSource")
@Composable
private fun PresetChip(
    label: String,
    fontSize: androidx.compose.ui.unit.TextUnit = 16.sp,
    preset: TypographyPreset,
    tokens: EditorTokens,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFFF5F5F5))
            .clickable(
                interactionSource = MutableInteractionSource(),
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            color = tokens.textSecondary,
        )
    }
}
