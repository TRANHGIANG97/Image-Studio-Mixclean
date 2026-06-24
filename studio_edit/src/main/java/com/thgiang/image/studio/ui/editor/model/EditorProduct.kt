package com.thgiang.image.studio.ui.editor.model

import android.net.Uri
import androidx.compose.ui.unit.IntSize

data class EditorProduct(
    val originalUriString: String? = null,
    val foregroundUriString: String? = null,
    val isBackgroundRemoved: Boolean = false,
    val baseWidth: Int = 0,
    val baseHeight: Int = 0,
    val processing: Boolean = false,
    val isSample: Boolean = false
) : java.io.Serializable {
    val originalUri: Uri? get() = originalUriString?.let { Uri.parse(it) }
    val foregroundUri: Uri? get() = foregroundUriString?.let { Uri.parse(it) }
    val baseSize: IntSize get() = IntSize(baseWidth, baseHeight)

    constructor() : this(null, null, false, 0, 0, false, false)
}
