@file:Suppress("OPT_IN_USAGE", "OPT_IN_USAGE_ERROR")
package com.thgiang.image.studio.ui.editor.label.panel

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import com.thgiang.image.studio.R
import com.thgiang.image.studio.ui.editor.theme.EditorTokens
import com.thgiang.image.studio.ui.editor.theme.MotionTokens

/**
 * Shape editing tabs — Canva-style icon tabs.
 * Each tab has an icon + short label below, 60dp height.
 */
enum class ShapeEditTab(
    val icon: ImageVector,
    @StringRes val labelRes: Int,
) {
    FILL(Icons.Default.FormatColorFill, R.string.studio_shape_tab_fill),
    STROKE(Icons.Default.BorderColor, R.string.studio_shape_tab_stroke),
    SHADOW(Icons.Default.WbSunny, R.string.studio_shape_tab_shadow),
    ELEVATION(Icons.Default.Layers, R.string.studio_shape_tab_elevation),
    SHAPE(Icons.Default.Category, R.string.studio_shape_tab_shape),
}

/** Default tabs shown for Shape tool editing mode */
val shapeDefaultTabs = listOf(
    ShapeEditTab.FILL,
    ShapeEditTab.STROKE,
    ShapeEditTab.SHADOW,
    ShapeEditTab.ELEVATION,
    ShapeEditTab.SHAPE,
)

private val shapeEffectTabs = setOf(ShapeEditTab.SHADOW, ShapeEditTab.ELEVATION)

fun shapeTabsForLayer(supportsEffects: Boolean): List<ShapeEditTab> =
    if (supportsEffects) shapeDefaultTabs else shapeDefaultTabs.filter { it !in shapeEffectTabs }

/**
 * Canva-style icon tab bar for the Shape editing panel.
 *
 * Design:
 * - Height: 64dp total (icon 24dp + label 10sp + padding)
 * - Horizontal scroll with LazyRow
 * - Selected tab: accent color icon + bold label + 2dp bottom indicator line
 * - Unselected: grey icon + grey label
 * - Touch target: min 56dp × 64dp
 */
@Composable
fun ShapeIconTabBar(
    tabs: List<ShapeEditTab>,
    selected: ShapeEditTab,
    tokens: EditorTokens,
    onTabSelected: (ShapeEditTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(Color.White),
    ) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxHeight(),
        ) {
            items(tabs) { tab ->
                ShapeTabItem(
                    tab = tab,
                    isSelected = selected == tab,
                    tokens = tokens,
                    onClick = { onTabSelected(tab) },
                )
            }
        }
    }
}

@Composable
private fun ShapeTabItem(
    tab: ShapeEditTab,
    isSelected: Boolean,
    tokens: EditorTokens,
    onClick: () -> Unit,
) {
    val indicatorHeight by animateDpAsState(
        targetValue = if (isSelected) 2.5.dp else 0.dp,
        animationSpec = MotionTokens.springEmphasized(),
        label = "tabIndicator_${tab.name}",
    )

    val iconTint = if (isSelected) tokens.accent else Color(0xFF9E9E9E)
    val labelColor = if (isSelected) tokens.accent else Color(0xFF9E9E9E)
    val label = stringResource(tab.labelRes)

    Box(
        modifier = Modifier
            .width(56.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
            .background(
                if (isSelected) tokens.accentSoft.copy(alpha = 0.4f) else Color.Transparent
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 4.dp),
        ) {
            Icon(
                imageVector = tab.icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = label,
                fontSize = 9.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = labelColor,
                maxLines = 1,
                modifier = Modifier.padding(top = 1.dp),
            )
        }

        // Bottom indicator
        if (indicatorHeight > 0.dp) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(indicatorHeight)
                    .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                    .background(tokens.accent),
            )
        }
    }
}
