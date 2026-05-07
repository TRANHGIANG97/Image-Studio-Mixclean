package com.thgiang.image.feature.home.viewmodel
import android.net.Uri
import com.thgiang.image.core.model.BorderPresetStyle
import com.thgiang.image.core.model.HomeStyleRequest
import com.thgiang.image.core.model.PresetStyle

data class HomeUiState(
    val selectedImageUri: Uri? = null,
    val processedImageUri: Uri? = null,
    val processedHasAlpha: Boolean = false,
    val isProcessing: Boolean = false,
    val isPresetFlowProcessing: Boolean = false,
    val isSaving: Boolean = false,
    val progress: Int = 0,
    val selectedPresetStyle: PresetStyle? = null,
    val selectedBorderPresetStyle: BorderPresetStyle? = null,
    val pendingStyleRequest: HomeStyleRequest? = null,
    val pendingEditorUri: Uri? = null,
    /** Cặp ảnh before/after dùng cho slider khi chưa chọn ảnh (lưu trong cache). */
    val lastSliderBeforeUri: Uri? = null,
    val lastSliderAfterUri: Uri? = null,
    val currentBorderThickness: Int? = null,
    val pureForegroundUri: Uri? = null
)





