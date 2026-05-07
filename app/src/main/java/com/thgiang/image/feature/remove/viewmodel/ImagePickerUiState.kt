package com.thgiang.image.feature.remove.viewmodel
import android.net.Uri

data class RemovalEngineResult(
    val displayUri: Uri,
    val hasAlpha: Boolean,
    val inferenceMs: Long? = null
)

data class ImagePickerUiState(
    val selectedImageUri: Uri? = null,
    val progress: Int = 0,
    val isProcessing: Boolean = false,
    val isSaving: Boolean = false,
    val result: RemovalEngineResult? = null,
    val error: String? = null
)




