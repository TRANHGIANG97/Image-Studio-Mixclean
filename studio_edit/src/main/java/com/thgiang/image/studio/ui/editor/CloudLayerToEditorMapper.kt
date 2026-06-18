package com.thgiang.image.studio.ui.editor

import com.thgiang.image.core.domain.model.template.CloudLayer
import com.thgiang.image.core.domain.model.template.CloudTemplate
import com.thgiang.image.core.domain.model.template.ColorArgbParser
import com.thgiang.image.core.domain.model.template.isLocked
import com.thgiang.image.core.domain.model.template.isShadowRegionLayer
import com.thgiang.image.core.domain.model.template.isVisible
import com.thgiang.image.core.domain.model.template.resolvedImageUrl
import com.thgiang.image.core.domain.model.template.resolvedShapeFillArgb
import com.thgiang.image.core.domain.model.template.resolvedTextColorArgb
import java.util.UUID

/**
 * Maps admin_web [CloudTemplate] layers into in-editor [EditorLayer] models.
 */
object CloudLayerToEditorMapper {

    fun mapLayers(cloudTemplate: CloudTemplate, scaledDensity: Float): List<EditorLayer> {
        val canvasWidth = cloudTemplate.canvas.baseWidth
        val canvasHeight = cloudTemplate.canvas.baseHeight

        return cloudTemplate.layers
            .filter { it.isVisible() }
            .sortedBy { it.zIndex }
            .mapNotNull { cloudLayer ->
                mapLayer(cloudLayer, canvasWidth, canvasHeight, scaledDensity)
            }
    }

    private fun mapLayer(
        cloudLayer: CloudLayer,
        canvasWidth: Int,
        canvasHeight: Int,
        scaledDensity: Float,
    ): EditorLayer? {
        val isText = cloudLayer.type == "TEXT" || cloudLayer.type == "SHAPE_TEXT"
        val isShadowRegion = cloudLayer.isShadowRegionLayer()
        val isDecorationShape = cloudLayer.isDecorationShapeLayer()
        val offsetX = (cloudLayer.transform.anchorX - 0.5f) * canvasWidth.toFloat()
        val offsetY = (cloudLayer.transform.anchorY - 0.5f) * canvasHeight.toFloat()
        val layerId = cloudLayer.layerId.ifEmpty { UUID.randomUUID().toString() }
        val viewport = EditorViewport(
            offsetX = offsetX,
            offsetY = offsetY,
            scale = cloudLayer.transform.scale,
            rotation = cloudLayer.transform.rotation,
        )
        val appearance = EditorAppearance(
            shadowIntensity = cloudLayer.payload.shadowIntensity ?: 0f,
            alpha = cloudLayer.payload.alpha ?: 1f,
            shadowAngle = cloudLayer.payload.shadowAngle ?: 45f,
            shadowDistance = cloudLayer.payload.shadowDistance ?: 12f,
            shadowColorArgb = cloudLayer.payload.shadowColorArgb ?: 0xFF000000.toInt(),
            shadowBlur = cloudLayer.payload.shadowBlur,
        )
        val locked = cloudLayer.isLocked()

        return when {
            isText || isDecorationShape -> mapShapeTextLayer(cloudLayer, layerId, viewport, appearance, scaledDensity, locked)

            isShadowRegion -> {
                EditorLayer(
                    id = layerId,
                    type = LayerType.SHADOW_REGION,
                    product = EditorProduct(
                        baseWidth = cloudLayer.payload.baseWidth ?: 0,
                        baseHeight = cloudLayer.payload.baseHeight ?: 0,
                    ),
                    viewport = viewport,
                    appearance = appearance,
                    isLocked = locked,
                    blendMode = cloudLayer.payload.blendMode,
                    strokeColorArgb = EditorStrokeMapper.parseStrokeColor(cloudLayer.payload.stroke),
                    strokeWidthPx = cloudLayer.payload.strokeWidth ?: 0f,
                    strokeDashArray = cloudLayer.payload.strokeDashArray ?: emptyList(),
                )
            }

            else -> {
                val imageUrl = cloudLayer.resolvedImageUrl() ?: return null
                EditorLayer(
                    id = layerId,
                    type = LayerType.IMAGE,
                    product = EditorProduct(
                        originalUriString = imageUrl,
                        foregroundUriString = imageUrl,
                        isBackgroundRemoved = true,
                        baseWidth = cloudLayer.payload.baseWidth ?: 0,
                        baseHeight = cloudLayer.payload.baseHeight ?: 0,
                        isSample = cloudLayer.type == "PLACEHOLDER_OBJECT",
                    ),
                    viewport = viewport.copy(
                        flippedH = cloudLayer.payload.flippedH ?: false,
                        flippedV = cloudLayer.payload.flippedV ?: false,
                    ),
                    appearance = appearance,
                    cropRatio = cloudLayer.resolvedCropRatio(),
                    isLocked = locked,
                    blendMode = cloudLayer.payload.blendMode,
                    strokeColorArgb = EditorStrokeMapper.parseStrokeColor(cloudLayer.payload.stroke),
                    strokeWidthPx = cloudLayer.payload.strokeWidth ?: 0f,
                    strokeDashArray = cloudLayer.payload.strokeDashArray ?: emptyList(),
                )
            }
        }
    }

    private fun mapShapeTextLayer(
        cloudLayer: CloudLayer,
        layerId: String,
        viewport: EditorViewport,
        appearance: EditorAppearance,
        scaledDensity: Float,
        locked: Boolean,
    ): EditorLayer {
        val density = scaledDensity.coerceAtLeast(1f)
        val payload = cloudLayer.payload
        val displayText = EditorTextStyleMapper.applyTextTransform(
            payload.text.orEmpty(),
            payload.textTransform,
        )

        val shapeType = mapCloudShapeType(payload.shapeType)
        val isLine = shapeType == ShapeType.LINE
        val lineStrokeArgb = payload.fillColor?.let(ColorArgbParser::parseOrNull)
            ?: payload.resolvedShapeFillArgb()
            ?: 0xFF6366F1.toInt()

        return EditorLayer(
            id = layerId,
            type = LayerType.SHAPE_TEXT,
            text = displayText,
            textColorArgb = payload.resolvedTextColorArgb() ?: 0xFFFFFFFF.toInt(),
            textSizeSp = ((payload.fontSize ?: 60f) / density).coerceIn(8f, 120f),
            shapeType = shapeType,
            shapeColorArgb = if (isLine) {
                0x00FFFFFF
            } else {
                payload.resolvedShapeFillArgb() ?: 0x00FFFFFF.toInt()
            },
            shapeWidthPx = (payload.baseWidth?.toFloat() ?: 350f).coerceAtLeast(1f),
            shapeHeightPx = (payload.baseHeight?.toFloat() ?: 140f).coerceAtLeast(1f),
            fontFamily = payload.font,
            fontWeight = payload.fontWeight,
            fontStyle = payload.fontStyle,
            textAlign = payload.textAlign,
            underline = payload.underline == true,
            linethrough = payload.linethrough == true,
            lineHeight = payload.lineHeight,
            charSpacing = payload.charSpacing ?: 0f,
            textBackgroundColorArgb = payload.textBackgroundColor?.let(ColorArgbParser::parseOrNull),
            textTransform = payload.textTransform,
            cornerRadiusX = payload.rx,
            cornerRadiusY = payload.ry,
            blendMode = payload.blendMode,
            strokeColorArgb = if (isLine) {
                lineStrokeArgb
            } else {
                EditorStrokeMapper.parseStrokeColor(payload.stroke)
            },
            strokeWidthPx = if (isLine) {
                payload.strokeWidth ?: 6f
            } else {
                payload.strokeWidth ?: 0f
            },
            strokeDashArray = payload.strokeDashArray ?: emptyList(),
            fillGradient = payload.fillGradient,
            textColorGradient = payload.textColorGradient,
            pathData = payload.pathData,
            polygonPoints = payload.polygonPoints ?: emptyList(),
            viewport = viewport,
            appearance = appearance,
            isLocked = locked,
        )
    }

    private fun CloudLayer.isDecorationShapeLayer(): Boolean {
        return type.equals("DECORATION", ignoreCase = true) &&
            !isShadowRegionLayer() &&
            resolvedImageUrl() == null &&
            (payload.shapeType != null || payload.resolvedShapeFillArgb() != null)
    }

    private fun CloudLayer.resolvedCropRatio(): CropRatio {
        return payload.cropRatio
            ?.let { value -> runCatching { CropRatio.valueOf(value) }.getOrNull() }
            ?: CropRatio.ORIGINAL
    }

    private fun mapCloudShapeType(raw: String?): ShapeType {
        return when (raw?.lowercase()) {
            "circle" -> ShapeType.CIRCLE
            "star" -> ShapeType.STAR
            "hexagon" -> ShapeType.HEXAGON
            "triangle" -> ShapeType.TRIANGLE
            "line" -> ShapeType.LINE
            "diamond" -> ShapeType.DIAMOND
            "arrow" -> ShapeType.ARROW
            "path" -> ShapeType.PATH
            "polygon" -> ShapeType.POLYGON
            "card", "rounded-rect", "rect" -> ShapeType.CARD
            "teardrop", "heart" -> ShapeType.TEARDROP
            "pill" -> ShapeType.PILL
            else -> ShapeType.PILL
        }
    }
}
