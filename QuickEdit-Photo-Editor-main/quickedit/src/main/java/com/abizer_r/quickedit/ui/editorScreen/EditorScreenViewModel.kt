package com.abizer_r.quickedit.ui.editorScreen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class EditorScreenViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _state = MutableStateFlow(EditorScreenState())
    val state: StateFlow<EditorScreenState> = _state

    private var overlayJob: Job? = null

    private val cacheDir = File(context.cacheDir, "editor_cache")

    private val ramCache = object : LruCache<String, Bitmap>(3) {
        override fun entryRemoved(evicted: Boolean, key: String, oldValue: Bitmap, newValue: Bitmap?) {
            // Do NOT call oldValue.recycle() here. Let GC handle it.
        }
    }

    private fun saveBitmapToCache(bitmap: Bitmap): String? {
        if (!cacheDir.exists()) cacheDir.mkdirs()
        val path = File(cacheDir, "local_${UUID.randomUUID()}.png").absolutePath
        try {
            FileOutputStream(path).use { out ->
                val success = bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                if (!success) return null
            }
            return path
        } catch (e: Throwable) {
            android.util.Log.e("EditorScreenVM", "Failed to save bitmap to cache", e)
            deleteCacheFile(path)
            return null
        }
    }

    private fun loadBitmapFromCache(path: String): Bitmap {
        val file = File(path)
        if (!file.exists()) {
            throw IllegalStateException("Cache file does not exist: $path")
        }
        var bitmap = BitmapFactory.decodeFile(path)
        if (bitmap == null) {
            ramCache.evictAll()
            System.gc()
            bitmap = BitmapFactory.decodeFile(path)
            if (bitmap == null) {
                throw IllegalStateException("Failed to decode cache file (corrupted or OOM): $path")
            }
        }
        return bitmap
    }

    private fun deleteCacheFile(path: String) {
        try {
            val file = File(path)
            if (file.exists()) file.delete()
        } catch (e: Exception) {
            android.util.Log.e("EditorScreenVM", "Failed to delete cache file: $path", e)
        }
    }

    fun getCurrentBitmap(): Bitmap {
        val stack = _state.value.bitmapStack
        if (stack.isEmpty()) {
            throw Exception("EmptyStackException")
        }
        val path = stack.last()
        val cached = ramCache.get(path)
        if (cached != null && !cached.isRecycled) {
            return cached
        }
        val loaded = loadBitmapFromCache(path)
        ramCache.put(path, loaded)
        return loaded
    }

    fun undoEnabled() = _state.value.bitmapStack.size > 1
    fun redoEnabled() = _state.value.bitmapRedoStack.isNotEmpty()

    fun addBitmapToStack(bitmap: Bitmap) {
        val path = saveBitmapToCache(bitmap) ?: return
        ramCache.put(path, bitmap)
        
        _state.update { current ->
            current.bitmapRedoStack.forEach { deleteCacheFile(it) }
            val newStack = current.bitmapStack.toMutableList()
            newStack.add(path)
            current.copy(
                bitmapStack = newStack,
                bitmapRedoStack = emptyList(),
                recompositionTrigger = current.recompositionTrigger + 1
            )
        }
    }

    fun updateInitialState(initialState: EditorScreenState) {
        _state.update { current ->
            val newPaths = (initialState.bitmapStack + initialState.bitmapRedoStack).toSet()
            val oldPaths = (current.bitmapStack + current.bitmapRedoStack).toSet()
            oldPaths.forEach { path ->
                if (path !in newPaths) {
                    deleteCacheFile(path)
                    ramCache.remove(path)
                }
            }
            initialState
        }
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

    override fun onCleared() {
        super.onCleared()
        overlayJob?.cancel()
        ramCache.evictAll()
    }
}