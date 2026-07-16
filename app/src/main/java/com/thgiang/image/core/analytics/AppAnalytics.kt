package com.thgiang.image.core.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.thgiang.image.core.ad.EngagementPrefs
import com.thgiang.image.core.ad.NewUserAdPolicy

object AppAnalytics {

    fun log(context: Context, event: String, params: Map<String, Any?> = emptyMap()) {
        runCatching {
            val bundle = Bundle()
            params.forEach { (key, value) ->
                when (value) {
                    null -> Unit
                    is String -> bundle.putString(key, value)
                    is Long -> bundle.putLong(key, value)
                    is Int -> bundle.putInt(key, value)
                    is Double -> bundle.putDouble(key, value)
                    is Boolean -> bundle.putBoolean(key, value)
                    is Float -> bundle.putDouble(key, value.toDouble())
                }
            }
            FirebaseAnalytics.getInstance(context).logEvent(event, bundle)
            SessionQualityTracker.onUserEngagement()
        }
    }

    fun onAppSessionStart(context: Context) {
        val prefs = context.getSharedPreferences(EngagementPrefs.PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val firstOpenAt = prefs.getLong(EngagementPrefs.KEY_FIRST_OPEN_AT, 0L)
        val isFirstSession = firstOpenAt == 0L

        if (isFirstSession) {
            prefs.edit().putLong(EngagementPrefs.KEY_FIRST_OPEN_AT, now).apply()
        } else if (!prefs.getBoolean(EngagementPrefs.KEY_DAY1_LOGGED, false)) {
            val hoursSinceFirst = (now - firstOpenAt) / 3_600_000.0
            if (hoursSinceFirst in 24.0..48.0) {
                log(context, "day_1_return")
                prefs.edit().putBoolean(EngagementPrefs.KEY_DAY1_LOGGED, true).apply()
            }
        }

        log(
            context,
            "app_session_start",
            mapOf("is_first_session" to isFirstSession)
        )
    }

    fun onSelectImage(context: Context, source: String) {
        context.getSharedPreferences(EngagementPrefs.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(EngagementPrefs.KEY_HAS_SELECTED_IMAGE, true)
            .apply()
        NewUserAdPolicy.clearSuspiciousSession(context)
        log(context, "select_image", mapOf("source" to source))
    }

    fun onBatchImagesSelected(context: Context, count: Int) {
        if (count <= 0) return
        onSelectImage(context, "batch_picker")
        log(context, "batch_images_selected", mapOf("count" to count))
    }

    fun onBatchRemoveStart(context: Context, count: Int) {
        log(context, "batch_remove_start", mapOf("count" to count))
        onRemoveBgStart(context)
    }

    fun onBatchSave(context: Context, count: Int) {
        log(context, "batch_save", mapOf("count" to count))
        onSaveImage(context, "batch_remove")
    }

    fun onStudioOpened(context: Context, templateId: String) {
        log(context, "studio_opened", mapOf("template_id" to templateId))
        onEditorOpened(context, "studio")
    }

    fun onAdBypassed(context: Context, adType: String, reason: String) {
        log(context, "ad_bypassed", mapOf("ad_type" to adType, "reason" to reason))
    }

    fun onEditorOpened(context: Context, source: String) {
        log(context, "editor_opened", mapOf("source" to source))
    }

    fun onSaveImage(context: Context, tool: String) {
        log(context, "save_image", mapOf("tool" to tool))
    }

    fun onRemoveBgStart(context: Context) {
        log(context, "remove_bg_start")
    }

    fun onRemoveBgSuccess(context: Context) {
        log(context, "remove_bg_success")
    }

    fun setInstallAttribution(context: Context, referrer: String?) {
        if (referrer.isNullOrBlank()) return
        runCatching {
            val analytics = FirebaseAnalytics.getInstance(context)
            analytics.setUserProperty("install_referrer_present", "true")
            val hasGclid = referrer.contains("gclid=", ignoreCase = true)
            analytics.setUserProperty("has_gclid", hasGclid.toString())

            val params = parseReferrerParams(referrer)
            val utmSource = params["utm_source"]?.takeIf { it.isNotBlank() }
            val utmMedium = params["utm_medium"]?.takeIf { it.isNotBlank() }
            val utmCampaign = params["utm_campaign"]?.takeIf { it.isNotBlank() }
            utmSource?.let { analytics.setUserProperty("utm_source", it) }
            utmMedium?.let { analytics.setUserProperty("utm_medium", it) }
            utmCampaign?.let { analytics.setUserProperty("utm_campaign", it) }

            val isYoutubeReferrer = referrer.contains("youtube", ignoreCase = true) ||
                utmSource.equals("youtube", ignoreCase = true)
            analytics.setUserProperty("youtube_referrer", isYoutubeReferrer.toString())

            log(
                context,
                "install_referrer_captured",
                mapOf(
                    "has_gclid" to hasGclid,
                    "referrer_length" to referrer.length,
                    "utm_source" to (utmSource ?: ""),
                    "utm_medium" to (utmMedium ?: ""),
                    "youtube_referrer" to isYoutubeReferrer
                )
            )
        }
    }

    private fun parseReferrerParams(referrer: String): Map<String, String> {
        return referrer.split("&").mapNotNull { part ->
            val kv = part.split("=", limit = 2)
            if (kv.size == 2) kv[0].lowercase() to kv[1] else null
        }.toMap()
    }

    fun setIntegrityResult(context: Context, passed: Boolean) {
        context.getSharedPreferences(EngagementPrefs.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(EngagementPrefs.KEY_INTEGRITY_CHECKED, true)
            .putBoolean(EngagementPrefs.KEY_INTEGRITY_PASSED, passed)
            .apply()
        log(context, "integrity_check", mapOf("passed" to passed))
    }
}
