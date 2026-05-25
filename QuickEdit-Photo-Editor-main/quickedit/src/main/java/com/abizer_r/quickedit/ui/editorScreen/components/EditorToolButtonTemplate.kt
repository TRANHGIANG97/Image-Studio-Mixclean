package com.abizer_r.quickedit.ui.editorScreen.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thgiang.image.studio.ui.editor.theme.EditorColorPalette
import com.thgiang.image.studio.ui.editor.theme.EditorTokens
import com.thgiang.image.studio.ui.editor.theme.LocalEditorTokens
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.clip

object EditorToolButtonTemplate {
    // Light, outline-first toolbar parameters inspired by mobile photo editors.
    val ToolbarBackgroundColor = EditorColorPalette.GlassOverlay
    val ButtonSelectedColor = EditorColorPalette.AccentCyan.copy(alpha = 0.12f)

    val ButtonShape = RoundedCornerShape(8.dp)
    val ButtonHorizontalPadding = 6.dp
    val ButtonVerticalPadding = 2.dp
    val IconSize = 24.dp
    val LabelSpacing = 4.dp
    val LabelFontSize = 11.sp

    val ToolbarHorizontalPadding = 4.dp
    val ToolbarVerticalPadding = 0.dp
    val ToolbarItemSpacing = 4.dp
    val ToolbarTonalElevation = 0.dp
}

@Composable
fun EditorBottomToolbarTemplate(
    modifier: Modifier = Modifier,
    toolbarHeight: Dp,
    tokens: EditorTokens = LocalEditorTokens.current,
    content: @Composable RowScope.() -> Unit
) {
    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .height(toolbarHeight)
            .background(tokens.glassBackground)
            .drawBehind {
                drawRect(
                    color = tokens.borderSubtle,
                    topLeft = Offset(0f, 0f),
                    size = Size(size.width, 0.5.dp.toPx())
                )
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(
                    horizontal = EditorToolButtonTemplate.ToolbarHorizontalPadding,
                    vertical = EditorToolButtonTemplate.ToolbarVerticalPadding
                ),
            horizontalArrangement = Arrangement.spacedBy(EditorToolButtonTemplate.ToolbarItemSpacing),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}
