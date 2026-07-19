package com.abizer_r.quickedit.ui.magicBrush

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.abizer_r.quickedit.R
import com.abizer_r.quickedit.utils.drawMode.MagicWandPro
import com.abizer_r.quickedit.utils.other.bitmap.BitmapCache
import com.thgiang.image.core.data.backgroundremove.BackgroundRemoverRepository
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
    BLUR
}

data class MagicWandDemoState(
    val sourceBitmap: Bitmap? = null,
    val erasedBitmap: Bitmap? = null,
    val foregroundBitmap: Bitmap? = null,
    val selectedBackgroundBitmap: Bitmap? = null,
    val boundaryPoints: List<PointF> = emptyList(),
    val tapXRatio: Float = 0.16f,
    val tapYRatio: Float = 0.22f,
    val isPreparing: Boolean = false
)


@HiltViewModel
class MagicBrushViewModel @Inject constructor(
    private val backgroundRemoverRepository: BackgroundRemoverRepository,
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

    private val _magicWandDemoState = MutableStateFlow(MagicWandDemoState())
    val magicWandDemoState: StateFlow<MagicWandDemoState> = _magicWandDemoState

    private val prefs = context.getSharedPreferences("magic_brush_prefs", android.content.Context.MODE_PRIVATE)

    private var demoPrepareJob: Job? = null

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

    fun prepareMagicWandDemo() {
        val current = _magicWandDemoState.value
        if (current.sourceBitmap != null || current.isPreparing) return

        demoPrepareJob?.cancel()
        demoPrepareJob = viewModelScope.launch {
            _magicWandDemoState.value = current.copy(isPreparing = true)

            val demo = withContext(Dispatchers.Default) {
                runCatching {
                    val decoded = BitmapFactory.decodeResource(
                        context.resources,
                        R.drawable.before_bird
                    ) ?: return@runCatching null

                    val scaled = decoded.scaledToMaxDimension(DEMO_MAX_DIMENSION)
                    val source = scaled.copy(Bitmap.Config.ARGB_8888, true)
                    if (decoded !== source && !decoded.isRecycled) {
                        decoded.recycle()
                    }
                    if (scaled !== decoded && scaled !== source && !scaled.isRecycled) {
                        scaled.recycle()
                    }

                    val rawForeground = backgroundRemoverRepository
                        .getForegroundBitmap(source)
                        .getOrNull()
                    val foregroundFromModel = rawForeground
                        ?.alignedTo(source.width, source.height)
                    if (
                        rawForeground != null &&
                        rawForeground !== foregroundFromModel &&
                        !rawForeground.isRecycled
                    ) {
                        rawForeground.recycle()
                    }

                    val tapPoint = findDemoBackgroundTap(foregroundFromModel, source)
                    val eraseSource = source.copy(Bitmap.Config.ARGB_8888, true)
                    val erased = MagicWandPro.eraseRegion(
                        srcBitmap = eraseSource,
                        startX = (tapPoint.x * source.width).toInt().coerceIn(0, source.width - 1),
                        startY = (tapPoint.y * source.height).toInt().coerceIn(0, source.height - 1),
                        tolerance = DEMO_MAGIC_WAND_TOLERANCE,
                        inPlace = true,
                        eightDir = false
                    ) ?: eraseSource

                    val foreground = foregroundFromModel
                        ?: createForegroundFromErasedResult(source, erased)
                    val selectedBackground = createSelectedBackgroundMask(source, erased)
                    val boundaryPoints = extractBoundaryPoints(foreground)

                    MagicWandDemoState(
                        sourceBitmap = source,
                        erasedBitmap = erased,
                        foregroundBitmap = foreground,
                        selectedBackgroundBitmap = selectedBackground,
                        boundaryPoints = boundaryPoints,
                        tapXRatio = tapPoint.x,
                        tapYRatio = tapPoint.y,
                        isPreparing = false
                    )
                }.getOrNull()
            }

            _magicWandDemoState.value = demo ?: MagicWandDemoState(isPreparing = false)
        }
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
        if (canvasWidth <= 0 || canvasHeight <= 0 ||
            bitmap.width <= 0 || bitmap.height <= 0 ||
            mosaicBitmap.isRecycled || mosaicBitmap.width <= 0 || mosaicBitmap.height <= 0
        ) {
            Log.w("MagicBrushViewModel", "Ignoring blur stroke with invalid bitmap/canvas dimensions")
            return
        }

        _isProcessing.value = true
        viewModelScope.launch {
            try {
                bitmapMutex.withLock {
                    if (isDisposed) return@withLock
                    val result = withContext(Dispatchers.Default) {
                        val snapshot = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                        try {
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
                            shaderMatrix.setScale(
                                bitmap.width.toFloat() / mosaicBitmap.width,
                                bitmap.height.toFloat() / mosaicBitmap.height
                            )
                            shader.setLocalMatrix(shaderMatrix)

                            paint.shader = shader
                            canvas.drawPath(androidPath, paint)

                            clearRedoStack()
                            pushUndo(snapshot)
                            bitmap.copy(Bitmap.Config.ARGB_8888, true)
                        } finally {
                            if (!snapshot.isRecycled) snapshot.recycle()
                        }
                    }
                    if (!isDisposed) {
                        _currentBitmap.value = result
                        updateUndoRedoStates()
                    } else if (!result.isRecycled) {
                        result.recycle()
                    }
                }
            } catch (error: Throwable) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                Log.e("MagicBrushViewModel", "Error applying blur stroke", error)
            } finally {
                _isProcessing.value = false
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
                if (isDisposed || undoStack.isEmpty()) return@withLock
                
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
                if (isDisposed || redoStack.isEmpty()) return@withLock
                
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

    private fun Bitmap.scaledToMaxDimension(maxDimension: Int): Bitmap {
        val largestSide = maxOf(width, height)
        if (largestSide <= maxDimension) return this
        val scale = maxDimension.toFloat() / largestSide.toFloat()
        val targetWidth = (width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true)
    }

    private fun Bitmap.alignedTo(targetWidth: Int, targetHeight: Int): Bitmap {
        return if (width == targetWidth && height == targetHeight) {
            copy(Bitmap.Config.ARGB_8888, true)
        } else {
            Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true)
        }
    }

    private fun findDemoBackgroundTap(mask: Bitmap?, source: Bitmap): PointF {
        val candidates = listOf(
            PointF(0.14f, 0.18f),
            PointF(0.86f, 0.20f),
            PointF(0.12f, 0.78f),
            PointF(0.88f, 0.78f),
            PointF(0.50f, 0.12f)
        )
        if (mask == null || mask.isRecycled) return candidates.first()

        return candidates.firstOrNull { point ->
            val x = (point.x * mask.width).toInt().coerceIn(0, mask.width - 1)
            val y = (point.y * mask.height).toInt().coerceIn(0, mask.height - 1)
            ((mask.getPixel(x, y) ushr 24) and 0xFF) < DEMO_ALPHA_THRESHOLD
        } ?: PointF(0.14f, 0.18f)
    }

    private fun createForegroundFromErasedResult(source: Bitmap, erased: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val sourcePixels = IntArray(width * height)
        val erasedPixels = IntArray(width * height)
        source.getPixels(sourcePixels, 0, width, 0, 0, width, height)
        erased.getPixels(erasedPixels, 0, width, 0, 0, width, height)

        for (i in sourcePixels.indices) {
            val alpha = (erasedPixels[i] ushr 24) and 0xFF
            if (alpha < DEMO_ALPHA_THRESHOLD) {
                sourcePixels[i] = android.graphics.Color.TRANSPARENT
            }
        }

        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(sourcePixels, 0, width, 0, 0, width, height)
        }
    }

    private fun createSelectedBackgroundMask(source: Bitmap, erased: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val sourcePixels = IntArray(width * height)
        val erasedPixels = IntArray(width * height)
        source.getPixels(sourcePixels, 0, width, 0, 0, width, height)
        erased.getPixels(erasedPixels, 0, width, 0, 0, width, height)

        for (i in sourcePixels.indices) {
            val sourceAlpha = (sourcePixels[i] ushr 24) and 0xFF
            val erasedAlpha = (erasedPixels[i] ushr 24) and 0xFF
            if (sourceAlpha <= DEMO_ALPHA_THRESHOLD || erasedAlpha > DEMO_ALPHA_THRESHOLD) {
                sourcePixels[i] = android.graphics.Color.TRANSPARENT
            }
        }

        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(sourcePixels, 0, width, 0, 0, width, height)
        }
    }

    private fun extractBoundaryPoints(mask: Bitmap): List<PointF> {
        if (mask.isRecycled || mask.width < 3 || mask.height < 3) return emptyList()

        val width = mask.width
        val height = mask.height
        val pixels = IntArray(width * height)
        mask.getPixels(pixels, 0, width, 0, 0, width, height)

        val points = ArrayList<PointF>(DEMO_MAX_BOUNDARY_POINTS)
        val step = maxOf(2, maxOf(width, height) / 180)

        fun alphaAt(x: Int, y: Int): Int {
            return (pixels[y * width + x] ushr 24) and 0xFF
        }

        for (y in 1 until height - 1 step step) {
            for (x in 1 until width - 1 step step) {
                if (alphaAt(x, y) <= DEMO_ALPHA_THRESHOLD) continue
                val isEdge =
                    alphaAt(x - 1, y) <= DEMO_ALPHA_THRESHOLD ||
                        alphaAt(x + 1, y) <= DEMO_ALPHA_THRESHOLD ||
                        alphaAt(x, y - 1) <= DEMO_ALPHA_THRESHOLD ||
                        alphaAt(x, y + 1) <= DEMO_ALPHA_THRESHOLD
                if (isEdge) {
                    points.add(PointF(x / width.toFloat(), y / height.toFloat()))
                }
            }
        }

        if (points.size <= DEMO_MAX_BOUNDARY_POINTS) return points
        val stride = (points.size / DEMO_MAX_BOUNDARY_POINTS).coerceAtLeast(1)
        return points.filterIndexed { index, _ -> index % stride == 0 }
    }

    override fun onCleared() {
        isDisposed = true
        currentEraseJob?.cancel()
        demoPrepareJob?.cancel()
        recycleDemoBitmaps(_magicWandDemoState.value)
        
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

    private fun recycleDemoBitmaps(state: MagicWandDemoState) {
        listOf(state.sourceBitmap, state.erasedBitmap, state.foregroundBitmap, state.selectedBackgroundBitmap)
            .distinct()
            .forEach { bitmap ->
                if (bitmap != null && !bitmap.isRecycled) {
                    bitmap.recycle()
                }
            }
    }

    private companion object {
        const val DEMO_MAX_DIMENSION = 520
        const val DEMO_MAGIC_WAND_TOLERANCE = 44
        const val DEMO_ALPHA_THRESHOLD = 24
        const val DEMO_MAX_BOUNDARY_POINTS = 900
    }
}
