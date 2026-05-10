package com.abizer_r.quickedit.ui.magicBrush

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.abizer_r.quickedit.utils.drawMode.MagicWandPro
import com.abizer_r.quickedit.utils.other.bitmap.BitmapCache
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Stack
import javax.inject.Inject

enum class MagicBrushTool {
    SMART_ERASE,
    SHATTER,
    BLUR,
    PAN
}


@HiltViewModel
class MagicBrushViewModel @Inject constructor() : ViewModel() {

    private val _currentBitmap = MutableStateFlow<Bitmap?>(null)
    val currentBitmap: StateFlow<Bitmap?> = _currentBitmap

    private val _tolerance = MutableStateFlow(30f)
    val tolerance: StateFlow<Float> = _tolerance

    private val _selectedTool = MutableStateFlow(MagicBrushTool.SMART_ERASE)
    val selectedTool: StateFlow<MagicBrushTool> = _selectedTool

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo

    // Cache IDs for undo/redo stacks
    private val undoStack = Stack<String>()
    private val redoStack = Stack<String>()

    // Bitmap gốc - dùng để tính toán, không bao giờ thay đổi
    private val _originalBitmap = MutableStateFlow<Bitmap?>(null)
    val originalBitmap: StateFlow<Bitmap?> = _originalBitmap

    private var bitmapCache: BitmapCache? = null

    fun setBitmapCache(cache: BitmapCache) {
        bitmapCache = cache
    }

    fun setInitialBitmap(bitmap: Bitmap) {
        if (_currentBitmap.value == null) {
            // Đảm bảo ARGB_8888 để hỗ trợ alpha
            val argbBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            _currentBitmap.value = argbBitmap
            _originalBitmap.value = argbBitmap.copy(Bitmap.Config.ARGB_8888, true)
        }
    }

    fun updateTolerance(newTolerance: Float) {
        _tolerance.value = newTolerance
    }

    fun selectTool(tool: MagicBrushTool) {
        _selectedTool.value = tool
    }

    fun onMagicErase(x: Int, y: Int) {
        val bitmap = _currentBitmap.value ?: return
        if (_isProcessing.value || _selectedTool.value != MagicBrushTool.SMART_ERASE) return
        if (x !in 0 until bitmap.width || y !in 0 until bitmap.height) return

        viewModelScope.launch {
            _isProcessing.value = true
            val result: Bitmap? = withContext(Dispatchers.Default) {
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
                    clearRedoStack()
                    pushUndo(snapshot)
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

    /**
     * Áp dụng hiệu ứng Mờ (Mosaic)
     */
    fun applyBlurResult(path: Path, mosaicBitmap: Bitmap, brushSizePx: Float, canvasWidth: Int, canvasHeight: Int) {
        val bitmap = _currentBitmap.value ?: return

        viewModelScope.launch {
            _isProcessing.value = true
            val result = withContext(Dispatchers.Default) {
                val snapshot = bitmap.copy(Bitmap.Config.ARGB_8888, true)

                val canvas = Canvas(bitmap)
                val paint = Paint().apply {
                    isAntiAlias = true
                    style = Paint.Style.STROKE
                    strokeWidth = brushSizePx * (bitmap.width.toFloat() / canvasWidth)
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }

                val androidPath = path.asAndroidPath()
                val matrix = android.graphics.Matrix()
                val scaleX = bitmap.width.toFloat() / canvasWidth
                val scaleY = bitmap.height.toFloat() / canvasHeight
                matrix.setScale(scaleX, scaleY)
                androidPath.transform(matrix)

                val shader = android.graphics.BitmapShader(
                    mosaicBitmap,
                    android.graphics.Shader.TileMode.CLAMP,
                    android.graphics.Shader.TileMode.CLAMP
                )
                val shaderMatrix = android.graphics.Matrix()
                shaderMatrix.setScale(bitmap.width.toFloat() / mosaicBitmap.width, bitmap.height.toFloat() / mosaicBitmap.height)
                shader.setLocalMatrix(shaderMatrix)

                paint.shader = shader
                canvas.drawPath(androidPath, paint)

                clearRedoStack()
                pushUndo(snapshot)
                bitmap
            }
            _currentBitmap.value = result
            updateUndoRedoStates()
            _isProcessing.value = false
        }
    }

    /**
     * Tẩy tự do (Magic Erase) - Baking bằng PorterDuff.Mode.CLEAR
     *
     * @param path Path đã scale sang bitmap coordinate
     * @param brushSizePx Kích thước cọ đã scale (px)
     */
    fun applyMagicErase(
        path: android.graphics.Path,
        brushSizePx: Float
    ) {

        val bitmap = _currentBitmap.value ?: return
        if (brushSizePx <= 0) return

        viewModelScope.launch {
            _isProcessing.value = true
            val result = withContext(Dispatchers.Default) {
                val snapshot = bitmap.copy(Bitmap.Config.ARGB_8888, true)

                val canvas = Canvas(bitmap)
                val paint = Paint().apply {
                    isAntiAlias = true
                    style = Paint.Style.STROKE
                    strokeWidth = brushSizePx
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                }

                canvas.drawPath(path, paint)

                clearRedoStack()
                pushUndo(snapshot)
                bitmap
            }

            _currentBitmap.value = result
            updateUndoRedoStates()
            _isProcessing.value = false
        }
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            val current = _currentBitmap.value ?: return
            val cache = bitmapCache ?: return
            val id = undoStack.pop()
            val previous = cache.loadBitmap(id) ?: return
            // Save current bitmap to redo stack
            val currentId = cache.saveBitmap(
                current.copy(current.config ?: Bitmap.Config.ARGB_8888, true)
            ) ?: return
            redoStack.push(currentId)
            _currentBitmap.value = previous
            updateUndoRedoStates()
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val current = _currentBitmap.value ?: return
            val cache = bitmapCache ?: return
            val id = redoStack.pop()
            val next = cache.loadBitmap(id) ?: return
            // Save current bitmap back to undo stack
            val currentId = cache.saveBitmap(
                current.copy(current.config ?: Bitmap.Config.ARGB_8888, true)
            ) ?: return
            undoStack.push(currentId)
            _currentBitmap.value = next
            updateUndoRedoStates()
        }
    }

    private fun pushUndo(snapshot: Bitmap) {
        val cache = bitmapCache
        if (cache != null) {
            val id = cache.saveBitmap(snapshot)
            if (id != null) {
                undoStack.push(id)
            }
        }
    }

    private fun clearRedoStack() {
        val cache = bitmapCache
        while (redoStack.isNotEmpty()) {
            val id = redoStack.pop()
            cache?.deleteBitmap(id)
        }
        _canRedo.value = false
    }

    private fun updateUndoRedoStates() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }

    override fun onCleared() {
        super.onCleared()
        _currentBitmap.value?.recycle()
        val cache = bitmapCache
        undoStack.forEach { cache?.deleteBitmap(it) }
        redoStack.forEach { cache?.deleteBitmap(it) }
        undoStack.clear()
        redoStack.clear()
        cache?.clear()
    }
}
