package com.thgiang.image.studio.data

import android.util.Log
import com.thgiang.image.studio.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data model cho một sticker từ remote (admin_web / CDN R2).
 */
data class RemoteSticker(
    val id: String,
    val url: String,
)

/**
 * Metadata cho một tab nhãn dán — map 1:1 với folder trong Media Library.
 */
data class StickerTabInfo(
    val folder: String,
    val label: String,
    val count: Int = 0,
)

/**
 * Repository lấy sticker từ admin_web API.
 *
 * Tái sử dụng [AdminWebJsonClient] để thống nhất HTTP config (timeout, base URL).
 * Cache kết quả bằng [StickerPageCache] để tránh gọi API lặp lại trong session.
 *
 * Sử dụng trực tiếp endpoint `/api/assets` có sẵn để đảm bảo tương thích tốt
 * trên cả local dev và bản deploy Vercel thực tế (không bị lỗi 404).
 */
@Singleton
class StickerRemoteRepository @Inject constructor(
    private val cache: StickerPageCache,
) {

    private val client = AdminWebJsonClient(BuildConfig.ADMIN_WEB_BASE_URL)

    companion object {
        private const val TAG = "StickerRemoteRepo"

        const val DEFAULT_PREVIEW_FOLDER = "materials_icon"
        const val PREVIEW_LIMIT = 20
        const val PAGE_LIMIT = 30

        /** Sticker library — materials_* only; never backgrounds_* */
        fun isMaterialsStickerFolder(folder: String): Boolean =
            AssetFolderLabels.isMaterialsStickerFolder(folder)
    }

    // ─── Dynamic Tabs ─────────────────────────────────────────────

    /**
     * Lấy danh sách tab nhãn dán từ Media Library.
     * Chỉ folder `materials_*` có ít nhất 1 ảnh/SVG → 1 tab.
     */
    fun fetchStickerTabs(): List<StickerTabInfo> {
        cache.getTabs()?.let { cached ->
            return cached
                .filter { isMaterialsStickerFolder(it.folder) }
                .map { it.copy(label = AssetFolderLabels.materialsTabLabel(it.folder)) }
        }

        return try {
            val root = client.getJson(path = "/api/v1/stickers/folders")
            val array = root.optJSONArray("folders") ?: JSONArray()
            val tabs = buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val folder = item.optString("id").ifBlank { continue }
                    if (!isMaterialsStickerFolder(folder)) continue
                    val count = item.optInt("count", 0)
                    add(
                        StickerTabInfo(
                            folder = folder,
                            label = AssetFolderLabels.materialsTabLabel(folder),
                            count = count,
                        ),
                    )
                }
            }
            cache.putTabs(tabs)
            Log.d(TAG, "fetchStickerTabs: ${tabs.size} tabs")
            tabs
        } catch (e: Exception) {
            Log.e(TAG, "fetchStickerTabs failed", e)
            throw e
        }
    }

    // ─── Preview ──────────────────────────────────────────────────

    /**
     * Tải 20 icon mặc định từ materials_icon.
     * Thử `/api/v1/stickers/preview` trước, fallback `/api/assets` nếu rỗng hoặc endpoint chưa deploy.
     */
    fun fetchPreview(
        folder: String = DEFAULT_PREVIEW_FOLDER,
        limit: Int = PREVIEW_LIMIT,
    ): List<RemoteSticker> {
        cache.getPreview()?.let { return it }

        val fromPreviewEndpoint = try {
            fetchPreviewFromDedicatedEndpoint(folder, limit)
        } catch (e: Exception) {
            Log.w(TAG, "fetchPreview dedicated endpoint failed, trying /api/assets", e)
            emptyList()
        }

        if (fromPreviewEndpoint.isNotEmpty()) {
            cache.putPreview(fromPreviewEndpoint)
            Log.d(TAG, "fetchPreview: folder=$folder size=${fromPreviewEndpoint.size} (preview API)")
            return fromPreviewEndpoint
        }

        val fromAssets = fetchPage(folder, page = 1, limit = limit).stickers
        if (fromAssets.isNotEmpty()) {
            cache.putPreview(fromAssets)
        }
        Log.d(TAG, "fetchPreview: folder=$folder size=${fromAssets.size} (assets fallback)")
        return fromAssets
    }

    private fun fetchPreviewFromDedicatedEndpoint(folder: String, limit: Int): List<RemoteSticker> {
        val root = client.getJson(
            path = "/api/v1/stickers/preview",
            query = mapOf(
                "folder" to folder,
                "limit" to limit.toString(),
            ),
        )
        return parsePreviewArray(root.optJSONArray("stickers"))
            .ifEmpty { parsePreviewArray(root.optJSONArray("meme")) }
    }

    // ─── Paginated Gallery ────────────────────────────────────────

    /**
     * Tải trang sticker phân trang cho gallery.
     * Kết quả được cache theo key folder:page:limit trong 10 phút.
     *
     * @param folder  Tên thư mục materials_* (vd. materials_icon, materials_christmas)
     * @param page    Số trang, bắt đầu từ 1
     * @param limit   Số sticker mỗi trang (mặc định [PAGE_LIMIT] = 30)
     * @return [StickerPage] chứa data và thông tin phân trang
     */
    fun fetchPage(folder: String, page: Int, limit: Int = PAGE_LIMIT): StickerPage {
        if (!isMaterialsStickerFolder(folder)) {
            Log.w(TAG, "fetchPage skipped non-materials folder: $folder")
            return StickerPage(stickers = emptyList(), hasMore = false, page = page)
        }

        // Thử lấy từ cache trước
        cache.getPage(folder, page, limit)?.let {
            return StickerPage(stickers = it, hasMore = it.size >= limit, page = page)
        }

        return try {
            val root = client.getJson(
                path = "/api/assets",
                query = mapOf(
                    "folder" to folder,
                    "page" to page.toString(),
                    "limit" to limit.toString(),
                ),
            )
            val stickers = parseArray(root.optJSONArray("assets"))
            val hasMore = root.optBoolean("hasMore", false)
            cache.putPage(folder, page, limit, stickers)
            Log.d(TAG, "fetchPage: folder=$folder page=$page size=${stickers.size} hasMore=$hasMore")
            StickerPage(stickers = stickers, hasMore = hasMore, page = page)
        } catch (e: Exception) {
            Log.e(TAG, "fetchPage failed: folder=$folder page=$page", e)
            throw e
        }
    }

    /** Xóa toàn bộ cache — gọi khi cần refresh */
    fun invalidateCache() = cache.invalidate()

    // ─── Internals ────────────────────────────────────────────────

    private fun parsePreviewArray(array: JSONArray?): List<RemoteSticker> {
        array ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val item: JSONObject = array.optJSONObject(i) ?: continue
                val id = item.optString("id").ifBlank { continue }
                val url = item.optString("url").ifBlank {
                    item.optString("file_url").ifBlank { continue }
                }
                add(RemoteSticker(id = id, url = url))
            }
        }
    }

    private fun parseArray(array: JSONArray?): List<RemoteSticker> {
        array ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val item: JSONObject = array.optJSONObject(i) ?: continue
                val id = item.optString("id").ifBlank { continue }
                // Đọc file_url hoặc url dự phòng để tương thích cả 2 chuẩn API
                val url = item.optString("file_url").ifBlank {
                    item.optString("url").ifBlank { continue }
                }
                add(RemoteSticker(id = id, url = url))
            }
        }
    }
}

/**
 * Kết quả một trang sticker từ gallery.
 */
data class StickerPage(
    val stickers: List<RemoteSticker>,
    val hasMore: Boolean,
    val page: Int,
)
