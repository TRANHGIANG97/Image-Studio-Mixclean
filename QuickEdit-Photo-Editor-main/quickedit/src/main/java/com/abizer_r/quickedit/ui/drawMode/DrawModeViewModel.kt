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
import com.abizer_r.quickedit.utils.drawMode.MagicEraserAlgorithm
import com.abizer_r.quickedit.utils.drawMode.getToleranceOrNull
import com.abizer_r.quickedit.utils.drawMode.getWidthOrNull
import javax.inject.Inject

@HiltViewModel
class DrawModeViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _state = MutableStateFlow(DrawModeState())
    val state: StateFlow<DrawModeState> = _state

    var shouldGoToNextScreen = false
    // shows the icon initially, then show selected color
    var showColorPickerIconInToolbar = true

    fun setWorkingBitmap(bitmap: Bitmap) {
        _state.update {
            it.copy(workingBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true))
        }
    }

    fun handleStateBeforeCaptureScreenshot() {
        shouldGoToNextScreen = true
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
                _state.update {
                    val action = EditingAction.ManualPath(event.pathDetail)
                    it.historyStack.push(action)
                    it.pathDetailStack.push(event.pathDetail) // Keep for rendering
                    it.redoStack.clear()
                    it.copy(recompositionTrigger = it.recompositionTrigger + 1)
                }
            }

            is DrawModeEvent.PerformMagicErase -> {
                handleMagicErase(event.offset)
            }
        }
    }

    private fun performUndo() {
        _state.update {
            if (it.historyStack.isNotEmpty()) {
                val action = it.historyStack.pop()
                it.redoStack.push(action)
                
                when (action) {
                    is EditingAction.ManualPath -> {
                        if (it.pathDetailStack.isNotEmpty()) {
                            it.pathDetailStack.pop()
                        }
                    }
                    is EditingAction.MagicErase -> {
                        // Restore bitmap from snapshot
                        it.workingBitmap?.let { bitmap ->
                            val canvas = android.graphics.Canvas(bitmap)
                            canvas.drawBitmap(action.prevBitmapSnapshot, 0f, 0f, null)
                        }
                    }
                }
            }
            it.copy(recompositionTrigger = it.recompositionTrigger + 1)
        }
    }

    private fun performRedo() {
        _state.update {
            if (it.redoStack.isNotEmpty()) {
                val action = it.redoStack.pop()
                it.historyStack.push(action)
                
                when (action) {
                    is EditingAction.ManualPath -> {
                        it.pathDetailStack.push(action.path)
                    }
                    is EditingAction.MagicErase -> {
                        // Redoing magic erase is harder without a "post-action" snapshot
                        // For now, we might need to re-run the algorithm or store post-snapshots
                    }
                }
            }
            it.copy(recompositionTrigger = it.recompositionTrigger + 1)
        }
    }

    private fun handleMagicErase(offset: androidx.compose.ui.geometry.Offset) = viewModelScope.launch(Dispatchers.Default) {
        val currentBitmap = _state.value.workingBitmap ?: return@launch
        val selectedTool = _state.value.selectedTool
        
        // Take a snapshot for undo
        val snapshot = currentBitmap.copy(currentBitmap.config ?: Bitmap.Config.ARGB_8888, false)
        
        // Run Flood Fill Algorithm
        val radius = selectedTool.getWidthOrNull() ?: DrawingConstants.DEFAULT_STROKE_WIDTH
        val tolerance = selectedTool.getToleranceOrNull()?.toInt() ?: 20
        
        val modifiedBitmap = MagicEraserAlgorithm.erase(
            bitmap = currentBitmap,
            startX = offset.x.toInt(),
            startY = offset.y.toInt(),
            radius = radius,
            tolerance = tolerance
        )
        
        withContext(Dispatchers.Main) {
            _state.update {
                it.historyStack.push(EditingAction.MagicErase(snapshot))
                it.redoStack.clear()
                it.copy(
                    workingBitmap = modifiedBitmap,
                    recompositionTrigger = it.recompositionTrigger + 1
                )
            }
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

            else -> {}
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