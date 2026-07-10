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
        const val FOLDER_MEME = "svg_undraw"
        const val FOLDER_DECOR = "sticker_decor"
        const val PREVIEW_LIMIT = 10
        const val PAGE_LIMIT = 30
    }

    // ─── Preview ──────────────────────────────────────────────────

    /**
     * Tải 10 sticker meme + 10 sticker decor.
     * Kết quả được cache trong bộ nhớ trong 10 phút.
     *
     * @return Pair(memeList, decorList)
     */
    fun fetchPreview(): Pair<List<RemoteSticker>, List<RemoteSticker>> {
        // Thử lấy từ cache trước
        cache.getPreview()?.let { return it }

        return try {
            // Tải meme từ endpoint chính
            val memeRoot = client.getJson(
                path = "/api/assets",
                query = mapOf(
                    "folder" to FOLDER_MEME,
                    "limit" to PREVIEW_LIMIT.toString(),
                ),
            )
            // Tải decor từ endpoint chính
            val decorRoot = client.getJson(
                path = "/api/assets",
                query = mapOf(
                    "folder" to FOLDER_DECOR,
                    "limit" to PREVIEW_LIMIT.toString(),
                ),
            )

            val meme = parseArray(memeRoot.optJSONArray("assets"))
            val decor = parseArray(decorRoot.optJSONArray("assets"))
            val result = Pair(meme, decor)
            cache.putPreview(meme, decor)
            Log.d(TAG, "fetchPreview: meme=${meme.size} decor=${decor.size}")
            result
        } catch (e: Exception) {
            Log.e(TAG, "fetchPreview failed", e)
            throw e // Ném ra ngoài để ViewModel cập nhật previewError = true
        }
    }

    // ─── Paginated Gallery ────────────────────────────────────────

    /**
     * Tải trang sticker phân trang cho gallery.
     * Kết quả được cache theo key folder:page:limit trong 10 phút.
     *
     * @param folder  Tên thư mục ("sticker_meme" hoặc "sticker_decor")
     * @param page    Số trang, bắt đầu từ 1
     * @param limit   Số sticker mỗi trang (mặc định [PAGE_LIMIT] = 30)
     * @return [StickerPage] chứa data và thông tin phân trang
     */
    fun fetchPage(folder: String, page: Int, limit: Int = PAGE_LIMIT): StickerPage {
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
