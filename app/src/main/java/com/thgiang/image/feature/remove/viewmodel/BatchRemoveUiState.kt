package com.thgiang.image.feature.remove.viewmodel

import android.net.Uri

sealed interface BatchRemoveSnackbarEvent {
    data object SaveSuccess : BatchRemoveSnackbarEvent
    data object SaveFailed : BatchRemoveSnackbarEvent
    data object SaveAllProgress : BatchRemoveSnackbarEvent
    data class Text(val message: String) : BatchRemoveSnackbarEvent
}

data class BatchRemoveUiState(
    val selectedUris: List<Uri> = emptyList(),
    val results: List<ProcessedResult> = emptyList(),
    val processedUriCount: Int = 0,
    val progressPercent: Int = 0,
    val batchCompleted: Int = 0,
    val batchTotal: Int = 0,
    val isProcessing: Boolean = false,
    val isSavingAll: Boolean = false,
    val saveAllDone: Int = 0,
    val saveAllTotal: Int = 0,
    val snackbarEvent: BatchRemoveSnackbarEvent? = null
) {
    val totalCount: Int get() = selectedUris.size
    val completedCount: Int get() = batchCompleted
    val pendingCount: Int get() = (selectedUris.size - processedUriCount).coerceAtLeast(0)
    val canProcess: Boolean get() = pendingCount > 0 && !isProcessing
    val canCancel: Boolean get() = isProcessing
}

data class ProcessedResult(val displayUri: Uri, val hasAlpha: Boolean)
