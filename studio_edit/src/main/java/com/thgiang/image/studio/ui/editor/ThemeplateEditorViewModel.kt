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
import com.thgiang.image.studio.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import kotlin.math.abs

@HiltViewModel
class ThemeplateEditorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backgroundRemoverRepository: BackgroundRemoverRepository,
    private val imageSaveRepository: ImageSaveRepository,
    private val renderer: EditorRenderer,
    private val historyManager: EditorHistoryManager,
    private val sampleObjectCacheManager: SampleObjectCacheManager,
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
        (savedStateHandle.get<EditorState>("editor_state") ?: EditorState()).let { restored ->
            restored.copy(
                template = restored.template ?: EditorTemplate(),
                selectedTool = restored.selectedTool,
                layers = restored.layers ?: emptyList(),
                selectedLayerId = restored.selectedLayerId
            )
        }
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

    private inline fun updateActiveLayer(crossinline block: (EditorLayer) -> EditorLayer) {
        _state.update { state ->
            val layerId = state.selectedLayerId ?: return@update state
            val newLayers = state.layers.map { 
                if (it.id == layerId) block(it) else it 
            }
            state.copy(layers = newLayers)
        }
    }

    fun onEvent(event: EditorEvent) {
        when (event) {
            is EditorEvent.LoadTemplate -> loadTemplate(event.assetPath, event.objectSourceAssetPath)
            is EditorEvent.LoadCloudTemplate -> loadCloudTemplate(event.cloudTemplate)
            is EditorEvent.SetProductImage -> setProductImage(event.uri, event.replaceLayerId)
            is EditorEvent.AddSticker -> addSticker(event.assetPath)
            is EditorEvent.UpdateGesture -> {
                gestureThrottleFlow.tryEmit(event.delta)
                requestHistoryPush()
            }
            is EditorEvent.UpdateOffset -> {
                updateActiveLayer { it.copy(viewport = it.viewport.withOffset(it.viewport.offset + event.delta)) }
                requestHistoryPush()
            }
            is EditorEvent.SetOffset -> {
                updateActiveLayer { it.copy(viewport = it.viewport.withOffset(event.offset)) }
                pushHistory()
            }
            is EditorEvent.UpdateScale -> updateScale(event.factor)
            is EditorEvent.SetScale -> {
                updateActiveLayer { it.copy(viewport = it.viewport.withScale(event.scale)) }
                pushHistory()
            }
            is EditorEvent.UpdateRotation -> {
                updateRotation(event.delta)
            }
            is EditorEvent.SetRotation -> {
                var normalized = event.degrees % 360f
                if (normalized < 0) normalized += 360f
                updateActiveLayer { it.copy(viewport = it.viewport.withRotation(normalized)) }
                pushHistory()
            }
            EditorEvent.FlipHorizontal -> {
                updateActiveLayer { it.copy(viewport = it.viewport.copy(flippedH = !it.viewport.flippedH)) }
                pushHistory()
            }
            EditorEvent.FlipVertical -> {
                updateActiveLayer { it.copy(viewport = it.viewport.copy(flippedV = !it.viewport.flippedV)) }
                pushHistory()
            }
            is EditorEvent.UpdateShadow -> {
                updateActiveLayer { it.copy(appearance = it.appearance.copy(shadowIntensity = event.intensity.coerceIn(0f, 1f))) }
            }
            is EditorEvent.UpdateShadowAngle -> {
                updateActiveLayer { it.copy(appearance = it.appearance.copy(shadowAngle = event.angle.coerceIn(0f, 360f))) }
            }
            is EditorEvent.UpdateShadowDistance -> {
                updateActiveLayer { it.copy(appearance = it.appearance.copy(shadowDistance = event.distance.coerceIn(0f, 50f))) }
            }
            is EditorEvent.UpdateShadowColor -> {
                updateActiveLayer { it.copy(appearance = it.appearance.copy(shadowColorArgb = event.argb)) }
            }
            is EditorEvent.UpdateAlpha -> {
                updateActiveLayer { it.copy(appearance = it.appearance.copy(alpha = event.alpha.coerceIn(0.1f, 1f))) }
            }
            is EditorEvent.SetBoundingBoxVisible -> {
                // Not used anymore as bounding box depends on selectedLayerId
            }
            is EditorEvent.SelectTool -> {
                _state.update {
                    val nextTool = if (it.selectedTool?.javaClass == event.tool.javaClass) null else event.tool
                    it.copy(selectedTool = nextTool)
                }
            }
            is EditorEvent.SelectCropRatio -> {
                updateActiveLayer { it.copy(cropRatio = event.ratio) }
                pushHistory()
            }
            is EditorEvent.SelectLayer -> {
                _state.update { it.copy(selectedLayerId = event.layerId) }
            }
            EditorEvent.DuplicateLayer -> {
                val current = _state.value
                if (current.selectedLayerId == null) {
                    _state.update { it.copy(errorMessage = context.getString(R.string.studio_error_select_object)) }
                    return
                }
                val activeLayer = current.layers.find { it.id == current.selectedLayerId }
                if (activeLayer != null) {
                    val duplicatedLayer = activeLayer.copy(
                        id = java.util.UUID.randomUUID().toString(),
                        viewport = activeLayer.viewport.withOffset(
                            Offset(activeLayer.viewport.offset.x + 50f, activeLayer.viewport.offset.y + 50f)
                        )
                    )
                    _state.update { it.copy(layers = it.layers + duplicatedLayer, selectedLayerId = duplicatedLayer.id) }
                    pushHistory()
                }
            }
            EditorEvent.DeleteLayer -> {
                val currentLayerId = _state.value.selectedLayerId
                if (currentLayerId != null) {
                    _state.update { 
                        it.copy(
                            layers = it.layers.filterNot { layer -> layer.id == currentLayerId }, 
                            selectedLayerId = null
                        ) 
                    }
                    pushHistory()
                }
            }
            EditorEvent.CommitTransform -> pushHistory()
            EditorEvent.Undo -> undo()
            EditorEvent.Redo -> redo()
            EditorEvent.MoveLayerUp -> moveLayer(up = true)
            EditorEvent.MoveLayerDown -> moveLayer(up = false)
            is EditorEvent.Export -> export(event.templateAssetPath)
        }
    }

    private fun moveLayer(up: Boolean) {
        val currentLayerId = _state.value.selectedLayerId ?: return
        _state.update { state ->
            val mutableLayers = state.layers.toMutableList()
            val index = mutableLayers.indexOfFirst { it.id == currentLayerId }
            if (index == -1) return@update state

            val targetIndex = if (up) index + 1 else index - 1
            if (targetIndex in mutableLayers.indices) {
                java.util.Collections.swap(mutableLayers, index, targetIndex)
                state.copy(layers = mutableLayers)
            } else {
                state
            }
        }
        pushHistory()
    }

    private fun getInputStreamForPath(path: String): java.io.InputStream? {
        return when {
            path.startsWith("content://") || path.startsWith("file://") -> {
                if (path.startsWith("file:///android_asset/")) {
                    context.assets.open(path.removePrefix("file:///android_asset/"))
                } else {
                    context.contentResolver.openInputStream(Uri.parse(path))
                }
            }
            path.startsWith("http://") || path.startsWith("https://") -> {
                java.net.URL(path).openStream()
            }
            else -> {
                context.assets.open(path)
            }
        }
    }

    private fun com.thgiang.image.core.domain.model.template.CloudLayer.resolvedImageUrl(): String? {
        return payload.imageUrl ?: payload.defaultImageUrl
    }

    private fun com.thgiang.image.core.domain.model.template.CloudLayer.resolvedCropRatio(): CropRatio {
        return payload.cropRatio
            ?.let { value -> runCatching { CropRatio.valueOf(value) }.getOrNull() }
            ?: CropRatio.ORIGINAL
    }

    private fun loadCloudTemplate(cloudTemplate: com.thgiang.image.core.domain.model.template.CloudTemplate) {
        val assetPath = cloudTemplate.canvas.backgroundUrl ?: return
        
        savedStateHandle["template_path"] = assetPath
        
        templateLoadJob?.cancel()
        templateLoadJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                getInputStreamForPath(assetPath)?.use { input ->
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(input, null, opts)
                    
                    val tw = opts.outWidth
                    val th = opts.outHeight
                    
                    val editorLayers = cloudTemplate.layers.sortedBy { it.zIndex }.mapNotNull { cloudLayer ->
                        val imageUrl = cloudLayer.resolvedImageUrl() ?: return@mapNotNull null
                        val anchorX = cloudLayer.transform.anchorX
                        val anchorY = cloudLayer.transform.anchorY
                        
                        val offsetX = (anchorX - 0.5f) * tw.toFloat()
                        val offsetY = (anchorY - 0.5f) * th.toFloat()
                        
                        EditorLayer(
                            id = cloudLayer.layerId.ifEmpty { java.util.UUID.randomUUID().toString() },
                            product = EditorProduct(
                                originalUriString = imageUrl,
                                foregroundUriString = imageUrl,
                                isBackgroundRemoved = true,
                                baseWidth = cloudLayer.payload.baseWidth ?: 0,
                                baseHeight = cloudLayer.payload.baseHeight ?: 0,
                                isSample = cloudLayer.type == "PLACEHOLDER_OBJECT"
                            ),
                            viewport = EditorViewport(
                                offsetX = offsetX,
                                offsetY = offsetY,
                                scale = cloudLayer.transform.scale,
                                rotation = cloudLayer.transform.rotation,
                                flippedH = cloudLayer.payload.flippedH ?: false,
                                flippedV = cloudLayer.payload.flippedV ?: false
                            ),
                            appearance = EditorAppearance(
                                shadowIntensity = cloudLayer.payload.shadowIntensity ?: 0f,
                                alpha = cloudLayer.payload.alpha ?: 1f,
                                shadowAngle = cloudLayer.payload.shadowAngle ?: 45f,
                                shadowDistance = cloudLayer.payload.shadowDistance ?: 12f,
                                shadowColorArgb = cloudLayer.payload.shadowColorArgb ?: 0xFF000000.toInt()
                            ),
                            cropRatio = cloudLayer.resolvedCropRatio()
                        )
                    }
                    
                    _state.update {
                        it.copy(
                            template = EditorTemplate(
                                assetPath = assetPath,
                                originalWidth = tw,
                                originalHeight = th,
                                loaded = true
                            ),
                            layers = editorLayers,
                            selectedLayerId = editorLayers.firstOrNull { layer -> layer.product.isSample }?.id,
                            errorMessage = null
                        )
                    }
                } ?: throw Exception("Failed to open stream")
            } catch (e: Exception) {
                android.util.Log.e(TAG, "loadCloudTemplate failed to load template: $assetPath", e)
                _state.update { it.copy(errorMessage = context.getString(R.string.studio_error_load_cloud_template, e.message ?: "")) }
            }
        }
    }

    private fun loadTemplate(assetPath: String, objectSourceAssetPath: String? = null) {
        android.util.Log.d(TAG, "loadTemplate called with path: $assetPath, objectSourceAssetPath: $objectSourceAssetPath")
        if (_state.value.template.loaded && _state.value.template.assetPath == assetPath) {
            android.util.Log.d(TAG, "Template already loaded, ignoring template load but checking sample object.")
            val hasObject = _state.value.layers.any { it.product.foregroundUriString != null || it.product.processing }
            if (objectSourceAssetPath != null && !hasObject) {
                loadSampleObject(objectSourceAssetPath)
            }
            return
        }
        
        savedStateHandle["template_path"] = assetPath
        
        templateLoadJob?.cancel()
        templateLoadJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                android.util.Log.d(TAG, "Opening input stream for: $assetPath")
                getInputStreamForPath(assetPath)?.use { input ->
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
                            ),
                            errorMessage = null
                        )
                    }
                    android.util.Log.d(TAG, "Successfully loaded template state")
                    
                    if (objectSourceAssetPath != null) {
                        loadSampleObject(objectSourceAssetPath)
                    }
                } ?: throw Exception("Failed to open stream")
            } catch (e: Exception) {
                android.util.Log.e(TAG, "loadTemplate failed to load template: $assetPath", e)
                _state.update { it.copy(errorMessage = context.getString(R.string.studio_error_load_template, e.message ?: "")) }
            }
        }
    }

    private var sampleObjectLoadJob: Job? = null

    private fun loadSampleObject(assetPath: String) {
        sampleObjectLoadJob?.cancel()
        val processingId = java.util.UUID.randomUUID().toString()
        _state.update {
            it.copy(
                layers = it.layers + EditorLayer(id = processingId, product = EditorProduct(processing = true, isSample = true)),
                selectedLayerId = processingId
            )
        }
        sampleObjectLoadJob = viewModelScope.launch {
            try {
                android.util.Log.d(TAG, "loadSampleObject: extracting $assetPath")
                val cachedUri = sampleObjectCacheManager.getOrExtract(assetPath)
                if (cachedUri != null) {
                    val tw = _state.value.template.originalSize.width
                    val th = _state.value.template.originalSize.height
                    
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(cachedUri).use { input ->
                            BitmapFactory.decodeStream(input, null, options)
                        }
                    }
                    
                    val w = options.outWidth
                    val h = options.outHeight
                    android.util.Log.d(TAG, "loadSampleObject: cached bounds $w x $h")
                    
                    val baseSize = if (tw > 0 && th > 0 && w > 0 && h > 0) {
                        val baseFitScale = kotlin.math.min(
                            tw.toFloat() / w,
                            th.toFloat() / h
                        )
                        IntSize(
                            (w * baseFitScale).toInt(),
                            (h * baseFitScale).toInt()
                        )
                    } else {
                        IntSize(w.coerceAtLeast(0), h.coerceAtLeast(0))
                    }
                    
                    _state.update { state ->
                        val newProduct = EditorProduct(
                            originalUriString = cachedUri.toString(),
                            foregroundUriString = cachedUri.toString(),
                            isBackgroundRemoved = true,
                            baseWidth = baseSize.width,
                            baseHeight = baseSize.height,
                            processing = false,
                            isSample = true
                        )
                        val updatedLayers = state.layers.map {
                            if (it.id == processingId) it.copy(product = newProduct, viewport = EditorViewport(scale = 1f)) else it
                        }
                        state.copy(
                            layers = updatedLayers,
                            showBoundingBox = true
                        )
                    }
                    triggerOverlay()
                    pushHistory()
                } else {
                    _state.update { state -> 
                        state.copy(layers = state.layers.filterNot { it.id == processingId }, selectedLayerId = if (state.selectedLayerId == processingId) null else state.selectedLayerId) 
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to load sample object: $assetPath", e)
                _state.update { state ->
                    state.copy(
                        layers = state.layers.filterNot { it.id == processingId },
                        selectedLayerId = if (state.selectedLayerId == processingId) null else state.selectedLayerId,
                        errorMessage = context.getString(R.string.studio_error_load_sample_product)
                    )
                }
            }
        }
    }

    private fun addSticker(assetPath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val templateSize = _state.value.template.originalSize
                val stickerPath = "file:///android_asset/$assetPath"

                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                val decoded = getInputStreamForPath(stickerPath)?.use { input ->
                    BitmapFactory.decodeStream(input, null, options)
                    options.outWidth > 0 && options.outHeight > 0
                } == true

                val stickerWidth = if (decoded) options.outWidth.coerceAtLeast(1) else 512
                val stickerHeight = if (decoded) options.outHeight.coerceAtLeast(1) else 512
                val maxStickerDim = maxOf(stickerWidth, stickerHeight).toFloat()
                val targetSize = if (templateSize.width > 0 && templateSize.height > 0) {
                    minOf(templateSize.width, templateSize.height) * 0.28f
                } else {
                    maxStickerDim * 0.35f
                }
                val initialScale = (targetSize / maxStickerDim).coerceIn(0.15f, 1.4f)

                val layerId = java.util.UUID.randomUUID().toString()
                val stickerLayer = EditorLayer(
                    id = layerId,
                    product = EditorProduct(
                        originalUriString = stickerPath,
                        foregroundUriString = stickerPath,
                        isBackgroundRemoved = true,
                        baseWidth = stickerWidth,
                        baseHeight = stickerHeight,
                        processing = false,
                        isSample = false
                    ),
                    viewport = EditorViewport(scale = initialScale),
                    appearance = EditorAppearance(
                        shadowIntensity = 0f,
                        alpha = 1f,
                        shadowAngle = 45f,
                        shadowDistance = 12f,
                        shadowColorArgb = 0xFF000000.toInt()
                    ),
                    cropRatio = CropRatio.ORIGINAL
                )

                _state.update { state ->
                    state.copy(
                        layers = state.layers + stickerLayer,
                        selectedLayerId = layerId,
                        showBoundingBox = true,
                        errorMessage = null
                    )
                }
                pushHistory()
            } catch (e: Exception) {
                android.util.Log.e(TAG, "addSticker failed for: $assetPath", e)
                _state.update { it.copy(errorMessage = context.getString(R.string.studio_error_unknown)) }
            }
        }
    }

    // ============ Product Image with Atomic State Updates ============

    private fun setProductImage(uri: Uri, replaceLayerId: String?) {
        bgRemoveJob?.cancel()
        
        val processingId = replaceLayerId ?: java.util.UUID.randomUUID().toString()
        _state.update { state ->
            val newLayerTemplate = EditorLayer(id = processingId, product = EditorProduct(originalUriString = uri.toString(), processing = true))
            val newLayers = if (replaceLayerId != null && state.layers.any { it.id == replaceLayerId }) {
                state.layers.map { if (it.id == replaceLayerId) newLayerTemplate.copy(viewport = it.viewport) else it }
            } else {
                state.layers + newLayerTemplate
            }
            state.copy(
                layers = newLayers,
                selectedLayerId = processingId,
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
                    _state.update { state -> state.copy(layers = state.layers.filterNot { it.id == processingId }) }
                    return@launch
                }

                // Step 2: Background removal (ML inference → Default/ML dispatcher)
                val foreground = withContext(Dispatchers.Default) {
                    backgroundRemoverRepository.getForegroundBitmap(decoded).getOrNull()
                }

                if (foreground == null) {
                    decoded.recycle()
                    _state.update { state -> state.copy(layers = state.layers.filterNot { it.id == processingId }) }
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
                        var finalWidth = (foreground.width * baseFitScale).toInt()
                        var finalHeight = (foreground.height * baseFitScale).toInt()
                        if (finalWidth > 500) {
                            val downScale = 500f / finalWidth
                            finalWidth = 500
                            finalHeight = (finalHeight * downScale).toInt()
                        }
                        IntSize(finalWidth, finalHeight)
                    } else {
                        var finalWidth = foreground.width
                        var finalHeight = foreground.height
                        if (finalWidth > 500) {
                            val downScale = 500f / finalWidth
                            finalWidth = 500
                            finalHeight = (finalHeight * downScale).toInt()
                        }
                        IntSize(finalWidth, finalHeight)
                    }

                    _state.update { state ->
                        val newProduct = EditorProduct(
                                originalUriString = uri.toString(),
                                foregroundUriString = cachedUri.toString(),
                                isBackgroundRemoved = true,
                                baseWidth = baseSize.width,
                                baseHeight = baseSize.height,
                                processing = false
                            )
                        val updatedLayers = state.layers.map {
                            if (it.id == processingId) it.copy(product = newProduct) else it
                        }
                        state.copy(
                            layers = updatedLayers,
                            showBoundingBox = true
                        )
                    }
                    triggerOverlay()
                    pushHistory()
                } else {
                    _state.update { state -> state.copy(layers = state.layers.filterNot { it.id == processingId }) }
                }
                
                // Cleanup bitmaps
                foreground.recycle()
                decoded.recycle()
                
            } catch (e: CancellationException) {
                throw e // Don't swallow cancellation
            } catch (e: Exception) {
                android.util.Log.e(TAG, "removeBackground failed", e)
                _state.update { state ->
                    state.copy(
                        layers = state.layers.filterNot { it.id == processingId },
                        errorMessage = e.message ?: context.getString(R.string.studio_error_process_image)
                    ) 
                }
            }
        }
    }

    // ============ Transform Operations ============
    
    private fun updateScale(factor: Float) {
        updateActiveLayer { layer ->
            val newScale = (layer.viewport.scale * factor).coerceIn(MIN_SCALE, MAX_SCALE)
            layer.copy(viewport = layer.viewport.withScale(newScale))
        }
    }

    private fun updateRotation(delta: Float) {
        updateActiveLayer { layer ->
            var newRotation = (layer.viewport.rotation + delta) % 360f
            if (newRotation < 0) newRotation += 360f
            
            val snapped = SNAP_ANGLES.minByOrNull { angle -> abs(angle - newRotation) }
            if (snapped != null && abs(snapped - newRotation) < SNAP_ANGLE_THRESHOLD) {
                newRotation = snapped
            }
            
            layer.copy(viewport = layer.viewport.withRotation(newRotation))
        }
    }

    private fun applyGestureDelta(delta: GestureDelta) {
        updateActiveLayer { layer ->
            var newViewport = layer.viewport
            
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
            
            layer.copy(viewport = newViewport)
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
                layers = current.layers
            )
        )
    }

    private fun undo() {
        historyManager.undo()?.let { snapshot ->
            _state.update {
                it.copy(layers = snapshot.layers)
            }
        }
    }

    private fun redo() {
        historyManager.redo()?.let { snapshot ->
            _state.update {
                it.copy(layers = snapshot.layers)
            }
        }
    }

    // ============ Export with Cancellation Support ============
    
    private fun export(templateAssetPath: String) {
        if (_state.value.isExporting || !_state.value.canExport) return
        
        val currentState = _state.value
        val templateSize = currentState.template.originalSize
        
        if (templateSize.width == 0 || templateSize.height == 0) return

        exportJob?.cancel()
        exportJob = viewModelScope.launch {
            _state.update { it.copy(isExporting = true, errorMessage = null) }
            
            try {
                // Prepare multiple RenderRequest (one per layer) or adapt EditorRenderer to handle list
                val result = renderer.renderLayers(
                    EditorRenderer.MultiLayerRenderRequest(
                        templateAssetPath = templateAssetPath,
                        templateSize = templateSize,
                        layers = currentState.layers.filter { it.product.foregroundUri != null }
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
                                it.copy(isExporting = false, errorMessage = context.getString(R.string.studio_error_save_image)) 
                            }
                        }
                    },
                    onFailure = { e ->
                        _state.update { 
                            it.copy(isExporting = false, errorMessage = context.getString(R.string.studio_error_render_failed, e.message ?: "")) 
                        }
                    }
                )
            } catch (e: CancellationException) {
                _state.update { it.copy(isExporting = false) }
                throw e
            } catch (e: Exception) {
                _state.update { 
                    it.copy(isExporting = false, errorMessage = e.message ?: context.getString(R.string.studio_error_unknown)) 
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
