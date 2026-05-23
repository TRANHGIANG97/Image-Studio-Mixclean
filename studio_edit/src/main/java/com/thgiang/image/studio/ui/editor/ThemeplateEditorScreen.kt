package com.thgiang.image.studio.ui.editor

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.hilt.navigation.compose.hiltViewModel
import com.thgiang.image.studio.R
import com.thgiang.image.studio.model.StudioThemeplate
import com.thgiang.image.studio.ui.editor.components.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeplateEditorScreen(
    themeplate: StudioThemeplate,
    onBack: () -> Unit,
    onDone: (Uri?) -> Unit = {},
    onRequireExportAd: ((() -> Unit) -> Unit)? = null,
    viewModel: ThemeplateEditorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()
    val canRedo by viewModel.canRedo.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Pick image from gallery contract
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.onEvent(EditorEvent.SetProductImage(it))
        }
    }

    // Export result handling
    LaunchedEffect(state.exportResult) {
        state.exportResult?.let { uri ->
            snackbarHostState.showSnackbar("Đã lưu ảnh")
            onDone(uri)
        }
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Long)
        }
    }

    // Initial template load
    LaunchedEffect(themeplate) {
        viewModel.onEvent(EditorEvent.LoadTemplate(themeplate.assetPath))
    }

    // Auto trigger picker if product empty
    var pickerTriggered by remember { mutableStateOf(false) }
    LaunchedEffect(state.template.loaded) {
        if (state.template.loaded && !state.product.isBackgroundRemoved && !state.product.processing && !pickerTriggered) {
            pickerTriggered = true
            delay(300)
            pickImageLauncher.launch("image/*")
        }
    }

    val onToolSelected = remember(state.selectedTool) {
        { tool: EditorTool ->
            viewModel.onEvent(EditorEvent.SelectTool(tool))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.height(118.dp),
                title = {
                    Text(
                        text = stringResource(themeplate.titleResId).replace(" ", "\n"),
                        color = Color.White,
                        fontSize = 31.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 31.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        EditorBackIcon(
                            modifier = Modifier.size(26.dp),
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.onEvent(EditorEvent.Undo) },
                        enabled = canUndo
                    ) {
                        EditorUndoIcon(
                            modifier = Modifier.size(24.dp),
                            tint = if (canUndo) Color.White.copy(alpha = 0.52f) else Color.White.copy(alpha = 0.28f)
                        )
                    }
                    IconButton(
                        onClick = { viewModel.onEvent(EditorEvent.Redo) },
                        enabled = canRedo
                    ) {
                        EditorRedoIcon(
                            modifier = Modifier.size(24.dp),
                            tint = if (canRedo) Color.White.copy(alpha = 0.52f) else Color.White.copy(alpha = 0.28f)
                        )
                    }
                    
                    if (state.isExporting) {
                        StudioLottieLoader(
                            modifier = Modifier
                                .size(48.dp)
                                .padding(end = 8.dp)
                        )
                    } else {
                        IconButton(
                            onClick = {
                                val exportAction = {
                                    viewModel.onEvent(EditorEvent.Export(themeplate.assetPath))
                                }
                                onRequireExportAd?.invoke(exportAction) ?: exportAction()
                            },
                            enabled = state.canExport
                        ) {
                            EditorCheckIcon(
                                modifier = Modifier.size(26.dp),
                                tint = if (state.canExport) Color.White else Color.White.copy(alpha = 0.28f)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xF20A0D0E)
                )
            )
        },
        bottomBar = {
            EditorBottomToolbar(
                selectedTool = state.selectedTool,
                onToolSelected = onToolSelected,
                onReplaceImage = {
                    pickImageLauncher.launch("image/*")
                },
                modifier = Modifier.fillMaxWidth().navigationBarsPadding()
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color(0xFF060A0B)
    ) { paddingValues ->
            ConstraintLayout(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(Color(0xFF060A0B))
            ) {
            val (canvasRef, controlsRef) = createRefs()

            if (state.template.loaded && state.template.originalSize.width > 0) {
                // Workspace Canvas area
                EditorCanvasV2(
                    templateAssetPath = themeplate.assetPath,
                    templateSize = state.template.originalSize,
                    product = state.product,
                    viewport = state.viewport,
                    appearance = state.appearance,
                    showBoundingBox = state.showBoundingBox,
                    showOverlay = state.showOverlay,
                    cropRatio = state.cropRatio,
                    onGesture = { delta ->
                        viewModel.onEvent(EditorEvent.UpdateGesture(delta))
                    },
                    onGestureEnd = {
                        viewModel.onEvent(EditorEvent.CommitTransform)
                    },
                    onPickImage = {
                        pickImageLauncher.launch("image/*")
                    },
                    onBoundingBoxVisible = { visible ->
                        viewModel.onEvent(EditorEvent.SetBoundingBoxVisible(visible))
                    },
                    modifier = Modifier.constrainAs(canvasRef) {
                        top.linkTo(parent.top)
                        start.linkTo(parent.start)
                        end.linkTo(parent.end)
                        bottom.linkTo(if (state.product.isBackgroundRemoved) controlsRef.top else parent.bottom)
                        width = Dimension.fillToConstraints
                        height = Dimension.fillToConstraints
                    }
                )
            }

            // Tool context bottom control panel with slide + fade animation
            AnimatedVisibility(
                visible = state.product.isBackgroundRemoved,
                enter = slideInVertically { it } + fadeIn(tween(250)),
                exit = slideOutVertically { it } + fadeOut(tween(200)),
                modifier = Modifier.constrainAs(controlsRef) {
                    bottom.linkTo(parent.bottom)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                    width = Dimension.fillToConstraints
                }
            ) {
                EditorControlsV2(
                    tool = state.selectedTool,
                    appearance = state.appearance,
                    cropRatio = state.cropRatio,
                    onUpdateShadow = { viewModel.onEvent(EditorEvent.UpdateShadow(it)) },
                    onUpdateShadowAngle = { viewModel.onEvent(EditorEvent.UpdateShadowAngle(it)) },
                    onUpdateShadowDistance = { viewModel.onEvent(EditorEvent.UpdateShadowDistance(it)) },
                    onUpdateShadowColor = { viewModel.onEvent(EditorEvent.UpdateShadowColor(it)) },
                    onUpdateAlpha = { viewModel.onEvent(EditorEvent.UpdateAlpha(it)) },
                    onSelectCropRatio = { viewModel.onEvent(EditorEvent.SelectCropRatio(it)) },
                    onLayoutEvent = { viewModel.onEvent(it) }
                )
            }
        }
    }
}
