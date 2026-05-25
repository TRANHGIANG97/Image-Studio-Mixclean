package com.abizer_r.quickedit.ui.editorScreen.topToolbar

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.thgiang.image.studio.ui.editor.theme.EditorTheme
// ToolBarBackgroundColor removed
import com.abizer_r.quickedit.ui.editorScreen.bottomToolbar.TOOLBAR_HEIGHT_SMALL
import com.abizer_r.quickedit.R
import androidx.compose.ui.res.stringResource
import com.abizer_r.quickedit.ui.editorScreen.components.EditorToolButton
import com.thgiang.image.studio.ui.editor.theme.EditorColorPalette

@Composable
fun EditorTopToolBar(
    modifier: Modifier,
    undoEnabled: Boolean = false,
    redoEnabled: Boolean = false,
    closeEnabled: Boolean = true,
    saveEnabled: Boolean = false,
    toolbarHeight: Dp = TOOLBAR_HEIGHT_SMALL,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onCloseClicked: () -> Unit = {},
    onSaveClicked: () -> Unit = {},
    onShareClicked: () -> Unit = {},
    onSaveDraftClicked: () -> Unit = {}
) {
    Row(
        modifier = modifier
            .height(toolbarHeight)
            .background(EditorColorPalette.GlassOverlay)
            .drawBehind {
                drawRect(
                    color = EditorColorPalette.BorderSubtle,
                    topLeft = Offset(0f, size.height - 0.5.dp.toPx()),
                    size = Size(size.width, 0.5.dp.toPx())
                )
            },
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        EditorToolButton(
            icon = Icons.Default.Close,
            contentDescription = "Close",
            onClick = onCloseClicked,
            modifier = Modifier.padding(horizontal = 8.dp),
            compact = true,
            enabled = closeEnabled
        )

        Spacer(modifier = Modifier.weight(1f))

        EditorToolButton(
            icon = Icons.AutoMirrored.Filled.Undo,
            contentDescription = "Undo",
            onClick = onUndo,
            modifier = Modifier.padding(horizontal = 6.dp),
            compact = true,
            enabled = undoEnabled
        )

        EditorToolButton(
            icon = Icons.AutoMirrored.Filled.Redo,
            contentDescription = "Redo",
            onClick = onRedo,
            modifier = Modifier.padding(horizontal = 6.dp),
            compact = true,
            enabled = redoEnabled
        )

        Spacer(modifier = Modifier.weight(1f))

        EditorToolButton(
            icon = Icons.Default.Download,
            contentDescription = stringResource(R.string.save_draft),
            label = stringResource(R.string.draft_label),
            onClick = onSaveDraftClicked,
            modifier = Modifier.padding(horizontal = 6.dp),
            compact = true
        )

        EditorToolButton(
            icon = Icons.Default.SaveAlt,
            contentDescription = "Save",
            onClick = onSaveClicked,
            modifier = Modifier.padding(horizontal = 6.dp),
            compact = true,
            enabled = saveEnabled
        )

        EditorToolButton(
            icon = Icons.Rounded.Share,
            contentDescription = "Share",
            onClick = onShareClicked,
            modifier = Modifier.padding(horizontal = 8.dp),
            compact = true
        )
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun PreviewTopToolbar() {
    EditorTheme {
        EditorTopToolBar(
            modifier = Modifier.fillMaxWidth(),
            undoEnabled = true,
            onUndo = {},
            onRedo = {},
            onCloseClicked = {},
            onSaveClicked = {}
        )
    }
}
