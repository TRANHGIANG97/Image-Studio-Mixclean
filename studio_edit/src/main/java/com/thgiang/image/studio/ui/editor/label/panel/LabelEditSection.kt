@file:Suppress("OPT_IN_USAGE", "OPT_IN_USAGE_ERROR")
package com.thgiang.image.studio.ui.editor.label.panel
import com.thgiang.image.studio.ui.editor.label.geometry.*
import com.thgiang.image.studio.ui.editor.label.panel.*
import com.thgiang.image.studio.ui.editor.panel.*
import com.thgiang.image.studio.ui.editor.mapper.*

import android.annotation.SuppressLint
import com.thgiang.image.studio.ui.editor.model.*
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatAlignRight
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.FormatUnderlined
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

internal enum class LabelEditTab {
    LABEL,      // Nhãn
    FONT,       // Font
    SIZE,       // Size
    FORMAT,     // Định dạng
    ALIGN,      // Căn lề
    BG_COLOR,   // Màu nền
    TEXT_COLOR, // Màu chữ
    SHAPE       // Hình dạng
}

// ── Main Section ────────────────────────────────────────

/**
 * Tab ordering inspired by Microsoft Word:
 * - [labelTextFirstTabs]:  Text-first (Label tool) — LABEL, FONT, SIZE, FORMAT, ALIGN, TEXT_COLOR
 * - [labelShapeFirstTabs]: Shape-first (Shape tool) — SHAPE, BG_COLOR, TEXT_COLOR, then text tabs
 * - [LabelEditTab.values()]: Default (all 8 tabs in enum order)
 */
internal val labelTextFirstTabs: List<LabelEditTab> = listOf(
    LabelEditTab.LABEL,
    LabelEditTab.FONT,
    LabelEditTab.SIZE,
    LabelEditTab.FORMAT,
    LabelEditTab.ALIGN,
    LabelEditTab.TEXT_COLOR,
)

internal val labelShapeFirstTabs: List<LabelEditTab> = listOf(
    LabelEditTab.SHAPE,
    LabelEditTab.BG_COLOR,
    LabelEditTab.TEXT_COLOR,
    LabelEditTab.LABEL,
    LabelEditTab.FONT,
    LabelEditTab.SIZE,
    LabelEditTab.FORMAT,
    LabelEditTab.ALIGN,
)

@SuppressLint("UnrememberedMutableInteractionSource")
@Composable
internal fun LabelEditSection(
    layer: EditorLayer,
    tokens: EditorTokens,
    onLayoutEvent: (EditorEvent) -> Unit,
    tabOrder: List<LabelEditTab> = LabelEditTab.values().toList(),
) {
    var textDraft by remember(layer.id) { mutableStateOf(layer.text) }
    LaunchedEffect(layer.text) {
        textDraft = layer.text
    }
    var showFontPicker by remember { mutableStateOf(false) }
    var manifestFonts by remember { mutableStateOf<List<FontManifestEntry>>(emptyList()) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val sliderColors = remember(tokens) { tokens.toSliderColors() }

    var activeTab by rememberSaveable { mutableStateOf(LabelEditTab.LABEL) }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

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
        // Horizontal Scrollable Tab Bar
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
                val tabTitle = when (tab) {
                    LabelEditTab.LABEL -> "Nhãn"
                    LabelEditTab.FONT -> "Font"
                    LabelEditTab.SIZE -> "Size"
                    LabelEditTab.FORMAT -> "Định dạng"
                    LabelEditTab.ALIGN -> "Căn lề"
                    LabelEditTab.BG_COLOR -> "Màu nền"
                    LabelEditTab.TEXT_COLOR -> "Màu chữ"
                    LabelEditTab.SHAPE -> "Hình dạng"
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) tokens.accentSoft else Color(0xFFF5F5F5))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { activeTab = tab }
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tabTitle,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) tokens.accent else tokens.textSecondary
                    )
                }
            }
        }

        // Tab Contents (enforcing 3.dp vertical spacing)
        when (activeTab) {
            LabelEditTab.LABEL -> {
                OutlinedTextField(
                    value = textDraft,
                    onValueChange = {
                        textDraft = it
                        onLayoutEvent(EditorEvent.UpdateShapeText(it))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.studio_label_text_placeholder)) },
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            onLayoutEvent(EditorEvent.UpdateShapeText(textDraft))
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        },
                    ),
                )
            }
            LabelEditTab.FONT -> {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = "Font",
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
                        text = "Size",
                        fontSize = 12.sp,
                        color = tokens.textSecondary,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                    SizeControl(
                        layerId = layer.id,
                        value = layer.textSizeSp,
                        onValueChange = { onLayoutEvent(EditorEvent.UpdateTextSize(it)) },
                        sliderColors = sliderColors,
                        tokens = tokens,
                    )
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
            valueRange = 1f..500f,
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
