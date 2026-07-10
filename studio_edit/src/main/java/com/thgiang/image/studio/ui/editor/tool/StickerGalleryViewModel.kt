package com.thgiang.image.studio.ui.editor.tool

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thgiang.image.studio.data.RemoteSticker
import com.thgiang.image.studio.data.StickerRemoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI State cho một Tab của Gallery (meme hoặc decor).
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
 * ViewModel quản lý phân trang cho StickerGallerySheet.
 *
 * Tách biệt với ThemeplateEditorViewModel để Gallery Sheet
 * có vòng đời riêng và không ảnh hưởng đến editor state.
 *
 * Cơ chế pagination:
 *  - [loadMore] tăng page và append vào list hiện có.
 *  - Các trang đã tải được cache trong [StickerPageCache] (10 phút TTL).
 *  - Khi đổi tab, data của tab kia được giữ nguyên trong memory.
 */
@HiltViewModel
class StickerGalleryViewModel @Inject constructor(
    private val stickerRepository: StickerRemoteRepository,
) : ViewModel() {

    companion object {
        private const val TAG = "StickerGalleryVM"
        const val PAGE_LIMIT = 30
    }

    private val _memeState = MutableStateFlow(StickerTabState())
    val memeState: StateFlow<StickerTabState> = _memeState.asStateFlow()

    private val _decorState = MutableStateFlow(StickerTabState())
    val decorState: StateFlow<StickerTabState> = _decorState.asStateFlow()

    private var memeLoadJob: Job? = null
    private var decorLoadJob: Job? = null

    init {
        // Tải trang đầu tiên của cả 2 tab khi ViewModel được tạo
        loadMore(StickerRemoteRepository.FOLDER_MEME)
        loadMore(StickerRemoteRepository.FOLDER_DECOR)
    }

    /**
     * Tải trang tiếp theo cho folder chỉ định.
     * Gọi khi user cuộn gần đến cuối danh sách.
     * Tự động bỏ qua nếu đang load hoặc không còn trang nào.
     */
    fun loadMore(folder: String) {
        val stateFlow = stateFlowFor(folder)
        val state = stateFlow.value

        if (state.isLoading || state.isLoadingMore) return
        if (!state.hasMore && state.currentPage > 0) return

        val nextPage = state.currentPage + 1

        jobFor(folder)?.cancel()
        setJobFor(
            folder,
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    // Đánh dấu trạng thái loading
                    stateFlow.update {
                        if (nextPage == 1) it.copy(isLoading = true, error = null)
                        else it.copy(isLoadingMore = true, error = null)
                    }

                    val result = stickerRepository.fetchPage(folder, nextPage, PAGE_LIMIT)
                    Log.d(TAG, "loadMore $folder page=$nextPage size=${result.stickers.size}")

                    stateFlow.update { current ->
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
                    stateFlow.update {
                        it.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            error = "Không thể tải thêm nhãn dán.",
                        )
                    }
                }
            },
        )
    }

    /** Xóa cache và tải lại từ trang 1 */
    fun refresh(folder: String) {
        stickerRepository.invalidateCache()
        val stateFlow = stateFlowFor(folder)
        stateFlow.update { StickerTabState() }
        loadMore(folder)
    }

    // ─── Helpers ──────────────────────────────────────────────────

    private fun stateFlowFor(folder: String): MutableStateFlow<StickerTabState> =
        if (folder == StickerRemoteRepository.FOLDER_MEME) _memeState else _decorState

    private fun jobFor(folder: String): Job? =
        if (folder == StickerRemoteRepository.FOLDER_MEME) memeLoadJob else decorLoadJob

    private fun setJobFor(folder: String, job: Job) {
        if (folder == StickerRemoteRepository.FOLDER_MEME) memeLoadJob = job
        else decorLoadJob = job
    }
}
