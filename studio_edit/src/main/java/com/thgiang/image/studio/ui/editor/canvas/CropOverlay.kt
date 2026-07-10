package com.thgiang.image.studio.ui.editor.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.thgiang.image.studio.ui.editor.model.EditorLayer
import kotlin.math.roundToInt

/**
 * Interactive crop overlay — rule-of-thirds grid + drag-to-pan (Phase 5).
 * Zero-confirmation: pan commits live; tap-away / tool switch persists via history.
 */
@Composable
fun CropOverlay(
    layer: EditorLayer,
    displayScale: Float,
    onCropPan: (Offset) -> Unit,
    onCropPanEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val cropSize = CropMath.cropWindowSize(layer)
    val displayW = with(density) { (cropSize.width * layer.viewport.scale * displayScale).roundToInt().toDp() }
    val displayH = with(density) { (cropSize.height * layer.viewport.scale * displayScale).roundToInt().toDp() }
    val handleInset = 12.dp
    val gridColor = Color.White.copy(alpha = 0.55f)
    val frameColor = Color.White

    Box(
        modifier = modifier
            .requiredSize(displayW + handleInset * 2, displayH + handleInset * 2)
            .pointerInput(layer.id, layer.cropOffsetX, layer.cropOffsetY) {
                detectDragGestures(
                    onDragEnd = { onCropPanEnd() },
                    onDragCancel = { onCropPanEnd() },
                ) { change, dragAmount ->
                    change.consume()
                    val templateDelta = Offset(
                        x = dragAmount.x / (displayScale * layer.viewport.scale),
                        y = dragAmount.y / (displayScale * layer.viewport.scale),
                    )
                    onCropPan(templateDelta)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.requiredSize(displayW, displayH)) {
            val w = size.width
            val h = size.height
            // Dim outside is handled by parent; draw crop frame + grid
            drawRect(
                color = frameColor,
                size = size,
                style = Stroke(width = 2.dp.toPx()),
            )
            val thirdW = w / 3f
            val thirdH = h / 3f
            for (i in 1..2) {
                drawLine(gridColor, Offset(thirdW * i, 0f), Offset(thirdW * i, h), strokeWidth = 1.dp.toPx())
                drawLine(gridColor, Offset(0f, thirdH * i), Offset(w, thirdH * i), strokeWidth = 1.dp.toPx())
            }
            // Corner handles (visual only — pan is on whole overlay)
            val handle = 10.dp.toPx()
            val corners = listOf(
                Offset(0f, 0f),
                Offset(w, 0f),
                Offset(w, h),
                Offset(0f, h),
            )
            corners.forEach { corner ->
                drawRect(
                    color = frameColor,
                    topLeft = Offset(corner.x - handle / 2f, corner.y - handle / 2f),
                    size = androidx.compose.ui.geometry.Size(handle, handle),
                )
            }
        }
    }
}
