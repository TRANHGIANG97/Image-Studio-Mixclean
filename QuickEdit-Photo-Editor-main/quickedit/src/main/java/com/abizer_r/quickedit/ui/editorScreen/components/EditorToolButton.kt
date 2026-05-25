package com.abizer_r.quickedit.ui.editorScreen.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thgiang.image.studio.ui.editor.theme.EditorTokens
import com.thgiang.image.studio.ui.editor.theme.LocalEditorTokens

@Composable
fun EditorToolButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
    compact: Boolean = false,
    containerColorOverride: Color? = null,
    tokens: EditorTokens = LocalEditorTokens.current
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val containerColor by animateColorAsState(
        targetValue = when {
            containerColorOverride != null -> containerColorOverride
            selected -> tokens.accent.copy(alpha = 0.08f)
            isPressed -> tokens.textPrimary.copy(alpha = 0.06f)
            else -> tokens.glassBackground
        },
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "editorToolContainer"
    )

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 300f),
        label = "editorToolPress"
    )

    val contentColor = when {
        !enabled -> tokens.textDisabled
        selected -> tokens.accent
        else -> tokens.textPrimary
    }
    val hasLabel = !label.isNullOrBlank()
    val iconSize = when {
        compact && hasLabel -> 20.dp
        compact -> 28.dp
        else -> 22.dp
    }

    Column(
        modifier = modifier
            .defaultMinSize(minWidth = if (compact && hasLabel) 52.dp else if (compact) 44.dp else 62.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(RoundedCornerShape(6.dp))
            .background(containerColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = if (compact) 8.dp else 8.dp, vertical = if (compact) 7.dp else 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize),
            tint = contentColor
        )

        if (hasLabel) {
            Spacer(Modifier.height(if (compact) 2.dp else 6.dp))
            Text(
                text = label.orEmpty(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = if (compact) 9.sp else 11.sp,
                    lineHeight = if (compact) 10.sp else 13.sp,
                    letterSpacing = 0.sp
                ),
                color = contentColor,
                fontWeight = FontWeight.Normal,
                maxLines = if (compact) 1 else 2,
                textAlign = TextAlign.Center
            )
        }
    }
}
