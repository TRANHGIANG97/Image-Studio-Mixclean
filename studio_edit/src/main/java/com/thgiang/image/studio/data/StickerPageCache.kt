package com.thgiang.image.studio.data

import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory cache cho sticker data với TTL 10 phút.
 *
 * Cache 2 loại:
 *  1. [previewCache] — 20 sticker preview (10 meme + 10 decor), dùng cho hàng ngang quick strip.
 *  2. [pageCache]    — Các trang đã tải trong Gallery Sheet, key = "folder:page:limit".
 *
 * Gọi [invalidate] để xóa toàn bộ cache khi cần tải lại.
 */
@Singleton
class StickerPageCache @Inject constructor() {

    private data class CacheEntry<T>(
        val data: T,
        val timestamp: Long = System.currentTimeMillis(),
    )

    /** Preview cache: Pair(memeList, decorList) */
    private val previewCache = AtomicReference<CacheEntry<Pair<List<RemoteSticker>, List<RemoteSticker>>>?>()

    /** Page cache: key = "Sticker_meme:1:30" */
    private val pageCache = ConcurrentHashMap<String, CacheEntry<List<RemoteSticker>>>()

    // ─── Preview ──────────────────────────────────────────────────

    fun getPreview(): Pair<List<RemoteSticker>, List<RemoteSticker>>? {
        val entry = previewCache.get() ?: return null
        return if (isValid(entry)) {
            Log.d(TAG, "Cache HIT: sticker preview")
            entry.data
        } else {
            Log.d(TAG, "Cache MISS (expired): sticker preview")
            previewCache.set(null)
            null
        }
    }

    fun putPreview(meme: List<RemoteSticker>, decor: List<RemoteSticker>) {
        previewCache.set(CacheEntry(Pair(meme, decor)))
        Log.d(TAG, "Cache PUT: sticker preview meme=${meme.size} decor=${decor.size}")
    }

    // ─── Page ─────────────────────────────────────────────────────

    fun getPage(folder: String, page: Int, limit: Int): List<RemoteSticker>? {
        val key = cacheKey(folder, page, limit)
        val entry = pageCache[key] ?: return null
        return if (isValid(entry)) {
            Log.d(TAG, "Cache HIT: $key")
            entry.data
        } else {
            Log.d(TAG, "Cache MISS (expired): $key")
            pageCache.remove(key)
            null
        }
    }

    fun putPage(folder: String, page: Int, limit: Int, data: List<RemoteSticker>) {
        val key = cacheKey(folder, page, limit)
        pageCache[key] = CacheEntry(data)
        Log.d(TAG, "Cache PUT: $key size=${data.size}")
    }

    // ─── Invalidation ─────────────────────────────────────────────

    fun invalidate() {
        previewCache.set(null)
        pageCache.clear()
        Log.d(TAG, "Cache INVALIDATED")
    }

    // ─── Internals ────────────────────────────────────────────────

    private fun isValid(entry: CacheEntry<*>): Boolean =
        System.currentTimeMillis() - entry.timestamp < TTL_MS

    private fun cacheKey(folder: String, page: Int, limit: Int) = "$folder:$page:$limit"

    private companion object {
        const val TAG = "StickerPageCache"

        /** 10 phút — sticker không thay đổi thường xuyên */
        const val TTL_MS = 10 * 60 * 1_000L
    }
}
