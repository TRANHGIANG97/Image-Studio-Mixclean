package com.thgiang.image.studio.ui.editor.screen
import com.thgiang.image.studio.ui.editor.model.*
import com.thgiang.image.studio.ui.editor.panel.*
import com.thgiang.image.studio.ui.editor.canvas.*
import com.thgiang.image.studio.ui.editor.*
import com.thgiang.image.studio.ui.editor.panel.*
import com.thgiang.image.studio.ui.editor.canvas.*

import android.net.Uri
import com.thgiang.image.studio.ui.editor.model.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.hilt.navigation.compose.hiltViewModel
import com.thgiang.image.studio.R
import com.thgiang.image.studio.model.StudioThemeplate
import com.thgiang.image.studio.ui.editor.theme.EditorTheme
import com.thgiang.image.studio.ui.editor.theme.LocalEditorTokens
import com.thgiang.image.studio.ui.editor.theme.MotionTokens
import com.thgiang.image.studio.ui.components.ShimmerBox
import androidx.compose.foundation.layout.aspectRatio
import com.thgiang.image.studio.ui.editor.label.panel.LabelEditTab
import com.thgiang.image.studio.ui.editor.label.panel.LabelEditingKeyboardToolbar
import com.thgiang.image.studio.ui.editor.label.panel.LabelSelectionToolbar
import com.thgiang.image.studio.ui.editor.label.panel.LabelShapePanel
import kotlinx.coroutines.delay

import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ThemeplateEditorScreen(
    themeplate: StudioThemeplate,
    onBack: () -> Unit,
    onDone: (Uri?) -> Unit = {},
    onRequireExportAd: ((() -> Unit) -> Unit)? = null,
    onPickImage: (@Composable (onImageSelected: (Uri) -> Unit, onCancel: () -> Unit) -> Unit)? = null,
    onExportSuccess: () -> Unit = {},
    viewModel: ThemeplateEditorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val gesturePreview by viewModel.gesturePreview.collectAsState()
    val templateAssetPath = state.template.assetPath
    val canUndo by viewModel.canUndo.collectAsState()
    val canRedo by viewModel.canRedo.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showCustomPicker by remember { mutableStateOf(false) }

    var targetReplaceLayerId by remember { mutableStateOf<String?>(null) }
    var quickActionsOffset by remember { mutableStateOf(Offset.Zero) }
    var layersOffset by remember { mutableStateOf(Offset.Zero) }
    
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.onEvent(EditorEvent.SetProductImage(it, targetReplaceLayerId)) }
    }

    val triggerImagePicker = {
        if (onPickImage != null) {
            showCustomPicker = true
        } else {
            pickImageLauncher.launch("image/*")
        }
    }

    val saveSuccessMessage = stringResource(R.string.studio_save_image_success)
    LaunchedEffect(state.exportResult) {
        state.exportResult?.let { uri ->
            snackbarHostState.showSnackbar(saveSuccessMessage)
            onExportSuccess()
            onDone(uri)
        }
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Long)
            viewModel.onEvent(EditorEvent.ClearError)
        }
    }
    
    val draftSavedMessage = stringResource(R.string.studio_draft_saved)
    LaunchedEffect(state.draftSavedAt) {
        if (state.draftSavedAt != null) {
            snackbarHostState.showSnackbar(draftSavedMessage)
        }
    }

    LaunchedEffect(themeplate.id) {
        if (themeplate.id != "draft") {
            val assetPath = themeplate.backgroundAssetPath ?: themeplate.assetPath
            if (assetPath.startsWith("http://") || assetPath.startsWith("https://")) {
                viewModel.onEvent(EditorEvent.LoadCloudTemplateById(themeplate.id))
            } else {
                viewModel.onEvent(EditorEvent.LoadTemplate(assetPath, themeplate.objectSourceAssetPath))
            }
        }
    }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    EditorTheme {
        val tokens = LocalEditorTokens.current
        val activeLayer = state.layers.find { it.id == state.selectedLayerId }
        val editingToolsUnlocked = activeLayer != null && !activeLayer.product.processing
        val selectedToolForUi = state.selectedTool.takeIf { tool ->
            editingToolsUnlocked || tool is EditorTool.Label || tool is EditorTool.Sticker || tool is EditorTool.Shape
        }
        var shouldAutoEditNextLabel by remember { mutableStateOf(false) }
        var activeLabelTab by rememberSaveable {
            mutableStateOf(LabelEditTab.FONT)
        }
        val editingLayer = state.layers.find { it.id == state.editingLayerId }
        val isLabelEditing = state.labelPhase == LabelInteractionPhase.Editing && editingLayer != null
        val isLabelSelected = state.labelPhase == LabelInteractionPhase.Selected &&
            activeLayer?.isLabelLayer == true

        LaunchedEffect(state.selectedLayerId) {
            val selectedId = state.selectedLayerId
            if (selectedId != null && shouldAutoEditNextLabel) {
                viewModel.onEvent(EditorEvent.StartTextEdit(selectedId))
                shouldAutoEditNextLabel = false
            }
        }

        // Reset the label tab only when the selection changes KIND (null/non-label → label);
        // switching between two label layers keeps the user's current tab.
        var previousSelectionWasLabel by remember { mutableStateOf(activeLayer?.isLabelLayer == true) }
        LaunchedEffect(state.selectedLayerId) {
            val isLabelSelection = state.layers
                .find { it.id == state.selectedLayerId }
                ?.isLabelLayer == true
            if (isLabelSelection && !previousSelectionWasLabel) {
                activeLabelTab = LabelEditTab.FONT
            }
            previousSelectionWasLabel = isLabelSelection
        }

        val isImeVisible = WindowInsets.isImeVisible
        var wasEditingImeVisible by remember { mutableStateOf(false) }
        var isExitingLabelEdit by remember { mutableStateOf(false) }
        var isCanvasGestureActive by remember { mutableStateOf(false) }
        var showLayersPanel by rememberSaveable { mutableStateOf(true) }

        val showControls = state.template.loaded &&
            selectedToolForUi != null &&
            !isLabelEditing &&
            !isLabelSelected &&
            (activeLayer != null ||
                selectedToolForUi is EditorTool.Label ||
                selectedToolForUi is EditorTool.Sticker ||
                selectedToolForUi is EditorTool.Shape)

        val bottomToolbarVisible = !isImeVisible && !isLabelEditing && !isLabelSelected

        fun exitLabelEditing(dismissKeyboard: Boolean = true) {
            if (state.editingLayerId == null || isExitingLabelEdit) return
            isExitingLabelEdit = true
            focusManager.clearFocus()
            if (dismissKeyboard) {
                keyboardController?.hide()
            }
            viewModel.onEvent(EditorEvent.FinishTextEdit)
        }

        LaunchedEffect(isLabelEditing) {
            if (!isLabelEditing) {
                isExitingLabelEdit = false
            }
        }

        LaunchedEffect(isImeVisible, isLabelEditing, state.editingLayerId, isCanvasGestureActive) {
            if (!isLabelEditing) {
                wasEditingImeVisible = false
                return@LaunchedEffect
            }
            val imeJustHidden = wasEditingImeVisible && !isImeVisible
            wasEditingImeVisible = isImeVisible
            if (imeJustHidden && !isCanvasGestureActive) {
                delay(300)
                if (state.editingLayerId != null && !isCanvasGestureActive) {
                    exitLabelEditing(dismissKeyboard = false)
                }
            }
        }

        fun confirmLabelEdit() {
            exitLabelEditing()
        }

        fun dispatchLayoutEvent(event: EditorEvent) {
            when {
                event is EditorEvent.RequestTextEdit -> {
                    viewModel.onEvent(EditorEvent.StartTextEdit(event.layerId))
                    keyboardController?.show()
                }
                event is EditorEvent.AddShapeTextLayer ||
                    event is EditorEvent.ConfirmAddLabel ||
                    event is EditorEvent.ConfirmAddLabelText -> {
                    shouldAutoEditNextLabel = true
                    viewModel.onEvent(event)
                }
                else -> viewModel.onEvent(event)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(tokens.moduleBackground)
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null
                ) {
                    if (state.editingLayerId != null) {
                        exitLabelEditing()
                    } else {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                        viewModel.onEvent(EditorEvent.DeselectLayer)
                    }
                }
        ) {

            // ── Layer 1: Canvas ───────────────────────────────────────
            if (state.template.loaded && state.template.originalSize.width > 0) {
                EditorCanvasV2(
                    templateAssetPath = state.template.assetPath,
                    templateBackgroundColor = Color(state.template.backgroundColorArgb),
                    templateSize = state.template.originalSize,
                    layers = gesturePreview?.layers ?: state.layers,
                    selectedLayerId = state.selectedLayerId,
                    selectedLayerIds = state.selectedLayerIds,
                    isCropToolActive = selectedToolForUi is EditorTool.Crop,
                    isLabelToolActive = selectedToolForUi is EditorTool.Label,
                    isShapeToolActive = selectedToolForUi is EditorTool.Shape,
                    editingLayerId = state.editingLayerId,
                    showOverlay = state.showOverlay,
                    viewportPadding = run {
                        val imeHeight = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
                        val navBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                        val toolbarHeight = if (isLabelEditing) 52.dp else 56.dp
                        val bottomPadding = if (imeHeight > 0.dp) {
                            imeHeight + toolbarHeight
                        } else {
                            when {
                                isLabelSelected -> 220.dp + navBarHeight
                                showControls -> 260.dp + navBarHeight
                                bottomToolbarVisible -> toolbarHeight + navBarHeight
                                else -> navBarHeight
                            }
                        }
                        PaddingValues(
                            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 76.dp,
                            bottom = bottomPadding
                        )
                    },
                    onGesture = { delta ->
                        if (state.editingLayerId == null) {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        }
                        viewModel.onEvent(EditorEvent.UpdateGesture(delta))
                    },
                    onGestureEnd = {
                        viewModel.onEvent(EditorEvent.CommitTransform)
                    },
                    onGestureActiveChanged = { active ->
                        isCanvasGestureActive = active
                    },
                    onPickImage = { layerId ->
                        // Prefer the tapped sample layer so swap icon never replaces the wrong object.
                        targetReplaceLayerId = layerId ?: state.selectedLayerId
                        if (layerId != null && layerId != state.selectedLayerId) {
                            viewModel.onEvent(EditorEvent.SelectLayer(layerId))
                        }
                        triggerImagePicker()
                    },
                    onSelectLayer = { id ->
                        focusManager.clearFocus()
                        keyboardController?.hide()
                        viewModel.onEvent(EditorEvent.SelectLayer(id))
                    },
                    onShapeTextCommit = { text -> viewModel.onEvent(EditorEvent.UpdateShapeText(text)) },
                    onSyncShapeSize = { widthPx, heightPx ->
                        viewModel.onEvent(EditorEvent.SyncShapeSize(widthPx, heightPx))
                    },
                    onEvent = { viewModel.onEvent(it) },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                val aspect = state.template.originalSize.let { size ->
                    if (size.width > 0 && size.height > 0) size.width.toFloat() / size.height else 9f / 16f
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 76.dp,
                            bottom = 72.dp,
                            start = 24.dp,
                            end = 24.dp,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(aspect)
                            .clip(RoundedCornerShape(12.dp)),
                    )
                }
            }

            if (showLayersPanel && state.template.loaded && state.layers.isNotEmpty()) {
                EditorObjectListVertical(
                    layers = state.layers,
                    selectedLayerId = state.selectedLayerId,
                    selectedLayerIds = state.selectedLayerIds,
                    onSelectLayer = { id -> viewModel.onEvent(EditorEvent.SelectLayer(id)) },
                    layersOffset = layersOffset,
                    onLayersOffsetChange = { layersOffset = it },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 12.dp)
                        .zIndex(5f)
                )
            }

            // ── Layer 2: Top bar — Premium Clean White ────────────────
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(64.dp)
                    .background(Color.White)
                    .drawBehind {
                        drawRect(
                            color = tokens.borderSubtle,
                            topLeft = Offset(0f, size.height - 1.dp.toPx()),
                            size = Size(size.width, 1.dp.toPx())
                        )
                    }
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back + Undo + Redo
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(Color(0xFFF3F4F6))
                        ) {
                            EditorBackIcon(
                                modifier = Modifier.size(20.dp),
                                tint = tokens.textPrimary
                            )
                        }
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = { viewModel.onEvent(EditorEvent.Undo) },
                                enabled = canUndo,
                                modifier = Modifier.size(32.dp)
                            ) {
                                EditorUndoIcon(
                                    modifier = Modifier.size(20.dp),
                                    tint = if (canUndo) tokens.textPrimary.copy(alpha = 0.65f)
                                           else tokens.textDisabled.copy(alpha = 0.32f)
                                )
                            }
                            IconButton(
                                onClick = { viewModel.onEvent(EditorEvent.Redo) },
                                enabled = canRedo,
                                modifier = Modifier.size(32.dp)
                            ) {
                                EditorRedoIcon(
                                    modifier = Modifier.size(20.dp),
                                    tint = if (canRedo) tokens.textPrimary.copy(alpha = 0.65f)
                                           else tokens.textDisabled.copy(alpha = 0.32f)
                                )
                            }
                            if (state.template.loaded && state.layers.isNotEmpty()) {
                                IconButton(
                                    onClick = { showLayersPanel = !showLayersPanel },
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                        .background(
                                            if (showLayersPanel) tokens.accentSoft
                                            else Color.Transparent,
                                        ),
                                ) {
                                    LayersIcon(
                                        tint = if (showLayersPanel) tokens.accent
                                        else tokens.textPrimary.copy(alpha = 0.65f),
                                    )
                                }
                            }
                        }
                    }

                    // Save draft + Export
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (state.isExporting) {
                            Box(
                                modifier = Modifier
                                    .height(36.dp)
                                    .padding(end = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                StudioLottieLoader(modifier = Modifier.size(36.dp))
                            }
                        } else {
                            // Save Draft Button
                            Text(
                                text = stringResource(R.string.studio_save_draft),
                                color = tokens.textPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .clickable { viewModel.onEvent(EditorEvent.SaveDraft) }
                                    .padding(horizontal = 8.dp, vertical = 8.dp)
                            )
                            
                            Box(
                                modifier = Modifier
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(
                                        if (state.canExport) tokens.accent
                                        else tokens.textDisabled.copy(alpha = 0.20f)
                                    )
                                    .clickable(enabled = state.canExport) {
                                        val exportAction = {
                                            viewModel.onEvent(EditorEvent.Export(templateAssetPath))
                                        }
                                        onRequireExportAd?.invoke(exportAction) ?: exportAction()
                                    }
                                    .padding(horizontal = 20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.studio_export),
                                    color = if (state.canExport) Color.White else tokens.textDisabled,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // ── Layer 3: Bottom controls + toolbar ────────────────────

            // Shared toolbar click handler
            val onToolClicked: (EditorTool?) -> Unit = { tool ->
                if (tool is EditorTool.Duplicate) {
                    viewModel.onEvent(EditorEvent.DuplicateLayer)
                } else if (tool is EditorTool.Delete) {
                    viewModel.onEvent(EditorEvent.DeleteLayer)
                } else if (tool != null) {
                    if (tool == EditorTool.Shape) {
                        if (selectedToolForUi == EditorTool.Shape) {
                            // Toggle the tool off only — keep the current layer selection.
                            viewModel.onEvent(EditorEvent.SelectTool(EditorTool.Shape))
                        } else {
                            viewModel.onEvent(EditorEvent.SelectLayer(null))
                            viewModel.onEvent(EditorEvent.SelectTool(EditorTool.Shape))
                        }
                    } else if (tool == selectedToolForUi && activeLayer != null && tool is EditorTool.Label) {
                        viewModel.onEvent(EditorEvent.SelectLayer(null))
                    } else {
                        if (tool is EditorTool.Label && (activeLayer == null || !activeLayer.isLabelLayer)) {
                            shouldAutoEditNextLabel = true
                        }
                        viewModel.onEvent(EditorEvent.SelectTool(tool))
                    }
                }
            }

            // Controls panel slides up with IME — placed BELOW canvas in z-order
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(
                        WindowInsets.ime.union(WindowInsets.safeDrawing)
                            .only(WindowInsetsSides.Bottom),
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AnimatedVisibility(
                    visible = isLabelEditing && editingLayer != null,
                    modifier = Modifier.fillMaxWidth(),
                    enter = slideInVertically(MotionTokens.springPanel()) { it } + fadeIn(MotionTokens.fadeDefault),
                    exit = slideOutVertically(MotionTokens.springPanel()) { it } + fadeOut(MotionTokens.fadeQuick),
                ) {
                    val layer = editingLayer ?: return@AnimatedVisibility
                    LabelEditingKeyboardToolbar(
                        layer = layer,
                        tokens = tokens,
                        onLayoutEvent = { viewModel.onEvent(it) },
                        onDismissKeyboard = { confirmLabelEdit() },
                    )
                }

                AnimatedVisibility(
                    visible = isLabelSelected && activeLayer != null,
                    modifier = Modifier.fillMaxWidth(),
                    enter = slideInVertically(MotionTokens.springPanel()) { it } + fadeIn(MotionTokens.fadeDefault),
                    exit = slideOutVertically(MotionTokens.springPanel()) { it } + fadeOut(MotionTokens.fadeQuick),
                ) {
                    val layer = activeLayer ?: return@AnimatedVisibility
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White)
                    ) {
                        LabelSelectionToolbar(
                            activeTab = activeLabelTab,
                            onTabSelected = { tab ->
                                if (tab == LabelEditTab.EDIT) {
                                    viewModel.onEvent(EditorEvent.StartTextEdit(layer.id))
                                    keyboardController?.show()
                                } else {
                                    activeLabelTab = tab
                                }
                            },
                            tokens = tokens,
                        )
                        LabelShapePanel(
                            selectedLayer = layer,
                            onLayoutEvent = { dispatchLayoutEvent(it) },
                            tokens = tokens,
                            canvasFirstMode = true,
                            showTabBar = false,
                            activeTab = activeLabelTab,
                            onActiveTabChange = { activeLabelTab = it },
                        )
                    }
                }

                AnimatedVisibility(
                    visible = showControls,
                    modifier = Modifier.fillMaxWidth(),
                    enter = slideInVertically(MotionTokens.springPanel()) { it } + fadeIn(MotionTokens.fadeDefault),
                    exit = slideOutVertically(MotionTokens.springPanel()) { it } + fadeOut(MotionTokens.fadeQuick),
                ) {
                    if (selectedToolForUi != null) {
                        EditorControlsV2(
                            tool = selectedToolForUi,
                            appearance = activeLayer?.appearance ?: EditorAppearance(shadowIntensity = 0f),
                            cropRatio = activeLayer?.cropRatio ?: CropRatio.ORIGINAL,
                            selectedLayer = activeLayer,
                            onUpdateShadow = { viewModel.onEvent(EditorEvent.UpdateShadow(it)) },
                            onUpdateShadowAngle = { viewModel.onEvent(EditorEvent.UpdateShadowAngle(it)) },
                            onUpdateShadowDistance = { viewModel.onEvent(EditorEvent.UpdateShadowDistance(it)) },
                            onUpdateShadowColor = { viewModel.onEvent(EditorEvent.UpdateShadowColor(it)) },
                            onUpdateShadowBlur = { viewModel.onEvent(EditorEvent.UpdateShadowBlur(it)) },
                            onUpdateAlpha = { viewModel.onEvent(EditorEvent.UpdateAlpha(it)) },
                            onSelectCropRatio = { viewModel.onEvent(EditorEvent.SelectCropRatio(it)) },
                            onLayoutEvent = { dispatchLayoutEvent(it) },
                        )
                    }
                }

                AnimatedVisibility(
                    visible = !isImeVisible && !isLabelEditing && !isLabelSelected,
                    modifier = Modifier.fillMaxWidth(),
                    enter = slideInVertically(MotionTokens.springPanel()) { it } + fadeIn(MotionTokens.fadeDefault),
                    exit = slideOutVertically(MotionTokens.springPanel()) { it } + fadeOut(MotionTokens.fadeQuick),
                ) {
                    EditorBottomToolbar(
                        selectedTool = selectedToolForUi,
                        onToolSelected = onToolClicked,
                        onReplaceImage = {
                            val target = activeLayer
                            if (target?.type == LayerType.IMAGE &&
                                target.product.isSample &&
                                !target.isLocked
                            ) {
                                targetReplaceLayerId = target.id
                                triggerImagePicker()
                            }
                        },
                        onAddImage = {
                            targetReplaceLayerId = null
                            triggerImagePicker()
                        },
                        onRemoveBg = {
                            val target = activeLayer
                            if (target?.type == LayerType.IMAGE && !target.isLocked) {
                                viewModel.onEvent(EditorEvent.RemoveBackground(target.id))
                            }
                        },
                        canReplaceImage = activeLayer?.type == LayerType.IMAGE &&
                            activeLayer.product.isSample &&
                            !activeLayer.isLocked,
                        canRemoveBg = activeLayer?.type == LayerType.IMAGE &&
                            !activeLayer.product.isSample &&
                            !activeLayer.isLocked,
                        toolsLocked = !editingToolsUnlocked,
                        labelLayerActive = selectedToolForUi is EditorTool.Label ||
                            activeLayer?.isLabelLayer == true,
                        shapeShadowInPanel = selectedToolForUi is EditorTool.Shape ||
                            activeLayer?.isFrameLayer == true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // ── Layer 4: Snackbar ─────────────────────────────────────
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
            )
        }

        if (showCustomPicker && onPickImage != null) {
            onPickImage(
                { uri ->
                    viewModel.onEvent(EditorEvent.SetProductImage(uri, targetReplaceLayerId))
                    showCustomPicker = false
                },
                {
                    showCustomPicker = false
                }
            )
        }
    }
}
