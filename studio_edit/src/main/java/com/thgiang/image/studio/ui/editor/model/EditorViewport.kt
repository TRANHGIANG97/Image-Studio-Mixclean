package com.thgiang.image.studio.ui.editor.model

import androidx.compose.ui.geometry.Offset

data class EditorViewport(
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val flippedH: Boolean = false,
    val flippedV: Boolean = false
) : java.io.Serializable {
    val offset: Offset get() = Offset(offsetX, offsetY)

    init {
        require(scale > 0) { "Scale must be positive" }
    }

    companion object {
        val IDENTITY = EditorViewport()
    }

    fun isIdentity(): Boolean = this == IDENTITY

    fun withScale(newScale: Float): EditorViewport = copy(scale = newScale.coerceIn(0.05f, 40f))

    fun withOffset(newOffset: Offset): EditorViewport = copy(offsetX = newOffset.x, offsetY = newOffset.y)

    fun withRotation(newRotation: Float): EditorViewport {
        var normalized = newRotation % 360f
        if (normalized < 0) normalized += 360f
        return copy(rotation = normalized)
    }
}
