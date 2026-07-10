package com.thgiang.image.studio.ui.editor.model

sealed class EditorTool(val iconName: String) : java.io.Serializable {
    data object Replace : EditorTool("photo")
    data object Sticker : EditorTool("sticker")
    data object Label : EditorTool("label")
    data object Shape : EditorTool("shape")
    data object Layout : EditorTool("drag_indicator")
    data object Rotate : EditorTool("refresh")
    data object Shadow : EditorTool("wb_sunny")
    data object Transparency : EditorTool("opacity")
    data object Crop : EditorTool("crop_square")
    data object Duplicate : EditorTool("content_copy")
    data object Delete : EditorTool("delete")
    data object AddImage : EditorTool("add_a_photo")
    data object RemoveBg : EditorTool("auto_awesome")

    companion object {
        val ALL = listOf(
            Replace, Sticker, Label, Shape, Rotate, Shadow, Transparency, Crop, Duplicate, Delete,
            AddImage, RemoveBg
        )
    }
}
