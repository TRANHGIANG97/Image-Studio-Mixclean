package com.abizer_r.quickedit.ui.drawMode.stateHandling

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import com.abizer_r.quickedit.ui.drawMode.drawingCanvas.models.PathDetails
import com.abizer_r.quickedit.ui.editorScreen.bottomToolbar.state.BottomToolbarItem

sealed class EditingAction {
    data class ManualPath(val path: PathDetails) : EditingAction()

}

data class DrawModeState(
    val showColorPicker: Boolean = false,
    val selectedColor: Color = Color.White,
    val selectedTool: BottomToolbarItem = BottomToolbarItem.NONE,
    val showBottomToolbarExtension: Boolean = false,
    
    // Unified History - sử dụng List thay cho Stack để tránh ClassCastException khi lưu/phục hồi state
    val historyStack: List<EditingAction> = emptyList(),
    val redoStack: List<EditingAction> = emptyList(),
    
    // Render list - derived từ historyStack
    val pathDetailStack: List<PathDetails> = emptyList(),
    
    val workingBitmap: Bitmap? = null,
    val recompositionTrigger: Long = 0
)