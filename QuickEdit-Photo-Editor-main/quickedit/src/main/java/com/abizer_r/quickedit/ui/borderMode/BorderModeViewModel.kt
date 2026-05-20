package com.abizer_r.quickedit.ui.borderMode

import android.graphics.Color as AndroidColor
import androidx.lifecycle.ViewModel
import com.abizer_r.quickedit.utils.BorderGradientPreset
import com.abizer_r.quickedit.utils.BorderGradientPresets
import com.abizer_r.quickedit.utils.BorderPreset
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

@HiltViewModel
class BorderModeViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(BorderModeState())
    val state: StateFlow<BorderModeState> = _state

    private val borderProcessingCount = AtomicInteger(0)
    private var lastBorderGradientPreset: BorderGradientPreset? = BorderGradientPresets.modernPresets.firstOrNull()
    private val _shouldGoToNextScreen = MutableStateFlow(false)
    val shouldGoToNextScreen: StateFlow<Boolean> = _shouldGoToNextScreen

    fun onNextScreenRequested() {
        _shouldGoToNextScreen.value = true
    }

    fun updateBorderColor(colorArgb: Int) {
        if (_state.value.borderColorArgb == colorArgb) return
        _state.update { it.copy(borderColorArgb = colorArgb, borderGradientPreset = null) }
    }

    fun updateBorderThickness(thickness: Float) {
        if (_state.value.borderThickness == thickness) return
        _state.update { it.copy(borderThickness = thickness) }
    }

    fun updateBorderPreset(preset: BorderPreset) {
        if (_state.value.borderPreset == preset) return
        _state.update { it.copy(borderPreset = preset) }
    }

    fun updateBorderGradientPreset(preset: BorderGradientPreset) {
        if (_state.value.borderGradientPreset == preset) return
        lastBorderGradientPreset = preset
        _state.update { it.copy(borderGradientPreset = preset) }
    }

    fun applyInitialGradientPreset(presetId: String?) {
        if (presetId.isNullOrBlank()) return
        val preset = BorderGradientPresets.modernPresets.firstOrNull { it.id == presetId } ?: return
        lastBorderGradientPreset = preset
        _state.update { it.copy(borderGradientPreset = preset) }
    }

    fun enableBorderGradientMode() {
        val current = _state.value.borderGradientPreset
        if (current != null) return
        _state.update {
            it.copy(borderGradientPreset = lastBorderGradientPreset ?: BorderGradientPresets.modernPresets.firstOrNull())
        }
    }

    fun enableBorderSolidMode() {
        if (_state.value.borderGradientPreset == null) return
        _state.update { it.copy(borderGradientPreset = null) }
    }

    fun resetBorderSettings() {
        val current = _state.value
        if (
            current.borderColorArgb == DEFAULT_BORDER_COLOR &&
            current.borderThickness == DEFAULT_BORDER_THICKNESS &&
            current.borderPreset == DEFAULT_BORDER_PRESET &&
            current.borderGradientPreset == null
        ) {
            return
        }
        _state.value = current.copy(
            borderColorArgb = DEFAULT_BORDER_COLOR,
            borderThickness = DEFAULT_BORDER_THICKNESS,
            borderPreset = DEFAULT_BORDER_PRESET,
            borderGradientPreset = null
        )
    }

    fun beginBorderProcessing() {
        if (borderProcessingCount.incrementAndGet() == 1) {
            setApplyingBorder(true)
        }
    }

    fun endBorderProcessing() {
        val remaining = borderProcessingCount.decrementAndGet()
        if (remaining <= 0) {
            borderProcessingCount.set(0)
            setApplyingBorder(false)
        }
    }

    fun setApplyingBorder(isApplying: Boolean) {
        if (_state.value.isApplyingBorder == isApplying) return
        _state.update { it.copy(isApplyingBorder = isApplying) }
    }

    companion object {
        private const val DEFAULT_BORDER_THICKNESS = 14f
        private val DEFAULT_BORDER_COLOR = AndroidColor.BLACK
        private val DEFAULT_BORDER_PRESET = BorderPreset.SOLID
    }
}
