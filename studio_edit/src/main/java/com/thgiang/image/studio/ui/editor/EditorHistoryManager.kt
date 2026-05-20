package com.thgiang.image.studio.ui.editor

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Undo/Redo v2 - Deduplication, memory-efficient, bounded memory
 * 
 * Cải tiến:
 * 1. Deduplication: không push nếu state giống hệt current
 * 2. Memory-efficient: dùng ring buffer thay vì ArrayDeque (tránh allocation)
 * 3. Batch compression: merge các micro-changes trong 500ms window
 */
class EditorHistoryManager(private val maxHistory: Int = 30) {
    
    private val history = arrayOfNulls<TransformSnapshot>(maxHistory)
    private var head = 0
    private var tail = 0
    private var currentIndex = -1
    private var size = 0
    
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()
    
    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()
    
    private var lastPushTime = 0L
    private var pendingSnapshot: TransformSnapshot? = null
    private val mergeWindowMs = 400L

    /**
     * Push new snapshot với deduplication và batching
     */
    fun push(snapshot: TransformSnapshot) {
        val now = System.currentTimeMillis()
        
        // Deduplication: skip if identical to current
        if (currentIndex >= 0) {
            val current = get(currentIndex)
            if (current != null && current == snapshot) {
                return
            }
        }
        
        // Batch compression: merge with pending if within window
        if (pendingSnapshot != null && now - lastPushTime < mergeWindowMs) {
            // Replace pending with new (merge micro-changes)
            pendingSnapshot = snapshot
            lastPushTime = now
            return
        }
        
        // Flush pending if exists
        pendingSnapshot?.let { flushPush(it) }
        
        // Store as pending for potential merge
        pendingSnapshot = snapshot
        lastPushTime = now
    }
    
    /**
     * Force flush pending snapshot (gọi khi gesture end)
     */
    fun flush() {
        pendingSnapshot?.let {
            flushPush(it)
            pendingSnapshot = null
        }
    }
    
    private fun flushPush(snapshot: TransformSnapshot) {
        // Xóa redo stack
        if (currentIndex >= 0 && currentIndex != (tail - 1 + maxHistory) % maxHistory) {
            // Clear from current+1 to tail
            var i = (currentIndex + 1) % maxHistory
            while (i != tail) {
                history[i] = null
                i = (i + 1) % maxHistory
            }
            tail = (currentIndex + 1) % maxHistory
            size = (currentIndex - head + 1).let { if (it < 0) it + maxHistory else it }
        }
        
        // Check if full
        if (size >= maxHistory) {
            head = (head + 1) % maxHistory
        } else {
            size++
        }
        
        history[tail] = snapshot
        currentIndex = tail
        tail = (tail + 1) % maxHistory
        
        updateStates()
    }
    
    fun undo(): TransformSnapshot? {
        flush() // Ensure all pending changes are committed
        
        if (currentIndex >= 0 && currentIndex != head) {
            currentIndex = if (currentIndex == 0) maxHistory - 1 else currentIndex - 1
            updateStates()
            return get(currentIndex)
        }
        return null
    }
    
    fun redo(): TransformSnapshot? {
        flush()
        
        val nextIndex = (currentIndex + 1) % maxHistory
        if (currentIndex >= 0 && nextIndex != tail && history[nextIndex] != null) {
            currentIndex = nextIndex
            updateStates()
            return get(currentIndex)
        }
        return null
    }
    
    fun clear() {
        for (i in history.indices) {
            history[i] = null
        }
        head = 0
        tail = 0
        currentIndex = -1
        size = 0
        pendingSnapshot = null
        updateStates()
    }
    
    private fun get(index: Int): TransformSnapshot? {
        return if (index in 0 until maxHistory) history[index] else null
    }
    
    private fun updateStates() {
        _canUndo.value = currentIndex >= 0 && currentIndex != head
        _canRedo.value = currentIndex >= 0 && (currentIndex + 1) % maxHistory != tail && history[(currentIndex + 1) % maxHistory] != null
    }
    
    fun getHistorySize(): Int = size
    fun getCurrentIndex(): Int = currentIndex
}
