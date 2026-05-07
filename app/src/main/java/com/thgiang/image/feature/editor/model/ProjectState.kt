package com.thgiang.image.feature.editor.model

/**
 * Metadata cho một bản nháp, dùng để hiển thị trong danh sách.
 */
data class DraftMetadata(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val thumbnailPath: String? = null
)

/**
 * Snapshot toàn bộ trạng thái dự án, dùng để serialize xuống JSON.
 */
data class ProjectSnapshot(
    val version: Int = 1,
    val name: String? = null,
    val selectedLayerIndex: Int,
    val layers: List<LayerSnapshot>,
    val undoStack: List<List<LayerSnapshot>> = emptyList(),
    val redoStack: List<List<LayerSnapshot>> = emptyList()
)

/**
 * Snapshot của một layer — chỉ chứa metadata, không chứa Bitmap.
 */
data class LayerSnapshot(
    val id: String,
    val type: String,               // "IMAGE", "BACKGROUND", hoặc "TEXT"
    val cacheFileName: String,      // tên file trong cacheDir, VD: "layer_uuid.bin"
    val isVisible: Boolean = true,
    val opacity: Float = 1f,
    val isBackgroundRemoved: Boolean = false,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotation: Float = 0f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val backgroundType: String? = null,  // chỉ dùng cho BACKGROUND
    val textConfigJson: String? = null,  // chỉ dùng cho TEXT
    val bitmapWidth: Int = 0,            // chỉ dùng cho TEXT
    val bitmapHeight: Int = 0            // chỉ dùng cho TEXT
)

