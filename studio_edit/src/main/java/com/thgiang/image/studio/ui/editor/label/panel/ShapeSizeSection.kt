package com.thgiang.image.studio.ui.editor.label.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thgiang.image.core.design.components.PrecisionSlider
import com.thgiang.image.studio.ui.editor.EditorEvent
import com.thgiang.image.studio.ui.editor.model.EditorLayer
import com.thgiang.image.studio.ui.editor.panel.toSliderColors
import com.thgiang.image.studio.ui.editor.theme.EditorTokens

@Composable
fun ShapeSizeSection(
    layer: EditorLayer,
    tokens: EditorTokens,
    onLayoutEvent: (EditorEvent) -> Unit,
) {
    var widthInput by remember(layer.id, layer.shapeWidthPx) {
        mutableStateOf(layer.shapeWidthPx.toInt().toString())
    }
    var heightInput by remember(layer.id, layer.shapeHeightPx) {
        mutableStateOf(layer.shapeHeightPx.toInt().toString())
    }
    var lockAspectRatio by remember { mutableStateOf(true) }

    val baseAspectRatio = remember(layer.id) {
        if (layer.shapeHeightPx > 0f) layer.shapeWidthPx / layer.shapeHeightPx else 1f
    }

    val sliderColors = remember(tokens) { tokens.toSliderColors() }
    val currentAlpha = layer.appearance.alpha.coerceIn(0f, 1f)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // ── Vị trí X/Y (read-only hiển thị) ──────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "X",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = tokens.textSecondary,
                )
                Text(
                    text = layer.viewport.offset.x.toInt().toString(),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = tokens.textPrimary,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Y",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = tokens.textSecondary,
                )
                Text(
                    text = layer.viewport.offset.y.toInt().toString(),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = tokens.textPrimary,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Xoay",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = tokens.textSecondary,
                )
                Text(
                    text = "${layer.viewport.rotation.toInt()}°",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = tokens.textPrimary,
                )
            }
        }

        // ── W/H inputs ────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = widthInput,
                onValueChange = { input ->
                    val clean = input.filter { it.isDigit() }
                    widthInput = clean
                    val newW = clean.toFloatOrNull() ?: 0f
                    if (newW >= 30f) {
                        val newH = if (lockAspectRatio) (newW / baseAspectRatio) else (heightInput.toFloatOrNull() ?: layer.shapeHeightPx)
                        if (lockAspectRatio) heightInput = newH.toInt().toString()
                        onLayoutEvent(EditorEvent.SyncShapeSize(newW, newH))
                    }
                },
                label = { Text("Rộng (px)", fontSize = 11.sp) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f),
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (lockAspectRatio) tokens.accentSoft else Color(0xFFF5F5F5))
                    .border(
                        width = 0.5.dp,
                        color = if (lockAspectRatio) tokens.accent else Color(0xFFE0E0E0),
                        shape = RoundedCornerShape(8.dp),
                    )
                    .clickable { lockAspectRatio = !lockAspectRatio }
                    .padding(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (lockAspectRatio) Icons.Default.Link else Icons.Default.LinkOff,
                    contentDescription = null,
                    tint = if (lockAspectRatio) tokens.accent else Color(0xFF757575),
                    modifier = Modifier.size(20.dp),
                )
            }

            OutlinedTextField(
                value = heightInput,
                onValueChange = { input ->
                    val clean = input.filter { it.isDigit() }
                    heightInput = clean
                    val newH = clean.toFloatOrNull() ?: 0f
                    if (newH >= 20f) {
                        val newW = if (lockAspectRatio) (newH * baseAspectRatio) else (widthInput.toFloatOrNull() ?: layer.shapeWidthPx)
                        if (lockAspectRatio) widthInput = newW.toInt().toString()
                        onLayoutEvent(EditorEvent.SyncShapeSize(newW, newH))
                    }
                },
                label = { Text("Cao (px)", fontSize = 11.sp) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f),
            )
        }

        // ── Opacity slider ────────────────────────────────────────────────
        PrecisionSlider(
            label = "Độ mờ (Opacity)",
            value = currentAlpha * 100f,
            valueRange = 10f..100f,
            onValueChange = { pct ->
                onLayoutEvent(EditorEvent.UpdateAlpha(pct / 100f))
            },
            valueFormatter = { "${it.toInt()}%" },
            colors = sliderColors,
        )
    }
}
