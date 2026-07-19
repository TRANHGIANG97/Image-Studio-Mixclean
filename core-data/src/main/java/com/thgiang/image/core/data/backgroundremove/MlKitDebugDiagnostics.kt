package com.thgiang.image.core.data.backgroundremove

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.StatFs
import android.util.Log
import java.util.Locale

/**
 * Verbose diagnostics for background removal — **debug builds only**.
 * Filter logcat: `adb logcat -s MlKitDiag`
 */
object MlKitDebugDiagnostics {

    const val TAG = "MlKitDiag"

    fun isEnabled(context: Context): Boolean = MlKitDeviceSupport.isDebugBuild(context)

    fun logDeviceSnapshot(context: Context, label: String) {
        if (!isEnabled(context)) return
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)

        val runtime = Runtime.getRuntime()
        val maxHeapMb = runtime.maxMemory() / MB
        val usedHeapMb = (runtime.totalMemory() - runtime.freeMemory()) / MB
        val freeHeapMb = MlKitMemoryBudget.freeHeapBytes() / MB

        val internalStat = runCatching {
            StatFs(context.filesDir.absolutePath).let { stat ->
                val block = stat.blockSizeLong * stat.blockCountLong / MB
                val avail = stat.blockSizeLong * stat.availableBlocksLong / MB
                "internal total=${block}MB free=${avail}MB"
            }
        }.getOrElse { "internal=unknown" }

        logBlock(
            label,
            listOf(
                "── device ──",
                "model=${Build.MANUFACTURER} ${Build.MODEL} device=${Build.DEVICE}",
                "android=${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                "hardware=${Build.HARDWARE} abis=${Build.SUPPORTED_ABIS.joinToString()}",
                "cpuCores=${runtime.availableProcessors()}",
                "emulator=${MlKitDeviceSupport.isX86Emulator()} x86Only=${MlKitDeviceSupport.isX86OnlyDevice()}",
                "forceSelfiePrimary=${MlKitDeviceSupport.shouldForceSelfiePrimary(context)}",
                "avoidSelfieForQuality=${MlKitDeviceSupport.shouldAvoidSelfieForQuality(context)}",
                "selfieNativeLib=${SelfieFallbackSegmenter.isNativeLibraryAvailable(context)} " +
                    "dir=${context.applicationInfo.nativeLibraryDir}",
                "── system RAM ──",
                "totalRam=${memInfo?.totalMem?.div(MB) ?: "?"}MB " +
                    "availRam=${memInfo?.availMem?.div(MB) ?: "?"}MB " +
                    "lowMemory=${memInfo?.lowMemory} threshold=${memInfo?.threshold?.div(MB) ?: "?"}MB",
                "memoryClass=${am?.memoryClass ?: "?"}MB " +
                    "largeMemoryClass=${am?.largeMemoryClass ?: "?"}MB " +
                    "isLowRamDevice=${am?.isLowRamDevice}",
                "── JVM heap ──",
                "maxHeap=${maxHeapMb}MB usedHeap=${usedHeapMb}MB freeHeap=${freeHeapMb}MB " +
                    "minRequired=${MlKitMemoryBudget.minFreeHeapRequiredBytes() / MB}MB",
                "── storage ──",
                internalStat,
            ),
        )
    }

    fun logRemovalStart(
        context: Context,
        source: String,
        inputWidth: Int,
        inputHeight: Int,
        maxDecodeSize: Int,
    ) {
        if (!isEnabled(context)) return
        logDeviceSnapshot(context, "BG_REMOVE_START [$source]")
        logBlock(
            "BG_REMOVE_INPUT [$source]",
            listOf(
                "input=${inputWidth}x$inputHeight (${megapixels(inputWidth, inputHeight)} MP)",
                "inputBytes≈${bitmapBytes(inputWidth, inputHeight) / MB}MB",
                "maxDecodeSize(policyCap)=$maxDecodeSize",
            ),
        )
    }

    fun logRouting(
        context: Context,
        inputWidth: Int,
        inputHeight: Int,
        policyMaxSide: Int,
        planName: String,
        maxWorkSide: Int,
    ) {
        if (!isEnabled(context)) return
        val workW = minOf(inputWidth, maxWorkSide)
        val workH = minOf(inputHeight, maxWorkSide)
        logBlock(
            "BG_REMOVE_ROUTING",
            listOf(
                "plan=$planName policyMaxSide=$policyMaxSide maxWorkSide=$maxWorkSide",
                "segTimeoutMs=${formatTimeout(MlKitDeviceSupport.segmentationTimeoutMs(context))}",
                "canRunSubject@${maxWorkSide}px=${MlKitMemoryBudget.canRunSubjectSegmentation(maxWorkSide, maxWorkSide)}",
                "canRunSelfie@${maxWorkSide}px=${MlKitMemoryBudget.canRunSelfieSegmentation(maxWorkSide, maxWorkSide)}",
                "largestRunnableSubject=${MlKitMemoryBudget.largestRunnableSubjectSide(policyMaxSide)}",
                "largestRunnableSelfie=${MlKitMemoryBudget.largestRunnableSelfieSide(policyMaxSide)}",
                "subjectRefusal=${MlKitMemoryBudget.subjectRefusalReason(workW, workH)}",
                "selfieRefusal=${MlKitMemoryBudget.selfieRefusalReason(workW, workH)}",
                "freeHeap=${MlKitMemoryBudget.freeHeapBytes() / MB}MB",
            ),
        )
    }

    fun logStage(context: Context, stage: String, detail: String, elapsedMs: Long? = null) {
        if (!isEnabled(context)) return
        val timing = elapsedMs?.let { " (+${it}ms)" }.orEmpty()
        Log.d(TAG, "▶ $stage$timing — $detail")
    }

    fun logBitmap(context: Context, label: String, bitmap: Bitmap) {
        if (!isEnabled(context)) return
        Log.d(
            TAG,
            "  bitmap[$label] ${bitmap.width}x${bitmap.height} " +
                "${megapixels(bitmap.width, bitmap.height)}MP " +
                "config=${bitmap.config} bytes≈${bitmap.byteCount / MB}MB recycled=${bitmap.isRecycled}",
        )
    }

    fun logRemovalSuccess(
        context: Context,
        planName: String,
        inputWidth: Int,
        inputHeight: Int,
        workWidth: Int,
        workHeight: Int,
        outputWidth: Int,
        outputHeight: Int,
        segmentationMs: Long,
        totalMs: Long,
        selfiePrimary: Boolean,
        selfieFallback: Boolean,
    ) {
        if (!isEnabled(context)) return
        logBlock(
            "BG_REMOVE_SUCCESS",
            listOf(
                "plan=$planName selfiePrimary=$selfiePrimary selfieFallback=$selfieFallback",
                "input=${inputWidth}x$inputHeight → work=${workWidth}x$workHeight → output=${outputWidth}x$outputHeight",
                "segmentation=${segmentationMs}ms total=${totalMs}ms",
                "freeHeap=${MlKitMemoryBudget.freeHeapBytes() / MB}MB",
            ),
        )
    }

    fun logRemovalFailure(context: Context, stage: String, error: Throwable, elapsedMs: Long) {
        if (!isEnabled(context)) return
        logDeviceSnapshot(context, "BG_REMOVE_FAIL [$stage]")
        Log.e(
            TAG,
            "✖ BG_REMOVE_FAIL stage=$stage elapsed=${elapsedMs}ms " +
                "type=${error::class.java.simpleName} msg=${error.message}",
            error,
        )
        var cause = error.cause
        var depth = 1
        while (cause != null && depth <= 4) {
            Log.e(TAG, "  cause[$depth]=${cause::class.java.simpleName}: ${cause.message}")
            cause = cause.cause
            depth++
        }
    }

    private fun logBlock(header: String, lines: List<String>) {
        Log.d(TAG, "═══ $header ═══")
        lines.forEach { Log.d(TAG, "  $it") }
    }

    private fun megapixels(w: Int, h: Int): String =
        String.format(Locale.US, "%.2f", w * h / 1_000_000.0)

    private fun bitmapBytes(w: Int, h: Int): Long = w.toLong() * h * 4L

    private fun formatTimeout(ms: Long): String =
        if (ms == Long.MAX_VALUE) "none(debug)" else "${ms}ms"

    private const val MB = 1024L * 1024L
}
