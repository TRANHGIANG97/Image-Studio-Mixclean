package com.thgiang.image.studio.ui.editor

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thgiang.image.core.data.backgroundremove.BackgroundRemoverRepository
import com.thgiang.image.core.data.save.ImageSaveRepository
import com.thgiang.image.core.util.processors.ProcessorUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import kotlin.math.abs

/**
 * EditorState v2 - Immutable, with copy-optimization
 */
data class EditorState(
    val template: EditorTemplate = EditorTemplate(),
    val product: EditorProduct = EditorProduct(),
    val viewport: EditorViewport = EditorViewport(),
    val appearance: EditorAppearance = EditorAppearance(),
    val selectedTool: EditorTool = EditorTool.Layout,
    val cropRatio: CropRatio = CropRatio.RATIO_1_1,
    val isExporting: Boolean = false,
    val exportResult: Uri? = null,
    val errorMessage: String? = null,
    val showOverlay: Boolean = false,
    val showBoundingBox: Boolean = false
) : java.io.Serializable {
    val canExport: Boolean
        get() = product.isBackgroundRemoved && !isExporting && template.loaded
}

@HiltViewModel
class ThemeplateEditorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backgroundRemoverRepository: BackgroundRemoverRepository,
    private val imageSaveRepository: ImageSaveRepository,
    private val renderer: EditorRenderer,
    private val historyManager: EditorHistoryManager,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val TAG = "EditorVM"
        private const val MIN_SCALE = 0.2f
        private const val MAX_SCALE = 5f
        private const val SNAP_ANGLE_THRESHOLD = 5f
        private val SNAP_ANGLES = listOf(0f, 90f, 180f, 270f)
        private const val HISTORY_DEBOUNCE_MS = 300L
        private const val GESTURE_THROTTLE_MS = 16L // ~60fps
    }

    private val _state = MutableStateFlow(
        savedStateHandle.get<EditorState>("editor_state") ?: EditorState()
    )
    val state: StateFlow<EditorState> = _state.asStateFlow()

    val canUndo: StateFlow<Boolean> = historyManager.canUndo
    val canRedo: StateFlow<Boolean> = historyManager.canRedo

    // Debounced history push for gesture operations
    private val historyPushFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val gestureThrottleFlow = MutableSharedFlow<GestureDelta>(extraBufferCapacity = 1)

    private var templateLoadJob: Job? = null
    private var bgRemoveJob: Job? = null
    private var exportJob: Job? = null
    private var historyPushJob: Job? = null
    private var overlayJob: Job? = null

    init {
        // Debounced history push
        viewModelScope.launch {
            historyPushFlow
                .debounce(HISTORY_DEBOUNCE_MS)
                .collect { pushHistoryInternal() }
        }
        
        // Throttled gesture updates for smooth 60fps
        viewModelScope.launch {
            gestureThrottleFlow
                .throttleLatest(GESTURE_THROTTLE_MS)
                .collect { delta ->
                    applyGestureDelta(delta)
                }
        }

        // Auto-persist state to SavedStateHandle on every change to protect from process death
        viewModelScope.launch {
            _state.collect { persistState() }
        }
        
        // Restore state if available
        savedStateHandle.get<String>("template_path")?.let {
            if (!_state.value.template.loaded) {
                loadTemplate(it)
            }
        }
    }

    fun onEvent(event: EditorEvent) {
        when (event) {
            is EditorEvent.LoadTemplate -> loadTemplate(event.assetPath)
            is EditorEvent.SetProductImage -> setProductImage(event.uri)
            is EditorEvent.UpdateGesture -> {
                gestureThrottleFlow.tryEmit(event.delta)
                requestHistoryPush()
            }
            is EditorEvent.UpdateOffset -> {
                _state.update { it.copy(viewport = it.viewport.withOffset(it.viewport.offset + event.delta)) }
                requestHistoryPush()
            }
            is EditorEvent.SetOffset -> {
                _state.update { it.copy(viewport = it.viewport.withOffset(event.offset)) }
                pushHistory()
            }
            is EditorEvent.UpdateScale -> updateScale(event.factor)
            is EditorEvent.SetScale -> {
                _state.update { it.copy(viewport = it.viewport.withScale(event.scale)) }
                pushHistory()
            }
            is EditorEvent.UpdateRotation -> updateRotation(event.delta)
            is EditorEvent.SetRotation -> {
                var normalized = event.degrees % 360f
                if (normalized < 0) normalized += 360f
                _state.update { it.copy(viewport = it.viewport.withRotation(normalized)) }
                pushHistory()
            }
            EditorEvent.FlipHorizontal -> {
                _state.update { it.copy(viewport = it.viewport.copy(flippedH = !it.viewport.flippedH)) }
                pushHistory()
            }
            EditorEvent.FlipVertical -> {
                _state.update { it.copy(viewport = it.viewport.copy(flippedV = !it.viewport.flippedV)) }
                pushHistory()
            }
            is EditorEvent.UpdateShadow -> {
                _state.update { it.copy(appearance = it.appearance.copy(shadowIntensity = event.intensity.coerceIn(0f, 1f))) }
            }
            is EditorEvent.UpdateShadowAngle -> {
                _state.update { it.copy(appearance = it.appearance.copy(shadowAngle = event.angle.coerceIn(0f, 360f))) }
            }
            is EditorEvent.UpdateShadowDistance -> {
                _state.update { it.copy(appearance = it.appearance.copy(shadowDistance = event.distance.coerceIn(0f, 50f))) }
            }
            is EditorEvent.UpdateShadowColor -> {
                _state.update { it.copy(appearance = it.appearance.copy(shadowColorArgb = event.argb)) }
            }
            is EditorEvent.UpdateAlpha -> {
                _state.update { it.copy(appearance = it.appearance.copy(alpha = event.alpha.coerceIn(0.1f, 1f))) }
            }
            is EditorEvent.SetBoundingBoxVisible -> {
                _state.update { it.copy(showBoundingBox = event.visible) }
            }
            is EditorEvent.SelectTool -> _state.update {
                val nextTool = if (event.tool == EditorTool.Shadow && it.selectedTool == EditorTool.Shadow) {
                    EditorTool.Layout
                } else {
                    event.tool
                }
                it.copy(selectedTool = nextTool)
            }
            is EditorEvent.SelectCropRatio -> {
                _state.update { it.copy(cropRatio = event.ratio) }
                pushHistory()
            }
            EditorEvent.CommitTransform -> pushHistory()
            EditorEvent.Undo -> undo()
            EditorEvent.Redo -> redo()
            is EditorEvent.Export -> export(event.templateAssetPath)
        }
    }

    private fun loadTemplate(assetPath: String) {
        android.util.Log.d(TAG, "loadTemplate called with path: $assetPath")
        if (_state.value.template.loaded && _state.value.template.assetPath == assetPath) {
            android.util.Log.d(TAG, "Template already loaded, ignoring.")
            return
        }
        
        savedStateHandle["template_path"] = assetPath
        
        templateLoadJob?.cancel()
        templateLoadJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                android.util.Log.d(TAG, "Opening asset input stream for: $assetPath")
                context.assets.open(assetPath).use { input ->
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(input, null, opts)
                    android.util.Log.d(TAG, "Decoded template original bounds: ${opts.outWidth} x ${opts.outHeight}")
                    
                    _state.update {
                        it.copy(
                            template = EditorTemplate(
                                assetPath = assetPath,
                                originalWidth = opts.outWidth,
                                originalHeight = opts.outHeight,
                                loaded = true
                            )
                        )
                    }
                    android.util.Log.d(TAG, "Successfully loaded template state")
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "loadTemplate failed to load from assets: $assetPath", e)
                _state.update { it.copy(errorMessage = "Không thể tải template") }
            }
        }
    }

    // ============ Product Image with Atomic State Updates ============

    private fun setProductImage(uri: Uri) {
        bgRemoveJob?.cancel()
        
        // Atomic reset
            _state.update {
                it.copy(
                    product = EditorProduct(originalUriString = uri.toString(), processing = true),
                    viewport = EditorViewport(),
                    exportResult = null,
                    errorMessage = null,
                    showBoundingBox = false
                )
            }
        historyManager.clear()
        
        bgRemoveJob = viewModelScope.launch {
            try {
                // Step 1: Decode (CPU-bound → Default dispatcher)
                val decoded = withContext(Dispatchers.Default) {
                    ProcessorUtils.decodeBitmapFromUri(context, uri)
                }
                
                if (decoded == null) {
                    _state.update { it.copy(product = it.product.copy(processing = false)) }
                    return@launch
                }

                // Step 2: Background removal (ML inference → Default/ML dispatcher)
                val foreground = withContext(Dispatchers.Default) {
                    backgroundRemoverRepository.getForegroundBitmap(decoded).getOrNull()
                }

                if (foreground == null) {
                    decoded.recycle()
                    _state.update { it.copy(product = it.product.copy(processing = false)) }
                    return@launch
                }

                // Step 3: Cache result (IO-bound)
                val cachedUri = withContext(Dispatchers.IO) {
                    imageSaveRepository.cacheBitmap(foreground).getOrNull()
                }

                if (cachedUri != null) {
                    val tw = _state.value.template.originalSize.width
                    val th = _state.value.template.originalSize.height
                    
                    val baseSize = if (tw > 0 && th > 0) {
                        val baseFitScale = kotlin.math.min(
                            tw.toFloat() / foreground.width,
                            th.toFloat() / foreground.height
                        )
                        IntSize(
                            (foreground.width * baseFitScale).toInt(),
                            (foreground.height * baseFitScale).toInt()
                        )
                    } else {
                        IntSize(foreground.width, foreground.height)
                    }

                    // Atomic state update with all new data
                    _state.update {
                        it.copy(
                            product = EditorProduct(
                                originalUriString = uri.toString(),
                                foregroundUriString = cachedUri.toString(),
                                isBackgroundRemoved = true,
                                baseWidth = baseSize.width,
                                baseHeight = baseSize.height,
                                processing = false
                            ),
                            viewport = EditorViewport(scale = 1f),
                            showBoundingBox = true
                        )
                    }
                    triggerOverlay()
                    pushHistory()
                } else {
                    _state.update { it.copy(product = it.product.copy(processing = false)) }
                }
                
                // Cleanup bitmaps
                foreground.recycle()
                decoded.recycle()
                
            } catch (e: CancellationException) {
                throw e // Don't swallow cancellation
            } catch (e: Exception) {
                android.util.Log.e(TAG, "removeBackground failed", e)
                _state.update { 
                    it.copy(
                        product = it.product.copy(processing = false),
                        errorMessage = e.message ?: "Lỗi xử lý ảnh"
                    ) 
                }
            }
        }
    }

    // ============ Transform Operations ============
    
    private fun updateScale(factor: Float) {
        _state.update {
            val newScale = (it.viewport.scale * factor).coerceIn(MIN_SCALE, MAX_SCALE)
            it.copy(viewport = it.viewport.withScale(newScale))
        }
    }

    private fun updateRotation(delta: Float) {
        _state.update {
            var newRotation = (it.viewport.rotation + delta) % 360f
            if (newRotation < 0) newRotation += 360f
            
            val snapped = SNAP_ANGLES.minByOrNull { angle -> abs(angle - newRotation) }
            if (snapped != null && abs(snapped - newRotation) < SNAP_ANGLE_THRESHOLD) {
                newRotation = snapped
            }
            
            it.copy(viewport = it.viewport.withRotation(newRotation))
        }
    }

    private fun applyGestureDelta(delta: GestureDelta) {
        _state.update { current ->
            var newViewport = current.viewport
            
            if (delta.pan != Offset.Zero) {
                newViewport = newViewport.withOffset(newViewport.offset + delta.pan)
            }
            if (delta.scale != 1f) {
                val newScale = (newViewport.scale * delta.scale).coerceIn(MIN_SCALE, MAX_SCALE)
                newViewport = newViewport.withScale(newScale)
            }
            if (delta.rotation != 0f) {
                var newRotation = (newViewport.rotation + delta.rotation) % 360f
                if (newRotation < 0) newRotation += 360f
                newViewport = newViewport.withRotation(newRotation)
            }
            
            current.copy(viewport = newViewport)
        }
    }

    // ============ History with Debounce ============
    
    private fun requestHistoryPush() {
        historyPushFlow.tryEmit(Unit)
    }
    
    private fun pushHistory() {
        historyPushJob?.cancel()
        historyPushJob = viewModelScope.launch {
            pushHistoryInternal()
        }
    }
    
    private fun pushHistoryInternal() {
        val current = _state.value
        historyManager.push(
            TransformSnapshot(
                viewport = current.viewport,
                appearance = current.appearance,
                cropRatio = current.cropRatio
            )
        )
    }

    private fun undo() {
        historyManager.undo()?.let { snapshot ->
            _state.update {
                it.copy(
                    viewport = snapshot.viewport,
                    appearance = snapshot.appearance,
                    cropRatio = snapshot.cropRatio
                )
            }
        }
    }

    private fun redo() {
        historyManager.redo()?.let { snapshot ->
            _state.update {
                it.copy(
                    viewport = snapshot.viewport,
                    appearance = snapshot.appearance,
                    cropRatio = snapshot.cropRatio
                )
            }
        }
    }

    // ============ Export with Cancellation Support ============
    
    private fun export(templateAssetPath: String) {
        if (_state.value.isExporting || !_state.value.canExport) return
        
        val currentState = _state.value
        val foregroundUri = currentState.product.foregroundUri ?: return
        val templateSize = currentState.template.originalSize
        
        if (templateSize.width == 0 || templateSize.height == 0) return

        exportJob?.cancel()
        exportJob = viewModelScope.launch {
            _state.update { it.copy(isExporting = true, errorMessage = null) }
            
            try {
                val result = renderer.render(
                    EditorRenderer.RenderRequest(
                        templateAssetPath = templateAssetPath,
                        foregroundUri = foregroundUri,
                        templateSize = templateSize,
                        viewport = currentState.viewport,
                        appearance = currentState.appearance,
                        baseSize = currentState.product.baseSize,
                        cropRatio = currentState.cropRatio
                    )
                )

                result.fold(
                    onSuccess = { bitmap ->
                        val uri = withContext(Dispatchers.IO) {
                            imageSaveRepository.saveBitmap(bitmap).getOrNull()
                        }
                        
                        if (uri != null) {
                            _state.update { it.copy(isExporting = false, exportResult = uri) }
                        } else {
                            _state.update { 
                                it.copy(isExporting = false, errorMessage = "Lưu ảnh thất bại") 
                            }
                        }
                    },
                    onFailure = { e ->
                        _state.update { 
                            it.copy(isExporting = false, errorMessage = e.message ?: "Render thất bại") 
                        }
                    }
                )
            } catch (e: CancellationException) {
                _state.update { it.copy(isExporting = false) }
                throw e
            } catch (e: Exception) {
                _state.update { 
                    it.copy(isExporting = false, errorMessage = e.message ?: "Lỗi không xác định") 
                }
            }
        }
    }

    // ============ SavedState Persistence ============
    
    private fun persistState() {
        savedStateHandle["editor_state"] = _state.value
    }

    override fun onCleared() {
        super.onCleared()
        persistState()
        templateLoadJob?.cancel()
        bgRemoveJob?.cancel()
        exportJob?.cancel()
        historyPushJob?.cancel()
        overlayJob?.cancel()
        historyManager.clear()
    }

    fun triggerOverlay() {
        _state.update { it.copy(showOverlay = true) }
        overlayJob?.cancel()
        overlayJob = viewModelScope.launch {
            delay(2000)
            _state.update { it.copy(showOverlay = false) }
        }
    }
}

// Extension for throttling
@OptIn(FlowPreview::class)
private fun <T> Flow<T>.throttleLatest(periodMillis: Long): Flow<T> = flow {
    var lastTime = 0L
    collect { value ->
        val now = System.currentTimeMillis()
        if (now - lastTime >= periodMillis) {
            lastTime = now
            emit(value)
        }
    }
}
