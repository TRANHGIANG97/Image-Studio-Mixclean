package com.abizer_r.quickedit.ui.borderMode

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class BorderModeViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(BorderModeState())
    val state: StateFlow<BorderModeState> = _state

    var shouldGoToNextScreen = false

    fun updateBorderColor(colorArgb: Int) {
        _state.update { it.copy(borderColorArgb = colorArgb) }
    }

    fun updateBorderThickness(thickness: Float) {
        _state.update { it.copy(borderThickness = thickness) }
    }

    fun setApplyingBorder(isApplying: Boolean) {
        _state.update { it.copy(isApplyingBorder = isApplying) }
    }
}
