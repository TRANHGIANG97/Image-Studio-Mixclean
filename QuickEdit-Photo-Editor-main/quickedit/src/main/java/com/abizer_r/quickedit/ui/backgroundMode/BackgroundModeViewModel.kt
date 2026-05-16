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
        val foregroundOffsetY: Float = 0f,
        val foregroundScale: Float = 1f,
        val foregroundRotation: Float = 0f
    )

    private val _state = MutableStateFlow(BackgroundModeState())
    val state: StateFlow<BackgroundModeState> = _state.asStateFlow()

    private var sourceWidth: Int = 0
    private var sourceHeight: Int = 0

    private var maxOffsetX: Float = 0f
    private var maxOffsetY: Float = 0f

    fun setInitialBitmap(bitmap: Bitmap) {
        sourceWidth = bitmap.width
        sourceHeight = bitmap.height
        val hasAlpha = checkHasAlpha(bitmap)

        if (hasAlpha) {
            _state.value = _state.value.copy(
                foregroundBitmap = ProcessorUtils.trimTransparentBounds(bitmap),
                hasAlpha = true,
                isProcessing = false,
                foregroundOffsetX = 0f,
                foregroundOffsetY = 0f,
                foregroundScale = 1f,
                foregroundRotation = 0f
            )
            applyColorBackground(android.graphics.Color.WHITE)
        } else {
            _state.value = _state.value.copy(
                hasAlpha = true,
                isProcessing = true,
                foregroundOffsetX = 0f,
                foregroundOffsetY = 0f,
                foregroundScale = 1f,
                foregroundRotation = 0f
            )

            viewModelScope.launch(Dispatchers.Default) {
                val result = backgroundRemoverRepository.getForegroundBitmap(bitmap)
                result.onSuccess { fg ->
                    withContext(Dispatchers.Main) {
                        _state.value = _state.value.copy(
                            foregroundBitmap = ProcessorUtils.trimTransparentBounds(fg),
                            isProcessing = false,
                            foregroundOffsetX = 0f,
                            foregroundOffsetY = 0f,
                            foregroundScale = 1f,
                            foregroundRotation = 0f
                        )
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
        return ProcessorUtils.hasMeaningfulTransparency(bitmap)
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
        _state.value = _state.value.copy(
            foregroundOffsetX = 0f,
            foregroundOffsetY = 0f,
            foregroundScale = 1f,
            foregroundRotation = 0f
        )
    }

    fun updateForegroundTransform(zoomChange: Float, rotationChange: Float) {
        val current = _state.value
        val newScale = (current.foregroundScale * zoomChange).coerceIn(0.5f, 3f)
        updateDragBounds(current.backgroundBitmap, current.foregroundBitmap, newScale)
        val clampedOffset = clampOffset(
            offsetX = current.foregroundOffsetX,
            offsetY = current.foregroundOffsetY
        )
        _state.value = current.copy(
            foregroundOffsetX = clampedOffset.first,
            foregroundOffsetY = clampedOffset.second,
            foregroundScale = newScale,
            foregroundRotation = current.foregroundRotation + rotationChange
        )
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
            selectedPresetStyle = null
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
            selectedPresetStyle = null
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
            selectedPresetStyle = null
        )

        viewModelScope.launch {
            val w = sourceWidth
            val h = sourceHeight
            val fg = _state.value.foregroundBitmap
            if (w <= 0 || h <= 0 || fg == null) {
                _state.value = _state.value.copy(isProcessing = false)
                return@launch
            }

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
            selectedImage = null
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
        updateDragBounds(bg, s.foregroundBitmap, s.foregroundScale)
        val clampedOffset = clampOffset(s.foregroundOffsetX, s.foregroundOffsetY)

        _state.value = _state.value.copy(
            backgroundBitmap = bg,
            isProcessing = false,
            foregroundOffsetX = clampedOffset.first,
            foregroundOffsetY = clampedOffset.second
        )
    }

    private fun updateDragBounds(background: Bitmap?, foreground: Bitmap?, scale: Float) {
        if (
            background == null ||
            foreground == null ||
            background.width <= 0 ||
            background.height <= 0 ||
            foreground.width <= 0 ||
            foreground.height <= 0
        ) {
            maxOffsetX = 0f
            maxOffsetY = 0f
            return
        }

        val baseScale = minOf(
            background.width.toFloat() / foreground.width.toFloat(),
            background.height.toFloat() / foreground.height.toFloat()
        )
        val drawW = foreground.width * baseScale * scale
        val drawH = foreground.height * baseScale * scale
        maxOffsetX = maxOf(background.width * 0.5f, drawW * 0.5f)
        maxOffsetY = maxOf(background.height * 0.5f, drawH * 0.5f)
    }

    private fun clampOffset(offsetX: Float, offsetY: Float): Pair<Float, Float> {
        return Pair(
            offsetX.coerceIn(-maxOffsetX, maxOffsetX),
            offsetY.coerceIn(-maxOffsetY, maxOffsetY)
        )
    }

    /** Composite foreground onto background with current offset. Called on Done. */
    fun getFinalBitmap(): Bitmap? {
        val s = _state.value
        val bg = s.backgroundBitmap ?: return null
        val fg = s.foregroundBitmap ?: return null
        return ProcessorUtils.compositeForegroundOverBackground(
            background = bg,
            foreground = fg,
            offsetX = s.foregroundOffsetX,
            offsetY = s.foregroundOffsetY,
            scale = s.foregroundScale,
            rotationDegrees = s.foregroundRotation
        )
    }
}
