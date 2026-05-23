package com.thgiang.image.studio.ui.editor

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
                title = { Text(stringResource(themeplate.titleResId)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.studio_back)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.onEvent(EditorEvent.Undo) },
                        enabled = canUndo
                    ) {
                        Icon(
                            imageVector = Icons.Default.Undo,
                            contentDescription = "Undo",
                            tint = if (canUndo) LocalContentColor.current
                                   else LocalContentColor.current.copy(alpha = 0.38f)
                        )
                    }
                    IconButton(
                        onClick = { viewModel.onEvent(EditorEvent.Redo) },
                        enabled = canRedo
                    ) {
                        Icon(
                            imageVector = Icons.Default.Redo,
                            contentDescription = "Redo",
                            tint = if (canRedo) LocalContentColor.current
                                   else LocalContentColor.current.copy(alpha = 0.38f)
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
                            Icon(
                                Icons.Default.Done,
                                contentDescription = "Done",
                                tint = if (state.canExport) 
                                    MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
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
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        ConstraintLayout(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(if (isSystemInDarkTheme()) Color(0xFF1A1A1A) else Color(0xFFF0EDE8))
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
