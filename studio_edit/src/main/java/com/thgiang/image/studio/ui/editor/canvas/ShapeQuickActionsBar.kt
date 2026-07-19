@file:Suppress("OPT_IN_USAGE", "OPT_IN_USAGE_ERROR")
package com.thgiang.image.studio.ui.editor.canvas

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FlipToBack
import androidx.compose.material.icons.filled.FlipToFront
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thgiang.image.studio.R
import com.thgiang.image.studio.ui.editor.EditorEvent
import com.thgiang.image.studio.ui.editor.model.EditorLayer
import com.thgiang.image.studio.ui.editor.theme.MotionTokens
import kotlinx.coroutines.launch

/**
 * Canva-style quick action mini toolbar shown above the selected shape on canvas.
 *
 * Shows: Collapsed FAB or Expanded toolbar: Duplicate | BringForward | SendBack | Lock/Unlock | Delete
 *
 * Position: rendered above the bounding box, centered horizontally.
 * Visibility: animated fade+slide when shape is selected/deselected.
 *
 * Expanded bar is width-capped to the screen; middle actions scroll horizontally
 * so long localized labels (e.g. German) stay reachable. Delete stays pinned.
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
    val screenWidthDp = LocalConfiguration.current.screenWidthDp.dp
    val maxBarWidth = screenWidthDp - 16.dp
    val actionsScrollState = rememberScrollState()
    val scrollScope = rememberCoroutineScope()
    val scrollStepPx = with(LocalDensity.current) { 72.dp.roundToPx() }

    val currentQuickActionsOffset by rememberUpdatedState(quickActionsOffset)
    val currentOnQuickActionsOffsetChange by rememberUpdatedState(onQuickActionsOffsetChange)

    AnimatedVisibility(
        visible = visible && layer != null,
        enter = fadeIn(MotionTokens.fadeDefault) + slideInVertically(MotionTokens.springPanel()) { -it / 2 },
        exit = fadeOut(MotionTokens.fadeQuick) + slideOutVertically(MotionTokens.springPanel()) { -it / 2 },
        modifier = modifier,
    ) {
        // wrapContentSize: never inherit full canvas constraints — expanded white
        // Surface must stay bar-sized (fillMaxHeight on scroll hints used to grow it).
        Box(
            modifier = Modifier
                .wrapContentSize(align = Alignment.TopCenter)
                .animateContentSize()
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
                        contentDescription = stringResource(R.string.studio_action_collapse),
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            } else {
                // Expanded State: width-capped toolbar; actions scroll; delete pinned.
                // Height must stay wrap-content — never fill the editor Box.
                Box(
                    modifier = Modifier
                        .wrapContentSize()
                        .widthIn(max = maxBarWidth)
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
                        // 6-Dot Drag Handle Icon (moves the floating bar)
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

                        // Scrollable action cluster with faint edge scroll hints.
                        // Size from the action Row only; hints use matchParentSize so they
                        // never expand to the editor's full maxHeight (white-wash bug).
                        Box(
                            modifier = Modifier.weight(1f, fill = false),
                        ) {
                            Row(
                                modifier = Modifier.horizontalScroll(actionsScrollState),
                                horizontalArrangement = Arrangement.spacedBy(0.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                QuickActionButton(
                                    icon = Icons.Default.KeyboardArrowLeft,
                                    label = stringResource(R.string.studio_action_collapse),
                                    tint = Color(0xFF424242),
                                    onClick = { isCollapsed = true },
                                )

                                QuickActionDivider()

                                QuickActionButton(
                                    icon = Icons.Default.ContentCopy,
                                    label = stringResource(R.string.studio_action_duplicate),
                                    tint = Color(0xFF424242),
                                    onClick = { onEvent(EditorEvent.DuplicateLayer) },
                                )

                                QuickActionDivider()

                                QuickActionButton(
                                    icon = Icons.Default.FlipToFront,
                                    label = stringResource(R.string.studio_action_bring_forward),
                                    tint = Color(0xFF424242),
                                    onClick = { onEvent(EditorEvent.MoveLayerUp) },
                                )

                                QuickActionButton(
                                    icon = Icons.Default.FlipToBack,
                                    label = stringResource(R.string.studio_action_send_backward),
                                    tint = Color(0xFF424242),
                                    onClick = { onEvent(EditorEvent.MoveLayerDown) },
                                )

                                QuickActionDivider()

                                if (layer != null) {
                                    val lockLabel = if (layer.isLocked)
                                        stringResource(R.string.studio_action_unlock)
                                    else
                                        stringResource(R.string.studio_action_lock)
                                    QuickActionButton(
                                        icon = if (layer.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                        label = lockLabel,
                                        tint = if (layer.isLocked) Color(0xFF1565C0) else Color(0xFF424242),
                                        onClick = { onEvent(EditorEvent.ToggleLayerLock) },
                                    )
                                }
                            }

                            val canScrollLeft = actionsScrollState.canScrollBackward
                            val canScrollRight = actionsScrollState.canScrollForward

                            Box(modifier = Modifier.matchParentSize()) {
                                // Fully-qualified: avoid RowScope.AnimatedVisibility from outer Row
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = canScrollLeft,
                                    enter = fadeIn(MotionTokens.fadeQuick),
                                    exit = fadeOut(MotionTokens.fadeQuick),
                                    modifier = Modifier.align(Alignment.CenterStart),
                                ) {
                                    ScrollHintArrow(
                                        icon = Icons.Default.KeyboardArrowLeft,
                                        alignStart = true,
                                        onClick = {
                                            scrollScope.launch {
                                                val target = (actionsScrollState.value - scrollStepPx)
                                                    .coerceAtLeast(0)
                                                actionsScrollState.animateScrollTo(target)
                                            }
                                        },
                                    )
                                }

                                androidx.compose.animation.AnimatedVisibility(
                                    visible = canScrollRight,
                                    enter = fadeIn(MotionTokens.fadeQuick),
                                    exit = fadeOut(MotionTokens.fadeQuick),
                                    modifier = Modifier.align(Alignment.CenterEnd),
                                ) {
                                    ScrollHintArrow(
                                        icon = Icons.Default.KeyboardArrowRight,
                                        alignStart = false,
                                        onClick = {
                                            scrollScope.launch {
                                                val target = (actionsScrollState.value + scrollStepPx)
                                                    .coerceAtMost(actionsScrollState.maxValue)
                                                actionsScrollState.animateScrollTo(target)
                                            }
                                        },
                                    )
                                }
                            }
                        }

                        QuickActionDivider()

                        // Delete always visible (pinned)
                        QuickActionButton(
                            icon = Icons.Default.Delete,
                            label = stringResource(R.string.studio_action_delete),
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
private fun ScrollHintArrow(
    icon: ImageVector,
    alignStart: Boolean,
    onClick: () -> Unit,
) {
    val fadeBrush = if (alignStart) {
        Brush.horizontalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.92f),
                Color.White.copy(alpha = 0f),
            )
        )
    } else {
        Brush.horizontalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0f),
                Color.White.copy(alpha = 0.92f),
            )
        )
    }
    Box(
        modifier = Modifier
            .width(18.dp)
            .height(44.dp)
            .background(fadeBrush)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFF9E9E9E).copy(alpha = 0.55f),
            modifier = Modifier.size(14.dp),
        )
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
        Column(
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
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 72.dp),
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

/**
 * Keeps the floating quick-actions bar inside [containerSize] when anchored top-center
 * with [anchorTopPx] padding from the top edge.
 */
fun clampQuickActionsOffset(
    offset: Offset,
    containerSize: IntSize,
    barSize: IntSize,
    anchorTopPx: Float,
    marginPx: Float,
): Offset {
    if (containerSize.width <= 0 || containerSize.height <= 0) return Offset.Zero

    val barW = barSize.width.toFloat().coerceAtLeast(1f)
    val barH = barSize.height.toFloat().coerceAtLeast(1f)
    val centerX = containerSize.width / 2f

    val minX = marginPx - centerX + barW / 2f
    val maxX = containerSize.width - marginPx - centerX - barW / 2f
    val minY = marginPx - anchorTopPx
    val maxY = containerSize.height - marginPx - anchorTopPx - barH

    return Offset(
        x = if (minX <= maxX) offset.x.coerceIn(minX, maxX) else 0f,
        y = if (minY <= maxY) offset.y.coerceIn(minY, maxY) else 0f,
    )
}
