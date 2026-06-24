package com.thgiang.image.studio.ui.editor.canvas
import com.thgiang.image.studio.ui.editor.label.geometry.*
import com.thgiang.image.studio.ui.editor.canvas.*
import com.thgiang.image.studio.ui.editor.mapper.*
import com.thgiang.image.studio.ui.editor.mapper.EditorShadowMapper.drawShapeDropShadow

import androidx.compose.foundation.background
import com.thgiang.image.studio.ui.editor.model.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thgiang.image.studio.ui.editor.*
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * ShapeTextLayer — renders a SHAPE_TEXT EditorLayer on the canvas.
 *
 * Supports three shapes:
 *  • PILL     — fully rounded capsule
 *  • CARD     — softly rounded rectangle
 *  • TEARDROP — 3 rounded corners + a sharp pointer at the bottom-left
 *
 * The layer participates in the same gesture model as image layers
 * (drag, pinch-zoom, two-finger rotate) and shows the bounding box
 * selection ring when selected.
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
    onCommitInlineEdit: (String) -> Unit = {},
    onSyncShapeSize: (widthPx: Float, heightPx: Float) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val context = androidx.compose.ui.platform.LocalContext.current

    var composeFontFamily by remember(layer.fontFamily) { mutableStateOf<androidx.compose.ui.text.font.FontFamily?>(null) }
    LaunchedEffect(layer.fontFamily) {
        layer.fontFamily?.let { familyName ->
            val nativeTf = com.thgiang.image.studio.util.FontDownloader.getTypeface(context, familyName)
            if (nativeTf != null) {
                composeFontFamily = androidx.compose.ui.text.font.FontFamily(nativeTf)
            }
        }
    }

    val templateScale = layer.viewport.scale * displayScale
    val shouldRenderShape = !EditorShapeGeometry.isTextOnlyShape(layer.shapeType)
    var textLayoutResult by remember(layer.id) { mutableStateOf<TextLayoutResult?>(null) }

    val strokePadTemplate = if (layer.hasShapeBorder) layer.resolveStrokeWidthPx() * 2f else 0f
    val padHXTemplate = with(density) { 12.dp.toPx() } / templateScale.coerceAtLeast(0.01f)
    val padVYTemplate = with(density) { 6.dp.toPx() } / templateScale.coerceAtLeast(0.01f)

    val textFittedWidthPx = textLayoutResult?.let {
        (it.size.width / templateScale + 2f * padHXTemplate + strokePadTemplate).coerceAtLeast(60f)
    }
    val textFittedHeightPx = textLayoutResult?.let {
        (it.size.height / templateScale + 2f * padVYTemplate + strokePadTemplate).coerceAtLeast(30f)
    }
    val effectiveShapeWidthPx = maxOf(layer.shapeWidthPx, textFittedWidthPx ?: layer.shapeWidthPx)
    val effectiveShapeHeightPx = maxOf(layer.shapeHeightPx, textFittedHeightPx ?: layer.shapeHeightPx)

    val displayW = with(density) { (effectiveShapeWidthPx * templateScale).toDp() }
    val displayH = with(density) { (effectiveShapeHeightPx * templateScale).toDp() }

    // ── Centre offset in display pixels ──────────────────────────────────
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
    val canDrawShapeShadow = shouldRenderShape &&
        (hasShapeFill || layer.hasShapeBorder || layer.appearance.shadowIntensity > 0.05f)
    val textColor = Color(layer.textColorArgb)
    val textSizeSp = layer.textSizeSp
    val fillBrush = remember(layer.fillGradient, layer.shapeColorArgb, shapeWidthPx, shapeHeightPx) {
        EditorGradientMapper.toComposeBrush(
            gradient = layer.fillGradient,
            width = shapeWidthPx,
            height = shapeHeightPx,
            fallbackColor = shapeColor,
        )
    }
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
    val scaledTextSize = (textSizeSp / density.density) * layer.viewport.scale * displayScale
    val finalFontSize = if (fontScale > 0) scaledTextSize / fontScale else scaledTextSize

    val textStyle = TextStyle(
        brush = textBrush,
        fontSize = finalFontSize.sp,
        fontWeight = EditorTextStyleMapper.resolveComposeFontWeight(layer.fontWeight),
        fontStyle = EditorTextStyleMapper.resolveComposeFontStyle(layer.fontStyle),
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

    LaunchedEffect(textFittedWidthPx, textFittedHeightPx, layer.id, shouldRenderShape) {
        if (!shouldRenderShape) return@LaunchedEffect
        val targetW = textFittedWidthPx ?: return@LaunchedEffect
        val targetH = textFittedHeightPx ?: return@LaunchedEffect
        kotlinx.coroutines.delay(250)
        if (abs(targetW - layer.shapeWidthPx) > 1f || abs(targetH - layer.shapeHeightPx) > 1f) {
            onSyncShapeSize(targetW, targetH)
        }
    }

    var inlineTextDraft by remember(layer.id) { mutableStateOf(layer.text) }
    val focusRequester = remember { FocusRequester() }
    var inlineEditHadFocus by remember(layer.id) { mutableStateOf(false) }
    val radiusScale = layer.viewport.scale * displayScale

    LaunchedEffect(isInlineEditing) {
        if (isInlineEditing) {
            inlineTextDraft = layer.text
            inlineEditHadFocus = false
            focusRequester.requestFocus()
        }
    }

    val commitInlineEdit = {
        onCommitInlineEdit(inlineTextDraft)
    }

    Box(
        modifier = modifier
            .offset { IntOffset(offsetXPx.roundToInt(), offsetYPx.roundToInt()) }
            .requiredSize(displayW, displayH)
            .graphicsLayer {
                rotationZ = layer.viewport.rotation
                alpha = layer.appearance.alpha
                scaleX = if (layer.viewport.flippedH) -1f else 1f
                scaleY = if (layer.viewport.flippedV) -1f else 1f
                blendMode = EditorBlendModeMapper.toComposeBlendMode(layer.blendMode)
                compositingStrategy = if (EditorBlendModeMapper.needsOffscreenCompositing(layer.blendMode)) {
                    CompositingStrategy.Offscreen
                } else {
                    CompositingStrategy.Auto
                }
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    if (canDrawShapeShadow) {
                        drawShapeDropShadow(
                            shapeType = layer.shapeType,
                            appearance = layer.appearance,
                            scale = displayScale,
                            cornerRadiusX = layer.cornerRadiusX,
                            cornerRadiusY = layer.cornerRadiusY,
                            pathData = layer.pathData,
                            polygonPoints = layer.polygonPoints,
                        )
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
        if (shouldRenderShape) {
            ShapeBackground(
                shapeType = layer.shapeType,
                fillBrush = fillBrush,
                cornerRadiusX = layer.cornerRadiusX,
                cornerRadiusY = layer.cornerRadiusY,
                shapeHeightPx = effectiveShapeHeightPx,
                radiusScale = radiusScale,
                strokeColorArgb = layer.resolveShapeBorderColorArgb(),
                strokeWidthPx = layer.resolveStrokeWidthPx() * layer.viewport.scale * displayScale,
                strokeDashArray = layer.strokeDashArray,
                pathData = layer.pathData,
                polygonPoints = layer.polygonPoints,
                drawFill = hasShapeFill,
                modifier = Modifier.fillMaxSize(),
            )
        }
        }

        Box(
            modifier = Modifier
                .fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
        if (isInlineEditing) {
            BasicTextField(
                value = inlineTextDraft,
                onValueChange = { inlineTextDraft = it },
                textStyle = textStyle,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { commitInlineEdit() }),
                modifier = Modifier
                    .widthIn(max = displayW)
                    .focusRequester(focusRequester)
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            inlineEditHadFocus = true
                        } else if (inlineEditHadFocus && isInlineEditing) {
                            commitInlineEdit()
                        }
                    }
                    .padding(horizontal = paddingX, vertical = paddingY),
            )
        } else if (layer.text.isNotBlank()) {
            val displayText = EditorTextStyleMapper.applyTextTransform(layer.text, layer.textTransform)
            Text(
                text = displayText,
                style = textStyle,
                onTextLayout = { textLayoutResult = it },
                overflow = TextOverflow.Visible,
                softWrap = false,
                modifier = Modifier
                    .then(
                        if (layer.textBackgroundColorArgb != null) {
                            Modifier.background(Color(layer.textBackgroundColorArgb))
                        } else {
                            Modifier
                        },
                    )
                    .padding(horizontal = paddingX, vertical = paddingY),
            )
        }
        }

        BoundingBoxOverlayV6(
            modifier = Modifier
                .align(Alignment.Center)
                .requiredSize(
                    width = displayW + 80.dp,
                    height = displayH + 80.dp,
                ),
            contentWidth = effectiveShapeWidthPx,
            contentHeight = effectiveShapeHeightPx,
            viewport = layer.viewport,
            displayScale = displayScale,
            templateSize = templateSize,
            onGesture = onGesture,
            onGestureEnd = onGestureEnd,
            showBoundingBox = showBoundingBox,
            onBoundingBoxVisible = {},
            isLocked = isLocked,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Private helpers
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ShapeBackground(
    shapeType: ShapeType,
    fillBrush: Brush,
    cornerRadiusX: Float?,
    cornerRadiusY: Float?,
    shapeHeightPx: Float,
    radiusScale: Float,
    strokeColorArgb: Int?,
    strokeWidthPx: Float,
    strokeDashArray: List<Float>,
    pathData: String?,
    polygonPoints: List<Float>,
    drawFill: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val cardRadius = with(density) {
        (cornerRadiusX ?: cornerRadiusY ?: 0f).toDp()
    }
    val pillRadius = with(density) {
        (EditorShapeGeometry.resolvePillCornerRadius(shapeHeightPx, cornerRadiusX, cornerRadiusY) * radiusScale).toDp()
    }
    val hasStroke = strokeWidthPx > 0f && strokeColorArgb != null && !EditorShapeGeometry.isTextOnlyShape(shapeType)
    val isLine = EditorShapeGeometry.isLineShape(shapeType)
    val isTextOnly = EditorShapeGeometry.isTextOnlyShape(shapeType)

    Box(
        modifier = modifier.drawBehind {
            if (hasStroke && !isLine) {
                drawShapeOutline(
                    shapeType = shapeType,
                    strokeColor = strokeColorArgb!!,
                    strokeWidthPx = strokeWidthPx,
                    cornerRadiusX = cornerRadiusX,
                    cornerRadiusY = cornerRadiusY,
                    shapeHeightPx = shapeHeightPx,
                    pathData = pathData,
                    polygonPoints = polygonPoints,
                )
            }
        },
    ) {
        when {
            isTextOnly -> Unit
            isLine && strokeColorArgb != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawBehind {
                            drawLine(
                                color = Color(strokeColorArgb),
                                start = Offset(0f, size.height / 2f),
                                end = Offset(size.width, size.height / 2f),
                                strokeWidth = strokeWidthPx,
                            )
                        },
                )
            }
            shapeType == ShapeType.PILL -> {
                if (drawFill) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(pillRadius))
                            .background(fillBrush),
                    )
                }
            }
            shapeType == ShapeType.CARD -> {
                if (drawFill) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(cardRadius))
                            .background(fillBrush),
                    )
                }
            }
            else -> {
                if (drawFill) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .drawBehind {
                                val path = EditorShapeGeometry.composePath(
                                    shapeType = shapeType,
                                    size = size,
                                    cornerRadiusX = cornerRadiusX,
                                    cornerRadiusY = cornerRadiusY,
                                    pathData = pathData,
                                    polygonPoints = polygonPoints,
                                )
                                drawPath(path, fillBrush)
                            },
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawShapeOutline(
    shapeType: ShapeType,
    strokeColor: Int,
    strokeWidthPx: Float,
    cornerRadiusX: Float? = null,
    cornerRadiusY: Float? = null,
    shapeHeightPx: Float = size.height,
    pathData: String? = null,
    polygonPoints: List<Float> = emptyList(),
) {
    val paint = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidthPx)
    val color = Color(strokeColor)

    if (EditorShapeGeometry.isLineShape(shapeType)) {
        drawLine(
            color = color,
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = strokeWidthPx,
        )
        return
    }

    val path = EditorShapeGeometry.composePath(
        shapeType = shapeType,
        size = size,
        cornerRadiusX = cornerRadiusX,
        cornerRadiusY = cornerRadiusY,
        pathData = pathData,
        polygonPoints = polygonPoints,
    )
    drawPath(path = path, color = color, style = paint)
}
