package com.thgiang.image.feature.home.model

import com.thgiang.image.core.model.PresetStyle

data class PresetItem(
    val title: String,
    val style: PresetStyle,
    val locked: Boolean,
    val lightLabel: Boolean = true
)
