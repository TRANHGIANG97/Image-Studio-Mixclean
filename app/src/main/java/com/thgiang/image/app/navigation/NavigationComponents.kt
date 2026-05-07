package com.thgiang.image.app.navigation
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.thgiang.image.R
import com.thgiang.image.core.design.theme.HomeDarkStyle
import com.thgiang.image.core.design.theme.ImageDesign
import androidx.compose.ui.draw.scale

private data class BottomNavItem(
    val destination: Screen,
    val icon: ImageVector,
    val labelResId: Int,
    val tooltipText: String
)

private val BottomBarHeight = 62.dp
private val BottomBarCornerRadius = 20.dp
private val BottomBarHorizontalPadding = 16.dp
private val BottomBarElevation = 2.dp
private val BottomBarBorderWidth = 1.dp

@Composable
fun AppBottomBar(
    currentDestination: Screen,
    onDestinationSelected: (Screen) -> Unit
) {
    val navItems = listOf(
        BottomNavItem(
            destination = Screen.Home,
            icon = Icons.Default.Home,
            labelResId = R.string.nav_home,
            tooltipText = "Home Dashboard"
        ),
        BottomNavItem(
            destination = Screen.BatchPicker,
            icon = Icons.Default.Collections,
            labelResId = R.string.nav_multi_remove,
            tooltipText = "Batch Processing"
        ),
        BottomNavItem(
            destination = Screen.Settings,
            icon = Icons.Default.Settings,
            labelResId = R.string.nav_settings,
            tooltipText = "App Settings"
        )
    )

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val barBackground = if (isDark) HomeDarkStyle.surfaceElevated else Color.White.copy(alpha = 0.96f)
    val barBorderColor = if (isDark) HomeDarkStyle.borderStrong else Color(0xFFE0D5C5)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = BottomBarHorizontalPadding)
            .padding(bottom = 12.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(BottomBarHeight)
                .shadow(
                    elevation = BottomBarElevation,
                    shape = RoundedCornerShape(BottomBarCornerRadius),
                    clip = false,
                    ambientColor = Color.Black.copy(alpha = 0.08f),
                    spotColor = Color.Black.copy(alpha = 0.06f)
                )
                .clip(RoundedCornerShape(BottomBarCornerRadius))
                .background(barBackground)
                .border(
                    width = BottomBarBorderWidth,
                    color = barBorderColor,
                    shape = RoundedCornerShape(BottomBarCornerRadius)
                )
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            navItems.forEach { item ->
                val isSelected = currentDestination == item.destination || 
                                (item.destination == Screen.BatchPicker && currentDestination == Screen.BatchRemove)

                AnimatedNavItem(
                    item = item,
                    isSelected = isSelected,
                    onItemClick = { onDestinationSelected(item.destination) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun AnimatedNavItem(
    item: BottomNavItem,
    isSelected: Boolean,
    onItemClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val accentColor = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        HomeDarkStyle.accent
    } else {
        ImageDesign.semantic.aiAccent
    }

    val iconScale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.96f
            isSelected -> 1.05f
            else -> 1f
        },
        animationSpec = tween(durationMillis = ImageDesign.motion.quick),
        label = "iconScale"
    )

    val iconColor by animateColorAsState(
        targetValue = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        animationSpec = tween(durationMillis = ImageDesign.motion.medium),
        label = "iconColor"
    )

    val labelAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = tween(durationMillis = ImageDesign.motion.medium),
        label = "labelAlpha"
    )

    val labelColor by animateColorAsState(
        targetValue = if (isSelected) {
            if (MaterialTheme.colorScheme.background.luminance() < 0.5f) HomeDarkStyle.textPrimary else MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
        },
        animationSpec = tween(durationMillis = ImageDesign.motion.medium),
        label = "labelColor"
    )

    val labelText = stringResource(item.labelResId)

    Column(
        modifier = modifier
            .sizeIn(minHeight = 48.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onItemClick
            )
            .semantics(mergeDescendants = true) {
                contentDescription = labelText
            }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.height(28.dp)
        ) {
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accentColor.copy(alpha = 0.12f))
                )
            }

            Icon(
                imageVector = item.icon,
                contentDescription = null,
                modifier = Modifier
                    .size(24.dp)
                    .scale(iconScale),
                tint = iconColor
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        if (isSelected) {
            Text(
                text = labelText,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = labelColor,
                maxLines = 1,
                modifier = Modifier.graphicsLayer(alpha = labelAlpha)
            )
        } else {
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (isSelected) {
            Spacer(modifier = Modifier.height(2.dp))
            Box(
                modifier = Modifier
                    .width(16.dp)
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(accentColor)
            )
        }
    }
}





