package com.thgiang.image.studio.ui.editor.document.adapter

import com.thgiang.image.studio.ui.editor.document.model.DocTextAlign
import com.thgiang.image.studio.ui.editor.document.model.DocTextTransform
import com.thgiang.image.studio.ui.editor.document.model.DocumentSelection
import com.thgiang.image.studio.ui.editor.document.model.DocumentSnapshot
import com.thgiang.image.studio.ui.editor.document.model.EdgeInsets
import com.thgiang.image.studio.ui.editor.document.model.Effect
import com.thgiang.image.studio.ui.editor.document.model.Fill
import com.thgiang.image.studio.ui.editor.document.model.FrameGeometry
import com.thgiang.image.studio.ui.editor.document.model.HeightConstraint
import com.thgiang.image.studio.ui.editor.document.model.LayoutConstraints
import com.thgiang.image.studio.ui.editor.document.model.NodePart
import com.thgiang.image.studio.ui.editor.document.model.NodeTransform
import com.thgiang.image.studio.ui.editor.document.model.SceneNode
import com.thgiang.image.studio.ui.editor.document.model.StrokeSpec
import com.thgiang.image.studio.ui.editor.document.model.StyleBag
import com.thgiang.image.studio.ui.editor.document.model.TextContent
import com.thgiang.image.studio.ui.editor.document.model.TextRun
import com.thgiang.image.studio.ui.editor.document.model.WidthConstraint
import com.thgiang.image.studio.ui.editor.label.geometry.EditorShapeGeometry
import com.thgiang.image.studio.ui.editor.label.model.ShapeLabelDefaults
import com.thgiang.image.studio.ui.editor.model.EditorAppearance
import com.thgiang.image.studio.ui.editor.model.EditorLayer
import com.thgiang.image.studio.ui.editor.model.EditorTextSpan
import com.thgiang.image.studio.ui.editor.model.ElevationTarget
import com.thgiang.image.studio.ui.editor.model.LayerGroupRole
import com.thgiang.image.studio.ui.editor.model.LayerType
import com.thgiang.image.studio.ui.editor.model.ShapeType
import com.thgiang.image.studio.ui.editor.model.TextRunOps
import com.thgiang.image.studio.ui.editor.model.frameInGroup
import com.thgiang.image.studio.ui.editor.model.groupMembers
import com.thgiang.image.studio.ui.editor.model.isFrameLayer
import com.thgiang.image.studio.ui.editor.model.isLabelLayer
import com.thgiang.image.studio.ui.editor.model.labelInGroup

/**
 * Bidirectional bridge between Document v2 and legacy [EditorLayer] lists.
 */
object EditorLayerBridge {

    fun fromEditorLayers(
        layers: List<EditorLayer>,
        templateWidth: Float,
        templateHeight: Float,
        selectedLayerId: String?,
    ): DocumentSnapshot {
        val nodes = mutableListOf<SceneNode>()
        val consumed = mutableSetOf<String>()

        for (layer in layers) {
            if (layer.id in consumed) continue
            when {
                layer.groupId != null && layer.groupRole != null -> {
                    val members = layers.groupMembers(layer)
                    val frame = members.firstOrNull { it.groupRole == LayerGroupRole.FRAME }
                    val label = members.firstOrNull { it.groupRole == LayerGroupRole.LABEL }
                    if (frame != null && label != null) {
                        consumed += frame.id
                        consumed += label.id
                        nodes += toTextInShape(frame, label)
                    } else {
                        consumed += layer.id
                        nodes += toSingleNode(layer)
                    }
                }
                layer.type == LayerType.IMAGE || layer.type == LayerType.SHADOW_REGION -> {
                    consumed += layer.id
                    nodes += SceneNode.LegacyPassthrough(
                        id = layer.id,
                        legacyType = layer.type.name,
                        isLocked = layer.isLocked,
                        isVisible = layer.isVisible,
                    )
                }
                else -> {
                    consumed += layer.id
                    nodes += toSingleNode(layer)
                }
            }
        }

        val selection = resolveSelection(nodes, layers, selectedLayerId)
        return DocumentSnapshot(
            nodes = nodes,
            selection = selection,
            templateWidth = templateWidth,
            templateHeight = templateHeight,
        )
    }

    /**
     * Projects document nodes back to EditorLayer list.
     * Preserves IMAGE/passthrough layers from [previousLegacy] by id.
     */
    fun toEditorLayers(
        snapshot: DocumentSnapshot,
        previousLegacy: List<EditorLayer>,
    ): List<EditorLayer> {
        val byId = previousLegacy.associateBy { it.id }
        val result = mutableListOf<EditorLayer>()
        for (node in snapshot.nodes) {
            when (node) {
                is SceneNode.PureText -> {
                    val base = byId[node.id] ?: EditorLayer(id = node.id, type = LayerType.TEXT)
                    result += fromPureText(node, base)
                }
                is SceneNode.TextInShape -> {
                    val frameBase = byId[node.frameId]
                        ?: byId[node.id]
                        ?: EditorLayer(id = node.frameId, type = LayerType.SHAPE)
                    val labelBase = byId[node.textId]
                        ?: EditorLayer(id = node.textId, type = LayerType.TEXT)
                    val (frame, label) = fromTextInShape(node, frameBase, labelBase)
                    result += frame
                    result += label
                }
                is SceneNode.Shape -> {
                    val base = byId[node.id] ?: EditorLayer(id = node.id, type = LayerType.SHAPE)
                    result += fromShape(node, base)
                }
                is SceneNode.LegacyPassthrough -> {
                    val base = byId[node.id]
                    if (base != null) {
                        result += base.copy(isLocked = node.isLocked, isVisible = node.isVisible)
                    }
                }
            }
        }
        // Append any previous legacy layers not represented (safety)
        val represented = result.map { it.id }.toSet() +
            result.mapNotNull { it.groupId }.flatMap { gid ->
                previousLegacy.filter { it.groupId == gid }.map { it.id }
            }
        previousLegacy.forEach { layer ->
            if (layer.id !in represented &&
                result.none { it.id == layer.id } &&
                layer.type == LayerType.IMAGE
            ) {
                // Already handled via passthrough; if missing, keep
                if (snapshot.nodes.none { it.id == layer.id }) {
                    result += layer
                }
            }
        }
        return result
    }

    fun findBridgeLayer(legacy: List<EditorLayer>, node: SceneNode): EditorLayer? = when (node) {
        is SceneNode.PureText -> legacy.find { it.id == node.id }
        is SceneNode.TextInShape -> legacy.find { it.id == node.textId } ?: legacy.find { it.id == node.frameId }
        is SceneNode.Shape -> legacy.find { it.id == node.id }
        is SceneNode.LegacyPassthrough -> legacy.find { it.id == node.id }
    }

    /** Resolve document node id from a legacy layer id. */
    fun nodeIdForLayer(snapshot: DocumentSnapshot, layerId: String): String? {
        snapshot.nodes.forEach { node ->
            when (node) {
                is SceneNode.PureText -> if (node.id == layerId) return node.id
                is SceneNode.Shape -> if (node.id == layerId) return node.id
                is SceneNode.TextInShape -> {
                    if (node.id == layerId || node.frameId == layerId || node.textId == layerId) {
                        return node.id
                    }
                }
                is SceneNode.LegacyPassthrough -> if (node.id == layerId) return node.id
            }
        }
        return null
    }

    fun partForLayer(snapshot: DocumentSnapshot, layerId: String): NodePart {
        val node = snapshot.nodes.firstOrNull { n ->
            when (n) {
                is SceneNode.TextInShape -> n.frameId == layerId || n.textId == layerId || n.id == layerId
                else -> n.id == layerId
            }
        } ?: return NodePart.WHOLE
        return when (node) {
            is SceneNode.TextInShape -> when (layerId) {
                node.frameId -> NodePart.FRAME
                node.textId -> NodePart.TEXT
                else -> NodePart.WHOLE
            }
            else -> NodePart.WHOLE
        }
    }

    // ── to nodes ──────────────────────────────────────────────

    private fun toSingleNode(layer: EditorLayer): SceneNode = when {
        layer.isLabelLayer && EditorShapeGeometry.isTextOnlyShape(layer.shapeType) ->
            toPureText(layer)
        layer.isFrameLayer && !layer.isLabelLayer && layer.text.isBlank() ->
            toShape(layer)
        layer.isLabelLayer -> toPureText(layer)
        layer.isFrameLayer -> toShape(layer)
        else -> SceneNode.LegacyPassthrough(
            id = layer.id,
            legacyType = layer.type.name,
            isLocked = layer.isLocked,
            isVisible = layer.isVisible,
        )
    }

    private fun toPureText(layer: EditorLayer): SceneNode.PureText {
        val content = contentFromLabel(layer)
        val padding = if (layer.hasDecorFill()) EdgeInsets.SHAPE_TEXT else EdgeInsets.ZERO
        return SceneNode.PureText(
            id = layer.id,
            content = content,
            layout = LayoutConstraints(
                width = WidthConstraint.Fixed,
                height = HeightConstraint.Hug,
                boxWidthPx = layer.shapeWidthPx,
                boxHeightPx = layer.shapeHeightPx,
                padding = padding,
                transform = NodeTransform.fromViewport(layer.viewport),
            ),
            style = styleFromLabelLayer(layer),
            isLocked = layer.isLocked,
            isVisible = layer.isVisible,
        )
    }

    private fun toShape(layer: EditorLayer): SceneNode.Shape =
        SceneNode.Shape(
            id = layer.id,
            geometry = FrameGeometry(
                shapeType = layer.shapeType,
                cornerRadiusX = layer.cornerRadiusX,
                cornerRadiusY = layer.cornerRadiusY,
                pathData = layer.pathData,
                polygonPoints = layer.polygonPoints,
            ),
            layout = LayoutConstraints(
                width = WidthConstraint.Fixed,
                height = HeightConstraint.Fixed,
                boxWidthPx = layer.shapeWidthPx,
                boxHeightPx = layer.shapeHeightPx,
                transform = NodeTransform.fromViewport(layer.viewport),
            ),
            style = styleFromFrameLayer(layer),
            isLocked = layer.isLocked,
            isVisible = layer.isVisible,
        )

    private fun toTextInShape(frame: EditorLayer, label: EditorLayer): SceneNode.TextInShape {
        val content = contentFromLabel(label)
        // I3: prefer label viewport if they drifted; use label size as source of truth for box
        val transform = NodeTransform.fromViewport(label.viewport)
        return SceneNode.TextInShape(
            id = label.groupId ?: label.id,
            frameId = frame.id,
            textId = label.id,
            frameGeometry = FrameGeometry(
                shapeType = frame.shapeType,
                cornerRadiusX = frame.cornerRadiusX,
                cornerRadiusY = frame.cornerRadiusY,
                pathData = frame.pathData,
                polygonPoints = frame.polygonPoints,
            ),
            frameStyle = styleFromFrameLayer(frame),
            textContent = content,
            textStyle = styleFromLabelLayer(label),
            layout = LayoutConstraints(
                width = WidthConstraint.Fixed,
                height = HeightConstraint.Hug,
                boxWidthPx = label.shapeWidthPx,
                boxHeightPx = label.shapeHeightPx,
                padding = EdgeInsets.SHAPE_TEXT,
                transform = transform,
            ),
            textPadding = EdgeInsets.SHAPE_TEXT,
            isLocked = label.isLocked || frame.isLocked,
            isVisible = label.isVisible && frame.isVisible,
        )
    }

    // ── from nodes ────────────────────────────────────────────

    private fun fromPureText(node: SceneNode.PureText, base: EditorLayer): EditorLayer {
        val run = node.content.primaryRun
        val style = node.style
        val fill = style.primaryFill
        val (shapeColor, fillGrad) = fillToArgb(fill)
        val stroke = style.strokes.firstOrNull()
        // Preserve PureText elevation target across Document round-trips (UI flag).
        val appearance = appearanceFromStyle(style, base.appearance.elevationTarget, base.appearance)
        val spans = spansFromContent(node.content)
        return base.copy(
            id = node.id,
            type = LayerType.TEXT,
            groupId = null,
            groupRole = null,
            text = node.content.text,
            textSpans = spans,
            textSizeSp = run.textSizeSp,
            textColorArgb = run.colorArgb,
            textColorGradient = run.colorGradient,
            fontFamily = run.fontFamily,
            fontWeight = run.fontWeight,
            fontStyle = run.fontStyle,
            textAlign = node.content.textAlign.toLegacy(),
            textTransform = node.content.textTransform.toLegacy(),
            underline = run.underline,
            linethrough = run.linethrough,
            lineHeight = node.content.lineHeight,
            charSpacing = node.content.charSpacing,
            shapeType = ShapeType.TEXT_ONLY,
            shapeWidthPx = node.layout.boxWidthPx,
            shapeHeightPx = node.layout.boxHeightPx,
            shapeColorArgb = shapeColor,
            fillGradient = fillGrad,
            strokeColorArgb = stroke?.colorArgb,
            strokeWidthPx = stroke?.widthPx ?: 0f,
            strokeDashArray = stroke?.dashArray ?: emptyList(),
            strokeDashGapPx = stroke?.dashGapPx ?: 6f,
            textForm = style.textForm,
            blendMode = style.blendMode,
            viewport = node.layout.transform.toViewport(),
            appearance = appearance,
            isLocked = node.isLocked,
            isVisible = node.isVisible,
        )
    }

    private fun fromShape(node: SceneNode.Shape, base: EditorLayer): EditorLayer {
        val style = node.style
        val (shapeColor, fillGrad) = fillToArgb(style.primaryFill)
        val stroke = style.strokes.firstOrNull()
        return base.copy(
            id = node.id,
            type = LayerType.SHAPE,
            groupId = null,
            groupRole = null,
            text = "",
            shapeType = node.geometry.shapeType,
            shapeWidthPx = node.layout.boxWidthPx,
            shapeHeightPx = node.layout.boxHeightPx,
            cornerRadiusX = node.geometry.cornerRadiusX,
            cornerRadiusY = node.geometry.cornerRadiusY,
            pathData = node.geometry.pathData,
            polygonPoints = node.geometry.polygonPoints,
            shapeColorArgb = shapeColor,
            fillGradient = fillGrad,
            strokeColorArgb = stroke?.colorArgb,
            strokeWidthPx = stroke?.widthPx ?: 0f,
            strokeDashArray = stroke?.dashArray ?: emptyList(),
            strokeDashGapPx = stroke?.dashGapPx ?: 6f,
            blendMode = style.blendMode,
            viewport = node.layout.transform.toViewport(),
            appearance = appearanceFromStyle(style, ElevationTarget.SHAPE, base.appearance),
            isLocked = node.isLocked,
            isVisible = node.isVisible,
        )
    }

    private fun fromTextInShape(
        node: SceneNode.TextInShape,
        frameBase: EditorLayer,
        labelBase: EditorLayer,
    ): Pair<EditorLayer, EditorLayer> {
        val gid = node.id
        val run = node.textContent.primaryRun
        val (frameColor, frameGrad) = fillToArgb(node.frameStyle.primaryFill)
        val frameStroke = node.frameStyle.strokes.firstOrNull()
        val sharedViewport = node.layout.transform.toViewport()
        val w = node.layout.boxWidthPx
        val h = node.layout.boxHeightPx

        val frame = frameBase.copy(
            id = node.frameId,
            type = LayerType.SHAPE,
            groupId = gid,
            groupRole = LayerGroupRole.FRAME,
            text = "",
            shapeType = node.frameGeometry.shapeType,
            shapeWidthPx = w,
            shapeHeightPx = h,
            cornerRadiusX = node.frameGeometry.cornerRadiusX,
            cornerRadiusY = node.frameGeometry.cornerRadiusY,
            pathData = node.frameGeometry.pathData,
            polygonPoints = node.frameGeometry.polygonPoints,
            shapeColorArgb = frameColor,
            fillGradient = frameGrad,
            strokeColorArgb = frameStroke?.colorArgb,
            strokeWidthPx = frameStroke?.widthPx ?: 0f,
            strokeDashArray = frameStroke?.dashArray ?: emptyList(),
            strokeDashGapPx = frameStroke?.dashGapPx ?: 6f,
            viewport = sharedViewport,
            appearance = appearanceFromStyle(node.frameStyle, ElevationTarget.SHAPE, frameBase.appearance),
            isLocked = node.isLocked,
            isVisible = node.isVisible,
            textForm = com.thgiang.image.studio.ui.editor.model.TextFormEffect(),
        )
        val label = labelBase.copy(
            id = node.textId,
            type = LayerType.TEXT,
            groupId = gid,
            groupRole = LayerGroupRole.LABEL,
            text = node.textContent.text,
            textSpans = spansFromContent(node.textContent),
            textSizeSp = run.textSizeSp,
            textColorArgb = run.colorArgb,
            textColorGradient = run.colorGradient,
            fontFamily = run.fontFamily,
            fontWeight = run.fontWeight,
            fontStyle = run.fontStyle,
            textAlign = node.textContent.textAlign.toLegacy(),
            textTransform = node.textContent.textTransform.toLegacy(),
            underline = run.underline,
            linethrough = run.linethrough,
            lineHeight = node.textContent.lineHeight,
            charSpacing = node.textContent.charSpacing,
            shapeType = node.frameGeometry.shapeType,
            shapeWidthPx = w,
            shapeHeightPx = h,
            shapeColorArgb = ShapeLabelDefaults.TRANSPARENT_FILL_ARGB,
            fillGradient = null,
            strokeColorArgb = null,
            strokeWidthPx = 0f,
            strokeDashArray = emptyList(),
            textForm = node.textStyle.textForm,
            blendMode = node.textStyle.blendMode,
            viewport = sharedViewport,
            appearance = appearanceFromStyle(node.textStyle, ElevationTarget.TEXT, labelBase.appearance)
                .copy(shadowIntensity = 0f),
            isLocked = node.isLocked,
            isVisible = node.isVisible,
        )
        return frame to label
    }

    // ── style helpers ─────────────────────────────────────────

    private fun contentFromLabel(layer: EditorLayer): TextContent {
        val spans = TextRunOps.effectiveSpans(layer)
        val runs = spans.map { span ->
            TextRun(
                text = span.text,
                fontFamily = span.fontFamily ?: layer.fontFamily,
                fontWeight = span.fontWeight ?: layer.fontWeight,
                fontStyle = span.fontStyle ?: layer.fontStyle,
                textSizeSp = layer.textSizeSp,
                colorArgb = span.colorArgb ?: layer.textColorArgb,
                // Keep layer gradient across multi-run splits (bold/italic); solid color clears it elsewhere.
                colorGradient = layer.textColorGradient,
                underline = span.underline ?: layer.underline,
                linethrough = span.linethrough ?: layer.linethrough,
            )
        }
        return TextContent(
            text = layer.text,
            runs = runs.ifEmpty {
                listOf(
                    TextRun(
                        text = layer.text,
                        fontFamily = layer.fontFamily,
                        fontWeight = layer.fontWeight,
                        fontStyle = layer.fontStyle,
                        textSizeSp = layer.textSizeSp,
                        colorArgb = layer.textColorArgb,
                        colorGradient = layer.textColorGradient,
                        underline = layer.underline,
                        linethrough = layer.linethrough,
                    ),
                )
            },
            textAlign = DocTextAlign.fromLegacy(layer.textAlign),
            textTransform = DocTextTransform.fromLegacy(layer.textTransform),
            lineHeight = layer.lineHeight,
            charSpacing = layer.charSpacing,
        )
    }

    private fun spansFromContent(content: TextContent): List<EditorTextSpan> =
        content.runs.map { run ->
            EditorTextSpan(
                text = run.text,
                fontWeight = run.fontWeight,
                fontStyle = run.fontStyle,
                colorArgb = run.colorArgb,
                underline = run.underline,
                linethrough = run.linethrough,
                fontFamily = run.fontFamily,
            )
        }

    private fun styleFromLabelLayer(layer: EditorLayer): StyleBag {
        val fills = buildList {
            if (layer.fillGradient != null) {
                add(Fill.Gradient(layer.fillGradient, layer.shapeColorArgb))
            } else {
                val a = (layer.shapeColorArgb ushr 24) and 0xFF
                if (a > 0) add(Fill.Solid(layer.shapeColorArgb))
            }
        }
        val strokes = buildList {
            val c = layer.strokeColorArgb
            if (c != null && layer.strokeWidthPx > 0f) {
                add(
                    StrokeSpec(
                        colorArgb = c,
                        widthPx = layer.strokeWidthPx,
                        dashArray = layer.strokeDashArray,
                        dashGapPx = layer.strokeDashGapPx,
                    ),
                )
            }
        }
        val effects = buildList {
            if (layer.appearance.shadowIntensity > 0.05f) {
                add(
                    Effect.DropShadow(
                        intensity = layer.appearance.shadowIntensity,
                        angle = layer.appearance.shadowAngle,
                        distance = layer.appearance.shadowDistance,
                        colorArgb = layer.appearance.shadowColorArgb,
                        blurPx = layer.appearance.shadowBlur,
                    ),
                )
            }
            if (layer.appearance.elevationIntensity > 0.01f ||
                (layer.appearance.depthSizePx ?: 0f) > 0.5f
            ) {
                add(
                    Effect.Elevation3D(
                        intensity = layer.appearance.elevationIntensity,
                        style = layer.appearance.elevationStyle,
                        depthSizePx = layer.appearance.depthSizePx,
                        depthColorArgb = layer.appearance.depthColorArgb,
                        extrusionAngle = layer.appearance.extrusionAngle,
                        softBlurPx = layer.appearance.elevationShadowBlur,
                    ),
                )
            }
        }
        return StyleBag(
            fills = fills,
            strokes = strokes,
            effects = effects,
            textForm = layer.textForm,
            blendMode = layer.blendMode,
            opacity = layer.appearance.alpha,
        )
    }

    private fun styleFromFrameLayer(layer: EditorLayer): StyleBag = styleFromLabelLayer(layer)

    private fun appearanceFromStyle(
        style: StyleBag,
        elevationTarget: ElevationTarget,
        base: EditorAppearance,
    ): EditorAppearance {
        val drop = style.dropShadow
        val elev = style.elevation
        return base.copy(
            shadowIntensity = drop?.intensity ?: 0f,
            shadowAngle = drop?.angle ?: base.shadowAngle,
            shadowDistance = drop?.distance ?: base.shadowDistance,
            shadowColorArgb = drop?.colorArgb ?: base.shadowColorArgb,
            shadowBlur = drop?.blurPx,
            alpha = style.opacity.coerceIn(0.1f, 1f),
            elevationIntensity = elev?.intensity ?: 0f,
            elevationStyle = elev?.style ?: base.elevationStyle,
            depthSizePx = elev?.depthSizePx,
            depthColorArgb = elev?.depthColorArgb,
            extrusionAngle = elev?.extrusionAngle ?: base.extrusionAngle,
            elevationShadowBlur = elev?.softBlurPx,
            elevationTarget = elevationTarget,
        )
    }

    private fun fillToArgb(fill: Fill?): Pair<Int, com.thgiang.image.core.domain.model.template.CloudGradient?> =
        when (fill) {
            is Fill.Solid -> fill.argb to null
            is Fill.Gradient -> fill.fallbackArgb to fill.gradient
            null -> ShapeLabelDefaults.TRANSPARENT_FILL_ARGB to null
        }

    private fun EditorLayer.hasDecorFill(): Boolean {
        val a = (shapeColorArgb ushr 24) and 0xFF
        return a > 0 || fillGradient != null
    }

    private fun resolveSelection(
        nodes: List<SceneNode>,
        layers: List<EditorLayer>,
        selectedLayerId: String?,
    ): DocumentSelection {
        if (selectedLayerId == null) return DocumentSelection()
        val layer = layers.find { it.id == selectedLayerId } ?: return DocumentSelection()
        nodes.forEach { node ->
            when (node) {
                is SceneNode.PureText -> if (node.id == selectedLayerId) {
                    return DocumentSelection(node.id, NodePart.WHOLE)
                }
                is SceneNode.Shape -> if (node.id == selectedLayerId) {
                    return DocumentSelection(node.id, NodePart.WHOLE)
                }
                is SceneNode.TextInShape -> {
                    when (selectedLayerId) {
                        node.frameId -> return DocumentSelection(node.id, NodePart.FRAME)
                        node.textId -> return DocumentSelection(node.id, NodePart.TEXT)
                        node.id -> return DocumentSelection(node.id, NodePart.WHOLE)
                    }
                    if (layer.groupId == node.id) {
                        return DocumentSelection(
                            node.id,
                            if (layer.groupRole == LayerGroupRole.FRAME) NodePart.FRAME else NodePart.TEXT,
                        )
                    }
                }
                is SceneNode.LegacyPassthrough -> if (node.id == selectedLayerId) {
                    return DocumentSelection(node.id, NodePart.WHOLE)
                }
            }
        }
        return DocumentSelection()
    }
}
