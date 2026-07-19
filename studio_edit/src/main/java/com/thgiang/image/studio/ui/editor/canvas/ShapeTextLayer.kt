package com.thgiang.image.studio.ui.editor.canvas

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.thgiang.image.studio.R
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.zIndex
import com.thgiang.image.studio.ui.editor.canvas.GestureDelta
import com.thgiang.image.studio.ui.editor.canvas.shape.ShapeFrameLayerContent
import com.thgiang.image.studio.ui.editor.canvas.text.TextLabelLayerContent
import com.thgiang.image.studio.ui.editor.canvas.text.isTapInsideInlineTextBounds
import com.thgiang.image.studio.ui.editor.canvas.text.mapOverlayTapToTextOffset
import com.thgiang.image.studio.ui.editor.canvas.text.textSelectionRange
import com.thgiang.image.studio.ui.editor.label.geometry.EditorShapeGeometry
import com.thgiang.image.studio.ui.editor.mapper.EditorBlendModeMapper
import com.thgiang.image.studio.ui.editor.mapper.EditorGradientMapper
import com.thgiang.image.studio.ui.editor.mapper.EditorTextStyleMapper
import com.thgiang.image.studio.ui.editor.model.EditorLayer
import androidx.compose.foundation.layout.offset
import com.thgiang.image.studio.ui.editor.mapper.hasShapeBorder
import com.thgiang.image.studio.ui.editor.mapper.resolveStrokeWidthPx
import com.thgiang.image.studio.ui.editor.model.hasTextOnlyBackgroundDecor
import com.thgiang.image.studio.ui.editor.model.shouldRenderFrameContent
import com.thgiang.image.studio.ui.editor.model.shouldRenderLabelContent
import kotlin.math.roundToInt

/**
 * Facade composable: [ShapeFrameLayerContent] (Khung) + [TextLabelLayerContent] (Nhãn).
 */
@Composable
fun ShapeTextLayer(
    layer: EditorLayer,
    displayScale: Float,
    templateSize: IntSize,
    onGesture: (GestureDelta) -> Unit,
    onGestureEnd: () -> Unit,
    showBoundingBox: Boolean,
    isLocked: Boolean,
    isInlineEditing: Boolean = false,
    onRequestInlineEdit: () -> Unit = {},
    onTapToSelect: () -> Unit = {},
    onCommitInlineEdit: (String) -> Unit = {},
    onUpdateInlineEdit: (String) -> Unit = {},
    onInlineSelectionChange: (start: Int, end: Int) -> Unit = { _, _ -> },
    onSyncShapeSize: (widthPx: Float, heightPx: Float) -> Unit = { _, _ -> },
    allLayers: List<EditorLayer> = emptyList(),
    onGestureActiveChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val context = androidx.compose.ui.platform.LocalContext.current

    var composeFontFamily by remember(layer.id) { mutableStateOf<FontFamily?>(null) }
    LaunchedEffect(layer.fontFamily, layer.fontWeight, layer.fontStyle) {
        if (layer.fontFamily.isNullOrBlank()) {
            composeFontFamily = null
            return@LaunchedEffect
        }
        val base = com.thgiang.image.studio.util.FontDownloader.getTypeface(context, layer.fontFamily)
            ?: android.graphics.Typeface.DEFAULT
        composeFontFamily = FontFamily(
            EditorTextStyleMapper.resolveStyledTypeface(base, layer.fontWeight, layer.fontStyle),
        )
    }

    val templateScale = (layer.viewport.scale * displayScale).coerceAtLeast(0.01f)
    var textLayoutResult by remember(layer.id) { mutableStateOf<TextLayoutResult?>(null) }

    // BB dims come from layer model; text edits re-fit via updateShapeText / FinishTextEdit.
    val effectiveShapeWidthPx = layer.shapeWidthPx.coerceAtLeast(60f)
    val effectiveShapeHeightPx = layer.shapeHeightPx.coerceAtLeast(30f)

    val displayW = with(density) { (effectiveShapeWidthPx * templateScale).toDp() }
    val displayH = with(density) { (effectiveShapeHeightPx * templateScale).toDp() }

    val offsetXPx = layer.viewport.offset.x * displayScale
    val offsetYPx = layer.viewport.offset.y * displayScale

    val shapeColor = Color(layer.shapeColorArgb)
    val shapeWidthPx = effectiveShapeWidthPx * templateScale
    val shapeHeightPx = effectiveShapeHeightPx * templateScale
    val hasShapeFill = EditorShapeGeometry.isFilledShape(
        layer.shapeType,
        (layer.shapeColorArgb ushr 24) and 0xFF,
        layer.fillGradient != null,
    )
    val fillOpacity = EditorGradientMapper.shapeFillOpacity(layer.shapeColorArgb)
    val fillBrush = remember(layer.fillGradient, layer.shapeColorArgb, shapeWidthPx, shapeHeightPx) {
        EditorGradientMapper.toComposeBrush(
            gradient = layer.fillGradient,
            width = shapeWidthPx,
            height = shapeHeightPx,
            fallbackColor = shapeColor,
            opacityMultiplier = if (layer.fillGradient != null) fillOpacity else 1f,
        )
    }
    val textColor = Color(layer.textColorArgb)
    val textBrush = remember(layer.textColorGradient, layer.textColorArgb, shapeWidthPx, shapeHeightPx) {
        if (layer.textColorGradient != null) {
            EditorGradientMapper.toComposeBrush(
                gradient = layer.textColorGradient,
                width = shapeWidthPx,
                height = shapeHeightPx,
                fallbackColor = textColor,
            )
        } else {
            SolidColor(textColor)
        }
    }
    val fontScale = density.fontScale
    val scaledTextSize = (layer.textSizeSp / density.density) * layer.viewport.scale * displayScale
    val finalFontSize = if (fontScale > 0) scaledTextSize / fontScale else scaledTextSize

    val textStyle = TextStyle(
        brush = textBrush,
        fontSize = finalFontSize.sp,
        fontWeight = if (composeFontFamily != null) {
            FontWeight.Normal
        } else {
            EditorTextStyleMapper.resolveComposeFontWeight(layer.fontWeight)
        },
        fontStyle = if (composeFontFamily != null) {
            FontStyle.Normal
        } else {
            EditorTextStyleMapper.resolveComposeFontStyle(layer.fontStyle)
        },
        fontFamily = composeFontFamily,
        textAlign = EditorTextStyleMapper.resolveComposeTextAlign(layer.textAlign),
        textDecoration = EditorTextStyleMapper.resolveTextDecoration(layer.underline, layer.linethrough),
        lineHeight = layer.lineHeight?.let {
            val baseLineHeight = scaledTextSize * it
            val finalLineHeight = if (fontScale > 0) baseLineHeight / fontScale else baseLineHeight
            finalLineHeight.sp
        } ?: TextUnit.Unspecified,
        letterSpacing = {
            val baseSpacing = EditorTextStyleMapper.resolveLetterSpacingEm(layer.charSpacing, scaledTextSize)
            val finalSpacing = if (fontScale > 0) baseSpacing / fontScale else baseSpacing
            finalSpacing.sp
        }(),
    )

    val shouldRenderShape = layer.shouldRenderFrameContent
    val paddingX = if (shouldRenderShape) {
        val calcX = 12.dp * layer.viewport.scale * displayScale
        if (calcX > displayW / 4) displayW / 4 else calcX
    } else {
        0.dp
    }
    val paddingY = if (shouldRenderShape) {
        val calcY = 6.dp * layer.viewport.scale * displayScale
        if (calcY > displayH / 4) displayH / 4 else calcY
    } else {
        0.dp
    }

    // Auto-fit on text change is handled in LabelViewModelDelegate.updateShapeText.

    val defaultPlaceholder = stringResource(R.string.studio_text_default_placeholder)

    var inlineTextDraft by remember(layer.id) {
        val initialText = if (layer.text == "Nhập chữ..." || layer.text == defaultPlaceholder) "" else layer.text
        mutableStateOf(
            TextFieldValue(
                text = initialText,
                selection = TextRange(initialText.length),
            )
        )
    }
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    var inlineEditHadFocus by remember(layer.id) { mutableStateOf(false) }
    val radiusScale = layer.viewport.scale * displayScale
 
    LaunchedEffect(isInlineEditing) {
        if (isInlineEditing) {
            val initialText = if (layer.text == "Nhập chữ..." || layer.text == defaultPlaceholder) "" else layer.text
            inlineTextDraft = TextFieldValue(
                text = initialText,
                selection = TextRange(initialText.length),
            )
            inlineEditHadFocus = false
            focusRequester.requestFocus()
        }
    }

    LaunchedEffect(layer.text, isInlineEditing) {
        if (isInlineEditing) {
            val normalizedText = if (layer.text == "Nhập chữ..." || layer.text == defaultPlaceholder) "" else layer.text
            if (inlineTextDraft.text != normalizedText) {
                // Keep caret / selection when parent syncs text (avoid jumping to end).
                val max = normalizedText.length
                val start = inlineTextDraft.selection.start.coerceIn(0, max)
                val end = inlineTextDraft.selection.end.coerceIn(0, max)
                inlineTextDraft = TextFieldValue(
                    text = normalizedText,
                    selection = TextRange(start, end),
                )
            }
        }
    }

    val commitInlineEdit = { onCommitInlineEdit(inlineTextDraft.text) }

    val isTextOnlyLayer = EditorShapeGeometry.isTextOnlyShape(layer.shapeType)
    val strokePad = if (!isTextOnlyLayer && layer.hasShapeBorder) {
        layer.resolveStrokeWidthPx() * templateScale
    } else {
        0f
    }
    val bleedPx = if (isTextOnlyLayer) {
        0f
    } else {
        com.thgiang.image.studio.ui.editor.mapper.EditorShadowMapper.computeShadowBleedPx(
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

    val moveHandleExtraTop = if (isInlineEditing) {
        EditorDims.TextInlineMoveHandleGapDp +
            EditorDims.TextInlineMoveHandleHitRadiusDp + 8.dp
    } else {
        0.dp
    }

    val overlayHorizontalMargin = paddingExtra * 2 + 16.dp
    val overlayVerticalMargin = maxOf(overlayHorizontalMargin, EditorDims.MinOverlayVerticalMarginDp)
    val parentPadH = maxOf(paddingExtra * 2, overlayHorizontalMargin)
    val parentPadV = maxOf(paddingExtra * 2, overlayVerticalMargin + moveHandleExtraTop)

    val applyInlineTextPointer: (Offset, Offset?, Boolean) -> Unit = fn@ { tapStart, tapEnd, isDragSelect ->
        val layout = textLayoutResult ?: return@fn
        val overlayW = with(density) { (displayW + overlayHorizontalMargin).toPx() }
        val overlayH = with(density) { (displayH + overlayVerticalMargin + moveHandleExtraTop).toPx() }
        // Overlay box is Alignment.Center with the text layer — use geometric center.
        val contentCenter = Offset(overlayW / 2f, overlayH / 2f)
        val screenW = with(density) { displayW.toPx() }
        val screenH = with(density) { displayH.toPx() }
        val paddingXPx = with(density) { paddingX.toPx() }
        val paddingYPx = with(density) { paddingY.toPx() }
        val rotation = layer.viewport.rotation

        if (!isTapInsideInlineTextBounds(tapStart, contentCenter, screenW, screenH, rotation)) {
            return@fn
        }

        if (isDragSelect && tapEnd != null &&
            isTapInsideInlineTextBounds(tapEnd, contentCenter, screenW, screenH, rotation)
        ) {
            val startOffset = mapOverlayTapToTextOffset(
                tapStart, contentCenter, screenW, screenH, rotation,
                paddingXPx, paddingYPx, layout,
            )
            val endOffset = mapOverlayTapToTextOffset(
                tapEnd, contentCenter, screenW, screenH, rotation,
                paddingXPx, paddingYPx, layout,
            )
            val (selStart, selEnd) = textSelectionRange(startOffset, endOffset)
            inlineTextDraft = TextFieldValue(
                text = inlineTextDraft.text,
                selection = TextRange(selStart, selEnd),
            )
            onInlineSelectionChange(selStart, selEnd)
        } else {
            val offset = mapOverlayTapToTextOffset(
                tapStart, contentCenter, screenW, screenH, rotation,
                paddingXPx, paddingYPx, layout,
            )
            inlineTextDraft = TextFieldValue(
                text = inlineTextDraft.text,
                selection = TextRange(offset),
            )
            onInlineSelectionChange(offset, offset)
        }
        focusRequester.requestFocus()
    }

    Box(
        modifier = modifier
            .offset {
                IntOffset(
                    offsetXPx.roundToInt(),
                    offsetYPx.roundToInt()
                )
            }
            .requiredSize(displayW + parentPadH, displayH + parentPadV),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .requiredSize(displayW + paddingExtra * 2, displayH + paddingExtra * 2)
                .zIndex(if (isInlineEditing) 3f else 0f)
                .graphicsLayer {
                    rotationZ = layer.viewport.rotation
                    scaleX = if (layer.viewport.flippedH) -1f else 1f
                    scaleY = if (layer.viewport.flippedV) -1f else 1f
                    alpha = layer.appearance.alpha
                    blendMode = EditorBlendModeMapper.toComposeBlendMode(layer.blendMode)
                    compositingStrategy = if (EditorBlendModeMapper.needsOffscreenCompositing(layer.blendMode)) {
                        CompositingStrategy.Offscreen
                    } else {
                        CompositingStrategy.Auto
                    }
                },
        ) {
            if (shouldRenderShape || layer.hasTextOnlyBackgroundDecor) {
                ShapeFrameLayerContent(
                    layer = layer,
                    displayScale = displayScale,
                    templateScale = templateScale,
                    fillBrush = fillBrush,
                    hasShapeFill = hasShapeFill,
                    effectiveShapeHeightPx = effectiveShapeHeightPx,
                    radiusScale = radiusScale,
                    modifier = Modifier.align(Alignment.Center).requiredSize(displayW, displayH),
                )
            }

            if (layer.shouldRenderLabelContent) {
                TextLabelLayerContent(
                    layer = layer,
                    templateScale = templateScale,
                    textStyle = textStyle,
                    displaySize = androidx.compose.ui.unit.DpSize(displayW, displayH),
                    paddingX = paddingX,
                    paddingY = paddingY,
                    isInlineEditing = isInlineEditing,
                    inlineTextDraft = inlineTextDraft,
                    onInlineTextDraftChange = { text ->
                        inlineTextDraft = text
                        onUpdateInlineEdit(text.text)
                        onInlineSelectionChange(text.selection.min, text.selection.max)
                    },
                    focusRequester = focusRequester,
                    inlineEditHadFocus = inlineEditHadFocus,
                    onInlineEditHadFocus = { inlineEditHadFocus = it },
                    onCommitInlineEdit = commitInlineEdit,
                    onTextLayout = { textLayoutResult = it },
                    textLayoutResult = textLayoutResult,
                    modifier = Modifier.align(Alignment.Center).requiredSize(displayW, displayH),
                )
            }
        }

        if (showBoundingBox || isInlineEditing) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .requiredSize(
                        width = displayW + overlayHorizontalMargin,
                        height = displayH + overlayVerticalMargin + moveHandleExtraTop,
                    )
                    .zIndex(
                        when {
                            isInlineEditing -> 4f
                            showBoundingBox -> 25f
                            else -> 0f
                        },
                    ),
            ) {
                val moveHandleExtraTopPx = with(density) { moveHandleExtraTop.toPx() }
                BoundingBoxOverlayV6(
                    modifier = Modifier.fillMaxSize(),
                    contentWidth = effectiveShapeWidthPx,
                    contentHeight = effectiveShapeHeightPx,
                    viewport = layer.viewport,
                    displayScale = displayScale,
                    templateSize = templateSize,
                    lockAspectRatio = false,
                    onGesture = onGesture,
                    onGestureEnd = onGestureEnd,
                    onBodyClick = {
                        if (!isLocked && layer.shouldRenderLabelContent) onRequestInlineEdit()
                    },
                    onBodyDoubleTap = {
                        if (!isLocked && layer.shouldRenderLabelContent) onRequestInlineEdit()
                    },
                    showBoundingBox = true,
                    onBoundingBoxVisible = {},
                    isLocked = isLocked,
                    otherLayers = allLayers.filter { it.id != layer.id },
                    isInlineEditing = isInlineEditing,
                    inlineEditTopInsetPx = if (isInlineEditing) moveHandleExtraTopPx else 0f,
                    minimalTextHandles = layer.shouldRenderLabelContent && !isInlineEditing,
                    onInlineTextPointer = applyInlineTextPointer,
                    onGestureActiveChanged = onGestureActiveChanged,
                )
            }
        }

        if (isInlineEditing) {
            TextEditFrameOverlay(
                contentWidth = effectiveShapeWidthPx,
                contentHeight = effectiveShapeHeightPx,
                viewport = layer.viewport,
                displayScale = displayScale,
                modifier = Modifier
                    .align(Alignment.Center)
                    .requiredSize(displayW + overlayHorizontalMargin, displayH + overlayVerticalMargin)
                    .zIndex(2f),
            )
        }
    }
}
