package com.thgiang.image.feature.remove.ui

import android.annotation.SuppressLint
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.thgiang.image.R
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import com.thgiang.image.core.design.components.GlassSurface
import com.thgiang.image.core.design.components.GradientPrimaryButton
import com.thgiang.image.core.design.components.StatusChip
import com.thgiang.image.core.design.components.TransparentBackgroundPattern
import com.thgiang.image.core.design.theme.ImageDesign
import com.thgiang.image.feature.remove.viewmodel.BatchRemoveSnackbarEvent
import com.thgiang.image.feature.remove.viewmodel.BatchRemoveViewModel
import com.thgiang.image.feature.remove.viewmodel.ProcessedResult

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun BatchRemoveScreen(
    initialUris: List<Uri>,
    onBack: () -> Unit,
    onAddMore: () -> Unit = {},
    viewModel: BatchRemoveViewModel = hiltViewModel(),
    contentPadding: PaddingValues = PaddingValues(),
    onRequireSaveAd: ((() -> Unit) -> Unit)? = null
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var zoomedUri by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(initialUris) {
        viewModel.setInitialUris(initialUris)
    }

    BackHandler {
        if (zoomedUri != null) {
            zoomedUri = null
        } else {
            onBack()
        }
    }

    val saveSuccessText = stringResource(R.string.multi_saved_successfully)
    val saveFailedText = stringResource(R.string.multi_save_failed)

    LaunchedEffect(state.snackbarEvent) {
        when (val event = state.snackbarEvent) {
            is BatchRemoveSnackbarEvent.Text -> {
                snackbarHostState.showSnackbar(event.message)
                viewModel.onErrorConsumed()
            }
            BatchRemoveSnackbarEvent.SaveSuccess -> {
                snackbarHostState.showSnackbar(saveSuccessText)
                viewModel.onErrorConsumed()
            }
            BatchRemoveSnackbarEvent.SaveFailed -> {
                snackbarHostState.showSnackbar(saveFailedText)
                viewModel.onErrorConsumed()
            }
            null -> Unit
            else -> Unit
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = ImageDesign.surfaces.base
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ImageDesign.gradients.appBackground)
                .padding(contentPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.go_back))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.multi_title),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        if (state.selectedUris.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.multi_selected_inline, state.selectedUris.size),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (state.canProcess && !state.isProcessing) {
                        StatusChip(
                            text = stringResource(R.string.batch_pending),
                            tone = ImageDesign.semantic.aiAccent
                        )
                    }
                    if (!state.isProcessing && !state.isSavingAll) {
                        Spacer(modifier = Modifier.width(4.dp))
                        TextButton(
                            onClick = onAddMore,
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text(
                                stringResource(R.string.batch_add_more),
                                fontWeight = FontWeight.SemiBold,
                                color = ImageDesign.semantic.aiAccent
                            )
                        }
                    }
                }

                // Progress Bar
                AnimatedVisibility(visible = state.isProcessing || state.isSavingAll) {
                    GlassSurface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val progress = if (state.isSavingAll) {
                                if (state.saveAllTotal == 0) 0f else state.saveAllDone.toFloat() / state.saveAllTotal
                            } else {
                                state.progressPercent / 100f
                            }
                            val label = if (state.isSavingAll) stringResource(R.string.multi_saving_progress, state.saveAllDone, state.saveAllTotal)
                                         else stringResource(R.string.batch_processing, state.batchCompleted, state.batchTotal)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                if (state.canCancel && !state.isSavingAll) {
                                    Text(
                                        text = stringResource(R.string.multi_cancel),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.clickable { viewModel.onCancelProcessing() }
                                    )
                                }
                            }
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(999.dp)),
                                color = ImageDesign.semantic.aiAccent,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                    }
                }

                // Grid Content
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 120.dp),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(bottom = 96.dp, top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(items = state.results, key = { it.displayUri.toString() }) { result ->
                        BatchResultCard(
                            result = result,
                            isProcessing = state.isProcessing,
                            isSavingAll = state.isSavingAll,
                            onRemove = { viewModel.removeResult(result) },
                            onSave = {
                                val saveAction = { viewModel.saveImage(result) }
                                onRequireSaveAd?.invoke(saveAction) ?: saveAction()
                            },
                            onDelete = { viewModel.deleteImage(result) },
                            onZoom = { zoomedUri = result.displayUri }
                        )
                    }
                }
            }

            // Floating Bottom Bar
            AnimatedVisibility(
                visible = state.results.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp, start = 16.dp, end = 16.dp)
            ) {
                GlassSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(999.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { viewModel.clearAllResults() },
                            enabled = !state.isProcessing && !state.isSavingAll,
                            contentPadding = PaddingValues(horizontal = 16.dp)
                        ) {
                            Text(stringResource(R.string.multi_clear_all), style = MaterialTheme.typography.labelLarge)
                        }
                        GradientPrimaryButton(
                            text = if (state.isSavingAll) stringResource(R.string.multi_saving_all) else stringResource(R.string.multi_save_all),
                            onClick = {
                                val saveAction = { viewModel.onSaveAllClicked() }
                                onRequireSaveAd?.invoke(saveAction) ?: saveAction()
                            },
                            enabled = !state.isProcessing && !state.isSavingAll,
                            modifier = Modifier.height(44.dp)
                        )
                    }
                }
            }

            // Zoom Overlay
            zoomedUri?.let { uriToShow ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.95f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { zoomedUri = null },
                    contentAlignment = Alignment.Center
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        TransparentBackgroundPattern(
                            modifier = Modifier.fillMaxSize(),
                            squareSize = 24.dp,
                            lightColor = Color(0xFF4A4A4A),
                            darkColor = Color(0xFF2D2D2D)
                        )
                        AsyncImage(
                            model = uriToShow,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 90.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BatchResultCard(
    result: ProcessedResult,
    isProcessing: Boolean,
    isSavingAll: Boolean,
    onRemove: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onZoom: () -> Unit
) {
    GlassSurface(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .combinedClickable(
                        onClick = { },
                        onLongClick = onZoom
                    )
            ) {
                TransparentBackgroundPattern(modifier = Modifier.matchParentSize())
                AsyncImage(
                    model = result.displayUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                if (!isProcessing) {
                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(24.dp)
                            .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.padding(2.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Button(
                    onClick = onSave,
                    enabled = !isProcessing && !isSavingAll,
                    modifier = Modifier
                        .weight(1f)
                        .height(30.dp),
                    contentPadding = PaddingValues(0.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(R.string.multi_save), fontSize = 10.sp)
                }
                OutlinedButton(
                    onClick = onDelete,
                    enabled = !isProcessing && !isSavingAll,
                    modifier = Modifier
                        .weight(1f)
                        .height(30.dp),
                    contentPadding = PaddingValues(0.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(R.string.multi_delete), fontSize = 10.sp)
                }
            }
        }
    }
}
