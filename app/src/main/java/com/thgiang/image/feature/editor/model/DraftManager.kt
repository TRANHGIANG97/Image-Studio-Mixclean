package com.thgiang.image.feature.editor.model

import android.content.Context
import java.io.File
import java.util.UUID

/**
 * Quản lý danh sách các bản nháp trên ổ đĩa.
 */
class DraftManager(private val context: Context) {

    private val serializer = ProjectSerializer()
    private val draftsDir = File(context.cacheDir, "drafts")

    init {
        if (!draftsDir.exists()) draftsDir.mkdirs()
    }

    /** Lấy thư mục gốc của một draft. */
    fun getDraftDir(draftId: String): File {
        return File(draftsDir, draftId)
    }

    /** Lấy danh sách toàn bộ metadata của các bản nháp. */
    fun getAllDrafts(): List<DraftMetadata> {
        val folders = draftsDir.listFiles { file -> file.isDirectory } ?: return emptyList()
        return folders.mapNotNull { folder ->
            serializer.loadMetadata(folder)
        }.sortedByDescending { it.updatedAt }
    }

    /** Lấy snapshot của một draft. */
    fun getSnapshot(id: String): ProjectSnapshot? {
        val draftDir = getDraftDir(id)
        return serializer.load(draftDir)
    }

    /** Tạo một draft mới từ snapshot. */
    fun createDraft(name: String, snapshot: ProjectSnapshot): String {
        val id = UUID.randomUUID().toString()
        val draftDir = getDraftDir(id)
        draftDir.mkdirs()

        val now = System.currentTimeMillis()
        val meta = DraftMetadata(
            id = id,
            name = name,
            createdAt = now,
            updatedAt = now
        )

        serializer.saveMetadata(draftDir, meta)
        serializer.save(draftDir, snapshot)
        return id
    }

    /** Cập nhật snapshot cho một draft hiện có. */
    fun updateDraft(id: String, snapshot: ProjectSnapshot) {
        val draftDir = getDraftDir(id)
        if (!draftDir.exists()) return

        serializer.save(draftDir, snapshot)
        
        // Cập nhật updatedAt trong metadata
        val oldMeta = serializer.loadMetadata(draftDir)
        if (oldMeta != null) {
            val newMeta = oldMeta.copy(updatedAt = System.currentTimeMillis())
            serializer.saveMetadata(draftDir, newMeta)
        }
    }

    /** Xoá một bản nháp. */
    fun deleteDraft(id: String) {
        val draftDir = getDraftDir(id)
        serializer.delete(draftDir)
    }

    /** Đổi tên bản nháp. */
    fun renameDraft(id: String, newName: String) {
        val draftDir = getDraftDir(id)
        val oldMeta = serializer.loadMetadata(draftDir)
        if (oldMeta != null) {
            val newMeta = oldMeta.copy(name = newName, updatedAt = System.currentTimeMillis())
            serializer.saveMetadata(draftDir, newMeta)
        }
    }

    /** Sao chép bản nháp. */
    fun duplicateDraft(id: String): String? {
        val sourceDir = getDraftDir(id)
        if (!sourceDir.exists()) return null

        val newId = UUID.randomUUID().toString()
        val targetDir = getDraftDir(newId)
        
        try {
            sourceDir.copyRecursively(targetDir)
            
            // Cập nhật metadata cho bản sao
            val oldMeta = serializer.loadMetadata(targetDir)
            if (oldMeta != null) {
                val newMeta = oldMeta.copy(
                    id = newId,
                    name = "${oldMeta.name} (Copy)",
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                serializer.saveMetadata(targetDir, newMeta)
            }
            return newId
        } catch (e: Exception) {
            android.util.Log.e("DraftManager", "duplicateDraft failed: ${e.message}")
            return null
        }
    }

    /** Di chuyển dữ liệu cũ (nếu có) vào hệ thống bản nháp mới. */
    fun migrateOldProject() {
        val oldStateFile = File(context.cacheDir, "project_state.json")
        if (oldStateFile.exists()) {
            try {
                val snapshot = serializer.load(context.cacheDir)
                if (snapshot != null) {
                    createDraft("Dự án cũ", snapshot)
                }
                // Dọn dẹp các file cũ
                oldStateFile.delete()
                context.cacheDir.listFiles()?.forEach { file ->
                    if (file.name.startsWith("layer_") && file.name.endsWith(".bin")) {
                        file.delete()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("DraftManager", "Migration failed: ${e.message}")
            }
        }
    }
}
