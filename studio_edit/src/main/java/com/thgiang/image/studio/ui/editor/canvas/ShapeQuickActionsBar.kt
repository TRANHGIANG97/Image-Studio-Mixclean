@file:Suppress("OPT_IN_USAGE", "OPT_IN_USAGE_ERROR")
package com.thgiang.image.studio.ui.editor.canvas

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FlipToBack
import androidx.compose.material.icons.filled.FlipToFront
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import com.thgiang.image.studio.ui.editor.EditorEvent
import com.thgiang.image.studio.ui.editor.model.EditorLayer

import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity

/**
 * Canva-style quick action mini toolbar shown above the selected shape on canvas.
 *
 * Shows: Collapsed FAB or Expanded toolbar: Duplicate | BringForward | SendBack | Lock/Unlock | Delete
 *
 * Position: rendered above the bounding box, centered horizontally.
 * Visibility: animated fade+slide when shape is selected/deselected.
 */
@Composable
fun ShapeQuickActionsBar(
    layer: EditorLayer?,
    visible: Boolean,
    onEvent: (EditorEvent) -> Unit,
    quickActionsOffset: Offset,
    onQuickActionsOffsetChange: (Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isCollapsed by remember(layer?.id) { mutableStateOf(false) }
    val density = LocalDensity.current

    val currentQuickActionsOffset by rememberUpdatedState(quickActionsOffset)
    val currentOnQuickActionsOffsetChange by rememberUpdatedState(onQuickActionsOffsetChange)

    AnimatedVisibility(
        visible = visible && layer != null,
        enter = fadeIn(tween(160)) + slideInVertically(tween(200)) { -it / 2 },
        exit = fadeOut(tween(120)) + slideOutVertically(tween(160)) { -it / 2 },
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier.animateContentSize()
        ) {
            if (isCollapsed) {
                // Collapsed State: A single prominent circular FAB button (draggable & clickable)
                var totalDrag by remember { mutableStateOf(Offset.Zero) }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .shadow(
                            elevation = 8.dp,
                            shape = CircleShape,
                            ambientColor = Color.Black.copy(alpha = 0.20f),
                            spotColor = Color.Black.copy(alpha = 0.30f),
                        )
                        .clip(CircleShape)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF6366F1), // Indigo
                                    Color(0xFF8B5CF6)  // Violet
                                )
                            )
                        )
                        .border(1.5.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { totalDrag = Offset.Zero },
                                onDragEnd = {},
                                onDragCancel = {},
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    totalDrag += dragAmount
                                    currentOnQuickActionsOffsetChange(
                                        Offset(
                                            x = currentQuickActionsOffset.x + dragAmount.x,
                                            y = currentQuickActionsOffset.y + dragAmount.y
                                        )
                                    )
                                }
                            )
                        }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            isCollapsed = false
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Mở rộng",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            } else {
                // Expanded State: Horizontal action toolbar row (with a 6-dot drag handle on the left)
                Box(
                    modifier = Modifier
                        .shadow(
                            elevation = 6.dp,
                            shape = RoundedCornerShape(12.dp),
                            ambientColor = Color.Black.copy(alpha = 0.12f),
                            spotColor = Color.Black.copy(alpha = 0.08f),
                        )
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.95f))
                        .border(0.5.dp, Color(0xFFE8E8E8), RoundedCornerShape(12.dp)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(0.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 6-Dot Drag Handle Icon (Safe & Standard drag trigger)
                        Box(
                            modifier = Modifier
                                .padding(start = 6.dp, end = 2.dp)
                                .size(width = 16.dp, height = 32.dp)
                                .pointerInput(Unit) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        currentOnQuickActionsOffsetChange(
                                            Offset(
                                                x = currentQuickActionsOffset.x + dragAmount.x,
                                                y = currentQuickActionsOffset.y + dragAmount.y
                                            )
                                        )
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val cx = size.width / 2f
                                val cy = size.height / 2f
                                val dotRadius = 1.2.dp.toPx()
                                val spacing = 4.dp.toPx()
                                val color = Color(0xFF9E9E9E)
                                drawCircle(color, dotRadius, Offset(cx - 2.dp.toPx(), cy - spacing))
                                drawCircle(color, dotRadius, Offset(cx - 2.dp.toPx(), cy))
                                drawCircle(color, dotRadius, Offset(cx - 2.dp.toPx(), cy + spacing))
                                drawCircle(color, dotRadius, Offset(cx + 2.dp.toPx(), cy - spacing))
                                drawCircle(color, dotRadius, Offset(cx + 2.dp.toPx(), cy))
                                drawCircle(color, dotRadius, Offset(cx + 2.dp.toPx(), cy + spacing))
                            }
                        }

                        // Collapse Trigger Action Button
                        QuickActionButton(
                            icon = Icons.Default.KeyboardArrowLeft,
                            label = "Thu gọn",
                            tint = Color(0xFF424242),
                            onClick = { isCollapsed = true },
                        )

                        QuickActionDivider()

                        // Duplicate
                        QuickActionButton(
                            icon = Icons.Default.ContentCopy,
                            label = "Sao chép",
                            tint = Color(0xFF424242),
                            onClick = { onEvent(EditorEvent.DuplicateLayer) },
                        )

                        QuickActionDivider()

                        // Bring Forward (Lên trên)
                        QuickActionButton(
                            icon = Icons.Default.FlipToFront,
                            label = "Lên trên",
                            tint = Color(0xFF424242),
                            onClick = { onEvent(EditorEvent.MoveLayerUp) },
                        )

                        // Send Backward (Xuống dưới)
                        QuickActionButton(
                            icon = Icons.Default.FlipToBack,
                            label = "Xuống dưới",
                            tint = Color(0xFF424242),
                            onClick = { onEvent(EditorEvent.MoveLayerDown) },
                        )

                        QuickActionDivider()

                        // Lock / Unlock
                        if (layer != null) {
                            QuickActionButton(
                                icon = if (layer.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                label = if (layer.isLocked) "Mở khóa" else "Khóa",
                                tint = if (layer.isLocked) Color(0xFF1565C0) else Color(0xFF424242),
                                onClick = { onEvent(EditorEvent.ToggleLayerLock) },
                            )
                            QuickActionDivider()
                        }

                        // Delete
                        QuickActionButton(
                            icon = Icons.Default.Delete,
                            label = "Xóa",
                            tint = Color(0xFFE53935),
                            onClick = { onEvent(EditorEvent.DeleteLayer) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickActionButton(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = label,
                fontSize = 8.sp,
                fontWeight = FontWeight.Medium,
                color = tint,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun QuickActionDivider() {
    Box(
        modifier = Modifier
            .width(0.5.dp)
            .height(28.dp)
            .background(Color(0xFFEEEEEE)),
    )
}
