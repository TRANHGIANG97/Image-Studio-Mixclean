package com.abizer_r.quickedit.ui.backgroundMode

import android.graphics.Bitmap
import android.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thgiang.image.core.util.ImageEffectProcessor
import com.thgiang.image.core.util.ImageEffectProcessor.BackgroundType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class BackgroundModeViewModel @Inject constructor() : ViewModel() {

    enum class BackgroundTab {
        IMAGE, COLOR, GRADIENT
    }

    data class BackgroundModeState(
        val processedBitmap: Bitmap? = null,
        val currentTab: BackgroundTab = BackgroundTab.COLOR,
        val selectedColor: Int? = null,
        val selectedGradient: IntArray? = null,
        val selectedImage: Bitmap? = null,
        val isProcessing: Boolean = false,
        val hasAlpha: Boolean = true,
        val error: String? = null
    )

    private val _state = MutableStateFlow(BackgroundModeState())
    val state: StateFlow<BackgroundModeState> = _state.asStateFlow()

    private var foregroundBitmap: Bitmap? = null

    fun setInitialBitmap(bitmap: Bitmap) {
        foregroundBitmap = bitmap
        val hasAlpha = checkHasAlpha(bitmap)
        _state.value = _state.value.copy(
            processedBitmap = bitmap,
            hasAlpha = hasAlpha
        )
        
        if (hasAlpha) {
            // Default background
            applyColorBackground(Color.WHITE)
        }
    }

    private fun checkHasAlpha(bitmap: Bitmap): Boolean {
        // Simple heuristic: check some pixels or use bitmap.hasAlpha()
        // Note: bitmap.hasAlpha() might be true even if all pixels are opaque.
        // For better accuracy, we could scan a few pixels, but let's trust the flag for now
        // or check if it's ARGB_8888.
        return bitmap.hasAlpha()
    }

    fun setTab(tab: BackgroundTab) {
        _state.value = _state.value.copy(currentTab = tab)
    }

    fun applyColorBackground(color: Int) {
        val fg = foregroundBitmap ?: return
        _state.value = _state.value.copy(isProcessing = true, selectedColor = color, selectedGradient = null, selectedImage = null)
        
        viewModelScope.launch {
            val result = ImageEffectProcessor.applyBackground(
                foreground = fg,
                backgroundType = BackgroundType.COLOR,
                backgroundColor = color
            )
            updateResult(result)
        }
    }

    fun applyGradientBackground(colors: IntArray) {
        val fg = foregroundBitmap ?: return
        _state.value = _state.value.copy(isProcessing = true, selectedGradient = colors, selectedColor = null, selectedImage = null)
        
        viewModelScope.launch {
            val result = ImageEffectProcessor.applyBackground(
                foreground = fg,
                backgroundType = BackgroundType.GRADIENT,
                backgroundGradient = colors
            )
            updateResult(result)
        }
    }

    fun applyImageBackground(bgImage: Bitmap) {
        val fg = foregroundBitmap ?: return
        _state.value = _state.value.copy(isProcessing = true, selectedImage = bgImage, selectedColor = null, selectedGradient = null)
        
        viewModelScope.launch {
            val result = ImageEffectProcessor.applyBackground(
                foreground = fg,
                backgroundType = BackgroundType.IMAGE,
                backgroundImage = bgImage
            )
            updateResult(result)
        }
    }

    private fun updateResult(result: Bitmap?) {
        if (result != null) {
            _state.value = _state.value.copy(processedBitmap = result, isProcessing = false)
        } else {
            _state.value = _state.value.copy(isProcessing = false, error = "Failed to apply background")
        }
    }
}
