package com.thgiang.image.feature.common.media

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private const val DEMO_SAMPLE_CACHE_DIR = "picker_demo_samples"

private val DEMO_SAMPLE_ASSETS = listOf(
    "image_sample/img_0001.jpg",
    "image_sample/img_0002.jpg",
    "image_sample/img_0003.jpg",
    "image_sample/img_0004.jpg",
)

suspend fun loadPickerDemoSampleUris(context: Context): List<Uri> = withContext(Dispatchers.IO) {
    val cacheDir = File(context.cacheDir, DEMO_SAMPLE_CACHE_DIR).apply { mkdirs() }

    DEMO_SAMPLE_ASSETS.mapNotNull { assetPath ->
        runCatching {
            val fileName = assetPath.substringAfterLast('/')
            val outFile = File(cacheDir, fileName)
            if (!outFile.exists() || outFile.length() <= 0L) {
                context.assets.open(assetPath).use { input ->
                    FileOutputStream(outFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                outFile
            )
        }.getOrNull()
    }
}
