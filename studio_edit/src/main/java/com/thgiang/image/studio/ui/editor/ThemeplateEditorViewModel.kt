package com.thgiang.image.studio.ui.editor

import android.content.Context
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thgiang.image.core.domain.model.template.CloudTemplate
import com.thgiang.image.studio.R
import com.thgiang.image.studio.data.TemplateDraftRepository
import com.thgiang.image.studio.ui.editor.export.EditorExportCoordinator
import com.thgiang.image.studio.ui.editor.export.ExportOutcome
import com.thgiang.image.studio.ui.editor.export.SaveDraftOutcome
import com.thgiang.image.studio.ui.editor.load.EditorTemplateLoader
import com.thgiang.image.studio.ui.editor.product.EditorProductWorkflow
import com.thgiang.image.studio.ui.editor.product.ProductImageResult
import com.thgiang.image.studio.ui.editor.product.SampleObjectResult
import com.thgiang.image.studio.ui.editor.product.StickerResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import kotlin.math.abs

@HiltViewModel
class ThemeplateEditorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val templateLoader: EditorTemplateLoader,
    private val exportCoordinator: EditorExportCoordinator,
    private val templateDraftRepository: TemplateDraftRepository,
    private val historyManager: EditorHistoryManager,
    private val productWorkflow: EditorProductWorkflow,
    private val layerFactory: EditorLayerFactory,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val TAG = "EditorVM"
        private const val BG_DEBUG_TAG = "TPL_BG_DEBUG"
        private const val MIN_SCALE = 0.2f
        private const val MAX_SCALE = 5f
        private const val SNAP_ANGLE_THRESHOLD = 5f
        private val SNAP_ANGLES = listOf(0f, 90f, 180f, 270f)
        private const val HISTORY_DEBOUNCE_MS = 300L
        private const val GESTURE_THROTTLE_MS = 16L // ~60fps
    }

    val draftId: String? = savedStateHandle.get<String>("draftId")
    
    private val _state = MutableStateFlow(
        (templateDraftRepository.loadDraft(draftId ?: "") ?: EditorState()).let { restored ->
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
    private var saveDraftJob: Job? = null
    private var overlayJob: Job? = null

    val themeplateId: String? = savedStateHandle.get<String>("themeplateId")

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
        val savedPath = savedStateHandle.get<String>("template_path")
        if (savedPath != null) {
            if (!_state.value.template.loaded) {
                loadTemplate(savedPath)
            }
        } else {
            val tId = themeplateId
            if (!tId.isNullOrEmpty() && tId != "draft") {
                if (!_state.value.template.loaded) {
                    loadCloudTemplateById(tId)
                }
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
            is EditorEvent.LoadCloudTemplateById -> loadCloudTemplateById(event.templateId)
            is EditorEvent.SetProductImage -> setProductImage(event.uri, event.replaceLayerId)
            is EditorEvent.AddSticker -> addSticker(event.assetPath)
            EditorEvent.AddTextLayer -> addTextLayer()
            is EditorEvent.AddShapeTextLayer -> addShapeTextLayer(event.shapeType)
            is EditorEvent.UpdateShapeText -> {
                updateActiveLayer { it.copy(text = event.text) }
                requestHistoryPush()
            }
            is EditorEvent.UpdateShapeColor -> {
                updateActiveLayer { it.copy(shapeColorArgb = event.argb, fillGradient = null) }
                requestHistoryPush()
            }
            is EditorEvent.UpdateTextColor -> {
                updateActiveLayer { it.copy(textColorArgb = event.argb, textColorGradient = null) }
                requestHistoryPush()
            }
            is EditorEvent.UpdateTextSize -> {
                updateActiveLayer { it.copy(textSizeSp = event.sizeSp.coerceIn(1f, 500f)) }
                requestHistoryPush()
            }
            is EditorEvent.UpdateTextFontFamily -> {
                updateActiveLayer { it.copy(fontFamily = event.fontFamily?.takeIf { family -> family.isNotBlank() }) }
                requestHistoryPush()
            }
            is EditorEvent.UpdateShapeType -> {
                updateActiveLayer { it.copy(shapeType = event.shapeType) }
                requestHistoryPush()
            }
            is EditorEvent.UpdateTextBold -> {
                updateActiveLayer {
                    it.copy(fontWeight = if (event.bold) "bold" else "normal")
                }
                requestHistoryPush()
            }
            is EditorEvent.UpdateTextItalic -> {
                updateActiveLayer {
                    it.copy(fontStyle = if (event.italic) "italic" else "normal")
                }
                requestHistoryPush()
            }
            is EditorEvent.UpdateTextUnderline -> {
                updateActiveLayer { it.copy(underline = event.underline) }
                requestHistoryPush()
            }
            is EditorEvent.UpdateTextLinethrough -> {
                updateActiveLayer { it.copy(linethrough = event.linethrough) }
                requestHistoryPush()
            }
            is EditorEvent.UpdateTextAlign -> {
                updateActiveLayer { it.copy(textAlign = event.align) }
                requestHistoryPush()
            }
            is EditorEvent.UpdateLineHeight -> {
                updateActiveLayer {
                    it.copy(lineHeight = event.multiplier.coerceIn(0.5f, 3f))
                }
                requestHistoryPush()
            }
            is EditorEvent.UpdateCharSpacing -> {
                updateActiveLayer { it.copy(charSpacing = event.spacing.coerceIn(-20f, 80f)) }
                requestHistoryPush()
            }
            is EditorEvent.UpdateTextTransform -> {
                updateActiveLayer { it.copy(textTransform = event.transform) }
                requestHistoryPush()
            }
            is EditorEvent.UpdateFillGradient -> {
                updateActiveLayer { it.copy(fillGradient = event.gradient) }
                requestHistoryPush()
            }
            is EditorEvent.UpdateTextColorGradient -> {
                updateActiveLayer { it.copy(textColorGradient = event.gradient) }
                requestHistoryPush()
            }
            is EditorEvent.ApplyLabelTypographyPreset -> {
                updateActiveLayer {
                    it.copy(
                        fontWeight = event.fontWeight,
                        textSizeSp = event.textSizeSp.coerceIn(1f, 500f),
                        textTransform = event.textTransform,
                    )
                }
                requestHistoryPush()
            }
            is EditorEvent.UpdateStrokeColor -> {
                updateActiveLayer { it.copy(strokeColorArgb = event.argb) }
                requestHistoryPush()
            }
            is EditorEvent.UpdateStrokeWidth -> {
                updateActiveLayer { it.copy(strokeWidthPx = event.widthPx.coerceIn(0f, 20f)) }
                requestHistoryPush()
            }
            is EditorEvent.UpdateCornerRadius -> {
                updateActiveLayer {
                    val radius = event.radiusPx.coerceAtLeast(0f)
                    it.copy(cornerRadiusX = radius, cornerRadiusY = radius)
                }
                requestHistoryPush()
            }
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
                val requiresLayer = event.tool is EditorTool.Rotate ||
                        event.tool is EditorTool.Shadow ||
                        event.tool is EditorTool.Transparency
                
                if (requiresLayer && _state.value.selectedLayerId == null) {
                    _state.update { it.copy(errorMessage = context.getString(R.string.studio_error_select_object)) }
                } else {
                    _state.update {
                        val nextTool = if (it.selectedTool?.javaClass == event.tool.javaClass) null else event.tool
                        it.copy(selectedTool = nextTool)
                    }
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
                } else {
                    _state.update { it.copy(errorMessage = context.getString(R.string.studio_error_select_object)) }
                }
            }
            EditorEvent.CommitTransform -> pushHistory()
            EditorEvent.Undo -> undo()
            EditorEvent.Redo -> redo()
            EditorEvent.MoveLayerUp -> moveLayer(up = true)
            EditorEvent.MoveLayerDown -> moveLayer(up = false)
            EditorEvent.SaveDraft -> saveDraft()
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

    private fun addShapeTextLayer(shapeType: ShapeType) {
        val layer = layerFactory.createShapeTextLayer(_state.value.template.originalSize.width.toFloat(), shapeType)
        val layerId = layer.id
        _state.update {
            it.copy(
                layers = it.layers + layer,
                selectedLayerId = layerId,
                selectedTool = EditorTool.Label
            )
        }
        pushHistory()
    }

    private fun addTextLayer() {
        val layer = layerFactory.createTextLayer(_state.value.template.originalSize.width.toFloat())
        val layerId = layer.id
        _state.update {
            it.copy(
                layers = it.layers + layer,
                selectedLayerId = layerId,
                selectedTool = EditorTool.Label
            )
        }
        pushHistory()
    }

    private fun applyLoadedTemplate(loaded: com.thgiang.image.studio.ui.editor.load.LoadedEditorTemplate) {
        _state.update {
            it.copy(
                template = loaded.template,
                layers = loaded.layers.ifEmpty { it.layers },
                selectedLayerId = loaded.selectedLayerId ?: it.selectedLayerId,
                errorMessage = null,
            )
        }
    }

    private fun loadCloudTemplate(cloudTemplate: CloudTemplate) {
        val assetPath = cloudTemplate.canvas.backgroundUrl.orEmpty()
        android.util.Log.d(
            BG_DEBUG_TAG,
            "load parsed templateId=${cloudTemplate.templateId} backgroundUrl=${assetPath.take(160)} layers=${cloudTemplate.layers.size}"
        )

        savedStateHandle["template_path"] = assetPath
        templateLoadJob?.cancel()
        templateLoadJob = viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                templateLoader.buildFromCloud(cloudTemplate)
            }.onSuccess { loaded ->
                applyLoadedTemplate(loaded)
                android.util.Log.d(
                    BG_DEBUG_TAG,
                    "state applied templateId=${cloudTemplate.templateId} editorLayers=${loaded.layers.size}"
                )
            }.onFailure { e ->
                android.util.Log.e(TAG, "loadCloudTemplate failed: $assetPath", e)
                _state.update {
                    it.copy(errorMessage = context.getString(R.string.studio_error_load_cloud_template, e.message ?: ""))
                }
            }
        }
    }

    private fun loadTemplate(assetPath: String, objectSourceAssetPath: String? = null) {
        if (assetPath.isBlank()) return

        if (assetPath == _state.value.template.assetPath && _state.value.template.loaded) {
            if (objectSourceAssetPath != null) {
                loadSampleObject(objectSourceAssetPath)
            }
            return
        }

        savedStateHandle["template_path"] = assetPath
        templateLoadJob?.cancel()
        templateLoadJob = viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                templateLoader.probeLocalAsset(assetPath)
            }.onSuccess { loaded ->
                _state.update {
                    it.copy(template = loaded.template, errorMessage = null)
                }
                android.util.Log.d(TAG, "Successfully loaded template state")
                if (objectSourceAssetPath != null) {
                    loadSampleObject(objectSourceAssetPath)
                }
            }.onFailure { e ->
                android.util.Log.e(TAG, "loadTemplate failed to load template: $assetPath", e)
                _state.update {
                    it.copy(errorMessage = context.getString(R.string.studio_error_load_template, e.message ?: ""))
                }
            }
        }
    }

    private var sampleObjectLoadJob: Job? = null

    private fun loadSampleObject(assetPath: String) {
        sampleObjectLoadJob?.cancel()
        val processingId = productWorkflow.newProcessingId()
        _state.update {
            it.copy(
                layers = it.layers + productWorkflow.buildSampleProcessingLayer(processingId),
                selectedLayerId = processingId,
            )
        }
        sampleObjectLoadJob = viewModelScope.launch {
            try {
                android.util.Log.d(TAG, "loadSampleObject: extracting $assetPath")
                when (val result = productWorkflow.loadSampleObject(assetPath)) {
                    is SampleObjectResult.Ready -> {
                        _state.update { state ->
                            val updatedLayers = state.layers.map {
                                if (it.id == processingId) {
                                    it.copy(product = result.product, viewport = EditorViewport(scale = 1f))
                                } else {
                                    it
                                }
                            }
                            state.copy(layers = updatedLayers, showBoundingBox = true)
                        }
                        triggerOverlay()
                        pushHistory()
                    }
                    SampleObjectResult.NotFound -> removeProcessingLayer(processingId)
                    is SampleObjectResult.Failed -> removeProcessingLayer(processingId, result.message)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to load sample object: $assetPath", e)
                removeProcessingLayer(
                    processingId,
                    context.getString(R.string.studio_error_load_sample_product),
                )
            }
        }
    }

    private fun addSticker(assetPath: String) {
        viewModelScope.launch {
            try {
                when (val result = productWorkflow.buildStickerLayer(assetPath, _state.value.template.originalSize)) {
                    is StickerResult.Ready -> {
                        _state.update { state ->
                            state.copy(
                                layers = state.layers + result.layer,
                                selectedLayerId = result.layer.id,
                                showBoundingBox = true,
                                errorMessage = null,
                            )
                        }
                        pushHistory()
                    }
                    is StickerResult.Failure -> {
                        _state.update { it.copy(errorMessage = result.message) }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "addSticker failed for: $assetPath", e)
                _state.update { it.copy(errorMessage = context.getString(R.string.studio_error_unknown)) }
            }
        }
    }

    // ============ Product Image with Atomic State Updates ============

    private fun setProductImage(uri: Uri, replaceLayerId: String?) {
        bgRemoveJob?.cancel()

        val update = productWorkflow.buildProcessingLayerUpdate(uri, replaceLayerId, _state.value.layers)
        _state.update {
            it.copy(
                layers = update.layers,
                selectedLayerId = update.processingId,
                exportResult = null,
                errorMessage = null,
                showBoundingBox = false,
            )
        }
        historyManager.clear()

        bgRemoveJob = viewModelScope.launch {
            try {
                when (val result = productWorkflow.processUserImage(uri)) {
                    is ProductImageResult.Ready -> {
                        _state.update { state ->
                            val updatedLayers = state.layers.map {
                                if (it.id == update.processingId) it.copy(product = result.product) else it
                            }
                            state.copy(layers = updatedLayers, showBoundingBox = true)
                        }
                        triggerOverlay()
                        pushHistory()
                    }
                    is ProductImageResult.Failed -> {
                        removeProcessingLayer(
                            update.processingId,
                            result.message ?: context.getString(R.string.studio_error_process_image),
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e(TAG, "removeBackground failed", e)
                removeProcessingLayer(
                    update.processingId,
                    e.message ?: context.getString(R.string.studio_error_process_image),
                )
            }
        }
    }

    private fun removeProcessingLayer(processingId: String, errorMessage: String? = null) {
        _state.update { state ->
            state.copy(
                layers = state.layers.filterNot { it.id == processingId },
                selectedLayerId = if (state.selectedLayerId == processingId) null else state.selectedLayerId,
                errorMessage = errorMessage ?: state.errorMessage,
            )
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
        if (currentState.template.originalSize.width == 0 || currentState.template.originalSize.height == 0) return

        exportJob?.cancel()
        exportJob = viewModelScope.launch {
            _state.update { it.copy(isExporting = true, errorMessage = null) }

            try {
                when (val outcome = exportCoordinator.export(currentState, templateAssetPath)) {
                    is ExportOutcome.Success -> {
                        _state.update { it.copy(isExporting = false, exportResult = outcome.uri) }
                    }
                    is ExportOutcome.Failure -> {
                        _state.update { it.copy(isExporting = false, errorMessage = outcome.message) }
                    }
                }
            } catch (e: CancellationException) {
                _state.update { it.copy(isExporting = false) }
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isExporting = false,
                        errorMessage = e.message ?: context.getString(R.string.studio_error_unknown),
                    )
                }
            }
        }
    }

    // ============ Save Draft ============

    private fun saveDraft() {
        val currentState = _state.value
        val templateAssetPath = currentState.template.assetPath
        if (currentState.template.originalSize.width == 0 || currentState.template.originalSize.height == 0) return

        saveDraftJob?.cancel()
        saveDraftJob = viewModelScope.launch {
            when (val outcome = exportCoordinator.saveDraft(currentState, draftId, templateAssetPath)) {
                is SaveDraftOutcome.Success -> {
                    if (draftId == null) {
                        savedStateHandle["draftId"] = outcome.draftId
                    }
                    _state.update { it.copy(draftSavedAt = outcome.savedAt) }
                }
                is SaveDraftOutcome.Failure -> outcome.error.printStackTrace()
            }
        }
    }

    // ============ SavedState Persistence ============

    private fun persistState() {
        savedStateHandle["editor_state_template_path"] = _state.value.template.assetPath
        savedStateHandle["editor_state_selected_layer_id"] = _state.value.selectedLayerId
        savedStateHandle["editor_state_selected_tool"] = _state.value.selectedTool?.javaClass?.name
        savedStateHandle["editor_state_show_overlay"] = _state.value.showOverlay
        savedStateHandle["editor_state_show_bounding_box"] = _state.value.showBoundingBox
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

    private fun loadCloudTemplateById(templateId: String) {
        if (_state.value.template.loaded) return
        templateLoadJob?.cancel()
        templateLoadJob = viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(errorMessage = null) }
            runCatching {
                templateLoader.fetchAndBuild(templateId)
            }.onSuccess { loaded ->
                savedStateHandle["template_path"] = loaded.template.assetPath
                applyLoadedTemplate(loaded)
                android.util.Log.d(
                    BG_DEBUG_TAG,
                    "loaded templateId=$templateId layers=${loaded.layers.size}"
                )
            }.onFailure { e ->
                android.util.Log.e(TAG, "Failed to load remote template $templateId", e)
                _state.update {
                    it.copy(
                        errorMessage = context.getString(
                            R.string.studio_error_load_cloud_template,
                            e.message ?: ""
                        )
                    )
                }
            }
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
