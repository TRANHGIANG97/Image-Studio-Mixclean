package com.abizer_r.quickedit.ui.removeBgMode

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

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

@HiltViewModel
class RemoveBgModeViewModel @Inject constructor(
    private val mlKitRemover: BackgroundRemoverRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    data class RemoveBgState(
        val originalBitmap: Bitmap? = null,
        val processedBitmap: Bitmap? = null,
        val isProcessing: Boolean = false,
        val error: String? = null,
        val showOverlay: Boolean = false
    )

    private val _state = MutableStateFlow(RemoveBgState())
    val state: StateFlow<RemoveBgState> = _state.asStateFlow()

    private var processingJob: Job? = null
    private var overlayJob: Job? = null

    private var cachedBitmap: Bitmap? = null

    fun setInitialBitmap(bitmap: Bitmap) {
        _state.value = _state.value.copy(
            originalBitmap = bitmap,
            processedBitmap = bitmap
        )
        processImage()
    }

    private fun processImage() {
        val original = _state.value.originalBitmap ?: return
        
        if (_state.value.processedBitmap != _state.value.originalBitmap && _state.value.error == null) {
            return
        }

        processingJob?.cancel()
        
        _state.value = _state.value.copy(
            isProcessing = true,
            error = null
        )

        processingJob = viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                runCatching {
                    getBgRemoved(original)
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
                            error = context.getString(R.string.error_apply_effect)
                        )
                    }
                },
                onFailure = {
                    _state.value = _state.value.copy(
                        isProcessing = false,
                        error = context.getString(R.string.error_apply_effect)
                    )
                }
            )
        }
    }

    private suspend fun getBgRemoved(bitmap: Bitmap): Bitmap? {
        if (cachedBitmap != null) return cachedBitmap
        val startTime = System.currentTimeMillis()
        val result = mlKitRemover.getForegroundBitmap(bitmap).getOrNull()
        val endTime = System.currentTimeMillis()
        android.util.Log.d("MLKitBenchmark", "MLKit background removal took ${endTime - startTime} ms")
        if (result != null) {
            cachedBitmap = result
        }
        return result
    }

    override fun onCleared() {
        super.onCleared()
        cachedBitmap?.let { if (!it.isRecycled) it.recycle() }
    }
}
