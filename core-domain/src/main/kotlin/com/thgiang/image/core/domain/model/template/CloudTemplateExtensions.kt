package com.thgiang.image.core.domain.model.template

fun CloudLayer.resolvedImageUrl(): String? {
    return payload.imageUrl?.takeIf { it.isNotBlank() }
        ?: payload.defaultImageUrl?.takeIf { it.isNotBlank() }
}

fun CloudLayer.isShadowRegionLayer(): Boolean {
    if (!payload.text.isNullOrBlank()) return false
    if (!resolvedImageUrl().isNullOrBlank()) return false
    return payload.sourceKind.equals("shadow-region", ignoreCase = true) ||
        type.equals("SHADOW_REGION", ignoreCase = true)
}

/**
 * Shape layers whose soft shadow is painted via [CloudPayload.fillGradient] (e.g. PSD ground
 * shadow ellipses). Drop-shadow params must not be applied on top of the gradient fill.
 */
fun CloudLayer.hasGradientBakedShadow(): Boolean {
    if (isShadowRegionLayer()) return false
    if (!payload.text.isNullOrBlank()) return false
    if (!resolvedImageUrl().isNullOrBlank()) return false
    val gradient = payload.fillGradient ?: return false
    if (!gradient.type.equals("radial", ignoreCase = true)) return false
    return gradient.colorStops.any { stop ->
        val argb = ColorArgbParser.parseOrNull(stop.color) ?: return@any false
        ((argb ushr 24) and 0xFF) < 13
    }
}

fun CloudLayer.isVisible(): Boolean = payload.visible != false

fun CloudLayer.isLocked(): Boolean = payload.locked == true

fun CloudLayer.isReplaceableLayer(): Boolean {
    return type.equals("PLACEHOLDER_OBJECT", ignoreCase = true) ||
        payload.replaceable == true
}

fun CloudPayload.resolvedTextColorArgb(): Int? {
    return textColorArgb ?: fill?.let(ColorArgbParser::parseOrNull)
        ?: fillColor?.let(ColorArgbParser::parseOrNull)
}

fun CloudPayload.resolvedShapeFillArgb(): Int? {
    return fillColor?.let(ColorArgbParser::parseOrNull)
        ?: fill?.let(ColorArgbParser::parseOrNull)
}
