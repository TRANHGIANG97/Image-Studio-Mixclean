package com.thgiang.image.core.analytics

import android.content.Context

object SessionQualityTracker {
    private var sessionStartMs = 0L
    private var engagementCount = 0
    private var screenViewCount = 0

    fun onSessionStart() {
        sessionStartMs = System.currentTimeMillis()
        engagementCount = 0
        screenViewCount = 0
    }

    fun onUserEngagement() {
        engagementCount++
    }

    fun onScreenView() {
        screenViewCount++
    }

    fun scoreAndLog(context: Context) {
        if (sessionStartMs == 0L) return
        val durationSec = ((System.currentTimeMillis() - sessionStartMs) / 1000).coerceAtLeast(0)
        val suspicious = durationSec < 10 && engagementCount == 0
        if (suspicious) {
            com.thgiang.image.core.ad.NewUserAdPolicy.setSuspiciousSession(context, true)
        } else if (engagementCount > 0) {
            com.thgiang.image.core.ad.NewUserAdPolicy.clearSuspiciousSession(context)
        }
        AppAnalytics.log(
            context,
            "session_quality",
            mapOf(
                "duration_sec" to durationSec,
                "engagement_count" to engagementCount,
                "screen_views" to screenViewCount,
                "suspicious" to suspicious
            )
        )
        sessionStartMs = 0L
    }
}
