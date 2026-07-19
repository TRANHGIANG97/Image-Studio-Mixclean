package com.thgiang.image.studio.model

import androidx.compose.ui.graphics.Color
import com.thgiang.image.studio.R
import com.thgiang.image.studio.data.RemoteTemplateRow

fun RemoteTemplateRow.toStudioThemeplate(): StudioThemeplate {
    val canvas = cloudTemplate.canvas
    val backgroundUrl = canvas.backgroundUrl?.takeIf { it.isNotBlank() }
    val objectSourceAssetPath = cloudTemplate.layers
        .firstOrNull { it.type.equals("PLACEHOLDER_OBJECT", ignoreCase = true) }
        ?.payload
        ?.let { payload -> payload.imageUrl ?: payload.defaultImageUrl }

    return StudioThemeplate(
        id = id,
        titleResId = R.string.themeplate_professional_watch,
        assetPath = backgroundUrl ?: thumbnailUrl,
        backgroundAssetPath = thumbnailUrl,
        objectSourceAssetPath = objectSourceAssetPath,
        accentColor = Color(0xFF7C4DFF),
        category = categoryId,
        titleString = title,
        isPremium = isPremium,
        canvasWidth = canvas.baseWidth,
        canvasHeight = canvas.baseHeight,
    )
}
