package com.thgiang.image.studio.ui.editor
import com.thgiang.image.studio.ui.editor.*
import com.thgiang.image.studio.ui.editor.label.factory.*
import com.thgiang.image.studio.ui.editor.canvas.*

import android.content.Context
import com.thgiang.image.studio.ui.editor.model.*
import com.thgiang.image.studio.ui.editor.label.model.ShapeLabelDefaults
import com.thgiang.image.studio.ui.editor.label.model.withShapeFittedToText
import com.thgiang.image.studio.ui.editor.label.model.withShapeHeightFittedToText
import com.thgiang.image.studio.ui.editor.model.preferredEditorTool
import com.thgiang.image.studio.ui.editor.model.isLabelLayer
import com.thgiang.image.studio.ui.editor.model.EditorLayerNormalizer
import com.thgiang.image.studio.ui.editor.canvas.LayerAlignment
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thgiang.image.core.domain.model.template.CloudTemplate
import com.thgiang.image.studio.R
import com.thgiang.image.studio.data.TemplateDraftRepository
import com.thgiang.image.studio.data.RemoteSticker
import com.thgiang.image.studio.data.StickerRemoteRepository
import com.thgiang.image.studio.ui.editor.export.EditorExportCoordinator
import com.thgiang.image.studio.ui.editor.export.ExportOutcome
import com.thgiang.image.studio.ui.editor.export.SaveDraftOutcome
import com.thgiang.image.studio.ui.editor.load.EditorTemplateLoader
import com.thgiang.image.studio.ui.editor.product.EditorProductWorkflow
import com.thgiang.image.studio.ui.editor.product.ProductImageResult
import com.thgiang.image.studio.ui.editor.product.SampleObjectResult
import com.thgiang.image.studio.ui.editor.product.StickerResult
import com.thgiang.image.studio.ui.editor.label.LabelViewModelDelegate
import com.thgiang.image.studio.ui.editor.label.model.LabelStyleClipboard
import com.thgiang.image.studio.ui.editor.shape.ShapeViewModelDelegate
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import kotlin.math.abs

/**
 * UI state cho khu vực Sticker picker.
 */
data class StickerUiState(
    val previewMeme: List<RemoteSticker> = emptyList(),
    val previewDecor: List<RemoteSticker> = emptyList(),
    val isLoadingPreview: Boolean = false,
    val previewError: Boolean = false,
)

@HiltViewModel
class ThemeplateEditorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val templateLoader: EditorTemplateLoader,
    private val exportCoordinator: EditorExportCoordinator,
    private val templateDraftRepository: TemplateDraftRepository,
    private val historyManager: EditorHistoryManager,
    private val productWorkflow: EditorProductWorkflow,
    private val layerFactory: EditorLayerFactory,
    private val stickerRepository: StickerRemoteRepository,
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
        private const val SHAPE_FIT_DEBOUNCE_MS = 200L
        private const val GESTURE_THROTTLE_MS = 16L // ~60fps
    }

    // Clipboard: stores style of last copied layer (fill, stroke, shadow)
    private var styleClipboard: LabelStyleClipboard? = null

    val draftId: String? = savedStateHandle.get<String>("draftId")
    
    private val _state = MutableStateFlow(
        (templateDraftRepository.loadDraft(draftId ?: "") ?: EditorState()).let { restored ->
            val layers = EditorLayerNormalizer.normalize(restored.layers ?: emptyList())
            val (sel, edit) = LabelInteractionState.normalize(
                restored.selectedLayerId,
                restored.editingLayerId,
                layers,
            )
            restored.copy(
                template = restored.template ?: EditorTemplate(),
                selectedTool = restored.selectedTool,
                layers = layers,
                selectedLayerId = sel,
                editingLayerId = edit,
            )
        }
    )
    val state: StateFlow<EditorState> = _state.asStateFlow()

    val canUndo: StateFlow<Boolean> = historyManager.canUndo
    val canRedo: StateFlow<Boolean> = historyManager.canRedo

    // ─── Sticker state ────────────────────────────────────────────
    private val _stickerState = MutableStateFlow(StickerUiState())
    val stickerState: StateFlow<StickerUiState> = _stickerState.asStateFlow()

    private var stickerPreviewJob: Job? = null

    // Debounced history push for gesture operations
    private val historyPushFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val shapeFitFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val gestureThrottleFlow = MutableSharedFlow<GestureDelta>(extraBufferCapacity = 1)

    private var templateLoadJob: Job? = null
    private var bgRemoveJob: Job? = null
    private var exportJob: Job? = null
    private var historyPushJob: Job? = null
    private var saveDraftJob: Job? = null
    private var overlayJob: Job? = null

    val themeplateId: String? = savedStateHandle.get<String>("themeplateId")

    val labelDelegate by lazy {
        LabelViewModelDelegate(
            context = context,
            layerFactory = layerFactory,
            shapeFitFlow = shapeFitFlow,
            readState = { _state.value },
            updateState = { block -> _state.update { s -> block(s) } },
            requestHistoryPush = { historyPushFlow.tryEmit(Unit) },
            pushHistory = { historyPushJob?.cancel(); historyPushJob = viewModelScope.launch { pushHistoryInternal() } },
        )
    }

    val shapeDelegate by lazy {
        ShapeViewModelDelegate(
            layerFactory = layerFactory,
            shapeFitFlow = shapeFitFlow,
            readState = { _state.value },
            updateState = { block -> _state.update { s -> block(s) } },
            requestHistoryPush = { historyPushFlow.tryEmit(Unit) },
            pushHistory = { historyPushJob?.cancel(); historyPushJob = viewModelScope.launch { pushHistoryInternal() } },
        )
    }

    init {
        // Debounced history push
        viewModelScope.launch {
            historyPushFlow
                .debounce(HISTORY_DEBOUNCE_MS)
                .collect { pushHistoryInternal() }
        }

        viewModelScope.launch {
            shapeFitFlow
                .debounce(SHAPE_FIT_DEBOUNCE_MS)
                .collect { applyShapeFitToActiveLayer() }
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

    private fun updateActiveLayer(block: (EditorLayer) -> EditorLayer) {
        _state.update { state ->
            val layerId = state.selectedLayerId ?: return@update state
            state.copy(layers = LayerGroupSync.apply(state.layers, layerId, block))
        }
    }

    private fun applyShapeFitToActiveLayer() {
        updateActiveLayer { layer ->
            if (layer.isLabelLayer) {
                layer.withShapeFittedToText(context)
            } else {
                layer
            }
        }
    }

    private fun refitActiveLabelHeightToText() {
        updateActiveLayer { layer ->
            if (layer.isLabelLayer && !layer.textForm.isActive) {
                layer.withShapeHeightFittedToText(context)
            } else {
                layer
            }
        }
    }

    fun onEvent(event: EditorEvent) {
        when (event) {
            is EditorEvent.LoadTemplate -> loadTemplate(event.assetPath, event.objectSourceAssetPath)
            is EditorEvent.LoadCloudTemplate -> loadCloudTemplate(event.cloudTemplate)
            is EditorEvent.LoadCloudTemplateById -> loadCloudTemplateById(event.templateId)
            is EditorEvent.SetProductImage -> setProductImage(event.uri, event.replaceLayerId)
            is EditorEvent.AddSticker -> addSticker(event.assetPath)
            // ── Label events (delegated) ──────────────────
            EditorEvent.AddTextLayer ->
                labelDelegate.addTextLayer(_state.value.template.originalSize.width.toFloat())
            is EditorEvent.AddShapeTextLayer ->
                labelDelegate.addShapeTextLayer(event.shapeType, _state.value.template.originalSize.width.toFloat())
            is EditorEvent.ConfirmAddLabel ->
                labelDelegate.confirmAddLabel(event.shapeType, _state.value.template.originalSize.width.toFloat())
            is EditorEvent.ConfirmAddLabelText ->
                labelDelegate.confirmAddLabelWithText(event.text, _state.value.template.originalSize.width.toFloat())
            EditorEvent.DismissLabelTool -> labelDelegate.dismissLabelTool()
            // ── Shape events (delegated) ──────────────────
            is EditorEvent.AddShapeLayer ->
                shapeDelegate.addShapeLayer(event.shapeType, _state.value.template.originalSize.width.toFloat())
            is EditorEvent.ConfirmAddShape ->
                shapeDelegate.confirmAddShape(event.shapeType, _state.value.template.originalSize.width.toFloat())
            EditorEvent.DismissShapeTool -> shapeDelegate.dismissShapeTool()
            // ── Label edit events ─────────────────────────
            is EditorEvent.UpdateShapeText -> labelDelegate.updateShapeText(event.text)
            EditorEvent.InsertTextNewline -> labelDelegate.insertTextNewline()
            is EditorEvent.UpdateShapeColor -> shapeDelegate.updateShapeColor(event.argb)
            is EditorEvent.UpdateTextColor -> labelDelegate.updateTextColor(event.argb)
            is EditorEvent.UpdateTextSize -> labelDelegate.updateTextSize(event.sizeSp)
            is EditorEvent.UpdateTextFontFamily -> labelDelegate.updateTextFontFamily(event.fontFamily)
            is EditorEvent.UpdateShapeType -> {
                val layer = _state.value.layers.find { it.id == _state.value.selectedLayerId }
                if (layer?.isLabelLayer == true) {
                    labelDelegate.updateShapeType(event.shapeType)
                } else {
                    shapeDelegate.updateShapeType(event.shapeType)
                }
            }
            is EditorEvent.UpdateTextBold -> labelDelegate.updateTextBold(event.bold)
            is EditorEvent.UpdateTextItalic -> labelDelegate.updateTextItalic(event.italic)
            is EditorEvent.UpdateTextUnderline -> labelDelegate.updateTextUnderline(event.underline)
            is EditorEvent.UpdateTextLinethrough -> labelDelegate.updateTextLinethrough(event.linethrough)
            is EditorEvent.UpdateTextAlign -> labelDelegate.updateTextAlign(event.align)
            is EditorEvent.UpdateLineHeight -> labelDelegate.updateLineHeight(event.multiplier)
            is EditorEvent.UpdateCharSpacing -> labelDelegate.updateCharSpacing(event.spacing)
            is EditorEvent.UpdateTextTransform -> labelDelegate.updateTextTransform(event.transform)
            is EditorEvent.ApplyTextFormPreset -> labelDelegate.applyTextFormPreset(event.preset)
            is EditorEvent.UpdateTextFormAmount -> labelDelegate.updateTextFormAmount(event.amount)
            EditorEvent.ResetTextForm -> labelDelegate.resetTextForm()
            is EditorEvent.UpdateFillGradient -> shapeDelegate.updateFillGradient(event.gradient)
            is EditorEvent.UpdateTextColorGradient -> labelDelegate.updateTextColorGradient(event.gradient)
            is EditorEvent.ApplyLabelTypographyPreset ->
                labelDelegate.applyLabelTypographyPreset(event.fontWeight, event.textSizeSp, event.textTransform)
            is EditorEvent.UpdateStrokeColor -> shapeDelegate.updateStrokeColor(event.argb)
            is EditorEvent.UpdateStrokeWidth -> shapeDelegate.updateStrokeWidth(event.widthPx)
            is EditorEvent.UpdateStrokeDash ->
                shapeDelegate.updateStrokeDash(event.dashArray)
            is EditorEvent.UpdateCornerRadius -> shapeDelegate.updateCornerRadius(event.radiusPx)
            is EditorEvent.SyncShapeSize -> {
                val layer = _state.value.layers.find { it.id == _state.value.selectedLayerId }
                if (layer?.isLabelLayer == true) {
                    labelDelegate.syncShapeSize(event.widthPx, event.heightPx)
                } else {
                    shapeDelegate.syncShapeSize(event.widthPx, event.heightPx)
                }
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
            is EditorEvent.UpdateShadowBlur -> {
                updateActiveLayer { it.copy(appearance = it.appearance.copy(shadowBlur = event.blurPx)) }
            }
            is EditorEvent.UpdateElevation -> {
                val intensity = event.intensity.coerceIn(0f, 1f)
                updateActiveLayer {
                    it.copy(
                        appearance = it.appearance.copy(
                            elevationIntensity = intensity,
                            depthSizePx = intensity * EditorAppearance.MAX_SHAPE_DEPTH_PX,
                        ),
                    )
                }
            }
            is EditorEvent.UpdateDepthSize -> {
                val size = event.sizePx.coerceIn(0f, EditorAppearance.MAX_SHAPE_DEPTH_PX)
                updateActiveLayer {
                    it.copy(
                        appearance = it.appearance.copy(
                            depthSizePx = size,
                            elevationIntensity = size / EditorAppearance.MAX_SHAPE_DEPTH_PX,
                        ),
                    )
                }
            }
            is EditorEvent.UpdateDepthColor -> {
                updateActiveLayer {
                    it.copy(appearance = it.appearance.copy(depthColorArgb = event.argb))
                }
            }
            is EditorEvent.UpdateExtrusionAngle -> {
                updateActiveLayer {
                    it.copy(appearance = it.appearance.copy(extrusionAngle = event.angle % 360f))
                }
            }
            is EditorEvent.UpdateElevationStyle -> {
                updateActiveLayer { it.copy(appearance = it.appearance.copy(elevationStyle = event.style)) }
            }
            is EditorEvent.UpdateElevationTarget -> {
                updateActiveLayer { it.copy(appearance = it.appearance.copy(elevationTarget = event.target)) }
            }
            is EditorEvent.UpdateAlpha -> {
                updateActiveLayer { it.copy(appearance = it.appearance.copy(alpha = event.alpha.coerceIn(0.1f, 1f))) }
            }
            is EditorEvent.SetBoundingBoxVisible -> {
                // Not used anymore as bounding box depends on selectedLayerId
            }
            is EditorEvent.SelectTool -> {
                if (event.tool is EditorTool.Label) {
                    val active = _state.value.layers.find { it.id == _state.value.selectedLayerId }
                    if (active == null || !active.isLabelLayer) {
                        labelDelegate.addTextLayer(_state.value.template.originalSize.width.toFloat())
                    } else {
                        _state.update {
                            it.copy(selectedTool = EditorTool.Label)
                        }
                    }
                } else {
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
            }
            is EditorEvent.SelectCropRatio -> {
                updateActiveLayer { it.copy(cropRatio = event.ratio) }
                pushHistory()
            }
            is EditorEvent.SelectLayer -> {
                val targetLayer = _state.value.layers.find { it.id == event.layerId }
                val (rawSel, rawEdit) = LabelInteractionState.onSelectLayer(event.layerId)
                val (sel, edit) = LabelInteractionState.normalize(rawSel, rawEdit, _state.value.layers)
                _state.update {
                    it.copy(
                        selectedLayerId = sel,
                        editingLayerId = edit,
                        selectedTool = targetLayer?.preferredEditorTool()
                            ?: if (it.selectedTool == EditorTool.Shape || it.selectedTool == EditorTool.Label) {
                                null
                            } else {
                                it.selectedTool
                            },
                    )
                }
            }
            EditorEvent.DuplicateLayer -> {
                val current = _state.value
                if (current.selectedLayerId == null) {
                    _state.update { it.copy(errorMessage = context.getString(R.string.studio_error_select_object)) }
                    return
                }
                val (newLayers, primaryId) = LayerGroupOps.duplicate(
                    current.layers,
                    current.selectedLayerId!!,
                )
                if (primaryId != null) {
                    _state.update { it.copy(layers = newLayers, selectedLayerId = primaryId) }
                    pushHistory()
                }
            }
            EditorEvent.DeleteLayer -> {
                val currentLayerId = _state.value.selectedLayerId
                if (currentLayerId != null) {
                    val removeIds = LayerGroupOps.deleteIds(_state.value.layers, currentLayerId)
                    _state.update {
                        it.copy(
                            layers = it.layers.filterNot { layer -> layer.id in removeIds },
                            selectedLayerId = null,
                            editingLayerId = null,
                        )
                    }
                    pushHistory()
                } else {
                    _state.update { it.copy(errorMessage = context.getString(R.string.studio_error_select_object)) }
                }
            }
            EditorEvent.CommitTransform -> {
                bakeViewportScaleForSelection()
                refitActiveLabelHeightToText()
                pushHistory()
            }
            EditorEvent.Undo -> undo()
            EditorEvent.Redo -> redo()
            EditorEvent.MoveLayerUp -> moveLayer(up = true)
            EditorEvent.MoveLayerDown -> moveLayer(up = false)
            EditorEvent.MoveLayerToTop -> moveLayerToEdge(toTop = true)
            EditorEvent.MoveLayerToBottom -> moveLayerToEdge(toTop = false)
            EditorEvent.ToggleLayerLock -> toggleLayerLock()
            is EditorEvent.AlignLayer -> alignLayer(event.alignment)
            EditorEvent.CopyLabelStyle -> copyLabelStyle()
            EditorEvent.PasteLabelStyle -> pasteLabelStyle()
            EditorEvent.SaveDraft -> saveDraft()
            is EditorEvent.RequestTextEdit -> startTextEdit(event.layerId)
            is EditorEvent.StartTextEdit -> startTextEdit(event.layerId)
            EditorEvent.ConfirmTextEdit -> confirmTextEdit()
            EditorEvent.DeselectLayer -> deselectLayer()
            EditorEvent.FinishTextEdit -> finishTextEdit()
            is EditorEvent.Export -> export(event.templateAssetPath)
        }
    }

    private fun startTextEdit(layerId: String) {
        val layer = _state.value.layers.find { it.id == layerId } ?: return
        if (!layer.isLabelLayer) return
        val (rawSel, rawEdit) = LabelInteractionState.onStartTextEdit(layerId)
        val (sel, edit) = LabelInteractionState.normalize(rawSel, rawEdit, _state.value.layers)
        _state.update {
            it.copy(
                selectedLayerId = sel,
                editingLayerId = edit,
                selectedTool = layer.preferredEditorTool(),
            )
        }
    }

    private fun finishTextEdit() {
        applyShapeFitToActiveLayer()
        requestHistoryPush()
        _state.update { state ->
            val (sel, edit) = LabelInteractionState.onFinishTextEdit(
                state.selectedLayerId,
                state.editingLayerId,
            )
            state.copy(selectedLayerId = sel, editingLayerId = edit)
        }
    }

    private fun confirmTextEdit() {
        applyShapeFitToActiveLayer()
        requestHistoryPush()
        _state.update { state ->
            val (rawSel, rawEdit) = LabelInteractionState.onConfirmTextEdit()
            val (sel, edit) = LabelInteractionState.normalize(rawSel, rawEdit, state.layers)
            state.copy(selectedLayerId = sel, editingLayerId = edit)
        }
    }

    private fun deselectLayer() {
        _state.update { state ->
            val (rawSel, rawEdit) = LabelInteractionState.onDeselectLayer()
            val (sel, edit) = LabelInteractionState.normalize(rawSel, rawEdit, state.layers)
            state.copy(selectedLayerId = sel, editingLayerId = edit)
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

    private fun moveLayerToEdge(toTop: Boolean) {
        val currentLayerId = _state.value.selectedLayerId ?: return
        _state.update { state ->
            val mutableLayers = state.layers.toMutableList()
            val index = mutableLayers.indexOfFirst { it.id == currentLayerId }
            if (index == -1) return@update state
            val layer = mutableLayers.removeAt(index)
            if (toTop) mutableLayers.add(layer) else mutableLayers.add(0, layer)
            state.copy(layers = mutableLayers)
        }
        pushHistory()
    }

    private fun toggleLayerLock() {
        updateActiveLayer { layer -> layer.copy(isLocked = !layer.isLocked) }
    }

    private fun alignLayer(alignment: LayerAlignment) {
        val canvasW = _state.value.template.originalSize.width.toFloat()
        val canvasH = _state.value.template.originalSize.height.toFloat()
        if (canvasW <= 0f || canvasH <= 0f) return

        updateActiveLayer { layer ->
            val shapeW = layer.shapeWidthPx.takeIf { it > 0f } ?: 100f
            val shapeH = layer.shapeHeightPx.takeIf { it > 0f } ?: 100f
            val currentOffset = layer.viewport.offset
            val newOffset = when (alignment) {
                LayerAlignment.LEFT             -> currentOffset.copy(x = shapeW / 2f)
                LayerAlignment.CENTER_HORIZONTAL -> currentOffset.copy(x = canvasW / 2f)
                LayerAlignment.RIGHT            -> currentOffset.copy(x = canvasW - shapeW / 2f)
                LayerAlignment.TOP              -> currentOffset.copy(y = shapeH / 2f)
                LayerAlignment.CENTER_VERTICAL  -> currentOffset.copy(y = canvasH / 2f)
                LayerAlignment.BOTTOM           -> currentOffset.copy(y = canvasH - shapeH / 2f)
            }
            layer.copy(viewport = layer.viewport.withOffset(newOffset))
        }
        pushHistory()
    }

    private fun copyLabelStyle() {
        val layer = _state.value.layers.find { it.id == _state.value.selectedLayerId } ?: return
        styleClipboard = LabelStyleClipboard(
            shapeColorArgb = layer.shapeColorArgb,
            fillGradient = layer.fillGradient,
            strokeColorArgb = layer.strokeColorArgb,
            strokeWidthPx = layer.strokeWidthPx,
            strokeDashArray = layer.strokeDashArray,
            cornerRadiusX = layer.cornerRadiusX,
            cornerRadiusY = layer.cornerRadiusY,
            appearance = layer.appearance,
        )
    }

    private fun pasteLabelStyle() {
        val clip = styleClipboard ?: return
        updateActiveLayer { layer ->
            layer.copy(
                shapeColorArgb = clip.shapeColorArgb,
                fillGradient = clip.fillGradient,
                strokeColorArgb = clip.strokeColorArgb,
                strokeWidthPx = clip.strokeWidthPx,
                strokeDashArray = clip.strokeDashArray,
                cornerRadiusX = clip.cornerRadiusX,
                cornerRadiusY = clip.cornerRadiusY,
                appearance = clip.appearance,
            )
        }
        pushHistory()
    }

    private fun applyLoadedTemplate(loaded: com.thgiang.image.studio.ui.editor.load.LoadedEditorTemplate) {

        _state.update {
            it.copy(
                template = loaded.template,
                layers = EditorLayerNormalizer.normalize(loaded.layers.ifEmpty { it.layers }),
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
                    it.copy(
                        template = loaded.template.copy(
                            // Lưu objectAssetPath để draft có thể khôi phục ảnh mẫu khi load lại
                            objectAssetPath = objectSourceAssetPath ?: it.template.objectAssetPath,
                        ),
                        errorMessage = null,
                    )
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

    // ============ Sticker ============

    /**
     * Tải 20 sticker preview (10 meme + 10 decor) từ admin_web trong background.
     * Gọi lần đầu khi người dùng bật tool Sticker; các lần sau dùng cache.
     * Chạy trên Dispatchers.IO để không block UI thread.
     */
    fun loadStickerPreview() {
        // Đã có dữ liệu → không cần gọi lại
        if (_stickerState.value.previewMeme.isNotEmpty() ||
            _stickerState.value.previewDecor.isNotEmpty()
        ) return

        // Đang load → tránh gọi trùng
        if (stickerPreviewJob?.isActive == true) return

        stickerPreviewJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _stickerState.update { it.copy(isLoadingPreview = true, previewError = false) }
            try {
                val (meme, decor) = stickerRepository.fetchPreview()
                _stickerState.update {
                    it.copy(
                        previewMeme = meme,
                        previewDecor = decor,
                        isLoadingPreview = false,
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "loadStickerPreview failed", e)
                _stickerState.update { it.copy(isLoadingPreview = false, previewError = true) }
            }
        }
    }

    /** Xóa cache sticker và tải lại preview — gọi khi user muốn refresh. */
    fun invalidateStickerCache() {
        stickerRepository.invalidateCache()
        _stickerState.update { StickerUiState() }
        loadStickerPreview()
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
                                if (it.id == update.processingId) {
                                    if (replaceLayerId != null) {
                                        // Fit new image aspect ratio within the old shape bounds
                                        val oldW = it.shapeWidthPx.coerceAtLeast(1f)
                                        val oldH = it.shapeHeightPx.coerceAtLeast(1f)
                                        val newAspect = result.product.baseWidth.toFloat() / result.product.baseHeight.toFloat().coerceAtLeast(1f)
                                        val oldAspect = oldW / oldH

                                        val (newW, newH) = if (newAspect > oldAspect) {
                                            oldW to (oldW / newAspect)
                                        } else {
                                            (oldH * newAspect) to oldH
                                        }

                                        it.copy(
                                            product = result.product.copy(isSample = it.product.isSample),
                                            shapeWidthPx = newW,
                                            shapeHeightPx = newH,
                                        )
                                    } else {
                                        // For new image insertions, use original aspect ratio and standard max scale
                                        val newW = result.product.baseWidth.toFloat()
                                        val newH = result.product.baseHeight.toFloat()
                                        val maxDim = 400f
                                        val scaleFactor = (maxDim / maxOf(newW, newH)).coerceAtMost(1f)

                                        it.copy(
                                            product = result.product,
                                            shapeWidthPx = newW * scaleFactor,
                                            shapeHeightPx = newH * scaleFactor,
                                        )
                                    }
                                } else {
                                    it
                                }
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

    private fun bakeViewportScaleForSelection() {
        val state = _state.value
        val layerId = state.selectedLayerId ?: return
        val selected = state.layers.find { it.id == layerId } ?: return
        val targets = selected.groupId?.let { gid ->
            state.layers.filter { it.groupId == gid }
        } ?: listOf(selected)
        if (targets.none { LayerViewportScale.needsBake(it) }) return

        val bakedIds = targets.map { it.id }.toSet()
        _state.update { current ->
            current.copy(
                layers = current.layers.map { layer ->
                    if (layer.id in bakedIds) LayerViewportScale.bake(layer) else layer
                },
            )
        }
    }

    private fun applyGestureDelta(delta: GestureDelta) {
        updateActiveLayer { layer ->
            var newViewport = layer.viewport
            
            if (delta.pan != Offset.Zero) {
                newViewport = newViewport.withOffset(newViewport.offset + delta.pan)
            }
            
            var updatedLayer = layer
            if (layer.isLabelLayer) {
                if (delta.scale != 1f) {
                    val newTextSize = (layer.textSizeSp * delta.scale).coerceIn(1f, ShapeLabelDefaults.MAX_TEXT_SIZE_SP)
                    val newW = (layer.shapeWidthPx * delta.scale).coerceAtLeast(60f)
                    val newH = (layer.shapeHeightPx * delta.scale).coerceAtLeast(30f)
                    updatedLayer = updatedLayer.copy(
                        textSizeSp = newTextSize,
                        shapeWidthPx = newW,
                        shapeHeightPx = newH,
                    )
                }
            } else {
                if (delta.scale != 1f) {
                    val newScale = (newViewport.scale * delta.scale).coerceIn(MIN_SCALE, MAX_SCALE)
                    newViewport = newViewport.withScale(newScale)
                }
            }

            if (delta.rotation != 0f) {
                var newRotation = (newViewport.rotation + delta.rotation) % 360f
                if (newRotation < 0) newRotation += 360f
                newViewport = newViewport.withRotation(newRotation)
            }
            
            updatedLayer = updatedLayer.copy(viewport = newViewport)
            if (delta.deltaWidth != 0f || delta.deltaHeight != 0f) {
                val newW = (updatedLayer.shapeWidthPx + delta.deltaWidth).coerceAtLeast(60f)
                val newH = (updatedLayer.shapeHeightPx + delta.deltaHeight).coerceAtLeast(30f)
                updatedLayer = updatedLayer.copy(shapeWidthPx = newW, shapeHeightPx = newH)
            }
            updatedLayer
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
        savedStateHandle["editor_state_editing_layer_id"] = _state.value.editingLayerId
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
