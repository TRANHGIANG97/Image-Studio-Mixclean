package com.thgiang.image.feature.drafts.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.thgiang.image.core.design.components.BackgroundRemovalLoadingOverlay
import com.thgiang.image.feature.drafts.viewmodel.DraftsViewModel
import com.thgiang.image.feature.editor.model.DraftMetadata
import com.thgiang.image.studio.util.toAssetModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DraftsScreen(
    onBack: () -> Unit,
    onSelectDraft: (DraftMetadata) -> Unit,
    viewModel: DraftsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var draftToRename by remember { mutableStateOf<DraftMetadata?>(null) }
    var draftToDelete by remember { mutableStateOf<DraftMetadata?>(null) }
    var showDeleteSelectedDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            if (uiState.isSelectionMode) {
                // Selection mode top bar
                TopAppBar(
                    title = {
                        Text(
                            stringResource(com.thgiang.image.R.string.draft_selected_count, uiState.selectedDraftIds.size),
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.exitSelectionMode() }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Cancel")
                        }
                    },
                    actions = {
                        TextButton(onClick = {
                            if (viewModel.isAllSelected) viewModel.deselectAll()
                            else viewModel.selectAll()
                        }) {
                            Text(
                                if (viewModel.isAllSelected) stringResource(com.thgiang.image.R.string.draft_deselect_all)
                                else stringResource(com.thgiang.image.R.string.draft_select_all)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            } else {
                // Normal top bar
                TopAppBar(
                    title = { Text(stringResource(com.thgiang.image.R.string.home_draft), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        if (uiState.drafts.isNotEmpty()) {
                            TextButton(onClick = { viewModel.enterSelectionMode() }) {
                                Text(stringResource(com.thgiang.image.R.string.select))
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        },
        bottomBar = {
            if (uiState.isSelectionMode && uiState.selectedDraftIds.isNotEmpty()) {
                Surface(
                    modifier = Modifier.navigationBarsPadding(),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(com.thgiang.image.R.string.draft_selected_count, uiState.selectedDraftIds.size),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Button(
                            onClick = { showDeleteSelectedDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = "Delete",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(com.thgiang.image.R.string.multi_delete))
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (uiState.drafts.isEmpty() && !uiState.isLoading) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        stringResource(com.thgiang.image.R.string.draft_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(uiState.drafts, key = { it.id }) { draft ->
                        val isSelected = draft.id in uiState.selectedDraftIds
                        DraftItem(
                            draft = draft,
                            isSelected = isSelected,
                            isSelectionMode = uiState.isSelectionMode,
                            onClick = {
                                if (uiState.isSelectionMode) {
                                    viewModel.toggleSelection(draft.id)
                                } else {
                                    onSelectDraft(draft)
                                }
                            },
                            onLongClick = {
                                if (!uiState.isSelectionMode) {
                                    viewModel.toggleSelection(draft.id)
                                }
                            },
                            onRename = { draftToRename = draft },
                            onDelete = { draftToDelete = draft },
                            onDuplicate = { viewModel.duplicateDraft(draft.id) }
                        )
                    }
                }
            }

            if (uiState.isLoading) {
                BackgroundRemovalLoadingOverlay(
                    modifier = Modifier.fillMaxSize(),
                    message = stringResource(com.thgiang.image.R.string.loading_image)
                )
            }
        }
    }

    // Rename Dialog
    draftToRename?.let { draft ->
        var newName by remember { mutableStateOf(draft.name) }
        AlertDialog(
            onDismissRequest = { draftToRename = null },
            title = { Text(stringResource(com.thgiang.image.R.string.draft_rename_title)) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.renameDraft(draft.id, newName)
                    draftToRename = null
                }) {
                    Text(stringResource(com.thgiang.image.R.string.multi_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { draftToRename = null }) {
                    Text(stringResource(com.thgiang.image.R.string.multi_cancel))
                }
            }
        )
    }

    // Delete single draft confirmation
    draftToDelete?.let { draft ->
        AlertDialog(
            onDismissRequest = { draftToDelete = null },
            title = { Text(stringResource(com.thgiang.image.R.string.draft_delete_title)) },
            text = { Text(stringResource(com.thgiang.image.R.string.draft_confirm_delete_single, draft.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteDraft(draft.id)
                        draftToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(com.thgiang.image.R.string.multi_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { draftToDelete = null }) {
                    Text(stringResource(com.thgiang.image.R.string.multi_cancel))
                }
            }
        )
    }

    // Delete multiple drafts confirmation
    if (showDeleteSelectedDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteSelectedDialog = false },
            title = { Text(stringResource(com.thgiang.image.R.string.draft_delete_title)) },
            text = {
                Text(
                    stringResource(com.thgiang.image.R.string.draft_confirm_delete_multiple, uiState.selectedDraftIds.size)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSelected()
                        showDeleteSelectedDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(com.thgiang.image.R.string.multi_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSelectedDialog = false }) {
                    Text(stringResource(com.thgiang.image.R.string.multi_cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DraftItem(
    draft: DraftMetadata,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val draftId = draft.id ?: ""
    val draftName = draft.name ?: "Untitled"
    val thumbnailModel = remember(draft) { resolveDraftThumbnailModel(context, draft) }

    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.8f)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            width = if (isSelected) 2.dp else 0.5.dp,
            brush = androidx.compose.ui.graphics.SolidColor(borderColor)
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 2.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .weight(1.3f)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.05f))
            ) {
                AsyncImage(
                    model = thumbnailModel,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(2.dp),
                    contentScale = ContentScale.Fit
                )

                if (isSelectionMode) {
                    // Selection checkbox overlay
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(2.dp)
                    ) {
                        Icon(
                            imageVector = if (isSelected) Icons.Rounded.CheckCircle
                            else Icons.Rounded.RadioButtonUnchecked,
                            contentDescription = if (isSelected) "Selected" else "Not selected",
                            tint = if (isSelected) MaterialTheme.colorScheme.primary
                            else Color.White.copy(alpha = 0.7f),
                            modifier = Modifier
                                .size(24.dp)
                                .background(
                                    Color.Black.copy(alpha = 0.3f),
                                    CircleShape
                                )
                                .clip(CircleShape)
                        )
                    }
                } else {
                    // Menu button (only when not in selection mode)
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(2.dp)
                    ) {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Rounded.MoreVert,
                                contentDescription = "Options",
                                tint = Color.White,
                                modifier = Modifier.background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            )
                        }

                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                            modifier = Modifier.widthIn(min = 184.dp, max = 320.dp)
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(com.thgiang.image.R.string.draft_rename), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                                onClick = { menuExpanded = false; onRename() }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(com.thgiang.image.R.string.draft_duplicate), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) },
                                onClick = { menuExpanded = false; onDuplicate() }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(com.thgiang.image.R.string.multi_delete), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                                onClick = { menuExpanded = false; onDelete() },
                                colors = MenuDefaults.itemColors(
                                    textColor = MaterialTheme.colorScheme.error,
                                    leadingIconColor = MaterialTheme.colorScheme.error
                                )
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .padding(2.dp)
                    .fillMaxWidth()
                    .heightIn(min = 82.dp)
            ) {
                Text(
                    text = draftName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold
                )
                val formattedDate = remember(draft.updatedAt) {
                    try {
                        val time = draft.updatedAt ?: 0L
                        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(time))
                    } catch (e: Exception) {
                        ""
                    }
                }
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    fontSize = 10.sp
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    TextButton(
                        onClick = onDuplicate,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            Icons.Rounded.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            text = stringResource(com.thgiang.image.R.string.draft_card_copy),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 10.sp
                        )
                    }
                    TextButton(
                        onClick = onDelete,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            text = stringResource(com.thgiang.image.R.string.draft_card_delete),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}

private fun resolveDraftThumbnailModel(context: android.content.Context, draft: DraftMetadata): Any? {
    val draftId = draft.id?.takeIf { it.isNotEmpty() } ?: return null
    val draftDir = File(context.filesDir, "drafts/$draftId")
    if (!draftDir.exists()) return null

    draft.thumbnailPath?.let { path ->
        when {
            path.startsWith("file://") -> {
                val filePath = android.net.Uri.parse(path).path
                if (!filePath.isNullOrEmpty()) {
                    val file = File(filePath)
                    if (file.exists()) return file
                }
            }
            else -> {
                val file = File(path)
                if (file.exists()) return file
            }
        }
    }

    val previewFile = File(draftDir, "preview.png")
    if (previewFile.exists()) return previewFile

    draftDir.listFiles { file -> file?.name?.startsWith("layer_") == true }
        ?.firstOrNull()
        ?.let { return it }

    if (draft.isTemplate) {
        draft.templateAssetPath
            ?.takeIf { it.isNotBlank() && it != "null" }
            ?.let { return it.toAssetModel() }
    }

    return null
}

