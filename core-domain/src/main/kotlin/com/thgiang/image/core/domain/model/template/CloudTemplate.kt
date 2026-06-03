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
    val schemaVersion: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) : java.io.Serializable

data class TemplateCanvas(
    val baseWidth: Int = 1080,
    val baseHeight: Int = 1920,
    val aspectRatio: String = "9:16",
    val backgroundUrl: String? = null
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

data class CloudPayload(
    val defaultImageUrl: String? = null,
    val imageUrl: String? = null,
    val shadowIntensity: Float? = null,
    val shadowAngle: Float? = null,
    val shadowDistance: Float? = null,
    val alpha: Float? = null,
    val shadowColorArgb: Int? = null,
    val cropRatio: String? = null,
    val flippedH: Boolean? = null,
    val flippedV: Boolean? = null,
    val baseWidth: Int? = null,
    val baseHeight: Int? = null,
    val text: String? = null,
    val font: String? = null
) : java.io.Serializable
