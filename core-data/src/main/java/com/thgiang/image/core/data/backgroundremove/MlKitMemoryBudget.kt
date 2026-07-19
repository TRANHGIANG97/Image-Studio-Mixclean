package com.thgiang.image.core.data.backgroundremove

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Heap-aware limits for ML Kit Subject Segmentation.
 *
 * GMS native code can allocate several times the input bitmap size (~120MB+ for 2K images).
 * Use free heap at call time — not just device RAM class — to pick a safe working size.
 */
internal object MlKitMemoryBudget {

    /** Conservative native peak ≈ input ARGB + confidence tensors + model scratch. */
    private const val SUBJECT_BYTES_PER_PIXEL = 24L
    /** Bundled selfie model — lighter than Play-services Subject. */
    private const val SELFIE_BYTES_PER_PIXEL = 10L

    const val MIN_SEGMENTATION_SIDE_PUBLIC = 384
    private const val MIN_SEGMENTATION_SIDE = MIN_SEGMENTATION_SIDE_PUBLIC
    private const val ABSOLUTE_MAX_SIDE = 1792
    private const val MB = 1024L * 1024L
    /** Native peak uses up to ~55% of reported free heap (rest = JVM + mask buffers). */
    private const val FREE_HEAP_USABLE_FRACTION = 0.55f
    private const val PEAK_SAFETY_MARGIN = 1.35f

    const val ERROR_INSUFFICIENT_HEAP = "INSUFFICIENT_HEAP"

    fun estimateSubjectPeakBytes(width: Int, height: Int): Long {
        if (width <= 0 || height <= 0) return 0L
        return width.toLong() * height * SUBJECT_BYTES_PER_PIXEL
    }

    fun estimateSelfiePeakBytes(width: Int, height: Int): Long {
        if (width <= 0 || height <= 0) return 0L
        return width.toLong() * height * SELFIE_BYTES_PER_PIXEL
    }

    fun freeHeapBytes(): Long {
        val runtime = Runtime.getRuntime()
        val used = runtime.totalMemory() - runtime.freeMemory()
        return (runtime.maxMemory() - used).coerceAtLeast(0L)
    }

    /**
     * Max side length ML Kit should see right now.
     * [policyCap] comes from device RAM class (injected at app start).
     */
    fun resolveMaxProcessSide(policyCap: Int, sourceWidth: Int, sourceHeight: Int): Int {
        val heapSide = maxSubjectSideForFreeHeap(freeHeapBytes())
        val sourceSide = max(sourceWidth, sourceHeight).coerceAtLeast(1)
        return min(policyCap, min(heapSide, min(sourceSide, ABSOLUTE_MAX_SIDE)))
            .coerceAtLeast(MIN_SEGMENTATION_SIDE)
    }

    fun maxSideForFreeHeap(
        freeBytes: Long,
        bytesPerPixel: Long,
        budgetFraction: Float = 0.28f,
    ): Int {
        val budget = (freeBytes * budgetFraction)
            .toLong()
            .coerceIn(6L * 1024 * 1024, 96L * 1024 * 1024)
        val side = sqrt(budget.toDouble() / bytesPerPixel).toInt()
        return side.coerceIn(MIN_SEGMENTATION_SIDE, ABSOLUTE_MAX_SIDE)
    }

    private fun maxSubjectSideForFreeHeap(freeBytes: Long): Int =
        maxSideForFreeHeap(freeBytes, SUBJECT_BYTES_PER_PIXEL)

    private fun maxSelfieSideForFreeHeap(freeBytes: Long): Int =
        maxSideForFreeHeap(freeBytes, SELFIE_BYTES_PER_PIXEL, budgetFraction = 0.32f)

    /** Absolute floor: never call GMS when free heap is below this. */
    fun minFreeHeapRequiredBytes(): Long {
        val maxHeap = Runtime.getRuntime().maxMemory()
        val percentFloor = (maxHeap * 0.12).toLong()
        return maxOf(24L * MB, percentFloor).coerceAtMost(40L * MB)
    }

    private fun refusalReason(
        width: Int,
        height: Int,
        bytesPerPixel: Long,
        usableFraction: Float,
    ): String? {
        val free = freeHeapBytes()
        if (free < minFreeHeapRequiredBytes()) {
            return "$ERROR_INSUFFICIENT_HEAP: free=${free / MB}MB < min=${minFreeHeapRequiredBytes() / MB}MB"
        }
        val peak = (width.toLong() * height * bytesPerPixel * PEAK_SAFETY_MARGIN).toLong()
        val budget = (free * usableFraction).toLong()
        if (peak > budget) {
            return "$ERROR_INSUFFICIENT_HEAP: need~${peak / MB}MB peak > budget=${budget / MB}MB"
        }
        return null
    }

    fun subjectRefusalReason(width: Int, height: Int): String? =
        refusalReason(width, height, SUBJECT_BYTES_PER_PIXEL, FREE_HEAP_USABLE_FRACTION)

    fun selfieRefusalReason(width: Int, height: Int): String? =
        refusalReason(width, height, SELFIE_BYTES_PER_PIXEL, 0.60f)

    fun canRunSubjectSegmentation(width: Int, height: Int): Boolean =
        subjectRefusalReason(width, height) == null

    fun canRunSelfieSegmentation(width: Int, height: Int): Boolean =
        selfieRefusalReason(width, height) == null

    /** @deprecated alias */ 
    fun canRunMlKitSegmentation(width: Int, height: Int): Boolean =
        canRunSubjectSegmentation(width, height)

    fun mlKitRefusalReason(width: Int, height: Int): String? =
        subjectRefusalReason(width, height)

    fun canRunAnySegmentationAtAll(): Boolean {
        val free = freeHeapBytes()
        if (free < minFreeHeapRequiredBytes()) return false
        return largestRunnableSelfieSide(ABSOLUTE_MAX_SIDE) != null
    }

    fun largestRunnableSubjectSide(preferredSide: Int): Int? =
        largestRunnableSide(preferredSide, ::canRunSubjectSegmentation)

    fun largestRunnableSelfieSide(preferredSide: Int): Int? =
        largestRunnableSide(preferredSide, ::canRunSelfieSegmentation)

    private fun largestRunnableSide(
        preferredSide: Int,
        canRun: (Int, Int) -> Boolean,
    ): Int? {
        var side = preferredSide.coerceIn(MIN_SEGMENTATION_SIDE, ABSOLUTE_MAX_SIDE)
        while (side >= MIN_SEGMENTATION_SIDE) {
            if (canRun(side, side)) return side
            side = (side * 0.82f).toInt().coerceAtLeast(MIN_SEGMENTATION_SIDE)
            if (side == MIN_SEGMENTATION_SIDE) {
                return if (canRun(side, side)) side else null
            }
        }
        return null
    }

    /**
     * Largest square side ≤ [preferredSide] that passes subject segmentation, or null.
     */
    fun largestRunnableSide(preferredSide: Int): Int? =
        largestRunnableSubjectSide(preferredSide)

    fun canAllocateDisplayCopy(bitmap: Bitmap): Boolean {
        val bytes = bitmap.byteCount.toLong()
        return freeHeapBytes() >= minFreeHeapRequiredBytes() + bytes * 2
    }

    /** Best-effort reclaim before native ML work (does not guarantee free RAM). */
    fun prepareForMlKit() {
        System.gc()
        System.runFinalization()
    }

    /**
     * Pick segmentation attempt scales from safest to largest.
     * When heap is tight, never start at 100% — avoids a fatal native OOM on first try.
     */
    fun segmentationRetryScales(width: Int, height: Int): FloatArray {
        val peak = estimateSubjectPeakBytes(width, height)
        val free = freeHeapBytes()
        return when {
            peak <= free * 0.22 -> floatArrayOf(1f, 0.75f, 0.5f, 0.35f)
            peak <= free * 0.35 -> floatArrayOf(0.75f, 0.5f, 0.35f, 0.25f)
            else -> floatArrayOf(0.5f, 0.35f, 0.25f, 0.2f)
        }
    }

    fun downscaleToMaxSide(bitmap: Bitmap, maxSide: Int): ScaledBitmap {
        val largest = max(bitmap.width, bitmap.height)
        if (largest <= maxSide) {
            return ScaledBitmap(bitmap, ownsBitmap = false)
        }
        val scale = maxSide.toFloat() / largest
        val w = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val h = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return ScaledBitmap(Bitmap.createScaledBitmap(bitmap, w, h, true), ownsBitmap = true)
    }

    fun isOutOfMemory(error: Throwable?): Boolean {
        var current = error
        while (current != null) {
            if (current is OutOfMemoryError) return true
            val message = current.message.orEmpty()
            if (message.contains("OutOfMemory", ignoreCase = true) ||
                message.contains("allocate", ignoreCase = true) && message.contains("OOM", ignoreCase = true)
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }

    data class ScaledBitmap(val bitmap: Bitmap, val ownsBitmap: Boolean)
}
