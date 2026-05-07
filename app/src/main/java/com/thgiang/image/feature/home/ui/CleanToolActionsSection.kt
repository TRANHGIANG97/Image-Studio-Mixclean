package com.thgiang.image.feature.home.ui

import android.graphics.Bitmap
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.thgiang.image.R
import com.thgiang.image.core.data.save.ImageSaveRepository
import com.thgiang.image.core.data.backgroundremove.BackgroundRemoverRepository
import com.thgiang.image.core.model.QuickToolAction
import com.thgiang.image.feature.editor.ui.QuickEditActivity
import com.thgiang.image.core.util.ImageEffectProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CleanToolActionsSection(
    imageUri: Uri?,
    intensity: Float,
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

    suspend fun buildBitmap(): Bitmap? = withContext(Dispatchers.IO) {
        val uri = imageUri ?: return@withContext null
        val repo = backgroundRemoverRepository ?: return@withContext null
        ImageEffectProcessor.applyClean(context, uri, intensity, repo)
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = {
                scope.launch {
                    busy = true
                    try {
                        val bmp = buildBitmap()
                        if (bmp == null) {
                            showFail(R.string.portrait_preview_export_failed)
                            return@launch
                        }
                        saveRepo.saveBitmap(
                            bmp,
                            fileName = "ENHANCE_${System.currentTimeMillis()}.png",
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
                scope.launch {
                    busy = true
                    try {
                        val bmp = buildBitmap()
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
