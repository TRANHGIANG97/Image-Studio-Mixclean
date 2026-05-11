package com.abizer_r.quickedit.ui.drawMode.drawingCanvas

import androidx.compose.ui.graphics.Color
import com.abizer_r.quickedit.ui.drawMode.drawingCanvas.drawingTool.DrawingTool
import com.abizer_r.quickedit.ui.drawMode.drawingCanvas.models.PathDetails

data class DrawingCanvasState (
    val strokeWidth: Int,
    val strokeColor: Color,
    val drawingTool: DrawingTool,
    val opacity: Int,
    val pathDetailStack: List<PathDetails>,
    val redoStack: List<PathDetails>
)