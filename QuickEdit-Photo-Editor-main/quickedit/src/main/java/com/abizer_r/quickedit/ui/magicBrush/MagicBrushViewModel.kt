package com.abizer_r.quickedit.ui.magicBrush

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.abizer_r.quickedit.utils.drawMode.MagicWandPro
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Stack
import javax.inject.Inject

@HiltViewModel
class MagicBrushViewModel @Inject constructor() : ViewModel() {

    private val _currentBitmap = MutableStateFlow<Bitmap?>(null)
    val currentBitmap: StateFlow<Bitmap?> = _currentBitmap

    private val _tolerance = MutableStateFlow(30f)
    val tolerance: StateFlow<Float> = _tolerance

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo

    private val undoStack = Stack<Bitmap>()
    private val redoStack = Stack<Bitmap>()

    fun setInitialBitmap(bitmap: Bitmap) {
        if (_currentBitmap.value == null) {
            _currentBitmap.value = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        }
    }

    fun updateTolerance(newTolerance: Float) {
        _tolerance.value = newTolerance
    }

    fun onMagicErase(x: Int, y: Int) {
        val bitmap = _currentBitmap.value ?: return
        if (_isProcessing.value) return
        if (x !in 0 until bitmap.width || y !in 0 until bitmap.height) return

        viewModelScope.launch {
            _isProcessing.value = true
            
            val result: Bitmap? = withContext(Dispatchers.Default) {
                // Tạo bản sao để lưu vào stack trước khi chỉnh sửa
                val snapshot = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                
                val erased: Bitmap? = MagicWandPro.eraseRegion(
                    srcBitmap = bitmap,
                    startX = x,
                    startY = y,
                    tolerance = _tolerance.value.toInt(),
                    inPlace = true,
                    eightDir = false
                )
                
                if (erased != null) {
                    // Xóa Redo Stack khi có thao tác mới
                    redoStack.forEach { it.recycle() }
                    redoStack.clear()
                    
                    undoStack.push(snapshot)
                    erased
                } else {
                    snapshot.recycle()
                    null
                }
            }

            if (result != null) {
                _currentBitmap.value = result
                updateUndoRedoStates()
            }
            _isProcessing.value = false
        }
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            val current = _currentBitmap.value ?: return
            val previous = undoStack.pop()
            
            redoStack.push(current.copy(current.config ?: Bitmap.Config.ARGB_8888, true))
            _currentBitmap.value = previous
            updateUndoRedoStates()
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val current = _currentBitmap.value ?: return
            val next = redoStack.pop()
            
            undoStack.push(current.copy(current.config ?: Bitmap.Config.ARGB_8888, true))
            _currentBitmap.value = next
            updateUndoRedoStates()
        }
    }

    private fun updateUndoRedoStates() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }

    override fun onCleared() {
        super.onCleared()
        _currentBitmap.value?.recycle()
        undoStack.forEach { it.recycle() }
        redoStack.forEach { it.recycle() }
        undoStack.clear()
        redoStack.clear()
    }
}
