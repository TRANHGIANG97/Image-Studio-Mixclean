package com.abizer_r.quickedit.utils.drawMode

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.abizer_r.quickedit.ui.drawMode.drawingCanvas.drawingTool.shapes.BrushShape
import com.abizer_r.quickedit.ui.drawMode.drawingCanvas.drawingTool.shapes.AbstractShape
import com.abizer_r.quickedit.ui.drawMode.drawingCanvas.drawingTool.shapes.LineShape
import com.abizer_r.quickedit.ui.drawMode.drawingCanvas.drawingTool.shapes.OvalShape
import com.abizer_r.quickedit.ui.drawMode.drawingCanvas.drawingTool.shapes.RectangleShape
import com.abizer_r.quickedit.ui.drawMode.drawingCanvas.drawingTool.shapes.ShapeType
import com.abizer_r.quickedit.ui.editorScreen.bottomToolbar.state.BottomToolbarItem
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.max
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect


object DrawModeUtils {

    const val DEFAULT_SELECTED_INDEX = 2

    fun getDefaultBottomToolbarItemsList(): ArrayList<BottomToolbarItem> {
        return arrayListOf(
            BottomToolbarItem.ColorItem,
            BottomToolbarItem.PanItem,
            BottomToolbarItem.BrushTool(
                width = DrawingConstants.DEFAULT_STROKE_WIDTH,
                opacity = DrawingConstants.DEFAULT_STROKE_OPACITY
            ),
            BottomToolbarItem.ShapeTool(
                width = DrawingConstants.DEFAULT_STROKE_WIDTH,
                opacity = DrawingConstants.DEFAULT_STROKE_OPACITY,
                shapeType = ShapeType.LINE
            ),
        )
    }

    /**
     * This function uses trigonometric functions to compute the new offset values after rotation.
     * new_x = x * cos(θ) - y * sin(θ)
     * new_y = x * sin(θ) + y * cos(θ)
     */
    fun rotateOffset(
        offset: Offset,
        angleDegrees: Float
    ): Offset {
        val angleRadians = Math.toRadians(angleDegrees.toDouble())
        val cosAngle = cos(angleRadians)
        val sinAngle = sin(angleRadians)
        val x = offset.x * cosAngle - offset.y * sinAngle
        val y = offset.x * sinAngle + offset.y * cosAngle
        return Offset(x.toFloat(), y.toFloat())
    }


    private const val MAX_BLOCK_SIZE_AUTO = 256

    
}




fun BottomToolbarItem.getShape(
    selectedColor: Color,
    scale: Float = 1f
): AbstractShape {

    return when (val toolbarItem = this) {
        is BottomToolbarItem.BrushTool -> {
            BrushShape(
                color = selectedColor,
                width = toolbarItem.width / scale,
                alpha = toolbarItem.opacity / 100f
            )
        }

        is BottomToolbarItem.ShapeTool -> when (toolbarItem.shapeType) {
            ShapeType.LINE -> LineShape(
                color = selectedColor,
                width = toolbarItem.width / scale,
                alpha = toolbarItem.opacity / 100f
            )

            ShapeType.OVAL -> OvalShape(
                color = selectedColor,
                width = toolbarItem.width / scale,
                alpha = toolbarItem.opacity / 100f
            )

            ShapeType.RECTANGLE -> RectangleShape(
                color = selectedColor,
                width = toolbarItem.width / scale,
                alpha = toolbarItem.opacity / 100f
            )
        }

        else -> {
            val isEraser = toolbarItem is BottomToolbarItem.EraserTool
            val isMosaic = toolbarItem is BottomToolbarItem.MosaicTool
            val width = when (toolbarItem) {
                is BottomToolbarItem.EraserTool -> toolbarItem.width
                is BottomToolbarItem.MosaicTool -> toolbarItem.width
                else -> DrawingConstants.DEFAULT_STROKE_WIDTH
            }
            BrushShape(
                isEraser = isEraser,
                isMosaic = isMosaic,
                width = width / scale
            )

        }
    }
}

fun BottomToolbarItem.getWidthOrNull(): Float? {
    return when (this) {
        is BottomToolbarItem.BrushTool -> this.width
        is BottomToolbarItem.EraserTool -> this.width
        is BottomToolbarItem.MosaicTool -> this.width
        is BottomToolbarItem.ShapeTool -> this.width

        else -> null
    }
}

fun BottomToolbarItem.setWidthIfPossible(mWidth: Float): BottomToolbarItem {
    when (this) {
        is BottomToolbarItem.BrushTool -> this.width = mWidth
        is BottomToolbarItem.EraserTool -> this.width = mWidth
        is BottomToolbarItem.MosaicTool -> this.width = mWidth
        is BottomToolbarItem.ShapeTool -> this.width = mWidth

        else -> {}
    }
    return this
}

fun BottomToolbarItem.getOpacityOrNull(): Float? {
    return when (this) {
        is BottomToolbarItem.BrushTool -> this.opacity
        is BottomToolbarItem.ShapeTool -> this.opacity
        else -> null
    }
}

fun BottomToolbarItem.setOpacityIfPossible(mOpacity: Float): BottomToolbarItem {
    when (this) {
        is BottomToolbarItem.BrushTool -> this.opacity = mOpacity
        is BottomToolbarItem.ShapeTool -> this.opacity = mOpacity
        else -> {}
    }
    return this
}

fun BottomToolbarItem.getToleranceOrNull(): Float? {
    return when (this) {

        else -> null
    }
}

fun BottomToolbarItem.setToleranceIfPossible(mTolerance: Float): BottomToolbarItem {
    when (this) {

        else -> {}
    }
    return this
}

fun BottomToolbarItem.getShapeTypeOrNull(): ShapeType? {
    return when (this) {
        is BottomToolbarItem.ShapeTool -> this.shapeType
        else -> null
    }
}

fun BottomToolbarItem.setShapeTypeIfPossible(mShapeType: ShapeType): BottomToolbarItem {
    when (this) {
        is BottomToolbarItem.ShapeTool -> this.shapeType = mShapeType
        else -> {}
    }
    return this
}

@Composable
fun Dp.toPx(): Float {
    return with(LocalDensity.current) {
        this@toPx.toPx()
    }
}

@Composable
fun Float.pxToDp(): Dp {
    return with(LocalDensity.current) {
        this@pxToDp.toDp()
    }
}