package com.abizer_r.quickedit.ui.magicBrush

import android.content.Context
import android.util.Log
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.LinkedList
import javax.inject.Inject

enum class MagicBrushTool {
    SMART_ERASE,
    BRUSH_ERASE,
    BLUR,
    PAN
}


@HiltViewModel
class MagicBrushViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : ViewModel() {

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

    private val _showGuide = MutableStateFlow(false)
    val showGuide: StateFlow<Boolean> = _showGuide

    private val _showSmartEraseTooltip = MutableStateFlow(false)
    val showSmartEraseTooltip: StateFlow<Boolean> = _showSmartEraseTooltip

    private val prefs = context.getSharedPreferences("magic_brush_prefs", android.content.Context.MODE_PRIVATE)

    init {
        _showSmartEraseTooltip.value = prefs.getBoolean("show_smart_erase_tooltip", true)
    }

    fun checkFirstLaunch() {
        val guideShown = prefs.getBoolean("guide_shown", false)
        if (!guideShown) {
            _showGuide.value = true
            prefs.edit().putBoolean("guide_shown", true).apply()
        }
    }

    fun dismissGuide() {
        _showGuide.value = false
    }

    fun dismissSmartEraseTooltip(dontShowAgain: Boolean) {
        _showSmartEraseTooltip.value = false
        if (dontShowAgain) {
            prefs.edit().putBoolean("show_smart_erase_tooltip", false).apply()
        }
    }

    fun forceShowSmartEraseTooltip() {
        _showSmartEraseTooltip.value = true
    }

    // Cache IDs for undo/redo stacks
    private val undoStack = LinkedList<String>()
    private val redoStack = LinkedList<String>()
    private val MAX_UNDO_STEPS = 20

    // Bitmap gốc - dùng để tính toán, không bao giờ thay đổi
    private val _originalBitmap = MutableStateFlow<Bitmap?>(null)
    val originalBitmap: StateFlow<Bitmap?> = _originalBitmap

    private var bitmapCache: BitmapCache? = null
    private var currentEraseJob: Job? = null

    // Hàng đợi cho Brush Erase để tránh mất nét vẽ khi thao tác nhanh
    private val eraseQueue = LinkedList<Pair<android.graphics.Path, Float>>()
    private var isEraseQueueProcessing = false

    // Mutex để đồng bộ hóa việc truy cập bitmap (Undo/Redo vs Drawing)
    private val bitmapMutex = Mutex()
    private var isDisposed = false

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
        if (tool != MagicBrushTool.BRUSH_ERASE) {
            cancelEraseQueue()
        }
        _selectedTool.value = tool
    }

    private fun cancelEraseQueue() {
        currentEraseJob?.cancel()
        isEraseQueueProcessing = false
        eraseQueue.clear()
        // KHÔNG set _isProcessing = false ở đây vì có thể đang trong mutex lock
    }

    fun onMagicErase(x: Int, y: Int) {
        val bitmap = _currentBitmap.value ?: return
        if (_isProcessing.value || _selectedTool.value != MagicBrushTool.SMART_ERASE) return
        if (x !in 0 until bitmap.width || y !in 0 until bitmap.height) return

        cancelEraseQueue() 
        _isProcessing.value = true
        viewModelScope.launch {
            bitmapMutex.withLock {
                if (isDisposed) return@withLock
                try {
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
                            snapshot.recycle()
                            erased.copy(Bitmap.Config.ARGB_8888, true)
                        } else {
                            snapshot.recycle()
                            null
                        }
                    }
                    if (result != null && !isDisposed) {
                        _currentBitmap.value = result
                        updateUndoRedoStates()
                    }
                } catch (e: Exception) {
                    Log.e("MagicBrushViewModel", "Error in onMagicErase", e)
                } finally {
                    _isProcessing.value = false
                }
            }
        }
    }

    fun applyBlurResult(path: Path, mosaicBitmap: Bitmap, brushSizePx: Float, canvasWidth: Int, canvasHeight: Int) {
        val bitmap = _currentBitmap.value ?: return
        if (_isProcessing.value) return

        _isProcessing.value = true
        viewModelScope.launch {
            bitmapMutex.withLock {
                if (isDisposed) return@withLock
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
                    snapshot.recycle()
                    
                    bitmap.copy(Bitmap.Config.ARGB_8888, true)
                }
                if (!isDisposed) {
                    _currentBitmap.value = result
                    updateUndoRedoStates()
                    _isProcessing.value = false
                }
            }
        }
    }

    fun applyEraseResult(path: android.graphics.Path, brushSizeBitmapPx: Float) {
        eraseQueue.add(path to brushSizeBitmapPx)
        if (!isEraseQueueProcessing) {
            processEraseQueue()
        }
    }

    private fun processEraseQueue() {
        if (isDisposed) return
        
        val next = eraseQueue.poll() ?: run {
            isEraseQueueProcessing = false
            _isProcessing.value = false
            return
        }

        isEraseQueueProcessing = true
        
        currentEraseJob = viewModelScope.launch {
            bitmapMutex.withLock {
                if (isDisposed) return@withLock
                
                _isProcessing.value = true
                
                try {
                    val currentBmp = _currentBitmap.value
                    if (currentBmp == null || currentBmp.isRecycled) {
                        _isProcessing.value = false
                        processEraseQueue() 
                        return@withLock
                    }

                    val result = withContext(Dispatchers.Default) {
                        val snapshot = currentBmp.copy(Bitmap.Config.ARGB_8888, true)
                        
                        val canvas = Canvas(currentBmp)
                        val paint = Paint().apply {
                            isAntiAlias = true
                            style = Paint.Style.STROKE
                            strokeWidth = next.second
                            strokeCap = Paint.Cap.ROUND
                            strokeJoin = Paint.Join.ROUND
                            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                        }
                        canvas.drawPath(next.first, paint)

                        clearRedoStack()
                        pushUndo(snapshot)
                        snapshot.recycle()
                        
                        currentBmp.copy(Bitmap.Config.ARGB_8888, true)
                    }
                    
                    if (!isDisposed) {
                        _currentBitmap.value = result
                        updateUndoRedoStates()
                    }
                } catch (e: Exception) {
                    Log.e("MagicBrushViewModel", "Error in processEraseQueue", e)
                } finally {
                    if (!isDisposed) {
                        _isProcessing.value = false
                        processEraseQueue() 
                    }
                }
            }
        }
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        
        viewModelScope.launch(Dispatchers.IO) {
            bitmapMutex.withLock {
                if (isDisposed) return@withLock
                
                cancelEraseQueue() 
                
                _isProcessing.value = true
                try {
                    val current = _currentBitmap.value
                    val id = undoStack.removeLast()
                    val previous = bitmapCache?.loadBitmap(id)
                    
                    if (previous != null && current != null) {
                        val currentId = bitmapCache?.saveBitmap(
                            current.copy(current.config ?: Bitmap.Config.ARGB_8888, true)
                        )
                        currentId?.let { redoStack.addLast(it) }
                        
                        _currentBitmap.value = previous
                        updateUndoRedoStates()
                    }
                } finally {
                    _isProcessing.value = false
                }
            }
        }
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        
        viewModelScope.launch(Dispatchers.IO) {
            bitmapMutex.withLock {
                if (isDisposed) return@withLock
                
                cancelEraseQueue()
                
                _isProcessing.value = true
                try {
                    val current = _currentBitmap.value
                    val id = redoStack.removeLast()
                    val next = bitmapCache?.loadBitmap(id)
                    
                    if (next != null && current != null) {
                        val currentId = bitmapCache?.saveBitmap(
                            current.copy(current.config ?: Bitmap.Config.ARGB_8888, true)
                        )
                        currentId?.let { undoStack.addLast(it) }
                        
                        _currentBitmap.value = next
                        updateUndoRedoStates()
                    }
                } finally {
                    _isProcessing.value = false
                }
            }
        }
    }

    private fun pushUndo(snapshot: Bitmap) {
        val cache = bitmapCache ?: return
        val id = cache.saveBitmap(snapshot)
        if (id != null) {
            undoStack.addLast(id)
            if (undoStack.size > MAX_UNDO_STEPS) {
                val oldestId = undoStack.removeFirst()
                cache.deleteBitmap(oldestId)
            }
        }
    }

    private fun clearRedoStack() {
        val cache = bitmapCache ?: return
        // Lặp qua tất cả id trong redoStack và xóa khỏi cache
        for (id in redoStack) {
            cache.deleteBitmap(id)
        }
        // Xóa toàn bộ stack
        redoStack.clear()
        _canRedo.value = false
    }

    private fun updateUndoRedoStates() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }

    override fun onCleared() {
        isDisposed = true
        currentEraseJob?.cancel()
        
        viewModelScope.launch {
            bitmapMutex.withLock {
                _currentBitmap.value?.let {
                    if (!it.isRecycled) it.recycle()
                }
                _originalBitmap.value?.let {
                    if (!it.isRecycled && it !== _currentBitmap.value) it.recycle()
                }
                
                val cache = bitmapCache
                undoStack.forEach { cache?.deleteBitmap(it) }
                redoStack.forEach { cache?.deleteBitmap(it) }
                undoStack.clear()
                redoStack.clear()
                cache?.clear()
            }
        }
        
        super.onCleared()
    }
}
