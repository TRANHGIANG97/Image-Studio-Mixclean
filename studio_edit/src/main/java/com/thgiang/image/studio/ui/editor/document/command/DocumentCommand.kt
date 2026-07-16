package com.thgiang.image.studio.ui.editor.document.command

import com.thgiang.image.studio.ui.editor.document.model.DocumentSelection
import com.thgiang.image.studio.ui.editor.document.model.FrameGeometry
import com.thgiang.image.studio.ui.editor.document.model.LayoutConstraints
import com.thgiang.image.studio.ui.editor.document.model.NodePart
import com.thgiang.image.studio.ui.editor.document.model.NodeTransform
import com.thgiang.image.studio.ui.editor.document.model.SceneNode
import com.thgiang.image.studio.ui.editor.document.model.StyleBag
import com.thgiang.image.studio.ui.editor.document.model.TextContent
import com.thgiang.image.studio.ui.editor.document.rules.LayoutIntent
import com.thgiang.image.studio.ui.editor.model.TextFormEffect
import com.thgiang.image.studio.ui.editor.model.TextFormPreset

/**
 * All document mutations go through these commands (I1).
 */
sealed class DocumentCommand {
    data class ReplaceSnapshot(val nodes: List<SceneNode>) : DocumentCommand()

    data class SelectNode(
        val nodeId: String?,
        val part: NodePart = NodePart.WHOLE,
    ) : DocumentCommand()

    data class EditText(
        val nodeId: String,
        val text: String,
        val intent: LayoutIntent = LayoutIntent.EditText,
    ) : DocumentCommand()

    data class SetTextContent(
        val nodeId: String,
        val content: TextContent,
        val intent: LayoutIntent = LayoutIntent.StyleOrCaseChange,
    ) : DocumentCommand()

    data class SetStyleBag(
        val nodeId: String,
        val style: StyleBag,
        val target: StyleTarget = StyleTarget.TEXT,
        val intent: LayoutIntent = LayoutIntent.StyleOrCaseChange,
    ) : DocumentCommand()

    /**
     * Update frame geometry (shapeType / corners / path) and optional frame style
     * (fill/stroke defaults from [applyShapeTypeChange]).
     */
    data class SetFrameGeometry(
        val nodeId: String,
        val geometry: FrameGeometry,
        val frameStyle: StyleBag? = null,
        val intent: LayoutIntent = LayoutIntent.StyleOrCaseChange,
    ) : DocumentCommand()

    /** Atomic template / preset replace of style bag (I4). */
    data class ApplyStyleTemplate(
        val nodeId: String,
        val textStyle: StyleBag? = null,
        val frameStyle: StyleBag? = null,
        val textContentPatch: TextContent? = null,
        val intent: LayoutIntent = LayoutIntent.StyleOrCaseChange,
    ) : DocumentCommand()

    data class SetTextForm(
        val nodeId: String,
        val textForm: TextFormEffect,
        val intent: LayoutIntent = LayoutIntent.TextFormMeasure,
    ) : DocumentCommand()

    data class ApplyTextFormPreset(
        val nodeId: String,
        val preset: TextFormPreset,
        val amount: Float? = null,
    ) : DocumentCommand()

    data class SetLayoutConstraints(
        val nodeId: String,
        val layout: LayoutConstraints,
    ) : DocumentCommand()

    data class ResizeEdge(
        val nodeId: String,
        val widthPx: Float,
        val heightDeltaPx: Float = 0f,
    ) : DocumentCommand()

    data class SetTransform(
        val nodeId: String,
        val transform: NodeTransform,
        /** TextInShape: always sync both parts (I3). */
        val syncGroup: Boolean = true,
    ) : DocumentCommand()

    data class BakeCornerScale(
        val nodeId: String,
        val scale: Float,
    ) : DocumentCommand()

    data class SetLocked(val nodeId: String, val locked: Boolean) : DocumentCommand()
    data class SetVisible(val nodeId: String, val visible: Boolean) : DocumentCommand()

    data class UpsertNode(val node: SceneNode) : DocumentCommand()
    data class RemoveNode(val nodeId: String) : DocumentCommand()
}

enum class StyleTarget {
    TEXT,
    FRAME,
    BOTH,
}
