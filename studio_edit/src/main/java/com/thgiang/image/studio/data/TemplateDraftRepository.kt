package com.thgiang.image.studio.data
import com.thgiang.image.studio.ui.editor.*

import android.content.Context
import com.thgiang.image.studio.ui.editor.model.*
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class TemplateDraftMeta(
    val id: String? = null,
    val name: String? = null,
    val templateAssetPath: String? = null,
    val templateObjectAssetPath: String? = null,
    val templateThumbnailUrl: String? = null,
    /** Real cloud template id — used to reload dimensions when themeplateId is "draft". */
    val cloudTemplateId: String? = null,
    val isTemplate: Boolean = false,
)

data class DraftRestoreBundle(
    val state: EditorState?,
    val meta: TemplateDraftMeta?,
    /** QuickEdit [project_state.json] without [editor_state.json]. */
    val hasLegacyProjectState: Boolean,
    val editorStateCorrupt: Boolean,
)

class UriTypeAdapter : TypeAdapter<Uri>() {
    override fun write(out: JsonWriter, value: Uri?) {
        if (value == null) {
            out.nullValue()
        } else {
            out.value(value.toString())
        }
    }
    override fun read(`in`: JsonReader): Uri? {
        if (`in`.peek() == com.google.gson.stream.JsonToken.NULL) {
            `in`.nextNull()
            return null
        }
        return Uri.parse(`in`.nextString())
    }
}

@Singleton
class TemplateDraftRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Dùng [filesDir] thay vì [cacheDir] để Android không tự xóa draft
     * khi bộ nhớ thiếu. Đồng bộ với DraftManager.
     */
    private val draftsDir = File(context.filesDir, "drafts")
    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(Uri::class.java, UriTypeAdapter())
        .registerTypeAdapter(EditorTool::class.java, EditorToolTypeAdapter())
        .create()

    fun saveDraft(
        draftId: String?,
        name: String,
        state: EditorState,
        templateAssetPath: String,
        templateObjectAssetPath: String?,
        templateThumbnailUrl: String? = null,
        cloudTemplateId: String? = null,
    ): String {
        draftsDir.mkdirs()
        val id = draftId ?: UUID.randomUUID().toString()
        val dir = File(draftsDir, id)
        dir.mkdirs()

        // Thư mục chứa ảnh được sao chép từ content://
        val imagesDir = File(dir, "images")
        imagesDir.mkdirs()

        // Sao chép tất cả ảnh content:// vào thư mục draft và thay bằng file:// path
        val persistedLayers = state.layers.map { layer ->
            val persistedProduct = persistProductImages(layer.product, imagesDir, layer.id)
            layer.copy(product = persistedProduct)
        }

        val now = System.currentTimeMillis()
        val existingMeta = loadMetadataMap(id)
        val createdAt = (existingMeta?.get("createdAt") as? Number)?.toLong() ?: now
        val resolvedCloudTemplateId = cloudTemplateId?.takeIf { it.isNotBlank() }
            ?: (existingMeta?.get("cloudTemplateId") as? String)?.takeIf { it.isNotBlank() }
        val metaMap = mutableMapOf<String, Any?>(
            "id" to id,
            "name" to (existingMeta?.get("name") as? String ?: name),
            "createdAt" to createdAt,
            "updatedAt" to now,
            "isTemplate" to true,
            "templateAssetPath" to templateAssetPath,
            "templateObjectAssetPath" to templateObjectAssetPath,
            "templateThumbnailUrl" to templateThumbnailUrl,
        )
        resolvedCloudTemplateId?.let { metaMap["cloudTemplateId"] = it }
        (existingMeta?.get("thumbnailPath") as? String)
            ?.takeIf { it.isNotBlank() }
            ?.let { metaMap["thumbnailPath"] = it }
        atomicWriteText(File(dir, "metadata.json"), gson.toJson(metaMap))
        val persistedState = state.copy(
            layers = persistedLayers,
            exportResult = null,
            isExporting = false,
        )
        atomicWriteText(File(dir, "editor_state.json"), gson.toJson(persistedState))
        return id
    }

    fun updateThumbnailPath(draftId: String, thumbnailAbsolutePath: String) {
        val dir = File(draftsDir, draftId)
        val metaFile = File(dir, "metadata.json")
        if (!metaFile.exists()) return
        try {
            val meta = loadMetadataMap(draftId)?.toMutableMap() ?: return
            meta["thumbnailPath"] = thumbnailAbsolutePath
            meta["updatedAt"] = System.currentTimeMillis()
            atomicWriteText(metaFile, gson.toJson(meta))
        } catch (e: Exception) {
            android.util.Log.w("TemplateDraftRepo", "Failed to update thumbnailPath: ${e.message}")
        }
    }

    private fun loadMetadataMap(draftId: String): Map<String, Any?>? {
        val metaFile = File(File(draftsDir, draftId), "metadata.json")
        if (!metaFile.exists()) return null
        return try {
            @Suppress("UNCHECKED_CAST")
            gson.fromJson(metaFile.readText(), Map::class.java) as? Map<String, Any?>
        } catch (e: Exception) {
            android.util.Log.w("TemplateDraftRepo", "Failed to read metadata for $draftId: ${e.message}")
            null
        }
    }

    /** Write via temp file + rename to avoid half-written JSON on crash/kill. */
    private fun atomicWriteText(target: File, content: String) {
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "${target.name}.tmp")
        temp.writeText(content)
        if (!temp.renameTo(target)) {
            target.writeText(content)
            temp.delete()
        }
    }

    /**
     * Sao chép ảnh trong [EditorProduct] từ URI tạm thời (content://) sang
     * file vĩnh viễn trong thư mục draft. Trả về bản copy của product với
     * URI đã được thay bằng file:// path bền vững.
     *
     * Các URI https:// (ảnh nền cloud) được giữ nguyên — Coil sẽ cache chúng.
     */
    private fun persistProductImages(
        product: EditorProduct,
        imagesDir: File,
        layerId: String,
    ): EditorProduct {
        val newOriginal = persistUriIfContent(
            uriString = product.originalUriString,
            targetFile = File(imagesDir, "${layerId}_original.jpg"),
        )
        val newForeground = persistUriIfContent(
            uriString = product.foregroundUriString,
            targetFile = File(imagesDir, "${layerId}_foreground.png"),
        )
        return if (newOriginal != product.originalUriString || newForeground != product.foregroundUriString) {
            product.copy(
                originalUriString = newOriginal,
                foregroundUriString = newForeground,
            )
        } else {
            product
        }
    }

    /**
     * Sao chép dữ liệu tại [uriString] (nếu là content://) sang [targetFile].
     * Trả về đường dẫn file:// mới nếu thành công; ném [IOException] nếu copy thất bại.
     */
    private fun persistUriIfContent(uriString: String?, targetFile: File): String? {
        if (uriString == null) return null
        // Chỉ xử lý content:// — file:// và https:// giữ nguyên
        if (!uriString.startsWith("content://")) return uriString

        try {
            val uri = Uri.parse(uriString)
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IOException("Cannot open input stream for $uriString")
            input.use { stream ->
                targetFile.outputStream().use { output -> stream.copyTo(output) }
            }
            if (!targetFile.exists() || targetFile.length() <= 0L) {
                throw IOException("Copied file is empty for $uriString")
            }
            return "file://${targetFile.absolutePath}"
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("TemplateDraftRepo", "Failed to persist URI $uriString: ${e.message}")
            throw IOException("Failed to persist image URI: $uriString", e)
        }
    }

    fun loadDraftMeta(draftId: String): TemplateDraftMeta? {
        val dir = File(draftsDir, draftId)
        val metaFile = File(dir, "metadata.json")
        if (!metaFile.exists()) return null
        return try {
            @Suppress("UNCHECKED_CAST")
            val map = gson.fromJson(metaFile.readText(), Map::class.java) as? Map<*, *> ?: return null
            TemplateDraftMeta(
                id = map["id"] as? String,
                name = map["name"] as? String,
                templateAssetPath = map["templateAssetPath"] as? String,
                templateObjectAssetPath = map["templateObjectAssetPath"] as? String,
                templateThumbnailUrl = map["templateThumbnailUrl"] as? String,
                cloudTemplateId = map["cloudTemplateId"] as? String,
                isTemplate = map["isTemplate"] as? Boolean ?: false,
            )
        } catch (e: Exception) {
            android.util.Log.w("TemplateDraftRepo", "Failed to load metadata for $draftId", e)
            null
        }
    }

    fun resolveDraftRestore(draftId: String): DraftRestoreBundle {
        val dir = File(draftsDir, draftId)
        if (!dir.exists()) {
            return DraftRestoreBundle(
                state = null,
                meta = null,
                hasLegacyProjectState = false,
                editorStateCorrupt = false,
            )
        }
        val meta = loadDraftMeta(draftId)
        val hasEditorState = File(dir, "editor_state.json").exists()
        val hasLegacy = File(dir, "project_state.json").exists() && !hasEditorState
        if (hasLegacy) {
            return DraftRestoreBundle(
                state = null,
                meta = meta,
                hasLegacyProjectState = true,
                editorStateCorrupt = false,
            )
        }
        val state = if (hasEditorState) loadDraft(draftId) else null
        return DraftRestoreBundle(
            state = state,
            meta = meta,
            hasLegacyProjectState = false,
            editorStateCorrupt = hasEditorState && state == null,
        )
    }

    fun loadDraft(draftId: String): EditorState? {
        val dir = File(draftsDir, draftId)
        val file = File(dir, "editor_state.json")
        if (!file.exists()) return null
        return try {
            val json = file.readText()
            val root = JsonParser.parseString(json).asJsonObject
            val state = gson.fromJson(root, EditorState::class.java) ?: return null
            // Re-parse each layer explicitly. With R8 + type erasure, Gson may leave
            // List<EditorLayer> as List<LinkedTreeMap>, which then ClassCastExceptions
            // inside EditorLayerNormalizer.normalize during ViewModel init.
            val layerElements = root.getAsJsonArray("layers") ?: return state.copy(layers = emptyList())
            val layers = layerElements.mapNotNull { element ->
                runCatching { gson.fromJson(element, EditorLayer::class.java) }.getOrNull()
            }
            if (layerElements.size() > 0 && layers.size < layerElements.size()) {
                android.util.Log.w(
                    "TemplateDraftRepo",
                    "Draft $draftId: dropped ${layerElements.size() - layers.size} corrupt layer(s)",
                )
                return null
            }
            state.copy(layers = layers)
        } catch (e: Exception) {
            android.util.Log.e("TemplateDraftRepo", "Failed to load draft $draftId", e)
            null
        }
    }
}
