package com.thgiang.image.studio.ui.editor.tool

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardDoubleArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.thgiang.image.studio.R
import com.thgiang.image.studio.data.RemoteSticker
import com.thgiang.image.studio.ui.editor.ThemeplateEditorViewModel
import com.thgiang.image.studio.ui.editor.theme.EditorTokens
import com.thgiang.image.studio.ui.editor.theme.LocalEditorTokens
import com.thgiang.image.studio.ui.editor.theme.MotionTokens

/**
 * Thanh nhãn dán ngang nhanh (Quick Sticker Strip).
 *
 * Hiển thị 20 nhãn dán (10 meme + 10 decor) theo hàng ngang cuộn được.
 * Kèm nút "Xem thêm" để mở màn hình thư viện đầy đủ [StickerGallerySheet].
 *
 * Dữ liệu được tải từ [ThemeplateEditorViewModel.loadStickerPreview] và
 * cache lại trong session — Composable này chỉ observe state, không tự gọi network.
 */
@Composable
internal fun StickerPicker(
    onStickerSelected: (String) -> Unit,
    onShowGallery: () -> Unit,
    viewModel: ThemeplateEditorViewModel = hiltViewModel(),
    tokens: EditorTokens = LocalEditorTokens.current,
) {
    val stickerState by viewModel.stickerState.collectAsState()

    // Kích hoạt tải preview khi composable này lần đầu xuất hiện
    LaunchedEffect(Unit) {
        viewModel.loadStickerPreview()
    }

    // Trộn: meme trước, decor sau → 20 sticker theo hàng ngang
    val previewList = remember(stickerState.previewMeme, stickerState.previewDecor) {
        stickerState.previewMeme + stickerState.previewDecor
    }

    val expanded = true

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ── Header row ─────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Tiêu đề
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = stringResource(R.string.studio_tool_sticker),
                    color = tokens.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            // Nút refresh khi lỗi + nút Xem thêm
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (stickerState.previewError) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = stringResource(R.string.studio_back_button),
                        tint = tokens.textSecondary,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { viewModel.invalidateStickerCache() },
                    )
                }

                // Nút Xem thêm — chỉ hiện khi có data
                if (previewList.isNotEmpty()) {
                    Surface(
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { onShowGallery() },
                        shape = RoundedCornerShape(6.dp),
                        color = tokens.surfaceElevated,
                        tonalElevation = 0.dp,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.studio_action_view_more),
                                color = tokens.textSecondary,
                                fontSize = 12.sp,
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = tokens.textSecondary,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }
        }

        // ── Content ────────────────────────────────────────────────
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(animationSpec = MotionTokens.springPanel()),
            exit = shrinkVertically(animationSpec = MotionTokens.springPanel()),
        ) {
            when {
                // Đang tải
                stickerState.isLoadingPreview -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = tokens.textSecondary,
                        )
                    }
                }

                // Lỗi tải
                stickerState.previewError -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Không thể tải nhãn dán. Nhấn ↻ để thử lại.",
                            color = tokens.textSecondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                // Không có dữ liệu
                previewList.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.studio_gallery_no_templates),
                        color = tokens.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                // Hiển thị hàng ngang 20 sticker
                else -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(bottom = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        previewList.forEach { sticker ->
                            RemoteStickerThumb(
                                sticker = sticker,
                                onClick = { onStickerSelected(sticker.url) },
                                tokens = tokens,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Private sub-components ────────────────────────────────────────────────────

@Composable
private fun RemoteStickerThumb(
    sticker: RemoteSticker,
    onClick: () -> Unit,
    tokens: EditorTokens = LocalEditorTokens.current,
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .size(54.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFF8F8F8))
            .border(1.dp, tokens.borderSubtle, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
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
