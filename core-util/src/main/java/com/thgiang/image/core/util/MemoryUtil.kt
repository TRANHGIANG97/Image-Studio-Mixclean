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
        val memClass = getMemoryClass(context)
        return when {
            memClass <= 48 -> 960
            memClass <= 64 -> 1280
            memClass <= 96 -> 1536
            memClass <= 128 -> 2048
            else -> 2048
        }
    }

    /** Thiết bị heap class ≤64MB — áp dụng chính sách RAM chặt. */
    fun isLowRamDevice(context: Context): Boolean =
        getMemoryClass(context) <= 64

    /** Số frame undo tối đa trên stack editor. */
    fun maxEditorStackSize(context: Context): Int =
        when {
            getMemoryClass(context) <= 48 -> 3
            getMemoryClass(context) <= 64 -> 5
            getMemoryClass(context) <= 96 -> 7
            else -> 10
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
