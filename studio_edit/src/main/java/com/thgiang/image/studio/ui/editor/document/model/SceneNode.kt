package com.thgiang.image.studio.ui.editor.document.model

import java.io.Serializable
import java.util.UUID

/**
 * Scene node kinds — Canva-grade typed objects.
 * IMAGE nodes are preserved as opaque passthrough for strangler migration.
 */
sealed class SceneNode : Serializable {
    abstract val id: String
    abstract val isLocked: Boolean
    abstract val isVisible: Boolean

    data class PureText(
        override val id: String = UUID.randomUUID().toString(),
        val content: TextContent,
        val layout: LayoutConstraints,
        val style: StyleBag,
        override val isLocked: Boolean = false,
        override val isVisible: Boolean = true,
    ) : SceneNode()

    data class TextInShape(
        override val id: String = UUID.randomUUID().toString(),
        /** Stable id for the frame part (maps to legacy FRAME layer id). */
        val frameId: String = UUID.randomUUID().toString(),
        /** Stable id for the text part (maps to legacy LABEL layer id). */
        val textId: String = UUID.randomUUID().toString(),
        val frameGeometry: FrameGeometry,
        val frameStyle: StyleBag,
        val textContent: TextContent,
        val textStyle: StyleBag,
        val layout: LayoutConstraints,
        val textPadding: EdgeInsets = EdgeInsets.SHAPE_TEXT,
        override val isLocked: Boolean = false,
        override val isVisible: Boolean = true,
    ) : SceneNode()

    data class Shape(
        override val id: String = UUID.randomUUID().toString(),
        val geometry: FrameGeometry,
        val layout: LayoutConstraints,
        val style: StyleBag,
        override val isLocked: Boolean = false,
        override val isVisible: Boolean = true,
    ) : SceneNode()

    /**
     * Opaque bridge for IMAGE / SHADOW_REGION / unknown layers during migration.
     * Payload is the legacy EditorLayer serialized externally via adapter.
     */
    data class LegacyPassthrough(
        override val id: String,
        val legacyType: String,
        override val isLocked: Boolean = false,
        override val isVisible: Boolean = true,
    ) : SceneNode()
}

/** Which part of a [SceneNode.TextInShape] is selected for editing. */
enum class NodePart : Serializable {
    WHOLE,
    FRAME,
    TEXT,
}

data class DocumentSelection(
    val nodeId: String? = null,
    val part: NodePart = NodePart.WHOLE,
) : Serializable

data class DocumentSnapshot(
    val nodes: List<SceneNode> = emptyList(),
    val selection: DocumentSelection = DocumentSelection(),
    val templateWidth: Float = 1080f,
    val templateHeight: Float = 1080f,
    val revision: Long = 0L,
) : Serializable {
    fun node(id: String): SceneNode? = nodes.find { it.id == id }

    fun selectedNode(): SceneNode? = selection.nodeId?.let { node(it) }
}
