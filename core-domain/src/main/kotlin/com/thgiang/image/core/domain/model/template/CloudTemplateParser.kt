package com.thgiang.image.core.domain.model.template

import org.json.JSONArray
import org.json.JSONObject

/**
 * Result of safely parsing a template API item. Invalid templates carry the failure
 * reason so callers can filter them out of lists and log/report — instead of crashing
 * or silently rendering an empty template.
 */
sealed interface ParseOutcome {
    data class Success(val template: CloudTemplate) : ParseOutcome
    data class Invalid(val reason: String, val templateId: String?) : ParseOutcome
}

/**
 * Single source of truth for parsing [CloudTemplate] JSON from admin_web public API.
 */
object CloudTemplateParser {
    const val SUPPORTED_SCHEMA_VERSION = 1

    /**
     * Never throws. Prefer this over [parseFromApiItem] — any malformed input
     * (missing canvas_data, JSON of the wrong shape, etc.) yields [ParseOutcome.Invalid].
     */
    fun parseFromApiItemSafe(item: JSONObject): ParseOutcome {
        val templateId = item.optNonBlankString("template_id") ?: item.optNonBlankString("id")
        item.optJSONObject("canvas_data")
            ?: return ParseOutcome.Invalid("missing canvas_data", templateId)
        return runCatching { parseFromApiItem(item) }
            .fold(
                onSuccess = { ParseOutcome.Success(it) },
                onFailure = { ParseOutcome.Invalid(it.message ?: "parse error", templateId) },
            )
    }

    fun parseFromApiItem(item: JSONObject): CloudTemplate {
        val canvasData = item.optJSONObject("canvas_data")
            ?: throw IllegalArgumentException("Template missing canvas_data")
        return parse(
            templateId = item.optString("template_id").ifBlank { item.optString("id") },
            categoryId = item.optString("category_id"),
            canvasData = canvasData,
            fallbackTitle = item.optString("title"),
            fallbackThumbnailUrl = item.optString("thumbnail_url"),
            fallbackStatus = item.optString("status"),
        )
    }

    fun parse(
        templateId: String,
        categoryId: String,
        canvasData: JSONObject,
        fallbackTitle: String = "",
        fallbackThumbnailUrl: String = "",
        fallbackStatus: String = "published",
    ): CloudTemplate {
        val metadataJson = canvasData.optJSONObject("metadata")
        val canvasJson = canvasData.optJSONObject("canvas")

        return CloudTemplate(
            templateId = canvasData.optNonBlankString("templateId") ?: templateId,
            categoryId = canvasData.optNonBlankString("categoryId") ?: categoryId,
            metadata = TemplateMetadata(
                title = metadataJson?.optNonBlankString("title") ?: fallbackTitle,
                thumbnailUrl = metadataJson?.optNonBlankString("thumbnailUrl") ?: fallbackThumbnailUrl,
                status = metadataJson?.optNonBlankString("status") ?: fallbackStatus,
                environment = metadataJson?.optNonBlankString("environment"),
                schemaVersion = metadataJson?.optInt("schemaVersion", SUPPORTED_SCHEMA_VERSION)
                    ?: SUPPORTED_SCHEMA_VERSION,
                createdAt = metadataJson?.optLong("createdAt", System.currentTimeMillis())
                    ?: System.currentTimeMillis(),
                updatedAt = metadataJson?.optLong("updatedAt", System.currentTimeMillis())
                    ?: System.currentTimeMillis(),
            ),
            canvas = TemplateCanvas(
                baseWidth = canvasJson?.optInt("baseWidth", 1080) ?: 1080,
                baseHeight = canvasJson?.optInt("baseHeight", 1920) ?: 1920,
                aspectRatio = canvasJson?.optNonBlankString("aspectRatio") ?: "9:16",
                backgroundUrl = canvasJson?.optNonBlankString("backgroundUrl"),
                backgroundColorArgb = canvasJson?.optArgbIntOrNull("backgroundColorArgb"),
            ),
            layers = parseLayers(canvasData.optJSONArray("layers")),
        )
    }

    fun parseLayers(layersArray: JSONArray?): List<CloudLayer> {
        if (layersArray == null) return emptyList()

        return buildList {
            for (index in 0 until layersArray.length()) {
                val item = layersArray.optJSONObject(index) ?: continue
                val transformJson = item.optJSONObject("transform")
                val payloadJson = item.optJSONObject("payload")

                add(
                    CloudLayer(
                        layerId = item.optNonBlankString("layerId") ?: "",
                        type = item.optNonBlankString("type") ?: "IMAGE",
                        zIndex = item.optInt("zIndex", index),
                        transform = CloudTransform(
                            anchorX = transformJson?.optDouble("anchorX", 0.5)?.toFloat() ?: 0.5f,
                            anchorY = transformJson?.optDouble("anchorY", 0.5)?.toFloat() ?: 0.5f,
                            scale = transformJson?.optDouble("scale", 1.0)?.toFloat() ?: 1.0f,
                            rotation = transformJson?.optDouble("rotation", 0.0)?.toFloat() ?: 0.0f,
                        ),
                        payload = parsePayload(payloadJson),
                    )
                )
            }
        }
    }

    private fun parsePayload(payloadJson: JSONObject?): CloudPayload {
        if (payloadJson == null) return CloudPayload()

        return CloudPayload(
            defaultImageUrl = payloadJson.optNonBlankString("defaultImageUrl"),
            imageUrl = payloadJson.optNonBlankString("imageUrl"),
            visible = payloadJson.optBooleanOrNull("visible"),
            locked = payloadJson.optBooleanOrNull("locked"),
            groupPath = payloadJson.optNonBlankString("groupPath"),
            sourceKind = payloadJson.optNonBlankString("sourceKind"),
            shadowIntensity = payloadJson.optFloatOrNull("shadowIntensity"),
            shadowAngle = payloadJson.optFloatOrNull("shadowAngle"),
            shadowDistance = payloadJson.optFloatOrNull("shadowDistance"),
            shadowBlur = payloadJson.optFloatOrNull("shadowBlur"),
            alpha = payloadJson.optFloatOrNull("alpha"),
            shadowColorArgb = payloadJson.optArgbIntOrNull("shadowColorArgb"),
            cropRatio = payloadJson.optNonBlankString("cropRatio"),
            flippedH = payloadJson.optBooleanOrNull("flippedH"),
            flippedV = payloadJson.optBooleanOrNull("flippedV"),
            baseWidth = payloadJson.optIntOrNull("baseWidth"),
            baseHeight = payloadJson.optIntOrNull("baseHeight"),
            text = payloadJson.optNonBlankString("text"),
            font = payloadJson.optNonBlankString("font"),
            textColorArgb = payloadJson.optArgbIntOrNull("textColorArgb"),
            fontSize = payloadJson.optFloatOrNull("fontSize"),
            fill = payloadJson.optNonBlankString("fill"),
            fontWeight = payloadJson.optFontWeightOrNull(),
            fontStyle = payloadJson.optNonBlankString("fontStyle"),
            textAlign = payloadJson.optNonBlankString("textAlign"),
            underline = payloadJson.optBooleanOrNull("underline"),
            lineHeight = payloadJson.optFloatOrNull("lineHeight"),
            charSpacing = payloadJson.optFloatOrNull("charSpacing"),
            textBackgroundColor = payloadJson.optNonBlankString("textBackgroundColor"),
            linethrough = payloadJson.optBooleanOrNull("linethrough"),
            textTransform = payloadJson.optNonBlankString("textTransform"),
            shapeType = payloadJson.optNonBlankString("shapeType"),
            fillColor = payloadJson.optNonBlankString("fillColor"),
            rx = payloadJson.optFloatOrNull("rx"),
            ry = payloadJson.optFloatOrNull("ry"),
            blendMode = payloadJson.optNonBlankString("blendMode"),
            stroke = payloadJson.optNonBlankString("stroke"),
            strokeWidth = payloadJson.optFloatOrNull("strokeWidth"),
            strokeDashArray = payloadJson.optFloatListOrNull("strokeDashArray"),
            fillGradient = parseGradient(payloadJson.optJSONObject("fillGradient")),
            textColorGradient = parseGradient(payloadJson.optJSONObject("textColorGradient")),
            pathData = payloadJson.optNonBlankString("pathData"),
            polygonPoints = payloadJson.optFloatListOrNull("polygonPoints"),
            replaceable = payloadJson.optBooleanOrNull("replaceable"),
        )
    }

    private fun parseGradient(gradientJson: JSONObject?): CloudGradient? {
        if (gradientJson == null) return null
        val type = gradientJson.optNonBlankString("type") ?: return null
        val stopsArray = gradientJson.optJSONArray("colorStops") ?: return null
        val colorStops = buildList {
            for (index in 0 until stopsArray.length()) {
                val stopJson = stopsArray.optJSONObject(index) ?: continue
                val color = stopJson.optNonBlankString("color") ?: continue
                add(
                    CloudGradientStop(
                        offset = stopJson.optDouble("offset", 0.0).toFloat(),
                        color = color,
                    )
                )
            }
        }
        if (colorStops.isEmpty()) return null

        val coordsJson = gradientJson.optJSONObject("coords")
        return CloudGradient(
            type = type,
            colorStops = colorStops,
            coords = CloudGradientCoords(
                x1 = coordsJson?.optDouble("x1", 0.0)?.toFloat() ?: 0f,
                y1 = coordsJson?.optDouble("y1", 0.0)?.toFloat() ?: 0f,
                x2 = coordsJson?.optDouble("x2", 1.0)?.toFloat() ?: 1f,
                y2 = coordsJson?.optDouble("y2", 0.0)?.toFloat() ?: 0f,
                r1 = coordsJson?.optFloatOrNull("r1"),
                r2 = coordsJson?.optFloatOrNull("r2"),
            ),
        )
    }

    private fun JSONObject.optNonBlankString(key: String): String? {
        return optString(key)
            .takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
    }

    private fun JSONObject.optFloatOrNull(key: String): Float? {
        if (!has(key) || isNull(key)) return null
        return optDouble(key).toFloat().takeUnless { it.isNaN() }
    }

    private fun JSONObject.optIntOrNull(key: String): Int? {
        if (!has(key) || isNull(key)) return null
        return optInt(key)
    }

    /**
     * Parse ARGB packed ints safely.
     * JS may emit unsigned values (> Int.MAX_VALUE) for opaque colors; [optInt] via Double
     * clamps those to Int.MAX_VALUE and corrupts the color. Prefer Long.intValue() wrap.
     */
    private fun JSONObject.optArgbIntOrNull(key: String): Int? {
        if (!has(key) || isNull(key)) return null
        return when (val raw = opt(key)) {
            is Number -> raw.toLong().toInt()
            else -> null
        }
    }

    private fun JSONObject.optBooleanOrNull(key: String): Boolean? {
        if (!has(key) || isNull(key)) return null
        return optBoolean(key)
    }

    private fun JSONObject.optFontWeightOrNull(): String? {
        if (!has("fontWeight") || isNull("fontWeight")) return null
        return when (val raw = get("fontWeight")) {
            is Number -> raw.toInt().toString()
            else -> optNonBlankString("fontWeight")
        }
    }

    private fun JSONObject.optFloatListOrNull(key: String): List<Float>? {
        if (!has(key) || isNull(key)) return null
        val array = optJSONArray(key) ?: return null
        return buildList {
            for (index in 0 until array.length()) {
                add(array.optDouble(index).toFloat())
            }
        }.takeIf { it.isNotEmpty() }
    }
}
