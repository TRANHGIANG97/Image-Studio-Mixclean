package com.thgiang.image.app.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thgiang.image.core.ad.AppOpenAdManager
import com.thgiang.image.core.ad.InterstitialAdManager
import com.thgiang.image.core.ad.RewardedAdManager
import com.thgiang.image.core.domain.settings.ReviewPromptDecision
import com.thgiang.image.core.domain.settings.UserPreferencesRepository
import com.thgiang.image.feature.premium.PremiumFeatureFlags
import com.thgiang.image.feature.premium.domain.PremiumRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class BatchAdState {
    data object Idle : BatchAdState()
    data object Loading : BatchAdState()
    data object Ready : BatchAdState()
    data object Watched : BatchAdState()
    data class Error(val message: String) : BatchAdState()
}

data class AppUiState(
    val isDarkMode: Boolean = false,
    val selectedLanguage: String = "system",
    val isPremium: Boolean = false,
    val preferredRemovalQuality: String = "standard",
    val isHomePreviewEnabled: Boolean = false,
    val batchUris: List<android.net.Uri> = emptyList()
)

@HiltViewModel
class AppViewModel @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
    private val premiumRepository: PremiumRepository,
    private val interstitialAdManager: InterstitialAdManager,
    private val appOpenAdManager: AppOpenAdManager,
    private val rewardedAdManager: RewardedAdManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()
    private val _selectedLanguage = MutableStateFlow("system")
    val selectedLanguage: StateFlow<String> = _selectedLanguage.asStateFlow()

    private val _batchAdState = MutableStateFlow<BatchAdState>(BatchAdState.Idle)
    val batchAdState: StateFlow<BatchAdState> = _batchAdState.asStateFlow()

    private val _batchAdWatchCount = MutableStateFlow(0)
    val batchAdWatchCount: StateFlow<Int> = _batchAdWatchCount.asStateFlow()

    private val _premiumLimitBlockedTemplate = MutableStateFlow<com.thgiang.image.studio.model.StudioThemeplate?>(null)
    val premiumLimitBlockedTemplate: StateFlow<com.thgiang.image.studio.model.StudioThemeplate?> = _premiumLimitBlockedTemplate.asStateFlow()

    private val _isCheckingPremiumLimit = MutableStateFlow(false)
    val isCheckingPremiumLimit: StateFlow<Boolean> = _isCheckingPremiumLimit.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesRepository.preferences.collectLatest { prefs ->
                _uiState.value = _uiState.value.copy(
                    isDarkMode = prefs.isDarkMode,
                    selectedLanguage = prefs.selectedLanguage,
                    preferredRemovalQuality = prefs.preferredRemovalQuality,
                    isHomePreviewEnabled = prefs.isHomePreviewEnabled
                )
                _selectedLanguage.value = prefs.selectedLanguage
            }
        }
        viewModelScope.launch {
            premiumRepository.isPremium.collect { isPremium ->
                _uiState.value = _uiState.value.copy(isPremium = isPremium)
                interstitialAdManager.isPremiumUser = isPremium
                appOpenAdManager.isPremiumUser = isPremium
            }
        }
        // Pre-load rewarded ad for batch flow
        preloadRewardedAd()
    }

    private fun preloadRewardedAd() {
        loadAdWithTimeout()
    }

    @JvmName("setDarkModeEnabled")
    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setDarkMode(enabled)
        }
    }

    fun setLanguage(languageCode: String) {
        viewModelScope.launch {
            preferencesRepository.setLanguage(languageCode)
        }
    }

    fun setPreferredRemovalQuality(quality: String) {
        viewModelScope.launch {
            preferencesRepository.setPreferredRemovalQuality(quality)
        }
        _uiState.value = _uiState.value.copy(preferredRemovalQuality = quality)
    }

    fun setHomePreviewEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setHomePreviewEnabled(enabled)
        }
    }

    fun recordSuccessfulSave(onShowPrompt: () -> Unit = {}) {
        viewModelScope.launch {
            val decision = preferencesRepository.recordSuccessfulSave()
            if (decision is ReviewPromptDecision.ShowPrompt) {
                onShowPrompt()
            }
        }
    }

    fun markReviewAccepted() {
        viewModelScope.launch {
            preferencesRepository.markReviewAccepted()
        }
    }

    fun markReviewDeclined() {
        viewModelScope.launch {
            preferencesRepository.markReviewDeclined()
        }
    }

    fun disableReviewPromptForever() {
        viewModelScope.launch {
            preferencesRepository.disableReviewPromptForever()
        }
    }

    fun setBatchUris(uris: List<android.net.Uri>) {
        _uiState.value = _uiState.value.copy(batchUris = uris)
    }

    fun requestBatchAccess() {
        _batchAdWatchCount.value = 0
        when (_batchAdState.value) {
            is BatchAdState.Ready, is BatchAdState.Loading -> { }
            else -> loadAdWithTimeout()
        }
    }

    private fun loadAdWithTimeout() {
        _batchAdState.value = BatchAdState.Loading
        rewardedAdManager.loadAd(
            onLoaded = { _batchAdState.value = BatchAdState.Ready },
            onFailed = { _batchAdState.value = BatchAdState.Error(it) }
        )
        viewModelScope.launch {
            delay(10_000L)
            if (_batchAdState.value is BatchAdState.Loading) {
                _batchAdState.value = BatchAdState.Error("timeout")
            }
        }
    }

    fun watchAdForBatch(activity: android.app.Activity, onAllWatched: () -> Unit) {
        rewardedAdManager.showAd(
            activity = activity,
            onRewardReceived = {
                val newCount = _batchAdWatchCount.value + 1
                _batchAdWatchCount.value = newCount
                _batchAdState.value = BatchAdState.Watched
            },
            onAdClosed = {
                if (_batchAdWatchCount.value >= 1) {
                    onAllWatched()
                    _batchAdWatchCount.value = 0
                    _batchAdState.value = BatchAdState.Idle
                } else {
                    loadAdWithTimeout()
                }
            },
            onFailedToShow = {
                _batchAdState.value = BatchAdState.Error(it)
            }
        )
    }

    fun resetBatchAdState() {
        _batchAdState.value = BatchAdState.Idle
        _batchAdWatchCount.value = 0
    }

    fun isAdDismissedRecently(): Boolean {
        return appOpenAdManager.wasAdDismissedRecently()
    }

    private var pendingEditorThemeplate: com.thgiang.image.studio.model.StudioThemeplate? = null

    fun setPendingEditorThemeplate(themeplate: com.thgiang.image.studio.model.StudioThemeplate) {
        pendingEditorThemeplate = themeplate
    }

    fun consumePendingEditorThemeplate(id: String): com.thgiang.image.studio.model.StudioThemeplate? {
        val pending = pendingEditorThemeplate
        return if (pending != null && pending.id == id) {
            pendingEditorThemeplate = null
            pending
        } else {
            null
        }
    }

    fun selectTemplate(themeplate: com.thgiang.image.studio.model.StudioThemeplate, onAllowed: () -> Unit) {
        // Premium monetization off → no daily limit / paywall.
        if (!PremiumFeatureFlags.enabled ||
            _uiState.value.isPremium ||
            !themeplate.isPremium
        ) {
            onAllowed()
            return
        }

        _isCheckingPremiumLimit.value = true
        viewModelScope.launch {
            try {
                val today = getTodayDateString()
                val result = preferencesRepository.checkPremiumTemplateLimit(themeplate.id, today)
                if (result is com.thgiang.image.core.domain.settings.PremiumLimitResult.Allowed) {
                    onAllowed()
                } else {
                    _premiumLimitBlockedTemplate.value = themeplate
                }
            } catch (e: Exception) {
                onAllowed()
            } finally {
                _isCheckingPremiumLimit.value = false
            }
        }
    }

    fun dismissPremiumLimitDialog() {
        _premiumLimitBlockedTemplate.value = null
    }

    fun watchAdForPremiumSlot(activity: android.app.Activity, templateId: String, onGranted: () -> Unit) {
        _batchAdState.value = BatchAdState.Loading
        rewardedAdManager.loadAd(
            onLoaded = {
                _batchAdState.value = BatchAdState.Ready
                rewardedAdManager.showAd(
                    activity = activity,
                    onRewardReceived = {
                        viewModelScope.launch {
                            val today = getTodayDateString()
                            preferencesRepository.grantExtraPremiumSlot(templateId, today)
                            onGranted()
                        }
                    },
                    onAdClosed = {
                        _batchAdState.value = BatchAdState.Idle
                    },
                    onFailedToShow = {
                        _batchAdState.value = BatchAdState.Error(it)
                    }
                )
            },
            onFailed = {
                _batchAdState.value = BatchAdState.Error(it)
            }
        )
    }

    private fun getTodayDateString(): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        return sdf.format(java.util.Date())
    }
}
