package com.thgiang.image.core.domain.settings
import kotlinx.coroutines.flow.Flow

data class UserPreferences(
    val isDarkMode: Boolean = false,
    val selectedLanguage: String = "system",
    /** "standard" | "pro" (Premium) */
    val preferredRemovalQuality: String = "standard"
)

interface UserPreferencesRepository {
    val preferences: Flow<UserPreferences>
    suspend fun setDarkMode(enabled: Boolean)
    suspend fun setLanguage(languageCode: String)
    suspend fun setPreferredRemovalQuality(quality: String)
}





