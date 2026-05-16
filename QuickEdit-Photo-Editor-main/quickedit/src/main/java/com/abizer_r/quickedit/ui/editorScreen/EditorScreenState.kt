package com.abizer_r.quickedit.ui.editorScreen

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable

@Immutable
data class EditorScreenState(
    val bitmapStack: List<Bitmap> = emptyList(),      // Đổi từ Stack sang List
    val bitmapRedoStack: List<Bitmap> = emptyList(),  // Đổi từ Stack sang List
    val recompositionTrigger: Long = 0,
    val showOverlay: Boolean = false
)