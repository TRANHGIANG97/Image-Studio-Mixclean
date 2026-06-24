package com.thgiang.image.admin.ui
import com.thgiang.image.studio.ui.editor.model.*
import com.thgiang.image.studio.ui.editor.panel.*
import com.thgiang.image.studio.ui.editor.*
import com.thgiang.image.studio.ui.editor.canvas.*

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.thgiang.image.admin.R as AdminR
import com.thgiang.image.admin.ui.components.AdminBackIcon
import com.thgiang.image.admin.ui.components.AdminLottieLoader
import com.thgiang.image.admin.ui.components.AdminRedoIcon
import com.thgiang.image.admin.ui.components.AdminUndoIcon
import com.thgiang.image.admin.ui.theme.AdminTokens
import com.thgiang.image.admin.viewmodel.TemplateBuilderViewModel
import com.thgiang.image.core.domain.model.template.CloudTemplate
import com.thgiang.image.studio.R
import com.thgiang.image.studio.model.StudioThemeplate
import com.thgiang.image.studio.ui.editor.EditorEvent
import com.thgiang.image.studio.ui.editor.theme.EditorTheme
import com.thgiang.image.studio.ui.editor.theme.LocalEditorTokens

@Composable
fun TemplateBuilderScreen(
    themeplate: StudioThemeplate,
    initialCloudTemplate: CloudTemplate? = null,
    onBack: () -> Unit,
    onDone: (Uri?) -> Unit = {},
    onRequireExportAd: ((() -> Unit) -> Unit)? = null,
    onPickImage: (@Composable (onImageSelected: (Uri) -> Unit, onCancel: () -> Unit) -> Unit)? = null,
    viewModel: TemplateBuilderViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val availableCategories by viewModel.availableCategories.collectAsState()
    val templateAssetPath = state.template.assetPath.ifEmpty { themeplate.backgroundAssetPath ?: themeplate.assetPath }
    val canUndo by viewModel.canUndo.collectAsState()
    val canRedo by viewModel.canRedo.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showCustomPicker by remember { mutableStateOf(false) }
    var targetReplaceLayerId by remember { mutableStateOf<String?>(null) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var inputTitle by remember { mutableStateOf("Mẫu mới") }
    var inputCategoryId by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var initialLayerCount by remember { mutableStateOf(0) }
    var hasUnsavedChanges by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.layers.size) {
        if (initialLayerCount > 0 && state.layers.size != initialLayerCount) {
            hasUnsavedChanges = state.layers.isNotEmpty()
        }
    }

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.onEvent(EditorEvent.SetProductImage(it, targetReplaceLayerId)) }
    }

    val pickBackgroundLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.setCustomBackground(it) }
    }

    val addSampleObjectLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.addSampleObject(it) }
    }

    val addDecorationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.addDecoration(it) }
    }

    val triggerImagePicker = {
        if (onPickImage != null) {
            showCustomPicker = true
        } else {
            pickImageLauncher.launch("image/*")
        }
    }

    val exportSuccessMessage = stringResource(AdminR.string.builder_export_success)

    LaunchedEffect(state.exportResult) {
        state.exportResult?.let { uri ->
            snackbarHostState.showSnackbar(exportSuccessMessage)
            onDone(uri)
        }
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Long)
            isSaving = false
        }
    }

    LaunchedEffect(state.exportResult) {
        if (state.exportResult != null) {
            isSaving = false
        }
    }

    LaunchedEffect(themeplate, initialCloudTemplate) {
        if (initialCloudTemplate != null) {
            viewModel.onEvent(EditorEvent.LoadCloudTemplate(initialCloudTemplate))
        } else {
            viewModel.onEvent(EditorEvent.LoadTemplate(templateAssetPath, themeplate.objectSourceAssetPath))
        }
    }

    LaunchedEffect(state.template.loaded) {
        if (state.template.loaded && initialLayerCount == 0) {
            initialLayerCount = state.layers.size
            hasUnsavedChanges = false
        }
    }

    val handleBack: () -> Unit = {
        if (hasUnsavedChanges) {
            showExitDialog = true
        } else {
            onBack()
        }
    }

    EditorTheme {
        val tokens = LocalEditorTokens.current
        val currentLayer = state.layers.find { it.id == state.selectedLayerId }
        val selectedLayerIndex = state.layers.indexOfFirst { it.id == state.selectedLayerId }
        val zoomPercent = ((currentLayer?.viewport?.scale ?: 1f) * 100).toInt()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(tokens.moduleBackground)
        ) {
            if (state.template.loaded && state.template.originalSize.width > 0) {
                EditorCanvasV2(
                    templateAssetPath = templateAssetPath,
                    templateSize = state.template.originalSize,
                    layers = state.layers,
                    selectedLayerId = state.selectedLayerId,
                    showOverlay = state.showOverlay,
                    viewportPadding = PaddingValues(
                        top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + AdminTokens.builderCanvasTopInset,
                        bottom = if (state.selectedTool != null || state.layers.any { it.product.isBackgroundRemoved }) {
                            AdminTokens.builderCanvasBottomInsetExpanded
                        } else {
                            AdminTokens.builderCanvasBottomInsetCompact
                        }
                    ),
                    onGesture = { delta -> viewModel.onEvent(EditorEvent.UpdateGesture(delta)) },
                    onGestureEnd = { viewModel.onEvent(EditorEvent.CommitTransform) },
                    onPickImage = {
                        targetReplaceLayerId = state.selectedLayerId
                        triggerImagePicker()
                    },
                    onSelectLayer = { id -> viewModel.onEvent(EditorEvent.SelectLayer(id)) },
                    modifier = Modifier.fillMaxSize()
                )
            }

            BuilderTopBar(
                zoomPercent = zoomPercent,
                onBack = handleBack,
                canUndo = canUndo,
                canRedo = canRedo,
                isExporting = state.isExporting,
                canExport = state.canExport,
                onUndo = { viewModel.onEvent(EditorEvent.Undo) },
                onRedo = { viewModel.onEvent(EditorEvent.Redo) },
                onSaveClick = { showSaveDialog = true },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = AdminTokens.builderOuterPadding, vertical = AdminTokens.builderOuterPadding)
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = AdminTokens.builderOuterPadding, vertical = AdminTokens.builderOuterPadding),
                verticalArrangement = Arrangement.spacedBy(AdminTokens.builderDockSpacing),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = RoundedCornerShape(AdminTokens.builderDockRadius),
                    color = tokens.glassBackground,
                    tonalElevation = 0.dp,
                    shadowElevation = 14.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, tokens.borderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AdminTokens.builderDockInnerPadding, vertical = AdminTokens.builderDockInnerPadding),
                        verticalArrangement = Arrangement.spacedBy(AdminTokens.builderSectionGap)
                    ) {
                        AdminQuickActionRow(
                            onSelectBackground = { pickBackgroundLauncher.launch("image/*") },
                            onAddObject = { addSampleObjectLauncher.launch("image/*") },
                            onAddDecoration = { addDecorationLauncher.launch("image/*") }
                        )

                        if (state.selectedLayerId != null) {
                            SelectedLayerHeader(
                                layer = currentLayer,
                                layerIndex = selectedLayerIndex,
                                layersCount = state.layers.size,
                                onMoveUp = { viewModel.onEvent(EditorEvent.MoveLayerUp) },
                                onMoveDown = { viewModel.onEvent(EditorEvent.MoveLayerDown) },
                                onToggleLock = {
                                    currentLayer?.let {
                                        viewModel.setLayerLocked(it.id, !it.isLocked)
                                    }
                                },
                                onResetTransform = {
                                    currentLayer?.let {
                                        viewModel.resetTransform(it.id)
                                    }
                                }
                            )
                        }

                        AnimatedVisibility(
                            visible = state.template.loaded && state.selectedTool != null && currentLayer != null,
                            enter = slideInVertically { it } + fadeIn(tween(200)),
                            exit = slideOutVertically { it } + fadeOut(tween(180))
                        ) {
                            EditorControlsV2(
                                tool = state.selectedTool,
                                appearance = currentLayer!!.appearance,
                                cropRatio = currentLayer.cropRatio,
                                onUpdateShadow = { viewModel.onEvent(EditorEvent.UpdateShadow(it)) },
                                onUpdateShadowAngle = { viewModel.onEvent(EditorEvent.UpdateShadowAngle(it)) },
                                onUpdateShadowDistance = { viewModel.onEvent(EditorEvent.UpdateShadowDistance(it)) },
                                onUpdateShadowColor = { viewModel.onEvent(EditorEvent.UpdateShadowColor(it)) },
                                onUpdateAlpha = { viewModel.onEvent(EditorEvent.UpdateAlpha(it)) },
                                onSelectCropRatio = { viewModel.onEvent(EditorEvent.SelectCropRatio(it)) },
                                onLayoutEvent = { viewModel.onEvent(it) }
                            )
                        }

                        EditorBottomToolbar(
                            selectedTool = state.selectedTool,
                            onToolSelected = { tool ->
                                when (tool) {
                                    is EditorTool.Duplicate -> viewModel.onEvent(EditorEvent.DuplicateLayer)
                                    is EditorTool.Delete -> viewModel.onEvent(EditorEvent.DeleteLayer)
                                    null -> Unit
                                    else -> viewModel.onEvent(EditorEvent.SelectTool(tool))
                                }
                            },
                            onReplaceImage = {
                                targetReplaceLayerId = state.selectedLayerId
                                triggerImagePicker()
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
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

        if (showExitDialog) {
            AlertDialog(
                onDismissRequest = { showExitDialog = false },
                title = { Text(stringResource(AdminR.string.builder_exit_dialog_title)) },
                text = { Text(stringResource(AdminR.string.builder_exit_dialog_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        showExitDialog = false
                        hasUnsavedChanges = false
                        onBack()
                    }) {
                        Text(stringResource(AdminR.string.builder_exit_confirm), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showExitDialog = false }) {
                        Text(stringResource(AdminR.string.builder_stay))
                    }
                }
            )
        }

        if (showSaveDialog) {
            AlertDialog(
                onDismissRequest = { showSaveDialog = false },
                title = { Text(stringResource(AdminR.string.builder_save_dialog_title)) },
                text = {
                    Column {
                        OutlinedTextField(
                            value = inputTitle,
                            onValueChange = { inputTitle = it },
                            label = { Text(stringResource(AdminR.string.builder_save_dialog_title_label)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        var expanded by remember { mutableStateOf(false) }
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = inputCategoryId,
                                onValueChange = { 
                                    inputCategoryId = it
                                    expanded = true
                                },
                                label = { Text(stringResource(AdminR.string.builder_save_dialog_category_label)) },
                                modifier = Modifier.fillMaxWidth(),
                                trailingIcon = {
                                    IconButton(onClick = { expanded = !expanded }) {
                                        Icon(
                                            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                            contentDescription = "Chọn danh mục"
                                        )
                                    }
                                }
                            )
                            if (availableCategories.isNotEmpty()) {
                                DropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false },
                                    modifier = Modifier.fillMaxWidth(0.9f)
                                ) {
                                    availableCategories.forEach { category ->
                                        DropdownMenuItem(
                                            text = { Text(category) },
                                            onClick = {
                                                inputCategoryId = category
                                                expanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (!isSaving) {
                                isSaving = true
                                viewModel.exportToBundle(inputTitle, inputCategoryId)
                                showSaveDialog = false
                                hasUnsavedChanges = false
                            }
                        },
                        enabled = !isSaving
                    ) {
                        Text(if (isSaving) stringResource(AdminR.string.builder_saving) else stringResource(AdminR.string.builder_save_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSaveDialog = false }) {
                        Text(stringResource(AdminR.string.admin_cancel))
                    }
                }
            )
        }
    }
}

@Composable
private fun BuilderTopBar(
    zoomPercent: Int,
    onBack: () -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    isExporting: Boolean,
    canExport: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSaveClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalEditorTokens.current
    Surface(
        shape = RoundedCornerShape(AdminTokens.builderTopBarRadius),
        color = tokens.glassBackground,
        shadowElevation = 12.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, tokens.borderSubtle),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AdminTokens.builderTopBarHorizontalPadding, vertical = AdminTokens.builderTopBarVerticalPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                AdminBackIcon(
                    modifier = Modifier.size(22.dp),
                    tint = tokens.textPrimary
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(AdminR.string.builder_topbar_title),
                    color = tokens.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = AdminTokens.builderTopBarTitleFontSize,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.studio_zoom_label, zoomPercent),
                    color = tokens.textSecondary,
                    fontSize = AdminTokens.builderTopBarSubtitleFontSize,
                    maxLines = 1
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onUndo,
                    enabled = canUndo
                ) {
                    AdminUndoIcon(
                        modifier = Modifier.size(20.dp),
                        tint = if (canUndo) tokens.textPrimary.copy(alpha = 0.65f) else tokens.textDisabled.copy(alpha = 0.32f)
                    )
                }
                IconButton(
                    onClick = onRedo,
                    enabled = canRedo
                ) {
                    AdminRedoIcon(
                        modifier = Modifier.size(20.dp),
                        tint = if (canRedo) tokens.textPrimary.copy(alpha = 0.65f) else tokens.textDisabled.copy(alpha = 0.32f)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                if (isExporting) {
                    Box(
                        modifier = Modifier
                            .widthIn(min = 88.dp)
                            .height(AdminTokens.builderSaveButtonHeight),
                        contentAlignment = Alignment.Center
                    ) {
                        AdminLottieLoader(modifier = Modifier.size(36.dp))
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (canExport) tokens.accent else tokens.textDisabled.copy(alpha = 0.20f),
                        contentColor = if (canExport) Color.White else tokens.textDisabled,
                        modifier = Modifier.clickable(enabled = canExport) { onSaveClick() }
                    ) {
                        Text(
                            text = stringResource(AdminR.string.builder_save_bundle),
                            color = if (canExport) Color.White else tokens.textDisabled,
                            fontSize = AdminTokens.builderSaveButtonFontSize,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = AdminTokens.builderSaveButtonHorizontalPadding, vertical = 10.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminQuickActionRow(
    onSelectBackground: () -> Unit,
    onAddObject: () -> Unit,
    onAddDecoration: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BuilderActionChip(
            icon = Icons.Default.Image,
            label = stringResource(AdminR.string.admin_tool_select_background),
            onClick = onSelectBackground,
            modifier = Modifier.weight(1f)
        )
        BuilderActionChip(
            icon = Icons.Default.AddPhotoAlternate,
            label = stringResource(AdminR.string.admin_tool_add_sample_object),
            onClick = onAddObject,
            modifier = Modifier.weight(1f)
        )
        BuilderActionChip(
            icon = Icons.Default.AutoAwesome,
            label = stringResource(AdminR.string.admin_tool_add_decoration),
            onClick = onAddDecoration,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun BuilderActionChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalEditorTokens.current
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = tokens.surfaceFloating,
        tonalElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, tokens.borderSubtle),
        modifier = modifier
            .height(AdminTokens.builderActionChipHeight)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tokens.textPrimary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = tokens.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SelectedLayerHeader(
    layer: com.thgiang.image.studio.ui.editor.model.EditorLayer?,
    layerIndex: Int,
    layersCount: Int,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onToggleLock: () -> Unit,
    onResetTransform: () -> Unit
) {
    val tokens = LocalEditorTokens.current
    val safeIndex = layerIndex.coerceAtLeast(0)
    val label = when {
        layer == null -> stringResource(AdminR.string.builder_layer_object, safeIndex + 1)
        layer.product.isSample -> stringResource(AdminR.string.builder_layer_object, safeIndex + 1)
        else -> stringResource(AdminR.string.builder_layer_decoration, safeIndex + 1)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = tokens.accent,
            contentColor = Color.White,
            modifier = Modifier.height(AdminTokens.builderLayerChipHeight)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = label,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(999.dp),
            color = tokens.surfaceFloating,
            border = androidx.compose.foundation.BorderStroke(1.dp, tokens.borderSubtle)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                IconButton(onClick = onMoveUp, enabled = layerIndex < layersCount - 1) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = stringResource(AdminR.string.admin_tool_layer_up),
                        tint = if (layerIndex < layersCount - 1) tokens.textPrimary else tokens.textDisabled.copy(alpha = 0.32f)
                    )
                }
                Text(
                    text = if (layerIndex >= 0) "${layerIndex + 1}/$layersCount" else "-/-",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = tokens.textPrimary,
                    modifier = Modifier.widthIn(min = 32.dp),
                    maxLines = 1
                )
                IconButton(onClick = onMoveDown, enabled = layerIndex > 0) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = stringResource(AdminR.string.admin_tool_layer_down),
                        tint = if (layerIndex > 0) tokens.textPrimary else tokens.textDisabled.copy(alpha = 0.32f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Surface(
            shape = RoundedCornerShape(999.dp),
            color = tokens.surfaceFloating,
            border = androidx.compose.foundation.BorderStroke(1.dp, tokens.borderSubtle)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                val isLocked = layer?.isLocked == true
                IconButton(onClick = onToggleLock) {
                    Icon(
                        imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = if (isLocked) "Unlock" else "Lock",
                        tint = if (isLocked) MaterialTheme.colorScheme.error else tokens.textPrimary
                    )
                }
                IconButton(onClick = onResetTransform, enabled = layer != null) {
                    Icon(
                        imageVector = Icons.Default.RestartAlt,
                        contentDescription = "Reset Transform",
                        tint = if (layer != null) tokens.textPrimary else tokens.textDisabled.copy(alpha = 0.32f)
                    )
                }
            }
        }
    }
}
