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
import androidx.hilt.navigation.compose.hiltViewModel
import com.thgiang.image.studio.R
import com.thgiang.image.studio.model.StudioThemeplate
import com.thgiang.image.studio.ui.editor.components.*
import com.thgiang.image.studio.ui.editor.theme.EditorTheme
import com.thgiang.image.studio.ui.editor.theme.LocalEditorTokens
import kotlinx.coroutines.delay

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
    val templateAssetPath = themeplate.backgroundAssetPath ?: themeplate.assetPath
    val state by viewModel.state.collectAsState()
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

    LaunchedEffect(themeplate) {
        viewModel.onEvent(EditorEvent.LoadTemplate(templateAssetPath, themeplate.objectSourceAssetPath))
    }




    EditorTheme {
        val tokens = LocalEditorTokens.current
        val editingToolsUnlocked = state.layers.any {
            it.product.isBackgroundRemoved && !it.product.isSample && !it.product.processing
        }
        val selectedToolForUi = state.selectedTool.takeIf { editingToolsUnlocked }

        Box(modifier = Modifier.fillMaxSize().background(tokens.moduleBackground)) {

            // ── Layer 1: Canvas ───────────────────────────────────────
            if (state.template.loaded && state.template.originalSize.width > 0) {
                EditorCanvasV2(
                    templateAssetPath = templateAssetPath,
                    templateSize = state.template.originalSize,
                    layers = state.layers,
                    selectedLayerId = state.selectedLayerId,
                    showOverlay = state.showOverlay,
                    viewportPadding = PaddingValues(
                        top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 76.dp,
                        bottom = if (state.layers.isNotEmpty()) 384.dp else 88.dp
                    ),
                    onGesture = { delta -> viewModel.onEvent(EditorEvent.UpdateGesture(delta)) },
                    onGestureEnd = { viewModel.onEvent(EditorEvent.CommitTransform) },
                    onPickImage = {
                        targetReplaceLayerId = state.selectedLayerId // Set ID of layer when tapping pink Replace button
                        triggerImagePicker()
                    },
                    onSelectLayer = { id -> viewModel.onEvent(EditorEvent.SelectLayer(id)) },
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
                    .background(tokens.glassBackground)
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
                    // Back + Title
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(onClick = onBack) {
                            EditorBackIcon(
                                modifier = Modifier.size(22.dp),
                                tint = tokens.textPrimary
                            )
                        }
                        val scaleToDisplay = state.layers.find { it.id == state.selectedLayerId }?.viewport?.scale ?: 1f
                        Text(
                            text = stringResource(
                                R.string.studio_zoom_label,
                                (scaleToDisplay * 100).toInt()
                            ),
                            color = tokens.textPrimary,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.padding(start = 6.dp, end = 4.dp)
                        )
                    }

                    // Undo / Redo / Export
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        IconButton(
                            onClick = { viewModel.onEvent(EditorEvent.Undo) },
                            enabled = canUndo
                        ) {
                            EditorUndoIcon(
                                modifier = Modifier.size(20.dp),
                                tint = if (canUndo) tokens.textPrimary.copy(alpha = 0.65f)
                                       else tokens.textDisabled.copy(alpha = 0.32f)
                            )
                        }
                        IconButton(
                            onClick = { viewModel.onEvent(EditorEvent.Redo) },
                            enabled = canRedo
                        ) {
                            EditorRedoIcon(
                                modifier = Modifier.size(20.dp),
                                tint = if (canRedo) tokens.textPrimary.copy(alpha = 0.65f)
                                       else tokens.textDisabled.copy(alpha = 0.32f)
                            )
                        }

                        Spacer(Modifier.width(4.dp))

                        // Export — accent pill button
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
                            Box(
                                modifier = Modifier
                                    .height(36.dp)
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
                                    .padding(horizontal = 18.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.studio_export),
                                    color = if (state.canExport) Color.White else tokens.textDisabled,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                    }
                }
            }

            // ── Layer 3: Bottom controls + toolbar ────────────────────
            Column(
                modifier = Modifier.align(Alignment.BottomCenter),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val activeLayer = state.layers.find { it.id == state.selectedLayerId }
                if (state.template.loaded && state.layers.any { it.product.isBackgroundRemoved } && state.layers.none { it.product.processing }) {
                    Box(
                        modifier = Modifier
                            .padding(bottom = 12.dp)
                            .background(
                                color = Color.Black.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(99.dp)
                            )
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.studio_layout_hint),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                AnimatedVisibility(
                    visible = state.template.loaded && selectedToolForUi != null && activeLayer != null,
                    enter = slideInVertically { it } + fadeIn(tween(200)),
                    exit  = slideOutVertically { it } + fadeOut(tween(180))
                ) {
                    if (activeLayer != null) {
                        EditorControlsV2(
                            tool = selectedToolForUi,
                            appearance = activeLayer.appearance,
                            cropRatio = activeLayer.cropRatio,
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

                // ── Danh sách đối tượng ngang ────────────────────────
                EditorObjectList(
                    layers = state.layers,
                    selectedLayerId = state.selectedLayerId,
                    onSelectLayer = { id -> viewModel.onEvent(EditorEvent.SelectLayer(id)) },
                    modifier = Modifier.fillMaxWidth()
                )

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
