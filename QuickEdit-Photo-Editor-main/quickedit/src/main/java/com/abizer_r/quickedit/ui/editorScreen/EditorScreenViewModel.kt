package com.abizer_r.quickedit.ui.editorScreen

import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope
import javax.inject.Inject

@HiltViewModel
class EditorScreenViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _state = MutableStateFlow(EditorScreenState())
    val state: StateFlow<EditorScreenState> = _state

    private var overlayJob: Job? = null

    fun getCurrentBitmap(): Bitmap {
        val stack = _state.value.bitmapStack
        if (stack.isEmpty()) {
            throw Exception("EmptyStackException")
        }
        return stack.last()
    }

    fun undoEnabled() = _state.value.bitmapStack.size > 1
    fun redoEnabled() = _state.value.bitmapRedoStack.isNotEmpty()

    fun addBitmapToStack(bitmap: Bitmap) {
        _state.update { current ->
            val newStack = current.bitmapStack.toMutableList()
            newStack.add(bitmap)
            current.copy(
                bitmapStack = newStack,
                bitmapRedoStack = emptyList(),
                recompositionTrigger = current.recompositionTrigger + 1
            )
        }
    }

    fun updateInitialState(initialState: EditorScreenState) {
        _state.update { initialState }
    }

    fun onUndo() {
        _state.update { current ->
            if (!undoEnabled()) return@update current
            
            val newStack = current.bitmapStack.toMutableList()
            val newRedo = current.bitmapRedoStack.toMutableList()
            
            val removed = newStack.removeAt(newStack.lastIndex)
            newRedo.add(removed)
            
            current.copy(
                bitmapStack = newStack,
                bitmapRedoStack = newRedo,
                recompositionTrigger = current.recompositionTrigger + 1
            )
        }
    }

    fun onRedo() {
        _state.update { current ->
            if (!redoEnabled()) return@update current
            
            val newStack = current.bitmapStack.toMutableList()
            val newRedo = current.bitmapRedoStack.toMutableList()
            
            val restored = newRedo.removeAt(newRedo.lastIndex)
            newStack.add(restored)
            
            current.copy(
                bitmapStack = newStack,
                bitmapRedoStack = newRedo,
                recompositionTrigger = current.recompositionTrigger + 1
            )
        }
    }

    fun triggerOverlay() {
        _state.update { it.copy(showOverlay = true) }
        overlayJob?.cancel()
        overlayJob = viewModelScope.launch {
            delay(2000)
            _state.update { it.copy(showOverlay = false) }
        }
    }
}