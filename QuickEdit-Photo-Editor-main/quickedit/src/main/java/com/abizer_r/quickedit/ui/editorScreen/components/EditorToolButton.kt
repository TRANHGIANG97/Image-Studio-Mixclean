package com.abizer_r.quickedit.ui.editorScreen.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Icon

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
    containerColorOverride: Color? = null
) {
    val containerColor by animateColorAsState(
        targetValue = containerColorOverride ?: if (selected) {
            EditorToolButtonTemplate.ButtonSelectedColor
        } else {
            EditorToolButtonTemplate.ToolbarBackgroundColor
        },
        animationSpec = tween(200),
        label = "editorToolContainer"
    )

    val contentColor = when {
        selected -> MaterialTheme.colorScheme.primary
        enabled -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    }

    Column(
        modifier = modifier
            .clip(EditorToolButtonTemplate.ButtonShape)
            .background(containerColor)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(
                horizontal = EditorToolButtonTemplate.ButtonHorizontalPadding,
                vertical = EditorToolButtonTemplate.ButtonVerticalPadding
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(EditorToolButtonTemplate.IconSize),
            tint = contentColor
        )

        if (!compact && !label.isNullOrBlank()) {
            Spacer(Modifier.height(EditorToolButtonTemplate.LabelSpacing))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = EditorToolButtonTemplate.LabelFontSize
                ),
                color = contentColor
            )
        }
    }
}
