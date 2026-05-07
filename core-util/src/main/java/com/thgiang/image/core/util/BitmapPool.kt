package com.thgiang.image.core.util
import android.graphics.Bitmap
import android.graphics.Bitmap.Config
import java.util.ArrayDeque

/**
 * Lightweight bitmap pool to reduce GC pressure and re-use bitmaps via [BitmapFactory.Options.inBitmap].
 * Use for same-dimension, same-config bitmaps (e.g. Studio relighting result layer).
 * Thread-safe for single producer/consumer on [Dispatchers.Default].
 */
object BitmapPool {

    private const val MAX_POOL_SIZE = 3

    @Volatile
    private var pool: ArrayDeque<Bitmap>? = ArrayDeque(maxOf(1, MAX_POOL_SIZE))

    /**
     * Obtain a bitmap of the given dimensions and config. Reuses a pooled bitmap if one matches
     * (same or larger size, same config); otherwise creates a new one.
     * Caller must [release] the bitmap when done.
     */
    @Synchronized
    fun get(width: Int, height: Int, config: Config = Config.ARGB_8888): Bitmap {
        val p = pool ?: ArrayDeque<Bitmap>().also { pool = it }
        val it = p.iterator()
        while (it.hasNext()) {
            val b = it.next()
            if (b.width == width && b.height == height && b.config == config && !b.isRecycled) {
                it.remove()
                return b
            }
        }
        return Bitmap.createBitmap(width, height, config)
    }

    /**
     * Return a bitmap to the pool for reuse. Do not use the bitmap after calling this.
     * If pool is full or config/size is not suitable for reuse, recycles the bitmap.
     */
    @Synchronized
    fun release(bitmap: Bitmap?) {
        if (bitmap == null || bitmap.isRecycled) return
        val p = pool ?: return
        if (p.size < MAX_POOL_SIZE && bitmap.config != null) {
            p.addLast(bitmap)
        } else {
            bitmap.recycle()
        }
    }

    /**
     * Clear the pool and recycle all held bitmaps. Call when changing image source.
     */
    @Synchronized
    fun clear() {
        pool?.forEach { if (!it.isRecycled) it.recycle() }
        pool?.clear()
    }
}




