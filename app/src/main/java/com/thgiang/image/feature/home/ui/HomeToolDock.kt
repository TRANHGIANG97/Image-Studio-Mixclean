package com.thgiang.image.feature.home.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AutoAwesomeMotion
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.thgiang.image.R
import com.thgiang.image.core.design.theme.HomeUiTokens
import com.thgiang.image.core.design.theme.ImageDesign
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.draw.shadow

private data class QuickTool(
    val title: String,
    val iconAsset: String? = null,
    val iconVector: androidx.compose.ui.graphics.vector.ImageVector? = null,
    val locked: Boolean,
    val badgeCount: Int = 0,
    val onClick: () -> Unit
)

@Composable
fun AiToolDock(
    isPremium: Boolean,
    onBatchRemove: () -> Unit,
    onOpenEffects: () -> Unit = {},
    onOpenStudioTool: () -> Unit = {},
    onOpenMagicTool: () -> Unit = {},
    onOpenRemoveBgEditor: () -> Unit = {},
    onLockedClick: () -> Unit,
    hasDraft: Boolean = false,
    draftCount: Int = 0,
    onRestoreDraft: () -> Unit = {},
    useHomeDarkStyle: Boolean = false,
    onOpenBackgroundPresetsTool: () -> Unit = {}
) {
    val tools = listOf(
        QuickTool(stringResource(R.string.home_dock_pick_image), iconVector = Icons.Outlined.Image, locked = false, onClick = onOpenRemoveBgEditor),
        QuickTool(stringResource(R.string.home_dock_batch), iconVector = Icons.Outlined.AutoAwesomeMotion, locked = false, onClick = onBatchRemove),
        QuickTool(
            title = stringResource(R.string.home_draft),
            iconVector = Icons.Outlined.Folder,
            locked = false,
            badgeCount = if (hasDraft) draftCount.coerceAtLeast(1) else draftCount,
            onClick = onRestoreDraft
        ),
        QuickTool(stringResource(R.string.home_dock_effects), iconVector = Icons.Outlined.AutoFixHigh, locked = false, onClick = onOpenEffects),
        QuickTool(stringResource(R.string.home_background_presets), iconVector = Icons.Outlined.Wallpaper, locked = false, onClick = onOpenBackgroundPresetsTool),
        QuickTool(stringResource(R.string.home_dock_studio), iconVector = Icons.Outlined.Palette, locked = false, onClick = onOpenStudioTool),
        QuickTool(stringResource(R.string.home_dock_magic), iconVector = Icons.Outlined.AutoAwesome, locked = false, onClick = onOpenMagicTool)
    )

    val isDark = useHomeDarkStyle || isSystemInDarkTheme()
    var isExpanded by remember { mutableStateOf(false) }

    if (tools.isEmpty()) return

    val columns = 5
    val primaryTools = tools.take(columns - 1)
    val expandedTools = remember(tools) {
        tools.drop(columns - 1)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.home_quick_tools),
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                letterSpacing = 0.5.sp
            ),
            color = if (isDark) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = HomeUiTokens.outerPadding, vertical = 8.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = HomeUiTokens.outerPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val itemWidth = maxWidth / columns

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    primaryTools.forEach { tool ->
                        DockToolItem(
                            modifier = Modifier.width(itemWidth - 4.dp),
                            title = tool.title,
                            iconAsset = tool.iconAsset,
                            iconVector = tool.iconVector,
                            locked = tool.locked,
                            badgeCount = tool.badgeCount,
                            onClick = { if (tool.locked) onLockedClick() else tool.onClick() },
                            isDark = isDark,
                            useHomeDarkStyle = useHomeDarkStyle
                        )
                    }

                    DockToolItem(
                        modifier = Modifier.width(itemWidth - 4.dp),
                        title = stringResource(R.string.home_dock_more),
                        iconVector = if (isExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        locked = false,
                        onClick = { isExpanded = !isExpanded },
                        isDark = isDark,
                        useHomeDarkStyle = useHomeDarkStyle
                    )
                }
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(2.dp))
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val itemWidth = maxWidth / columns

                    expandedTools.chunked(columns).forEachIndexed { index, row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            row.forEach { tool ->
                                DockToolItem(
                                    modifier = Modifier.width(itemWidth - 4.dp),
                                    title = tool.title,
                                    iconAsset = tool.iconAsset,
                                    iconVector = tool.iconVector,
                                    locked = tool.locked,
                                    badgeCount = tool.badgeCount,
                                    onClick = { if (tool.locked) onLockedClick() else tool.onClick() },
                                    isDark = isDark,
                                    useHomeDarkStyle = useHomeDarkStyle
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DockToolItem(
    modifier: Modifier = Modifier,
    title: String,
    iconAsset: String? = null,
    iconVector: androidx.compose.ui.graphics.vector.ImageVector? = null,
    locked: Boolean,
    badgeCount: Int = 0,
    onClick: () -> Unit,
    isDark: Boolean = false,
    useHomeDarkStyle: Boolean = false
) {
    val context = LocalContext.current
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = tween(ImageDesign.motion.quick),
        label = "dockScale"
    )

    Column(
        modifier = modifier
            .scale(scale)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(48.dp)
            ) {
                if (iconAsset != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data("file:///android_asset/icon_quicktools/$iconAsset")
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        contentScale = ContentScale.Fit
                    )
                } else if (iconVector != null) {
                    Icon(
                        imageVector = iconVector,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = if (isDark) Color(0xFF26C6DA) else Color(0xFF4A443D)
                    )
                }
            }

            if (badgeCount > 0) {
                Box(
                    modifier = Modifier
                        .offset(x = 4.dp, y = (-4).dp)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEF4444)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (badgeCount > 9) "+" else badgeCount.toString(),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            platformStyle = androidx.compose.ui.text.PlatformTextStyle(
                                includeFontPadding = false
                            )
                        ),
                        maxLines = 1
                    )
                }
            }

            if (locked) {
                Box(
                    modifier = Modifier
                        .offset(x = 4.dp, y = (-4).dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFFF2D55))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Try",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                fontSize = 11.sp,
                lineHeight = 14.sp
            ),
            color = if (isDark) Color.White.copy(alpha = 0.7f) else Color(0xFF5A5A5A),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
    }
}
