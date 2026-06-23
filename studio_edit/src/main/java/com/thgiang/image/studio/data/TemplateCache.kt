package com.thgiang.image.studio.data

import android.util.Log
import com.thgiang.image.core.domain.model.template.CloudCategory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cache in-memory cho templates và categories với TTL.
 *
 * Mục đích:
 *  - Tránh gọi API lại mỗi lần user mở màn hình Gallery/Home.
 *  - Giảm tải server và cải thiện tốc độ hiển thị.
 *
 * Chiến lược TTL:
 *  - Categories: [CATEGORY_TTL_MS] (mặc định 30 phút) — ít thay đổi.
 *  - Templates per category: [TEMPLATE_TTL_MS] (mặc định 30 phút).
 *
 * Gọi [invalidate] khi user pull-to-refresh để force tải lại từ server.
 */
@Singleton
class TemplateCache @Inject constructor() {

    private data class CacheEntry<T>(
        val data: T,
        val timestamp: Long = System.currentTimeMillis(),
    )

    private val categoryCache = AtomicReference<CacheEntry<List<CloudCategory>>?>()
    private val templateCache = ConcurrentHashMap<String, CacheEntry<List<RemoteTemplateRow>>>()

    // ─── Categories ───────────────────────────────────────────────

    /** Trả về danh sách categories nếu còn trong TTL, null nếu đã hết hạn. */
    fun getCategories(): List<CloudCategory>? {
        val entry = categoryCache.get() ?: return null
        return if (isValid(entry)) {
            Log.d(TAG, "Cache HIT: categories")
            entry.data
        } else {
            Log.d(TAG, "Cache MISS (expired): categories")
            categoryCache.set(null)
            null
        }
    }

    fun putCategories(list: List<CloudCategory>) {
        categoryCache.set(CacheEntry(list))
        Log.d(TAG, "Cache PUT: ${list.size} categories")
    }

    // ─── Templates ────────────────────────────────────────────────

    /** Trả về danh sách templates cho categoryId nếu còn trong TTL, null nếu đã hết hạn. */
    fun getTemplates(categoryId: String): List<RemoteTemplateRow>? {
        val entry = templateCache[categoryId] ?: return null
        return if (isValid(entry)) {
            Log.d(TAG, "Cache HIT: templates for $categoryId")
            entry.data
        } else {
            Log.d(TAG, "Cache MISS (expired): templates for $categoryId")
            templateCache.remove(categoryId)
            null
        }
    }

    fun putTemplates(categoryId: String, list: List<RemoteTemplateRow>) {
        templateCache[categoryId] = CacheEntry(list)
        Log.d(TAG, "Cache PUT: ${list.size} templates for $categoryId")
    }

    // ─── Invalidation ─────────────────────────────────────────────

    /**
     * Xóa toàn bộ cache — gọi khi user pull-to-refresh hoặc khi cần
     * đảm bảo dữ liệu mới nhất được tải từ server.
     */
    fun invalidate() {
        categoryCache.set(null)
        templateCache.clear()
        Log.d(TAG, "Cache INVALIDATED")
    }

    // ─── Internals ────────────────────────────────────────────────

    private fun isValid(entry: CacheEntry<*>): Boolean {
        return System.currentTimeMillis() - entry.timestamp < TTL_MS
    }

    private companion object {
        const val TAG = "TemplateCache"

        /** TTL chung: 30 phút. Templates và categories đều dùng cùng TTL để đơn giản. */
        const val TTL_MS = 30 * 60 * 1000L
    }
}
