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
import com.thgiang.image.studio.ui.editor.label.geometry.EditorShapeGeometry
import com.thgiang.image.studio.ui.editor.mapper.EditorShadowMapper
import com.thgiang.image.studio.ui.editor.mapper.hasShapeBorder
import com.thgiang.image.studio.ui.editor.mapper.resolveStrokeWidthPx
import com.thgiang.image.studio.ui.editor.model.EditorLayer
import com.thgiang.image.studio.ui.editor.model.LayerType
import com.thgiang.image.studio.ui.editor.model.imageLayerTightMetrics
import com.thgiang.image.studio.ui.editor.model.imageSelectionCenterOffset
import com.thgiang.image.studio.ui.editor.model.isVectorContentLayer
import com.thgiang.image.studio.ui.editor.model.shouldRenderLabelContent
import kotlin.math.roundToInt

/**
 * Renders the selected layer's bounding box above all layer content.
 * Placed outside the artboard clip so rotate/resize handles are not cut off.
 */
@Composable
fun LayerSelectionChromeOverlay(
    layer: EditorLayer?,
    displayScale: Float,
    templateSize: IntSize,
    allLayers: List<EditorLayer>,
    visible: Boolean,
    onGesture: (GestureDelta) -> Unit,
    onGestureEnd: () -> Unit,
    onGestureActiveChanged: (Boolean) -> Unit,
    onRequestInlineEdit: (() -> Unit)? = null,
    onCanvasDoubleTapFromOverlay: (
        localTap: Offset,
        overlayWidthPx: Float,
        overlayHeightPx: Float,
        artboardCenterOffsetPx: Offset,
    ) -> Unit = { _, _, _, _ -> },
    modifier: Modifier = Modifier,
) {
    if (!visible || layer == null || layer.isLocked) return

    val density = LocalDensity.current

    when {
        layer.type == LayerType.IMAGE && layer.product.foregroundUri != null -> {
            val actualSize = remember(layer.shapeWidthPx, layer.shapeHeightPx, layer.cropRatio) {
                layer.cropRatio.calculateSize(layer.shapeWidthPx, layer.shapeHeightPx)
            }
            val tightMetrics = remember(
                layer.opaqueContentLeftPx,
                layer.opaqueContentTopPx,
                layer.opaqueContentWidthPx,
                layer.opaqueContentHeightPx,
                layer.shapeWidthPx,
                layer.shapeHeightPx,
                layer.cropRatio,
            ) {
                layer.imageLayerTightMetrics()
            }
            val contentWidth = tightMetrics?.contentWidth ?: actualSize.width.toFloat()
            val contentHeight = tightMetrics?.contentHeight ?: actualSize.height.toFloat()
            val viewport = layer.viewport
            val originalWidth = with(density) {
                (contentWidth * viewport.scale * displayScale).toInt().toDp()
            }
            val originalHeight = with(density) {
                (contentHeight * viewport.scale * displayScale).toInt().toDp()
            }
            val (selectionCenterX, selectionCenterY) = layer.imageSelectionCenterOffset()
            val displayOffset = IntOffset(
                (selectionCenterX * displayScale).roundToInt(),
                (selectionCenterY * displayScale).roundToInt(),
            )
            val selectionViewport = remember(viewport, tightMetrics) {
                viewport.copy(
                    offsetX = selectionCenterX,
                    offsetY = selectionCenterY,
                )
            }
            val bbOverlayPad = EditorDims.overlayPaddingDp()
            val paddingExtra = 40.dp
            val bbWidth = originalWidth + bbOverlayPad
            val bbHeight = originalHeight + bbOverlayPad
            val artboardCenterOffset = Offset(
                selectionCenterX * displayScale,
                selectionCenterY * displayScale,
            )

            Box(
                modifier = modifier
                    .requiredSize(
                        width = originalWidth + paddingExtra * 2,
                        height = originalHeight + paddingExtra * 2,
                    )
                    .offset { displayOffset }
                    .zIndex(50f),
            ) {
                BoundingBoxOverlayV6(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .requiredSize(width = bbWidth, height = bbHeight),
                    contentWidth = contentWidth,
                    contentHeight = contentHeight,
                    viewport = selectionViewport,
                    displayScale = displayScale,
                    templateSize = templateSize,
                    lockAspectRatio = false,
                    onGesture = onGesture,
                    onGestureEnd = onGestureEnd,
                    showBoundingBox = true,
                    onBoundingBoxVisible = {},
                    isLocked = false,
                    otherLayers = allLayers.filter { it.id != layer.id },
                    onGestureActiveChanged = onGestureActiveChanged,
                    reserveCenterReplaceHit = layer.product.isSample,
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

        layer.isVectorContentLayer -> {
            val templateScale = (layer.viewport.scale * displayScale).coerceAtLeast(0.01f)
            val effectiveShapeWidthPx = layer.shapeWidthPx.coerceAtLeast(60f)
            val effectiveShapeHeightPx = layer.shapeHeightPx.coerceAtLeast(30f)
            val displayW = with(density) { (effectiveShapeWidthPx * templateScale).toDp() }
            val displayH = with(density) { (effectiveShapeHeightPx * templateScale).toDp() }
            val offsetXPx = layer.viewport.offset.x * displayScale
            val offsetYPx = layer.viewport.offset.y * displayScale

            val isTextOnlyLayer = EditorShapeGeometry.isTextOnlyShape(layer.shapeType)
            val strokePad = if (!isTextOnlyLayer && layer.hasShapeBorder) {
                layer.resolveStrokeWidthPx() * templateScale
            } else {
                0f
            }
            val bleedPx = if (isTextOnlyLayer) {
                0f
            } else {
                EditorShadowMapper.computeShadowBleedPx(
                    appearance = layer.appearance,
                    scale = templateScale,
                    rotationDeg = layer.viewport.rotation,
                    extraStrokePx = strokePad,
                )
            }
            val paddingExtra = if (isTextOnlyLayer) {
                16.dp
            } else {
                with(density) { bleedPx.toDp() }.coerceAtLeast(16.dp)
            }
            val overlayHorizontalMargin = paddingExtra * 2 + 16.dp
            val overlayVerticalMargin = maxOf(overlayHorizontalMargin, EditorDims.MinOverlayVerticalMarginDp)

            val parentPadH = maxOf(paddingExtra * 2, overlayHorizontalMargin)
            val parentPadV = maxOf(paddingExtra * 2, overlayVerticalMargin)
            val bbWidth = displayW + overlayHorizontalMargin
            val bbHeight = displayH + overlayVerticalMargin
            val artboardCenterOffset = Offset(offsetXPx, offsetYPx)

            Box(
                modifier = modifier
                    .requiredSize(
                        width = displayW + parentPadH,
                        height = displayH + parentPadV,
                    )
                    .offset {
                        IntOffset(offsetXPx.roundToInt(), offsetYPx.roundToInt())
                    }
                    .zIndex(50f),
            ) {
                BoundingBoxOverlayV6(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .requiredSize(width = bbWidth, height = bbHeight),
                    contentWidth = effectiveShapeWidthPx,
                    contentHeight = effectiveShapeHeightPx,
                    viewport = layer.viewport,
                    displayScale = displayScale,
                    templateSize = templateSize,
                    lockAspectRatio = false,
                    onGesture = onGesture,
                    onGestureEnd = onGestureEnd,
                    onBodyClick = {
                        if (layer.shouldRenderLabelContent) onRequestInlineEdit?.invoke()
                    },
                    onBodyDoubleTap = { localTap ->
                        if (layer.shouldRenderLabelContent) {
                            onRequestInlineEdit?.invoke()
                        } else {
                            onCanvasDoubleTapFromOverlay(
                                localTap,
                                with(density) { bbWidth.toPx() },
                                with(density) { bbHeight.toPx() },
                                artboardCenterOffset,
                            )
                        }
                    },
                    showBoundingBox = true,
                    onBoundingBoxVisible = {},
                    isLocked = false,
                    otherLayers = allLayers.filter { it.id != layer.id },
                    minimalTextHandles = layer.shouldRenderLabelContent,
                    onGestureActiveChanged = onGestureActiveChanged,
                )
            }
        }

        layer.type == LayerType.SHADOW_REGION -> {
            val baseW = layer.shapeWidthPx
            val baseH = layer.shapeHeightPx
            val widthDp = with(density) {
                (baseW * layer.viewport.scale * displayScale).toInt().coerceAtLeast(1).toDp()
            }
            val heightDp = with(density) {
                (baseH * layer.viewport.scale * displayScale).toInt().coerceAtLeast(1).toDp()
            }
            val displayOffset = IntOffset(
                (layer.viewport.offset.x * displayScale).roundToInt(),
                (layer.viewport.offset.y * displayScale).roundToInt(),
            )
            val bbOverlayPad = EditorDims.overlayPaddingDp()
            val bbWidth = widthDp + bbOverlayPad
            val bbHeight = heightDp + bbOverlayPad
            val artboardCenterOffset = Offset(
                layer.viewport.offset.x * displayScale,
                layer.viewport.offset.y * displayScale,
            )

            Box(
                modifier = modifier
                    .requiredSize(
                        width = bbWidth,
                        height = bbHeight,
                    )
                    .offset { displayOffset }
                    .zIndex(50f),
            ) {
                BoundingBoxOverlayV6(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .requiredSize(width = bbWidth, height = bbHeight),
                    contentWidth = baseW,
                    contentHeight = baseH,
                    viewport = layer.viewport,
                    displayScale = displayScale,
                    templateSize = templateSize,
                    lockAspectRatio = false,
                    onGesture = onGesture,
                    onGestureEnd = onGestureEnd,
                    showBoundingBox = true,
                    onBoundingBoxVisible = {},
                    isLocked = false,
                    otherLayers = allLayers.filter { it.id != layer.id },
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
    }
}
