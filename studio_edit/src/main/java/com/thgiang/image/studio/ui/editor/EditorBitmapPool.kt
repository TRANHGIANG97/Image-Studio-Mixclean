package com.thgiang.image.studio.ui.editor

import android.graphics.Bitmap
import android.util.Log
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Bitmap Pool v2 - Lru-style eviction, memory pressure aware, atomic operations
 * 
 * Cải tiến chính:
 * 1. Dùng ConcurrentLinkedQueue + atomic counter thay vì synchronized blocks
 * 2. Lru-eviction khi vượt memory budget
 * 3. Memory pressure listener
 * 4. Config-based key để tránh string allocation
 */
class EditorBitmapPool {
    
    data class PoolKey(
        val width: Int,
        val height: Int,
        val config: Bitmap.Config
    ) {
        override fun toString(): String = "${width}x${height}_${config.ordinal}"
    }
    
    private data class PooledBitmap(
        val bitmap: Bitmap,
        var lastUsed: Long = System.currentTimeMillis()
    )
    
    private val pools = mutableMapOf<PoolKey, ConcurrentLinkedQueue<PooledBitmap>>()
    private val poolSizes = mutableMapOf<PoolKey, AtomicInteger>()
    private val totalPixels = AtomicLong(0)
    
    private val maxTotalPixels: Long
    private val maxBucketSize: Int
    
    constructor(
        maxMemoryPercent: Float = 0.15f,
        maxBucketSize: Int = 6
    ) {
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory()
        this.maxTotalPixels = (maxMemory * maxMemoryPercent / 4).toLong() // 4 bytes per pixel (ARGB_8888)
        this.maxBucketSize = maxBucketSize
        
        Log.d(TAG, "Pool initialized: maxPixels=$maxTotalPixels, maxBucket=$maxBucketSize")
    }
    
    fun obtain(width: Int, height: Int, config: Bitmap.Config = Bitmap.Config.ARGB_8888): Bitmap {
        val key = PoolKey(width, height, config)
        val queue = pools.getOrPut(key) { ConcurrentLinkedQueue() }
        val sizeCounter = poolSizes.getOrPut(key) { AtomicInteger(0) }
        
        // Try to find reusable bitmap (FIFO with validation)
        while (true) {
            val pooled = queue.poll() ?: break
            sizeCounter.decrementAndGet()
            
            if (!pooled.bitmap.isRecycled && 
                pooled.bitmap.width == width && 
                pooled.bitmap.height == height &&
                pooled.bitmap.config == config) {
                
                totalPixels.addAndGet(-(width * height.toLong()))
                
                // Fast clear: use hardware-accelerated erase if possible
                pooled.bitmap.eraseColor(0)
                return pooled.bitmap
            }
            
            // Invalid bitmap, ensure recycled
            if (!pooled.bitmap.isRecycled) {
                pooled.bitmap.recycle()
            }
        }
        
        // Create new bitmap
        return Bitmap.createBitmap(width, height, config)
    }
    
    fun recycle(bitmap: Bitmap) {
        if (bitmap.isRecycled) return
        
        val width = bitmap.width
        val height = bitmap.height
        val pixelCount = width * height.toLong()
        
        // Memory pressure check: if over budget, recycle immediately
        if (totalPixels.get() + pixelCount > maxTotalPixels) {
            bitmap.recycle()
            return
        }
        
        val key = PoolKey(width, height, bitmap.config ?: Bitmap.Config.ARGB_8888)
        val queue = pools.getOrPut(key) { ConcurrentLinkedQueue() }
        val sizeCounter = poolSizes.getOrPut(key) { AtomicInteger(0) }
        
        // Check bucket limit
        if (sizeCounter.get() >= maxBucketSize) {
            // Evict oldest (FIFO)
            val evicted = queue.poll()
            if (evicted != null) {
                sizeCounter.decrementAndGet()
                totalPixels.addAndGet(-pixelCount)
                if (!evicted.bitmap.isRecycled) evicted.bitmap.recycle()
            }
        }
        
        queue.offer(PooledBitmap(bitmap))
        sizeCounter.incrementAndGet()
        totalPixels.addAndGet(pixelCount)
    }
    
    /**
     * Emergency cleanup khi system báo low memory
     */
    fun trimMemory(level: Int) {
        when (level) {
            android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
            android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                clear()
            }
            android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
            android.content.ComponentCallbacks2.TRIM_MEMORY_MODERATE -> {
                // Evict 50% of oldest entries
                evictPercent(0.5f)
            }
        }
    }
    
    private fun evictPercent(percent: Float) {
        val allEntries = pools.flatMap { (key, queue) ->
            queue.toList().map { key to it }
        }.sortedBy { it.second.lastUsed }
        
        val evictCount = (allEntries.size * percent).toInt().coerceAtLeast(1)
        
        allEntries.take(evictCount).forEach { (key, pooled) ->
            pools[key]?.remove(pooled)
            poolSizes[key]?.decrementAndGet()
            totalPixels.addAndGet(-(pooled.bitmap.width * pooled.bitmap.height.toLong()))
            if (!pooled.bitmap.isRecycled) pooled.bitmap.recycle()
        }
    }
    
    fun clear() {
        pools.forEach { (key, queue) ->
            queue.forEach { 
                if (!it.bitmap.isRecycled) it.bitmap.recycle() 
            }
            queue.clear()
            poolSizes[key]?.set(0)
        }
        totalPixels.set(0)
    }
    
    fun getStats(): PoolStats {
        var totalBitmaps = 0
        var totalMemory = 0L
        
        pools.forEach { (key, queue) ->
            val count = queue.size
            totalBitmaps += count
            totalMemory += count * key.width * key.height * 4L
        }
        
        return PoolStats(totalBitmaps, totalMemory, totalPixels.get())
    }
    
    data class PoolStats(
        val totalBitmaps: Int,
        val totalMemoryBytes: Long,
        val trackedPixels: Long
    )
    
    companion object {
        private const val TAG = "EditorBitmapPool"
    }
}
