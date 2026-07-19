package com.thgiang.image.core.util

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache

/**
 * Tiện ích tính toán giới hạn bộ nhớ dựa trên heap của thiết bị.
 * Giúp tránh OutOfMemoryError bằng cách điều chỉnh kích thước cache và ảnh động.
 */
object MemoryUtil {

    private const val MB = 1024L * 1024L

    /** Heap ≤48MB or Android low-RAM flag — batch/studio export disabled. */
    private const val EXTREMELY_LOW_HEAP_MB = 48

    enum class DeviceMemoryTier {
        /** Batch remove + large export blocked; single-image AI at minimum quality. */
        EXTREMELY_LOW,
        LOW,
        NORMAL,
        HIGH,
    }

    data class ExportSize(val width: Int, val height: Int)

    /** Heap class (MB) từ ActivityManager. */
    fun getMemoryClass(context: Context): Int {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        return am?.memoryClass ?: 64
    }

    /** Heap class (MB) dùng cho largeHeap nếu được khai báo. */
    fun getLargeMemoryClass(context: Context): Int {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        return am?.largeMemoryClass ?: 128
    }

    fun getDeviceMemoryTier(context: Context): DeviceMemoryTier {
        val memClass = getMemoryClass(context)
        return when {
            isExtremelyLowEndDevice(context) -> DeviceMemoryTier.EXTREMELY_LOW
            memClass <= 64 -> DeviceMemoryTier.LOW
            memClass <= 128 -> DeviceMemoryTier.NORMAL
            else -> DeviceMemoryTier.HIGH
        }
    }

    fun isExtremelyLowEndDevice(context: Context): Boolean {
        if (getMemoryClass(context) <= EXTREMELY_LOW_HEAP_MB) return true
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        return am?.isLowRamDevice == true
    }

    fun supportsBatchBackgroundRemove(context: Context): Boolean =
        !isExtremelyLowEndDevice(context)

    fun freeHeapBytes(): Long {
        val runtime = Runtime.getRuntime()
        val used = runtime.totalMemory() - runtime.freeMemory()
        return (runtime.maxMemory() - used).coerceAtLeast(0L)
    }

    fun maxExportSide(context: Context): Int {
        val memClass = getMemoryClass(context)
        val policyCap = when {
            isExtremelyLowEndDevice(context) -> 960
            memClass <= 64 -> 1280
            memClass <= 96 -> 1536
            memClass <= 128 -> 2048
            memClass <= 192 -> 2560
            else -> 3072
        }
        val heapSide = maxSideForByteBudget(
            bytes = freeHeapBytes(),
            bytesPerPixel = 8L,
            budgetFraction = 0.22f,
            minSide = 512,
            maxSide = policyCap,
        )
        return minOf(policyCap, heapSide)
    }

    fun clampExportSize(width: Int, height: Int, context: Context): ExportSize {
        if (width <= 0 || height <= 0) return ExportSize(width, height)
        val maxSide = maxExportSide(context)
        val largest = maxOf(width, height)
        if (largest <= maxSide) return ExportSize(width, height)
        val scale = maxSide.toFloat() / largest
        return ExportSize(
            width = (width * scale).toInt().coerceAtLeast(1),
            height = (height * scale).toInt().coerceAtLeast(1),
        )
    }

    fun canAllocateExportBitmap(width: Int, height: Int, context: Context): Boolean {
        if (width <= 0 || height <= 0) return false
        val bytes = width.toLong() * height * 4L
        if (bytes > maxCompositeBytes(context)) return false
        val peakEstimate = bytes * 3
        return peakEstimate <= freeHeapBytes() * 0.35
    }

    fun maxSideForByteBudget(
        bytes: Long,
        bytesPerPixel: Long,
        budgetFraction: Float,
        minSide: Int,
        maxSide: Int,
    ): Int {
        val budget = (bytes * budgetFraction).toLong().coerceAtLeast(4L * MB)
        val side = kotlin.math.sqrt(budget.toDouble() / bytesPerPixel).toInt()
        return side.coerceIn(minSide, maxSide)
    }

    fun isOutOfMemoryError(error: Throwable?): Boolean {
        var current = error
        while (current != null) {
            if (current is OutOfMemoryError) return true
            val message = current.message.orEmpty()
            if (message.contains("INSUFFICIENT_HEAP", ignoreCase = true) ||
                message.contains("OutOfMemory", ignoreCase = true)
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }

    /** Best-effort reclaim before export / ML Kit (call from UI layer optional). */
    fun prepareForHeavyImageWork() {
        System.gc()
        System.runFinalization()
    }

    /**
     * Dung lượng tối đa (bytes) cho LruCache bitmap.
     * Dùng 25% heap class — đủ lớn để cache hiệu quả, đủ nhỏ để không gây OOM.
     */
    fun getBitmapCacheMaxBytes(context: Context): Long {
        val memoryClass = getMemoryClass(context)
        return (memoryClass * MB * 0.25).toLong().coerceAtLeast(32 * MB)
    }

    /**
     * Tính inSampleSize (luỹ thừa của 2) để decode ảnh với kích thước tối đa cho phép.
     */
    fun calculateInSampleSize(
        options: BitmapFactory.Options,
        maxSidePx: Int
    ): Int {
        val rawWidth = options.outWidth
        val rawHeight = options.outHeight
        if (rawWidth <= 0 || rawHeight <= 0) return 1
        var sample = 1
        while (rawWidth / sample > maxSidePx || rawHeight / sample > maxSidePx) {
            sample *= 2
        }
        return sample.coerceAtLeast(1)
    }

    /**
     * Kích thước cạnh lớn nhất cho decode bitmap thô (processor, preview).
     */
    fun maxDecodeSide(context: Context): Int {
        val memClass = getMemoryClass(context)
        return when {
            memClass <= 48 -> 1280
            memClass <= 64 -> 1536
            memClass <= 96 -> 2048
            memClass <= 128 -> 3072
            else -> 4096
        }
    }

    /**
     * Cạnh lớn nhất cho bitmap làm việc trong QuickEdit editor.
     * Ảnh 108MP (~12000×9000) luôn được downscale trước khi vào RAM.
     */
    fun maxEditorBitmapSide(context: Context): Int {
        val memClass = getMemoryClass(context)
        return when {
            memClass <= 48 -> 1280  // ~1.6MP — heap ≤48MB
            memClass <= 64 -> 1536  // ~2.4MP — máy 2–3GB RAM
            memClass <= 96 -> 2048  // ~4MP — máy 4GB
            memClass <= 128 -> 2560 // ~6.5MP — máy 4–6GB
            memClass <= 192 -> 3072 // ~9.4MP — máy 6GB (vd. A73 5G)
            else -> 4096            // ~16MP — flagship 8GB+
        }
    }

    /**
     * Cạnh lớn nhất khi chạy ML Kit xóa nền (native heap + mask buffers).
     * Thấp hơn [maxEditorBitmapSide] để tránh OOM trên máy yếu.
     */
    fun maxMlKitProcessSide(context: Context): Int {
        if (isExtremelyLowEndDevice(context)) return 640
        val memClass = getMemoryClass(context)
        return when {
            memClass <= 48 -> 768
            memClass <= 64 -> 1024
            memClass <= 96 -> 1280
            memClass <= 128 -> 1536
            else -> 1792
        }
    }

    /** Thiết bị heap class ≤64MB — áp dụng chính sách RAM chặt. */
    fun isLowRamDevice(context: Context): Boolean =
        getMemoryClass(context) <= 64

    /** Số frame undo tối đa trên stack editor. */
    fun maxEditorStackSize(context: Context): Int =
        when {
            isExtremelyLowEndDevice(context) -> 2
            getMemoryClass(context) <= 48 -> 3
            getMemoryClass(context) <= 64 -> 4
            getMemoryClass(context) <= 96 -> 6
            else -> 8
        }

    /**
     * LruCache bitmap theo bytes (không theo số lượng).
     * [budgetFraction] = phần heap class dành cho cache (mặc định 25%).
     */
    fun createBitmapByteCache(
        context: Context,
        budgetFraction: Float = 0.25f,
    ): LruCache<String, Bitmap> {
        val memClass = getMemoryClass(context)
        val fraction = when {
            memClass <= 48 -> budgetFraction.coerceAtMost(0.10f)
            memClass <= 64 -> budgetFraction.coerceAtMost(0.15f)
            memClass <= 96 -> budgetFraction.coerceAtMost(0.20f)
            else -> budgetFraction
        }
        val hardCapMb = when {
            memClass <= 48 -> 16L
            memClass <= 64 -> 24L
            memClass <= 96 -> 32L
            else -> 96L
        }
        val maxBytes = (memClass * MB * fraction)
            .toLong()
            .coerceIn(8 * MB, hardCapMb * MB)
            .toInt()
        return object : LruCache<String, Bitmap>(maxBytes) {
            override fun sizeOf(key: String, value: Bitmap): Int =
                value.byteCount.coerceAtLeast(1)
        }
    }

    /** Kiểm tra xem bitmap với kích thước cho trước có khả thi trên thiết bị này không. */
    fun isBitmapSizeFeasible(width: Int, height: Int, context: Context): Boolean {
        if (width <= 0 || height <= 0) return false
        val bytesNeeded = width.toLong() * height.toLong() * 4 // ARGB_8888
        val maxSide = maxDecodeSide(context)
        if (maxSide > 0 && (width > maxSide || height > maxSide)) return false
        val memoryClass = getMemoryClass(context)
        // Không cho phép bitmap chiếm >60% heap class
        val maxBitmapBytes = (memoryClass * MB * 0.6).toLong()
        return bytesNeeded <= maxBitmapBytes
    }

    /** Số bytes tối đa cho phép khi tạo bitmap composite. */
    fun maxCompositeBytes(context: Context): Long {
        val memClass = getMemoryClass(context)
        return (memClass * MB * 0.4).toLong().coerceAtLeast(16 * MB)
    }
}
