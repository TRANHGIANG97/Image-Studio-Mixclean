package com.thgiang.image.feature.editor.ui

import android.content.Context
import com.google.firebase.analytics.FirebaseAnalytics
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.abizer_r.quickedit.ui.SharedEditorViewModel
import com.abizer_r.quickedit.utils.other.bitmap.BitmapStatus
import com.abizer_r.quickedit.utils.other.bitmap.BitmapUtils
import com.thgiang.image.core.data.backgroundremove.BackgroundRemoverRepository
import com.thgiang.image.core.diagnostics.ImageProcessingCrashReporter
import com.thgiang.image.core.domain.settings.ReviewPromptDecision
import com.thgiang.image.core.domain.settings.UserPreferencesRepository
import com.thgiang.image.core.util.processors.ProcessorUtils
import com.thgiang.image.feature.editor.model.DraftManager
import com.thgiang.image.feature.editor.model.LayerSnapshot
import com.thgiang.image.feature.editor.model.ProjectSnapshot
import com.thgiang.image.feature.premium.domain.PremiumRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject

private const val TAG = "QuickEditVM"
private const val QUICK_TOOLS_REMOVE_BG_TIMEOUT_MS = 45_000L

sealed interface QuickEditUiEvent {
    data class ShowToast(val message: String) : QuickEditUiEvent
    object ShowSelfieFallbackWarning : QuickEditUiEvent
    data class NavigateToBackground(val presetId: String) : QuickEditUiEvent
    data class NavigateToBorder(val presetId: String) : QuickEditUiEvent
    data class NavigateToTool(val tool: String) : QuickEditUiEvent
}

data class QuickEditUiState(
    val bitmapLoaded: Boolean = false,
    val loadError: Boolean = false,
    val isRemovingBg: Boolean = false,
    val autoRemoveStatusMessage: String? = null,
    val showReviewPrompt: Boolean = false,
    val showSaveSuccessScreen: Boolean = false,
    val lastSavedImageUri: Uri? = null,
    val lastSavedImageFile: File? = null
)

@HiltViewModel
class QuickEditViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val draftManager: DraftManager,
    private val backgroundRemoverRepository: BackgroundRemoverRepository,
    private val premiumRepository: PremiumRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(QuickEditUiState())
    val uiState: StateFlow<QuickEditUiState> = _uiState.asStateFlow()

    private val _uiEvent = Channel<QuickEditUiEvent>(Channel.BUFFERED)
    val uiEvent = _uiEvent.receiveAsFlow()

    val isPremium: StateFlow<Boolean> = premiumRepository.isPremium
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    fun loadInitialBitmap(
        uri: Uri?,
        draftId: String?,
        sharedViewModel: SharedEditorViewModel
    ) {
        if (_uiState.value.bitmapLoaded) return

        viewModelScope.launch {
            if (draftId != null) {
                val snapshot = withContext(Dispatchers.IO) {
                    draftManager.getSnapshot(draftId)
                }
                if (snapshot != null && snapshot.layers.isNotEmpty()) {
                    val layer = snapshot.layers.first()
                    // Sử dụng filesDir (bộ nhớ vĩnh viễn) — đồng bộ với DraftManager
                    val draftDir = File(File(context.filesDir, "drafts"), draftId)
                    val bitmapFile = File(draftDir, layer.cacheFileName)
                    if (bitmapFile.exists()) {
                        val bitmap = withContext(Dispatchers.IO) {
                            android.graphics.BitmapFactory.decodeFile(bitmapFile.absolutePath)
                        }
                        if (bitmap != null) {
                            sharedViewModel.addBitmapToStack(
                                bitmap = bitmap,
                                triggerRecomposition = true
                            )
                            _uiState.update { it.copy(bitmapLoaded = true) }
                            return@launch
                        }
                    }
                    // File draft bị thiếu → thông báo lỗi thân thiện thay vì màn hình trắng
                    android.util.Log.w(TAG, "Draft file not found for draftId=$draftId, file=${layer.cacheFileName}")
                    _uiState.update { it.copy(loadError = true) }
                    return@launch
                }
            }

            if (uri != null) {
                BitmapUtils.getScaledBitmap(context, uri).collect { status ->
                    when (status) {
                        is BitmapStatus.Success -> {
                            sharedViewModel.addBitmapToStack(
                                bitmap = status.scaledBitmap.copy(Bitmap.Config.ARGB_8888, false),
                                triggerRecomposition = true
                            )
                            _uiState.update { it.copy(bitmapLoaded = true) }
                        }
                        is BitmapStatus.Failed -> {
                            _uiState.update { it.copy(loadError = true) }
                        }
                        else -> { /* Processing */ }
                    }
                }
            }
        }
    }

    fun performBackgroundRemoval(
        sharedViewModel: SharedEditorViewModel,
        isAutoRemove: Boolean,
        initialBackgroundGradientPresetId: String?,
        initialBorderGradientPresetId: String?,
        targetTool: String?
    ) {
        viewModelScope.launch {
            val subjectRemover = backgroundRemoverRepository
            _uiState.update { it.copy(isRemovingBg = true, autoRemoveStatusMessage = "") }
            try {
                FirebaseAnalytics.getInstance(context).logEvent("remove_bg_start", null)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to log remove_bg_start event", e)
            }
            try {
                val bitmap = sharedViewModel.getCurrentBitmap()
                ImageProcessingCrashReporter.setActiveTool("quick_tools_remove_bg")
                ImageProcessingCrashReporter.setBitmapInfo("input", bitmap)
                ImageProcessingCrashReporter.setRemoveBgRoute(
                    isAutoRemove = isAutoRemove,
                    remover = "adaptive_hybrid"
                )
                Log.d(TAG, "removeBackground flow start bitmap=${bitmap.width}x${bitmap.height} hasAlpha=${bitmap.hasAlpha()}")

                val removeStartMs = SystemClock.elapsedRealtime()
                val result = runCatching {
                    withTimeout(QUICK_TOOLS_REMOVE_BG_TIMEOUT_MS) {
                        withContext(Dispatchers.Default) {
                            ImageProcessingCrashReporter.setStage("quick_tools_call_adaptive_hybrid")
                            val fg = subjectRemover.getForegroundBitmap(bitmap).getOrThrow()
                            ProcessorUtils.trimTransparentBounds(
                                fg.copy(Bitmap.Config.ARGB_8888, true)
                            ).also { it.setHasAlpha(true) }
                        }
                    }
                }.also {
                    ImageProcessingCrashReporter.setDuration(
                        stage = "remove_bg",
                        durationMs = SystemClock.elapsedRealtime() - removeStartMs
                    )
                }

                result.fold(
                    onSuccess = { resultBitmap ->
                        try {
                            FirebaseAnalytics.getInstance(context).logEvent("remove_bg_success", null)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to log remove_bg_success event", e)
                        }
                        ImageProcessingCrashReporter.setBitmapInfo("output", resultBitmap)
                        sharedViewModel.addBitmapToStack(
                            bitmap = resultBitmap,
                            triggerRecomposition = true,
                            addSafelyWithoutMultipleTriggers = false
                        )
                        sharedViewModel.triggerOverlay()
                        if (subjectRemover.consumeSelfieFallbackWarning()) {
                            _uiEvent.send(QuickEditUiEvent.ShowSelfieFallbackWarning)
                        }
                        if (isAutoRemove) {
                            if (!initialBackgroundGradientPresetId.isNullOrBlank()) {
                                _uiEvent.send(QuickEditUiEvent.NavigateToBackground(initialBackgroundGradientPresetId))
                            } else if (!initialBorderGradientPresetId.isNullOrBlank()) {
                                _uiEvent.send(QuickEditUiEvent.NavigateToBorder(initialBorderGradientPresetId))
                            } else if (!targetTool.isNullOrBlank()) {
                                _uiEvent.send(QuickEditUiEvent.NavigateToTool(targetTool))
                            }
                        }
                    },
                    onFailure = {
                        Log.e(TAG, "autoRemoveBackground: remover failed", it)
                        ImageProcessingCrashReporter.recordNonFatal(it, "quick_tools_remove_bg")
                        val failedMsg = context.getString(com.thgiang.image.R.string.bg_removal_failed)
                        _uiEvent.send(QuickEditUiEvent.ShowToast(failedMsg))
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "performBackgroundRemoval: failed", e)
                val failedMsg = context.getString(com.thgiang.image.R.string.bg_removal_failed)
                _uiEvent.send(QuickEditUiEvent.ShowToast(failedMsg))
            } finally {
                _uiState.update { it.copy(isRemovingBg = false, autoRemoveStatusMessage = null) }
            }
        }
    }

    fun saveDraft(bitmap: Bitmap, draftNamePrefix: String) {
        viewModelScope.launch {
            val draftName = "${draftNamePrefix}_${System.currentTimeMillis()}"
            val layerId = UUID.randomUUID().toString()
            val cacheFileName = "layer_$layerId.bin"

            val snapshot = ProjectSnapshot(
                selectedLayerIndex = 0,
                layers = listOf(
                    LayerSnapshot(
                        id = layerId,
                        type = "IMAGE",
                        cacheFileName = cacheFileName
                    )
                )
            )

            withContext(Dispatchers.IO) {
                try {
                    val id = draftManager.createDraft(draftName, snapshot)
                    val draftDir = draftManager.getDraftDir(id)
                    val bitmapFile = File(draftDir, cacheFileName)
                    FileOutputStream(bitmapFile).use { fos ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "saveDraft failed", e)
                }
            }
        }
    }

    fun onSaveSuccess(uri: Uri?, file: File?) {
        try {
            FirebaseAnalytics.getInstance(context).logEvent("save_image", null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log save_image event", e)
        }
        _uiState.update {
            it.copy(
                lastSavedImageUri = uri,
                lastSavedImageFile = file,
                showSaveSuccessScreen = true
            )
        }
        viewModelScope.launch {
            if (userPreferencesRepository.recordSuccessfulSave() is ReviewPromptDecision.ShowPrompt) {
                _uiState.update { it.copy(showReviewPrompt = true) }
            }
        }
    }

    fun dismissReviewPrompt() {
        _uiState.update { it.copy(showReviewPrompt = false) }
    }

    fun dismissSaveSuccess() {
        _uiState.update { it.copy(showSaveSuccessScreen = false) }
    }

    fun recordReviewAccepted() {
        viewModelScope.launch {
            userPreferencesRepository.markReviewAccepted()
            _uiState.update { it.copy(showReviewPrompt = false) }
        }
    }

    fun recordReviewDeclined() {
        viewModelScope.launch {
            userPreferencesRepository.markReviewDeclined()
            _uiState.update { it.copy(showReviewPrompt = false) }
        }
    }

    fun disableReviewPromptForever() {
        viewModelScope.launch {
            userPreferencesRepository.disableReviewPromptForever()
        }
    }
}
