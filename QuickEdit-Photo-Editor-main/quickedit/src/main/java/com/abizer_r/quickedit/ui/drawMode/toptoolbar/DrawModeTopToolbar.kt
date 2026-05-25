package com.abizer_r.quickedit.ui.drawMode.toptoolbar

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Undo
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.thgiang.image.studio.ui.editor.theme.EditorColorPalette
import com.thgiang.image.studio.ui.editor.theme.EditorTheme
import com.abizer_r.quickedit.ui.editorScreen.bottomToolbar.TOOLBAR_HEIGHT_SMALL
import com.abizer_r.quickedit.ui.editorScreen.components.EditorToolButton

@Composable
fun DrawModeTopToolBar(
    modifier: Modifier,
    undoEnabled: Boolean = false,
    redoEnabled: Boolean = false,
    showCloseAndDone: Boolean = true,
    closeEnabled: Boolean = true,
    doneEnabled: Boolean = false,
    toolbarHeight: Dp = TOOLBAR_HEIGHT_SMALL,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onCloseClicked: () -> Unit = {},
    onDoneClicked: () -> Unit = {}
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
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showCloseAndDone) {
            EditorToolButton(
                icon = Icons.Default.Close,
                contentDescription = "Close",
                onClick = onCloseClicked,
                compact = true,
                enabled = closeEnabled
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        EditorToolButton(
            icon = Icons.Default.Undo,
            contentDescription = "Undo",
            onClick = onUndo,
            compact = true,
            enabled = undoEnabled
        )

        EditorToolButton(
            icon = Icons.Default.Redo,
            contentDescription = "Redo",
            onClick = onRedo,
            compact = true,
            enabled = redoEnabled
        )

        Spacer(modifier = Modifier.weight(1f))

        if (showCloseAndDone) {
            EditorToolButton(
                icon = Icons.Default.Check,
                contentDescription = "Done",
                onClick = onDoneClicked,
                compact = true,
                enabled = doneEnabled
            )
        }
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun PreviewTopToolbar() {
    EditorTheme {
        DrawModeTopToolBar(
            modifier = Modifier.fillMaxWidth(),
            undoEnabled = true,
            onUndo = {},
            onRedo = {},
            onCloseClicked = {},
            onDoneClicked = {}
        )
    }
}


@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun PreviewTopToolbar2() {
    EditorTheme {
        DrawModeTopToolBar(
            modifier = Modifier.fillMaxWidth(),
            undoEnabled = true,
            showCloseAndDone = false,
            onUndo = {},
            onRedo = {},
            onCloseClicked = {},
            onDoneClicked = {}
        )
    }
}