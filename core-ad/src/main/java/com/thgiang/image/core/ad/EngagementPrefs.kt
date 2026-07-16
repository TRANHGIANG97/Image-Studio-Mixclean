package com.thgiang.image.core.ad

object EngagementPrefs {
    const val PREFS_NAME = "app_engagement_prefs"
    const val KEY_FIRST_OPEN_AT = "first_open_at"
    const val KEY_DAY1_LOGGED = "day_1_return_logged"
    const val KEY_HAS_SELECTED_IMAGE = "has_selected_image"
    const val KEY_INTEGRITY_PASSED = "integrity_passed"
    const val KEY_INTEGRITY_CHECKED = "integrity_checked"
    const val KEY_REWARDED_COUNT_DAY = "rewarded_count_day"
    const val KEY_REWARDED_COUNT = "rewarded_count"
    const val KEY_SUSPICIOUS_SESSION = "suspicious_session"
    const val FIRST_24H_MS = 86_400_000L
    const val MAX_REWARDED_PER_DAY = 3
}
