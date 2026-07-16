package com.thgiang.image.studio.ui.editor.document.rules

import com.thgiang.image.studio.ui.editor.document.model.Effect
import com.thgiang.image.studio.ui.editor.document.model.StyleBag
import com.thgiang.image.studio.ui.editor.model.TextFormEffect

/**
 * Effect compatibility (I5). Deterministic v1:
 * TextForm active → Elevation3D is stripped (cannot stack reliably with warp metrics).
 */
object EffectCompatibilityMatrix {

    data class ResolveResult(
        val style: StyleBag,
        val disabled: List<String> = emptyList(),
    )

    fun resolve(style: StyleBag): ResolveResult {
        val disabled = mutableListOf<String>()
        var effects = style.effects
        if (style.textForm.isActive) {
            val before = effects.size
            effects = effects.filterNot { it is Effect.Elevation3D }
            if (effects.size < before) {
                disabled += "Elevation3D disabled while TextForm is active"
            }
        }
        // Keep at most one DropShadow and one Elevation3D
        val drop = effects.filterIsInstance<Effect.DropShadow>().lastOrNull()
        val elev = effects.filterIsInstance<Effect.Elevation3D>().lastOrNull()
        val others = effects.filterNot { it is Effect.DropShadow || it is Effect.Elevation3D }
        effects = buildList {
            addAll(others)
            drop?.let { add(it) }
            elev?.let { add(it) }
        }
        return ResolveResult(style.copy(effects = effects), disabled)
    }

    fun withTextForm(style: StyleBag, textForm: TextFormEffect): ResolveResult =
        resolve(style.copy(textForm = textForm))
}
