package com.thgiang.image.core.ad

import android.content.Context

/**
 * Ad eligibility rules for new installs and low-trust devices.
 * Shared prefs are written by [com.thgiang.image.core.analytics.AppAnalytics] in the app module.
 */
object NewUserAdPolicy {

    /** Optional hook for the app module to log [ad_bypassed] to Firebase. */
    @Volatile
    var bypassListener: ((adType: String, reason: String) -> Unit)? = null

    fun isWithinFirst24h(context: Context): Boolean {
        val prefs = context.getSharedPreferences(EngagementPrefs.PREFS_NAME, Context.MODE_PRIVATE)
        val firstOpenAt = prefs.getLong(EngagementPrefs.KEY_FIRST_OPEN_AT, 0L)
        if (firstOpenAt == 0L) return true
        return System.currentTimeMillis() - firstOpenAt < EngagementPrefs.FIRST_24H_MS
    }

    fun hasSelectedImage(context: Context): Boolean {
        val prefs = context.getSharedPreferences(EngagementPrefs.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(EngagementPrefs.KEY_HAS_SELECTED_IMAGE, false)
    }

    fun isIntegrityTrusted(context: Context): Boolean {
        val prefs = context.getSharedPreferences(EngagementPrefs.PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(EngagementPrefs.KEY_INTEGRITY_CHECKED, false)) return true
        return prefs.getBoolean(EngagementPrefs.KEY_INTEGRITY_PASSED, true)
    }

    fun isSuspiciousSession(context: Context): Boolean {
        val prefs = context.getSharedPreferences(EngagementPrefs.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(EngagementPrefs.KEY_SUSPICIOUS_SESSION, false)
    }

    fun setSuspiciousSession(context: Context, suspicious: Boolean) {
        context.getSharedPreferences(EngagementPrefs.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(EngagementPrefs.KEY_SUSPICIOUS_SESSION, suspicious)
            .apply()
    }

    fun clearSuspiciousSession(context: Context) {
        setSuspiciousSession(context, false)
    }

    fun bypassReason(context: Context): String? = when {
        isWithinFirst24h(context) -> "first_24h"
        !hasSelectedImage(context) -> "no_image_selected"
        !isIntegrityTrusted(context) -> "integrity_failed"
        isSuspiciousSession(context) -> "suspicious_session"
        else -> null
    }

    fun shouldBypassAllAds(context: Context): Boolean = bypassReason(context) != null

    fun shouldBypassInterstitial(context: Context): Boolean = shouldBypassAllAds(context)

    fun shouldBypassBanner(context: Context): Boolean = shouldBypassAllAds(context)

    fun shouldPreloadAds(context: Context): Boolean = !shouldBypassAllAds(context)

    fun shouldBypassRewarded(context: Context): Boolean {
        if (shouldBypassAllAds(context)) return true
        return !canShowRewardedToday(context)
    }

    fun rewardedBypassReason(context: Context): String? {
        bypassReason(context)?.let { return it }
        if (!canShowRewardedToday(context)) return "daily_cap"
        return null
    }

    fun reportBypassIfNeeded(context: Context, adType: String) {
        val reason = when (adType) {
            "rewarded" -> rewardedBypassReason(context)
            else -> bypassReason(context)
        } ?: return
        bypassListener?.invoke(adType, reason)
    }

    fun recordRewardedShown(context: Context) {
        val prefs = context.getSharedPreferences(EngagementPrefs.PREFS_NAME, Context.MODE_PRIVATE)
        val today = dayKey()
        val storedDay = prefs.getInt(EngagementPrefs.KEY_REWARDED_COUNT_DAY, -1)
        val count = if (storedDay == today) {
            prefs.getInt(EngagementPrefs.KEY_REWARDED_COUNT, 0) + 1
        } else {
            1
        }
        prefs.edit()
            .putInt(EngagementPrefs.KEY_REWARDED_COUNT_DAY, today)
            .putInt(EngagementPrefs.KEY_REWARDED_COUNT, count)
            .apply()
    }

    private fun canShowRewardedToday(context: Context): Boolean {
        val prefs = context.getSharedPreferences(EngagementPrefs.PREFS_NAME, Context.MODE_PRIVATE)
        val today = dayKey()
        val storedDay = prefs.getInt(EngagementPrefs.KEY_REWARDED_COUNT_DAY, -1)
        val count = if (storedDay == today) prefs.getInt(EngagementPrefs.KEY_REWARDED_COUNT, 0) else 0
        return count < EngagementPrefs.MAX_REWARDED_PER_DAY
    }

    private fun dayKey(): Int = (System.currentTimeMillis() / 86_400_000L).toInt()
}
