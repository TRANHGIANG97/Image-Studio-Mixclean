package com.thgiang.image.studio.data
import com.thgiang.image.studio.ui.editor.*

import android.content.Context
import com.thgiang.image.studio.ui.editor.model.*
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

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
        .create()

    fun saveDraft(
        draftId: String?,
        name: String,
        state: EditorState,
        templateAssetPath: String,
        templateObjectAssetPath: String?,
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

        val metaMap = mapOf(
            "id" to id,
            "name" to name,
            "createdAt" to System.currentTimeMillis(),
            "updatedAt" to System.currentTimeMillis(),
            "isTemplate" to true,
            "templateAssetPath" to templateAssetPath,
            "templateObjectAssetPath" to templateObjectAssetPath
        )
        File(dir, "metadata.json").writeText(gson.toJson(metaMap))
        val persistedState = state.copy(
            layers = persistedLayers,
            exportResult = null,
            isExporting = false,
        )
        File(dir, "editor_state.json").writeText(gson.toJson(persistedState))
        return id
    }

    fun updateThumbnailPath(draftId: String, thumbnailAbsolutePath: String) {
        val dir = File(draftsDir, draftId)
        val metaFile = File(dir, "metadata.json")
        if (!metaFile.exists()) return
        try {
            @Suppress("UNCHECKED_CAST")
            val meta = gson.fromJson(metaFile.readText(), Map::class.java) as MutableMap<String, Any?>
            meta["thumbnailPath"] = thumbnailAbsolutePath
            meta["updatedAt"] = System.currentTimeMillis()
            metaFile.writeText(gson.toJson(meta))
        } catch (e: Exception) {
            android.util.Log.w("TemplateDraftRepo", "Failed to update thumbnailPath: ${e.message}")
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
     * Trả về đường dẫn file:// mới nếu thành công, hoặc [uriString] gốc nếu không.
     */
    private fun persistUriIfContent(uriString: String?, targetFile: File): String? {
        if (uriString == null) return null
        // Chỉ xử lý content:// — file:// và https:// giữ nguyên
        if (!uriString.startsWith("content://")) return uriString

        return try {
            val uri = Uri.parse(uriString)
            context.contentResolver.openInputStream(uri)?.use { input ->
                targetFile.outputStream().use { output -> input.copyTo(output) }
            }
            if (targetFile.exists() && targetFile.length() > 0) {
                "file://${targetFile.absolutePath}"
            } else {
                uriString // fallback: giữ nguyên nếu copy thất bại
            }
        } catch (e: Exception) {
            android.util.Log.w("TemplateDraftRepo", "Failed to persist URI $uriString: ${e.message}")
            uriString // fallback an toàn
        }
    }

    fun loadDraft(draftId: String): EditorState? {
        val dir = File(draftsDir, draftId)
        val file = File(dir, "editor_state.json")
        if (!file.exists()) return null
        return try {
            gson.fromJson(file.readText(), EditorState::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
