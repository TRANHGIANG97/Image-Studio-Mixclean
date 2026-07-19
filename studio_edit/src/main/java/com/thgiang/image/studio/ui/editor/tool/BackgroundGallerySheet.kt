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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.thgiang.image.studio.R
import com.thgiang.image.studio.data.RemoteBackground
import com.thgiang.image.studio.ui.editor.theme.EditorTokens
import com.thgiang.image.studio.ui.editor.theme.LocalEditorTokens
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BackgroundGallerySheet(
    onBackgroundSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    viewModel: BackgroundGalleryViewModel = hiltViewModel(),
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp, top = 18.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.studio_background_library_title),
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
                    GalleryMessage(
                        message = galleryState.tabsError ?: "",
                        onRetry = { viewModel.loadTabs() },
                        tokens = tokens,
                    )
                }

                tabs.isEmpty() -> {
                    GalleryMessage(
                        message = stringResource(R.string.studio_background_empty),
                        tokens = tokens,
                    )
                }

                else -> {
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
                        ?: BackgroundTabState()

                    BackgroundGrid(
                        state = currentState,
                        onBackgroundSelected = {
                            onBackgroundSelected(it)
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

@Composable
private fun GalleryMessage(
    message: String,
    onRetry: (() -> Unit)? = null,
    tokens: EditorTokens,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = message,
                color = tokens.textSecondary,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (onRetry != null) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Thử lại",
                    tint = tokens.textPrimary,
                    modifier = Modifier
                        .size(36.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onRetry() },
                )
            }
        }
    }
}

@Composable
private fun BackgroundGrid(
    state: BackgroundTabState,
    onBackgroundSelected: (String) -> Unit,
    onLoadMore: () -> Unit,
    onRefresh: () -> Unit,
    tokens: EditorTokens,
) {
    val gridState = rememberLazyGridState()

    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val totalItems = gridState.layoutInfo.totalItemsCount
            lastVisible >= totalItems - 6 && totalItems > 0
        }
    }

    LaunchedEffect(shouldLoadMore, state.hasMore) {
        if (shouldLoadMore && state.hasMore && !state.isLoading && !state.isLoadingMore) {
            onLoadMore()
        }
    }

    when {
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

        state.error != null && state.backgrounds.isEmpty() -> {
            GalleryMessage(message = state.error ?: "", onRetry = onRefresh, tokens = tokens)
        }

        state.backgrounds.isEmpty() -> {
            GalleryMessage(
                message = stringResource(R.string.studio_background_empty),
                tokens = tokens,
            )
        }

        else -> {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                state = gridState,
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    items = state.backgrounds,
                    key = { it.id },
                ) { background ->
                    BackgroundThumb(
                        background = background,
                        onClick = { onBackgroundSelected(background.url) },
                        tokens = tokens,
                    )
                }

                if (state.isLoadingMore) {
                    item(
                        key = "loading_footer",
                        span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) },
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
            }
        }
    }
}

@Composable
private fun BackgroundThumb(
    background: RemoteBackground,
    onClick: () -> Unit,
    tokens: EditorTokens,
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF5F5F5))
            .border(1.dp, tokens.borderSubtle, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(background.url)
                .crossfade(true)
                .memoryCacheKey(background.id)
                .diskCacheKey(background.id)
                .build(),
            contentDescription = background.id,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
