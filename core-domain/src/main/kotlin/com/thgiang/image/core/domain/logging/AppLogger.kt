package com.thgiang.image.core.domain.logging

/**
 * Pluggable observability sink for defense paths (graceful skips, fallbacks, funnel events).
 *
 * Lives in core-domain (pure Kotlin) so any module can depend on it without pulling in
 * Android or vendor SDKs. The default binding logs to Logcat; a future
 * CrashlyticsAppLogger/SentryAppLogger can be swapped in at the Hilt binding site
 * without touching call sites.
 */
interface AppLogger {

    /** Reports a caught, non-crashing throwable (e.g. a layer skipped during mapping). */
    fun logNonFatal(throwable: Throwable, context: Map<String, String> = emptyMap())

    /** Reports a degraded-but-recovered situation without an associated throwable. */
    fun logWarning(message: String, context: Map<String, String> = emptyMap())

    /** Reports a structured analytics/diagnostic event (e.g. "template_parse_invalid"). */
    fun logEvent(name: String, params: Map<String, String> = emptyMap())
}
