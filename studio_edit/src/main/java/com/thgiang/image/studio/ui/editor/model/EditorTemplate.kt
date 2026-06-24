package com.thgiang.image.studio.ui.editor.model

import androidx.compose.ui.unit.IntSize

data class EditorTemplate(
    val assetPath: String = "",
    val originalWidth: Int = 0,
    val originalHeight: Int = 0,
    val backgroundColorArgb: Int = 0xFFFFFFFF.toInt(),
    val loaded: Boolean = false
) : java.io.Serializable {
    val originalSize: IntSize get() = IntSize(originalWidth, originalHeight)
}
