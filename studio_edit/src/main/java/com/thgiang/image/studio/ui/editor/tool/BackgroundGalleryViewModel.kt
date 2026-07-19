package com.thgiang.image.studio.ui.editor.tool

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thgiang.image.studio.data.BackgroundRemoteRepository
import com.thgiang.image.studio.data.BackgroundTabInfo
import com.thgiang.image.studio.data.RemoteBackground
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

data class BackgroundTabState(
    val backgrounds: List<RemoteBackground> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val error: String? = null,
    val currentPage: Int = 0,
)

data class BackgroundGalleryUiState(
    val tabs: List<BackgroundTabInfo> = emptyList(),
    val tabStates: Map<String, BackgroundTabState> = emptyMap(),
    val isLoadingTabs: Boolean = true,
    val tabsError: String? = null,
)

@HiltViewModel
class BackgroundGalleryViewModel @Inject constructor(
    private val backgroundRepository: BackgroundRemoteRepository,
) : ViewModel() {

    companion object {
        private const val TAG = "BackgroundGalleryVM"
        const val PAGE_LIMIT = 30
    }

    private val _uiState = MutableStateFlow(BackgroundGalleryUiState())
    val uiState: StateFlow<BackgroundGalleryUiState> = _uiState.asStateFlow()

    private val loadJobs = ConcurrentHashMap<String, Job>()

    init {
        loadTabs()
    }

    fun loadTabs() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoadingTabs = true, tabsError = null) }
            try {
                val tabs = backgroundRepository.fetchBackgroundTabs()
                _uiState.update { state ->
                    state.copy(
                        tabs = tabs,
                        isLoadingTabs = false,
                        tabStates = tabs.associate { tab ->
                            tab.folder to (state.tabStates[tab.folder] ?: BackgroundTabState())
                        },
                    )
                }
                tabs.firstOrNull()?.folder?.let { loadMore(it) }
            } catch (e: Exception) {
                Log.e(TAG, "loadTabs failed", e)
                _uiState.update {
                    it.copy(
                        isLoadingTabs = false,
                        tabsError = "Không thể tải danh mục nền.",
                    )
                }
            }
        }
    }

    fun loadMore(folder: String) {
        val state = _uiState.value.tabStates[folder] ?: BackgroundTabState()
        if (state.isLoading || state.isLoadingMore) return
        if (!state.hasMore && state.currentPage > 0) return

        val nextPage = state.currentPage + 1

        loadJobs[folder]?.cancel()
        loadJobs[folder] = viewModelScope.launch(Dispatchers.IO) {
            try {
                updateTabState(folder) {
                    if (nextPage == 1) it.copy(isLoading = true, error = null)
                    else it.copy(isLoadingMore = true, error = null)
                }

                val result = backgroundRepository.fetchPage(folder, nextPage, PAGE_LIMIT)
                updateTabState(folder) { current ->
                    current.copy(
                        backgrounds = (current.backgrounds + result.backgrounds).distinctBy { it.id },
                        isLoading = false,
                        isLoadingMore = false,
                        hasMore = result.hasMore,
                        currentPage = nextPage,
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadMore failed: folder=$folder page=$nextPage", e)
                updateTabState(folder) {
                    it.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        error = "Không thể tải thêm nền.",
                    )
                }
            }
        }
    }

    fun refresh(folder: String) {
        backgroundRepository.invalidateCache()
        updateTabState(folder) { BackgroundTabState() }
        loadMore(folder)
    }

    fun onTabSelected(folder: String) {
        val state = _uiState.value.tabStates[folder] ?: return
        if (state.backgrounds.isEmpty() && !state.isLoading) {
            loadMore(folder)
        }
    }

    private fun updateTabState(folder: String, transform: (BackgroundTabState) -> BackgroundTabState) {
        _uiState.update { state ->
            val current = state.tabStates[folder] ?: BackgroundTabState()
            state.copy(tabStates = state.tabStates + (folder to transform(current)))
        }
    }
}
