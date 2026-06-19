package com.thgiang.image.studio.ui.editor

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.thgiang.image.studio.R
import com.thgiang.image.studio.model.StudioThemeplate
import com.thgiang.image.studio.ui.editor.components.*
import com.thgiang.image.studio.ui.editor.theme.EditorTheme
import com.thgiang.image.studio.ui.editor.theme.LocalEditorTokens
import kotlinx.coroutines.delay

import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester

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
    val templateAssetPath = state.template.assetPath
    val canUndo by viewModel.canUndo.collectAsState()
    val canRedo by viewModel.canRedo.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showCustomPicker by remember { mutableStateOf(false) }

    var targetReplaceLayerId by remember { mutableStateOf<String?>(null) }
    
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
        val editingToolsUnlocked = state.layers.any {
            it.product.isBackgroundRemoved && !it.product.isSample && !it.product.processing
        }
        val selectedToolForUi = state.selectedTool.takeIf { tool ->
            editingToolsUnlocked || tool is EditorTool.Label || tool is EditorTool.Sticker
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(tokens.moduleBackground)
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null
                ) {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                }
        ) {

            // ── Layer 1: Canvas ───────────────────────────────────────
            if (state.template.loaded && state.template.originalSize.width > 0) {
                EditorCanvasV2(
                    templateAssetPath = state.template.assetPath,
                    templateBackgroundColor = Color(state.template.backgroundColorArgb),
                    templateSize = state.template.originalSize,
                    layers = state.layers,
                    selectedLayerId = state.selectedLayerId,
                    showOverlay = state.showOverlay,
                    viewportPadding = PaddingValues(
                        top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 76.dp,
                        bottom = if (state.layers.isNotEmpty()) 360.dp else 88.dp
                    ),
                    onGesture = { delta ->
                        focusManager.clearFocus()
                        keyboardController?.hide()
                        viewModel.onEvent(EditorEvent.UpdateGesture(delta))
                    },
                    onGestureEnd = { viewModel.onEvent(EditorEvent.CommitTransform) },
                    onPickImage = {
                        targetReplaceLayerId = state.selectedLayerId // Set ID of layer when tapping pink Replace button
                        triggerImagePicker()
                    },
                    onSelectLayer = { id ->
                        focusManager.clearFocus()
                        keyboardController?.hide()
                        viewModel.onEvent(EditorEvent.SelectLayer(id))
                    },
                    onShapeTextCommit = { text -> viewModel.onEvent(EditorEvent.UpdateShapeText(text)) },
                    modifier = Modifier.fillMaxSize()
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
            val activeLayer = state.layers.find { it.id == state.selectedLayerId }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                // ── Danh sách đối tượng ngang ────────────────────────
                EditorObjectList(
                    layers = state.layers,
                    selectedLayerId = state.selectedLayerId,
                    onSelectLayer = { id -> viewModel.onEvent(EditorEvent.SelectLayer(id)) },
                    modifier = Modifier.fillMaxWidth()
                )

                // Controls flow in the same bottom stack so it sits below the object strip.
                AnimatedVisibility(
                    visible = state.template.loaded &&
                        selectedToolForUi != null &&
                        (activeLayer != null || selectedToolForUi is EditorTool.Label || selectedToolForUi is EditorTool.Sticker),
                    modifier = Modifier
                        .fillMaxWidth(),
                    enter = slideInVertically(tween(250)) { it } + fadeIn(tween(200)),
                    exit  = slideOutVertically(tween(200)) { it } + fadeOut(tween(180))
                ) {
                    if (selectedToolForUi != null) {
                        EditorControlsV2(
                            tool = selectedToolForUi,
                            appearance = activeLayer?.appearance ?: EditorAppearance(shadowIntensity = 0f),
                            cropRatio = activeLayer?.cropRatio ?: CropRatio.ORIGINAL,
                            selectedLayer = activeLayer,
                            onUpdateShadow         = { viewModel.onEvent(EditorEvent.UpdateShadow(it)) },
                            onUpdateShadowAngle    = { viewModel.onEvent(EditorEvent.UpdateShadowAngle(it)) },
                            onUpdateShadowDistance = { viewModel.onEvent(EditorEvent.UpdateShadowDistance(it)) },
                            onUpdateShadowColor    = { viewModel.onEvent(EditorEvent.UpdateShadowColor(it)) },
                            onUpdateAlpha          = { viewModel.onEvent(EditorEvent.UpdateAlpha(it)) },
                            onSelectCropRatio      = { viewModel.onEvent(EditorEvent.SelectCropRatio(it)) },
                            onLayoutEvent          = { viewModel.onEvent(it) }
                        )
                    }
                }

                EditorBottomToolbar(
                    selectedTool = selectedToolForUi,
                    onToolSelected = { tool ->
                        if (tool is EditorTool.Duplicate) {
                            viewModel.onEvent(EditorEvent.DuplicateLayer)
                        } else if (tool is EditorTool.Delete) {
                            viewModel.onEvent(EditorEvent.DeleteLayer)
                        } else if (tool != null) {
                            viewModel.onEvent(EditorEvent.SelectTool(tool))
                        }
                    },
                    onReplaceImage = {
                        targetReplaceLayerId = state.selectedLayerId
                        triggerImagePicker()
                    },
                    toolsLocked = !editingToolsUnlocked,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding()
                )
            }

            // ── Floating Text Input Layer (only shown above keyboard when editing text) ──
            val isImeVisible = WindowInsets.isImeVisible
            if (isImeVisible && selectedToolForUi is EditorTool.Label && activeLayer != null) {
                var floatingTextDraft by remember(activeLayer.id) { mutableStateOf(activeLayer.text) }
                
                LaunchedEffect(activeLayer.text) {
                    floatingTextDraft = activeLayer.text
                }

                val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }

                LaunchedEffect(activeLayer.id) {
                    focusRequester.requestFocus()
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .imePadding()
                        .background(Color.White)
                        .drawBehind {
                            drawRect(
                                color = tokens.borderSubtle,
                                topLeft = Offset(0f, 0f),
                                size = Size(size.width, 1.dp.toPx())
                            )
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    OutlinedTextField(
                        value = floatingTextDraft,
                        onValueChange = {
                            floatingTextDraft = it
                            viewModel.onEvent(EditorEvent.UpdateShapeText(it))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .focusRequester(focusRequester),
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.studio_label_text_placeholder)) },
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                            onDone = {
                                viewModel.onEvent(EditorEvent.UpdateShapeText(floatingTextDraft))
                                focusManager.clearFocus()
                                keyboardController?.hide()
                            }
                        )
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
