package com.thgiang.image.studio.util

import androidx.annotation.StringRes
import com.thgiang.image.core.domain.model.template.CloudCategory
import com.thgiang.image.core.domain.model.template.TemplateCategorySlug
import com.thgiang.image.studio.R
import java.text.Normalizer
import java.util.Locale

object TemplateCategoryTitleResolver {

    @StringRes
    fun titleResId(category: CloudCategory): Int {
        val id = category.id.lowercase(Locale.ROOT)
        val slug = category.slug?.lowercase(Locale.ROOT).orEmpty()
        val nameKey = normalizedKey(category.name)
        val combined = "$id $slug $nameKey"

        return when {
            isCosmeticsCategory(category, combined, nameKey) -> R.string.themeplate_section_cosmetics
            isFashionCategory(category, combined, nameKey) -> R.string.themeplate_section_fashion
            isDigitalLifeCategory(category, combined) -> R.string.themeplate_section_digital_life
            isFoodSelfieCategory(category, combined) -> R.string.themeplate_section_food_selfie
            containsAny(combined, "christmas", "giang sinh") -> R.string.themeplate_section_christmas
            containsAny(combined, "memories", "ky niem") -> R.string.themeplate_section_memories
            containsAny(combined, "phone", "dien thoai", "phone_mode") -> R.string.themeplate_section_phone_mode
            else -> 0
        }
    }

    private fun isCosmeticsCategory(
        category: CloudCategory,
        combined: String,
        nameKey: String,
    ): Boolean {
        return idOrSlugMatches(category, TemplateCategorySlug.COSMETICS) ||
            TemplateCategorySlug.matchesSlug(TemplateCategorySlug.COSMETICS, category.name) ||
            containsAny(combined, "cosmetics", "my pham", "mau my pham")
    }

    private fun isFashionCategory(
        category: CloudCategory,
        combined: String,
        nameKey: String,
    ): Boolean {
        return idOrSlugMatches(category, TemplateCategorySlug.PROFESSIONAL) ||
            TemplateCategorySlug.matchesSlug(TemplateCategorySlug.PROFESSIONAL, category.name) ||
            containsAny(combined, "professional", "fashion", "thoi trang", "chuyen nghiep")
    }

    private fun isDigitalLifeCategory(
        category: CloudCategory,
        combined: String,
    ): Boolean {
        return idOrSlugMatches(category, TemplateCategorySlug.DIGITAL_LIFE) ||
            TemplateCategorySlug.matchesSlug(TemplateCategorySlug.DIGITAL_LIFE, category.name) ||
            containsAny(combined, "digital_life", "doi song so")
    }

    private fun isFoodSelfieCategory(
        category: CloudCategory,
        combined: String,
    ): Boolean {
        return idOrSlugMatches(category, TemplateCategorySlug.SELFIE_FOOD) ||
            TemplateCategorySlug.matchesSlug(TemplateCategorySlug.SELFIE_FOOD, category.name) ||
            containsAny(combined, "selfie_food", "me an uong", "dam me an uong")
    }

    private fun idOrSlugMatches(category: CloudCategory, slug: String): Boolean {
        val normalizedSlug = slug.lowercase(Locale.ROOT)
        return category.id.equals(normalizedSlug, ignoreCase = true) ||
            category.slug.equals(normalizedSlug, ignoreCase = true)
    }

    private fun containsAny(value: String, vararg needles: String): Boolean {
        return needles.any { needle -> needle in value }
    }

    private fun normalizedKey(value: String): String {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace("đ", "d")
            .replace("Đ", "D")
            .lowercase(Locale.ROOT)
    }
}
