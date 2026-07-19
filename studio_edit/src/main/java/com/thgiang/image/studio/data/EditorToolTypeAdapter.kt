package com.thgiang.image.studio.data

import com.google.gson.JsonParser
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import com.thgiang.image.studio.ui.editor.model.EditorTool

/**
 * Gson cannot instantiate the sealed [EditorTool] hierarchy by default.
 * Persists tools as a short string discriminator; also reads legacy
 * `{"iconName":"..."}` objects written before this adapter existed.
 */
class EditorToolTypeAdapter : TypeAdapter<EditorTool>() {

    override fun write(out: JsonWriter, value: EditorTool?) {
        if (value == null) {
            out.nullValue()
            return
        }
        out.value(toolToKey(value))
    }

    override fun read(`in`: JsonReader): EditorTool? = when (`in`.peek()) {
        JsonToken.NULL -> {
            `in`.nextNull()
            null
        }
        JsonToken.STRING -> keyToTool(`in`.nextString())
        JsonToken.BEGIN_OBJECT -> {
            val obj = JsonParser.parseReader(`in`).asJsonObject
            val iconName = obj.get("iconName")?.asString
            if (iconName != null) iconNameToTool(iconName) else null
        }
        else -> {
            `in`.skipValue()
            null
        }
    }

    private fun toolToKey(tool: EditorTool): String = when (tool) {
        EditorTool.Replace -> "Replace"
        EditorTool.Sticker -> "Sticker"
        EditorTool.Background -> "Background"
        EditorTool.Label -> "Label"
        EditorTool.Shape -> "Shape"
        EditorTool.Layout -> "Layout"
        EditorTool.Rotate -> "Rotate"
        EditorTool.Shadow -> "Shadow"
        EditorTool.Transparency -> "Transparency"
        EditorTool.Crop -> "Crop"
        EditorTool.Duplicate -> "Duplicate"
        EditorTool.Delete -> "Delete"
        EditorTool.AddImage -> "AddImage"
        EditorTool.RemoveBg -> "RemoveBg"
    }

    private fun keyToTool(key: String): EditorTool? = when (key) {
        "Replace", "replace" -> EditorTool.Replace
        "Sticker", "sticker" -> EditorTool.Sticker
        "Background", "background" -> EditorTool.Background
        "Label", "label" -> EditorTool.Label
        "Shape", "shape" -> EditorTool.Shape
        "Layout", "layout" -> EditorTool.Layout
        "Rotate", "rotate" -> EditorTool.Rotate
        "Shadow", "shadow" -> EditorTool.Shadow
        "Transparency", "transparency" -> EditorTool.Transparency
        "Crop", "crop" -> EditorTool.Crop
        "Duplicate", "duplicate" -> EditorTool.Duplicate
        "Delete", "delete" -> EditorTool.Delete
        "AddImage", "addImage", "add_image" -> EditorTool.AddImage
        "RemoveBg", "removeBg", "remove_bg" -> EditorTool.RemoveBg
        else -> iconNameToTool(key)
    }

    private fun iconNameToTool(iconName: String): EditorTool? = when (iconName) {
        "photo" -> EditorTool.Replace
        "sticker" -> EditorTool.Sticker
        "background" -> EditorTool.Background
        "label" -> EditorTool.Label
        "shape" -> EditorTool.Shape
        "drag_indicator" -> EditorTool.Layout
        "refresh" -> EditorTool.Rotate
        "wb_sunny" -> EditorTool.Shadow
        "opacity" -> EditorTool.Transparency
        "crop_square" -> EditorTool.Crop
        "content_copy" -> EditorTool.Duplicate
        "delete" -> EditorTool.Delete
        "add_a_photo" -> EditorTool.AddImage
        "auto_awesome" -> EditorTool.RemoveBg
        else -> null
    }
}
