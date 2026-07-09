package com.thgiang.image.studio.data

import android.util.Log
import com.thgiang.image.core.domain.model.template.CloudCategory
import com.thgiang.image.core.domain.model.template.CloudTemplate
import com.thgiang.image.core.domain.model.template.CloudTemplateParser
import com.thgiang.image.core.domain.model.template.ParseOutcome
import com.thgiang.image.studio.BuildConfig
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

import java.io.File
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext

data class RemoteTemplateRow(
    val id: String,
    val title: String,
    val thumbnailUrl: String,
    val status: String,
    val categoryId: String,
    val cloudTemplate: CloudTemplate,
    val isPremium: Boolean = false,
)

@Singleton
class CloudTemplateRemoteRepository @Inject constructor(
    private val cache: TemplateCache,
    @ApplicationContext private val context: Context,
) {

    private val client = AdminWebJsonClient(BuildConfig.ADMIN_WEB_BASE_URL)

    fun fetchCategories(): List<CloudCategory> {
        cache.getCategories()?.let { return it }

        val result = try {
            val root = client.getJson("/api/v1/categories")
            val categoriesArray = root.optJSONArray("categories") ?: return emptyList()

            val list = buildList {
                for (index in 0 until categoriesArray.length()) {
                    val item = categoriesArray.optJSONObject(index) ?: continue
                    add(
                        CloudCategory(
                            id = item.optString("id"),
                            name = item.optString("name"),
                            order = item.optInt("order", index),
                            slug = item.optString("slug").takeIf { it.isNotBlank() },
                        )
                    )
                }
            }.sortedBy { it.order }
            saveCategoriesToDisk(root.toString())
            list
        } catch (e: Exception) {
            Log.e(TAG, "Network categories fetch failed, loading from disk cache", e)
            val cachedJson = loadCategoriesFromDisk()
            if (cachedJson != null) {
                try {
                    val root = JSONObject(cachedJson)
                    val categoriesArray = root.optJSONArray("categories") ?: return emptyList()
                    buildList {
                        for (index in 0 until categoriesArray.length()) {
                            val item = categoriesArray.optJSONObject(index) ?: continue
                            add(
                                CloudCategory(
                                    id = item.optString("id"),
                                    name = item.optString("name"),
                                    order = item.optInt("order", index),
                                    slug = item.optString("slug").takeIf { it.isNotBlank() },
                                )
                            )
                        }
                    }.sortedBy { it.order }
                } catch (pe: Exception) {
                    Log.e(TAG, "Parsing disk cache categories failed", pe)
                    emptyList()
                }
            } else {
                emptyList()
            }
        }

        cache.putCategories(result)
        return result
    }

    fun fetchTemplatesForCategory(categoryId: String): List<RemoteTemplateRow> {
        if (categoryId.isBlank()) return emptyList()

        cache.getTemplates(categoryId)?.let { return it }

        val env = if (BuildConfig.DEBUG) "debug" else "release"
        val result = try {
            val root = client.getJson(
                path = "/api/v1/templates",
                query = mapOf(
                    "categoryId" to categoryId,
                    "limit" to "50",
                    "env" to env,
                ),
            )
            val parsed = parseTemplateRows(root)
            if (parsed.isNotEmpty()) {
                saveTemplatesToDisk(categoryId, root.toString())
            }
            parsed
        } catch (e: Exception) {
            Log.e(TAG, "Network templates fetch failed for $categoryId, loading from disk cache", e)
            val cachedJson = loadTemplatesFromDisk(categoryId)
            if (cachedJson != null) {
                try {
                    parseTemplateRows(JSONObject(cachedJson))
                } catch (pe: Exception) {
                    Log.e(TAG, "Parsing disk cache templates failed for $categoryId", pe)
                    emptyList()
                }
            } else {
                emptyList()
            }
        }
        cache.putTemplates(categoryId, result)
        return result
    }

    private fun saveCategoriesToDisk(jsonStr: String) {
        runCatching {
            val dir = File(context.filesDir, "templates_cache")
            if (!dir.exists()) dir.mkdirs()
            File(dir, "categories.json").writeText(jsonStr)
        }.onFailure {
            Log.e(TAG, "Failed to save categories to disk", it)
        }
    }

    private fun loadCategoriesFromDisk(): String? {
        return runCatching {
            val file = File(context.filesDir, "templates_cache/categories.json")
            if (file.exists()) file.readText() else null
        }.getOrNull()
    }

    private fun saveTemplatesToDisk(categoryId: String, jsonStr: String) {
        runCatching {
            val dir = File(context.filesDir, "templates_cache")
            if (!dir.exists()) dir.mkdirs()
            File(dir, "templates_$categoryId.json").writeText(jsonStr)
        }.onFailure {
            Log.e(TAG, "Failed to save templates to disk for $categoryId", it)
        }
    }

    private fun loadTemplatesFromDisk(categoryId: String): String? {
        return runCatching {
            val file = File(context.filesDir, "templates_cache/templates_$categoryId.json")
            if (file.exists()) file.readText() else null
        }.getOrNull()
    }

    /** Xóa cache — gọi khi user pull-to-refresh. */
    fun invalidateCache() {
        cache.invalidate()
        runCatching {
            val dir = File(context.filesDir, "templates_cache")
            if (dir.exists()) {
                dir.deleteRecursively()
            }
        }
    }

    fun fetchTemplateById(templateId: String): CloudTemplate {
        require(templateId.isNotBlank()) { "templateId is blank" }

        val root = client.getJson(
            path = "/api/v1/templates",
            query = mapOf("templateId" to templateId),
        )
        val rows = parseTemplateRows(root)
        return rows.firstOrNull()?.cloudTemplate
            ?: throw IllegalStateException("Template not found: $templateId")
    }

    private fun parseTemplateRows(root: JSONObject): List<RemoteTemplateRow> {
        val templatesArray = root.optJSONArray("templates") ?: return emptyList()

        return buildList {
            for (index in 0 until templatesArray.length()) {
                val item = templatesArray.optJSONObject(index) ?: continue
                if (!item.has("canvas_data")) continue

                // Safe parse: template hỏng bị loại khỏi danh sách một cách có chủ đích
                // (kèm log) thay vì crash cả flow hoặc render trống vô danh.
                val cloudTemplate = when (val outcome = CloudTemplateParser.parseFromApiItemSafe(item)) {
                    is ParseOutcome.Success -> outcome.template
                    is ParseOutcome.Invalid -> {
                        Log.w(
                            TAG,
                            "Skipping invalid template '${outcome.templateId}': ${outcome.reason}"
                        )
                        continue
                    }
                }

                // Schema version guard: bỏ qua template yêu cầu schema mới hơn app hỗ trợ.
                // Tránh crash hoặc hiển thị lỗi trên bản Release cũ khi admin_web publish
                // template dùng tính năng mới.
                val templateSchemaVersion = cloudTemplate.metadata.schemaVersion
                if (templateSchemaVersion > CloudTemplateParser.SUPPORTED_SCHEMA_VERSION) {
                    Log.w(
                        TAG,
                        "Skipping template '${cloudTemplate.templateId}': " +
                            "requires schemaVersion=$templateSchemaVersion, " +
                            "app supports schemaVersion=${CloudTemplateParser.SUPPORTED_SCHEMA_VERSION}"
                    )
                    continue
                }

                add(
                    RemoteTemplateRow(
                        id = item.optString("template_id").ifBlank { item.optString("id") },
                        title = item.optString("title"),
                        thumbnailUrl = item.optString("thumbnail_url"),
                        status = item.optString("status"),
                        categoryId = item.optString("category_id"),
                        cloudTemplate = cloudTemplate,
                        isPremium = item.optBoolean("is_premium", false),
                    )
                )
            }
        }
    }

    private companion object {
        const val TAG = "CloudTemplateRepo"
    }
}
