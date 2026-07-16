package com.thgiang.image.studio.ui.editor.document.layout

import android.content.Context
import com.thgiang.image.studio.ui.editor.document.model.DocTextAlign
import com.thgiang.image.studio.ui.editor.document.model.HeightConstraint
import com.thgiang.image.studio.ui.editor.document.model.LayoutConstraints
import com.thgiang.image.studio.ui.editor.document.model.SceneNode
import com.thgiang.image.studio.ui.editor.document.model.TextContent
import com.thgiang.image.studio.ui.editor.document.model.WidthConstraint
import com.thgiang.image.studio.ui.editor.document.rules.LayoutIntent
import com.thgiang.image.studio.ui.editor.document.rules.LayoutPolicy
import com.thgiang.image.studio.ui.editor.label.model.ShapeTextBoundsResolver
import com.thgiang.image.studio.ui.editor.label.model.withShapeFittedForInlineEdit
import com.thgiang.image.studio.ui.editor.label.model.withShapeFittedToText
import com.thgiang.image.studio.ui.editor.label.model.withShapeHeightFittedToText
import com.thgiang.image.studio.ui.editor.label.model.withTextFormShapeFitted
import com.thgiang.image.studio.ui.editor.mapper.TextFormLayoutEngine
import com.thgiang.image.studio.ui.editor.model.EditorLayer
import com.thgiang.image.studio.ui.editor.model.TextFormEffect

/**
 * Single measure facade (I2, I6).
 * Phase B: delegates pixel math to existing ShapeTextBoundsResolver / TextFormLayoutEngine
 * so preview/export stay consistent while callers only go through DocumentStore.
 */
object LayoutEngine {

    const val TEXT_LAYOUT_INCLUDE_PAD = ShapeTextBoundsResolver.TEXT_LAYOUT_INCLUDE_PAD

    data class DerivedBounds(
        val widthPx: Float,
        val heightPx: Float,
        val contentWidthPx: Float,
        val contentHeightPx: Float,
    )

    data class TextLayoutSnapshot(
        val nodeId: String,
        val bounds: DerivedBounds,
        val lineCount: Int,
        val hasTextForm: Boolean,
        /** Legacy bridge layer after measure — used by adapter until render is unified. */
        val measuredLayer: EditorLayer?,
    )

    fun measureNode(
        context: Context,
        node: SceneNode,
        bridgeLayer: EditorLayer,
        intent: LayoutIntent,
    ): Pair<SceneNode, TextLayoutSnapshot> {
        val measuredLayer = measureLayer(context, bridgeLayer, intent, node)
        val bounds = DerivedBounds(
            widthPx = measuredLayer.shapeWidthPx,
            heightPx = measuredLayer.shapeHeightPx,
            contentWidthPx = measuredLayer.shapeWidthPx,
            contentHeightPx = measuredLayer.shapeHeightPx,
        )
        val updated = applyBoundsToNode(node, bounds)
        val lineCount = if (measuredLayer.textForm.isActive) {
            measuredLayer.text.length.coerceAtLeast(1)
        } else {
            estimateLineCount(measuredLayer)
        }
        val snapshot = TextLayoutSnapshot(
            nodeId = node.id,
            bounds = bounds,
            lineCount = lineCount,
            hasTextForm = measuredLayer.textForm.isActive,
            measuredLayer = measuredLayer,
        )
        return updated to snapshot
    }

    fun measureLayer(
        context: Context,
        layer: EditorLayer,
        intent: LayoutIntent,
        node: SceneNode? = null,
    ): EditorLayer {
        if (layer.textForm.isActive || intent == LayoutIntent.TextFormMeasure) {
            return layer.withTextFormShapeFitted(context)
        }
        return when (intent) {
            LayoutIntent.EditText -> {
                // Prefer preserve width when Fixed
                val widthMode = when (node) {
                    is SceneNode.PureText -> node.layout.width
                    is SceneNode.TextInShape -> node.layout.width
                    else -> WidthConstraint.Fixed
                }
                if (widthMode == WidthConstraint.Hug) {
                    layer.withShapeFittedToText(context)
                } else {
                    layer.withShapeHeightFittedToText(context)
                }
            }
            LayoutIntent.StyleOrCaseChange,
            LayoutIntent.ResizeEdgeWidth,
            -> layer.withShapeHeightFittedToText(context)
            LayoutIntent.FontSizeChange,
            LayoutIntent.CornerScaleBake,
            LayoutIntent.ManualBox,
            -> layer // dimensions already set on constraints / bake
            LayoutIntent.InlineGrow -> layer.withShapeFittedForInlineEdit(context)
            LayoutIntent.TextFormMeasure -> layer.withTextFormShapeFitted(context)
        }
    }

    fun glyphContentBounds(
        layer: EditorLayer,
        context: Context,
        scale: Float = 1f,
    ): android.graphics.RectF? {
        if (!layer.textForm.isActive) return null
        return try {
            val glyphs = TextFormLayoutEngine.computeLayerGlyphs(
                layer = layer,
                boxWidth = layer.shapeWidthPx * scale,
                boxHeight = layer.shapeHeightPx * scale,
                renderScale = scale,
                context = context,
            )
            val textSizePx = layer.textSizeSp * scale
            TextFormLayoutEngine.glyphContentBounds(glyphs, textSizePx)
        } catch (_: Exception) {
            null
        }
    }

    private fun applyBoundsToNode(node: SceneNode, bounds: DerivedBounds): SceneNode {
        fun patch(layout: LayoutConstraints): LayoutConstraints {
            val w = when (layout.width) {
                WidthConstraint.Fixed -> layout.boxWidthPx
                WidthConstraint.Hug -> bounds.widthPx.coerceAtLeast(LayoutPolicy.MIN_WIDTH)
            }
            val h = when (layout.height) {
                HeightConstraint.Fixed -> layout.boxHeightPx
                HeightConstraint.Hug -> bounds.heightPx.coerceAtLeast(LayoutPolicy.MIN_HEIGHT)
            }
            // When Fixed width + Hug height, use measured height but keep width
            val finalW = if (layout.width == WidthConstraint.Fixed) layout.boxWidthPx else w
            val finalH = if (layout.height == HeightConstraint.Fixed) layout.boxHeightPx else {
                // Prefer measured
                bounds.heightPx.coerceAtLeast(LayoutPolicy.MIN_HEIGHT)
            }
            return layout.copy(boxWidthPx = finalW, boxHeightPx = finalH)
        }
        return when (node) {
            is SceneNode.PureText -> node.copy(layout = patch(node.layout))
            is SceneNode.TextInShape -> node.copy(layout = patch(node.layout))
            is SceneNode.Shape -> node.copy(layout = patch(node.layout))
            is SceneNode.LegacyPassthrough -> node
        }
    }

    private fun estimateLineCount(layer: EditorLayer): Int {
        val t = layer.text
        if (t.isBlank()) return 1
        return t.count { it == '\n' } + 1
    }
}
