package com.thgiang.image.studio.data

import android.util.Log
import com.thgiang.image.studio.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class RemoteBackground(
    val id: String,
    val url: String,
    val folder: String = "",
)

data class BackgroundTabInfo(
    val folder: String,
    val label: String,
    val count: Int = 0,
)

data class BackgroundPage(
    val backgrounds: List<RemoteBackground>,
    val hasMore: Boolean,
    val page: Int,
)

@Singleton
class BackgroundRemoteRepository @Inject constructor(
    private val cache: BackgroundPageCache,
) {

    private val client = AdminWebJsonClient(BuildConfig.ADMIN_WEB_BASE_URL)

    companion object {
        private const val TAG = "BackgroundRemoteRepo"
        const val PREVIEW_LIMIT = 20
        const val PAGE_LIMIT = 30
    }

    fun fetchBackgroundTabs(): List<BackgroundTabInfo> {
        cache.getTabs()?.let { cached ->
            return cached
                .filter { AssetFolderLabels.isBackgroundTabFolder(it.folder) }
                .map { it.copy(label = AssetFolderLabels.backgroundTabLabel(it.folder)) }
        }

        return try {
            val root = client.getJson(path = "/api/v1/backgrounds/folders")
            val array = root.optJSONArray("folders") ?: JSONArray()
            val tabs = buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val folder = item.optString("id").ifBlank { continue }
                    if (!AssetFolderLabels.isBackgroundTabFolder(folder)) continue
                    val count = item.optInt("count", 0)
                    add(
                        BackgroundTabInfo(
                            folder = folder,
                            label = AssetFolderLabels.backgroundTabLabel(folder),
                            count = count,
                        ),
                    )
                }
            }
            cache.putTabs(tabs)
            Log.d(TAG, "fetchBackgroundTabs: ${tabs.size} tabs")
            tabs
        } catch (e: Exception) {
            Log.e(TAG, "fetchBackgroundTabs failed", e)
            emptyList()
        }
    }

    fun fetchPreview(limit: Int = PREVIEW_LIMIT): List<RemoteBackground> {
        cache.getPreview()?.let { return it }

        return try {
            val root = client.getJson(
                path = "/api/v1/backgrounds/preview",
                query = mapOf("limit" to limit.toString()),
            )
            val backgrounds = parseArray(root.optJSONArray("backgrounds"))
            cache.putPreview(backgrounds)
            Log.d(TAG, "fetchPreview: size=${backgrounds.size}")
            backgrounds
        } catch (e: Exception) {
            Log.e(TAG, "fetchPreview failed", e)
            emptyList()
        }
    }

    fun fetchPage(folder: String, page: Int, limit: Int = PAGE_LIMIT): BackgroundPage {
        if (!AssetFolderLabels.isBackgroundTabFolder(folder)) {
            Log.w(TAG, "fetchPage skipped non-background tab folder: $folder")
            return BackgroundPage(backgrounds = emptyList(), hasMore = false, page = page)
        }

        cache.getPage(folder, page, limit)?.let {
            return BackgroundPage(backgrounds = it, hasMore = it.size >= limit, page = page)
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
            val backgrounds = parseArray(root.optJSONArray("assets"))
            val hasMore = root.optBoolean("hasMore", false)
            cache.putPage(folder, page, limit, backgrounds)
            Log.d(TAG, "fetchPage: folder=$folder page=$page size=${backgrounds.size}")
            BackgroundPage(backgrounds = backgrounds, hasMore = hasMore, page = page)
        } catch (e: Exception) {
            Log.e(TAG, "fetchPage failed: folder=$folder page=$page", e)
            throw e
        }
    }

    fun invalidateCache() = cache.invalidate()

    private fun parseArray(array: JSONArray?): List<RemoteBackground> {
        array ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val item: JSONObject = array.optJSONObject(i) ?: continue
                val id = item.optString("id").ifBlank { continue }
                val url = item.optString("file_url").ifBlank {
                    item.optString("url").ifBlank { continue }
                }
                val folder = item.optString("folder", "")
                add(RemoteBackground(id = id, url = url, folder = folder))
            }
        }
    }
}
