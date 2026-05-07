package com.thgiang.image.feature.editor.model

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File

/**
 * Serializer chịu trách nhiệm ghi / đọc [ProjectSnapshot] từ file JSON.
 */
class ProjectSerializer {

    companion object {
        private const val STATE_FILE_NAME = "project_state.json"
        private const val META_FILE_NAME = "metadata.json"

        /** Kiểm tra xem có dự án đã lưu trong [draftDir] không. */
        fun hasSavedProject(draftDir: File): Boolean {
            return File(draftDir, STATE_FILE_NAME).exists()
        }
    }

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    /** Ghi snapshot xuống file JSON trong [draftDir]. */
    fun save(draftDir: File, snapshot: ProjectSnapshot): Boolean {
        return try {
            draftDir.mkdirs()
            val file = File(draftDir, STATE_FILE_NAME)
            val json = gson.toJson(snapshot)
            val writer = file.bufferedWriter()
            try {
                writer.write(json)
            } finally {
                writer.close()
            }
            true
        } catch (e: Exception) {
            android.util.Log.e("ProjectSerializer", "save snapshot failed: ${e.message}")
            false
        }
    }

    /** Đọc snapshot từ file JSON trong [draftDir], hoặc null nếu không có. */
    fun load(draftDir: File): ProjectSnapshot? {
        val file = File(draftDir, STATE_FILE_NAME)
        if (!file.exists()) return null
        val reader = file.bufferedReader()
        return try {
            gson.fromJson(reader, ProjectSnapshot::class.java)
        } catch (e: Exception) {
            android.util.Log.e("ProjectSerializer", "load snapshot failed: ${e.message}")
            null
        } finally {
            reader.close()
        }
    }

    /** Ghi metadata xuống file JSON. */
    fun saveMetadata(draftDir: File, meta: DraftMetadata): Boolean {
        return try {
            draftDir.mkdirs()
            val file = File(draftDir, META_FILE_NAME)
            val json = gson.toJson(meta)
            val writer = file.bufferedWriter()
            try {
                writer.write(json)
            } finally {
                writer.close()
            }
            true
        } catch (e: Exception) {
            android.util.Log.e("ProjectSerializer", "save metadata failed: ${e.message}")
            false
        }
    }

    /** Đọc metadata từ file JSON. */
    fun loadMetadata(draftDir: File): DraftMetadata? {
        val file = File(draftDir, META_FILE_NAME)
        if (!file.exists()) return null
        val reader = file.bufferedReader()
        return try {
            gson.fromJson(reader, DraftMetadata::class.java)
        } catch (e: Exception) {
            android.util.Log.e("ProjectSerializer", "load metadata failed: ${e.message}")
            null
        } finally {
            reader.close()
        }
    }

    /** Xoá toàn bộ thư mục draft. */
    fun delete(draftDir: File) {
        draftDir.deleteRecursively()
    }
}
