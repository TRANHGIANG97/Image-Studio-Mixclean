package com.thgiang.image.studio.ui.editor.components

import androidx.compose.foundation.background
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
import com.thgiang.image.studio.ui.editor.EditorShadowMapper.drawShapeDropShadow
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

    // ── Derive display dimensions from template-space shape size ──────────
    val displayW = with(density) { (layer.shapeWidthPx  * layer.viewport.scale * displayScale).toDp() }
    val displayH = with(density) { (layer.shapeHeightPx * layer.viewport.scale * displayScale).toDp() }

    // ── Centre offset in display pixels ──────────────────────────────────
    val offsetXPx = layer.viewport.offset.x * displayScale
    val offsetYPx = layer.viewport.offset.y * displayScale

    val shapeColor = Color(layer.shapeColorArgb)
    val shapeWidthPx = layer.shapeWidthPx * layer.viewport.scale * displayScale
    val shapeHeightPx = layer.shapeHeightPx * layer.viewport.scale * displayScale
    val isShapeVisible = EditorShapeGeometry.isFilledShape(
        layer.shapeType,
        (layer.shapeColorArgb ushr 24) and 0xFF,
        layer.fillGradient != null,
    ) || (EditorShapeGeometry.isLineShape(layer.shapeType) && layer.resolveStrokeColorArgb() != null)
    val canDrawShapeShadow = isShapeVisible || layer.hasStroke || layer.appearance.shadowIntensity > 0.05f
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
    val paddingX = if (isShapeVisible) {
        val calcX = 12.dp * layer.viewport.scale * displayScale
        if (calcX > displayW / 4) displayW / 4 else calcX
    } else {
        0.dp
    }
    val paddingY = if (isShapeVisible) {
        val calcY = 6.dp * layer.viewport.scale * displayScale
        if (calcY > displayH / 4) displayH / 4 else calcY
    } else {
        0.dp
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
            .requiredSize(displayW, displayH),
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
                }
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
            contentAlignment = Alignment.Center,
        ) {
        // ── Shape background drawn by Compose Canvas ──────────────────────
        if (isShapeVisible) {
            ShapeBackground(
                shapeType = layer.shapeType,
                fillBrush = fillBrush,
                cornerRadiusX = layer.cornerRadiusX,
                cornerRadiusY = layer.cornerRadiusY,
                shapeHeightPx = layer.shapeHeightPx,
                radiusScale = radiusScale,
                strokeColorArgb = layer.resolveStrokeColorArgb(),
                strokeWidthPx = layer.resolveStrokeWidthPx() * layer.viewport.scale * displayScale,
                strokeDashArray = layer.strokeDashArray,
                pathData = layer.pathData,
                polygonPoints = layer.polygonPoints,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // ── Text label ───────────────────────────────────────────────────
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
            val displayStrokeWidth = layer.resolveStrokeWidthPx() * layer.viewport.scale * displayScale
            if (layer.hasStroke) {
                Text(
                    text = displayText,
                    style = textStyle.copy(
                        brush = SolidColor(Color(layer.resolveStrokeColorArgb()!!)),
                        drawStyle = Stroke(width = displayStrokeWidth),
                    ),
                    modifier = Modifier
                        .widthIn(max = displayW)
                        .padding(horizontal = paddingX, vertical = paddingY),
                )
            }
            Text(
                text = displayText,
                style = textStyle,
                modifier = Modifier
                    .widthIn(max = displayW)
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
            contentWidth = layer.shapeWidthPx,
            contentHeight = layer.shapeHeightPx,
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
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val cardRadius = with(density) {
        (cornerRadiusX ?: cornerRadiusY ?: (shapeHeightPx * 0.16f)).toDp()
    }
    val pillRadius = with(density) {
        (EditorShapeGeometry.resolvePillCornerRadius(shapeHeightPx, cornerRadiusX, cornerRadiusY) * radiusScale).toDp()
    }
    val hasStroke = strokeWidthPx > 0f && strokeColorArgb != null
    val isLine = EditorShapeGeometry.isLineShape(shapeType)

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
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(pillRadius))
                        .background(fillBrush),
                )
            }
            shapeType == ShapeType.CARD -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(cardRadius))
                        .background(fillBrush),
                )
            }
            else -> {
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
