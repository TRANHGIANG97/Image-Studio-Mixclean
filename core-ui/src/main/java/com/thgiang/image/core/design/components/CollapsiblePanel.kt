package com.thgiang.image.core.design.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Adobe-style collapsible section with animated chevron.
 *
 * Usage:
 * ```
 * CollapsiblePanel(title = "Transform") {
 *     // content here
 * }
 * ```
 */
@Composable
fun CollapsiblePanel(
    title: String,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = true,
    titleColor: Color = Color(0xFFF9FAFB),
    titleSize: Float = 13f,
    contentPadding: PaddingValues = PaddingValues(vertical = 8.dp),
    headerPadding: PaddingValues = PaddingValues(vertical = 4.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 0f else -90f,
        animationSpec = tween(200),
        label = "chevron"
    )

    Column(modifier = modifier.animateContentSize(tween(200))) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = MutableInteractionSource(),
                    indication = null
                ) { expanded = !expanded }
                .padding(headerPadding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title.uppercase(),
                fontSize = titleSize.sp,
                fontWeight = FontWeight.SemiBold,
                color = titleColor
            )
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                modifier = Modifier
                    .size(18.dp)
                    .rotate(rotation),
                tint = titleColor.copy(alpha = 0.6f)
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(200)) + fadeIn(tween(150)),
            exit = shrinkVertically(tween(150)) + fadeOut(tween(100))
        ) {
            Column(
                modifier = Modifier.padding(contentPadding),
                content = content
            )
        }
    }
}
