package com.thgiang.image.studio.logging

import android.util.Log
import com.thgiang.image.core.domain.logging.AppLogger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default [AppLogger]: formats structured context into Logcat lines.
 * Production sinks (Crashlytics/Sentry) replace this at the Hilt binding
 * in [com.thgiang.image.studio.di.StudioModule] — call sites stay untouched.
 */
@Singleton
class LogcatAppLogger @Inject constructor() : AppLogger {

    override fun logNonFatal(throwable: Throwable, context: Map<String, String>) {
        Log.w(TAG, "non-fatal: ${throwable.message}${formatContext(context)}", throwable)
    }

    override fun logWarning(message: String, context: Map<String, String>) {
        Log.w(TAG, message + formatContext(context))
    }

    override fun logEvent(name: String, params: Map<String, String>) {
        Log.i(TAG, "event=$name${formatContext(params)}")
    }

    private fun formatContext(context: Map<String, String>): String =
        if (context.isEmpty()) {
            ""
        } else {
            context.entries.joinToString(prefix = " [", postfix = "]") { (key, value) -> "$key=$value" }
        }

    private companion object {
        const val TAG = "AppLogger"
    }
}
