package com.abizer_r.quickedit.ui.drawMode

import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.abizer_r.quickedit.ui.drawMode.stateHandling.DrawModeEvent
import com.abizer_r.quickedit.ui.drawMode.stateHandling.DrawModeState
import com.abizer_r.quickedit.ui.drawMode.stateHandling.EditingAction
import com.abizer_r.quickedit.ui.editorScreen.bottomToolbar.state.BottomToolbarEvent
import com.abizer_r.quickedit.ui.editorScreen.bottomToolbar.state.BottomToolbarItem
import com.abizer_r.quickedit.utils.drawMode.DrawModeUtils
import com.abizer_r.quickedit.utils.drawMode.DrawingConstants
import com.abizer_r.quickedit.utils.drawMode.getToleranceOrNull
import com.abizer_r.quickedit.utils.drawMode.getWidthOrNull
import com.abizer_r.quickedit.utils.drawMode.setOpacityIfPossible
import com.abizer_r.quickedit.utils.drawMode.setShapeTypeIfPossible
import com.abizer_r.quickedit.utils.drawMode.setWidthIfPossible
import com.abizer_r.quickedit.utils.drawMode.setToleranceIfPossible
import com.abizer_r.quickedit.utils.other.anim.AnimUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import javax.inject.Inject

@HiltViewModel
class DrawModeViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _state = MutableStateFlow(DrawModeState())
    val state: StateFlow<DrawModeState> = _state

    private val _shouldGoToNextScreen = MutableStateFlow(false)
    val shouldGoToNextScreen: StateFlow<Boolean> = _shouldGoToNextScreen
    // shows the icon initially, then show selected color
    var showColorPickerIconInToolbar = true

    fun onNextScreenRequested() {
        _shouldGoToNextScreen.value = true
    }

    fun onNextScreenConsumed() {
        _shouldGoToNextScreen.value = false
    }

    fun setWorkingBitmap(bitmap: Bitmap) {
        _state.update {
            it.copy(workingBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true))
        }
    }

    fun handleStateBeforeCaptureScreenshot() {
        _shouldGoToNextScreen.value = true
        _state.update {
            it.copy(showBottomToolbarExtension = false)
        }
    }

    fun onEvent(event: DrawModeEvent) {
        when (event) {
            is DrawModeEvent.UpdateToolbarExtensionVisibility -> {
                _state.update { it.copy(showBottomToolbarExtension = event.isVisible) }
            }
            is DrawModeEvent.ToggleColorPicker -> {
                _state.update {
                    it.copy(
                        showColorPicker = it.showColorPicker.not(),
                        selectedColor = event.selectedColor ?: it.selectedColor
                    )
                }
                showColorPickerIconInToolbar = false
            }

            DrawModeEvent.OnUndo -> {
                performUndo()
            }

            DrawModeEvent.OnRedo -> {
                performRedo()
            }

            is DrawModeEvent.AddNewPath -> {
                _state.update { current ->
                    val newHistory = current.historyStack.toMutableList().apply {
                        add(EditingAction.ManualPath(event.pathDetail))
                    }
                    val newPaths = rebuildPathStack(newHistory)
                    
                    current.copy(
                        historyStack = newHistory,
                        pathDetailStack = newPaths,
                        redoStack = emptyList(), // Clear redo khi có action mới
                        recompositionTrigger = current.recompositionTrigger + 1
                    )
                }
            }
        }
    }

    private fun rebuildPathStack(history: List<EditingAction>): List<com.abizer_r.quickedit.ui.drawMode.drawingCanvas.models.PathDetails> {
        return history.filterIsInstance<EditingAction.ManualPath>()
            .map { it.path }
    }


    private fun performUndo() {
        _state.update { current ->
            if (current.historyStack.isEmpty()) return@update current
            
            val newHistory = current.historyStack.toMutableList()
            val action = newHistory.removeAt(newHistory.lastIndex)
            
            val newRedo = current.redoStack.toMutableList().apply {
                add(action)
            }
            
            // Rebuild pathDetailStack từ historyStack
            val newPaths = rebuildPathStack(newHistory)
            
            current.copy(
                historyStack = newHistory,
                redoStack = newRedo,
                pathDetailStack = newPaths,
                recompositionTrigger = current.recompositionTrigger + 1
            )
        }
    }

    private fun performRedo() {
        _state.update { current ->
            if (current.redoStack.isEmpty()) return@update current
            
            val newRedo = current.redoStack.toMutableList()
            val action = newRedo.removeAt(newRedo.lastIndex)
            
            val newHistory = current.historyStack.toMutableList().apply {
                add(action)
            }
            
            // Rebuild pathDetailStack từ historyStack
            val newPaths = rebuildPathStack(newHistory)
            
            current.copy(
                historyStack = newHistory,
                redoStack = newRedo,
                pathDetailStack = newPaths,
                recompositionTrigger = current.recompositionTrigger + 1
            )
        }
    }




    fun onBottomToolbarEvent(event: BottomToolbarEvent) {
        when (event) {
            is BottomToolbarEvent.OnItemClicked -> {
                onBottomToolbarItemClicked(event.toolbarItem)
            }

            is BottomToolbarEvent.UpdateOpacity -> {
                _state.update { it.copy(
                    selectedTool = it.selectedTool.setOpacityIfPossible(event.newOpacity),
                    recompositionTrigger = it.recompositionTrigger + 1
                ) }
            }

            is BottomToolbarEvent.UpdateWidth -> {
                _state.update { it.copy(
                    selectedTool = it.selectedTool.setWidthIfPossible(event.newWidth),
                    recompositionTrigger = it.recompositionTrigger + 1
                ) }
            }

            is BottomToolbarEvent.UpdateShapeType -> {
                _state.update { it.copy(
                    selectedTool = it.selectedTool.setShapeTypeIfPossible(event.newShapeType),
                    recompositionTrigger = it.recompositionTrigger + 1
                ) }
            }
            
            is BottomToolbarEvent.UpdateTolerance -> {
                _state.update { it.copy(
                    selectedTool = it.selectedTool.setToleranceIfPossible(event.newTolerance),
                    recompositionTrigger = it.recompositionTrigger + 1
                ) }
            }


        }
    }

    private fun onBottomToolbarItemClicked(selectedItem: BottomToolbarItem) = viewModelScope.launch {
        when (selectedItem) {
            is BottomToolbarItem.ColorItem -> {
                _state.update {
                    it.copy(showColorPicker = it.showColorPicker.not())
                }
            }

            // Clicked on already selected item
            state.value.selectedTool -> {
                if (selectedItem != BottomToolbarItem.PanItem) {
                    _state.update {
                        it.copy(showBottomToolbarExtension = it.showBottomToolbarExtension.not())
                    }
                }
            }

            // clicked on another item
            else -> {
                if (state.value.showBottomToolbarExtension) {
                    // Collapse toolbarExtension and change current item after DELAY
                    _state.update { it.copy(showBottomToolbarExtension = false) }
                    delay(AnimUtils.TOOLBAR_COLLAPSE_ANIM_DURATION.toLong())
                }
                _state.update { it.copy(selectedTool = selectedItem) }
                if (selectedItem != BottomToolbarItem.PanItem) {
                    // open toolbarExtension for new item
                    _state.update { it.copy(showBottomToolbarExtension = true) }
                }
            }
        }
    }
}