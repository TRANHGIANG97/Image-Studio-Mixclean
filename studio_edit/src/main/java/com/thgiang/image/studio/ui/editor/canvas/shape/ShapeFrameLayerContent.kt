package com.thgiang.image.studio.ui.editor.canvas.shape

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.thgiang.image.studio.ui.editor.label.geometry.EditorShapeGeometry
import com.thgiang.image.studio.ui.editor.mapper.EditorElevationMapper.drawShapeElevation
import com.thgiang.image.studio.ui.editor.mapper.EditorShadowMapper.drawShapeDropShadow
import com.thgiang.image.studio.ui.editor.mapper.hasShape3DDepth
import com.thgiang.image.studio.ui.editor.model.EditorLayer
import com.thgiang.image.studio.ui.editor.model.ShapeType
import com.thgiang.image.studio.ui.editor.mapper.hasShapeBorder
import com.thgiang.image.studio.ui.editor.mapper.resolveStrokeWidthPx
import com.thgiang.image.studio.ui.editor.model.appliesShapeElevation
import com.thgiang.image.studio.ui.editor.model.hasVisibleFrameGeometry
import com.thgiang.image.studio.ui.editor.mapper.resolveShapeBorderColorArgb
import androidx.compose.ui.draw.clip

@Composable
internal fun ShapeFrameLayerContent(
    layer: EditorLayer,
    displayScale: Float,
    templateScale: Float,
    fillBrush: Brush,
    hasShapeFill: Boolean,
    effectiveShapeHeightPx: Float,
    radiusScale: Float,
    modifier: Modifier = Modifier,
) {
    if (!layer.hasVisibleFrameGeometry) return

    val canDrawShapeShadow = hasShapeFill || layer.hasShapeBorder || layer.appearance.shadowIntensity > 0.05f
    val canDrawShapeElevation = layer.hasVisibleFrameGeometry &&
        layer.appearance.hasShape3DDepth(templateScale) &&
        layer.appearance.appliesShapeElevation()

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                if (canDrawShapeElevation) {
                    drawShapeElevation(
                        shapeType = layer.shapeType,
                        appearance = layer.appearance,
                        fillColorArgb = layer.shapeColorArgb,
                        scale = displayScale,
                        cornerRadiusX = layer.cornerRadiusX,
                        cornerRadiusY = layer.cornerRadiusY,
                        pathData = layer.pathData,
                        polygonPoints = layer.polygonPoints,
                    )
                }
                if (canDrawShapeShadow) {
                    drawShapeDropShadow(
                        shapeType = layer.shapeType,
                        appearance = layer.appearance,
                        scale = displayScale,
                        cornerRadiusX = layer.cornerRadiusX,
                        cornerRadiusY = layer.cornerRadiusY,
                        pathData = layer.pathData,
                        polygonPoints = layer.polygonPoints,
                        rotationDeg = layer.viewport.rotation,
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        ShapeFrameBackground(
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

@Composable
internal fun ShapeFrameBackground(
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
    val paint = Stroke(width = strokeWidthPx)
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
