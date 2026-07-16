package com.thgiang.image.studio.ui.editor.document.render

import android.content.Context
import android.graphics.Canvas
import com.thgiang.image.studio.ui.editor.document.layout.LayoutEngine
import com.thgiang.image.studio.ui.editor.mapper.EditorTextRenderMapper
import com.thgiang.image.studio.ui.editor.mapper.TextFormLayoutEngine
import com.thgiang.image.studio.ui.editor.model.EditorLayer

/**
 * Unified draw entry for text layers (Phase E / I6).
 * Preview and export call this so both use the same measured layer when available.
 */
object DocumentRenderPipeline {

    fun drawTextLayer(
        canvas: Canvas,
        context: Context,
        layer: EditorLayer,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        renderScale: Float,
        layoutSnapshot: LayoutEngine.TextLayoutSnapshot? = null,
    ) {
        val drawLayer = layoutSnapshot?.measuredLayer ?: layer
        if (drawLayer.textForm.isActive) {
            TextFormLayoutEngine.drawOnCanvas(
                canvas = canvas,
                layer = drawLayer,
                left = left,
                top = top,
                width = width,
                height = height,
                renderScale = renderScale,
                context = context,
            )
        } else {
            EditorTextRenderMapper.drawFlatTextOnCanvas(
                canvas = canvas,
                layer = drawLayer,
                left = left,
                top = top,
                width = width,
                height = height,
                renderScale = renderScale,
                context = context,
            )
        }
    }

    init {
        check(LayoutEngine.TEXT_LAYOUT_INCLUDE_PAD == false) {
            "LayoutEngine pad must stay aligned with EditorTextRenderMapper"
        }
    }
}
