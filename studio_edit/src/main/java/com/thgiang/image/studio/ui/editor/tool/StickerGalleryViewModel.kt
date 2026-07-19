package com.thgiang.image.studio.ui.editor.tool

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thgiang.image.studio.data.RemoteSticker
import com.thgiang.image.studio.data.StickerRemoteRepository
import com.thgiang.image.studio.data.StickerTabInfo
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

/**
 * UI State cho một Tab của Gallery.
 */
data class StickerTabState(
    val stickers: List<RemoteSticker> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val error: String? = null,
    val currentPage: Int = 0,
)

/**
 * UI State tổng cho StickerGallerySheet — tabs động từ Media Library.
 */
data class StickerGalleryUiState(
    val tabs: List<StickerTabInfo> = emptyList(),
    val tabStates: Map<String, StickerTabState> = emptyMap(),
    val isLoadingTabs: Boolean = true,
    val tabsError: String? = null,
)

/**
 * ViewModel quản lý phân trang cho StickerGallerySheet.
 *
 * Tab được tạo tự động từ `/api/v1/stickers/folders` — mỗi folder nhãn dán
 * trong Media Library (admin_web) tương ứng 1 tab trên app.
 */
@HiltViewModel
class StickerGalleryViewModel @Inject constructor(
    private val stickerRepository: StickerRemoteRepository,
) : ViewModel() {

    companion object {
        private const val TAG = "StickerGalleryVM"
        const val PAGE_LIMIT = 30
    }

    private val _uiState = MutableStateFlow(StickerGalleryUiState())
    val uiState: StateFlow<StickerGalleryUiState> = _uiState.asStateFlow()

    private val loadJobs = ConcurrentHashMap<String, Job>()

    init {
        loadTabs()
    }

    /** Tải danh sách tab từ Media Library, sau đó prefetch tab đầu tiên. */
    fun loadTabs() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoadingTabs = true, tabsError = null) }
            try {
                val tabs = stickerRepository.fetchStickerTabs()
                _uiState.update { state ->
                    state.copy(
                        tabs = tabs,
                        isLoadingTabs = false,
                        tabStates = tabs.associate { tab ->
                            tab.folder to (state.tabStates[tab.folder] ?: StickerTabState())
                        },
                    )
                }
                tabs.firstOrNull()?.folder?.let { loadMore(it) }
            } catch (e: Exception) {
                Log.e(TAG, "loadTabs failed", e)
                _uiState.update {
                    it.copy(
                        isLoadingTabs = false,
                        tabsError = "Không thể tải danh mục nhãn dán.",
                    )
                }
            }
        }
    }

    /**
     * Tải trang tiếp theo cho folder chỉ định.
     * Gọi khi user cuộn gần đến cuối danh sách hoặc chuyển tab lần đầu.
     */
    fun loadMore(folder: String) {
        val state = _uiState.value.tabStates[folder] ?: StickerTabState()
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

                val result = stickerRepository.fetchPage(folder, nextPage, PAGE_LIMIT)
                Log.d(TAG, "loadMore $folder page=$nextPage size=${result.stickers.size}")

                updateTabState(folder) { current ->
                    current.copy(
                        stickers = (current.stickers + result.stickers).distinctBy { it.id },
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
                        error = "Không thể tải thêm nhãn dán.",
                    )
                }
            }
        }
    }

    /** Xóa cache và tải lại từ trang 1 */
    fun refresh(folder: String) {
        stickerRepository.invalidateCache()
        updateTabState(folder) { StickerTabState() }
        loadMore(folder)
    }

    /** Gọi khi user chọn tab — lazy load nếu tab chưa có data */
    fun onTabSelected(folder: String) {
        val state = _uiState.value.tabStates[folder] ?: return
        if (state.stickers.isEmpty() && !state.isLoading) {
            loadMore(folder)
        }
    }

    private fun updateTabState(folder: String, transform: (StickerTabState) -> StickerTabState) {
        _uiState.update { state ->
            val current = state.tabStates[folder] ?: StickerTabState()
            state.copy(tabStates = state.tabStates + (folder to transform(current)))
        }
    }
}
