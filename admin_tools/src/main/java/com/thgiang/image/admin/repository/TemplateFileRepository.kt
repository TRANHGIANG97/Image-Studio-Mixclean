package com.thgiang.image.admin.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject

/**
 * Handles low-level file I/O for template assets:
 * - Reading from assets, content URIs, file URIs, and HTTP URLs
 * - Copying files locally
 * - Writing bitmap PNGs
 * - Packaging directories into ZIP archives
 */
class TemplateFileRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun getInputStreamForPath(path: String): java.io.InputStream? {
        return when {
            path.startsWith("content://") || path.startsWith("file://") -> {
                context.contentResolver.openInputStream(Uri.parse(path))
            }
            path.startsWith("http://") || path.startsWith("https://") -> {
                java.net.URL(path).openStream()
            }
            else -> {
                context.assets.open(path)
            }
        }
    }

    fun extensionFromPath(path: String?, fallback: String = "png"): String {
        val cleanPath = path
            ?.substringBefore('?')
            ?.substringBefore('#')
            ?: return fallback
        val extension = cleanPath.substringAfterLast('.', missingDelimiterValue = "")
        return extension.takeIf { it.length in 2..5 } ?: fallback
    }

    fun copyPathToFile(sourcePath: String, destination: File) {
        destination.parentFile?.mkdirs()
        getInputStreamForPath(sourcePath)?.use { input ->
            destination.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IllegalStateException("Cannot open $sourcePath")
    }

    fun writeBitmapPng(bitmap: Bitmap, destination: File) {
        destination.parentFile?.mkdirs()
        FileOutputStream(destination).use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 92, output)
        }
    }

    fun zipDirectory(sourceDir: File, zipFile: File) {
        zipFile.parentFile?.mkdirs()
        ZipOutputStream(FileOutputStream(zipFile)).use { zip ->
            sourceDir.walkTopDown()
                .filter { it.isFile }
                .forEach { file ->
                    val relativePath = sourceDir.toPath()
                        .relativize(file.toPath())
                        .toString()
                        .replace(File.separatorChar, '/')
                    zip.putNextEntry(ZipEntry(relativePath))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
        }
    }

    fun decodeBounds(assetPath: String): Pair<Int, Int> {
        getInputStreamForPath(assetPath)?.use { input ->
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(input, null, opts)
            return Pair(opts.outWidth, opts.outHeight)
        }
        return Pair(0, 0)
    }
}
