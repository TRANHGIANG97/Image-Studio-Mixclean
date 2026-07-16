package com.abizer_r.quickedit.ui.effectsMode

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.abizer_r.quickedit.ui.effectsMode.effectsPreview.EffectItem
import com.abizer_r.quickedit.ui.effectsMode.effectsPreview.EffectRecipe
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class EffectsModeViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _state = MutableStateFlow(EffectsModeState())
    val state: StateFlow<EffectsModeState> = _state

    private val _shouldGoToNextScreen = MutableStateFlow(false)
    val shouldGoToNextScreen: StateFlow<Boolean> = _shouldGoToNextScreen

    fun onNextScreenRequested() {
        _shouldGoToNextScreen.value = true
    }

    fun onNextScreenConsumed() {
        _shouldGoToNextScreen.value = false
    }

    fun updateEffectList(effectList: ArrayList<EffectItem>) {
        _state.update { it.copy(effectsList = effectList) }
    }

    fun addToEffectList(
        effectItems: ArrayList<EffectItem>,
        selectInitialBitmap: Boolean = false,
    ) {
        val currList = ArrayList(state.value.effectsList)
        currList.addAll(effectItems)
        _state.update { it.copy(effectsList = currList) }
        if (selectInitialBitmap) {
            selectEffect(selectedIndex = 0)
        }
    }

    fun selectEffect(selectedIndex: Int) {
        if (selectedIndex < 0 || selectedIndex >= state.value.effectsList.size) {
            Log.e("EffectsModeViewModel", "selectEffect: index out of bound, selectedIndex = $selectedIndex")
            return
        }
        _state.update {
            it.copy(
                selectedEffectIndex = selectedIndex,
                filteredBitmap = state.value.effectsList[selectedIndex].ogBitmap
            )
        }
    }

    fun selectedRecipe(): EffectRecipe {
        val list = _state.value.effectsList
        val index = _state.value.selectedEffectIndex
        return list.getOrNull(index)?.recipe ?: EffectRecipe.Original
    }

    override fun onCleared() {
        super.onCleared()
        val items = _state.value.effectsList
        val recycled = mutableSetOf<Bitmap>()
        items.forEach { item ->
            listOf(item.ogBitmap, item.previewBitmap).forEach { bmp ->
                if (bmp !in recycled && !bmp.isRecycled) {
                    recycled.add(bmp)
                    bmp.recycle()
                }
            }
        }
        _state.value = EffectsModeState()
    }
}
