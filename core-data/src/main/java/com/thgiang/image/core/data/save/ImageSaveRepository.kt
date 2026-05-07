package com.thgiang.image.core.data.save
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class CachedImage(val uri: Uri, val hasAlpha: Boolean)

class ImageSaveRepository(
    private val context: Context
) {
    private val cacheDir: File
        get() = File(context.cacheDir, "processed").apply { mkdirs() }

    /** Slider demo cache: cặp ảnh before/after dùng cho slider khi chưa chọn ảnh */
    private val sliderCacheDir: File
        get() = File(context.cacheDir, "slider_demo").apply { mkdirs() }

    /**
     * Copy ảnh từ Uri vào thư mục cache với tên cố định (ghi đè nếu đã tồn tại).
     * Dùng để lưu cặp before/after cho slider khi chưa chọn ảnh.
     */
    suspend fun copyUriToCache(uri: Uri, cacheFileName: String): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val outFile = File(sliderCacheDir, cacheFileName)
            val inputStream = when (uri.scheme) {
                "file" -> uri.path?.let { File(it).takeIf { f -> f.exists() }?.inputStream() }
                else -> context.contentResolver.openInputStream(uri)
            }
            inputStream?.use { input ->
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                    output.flush()
                }
            } ?: error("Cannot open input stream for $uri")
            Uri.fromFile(outFile)
        }
    }

    /**
     * Cache bitmap to file. Uses PNG when the bitmap has alpha or is ARGB_8888 (keeps transparency;
     * some devices report hasAlpha() false even with transparent pixels). Uses JPG otherwise.
     */
    suspend fun cacheBitmap(bitmap: Bitmap): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val usePng = bitmap.hasAlpha() ||
                    (bitmap.config == Bitmap.Config.ARGB_8888)
            val ext = if (usePng) "png" else "jpg"
            val file = File(cacheDir, "img_${System.currentTimeMillis()}_${bitmap.hashCode()}.$ext")
            FileOutputStream(file).use { out ->
                val format = if (usePng) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                val quality = if (usePng) 100 else 90
                bitmap.compress(format, quality, out)
            }
            Uri.fromFile(file)
        }
    }

    /**
     * Saves an image from a URI to the gallery with transparency support.
     *
     * @param imageUri Source image URI
     * @param fileName Destination file name (e.g., "IMG_123.png")
     * @return Result<Uri> - URI of saved image or error
     */
    suspend fun saveImage(
        imageUri: Uri,
        fileName: String = "IMG_${System.currentTimeMillis()}.png"
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver

            // Determine file format based on filename
            val isPng = fileName.endsWith(".png", ignoreCase = true) ||
                    fileName.endsWith(".PNG", ignoreCase = true)
            val mimeType = if (isPng) "image/png" else "image/jpeg"

            // Create content values for MediaStore
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ImageTools")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            // Insert into MediaStore
            val destinationUri = resolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            ) ?: throw IllegalStateException("Failed to create MediaStore record")

            try {
                // Copy image data from source to destination
                resolver.openInputStream(imageUri)?.use { inputStream ->
                    resolver.openOutputStream(destinationUri)?.use { outputStream ->
                        inputStream.copyTo(outputStream)
                        outputStream.flush()
                    } ?: throw IllegalStateException("Cannot open output stream")
                } ?: throw IllegalStateException("Cannot open input stream")

                // Finalize the pending image
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val finalize = ContentValues().apply {
                        put(MediaStore.Images.Media.IS_PENDING, 0)
                    }
                    resolver.update(destinationUri, finalize, null, null)
                }

                destinationUri
            } catch (e: Exception) {
                // Clean up on failure
                try {
                    resolver.delete(destinationUri, null, null)
                } catch (ignored: Exception) {
                }
                throw e
            }
        }
    }

    /**
     * Save bitmap directly to gallery with transparency support
     */
    suspend fun saveBitmap(
        bitmap: Bitmap,
        fileName: String = "IMG_${System.currentTimeMillis()}.jpg",
        transparent: Boolean = false
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val safeFileName = if (transparent && fileName.endsWith(".jpg")) {
                fileName.replaceAfterLast('.', "png")
            } else {
                fileName
            }
            val mimeType = if (transparent) "image/png" else "image/jpeg"
            val compressFormat = if (transparent) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
            val quality = if (transparent) 100 else 90

            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, safeFileName)
                put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ImageTools")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val uri = resolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            ) ?: error("Cannot create MediaStore record.")

            try {
                resolver.openOutputStream(uri)?.use { outputStream ->
                    bitmap.compress(compressFormat, quality, outputStream)
                    outputStream.flush()
                } ?: error("Cannot open output stream.")

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val pendingOff = ContentValues().apply {
                        put(MediaStore.Images.Media.IS_PENDING, 0)
                    }
                    resolver.update(uri, pendingOff, null, null)
                }

                uri
            } catch (t: Throwable) {
                resolver.delete(uri, null, null)
                throw t
            }
        }
    }

    /**
     * Save all cached images to gallery with concurrency control
     */
    suspend fun saveAll(
        items: List<CachedImage>,
        maxConcurrency: Int = 2,
        onProgress: suspend (done: Int, total: Int) -> Unit = { _, _ -> }
    ): Result<Int> = withContext(Dispatchers.IO) {
        val semaphore = Semaphore(maxConcurrency)
        var successCount = 0
        var doneCount = 0
        runCatching {
            items.forEach { cached ->
                semaphore.withPermit {
                    val result = saveFromCache(cached)
                    result.onSuccess { successCount++ }
                    doneCount++
                    onProgress(doneCount, items.size)
                }
            }
            successCount
        }
    }

    /**
     * Save a cached image to gallery
     */
    suspend fun saveCachedToGallery(cached: CachedImage): Result<Uri> = withContext(Dispatchers.IO) {
        saveFromCache(cached)
    }

    private suspend fun saveFromCache(cached: CachedImage): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(cached.uri.path ?: return@runCatching error("Invalid uri"))
            if (!file.exists()) error("Cached file not found")
            val resolver = context.contentResolver
            // Use same format as cached file (by path) so gallery entry matches content (avoids black background from PNG saved as .jpg)
            val isPng = file.name.endsWith(".png", ignoreCase = true)
            val ext = if (isPng) "png" else "jpg"
            val mimeType = if (isPng) "image/png" else "image/jpeg"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "IMG_${System.currentTimeMillis()}.$ext")
                put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ImageTools")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("Cannot create MediaStore record.")
            file.inputStream().use { input ->
                resolver.openOutputStream(uri)?.use { output ->
                    input.copyTo(output)
                    output.flush()
                } ?: error("Cannot open output stream")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val publish = ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }
                resolver.update(uri, publish, null, null)
            }
            uri
        }
    }

    /**
     * Border by alpha dilation (no BlurMaskFilter). Extract alpha, dilate by radius = borderWidthPx,
     * draw border where dilatedAlpha > 0 and originalAlpha == 0, then draw original on top.
     * Thickness is deterministic and matches borderWidthPx. Follows silhouette only.
     *
     * @param bitmap Foreground ARGB_8888 with alpha (e.g. after background removal).
     * @param borderColorArgb Border color ARGB.
     * @param borderWidthPx Dilation radius in pixels (border thickness); min 1.
     * @param previewMaxDimension When set, process at this max dimension then scale result back to full size for faster preview (e.g. 1024). Use null for final export.
     * @return New bitmap (w+2*borderWidthPx, h+2*borderWidthPx) with contour border + original.
     */
    suspend fun applyBorderToBitmap(
        bitmap: Bitmap,
        borderColorArgb: Int,
        borderWidthPx: Int = 24,
        previewMaxDimension: Int? = null
    ): Result<Bitmap> = withContext(Dispatchers.IO) {
        runCatching {
            val w = bitmap.width
            val h = bitmap.height
            val R = borderWidthPx.coerceAtLeast(1)
            val targetOutW = w + 2 * R
            val targetOutH = h + 2 * R

            when {
                previewMaxDimension != null && maxOf(w, h) > previewMaxDimension -> {
                    val scale = previewMaxDimension.toFloat() / maxOf(w, h)
                    val smallW = (w * scale).toInt().coerceAtLeast(1)
                    val smallH = (h * scale).toInt().coerceAtLeast(1)
                    val smallR = (R * scale).toInt().coerceAtLeast(1)
                    val small = Bitmap.createScaledBitmap(bitmap, smallW, smallH, true)
                    val bordered = applyBorderToBitmapInternal(small, borderColorArgb, smallR)
                    if (small != bitmap) small.recycle()
                    val upscaled = Bitmap.createScaledBitmap(bordered, targetOutW, targetOutH, true)
                    bordered.recycle()
                    upscaled
                }
                else -> applyBorderToBitmapInternal(bitmap, borderColorArgb, R)
            }
        }
    }

    private fun applyBorderToBitmapInternal(
        bitmap: Bitmap,
        borderColorArgb: Int,
        borderWidthPx: Int
    ): Bitmap {
        val R = borderWidthPx.coerceAtLeast(1)
        val w = bitmap.width
        val h = bitmap.height
        val outW = w + 2 * R
        val outH = h + 2 * R

        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        // 1. Lọc mượt alpha của ảnh gốc (khử răng cưa ML Kit)
        val origAlpha = IntArray(w * h)
        for (i in pixels.indices) origAlpha[i] = Color.alpha(pixels[i])
        boxBlur(origAlpha, w, h, 2) // Radius 2 để mượt mà

        // 2. Mở rộng alpha vào canvas output và nở rộng (dilation)
        val extAlpha = IntArray(outW * outH)
        for (y in 0 until h) {
            for (x in 0 until w) {
                extAlpha[(y + R) * outW + (x + R)] = origAlpha[y * w + x]
            }
        }
        dilate2DSeparable(extAlpha, outW, outH, R)
        
        // 3. Làm mượt viền ngoài sau khi nở rộng
        boxBlur(extAlpha, outW, outH, 2)

        val r = Color.red(borderColorArgb)
        val g = Color.green(borderColorArgb)
        val b = Color.blue(borderColorArgb)
        
        val outPixels = IntArray(outW * outH)
        for (j in 0 until outH) {
            for (i in 0 until outW) {
                val sx = i - R
                val sy = j - R
                
                // Alpha mượt của ảnh gốc
                val oa = if (sx in 0 until w && sy in 0 until h) origAlpha[sy * w + sx] else 0
                // Alpha mượt của viền mở rộng
                val da = extAlpha[j * outW + i]
                
                outPixels[j * outW + i] = when {
                    oa > 200 -> pixels[sy * w + sx] // Vùng đặc thì lấy ảnh gốc
                    oa > 0 -> {
                        // Vùng biên: Trộn giữa ảnh gốc và màu viền (Anti-aliasing)
                        val f = oa / 255f
                        val orig = pixels[sy * w + sx]
                        val mixR = (Color.red(orig) * f + r * (1 - f)).toInt().coerceIn(0, 255)
                        val mixG = (Color.green(orig) * f + g * (1 - f)).toInt().coerceIn(0, 255)
                        val mixB = (Color.blue(orig) * f + b * (1 - f)).toInt().coerceIn(0, 255)
                        Color.argb(maxOf(oa, da), mixR, mixG, mixB)
                    }
                    da > 0 -> Color.argb(da, r, g, b) // Viền ngoài
                    else -> Color.TRANSPARENT
                }
            }
        }

        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        out.setPixels(outPixels, 0, outW, 0, 0, outW, outH)
        return out
    }

    /** Box Blur tách biệt cho alpha channel (làm mượt viền) */
    private fun boxBlur(data: IntArray, width: Int, height: Int, radius: Int) {
        if (radius <= 0) return
        val temp = IntArray(data.size)
        // Blur hàng
        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                var sum = 0
                var count = 0
                for (kx in -radius..radius) {
                    val nx = x + kx
                    if (nx in 0 until width) {
                        sum += data[offset + nx]
                        count++
                    }
                }
                temp[offset + x] = sum / count
            }
        }
        // Blur cột
        for (x in 0 until width) {
            for (y in 0 until height) {
                var sum = 0
                var count = 0
                for (ky in -radius..radius) {
                    val ny = y + ky
                    if (ny in 0 until height) {
                        sum += temp[ny * width + x]
                        count++
                    }
                }
                data[y * width + x] = sum / count
            }
        }
    }

    /** Separable 2D dilation: 1D max along rows then along columns. Updates data in place. */
    private fun dilate2DSeparable(data: IntArray, width: Int, height: Int, radius: Int) {
        val temp = IntArray(data.size)
        val r = radius.coerceIn(0, maxOf(width, height))
        for (y in 0 until height) {
            dilate1DMax(data, temp, y * width, width, r)
        }
        for (x in 0 until width) {
            dilate1DColumnMax(temp, data, x, width, height, r)
        }
    }

    private fun dilate1DMax(src: IntArray, dst: IntArray, offset: Int, length: Int, radius: Int) {
        val d = ArrayDeque<Int>()
        for (i in 0 until length) {
            val windowStart = (i - radius).coerceAtLeast(0)
            while (d.isNotEmpty() && d.first() < windowStart) d.removeFirst()
            val j = i + radius
            if (j < length) {
                val v = src[offset + j]
                while (d.isNotEmpty() && src[offset + d.last()] <= v) d.removeLast()
                d.addLast(j)
            }
            dst[offset + i] = if (d.isEmpty()) 0 else src[offset + d.first()]
        }
    }

    private fun dilate1DColumnMax(
        src: IntArray,
        dst: IntArray,
        col: Int,
        width: Int,
        height: Int,
        radius: Int
    ) {
        val d = ArrayDeque<Int>()
        for (i in 0 until height) {
            val windowStart = (i - radius).coerceAtLeast(0)
            while (d.isNotEmpty() && d.first() < windowStart) d.removeFirst()
            val j = i + radius
            if (j < height) {
                val v = src[j * width + col]
                while (d.isNotEmpty() && src[d.last() * width + col] <= v) d.removeLast()
                d.addLast(j)
            }
            val idx = i * width + col
            dst[idx] = if (d.isEmpty()) 0 else src[d.first() * width + col]
        }
    }

    /**
     * Viền gradient thực: dùng thuật toán dilation giống [applyBorderToBitmap] nhưng các pixel viền
     * được tô bằng LinearGradient (sweep từ top-left → bottom-right) thay vì màu đơn.
     *
     * @param bitmap   Foreground ARGB_8888 đã xoá nền.
     * @param colors   Dãy màu ARGB (≥ 2) dọc gradient; ví dụ [0xFFC8A46A, 0xFFE9CF9A, 0xFFB98744].
     * @param borderWidthPx Độ dày viền tính bằng pixel.
     * @param previewMaxDimension Nếu set, scale xuống để xử lý nhanh rồi scale lại.
     * @return Bitmap mới (w+2R × h+2R) có viền gradient + ảnh gốc đặt lên trên.
     */
    suspend fun applyGradientBorderToBitmap(
        bitmap: Bitmap,
        colors: List<Int>,
        borderWidthPx: Int = 24,
        previewMaxDimension: Int? = null
    ): Result<Bitmap> = withContext(Dispatchers.IO) {
        runCatching {
            require(colors.size >= 2) { "Gradient cần ít nhất 2 màu" }
            val w = bitmap.width
            val h = bitmap.height
            val R = borderWidthPx.coerceAtLeast(1)
            val targetOutW = w + 2 * R
            val targetOutH = h + 2 * R

            when {
                previewMaxDimension != null && maxOf(w, h) > previewMaxDimension -> {
                    val scale = previewMaxDimension.toFloat() / maxOf(w, h)
                    val smallW = (w * scale).toInt().coerceAtLeast(1)
                    val smallH = (h * scale).toInt().coerceAtLeast(1)
                    val smallR = (R * scale).toInt().coerceAtLeast(1)
                    val small = Bitmap.createScaledBitmap(bitmap, smallW, smallH, true)
                    val bordered = applyGradientBorderInternal(small, colors, smallR)
                    if (small != bitmap) small.recycle()
                    val upscaled = Bitmap.createScaledBitmap(bordered, targetOutW, targetOutH, true)
                    bordered.recycle()
                    upscaled
                }
                else -> applyGradientBorderInternal(bitmap, colors, R)
            }
        }
    }

    private fun applyGradientBorderInternal(
        bitmap: Bitmap,
        colors: List<Int>,
        borderWidthPx: Int
    ): Bitmap {
        val R = borderWidthPx.coerceAtLeast(1)
        val w = bitmap.width
        val h = bitmap.height
        val outW = w + 2 * R
        val outH = h + 2 * R

        // 1. Lấy pixels và phân tách alpha mượt
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        
        val origAlpha = IntArray(w * h)
        for (i in pixels.indices) origAlpha[i] = Color.alpha(pixels[i])
        boxBlur(origAlpha, w, h, 2)

        val extAlpha = IntArray(outW * outH)
        for (y in 0 until h) {
            for (x in 0 until w) {
                extAlpha[(y + R) * outW + (x + R)] = origAlpha[y * w + x]
            }
        }
        dilate2DSeparable(extAlpha, outW, outH, R)
        boxBlur(extAlpha, outW, outH, 2)

        // 2. Tạo bitmap viền gradient
        val gradientBitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val gradientCanvas = Canvas(gradientBitmap)
        val gradientShader = LinearGradient(
            0f, 0f,
            outW.toFloat(), outH.toFloat(),
            colors.toIntArray(),
            null,
            Shader.TileMode.CLAMP
        )
        val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = gradientShader
        }
        gradientCanvas.drawRect(0f, 0f, outW.toFloat(), outH.toFloat(), gradientPaint)

        val gradientPixels = IntArray(outW * outH)
        gradientBitmap.getPixels(gradientPixels, 0, outW, 0, 0, outW, outH)
        gradientBitmap.recycle()

        // 3. Tổng hợp mượt mà
        val outPixels = IntArray(outW * outH)
        for (j in 0 until outH) {
            for (i in 0 until outW) {
                val sx = i - R
                val sy = j - R
                
                val oa = if (sx in 0 until w && sy in 0 until h) origAlpha[sy * w + sx] else 0
                val da = extAlpha[j * outW + i]
                
                outPixels[j * outW + i] = when {
                    oa > 200 -> pixels[sy * w + sx]
                    oa > 0 -> {
                        val f = oa / 255f
                        val orig = pixels[sy * w + sx]
                        val gc = gradientPixels[j * outW + i]
                        val mixR = (Color.red(orig) * f + Color.red(gc) * (1 - f)).toInt().coerceIn(0, 255)
                        val mixG = (Color.green(orig) * f + Color.green(gc) * (1 - f)).toInt().coerceIn(0, 255)
                        val mixB = (Color.blue(orig) * f + Color.blue(gc) * (1 - f)).toInt().coerceIn(0, 255)
                        Color.argb(maxOf(oa, da), mixR, mixG, mixB)
                    }
                    da > 0 -> {
                        val gc = gradientPixels[j * outW + i]
                        Color.argb(da, Color.red(gc), Color.green(gc), Color.blue(gc))
                    }
                    else -> Color.TRANSPARENT
                }
            }
        }

        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        out.setPixels(outPixels, 0, outW, 0, 0, outW, outH)
        return out
    }
}



