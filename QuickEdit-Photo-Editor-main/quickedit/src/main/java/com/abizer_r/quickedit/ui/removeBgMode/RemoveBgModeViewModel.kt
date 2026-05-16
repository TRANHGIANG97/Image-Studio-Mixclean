package com.abizer_r.quickedit.ui.removeBgMode

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.abizer_r.quickedit.backgroundremove.ModNetBackgroundRemoverRepository
import com.thgiang.image.core.data.backgroundremove.BackgroundRemoverRepository
import com.thgiang.image.core.util.processors.ProcessorUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import com.abizer_r.quickedit.R
import com.abizer_r.quickedit.utils.other.QuickToolsPortraitClassifier

@HiltViewModel
class RemoveBgModeViewModel @Inject constructor(
    private val mlKitRemover: BackgroundRemoverRepository,
    private val modNetRemover: ModNetBackgroundRemoverRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    enum class RemoveBgOption {
        AUTO,
        PORTRAIT,
        OBJECT
    }

    data class RemoveBgState(
        val originalBitmap: Bitmap? = null,
        val processedBitmap: Bitmap? = null,
        val currentOption: RemoveBgOption = RemoveBgOption.AUTO,
        val isProcessing: Boolean = false,
        val processingMessage: String? = null,
        val error: String? = null,
        val hasFace: Boolean? = null,
        val warningMessage: String? = null,
        val showOverlay: Boolean = false
    )

    private val _state = MutableStateFlow(RemoveBgState())
    val state: StateFlow<RemoveBgState> = _state.asStateFlow()

    private var processingJob: Job? = null
    private var overlayJob: Job? = null
    private val portraitClassifier = QuickToolsPortraitClassifier()

    // Caches to avoid re-processing if user clicks back and forth
    private var cachedPortraitBitmap: Bitmap? = null
    private var cachedObjectBitmap: Bitmap? = null

    fun setInitialBitmap(bitmap: Bitmap) {
        _state.value = _state.value.copy(
            originalBitmap = bitmap,
            processedBitmap = bitmap
        )
        // Automatically start processing with AUTO mode
        applyOption(RemoveBgOption.AUTO)
    }

    fun applyOption(option: RemoveBgOption) {
        val original = _state.value.originalBitmap ?: return
        
        // If same option, do nothing unless we have an error
        if (_state.value.currentOption == option && _state.value.processedBitmap != _state.value.originalBitmap && _state.value.error == null) {
            return
        }

        processingJob?.cancel()
        
        _state.value = _state.value.copy(
            currentOption = option,
            isProcessing = true,
            error = null,
            processingMessage = getProcessingMessage(option)
        )

        processingJob = viewModelScope.launch {
            ensureFaceDetected(original)
            
            val warning = when (option) {
                RemoveBgOption.PORTRAIT -> if (_state.value.hasFace == false) context.getString(R.string.remove_bg_portrait_no_face_warning) else null
                RemoveBgOption.OBJECT -> if (_state.value.hasFace == true) context.getString(R.string.remove_bg_object_has_face_warning) else null
                else -> null
            }
            
            _state.value = _state.value.copy(warningMessage = warning)

            val result = withContext(Dispatchers.Default) {
                runCatching {
                    when (option) {
                        RemoveBgOption.PORTRAIT -> getPortraitBgRemoved(original)
                        RemoveBgOption.OBJECT -> getObjectBgRemoved(original)
                        RemoveBgOption.AUTO -> {
                            val hasFace = portraitClassifier.hasDetectableFace(original).getOrDefault(false)
                            if (hasFace) {
                                getPortraitBgRemoved(original) ?: getObjectBgRemoved(original) // fallback
                            } else {
                                getObjectBgRemoved(original)
                            }
                        }
                    }
                }
            }

            result.fold(
                onSuccess = { fg ->
                    if (fg != null) {
                        val fgCopy = fg.copy(Bitmap.Config.ARGB_8888, true)
                        val finalResult = ProcessorUtils.trimTransparentBounds(fgCopy)
                        finalResult.setHasAlpha(true)
                        if (finalResult !== fgCopy && !fgCopy.isRecycled) {
                            fgCopy.recycle()
                        }
                        
                        _state.value = _state.value.copy(
                            processedBitmap = finalResult,
                            isProcessing = false,
                            processingMessage = null,
                            showOverlay = true
                        )

                        // Hide overlay after 2 seconds
                        overlayJob?.cancel()
                        overlayJob = viewModelScope.launch {
                            kotlinx.coroutines.delay(2000)
                            _state.value = _state.value.copy(showOverlay = false)
                        }
                    } else {
                        _state.value = _state.value.copy(
                            isProcessing = false,
                            processingMessage = null,
                            error = context.getString(R.string.error_apply_effect)
                        )
                    }
                },
                onFailure = {
                    _state.value = _state.value.copy(
                        isProcessing = false,
                        processingMessage = null,
                        error = context.getString(R.string.error_apply_effect)
                    )
                }
            )
        }
    }

    private suspend fun getPortraitBgRemoved(bitmap: Bitmap): Bitmap? {
        if (cachedPortraitBitmap != null) return cachedPortraitBitmap
        val result = modNetRemover.getForegroundBitmap(bitmap).getOrNull()
        if (result != null) {
            cachedPortraitBitmap = result
        }
        return result
    }

    private suspend fun getObjectBgRemoved(bitmap: Bitmap): Bitmap? {
        if (cachedObjectBitmap != null) return cachedObjectBitmap
        val result = mlKitRemover.getForegroundBitmap(bitmap).getOrNull()
        if (result != null) {
            cachedObjectBitmap = result
        }
        return result
    }

    private suspend fun ensureFaceDetected(bitmap: Bitmap) {
        if (_state.value.hasFace == null) {
            val hasFace = portraitClassifier.hasDetectableFace(bitmap).getOrDefault(false)
            _state.value = _state.value.copy(hasFace = hasFace)
        }
    }

    private fun getProcessingMessage(option: RemoveBgOption): String {
        return when (option) {
            RemoveBgOption.AUTO -> context.getString(R.string.remove_bg_processing_auto)
            RemoveBgOption.PORTRAIT -> context.getString(R.string.remove_bg_processing_portrait)
            RemoveBgOption.OBJECT -> context.getString(R.string.remove_bg_processing_object)
        }
    }

    override fun onCleared() {
        super.onCleared()
        cachedPortraitBitmap?.let { if (!it.isRecycled) it.recycle() }
        cachedObjectBitmap?.let { if (!it.isRecycled) it.recycle() }
    }
}
