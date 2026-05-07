package com.thgiang.image.core.data.settings
import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.thgiang.image.core.domain.settings.UserPreferences
import com.thgiang.image.core.domain.settings.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val DATASTORE_NAME = "user_preferences"
private val DARK_MODE_KEY = booleanPreferencesKey("dark_mode")
private val LANGUAGE_TAG_KEY = stringPreferencesKey("language_tag")
private val REMOVAL_QUALITY_KEY = stringPreferencesKey("removal_quality")

private val Context.dataStore by preferencesDataStore(
    name = DATASTORE_NAME
)

class DatastoreUserPreferencesRepository(
    private val context: Context
) : UserPreferencesRepository {

    override val preferences: Flow<UserPreferences> =
        context.dataStore.data.map { prefs: Preferences ->
            UserPreferences(
                isDarkMode = prefs[DARK_MODE_KEY] ?: false,
                selectedLanguage = prefs[LANGUAGE_TAG_KEY] ?: "system",
                preferredRemovalQuality = prefs[REMOVAL_QUALITY_KEY] ?: "standard"
            )
        }

    override suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[DARK_MODE_KEY] = enabled
        }
    }

    override suspend fun setLanguage(languageCode: String) {
        context.dataStore.edit { prefs ->
            prefs[LANGUAGE_TAG_KEY] = languageCode
        }
    }

    override suspend fun setPreferredRemovalQuality(quality: String) {
        context.dataStore.edit { prefs ->
            prefs[REMOVAL_QUALITY_KEY] = quality
        }
    }
}






