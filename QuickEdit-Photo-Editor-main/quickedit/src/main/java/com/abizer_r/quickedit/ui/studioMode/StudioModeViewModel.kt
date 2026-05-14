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
    private var blurCacheJob: Job? = null
    private var subjectBlurCacheJob: Job? = null

    /** Monotonically increasing generation — each new render path increments it.
     *  Jobs check at completion: if their captured gen != current, result is stale. */
    private var currentRenderGen = 0L

    companion object {
        private const val MAX_LEVELS = 100
        private const val PREVIEW_PIXELS = 500_000 // ~0.5MP → 100×0.5MP×4 ≈ 200MB cache
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
        val intensity: Float = 0.5f
    )

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

    // Preview-resolution for instant cache
    private var previewBitmap: Bitmap? = null
    private var previewForeground: Bitmap? = null

    private var lastAppliedEffect: StudioEffect? = null
    private var lastAppliedIntensity: Float = -1f

    // 100-level background blur cache at preview resolution
    private var blurCache: Array<Bitmap?> = arrayOfNulls(MAX_LEVELS)
    private val blurLevels = IntArray(MAX_LEVELS) { i ->
        ((i * 25f / (MAX_LEVELS - 1).toFloat()) + 0.5f).toInt().coerceAtMost(25)
    }
    private var cacheReadyFor: Bitmap? = null

    // 100-level subject blur cache at preview resolution
    private var subjectBlurCache: Array<Bitmap?> = arrayOfNulls(MAX_LEVELS)
    private var subjectCacheReadyFor: Bitmap? = null

    private fun computePreviewSize(bitmap: Bitmap): Pair<Int, Int> {
        val pixels = bitmap.width.toLong() * bitmap.height
        if (pixels <= PREVIEW_PIXELS) return Pair(bitmap.width, bitmap.height)
        val scale = sqrt(PREVIEW_PIXELS.toFloat() / pixels)
        return Pair((bitmap.width * scale).toInt().coerceAtLeast(1),
                    (bitmap.height * scale).toInt().coerceAtLeast(1))
    }

    private fun releaseBlurCache() {
        blurCacheJob?.cancel()
        blurCacheJob = null
        for (i in blurCache.indices) {
            blurCache[i]?.let { if (!it.isRecycled) it.recycle() }
            blurCache[i] = null
        }
        cacheReadyFor = null
        releaseSubjectBlurCache()
    }

    private fun releaseSubjectBlurCache() {
        subjectBlurCacheJob?.cancel()
        subjectBlurCacheJob = null
        for (i in subjectBlurCache.indices) {
            subjectBlurCache[i]?.let { if (!it.isRecycled) it.recycle() }
            subjectBlurCache[i] = null
        }
        subjectCacheReadyFor = null
    }

    fun setInitialBitmap(bitmap: Bitmap) {
        releaseBlurCache()
        originalBitmap = bitmap

        // Downscale to ~0.5MP for preview cache
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
            // Build cache if a cacheable effect was already selected when foreground arrives
            val currentEffect = _state.value.currentEffect
            if (foregroundBitmap != null) {
                when (currentEffect) {
                    StudioEffect.PORTRAIT, StudioEffect.BLUR -> buildBlurCache()
                    StudioEffect.BLUR_SUBJECT -> buildSubjectBlurCache()
                    else -> {}
                }
            }
        }
    }

    fun updateIntensity(value: Float) {
        val effect = _state.value.currentEffect
        val canUseBgCache = cacheReadyFor != null &&
                (effect == StudioEffect.PORTRAIT || effect == StudioEffect.BLUR)
        val canUseSubjectCache = subjectCacheReadyFor != null && effect == StudioEffect.BLUR_SUBJECT
        val canUseCache = canUseBgCache || canUseSubjectCache

        val effectiveValue = if (canUseCache) {
            intensityToLevel(value) / (MAX_LEVELS - 1).toFloat()
        } else {
            value
        }
        val clamped = effectiveValue.coerceIn(0f, 1f)
        _state.value = _state.value.copy(intensity = clamped)

        if (clamped == lastAppliedIntensity && lastAppliedEffect == effect) return
        lastAppliedIntensity = clamped
        lastAppliedEffect = effect

        // ── Fast path: use cached preview results ──────────────────────────────
        if (canUseCache) {
            intensityDebounceJob?.cancel()
            processingJob?.cancel()
            currentRenderGen++
            val myGen = currentRenderGen
            processingJob = viewModelScope.launch {
                val t0 = System.nanoTime()
                val result = withContext(Dispatchers.Default) {
                    when (effect) {
                        StudioEffect.PORTRAIT -> {
                            val bg = blurCache[intensityToLevel(clamped)] ?: return@withContext null
                            val pf = previewForeground ?: return@withContext null
                            ImageEffectProcessor.applyPortraitCached(
                                bg, pf, blurRadius = 0f, darkenAlpha = 0f, vignette = false
                            )
                        }
                        StudioEffect.BLUR -> {
                            val bg = blurCache[intensityToLevel(clamped)] ?: return@withContext null
                            bg.copy(Bitmap.Config.ARGB_8888, false)
                        }
                        StudioEffect.BLUR_SUBJECT -> {
                            val blurredFg = subjectBlurCache[intensityToLevel(clamped)] ?: return@withContext null
                            val bg = previewBitmap ?: return@withContext null
                            ImageEffectProcessor.applyPortraitCached(
                                bg, blurredFg, blurRadius = 0f, darkenAlpha = 0f, vignette = false
                            )
                        }
                        else -> null
                    }
                }
                // Discard if a newer generation already superseded this
                if (myGen != currentRenderGen) {
                    result?.let { if (!it.isRecycled) it.recycle() }
                    return@launch
                }
                val elapsedMs = (System.nanoTime() - t0) / 1_000_000f
                Log.d("StudioBenchmark", "${effect} ${(clamped * 100).toInt()}% cache: ${"%.2f".format(elapsedMs)}ms")
                _state.value = _state.value.copy(
                    processedBitmap = result ?: previewBitmap,
                    isProcessing = false
                )
            }
            return
        }

        // ── Debounce path: effects without cache ──────────────────────────────
        intensityDebounceJob?.cancel()
        intensityDebounceJob = viewModelScope.launch {
            delay(80)
            intensityDebounceJob = null
            val effect = _state.value.currentEffect
            val original = originalBitmap ?: return@launch
            if (effect == StudioEffect.NONE) return@launch
            if (effect != lastAppliedEffect || clamped != lastAppliedIntensity) {
                lastAppliedEffect = effect
                lastAppliedIntensity = clamped
            }
            _state.value = _state.value.copy(isProcessing = false)

            processingJob?.cancel()
            currentRenderGen++
            val myGen = currentRenderGen
            processingJob = viewModelScope.launch {
                val t0 = System.nanoTime()
                val fg = foregroundBitmap
                val result = withContext(Dispatchers.Default) {
                    computeEffect(effect, original, fg, clamped)
                }
                if (myGen != currentRenderGen) {
                    result?.let { if (!it.isRecycled) it.recycle() }
                    return@launch
                }
                val elapsedMs = (System.nanoTime() - t0) / 1_000_000f
                Log.d("StudioBenchmark", "${effect} ${(clamped * 100).toInt()}% debounce: ${"%.2f".format(elapsedMs)}ms")
                _state.value = _state.value.copy(
                    processedBitmap = result ?: previewBitmap ?: original,
                    isProcessing = false
                )
            }
        }
    }

    fun applyEffect(effect: StudioEffect) {
        applyEffect(effect, _state.value.intensity, isIntensityUpdate = false)
    }

    private fun applyEffect(effect: StudioEffect, intensity: Float, isIntensityUpdate: Boolean) {
        intensityDebounceJob?.cancel()
        val original = originalBitmap ?: return
        if (effect == lastAppliedEffect && intensity == lastAppliedIntensity) return

        // Release cache when leaving a cached effect for a non-cached one
        val leavingCached = lastAppliedEffect == StudioEffect.PORTRAIT ||
                lastAppliedEffect == StudioEffect.BLUR ||
                lastAppliedEffect == StudioEffect.BLUR_SUBJECT
        val enteringCached = effect == StudioEffect.PORTRAIT ||
                effect == StudioEffect.BLUR ||
                effect == StudioEffect.BLUR_SUBJECT
        if (leavingCached && !enteringCached) {
            releaseBlurCache()
        }

        if (effect == StudioEffect.NONE) {
            processingJob?.cancel()
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

        val shouldShowLoader = !isIntensityUpdate && foregroundBitmap == null
        _state.value = _state.value.copy(
            isProcessing = shouldShowLoader,
            currentEffect = effect
        )

        // Trigger cache build for effects that use it
        when (effect) {
            StudioEffect.PORTRAIT, StudioEffect.BLUR -> {
                if (cacheReadyFor !== previewBitmap) buildBlurCache()
            }
            StudioEffect.BLUR_SUBJECT -> {
                if (subjectCacheReadyFor !== previewForeground) buildSubjectBlurCache()
            }
            else -> {}
        }

        processingJob?.cancel()
        currentRenderGen++
        val myGen = currentRenderGen
        processingJob = viewModelScope.launch {
            val fg = foregroundBitmap
            val result = withContext(Dispatchers.Default) {
                computeEffect(effect, original, fg, intensity)
            }
            if (myGen != currentRenderGen) {
                result?.let { if (!it.isRecycled) it.recycle() }
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
     * Compute effect at preview resolution for display.
     * All fallbacks use previewBitmap/previewForeground to keep rendering fast
     * and avoid size inconsistency with the cache path.
     */
    private suspend fun computeEffect(
        effect: StudioEffect,
        original: Bitmap,
        foreground: Bitmap?,
        intensity: Float
    ): Bitmap? {
        val mappedBlurRadius = (intensity * 25f).coerceIn(0f, 25f)
        val src = previewBitmap ?: original
        val pf = previewForeground ?: foreground

        return if (foreground != null && pf != null) {
            when (effect) {
                StudioEffect.BLUR -> {
                    val cacheIdx = intensityToLevel(intensity)
                    val bg = blurCache[cacheIdx]
                    if (bg != null && cacheReadyFor === previewBitmap) {
                        bg.copy(Bitmap.Config.ARGB_8888, false)
                    } else {
                        ImageEffectProcessor.applyBlur(src, intensity * 25f)
                    }
                }
                StudioEffect.PORTRAIT -> {
                    val cacheIdx = intensityToLevel(intensity)
                    val bg = blurCache[cacheIdx]
                    if (bg != null && cacheReadyFor === previewBitmap) {
                        ImageEffectProcessor.applyPortraitCached(
                            bg, pf, blurRadius = 0f, darkenAlpha = 0f, vignette = false
                        )
                    } else {
                        ImageEffectProcessor.applyPortraitCached(
                            src, pf, blurRadius = mappedBlurRadius, darkenAlpha = 0f, vignette = false
                        )
                    }
                }
                StudioEffect.BLUR_SUBJECT -> {
                    val blurredFg = ImageEffectProcessor.applySubjectBlur(pf, mappedBlurRadius)
                    if (blurredFg != null) {
                        ImageEffectProcessor.applyPortraitCached(
                            src, blurredFg, blurRadius = 0f, darkenAlpha = 0f, vignette = false
                        ).also { if (!blurredFg.isRecycled) blurredFg.recycle() }
                    } else {
                        src
                    }
                }
                StudioEffect.CLEAN -> ImageEffectProcessor.applyCleanCached(src, pf, intensity)
                StudioEffect.DARKEN -> ImageEffectProcessor.applyDarkenCached(src, pf, intensity, vignette = true)
                StudioEffect.NONE -> src
            }
        } else {
            when (effect) {
                StudioEffect.BLUR -> {
                    val cacheIdx = intensityToLevel(intensity)
                    val bg = blurCache[cacheIdx]
                    if (bg != null && cacheReadyFor === previewBitmap) {
                        bg.copy(Bitmap.Config.ARGB_8888, false)
                    } else {
                        ImageEffectProcessor.applyBlur(src, intensity * 25f)
                    }
                }
                StudioEffect.BLUR_SUBJECT -> ImageEffectProcessor.applyBlur(src, mappedBlurRadius)
                StudioEffect.PORTRAIT -> ImageEffectProcessor.applyPortrait(
                    context, src, mappedBlurRadius, 0f, false, backgroundRemoverRepository
                )
                StudioEffect.CLEAN -> ImageEffectProcessor.applyClean(context, src, intensity, backgroundRemoverRepository)
                StudioEffect.DARKEN -> ImageEffectProcessor.applyDarken(context, src, intensity, true, backgroundRemoverRepository)
                StudioEffect.NONE -> src
            }
        }
    }

    private fun intensityToLevel(value: Float): Int {
        return (value * (MAX_LEVELS - 1) + 0.5f).toInt().coerceIn(0, MAX_LEVELS - 1)
    }

    private fun buildBlurCache() {
        val src = previewBitmap ?: return
        if (cacheReadyFor === src) return
        releaseBlurCache()

        blurCacheJob = viewModelScope.launch {
            withContext(Dispatchers.Default) {
                for (i in blurLevels.indices) {
                    if (!isActive) break
                    blurCache[i] = ImageEffectProcessor.applyBlur(src, blurLevels[i].toFloat())
                }
            }
            cacheReadyFor = if (isActive) src else null
        }
    }

    private fun buildSubjectBlurCache() {
        val src = previewForeground ?: return
        if (subjectCacheReadyFor === src) return
        releaseSubjectBlurCache()

        subjectBlurCacheJob = viewModelScope.launch {
            withContext(Dispatchers.Default) {
                for (i in blurLevels.indices) {
                    if (!isActive) break
                    subjectBlurCache[i] = ImageEffectProcessor.applySubjectBlur(src, blurLevels[i].toFloat())
                }
            }
            subjectCacheReadyFor = if (isActive) src else null
        }
    }

    /** Render final full-resolution result when user taps Check/Done. */
    suspend fun renderFinalForExport(): Bitmap? = withContext(Dispatchers.Default) {
        val original = originalBitmap ?: return@withContext null
        val fg = foregroundBitmap ?: return@withContext null
        val intensity = _state.value.intensity.coerceIn(0f, 1f)
        when (_state.value.currentEffect) {
            StudioEffect.PORTRAIT -> ImageEffectProcessor.applyPortraitCached(
                original, fg, blurRadius = intensity * 25f, darkenAlpha = 0f, vignette = false
            )
            StudioEffect.BLUR -> ImageEffectProcessor.applyBlur(original, intensity * 25f)
            StudioEffect.BLUR_SUBJECT -> {
                val blurredFg = ImageEffectProcessor.applySubjectBlur(fg, intensity * 25f)
                if (blurredFg != null) {
                    ImageEffectProcessor.applyPortraitCached(
                        original, blurredFg, blurRadius = 0f, darkenAlpha = 0f, vignette = false
                    ).also { if (!blurredFg.isRecycled) blurredFg.recycle() }
                } else null
            }
            StudioEffect.CLEAN -> ImageEffectProcessor.applyCleanCached(original, fg, intensity)
            StudioEffect.DARKEN -> ImageEffectProcessor.applyDarkenCached(original, fg, intensity, vignette = true)
            StudioEffect.NONE -> original
        }
    }

    override fun onCleared() {
        super.onCleared()
        releaseBlurCache()
        releaseSubjectBlurCache()
        previewBitmap?.let { if (it !== originalBitmap && !it.isRecycled) it.recycle() }
        previewForeground?.let { if (it !== foregroundBitmap && !it.isRecycled) it.recycle() }
        originalBitmap = null
        foregroundBitmap = null
    }
}
