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
