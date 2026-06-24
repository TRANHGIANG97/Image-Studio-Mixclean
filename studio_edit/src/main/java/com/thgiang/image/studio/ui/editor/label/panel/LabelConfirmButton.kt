package com.thgiang.image.studio.ui.editor.label.panel
import com.thgiang.image.studio.ui.editor.panel.*
import com.thgiang.image.studio.ui.editor.label.panel.*

import androidx.compose.foundation.background
import com.thgiang.image.studio.ui.editor.model.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.thgiang.image.studio.R
import com.thgiang.image.studio.ui.editor.theme.EditorTokens

@Composable
internal fun LabelConfirmButton(
    onClick: () -> Unit,
    tokens: EditorTokens,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val description = stringResource(R.string.studio_label_confirm_add)
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(
                if (enabled) tokens.accent else tokens.accent.copy(alpha = 0.35f),
            )
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        EditorCheckIcon(
            modifier = Modifier.size(18.dp),
            tint = Color.White,
        )
    }
}
