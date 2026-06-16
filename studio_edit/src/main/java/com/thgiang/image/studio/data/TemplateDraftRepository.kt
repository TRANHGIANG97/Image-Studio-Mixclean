package com.thgiang.image.studio.data

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import com.thgiang.image.studio.ui.editor.EditorState
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
    private val draftsDir = File(context.cacheDir, "drafts")
    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(Uri::class.java, UriTypeAdapter())
        .create()

    fun saveDraft(draftId: String?, name: String, state: EditorState, templateAssetPath: String, templateObjectAssetPath: String?): String {
        draftsDir.mkdirs()
        val id = draftId ?: UUID.randomUUID().toString()
        val dir = File(draftsDir, id)
        dir.mkdirs()

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
        File(dir, "editor_state.json").writeText(gson.toJson(state.copy(exportResult = null, isExporting = false)))
        return id
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
