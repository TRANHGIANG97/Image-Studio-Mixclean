package com.abizer_r.quickedit.ui.backgroundMode

import android.graphics.Bitmap
import android.graphics.Color
import androidx.lifecycle.ViewModel
import com.abizer_r.quickedit.R
import androidx.lifecycle.viewModelScope
import com.thgiang.image.core.util.ImageEffectProcessor
import com.thgiang.image.core.util.ImageEffectProcessor.BackgroundType
import com.thgiang.image.core.util.processors.ProcessorUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class BackgroundModeViewModel @Inject constructor(
    private val backgroundRemoverRepository: com.thgiang.image.core.data.backgroundremove.BackgroundRemoverRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : ViewModel() {

    enum class BackgroundTab {
        IMAGE, COLOR, GRADIENT, PRESET
    }

    data class BackgroundModeState(
        val backgroundBitmap: Bitmap? = null,
        val foregroundBitmap: Bitmap? = null,
        val currentTab: BackgroundTab = BackgroundTab.COLOR,
        val selectedColor: Int? = null,
        val selectedGradient: IntArray? = null,
        val selectedImage: Bitmap? = null,
        val selectedPresetStyle: com.thgiang.image.core.model.PresetStyle? = null,
        val isProcessing: Boolean = false,
        val hasAlpha: Boolean = true,
        val error: String? = null,
        val foregroundOffsetX: Float = 0f,
        val foregroundOffsetY: Float = 0f
    )

    private val _state = MutableStateFlow(BackgroundModeState())
    val state: StateFlow<BackgroundModeState> = _state.asStateFlow()

    private var sourceWidth: Int = 0
    private var sourceHeight: Int = 0

    // Max drag offset so foreground stays within background bounds (fraction of dimension)
    private var maxOffsetX: Float = 0f
    private var maxOffsetY: Float = 0f

    fun setInitialBitmap(bitmap: Bitmap) {
        sourceWidth = bitmap.width
        sourceHeight = bitmap.height
        val hasAlpha = checkHasAlpha(bitmap)

        if (hasAlpha) {
            _state.value = _state.value.copy(
                foregroundBitmap = bitmap,
                hasAlpha = true,
                isProcessing = false
            )
            applyColorBackground(android.graphics.Color.WHITE)
        } else {
            _state.value = _state.value.copy(
                hasAlpha = true,
                isProcessing = true
            )

            viewModelScope.launch(Dispatchers.Default) {
                val result = backgroundRemoverRepository.getForegroundBitmap(bitmap)
                result.onSuccess { fg ->
                    withContext(Dispatchers.Main) {
                        _state.value = _state.value.copy(foregroundBitmap = fg, isProcessing = false)
                        applyColorBackground(android.graphics.Color.WHITE)
                    }
                }.onFailure { e ->
                    withContext(Dispatchers.Main) {
                        _state.value = _state.value.copy(
                            isProcessing = false,
                            error = context.getString(com.abizer_r.quickedit.R.string.error_auto_removal_failed, e.message),
                            hasAlpha = false
                        )
                    }
                }
            }
        }
    }

    private fun checkHasAlpha(bitmap: Bitmap): Boolean {
        return bitmap.hasAlpha()
    }

    fun setTab(tab: BackgroundTab) {
        _state.value = _state.value.copy(currentTab = tab)
    }

    fun updateForegroundOffset(dx: Float, dy: Float) {
        val current = _state.value
        val newX = (current.foregroundOffsetX + dx).coerceIn(-maxOffsetX, maxOffsetX)
        val newY = (current.foregroundOffsetY + dy).coerceIn(-maxOffsetY, maxOffsetY)
        _state.value = current.copy(foregroundOffsetX = newX, foregroundOffsetY = newY)
    }

    fun resetForegroundPosition() {
        _state.value = _state.value.copy(foregroundOffsetX = 0f, foregroundOffsetY = 0f)
    }

    fun applyColorBackground(color: Int) {
        val w = sourceWidth
        val h = sourceHeight
        if (w <= 0 || h <= 0) return

        _state.value = _state.value.copy(
            isProcessing = true,
            selectedColor = color,
            selectedGradient = null,
            selectedImage = null,
            selectedPresetStyle = null,
            foregroundOffsetX = 0f,
            foregroundOffsetY = 0f
        )

        viewModelScope.launch {
            val bg = withContext(Dispatchers.Default) {
                ProcessorUtils.createColorBitmap(w, h, color)
            }
            updateBackgroundBitmap(bg)
        }
    }

    fun applyGradientBackground(colors: IntArray) {
        val w = sourceWidth
        val h = sourceHeight
        if (w <= 0 || h <= 0) return

        _state.value = _state.value.copy(
            isProcessing = true,
            selectedGradient = colors,
            selectedColor = null,
            selectedImage = null,
            selectedPresetStyle = null,
            foregroundOffsetX = 0f,
            foregroundOffsetY = 0f
        )

        viewModelScope.launch {
            val bg = withContext(Dispatchers.Default) {
                ProcessorUtils.createGradientBitmap(w, h, colors)
            }
            updateBackgroundBitmap(bg)
        }
    }

    fun applyImageBackground(bgImage: Bitmap) {
        _state.value = _state.value.copy(
            isProcessing = true,
            selectedImage = bgImage,
            selectedColor = null,
            selectedGradient = null,
            selectedPresetStyle = null,
            foregroundOffsetX = 0f,
            foregroundOffsetY = 0f
        )

        viewModelScope.launch {
            val w = sourceWidth
            val h = sourceHeight
            val fg = _state.value.foregroundBitmap
            if (w <= 0 || h <= 0 || fg == null) return@launch

            val bg = withContext(Dispatchers.Default) {
                val scaledBg = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(scaledBg)
                val paint = android.graphics.Paint(
                    android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG
                )
                val scaleX = w.toFloat() / bgImage.width
                val scaleY = h.toFloat() / bgImage.height
                val scale = maxOf(scaleX, scaleY)
                val dx = (w - bgImage.width * scale) / 2
                val dy = (h - bgImage.height * scale) / 2
                canvas.save()
                canvas.translate(dx, dy)
                canvas.scale(scale, scale)
                canvas.drawBitmap(bgImage, 0f, 0f, paint)
                canvas.restore()
                scaledBg
            }
            updateBackgroundBitmap(bg)
        }
    }

    fun applyPresetBackground(style: com.thgiang.image.core.model.PresetStyle) {
        val w = sourceWidth
        val h = sourceHeight
        if (w <= 0 || h <= 0) return

        _state.value = _state.value.copy(
            isProcessing = true,
            selectedPresetStyle = style,
            selectedColor = null,
            selectedGradient = null,
            selectedImage = null,
            foregroundOffsetX = 0f,
            foregroundOffsetY = 0f
        )

        viewModelScope.launch {
            val bg = withContext(Dispatchers.Default) {
                com.thgiang.image.core.util.processors.PresetRenderer.createPresetBitmap(w, h, style)
            }
            updateBackgroundBitmap(bg)
        }
    }

    private fun updateBackgroundBitmap(bg: Bitmap) {
        val s = _state.value
        val fg = s.foregroundBitmap
        if (fg != null && fg.width > 0 && fg.height > 0) {
            val scaleX = bg.width.toFloat() / fg.width
            val scaleY = bg.height.toFloat() / fg.height
            val scale = minOf(scaleX, scaleY)
            val drawW = fg.width * scale
            val drawH = fg.height * scale
            // Allow dragging up to 40% of background dimension in each direction
            maxOffsetX = bg.width * 0.4f
            maxOffsetY = bg.height * 0.4f
        } else {
            maxOffsetX = 0f
            maxOffsetY = 0f
        }

        _state.value = _state.value.copy(
            backgroundBitmap = bg,
            isProcessing = false,
            foregroundOffsetX = 0f,
            foregroundOffsetY = 0f
        )
    }

    /** Composite foreground onto background with current offset. Called on Done. */
    fun getFinalBitmap(): Bitmap? {
        val s = _state.value
        val bg = s.backgroundBitmap ?: return null
        val fg = s.foregroundBitmap ?: return null
        return ProcessorUtils.compositeForegroundOverBackground(bg, fg, s.foregroundOffsetX, s.foregroundOffsetY)
    }
}
