package com.thgiang.image.core.domain.model.template

data class CloudCategory(
    val id: String = "",
    val name: String = "",
    val order: Int = 0,
    /** Slug chuẩn hóa từ admin_web DB (vd: "professional", "cosmetics").
     *  Null nếu server cũ chưa hỗ trợ trường này — sẽ fallback về name-matching. */
    val slug: String? = null,
) : java.io.Serializable
