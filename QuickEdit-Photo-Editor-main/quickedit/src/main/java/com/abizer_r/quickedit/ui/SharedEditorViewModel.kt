package com.abizer_r.quickedit.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.abizer_r.quickedit.ui.editorScreen.EditorScreenState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class SharedEditorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val savedStateHandle: SavedStateHandle
): ViewModel() {

    companion object {
        const val MAX_BITMAP_STACK_SIZE = 10
        const val MAX_BITMAP_DIMENSION = 2048
    }

    var useTransition = false

    private val _bitmapStack = mutableListOf<String>()
    private val _bitmapRedoStack = mutableListOf<String>()
    
    val bitmapStack: List<String> get() = _bitmapStack.toList()
    val bitmapRedoStack: List<String> get() = _bitmapRedoStack.toList()

    private val _recompositionTrigger = MutableStateFlow<Long>(0)
    val recompositionTrigger: StateFlow<Long> = _recompositionTrigger

    private val _showOverlay = MutableStateFlow(false)
    val showOverlay: StateFlow<Boolean> = _showOverlay.asStateFlow()

    private var overlayJob: Job? = null
    private var latestTimeForAddingBitmapToStack: Long = 0

    private val cacheDir = File(context.cacheDir, "editor_cache")

    private val ramCache = object : LruCache<String, Bitmap>(3) {
        override fun entryRemoved(evicted: Boolean, key: String, oldValue: Bitmap, newValue: Bitmap?) {
            // Do NOT call oldValue.recycle() here. 
            // Compose UI might still be holding a reference to the evicted bitmap (e.g., during transitions or in ImageBitmap wrappers).
            // Manually recycling it causes 'Canvas: trying to use a recycled bitmap' crashes.
            // Let the GC handle Bitmap memory.
        }
    }

    private fun saveBitmapToCache(bitmap: Bitmap): String? {
        if (!cacheDir.exists()) cacheDir.mkdirs()
        val path = File(cacheDir, "shared_${UUID.randomUUID()}.png").absolutePath
        try {
            FileOutputStream(path).use { out ->
                val success = bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                if (!success) return null
            }
            return path
        } catch (e: Throwable) {
            android.util.Log.e("SharedEditorVM", "Failed to save bitmap to cache", e)
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
            // Try clearing RAM cache to free up memory and decode again
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
            android.util.Log.e("SharedEditorVM", "Failed to delete cache file: $path", e)
        }
    }

    @Throws(Exception::class)
    fun getCurrentBitmap(): Bitmap {
        if (_bitmapStack.isEmpty()) {
            throw Exception("EmptyStackException: The bitmapStack should contain at least one bitmap")
        }
        val path = _bitmapStack.last()
        val cached = ramCache.get(path)
        if (cached != null && !cached.isRecycled) {
            return cached
        }
        val loaded = loadBitmapFromCache(path)
        ramCache.put(path, loaded)
        return loaded
    }

    fun resetStacks() {
        _bitmapStack.clear()
        _bitmapRedoStack.clear()
        ramCache.evictAll()
        try {
            if (cacheDir.exists()) {
                cacheDir.listFiles()?.forEach { it.delete() }
            }
        } catch (e: Exception) {
            android.util.Log.e("SharedEditorVM", "Failed to clean cache directory", e)
        }
    }

    fun addBitmapToStack(
        bitmap: Bitmap,
        triggerRecomposition: Boolean = false,
        addSafelyWithoutMultipleTriggers: Boolean = true
    ) {
        val currTime = System.currentTimeMillis()
        if (addSafelyWithoutMultipleTriggers) {
            val timeDiff = currTime - latestTimeForAddingBitmapToStack
            if (timeDiff < 1000) return
        }
        latestTimeForAddingBitmapToStack = currTime

        val optimizedBitmap = optimizeBitmap(bitmap)
        val path = saveBitmapToCache(optimizedBitmap) ?: return // Abort if saving failed
        ramCache.put(path, optimizedBitmap)
        
        _bitmapRedoStack.forEach { deleteCacheFile(it) }
        _bitmapRedoStack.clear()

        _bitmapStack.add(path)
        
        while (_bitmapStack.size > MAX_BITMAP_STACK_SIZE) {
            val removedPath = _bitmapStack.removeAt(0)
            deleteCacheFile(removedPath)
            ramCache.remove(removedPath)
        }

        if (triggerRecomposition) {
            _recompositionTrigger.update { it + 1 }
        }
    }

    fun undo(): Boolean {
        if (_bitmapStack.size <= 1) return false
        
        val currentPath = _bitmapStack.removeAt(_bitmapStack.lastIndex)
        _bitmapRedoStack.add(currentPath)
        return true
    }

    fun redo(): Boolean {
        if (_bitmapRedoStack.isEmpty()) return false
        
        val path = _bitmapRedoStack.removeAt(_bitmapRedoStack.lastIndex)
        _bitmapStack.add(path)
        return true
    }

    private fun optimizeBitmap(bitmap: Bitmap): Bitmap {
        val maxDim = maxOf(bitmap.width, bitmap.height)
        if (maxDim <= MAX_BITMAP_DIMENSION) {
            return if (bitmap.config == Bitmap.Config.ARGB_8888 && bitmap.isMutable) {
                bitmap
            } else {
                bitmap.copy(Bitmap.Config.ARGB_8888, true)
            }
        }
        
        val scale = MAX_BITMAP_DIMENSION.toFloat() / maxDim
        val newWidth = (bitmap.width * scale).toInt()
        val newHeight = (bitmap.height * scale).toInt()
        
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun recycleSafely(bitmap: Bitmap?) {
        bitmap?.let {
            if (!it.isRecycled) it.recycle()
        }
    }

    fun updateStacksFromEditorState(finalEditorState: EditorScreenState) {
        val newPaths = (finalEditorState.bitmapStack + finalEditorState.bitmapRedoStack).toSet()
        val oldPaths = (_bitmapStack + _bitmapRedoStack).toSet()
        oldPaths.forEach { path ->
            if (path !in newPaths) {
                deleteCacheFile(path)
                ramCache.remove(path)
            }
        }
        _bitmapStack.clear()
        _bitmapStack.addAll(finalEditorState.bitmapStack)
        _bitmapRedoStack.clear()
        _bitmapRedoStack.addAll(finalEditorState.bitmapRedoStack)
    }

    override fun onCleared() {
        super.onCleared()
        overlayJob?.cancel()
        resetStacks()
    }

    fun triggerOverlay() {
        _showOverlay.value = true
        overlayJob?.cancel()
        overlayJob = viewModelScope.launch {
            delay(2000)
            _showOverlay.value = false
        }
    }
}