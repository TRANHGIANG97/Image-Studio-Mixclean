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
    val category: String = "cosmetics"
)

data class StudioThemeplateSection(
    val id: String,
    val titleResId: Int,
    val themeplates: List<StudioThemeplate>
)

object StudioThemeplates {

    private val cosmeticsList = listOf(
        StudioThemeplate(
            id = "cosmetics_01",
            titleResId = R.string.themeplate_cosmetics_01,
            assetPath = "anh_my_pham/anh_mypham.jpeg",
            accentColor = Color(0xFF7C4DFF),
            category = "cosmetics"
        ),
        StudioThemeplate(
            id = "cosmetics_02",
            titleResId = R.string.themeplate_cosmetics_02,
            assetPath = "anh_my_pham/my_pham_1 (1).jpg",
            accentColor = Color(0xFFE91E63),
            category = "cosmetics"
        ),
        StudioThemeplate(
            id = "cosmetics_03",
            titleResId = R.string.themeplate_cosmetics_03,
            assetPath = "anh_my_pham/my_pham_1 (2).jpg",
            accentColor = Color(0xFF009688),
            category = "cosmetics"
        ),
        StudioThemeplate(
            id = "cosmetics_04",
            titleResId = R.string.themeplate_cosmetics_04,
            assetPath = "anh_my_pham/my_pham_1 (3).jpg",
            accentColor = Color(0xFFFF9800),
            category = "cosmetics"
        ),
        StudioThemeplate(
            id = "cosmetics_05",
            titleResId = R.string.themeplate_cosmetics_05,
            assetPath = "anh_my_pham/my_pham_1 (4).jpg",
            accentColor = Color(0xFF4CAF50),
            category = "cosmetics"
        ),
        StudioThemeplate(
            id = "cosmetics_06",
            titleResId = R.string.themeplate_cosmetics_06,
            assetPath = "anh_my_pham/my_pham_1 (5).jpg",
            accentColor = Color(0xFF2196F3),
            category = "cosmetics"
        ),
        StudioThemeplate(
            id = "cosmetics_07",
            titleResId = R.string.themeplate_cosmetics_07,
            assetPath = "anh_my_pham/my_pham_1 (6).jpg",
            accentColor = Color(0xFF9C27B0),
            category = "cosmetics"
        ),
        StudioThemeplate(
            id = "cosmetics_08",
            titleResId = R.string.themeplate_cosmetics_08,
            assetPath = "anh_my_pham/my_pham_1 (7).jpg",
            accentColor = Color(0xFFFFC107),
            category = "cosmetics"
        ),
        StudioThemeplate(
            id = "cosmetics_09",
            titleResId = R.string.themeplate_cosmetics_09,
            assetPath = "anh_my_pham/my_pham_1 (8).jpg",
            accentColor = Color(0xFF795548),
            category = "cosmetics"
        )
    )

    private val professionalList = listOf(
        StudioThemeplate(
            id = "professional_watch",
            titleResId = R.string.themeplate_professional_watch,
            assetPath = "anh_chuyen_nghiep/watch_bg.jpg",
            objectSourceAssetPath = "anh_chuyen_nghiep/watch_obj_src.webp",
            backgroundAssetPath = null,
            accentColor = Color(0xFF795548),
            category = "professional"
        ),
        StudioThemeplate(
            id = "professional_perfume",
            titleResId = R.string.themeplate_professional_perfume,
            assetPath = "anh_chuyen_nghiep/perfume_bg.jpg",
            objectSourceAssetPath = "anh_chuyen_nghiep/perfume_obj_src.webp",
            backgroundAssetPath = null,
            accentColor = Color(0xFFE91E63),
            category = "professional"
        ),
        StudioThemeplate(
            id = "professional_handbag",
            titleResId = R.string.themeplate_professional_handbag,
            assetPath = "anh_chuyen_nghiep/handbag_bg.jpg",
            objectSourceAssetPath = "anh_chuyen_nghiep/handbag_obj_src.webp",
            backgroundAssetPath = null,
            accentColor = Color(0xFF212121),
            category = "professional"
        ),
        StudioThemeplate(
            id = "professional_sneaker",
            titleResId = R.string.themeplate_professional_sneaker,
            assetPath = "anh_chuyen_nghiep/sneaker_bg.jpg",
            objectSourceAssetPath = "anh_chuyen_nghiep/sneaker_obj_src.webp",
            backgroundAssetPath = null,
            accentColor = Color(0xFFE0F7FA),
            category = "professional"
        ),
        StudioThemeplate(
            id = "professional_glasses",
            titleResId = R.string.themeplate_professional_glasses,
            assetPath = "anh_chuyen_nghiep/glasses_bg.jpg",
            objectSourceAssetPath = "anh_chuyen_nghiep/glasses_obj_src.webp",
            backgroundAssetPath = null,
            accentColor = Color(0xFFFCE4EC),
            category = "professional"
        ),
        StudioThemeplate(
            id = "professional_cup",
            titleResId = R.string.themeplate_professional_cup,
            assetPath = "anh_chuyen_nghiep/cup_bg.jpg",
            objectSourceAssetPath = "anh_chuyen_nghiep/cup_obj_src.webp",
            backgroundAssetPath = null,
            accentColor = Color(0xFFFFF3E0),
            category = "professional"
        ),
        StudioThemeplate(
            id = "professional_lipstick",
            titleResId = R.string.themeplate_professional_lipstick,
            assetPath = "anh_chuyen_nghiep/lipstick_bg.jpg",
            objectSourceAssetPath = "anh_chuyen_nghiep/lipstick_obj_src.webp",
            backgroundAssetPath = null,
            accentColor = Color(0xFFFFEB3B),
            category = "professional"
        ),
        StudioThemeplate(
            id = "professional_hat",
            titleResId = R.string.themeplate_professional_hat,
            assetPath = "anh_chuyen_nghiep/hat_bg.png",
            objectSourceAssetPath = "anh_chuyen_nghiep/hat_obj_src.webp",
            backgroundAssetPath = null,
            accentColor = Color(0xFFD7CCC8),
            category = "professional"
        ),
        StudioThemeplate(
            id = "professional_beanie",
            titleResId = R.string.themeplate_professional_beanie,
            assetPath = "anh_chuyen_nghiep/beanie_bg.png",
            objectSourceAssetPath = "anh_chuyen_nghiep/beanie_obj_src.webp",
            backgroundAssetPath = null,
            accentColor = Color(0xFFECEFF1),
            category = "professional"
        ),
        StudioThemeplate(
            id = "professional_lemonade",
            titleResId = R.string.themeplate_professional_lemonade,
            assetPath = "anh_chuyen_nghiep/lemonade_bg.png",
            objectSourceAssetPath = "anh_chuyen_nghiep/lemonade_obj_src.webp",
            backgroundAssetPath = null,
            accentColor = Color(0xFFE8F5E9),
            category = "professional"
        ),
        StudioThemeplate(
            id = "professional_lemonade_beige",
            titleResId = R.string.themeplate_professional_lemonade_beige,
            assetPath = "anh_chuyen_nghiep/lemonade_beige_bg.png",
            objectSourceAssetPath = "anh_chuyen_nghiep/lemonade_beige_obj_src.webp",
            backgroundAssetPath = null,
            accentColor = Color(0xFFFFF3E0),
            category = "professional"
        ),
        StudioThemeplate(
            id = "professional_lemonade_blue",
            titleResId = R.string.themeplate_professional_lemonade_blue,
            assetPath = "anh_chuyen_nghiep/lemonade_blue_bg.jpg",
            objectSourceAssetPath = "anh_chuyen_nghiep/lemonade_obj_src.webp",
            backgroundAssetPath = null,
            accentColor = Color(0xFFE0F7FA),
            category = "professional"
        ),
        StudioThemeplate(
            id = "professional_lemonade_cyan",
            titleResId = R.string.themeplate_professional_lemonade_cyan,
            assetPath = "anh_chuyen_nghiep/lemonade_cyan_bg.png",
            objectSourceAssetPath = "anh_chuyen_nghiep/lemonade_cyan_obj_src.webp",
            backgroundAssetPath = null,
            accentColor = Color(0xFFE0F2F1),
            category = "professional"
        )
    )

    private val digitalLifeList = listOf(
        StudioThemeplate(
            id = "professional_asphalt",
            titleResId = R.string.themeplate_professional_asphalt,
            assetPath = "anh_chuyen_nghiep/digital_life_asphalt_bg.jpg",
            objectSourceAssetPath = "anh_chuyen_nghiep/digital_life_asphalt_obj_src.webp",
            backgroundAssetPath = null,
            accentColor = Color(0xFFFF5722),
            category = "professional"
        ),
        StudioThemeplate(
            id = "professional_sidewalk",
            titleResId = R.string.themeplate_professional_sidewalk,
            assetPath = "anh_chuyen_nghiep/digital_life_sidewalk_bg.jpg",
            objectSourceAssetPath = "anh_chuyen_nghiep/digital_life_sidewalk_obj_src.webp",
            backgroundAssetPath = null,
            accentColor = Color(0xFFFF9800),
            category = "professional"
        ),
        StudioThemeplate(
            id = "professional_retro",
            titleResId = R.string.themeplate_professional_retro,
            assetPath = "anh_chuyen_nghiep/digital_life_retro_bg.jpg",
            objectSourceAssetPath = "anh_chuyen_nghiep/digital_life_retro_obj_src.webp",
            backgroundAssetPath = null,
            accentColor = Color(0xFF008080),
            category = "professional"
        ),
        StudioThemeplate(
            id = "professional_cafe",
            titleResId = R.string.themeplate_professional_cafe,
            assetPath = "anh_chuyen_nghiep/digital_life_cafe_bg.jpg",
            objectSourceAssetPath = "anh_chuyen_nghiep/digital_life_cafe_obj_src.webp",
            backgroundAssetPath = null,
            accentColor = Color(0xFF8D6E63),
            category = "professional"
        ),
        StudioThemeplate(
            id = "professional_wall",
            titleResId = R.string.themeplate_professional_wall,
            assetPath = "anh_chuyen_nghiep/digital_life_wall_bg.png",
            objectSourceAssetPath = "anh_chuyen_nghiep/digital_life_wall_obj_src.webp",
            backgroundAssetPath = null,
            accentColor = Color(0xFF1E88E5),
            category = "professional"
        )
    )

    // Selfie & Äam mÃª Äƒn uá»‘ng â€” 8 template tá»« assets selfie_dam_me_an_uong/
    // Quy Æ°á»›c: X1.png = áº£nh ná»n (assetPath), X2.png = áº£nh máº«u sáº£n pháº©m (objectSourceAssetPath)
    private val selfieFoodTemplateList = listOf(
        StudioThemeplate(
            id = "selfie_food_01",
            titleResId = R.string.themeplate_selfie_food_01,
            assetPath = "selfie_dam_me_an_uong/11.jpg",
            objectSourceAssetPath = "selfie_dam_me_an_uong/12.webp",
            backgroundAssetPath = null,
            accentColor = Color(0xFFFF8A65),
            category = "selfie_food"
        ),
        StudioThemeplate(
            id = "selfie_food_02",
            titleResId = R.string.themeplate_selfie_food_02,
            assetPath = "selfie_dam_me_an_uong/21.jpg",
            objectSourceAssetPath = "selfie_dam_me_an_uong/22.webp",
            backgroundAssetPath = null,
            accentColor = Color(0xFFFFB300),
            category = "selfie_food"
        ),
        StudioThemeplate(
            id = "selfie_food_03",
            titleResId = R.string.themeplate_selfie_food_03,
            assetPath = "selfie_dam_me_an_uong/31.jpg",
            objectSourceAssetPath = "selfie_dam_me_an_uong/32.webp",
            backgroundAssetPath = null,
            accentColor = Color(0xFF66BB6A),
            category = "selfie_food"
        ),
        StudioThemeplate(
            id = "selfie_food_04",
            titleResId = R.string.themeplate_selfie_food_04,
            assetPath = "selfie_dam_me_an_uong/41.jpg",
            objectSourceAssetPath = "selfie_dam_me_an_uong/42.webp",
            backgroundAssetPath = null,
            accentColor = Color(0xFF29B6F6),
            category = "selfie_food"
        ),
        StudioThemeplate(
            id = "selfie_food_05",
            titleResId = R.string.themeplate_selfie_food_05,
            assetPath = "selfie_dam_me_an_uong/51.jpg",
            objectSourceAssetPath = "selfie_dam_me_an_uong/52.webp",
            backgroundAssetPath = null,
            accentColor = Color(0xFFEC407A),
            category = "selfie_food"
        ),
        StudioThemeplate(
            id = "selfie_food_06",
            titleResId = R.string.themeplate_selfie_food_06,
            assetPath = "selfie_dam_me_an_uong/61.jpg",
            objectSourceAssetPath = "selfie_dam_me_an_uong/62.webp",
            backgroundAssetPath = null,
            accentColor = Color(0xFFAB47BC),
            category = "selfie_food"
        ),
        StudioThemeplate(
            id = "selfie_food_07",
            titleResId = R.string.themeplate_selfie_food_07,
            assetPath = "selfie_dam_me_an_uong/71.jpg",
            objectSourceAssetPath = "selfie_dam_me_an_uong/72.webp",
            backgroundAssetPath = null,
            accentColor = Color(0xFF8D6E63),
            category = "selfie_food"
        ),
        StudioThemeplate(
            id = "selfie_food_08",
            titleResId = R.string.themeplate_selfie_food_08,
            assetPath = "selfie_dam_me_an_uong/81.jpg",
            objectSourceAssetPath = "selfie_dam_me_an_uong/82.webp",
            backgroundAssetPath = null,
            accentColor = Color(0xFF26A69A),
            category = "selfie_food"
        )
    )

    private val professionalSectionList = listOf(
        StudioThemeplateSection(
            id = "professional_digital_life",
            titleResId = R.string.themeplate_section_digital_life,
            themeplates = listOf(
                digitalLifeList.find { it.id == "professional_asphalt" }!!.copy(
                    id = "professional_digital_life_asphalt",
                    category = "professional_digital_life"
                ),
                digitalLifeList.find { it.id == "professional_sidewalk" }!!.copy(
                    id = "professional_digital_life_sidewalk",
                    category = "professional_digital_life"
                ),
                digitalLifeList.find { it.id == "professional_retro" }!!.copy(
                    id = "professional_digital_life_retro",
                    category = "professional_digital_life"
                ),
                digitalLifeList.find { it.id == "professional_cafe" }!!.copy(
                    id = "professional_digital_life_cafe",
                    category = "professional_digital_life"
                ),
                digitalLifeList.find { it.id == "professional_wall" }!!.copy(
                    id = "professional_digital_life_wall",
                    category = "professional_digital_life"
                )
            )
        ),
        StudioThemeplateSection(
            id = "professional_phone_mode",
            titleResId = R.string.themeplate_section_phone_mode,
            themeplates = listOf(
                professionalList[2].copy(
                    id = "professional_phone_mode_handbag",
                    category = "professional_phone_mode"
                ),
                professionalList[1].copy(
                    id = "professional_phone_mode_perfume",
                    category = "professional_phone_mode"
                ),
                professionalList[6].copy(
                    id = "professional_phone_mode_lipstick",
                    category = "professional_phone_mode"
                ),
                professionalList[7].copy(
                    id = "professional_phone_mode_hat",
                    category = "professional_phone_mode"
                ),
                professionalList[11].copy(
                    id = "professional_phone_mode_lemonade_blue",
                    category = "professional_phone_mode"
                )
            )
        ),
        StudioThemeplateSection(
            id = "professional_food_selfie",
            titleResId = R.string.themeplate_section_food_selfie,
            // Thay toÃ n bá»™ báº±ng 8 template má»›i tá»« selfie_dam_me_an_uong/
            themeplates = selfieFoodTemplateList
        )
    )

    private val newProfessionalList = listOf(
        StudioThemeplate(
            id = "professional_lemonade_minimal",
            titleResId = R.string.themeplate_professional_digital_life,
            assetPath = "anh_chuyen_nghiep/lemonade_minimal_bg.jpg",
            objectSourceAssetPath = "anh_chuyen_nghiep/lemonade_minimal_obj_src.webp",
            backgroundAssetPath = null,
            accentColor = Color(0xFFECEFF1),
            category = "professional"
        ),
        StudioThemeplate(
            id = "professional_lemonade_warm",
            titleResId = R.string.themeplate_professional_phone_mode,
            assetPath = "anh_chuyen_nghiep/lemonade_warm_bg.jpg",
            objectSourceAssetPath = "anh_chuyen_nghiep/lemonade_warm_obj_src.webp",
            backgroundAssetPath = null,
            accentColor = Color(0xFFFFE0B2),
            category = "professional"
        ),
        StudioThemeplate(
            id = "professional_lemonade_fresh_blue",
            titleResId = R.string.themeplate_professional_food_selfie,
            assetPath = "anh_chuyen_nghiep/lemonade_fresh_blue_bg.jpg",
            objectSourceAssetPath = "anh_chuyen_nghiep/lemonade_fresh_blue_obj_src.webp",
            backgroundAssetPath = null,
            accentColor = Color(0xFF81D4FA),
            category = "professional"
        )
    )

    private val professionalFlatList: List<StudioThemeplate> =
        professionalList

    val all: List<StudioThemeplate> =
        cosmeticsList + professionalFlatList + digitalLifeList +
        professionalSectionList.flatMap { it.themeplates }  // includes selfieFoodTemplateList via professional_food_selfie section
    val cosmetics: List<StudioThemeplate> get() = cosmeticsList
    val professional: List<StudioThemeplate> get() = professionalFlatList
    val professionalSections: List<StudioThemeplateSection> get() = professionalSectionList

    fun findById(id: String): StudioThemeplate? = all.find { it.id == id }
}



