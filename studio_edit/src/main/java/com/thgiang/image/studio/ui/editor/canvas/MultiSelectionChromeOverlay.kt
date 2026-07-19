package com.thgiang.image.studio.ui.editor.canvas

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.thgiang.image.studio.ui.editor.model.EditorLayer
import com.thgiang.image.studio.ui.editor.model.EditorViewport
import com.thgiang.image.studio.ui.editor.model.computeOrientedUnionContentBounds
import kotlin.math.roundToInt

/**
 * Union bounding box for multi-selected layers — pan/scale/rotate apply to all via [GestureDelta].
 */
@Composable
fun MultiSelectionChromeOverlay(
    layers: List<EditorLayer>,
    selectedIds: Set<String>,
    displayScale: Float,
    templateSize: IntSize,
    visible: Boolean,
    onGesture: (GestureDelta) -> Unit,
    onGestureEnd: () -> Unit,
    onGestureActiveChanged: (Boolean) -> Unit,
    onCanvasDoubleTapFromOverlay: (
        localTap: Offset,
        overlayWidthPx: Float,
        overlayHeightPx: Float,
        artboardCenterOffsetPx: Offset,
    ) -> Unit = { _, _, _, _ -> },
    modifier: Modifier = Modifier,
) {
    if (!visible || selectedIds.size < 2) return

    val selectedLayers = layers.filter { it.id in selectedIds && !it.isLocked }
    if (selectedLayers.size < 2) return

    val density = LocalDensity.current
    val orientedBounds = remember(selectedLayers, displayScale) {
        computeOrientedUnionContentBounds(selectedLayers)
    } ?: return
    val bounds = orientedBounds.bounds

    val widthDp = with(density) {
        (bounds.width * displayScale).toInt().coerceAtLeast(1).toDp()
    }
    val heightDp = with(density) {
        (bounds.height * displayScale).toInt().coerceAtLeast(1).toDp()
    }
    val displayOffset = IntOffset(
        (bounds.centerX * displayScale).roundToInt(),
        (bounds.centerY * displayScale).roundToInt(),
    )
    val pseudoViewport = remember(bounds, orientedBounds.rotationDeg) {
        EditorViewport(
            offsetX = bounds.centerX,
            offsetY = bounds.centerY,
            scale = 1f,
            rotation = orientedBounds.rotationDeg,
        )
    }
    val bbOverlayPad = EditorDims.overlayPaddingDp()
    val paddingExtra = 24.dp
    val bbWidth = widthDp + bbOverlayPad
    val bbHeight = heightDp + bbOverlayPad
    val artboardCenterOffset = Offset(
        bounds.centerX * displayScale,
        bounds.centerY * displayScale,
    )

    Box(
        modifier = modifier
            .requiredSize(
                width = widthDp + paddingExtra * 2,
                height = heightDp + paddingExtra * 2,
            )
            .offset { displayOffset }
            .zIndex(50f),
    ) {
        BoundingBoxOverlayV6(
            modifier = Modifier
                .align(Alignment.Center)
                .requiredSize(width = bbWidth, height = bbHeight),
            contentWidth = bounds.width,
            contentHeight = bounds.height,
            viewport = pseudoViewport,
            displayScale = displayScale,
            templateSize = templateSize,
            lockAspectRatio = false,
            onGesture = onGesture,
            onGestureEnd = onGestureEnd,
            showBoundingBox = true,
            onBoundingBoxVisible = {},
            isLocked = false,
            otherLayers = layers.filter { it.id !in selectedIds },
            onGestureActiveChanged = onGestureActiveChanged,
            onBodyDoubleTap = { localTap ->
                onCanvasDoubleTapFromOverlay(
                    localTap,
                    with(density) { bbWidth.toPx() },
                    with(density) { bbHeight.toPx() },
                    artboardCenterOffset,
                )
            },
        )
    }
}
