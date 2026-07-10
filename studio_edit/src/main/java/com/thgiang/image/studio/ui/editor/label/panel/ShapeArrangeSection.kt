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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlignHorizontalCenter
import androidx.compose.material.icons.filled.AlignHorizontalLeft
import androidx.compose.material.icons.filled.AlignHorizontalRight
import androidx.compose.material.icons.filled.AlignVerticalBottom
import androidx.compose.material.icons.filled.AlignVerticalCenter
import androidx.compose.material.icons.filled.AlignVerticalTop
import androidx.compose.material.icons.filled.FlipToBack
import androidx.compose.material.icons.filled.FlipToFront
import androidx.compose.material.icons.filled.LayersClear
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thgiang.image.studio.ui.editor.EditorEvent
import com.thgiang.image.studio.ui.editor.canvas.LayerAlignment
import com.thgiang.image.studio.ui.editor.theme.EditorTokens

@Composable
fun ShapeArrangeSection(
    tokens: EditorTokens,
    onLayoutEvent: (EditorEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

        // ── Z-Order ───────────────────────────────────────────────────────
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Thứ tự lớp (Z-Order)",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = tokens.textSecondary,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ArrangeButton(
                    icon = Icons.Default.Layers,
                    label = "Lên trên cùng",
                    tokens = tokens,
                    modifier = Modifier.weight(1f),
                    onClick = { onLayoutEvent(EditorEvent.MoveLayerToTop) },
                )
                ArrangeButton(
                    icon = Icons.Default.FlipToFront,
                    label = "Lên 1 bậc",
                    tokens = tokens,
                    modifier = Modifier.weight(1f),
                    onClick = { onLayoutEvent(EditorEvent.MoveLayerUp) },
                )
                ArrangeButton(
                    icon = Icons.Default.FlipToBack,
                    label = "Xuống 1 bậc",
                    tokens = tokens,
                    modifier = Modifier.weight(1f),
                    onClick = { onLayoutEvent(EditorEvent.MoveLayerDown) },
                )
                ArrangeButton(
                    icon = Icons.Default.LayersClear,
                    label = "Xuống đáy",
                    tokens = tokens,
                    modifier = Modifier.weight(1f),
                    onClick = { onLayoutEvent(EditorEvent.MoveLayerToBottom) },
                )
            }
        }

        // ── Align Horizontal ──────────────────────────────────────────────
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Căn ngang theo canvas",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = tokens.textSecondary,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ArrangeButton(
                    icon = Icons.Default.AlignHorizontalLeft,
                    label = "Trái",
                    tokens = tokens,
                    modifier = Modifier.weight(1f),
                    onClick = { onLayoutEvent(EditorEvent.AlignLayer(LayerAlignment.LEFT)) },
                )
                ArrangeButton(
                    icon = Icons.Default.AlignHorizontalCenter,
                    label = "Giữa",
                    tokens = tokens,
                    modifier = Modifier.weight(1f),
                    onClick = { onLayoutEvent(EditorEvent.AlignLayer(LayerAlignment.CENTER_HORIZONTAL)) },
                )
                ArrangeButton(
                    icon = Icons.Default.AlignHorizontalRight,
                    label = "Phải",
                    tokens = tokens,
                    modifier = Modifier.weight(1f),
                    onClick = { onLayoutEvent(EditorEvent.AlignLayer(LayerAlignment.RIGHT)) },
                )
            }
        }

        // ── Align Vertical ────────────────────────────────────────────────
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Căn dọc theo canvas",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = tokens.textSecondary,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ArrangeButton(
                    icon = Icons.Default.AlignVerticalTop,
                    label = "Trên",
                    tokens = tokens,
                    modifier = Modifier.weight(1f),
                    onClick = { onLayoutEvent(EditorEvent.AlignLayer(LayerAlignment.TOP)) },
                )
                ArrangeButton(
                    icon = Icons.Default.AlignVerticalCenter,
                    label = "Giữa dọc",
                    tokens = tokens,
                    modifier = Modifier.weight(1f),
                    onClick = { onLayoutEvent(EditorEvent.AlignLayer(LayerAlignment.CENTER_VERTICAL)) },
                )
                ArrangeButton(
                    icon = Icons.Default.AlignVerticalBottom,
                    label = "Dưới",
                    tokens = tokens,
                    modifier = Modifier.weight(1f),
                    onClick = { onLayoutEvent(EditorEvent.AlignLayer(LayerAlignment.BOTTOM)) },
                )
            }
        }
    }
}

@Composable
private fun ArrangeButton(
    icon: ImageVector,
    label: String,
    tokens: EditorTokens,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFFF5F5F5))
                .border(0.5.dp, Color(0xFFE0E0E0), RoundedCornerShape(10.dp))
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tokens.textPrimary,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            color = tokens.textSecondary,
        )
    }
}
