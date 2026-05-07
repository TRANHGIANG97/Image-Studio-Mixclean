package com.thgiang.image.feature.home.ui
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.Hd
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.FaceRetouchingNatural
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.thgiang.image.R
import com.thgiang.image.core.design.theme.HomeDarkStyle
import com.thgiang.image.core.design.theme.ImageDesign
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest

private data class QuickTool(
    val title: String,
    val iconAsset: String? = null,
    val iconVector: androidx.compose.ui.graphics.vector.ImageVector? = null,
    val locked: Boolean,
    val badgeCount: Int = 0,
    val color: Color = Color.Transparent,
    val onClick: () -> Unit
)

@Composable
fun AiToolDock(
    isPremium: Boolean,
    onBatchRemove: () -> Unit,
    onOpenRemoveBgEditor: () -> Unit = {},
    onLockedClick: () -> Unit,
    hasDraft: Boolean = false,
    draftCount: Int = 0,
    onRestoreDraft: () -> Unit = {},
    useHomeDarkStyle: Boolean = false
) {
    val tools = mutableListOf<QuickTool>()
    
    tools.add(QuickTool(
        title = stringResource(R.string.home_draft),
        iconAsset = "draft.png",
        locked = false,
        badgeCount = draftCount,
        onClick = onRestoreDraft
    ))
    
    tools.addAll(listOf(
        QuickTool(stringResource(R.string.home_dock_pick_image), iconAsset = "pick_image.png", locked = false, onClick = onOpenRemoveBgEditor),
        QuickTool(stringResource(R.string.home_dock_batch), iconAsset = "batch.png", locked = false, onClick = onBatchRemove)
    ))
    val isDark = useHomeDarkStyle || isSystemInDarkTheme()
    var isExpanded by remember { mutableStateOf(false) }

    if (tools.isEmpty()) return

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.home_quick_tools),
            style = MaterialTheme.typography.labelLarge,
            color = if (isDark) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )
        val columns = 4
        // Hiển thị tất cả tools còn lại (nếu có)
        val displayTools = tools
        val rows = displayTools.chunked(columns)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            rows.forEachIndexed { rowIndex, rowTools ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    rowTools.forEach { tool ->
                        DockToolItem(
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
                    
                    /* Expand logic removed for centering remaining tools */
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
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = tween(ImageDesign.motion.quick),
        label = "dockScale"
    )
    val dockBg = when {
        useHomeDarkStyle -> HomeDarkStyle.surfaceButton
        isDark -> Color(0xFF2C2C2C)
        else -> ImageDesign.surfaces.glass
    }
    val dockBorder = when {
        useHomeDarkStyle -> HomeDarkStyle.borderStrong
        isDark -> Color.White.copy(alpha = 0.08f)
        else -> ImageDesign.semantic.aiAccent.copy(alpha = 0.12f)
    }
    val dockIconTint = when {
        useHomeDarkStyle -> HomeDarkStyle.textPrimary
        isDark -> Color.White
        locked -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        else -> ImageDesign.semantic.aiAccent
    }
    val dockTextColor = when {
        useHomeDarkStyle -> HomeDarkStyle.textSecondary
        isDark -> Color.White.copy(alpha = 0.7f)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val proBadgeColor = if (useHomeDarkStyle) HomeDarkStyle.accent else Color(0xFFD2A86A)
    val badgeBgColor = Color(0xFFD2A86A) // Màu vàng gold tinh tế cho badge

    Column(
        modifier = modifier
            .width(80.dp)
            .scale(scale)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Surface(
                modifier = Modifier.size(60.dp),
                shape = CircleShape,
                color = if (isDark) Color(0xFF2C2C2E) else Color.White,
                shadowElevation = if (isDark) 0.dp else 4.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (iconAsset != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data("file:///android_asset/icon_quicktools/$iconAsset")
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            contentScale = ContentScale.Fit
                        )
                    } else if (iconVector != null) {
                        Icon(
                            imageVector = iconVector,
                            contentDescription = null,
                            modifier = Modifier.size(30.dp),
                            tint = if (isDark) Color.White else Color.Black
                        )
                    }
                }
            }
            
            if (badgeCount > 0) {
                Box(
                    modifier = Modifier
                        .offset(x = 4.dp, y = (-2).dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF3B30)) // Màu đỏ Try rực rỡ
                        .border(1.5.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (badgeCount > 9) "+" else badgeCount.toString(),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
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
                        .offset(x = 4.dp, y = (-2).dp)
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
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            ),
            color = if (isDark) Color.White else Color.Black,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}




