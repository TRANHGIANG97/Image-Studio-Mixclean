package com.thgiang.image.core.diagnostics

import android.graphics.Bitmap
import com.google.firebase.crashlytics.FirebaseCrashlytics

object ImageProcessingCrashReporter {
    private val crashlytics: FirebaseCrashlytics
        get() = FirebaseCrashlytics.getInstance()

    fun setActiveTool(tool: String) {
        crashlytics.setCustomKey("active_tool", tool)
    }

    fun setBitmapInfo(prefix: String, bitmap: Bitmap?) {
        if (bitmap == null) {
            crashlytics.setCustomKey("${prefix}_bitmap_present", false)
            return
        }
        crashlytics.setCustomKey("${prefix}_bitmap_present", true)
        crashlytics.setCustomKey("${prefix}_bitmap_width", bitmap.width)
        crashlytics.setCustomKey("${prefix}_bitmap_height", bitmap.height)
        crashlytics.setCustomKey("${prefix}_bitmap_has_alpha", bitmap.hasAlpha())
        crashlytics.setCustomKey("${prefix}_bitmap_config", bitmap.config?.name ?: "unknown")
    }

    fun setRemoveBgRoute(
        isAutoRemove: Boolean,
        hasFace: Boolean? = null,
        remover: String? = null
    ) {
        crashlytics.setCustomKey("is_auto_remove", isAutoRemove)
        hasFace?.let { crashlytics.setCustomKey("has_detectable_face", it) }
        remover?.let { crashlytics.setCustomKey("background_remover", it) }
    }

    fun setStage(stage: String) {
        crashlytics.setCustomKey("processing_stage", stage)
    }

    fun setDuration(stage: String, durationMs: Long) {
        crashlytics.setCustomKey("${stage}_duration_ms", durationMs)
    }

    fun recordNonFatal(throwable: Throwable, stage: String) {
        crashlytics.setCustomKey("failure_stage", stage)
        crashlytics.recordException(throwable)
    }

    fun clearImageProcessingKeys() {
        crashlytics.setCustomKey("active_tool", "idle")
        crashlytics.setCustomKey("is_auto_remove", false)
        crashlytics.setCustomKey("background_remover", "none")
        crashlytics.setCustomKey("failure_stage", "none")
    }
}
