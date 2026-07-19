package com.thgiang.image.studio.ui.editor.model

import androidx.compose.ui.unit.IntSize

data class EditorTemplate(
    val assetPath: String = "",
    val originalWidth: Int = 0,
    val originalHeight: Int = 0,
    val backgroundColorArgb: Int = 0xFFFFFFFF.toInt(),
    val loaded: Boolean = false,
    /** Đường dẫn ảnh mẫu sản phẩm (sample object) — dùng để khôi phục khi load lại draft. */
    val objectAssetPath: String? = null,
    /** Thumbnail cloud từ admin_web — dùng cho preview draft khi render preview thất bại. */
    val thumbnailUrl: String? = null,
) : java.io.Serializable {
    val originalSize: IntSize get() = IntSize(originalWidth, originalHeight)
}
