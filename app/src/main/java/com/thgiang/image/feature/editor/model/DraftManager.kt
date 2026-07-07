package com.thgiang.image.feature.editor.model

import android.content.Context
import com.thgiang.image.R
import java.io.File
import java.util.UUID

/**
 * Quản lý danh sách các bản nháp trên ổ đĩa.
 */
class DraftManager(private val context: Context) {

    private val serializer = ProjectSerializer()

    /**
     * Dùng [filesDir] thay vì [cacheDir] để Android không tự xóa dữ liệu draft
     * khi bộ nhớ thiếu. [cacheDir] là bộ nhớ đệm tạm thời, hệ thống có thể
     * xóa bất cứ lúc nào → gây màn hình trắng khi mở lại draft.
     */
    private val draftsDir = File(context.filesDir, "drafts")

    init {
        if (!draftsDir.exists()) draftsDir.mkdirs()
        migrateCacheDirDrafts()
    }

    /**
     * Migration một lần: chuyển các draft cũ từ [cacheDir]/drafts → [filesDir]/drafts.
     * Chạy khi khởi tạo, tự động bỏ qua nếu không có gì cần migrate.
     */
    private fun migrateCacheDirDrafts() {
        val oldDir = File(context.cacheDir, "drafts")
        if (!oldDir.exists()) return
        try {
            oldDir.listFiles { f -> f.isDirectory }?.forEach { oldDraft ->
                val target = File(draftsDir, oldDraft.name)
                if (!target.exists()) {
                    oldDraft.copyRecursively(target, overwrite = false)
                }
                oldDraft.deleteRecursively()
            }
            if (oldDir.listFiles().isNullOrEmpty()) oldDir.delete()
        } catch (e: Exception) {
            android.util.Log.w("DraftManager", "migrateCacheDirDrafts failed: ${e.message}")
        }
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

    /** Tạo một template draft mới. */
    fun createTemplateDraft(
        name: String, 
        snapshot: ProjectSnapshot,
        templateAssetPath: String,
        templateObjectAssetPath: String?
    ): String {
        val id = UUID.randomUUID().toString()
        val draftDir = getDraftDir(id)
        draftDir.mkdirs()

        val now = System.currentTimeMillis()
        val meta = DraftMetadata(
            id = id,
            name = name,
            createdAt = now,
            updatedAt = now,
            isTemplate = true,
            templateAssetPath = templateAssetPath,
            templateObjectAssetPath = templateObjectAssetPath
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
                    name = "${oldMeta.name} (${context.getString(R.string.draft_copy_suffix)})",
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
                    createDraft(context.getString(R.string.old_project_name), snapshot)
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
