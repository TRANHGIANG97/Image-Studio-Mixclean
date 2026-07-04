@file:Suppress("OPT_IN_USAGE", "OPT_IN_USAGE_ERROR")
package com.thgiang.image.studio.ui.editor.label.panel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thgiang.image.core.design.components.PrecisionSlider
import com.thgiang.image.studio.R
import com.thgiang.image.studio.ui.editor.EditorEvent
import com.thgiang.image.studio.ui.editor.mapper.TextFormLayoutEngine
import com.thgiang.image.studio.ui.editor.model.EditorLayer
import com.thgiang.image.studio.ui.editor.model.TextFormPreset
import com.thgiang.image.studio.ui.editor.panel.toSliderColors
import com.thgiang.image.studio.ui.editor.theme.EditorTokens

private val TextFormCardWidth = 48.dp
private val TextFormPreviewSize = 34.dp

@Composable
internal fun TextFormSection(
    layer: EditorLayer,
    tokens: EditorTokens,
    onLayoutEvent: (EditorEvent) -> Unit,
) {
    val sliderColors = remember(tokens) { tokens.toSliderColors() }
    val activePreset = layer.textForm.preset
    val showAmount = activePreset != TextFormPreset.NONE

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 2.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            textFormAllPresets.forEach { item ->
                TextFormPresetTile(
                    item = item,
                    selected = activePreset == item.preset,
                    tokens = tokens,
                    onClick = {
                        if (item.preset == TextFormPreset.NONE) {
                            onLayoutEvent(EditorEvent.ResetTextForm)
                        } else {
                            onLayoutEvent(EditorEvent.ApplyTextFormPreset(item.preset))
                        }
                    },
                )
            }
        }

        if (showAmount) {
            PrecisionSlider(
                label = stringResource(R.string.studio_text_form_amount),
                value = layer.textForm.amount * 100f,
                onValueChange = { onLayoutEvent(EditorEvent.UpdateTextFormAmount(it / 100f)) },
                valueRange = 0f..100f,
                valueFormatter = { "${it.toInt()}%" },
                colors = sliderColors,
                isCompact = true,
            )
        }
    }
}

@Composable
private fun TextFormPresetTile(
    item: TextFormPresetItem,
    selected: Boolean,
    tokens: EditorTokens,
    onClick: () -> Unit,
) {
    val borderColor = if (selected) tokens.accent else tokens.borderSubtle
    val bgColor = if (selected) tokens.accentSoft else Color(0xFFF5F5F5)

    Column(
        modifier = Modifier
            .width(TextFormCardWidth)
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 2.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(TextFormPreviewSize)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            if (item.preset == TextFormPreset.NONE) {
                Text(
                    text = "abc",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF333333),
                )
            } else {
                TextFormPreviewIcon(
                    preset = item.preset,
                    amount = item.defaultAmount,
                )
            }
        }
        Text(
            text = stringResource(item.labelRes),
            fontSize = 8.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (selected) tokens.accent else tokens.textSecondary,
            modifier = Modifier.padding(top = 1.dp),
        )
    }
}

@Composable
private fun TextFormPreviewIcon(
    preset: TextFormPreset,
    amount: Float,
) {
    Canvas(modifier = Modifier.size(TextFormPreviewSize)) {
        val glyphs = TextFormLayoutEngine.computePreviewGlyphs(
            preset = preset,
            amount = amount,
            iconWidth = size.width,
            iconHeight = size.height,
        )
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#333333")
            textSize = size.height * 0.24f
            textAlign = android.graphics.Paint.Align.CENTER
        }
        drawContext.canvas.nativeCanvas.apply {
            glyphs.forEach { spec ->
                save()
                translate(spec.x, spec.y)
                rotate(spec.rotationDeg)
                drawText(spec.char, 0f, 0f, paint)
                restore()
            }
        }
    }
}
