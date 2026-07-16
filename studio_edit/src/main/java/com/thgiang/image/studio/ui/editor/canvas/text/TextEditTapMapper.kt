package com.thgiang.image.studio.ui.editor.canvas.text

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextLayoutResult
import com.thgiang.image.studio.ui.editor.canvas.inverseRotatePoint
import kotlin.math.max
import kotlin.math.min

/**
 * Maps a tap inside the inline text overlay to a character offset in [textLayoutResult].
 *
 * Coordinates: [tap] is in the bounding-box overlay box (same space as resize/move handles).
 * [contentCenter] must be the geometric center of that overlay (aligned with the text layer).
 *
 * Layout origin:
 * - Full-width (multi-line / width == frame): left = frameLeft + [paddingXPx]
 * - Wrap-content single line (TopCenter): horizontally centered on [contentCenter]
 */
internal fun isTapInsideInlineTextBounds(
    tap: Offset,
    contentCenter: Offset,
    screenW: Float,
    screenH: Float,
    rotationDeg: Float,
): Boolean {
    val local = inverseRotatePoint(tap, contentCenter, rotationDeg)
    val hw = screenW / 2f
    val hh = screenH / 2f
    return local.x in (contentCenter.x - hw)..(contentCenter.x + hw) &&
        local.y in (contentCenter.y - hh)..(contentCenter.y + hh)
}

internal fun mapOverlayTapToTextOffset(
    tap: Offset,
    contentCenter: Offset,
    screenW: Float,
    screenH: Float,
    rotationDeg: Float,
    paddingXPx: Float,
    paddingYPx: Float,
    textLayoutResult: TextLayoutResult,
): Int {
    val local = inverseRotatePoint(tap, contentCenter, rotationDeg)
    val layoutWidth = textLayoutResult.size.width.toFloat().coerceAtLeast(1f)
    val layoutHeight = textLayoutResult.size.height.toFloat().coerceAtLeast(1f)
    val frameLeft = contentCenter.x - screenW / 2f
    val frameTop = contentCenter.y - screenH / 2f
    val contentWidth = (screenW - paddingXPx * 2f).coerceAtLeast(1f)
    val fillsWidth = layoutWidth >= contentWidth * 0.92f
    val layoutOriginX = if (fillsWidth) {
        frameLeft + paddingXPx
    } else {
        contentCenter.x - layoutWidth / 2f
    }
    val layoutOriginY = frameTop + paddingYPx
    val layoutX = (local.x - layoutOriginX).coerceIn(0f, layoutWidth)
    val layoutY = (local.y - layoutOriginY).coerceIn(0f, layoutHeight)
    val textLength = textLayoutResult.layoutInput.text.length
    return textLayoutResult
        .getOffsetForPosition(Offset(layoutX, layoutY))
        .coerceIn(0, textLength)
}

/** @deprecated Use overload with [paddingXPx]; kept for binary compatibility during migration. */
internal fun mapOverlayTapToTextOffset(
    tap: Offset,
    contentCenter: Offset,
    screenW: Float,
    screenH: Float,
    rotationDeg: Float,
    paddingYPx: Float,
    textLayoutResult: TextLayoutResult,
): Int = mapOverlayTapToTextOffset(
    tap = tap,
    contentCenter = contentCenter,
    screenW = screenW,
    screenH = screenH,
    rotationDeg = rotationDeg,
    paddingXPx = 0f,
    paddingYPx = paddingYPx,
    textLayoutResult = textLayoutResult,
)

internal fun textSelectionRange(
    startOffset: Int,
    endOffset: Int,
): Pair<Int, Int> {
    val safeStart = max(0, startOffset)
    val safeEnd = max(0, endOffset)
    return min(safeStart, safeEnd) to max(safeStart, safeEnd)
}

/**
 * Layout origin X for caret hit-testing — pure helper for unit tests without TextLayoutResult.
 */
internal fun inlineTextLayoutOriginX(
    contentCenterX: Float,
    screenW: Float,
    layoutWidth: Float,
    paddingXPx: Float,
): Float {
    val contentWidth = (screenW - paddingXPx * 2f).coerceAtLeast(1f)
    val fillsWidth = layoutWidth >= contentWidth * 0.92f
    return if (fillsWidth) {
        contentCenterX - screenW / 2f + paddingXPx
    } else {
        contentCenterX - layoutWidth / 2f
    }
}
