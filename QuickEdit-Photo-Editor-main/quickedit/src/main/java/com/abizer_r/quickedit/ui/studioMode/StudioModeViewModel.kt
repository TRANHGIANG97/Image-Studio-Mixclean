package com.abizer_r.quickedit.ui.studioMode

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import com.abizer_r.quickedit.R
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.thgiang.image.core.util.ImageEffectProcessor
import com.thgiang.image.core.data.backgroundremove.BackgroundRemoverRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class StudioModeViewModel @Inject constructor(
    private val backgroundRemoverRepository: BackgroundRemoverRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    enum class StudioEffect {
        NONE,
        BLUR,
        PORTRAIT,
        CLEAN,
        DARKEN
    }

    data class StudioModeState(
        val processedBitmap: Bitmap? = null,
        val currentEffect: StudioEffect = StudioEffect.NONE,
        val isProcessing: Boolean = false,
        val error: String? = null,
        val intensity: Float = 0.5f
    )

    data class StudioOption(
        val effect: StudioEffect,
        val title: String,
        val icon: androidx.compose.ui.graphics.vector.ImageVector
    )

    private val _state = MutableStateFlow(StudioModeState())
    val state: StateFlow<StudioModeState> = _state.asStateFlow()

    private var originalBitmap: Bitmap? = null
    private var foregroundBitmap: Bitmap? = null
    private var lastAppliedEffect: StudioEffect? = null
    private var lastAppliedIntensity: Float = -1f

    fun setInitialBitmap(bitmap: Bitmap) {
        originalBitmap = bitmap
        _state.value = _state.value.copy(processedBitmap = bitmap)

        // Pre-extract foreground for faster processing
        viewModelScope.launch(Dispatchers.Default) {
            foregroundBitmap = ImageEffectProcessor.extractForeground(
                context = context,
                bitmap = bitmap,
                backgroundRemoverRepository = backgroundRemoverRepository
            )
        }
    }

    fun updateIntensity(value: Float) {
        val clamped = value.coerceIn(0f, 1f)
        _state.value = _state.value.copy(intensity = clamped)
        applyEffect(_state.value.currentEffect, clamped)
    }

    fun applyEffect(effect: StudioEffect) {
        applyEffect(effect, _state.value.intensity)
    }

    private fun applyEffect(effect: StudioEffect, intensity: Float) {
        val original = originalBitmap ?: return
        if (effect == lastAppliedEffect && intensity == lastAppliedIntensity) return
        if (_state.value.isProcessing) return

        if (effect == StudioEffect.NONE) {
            lastAppliedEffect = effect
            lastAppliedIntensity = intensity
            _state.value = _state.value.copy(
                processedBitmap = original,
                currentEffect = effect,
                isProcessing = false
            )
            return
        }

        lastAppliedEffect = effect
        lastAppliedIntensity = intensity
        _state.value = _state.value.copy(isProcessing = true, currentEffect = effect)

        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                val fg = foregroundBitmap
                if (fg != null) {
                    when (effect) {
                        StudioEffect.BLUR -> ImageEffectProcessor.applyBlur(original, intensity * 25f)
                        StudioEffect.PORTRAIT -> ImageEffectProcessor.applyPortraitCached(
                            original, fg,
                            blurRadius = intensity * 20f,
                            darkenAlpha = 0.15f + intensity * 0.35f,
                            vignette = true
                        )
                        StudioEffect.CLEAN -> ImageEffectProcessor.applyCleanCached(original, fg, intensity)
                        StudioEffect.DARKEN -> ImageEffectProcessor.applyDarkenCached(original, fg, intensity, vignette = true)
                        StudioEffect.NONE -> original
                    }
                } else {
                    // Fallback to non-cached if foreground not ready
                    when (effect) {
                        StudioEffect.BLUR -> ImageEffectProcessor.applyBlur(original, intensity * 25f)
                        StudioEffect.PORTRAIT -> ImageEffectProcessor.applyPortrait(
                            context, original,
                            blurRadius = intensity * 20f,
                            darkenAlpha = 0.15f + intensity * 0.35f,
                            vignette = true,
                            backgroundRemoverRepository = backgroundRemoverRepository
                        )
                        StudioEffect.CLEAN -> ImageEffectProcessor.applyClean(context, original, intensity, backgroundRemoverRepository)
                        StudioEffect.DARKEN -> ImageEffectProcessor.applyDarken(context, original, intensity, vignette = true, backgroundRemoverRepository)
                        StudioEffect.NONE -> original
                    }
                }
            }

            if (result != null) {
                _state.value = _state.value.copy(
                    processedBitmap = result,
                    isProcessing = false
                )
            } else {
                _state.value = _state.value.copy(
                    isProcessing = false,
                    error = context.getString(com.abizer_r.quickedit.R.string.error_apply_effect)
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        originalBitmap = null
        foregroundBitmap = null
    }
}
