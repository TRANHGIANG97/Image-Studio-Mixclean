package com.thgiang.image.core.domain.model.template

import java.text.Normalizer
import java.util.Locale

object TemplateCategorySlug {
    const val PROFESSIONAL = "professional"
    const val COSMETICS = "cosmetics"
    const val DIGITAL_LIFE = "digital_life"
    const val SELFIE_FOOD = "selfie_food"

    /**
     * Giải quyết categoryId từ slug.
     * Thứ tự ưu tiên:
     *   1. Match trực tiếp theo [CloudCategory.slug] (chính xác nhất, không phụ thuộc tên tiếng Việt)
     *   2. Fallback về name-matching (cho server cũ chưa trả slug)
     *   3. Trả về slug gốc nếu không tìm thấy (dùng làm query param thô)
     */
    fun resolveCategoryId(slug: String, categories: List<CloudCategory>): String {
        if (categories.isEmpty()) return slug

        // Ưu tiên 1: match theo slug từ DB — không phụ thuộc tên tiếng Việt hardcode
        val bySlug = categories.firstOrNull {
            it.slug?.equals(slug, ignoreCase = true) == true
        }
        if (bySlug != null) return bySlug.id.takeIf { it.isNotBlank() } ?: slug

        // Ưu tiên 2: fallback về name-matching cho server cũ chưa hỗ trợ slug
        return categories
            .firstOrNull { matchesSlug(slug, it.name) }
            ?.id
            ?.takeIf { it.isNotBlank() }
            ?: slug
    }

    fun matchesSlug(slug: String, categoryName: String): Boolean {
        val normalized = normalize(categoryName)
        return when (slug.lowercase(Locale.ROOT)) {
            PROFESSIONAL -> "thoi trang" in normalized || "chuyen nghiep" in normalized
            COSMETICS -> "my pham" in normalized
            DIGITAL_LIFE -> "doi song so" in normalized
            SELFIE_FOOD -> "me an uong" in normalized || "dam me an uong" in normalized
            else -> normalize(slug) == normalized
        }
    }

    fun slugFromCategoryName(name: String): String? {
        return listOf(PROFESSIONAL, COSMETICS, DIGITAL_LIFE, SELFIE_FOOD)
            .firstOrNull { matchesSlug(it, name) }
    }

    private fun normalize(value: String): String {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
