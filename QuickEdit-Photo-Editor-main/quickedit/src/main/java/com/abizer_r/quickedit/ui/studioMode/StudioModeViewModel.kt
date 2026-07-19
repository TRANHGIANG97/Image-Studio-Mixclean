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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.sqrt
import android.util.Log

@HiltViewModel
class StudioModeViewModel @Inject constructor(
    private val backgroundRemoverRepository: BackgroundRemoverRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private var processingJob: Job? = null
    private var intensityDebounceJob: Job? = null

    /** Monotonically increasing generation — each new render path increments it.
     *  Jobs check at completion: if their captured gen != current, result is stale. */
    private var currentRenderGen = 0L

    companion object {
        private const val PREVIEW_PIXELS = 500_000
    }

    enum class StudioEffect {
        NONE,
        BLUR,
        BLUR_SUBJECT,
        PORTRAIT,
        CLEAN,
        DARKEN
    }

    data class StudioModeState(
        val processedBitmap: Bitmap? = null,
        val currentEffect: StudioEffect = StudioEffect.NONE,
        val isProcessing: Boolean = false,
        val error: String? = null,
        val intensities: Map<StudioEffect, Float> = emptyMap()
    ) {
        val intensity: Float
            get() = intensities[currentEffect] ?: 0f
    }

    data class StudioOption(
        val effect: StudioEffect,
        val title: String,
        val icon: androidx.compose.ui.graphics.vector.ImageVector
    )

    private val _state = MutableStateFlow(StudioModeState())
    val state: StateFlow<StudioModeState> = _state.asStateFlow()

    // Full-resolution assets (used only for export)
    private var originalBitmap: Bitmap? = null
    private var foregroundBitmap: Bitmap? = null

    // Preview-resolution
    private var previewBitmap: Bitmap? = null
    private var previewForeground: Bitmap? = null

    private fun computePreviewSize(bitmap: Bitmap): Pair<Int, Int> {
        val pixels = bitmap.width.toLong() * bitmap.height
        if (pixels <= PREVIEW_PIXELS) return Pair(bitmap.width, bitmap.height)
        val scale = sqrt(PREVIEW_PIXELS.toFloat() / pixels)
        return Pair((bitmap.width * scale).toInt().coerceAtLeast(1),
                    (bitmap.height * scale).toInt().coerceAtLeast(1))
    }

    fun setInitialBitmap(bitmap: Bitmap) {
        originalBitmap = bitmap

        // Downscale to ~0.5MP for preview
        val (pw, ph) = computePreviewSize(bitmap)
        previewBitmap = if (pw == bitmap.width && ph == bitmap.height) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, pw, ph, true)
        }

        _state.value = _state.value.copy(processedBitmap = bitmap)

        // Extract foreground at full resolution, then create preview foreground
        viewModelScope.launch(Dispatchers.Default) {
            foregroundBitmap = ImageEffectProcessor.extractForeground(
                context = context,
                bitmap = bitmap,
                backgroundRemoverRepository = backgroundRemoverRepository
            )
            // Downscale foreground to preview resolution
            foregroundBitmap?.let { fg ->
                val prev = previewBitmap
                if (prev != null && (fg.width != prev.width || fg.height != prev.height)) {
                    previewForeground = Bitmap.createScaledBitmap(fg, prev.width, prev.height, true)
                } else {
                    previewForeground = fg
                }
            }
            // Trigger a render if an effect was already selected when foreground arrives
            val currentEffect = _state.value.currentEffect
            if (currentEffect != StudioEffect.NONE) {
                applyEffect(currentEffect)
            }
        }
    }

    fun updateIntensity(value: Float) {
        val effect = _state.value.currentEffect
        if (effect == StudioEffect.NONE) return

        val clamped = value.coerceIn(0f, 1f)
        val updatedMap = _state.value.intensities.toMutableMap().apply {
            put(effect, clamped)
        }
        _state.value = _state.value.copy(intensities = updatedMap)

        intensityDebounceJob?.cancel()
        intensityDebounceJob = viewModelScope.launch {
            delay(80)
            intensityDebounceJob = null

            processingJob?.cancel()
            currentRenderGen++
            val myGen = currentRenderGen
            processingJob = viewModelScope.launch {
                val t0 = System.nanoTime()
                val original = originalBitmap ?: return@launch
                val fg = foregroundBitmap
                val currentMap = _state.value.intensities
                val result = try {
                    withContext(Dispatchers.Default) {
                        computeCumulativeEffect(previewBitmap ?: original, previewForeground ?: fg, currentMap)
                    }
                } catch (error: kotlinx.coroutines.CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    Log.e("StudioModeViewModel", "Failed to update cumulative effect", error)
                    null
                }
                if (myGen != currentRenderGen) {
                    result?.let { if (it !== previewBitmap && it !== original && !it.isRecycled) it.recycle() }
                    return@launch
                }
                val elapsedMs = (System.nanoTime() - t0) / 1_000_000f
                Log.d("StudioBenchmark", "Cumulative effects update: ${"%.2f".format(elapsedMs)}ms")
                _state.value = _state.value.copy(
                    processedBitmap = result ?: previewBitmap ?: original,
                    isProcessing = false
                )
            }
        }
    }

    fun applyEffect(effect: StudioEffect) {
        intensityDebounceJob?.cancel()
        val original = originalBitmap ?: return

        _state.value = _state.value.copy(
            currentEffect = effect
        )

        if (effect == StudioEffect.NONE) {
            processingJob?.cancel()
            _state.value = _state.value.copy(
                processedBitmap = previewBitmap ?: original,
                isProcessing = false
            )
            return
        }

        val shouldShowLoader = foregroundBitmap == null
        _state.value = _state.value.copy(
            isProcessing = shouldShowLoader
        )

        processingJob?.cancel()
        currentRenderGen++
        val myGen = currentRenderGen
        processingJob = viewModelScope.launch {
            val fg = foregroundBitmap
            val result = try {
                withContext(Dispatchers.Default) {
                    computeCumulativeEffect(
                        previewBitmap ?: original,
                        previewForeground ?: fg,
                        _state.value.intensities,
                    )
                }
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Throwable) {
                Log.e("StudioModeViewModel", "Failed to apply cumulative effect", error)
                null
            }
            if (myGen != currentRenderGen) {
                result?.let { if (it !== previewBitmap && it !== original && !it.isRecycled) it.recycle() }
                return@launch
            }
            if (result != null) {
                _state.value = _state.value.copy(processedBitmap = result, isProcessing = false)
            } else {
                _state.value = _state.value.copy(
                    isProcessing = false,
                    error = context.getString(com.abizer_r.quickedit.R.string.error_apply_effect)
                )
            }
        }
    }

    /**
     * Compute cumulative effect combining all non-zero active effects in the defined pipeline order.
     */
    private suspend fun computeCumulativeEffect(
        src: Bitmap,
        foreground: Bitmap?,
        intensities: Map<StudioEffect, Float>
    ): Bitmap? {
        val cleanIntensity = intensities[StudioEffect.CLEAN] ?: 0f
        val portraitIntensity = intensities[StudioEffect.PORTRAIT] ?: 0f
        val darkenIntensity = intensities[StudioEffect.DARKEN] ?: 0f
        val subjectBlurIntensity = intensities[StudioEffect.BLUR_SUBJECT] ?: 0f
        val blurIntensity = intensities[StudioEffect.BLUR] ?: 0f

        val activeFg = foreground
        var current = src
        var isCurrentNew = false

        if (activeFg != null) {
            // ── Background Effects ──
            // 1. Clean background
            if (cleanIntensity > 0f) {
                val next = ImageEffectProcessor.applyCleanCached(current, activeFg, cleanIntensity)
                if (next != null && next !== current) {
                    if (isCurrentNew && !current.isRecycled && current !== src) {
                        current.recycle()
                    }
                    current = next
                    isCurrentNew = true
                }
            }

            // 2. Portrait (Background Blur)
            if (portraitIntensity > 0f) {
                val next = ImageEffectProcessor.applyPortraitCached(
                    current, activeFg, blurRadius = portraitIntensity * 25f, darkenAlpha = 0f, vignette = false
                )
                if (next != null && next !== current) {
                    if (isCurrentNew && !current.isRecycled && current !== src) {
                        current.recycle()
                    }
                    current = next
                    isCurrentNew = true
                }
            }

            // 3. Darken background
            if (darkenIntensity > 0f) {
                val next = ImageEffectProcessor.applyDarkenCached(current, activeFg, darkenIntensity, vignette = true)
                if (next != null && next !== current) {
                    if (isCurrentNew && !current.isRecycled && current !== src) {
                        current.recycle()
                    }
                    current = next
                    isCurrentNew = true
                }
            }

            // ── Foreground / Subject Effects ──
            // 4. Blur subject (foreground)
            if (subjectBlurIntensity > 0f) {
                val blurredFg = ImageEffectProcessor.applySubjectBlur(activeFg, subjectBlurIntensity * 25f)
                if (blurredFg != null) {
                    val next = ImageEffectProcessor.applyPortraitCached(
                        current, blurredFg, blurRadius = 0f, darkenAlpha = 0f, vignette = false
                    )
                    if (!blurredFg.isRecycled) blurredFg.recycle()
                    if (next != null && next !== current) {
                        if (isCurrentNew && !current.isRecycled && current !== src) {
                            current.recycle()
                        }
                        current = next
                        isCurrentNew = true
                    }
                }
            }

            // ── Whole Image Effects ──
            // 5. Blur whole image
            if (blurIntensity > 0f) {
                val next = ImageEffectProcessor.applyBlur(current, blurIntensity * 25f)
                if (next != null && next !== current) {
                    if (isCurrentNew && !current.isRecycled && current !== src) {
                        current.recycle()
                    }
                    current = next
                    isCurrentNew = true
                }
            }

        } else {
            // Fallback path when foreground is null (no segmentation mask)
            if (cleanIntensity > 0f) {
                val next = ImageEffectProcessor.applyClean(context, current, cleanIntensity, backgroundRemoverRepository)
                if (next != null && next !== current) {
                    if (isCurrentNew && !current.isRecycled && current !== src) {
                        current.recycle()
                    }
                    current = next
                    isCurrentNew = true
                }
            }

            if (portraitIntensity > 0f) {
                val next = ImageEffectProcessor.applyPortrait(
                    context, current, portraitIntensity * 25f, 0f, false, backgroundRemoverRepository
                )
                if (next != null && next !== current) {
                    if (isCurrentNew && !current.isRecycled && current !== src) {
                        current.recycle()
                    }
                    current = next
                    isCurrentNew = true
                }
            }

            if (darkenIntensity > 0f) {
                val next = ImageEffectProcessor.applyDarken(context, current, darkenIntensity, true, backgroundRemoverRepository)
                if (next != null && next !== current) {
                    if (isCurrentNew && !current.isRecycled && current !== src) {
                        current.recycle()
                    }
                    current = next
                    isCurrentNew = true
                }
            }

            if (subjectBlurIntensity > 0f) {
                val next = ImageEffectProcessor.applyBlur(current, subjectBlurIntensity * 25f)
                if (next != null && next !== current) {
                    if (isCurrentNew && !current.isRecycled && current !== src) {
                        current.recycle()
                    }
                    current = next
                    isCurrentNew = true
                }
            }

            if (blurIntensity > 0f) {
                val next = ImageEffectProcessor.applyBlur(current, blurIntensity * 25f)
                if (next != null && next !== current) {
                    if (isCurrentNew && !current.isRecycled && current !== src) {
                        current.recycle()
                    }
                    current = next
                    isCurrentNew = true
                }
            }
        }

        return current
    }

    /** Render final full-resolution result when user taps Check/Done. */
    suspend fun renderFinalForExport(): Bitmap? {
        intensityDebounceJob?.cancel()
        intensityDebounceJob = null
        processingJob?.cancel()
        processingJob?.join()
        currentRenderGen++
        return withContext(Dispatchers.Default) {
            val original = originalBitmap ?: return@withContext null
            val fg = foregroundBitmap
            computeCumulativeEffect(original, fg, _state.value.intensities)
        }
    }

    override fun onCleared() {
        super.onCleared()
        processingJob?.cancel()
        intensityDebounceJob?.cancel()
        previewBitmap?.let { if (it !== originalBitmap && !it.isRecycled) it.recycle() }
        previewForeground?.let { if (it !== foregroundBitmap && !it.isRecycled) it.recycle() }
        originalBitmap = null
        foregroundBitmap = null
    }
}
