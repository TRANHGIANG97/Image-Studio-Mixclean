package com.abizer_r.quickedit.ui.drawMode.stateHandling

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import com.abizer_r.quickedit.ui.drawMode.drawingCanvas.models.PathDetails
import com.abizer_r.quickedit.ui.editorScreen.bottomToolbar.state.BottomToolbarItem
import java.util.Stack

sealed class EditingAction {
    data class ManualPath(val path: PathDetails) : EditingAction()

}

data class DrawModeState(
    val showColorPicker: Boolean = false,
    val selectedColor: Color = Color.White,
    val selectedTool: BottomToolbarItem = BottomToolbarItem.NONE,
    val showBottomToolbarExtension: Boolean = false,
    
    // Unified History - chỉ dùng 1 stack
    val historyStack: Stack<EditingAction> = Stack(),
    val redoStack: Stack<EditingAction> = Stack(),
    
    // Render list - derived từ historyStack
    val pathDetailStack: List<PathDetails> = emptyList(),
    
    val workingBitmap: Bitmap? = null,
    val recompositionTrigger: Long = 0
)