package com.thgiang.image.core.util

import android.app.ActivityManager
import android.content.Context
import android.graphics.BitmapFactory

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
     * Kích thước cạnh lớn nhất cho decode bitmap, dựa trên heap class.
     * Tránh decode ảnh >12MP trên thiết bị tầm thấp.
     */
    fun maxDecodeSide(context: Context): Int {
        val memClass = getMemoryClass(context)
        return when {
            memClass <= 64 -> 2048   // Thiết bị thấp (2GB RAM)
            memClass <= 128 -> 4096  // Trung bình (4GB RAM)
            else -> 0                 // Cao cấp — không giới hạn (0 = full res)
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
