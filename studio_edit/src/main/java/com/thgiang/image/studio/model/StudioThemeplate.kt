package com.thgiang.image.studio.model

import androidx.compose.ui.graphics.Color
import com.thgiang.image.studio.R

/** Lightweight UI model for template cards; cloud templates use [id] as template_id from API. */
data class StudioThemeplate(
    val id: String,
    val titleResId: Int,
    val assetPath: String,
    val backgroundAssetPath: String? = null,
    val objectSourceAssetPath: String? = null,
    val accentColor: Color,
    val category: String = "cosmetics",
    val titleString: String? = null,
    val isPremium: Boolean = false,
    val canvasWidth: Int = 0,
    val canvasHeight: Int = 0,
)

data class StudioThemeplateSection(
    val id: String,
    val titleResId: Int,
    val themeplates: List<StudioThemeplate>,
    val titleString: String? = null
)

/** Legacy registry — bundled templates removed; gallery loads from cloud API. */
object StudioThemeplates {
    /** Nav argument for free-form design (no cloud template). */
    const val BLANK_THEMEPLATE_ID = "blank"
    const val BLANK_CANVAS_SIZE = 1080

    val all: List<StudioThemeplate> = emptyList()
    val cosmetics: List<StudioThemeplate> = emptyList()
    val professional: List<StudioThemeplate> = emptyList()
    val professionalSections: List<StudioThemeplateSection> = emptyList()

    fun findById(id: String): StudioThemeplate? = null
}
