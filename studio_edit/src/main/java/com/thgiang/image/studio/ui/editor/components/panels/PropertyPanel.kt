package com.thgiang.image.studio.ui.editor.components.panels

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thgiang.image.core.design.adaptive.EditorLayoutMode
import com.thgiang.image.core.design.adaptive.LocalEditorLayoutMode
import com.thgiang.image.studio.ui.editor.theme.EditorColorPalette
import com.thgiang.image.studio.ui.editor.theme.EditorTokens
import com.thgiang.image.studio.ui.editor.theme.LocalEditorTokens

/**
 * Contextual property panel that slides in when a tool is active.
 *
 * Compact mode: slides up from bottom as a bottom sheet.
 * Tablet/Desktop mode: slides in from right as a side panel (280-320dp wide).
 */
@Composable
fun PropertyPanel(
    visible: Boolean,
    toolName: String = "",
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier,
    tokens: EditorTokens = LocalEditorTokens.current,
    header: @Composable () -> Unit = {},
    footer: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    val layoutMode = LocalEditorLayoutMode.current
    val isCompact = layoutMode == EditorLayoutMode.Mobile

    AnimatedVisibility(
        visible = visible,
        enter = when {
            isCompact -> slideInVertically(tween(250)) { it } + fadeIn(tween(250))
            else -> slideInHorizontally(tween(250)) { it } + fadeIn(tween(250))
        },
        exit = when {
            isCompact -> slideOutVertically(tween(200)) { it } + fadeOut(tween(200))
            else -> slideOutHorizontally(tween(200)) { it } + fadeOut(tween(200))
        }
    ) {
        when {
            isCompact -> CompactPropertySheet(
                toolName = toolName,
                onClose = onClose,
                modifier = modifier,
                tokens = tokens,
                header = header,
                footer = footer,
                content = content
            )
            else -> SidePropertyPanel(
                toolName = toolName,
                onClose = onClose,
                modifier = modifier,
                tokens = tokens,
                header = header,
                footer = footer,
                content = content
            )
        }
    }
}

@Composable
private fun CompactPropertySheet(
    toolName: String,
    onClose: () -> Unit,
    modifier: Modifier,
    tokens: EditorTokens,
    header: @Composable () -> Unit,
    footer: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(tokens.surfaceElevated)
            .padding(top = 8.dp, bottom = 16.dp)
    ) {
        PanelDragHandle(tokens = tokens)
        PanelHeader(toolName = toolName, onClose = onClose, tokens = tokens)
        header()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            content = content
        )
        footer()
    }
}

@Composable
private fun SidePropertyPanel(
    toolName: String,
    onClose: () -> Unit,
    modifier: Modifier,
    tokens: EditorTokens,
    header: @Composable () -> Unit,
    footer: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .widthIn(min = tokens.panelMinWidth, max = tokens.panelMaxWidth)
            .fillMaxHeight()
            .background(tokens.surfaceElevated)
            .padding(12.dp)
    ) {
        PanelHeader(toolName = toolName, onClose = onClose, tokens = tokens)
        header()
        Column(
            modifier = Modifier.weight(1f),
            content = content
        )
        footer()
    }
}

@Composable
fun PanelDragHandle(
    modifier: Modifier = Modifier,
    tokens: EditorTokens = LocalEditorTokens.current
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(32.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(tokens.borderStrong)
        )
    }
}

@Composable
fun PanelHeader(
    toolName: String,
    onClose: () -> Unit,
    tokens: EditorTokens = LocalEditorTokens.current
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = toolName,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = tokens.textPrimary
        )
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Close",
            modifier = Modifier
                .size(24.dp)
                .clickable(
                    interactionSource = MutableInteractionSource(),
                    indication = null,
                    onClick = onClose
                ),
            tint = tokens.textSecondary
        )
    }
}
