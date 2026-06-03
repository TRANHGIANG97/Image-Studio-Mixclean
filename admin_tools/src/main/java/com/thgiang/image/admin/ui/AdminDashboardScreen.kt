package com.thgiang.image.admin.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Scale
import com.thgiang.image.admin.viewmodel.AdminDashboardViewModel
import com.thgiang.image.core.domain.model.template.CloudTemplate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    onNavigateToBuilder: (CloudTemplate?) -> Unit,
    onBack: () -> Unit,
    viewModel: AdminDashboardViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var showMessageDialog by remember { mutableStateOf(false) }

    val importBundleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importBundle(it) }
    }

    // Message dialog
    LaunchedEffect(state.dashboardMessage) {
        if (state.dashboardMessage != null) showMessageDialog = true
    }

    if (showMessageDialog && state.dashboardMessage != null) {
        AlertDialog(
            onDismissRequest = { showMessageDialog = false; viewModel.clearMessage() },
            title = { Text("Admin Tools") },
            text = { Text(state.dashboardMessage!!) },
            confirmButton = {
                TextButton(onClick = { showMessageDialog = false; viewModel.clearMessage() }) {
                    Text("OK")
                }
            }
        )
    }

    // Delete confirmation
    state.fileToDelete?.let { file ->
        AlertDialog(
            onDismissRequest = { viewModel.setFileToDelete(null) },
            title = { Text("Xác nhận xóa") },
            text = { Text("Bạn có chắc chắn muốn xóa template '${file.name}' này không? Hành động này không thể hoàn tác.") },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmDelete() }) {
                    Text("Xóa", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setFileToDelete(null) }) {
                    Text("Hủy")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Admin Dashboard") },
                actions = {
                    IconButton(onClick = { importBundleLauncher.launch(arrayOf("application/zip", "application/octet-stream")) }) {
                        Icon(Icons.Default.FileUpload, contentDescription = "Import Bundle")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onNavigateToBuilder(null) },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Tạo Template Mới") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search & Filter Bar
            if (state.templates.isNotEmpty() && !state.isLoading) {
                SearchFilterBar(
                    query = state.searchQuery,
                    onQueryChange = { viewModel.setSearchQuery(it) },
                    categories = state.availableCategories,
                    selectedCategory = state.categoryFilter,
                    onCategorySelected = { viewModel.setCategoryFilter(it) }
                )
            }

            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (state.importProgress != null) {
                            if (state.importProgress!! < 0f) {
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth(0.6f).padding(bottom = 16.dp)
                                )
                            } else {
                                LinearProgressIndicator(
                                    progress = { state.importProgress!!.coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth(0.6f).padding(bottom = 16.dp)
                                )
                            }
                            Text(
                                "Đang import template...",
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            CircularProgressIndicator()
                        }
                    }
                }
            } else if (state.templates.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Chưa có template nào được lưu.")
                }
            } else {
                val displayTemplates = state.filteredTemplates
                if (displayTemplates.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Không tìm thấy template phù hợp.")
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(displayTemplates, key = { it.jsonFile.absolutePath }) { record ->
                            TemplateCard(
                                record = record,
                                onClick = { onNavigateToBuilder(record.template) },
                                onShare = { viewModel.shareFile(record.shareFile) },
                                onDelete = { viewModel.setFileToDelete(record.deleteTarget) }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Search & Filter ──

@Composable
private fun SearchFilterBar(
    query: String,
    onQueryChange: (String) -> Unit,
    categories: List<String>,
    selectedCategory: String?,
    onCategorySelected: (String?) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Tìm kiếm template...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Xóa")
                    }
                }
            },
            singleLine = true
        )
        if (categories.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { onCategorySelected(null) },
                    label = { Text("Tất cả") }
                )
                categories.forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { onCategorySelected(if (selectedCategory == cat) null else cat) },
                        label = { Text(cat, maxLines = 1) }
                    )
                }
            }
        }
    }
}

// ── Template Card ──

@Composable
private fun TemplateCard(
    record: AdminTemplateRecord,
    onClick: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val template = record.template
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column {
            // Thumbnail
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
            ) {
                val imageUrl = template.metadata.thumbnailUrl.ifEmpty { template.canvas.backgroundUrl }
                if (!imageUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(imageUrl)
                            .crossfade(300)
                            .scale(Scale.FILL)
                            .build(),
                        contentDescription = "Thumbnail",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No Image")
                    }
                }
            }

            // Info
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = template.metadata.title.ifEmpty { "Untitled" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = template.categoryId.ifEmpty { "No category" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                val hasIssues = record.issues.isNotEmpty()
                val statusText = if (hasIssues) "${record.issues.size} issue(s)" else "Valid"
                val statusColor = if (hasIssues) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (hasIssues) {
                    Text(
                        text = record.issues.first(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(onClick = onShare, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Share, contentDescription = "Share", modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}
