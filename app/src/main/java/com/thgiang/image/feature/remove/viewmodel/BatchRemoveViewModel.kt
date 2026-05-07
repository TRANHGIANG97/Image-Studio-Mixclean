package com.thgiang.image.feature.remove.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thgiang.image.core.data.save.CachedImage
import com.thgiang.image.core.data.save.ImageSaveRepository
import com.thgiang.image.core.domain.AppError
import com.thgiang.image.core.data.backgroundremove.BackgroundRemoverRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BatchRemoveViewModel @Inject constructor(
    private val backgroundRemoverRepository: BackgroundRemoverRepository,
    private val imageSave: ImageSaveRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BatchRemoveUiState())
    val uiState: StateFlow<BatchRemoveUiState> = _uiState.asStateFlow()

    private var processJob: Job? = null
    private var saveAllJob: Job? = null

    fun setInitialUris(uris: List<Uri>) {
        if (uris.isEmpty() || _uiState.value.selectedUris.isNotEmpty()) return
        _uiState.value = _uiState.value.copy(
            selectedUris = uris,
            progressPercent = 0,
            batchCompleted = 0,
            batchTotal = uris.size,
            snackbarEvent = null
        )
        onProcessBatchImages()
    }

    fun onProcessBatchImages() {
        if (_uiState.value.isProcessing) return
        val pendingUris = _uiState.value.selectedUris.drop(_uiState.value.processedUriCount)
        if (pendingUris.isEmpty()) return

        processJob?.cancel()
        _uiState.value = _uiState.value.copy(
            progressPercent = 0,
            batchCompleted = 0,
            batchTotal = pendingUris.size,
            isProcessing = true,
            snackbarEvent = null
        )

        processJob = viewModelScope.launch {
            val remover = backgroundRemoverRepository
            val mutableResults = _uiState.value.results.toMutableList()
            val total = pendingUris.size
            var processed = 0
            var processedUriCount = _uiState.value.processedUriCount

            for (uri in pendingUris) {
                if (!isActive) break

                val result = remover.removeBackground(uri)
                result.fold(
                    onSuccess = { output ->
                        val cacheResult = imageSave.cacheBitmap(output.foregroundToSave)
                        cacheResult.fold(
                            onSuccess = { displayUri ->
                                mutableResults.add(
                                    ProcessedResult(
                                        displayUri = displayUri,
                                        hasAlpha = output.foregroundToSave.hasAlpha()
                                    )
                                )
                                processed++
                                processedUriCount++
                                _uiState.value = _uiState.value.copy(
                                    results = mutableResults.toList(),
                                    processedUriCount = processedUriCount,
                                    batchCompleted = processed,
                                    progressPercent = (processed * 100) / total
                                )
                            },
                            onFailure = { e ->
                                _uiState.value = _uiState.value.copy(
                                    isProcessing = false,
                                    snackbarEvent = BatchRemoveSnackbarEvent.Text(AppError.from(e).userMessage),
                                    results = mutableResults.toList(),
                                    processedUriCount = processedUriCount,
                                    batchCompleted = processed
                                )
                                return@launch
                            }
                        )
                    },
                    onFailure = { e ->
                        _uiState.value = _uiState.value.copy(
                            isProcessing = false,
                            snackbarEvent = BatchRemoveSnackbarEvent.Text(AppError.from(e).userMessage),
                            results = mutableResults.toList(),
                            processedUriCount = processedUriCount,
                            batchCompleted = processed
                        )
                        return@launch
                    }
                )
            }

            _uiState.value = _uiState.value.copy(
                isProcessing = false,
                batchCompleted = processed,
                progressPercent = if (total > 0) (processed * 100) / total else 0
            )
        }
    }

    fun onCancelProcessing() {
        processJob?.cancel()
        _uiState.value = _uiState.value.copy(isProcessing = false)
    }

    fun onSaveAllClicked() {
        if (_uiState.value.results.isEmpty() || _uiState.value.isSavingAll) return

        saveAllJob?.cancel()
        saveAllJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSavingAll = true,
                saveAllDone = 0,
                saveAllTotal = _uiState.value.results.size
            )
            val items = _uiState.value.results.map { CachedImage(it.displayUri, it.hasAlpha) }
            val result = imageSave.saveAll(items, maxConcurrency = 2) { done, total ->
                _uiState.value = _uiState.value.copy(saveAllDone = done, saveAllTotal = total)
            }
            _uiState.value = _uiState.value.copy(isSavingAll = false)
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        snackbarEvent = BatchRemoveSnackbarEvent.SaveSuccess
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        snackbarEvent = BatchRemoveSnackbarEvent.Text(AppError.from(e).userMessage)
                    )
                }
            )
        }
    }

    fun removeResult(result: ProcessedResult) {
        val updated = _uiState.value.results.filter { it.displayUri != result.displayUri }
        _uiState.value = _uiState.value.copy(results = updated)
    }

    fun clearAllResults() {
        _uiState.value = _uiState.value.copy(results = emptyList(), selectedUris = emptyList(), processedUriCount = 0)
    }

    fun saveImage(result: ProcessedResult) {
        if (_uiState.value.isProcessing) return
        viewModelScope.launch {
            val saveResult = imageSave.saveCachedToGallery(CachedImage(result.displayUri, result.hasAlpha))
            _uiState.value = _uiState.value.copy(
                snackbarEvent = saveResult.fold(
                    onSuccess = { BatchRemoveSnackbarEvent.SaveSuccess },
                    onFailure = { BatchRemoveSnackbarEvent.Text(AppError.from(it).userMessage) }
                )
            )
        }
    }

    fun deleteImage(result: ProcessedResult) {
        removeResult(result)
    }

    fun onErrorConsumed() {
        _uiState.value = _uiState.value.copy(snackbarEvent = null)
    }

    override fun onCleared() {
        processJob?.cancel()
        saveAllJob?.cancel()
        super.onCleared()
    }
}
