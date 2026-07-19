package com.thgiang.image.studio.ui.editor.tool

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.thgiang.image.studio.data.RemoteSticker
import com.thgiang.image.studio.ui.editor.theme.EditorTokens
import com.thgiang.image.studio.ui.editor.theme.LocalEditorTokens
import androidx.compose.ui.res.stringResource
import com.thgiang.image.studio.R
import kotlinx.coroutines.launch

/**
 * Bottom Sheet hiển thị thư viện nhãn dán với tab động.
 *
 * Mỗi tab tương ứng 1 folder nhãn dán trong Media Library (admin_web).
 * Khi tạo folder mới và upload nhãn dán, tab mới tự xuất hiện sau khi API sync.
 *
 * @param onStickerSelected Callback khi user chọn 1 nhãn dán; truyền URL.
 * @param onDismiss         Callback để đóng bottom sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StickerGallerySheet(
    onStickerSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    viewModel: StickerGalleryViewModel = hiltViewModel(),
    tokens: EditorTokens = LocalEditorTokens.current,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    val galleryState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }

    val tabs = galleryState.tabs
    val selectedFolder = tabs.getOrNull(selectedTab)?.folder

    LaunchedEffect(selectedFolder) {
        selectedFolder?.let { viewModel.onTabSelected(it) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = tokens.surfaceBase,
        dragHandle = null,
        shape = RoundedCornerShape(0.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Header ──────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp, top = 18.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.studio_sticker_library_title),
                    color = tokens.textPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.studio_back),
                    tint = tokens.textSecondary,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                        }
                        .padding(4.dp),
                )
            }

            when {
                galleryState.isLoadingTabs -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(36.dp),
                            strokeWidth = 2.dp,
                            color = tokens.textSecondary,
                        )
                    }
                }

                galleryState.tabsError != null && tabs.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = galleryState.tabsError ?: "",
                                color = tokens.textSecondary,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Thử lại",
                                tint = tokens.textPrimary,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) { viewModel.loadTabs() },
                            )
                        }
                    }
                }

                tabs.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Chưa có nhãn dán nào",
                            color = tokens.textSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                else -> {
                    // ── Tabs (cuộn ngang khi có nhiều folder) ─────────
                    ScrollableTabRow(
                        selectedTabIndex = selectedTab.coerceIn(0, tabs.lastIndex),
                        containerColor = tokens.surfaceBase,
                        contentColor = tokens.textPrimary,
                        edgePadding = 16.dp,
                        indicator = { tabPositions ->
                            val index = selectedTab.coerceIn(0, tabPositions.lastIndex)
                            TabRowDefaults.PrimaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[index]),
                                color = tokens.textPrimary,
                                width = tabPositions[index].contentWidth,
                            )
                        },
                        divider = {},
                    ) {
                        tabs.forEachIndexed { index, tab ->
                            Tab(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                text = {
                                    Text(
                                        text = tab.label,
                                        fontSize = 13.sp,
                                        fontWeight = if (selectedTab == index) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (selectedTab == index) tokens.textPrimary else tokens.textSecondary,
                                    )
                                },
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val currentState = selectedFolder?.let { galleryState.tabStates[it] }
                        ?: StickerTabState()

                    StickerGrid(
                        state = currentState,
                        onStickerSelected = {
                            onStickerSelected(it)
                            scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                        },
                        onLoadMore = { selectedFolder?.let { viewModel.loadMore(it) } },
                        onRefresh = { selectedFolder?.let { viewModel.refresh(it) } },
                        tokens = tokens,
                    )
                }
            }
        }
    }
}

// ── StickerGrid ──────────────────────────────────────────────────────────────

/**
 * LazyVerticalGrid 4 cột + Infinite Scroll.
 * Tự động gọi [onLoadMore] khi số phần tử còn lại < 9 (ngưỡng prefetch).
 */
@Composable
private fun StickerGrid(
    state: StickerTabState,
    onStickerSelected: (String) -> Unit,
    onLoadMore: () -> Unit,
    onRefresh: () -> Unit,
    tokens: EditorTokens,
) {
    val gridState = rememberLazyGridState()

    // Infinite scroll: kích hoạt tải thêm khi còn < 9 item
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val totalItems = gridState.layoutInfo.totalItemsCount
            lastVisible >= totalItems - 9 && totalItems > 0
        }
    }

    LaunchedEffect(shouldLoadMore, state.hasMore) {
        if (shouldLoadMore && state.hasMore && !state.isLoading && !state.isLoadingMore) {
            onLoadMore()
        }
    }

    when {
        // Đang tải trang đầu
        state.isLoading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(36.dp),
                    strokeWidth = 2.dp,
                    color = tokens.textSecondary,
                )
            }
        }

        // Lỗi và chưa có data
        state.error != null && state.stickers.isEmpty() -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = state.error,
                        color = tokens.textSecondary,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Thử lại",
                        tint = tokens.textPrimary,
                        modifier = Modifier
                            .size(36.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { onRefresh() },
                    )
                }
            }
        }

        // Không có sticker
        state.stickers.isEmpty() -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Chưa có nhãn dán nào",
                    color = tokens.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        // Grid chính
        else -> {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                state = gridState,
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    items = state.stickers,
                    key = { it.id },
                ) { sticker ->
                    GalleryThumb(
                        sticker = sticker,
                        onClick = { onStickerSelected(sticker.url) },
                        tokens = tokens,
                    )
                }

                // Footer: loading indicator khi tải thêm trang
                if (state.isLoadingMore) {
                    item(
                        key = "loading_footer",
                        span = { androidx.compose.foundation.lazy.grid.GridItemSpan(4) },
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = tokens.textSecondary,
                            )
                        }
                    }
                }

                // Footer: lỗi khi tải thêm (nhưng đã có data)
                if (state.error != null && state.stickers.isNotEmpty()) {
                    item(
                        key = "error_footer",
                        span = { androidx.compose.foundation.lazy.grid.GridItemSpan(4) },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Tải thêm thất bại.",
                                color = tokens.textSecondary,
                                fontSize = 12.sp,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Thử lại",
                                color = tokens.textPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { onRefresh() },
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Gallery Thumb ─────────────────────────────────────────────────────────────

@Composable
private fun GalleryThumb(
    sticker: RemoteSticker,
    onClick: () -> Unit,
    tokens: EditorTokens,
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF5F5F5))
            .border(1.dp, tokens.borderSubtle, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(sticker.url)
                .crossfade(true)
                .memoryCacheKey(sticker.id)
                .diskCacheKey(sticker.id)
                .build(),
            contentDescription = sticker.id,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
