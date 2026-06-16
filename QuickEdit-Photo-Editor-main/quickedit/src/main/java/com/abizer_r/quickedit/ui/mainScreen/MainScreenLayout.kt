package com.abizer_r.quickedit.ui.mainScreen

import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.abizer_r.quickedit.R
import com.thgiang.image.studio.ui.editor.theme.EditorTheme
import com.abizer_r.quickedit.ui.common.AppIconWithName
import com.abizer_r.quickedit.utils.other.bitmap.BitmapStatus
import com.thgiang.image.studio.model.StudioThemeplate

@Composable
fun MainScreenLayout(
    modifier: Modifier = Modifier,
    onThemeplateSelected: (StudioThemeplate) -> Unit = {}
) {
    Column(
        modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AppIconWithName(Modifier.padding(vertical = 64.dp))
        CosmeticsThemeplateSection(
            templates = emptyList(),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 480.dp),
            onThemeplateSelected = onThemeplateSelected
        )
    }

}


@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewDefault() {
    EditorTheme {
        MainScreenLayout(
            modifier = Modifier.background(MaterialTheme.colorScheme.background),
        )
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewProcessing() {
    EditorTheme {
        MainScreenLayout(
            modifier = Modifier.background(MaterialTheme.colorScheme.background),
        )
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewFailed() {
    EditorTheme {
        MainScreenLayout(
            modifier = Modifier.background(MaterialTheme.colorScheme.background),
        )
    }
}
