package com.thgiang.image.feature.drafts.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thgiang.image.feature.editor.model.DraftManager
import com.thgiang.image.feature.editor.model.DraftMetadata
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DraftsUiState(
    val drafts: List<DraftMetadata> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedDraftIds: Set<String> = emptySet(),
    val isSelectionMode: Boolean = false
)

@HiltViewModel
class DraftsViewModel @Inject constructor(
    private val draftManager: DraftManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(DraftsUiState())
    val uiState: StateFlow<DraftsUiState> = _uiState.asStateFlow()

    init {
        loadDrafts()
    }

    fun loadDrafts() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val drafts = draftManager.getAllDrafts()
                _uiState.value = _uiState.value.copy(drafts = drafts, isLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Lỗi khi tải bản nháp: ${e.message}",
                    isLoading = false
                )
            }
        }
    }

    fun deleteDraft(id: String) {
        viewModelScope.launch {
            draftManager.deleteDraft(id)
            loadDrafts()
        }
    }

    fun renameDraft(id: String, newName: String) {
        viewModelScope.launch {
            draftManager.renameDraft(id, newName)
            loadDrafts()
        }
    }

    fun duplicateDraft(id: String) {
        viewModelScope.launch {
            draftManager.duplicateDraft(id)
            loadDrafts()
        }
    }

    fun enterSelectionMode() {
        _uiState.value = _uiState.value.copy(isSelectionMode = true)
    }

    fun exitSelectionMode() {
        _uiState.value = _uiState.value.copy(
            isSelectionMode = false,
            selectedDraftIds = emptySet()
        )
    }

    fun toggleSelection(id: String) {
        val current = _uiState.value.selectedDraftIds
        val updated = if (id in current) current - id else current + id
        _uiState.value = _uiState.value.copy(
            selectedDraftIds = updated,
            isSelectionMode = true
        )
    }

    fun selectAll() {
        _uiState.value = _uiState.value.copy(
            selectedDraftIds = _uiState.value.drafts.map { it.id }.toSet(),
            isSelectionMode = true
        )
    }

    fun deselectAll() {
        _uiState.value = _uiState.value.copy(
            selectedDraftIds = emptySet(),
            isSelectionMode = false
        )
    }

    val isAllSelected: Boolean
        get() = _uiState.value.drafts.isNotEmpty() &&
                _uiState.value.selectedDraftIds.size == _uiState.value.drafts.size

    fun deleteSelected() {
        val ids = _uiState.value.selectedDraftIds.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            ids.forEach { id ->
                draftManager.deleteDraft(id)
            }
            _uiState.value = _uiState.value.copy(
                selectedDraftIds = emptySet(),
                isSelectionMode = false
            )
            loadDrafts()
        }
    }
}
