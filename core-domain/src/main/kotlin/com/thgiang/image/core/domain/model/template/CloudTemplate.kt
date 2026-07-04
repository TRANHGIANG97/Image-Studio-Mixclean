package com.thgiang.image.core.domain.model.template



data class CloudTemplate(
    val templateId: String = "",
    val categoryId: String = "",
    val metadata: TemplateMetadata = TemplateMetadata(),
    val canvas: TemplateCanvas = TemplateCanvas(),
    val layers: List<CloudLayer> = emptyList()
) : java.io.Serializable

data class TemplateMetadata(
    val title: String = "",
    val thumbnailUrl: String = "",
    val status: String = "draft", // "draft" or "published"
    val environment: String? = null, // "debug" | "release" | "all"
    val schemaVersion: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) : java.io.Serializable

data class TemplateCanvas(
    val baseWidth: Int = 1080,
    val baseHeight: Int = 1920,
    val aspectRatio: String = "9:16",
    val backgroundUrl: String? = null,
    val backgroundColorArgb: Int? = null
) : java.io.Serializable

data class CloudLayer(
    val layerId: String = "",
    val type: String = "IMAGE", // e.g. "IMAGE", "DECORATION", "TEXT"
    val zIndex: Int = 0,
    val transform: CloudTransform = CloudTransform(),
    val payload: CloudPayload = CloudPayload()
) : java.io.Serializable

data class CloudTransform(
    val anchorX: Float = 0.5f,
    val anchorY: Float = 0.5f,
    val scale: Float = 1.0f,
    val rotation: Float = 0.0f
) : java.io.Serializable

/** Fabric.js gradient stop — offset 0..1, color as CSS hex/rgba string */
data class CloudGradientStop(
    val offset: Float = 0f,
    val color: String = "#000000",
) : java.io.Serializable

/** Fabric gradient coords in percentage units (0..1) relative to layer bounds */
data class CloudGradientCoords(
    val x1: Float = 0f,
    val y1: Float = 0f,
    val x2: Float = 1f,
    val y2: Float = 0f,
    val r1: Float? = null,
    val r2: Float? = null,
) : java.io.Serializable

data class CloudGradient(
    val type: String = "linear",
    val colorStops: List<CloudGradientStop> = emptyList(),
    val coords: CloudGradientCoords = CloudGradientCoords(),
) : java.io.Serializable

data class CloudPayload(
    val defaultImageUrl: String? = null,
    val imageUrl: String? = null,
    val visible: Boolean? = null,
    val locked: Boolean? = null,
    val groupPath: String? = null,
    val sourceKind: String? = null,
    val shadowIntensity: Float? = null,
    val shadowAngle: Float? = null,
    val shadowDistance: Float? = null,
    val shadowBlur: Float? = null,
    val alpha: Float? = null,
    val shadowColorArgb: Int? = null,
    val cropRatio: String? = null,
    val flippedH: Boolean? = null,
    val flippedV: Boolean? = null,
    val baseWidth: Int? = null,
    val baseHeight: Int? = null,
    val text: String? = null,
    val font: String? = null,
    val textColorArgb: Int? = null,
    val fontSize: Float? = null,
    val fill: String? = null,
    val fontWeight: String? = null,
    val fontStyle: String? = null,
    val textAlign: String? = null,
    val underline: Boolean? = null,
    val lineHeight: Float? = null,
    val charSpacing: Float? = null,
    val textBackgroundColor: String? = null,
    val linethrough: Boolean? = null,
    val textTransform: String? = null,
    val shapeType: String? = null,
    val fillColor: String? = null,
    val rx: Float? = null,
    val ry: Float? = null,
    val blendMode: String? = null,
    val stroke: String? = null,
    val strokeWidth: Float? = null,
    val strokeDashArray: List<Float>? = null,
    val fillGradient: CloudGradient? = null,
    val textColorGradient: CloudGradient? = null,
    /** SVG path commands for Fabric `path` / `arrow` shapes */
    val pathData: String? = null,
    /** Flat x,y pairs in shape local space for Fabric `polygon` */
    val polygonPoints: List<Float>? = null,
    /** When true, user can replace this image in studio_edit (Android). */
    val replaceable: Boolean? = null,
    /** Text path / warp (Word Transform). */
    val textFormCategory: String? = null,
    val textFormPreset: String? = null,
    val textFormAmount: Float? = null,
    val textFormReversePath: Boolean? = null,
) : java.io.Serializable
