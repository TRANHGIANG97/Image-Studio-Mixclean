@file:Suppress("OPT_IN_USAGE", "OPT_IN_USAGE_ERROR")
package com.thgiang.image.studio.ui.editor.components.label

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thgiang.image.core.design.components.PrecisionSlider
import com.thgiang.image.studio.R
import com.thgiang.image.studio.ui.editor.EditorEvent
import com.thgiang.image.studio.ui.editor.EditorLayer
import com.thgiang.image.studio.ui.editor.EditorTextStyleMapper
import com.thgiang.image.studio.ui.editor.components.toSliderColors
import com.thgiang.image.studio.ui.editor.theme.EditorTokens

@Composable
internal fun LabelTypographySection(
    layer: EditorLayer,
    tokens: EditorTokens,
    onLayoutEvent: (EditorEvent) -> Unit,
) {
    val sliderColors = androidx.compose.runtime.remember(tokens) { tokens.toSliderColors() }
    val isBold = EditorTextStyleMapper.isBoldWeight(layer.fontWeight)
    val isItalic = EditorTextStyleMapper.isItalicStyle(layer.fontStyle)
    val currentAlign = layer.textAlign?.lowercase() ?: "center"
    val currentTransform = layer.textTransform?.lowercase()

    val presets = listOf(
        TypographyPreset(R.string.studio_label_preset_title, "bold", 80f, null),
        TypographyPreset(R.string.studio_label_preset_subtitle, "600", 60f, null),
        TypographyPreset(R.string.studio_label_preset_caption, "normal", 40f, null),
        TypographyPreset(R.string.studio_label_preset_uppercase, "bold", 54f, "uppercase"),
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(R.string.studio_label_format),
            style = MaterialTheme.typography.labelMedium,
            color = tokens.textSecondary,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
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

        Text(
            text = stringResource(R.string.studio_label_align),
            style = MaterialTheme.typography.labelMedium,
            color = tokens.textSecondary,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
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
        }

        Text(
            text = stringResource(R.string.studio_label_transform),
            style = MaterialTheme.typography.labelMedium,
            color = tokens.textSecondary,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LabelChip(
                label = stringResource(R.string.studio_label_transform_none),
                selected = currentTransform.isNullOrBlank() || currentTransform == "none",
                tokens = tokens,
                onClick = { onLayoutEvent(EditorEvent.UpdateTextTransform(null)) },
            )
            LabelChip(
                label = stringResource(R.string.studio_label_transform_upper),
                selected = currentTransform == "uppercase",
                tokens = tokens,
                onClick = { onLayoutEvent(EditorEvent.UpdateTextTransform("uppercase")) },
            )
            LabelChip(
                label = stringResource(R.string.studio_label_transform_lower),
                selected = currentTransform == "lowercase",
                tokens = tokens,
                onClick = { onLayoutEvent(EditorEvent.UpdateTextTransform("lowercase")) },
            )
            LabelChip(
                label = stringResource(R.string.studio_label_transform_capitalize),
                selected = currentTransform == "capitalize",
                tokens = tokens,
                onClick = { onLayoutEvent(EditorEvent.UpdateTextTransform("capitalize")) },
            )
        }

        PrecisionSlider(
            label = stringResource(R.string.studio_label_line_height),
            value = layer.lineHeight ?: 1f,
            valueRange = 0.8f..2.5f,
            onValueChange = { onLayoutEvent(EditorEvent.UpdateLineHeight(it)) },
            valueFormatter = { String.format("%.1fx", it) },
            colors = sliderColors,
        )
        PrecisionSlider(
            label = stringResource(R.string.studio_label_letter_spacing),
            value = layer.charSpacing,
            valueRange = -5f..40f,
            onValueChange = { onLayoutEvent(EditorEvent.UpdateCharSpacing(it)) },
            valueFormatter = { "${it.toInt()}px" },
            colors = sliderColors,
        )

        Text(
            text = stringResource(R.string.studio_label_presets),
            style = MaterialTheme.typography.labelMedium,
            color = tokens.textSecondary,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            presets.forEach { preset ->
                LabelChip(
                    label = stringResource(preset.labelRes),
                    selected = false,
                    tokens = tokens,
                    onClick = {
                        onLayoutEvent(
                            EditorEvent.ApplyLabelTypographyPreset(
                                fontWeight = preset.fontWeight,
                                textSizeSp = preset.textSizeSp,
                                textTransform = preset.textTransform,
                            ),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun FormatToggle(
    icon: ImageVector,
    selected: Boolean,
    tokens: EditorTokens,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) tokens.accentSoft else Color(0xFFF5F5F5))
            .border(
                width = if (selected) 2.dp else 0.5.dp,
                color = if (selected) tokens.accent else tokens.borderSubtle,
                shape = RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
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
