package com.thgiang.image.core.domain.usecase

import android.graphics.Color
import com.thgiang.image.core.model.BorderPresetStyle
import com.thgiang.image.core.model.BorderRenderMode

object BorderStyleMapper {
    fun map(style: BorderPresetStyle): Pair<BorderRenderMode, Int> = when (style) {
        BorderPresetStyle.SOLID -> BorderRenderMode.Solid(
            Color.parseColor("#0F1117")
        ) to 14
        BorderPresetStyle.WHITE -> BorderRenderMode.Solid(
            Color.parseColor("#FFFFFF")
        ) to 14
        BorderPresetStyle.GRADIENT -> BorderRenderMode.Gradient(
            listOf(
                Color.parseColor("#B98744"),
                Color.parseColor("#E9CF9A"),
                Color.parseColor("#C8A46A"),
                Color.parseColor("#F5E0A8"),
                Color.parseColor("#B98744")
            )
        ) to 16
        BorderPresetStyle.NEON -> BorderRenderMode.Gradient(
            listOf(
                Color.parseColor("#08111E"),
                Color.parseColor("#57E4FF"),
                Color.parseColor("#AFFFFF"),
                Color.parseColor("#57E4FF"),
                Color.parseColor("#08111E")
            )
        ) to 10
    }
}
