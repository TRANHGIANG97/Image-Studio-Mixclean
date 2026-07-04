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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thgiang.image.core.design.components.PrecisionSlider
import com.thgiang.image.studio.ui.editor.EditorEvent
import com.thgiang.image.studio.ui.editor.model.EditorLayer
import com.thgiang.image.studio.ui.editor.panel.toSliderColors
import com.thgiang.image.studio.ui.editor.theme.EditorTokens

/** Preset snap angles như Word: 0°, 45°, 90°, 135°, 180°, 270° */
private val SNAP_ANGLE_PRESETS = listOf(0f, 45f, 90f, 135f, 180f, 270f)

@Composable
internal fun ShapeRotationSection(
    layer: EditorLayer,
    tokens: EditorTokens,
    onLayoutEvent: (EditorEvent) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val currentRotation = layer.viewport.rotation
    val sliderColors = remember(tokens) { tokens.toSliderColors() }

    var degreeInput by remember(layer.id, currentRotation) {
        mutableStateOf(currentRotation.toInt().toString())
    }

    fun commitDegree(input: String) {
        val deg = input.toFloatOrNull()?.coerceIn(0f, 360f) ?: return
        onLayoutEvent(EditorEvent.SetRotation(deg))
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

        // ── Slider xoay liên tục ──────────────────────────────────────────
        PrecisionSlider(
            label = "Góc xoay",
            value = currentRotation,
            valueRange = 0f..359f,
            onValueChange = { onLayoutEvent(EditorEvent.SetRotation(it)) },
            valueFormatter = { "${it.toInt()}°" },
            colors = sliderColors,
        )

        // ── Snap to angle preset buttons ──────────────────────────────────
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Snap góc nhanh",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = tokens.textSecondary,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SNAP_ANGLE_PRESETS.forEach { angle ->
                    val isSelected = kotlin.math.abs(currentRotation - angle) < 2f
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) tokens.accentSoft else Color(0xFFF5F5F5))
                            .border(
                                width = if (isSelected) 1.5.dp else 0.5.dp,
                                color = if (isSelected) tokens.accent else Color(0xFFE0E0E0),
                                shape = RoundedCornerShape(8.dp),
                            )
                            .clickable {
                                onLayoutEvent(EditorEvent.SetRotation(angle))
                                degreeInput = angle.toInt().toString()
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "${angle.toInt()}°",
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) tokens.accent else tokens.textSecondary,
                        )
                    }
                }
            }
        }

        // ── Numeric input chính xác ───────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = degreeInput,
                onValueChange = { input ->
                    val clean = input.filter { it.isDigit() }
                    degreeInput = clean
                },
                label = { Text("Nhập góc (0–360°)", fontSize = 11.sp) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        commitDegree(degreeInput)
                        focusManager.clearFocus()
                    }
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f),
            )

            // Nút RotateLeft (-45°)
            IconActionButton(
                icon = Icons.Default.RotateLeft,
                label = "-45°",
                tokens = tokens,
                onClick = {
                    val newDeg = ((currentRotation - 45f) + 360f) % 360f
                    onLayoutEvent(EditorEvent.SetRotation(newDeg))
                    degreeInput = newDeg.toInt().toString()
                },
            )

            // Nút RotateRight (+45°)
            IconActionButton(
                icon = Icons.Default.RotateRight,
                label = "+45°",
                tokens = tokens,
                onClick = {
                    val newDeg = (currentRotation + 45f) % 360f
                    onLayoutEvent(EditorEvent.SetRotation(newDeg))
                    degreeInput = newDeg.toInt().toString()
                },
            )
        }

        // ── Flip H / Flip V ───────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Flip Horizontal
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFF5F5F5))
                    .border(0.5.dp, Color(0xFFE0E0E0), RoundedCornerShape(10.dp))
                    .clickable { onLayoutEvent(EditorEvent.FlipHorizontal) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Flip,
                        contentDescription = null,
                        tint = tokens.textSecondary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "Lật ngang",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = tokens.textPrimary,
                    )
                }
            }

            // Flip Vertical
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFF5F5F5))
                    .border(0.5.dp, Color(0xFFE0E0E0), RoundedCornerShape(10.dp))
                    .clickable { onLayoutEvent(EditorEvent.FlipVertical) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Flip,
                        contentDescription = null,
                        tint = tokens.textSecondary,
                        modifier = Modifier
                            .size(16.dp)
                            .graphicsLayer { rotationZ = 90f },
                    )
                    Text(
                        text = "Lật dọc",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = tokens.textPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun IconActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tokens: EditorTokens,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFFF5F5F5))
                .border(0.5.dp, Color(0xFFE0E0E0), RoundedCornerShape(10.dp))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tokens.textPrimary,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(label, fontSize = 9.sp, color = tokens.textSecondary)
    }
}
