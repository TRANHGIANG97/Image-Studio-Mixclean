package com.abizer_r.quickedit.ui.editorScreen.topToolbar

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.thgiang.image.studio.ui.editor.theme.EditorColorPalette
import com.thgiang.image.studio.ui.editor.theme.EditorTheme
import com.abizer_r.quickedit.ui.editorScreen.components.EditorToolButton

@Composable
fun TextModeTopToolbar(
    modifier: Modifier,
    toolbarHeight: Dp = 56.dp,
    onCloseClicked: () -> Unit,
    onDoneClicked: () -> Unit
) {
    Row(
        modifier = modifier
            .background(EditorColorPalette.GlassOverlay)
            .statusBarsPadding()
            .height(toolbarHeight)
            .drawBehind {
                drawRect(
                    color = EditorColorPalette.BorderSubtle,
                    topLeft = Offset(0f, size.height - 0.5.dp.toPx()),
                    size = Size(size.width, 0.5.dp.toPx())
                )
            },
        horizontalArrangement = Arrangement.Center
    ) {
        EditorToolButton(
            icon = Icons.Default.Close,
            contentDescription = "Close",
            onClick = onCloseClicked,
            compact = true
        )

        Spacer(modifier = Modifier.weight(1f))

        EditorToolButton(
            icon = Icons.Default.Check,
            contentDescription = "Done",
            onClick = onDoneClicked,
            compact = true
        )
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun PreviewTextModeTopToolbar() {
    EditorTheme {
        TextModeTopToolbar(
            modifier = Modifier.fillMaxWidth(),
            onCloseClicked = {},
            onDoneClicked = {}
        )
    }
}
