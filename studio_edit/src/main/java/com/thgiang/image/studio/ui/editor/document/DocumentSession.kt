package com.thgiang.image.studio.ui.editor.document

import android.content.Context
import com.thgiang.image.core.domain.model.template.CloudGradient
import com.thgiang.image.studio.ui.editor.document.adapter.EditorLayerBridge
import com.thgiang.image.studio.ui.editor.document.command.DocumentCommand
import com.thgiang.image.studio.ui.editor.document.command.StyleTarget
import com.thgiang.image.studio.ui.editor.document.model.DocTextAlign
import com.thgiang.image.studio.ui.editor.document.model.DocTextTransform
import com.thgiang.image.studio.ui.editor.document.model.DocumentSnapshot
import com.thgiang.image.studio.ui.editor.document.model.EdgeInsets
import com.thgiang.image.studio.ui.editor.document.model.Effect
import com.thgiang.image.studio.ui.editor.document.model.Fill
import com.thgiang.image.studio.ui.editor.document.model.FrameGeometry
import com.thgiang.image.studio.ui.editor.document.model.HeightConstraint
import com.thgiang.image.studio.ui.editor.document.model.NodePart
import com.thgiang.image.studio.ui.editor.document.model.NodeTransform
import com.thgiang.image.studio.ui.editor.document.model.SceneNode
import com.thgiang.image.studio.ui.editor.document.model.StrokeSpec
import com.thgiang.image.studio.ui.editor.document.model.StyleBag
import com.thgiang.image.studio.ui.editor.document.model.TextContent
import com.thgiang.image.studio.ui.editor.document.model.TextRun
import com.thgiang.image.studio.ui.editor.document.model.WidthConstraint
import com.thgiang.image.studio.ui.editor.document.rules.LayoutIntent
import com.thgiang.image.studio.ui.editor.document.store.DocumentStore
import com.thgiang.image.studio.ui.editor.label.model.ShapeLabelDefaults
import com.thgiang.image.studio.ui.editor.label.model.applyShapeTypeChange
import com.thgiang.image.studio.ui.editor.label.panel.TextStyleTemplate
import com.thgiang.image.studio.ui.editor.label.panel.findTextStyleTemplate
import com.thgiang.image.studio.ui.editor.mapper.EditorGradientMapper
import com.thgiang.image.studio.ui.editor.model.EditorAppearance
import com.thgiang.image.studio.ui.editor.model.EditorLayer
import com.thgiang.image.studio.ui.editor.model.EditorState
import com.thgiang.image.studio.ui.editor.model.EditorTextSpan
import com.thgiang.image.studio.ui.editor.model.ElevationTarget
import com.thgiang.image.studio.ui.editor.model.ShapeElevationStyle
import com.thgiang.image.studio.ui.editor.model.ShapeType
import com.thgiang.image.studio.ui.editor.model.TextFormEffect
import com.thgiang.image.studio.ui.editor.model.TextFormPreset
import com.thgiang.image.studio.ui.editor.model.TextRunOps
import com.thgiang.image.studio.ui.editor.model.TextSpanStylePatch
import com.thgiang.image.studio.ui.editor.model.isFrameLayer
import com.thgiang.image.studio.ui.editor.model.withPreset
import java.util.UUID

/**
 * Facade used by ThemeplateEditorViewModel during strangler migration.
 * Keeps DocumentStore in sync and returns projected legacy layers.
 */
class DocumentSession(
    context: Context,
) {
    val store = DocumentStore(context.applicationContext)

    @Volatile
    var enabled: Boolean = true

    fun syncFromState(state: EditorState) {
        if (!enabled) return
        val tw = state.template.originalWidth.toFloat().coerceAtLeast(1f)
        val th = state.template.originalHeight.toFloat().coerceAtLeast(1f)
        store.loadFromLegacyLayers(
            layers = state.layers,
            templateWidth = tw,
            templateHeight = th,
            selectedLayerId = state.selectedLayerId,
            pushHistory = false,
        )
    }

    fun snapshot(): DocumentSnapshot = store.current()

    fun projectedLayers(): List<EditorLayer> = store.legacyLayers.value

    /** Align Document [NodePart] with the selected legacy layer (FRAME vs TEXT). */
    fun selectForLayer(selectedLayerId: String?) {
        if (!enabled) return
        if (selectedLayerId == null) {
            store.dispatch(DocumentCommand.SelectNode(null), recordHistory = false)
            return
        }
        val snap = store.current()
        val nodeId = EditorLayerBridge.nodeIdForLayer(snap, selectedLayerId) ?: return
        val part = EditorLayerBridge.partForLayer(snap, selectedLayerId)
        store.dispatch(DocumentCommand.SelectNode(nodeId, part), recordHistory = false)
    }

    // ── Text content ──────────────────────────────────────────

    fun editText(selectedLayerId: String?, text: String, inline: Boolean): List<EditorLayer>? {
        val nodeId = resolveNodeId(selectedLayerId) ?: return null
        val content = textContentOf(selectedLayerId) ?: return null
        val spans = contentToSpans(content)
        val reflowed = TextRunOps.reflow(spans, content.text, text)
        val next = contentFromSpans(content, reflowed, text)
        store.dispatch(
            DocumentCommand.SetTextContent(
                nodeId = nodeId,
                content = next,
                intent = if (inline) LayoutIntent.InlineGrow else LayoutIntent.EditText,
            ),
        )
        return store.legacyLayers.value
    }

    fun insertNewline(selectedLayerId: String?): List<EditorLayer>? {
        val content = textContentOf(selectedLayerId) ?: return null
        return editText(selectedLayerId, content.text + "\n", inline = false)
    }

    // ── Typography / content bag ───────────────────────────────

    fun applyTextTransform(selectedLayerId: String?, transform: String?): List<EditorLayer>? =
        patchTextContent(selectedLayerId, LayoutIntent.StyleOrCaseChange) {
            it.copy(textTransform = DocTextTransform.fromLegacy(transform))
        }

    fun setFontSize(selectedLayerId: String?, sizeSp: Float): List<EditorLayer>? =
        patchTextContent(selectedLayerId, LayoutIntent.FontSizeChange) { content ->
            val size = sizeSp.coerceIn(1f, ShapeLabelDefaults.MAX_TEXT_SIZE_SP)
            content.withAllRuns { it.copy(textSizeSp = size) }
        }

    fun setFontFamily(selectedLayerId: String?, fontFamily: String?): List<EditorLayer>? =
        applySpanStyle(
            selectedLayerId,
            start = 0,
            end = Int.MAX_VALUE,
            patch = TextSpanStylePatch(fontFamily = fontFamily?.takeIf { f -> f.isNotBlank() }),
        )

    fun setBold(
        selectedLayerId: String?,
        bold: Boolean,
        selectionStart: Int = 0,
        selectionEnd: Int = Int.MAX_VALUE,
    ): List<EditorLayer>? =
        applySpanStyle(
            selectedLayerId,
            selectionStart,
            selectionEnd,
            TextSpanStylePatch(fontWeight = if (bold) "bold" else "normal"),
        )

    fun setItalic(
        selectedLayerId: String?,
        italic: Boolean,
        selectionStart: Int = 0,
        selectionEnd: Int = Int.MAX_VALUE,
    ): List<EditorLayer>? =
        applySpanStyle(
            selectedLayerId,
            selectionStart,
            selectionEnd,
            TextSpanStylePatch(fontStyle = if (italic) "italic" else "normal"),
        )

    fun setUnderline(
        selectedLayerId: String?,
        underline: Boolean,
        selectionStart: Int = 0,
        selectionEnd: Int = Int.MAX_VALUE,
    ): List<EditorLayer>? =
        applySpanStyle(
            selectedLayerId,
            selectionStart,
            selectionEnd,
            TextSpanStylePatch(underline = underline),
        )

    fun setLinethrough(
        selectedLayerId: String?,
        linethrough: Boolean,
        selectionStart: Int = 0,
        selectionEnd: Int = Int.MAX_VALUE,
    ): List<EditorLayer>? =
        applySpanStyle(
            selectedLayerId,
            selectionStart,
            selectionEnd,
            TextSpanStylePatch(linethrough = linethrough),
        )

    fun setAlign(selectedLayerId: String?, align: String): List<EditorLayer>? =
        patchTextContent(selectedLayerId, LayoutIntent.StyleOrCaseChange) {
            it.copy(textAlign = DocTextAlign.fromLegacy(align))
        }

    fun setLineHeight(selectedLayerId: String?, multiplier: Float): List<EditorLayer>? =
        patchTextContent(selectedLayerId, LayoutIntent.StyleOrCaseChange) {
            it.copy(lineHeight = multiplier.coerceIn(0.5f, 3f))
        }

    fun setCharSpacing(selectedLayerId: String?, spacing: Float): List<EditorLayer>? =
        patchTextContent(selectedLayerId, LayoutIntent.StyleOrCaseChange) {
            it.copy(charSpacing = spacing.coerceIn(-20f, 80f))
        }

    fun applyTypographyPreset(
        selectedLayerId: String?,
        fontWeight: String,
        textSizeSp: Float,
        textTransform: String?,
    ): List<EditorLayer>? =
        patchTextContent(selectedLayerId, LayoutIntent.StyleOrCaseChange) { content ->
            content
                .withAllRuns {
                    it.copy(
                        fontWeight = fontWeight,
                        textSizeSp = textSizeSp.coerceIn(1f, ShapeLabelDefaults.MAX_TEXT_SIZE_SP),
                    )
                }
                .copy(textTransform = DocTextTransform.fromLegacy(textTransform))
        }

    fun setTextColor(
        selectedLayerId: String?,
        argb: Int,
        selectionStart: Int = 0,
        selectionEnd: Int = Int.MAX_VALUE,
    ): List<EditorLayer>? =
        applySpanStyle(
            selectedLayerId,
            selectionStart,
            selectionEnd,
            TextSpanStylePatch(colorArgb = argb),
        )

    fun setTextColorGradient(selectedLayerId: String?, gradient: CloudGradient?): List<EditorLayer>? =
        patchTextContent(selectedLayerId, LayoutIntent.FontSizeChange) { content ->
            val startArgb = gradient?.let {
                EditorGradientMapper.parseStopArgb(it, 0, content.primaryRun.colorArgb)
            }
            content.withAllRuns {
                it.copy(
                    colorGradient = gradient,
                    colorArgb = startArgb ?: it.colorArgb,
                )
            }
        }

    /**
     * Apply a style patch to [start, end). If the range is empty / full-text sentinel,
     * styles the entire string (toolbar without selection).
     */
    fun applySpanStyle(
        selectedLayerId: String?,
        start: Int,
        end: Int,
        patch: TextSpanStylePatch,
    ): List<EditorLayer>? {
        val content = textContentOf(selectedLayerId) ?: return null
        val nodeId = resolveNodeId(selectedLayerId) ?: return null
        val spans = contentToSpans(content)
        // Explicit full-text sentinel from panel (not editing).
        if (end == Int.MAX_VALUE || (start <= 0 && end >= content.text.length && end > start)) {
            val nextSpans = TextRunOps.applyStyle(spans, 0, content.text.length, patch)
            val next = contentFromSpans(content, nextSpans, content.text, patch)
            store.dispatch(
                DocumentCommand.SetTextContent(
                    nodeId = nodeId,
                    content = next,
                    intent = LayoutIntent.StyleOrCaseChange,
                ),
                recordHistory = false,
            )
            return store.legacyLayers.value
        }
        if (end <= start) {
            // Collapsed / invalid — do not restyle entire string.
            return store.legacyLayers.value
        }
        val rangeStart = start.coerceIn(0, content.text.length)
        val rangeEnd = end.coerceIn(0, content.text.length)
        if (rangeStart >= rangeEnd) {
            return store.legacyLayers.value
        }
        val nextSpans = TextRunOps.applyStyle(spans, rangeStart, rangeEnd, patch)
        val next = contentFromSpans(content, nextSpans, content.text, patch)
        store.dispatch(
            DocumentCommand.SetTextContent(
                nodeId = nodeId,
                content = next,
                intent = LayoutIntent.StyleOrCaseChange,
            ),
            recordHistory = false,
        )
        return store.legacyLayers.value
    }

    // ── Text form ─────────────────────────────────────────────

    fun applyTextFormPreset(
        selectedLayerId: String?,
        preset: TextFormPreset,
        amount: Float? = null,
    ): List<EditorLayer>? {
        val nodeId = resolveNodeId(selectedLayerId) ?: return null
        store.dispatch(DocumentCommand.ApplyTextFormPreset(nodeId, preset, amount))
        return store.legacyLayers.value
    }

    fun setTextFormAmount(selectedLayerId: String?, amount: Float): List<EditorLayer>? {
        val nodeId = resolveNodeId(selectedLayerId) ?: return null
        val style = textStyleOf(nodeId) ?: return null
        val form = style.textForm.copy(amount = amount.coerceIn(0f, TextFormEffect.MAX_AMOUNT))
        store.dispatch(DocumentCommand.SetTextForm(nodeId, form, LayoutIntent.TextFormMeasure))
        return store.legacyLayers.value
    }

    fun resetTextForm(selectedLayerId: String?): List<EditorLayer>? {
        val nodeId = resolveNodeId(selectedLayerId) ?: return null
        store.dispatch(
            DocumentCommand.SetTextForm(nodeId, TextFormEffect(), LayoutIntent.StyleOrCaseChange),
        )
        return store.legacyLayers.value
    }

    // ── Style templates ───────────────────────────────────────

    fun applyTextStyleTemplate(selectedLayerId: String?, templateId: String): List<EditorLayer>? {
        val template = findTextStyleTemplate(templateId) ?: return null
        val nodeId = resolveNodeId(selectedLayerId) ?: return null
        val node = store.current().node(nodeId) ?: return null
        val (textStyle, frameStyle, _) = template.toStyleBags()
        val contentPatch = when (node) {
            is SceneNode.PureText -> mergeTemplateTypography(node.content, template)
            is SceneNode.TextInShape -> mergeTemplateTypography(node.textContent, template)
            else -> null
        }
        store.dispatch(
            DocumentCommand.ApplyStyleTemplate(
                nodeId = nodeId,
                textStyle = textStyle,
                frameStyle = frameStyle,
                textContentPatch = contentPatch,
                intent = LayoutIntent.StyleOrCaseChange,
            ),
        )
        return store.legacyLayers.value
    }

    // ── Decor fill / stroke (preserve box) ────────────────────

    fun setShapeFill(selectedLayerId: String?, argb: Int): List<EditorLayer>? =
        patchDecorStyle(selectedLayerId, LayoutIntent.FontSizeChange) { style ->
            style.copy(fills = listOf(Fill.Solid(argb)))
        }

    fun setShapeFillOpacity(selectedLayerId: String?, alpha: Float): List<EditorLayer>? =
        patchDecorStyle(selectedLayerId, LayoutIntent.FontSizeChange) { style ->
            val alphaByte = (alpha.coerceIn(0.1f, 1f) * 255f).toInt().coerceIn(0, 255)
            val fills = style.fills.map { fill ->
                when (fill) {
                    is Fill.Solid -> Fill.Solid((alphaByte shl 24) or (fill.argb and 0x00FFFFFF))
                    is Fill.Gradient -> fill.copy(
                        fallbackArgb = (alphaByte shl 24) or (fill.fallbackArgb and 0x00FFFFFF),
                    )
                }
            }
            style.copy(fills = fills)
        }

    fun setFillGradient(selectedLayerId: String?, gradient: CloudGradient?): List<EditorLayer>? =
        patchDecorStyle(selectedLayerId, LayoutIntent.FontSizeChange) { style ->
            val fallback = when (val f = style.primaryFill) {
                is Fill.Solid -> f.argb
                is Fill.Gradient -> f.fallbackArgb
                null -> ShapeLabelDefaults.TRANSPARENT_FILL_ARGB
            }
            style.copy(
                fills = if (gradient == null) {
                    listOf(Fill.Solid(fallback))
                } else {
                    listOf(Fill.Gradient(gradient, fallback))
                },
            )
        }

    fun setStrokeColor(selectedLayerId: String?, argb: Int): List<EditorLayer>? =
        patchDecorStyle(selectedLayerId, LayoutIntent.FontSizeChange) { style ->
            val alpha = (argb ushr 24) and 0xFF
            if (alpha == 0) {
                style.copy(strokes = emptyList())
            } else {
                val prev = style.strokes.firstOrNull()
                style.copy(
                    strokes = listOf(
                        StrokeSpec(
                            colorArgb = argb,
                            widthPx = prev?.widthPx?.takeIf { it > 0f } ?: 2f,
                            dashArray = prev?.dashArray.orEmpty(),
                            dashGapPx = prev?.dashGapPx ?: 6f,
                        ),
                    ),
                )
            }
        }

    fun setStrokeWidth(selectedLayerId: String?, widthPx: Float): List<EditorLayer>? =
        patchDecorStyle(selectedLayerId, LayoutIntent.FontSizeChange) { style ->
            val width = widthPx.coerceIn(0f, 20f)
            if (width <= 0f) {
                style.copy(strokes = emptyList())
            } else {
                val prev = style.strokes.firstOrNull()
                style.copy(
                    strokes = listOf(
                        StrokeSpec(
                            colorArgb = prev?.colorArgb ?: 0xFF000000.toInt(),
                            widthPx = width,
                            dashArray = prev?.dashArray.orEmpty(),
                            dashGapPx = prev?.dashGapPx ?: 6f,
                        ),
                    ),
                )
            }
        }

    fun setStrokeDash(selectedLayerId: String?, dashArray: List<Float>, gapPx: Float? = null): List<EditorLayer>? =
        patchDecorStyle(selectedLayerId, LayoutIntent.FontSizeChange) { style ->
            val prev = style.strokes.firstOrNull() ?: return@patchDecorStyle style
            val gap = gapPx ?: prev.dashGapPx
            style.copy(
                strokes = listOf(
                    prev.copy(dashArray = dashArray, dashGapPx = gap),
                ),
            )
        }

    fun setStrokeDashGap(selectedLayerId: String?, gapPx: Float): List<EditorLayer>? =
        patchDecorStyle(selectedLayerId, LayoutIntent.FontSizeChange) { style ->
            val prev = style.strokes.firstOrNull() ?: return@patchDecorStyle style
            val gap = gapPx.coerceIn(0f, 40f)
            val updatedArray = if (prev.dashArray.size >= 2) {
                prev.dashArray.mapIndexed { index, length ->
                    if (index % 2 == 1) gap else length
                }
            } else {
                prev.dashArray
            }
            style.copy(strokes = listOf(prev.copy(dashArray = updatedArray, dashGapPx = gap)))
        }

    fun setCornerRadius(selectedLayerId: String?, radiusPx: Float): List<EditorLayer>? {
        val nodeId = resolveNodeId(selectedLayerId) ?: return null
        val node = store.current().node(nodeId) ?: return null
        val r = radiusPx.coerceAtLeast(0f)
        return when (node) {
            is SceneNode.TextInShape -> {
                store.dispatch(
                    DocumentCommand.SetFrameGeometry(
                        nodeId = nodeId,
                        geometry = node.frameGeometry.copy(cornerRadiusX = r, cornerRadiusY = r),
                        intent = LayoutIntent.FontSizeChange,
                    ),
                )
                store.legacyLayers.value
            }
            is SceneNode.Shape -> {
                store.dispatch(
                    DocumentCommand.SetFrameGeometry(
                        nodeId = nodeId,
                        geometry = node.geometry.copy(cornerRadiusX = r, cornerRadiusY = r),
                        intent = LayoutIntent.FontSizeChange,
                    ),
                )
                store.legacyLayers.value
            }
            else -> null
        }
    }

    /**
     * Change shape type for [SceneNode.Shape] / [SceneNode.TextInShape].
     * PureText + non-TEXT_ONLY upgrades to TextInShape so geometry survives Document round-trip.
     */
    fun setShapeType(selectedLayerId: String?, shapeType: ShapeType): List<EditorLayer>? {
        val nodeId = resolveNodeId(selectedLayerId) ?: return null
        val node = store.current().node(nodeId) ?: return null
        return when (node) {
            is SceneNode.TextInShape -> {
                val (geometry, style) = applyShapeTypeChangeToFrame(
                    geometry = node.frameGeometry,
                    style = node.frameStyle,
                    shapeType = shapeType,
                )
                store.dispatch(
                    DocumentCommand.SetFrameGeometry(
                        nodeId = nodeId,
                        geometry = geometry,
                        frameStyle = style,
                        intent = LayoutIntent.StyleOrCaseChange,
                    ),
                )
                store.legacyLayers.value
            }
            is SceneNode.Shape -> {
                val (geometry, style) = applyShapeTypeChangeToFrame(
                    geometry = node.geometry,
                    style = node.style,
                    shapeType = shapeType,
                )
                store.dispatch(
                    DocumentCommand.SetFrameGeometry(
                        nodeId = nodeId,
                        geometry = geometry,
                        frameStyle = style,
                        intent = LayoutIntent.FontSizeChange,
                    ),
                )
                store.legacyLayers.value
            }
            is SceneNode.PureText -> {
                if (shapeType == ShapeType.TEXT_ONLY) {
                    store.legacyLayers.value
                } else {
                    upgradePureTextToTextInShape(node, shapeType)
                }
            }
            else -> null
        }
    }

    // ── Effects (preserve box) ────────────────────────────────

    fun setShadowIntensity(selectedLayerId: String?, intensity: Float): List<EditorLayer>? =
        patchDropShadow(selectedLayerId) { it.copy(intensity = intensity.coerceIn(0f, 1f)) }

    fun setShadowAngle(selectedLayerId: String?, angle: Float): List<EditorLayer>? =
        patchDropShadow(selectedLayerId) { it.copy(angle = angle.coerceIn(0f, 360f)) }

    fun setShadowDistance(selectedLayerId: String?, distance: Float): List<EditorLayer>? =
        patchDropShadow(selectedLayerId) { it.copy(distance = distance.coerceIn(0f, 50f)) }

    fun setShadowColor(selectedLayerId: String?, argb: Int): List<EditorLayer>? =
        patchDropShadow(selectedLayerId) { it.copy(colorArgb = argb) }

    fun setShadowBlur(selectedLayerId: String?, blurPx: Float?): List<EditorLayer>? =
        patchDropShadow(selectedLayerId) { it.copy(blurPx = blurPx) }

    fun setElevationIntensity(selectedLayerId: String?, intensity: Float): List<EditorLayer>? {
        val i = intensity.coerceIn(0f, 1f)
        return patchElevation(selectedLayerId) {
            it.copy(
                intensity = i,
                depthSizePx = i * EditorAppearance.MAX_SHAPE_DEPTH_PX,
            )
        }
    }

    fun setDepthSize(selectedLayerId: String?, sizePx: Float): List<EditorLayer>? {
        val size = sizePx.coerceIn(0f, EditorAppearance.MAX_SHAPE_DEPTH_PX)
        return patchElevation(selectedLayerId) {
            it.copy(
                depthSizePx = size,
                intensity = size / EditorAppearance.MAX_SHAPE_DEPTH_PX,
            )
        }
    }

    fun setDepthColor(selectedLayerId: String?, argb: Int?): List<EditorLayer>? =
        patchElevation(selectedLayerId) { it.copy(depthColorArgb = argb) }

    fun setExtrusionAngle(selectedLayerId: String?, angle: Float): List<EditorLayer>? =
        patchElevation(selectedLayerId) { it.copy(extrusionAngle = angle % 360f) }

    fun setElevationStyle(selectedLayerId: String?, style: ShapeElevationStyle): List<EditorLayer>? =
        patchElevation(selectedLayerId) { it.copy(style = style) }

    fun setElevationShadowBlur(selectedLayerId: String?, blurPx: Float?): List<EditorLayer>? =
        patchElevation(selectedLayerId) { it.copy(softBlurPx = blurPx) }

    fun setOpacity(selectedLayerId: String?, alpha: Float): List<EditorLayer>? =
        patchTextStyle(selectedLayerId, LayoutIntent.FontSizeChange) { style ->
            style.copy(opacity = alpha.coerceIn(0.1f, 1f))
        }

    /**
     * Route 3D elevation to frame vs text style bag.
     * TextInShape: move [Effect.Elevation3D] between [SceneNode.TextInShape.frameStyle] /
     * [SceneNode.TextInShape.textStyle]. PureText/Shape: stamp target on projected layer.
     */
    fun setElevationTarget(selectedLayerId: String?, target: ElevationTarget): List<EditorLayer>? {
        val nodeId = resolveNodeId(selectedLayerId) ?: return null
        val node = store.current().node(nodeId) ?: return null
        return when (node) {
            is SceneNode.TextInShape -> {
                val elev = node.frameStyle.elevation ?: node.textStyle.elevation
                val frameStyle = when (target) {
                    ElevationTarget.SHAPE -> node.frameStyle.withElevation(elev)
                    ElevationTarget.TEXT -> node.frameStyle.withoutElevation()
                }
                val textStyle = when (target) {
                    ElevationTarget.TEXT -> node.textStyle.withElevation(elev)
                    ElevationTarget.SHAPE -> node.textStyle.withoutElevation()
                }
                store.dispatch(
                    DocumentCommand.SetStyleBag(
                        nodeId = nodeId,
                        style = frameStyle,
                        target = StyleTarget.FRAME,
                        intent = LayoutIntent.FontSizeChange,
                    ),
                    recordHistory = false,
                )
                store.dispatch(
                    DocumentCommand.SetStyleBag(
                        nodeId = nodeId,
                        style = textStyle,
                        target = StyleTarget.TEXT,
                        intent = LayoutIntent.FontSizeChange,
                    ),
                    recordHistory = false,
                )
                store.legacyLayers.value
            }
            is SceneNode.PureText -> {
                store.legacyLayers.value.map { layer ->
                    if (layer.id == node.id) {
                        layer.copy(appearance = layer.appearance.copy(elevationTarget = target))
                    } else {
                        layer
                    }
                }
            }
            is SceneNode.Shape -> {
                store.legacyLayers.value.map { layer ->
                    if (layer.id == node.id) {
                        layer.copy(
                            appearance = layer.appearance.copy(elevationTarget = ElevationTarget.SHAPE),
                        )
                    } else {
                        layer
                    }
                }
            }
            else -> null
        }
    }

    // ── Geometry ──────────────────────────────────────────────

    fun resizeBox(selectedLayerId: String?, widthPx: Float, heightPx: Float): List<EditorLayer>? {
        val nodeId = resolveNodeId(selectedLayerId) ?: return null
        val node = store.current().node(nodeId) ?: return null
        val w = widthPx.coerceAtLeast(60f)
        val h = heightPx.coerceAtLeast(30f)
        val layout = when (node) {
            is SceneNode.PureText -> node.layout.copy(
                width = com.thgiang.image.studio.ui.editor.document.model.WidthConstraint.Fixed,
                height = com.thgiang.image.studio.ui.editor.document.model.HeightConstraint.Fixed,
                boxWidthPx = w,
                boxHeightPx = h,
            )
            is SceneNode.TextInShape -> node.layout.copy(
                width = com.thgiang.image.studio.ui.editor.document.model.WidthConstraint.Fixed,
                height = com.thgiang.image.studio.ui.editor.document.model.HeightConstraint.Fixed,
                boxWidthPx = w,
                boxHeightPx = h,
            )
            else -> return null
        }
        store.dispatch(DocumentCommand.SetLayoutConstraints(nodeId, layout))
        return store.legacyLayers.value
    }

    fun setTransform(selectedLayerId: String?, transform: NodeTransform): List<EditorLayer>? {
        val nodeId = resolveNodeId(selectedLayerId) ?: return null
        store.dispatch(DocumentCommand.SetTransform(nodeId, transform, syncGroup = true))
        return store.legacyLayers.value
    }

    /**
     * Commit end-of-gesture for a label / TextInShape node.
     * - Corner scale ([viewport.scale] ≠ 1): [DocumentCommand.BakeCornerScale]
     * - Edge / pan / rotate: [SetTransform] + [ResizeEdge] (hug height) or fixed box
     */
    fun commitLabelGesture(
        selectedLayerId: String?,
        previewLayer: EditorLayer,
        hugHeightAfterEdge: Boolean,
    ): List<EditorLayer>? {
        val nodeId = resolveNodeId(selectedLayerId) ?: return null
        val scale = previewLayer.viewport.scale
        if (kotlin.math.abs(scale - 1f) >= 0.001f) {
            store.dispatch(DocumentCommand.BakeCornerScale(nodeId, scale))
            return store.legacyLayers.value
        }
        val transform = NodeTransform.fromViewport(previewLayer.viewport)
        store.dispatch(
            DocumentCommand.SetTransform(nodeId, transform, syncGroup = true),
            recordHistory = false,
        )
        if (hugHeightAfterEdge) {
            store.dispatch(
                DocumentCommand.ResizeEdge(
                    nodeId = nodeId,
                    widthPx = previewLayer.shapeWidthPx.coerceAtLeast(60f),
                ),
            )
        } else {
            val node = store.current().node(nodeId) ?: return store.legacyLayers.value
            val layout = when (node) {
                is SceneNode.PureText -> node.layout
                is SceneNode.TextInShape -> node.layout
                else -> return store.legacyLayers.value
            }.copy(
                width = com.thgiang.image.studio.ui.editor.document.model.WidthConstraint.Fixed,
                height = com.thgiang.image.studio.ui.editor.document.model.HeightConstraint.Fixed,
                boxWidthPx = previewLayer.shapeWidthPx.coerceAtLeast(60f),
                boxHeightPx = previewLayer.shapeHeightPx.coerceAtLeast(30f),
                transform = transform,
            )
            store.dispatch(DocumentCommand.SetLayoutConstraints(nodeId, layout))
        }
        return store.legacyLayers.value
    }

    fun bakeCornerScale(selectedLayerId: String?, scale: Float): List<EditorLayer>? {
        val nodeId = resolveNodeId(selectedLayerId) ?: return null
        store.dispatch(DocumentCommand.BakeCornerScale(nodeId, scale))
        return store.legacyLayers.value
    }

    // ── Internals ─────────────────────────────────────────────

    private fun patchTextContent(
        selectedLayerId: String?,
        intent: LayoutIntent,
        block: (TextContent) -> TextContent,
    ): List<EditorLayer>? {
        val nodeId = resolveNodeId(selectedLayerId) ?: return null
        val content = textContentOf(selectedLayerId) ?: return null
        store.dispatch(
            DocumentCommand.SetTextContent(
                nodeId = nodeId,
                content = block(content),
                intent = intent,
            ),
        )
        return store.legacyLayers.value
    }

    private fun patchTextStyle(
        selectedLayerId: String?,
        intent: LayoutIntent,
        block: (StyleBag) -> StyleBag,
    ): List<EditorLayer>? {
        val nodeId = resolveNodeId(selectedLayerId) ?: return null
        val style = textStyleOf(nodeId) ?: return null
        store.dispatch(
            DocumentCommand.SetStyleBag(
                nodeId = nodeId,
                style = block(style),
                target = StyleTarget.TEXT,
                intent = intent,
            ),
        )
        return store.legacyLayers.value
    }

    private fun patchDecorStyle(
        selectedLayerId: String?,
        intent: LayoutIntent,
        block: (StyleBag) -> StyleBag,
    ): List<EditorLayer>? {
        val nodeId = resolveNodeId(selectedLayerId) ?: return null
        val node = store.current().node(nodeId) ?: return null
        return when (node) {
            is SceneNode.PureText -> {
                store.dispatch(
                    DocumentCommand.SetStyleBag(
                        nodeId = nodeId,
                        style = block(node.style),
                        target = StyleTarget.TEXT,
                        intent = intent,
                    ),
                )
                store.legacyLayers.value
            }
            is SceneNode.TextInShape -> {
                store.dispatch(
                    DocumentCommand.SetStyleBag(
                        nodeId = nodeId,
                        style = block(node.frameStyle),
                        target = StyleTarget.FRAME,
                        intent = intent,
                    ),
                )
                store.legacyLayers.value
            }
            is SceneNode.Shape -> {
                store.dispatch(
                    DocumentCommand.SetStyleBag(
                        nodeId = nodeId,
                        style = block(node.style),
                        target = StyleTarget.FRAME,
                        intent = intent,
                    ),
                )
                store.legacyLayers.value
            }
            else -> null
        }
    }

    private fun patchDropShadow(
        selectedLayerId: String?,
        block: (Effect.DropShadow) -> Effect.DropShadow,
    ): List<EditorLayer>? {
        val patch: (StyleBag) -> StyleBag = { style ->
            val current = style.dropShadow ?: Effect.DropShadow(intensity = 0.3f)
            val next = block(current)
            val others = style.effects.filterNot { it is Effect.DropShadow }
            style.copy(
                effects = if (next.intensity <= 0.05f) others else others + next,
            )
        }
        // Shape panel selects FRAME → frameStyle; Label panel selects LABEL → textStyle.
        return if (selectedIsFrameLayer(selectedLayerId)) {
            patchDecorStyle(selectedLayerId, LayoutIntent.FontSizeChange, patch)
        } else {
            patchTextStyle(selectedLayerId, LayoutIntent.FontSizeChange, patch)
        }
    }

    private fun patchElevation(
        selectedLayerId: String?,
        block: (Effect.Elevation3D) -> Effect.Elevation3D,
    ): List<EditorLayer>? {
        val patch: (StyleBag) -> StyleBag = { style ->
            val current = style.elevation ?: Effect.Elevation3D()
            val next = block(current)
            val others = style.effects.filterNot { it is Effect.Elevation3D }
            val keep = next.intensity > 0.01f || (next.depthSizePx ?: 0f) > 0.5f
            style.copy(effects = if (keep) others + next else others)
        }
        return if (selectedIsFrameLayer(selectedLayerId)) {
            patchDecorStyle(selectedLayerId, LayoutIntent.FontSizeChange, patch)
        } else {
            patchTextStyle(selectedLayerId, LayoutIntent.FontSizeChange, patch)
        }
    }

    private fun selectedIsFrameLayer(selectedLayerId: String?): Boolean {
        if (selectedLayerId == null) return false
        val layer = store.legacyLayers.value.find { it.id == selectedLayerId }
        if (layer?.isFrameLayer == true) return true
        // Honor Document NodePart when selection is the group / text id with FRAME part.
        val snap = store.current()
        val nodeId = EditorLayerBridge.nodeIdForLayer(snap, selectedLayerId) ?: return false
        return snap.selection.nodeId == nodeId && snap.selection.part == NodePart.FRAME
    }

    private fun applyShapeTypeChangeToFrame(
        geometry: FrameGeometry,
        style: StyleBag,
        shapeType: ShapeType,
    ): Pair<FrameGeometry, StyleBag> {
        val (fillArgb, fillGrad) = fillToLayerArgb(style.primaryFill)
        val stroke = style.strokes.firstOrNull()
        val temp = EditorLayer(
            shapeType = geometry.shapeType,
            cornerRadiusX = geometry.cornerRadiusX,
            cornerRadiusY = geometry.cornerRadiusY,
            pathData = geometry.pathData,
            polygonPoints = geometry.polygonPoints,
            shapeColorArgb = fillArgb,
            fillGradient = fillGrad,
            strokeColorArgb = stroke?.colorArgb,
            strokeWidthPx = stroke?.widthPx ?: 0f,
            strokeDashArray = stroke?.dashArray.orEmpty(),
            strokeDashGapPx = stroke?.dashGapPx ?: 6f,
        ).applyShapeTypeChange(shapeType)
        val nextGeometry = FrameGeometry(
            shapeType = temp.shapeType,
            cornerRadiusX = temp.cornerRadiusX,
            cornerRadiusY = temp.cornerRadiusY,
            pathData = temp.pathData,
            polygonPoints = temp.polygonPoints,
        )
        val nextStyle = style.copy(
            fills = buildFills(temp.shapeColorArgb, temp.fillGradient),
            strokes = buildStrokes(temp),
        )
        return nextGeometry to nextStyle
    }

    private fun upgradePureTextToTextInShape(
        node: SceneNode.PureText,
        shapeType: ShapeType,
    ): List<EditorLayer> {
        val (geometry, frameStyle) = applyShapeTypeChangeToFrame(
            geometry = FrameGeometry(shapeType = ShapeType.TEXT_ONLY),
            style = StyleBag(),
            shapeType = shapeType,
        )
        val textStyle = node.style.copy(fills = emptyList(), strokes = emptyList())
        val groupId = UUID.randomUUID().toString()
        val frameId = UUID.randomUUID().toString()
        val upgraded = SceneNode.TextInShape(
            id = groupId,
            frameId = frameId,
            textId = node.id,
            frameGeometry = geometry,
            frameStyle = frameStyle,
            textContent = node.content,
            textStyle = textStyle,
            layout = node.layout.copy(
                width = WidthConstraint.Fixed,
                height = HeightConstraint.Hug,
                padding = EdgeInsets.SHAPE_TEXT,
            ),
            textPadding = EdgeInsets.SHAPE_TEXT,
            isLocked = node.isLocked,
            isVisible = node.isVisible,
        )
        store.dispatch(DocumentCommand.RemoveNode(node.id))
        store.dispatch(DocumentCommand.UpsertNode(upgraded))
        store.dispatch(DocumentCommand.SelectNode(groupId, NodePart.TEXT), recordHistory = false)
        return store.legacyLayers.value
    }

    private fun fillToLayerArgb(fill: Fill?): Pair<Int, CloudGradient?> = when (fill) {
        is Fill.Solid -> fill.argb to null
        is Fill.Gradient -> fill.fallbackArgb to fill.gradient
        null -> ShapeLabelDefaults.TRANSPARENT_FILL_ARGB to null
    }

    private fun buildFills(shapeColorArgb: Int, fillGradient: CloudGradient?): List<Fill> = buildList {
        if (fillGradient != null) {
            add(Fill.Gradient(fillGradient, shapeColorArgb))
        } else {
            val a = (shapeColorArgb ushr 24) and 0xFF
            if (a > 0) add(Fill.Solid(shapeColorArgb))
        }
    }

    private fun buildStrokes(layer: EditorLayer): List<StrokeSpec> {
        val c = layer.strokeColorArgb ?: return emptyList()
        if (layer.strokeWidthPx <= 0f) return emptyList()
        return listOf(
            StrokeSpec(
                colorArgb = c,
                widthPx = layer.strokeWidthPx,
                dashArray = layer.strokeDashArray,
                dashGapPx = layer.strokeDashGapPx,
            ),
        )
    }

    private fun StyleBag.withoutElevation(): StyleBag =
        copy(effects = effects.filterNot { it is Effect.Elevation3D })

    private fun StyleBag.withElevation(elev: Effect.Elevation3D?): StyleBag {
        val cleared = withoutElevation()
        return if (elev == null) cleared else cleared.copy(effects = cleared.effects + elev)
    }

    private fun textContentOf(selectedLayerId: String?): TextContent? {
        val nodeId = resolveNodeId(selectedLayerId) ?: return null
        return when (val node = store.current().node(nodeId)) {
            is SceneNode.PureText -> node.content
            is SceneNode.TextInShape -> node.textContent
            else -> null
        }
    }

    private fun textStyleOf(nodeId: String): StyleBag? =
        when (val node = store.current().node(nodeId)) {
            is SceneNode.PureText -> node.style
            is SceneNode.TextInShape -> node.textStyle
            else -> null
        }

    private fun resolveNodeId(selectedLayerId: String?): String? {
        if (selectedLayerId == null) return null
        return EditorLayerBridge.nodeIdForLayer(store.current(), selectedLayerId)
    }

    private fun TextContent.withAllRuns(block: (TextRun) -> TextRun): TextContent {
        val nextRuns = if (runs.isEmpty()) {
            listOf(block(primaryRun).copy(text = text))
        } else {
            runs.map { run -> block(run).copy(text = run.text) }
        }
        return copy(runs = nextRuns, text = nextRuns.joinToString("") { it.text }.ifEmpty { text })
    }

    private fun contentToSpans(content: TextContent): List<EditorTextSpan> =
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

    private fun contentFromSpans(
        base: TextContent,
        spans: List<EditorTextSpan>,
        text: String,
        patch: TextSpanStylePatch? = null,
    ): TextContent {
        val size = base.primaryRun.textSizeSp
        // Keep text gradient across run splits (bold/italic); clear only when solid color is set.
        val clearGradient = patch?.colorArgb != null || patch?.clearColor == true
        val gradient = if (clearGradient) null else base.primaryRun.colorGradient
        val runs = TextRunOps.normalize(spans).map { span ->
            TextRun(
                text = span.text,
                fontFamily = span.fontFamily ?: base.primaryRun.fontFamily,
                fontWeight = span.fontWeight ?: base.primaryRun.fontWeight,
                fontStyle = span.fontStyle ?: base.primaryRun.fontStyle,
                textSizeSp = size,
                colorArgb = span.colorArgb ?: base.primaryRun.colorArgb,
                colorGradient = gradient,
                underline = span.underline ?: base.primaryRun.underline,
                linethrough = span.linethrough ?: base.primaryRun.linethrough,
            )
        }
        return base.copy(
            text = text,
            runs = runs.ifEmpty {
                listOf(base.primaryRun.copy(text = text))
            },
        )
    }
}

private fun mergeTemplateTypography(content: TextContent, template: TextStyleTemplate): TextContent {
    // Patch every run — preserve multi-run structure (do not flatten to primary).
    val nextRuns = if (content.runs.isEmpty()) {
        listOf(
            content.primaryRun.copy(
                text = content.text,
                colorArgb = template.textColorArgb,
                colorGradient = template.textColorGradient,
                fontWeight = template.fontWeight ?: content.primaryRun.fontWeight,
                fontStyle = template.fontStyle ?: content.primaryRun.fontStyle,
            ),
        )
    } else {
        content.runs.map { run ->
            run.copy(
                colorArgb = template.textColorArgb,
                colorGradient = template.textColorGradient,
                fontWeight = template.fontWeight ?: run.fontWeight,
                fontStyle = template.fontStyle ?: run.fontStyle,
            )
        }
    }
    return content.copy(
        runs = nextRuns,
        textTransform = DocTextTransform.fromLegacy(template.textTransform),
        charSpacing = template.charSpacing,
    )
}

/** Map legacy TextStyleTemplate into StyleBag bags for atomic apply. */
internal fun TextStyleTemplate.toStyleBags(): Triple<StyleBag, StyleBag?, TextContent?> {
    val fills = buildList {
        if (fillGradient != null) {
            add(Fill.Gradient(fillGradient, shapeColorArgb))
        } else {
            val a = (shapeColorArgb ushr 24) and 0xFF
            if (a > 0) add(Fill.Solid(shapeColorArgb))
        }
    }
    val strokes = buildList {
        val c = strokeColorArgb
        if (c != null && strokeWidthPx > 0f) {
            add(StrokeSpec(colorArgb = c, widthPx = strokeWidthPx))
        }
    }
    val effects = buildList {
        if (shadowIntensity > 0.05f) {
            add(
                Effect.DropShadow(
                    intensity = shadowIntensity,
                    angle = shadowAngle,
                    distance = shadowDistance,
                    colorArgb = shadowColorArgb,
                    blurPx = shadowBlur,
                ),
            )
        }
        if (elevationIntensity > 0.01f || (depthSizePx ?: 0f) > 0.5f) {
            add(
                Effect.Elevation3D(
                    intensity = elevationIntensity,
                    style = elevationStyle,
                    depthSizePx = depthSizePx,
                    softBlurPx = elevationShadowBlur,
                ),
            )
        }
    }
    val textForm = if (textFormPreset == TextFormPreset.NONE) {
        TextFormEffect()
    } else {
        TextFormEffect().withPreset(textFormPreset).copy(amount = textFormAmount)
    }
    val textStyle = StyleBag(
        fills = fills,
        strokes = strokes,
        effects = effects,
        textForm = textForm,
        opacity = 1f,
    )
    val frameStyle = StyleBag(
        fills = fills,
        strokes = strokes,
        effects = effects.filterIsInstance<Effect.DropShadow>() +
            effects.filterIsInstance<Effect.Elevation3D>(),
        opacity = 1f,
    )
    return Triple(textStyle, frameStyle, null)
}
