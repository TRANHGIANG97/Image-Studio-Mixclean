package com.abizer_r.quickedit.ui.textMode

import android.util.Log
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.abizer_r.quickedit.ui.editorScreen.bottomToolbar.state.BottomToolbarEvent
import com.abizer_r.quickedit.ui.editorScreen.bottomToolbar.state.BottomToolbarItem
import com.abizer_r.quickedit.ui.textMode.TextModeEvent.ShowTextEditor
import com.abizer_r.quickedit.ui.textMode.bottomToolbarExtension.TextModeToolbarExtensionEvent
import com.abizer_r.quickedit.ui.textMode.bottomToolbarExtension.textFormatOptions.caseOptions.TextCaseType
import com.abizer_r.quickedit.ui.textMode.bottomToolbarExtension.textFormatOptions.styleOptions.TextStyleAttr
import com.abizer_r.quickedit.ui.textMode.textEditorLayout.TextEditorState
import com.abizer_r.quickedit.ui.transformableViews.base.TransformableTextBoxState
import com.abizer_r.quickedit.ui.transformableViews.base.TransformableBoxEvents
import com.abizer_r.quickedit.ui.transformableViews.base.TransformableBoxState
import com.abizer_r.quickedit.utils.ImmutableList
import com.abizer_r.quickedit.utils.other.anim.AnimUtils
import com.abizer_r.quickedit.utils.textMode.TextModeUtils
import com.abizer_r.quickedit.utils.textMode.TextModeUtils.isTextModeItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TextModeViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _state = MutableStateFlow(TextModeState())
    val state: StateFlow<TextModeState> = _state

    private val _showTextEditor = MutableStateFlow(false)
    val showTextEditor: StateFlow<Boolean> = _showTextEditor

    var initialTextEditorState: TextEditorState? = null
        private set

    private val _bottomToolbarItems = MutableStateFlow<ImmutableList<BottomToolbarItem>>(
        ImmutableList(TextModeUtils.getBottomToolbarItemsList(null))
    )
    val bottomToolbarItems: StateFlow<ImmutableList<BottomToolbarItem>> = _bottomToolbarItems

    var selectedViewState: TransformableBoxState? = null
        private set

//    fun getSelectedViewState(): TransformableBoxState? =
//        state.value.transformableViewStateList.find { it.isSelected }



//    private val _toolbarExtensionVisible = MutableStateFlow(false)
//    val toolbarExtensionVisible: StateFlow<Boolean> = _toolbarExtensionVisible

    private fun updateShowTextEditor(isVisible: Boolean, textEditorState: TextEditorState? = null) {
        // set the initial EditorState for TextEditorLayout
        initialTextEditorState = textEditorState
        _showTextEditor.value = isVisible
    }

//    fun updateToolbarExtensionVisibility(isVisible: Boolean) {
//        _toolbarExtensionVisible.value = isVisible
//    }

    init {
//        debugTrackViewListSize()
    }

    private fun debugTrackViewListSize() {
        GlobalScope.launch {
            while (true) {
                delay(1000)
                Log.e("TEST_TEXT_MODE", ": viewList size = ${state.value.transformableViewStateList.size}", )
            }
        }
    }

    private val _shouldGoToNextScreen = MutableStateFlow(false)
    val shouldGoToNextScreen: StateFlow<Boolean> = _shouldGoToNextScreen

    fun onNextScreenRequested() {
        _shouldGoToNextScreen.value = true
    }

    fun onNextScreenConsumed() {
        _shouldGoToNextScreen.value = false
    }

    fun handleStateBeforeCaptureScreenshot() {
        _shouldGoToNextScreen.value = true
        updateViewSelection(null)
        _state.update {
            it.copy(showBottomToolbarExtension = false)
        }
    }

    fun onEvent(event: TextModeEvent) = viewModelScope.launch {
        when (event) {

            is TextModeEvent.UpdateToolbarExtensionVisibility -> {
                _state.update { it.copy(showBottomToolbarExtension = event.isVisible) }
            }

            is ShowTextEditor -> {
                Log.e("TEST_BLUR", "PlaceHolder Text: ", )
                if (state.value.showBottomToolbarExtension) {
                    // Collapse toolbarExtension and then show text field
                    _state.update { it.copy(showBottomToolbarExtension = false) }
                    delay(AnimUtils.TOOLBAR_COLLAPSE_ANIM_DURATION.toLong())
                }
                updateShowTextEditor(true, event.textEditorState)
            }

            is TextModeEvent.HideTextEditor -> {
                updateShowTextEditor(false)
            }

            is TextModeEvent.UpdateTransformableViewsList -> {
                _state.update {
                    it.copy(
                        transformableViewStateList = event.list,
                        recompositionTrigger = it.recompositionTrigger + 1
                    )
                }
            }

            is TextModeEvent.AddTransformableTextBox -> {
                val id = event.textBoxState.id
                val existingItem = state.value.transformableViewStateList.find { it.id == id }
                
                val newList = if (existingItem != null) {
                    state.value.transformableViewStateList.map {
                        if (it.id == id) {
                            (it as TransformableTextBoxState).copy(
                                text = event.textBoxState.text,
                                textAlign = event.textBoxState.textAlign,
                                textColor = event.textBoxState.textColor,
                                textFont = event.textBoxState.textFont
                            )
                        } else it
                    }
                } else {
                    state.value.transformableViewStateList.toMutableList().also {
                        it.add(event.textBoxState)
                    }
                }
                
                _state.update { it.copy(
                    transformableViewStateList = ArrayList(newList),
                    recompositionTrigger = it.recompositionTrigger + 1
                ) }

                updateViewSelection(selectedViewId = event.textBoxState.id)
                updateShowTextEditor(false)
            }

        }
    }

    fun onTransformableBoxEvent(mEvent: TransformableBoxEvents) {
        val stateList = state.value.transformableViewStateList
        val viewItem = stateList.find { it.id == mEvent.id } ?: return
        when(mEvent) {

            is TransformableBoxEvents.UpdateBoxBorder -> {
                viewItem.innerBoxSize = mEvent.innerBoxSize
            }

            is TransformableBoxEvents.UpdateTransformation -> {
                viewItem.positionOffset += mEvent.dragAmount
                viewItem.scale = (viewItem.scale * mEvent.zoomAmount).coerceIn(0.5f, 5f)
                viewItem.rotation += mEvent.rotationChange
            }

            is TransformableBoxEvents.OnCloseClicked -> {
                stateList.remove(viewItem)
            }

            is TransformableBoxEvents.OnTapped -> {
                Log.e("TEST_editor", "OnTapped: id = ${viewItem.id}, selected = ${viewItem.isSelected}", )
                if (viewItem.isSelected && mEvent.textViewState != null) {
                    val textEditorState = TextEditorState(
                        textStateId = mEvent.id,
                        text = mEvent.textViewState.text,
                        textAlign = mEvent.textViewState.textAlign,
                        selectedColor = mEvent.textViewState.textColor,
                        textFont = mEvent.textViewState.textFont
                    )
                    onEvent(ShowTextEditor(textEditorState = textEditorState))
                }
            }
        }
        if (viewItem.isSelected.not()) {
            Log.e("TEST_Select", "onTransformableBoxEvent: selecting item ${viewItem.id}", )
            updateViewSelection(viewItem.id)
        }
        onEvent(TextModeEvent.UpdateTransformableViewsList(stateList))
    }

    fun updateViewSelection(
        selectedViewId: String? = null
    ) = viewModelScope.launch{
        val prevSelectedView = selectedViewState
        selectedViewState?.isSelected = false

        val newViewState = state.value.transformableViewStateList.find { it.id == selectedViewId }
        newViewState?.isSelected = true
        selectedViewState = newViewState

//        val transformableViewsList = state.value.transformableViewStateList
//        transformableViewsList.forEach {
//            it.isSelected = it.id == selectedViewId
//        }


        val isNewItemSelected = prevSelectedView != newViewState
        var prevSelectedToolbarItem: BottomToolbarItem = BottomToolbarItem.NONE
        if (isNewItemSelected) {
           prevSelectedToolbarItem = state.value.selectedTool
            // the selected toolbar item must be updated before getting new toolbarItem-list
            updateSelectedToolbarItem(BottomToolbarItem.NONE)
        }

        val newToolbarItems = TextModeUtils.getBottomToolbarItemsList(
            selectedViewState = selectedViewState,
            selectedItem = state.value.selectedTool
        )
        _bottomToolbarItems.update { ImmutableList(newToolbarItems) }

        if (isNewItemSelected) {
            val newSelectedTool = TextModeUtils.getNewSelectedToolItem(newToolbarItems, prevSelectedToolbarItem)
            updateSelectedToolbarItem(newSelectedTool)

        } else {
            _state.update {
                it.copy( showBottomToolbarExtension = selectedViewState != null )
            }
        }
    }


    fun onBottomToolbarEvent(event: BottomToolbarEvent) {
        when (event) {
            is BottomToolbarEvent.OnItemClicked -> {
                onBottomToolbarItemClicked(event.toolbarItem)
            }

            else -> {}
        }
    }

    private fun onBottomToolbarItemClicked(selectedItem: BottomToolbarItem) = viewModelScope.launch {
        when (selectedItem) {
            is BottomToolbarItem.AddItem -> {
                onEvent(ShowTextEditor())
            }

            // Clicked on already selected item
            state.value.selectedTool -> {
                if (selectedItem != BottomToolbarItem.AddItem) {
                    _state.update {
                        it.copy(showBottomToolbarExtension = it.showBottomToolbarExtension.not())
                    }
                }
            }

            // clicked on another item
            else -> updateSelectedToolbarItem(selectedItem)

        }
    }

    private suspend fun updateSelectedToolbarItem(selectedItem: BottomToolbarItem) {
        if (state.value.showBottomToolbarExtension) {
            // Collapse toolbarExtension and change current item after DELAY
            _state.update { it.copy(showBottomToolbarExtension = false) }
            delay(AnimUtils.TOOLBAR_COLLAPSE_ANIM_DURATION.toLong())
        }
        _state.update { it.copy(
            selectedTool = selectedItem,
            showBottomToolbarExtension = isTextModeItem(selectedItem),
            recompositionTrigger = it.recompositionTrigger + 1  // to trigger recomposition of transformable boxes
        ) }
    }

    fun onTextModeToolbarExtensionEvent(event: TextModeToolbarExtensionEvent) {
        when (event) {
            is TextModeToolbarExtensionEvent.UpdateTextAlignment -> {
                updateTransformableText(textAlignment = event.textAlignment)
            }
            is TextModeToolbarExtensionEvent.UpdateTextCaseType -> {
                updateTransformableText(textCaseType = event.textCaseType)
            }
            is TextModeToolbarExtensionEvent.UpdateTextStyleAttr -> {
                updateTransformableText(textStyleAttr = event.textStyleAttr)
            }
            is TextModeToolbarExtensionEvent.UpdateTextFontFamily -> {
                updateTransformableText(textFontFamily = event.fontFamily)
            }
            is TextModeToolbarExtensionEvent.UpdateTextFontSize -> {
                updateTransformableText(fontSizeDelta = event.delta)
            }
        }
    }

    private fun updateTransformableText(
        textAlignment: TextAlign? = null,
        textCaseType: TextCaseType? = null,
        textStyleAttr: TextStyleAttr? = null,
        textFontFamily: FontFamily? = null,
        fontSizeDelta: Float? = null
    ) {
        val selectedViewState = state.value.transformableViewStateList.find { it.isSelected }
        if (selectedViewState == null || selectedViewState !is TransformableTextBoxState)
            return

        val newList = state.value.transformableViewStateList.map { item ->
            if (item.id == selectedViewState.id && item is TransformableTextBoxState) {
                item.copy(
                    textAlign = textAlignment ?: item.textAlign,
                    textCaseType = textCaseType ?: item.textCaseType,
                    textStyleAttr = textStyleAttr ?: item.textStyleAttr,
                    textFontFamily = textFontFamily ?: item.textFontFamily,
                    textFont = if (fontSizeDelta != null) {
                        (item.textFont.value + fontSizeDelta).coerceIn(10f, 100f).sp
                    } else item.textFont
                )
            } else item
        }

        val updatedView = newList.find { it.isSelected } as? TransformableTextBoxState
        if (updatedView != null) {
            state.value.selectedTool.apply {
                when(this) {
                    is BottomToolbarItem.TextFormat -> {
                        this.textAlign = updatedView.textAlign
                        this.textCaseType = updatedView.textCaseType
                        this.textStyleAttr = updatedView.textStyleAttr
                    }
                    is BottomToolbarItem.TextFontFamily -> {
                        this.textFontFamily = updatedView.textFontFamily
                    }
                    else -> {}
                }
            }
        }

        _state.update { it.copy(
            transformableViewStateList = ArrayList(newList),
            selectedViewStateUpdateTrigger = it.selectedViewStateUpdateTrigger + 1,
            recompositionTrigger = it.recompositionTrigger + 1
        ) }
    }
}