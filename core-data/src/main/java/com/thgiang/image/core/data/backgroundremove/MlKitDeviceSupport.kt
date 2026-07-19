package com.thgiang.image.core.data.backgroundremove

import android.content.Context
import android.content.pm.ApplicationInfo
import android.app.ActivityManager
import android.os.Build

/**
 * Device / build-type rules for ML Kit segmentation.
 *
 * - Release APK ships ARM native libs only → x86-only devices are unsupported.
 * - Debug builds may include x86 for emulators; Subject Segmentation has no x86 native
 *   backend → force bundled Selfie on x86 emulators.
 */
object MlKitDeviceSupport {

    const val ERROR_DEVICE_UNSUPPORTED = "DEVICE_UNSUPPORTED"
    const val ERROR_SEGMENTATION_TIMEOUT = "SEGMENTATION_TIMEOUT"
    const val RELEASE_SEGMENTATION_TIMEOUT_MS = 8_000L
    /** Debug Subject/Selfie on real devices — avoid infinite GMS hang. */
    const val DEBUG_SEGMENTATION_TIMEOUT_MS = 90_000L
    /** Debug x86 emulator — Selfie is slow but should still finish. */
    const val DEBUG_X86_SEGMENTATION_TIMEOUT_MS = 120_000L
    /** x86 AVD with 1 CPU: keep ML work tiny — memoryClass is often overstated on emulator. */
    const val X86_EMULATOR_ML_CAP = 512

    fun resolveMaxDecodeSize(context: Context, devicePolicyCap: Int): Int {
        if (shouldForceSelfiePrimary(context)) {
            return minOf(devicePolicyCap, X86_EMULATOR_ML_CAP)
        }
        return devicePolicyCap
    }

    fun isDebugBuild(context: Context): Boolean =
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    fun isX86Emulator(): Boolean {
        val isEmulator = (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")) ||
            Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.startsWith("unknown") ||
            Build.HARDWARE.contains("goldfish") ||
            Build.HARDWARE.contains("ranchu") ||
            Build.MODEL.contains("google_sdk") ||
            Build.MODEL.contains("Emulator") ||
            Build.MODEL.contains("Android SDK built for x86") ||
            Build.MANUFACTURER.contains("Genymotion") ||
            Build.PRODUCT.contains("sdk_google") ||
            Build.PRODUCT.contains("google_sdk") ||
            Build.PRODUCT.contains("sdk") ||
            Build.PRODUCT.contains("sdk_x86") ||
            Build.PRODUCT.contains("vbox86p") ||
            Build.PRODUCT.contains("emulator") ||
            Build.PRODUCT.contains("simulator")
        val isX86 = Build.SUPPORTED_ABIS.any { it.contains("x86", ignoreCase = true) }
        return isEmulator && isX86
    }

    /** Device runs only x86/x86_64 ABIs (release APK has no matching native libs). */
    fun isX86OnlyDevice(): Boolean =
        Build.SUPPORTED_ABIS.isNotEmpty() &&
            Build.SUPPORTED_ABIS.all { it.contains("x86", ignoreCase = true) }

    /** Debug x86 AVD: skip Play-services Subject, use bundled Selfie immediately. */
    fun shouldForceSelfiePrimary(context: Context): Boolean =
        isDebugBuild(context) && isX86Emulator()

    /**
     * Real weak phones (Android Go / low RAM): prefer Subject quality.
     * Selfie is not used as primary or fallback — only x86 emulators force Selfie.
     */
    fun shouldAvoidSelfieForQuality(context: Context): Boolean {
        if (shouldForceSelfiePrimary(context)) return false
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return false
        return am.isLowRamDevice || am.memoryClass <= 96
    }

    fun isSegmentationUnsupportedOnRelease(context: Context): Boolean =
        !isDebugBuild(context) && isX86OnlyDevice()

    fun segmentationTimeoutMs(context: Context): Long = when {
        !isDebugBuild(context) -> RELEASE_SEGMENTATION_TIMEOUT_MS
        shouldForceSelfiePrimary(context) -> DEBUG_X86_SEGMENTATION_TIMEOUT_MS
        else -> DEBUG_SEGMENTATION_TIMEOUT_MS
    }

    /**
     * UI / interactive remove-bg budget (QuickEdit, Background mode).
     * Strong phones get more time; low-RAM stays at 45s.
     */
    fun interactiveRemoveBgTimeoutMs(context: Context): Long {
        if (shouldForceSelfiePrimary(context)) return 180_000L
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memClass = am?.memoryClass ?: 64
        return when {
            am?.isLowRamDevice == true || memClass <= 64 -> 45_000L
            memClass <= 96 -> 60_000L
            memClass <= 128 -> 90_000L
            else -> 120_000L
        }
    }

    fun isUnsupportedOrTimeout(error: Throwable?): Boolean {
        if (error == null) return false
        if (error is kotlinx.coroutines.TimeoutCancellationException) return true
        val message = generateSequence(error) { it.cause }
            .joinToString(" ") { it.message.orEmpty() }
        return message.contains(ERROR_DEVICE_UNSUPPORTED, ignoreCase = true) ||
            message.contains(ERROR_SEGMENTATION_TIMEOUT, ignoreCase = true) ||
            message.contains("Timed out waiting for", ignoreCase = true)
    }
}
