package com.thgiang.image.studio.ui.editor.tool

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.thgiang.image.studio.data.RemoteBackground
import com.thgiang.image.studio.ui.editor.ThemeplateEditorViewModel
import com.thgiang.image.studio.ui.editor.theme.EditorTokens
import com.thgiang.image.studio.ui.editor.theme.LocalEditorTokens
import com.thgiang.image.studio.ui.editor.theme.MotionTokens

@Composable
internal fun BackgroundPicker(
    onBackgroundSelected: (String) -> Unit,
    onShowGallery: () -> Unit,
    viewModel: ThemeplateEditorViewModel = hiltViewModel(),
    tokens: EditorTokens = LocalEditorTokens.current,
) {
    val backgroundState by viewModel.backgroundState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadBackgroundPreview()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.studio_tool_background),
                color = tokens.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (backgroundState.previewError) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = stringResource(R.string.studio_back_button),
                        tint = tokens.textSecondary,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { viewModel.invalidateBackgroundCache() },
                    )
                }

                if (backgroundState.preview.isNotEmpty()) {
                    Surface(
                        modifier = Modifier.clickable(
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

        AnimatedVisibility(
            visible = true,
            enter = expandVertically(animationSpec = MotionTokens.springPanel()),
            exit = shrinkVertically(animationSpec = MotionTokens.springPanel()),
        ) {
            when {
                backgroundState.isLoadingPreview -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = tokens.textSecondary,
                        )
                    }
                }

                backgroundState.previewError -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.studio_background_load_error),
                            color = tokens.textSecondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                backgroundState.preview.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.studio_background_empty),
                        color = tokens.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                else -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(bottom = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        backgroundState.preview.forEach { background ->
                            BackgroundPreviewThumb(
                                background = background,
                                onClick = { onBackgroundSelected(background.url) },
                                tokens = tokens,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BackgroundPreviewThumb(
    background: RemoteBackground,
    onClick: () -> Unit,
    tokens: EditorTokens,
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .size(width = 72.dp, height = 72.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFF8F8F8))
            .border(1.dp, tokens.borderSubtle, RoundedCornerShape(10.dp))
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
