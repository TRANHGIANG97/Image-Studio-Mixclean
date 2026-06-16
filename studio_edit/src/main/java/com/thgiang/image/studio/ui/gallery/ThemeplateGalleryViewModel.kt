package com.thgiang.image.studio.ui.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thgiang.image.studio.BuildConfig
import com.thgiang.image.core.domain.model.template.CloudCategory
import com.thgiang.image.core.domain.model.template.CloudLayer
import com.thgiang.image.core.domain.model.template.CloudPayload
import com.thgiang.image.core.domain.model.template.CloudTemplate
import com.thgiang.image.core.domain.model.template.CloudTransform
import com.thgiang.image.core.domain.model.template.TemplateCanvas
import com.thgiang.image.core.domain.model.template.TemplateMetadata
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject

data class RemoteThemeplateItem(
    val id: String,
    val title: String,
    val thumbnailUrl: String,
    val status: String,
    val categoryId: String,
    val cloudTemplate: CloudTemplate
)

@HiltViewModel
class ThemeplateGalleryViewModel @Inject constructor() : ViewModel() {

    private val _categories = MutableStateFlow<List<CloudCategory>>(fallbackCategories())
    val categories: StateFlow<List<CloudCategory>> = _categories.asStateFlow()

    private val _remoteTemplates = MutableStateFlow<List<RemoteThemeplateItem>>(emptyList())
    val remoteTemplates: StateFlow<List<RemoteThemeplateItem>> = _remoteTemplates.asStateFlow()

    private val _loadingRemoteTemplates = MutableStateFlow(false)
    val loadingRemoteTemplates: StateFlow<Boolean> = _loadingRemoteTemplates.asStateFlow()

    private val publicApiBaseUrl = BuildConfig.ADMIN_WEB_BASE_URL

    init {
        loadCategories()
    }

    fun loadTemplatesForCategory(categoryId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _loadingRemoteTemplates.value = true
            runCatching {
                fetchRemoteTemplates(categoryId)
            }.onSuccess { remoteItems ->
                _remoteTemplates.value = remoteItems
            }.onFailure { error ->
                android.util.Log.e(TAG, "Failed to load remote templates", error)
                _remoteTemplates.value = emptyList()
            }
            _loadingRemoteTemplates.value = false
        }
    }

    private fun loadCategories() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                fetchRemoteCategories()
            }.onSuccess { remoteCategories ->
                if (remoteCategories.isNotEmpty()) {
                    val merged = mergeWithFallbackCategories(remoteCategories)
                    _categories.value = merged
                    loadTemplatesForCategory(merged.first().id)
                } else {
                    loadFallbackData()
                }
            }.onFailure { error ->
                android.util.Log.e(TAG, "Failed to load remote categories", error)
                loadFallbackData()
            }
        }
    }

    private fun loadFallbackData() {
        val fallback = fallbackCategories()
        _categories.value = fallback
        loadTemplatesForCategory(fallback.firstOrNull()?.id.orEmpty())
    }

    private fun mergeWithFallbackCategories(remoteCategories: List<CloudCategory>): List<CloudCategory> {
        val fallback = fallbackCategories()
        val fallbackIds = fallback.map { it.id }.toSet()
        val defaultCategoryIds = setOf("professional", "cosmetics", "digital_life", "selfie_food")
        return buildList {
            addAll(fallback)
            addAll(
                remoteCategories.filterNot { remote ->
                    remote.id in fallbackIds || canonicalCategoryId(remote.name) in defaultCategoryIds
                }
            )
        }
    }

    private fun fallbackCategories(): List<CloudCategory> = listOf(
        CloudCategory("professional", "Chuyên nghiệp", 0),
        CloudCategory("cosmetics", "Mẫu Mỹ Phẩm", 1),
        CloudCategory("digital_life", "Đời sống số", 2),
        CloudCategory("selfie_food", "Mê ăn uống", 3)
    )

    private fun canonicalCategoryId(name: String): String? {
        val normalized = Normalizer.normalize(name, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")
            .trim()

        return when {
            "chuyen nghiep" in normalized -> "professional"
            "my pham" in normalized -> "cosmetics"
            "doi song so" in normalized -> "digital_life"
            "me an uong" in normalized || "dam me an uong" in normalized -> "selfie_food"
            else -> null
        }
    }

    private fun fetchRemoteCategories(): List<CloudCategory> {
        val connection = (URL("$publicApiBaseUrl/api/v1/categories").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5000
            readTimeout = 5000
            setRequestProperty("Accept", "application/json")
        }

        try {
            val body = if (connection.responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }

            if (body.isBlank()) return emptyList()

            val root = JSONObject(body)
            if (!root.optBoolean("success", false)) return emptyList()

            val categoriesArray = root.optJSONArray("categories") ?: return emptyList()
            return buildList {
                for (index in 0 until categoriesArray.length()) {
                    val item = categoriesArray.optJSONObject(index) ?: continue
                    add(
                        CloudCategory(
                            id = item.optString("id"),
                            name = item.optString("name"),
                            order = item.optInt("order", index)
                        )
                    )
                }
            }.sortedBy { it.order }
        } finally {
            connection.disconnect()
        }
    }

    private fun fetchRemoteTemplates(categoryId: String): List<RemoteThemeplateItem> {
        if (categoryId.isBlank()) return emptyList()

        val env = if (BuildConfig.DEBUG) "debug" else "release"
        val connection = (URL("$publicApiBaseUrl/api/v1/templates?categoryId=$categoryId&limit=50&env=$env").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5000
            readTimeout = 5000
            setRequestProperty("Accept", "application/json")
        }

        try {
            val body = if (connection.responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }

            if (body.isBlank()) return emptyList()

            val root = JSONObject(body)
            if (!root.optBoolean("success", false)) return emptyList()

            val templatesArray = root.optJSONArray("templates") ?: return emptyList()
            return buildList {
                for (index in 0 until templatesArray.length()) {
                    val item = templatesArray.optJSONObject(index) ?: continue
                    val canvasData = item.optJSONObject("canvas_data") ?: continue
                    add(
                        RemoteThemeplateItem(
                            id = item.optString("template_id").ifBlank { item.optString("id") },
                            title = item.optString("title"),
                            thumbnailUrl = item.optString("thumbnail_url"),
                            status = item.optString("status"),
                            categoryId = item.optString("category_id"),
                            cloudTemplate = parseCloudTemplate(
                                item.optString("template_id").ifBlank { item.optString("id") },
                                item.optString("category_id"),
                                canvasData,
                                item.optString("title"),
                                item.optString("thumbnail_url"),
                                item.optString("status")
                            )
                        )
                    )
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun parseCloudTemplate(
        templateId: String,
        categoryId: String,
        canvasData: JSONObject,
        title: String,
        thumbnailUrl: String,
        status: String
    ): CloudTemplate {
        val metadataJson = canvasData.optJSONObject("metadata")
        val canvasJson = canvasData.optJSONObject("canvas")

        return CloudTemplate(
            templateId = canvasData.optString("templateId", templateId),
            categoryId = canvasData.optString("categoryId", categoryId),
            metadata = TemplateMetadata(
                title = metadataJson?.optString("title") ?: title,
                thumbnailUrl = metadataJson?.optString("thumbnailUrl")?.takeIf { it.isNotBlank() } ?: thumbnailUrl,
                status = metadataJson?.optString("status")?.takeIf { it.isNotBlank() } ?: status,
                schemaVersion = metadataJson?.optInt("schemaVersion", 1) ?: 1,
                createdAt = metadataJson?.optLong("createdAt", System.currentTimeMillis()) ?: System.currentTimeMillis(),
                updatedAt = metadataJson?.optLong("updatedAt", System.currentTimeMillis()) ?: System.currentTimeMillis()
            ),
            canvas = TemplateCanvas(
                baseWidth = canvasJson?.optInt("baseWidth", 1080) ?: 1080,
                baseHeight = canvasJson?.optInt("baseHeight", 1920) ?: 1920,
                aspectRatio = canvasJson?.optString("aspectRatio")?.takeIf { it.isNotBlank() } ?: "9:16",
                backgroundUrl = canvasJson?.optString("backgroundUrl")?.takeIf { it.isNotBlank() && it != "null" },
                backgroundColorArgb = canvasJson?.takeIf { it.has("backgroundColorArgb") }?.optInt("backgroundColorArgb")
            ),
            layers = parseLayers(canvasData.optJSONArray("layers"))
        )
    }

    private fun parseLayers(layersArray: JSONArray?): List<CloudLayer> {
        if (layersArray == null) return emptyList()

        return buildList {
            for (index in 0 until layersArray.length()) {
                val item = layersArray.optJSONObject(index) ?: continue
                val transformJson = item.optJSONObject("transform")
                val payloadJson = item.optJSONObject("payload")

                add(
                    CloudLayer(
                        layerId = item.optString("layerId"),
                        type = item.optString("type", "IMAGE"),
                        zIndex = item.optInt("zIndex", index),
                        transform = CloudTransform(
                            anchorX = transformJson?.optDouble("anchorX", 0.5)?.toFloat() ?: 0.5f,
                            anchorY = transformJson?.optDouble("anchorY", 0.5)?.toFloat() ?: 0.5f,
                            scale = transformJson?.optDouble("scale", 1.0)?.toFloat() ?: 1.0f,
                            rotation = transformJson?.optDouble("rotation", 0.0)?.toFloat() ?: 0.0f
                        ),
                        payload = CloudPayload(
                            defaultImageUrl = payloadJson?.optString("defaultImageUrl")?.takeIf { it.isNotBlank() },
                            imageUrl = payloadJson?.optString("imageUrl")?.takeIf { it.isNotBlank() },
                            sourceKind = payloadJson?.optString("sourceKind")?.takeIf { it.isNotBlank() },
                            shadowIntensity = payloadJson?.optDouble("shadowIntensity")?.toFloat()?.takeUnless { it.isNaN() },
                            shadowAngle = payloadJson?.optDouble("shadowAngle")?.toFloat()?.takeUnless { it.isNaN() },
                            shadowDistance = payloadJson?.optDouble("shadowDistance")?.toFloat()?.takeUnless { it.isNaN() },
                            shadowBlur = payloadJson?.optDouble("shadowBlur")?.toFloat()?.takeUnless { it.isNaN() },
                            alpha = payloadJson?.optDouble("alpha")?.toFloat()?.takeUnless { it.isNaN() },
                            shadowColorArgb = payloadJson?.optInt("shadowColorArgb"),
                            cropRatio = payloadJson?.optString("cropRatio")?.takeIf { it.isNotBlank() },
                            flippedH = payloadJson?.optBoolean("flippedH"),
                            flippedV = payloadJson?.optBoolean("flippedV"),
                            baseWidth = payloadJson?.optInt("baseWidth"),
                            baseHeight = payloadJson?.optInt("baseHeight"),
                            text = payloadJson?.optString("text")?.takeIf { it.isNotBlank() },
                            font = payloadJson?.optString("font")?.takeIf { it.isNotBlank() },
                            textColorArgb = payloadJson?.takeIf { it.has("textColorArgb") }?.optInt("textColorArgb"),
                            fontSize = payloadJson?.takeIf { it.has("fontSize") }?.optDouble("fontSize")?.toFloat()?.takeUnless { it.isNaN() },
                            fontWeight = payloadJson?.optString("fontWeight")?.takeIf { it.isNotBlank() && it != "null" },
                            fontStyle = payloadJson?.optString("fontStyle")?.takeIf { it.isNotBlank() && it != "null" },
                            textAlign = payloadJson?.optString("textAlign")?.takeIf { it.isNotBlank() && it != "null" }
                        )
                    )
                )
            }
        }
    }

    private companion object {
        const val TAG = "ThemeplateGalleryVM"
    }
}
