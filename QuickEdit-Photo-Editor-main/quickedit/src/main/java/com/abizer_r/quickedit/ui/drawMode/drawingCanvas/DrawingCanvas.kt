package com.abizer_r.quickedit.ui.drawMode.drawingCanvas

import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.TransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.abizer_r.quickedit.ui.drawMode.stateHandling.DrawModeEvent
import com.abizer_r.quickedit.ui.drawMode.drawingCanvas.drawingTool.shapes.AbstractShape
import com.abizer_r.quickedit.ui.drawMode.drawingCanvas.models.PathDetails
import com.abizer_r.quickedit.ui.editorScreen.bottomToolbar.state.BottomToolbarItem
import com.abizer_r.quickedit.utils.drawMode.DrawModeUtils
import com.abizer_r.quickedit.utils.drawMode.getShape
import com.abizer_r.quickedit.utils.drawMode.getWidthOrNull
import com.abizer_r.quickedit.utils.drawMode.toPx
import com.abizer_r.quickedit.utils.other.bitmap.ImmutableBitmap
import java.util.Stack

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DrawingCanvas(
    modifier: Modifier = Modifier,
    pathDetailStack: Stack<PathDetails>,
    selectedColor: Color,
    currentTool: BottomToolbarItem,
    scale: Float,
    onDrawingEvent: (DrawModeEvent) -> Unit,
    transformableState: TransformableState,
    offset: Offset,
    immutableBitmap: ImmutableBitmap? = null,
    workingBitmap: android.graphics.Bitmap? = null,
    indicatorDistanceDp: androidx.compose.ui.unit.Dp = 50.dp
) {
    val density = LocalDensity.current
    val indicatorDistancePx = with(density) { indicatorDistanceDp.toPx() }
    val tipOffsetX = 0f
    val tipOffsetY = -indicatorDistancePx

    var fingerPos by remember { mutableStateOf(Offset.Unspecified) }
    var isTouching by remember { mutableStateOf(false) }

    var currentManualShape by remember { mutableStateOf<AbstractShape?>(null) }
    var drawPhaseTrigger by remember { mutableDoubleStateOf(0.0) }

    var canvasModifier = modifier
        .graphicsLayer(
            scaleX = scale,
            scaleY = scale,
            translationX = offset.x,
            translationY = offset.y
        )

    if (currentTool is BottomToolbarItem.PanItem) {
        canvasModifier = canvasModifier.transformable(transformableState)
    } else {
        canvasModifier = canvasModifier.pointerInteropFilter { motionEvent ->
            fingerPos = Offset(motionEvent.x, motionEvent.y)
            isTouching = motionEvent.action != MotionEvent.ACTION_UP && motionEvent.action != MotionEvent.ACTION_CANCEL

            when (currentTool) {

                else -> {
                    handleManualTouch(
                        event = motionEvent,
                        scale = scale,
                        selectedColor = selectedColor,
                        currentTool = currentTool,
                        tipOffsetX = tipOffsetX,
                        tipOffsetY = tipOffsetY,
                        onDrawingEvent = onDrawingEvent,
                        getCurrentShape = { currentManualShape },
                        updateCurrentShape = { currentManualShape = it },
                        triggerDraw = { drawPhaseTrigger += 0.1 }

                    )
                }
            }
            true
        }
    }

    Canvas(modifier = canvasModifier.clipToBounds()) {
        drawManualLayer(pathDetailStack, currentManualShape, drawPhaseTrigger > 0)
    }
}

private fun handleManualTouch(
    event: MotionEvent,
    scale: Float,
    selectedColor: Color,
    currentTool: BottomToolbarItem,
    tipOffsetX: Float,
    tipOffsetY: Float,
    onDrawingEvent: (DrawModeEvent) -> Unit,
    getCurrentShape: () -> AbstractShape?,
    updateCurrentShape: (AbstractShape?) -> Unit,
    triggerDraw: () -> Unit
) {
    val adjX = event.x / scale
    val adjY = event.y / scale

    when (event.action) {
        MotionEvent.ACTION_DOWN -> {
            val shape = currentTool.getShape(selectedColor, scale)
            shape.initShape(adjX, adjY)
            updateCurrentShape(shape)
        }

        MotionEvent.ACTION_MOVE -> {
            getCurrentShape()?.moveShape(adjX, adjY)
            triggerDraw()
        }
        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
            val shape = getCurrentShape()
            if (shape != null && shape.shouldDraw()) {
                onDrawingEvent(DrawModeEvent.AddNewPath(PathDetails(shape)))
            }
            updateCurrentShape(null)
        }
    }
}



private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawManualLayer(
    pathDetailStack: Stack<PathDetails>,
    currentShape: AbstractShape?,
    shouldDrawCurrent: Boolean
) {
    pathDetailStack.forEach { it.drawingShape.draw(this) }
    if (shouldDrawCurrent) {
        currentShape?.draw(this)
    }
}
