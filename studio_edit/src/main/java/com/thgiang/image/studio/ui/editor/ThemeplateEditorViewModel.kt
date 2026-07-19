package com.thgiang.image.studio.ui.editor
import com.thgiang.image.studio.ui.editor.*
import com.thgiang.image.studio.ui.editor.label.factory.*
import com.thgiang.image.studio.ui.editor.canvas.*

import android.content.Context
import com.thgiang.image.studio.ui.editor.model.*
import com.thgiang.image.studio.ui.editor.label.model.ShapeLabelDefaults
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
import com.thgiang.image.core.domain.logging.AppLogger
import com.thgiang.image.core.domain.model.template.CloudTemplate
import com.thgiang.image.studio.R
import com.thgiang.image.studio.data.TemplateDraftRepository
import com.thgiang.image.studio.data.DraftRestoreBundle
import com.thgiang.image.studio.data.RemoteSticker
import com.thgiang.image.studio.data.RemoteBackground
import com.thgiang.image.studio.data.BackgroundRemoteRepository
import com.thgiang.image.studio.data.StickerRemoteRepository
import com.thgiang.image.studio.model.StudioThemeplates
import com.thgiang.image.studio.ui.editor.export.EditorExportCoordinator
import com.thgiang.image.studio.ui.editor.export.ExportOutcome
import com.thgiang.image.studio.ui.editor.export.SaveDraftOutcome
import com.thgiang.image.studio.ui.editor.load.EditorTemplateLoader
import com.thgiang.image.studio.ui.editor.product.EditorProductWorkflow
import com.thgiang.image.studio.ui.editor.product.ProductImageResult
import com.thgiang.image.studio.ui.editor.product.SampleObjectResult
import com.thgiang.image.studio.ui.editor.product.StickerResult
import com.thgiang.image.studio.ui.editor.document.DocumentSession
import com.thgiang.image.studio.ui.editor.label.LabelViewModelDelegate
import com.thgiang.image.studio.ui.editor.label.model.LabelStyleClipboard
import com.thgiang.image.studio.ui.editor.shape.ShapeViewModelDelegate
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import kotlin.math.abs

/**
 * UI state cho khu vực Sticker picker.
 */
data class StickerUiState(
    val preview: List<RemoteSticker> = emptyList(),
    val isLoadingPreview: Boolean = false,
    val previewError: Boolean = false,
)

data class BackgroundUiState(
    val preview: List<RemoteBackground> = emptyList(),
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
    private val backgroundRepository: BackgroundRemoteRepository,
    private val appLogger: AppLogger,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val TAG = "EditorVM"
        private const val BG_DEBUG_TAG = "TPL_BG_DEBUG"
        private const val MIN_SCALE = 0.05f
        private const val MAX_SCALE = 40f
        private const val SNAP_ANGLE_THRESHOLD = 5f
        private val SNAP_ANGLES = listOf(0f, 90f, 180f, 270f)
        private const val HISTORY_DEBOUNCE_MS = 300L
    }

    // Clipboard: stores style of last copied layer (fill, stroke, shadow)
    private var styleClipboard: LabelStyleClipboard? = null

    val draftId: String? = savedStateHandle.get<String>("draftId")

    private val draftRestoreBundle: DraftRestoreBundle? = draftId
        ?.takeIf { it.isNotBlank() }
        ?.let { templateDraftRepository.resolveDraftRestore(it) }

    private val _state = MutableStateFlow(buildInitialEditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    private val _isTemplateLoading = MutableStateFlow(shouldShowTemplateLoadingInitially())
    val isTemplateLoading: StateFlow<Boolean> = _isTemplateLoading.asStateFlow()
    private var templateLoadingDepth = 0

    private val _isSavingDraft = MutableStateFlow(false)
    val isSavingDraft: StateFlow<Boolean> = _isSavingDraft.asStateFlow()

    private val _gesturePreview = MutableStateFlow<GesturePreview?>(null)
    val gesturePreview: StateFlow<GesturePreview?> = _gesturePreview.asStateFlow()

    val canUndo: StateFlow<Boolean> = historyManager.canUndo
    val canRedo: StateFlow<Boolean> = historyManager.canRedo

    // ─── Sticker state ────────────────────────────────────────────
    private val _stickerState = MutableStateFlow(StickerUiState())
    val stickerState: StateFlow<StickerUiState> = _stickerState.asStateFlow()

    private val _backgroundState = MutableStateFlow(BackgroundUiState())
    val backgroundState: StateFlow<BackgroundUiState> = _backgroundState.asStateFlow()

    private var stickerPreviewJob: Job? = null
    private var backgroundPreviewJob: Job? = null

    // Debounced history push for gesture operations
    private val historyPushFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private var templateLoadJob: Job? = null
    private var bgRemoveJob: Job? = null
    private var exportJob: Job? = null
    private var historyPushJob: Job? = null
    private var saveDraftJob: Job? = null
    private val saveDraftGeneration = AtomicLong(0)
    private var overlayJob: Job? = null

    val themeplateId: String? = savedStateHandle.get<String>("themeplateId")

    /** Document v2 session (strangler) — drives text transform / templates / form via commands. */
    private val documentSession by lazy { DocumentSession(context) }

    val labelDelegate by lazy {
        LabelViewModelDelegate(
            context = context,
            layerFactory = layerFactory,
            readState = { _state.value },
            updateState = { block -> _state.update { s -> block(s) } },
            requestHistoryPush = { historyPushFlow.tryEmit(Unit) },
            pushHistory = { historyPushJob?.cancel(); historyPushJob = viewModelScope.launch { pushHistoryInternal() } },
        )
    }

    val shapeDelegate by lazy {
        ShapeViewModelDelegate(
            layerFactory = layerFactory,
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
        } else if (!draftId.isNullOrBlank()) {
            restoreDraftTemplateIfNeeded()
            finalizeDraftEditorState()
        }
    }

    private fun buildInitialEditorState(): EditorState {
        val bundle = draftRestoreBundle
        when {
            bundle?.hasLegacyProjectState == true -> {
                return EditorState(
                    errorMessage = context.getString(R.string.studio_draft_legacy_unsupported),
                )
            }
            bundle?.editorStateCorrupt == true -> {
                return EditorState(
                    errorMessage = context.getString(R.string.studio_draft_load_failed),
                )
            }
            draftId != null && bundle?.state == null && bundle?.meta == null -> {
                return EditorState(
                    errorMessage = context.getString(R.string.studio_draft_load_failed),
                )
            }
        }

        val meta = bundle?.meta
        val restored = bundle?.state ?: EditorState()
        val assetPath = meta?.templateAssetPath?.takeIf { it.isNotBlank() }
            ?: restored.template.assetPath
        val objectAssetPath = meta?.templateObjectAssetPath?.takeIf { it.isNotBlank() }
            ?: restored.template.objectAssetPath
        val thumbnailUrl = meta?.templateThumbnailUrl?.takeIf { it.isNotBlank() }
            ?: restored.template.thumbnailUrl
        val withMetaTemplate = restored.copy(
            template = restored.template.copy(
                assetPath = assetPath,
                objectAssetPath = objectAssetPath,
                thumbnailUrl = thumbnailUrl,
            ),
        )

        return try {
            val layers = EditorLayerNormalizer.normalize(withMetaTemplate.layers)
            val (sel, edit) = LabelInteractionState.normalize(
                withMetaTemplate.selectedLayerId,
                withMetaTemplate.editingLayerId,
                layers,
            )
            val (_, ids) = SelectionState.singleSelect(layers, sel)
            withMetaTemplate.copy(
                layers = layers,
                selectedLayerId = sel,
                selectedLayerIds = ids,
                editingLayerId = edit,
            )
        } catch (e: Exception) {
            android.util.Log.e("ThemeplateEditorVM", "Failed to restore draft layers", e)
            withMetaTemplate.copy(
                errorMessage = context.getString(R.string.studio_draft_load_failed),
            )
        }
    }

    private fun restoreDraftTemplateIfNeeded() {
        if (_state.value.errorMessage != null) return

        val meta = draftRestoreBundle?.meta
        val template = _state.value.template
        val assetPath = meta?.templateAssetPath?.takeIf { it.isNotBlank() }
            ?: template.assetPath.takeIf { it.isNotBlank() }

        if (template.loaded && template.originalSize.width > 0) return

        if (assetPath.isNullOrBlank()) {
            _state.update {
                it.copy(errorMessage = context.getString(R.string.studio_draft_load_failed))
            }
            return
        }

        if (assetPath.startsWith("http://") || assetPath.startsWith("https://")) {
            if (template.originalWidth > 0 && template.originalHeight > 0) {
                _state.update {
                    it.copy(
                        template = it.template.copy(
                            assetPath = assetPath,
                            loaded = true,
                            thumbnailUrl = meta?.templateThumbnailUrl?.takeIf { url -> url.isNotBlank() }
                                ?: it.template.thumbnailUrl,
                        ),
                    )
                }
            } else {
                val cloudId = meta?.cloudTemplateId?.takeIf { it.isNotBlank() }
                    ?: themeplateId?.takeIf {
                        it.isNotBlank() && it != "draft" && it != StudioThemeplates.BLANK_THEMEPLATE_ID
                    }
                if (cloudId != null) {
                    loadCloudTemplateById(cloudId)
                } else {
                    _state.update {
                        it.copy(errorMessage = context.getString(R.string.studio_draft_load_failed))
                    }
                }
            }
            return
        }

        loadTemplate(assetPath, meta?.templateObjectAssetPath ?: template.objectAssetPath)
    }

    /**
     * Draft JSON can carry stale inline-edit, crop-tool, or overlay flags from the last save.
     * Normalize interaction state so the canvas is zoom/pan-ready immediately after open.
     */
    private fun finalizeDraftEditorState() {
        if (draftId.isNullOrBlank()) return

        val state = _state.value
        if (state.errorMessage != null) {
            dismissTemplateLoadingIfIdle()
            return
        }

        if (state.template.loaded && state.template.originalSize.width > 0) {
            dismissTemplateLoadingIfIdle()
        }

        val needsInteractionReset = state.editingLayerId != null ||
            state.selectedTool is EditorTool.Crop ||
            state.showOverlay

        if (needsInteractionReset) {
            val layers = state.layers
            val (sel, _) = LabelInteractionState.normalize(
                state.selectedLayerId,
                editingLayerId = null,
                layers,
            )
            val (_, ids) = SelectionState.singleSelect(layers, sel)
            _state.update {
                it.copy(
                    selectedLayerId = sel,
                    selectedLayerIds = ids,
                    editingLayerId = null,
                    selectedTool = if (it.selectedTool is EditorTool.Crop) null else it.selectedTool,
                    showOverlay = false,
                )
            }
        }

        syncDocumentSelection(_state.value.selectedLayerId)
    }

    private fun dismissTemplateLoadingIfIdle() {
        if (templateLoadingDepth == 0) {
            _isTemplateLoading.value = false
        }
    }

    private fun shouldShowTemplateLoadingInitially(): Boolean {
        val initialState = _state.value
        if (initialState.errorMessage != null) return false
        if (initialState.template.loaded && initialState.template.originalSize.width > 0) return false

        val savedPath = savedStateHandle.get<String>("template_path")
        if (!savedPath.isNullOrBlank() && !initialState.template.loaded) return true

        if (!draftId.isNullOrBlank()) {
            val assetPath = draftRestoreBundle?.meta?.templateAssetPath?.takeIf { it.isNotBlank() }
                ?: initialState.template.assetPath.takeIf { it.isNotBlank() }
            if (assetPath.isNullOrBlank()) return false
            if (assetPath.startsWith("http://") || assetPath.startsWith("https://")) {
                val hasDimensions = initialState.template.originalWidth > 0 &&
                    initialState.template.originalHeight > 0
                val canLoadFromCloud = draftRestoreBundle?.meta?.cloudTemplateId?.isNotBlank() == true ||
                    (!themeplateId.isNullOrBlank() &&
                        themeplateId != "draft" &&
                        themeplateId != StudioThemeplates.BLANK_THEMEPLATE_ID)
                return !hasDimensions && canLoadFromCloud
            }
            return true
        }

        val tId = themeplateId
        return !tId.isNullOrEmpty() &&
            tId != "draft" &&
            tId != StudioThemeplates.BLANK_THEMEPLATE_ID
    }

    private fun beginTemplateLoading() {
        templateLoadingDepth++
        _isTemplateLoading.value = true
    }

    private fun endTemplateLoading() {
        templateLoadingDepth = (templateLoadingDepth - 1).coerceAtLeast(0)
        _isTemplateLoading.value = templateLoadingDepth > 0
    }

    private fun updateActiveLayer(block: (EditorLayer) -> EditorLayer) {
        _state.update { state ->
            val layerId = state.selectedLayerId ?: return@update state
            state.copy(layers = LayerGroupSync.apply(state.layers, layerId, block))
        }
    }

    /**
     * Sync EditorState → DocumentSession, run [block], project layers back.
     * @return true if DocumentSession handled the mutation.
     */
    private fun applyDocument(
        selectedLayerId: String? = _state.value.selectedLayerId,
        debounceHistory: Boolean = true,
        recordHistory: Boolean = true,
        block: DocumentSession.(String?) -> List<EditorLayer>?,
    ): Boolean {
        if (!documentSession.enabled) return false
        documentSession.syncFromState(_state.value)
        val layers = documentSession.block(selectedLayerId) ?: return false
        _state.update { it.copy(layers = layers) }
        if (recordHistory) {
            if (debounceHistory) {
                historyPushFlow.tryEmit(Unit)
            } else {
                pushHistory()
            }
        }
        return true
    }

    private fun isLabelOrTextInShapeSelection(): Boolean {
        val id = _state.value.selectedLayerId ?: return false
        val layer = _state.value.layers.find { it.id == id } ?: return false
        return layer.isLabelLayer ||
            (layer.groupId != null && _state.value.layers.any { it.groupId == layer.groupId && it.isLabelLayer })
    }

    /** Select FRAME or LABEL sibling for Shape/Label tool and sync Document [NodePart]. */
    private fun applyGroupPartSelection(layerId: String?, tool: EditorTool) {
        val layers = _state.value.layers
        val (_, ids) = SelectionState.singleSelect(layers, layerId)
        _state.update {
            it.copy(
                selectedLayerId = layerId,
                selectedLayerIds = ids,
                selectedTool = tool,
                editingLayerId = null,
            )
        }
        syncDocumentSelection(layerId)
    }

    private fun syncDocumentSelection(selectedLayerId: String?) {
        if (!documentSession.enabled) return
        documentSession.syncFromState(_state.value)
        documentSession.selectForLayer(selectedLayerId)
    }

    /** Selection for rich-text style. null = collapsed caret during edit (no-op). */
    private fun inlineStyleRange(): Pair<Int, Int>? {
        val state = _state.value
        if (state.editingLayerId == null) return 0 to Int.MAX_VALUE
        val start = state.inlineSelectionStart
        val end = state.inlineSelectionEnd
        return if (end > start) start to end else null
    }

    private fun refitActiveLabelHeightToText() {
        // LayoutEngine already hugs height for StyleOrCaseChange; keep as safety for legacy paths.
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
            EditorEvent.LoadBlankCanvas -> loadBlankCanvas()
            is EditorEvent.PrepareTemplatePreview -> prepareTemplatePreview(event.themeplate)
            is EditorEvent.SetProductImage -> setProductImage(event.uri, event.replaceLayerId)
            is EditorEvent.RemoveBackground -> removeBackground(event.layerId)
            is EditorEvent.AddSticker -> addSticker(event.assetPath)
            is EditorEvent.ApplyBackground -> applyBackground(event.url)
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
            // ── Label edit events (DocumentSession primary) ──
            is EditorEvent.UpdateShapeText -> {
                val inline = _state.value.editingLayerId == _state.value.selectedLayerId
                if (!applyDocument { editText(it, event.text, inline) }) {
                    labelDelegate.updateShapeText(event.text)
                }
                val len = event.text.length
                _state.update {
                    it.copy(
                        inlineSelectionStart = it.inlineSelectionStart.coerceIn(0, len),
                        inlineSelectionEnd = it.inlineSelectionEnd.coerceIn(0, len),
                    )
                }
            }
            is EditorEvent.UpdateInlineTextSelection -> {
                _state.update {
                    it.copy(
                        inlineSelectionStart = event.start.coerceAtLeast(0),
                        inlineSelectionEnd = event.end.coerceAtLeast(0),
                    )
                }
            }
            EditorEvent.InsertTextNewline -> {
                if (!applyDocument { insertNewline(it) }) {
                    labelDelegate.insertTextNewline()
                }
            }
            is EditorEvent.UpdateShapeColor -> {
                if (!applyDocument { setShapeFill(it, event.argb) }) {
                    shapeDelegate.updateShapeColor(event.argb)
                }
            }
            is EditorEvent.UpdateTextColor -> {
                val range = inlineStyleRange()
                if (range == null) {
                    // Collapsed caret while editing: skip (avoid restyling whole string).
                } else if (!applyDocument { setTextColor(it, event.argb, range.first, range.second) }) {
                    labelDelegate.updateTextColor(event.argb)
                }
            }
            is EditorEvent.UpdateTextSize -> {
                if (!applyDocument { setFontSize(it, event.sizeSp) }) {
                    labelDelegate.updateTextSize(event.sizeSp)
                }
            }
            is EditorEvent.UpdateTextFontFamily -> {
                if (!applyDocument { setFontFamily(it, event.fontFamily) }) {
                    labelDelegate.updateTextFontFamily(event.fontFamily)
                }
            }
            is EditorEvent.UpdateShapeType -> {
                if (!applyDocument { setShapeType(it, event.shapeType) }) {
                    val layer = _state.value.layers.find { it.id == _state.value.selectedLayerId }
                    if (layer?.isLabelLayer == true) {
                        labelDelegate.updateShapeType(event.shapeType)
                    } else {
                        shapeDelegate.updateShapeType(event.shapeType)
                    }
                }
            }
            is EditorEvent.UpdateTextBold -> {
                val range = inlineStyleRange()
                if (range != null) {
                    if (!applyDocument { setBold(it, event.bold, range.first, range.second) }) {
                        labelDelegate.updateTextBold(event.bold)
                    }
                }
            }
            is EditorEvent.UpdateTextItalic -> {
                val range = inlineStyleRange()
                if (range != null) {
                    if (!applyDocument { setItalic(it, event.italic, range.first, range.second) }) {
                        labelDelegate.updateTextItalic(event.italic)
                    }
                }
            }
            is EditorEvent.UpdateTextUnderline -> {
                val range = inlineStyleRange()
                if (range != null) {
                    if (!applyDocument { setUnderline(it, event.underline, range.first, range.second) }) {
                        labelDelegate.updateTextUnderline(event.underline)
                    }
                }
            }
            is EditorEvent.UpdateTextLinethrough -> {
                val range = inlineStyleRange()
                if (range != null) {
                    if (!applyDocument { setLinethrough(it, event.linethrough, range.first, range.second) }) {
                        labelDelegate.updateTextLinethrough(event.linethrough)
                    }
                }
            }
            is EditorEvent.UpdateTextAlign -> {
                if (!applyDocument { setAlign(it, event.align) }) {
                    labelDelegate.updateTextAlign(event.align)
                }
            }
            is EditorEvent.UpdateLineHeight -> {
                if (!applyDocument { setLineHeight(it, event.multiplier) }) {
                    labelDelegate.updateLineHeight(event.multiplier)
                }
            }
            is EditorEvent.UpdateCharSpacing -> {
                if (!applyDocument { setCharSpacing(it, event.spacing) }) {
                    labelDelegate.updateCharSpacing(event.spacing)
                }
            }
            is EditorEvent.UpdateTextTransform -> {
                if (!applyDocument { applyTextTransform(it, event.transform) }) {
                    labelDelegate.updateTextTransform(event.transform)
                }
            }
            is EditorEvent.ApplyTextFormPreset -> {
                if (!applyDocument { applyTextFormPreset(it, event.preset) }) {
                    labelDelegate.applyTextFormPreset(event.preset)
                }
            }
            is EditorEvent.UpdateTextFormAmount -> {
                if (!applyDocument { setTextFormAmount(it, event.amount) }) {
                    labelDelegate.updateTextFormAmount(event.amount)
                }
            }
            EditorEvent.ResetTextForm -> {
                if (!applyDocument { resetTextForm(it) }) {
                    labelDelegate.resetTextForm()
                }
            }
            is EditorEvent.UpdateFillGradient -> {
                if (!applyDocument { setFillGradient(it, event.gradient) }) {
                    shapeDelegate.updateFillGradient(event.gradient)
                }
            }
            is EditorEvent.UpdateTextColorGradient -> {
                if (!applyDocument { setTextColorGradient(it, event.gradient) }) {
                    labelDelegate.updateTextColorGradient(event.gradient)
                }
            }
            is EditorEvent.ApplyLabelTypographyPreset -> {
                if (!applyDocument {
                        applyTypographyPreset(it, event.fontWeight, event.textSizeSp, event.textTransform)
                    }
                ) {
                    labelDelegate.applyLabelTypographyPreset(
                        event.fontWeight,
                        event.textSizeSp,
                        event.textTransform,
                    )
                }
            }
            is EditorEvent.ApplyTextStyleTemplate -> {
                if (!applyDocument { applyTextStyleTemplate(it, event.templateId) }) {
                    labelDelegate.applyTextStyleTemplate(event.templateId)
                }
            }
            is EditorEvent.UpdateStrokeColor -> {
                if (!applyDocument { setStrokeColor(it, event.argb) }) {
                    shapeDelegate.updateStrokeColor(event.argb)
                }
            }
            is EditorEvent.UpdateStrokeWidth -> {
                if (!applyDocument { setStrokeWidth(it, event.widthPx) }) {
                    shapeDelegate.updateStrokeWidth(event.widthPx)
                }
            }
            is EditorEvent.UpdateStrokeDash -> {
                if (!applyDocument { setStrokeDash(it, event.dashArray) }) {
                    shapeDelegate.updateStrokeDash(event.dashArray)
                }
            }
            is EditorEvent.UpdateStrokeDashGap -> {
                if (!applyDocument { setStrokeDashGap(it, event.gapPx) }) {
                    shapeDelegate.updateStrokeDashGap(event.gapPx)
                }
            }
            is EditorEvent.UpdateCornerRadius -> {
                if (!applyDocument { setCornerRadius(it, event.radiusPx) }) {
                    shapeDelegate.updateCornerRadius(event.radiusPx)
                }
            }
            is EditorEvent.SyncShapeSize -> {
                val layer = _state.value.layers.find { it.id == _state.value.selectedLayerId }
                if (layer?.isLabelLayer == true) {
                    if (!applyDocument { resizeBox(it, event.widthPx, event.heightPx) }) {
                        labelDelegate.syncShapeSize(event.widthPx, event.heightPx)
                    }
                } else {
                    shapeDelegate.syncShapeSize(event.widthPx, event.heightPx)
                }
            }
            is EditorEvent.UpdateGesture -> {
                // Apply immediately so drag follows the finger 1:1 (throttling dropped pan deltas).
                applyGesturePreview(event.delta)
                requestHistoryPush()
            }
            is EditorEvent.UpdateOffset -> {
                if (isLabelOrTextInShapeSelection()) {
                    applyLabelTransformDelta { vp -> vp.withOffset(vp.offset + event.delta) }
                } else {
                    updateActiveLayer { it.copy(viewport = it.viewport.withOffset(it.viewport.offset + event.delta)) }
                }
                requestHistoryPush()
            }
            is EditorEvent.SetOffset -> {
                if (isLabelOrTextInShapeSelection()) {
                    applyLabelTransformDelta { vp -> vp.withOffset(event.offset) }
                } else {
                    updateActiveLayer { it.copy(viewport = it.viewport.withOffset(event.offset)) }
                }
                pushHistory()
            }
            is EditorEvent.UpdateScale -> updateScale(event.factor)
            is EditorEvent.SetScale -> {
                if (isLabelOrTextInShapeSelection()) {
                    applyLabelTransformDelta { vp -> vp.withScale(event.scale) }
                } else {
                    updateActiveLayer { it.copy(viewport = it.viewport.withScale(event.scale)) }
                }
                pushHistory()
            }
            is EditorEvent.UpdateRotation -> {
                updateRotation(event.delta)
            }
            is EditorEvent.SetRotation -> {
                var normalized = event.degrees % 360f
                if (normalized < 0) normalized += 360f
                if (isLabelOrTextInShapeSelection()) {
                    applyLabelTransformDelta { vp -> vp.withRotation(normalized) }
                } else {
                    updateActiveLayer { it.copy(viewport = it.viewport.withRotation(normalized)) }
                }
                pushHistory()
            }
            EditorEvent.FlipHorizontal -> {
                if (isLabelOrTextInShapeSelection()) {
                    applyLabelTransformDelta { vp -> vp.copy(flippedH = !vp.flippedH) }
                } else {
                    updateActiveLayer { it.copy(viewport = it.viewport.copy(flippedH = !it.viewport.flippedH)) }
                }
                pushHistory()
            }
            EditorEvent.FlipVertical -> {
                if (isLabelOrTextInShapeSelection()) {
                    applyLabelTransformDelta { vp -> vp.copy(flippedV = !vp.flippedV) }
                } else {
                    updateActiveLayer { it.copy(viewport = it.viewport.copy(flippedV = !it.viewport.flippedV)) }
                }
                pushHistory()
            }
            is EditorEvent.UpdateShadow -> {
                if (isLabelOrTextInShapeSelection()) {
                    if (!applyDocument { setShadowIntensity(it, event.intensity) }) {
                        updateActiveLayer {
                            it.copy(appearance = it.appearance.copy(shadowIntensity = event.intensity.coerceIn(0f, 1f)))
                        }
                    }
                } else {
                    updateActiveLayer {
                        it.copy(appearance = it.appearance.copy(shadowIntensity = event.intensity.coerceIn(0f, 1f)))
                    }
                }
            }
            is EditorEvent.UpdateShadowAngle -> {
                if (isLabelOrTextInShapeSelection()) {
                    if (!applyDocument { setShadowAngle(it, event.angle) }) {
                        updateActiveLayer {
                            it.copy(appearance = it.appearance.copy(shadowAngle = event.angle.coerceIn(0f, 360f)))
                        }
                    }
                } else {
                    updateActiveLayer {
                        it.copy(appearance = it.appearance.copy(shadowAngle = event.angle.coerceIn(0f, 360f)))
                    }
                }
            }
            is EditorEvent.UpdateShadowDistance -> {
                if (isLabelOrTextInShapeSelection()) {
                    if (!applyDocument { setShadowDistance(it, event.distance) }) {
                        updateActiveLayer {
                            it.copy(appearance = it.appearance.copy(shadowDistance = event.distance.coerceIn(0f, 50f)))
                        }
                    }
                } else {
                    updateActiveLayer {
                        it.copy(appearance = it.appearance.copy(shadowDistance = event.distance.coerceIn(0f, 50f)))
                    }
                }
            }
            is EditorEvent.UpdateShadowColor -> {
                if (isLabelOrTextInShapeSelection()) {
                    if (!applyDocument { setShadowColor(it, event.argb) }) {
                        updateActiveLayer { it.copy(appearance = it.appearance.copy(shadowColorArgb = event.argb)) }
                    }
                } else {
                    updateActiveLayer { it.copy(appearance = it.appearance.copy(shadowColorArgb = event.argb)) }
                }
            }
            is EditorEvent.UpdateShadowBlur -> {
                if (isLabelOrTextInShapeSelection()) {
                    if (!applyDocument { setShadowBlur(it, event.blurPx) }) {
                        updateActiveLayer { it.copy(appearance = it.appearance.copy(shadowBlur = event.blurPx)) }
                    }
                } else {
                    updateActiveLayer { it.copy(appearance = it.appearance.copy(shadowBlur = event.blurPx)) }
                }
            }
            is EditorEvent.UpdateElevationShadowBlur -> {
                if (isLabelOrTextInShapeSelection()) {
                    if (!applyDocument { setElevationShadowBlur(it, event.blurPx) }) {
                        updateActiveLayer {
                            it.copy(appearance = it.appearance.copy(elevationShadowBlur = event.blurPx))
                        }
                    }
                } else {
                    updateActiveLayer {
                        it.copy(appearance = it.appearance.copy(elevationShadowBlur = event.blurPx))
                    }
                }
            }
            is EditorEvent.UpdateElevation -> {
                if (isLabelOrTextInShapeSelection()) {
                    if (!applyDocument { setElevationIntensity(it, event.intensity) }) {
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
                } else {
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
            }
            is EditorEvent.UpdateDepthSize -> {
                if (isLabelOrTextInShapeSelection()) {
                    if (!applyDocument { setDepthSize(it, event.sizePx) }) {
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
                } else {
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
            }
            is EditorEvent.UpdateDepthColor -> {
                if (isLabelOrTextInShapeSelection()) {
                    if (!applyDocument { setDepthColor(it, event.argb) }) {
                        updateActiveLayer {
                            it.copy(appearance = it.appearance.copy(depthColorArgb = event.argb))
                        }
                    }
                } else {
                    updateActiveLayer {
                        it.copy(appearance = it.appearance.copy(depthColorArgb = event.argb))
                    }
                }
            }
            is EditorEvent.UpdateExtrusionAngle -> {
                if (isLabelOrTextInShapeSelection()) {
                    if (!applyDocument { setExtrusionAngle(it, event.angle) }) {
                        updateActiveLayer {
                            it.copy(appearance = it.appearance.copy(extrusionAngle = event.angle % 360f))
                        }
                    }
                } else {
                    updateActiveLayer {
                        it.copy(appearance = it.appearance.copy(extrusionAngle = event.angle % 360f))
                    }
                }
            }
            is EditorEvent.UpdateElevationStyle -> {
                if (isLabelOrTextInShapeSelection()) {
                    if (!applyDocument { setElevationStyle(it, event.style) }) {
                        updateActiveLayer {
                            it.copy(appearance = it.appearance.copy(elevationStyle = event.style))
                        }
                    }
                } else {
                    updateActiveLayer { it.copy(appearance = it.appearance.copy(elevationStyle = event.style)) }
                }
            }
            is EditorEvent.UpdateElevationTarget -> {
                if (isLabelOrTextInShapeSelection()) {
                    if (!applyDocument { setElevationTarget(it, event.target) }) {
                        updateActiveLayer {
                            it.copy(appearance = it.appearance.copy(elevationTarget = event.target))
                        }
                    }
                } else {
                    updateActiveLayer {
                        it.copy(appearance = it.appearance.copy(elevationTarget = event.target))
                    }
                }
            }
            is EditorEvent.UpdateAlpha -> {
                if (isLabelOrTextInShapeSelection()) {
                    if (!applyDocument { setOpacity(it, event.alpha) }) {
                        updateActiveLayer {
                            it.copy(appearance = it.appearance.copy(alpha = event.alpha.coerceIn(0.1f, 1f)))
                        }
                    }
                } else {
                    updateActiveLayer {
                        it.copy(appearance = it.appearance.copy(alpha = event.alpha.coerceIn(0.1f, 1f)))
                    }
                }
            }
            is EditorEvent.UpdateShapeFillOpacity -> {
                if (!applyDocument { setShapeFillOpacity(it, event.alpha) }) {
                    shapeDelegate.updateShapeFillOpacity(event.alpha)
                }
            }
            is EditorEvent.SelectTool -> {
                when (event.tool) {
                    is EditorTool.Label -> {
                        val current = _state.value
                        val retargetId = LayerGroupOps.retargetForTool(
                            current.layers,
                            current.selectedLayerId,
                            preferFrame = false,
                        )
                        val active = current.layers.find { it.id == retargetId }
                        if (active == null || !active.isLabelLayer) {
                            labelDelegate.addTextLayer(current.template.originalSize.width.toFloat())
                        } else {
                            applyGroupPartSelection(
                                layerId = retargetId,
                                tool = EditorTool.Label,
                            )
                        }
                    }
                    is EditorTool.Shape -> {
                        val current = _state.value
                        val nextTool = if (current.selectedTool is EditorTool.Shape) null else EditorTool.Shape
                        if (nextTool == null) {
                            _state.update { it.copy(selectedTool = null) }
                        } else {
                            val retargetId = LayerGroupOps.retargetForTool(
                                current.layers,
                                current.selectedLayerId,
                                preferFrame = true,
                            )
                            applyGroupPartSelection(
                                layerId = retargetId,
                                tool = EditorTool.Shape,
                            )
                        }
                    }
                    else -> {
                        val requiresLayer = event.tool is EditorTool.Rotate ||
                            event.tool is EditorTool.Shadow ||
                            event.tool is EditorTool.Transparency

                        if (requiresLayer && _state.value.selectedLayerId == null) {
                            _state.update {
                                it.copy(errorMessage = context.getString(R.string.studio_error_select_object))
                            }
                        } else {
                            _state.update {
                                val nextTool =
                                    if (it.selectedTool?.javaClass == event.tool.javaClass) null else event.tool
                                it.copy(selectedTool = nextTool)
                            }
                        }
                    }
                }
            }
            is EditorEvent.SelectCropRatio -> {
                updateActiveLayer { CropMath.resetOffsetForRatio(it, event.ratio) }
                pushHistory()
            }
            is EditorEvent.UpdateCropPan -> {
                updateActiveLayer { CropMath.applyPanDelta(it, event.delta) }
            }
            EditorEvent.CommitCrop -> pushHistory()
            is EditorEvent.SelectLayer -> {
                clearGesturePreview()
                val layers = _state.value.layers
                val currentTool = _state.value.selectedTool
                val (rawSel, rawEdit) = LabelInteractionState.onSelectLayer(event.layerId)
                val (normalizedSel, edit) = LabelInteractionState.normalize(rawSel, rawEdit, layers)
                // Keep FRAME/LABEL sibling aligned with the active tool (I3 / NodePart).
                val sel = when (currentTool) {
                    is EditorTool.Shape -> LayerGroupOps.retargetForTool(layers, normalizedSel, preferFrame = true)
                    is EditorTool.Label -> LayerGroupOps.retargetForTool(layers, normalizedSel, preferFrame = false)
                    else -> normalizedSel
                }
                val targetLayer = layers.find { it.id == sel }
                val memberIds = if (sel != null) UserGroupOps.selectionMembers(layers, sel) else emptySet()
                val (anchor, ids) = if (memberIds.size > 1) {
                    sel to memberIds
                } else {
                    SelectionState.singleSelect(layers, sel)
                }
                _state.update {
                    it.copy(
                        selectedLayerId = anchor,
                        selectedLayerIds = ids,
                        editingLayerId = edit,
                        selectedTool = targetLayer?.preferredEditorTool()
                            ?: if (it.selectedTool == EditorTool.Shape || it.selectedTool == EditorTool.Label) {
                                null
                            } else {
                                it.selectedTool
                            },
                    )
                }
                syncDocumentSelection(sel)
            }
            is EditorEvent.ToggleLayerSelection -> {
                clearGesturePreview()
                val current = _state.value
                val (anchor, ids) = SelectionState.toggle(
                    current.layers,
                    current.selectedLayerIds,
                    current.selectedLayerId,
                    event.layerId,
                )
                val (sel, edit) = LabelInteractionState.normalize(anchor, null, current.layers)
                _state.update {
                    it.copy(
                        selectedLayerId = sel,
                        selectedLayerIds = ids,
                        editingLayerId = edit,
                    )
                }
            }
            EditorEvent.GroupLayers -> {
                groupSelectedLayers()
            }
            EditorEvent.UngroupLayers -> {
                clearGesturePreview()
                val current = _state.value
                val ids = SelectionState.effectiveIds(current)
                if (!UserGroupOps.canUngroup(current.layers, ids, current.userGroupMaps)) {
                    _state.update {
                        it.copy(errorMessage = context.getString(R.string.studio_error_not_grouped))
                    }
                    return
                }
                val groupId = ids.mapNotNull { id ->
                    current.layers.find { it.id == id }?.userGroupId
                }.firstOrNull()
                val (updatedLayers, maps) = UserGroupOps.ungroup(
                    current.layers,
                    current.userGroupMaps,
                    ids,
                )
                val anchor = groupId?.let {
                    UserGroupOps.firstMemberId(updatedLayers, it, maps)
                }
                _state.update {
                    it.copy(
                        layers = updatedLayers,
                        userGroups = maps.groups,
                        userGroupBundles = maps.bundles,
                        selectedLayerId = anchor,
                        selectedLayerIds = anchor?.let { id -> setOf(id) } ?: emptySet(),
                    )
                }
                pushHistory()
            }
            EditorEvent.DuplicateLayer -> {
                val current = _state.value
                val ids = SelectionState.effectiveIds(current)
                if (ids.isEmpty()) {
                    _state.update { it.copy(errorMessage = context.getString(R.string.studio_error_select_object)) }
                    return
                }
                val roots = SelectionState.selectionRoots(current.layers, ids)
                var newLayers = current.layers
                var newMaps = current.userGroupMaps
                var primaryId: String? = null
                val processedUserGroups = mutableSetOf<String>()
                for (rootId in roots) {
                    val layer = newLayers.find { it.id == rootId }
                    val ugid = layer?.userGroupId
                    if (ugid != null && ugid in processedUserGroups) continue

                    if (ugid != null) {
                        processedUserGroups += ugid
                        val (dupLayers, dupMaps, pid) = UserGroupOps.duplicateUserGroup(
                            newLayers,
                            newMaps,
                            ugid,
                        )
                        newLayers = dupLayers
                        newMaps = dupMaps
                        if (primaryId == null) primaryId = pid
                    } else {
                        val (dupLayers, pid) = LayerGroupOps.duplicate(newLayers, rootId)
                        newLayers = dupLayers
                        if (primaryId == null) primaryId = pid
                    }
                }
                if (primaryId != null) {
                    val memberIds = UserGroupOps.selectionMembers(newLayers, primaryId)
                    val (anchor, newIds) = if (memberIds.size > 1) {
                        primaryId to memberIds
                    } else {
                        SelectionState.singleSelect(newLayers, primaryId)
                    }
                    _state.update {
                        it.copy(
                            layers = newLayers,
                            userGroups = newMaps.groups,
                            userGroupBundles = newMaps.bundles,
                            selectedLayerId = anchor,
                            selectedLayerIds = newIds,
                        )
                    }
                    pushHistory()
                }
            }
            EditorEvent.DeleteLayer -> {
                val current = _state.value
                val ids = SelectionState.effectiveIds(current)
                if (ids.isEmpty()) {
                    _state.update { it.copy(errorMessage = context.getString(R.string.studio_error_select_object)) }
                    return
                }
                val removeIds = SelectionState.deleteIds(current.layers, ids)
                _state.update {
                    it.copy(
                        layers = it.layers.filterNot { layer -> layer.id in removeIds },
                        selectedLayerId = null,
                        selectedLayerIds = emptySet(),
                        editingLayerId = null,
                    )
                }
                pushHistory()
            }
            EditorEvent.CommitTransform -> {
                val preview = _gesturePreview.value
                val labelWasCornerScaled = preview?.labelTextScaled == true
                val labelWidthResized = preview?.labelWidthResized == true
                commitGesturePreview()
                if (isLabelOrTextInShapeSelection()) {
                    commitLabelGestureViaDocument(
                        wasCornerScaled = labelWasCornerScaled,
                        widthResized = labelWidthResized,
                    )
                } else {
                    bakeViewportScaleForSelection()
                }
                pushHistory()
            }
            EditorEvent.Undo -> {
                clearGesturePreview()
                undo()
            }
            EditorEvent.Redo -> {
                clearGesturePreview()
                redo()
            }
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
            EditorEvent.DeselectLayer -> {
                clearGesturePreview()
                deselectLayer()
            }
            EditorEvent.FinishTextEdit -> finishTextEdit()
            EditorEvent.ClearError -> _state.update { it.copy(errorMessage = null) }
            EditorEvent.ClearExportResult -> _state.update { it.copy(exportResult = null) }
            EditorEvent.ClearDraftSaved -> _state.update { it.copy(draftSavedAt = null) }
            is EditorEvent.Export -> export(event.templateAssetPath)
        }
    }

    private fun startTextEdit(layerId: String) {
        val layer = _state.value.layers.find { it.id == layerId } ?: return
        if (!layer.isLabelLayer) return
        val (rawSel, rawEdit) = LabelInteractionState.onStartTextEdit(layerId)
        val (sel, edit) = LabelInteractionState.normalize(rawSel, rawEdit, _state.value.layers)
        val (_, ids) = SelectionState.singleSelect(_state.value.layers, sel)
        _state.update {
            it.copy(
                selectedLayerId = sel,
                selectedLayerIds = ids,
                editingLayerId = edit,
                selectedTool = layer.preferredEditorTool(),
            )
        }
    }

    private fun finishTextEdit() {
        val editingId = _state.value.editingLayerId
        val text = _state.value.layers.find { it.id == editingId }?.text
        if (editingId != null && text != null) {
            applyDocument(selectedLayerId = editingId) { editText(it, text, inline = false) }
        } else {
            refitActiveLabelHeightToText()
            requestHistoryPush()
        }
        _state.update { state ->
            val (sel, edit) = LabelInteractionState.onFinishTextEdit(
                state.selectedLayerId,
                state.editingLayerId,
            )
            state.copy(selectedLayerId = sel, editingLayerId = edit)
        }
    }

    private fun deselectLayer() {
        _state.update { state ->
            val (rawSel, rawEdit) = LabelInteractionState.onDeselectLayer()
            val (sel, edit) = LabelInteractionState.normalize(rawSel, rawEdit, state.layers)
            state.copy(selectedLayerId = sel, selectedLayerIds = emptySet(), editingLayerId = edit)
        }
    }

    private fun groupSelectedLayers() {
        clearGesturePreview()
        val current = _state.value
        val ids = SelectionState.effectiveIds(current)
        if (!UserGroupOps.canGroup(current.layers, ids)) {
            _state.update {
                it.copy(errorMessage = context.getString(R.string.studio_error_group_requires_two))
            }
            return
        }

        val prep = UserGroupOps.prepareGroup(current.layers, ids) ?: return
        val (updatedLayers, maps) = UserGroupOps.applyContainerGroup(
            layers = current.layers,
            maps = current.userGroupMaps,
            prep = prep,
        )
        _state.update {
            it.copy(
                layers = updatedLayers,
                userGroups = maps.groups,
                userGroupBundles = maps.bundles,
                selectedLayerId = prep.orderedMemberIds.firstOrNull(),
                selectedLayerIds = prep.memberIds,
            )
        }
        pushHistory()
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
            strokeDashGapPx = layer.strokeDashGapPx,
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
                strokeDashGapPx = clip.strokeDashGapPx,
                cornerRadiusX = clip.cornerRadiusX,
                cornerRadiusY = clip.cornerRadiusY,
                appearance = clip.appearance,
            )
        }
        pushHistory()
    }

    private fun publishCloudTemplateShell(cloudTemplate: CloudTemplate) {
        val shell = templateLoader.cloudTemplateShell(cloudTemplate)
        _state.update { state ->
            state.copy(
                template = shell.copy(
                    objectAssetPath = state.template.objectAssetPath,
                ),
                errorMessage = null,
            )
        }
    }

    private fun prepareTemplatePreview(themeplate: com.thgiang.image.studio.model.StudioThemeplate) {
        if (themeplate.id == "draft") return

        val previewUrl = themeplate.backgroundAssetPath?.takeIf { it.isNotBlank() }
            ?: themeplate.assetPath.takeIf { it.isNotBlank() }
            ?: return

        val fallbackSize = 1080
        val width = themeplate.canvasWidth.takeIf { it > 0 } ?: fallbackSize
        val height = themeplate.canvasHeight.takeIf { it > 0 } ?: fallbackSize

        _state.update { state ->
            if (state.template.loaded && state.template.originalSize.width > 0) {
                return@update state
            }
            state.copy(
                template = state.template.copy(
                    assetPath = previewUrl,
                    thumbnailUrl = previewUrl,
                    originalWidth = width,
                    originalHeight = height,
                    loaded = false,
                    objectAssetPath = themeplate.objectSourceAssetPath ?: state.template.objectAssetPath,
                ),
                errorMessage = null,
            )
        }
    }

    private fun applyLoadedTemplate(loaded: com.thgiang.image.studio.ui.editor.load.LoadedEditorTemplate) {
        clearGesturePreview()
        _state.update {
            val layers = try {
                EditorLayerNormalizer.normalize(loaded.layers.ifEmpty { it.layers })
            } catch (e: Exception) {
                android.util.Log.e("ThemeplateEditorVM", "Failed to normalize loaded layers", e)
                loaded.layers.filterIsInstance<EditorLayer>().ifEmpty { it.layers }
            }
            val sel = loaded.selectedLayerId ?: it.selectedLayerId
            val (_, ids) = SelectionState.singleSelect(layers, sel)
            it.copy(
                template = loaded.template,
                layers = layers,
                selectedLayerId = sel,
                selectedLayerIds = ids,
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
        templateLoadJob = viewModelScope.launch {
            beginTemplateLoading()
            try {
                publishCloudTemplateShell(cloudTemplate)
                val loaded = withContext(Dispatchers.IO) {
                    templateLoader.buildFromCloud(cloudTemplate)
                }
                applyLoadedTemplate(loaded)
                android.util.Log.d(
                    BG_DEBUG_TAG,
                    "state applied templateId=${cloudTemplate.templateId} editorLayers=${loaded.layers.size}"
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e(TAG, "loadCloudTemplate failed: $assetPath", e)
                _state.update {
                    it.copy(errorMessage = context.getString(R.string.studio_error_load_cloud_template, e.message ?: ""))
                }
            } finally {
                endTemplateLoading()
            }
        }
    }

    private fun shouldLoadSampleObjectOnTemplateInit(): Boolean {
        // Restoring a draft already has persisted layers — injecting sample object duplicates them.
        if (!draftId.isNullOrBlank() && _state.value.layers.isNotEmpty()) return false
        return true
    }

    private fun loadBlankCanvas() {
        val current = _state.value.template
        if (current.loaded && current.assetPath.isBlank() && current.originalSize.width > 0) {
            dismissTemplateLoadingIfIdle()
            return
        }

        clearGesturePreview()
        historyManager.clear()
        savedStateHandle["template_path"] = ""

        _state.update {
            it.copy(
                template = EditorTemplate(
                    assetPath = "",
                    originalWidth = StudioThemeplates.BLANK_CANVAS_SIZE,
                    originalHeight = StudioThemeplates.BLANK_CANVAS_SIZE,
                    backgroundColorArgb = android.graphics.Color.TRANSPARENT,
                    loaded = true,
                ),
                layers = emptyList(),
                selectedLayerId = null,
                selectedLayerIds = emptySet(),
                editingLayerId = null,
                selectedTool = null,
                errorMessage = null,
            )
        }
        dismissTemplateLoadingIfIdle()
    }

    private fun loadTemplate(assetPath: String, objectSourceAssetPath: String? = null) {
        if (assetPath.isBlank()) {
            dismissTemplateLoadingIfIdle()
            return
        }

        val currentTemplate = _state.value.template
        if (
            assetPath == currentTemplate.assetPath &&
            currentTemplate.loaded &&
            currentTemplate.originalSize.width > 0
        ) {
            dismissTemplateLoadingIfIdle()
            if (objectSourceAssetPath != null && shouldLoadSampleObjectOnTemplateInit()) {
                loadSampleObject(objectSourceAssetPath, partOfTemplateInit = true)
            }
            return
        }

        savedStateHandle["template_path"] = assetPath
        templateLoadJob?.cancel()
        templateLoadJob = viewModelScope.launch {
            beginTemplateLoading()
            try {
                val loaded = withContext(Dispatchers.IO) {
                    templateLoader.probeLocalAsset(assetPath)
                }
                _state.update {
                    it.copy(
                        template = loaded.template.copy(
                            objectAssetPath = objectSourceAssetPath ?: it.template.objectAssetPath,
                        ),
                        errorMessage = null,
                    )
                }
                android.util.Log.d(TAG, "Successfully loaded template state")
                if (objectSourceAssetPath != null && shouldLoadSampleObjectOnTemplateInit()) {
                    loadSampleObjectForTemplateInit(objectSourceAssetPath)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e(TAG, "loadTemplate failed to load template: $assetPath", e)
                _state.update {
                    it.copy(errorMessage = context.getString(R.string.studio_error_load_template, e.message ?: ""))
                }
            } finally {
                endTemplateLoading()
            }
        }
    }

    private var sampleObjectLoadJob: Job? = null

    private suspend fun loadSampleObjectForTemplateInit(assetPath: String) {
        val processingId = productWorkflow.newProcessingId()
        _state.update {
            val (_, ids) = SelectionState.singleSelect(it.layers, processingId)
            it.copy(
                layers = it.layers + productWorkflow.buildSampleProcessingLayer(processingId),
                selectedLayerId = processingId,
                selectedLayerIds = ids,
            )
        }
        runSampleObjectLoad(assetPath, processingId, recordHistory = true)
    }

    private fun loadSampleObject(assetPath: String, partOfTemplateInit: Boolean = false) {
        sampleObjectLoadJob?.cancel()
        val processingId = productWorkflow.newProcessingId()
        _state.update {
            val (_, ids) = SelectionState.singleSelect(it.layers, processingId)
            it.copy(
                layers = it.layers + productWorkflow.buildSampleProcessingLayer(processingId),
                selectedLayerId = processingId,
                selectedLayerIds = ids,
            )
        }
        sampleObjectLoadJob = viewModelScope.launch {
            if (partOfTemplateInit) beginTemplateLoading()
            try {
                runSampleObjectLoad(assetPath, processingId, recordHistory = partOfTemplateInit)
            } finally {
                if (partOfTemplateInit) endTemplateLoading()
            }
        }
    }

    private suspend fun runSampleObjectLoad(
        assetPath: String,
        processingId: String,
        recordHistory: Boolean,
    ) {
        try {
            android.util.Log.d(TAG, "loadSampleObject: extracting $assetPath")
            when (val result = productWorkflow.loadSampleObject(assetPath)) {
                is SampleObjectResult.Ready -> {
                    _state.update { state ->
                        val updatedLayers = state.layers.map {
                            if (it.id == processingId) {
                                it.copy(
                                    product = result.product,
                                    viewport = EditorViewport(scale = 1f),
                                ).withOpaqueContentBounds(result.opaqueBounds)
                            } else {
                                it
                            }
                        }
                        state.copy(layers = updatedLayers, showBoundingBox = true)
                    }
                    triggerOverlay()
                    if (recordHistory) pushHistory()
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

    // ============ Sticker ============

    /**
     * Tải 20 icon mặc định từ folder materials_icon (quick sticker strip).
     */
    fun loadStickerPreview() {
        if (_stickerState.value.preview.isNotEmpty()) return

        // Đang load → tránh gọi trùng
        if (stickerPreviewJob?.isActive == true) return

        stickerPreviewJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _stickerState.update { it.copy(isLoadingPreview = true, previewError = false) }
            try {
                val preview = stickerRepository.fetchPreview()
                _stickerState.update {
                    it.copy(
                        preview = preview,
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

    fun loadBackgroundPreview() {
        if (_backgroundState.value.preview.isNotEmpty()) return
        if (backgroundPreviewJob?.isActive == true) return

        backgroundPreviewJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _backgroundState.update { it.copy(isLoadingPreview = true, previewError = false) }
            try {
                val preview = backgroundRepository.fetchPreview()
                _backgroundState.update {
                    it.copy(
                        preview = preview,
                        isLoadingPreview = false,
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "loadBackgroundPreview failed", e)
                _backgroundState.update { it.copy(isLoadingPreview = false, previewError = true) }
            }
        }
    }

    fun invalidateBackgroundCache() {
        backgroundRepository.invalidateCache()
        _backgroundState.update { BackgroundUiState() }
        loadBackgroundPreview()
    }

    private fun applyBackground(url: String) {
        if (url.isBlank()) return
        _state.update { state ->
            state.copy(
                template = state.template.copy(assetPath = url),
            )
        }
        pushHistory()
    }

    private fun addSticker(assetPath: String) {
        viewModelScope.launch {
            try {
                when (val result = productWorkflow.buildStickerLayer(assetPath, _state.value.template.originalSize)) {
                    is StickerResult.Ready -> {
                        _state.update { state ->
                            val (_, ids) = SelectionState.singleSelect(state.layers + result.layer, result.layer.id)
                            state.copy(
                                layers = state.layers + result.layer,
                                selectedLayerId = result.layer.id,
                                selectedLayerIds = ids,
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
            val (_, ids) = SelectionState.singleSelect(update.layers, update.processingId)
            it.copy(
                layers = update.layers,
                selectedLayerId = update.processingId,
                selectedLayerIds = ids,
                exportResult = null,
                errorMessage = null,
                showBoundingBox = false,
            )
        }
        historyManager.clear()

        bgRemoveJob = viewModelScope.launch {
            try {
                when (val result = productWorkflow.processUserImage(uri, removeBg = replaceLayerId != null)) {
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
                                        ).withOpaqueContentBounds(result.opaqueBounds)
                                    } else {
                                        // Fit longest side to ~50% of template so "Thêm ảnh" is usable
                                        // on large PSD canvases. Allow upscale when source pixels are small.
                                        // Corner scale can still grow further (viewport MAX_SCALE = 40).
                                        val newW = result.product.baseWidth.toFloat().coerceAtLeast(1f)
                                        val newH = result.product.baseHeight.toFloat().coerceAtLeast(1f)
                                        val templateSize = state.template.originalSize
                                        val targetMax = if (templateSize.width > 0 && templateSize.height > 0) {
                                            minOf(templateSize.width, templateSize.height) * 0.50f
                                        } else {
                                            800f
                                        }
                                        val scaleFactor = targetMax / maxOf(newW, newH)

                                        it.copy(
                                            product = result.product,
                                            shapeWidthPx = newW * scaleFactor,
                                            shapeHeightPx = newH * scaleFactor,
                                            viewport = it.viewport.withScale(1f),
                                        ).withOpaqueContentBounds(result.opaqueBounds)
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

    private fun removeBackground(layerId: String) {
        val layer = _state.value.layers.find { it.id == layerId } ?: return
        val uriStr = layer.product.originalUriString ?: layer.product.foregroundUriString ?: return
        val uri = Uri.parse(uriStr)

        bgRemoveJob?.cancel()

        _state.update { state ->
            val updatedLayers = state.layers.map {
                if (it.id == layerId) {
                    it.copy(product = it.product.copy(processing = true))
                } else {
                    it
                }
            }
            state.copy(layers = updatedLayers)
        }

        bgRemoveJob = viewModelScope.launch {
            try {
                when (val result = productWorkflow.processUserImage(uri)) {
                    is ProductImageResult.Ready -> {
                        _state.update { state ->
                            val updatedLayers = state.layers.map {
                                if (it.id == layerId) {
                                    it.copy(
                                        product = result.product.copy(
                                            isSample = it.product.isSample,
                                            originalUriString = uriStr
                                        )
                                    ).withOpaqueContentBounds(result.opaqueBounds)
                                } else {
                                    it
                                }
                            }
                            state.copy(layers = updatedLayers)
                        }
                        triggerOverlay()
                        pushHistory()
                    }
                    is ProductImageResult.Failed -> {
                        _state.update { state ->
                            val updatedLayers = state.layers.map {
                                if (it.id == layerId) {
                                    it.copy(product = it.product.copy(processing = false))
                                } else {
                                    it
                                }
                            }
                            state.copy(
                                layers = updatedLayers,
                                errorMessage = result.message ?: context.getString(R.string.studio_error_process_image)
                            )
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e(TAG, "removeBackground failed", e)
                _state.update { state ->
                    val updatedLayers = state.layers.map {
                        if (it.id == layerId) {
                            it.copy(product = it.product.copy(processing = false))
                        } else {
                            it
                        }
                    }
                    state.copy(
                        layers = updatedLayers,
                        errorMessage = e.message ?: context.getString(R.string.studio_error_process_image)
                    )
                }
            }
        }
    }

    private fun removeProcessingLayer(processingId: String, errorMessage: String? = null) {
        _state.update { state ->
            val newSel = if (state.selectedLayerId == processingId) null else state.selectedLayerId
            val newIds = if (processingId in state.selectedLayerIds) {
                state.selectedLayerIds - processingId
            } else {
                state.selectedLayerIds
            }
            state.copy(
                layers = state.layers.filterNot { it.id == processingId },
                selectedLayerId = newSel,
                selectedLayerIds = newIds,
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

    private fun applyLabelTransformDelta(block: (EditorViewport) -> EditorViewport) {
        val id = _state.value.selectedLayerId ?: return
        val layer = resolveLabelPreviewLayer(id) ?: return
        val next = block(layer.viewport)
        applyDocument(selectedLayerId = id, recordHistory = false) {
            setTransform(it, com.thgiang.image.studio.ui.editor.document.model.NodeTransform.fromViewport(next))
        }
    }

    private fun resolveLabelPreviewLayer(selectedId: String): EditorLayer? {
        val layers = _state.value.layers
        val selected = layers.find { it.id == selectedId } ?: return null
        if (selected.isLabelLayer) return selected
        val gid = selected.groupId ?: return selected
        return layers.find { it.groupId == gid && it.isLabelLayer } ?: selected
    }

    /**
     * After live gesture preview is merged into EditorState, bake / measure via DocumentSession
     * so FRAME+LABEL stay in sync (I3) and box size comes from LayoutEngine (I2).
     */
    private fun commitLabelGestureViaDocument(
        wasCornerScaled: Boolean,
        widthResized: Boolean,
    ) {
        val id = _state.value.selectedLayerId ?: return
        val preview = resolveLabelPreviewLayer(id) ?: return
        // Only hug height after width-edge resize — never on pan/rotate/height-grow.
        val hugHeight = widthResized &&
            !wasCornerScaled &&
            !preview.textForm.isActive &&
            kotlin.math.abs(preview.viewport.scale - 1f) < 0.001f
        if (!applyDocument(selectedLayerId = id, recordHistory = false) {
                commitLabelGesture(it, preview, hugHeightAfterEdge = hugHeight)
            }
        ) {
            bakeViewportScaleForSelection()
            if (hugHeight) refitActiveLabelHeightToText()
        }
    }

    private fun updateRotation(delta: Float) {
        if (isLabelOrTextInShapeSelection()) {
            applyLabelTransformDelta { vp ->
                var newRotation = (vp.rotation + delta) % 360f
                if (newRotation < 0) newRotation += 360f
                val snapped = SNAP_ANGLES.minByOrNull { angle -> abs(angle - newRotation) }
                if (snapped != null && abs(snapped - newRotation) < SNAP_ANGLE_THRESHOLD) {
                    newRotation = snapped
                }
                vp.withRotation(newRotation)
            }
            return
        }
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
        val groupId = selected.groupId
        val anyNeedsBake = state.layers.any { layer ->
            LayerViewportScale.needsBake(layer) &&
                (layer.id == layerId || (groupId != null && layer.groupId == groupId))
        }
        if (!anyNeedsBake) return

        // Bake from LABEL member so textSizeSp is multiplied (frame-only bake would desync glyphs).
        val bakeId = groupId?.let { gid ->
            state.layers.find { it.groupId == gid && it.isLabelLayer }?.id
        } ?: layerId

        _state.update { current ->
            current.copy(
                layers = LayerGroupSync.apply(current.layers, bakeId) { layer ->
                    LayerViewportScale.bake(layer)
                },
            )
        }
    }

    private fun clearGesturePreview() {
        _gesturePreview.value = null
    }

    private fun commitGesturePreview() {
        val preview = _gesturePreview.value ?: return
        _state.update { it.copy(layers = preview.layers) }
        clearGesturePreview()
    }

    private fun applyGestureDeltaToLayer(layer: EditorLayer, delta: GestureDelta): EditorLayer {
        val transformed = GestureLayerOps.applyDelta(layer, delta)
        if (
            transformed.isLabelLayer &&
            !transformed.textForm.isActive &&
            delta.scale == 1f &&
            (delta.deltaWidth != 0f || delta.deltaHeight < 0f)
        ) {
            return transformed.withShapeHeightFittedToText(context)
        }
        return transformed
    }

    private fun applyGesturePreview(delta: GestureDelta) {
        val state = _state.value
        val ids = SelectionState.effectiveIds(state)
        val anchorId = state.selectedLayerId ?: ids.firstOrNull() ?: return
        val baseLayers = _gesturePreview.value?.layers ?: state.layers
        val updatedLayers = if (ids.size <= 1) {
            LayerGroupSync.apply(baseLayers, anchorId) { layer ->
                applyGestureDeltaToLayer(layer, delta)
            }
        } else {
            baseLayers.map { layer ->
                if (layer.id in ids) applyGestureDeltaToLayer(layer, delta) else layer
            }
        }
        val labelTextScaled = delta.scale != 1f &&
            baseLayers.any { it.id in ids && it.isLabelLayer }
        val labelWidthResized = delta.deltaWidth != 0f &&
            baseLayers.any { it.id in ids && it.isLabelLayer }
        _gesturePreview.value = GesturePreview(
            anchorLayerId = anchorId,
            layers = updatedLayers,
            labelTextScaled = _gesturePreview.value?.labelTextScaled == true || labelTextScaled,
            labelWidthResized = _gesturePreview.value?.labelWidthResized == true || labelWidthResized,
        )
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
                layers = current.layers,
                userGroups = current.userGroups,
                userGroupBundles = current.userGroupBundles,
            )
        )
    }

    private fun undo() {
        historyManager.undo()?.let { snapshot ->
            _state.update {
                it.copy(
                    layers = snapshot.layers,
                    userGroups = snapshot.userGroups,
                    userGroupBundles = snapshot.userGroupBundles,
                )
            }
        }
    }

    private fun redo() {
        historyManager.redo()?.let { snapshot ->
            _state.update {
                it.copy(
                    layers = snapshot.layers,
                    userGroups = snapshot.userGroups,
                    userGroupBundles = snapshot.userGroupBundles,
                )
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
        if (currentState.template.originalSize.width == 0 || currentState.template.originalSize.height == 0) {
            _state.update {
                it.copy(errorMessage = context.getString(R.string.studio_draft_save_failed))
            }
            return
        }

        saveDraftJob?.cancel()
        val generation = saveDraftGeneration.incrementAndGet()
        saveDraftJob = viewModelScope.launch {
            _isSavingDraft.value = true
            try {
                when (
                    val outcome = exportCoordinator.saveDraft(
                        state = currentState,
                        draftId = draftId,
                        templateAssetPath = templateAssetPath,
                        templateThumbnailUrl = currentState.template.thumbnailUrl,
                        cloudTemplateId = themeplateId?.takeIf {
                            it.isNotBlank() &&
                                it != "draft" &&
                                it != StudioThemeplates.BLANK_THEMEPLATE_ID
                        },
                    )
                ) {
                    is SaveDraftOutcome.Success -> {
                        if (generation != saveDraftGeneration.get()) return@launch
                        if (draftId == null) {
                            savedStateHandle["draftId"] = outcome.draftId
                        }
                        _state.update { it.copy(draftSavedAt = outcome.savedAt) }
                    }
                    is SaveDraftOutcome.Failure -> {
                        if (generation != saveDraftGeneration.get()) return@launch
                        appLogger.logNonFatal(
                            outcome.error,
                            mapOf("templateId" to (themeplateId ?: draftId ?: "unknown")),
                        )
                        _state.update {
                            it.copy(errorMessage = context.getString(R.string.studio_draft_save_failed))
                        }
                    }
                }
            } finally {
                if (generation == saveDraftGeneration.get()) {
                    _isSavingDraft.value = false
                }
            }
        }
    }

    // ============ SavedState Persistence ============

    private fun persistState() {
        savedStateHandle["editor_state_template_path"] = _state.value.template.assetPath
        savedStateHandle["editor_state_selected_layer_id"] = _state.value.selectedLayerId
        savedStateHandle["editor_state_selected_layer_ids"] = _state.value.selectedLayerIds.toList()
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
        if (_state.value.template.loaded && _state.value.template.originalSize.width > 0) return
        templateLoadJob?.cancel()
        templateLoadJob = viewModelScope.launch {
            beginTemplateLoading()
            try {
                _state.update { it.copy(errorMessage = null) }
                val cloudTemplate = withContext(Dispatchers.IO) {
                    templateLoader.fetchCloudTemplate(templateId)
                }
                publishCloudTemplateShell(cloudTemplate)
                val loaded = withContext(Dispatchers.IO) {
                    templateLoader.buildFromCloud(cloudTemplate)
                }
                savedStateHandle["template_path"] = loaded.template.assetPath
                applyLoadedTemplate(loaded)
                android.util.Log.d(
                    BG_DEBUG_TAG,
                    "loaded templateId=$templateId layers=${loaded.layers.size}"
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to load remote template $templateId", e)
                _state.update {
                    it.copy(
                        errorMessage = context.getString(
                            R.string.studio_error_load_cloud_template,
                            e.message ?: ""
                        )
                    )
                }
            } finally {
                endTemplateLoading()
            }
        }
    }
}
