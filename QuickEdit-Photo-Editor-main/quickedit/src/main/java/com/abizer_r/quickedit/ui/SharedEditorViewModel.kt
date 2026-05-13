package com.abizer_r.quickedit.ui

import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.abizer_r.quickedit.ui.editorScreen.EditorScreenState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class SharedEditorViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle
): ViewModel() {

    companion object {
        const val MAX_BITMAP_STACK_SIZE = 10  // Giới hạn tối đa
        const val MAX_BITMAP_DIMENSION = 2048 // Resize xuống nếu quá lớn
    }

    var useTransition = false

    private val _bitmapStack = mutableListOf<Bitmap>()
    private val _bitmapRedoStack = mutableListOf<Bitmap>()
    
    // Expose as read-only
    val bitmapStack: List<Bitmap> get() = _bitmapStack.toList()
    val bitmapRedoStack: List<Bitmap> get() = _bitmapRedoStack.toList()

    private val _recompositionTrigger = MutableStateFlow<Long>(0)
    val recompositionTrigger: StateFlow<Long> = _recompositionTrigger

    private var latestTimeForAddingBitmapToStack: Long = 0

    @Throws(Exception::class)
    fun getCurrentBitmap(): Bitmap {
        if (_bitmapStack.isEmpty()) {
            throw Exception("EmptyStackException: The bitmapStack should contain at least one bitmap")
        }
        return _bitmapStack.last()
    }

    fun resetStacks() {
        _bitmapStack.clear()
        _bitmapRedoStack.clear()
    }

    fun addBitmapToStack(
        bitmap: Bitmap,
        triggerRecomposition: Boolean = false,
        addSafelyWithoutMultipleTriggers: Boolean = true
    ) {
        val currTime = System.currentTimeMillis()
        if (addSafelyWithoutMultipleTriggers) {
            val timeDiff = currTime - latestTimeForAddingBitmapToStack
            if (timeDiff < 1000) return
        }
        latestTimeForAddingBitmapToStack = currTime

        // Resize nếu bitmap quá lớn
        val optimizedBitmap = optimizeBitmap(bitmap)
        
        // Xóa redo stack khi có action mới
        _bitmapRedoStack.forEach { recycleSafely(it) }
        _bitmapRedoStack.clear()

        _bitmapStack.add(optimizedBitmap)
        
        // Giới hạn stack size - xóa oldest
        while (_bitmapStack.size > MAX_BITMAP_STACK_SIZE) {
            val removed = _bitmapStack.removeAt(0)
            recycleSafely(removed)
        }

        if (triggerRecomposition) {
            _recompositionTrigger.update { it + 1 }
        }
    }

    fun undo(): Boolean {
        if (_bitmapStack.size <= 1) return false // Giữ ít nhất 1 bitmap
        
        val current = _bitmapStack.removeAt(_bitmapStack.lastIndex)
        _bitmapRedoStack.add(current)
        return true
    }

    fun redo(): Boolean {
        if (_bitmapRedoStack.isEmpty()) return false
        
        val bitmap = _bitmapRedoStack.removeAt(_bitmapRedoStack.lastIndex)
        _bitmapStack.add(bitmap)
        return true
    }

    private fun optimizeBitmap(bitmap: Bitmap): Bitmap {
        val maxDim = maxOf(bitmap.width, bitmap.height)
        if (maxDim <= MAX_BITMAP_DIMENSION) {
            // Chỉ copy nếu cần mutable hoặc config không đúng
            return if (bitmap.config == Bitmap.Config.ARGB_8888 && bitmap.isMutable) {
                bitmap
            } else {
                bitmap.copy(Bitmap.Config.ARGB_8888, true)
            }
        }
        
        val scale = MAX_BITMAP_DIMENSION.toFloat() / maxDim
        val newWidth = (bitmap.width * scale).toInt()
        val newHeight = (bitmap.height * scale).toInt()
        
        // createScaledBitmap trả về mutable bitmap
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun recycleSafely(bitmap: Bitmap?) {
        bitmap?.let {
            if (!it.isRecycled) it.recycle()
        }
    }

    fun updateStacksFromEditorState(finalEditorState: EditorScreenState) {
        // Chuyển đổi từ Stack sang List
        _bitmapStack.clear()
        _bitmapStack.addAll(finalEditorState.bitmapStack)
        _bitmapRedoStack.clear()
        _bitmapRedoStack.addAll(finalEditorState.bitmapRedoStack)
    }

    override fun onCleared() {
        super.onCleared()
        resetStacks()
    }
}