package com.thgiang.image.studio.ui.editor.document.rules

import com.thgiang.image.studio.ui.editor.document.model.HeightConstraint
import com.thgiang.image.studio.ui.editor.document.model.LayoutConstraints
import com.thgiang.image.studio.ui.editor.document.model.WidthConstraint

/**
 * Layout intent → constraint updates (replaces scattered withShapeFitted*).
 * Actual pixel measure happens in [com.thgiang.image.studio.ui.editor.document.layout.LayoutEngine].
 */
enum class LayoutIntent {
    /** Typing / newline — keep fixed width when Fixed, hug height. */
    EditText,
    /** Case / font / style template — preserve width, hug height. */
    StyleOrCaseChange,
    /** Font size slider — preserve both box dimensions. */
    FontSizeChange,
    /** Right-edge resize — set fixed width, hug height. */
    ResizeEdgeWidth,
    /** Corner uniform scale — bake handled separately; box hug after bake. */
    CornerScaleBake,
    /** Inline edit grow — may hug width if content overflows. */
    InlineGrow,
    /** TextForm active — measure glyph extents. */
    TextFormMeasure,
    /** Explicit user set both dimensions. */
    ManualBox,
}

object LayoutPolicy {

    fun applyIntent(
        current: LayoutConstraints,
        intent: LayoutIntent,
        newWidthPx: Float? = null,
        newHeightPx: Float? = null,
    ): LayoutConstraints = when (intent) {
        LayoutIntent.EditText -> current.copy(
            width = if (current.width == WidthConstraint.Hug) WidthConstraint.Hug else WidthConstraint.Fixed,
            height = HeightConstraint.Hug,
        )
        LayoutIntent.StyleOrCaseChange -> current.copy(
            width = WidthConstraint.Fixed,
            height = HeightConstraint.Hug,
            boxWidthPx = current.boxWidthPx,
        )
        LayoutIntent.FontSizeChange -> current.copy(
            width = WidthConstraint.Fixed,
            height = HeightConstraint.Fixed,
        )
        LayoutIntent.ResizeEdgeWidth -> current.copy(
            width = WidthConstraint.Fixed,
            height = HeightConstraint.Hug,
            boxWidthPx = (newWidthPx ?: current.boxWidthPx).coerceAtLeast(MIN_WIDTH),
        )
        LayoutIntent.CornerScaleBake -> current.copy(
            width = WidthConstraint.Fixed,
            height = HeightConstraint.Fixed,
            boxWidthPx = (newWidthPx ?: current.boxWidthPx).coerceAtLeast(MIN_WIDTH),
            boxHeightPx = (newHeightPx ?: current.boxHeightPx).coerceAtLeast(MIN_HEIGHT),
        )
        LayoutIntent.InlineGrow -> current.copy(
            width = WidthConstraint.Hug,
            height = HeightConstraint.Hug,
        )
        LayoutIntent.TextFormMeasure -> current.copy(
            width = WidthConstraint.Fixed,
            height = HeightConstraint.Fixed,
        )
        LayoutIntent.ManualBox -> current.copy(
            width = WidthConstraint.Fixed,
            height = HeightConstraint.Fixed,
            boxWidthPx = (newWidthPx ?: current.boxWidthPx).coerceAtLeast(MIN_WIDTH),
            boxHeightPx = (newHeightPx ?: current.boxHeightPx).coerceAtLeast(MIN_HEIGHT),
        )
    }

    const val MIN_WIDTH = 60f
    const val MIN_HEIGHT = 30f
}
