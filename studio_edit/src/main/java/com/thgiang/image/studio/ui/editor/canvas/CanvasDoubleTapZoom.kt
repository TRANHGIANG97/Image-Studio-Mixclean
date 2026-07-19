package com.thgiang.image.studio.ui.editor.canvas

import androidx.compose.ui.geometry.Offset

/** Result of resolving a canvas double-tap against the current viewport. */
data class CanvasDoubleTapTarget(
    val scale: Float,
    val offset: Offset,
)

private const val ZOOM_IN_STEP = 0.5f
private const val MIN_ZOOM_IN_SCALE = 0.5f
private const val DEFAULT_SCALE = 1f

/**
 * Maps canvas scale to the next zoom level on double-tap.
 *
 * - (50%, 100%]: zoom in by +50 percentage points (e.g. 100% → 150%), focal at [tap].
 * - < 50%: return to 100% at default pan.
 * - > 100%: return to 100% at default pan.
 */
fun resolveCanvasDoubleTapTarget(
    currentScale: Float,
    currentOffset: Offset,
    tapX: Float,
    tapY: Float,
    viewportCenterX: Float,
    viewportCenterY: Float,
    maxScale: Float = 5.8f,
): CanvasDoubleTapTarget {
    return when {
        currentScale > DEFAULT_SCALE -> CanvasDoubleTapTarget(
            scale = DEFAULT_SCALE,
            offset = Offset.Zero,
        )
        currentScale <= MIN_ZOOM_IN_SCALE -> CanvasDoubleTapTarget(
            scale = DEFAULT_SCALE,
            offset = Offset.Zero,
        )
        else -> {
            val targetScale = (currentScale + ZOOM_IN_STEP).coerceAtMost(maxScale)
            CanvasDoubleTapTarget(
                scale = targetScale,
                offset = offsetZoomedToPoint(
                    currentScale = currentScale,
                    targetScale = targetScale,
                    currentOffset = currentOffset,
                    tapX = tapX,
                    tapY = tapY,
                    centerX = viewportCenterX,
                    centerY = viewportCenterY,
                ),
            )
        }
    }
}

/** Keeps [tap] fixed on screen while scale changes (artboard transform around viewport center). */
fun offsetZoomedToPoint(
    currentScale: Float,
    targetScale: Float,
    currentOffset: Offset,
    tapX: Float,
    tapY: Float,
    centerX: Float,
    centerY: Float,
): Offset {
    if (currentScale <= 0f) return currentOffset
    val ratio = targetScale / currentScale
    val dx = tapX - centerX
    val dy = tapY - centerY
    return Offset(
        dx * (1f - ratio) + currentOffset.x * ratio,
        dy * (1f - ratio) + currentOffset.y * ratio,
    )
}

/** Converts a tap inside a centered BB overlay to workspace coordinates. */
fun overlayLocalTapToWorkspaceTap(
    localTap: Offset,
    overlayWidthPx: Float,
    overlayHeightPx: Float,
    artboardCenterOffsetPx: Offset,
    canvasScale: Float,
    canvasOffset: Offset,
    workspaceCenterX: Float,
    workspaceCenterY: Float,
): Offset {
    val relativeToArtboardCenter = Offset(
        artboardCenterOffsetPx.x + localTap.x - overlayWidthPx / 2f,
        artboardCenterOffsetPx.y + localTap.y - overlayHeightPx / 2f,
    )
    return Offset(
        workspaceCenterX + canvasOffset.x + relativeToArtboardCenter.x * canvasScale,
        workspaceCenterY + canvasOffset.y + relativeToArtboardCenter.y * canvasScale,
    )
}
