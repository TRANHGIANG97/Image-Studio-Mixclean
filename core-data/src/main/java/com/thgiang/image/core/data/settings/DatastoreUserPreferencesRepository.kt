package com.thgiang.image.core.data.settings
import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.thgiang.image.core.domain.settings.UserPreferences
import com.thgiang.image.core.domain.settings.ReviewPromptDecision
import com.thgiang.image.core.domain.settings.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val DATASTORE_NAME = "user_preferences"
private val DARK_MODE_KEY = booleanPreferencesKey("dark_mode")
private val LANGUAGE_TAG_KEY = stringPreferencesKey("language_tag")
private val REMOVAL_QUALITY_KEY = stringPreferencesKey("removal_quality")
private val HOME_PREVIEW_ENABLED_KEY = booleanPreferencesKey("home_preview_enabled")
private val REVIEW_SUCCESSFUL_SAVE_COUNT_KEY = intPreferencesKey("review_successful_save_count")
private val REVIEW_PROMPT_SHOWN_COUNT_KEY = intPreferencesKey("review_prompt_shown_count")
private val REVIEW_PROMPT_LAST_SHOWN_AT_MILLIS_KEY = longPreferencesKey("review_prompt_last_shown_at_millis")
private val REVIEW_PROMPT_DISABLED_KEY = booleanPreferencesKey("review_prompt_disabled")
private val REVIEW_MARKED_AS_REVIEWED_KEY = booleanPreferencesKey("review_marked_as_reviewed")
private val LAST_PREMIUM_EDIT_DATE_KEY = stringPreferencesKey("last_premium_edit_date")
private val EDITED_PREMIUM_TEMPLATES_TODAY_KEY = stringPreferencesKey("edited_premium_templates_today")

private const val REVIEW_PROMPT_THRESHOLD = 3
private const val REVIEW_PROMPT_INTERVAL_MILLIS = 24L * 60L * 60L * 1000L

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
                preferredRemovalQuality = prefs[REMOVAL_QUALITY_KEY] ?: "standard",
                isHomePreviewEnabled = prefs[HOME_PREVIEW_ENABLED_KEY] ?: false,
                reviewSuccessfulSaveCount = prefs[REVIEW_SUCCESSFUL_SAVE_COUNT_KEY] ?: 0,
                reviewPromptShownCount = prefs[REVIEW_PROMPT_SHOWN_COUNT_KEY] ?: 0,
                reviewPromptLastShownAtMillis = prefs[REVIEW_PROMPT_LAST_SHOWN_AT_MILLIS_KEY] ?: 0L,
                reviewPromptDisabled = prefs[REVIEW_PROMPT_DISABLED_KEY] ?: false,
                reviewMarkedAsReviewed = prefs[REVIEW_MARKED_AS_REVIEWED_KEY] ?: false,
                lastPremiumEditDate = prefs[LAST_PREMIUM_EDIT_DATE_KEY] ?: "",
                editedPremiumTemplatesToday = prefs[EDITED_PREMIUM_TEMPLATES_TODAY_KEY] ?: ""
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

    override suspend fun setHomePreviewEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[HOME_PREVIEW_ENABLED_KEY] = enabled
        }
    }

    override suspend fun recordSuccessfulSave(nowMillis: Long): ReviewPromptDecision {
        var decision: ReviewPromptDecision = ReviewPromptDecision.None

        context.dataStore.edit { prefs ->
            val alreadyReviewed = prefs[REVIEW_MARKED_AS_REVIEWED_KEY] ?: false
            val promptDisabled = prefs[REVIEW_PROMPT_DISABLED_KEY] ?: false
            if (alreadyReviewed || promptDisabled) {
                return@edit
            }

            val saveCount = (prefs[REVIEW_SUCCESSFUL_SAVE_COUNT_KEY] ?: 0) + 1
            prefs[REVIEW_SUCCESSFUL_SAVE_COUNT_KEY] = saveCount

            val promptShownCount = prefs[REVIEW_PROMPT_SHOWN_COUNT_KEY] ?: 0
            val lastPromptAt = prefs[REVIEW_PROMPT_LAST_SHOWN_AT_MILLIS_KEY] ?: 0L

            val shouldPrompt = when (promptShownCount) {
                0 -> saveCount >= REVIEW_PROMPT_THRESHOLD
                1 -> saveCount >= REVIEW_PROMPT_THRESHOLD * 2 &&
                    nowMillis - lastPromptAt >= REVIEW_PROMPT_INTERVAL_MILLIS
                else -> false
            }

            if (shouldPrompt) {
                prefs[REVIEW_PROMPT_SHOWN_COUNT_KEY] = promptShownCount + 1
                prefs[REVIEW_PROMPT_LAST_SHOWN_AT_MILLIS_KEY] = nowMillis
                decision = ReviewPromptDecision.ShowPrompt
            }
        }

        return decision
    }

    override suspend fun markReviewAccepted() {
        context.dataStore.edit { prefs ->
            prefs[REVIEW_MARKED_AS_REVIEWED_KEY] = true
            prefs[REVIEW_PROMPT_DISABLED_KEY] = true
        }
    }

    override suspend fun markReviewDeclined() {
        context.dataStore.edit { prefs ->
            val shownCount = prefs[REVIEW_PROMPT_SHOWN_COUNT_KEY] ?: 0
            if (shownCount >= 2) {
                prefs[REVIEW_PROMPT_DISABLED_KEY] = true
            }
        }
    }

    override suspend fun checkPremiumTemplateLimit(
        templateId: String,
        todayDateString: String,
        limit: Int
    ): com.thgiang.image.core.domain.settings.PremiumLimitResult {
        var result: com.thgiang.image.core.domain.settings.PremiumLimitResult = 
            com.thgiang.image.core.domain.settings.PremiumLimitResult.Allowed

        context.dataStore.edit { prefs ->
            val lastDate = prefs[LAST_PREMIUM_EDIT_DATE_KEY] ?: ""
            var editedTemplatesStr = prefs[EDITED_PREMIUM_TEMPLATES_TODAY_KEY] ?: ""

            if (lastDate != todayDateString) {
                editedTemplatesStr = ""
                prefs[LAST_PREMIUM_EDIT_DATE_KEY] = todayDateString
            }

            val list = if (editedTemplatesStr.isBlank()) emptyList() else editedTemplatesStr.split(",")
            if (list.contains(templateId)) {
                result = com.thgiang.image.core.domain.settings.PremiumLimitResult.Allowed
            } else {
                if (list.size < limit) {
                    val newList = list + templateId
                    prefs[EDITED_PREMIUM_TEMPLATES_TODAY_KEY] = newList.joinToString(",")
                    result = com.thgiang.image.core.domain.settings.PremiumLimitResult.Allowed
                } else {
                    result = com.thgiang.image.core.domain.settings.PremiumLimitResult.Blocked(list.size)
                }
            }
        }

        return result
    }

    override suspend fun grantExtraPremiumSlot(templateId: String, todayDateString: String) {
        context.dataStore.edit { prefs ->
            val lastDate = prefs[LAST_PREMIUM_EDIT_DATE_KEY] ?: ""
            var editedTemplatesStr = prefs[EDITED_PREMIUM_TEMPLATES_TODAY_KEY] ?: ""

            if (lastDate != todayDateString) {
                editedTemplatesStr = ""
                prefs[LAST_PREMIUM_EDIT_DATE_KEY] = todayDateString
            }

            val list = if (editedTemplatesStr.isBlank()) emptyList() else editedTemplatesStr.split(",")
            if (!list.contains(templateId)) {
                val newList = list + templateId
                prefs[EDITED_PREMIUM_TEMPLATES_TODAY_KEY] = newList.joinToString(",")
            }
        }
    }
}






