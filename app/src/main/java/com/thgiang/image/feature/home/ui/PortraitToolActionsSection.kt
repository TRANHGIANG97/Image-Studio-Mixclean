package com.thgiang.image.feature.home.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.thgiang.image.R
import com.thgiang.image.core.data.save.ImageSaveRepository
import com.thgiang.image.core.data.backgroundremove.BackgroundRemoverRepository
import com.thgiang.image.core.model.QuickToolAction
import com.thgiang.image.feature.editor.ui.QuickEditActivity
import com.thgiang.image.feature.home.util.renderPortraitStylePreviewBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PortraitToolActionsSection(
    imageUri: Uri?,
    blurRadius: Float,
    darkenAlpha: Float,
    vignette: Boolean,
    backgroundRemoverRepository: BackgroundRemoverRepository?,
    onSaveSuccess: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val saveRepo = remember { ImageSaveRepository(context.applicationContext) }
    val editLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { }

    fun showFail(messageRes: Int) {
        Toast.makeText(context, messageRes, Toast.LENGTH_SHORT).show()
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = {
                val uri = imageUri ?: return@Button
                scope.launch {
                    busy = true
                    try {
                        val bmp = withContext(Dispatchers.Default) {
                            val repo = backgroundRemoverRepository ?: return@withContext null
                            renderPortraitStylePreviewBitmap(
                                context = context,
                                uri = uri,
                                blurRadius = blurRadius,
                                darkenAlpha = darkenAlpha,
                                vignette = vignette,
                                backgroundRemoverRepository = repo
                            )
                        }
                        if (bmp == null) {
                            showFail(R.string.portrait_preview_export_failed)
                            return@launch
                        }
                        saveRepo.saveBitmap(
                            bmp,
                            fileName = "IMG_${System.currentTimeMillis()}.png",
                            transparent = true
                        ).onSuccess {
                            Toast.makeText(
                                context,
                                R.string.multi_saved_successfully,
                                Toast.LENGTH_SHORT
                            ).show()
                            onSaveSuccess()
                        }.onFailure {
                            showFail(R.string.multi_save_failed)
                        }
                        if (!bmp.isRecycled) bmp.recycle()
                    } finally {
                        busy = false
                    }
                }
            },
            enabled = imageUri != null && !busy,
            modifier = Modifier.weight(1f)
        ) {
            Text(stringResource(R.string.multi_save))
        }
        OutlinedButton(
            onClick = {
                val uri = imageUri ?: return@OutlinedButton
                scope.launch {
                    busy = true
                    try {
                        val bmp = withContext(Dispatchers.Default) {
                            val repo = backgroundRemoverRepository ?: return@withContext null
                            renderPortraitStylePreviewBitmap(
                                context = context,
                                uri = uri,
                                blurRadius = blurRadius,
                                darkenAlpha = darkenAlpha,
                                vignette = vignette,
                                backgroundRemoverRepository = repo
                            )
                        }
                        if (bmp == null) {
                            showFail(R.string.portrait_preview_export_failed)
                            return@launch
                        }
                        val cacheUri = saveRepo.cacheBitmap(bmp).getOrNull()
                        if (!bmp.isRecycled) bmp.recycle()
                        if (cacheUri == null) {
                            showFail(R.string.multi_save_failed)
                            return@launch
                        }
                        editLauncher.launch(QuickEditActivity.createIntent(context, cacheUri))
                    } finally {
                        busy = false
                    }
                }
            },
            enabled = imageUri != null && !busy,
            modifier = Modifier.weight(1f)
        ) {
            Text(stringResource(R.string.action_edit))
        }
    }
}
