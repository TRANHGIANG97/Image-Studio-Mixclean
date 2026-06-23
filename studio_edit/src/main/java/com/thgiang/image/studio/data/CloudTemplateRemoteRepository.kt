package com.thgiang.image.studio.data

import android.util.Log
import com.thgiang.image.core.domain.model.template.CloudCategory
import com.thgiang.image.core.domain.model.template.CloudTemplate
import com.thgiang.image.core.domain.model.template.CloudTemplateParser
import com.thgiang.image.studio.BuildConfig
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class RemoteTemplateRow(
    val id: String,
    val title: String,
    val thumbnailUrl: String,
    val status: String,
    val categoryId: String,
    val cloudTemplate: CloudTemplate,
)

@Singleton
class CloudTemplateRemoteRepository @Inject constructor(
    private val cache: TemplateCache,
) {

    private val client = AdminWebJsonClient(BuildConfig.ADMIN_WEB_BASE_URL)

    fun fetchCategories(): List<CloudCategory> {
        cache.getCategories()?.let { return it }

        val root = client.getJson("/api/v1/categories")
        val categoriesArray = root.optJSONArray("categories") ?: return emptyList()

        val result = buildList {
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

        cache.putCategories(result)
        return result
    }

    fun fetchTemplatesForCategory(categoryId: String): List<RemoteTemplateRow> {
        if (categoryId.isBlank()) return emptyList()

        cache.getTemplates(categoryId)?.let { return it }

        val env = if (BuildConfig.DEBUG) "debug" else "release"
        val root = client.getJson(
            path = "/api/v1/templates",
            query = mapOf(
                "categoryId" to categoryId,
                "limit" to "50",
                "env" to env,
            ),
        )
        val result = parseTemplateRows(root)
        cache.putTemplates(categoryId, result)
        return result
    }

    /** Xóa cache — gọi khi user pull-to-refresh. */
    fun invalidateCache() = cache.invalidate()

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

                val cloudTemplate = CloudTemplateParser.parseFromApiItem(item)

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
                    )
                )
            }
        }
    }

    private companion object {
        const val TAG = "CloudTemplateRepo"
    }
}
