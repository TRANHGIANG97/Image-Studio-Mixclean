package com.thgiang.image.core.util

import android.app.ActivityManager
import android.content.Context

object DeviceProfile {
    private const val MB = 1024L * 1024L

    fun isLowEnd(context: Context): Boolean {
        val cores = Runtime.getRuntime().availableProcessors()
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val info = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(info)
        val totalMemMb = info.totalMem / MB
        return cores <= 6 || totalMemMb in 1..4096
    }

    fun intensityDebounceMs(context: Context): Long = if (isLowEnd(context)) 50L else 16L

    fun previewDecodeMaxSide(context: Context): Int = if (isLowEnd(context)) 800 else 1600

    fun blurRequestSize(context: Context): Int = if (isLowEnd(context)) 400 else 600

    fun blurQuantizeStep(context: Context): Float = if (isLowEnd(context)) 3f else 1f
}
