package com.abizer_r.quickedit.ui.editorScreen

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable

@Immutable
data class EditorScreenState(
    val bitmapStack: List<String> = emptyList(),      // Lưu đường dẫn file tạm
    val bitmapRedoStack: List<String> = emptyList(),  // Lưu đường dẫn file tạm
    val recompositionTrigger: Long = 0,
    val showOverlay: Boolean = false
)