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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.abizer_r.quickedit.utils.background.GradientBackgroundRenderer
import javax.inject.Inject

@HiltViewModel
class BackgroundModeViewModel @Inject constructor(
    private val backgroundRemoverRepository: com.thgiang.image.core.data.backgroundremove.BackgroundRemoverRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : ViewModel() {

    private var overlayJob: Job? = null
    private var backgroundRenderJob: Job? = null
    private var backgroundRenderGeneration: Long = 0L

    enum class BackgroundTab {
        IMAGE, COLOR, GRADIENT, PRESET
    }

    enum class CanvasRatio(val widthRatio: Float, val heightRatio: Float, val label: String) {
        RATIO_9_16(9f, 16f, "9:16"),
        RATIO_16_9(16f, 9f, "16:9"),
        RATIO_1_1(1f, 1f, "1:1"),
        RATIO_4_5(4f, 5f, "4:5"),
        RATIO_3_4(3f, 4f, "3:4"),
        RATIO_4_3(4f, 3f, "4:3"),
        RATIO_ORIGINAL(0f, 0f, "Gốc")
    }

    data class BackgroundModeState(
        val backgroundBitmap: Bitmap? = null,
        val foregroundBitmap: Bitmap? = null,
        val currentTab: BackgroundTab = BackgroundTab.COLOR,
        val selectedColor: Int? = null,
        val selectedGradientPreset: BackgroundGradientPreset? = null,
        val selectedImage: Bitmap? = null,
        val selectedPresetStyle: com.thgiang.image.core.model.PresetStyle? = null,
        val isProcessing: Boolean = false,
        val hasAlpha: Boolean = true,
        val error: String? = null,
        val foregroundOffsetX: Float = 0f,
        val foregroundOffsetY: Float = 0f,
        val foregroundScale: Float = 1f,
        val foregroundRotation: Float = 0f,
        val foregroundFlippedH: Boolean = false,
        val foregroundFlippedV: Boolean = false,
        val showOverlay: Boolean = false,
        val selectedRatio: CanvasRatio = CanvasRatio.RATIO_9_16
    )

    private val _state = MutableStateFlow(BackgroundModeState())
    val state: StateFlow<BackgroundModeState> = _state.asStateFlow()

    private var sourceWidth: Int = 0
    private var sourceHeight: Int = 0
    private var originalWidth: Int = 0
    private var originalHeight: Int = 0

    private var maxOffsetX: Float = 0f
    private var maxOffsetY: Float = 0f

    private fun calculateCanvasSize(ratio: CanvasRatio): Pair<Int, Int> {
        val w = originalWidth
        val h = originalHeight
        
        if (ratio == CanvasRatio.RATIO_ORIGINAL) {
            return Pair(w, h)
        }

        val targetRatio = ratio.widthRatio / ratio.heightRatio
        
        // We want to keep the max dimension roughly the same to maintain high resolution
        val maxDim = maxOf(w, h)
        
        return if (targetRatio <= 1f) { // Vertical or Square
            val targetH = maxDim
            val targetW = (targetH * targetRatio).toInt()
            Pair(targetW, targetH)
        } else { // Horizontal
            val targetW = maxDim
            val targetH = (targetW / targetRatio).toInt()
            Pair(targetW, targetH)
        }
    }

    fun setCanvasRatio(ratio: CanvasRatio) {
        if (_state.value.selectedRatio == ratio) return
        
        _state.value = _state.value.copy(selectedRatio = ratio)
        val (targetW, targetH) = calculateCanvasSize(ratio)
        sourceWidth = targetW
        sourceHeight = targetH
        reanchorForeground()
        
        refreshBackground()
    }

    private fun refreshBackground() {
        val current = _state.value
        when {
            current.selectedColor != null -> applyColorBackground(current.selectedColor)
            current.selectedGradientPreset != null -> applyGradientBackground(current.selectedGradientPreset)
            current.selectedPresetStyle != null -> applyPresetBackground(current.selectedPresetStyle)
            current.selectedImage != null -> applyImageBackground(current.selectedImage)
        }
    }

    private fun cancelBackgroundRenderJob() {
        backgroundRenderJob?.cancel()
        backgroundRenderJob = null
    }

    private fun nextBackgroundRenderGeneration(): Long {
        backgroundRenderGeneration += 1
        return backgroundRenderGeneration
    }

    private fun calculateInitialForegroundScale(foreground: Bitmap?): Float {
        if (foreground == null || sourceWidth <= 0 || sourceHeight <= 0) return 1f
        return 1f
    }

    private fun calculateInitialOffsetY(foreground: Bitmap?, scale: Float): Float {
        return 0f
    }

    private fun reanchorForeground() {
        val current = _state.value
        val fg = current.foregroundBitmap ?: return
        val defaultScale = calculateInitialForegroundScale(fg)
        _state.value = current.copy(
            foregroundOffsetX = 0f,
            foregroundOffsetY = calculateInitialOffsetY(fg, defaultScale),
            foregroundScale = defaultScale,
            foregroundRotation = 0f,
            foregroundFlippedH = false,
            foregroundFlippedV = false
        )
    }

    fun setInitialBitmap(
        bitmap: Bitmap,
        initialGradientPresetId: String? = null
    ) {
        originalWidth = bitmap.width
        originalHeight = bitmap.height
        
        val (targetW, targetH) = calculateCanvasSize(_state.value.selectedRatio)
        sourceWidth = targetW
        sourceHeight = targetH
        val hasAlpha = checkHasAlpha(bitmap)

        if (hasAlpha) {
            val fg = ProcessorUtils.trimTransparentBounds(bitmap)
            val defaultScale = calculateInitialForegroundScale(fg)
            val offsetY = calculateInitialOffsetY(fg, defaultScale)
            _state.value = _state.value.copy(
                foregroundBitmap = fg,
                hasAlpha = true,
                isProcessing = false,
                foregroundOffsetX = 0f,
                foregroundOffsetY = offsetY,
                foregroundScale = defaultScale,
                foregroundRotation = 0f,
                foregroundFlippedH = false,
                foregroundFlippedV = false
            )
            applyInitialBackground(initialGradientPresetId)
        } else {
            val defaultScale = 1.0f
            _state.value = _state.value.copy(
                hasAlpha = true,
                isProcessing = true,
                foregroundOffsetX = 0f,
                foregroundOffsetY = 0f,
                foregroundScale = defaultScale,
                foregroundRotation = 0f,
                foregroundFlippedH = false,
                foregroundFlippedV = false
            )
            viewModelScope.launch(Dispatchers.Default) {
                var result = runCatching {
                    android.util.Log.d("BackgroundModeVM", "Using ML Kit Subject Segmentation")
                    backgroundRemoverRepository.getForegroundBitmap(bitmap).getOrThrow()
                }

                result.onSuccess { fgRaw ->
                    withContext(Dispatchers.Main) {
                        val fg = ProcessorUtils.trimTransparentBounds(fgRaw)
                        val defaultScale = calculateInitialForegroundScale(fg)
                        val offsetY = calculateInitialOffsetY(fg, defaultScale)
                        _state.value = _state.value.copy(
                            foregroundBitmap = fg,
                            isProcessing = false,
                            foregroundOffsetX = 0f,
                            foregroundOffsetY = offsetY,
                            foregroundScale = defaultScale,
                            foregroundRotation = 0f,
                            foregroundFlippedH = false,
                            foregroundFlippedV = false
                        )
                        applyInitialBackground(initialGradientPresetId)
                        triggerOverlay()
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
        val current = _state.value
        val defaultScale = calculateInitialForegroundScale(current.foregroundBitmap)
        val offsetY = calculateInitialOffsetY(current.foregroundBitmap, defaultScale)
        
        _state.value = current.copy(
            foregroundOffsetX = 0f,
            foregroundOffsetY = offsetY,
            foregroundScale = defaultScale,
            foregroundRotation = 0f,
            foregroundFlippedH = false,
            foregroundFlippedV = false
        )
    }

    fun toggleForegroundFlipHorizontal() {
        val current = _state.value
        _state.value = current.copy(foregroundFlippedH = !current.foregroundFlippedH)
    }

    fun toggleForegroundFlipVertical() {
        val current = _state.value
        _state.value = current.copy(foregroundFlippedV = !current.foregroundFlippedV)
    }

    fun updateForegroundTransform(zoomChange: Float, rotationChange: Float) {
        val current = _state.value
        val newScale = (current.foregroundScale * zoomChange).coerceIn(0.1f, 8f)
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

    fun setForegroundScale(scale: Float) {
        val current = _state.value
        val clampedScale = scale.coerceIn(0.1f, 8f)
        updateDragBounds(current.backgroundBitmap, current.foregroundBitmap, clampedScale)
        val clampedOffset = clampOffset(
            offsetX = current.foregroundOffsetX,
            offsetY = current.foregroundOffsetY
        )
        _state.value = current.copy(
            foregroundOffsetX = clampedOffset.first,
            foregroundOffsetY = clampedOffset.second,
            foregroundScale = clampedScale
        )
    }

    fun applyColorBackground(color: Int) {
        val w = sourceWidth
        val h = sourceHeight
        if (w <= 0 || h <= 0) return

        cancelBackgroundRenderJob()
        val generation = nextBackgroundRenderGeneration()
        _state.value = _state.value.copy(
            isProcessing = true,
            selectedColor = color,
            selectedGradientPreset = null,
            selectedImage = null,
            selectedPresetStyle = null
        )

        backgroundRenderJob = viewModelScope.launch {
            val bg = withContext(Dispatchers.Default) {
                ProcessorUtils.createColorBitmap(w, h, color)
            }
            if (generation == backgroundRenderGeneration) {
                updateBackgroundBitmap(bg)
            }
        }
    }

    private fun applyInitialBackground(initialGradientPresetId: String?) {
        val preset = initialGradientPresetId?.let { id ->
            BackgroundGradientPresets.modernPresets.firstOrNull { it.id == id }
        }
        if (preset != null) {
            _state.value = _state.value.copy(currentTab = BackgroundTab.GRADIENT)
            applyGradientBackground(preset)
        } else {
            applyColorBackground(android.graphics.Color.WHITE)
        }
    }

    fun applyGradientBackground(preset: BackgroundGradientPreset) {
        val w = sourceWidth
        val h = sourceHeight
        if (w <= 0 || h <= 0) return

        cancelBackgroundRenderJob()
        val generation = nextBackgroundRenderGeneration()
        _state.value = _state.value.copy(
            isProcessing = true,
            selectedGradientPreset = preset,
            selectedColor = null,
            selectedImage = null,
            selectedPresetStyle = null
        )

        backgroundRenderJob = viewModelScope.launch {
            val bg = withContext(Dispatchers.Default) {
                GradientBackgroundRenderer.createGradientBitmap(w, h, preset)
            }
            if (generation == backgroundRenderGeneration) {
                updateBackgroundBitmap(bg)
            }
        }
    }

    fun applyImageBackground(bgImage: Bitmap) {
        cancelBackgroundRenderJob()
        val generation = nextBackgroundRenderGeneration()
        _state.value = _state.value.copy(
            isProcessing = true,
            selectedImage = bgImage,
            selectedColor = null,
            selectedGradientPreset = null,
            selectedPresetStyle = null
        )

        backgroundRenderJob = viewModelScope.launch {
            val w = sourceWidth
            val h = sourceHeight
            val fg = _state.value.foregroundBitmap
            if (w <= 0 || h <= 0 || fg == null) {
                if (generation == backgroundRenderGeneration) {
                    _state.value = _state.value.copy(isProcessing = false)
                }
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
            if (generation == backgroundRenderGeneration) {
                updateBackgroundBitmap(bg)
            }
        }
    }

    fun applyPresetBackground(style: com.thgiang.image.core.model.PresetStyle) {
        val w = sourceWidth
        val h = sourceHeight
        if (w <= 0 || h <= 0) return

        cancelBackgroundRenderJob()
        val generation = nextBackgroundRenderGeneration()
        _state.value = _state.value.copy(
            isProcessing = true,
            selectedPresetStyle = style,
            selectedColor = null,
            selectedGradientPreset = null,
            selectedImage = null
        )

        backgroundRenderJob = viewModelScope.launch {
            val bg = withContext(Dispatchers.Default) {
                com.thgiang.image.core.util.processors.PresetRenderer.createPresetBitmap(w, h, style)
            }
            if (generation == backgroundRenderGeneration) {
                updateBackgroundBitmap(bg)
            }
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
            rotationDegrees = s.foregroundRotation,
            flipHorizontal = s.foregroundFlippedH,
            flipVertical = s.foregroundFlippedV
        )
    }

    fun triggerOverlay() {
        _state.value = _state.value.copy(showOverlay = true)
        overlayJob?.cancel()
        overlayJob = viewModelScope.launch {
            delay(2000)
            _state.value = _state.value.copy(showOverlay = false)
        }
    }
}
