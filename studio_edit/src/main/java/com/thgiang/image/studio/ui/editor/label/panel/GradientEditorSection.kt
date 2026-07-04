@file:Suppress("OPT_IN_USAGE", "OPT_IN_USAGE_ERROR")
package com.thgiang.image.studio.ui.editor.label.panel


import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thgiang.image.core.domain.model.template.CloudGradient
import com.thgiang.image.studio.ui.editor.EditorEvent
import com.thgiang.image.studio.ui.editor.mapper.EditorGradientMapper
import com.thgiang.image.studio.ui.editor.theme.EditorTokens
import io.mhssn.colorpicker.ColorPickerDialog
import io.mhssn.colorpicker.ColorPickerType

/**
 * Phase 10 — Multi-stop Gradient Editor
 * Visual gradient bar with 2 draggable color stops + color picker for each stop.
 * Angle can be set via preset buttons.
 */
@Composable
internal fun GradientEditorSection(
    color1Argb: Int,
    color2Argb: Int,
    stop1Offset: Float,
    stop2Offset: Float,
    angle: Float,
    tokens: EditorTokens,
    onGradientChanged: (CloudGradient) -> Unit,
) {
    var activeStopPicker by remember { mutableStateOf<String?>(null) }
    var barWidthPx by remember { mutableFloatStateOf(1f) }

    val color1 = Color(color1Argb)
    val color2 = Color(color2Argb)

    if (activeStopPicker != null) {
        ColorPickerDialog(
            show = true,
            type = ColorPickerType.Circle(showAlphaBar = false),
            onDismissRequest = { activeStopPicker = null },
            onPickedColor = { picked ->
                val argb = picked.toArgb()
                val newGradient = when (activeStopPicker) {
                    "stop1" -> EditorGradientMapper.buildLinearGradient(argb, color2Argb, angle, stop1Offset, stop2Offset)
                    "stop2" -> EditorGradientMapper.buildLinearGradient(color1Argb, argb, angle, stop1Offset, stop2Offset)
                    else -> null
                }
                newGradient?.let { onGradientChanged(it) }
                activeStopPicker = null
            },
        )
    }

    val angleRad = Math.toRadians(angle.toDouble())
    val cosA = kotlin.math.cos(angleRad).toFloat()
    val isReversed = cosA < 0f

    val previewColors = if (isReversed) listOf(color2, color1) else listOf(color1, color2)
    val handle1Pos = if (isReversed) 1f - stop1Offset else stop1Offset
    val handle2Pos = if (isReversed) 1f - stop2Offset else stop2Offset

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {

        // ── Visual gradient bar ───────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 12.dp),
        ) {
            // Gradient bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(14.dp))
                    .drawBehind {
                        drawRect(
                            brush = Brush.linearGradient(
                                colors = previewColors,
                                start = Offset(0f, size.height / 2f),
                                end = Offset(size.width, size.height / 2f),
                            )
                        )
                    }
                    .border(0.5.dp, tokens.borderSubtle, RoundedCornerShape(14.dp))
                    .onSizeChanged { barWidthPx = it.width.toFloat().coerceAtLeast(1f) },
            )

            // Stop 1 handle
            ColorStopHandle(
                color = color1,
                position = handle1Pos,
                barWidthPx = barWidthPx,
                modifier = Modifier.align(Alignment.CenterStart),
                onClick = { activeStopPicker = "stop1" },
                onDrag = { delta ->
                    val deltaOffset = delta / barWidthPx
                    val newOffset = if (isReversed) {
                        (stop1Offset - deltaOffset).coerceIn(0f, stop2Offset - 0.05f)
                    } else {
                        (stop1Offset + deltaOffset).coerceIn(0f, stop2Offset - 0.05f)
                    }
                    onGradientChanged(
                        EditorGradientMapper.buildLinearGradient(
                            color1Argb,
                            color2Argb,
                            angle,
                            stop1Offset = newOffset,
                            stop2Offset = stop2Offset
                        )
                    )
                },
            )

            // Stop 2 handle
            ColorStopHandle(
                color = color2,
                position = handle2Pos,
                barWidthPx = barWidthPx,
                modifier = Modifier.align(Alignment.CenterStart),
                onClick = { activeStopPicker = "stop2" },
                onDrag = { delta ->
                    val deltaOffset = delta / barWidthPx
                    val newOffset = if (isReversed) {
                        (stop2Offset - deltaOffset).coerceIn(stop1Offset + 0.05f, 1f)
                    } else {
                        (stop2Offset + deltaOffset).coerceIn(stop1Offset + 0.05f, 1f)
                    }
                    onGradientChanged(
                        EditorGradientMapper.buildLinearGradient(
                            color1Argb,
                            color2Argb,
                            angle,
                            stop1Offset = stop1Offset,
                            stop2Offset = newOffset
                        )
                    )
                },
            )
        }

        // ── Color swatch row ──────────────────────────────────────────────
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (isReversed) {
                ColorStopSwatch(
                    label = "Màu cuối",
                    color = color2,
                    tokens = tokens,
                    onClick = { activeStopPicker = "stop2" },
                )
                ColorStopSwatch(
                    label = "Màu đầu",
                    color = color1,
                    tokens = tokens,
                    onClick = { activeStopPicker = "stop1" },
                )
            } else {
                ColorStopSwatch(
                    label = "Màu đầu",
                    color = color1,
                    tokens = tokens,
                    onClick = { activeStopPicker = "stop1" },
                )
                ColorStopSwatch(
                    label = "Màu cuối",
                    color = color2,
                    tokens = tokens,
                    onClick = { activeStopPicker = "stop2" },
                )
            }
        }
    }
}

@Composable
private fun ColorStopHandle(
    color: Color,
    position: Float,
    barWidthPx: Float,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onDrag: (Float) -> Unit,
) {
    val offsetX = (position * barWidthPx).toInt() - 14  // center the 28dp handle
    val currentOnDrag by rememberUpdatedState(onDrag)
    Box(
        modifier = modifier
            .offset { IntOffset(offsetX, 0) }
            .size(28.dp)
            .clip(CircleShape)
            .background(color)
            .border(BorderStroke(2.dp, Color.White), CircleShape)
            .clickable(onClick = onClick)
            .pointerInput(Unit) {
                detectHorizontalDragGestures { _, dragAmount ->
                    currentOnDrag(dragAmount)
                }
            },
    )
}

@Composable
private fun ColorStopSwatch(
    label: String,
    color: Color,
    tokens: EditorTokens,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(color)
                .border(BorderStroke(0.5.dp, tokens.borderSubtle), CircleShape)
                .clickable(onClick = onClick),
        )
        Text(label, fontSize = 10.sp, color = tokens.textSecondary)
    }
}
