package com.thgiang.image.studio.model

import androidx.compose.ui.graphics.Color
import com.thgiang.image.studio.R

data class StudioThemeplate(
    val id: String,
    val titleResId: Int,
    val assetPath: String,
    val backgroundAssetPath: String? = null,
    val objectSourceAssetPath: String? = null,
    val accentColor: Color,
    val category: String = "cosmetics",
    val titleString: String? = null
)

data class StudioThemeplateSection(
    val id: String,
    val titleResId: Int,
    val themeplates: List<StudioThemeplate>
)

object StudioThemeplates {

    private val cosmeticsList = emptyList<StudioThemeplate>()
    private val professionalList = emptyList<StudioThemeplate>()
    private val digitalLifeList = emptyList<StudioThemeplate>()
    private val selfieFoodTemplateList = emptyList<StudioThemeplate>()
    private val professionalSectionList = emptyList<StudioThemeplateSection>()

    private val professionalFlatList: List<StudioThemeplate> = emptyList()

    val all: List<StudioThemeplate> = emptyList()
    val cosmetics: List<StudioThemeplate> get() = emptyList()
    val professional: List<StudioThemeplate> get() = emptyList()
    val professionalSections: List<StudioThemeplateSection> get() = emptyList()

    fun findById(id: String): StudioThemeplate? = null
}



