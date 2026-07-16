package com.thgiang.image.studio.ui.editor.mapper
import com.thgiang.image.studio.ui.editor.canvas.*
import com.thgiang.image.studio.ui.editor.mapper.*

import com.thgiang.image.studio.ui.editor.label.model.ShapeLabelDefaults
import com.thgiang.image.studio.ui.editor.label.geometry.EditorShapeGeometry
import com.thgiang.image.studio.ui.editor.model.*
import com.thgiang.image.studio.ui.editor.model.EditorLayerNormalizer

import com.thgiang.image.core.domain.model.template.CloudLayer
import com.thgiang.image.core.domain.model.template.CloudTemplate
import com.thgiang.image.core.domain.model.template.ColorArgbParser
import com.thgiang.image.core.domain.model.template.isLocked
import com.thgiang.image.core.domain.model.template.hasGradientBakedShadow
import com.thgiang.image.core.domain.model.template.isShadowRegionLayer
import com.thgiang.image.core.domain.model.template.isReplaceableLayer
import com.thgiang.image.core.domain.model.template.isVisible
import com.thgiang.image.core.domain.model.template.resolvedImageUrl
import com.thgiang.image.core.domain.model.template.resolvedShapeFillArgb
import com.thgiang.image.core.domain.model.template.resolvedTextColorArgb
import android.util.Log
import com.thgiang.image.core.domain.logging.AppLogger
import com.thgiang.image.studio.util.replaceLocalhostWithConfiguredHost
import java.util.UUID

/**
 * Maps admin_web [CloudTemplate] layers into in-editor [EditorLayer] models.
 */
object CloudLayerToEditorMapper {

    private const val TAG = "CloudMapper"

    fun mapLayers(
        cloudTemplate: CloudTemplate,
        scaledDensity: Float,
        logger: AppLogger? = null,
    ): List<EditorLayer> {
        val canvasWidth = cloudTemplate.canvas.baseWidth
        val canvasHeight = cloudTemplate.canvas.baseHeight
        var skippedCount = 0

        return cloudTemplate.layers
            .sortedBy { it.zIndex }
            .mapNotNull { cloudLayer ->
                // Graceful skip: một layer hỏng không được phép kéo sập cả template.
                val result = runCatching {
                    mapLayer(cloudLayer, canvasWidth, canvasHeight, scaledDensity)
                }
                val mapped = result.getOrNull()
                if (mapped == null) {
                    skippedCount++
                    val error = result.exceptionOrNull()
                    Log.w(TAG, "Skipped layer ${cloudLayer.layerId}: ${error?.message ?: "unmappable payload"}")
                    val skipContext = mapOf(
                        "templateId" to cloudTemplate.templateId,
                        "layerId" to cloudLayer.layerId,
                    )
                    if (error != null) {
                        logger?.logNonFatal(error, skipContext)
                    } else {
                        logger?.logWarning("Skipped unmappable layer", skipContext)
                    }
                }
                mapped
            }
            .let { EditorLayerNormalizer.normalize(it) }
            .also { normalized ->
                logger?.logEvent(
                    "template_mapped",
                    mapOf(
                        "templateId" to cloudTemplate.templateId,
                        "layerCount" to normalized.size.toString(),
                        "skippedCount" to skippedCount.toString(),
                    ),
                )
            }
    }

    private fun mapLayer(
        cloudLayer: CloudLayer,
        canvasWidth: Int,
        canvasHeight: Int,
        scaledDensity: Float,
    ): EditorLayer? {
        val isText = isTextLayer(cloudLayer)
        val isShadowRegion = !isText && cloudLayer.isShadowRegionLayer()
        val isDecorationShape = !isText && cloudLayer.isDecorationShapeLayer()
        val offsetX = (cloudLayer.transform.anchorX - 0.5f) * canvasWidth.toFloat()
        val offsetY = (cloudLayer.transform.anchorY - 0.5f) * canvasHeight.toFloat()
        val layerId = cloudLayer.layerId.ifEmpty { UUID.randomUUID().toString() }
        val viewport = EditorViewport(
            offsetX = offsetX,
            offsetY = offsetY,
            scale = cloudLayer.transform.scale,
            rotation = cloudLayer.transform.rotation,
        )
        val appearance = resolveAppearance(cloudLayer)
        val locked = cloudLayer.isLocked()
        val isVisible = cloudLayer.isVisible()

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
                    shapeWidthPx = (cloudLayer.payload.baseWidth ?: 0).toFloat(),
                    shapeHeightPx = (cloudLayer.payload.baseHeight ?: 0).toFloat(),
                    viewport = viewport,
                    appearance = appearance,
                    isLocked = locked,
                    isVisible = isVisible,
                    blendMode = cloudLayer.payload.blendMode,
                    strokeColorArgb = EditorStrokeMapper.parseStrokeColor(cloudLayer.payload.stroke),
                    strokeWidthPx = cloudLayer.payload.strokeWidth ?: 0f,
                    strokeDashArray = cloudLayer.payload.strokeDashArray ?: emptyList(),
                    strokeDashGapPx = EditorStrokeMapper.extractDashGap(
                        cloudLayer.payload.strokeDashArray ?: emptyList(),
                    ),
                )
            }

            else -> {
                val baseWidth = cloudLayer.payload.baseWidth ?: 0
                val baseHeight = cloudLayer.payload.baseHeight ?: 0
                if (baseWidth <= 1 && baseHeight <= 1) return null
                val imageUrl = cloudLayer.resolvedImageUrl()?.replaceLocalhostWithConfiguredHost() ?: return null
                EditorLayer(
                    id = layerId,
                    type = LayerType.IMAGE,
                    product = EditorProduct(
                        originalUriString = imageUrl,
                        foregroundUriString = imageUrl,
                        isBackgroundRemoved = true,
                        baseWidth = baseWidth,
                        baseHeight = baseHeight,
                        isSample = cloudLayer.isReplaceableLayer(),
                    ),
                    shapeWidthPx = baseWidth.toFloat(),
                    shapeHeightPx = baseHeight.toFloat(),
                    viewport = viewport.copy(
                        flippedH = cloudLayer.payload.flippedH ?: false,
                        flippedV = cloudLayer.payload.flippedV ?: false,
                    ),
                    appearance = appearance,
                    cropRatio = cloudLayer.resolvedCropRatio(),
                    isLocked = locked,
                    isVisible = isVisible,
                    blendMode = cloudLayer.payload.blendMode,
                    strokeColorArgb = EditorStrokeMapper.parseStrokeColor(cloudLayer.payload.stroke),
                    strokeWidthPx = cloudLayer.payload.strokeWidth ?: 0f,
                    strokeDashArray = cloudLayer.payload.strokeDashArray ?: emptyList(),
                    strokeDashGapPx = EditorStrokeMapper.extractDashGap(
                        cloudLayer.payload.strokeDashArray ?: emptyList(),
                    ),
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
        val textSizeSp = (payload.fontSize ?: 60f).coerceIn(1f, ShapeLabelDefaults.MAX_TEXT_SIZE_SP)
        val textSizePx = textSizeSp * density
        val charSpacingPx = (payload.charSpacing ?: 0f) * textSizeSp / 1000f
        val displayText = EditorTextStyleMapper.applyTextTransform(
            payload.text.orEmpty(),
            payload.textTransform,
        )

        val shapeType = mapCloudShapeType(
            raw = payload.shapeType,
            plainText = payload.shapeType.isNullOrBlank() &&
                payload.fillGradient == null &&
                payload.resolvedShapeFillArgb() == null,
        )
        val isTextOnly = EditorShapeGeometry.isTextOnlyShape(shapeType)
        val isLine = shapeType == ShapeType.LINE
        val layerType = when {
            displayText.isBlank() && !isTextOnly -> LayerType.SHAPE
            isTextOnly -> LayerType.TEXT
            displayText.isNotBlank() && !isTextOnly -> LayerType.SHAPE_TEXT
            else -> LayerType.TEXT
        }
        val lineStrokeArgb = payload.fillColor?.let(ColorArgbParser::parseOrNull)
            ?: payload.resolvedShapeFillArgb()
            ?: 0xFF6366F1.toInt()

        return EditorLayer(
            id = layerId,
            type = layerType,
            text = displayText,
            textColorArgb = payload.resolvedTextColorArgb() ?: 0xFFFFFFFF.toInt(),
            textSizeSp = (payload.fontSize ?: 60f).coerceIn(1f, ShapeLabelDefaults.MAX_TEXT_SIZE_SP),
            shapeType = shapeType,
            shapeColorArgb = if (isLine) {
                0x00FFFFFF
            } else {
                payload.resolvedShapeFillArgb() ?: 0x00FFFFFF.toInt()
            },
            shapeWidthPx = ((payload.baseWidth?.toFloat() ?: 350f) / cloudLayer.transform.scale.coerceAtLeast(0.01f)).coerceAtLeast(1f),
            shapeHeightPx = ((payload.baseHeight?.toFloat() ?: 140f) / cloudLayer.transform.scale.coerceAtLeast(0.01f)).coerceAtLeast(1f),
            fontFamily = payload.font,
            fontWeight = payload.fontWeight,
            fontStyle = payload.fontStyle,
            textAlign = payload.textAlign,
            underline = payload.underline == true,
            linethrough = payload.linethrough == true,
            lineHeight = payload.lineHeight,
            charSpacing = charSpacingPx,
            textBackgroundColorArgb = payload.textBackgroundColor?.let(ColorArgbParser::parseOrNull),
            textTransform = payload.textTransform,
            textForm = resolveTextForm(payload),
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
            strokeDashGapPx = EditorStrokeMapper.extractDashGap(payload.strokeDashArray ?: emptyList()),
            fillGradient = payload.fillGradient,
            textColorGradient = payload.textColorGradient,
            pathData = payload.pathData,
            polygonPoints = payload.polygonPoints ?: emptyList(),
            viewport = viewport.copy(
                flippedH = payload.flippedH == true,
                flippedV = payload.flippedV == true,
            ),
            appearance = appearance,
            isLocked = locked,
            isVisible = cloudLayer.isVisible(),
        )
    }

    private fun isTextLayer(cloudLayer: CloudLayer): Boolean {
        if (cloudLayer.type.equals("TEXT", ignoreCase = true) ||
            cloudLayer.type.equals("SHAPE_TEXT", ignoreCase = true)
        ) {
            return true
        }
        if (cloudLayer.payload.sourceKind.equals("psd-text", ignoreCase = true)) return true
        if (cloudLayer.type.equals("DECORATION", ignoreCase = true) &&
            cloudLayer.resolvedImageUrl() == null &&
            !cloudLayer.payload.text.isNullOrBlank()
        ) {
            return true
        }
        return !cloudLayer.payload.text.isNullOrBlank() && cloudLayer.resolvedImageUrl() == null
    }

    private fun resolveAppearance(cloudLayer: CloudLayer): EditorAppearance {
        val shadowIntensity = cloudLayer.payload.shadowIntensity ?: 0f
        // Only strip shadow when PSD export baked layer styles into the raster (composite(true)).
        val bakedIntoRaster = cloudLayer.resolvedImageUrl() != null &&
            cloudLayer.payload.sourceKind.equals("psd-rasterized", ignoreCase = true) &&
            shadowIntensity > 0f
        val bakedInGradient = cloudLayer.hasGradientBakedShadow()
        return EditorAppearance(
            shadowIntensity = if (bakedIntoRaster || bakedInGradient) 0f else shadowIntensity,
            alpha = cloudLayer.payload.alpha ?: 1f,
            shadowAngle = cloudLayer.payload.shadowAngle ?: 45f,
            shadowDistance = cloudLayer.payload.shadowDistance ?: 12f,
            shadowColorArgb = cloudLayer.payload.shadowColorArgb?.let { argb ->
                // Opacity is carried by shadowIntensity; keep RGB opaque for stable tinting.
                (argb and 0x00FFFFFF) or 0xFF000000.toInt()
            } ?: 0xFF000000.toInt(),
            shadowBlur = cloudLayer.payload.shadowBlur,
        )
    }

    private fun CloudLayer.isDecorationShapeLayer(): Boolean {
        return type.equals("DECORATION", ignoreCase = true) &&
            !isShadowRegionLayer() &&
            resolvedImageUrl() == null &&
            (payload.shapeType != null ||
                payload.resolvedShapeFillArgb() != null ||
                payload.fillGradient != null)
    }

    private fun CloudLayer.resolvedCropRatio(): CropRatio {
        return payload.cropRatio
            ?.let { value -> runCatching { CropRatio.valueOf(value) }.getOrNull() }
            ?: CropRatio.ORIGINAL
    }

    private fun resolveTextForm(payload: com.thgiang.image.core.domain.model.template.CloudPayload): TextFormEffect {
        val preset = TextFormPreset.fromId(payload.textFormPreset)
        if (preset == TextFormPreset.NONE) return TextFormEffect()
        val category = when (payload.textFormCategory?.lowercase()) {
            "follow_path", "path" -> TextFormCategory.FOLLOW_PATH
            "warp" -> TextFormCategory.WARP
            else -> preset.category
        }
        return TextFormEffect(
            category = category,
            preset = preset,
            amount = payload.textFormAmount?.coerceIn(0f, TextFormEffect.MAX_AMOUNT) ?: 0.55f,
            reversePath = payload.textFormReversePath == true,
        )
    }

    private fun mapCloudShapeType(raw: String?, plainText: Boolean = false): ShapeType {
        if (raw.isNullOrBlank()) {
            return if (plainText) ShapeType.TEXT_ONLY else ShapeType.PILL
        }
        return when (raw.lowercase()) {
            "text-only", "text_only", "plain-text", "plaintext" -> ShapeType.TEXT_ONLY
            "circle", "ellipse" -> ShapeType.CIRCLE
            "star" -> ShapeType.STAR
            "hexagon" -> ShapeType.HEXAGON
            "triangle" -> ShapeType.TRIANGLE
            "line" -> ShapeType.LINE
            "diamond" -> ShapeType.DIAMOND
            "arrow" -> ShapeType.ARROW
            "path" -> ShapeType.PATH
            "polygon" -> ShapeType.POLYGON
            "parallelogram" -> ShapeType.PARALLELOGRAM
            "card", "rounded-rect", "rect" -> ShapeType.CARD
            "teardrop", "heart" -> ShapeType.TEARDROP
            "pill" -> ShapeType.PILL
            else -> ShapeType.PILL
        }
    }
}
