package com.thgiang.image.studio.model

import androidx.compose.ui.graphics.Color
import com.thgiang.image.studio.R

data class StudioThemeplate(
    val id: String,
    val titleResId: Int,
    val assetPath: String,
    val accentColor: Color
)

object StudioThemeplates {
    val all = listOf(
        StudioThemeplate(
            id = "cosmetics_01",
            titleResId = R.string.themeplate_cosmetics_01,
            assetPath = "anh_my_pham/anh_mypham.jpeg",
            accentColor = Color(0xFF7C4DFF)
        ),
        StudioThemeplate(
            id = "cosmetics_02",
            titleResId = R.string.themeplate_cosmetics_02,
            assetPath = "anh_my_pham/my_pham_1 (1).png",
            accentColor = Color(0xFFE91E63)
        ),
        StudioThemeplate(
            id = "cosmetics_03",
            titleResId = R.string.themeplate_cosmetics_03,
            assetPath = "anh_my_pham/my_pham_1 (2).png",
            accentColor = Color(0xFF009688)
        ),
        StudioThemeplate(
            id = "cosmetics_04",
            titleResId = R.string.themeplate_cosmetics_04,
            assetPath = "anh_my_pham/my_pham_1 (3).png",
            accentColor = Color(0xFFFF9800)
        ),
        StudioThemeplate(
            id = "cosmetics_05",
            titleResId = R.string.themeplate_cosmetics_05,
            assetPath = "anh_my_pham/my_pham_1 (4).png",
            accentColor = Color(0xFF4CAF50)
        ),
        StudioThemeplate(
            id = "cosmetics_06",
            titleResId = R.string.themeplate_cosmetics_06,
            assetPath = "anh_my_pham/my_pham_1 (5).png",
            accentColor = Color(0xFF2196F3)
        ),
        StudioThemeplate(
            id = "cosmetics_07",
            titleResId = R.string.themeplate_cosmetics_07,
            assetPath = "anh_my_pham/my_pham_1 (6).png",
            accentColor = Color(0xFF9C27B0)
        ),
        StudioThemeplate(
            id = "cosmetics_08",
            titleResId = R.string.themeplate_cosmetics_08,
            assetPath = "anh_my_pham/my_pham_1 (7).png",
            accentColor = Color(0xFFFFC107)
        ),
        StudioThemeplate(
            id = "cosmetics_09",
            titleResId = R.string.themeplate_cosmetics_09,
            assetPath = "anh_my_pham/my_pham_1 (8).png",
            accentColor = Color(0xFF795548)
        )
    )

    fun findById(id: String): StudioThemeplate? = all.find { it.id == id }
}
