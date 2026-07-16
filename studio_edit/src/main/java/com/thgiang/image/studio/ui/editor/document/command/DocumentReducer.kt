package com.thgiang.image.studio.ui.editor.document.command

import com.thgiang.image.studio.ui.editor.document.model.DocumentSelection
import com.thgiang.image.studio.ui.editor.document.model.DocumentSnapshot
import com.thgiang.image.studio.ui.editor.document.model.HeightConstraint
import com.thgiang.image.studio.ui.editor.document.model.LayoutConstraints
import com.thgiang.image.studio.ui.editor.document.model.NodePart
import com.thgiang.image.studio.ui.editor.document.model.NodeTransform
import com.thgiang.image.studio.ui.editor.document.model.SceneNode
import com.thgiang.image.studio.ui.editor.document.model.StyleBag
import com.thgiang.image.studio.ui.editor.document.model.TextContent
import com.thgiang.image.studio.ui.editor.document.model.TextRun
import com.thgiang.image.studio.ui.editor.document.model.WidthConstraint
import com.thgiang.image.studio.ui.editor.document.rules.EffectCompatibilityMatrix
import com.thgiang.image.studio.ui.editor.document.rules.LayoutIntent
import com.thgiang.image.studio.ui.editor.document.rules.LayoutPolicy
import com.thgiang.image.studio.ui.editor.model.EditorTextSpan
import com.thgiang.image.studio.ui.editor.model.TextFormEffect
import com.thgiang.image.studio.ui.editor.model.TextFormPreset
import com.thgiang.image.studio.ui.editor.model.TextRunOps
import com.thgiang.image.studio.ui.editor.model.withPreset

/**
 * Pure reducer — no Android Context. Layout pixel measure is applied by DocumentStore
 * after reduce via LayoutEngine (keeps reducer testable).
 */
object DocumentReducer {

    data class ReduceResult(
        val snapshot: DocumentSnapshot,
        val layoutNodeIds: Set<String> = emptySet(),
        val layoutIntent: LayoutIntent? = null,
        val warnings: List<String> = emptyList(),
    )

    fun reduce(snapshot: DocumentSnapshot, command: DocumentCommand): ReduceResult {
        return when (command) {
            is DocumentCommand.ReplaceSnapshot -> ReduceResult(
                snapshot.copy(nodes = command.nodes, revision = snapshot.revision + 1),
            )
            is DocumentCommand.SelectNode -> ReduceResult(
                snapshot.copy(
                    selection = DocumentSelection(command.nodeId, command.part),
                    revision = snapshot.revision + 1,
                ),
            )
            is DocumentCommand.EditText -> editText(snapshot, command)
            is DocumentCommand.SetTextContent -> setTextContent(snapshot, command)
            is DocumentCommand.SetStyleBag -> setStyle(snapshot, command)
            is DocumentCommand.SetFrameGeometry -> setFrameGeometry(snapshot, command)
            is DocumentCommand.ApplyStyleTemplate -> applyTemplate(snapshot, command)
            is DocumentCommand.SetTextForm -> setTextForm(snapshot, command)
            is DocumentCommand.ApplyTextFormPreset -> applyTextFormPreset(snapshot, command)
            is DocumentCommand.SetLayoutConstraints -> updateNode(snapshot, command.nodeId) { n ->
                when (n) {
                    is SceneNode.PureText -> n.copy(layout = command.layout)
                    is SceneNode.TextInShape -> n.copy(layout = command.layout)
                    is SceneNode.Shape -> n.copy(layout = command.layout)
                    else -> n
                }
            }.let { ReduceResult(it, setOf(command.nodeId), LayoutIntent.ManualBox) }
            is DocumentCommand.ResizeEdge -> resizeEdge(snapshot, command)
            is DocumentCommand.SetTransform -> setTransform(snapshot, command)
            is DocumentCommand.BakeCornerScale -> bakeScale(snapshot, command)
            is DocumentCommand.SetLocked -> updateNode(snapshot, command.nodeId) { lock(it, command.locked) }
                .let { ReduceResult(it) }
            is DocumentCommand.SetVisible -> updateNode(snapshot, command.nodeId) { vis(it, command.visible) }
                .let { ReduceResult(it) }
            is DocumentCommand.UpsertNode -> {
                val exists = snapshot.nodes.any { it.id == command.node.id }
                val nodes = if (exists) {
                    snapshot.nodes.map { if (it.id == command.node.id) command.node else it }
                } else {
                    snapshot.nodes + command.node
                }
                ReduceResult(snapshot.copy(nodes = nodes, revision = snapshot.revision + 1))
            }
            is DocumentCommand.RemoveNode -> ReduceResult(
                snapshot.copy(
                    nodes = snapshot.nodes.filterNot { it.id == command.nodeId },
                    selection = if (snapshot.selection.nodeId == command.nodeId) {
                        DocumentSelection()
                    } else {
                        snapshot.selection
                    },
                    revision = snapshot.revision + 1,
                ),
            )
        }
    }

    private fun editText(snapshot: DocumentSnapshot, cmd: DocumentCommand.EditText): ReduceResult {
        val warnings = mutableListOf<String>()
        val next = updateNode(snapshot, cmd.nodeId) { node ->
            when (node) {
                is SceneNode.PureText -> {
                    val content = reflowTextContent(node.content, cmd.text)
                    val layout = LayoutPolicy.applyIntent(node.layout, cmd.intent)
                    node.copy(content = content, layout = layout)
                }
                is SceneNode.TextInShape -> {
                    val content = reflowTextContent(node.textContent, cmd.text)
                    val layout = LayoutPolicy.applyIntent(node.layout, cmd.intent)
                    node.copy(textContent = content, layout = layout)
                }
                else -> node
            }
        }
        return ReduceResult(next, setOf(cmd.nodeId), cmd.intent, warnings)
    }

    private fun reflowTextContent(content: TextContent, newText: String): TextContent {
        val spans = content.runs.map { run ->
            EditorTextSpan(
                text = run.text,
                fontWeight = run.fontWeight,
                fontStyle = run.fontStyle,
                colorArgb = run.colorArgb,
                underline = run.underline,
                linethrough = run.linethrough,
                fontFamily = run.fontFamily,
            )
        }.ifEmpty {
            listOf(
                EditorTextSpan(
                    text = content.text,
                    fontWeight = content.primaryRun.fontWeight,
                    fontStyle = content.primaryRun.fontStyle,
                    colorArgb = content.primaryRun.colorArgb,
                    underline = content.primaryRun.underline,
                    linethrough = content.primaryRun.linethrough,
                    fontFamily = content.primaryRun.fontFamily,
                ),
            )
        }
        val reflowed = TextRunOps.reflow(spans, content.text, newText)
        val size = content.primaryRun.textSizeSp
        val gradient = if (reflowed.size <= 1) content.primaryRun.colorGradient else null
        val runs = reflowed.map { span ->
            TextRun(
                text = span.text,
                fontFamily = span.fontFamily ?: content.primaryRun.fontFamily,
                fontWeight = span.fontWeight ?: content.primaryRun.fontWeight,
                fontStyle = span.fontStyle ?: content.primaryRun.fontStyle,
                textSizeSp = size,
                colorArgb = span.colorArgb ?: content.primaryRun.colorArgb,
                colorGradient = gradient,
                underline = span.underline ?: content.primaryRun.underline,
                linethrough = span.linethrough ?: content.primaryRun.linethrough,
            )
        }
        return content.copy(text = newText, runs = runs.ifEmpty { listOf(content.primaryRun.copy(text = newText)) })
    }

    private fun setTextContent(snapshot: DocumentSnapshot, cmd: DocumentCommand.SetTextContent): ReduceResult {
        val next = updateNode(snapshot, cmd.nodeId) { node ->
            when (node) {
                is SceneNode.PureText -> node.copy(
                    content = cmd.content,
                    layout = LayoutPolicy.applyIntent(node.layout, cmd.intent),
                )
                is SceneNode.TextInShape -> node.copy(
                    textContent = cmd.content,
                    layout = LayoutPolicy.applyIntent(node.layout, cmd.intent),
                )
                else -> node
            }
        }
        return ReduceResult(next, setOf(cmd.nodeId), cmd.intent)
    }

    private fun setStyle(snapshot: DocumentSnapshot, cmd: DocumentCommand.SetStyleBag): ReduceResult {
        val resolved = EffectCompatibilityMatrix.resolve(cmd.style)
        val next = updateNode(snapshot, cmd.nodeId) { node ->
            when (node) {
                is SceneNode.PureText -> node.copy(
                    style = resolved.style,
                    layout = LayoutPolicy.applyIntent(node.layout, cmd.intent),
                )
                is SceneNode.TextInShape -> when (cmd.target) {
                    StyleTarget.TEXT -> node.copy(
                        textStyle = resolved.style,
                        layout = LayoutPolicy.applyIntent(node.layout, cmd.intent),
                    )
                    StyleTarget.FRAME -> node.copy(frameStyle = resolved.style)
                    StyleTarget.BOTH -> node.copy(
                        textStyle = resolved.style,
                        frameStyle = resolved.style,
                        layout = LayoutPolicy.applyIntent(node.layout, cmd.intent),
                    )
                }
                is SceneNode.Shape -> node.copy(style = resolved.style)
                else -> node
            }
        }
        return ReduceResult(next, setOf(cmd.nodeId), cmd.intent, resolved.disabled)
    }

    private fun setFrameGeometry(
        snapshot: DocumentSnapshot,
        cmd: DocumentCommand.SetFrameGeometry,
    ): ReduceResult {
        val styleResolved = cmd.frameStyle?.let { EffectCompatibilityMatrix.resolve(it) }
        val next = updateNode(snapshot, cmd.nodeId) { node ->
            when (node) {
                is SceneNode.TextInShape -> {
                    val style = styleResolved?.style ?: node.frameStyle
                    node.copy(
                        frameGeometry = cmd.geometry,
                        frameStyle = style,
                        layout = LayoutPolicy.applyIntent(node.layout, cmd.intent),
                    )
                }
                is SceneNode.Shape -> node.copy(
                    geometry = cmd.geometry,
                    style = styleResolved?.style ?: node.style,
                    layout = LayoutPolicy.applyIntent(node.layout, LayoutIntent.FontSizeChange),
                )
                else -> node
            }
        }
        return ReduceResult(
            next,
            setOf(cmd.nodeId),
            cmd.intent,
            styleResolved?.disabled.orEmpty(),
        )
    }

    private fun applyTemplate(snapshot: DocumentSnapshot, cmd: DocumentCommand.ApplyStyleTemplate): ReduceResult {
        val warnings = mutableListOf<String>()
        val next = updateNode(snapshot, cmd.nodeId) { node ->
            when (node) {
                is SceneNode.PureText -> {
                    var style = cmd.textStyle ?: node.style
                    val r = EffectCompatibilityMatrix.resolve(style)
                    warnings += r.disabled
                    style = r.style
                    val content = cmd.textContentPatch ?: node.content
                    node.copy(
                        content = content,
                        style = style,
                        layout = LayoutPolicy.applyIntent(node.layout, cmd.intent),
                    )
                }
                is SceneNode.TextInShape -> {
                    var textStyle = cmd.textStyle ?: node.textStyle
                    val tr = EffectCompatibilityMatrix.resolve(textStyle)
                    warnings += tr.disabled
                    textStyle = tr.style
                    var frameStyle = cmd.frameStyle ?: node.frameStyle
                    val fr = EffectCompatibilityMatrix.resolve(frameStyle)
                    warnings += fr.disabled
                    frameStyle = fr.style
                    node.copy(
                        textContent = cmd.textContentPatch ?: node.textContent,
                        textStyle = textStyle,
                        frameStyle = frameStyle,
                        layout = LayoutPolicy.applyIntent(node.layout, cmd.intent),
                    )
                }
                else -> node
            }
        }
        return ReduceResult(next, setOf(cmd.nodeId), cmd.intent, warnings)
    }

    private fun setTextForm(snapshot: DocumentSnapshot, cmd: DocumentCommand.SetTextForm): ReduceResult {
        val next = updateNode(snapshot, cmd.nodeId) { node ->
            when (node) {
                is SceneNode.PureText -> {
                    val r = EffectCompatibilityMatrix.withTextForm(node.style, cmd.textForm)
                    node.copy(
                        style = r.style,
                        layout = LayoutPolicy.applyIntent(node.layout, cmd.intent),
                    )
                }
                is SceneNode.TextInShape -> {
                    val r = EffectCompatibilityMatrix.withTextForm(node.textStyle, cmd.textForm)
                    node.copy(
                        textStyle = r.style,
                        layout = LayoutPolicy.applyIntent(node.layout, cmd.intent),
                    )
                }
                else -> node
            }
        }
        return ReduceResult(next, setOf(cmd.nodeId), cmd.intent)
    }

    private fun applyTextFormPreset(snapshot: DocumentSnapshot, cmd: DocumentCommand.ApplyTextFormPreset): ReduceResult {
        fun patch(style: StyleBag): StyleBag {
            val amount = cmd.amount ?: style.textForm.amount.takeIf { it > 0.01f } ?: 0.55f
            val form = style.textForm.withPreset(cmd.preset).copy(
                amount = if (cmd.preset == TextFormPreset.NONE) 0.5f else amount,
            )
            return EffectCompatibilityMatrix.withTextForm(style, form).style
        }
        val intent = if (cmd.preset == TextFormPreset.NONE) {
            LayoutIntent.StyleOrCaseChange
        } else {
            LayoutIntent.TextFormMeasure
        }
        val next = updateNode(snapshot, cmd.nodeId) { node ->
            when (node) {
                is SceneNode.PureText -> node.copy(
                    style = patch(node.style),
                    layout = LayoutPolicy.applyIntent(node.layout, intent),
                )
                is SceneNode.TextInShape -> node.copy(
                    textStyle = patch(node.textStyle),
                    layout = LayoutPolicy.applyIntent(node.layout, intent),
                )
                else -> node
            }
        }
        return ReduceResult(next, setOf(cmd.nodeId), intent)
    }

    private fun resizeEdge(snapshot: DocumentSnapshot, cmd: DocumentCommand.ResizeEdge): ReduceResult {
        val next = updateNode(snapshot, cmd.nodeId) { node ->
            val layout = when (node) {
                is SceneNode.PureText -> node.layout
                is SceneNode.TextInShape -> node.layout
                is SceneNode.Shape -> node.layout
                else -> return@updateNode node
            }
            val updated = LayoutPolicy.applyIntent(
                layout,
                LayoutIntent.ResizeEdgeWidth,
                newWidthPx = cmd.widthPx,
            ).let {
                if (cmd.heightDeltaPx != 0f && it.height == HeightConstraint.Fixed) {
                    it.copy(boxHeightPx = (it.boxHeightPx + cmd.heightDeltaPx).coerceAtLeast(LayoutPolicy.MIN_HEIGHT))
                } else it
            }
            when (node) {
                is SceneNode.PureText -> node.copy(layout = updated)
                is SceneNode.TextInShape -> node.copy(layout = updated)
                is SceneNode.Shape -> node.copy(layout = updated)
                else -> node
            }
        }
        return ReduceResult(next, setOf(cmd.nodeId), LayoutIntent.ResizeEdgeWidth)
    }

    private fun setTransform(snapshot: DocumentSnapshot, cmd: DocumentCommand.SetTransform): ReduceResult {
        // I3: TextInShape has a single layout.transform for both parts.
        val next = updateNode(snapshot, cmd.nodeId) { node ->
            when (node) {
                is SceneNode.PureText -> node.copy(layout = node.layout.copy(transform = cmd.transform))
                is SceneNode.TextInShape -> node.copy(layout = node.layout.copy(transform = cmd.transform))
                is SceneNode.Shape -> node.copy(layout = node.layout.copy(transform = cmd.transform))
                else -> node
            }
        }
        return ReduceResult(next)
    }

    private fun bakeScale(snapshot: DocumentSnapshot, cmd: DocumentCommand.BakeCornerScale): ReduceResult {
        val s = cmd.scale.coerceIn(0.05f, 40f)
        val next = updateNode(snapshot, cmd.nodeId) { node ->
            when (node) {
                is SceneNode.PureText -> {
                    val runs = node.content.runs.map { run ->
                        run.copy(textSizeSp = run.textSizeSp * s)
                    }.ifEmpty {
                        listOf(node.content.primaryRun.copy(textSizeSp = node.content.primaryRun.textSizeSp * s))
                    }
                    val layout = LayoutPolicy.applyIntent(
                        node.layout,
                        LayoutIntent.CornerScaleBake,
                        newWidthPx = node.layout.boxWidthPx * s,
                        newHeightPx = node.layout.boxHeightPx * s,
                    ).copy(transform = node.layout.transform.copy(scale = 1f))
                    node.copy(
                        content = node.content.copy(runs = runs),
                        layout = layout,
                    )
                }
                is SceneNode.TextInShape -> {
                    val runs = node.textContent.runs.map { run ->
                        run.copy(textSizeSp = run.textSizeSp * s)
                    }.ifEmpty {
                        listOf(node.textContent.primaryRun.copy(textSizeSp = node.textContent.primaryRun.textSizeSp * s))
                    }
                    val layout = LayoutPolicy.applyIntent(
                        node.layout,
                        LayoutIntent.CornerScaleBake,
                        newWidthPx = node.layout.boxWidthPx * s,
                        newHeightPx = node.layout.boxHeightPx * s,
                    ).copy(transform = node.layout.transform.copy(scale = 1f))
                    node.copy(
                        textContent = node.textContent.copy(runs = runs),
                        layout = layout,
                    )
                }
                is SceneNode.Shape -> {
                    val layout = LayoutPolicy.applyIntent(
                        node.layout,
                        LayoutIntent.CornerScaleBake,
                        newWidthPx = node.layout.boxWidthPx * s,
                        newHeightPx = node.layout.boxHeightPx * s,
                    ).copy(transform = node.layout.transform.copy(scale = 1f))
                    node.copy(layout = layout)
                }
                else -> node
            }
        }
        return ReduceResult(next, setOf(cmd.nodeId), LayoutIntent.CornerScaleBake)
    }

    private fun updateNode(
        snapshot: DocumentSnapshot,
        nodeId: String,
        block: (SceneNode) -> SceneNode,
    ): DocumentSnapshot {
        val nodes = snapshot.nodes.map { if (it.id == nodeId) block(it) else it }
        return snapshot.copy(nodes = nodes, revision = snapshot.revision + 1)
    }

    private fun lock(node: SceneNode, locked: Boolean): SceneNode = when (node) {
        is SceneNode.PureText -> node.copy(isLocked = locked)
        is SceneNode.TextInShape -> node.copy(isLocked = locked)
        is SceneNode.Shape -> node.copy(isLocked = locked)
        is SceneNode.LegacyPassthrough -> node.copy(isLocked = locked)
    }

    private fun vis(node: SceneNode, visible: Boolean): SceneNode = when (node) {
        is SceneNode.PureText -> node.copy(isVisible = visible)
        is SceneNode.TextInShape -> node.copy(isVisible = visible)
        is SceneNode.Shape -> node.copy(isVisible = visible)
        is SceneNode.LegacyPassthrough -> node.copy(isVisible = visible)
    }
}
