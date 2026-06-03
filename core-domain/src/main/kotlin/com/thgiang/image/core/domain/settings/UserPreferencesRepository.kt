package com.thgiang.image.core.domain.settings
import kotlinx.coroutines.flow.Flow

sealed interface ReviewPromptDecision {
    data object None : ReviewPromptDecision
    data object ShowPrompt : ReviewPromptDecision
}

data class UserPreferences(
    val isDarkMode: Boolean = false,
    val selectedLanguage: String = "system",
    /** "standard" | "pro" (Premium) */
    val preferredRemovalQuality: String = "standard",
    val isHomePreviewEnabled: Boolean = false,
    val reviewSuccessfulSaveCount: Int = 0,
    val reviewPromptShownCount: Int = 0,
    val reviewPromptLastShownAtMillis: Long = 0L,
    val reviewPromptDisabled: Boolean = false,
    val reviewMarkedAsReviewed: Boolean = false
)

interface UserPreferencesRepository {
    val preferences: Flow<UserPreferences>
    suspend fun setDarkMode(enabled: Boolean)
    suspend fun setLanguage(languageCode: String)
    suspend fun setPreferredRemovalQuality(quality: String)
    suspend fun setHomePreviewEnabled(enabled: Boolean)
    suspend fun recordSuccessfulSave(nowMillis: Long = System.currentTimeMillis()): ReviewPromptDecision
    suspend fun markReviewAccepted()
    suspend fun markReviewDeclined()
}





