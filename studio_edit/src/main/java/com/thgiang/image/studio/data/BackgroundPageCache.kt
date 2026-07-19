package com.thgiang.image.studio.data

import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackgroundPageCache @Inject constructor() {

    private data class CacheEntry<T>(
        val data: T,
        val timestamp: Long = System.currentTimeMillis(),
    )

    private val previewCache = AtomicReference<CacheEntry<List<RemoteBackground>>?>()
    private val tabsCache = AtomicReference<CacheEntry<List<BackgroundTabInfo>>?>()
    private val pageCache = ConcurrentHashMap<String, CacheEntry<List<RemoteBackground>>>()

    fun getPreview(): List<RemoteBackground>? {
        val entry = previewCache.get() ?: return null
        return if (isValid(entry)) entry.data else {
            previewCache.set(null)
            null
        }
    }

    fun putPreview(data: List<RemoteBackground>) {
        previewCache.set(CacheEntry(data))
        Log.d(TAG, "Cache PUT: background preview size=${data.size}")
    }

    fun getTabs(): List<BackgroundTabInfo>? {
        val entry = tabsCache.get() ?: return null
        return if (isValid(entry)) entry.data else {
            tabsCache.set(null)
            null
        }
    }

    fun putTabs(tabs: List<BackgroundTabInfo>) {
        tabsCache.set(CacheEntry(tabs))
        Log.d(TAG, "Cache PUT: background tabs size=${tabs.size}")
    }

    fun getPage(folder: String, page: Int, limit: Int): List<RemoteBackground>? {
        val key = cacheKey(folder, page, limit)
        val entry = pageCache[key] ?: return null
        return if (isValid(entry)) entry.data else {
            pageCache.remove(key)
            null
        }
    }

    fun putPage(folder: String, page: Int, limit: Int, data: List<RemoteBackground>) {
        pageCache[cacheKey(folder, page, limit)] = CacheEntry(data)
    }

    fun invalidate() {
        previewCache.set(null)
        tabsCache.set(null)
        pageCache.clear()
        Log.d(TAG, "Cache INVALIDATED")
    }

    private fun isValid(entry: CacheEntry<*>): Boolean =
        System.currentTimeMillis() - entry.timestamp < TTL_MS

    private fun cacheKey(folder: String, page: Int, limit: Int) = "$folder:$page:$limit"

    private companion object {
        const val TAG = "BackgroundPageCache"
        const val TTL_MS = 10 * 60 * 1_000L
    }
}
