package com.thgiang.image.studio.ui.editor.label.panel

import com.thgiang.image.studio.R
import com.thgiang.image.studio.ui.editor.model.TextFormPreset

internal data class TextFormPresetItem(
    val preset: TextFormPreset,
    val labelRes: Int,
    val defaultAmount: Float = 0.55f,
)

internal val textFormPathPresets: List<TextFormPresetItem> = listOf(
    TextFormPresetItem(TextFormPreset.NONE, R.string.studio_text_form_none),
    TextFormPresetItem(TextFormPreset.PATH_WAVE, R.string.studio_text_form_path_wave, 0.5f),
    TextFormPresetItem(TextFormPreset.PATH_ARC_UP, R.string.studio_text_form_path_arc_up, 0.55f),
    TextFormPresetItem(TextFormPreset.PATH_ARC_DOWN, R.string.studio_text_form_path_arc_down, 0.55f),
    TextFormPresetItem(TextFormPreset.PATH_CIRCLE, R.string.studio_text_form_path_circle, 0.6f),
)

internal val textFormWarpPresets: List<TextFormPresetItem> = listOf(
    TextFormPresetItem(TextFormPreset.NONE, R.string.studio_text_form_none),
    TextFormPresetItem(TextFormPreset.WARP_ARCH_UP, R.string.studio_text_form_warp_arch_up),
    TextFormPresetItem(TextFormPreset.WARP_ARCH_DOWN, R.string.studio_text_form_warp_arch_down),
    TextFormPresetItem(TextFormPreset.WARP_BULGE, R.string.studio_text_form_warp_bulge),
    TextFormPresetItem(TextFormPreset.WARP_WAVE, R.string.studio_text_form_warp_wave),
    TextFormPresetItem(TextFormPreset.WARP_FLAG, R.string.studio_text_form_warp_flag),
    TextFormPresetItem(TextFormPreset.WARP_RISE, R.string.studio_text_form_warp_rise),
    TextFormPresetItem(TextFormPreset.WARP_FALL, R.string.studio_text_form_warp_fall),
    TextFormPresetItem(TextFormPreset.WARP_CHEVRON_UP, R.string.studio_text_form_warp_chevron_up),
    TextFormPresetItem(TextFormPreset.WARP_CHEVRON_DOWN, R.string.studio_text_form_warp_chevron_down),
)

/** Path + warp presets in one scroll row (single "None" at start). */
internal val textFormAllPresets: List<TextFormPresetItem> =
    textFormPathPresets + textFormWarpPresets.drop(1)
