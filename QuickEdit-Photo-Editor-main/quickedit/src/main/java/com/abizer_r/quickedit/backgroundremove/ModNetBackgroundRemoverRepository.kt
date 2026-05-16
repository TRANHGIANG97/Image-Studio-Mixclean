package com.abizer_r.quickedit.backgroundremove

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.thgiang.image.core.data.backgroundremove.BackgroundRemovalOutput
import com.thgiang.image.core.data.backgroundremove.BackgroundRemoverRepository
import com.thgiang.image.core.data.backgroundremove.PortraitConfidenceMask
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val DEFAULT_MAX_DIMENSION = 2048

class ModNetBackgroundRemoverRepository(
    private val context: Context,
) : BackgroundRemoverRepository, AutoCloseable {
    private companion object {
        private const val DEFAULT_MODEL_SIZE = 512
        private const val DEFAULT_MODEL_ASSET = "modnet_matte_512_fp16.onnx"
    }

    private val environment: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var session: OrtSession? = null
    private var inputName: String? = null
    private var outputName: String? = null
    private var modelSize: Int = DEFAULT_MODEL_SIZE

    @Synchronized
    private fun ensureInitialized(
        modelAssetPath: String = DEFAULT_MODEL_ASSET,
        size: Int = DEFAULT_MODEL_SIZE,
    ) {
        if (session != null) return

        modelSize = size
        val modelFile = context.copyAssetToCache(modelAssetPath)

        val options = OrtSession.SessionOptions().apply {
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            setIntraOpNumThreads(max(1, Runtime.getRuntime().availableProcessors() / 2))
        }

        try {
            val createdSession = environment.createSession(modelFile.absolutePath, options)
            session = createdSession
            inputName = createdSession.inputInfo.keys.firstOrNull()
                ?: error("ONNX model has no input")
            outputName = createdSession.outputInfo.keys.firstOrNull()
                ?: error("ONNX model has no output")
        } finally {
            options.close()
        }
    }

    override suspend fun removeBackground(imageUri: Uri): Result<BackgroundRemovalOutput> = runCatching {
        val original = context.loadBitmapFromUri(imageUri)
        val foreground = try {
            getForegroundBitmap(original).getOrThrow()
        } catch (e: Throwable) {
            if (!original.isRecycled) original.recycle()
            throw e
        }

        if (!original.isRecycled) {
            original.recycle()
        }

        val display = foreground.copy(Bitmap.Config.ARGB_8888, true)
        BackgroundRemovalOutput(
            foregroundToDisplay = display,
            foregroundToSave = foreground,
        )
    }

    override suspend fun getForegroundBitmap(bitmap: Bitmap): Result<Bitmap> = runCatching {
        val source = bitmap.ensureArgb8888CopyIfNeeded()
        val result = removeBgInternal(
            bitmap = source,
            smoothMask = true,
            enhanceEdges = false,
        )
        if (source !== bitmap && !source.isRecycled) {
            source.recycle()
        }
        result.bitmap
    }

    override suspend fun getPortraitConfidenceMask(bitmap: Bitmap): Result<PortraitConfidenceMask> = runCatching {
        val source = bitmap.ensureArgb8888CopyIfNeeded()
        val letterboxed = source.toLetterboxedSquare(modelSize)
        val inputFloats = try {
            imageToNchwFloatTensor(letterboxed.bitmap, modelSize)
        } finally {
            letterboxed.bitmap.recycle()
        }

        ensureInitialized()
        val currentSession = session ?: error("ONNX session not initialized")
        val currentInputName = inputName ?: error("ONNX input name not initialized")
        val currentOutputName = outputName ?: error("ONNX output name not initialized")
        val inputTensor = OnnxTensor.createTensor(
            environment,
            FloatBuffer.wrap(inputFloats),
            longArrayOf(1, 3, modelSize.toLong(), modelSize.toLong()),
        )

        try {
            currentSession.run(mapOf(currentInputName to inputTensor)).use { outputs ->
                val outputTensor = outputs.get(currentOutputName).orElseThrow() as OnnxTensor
                outputTensor.use {
                    val rawMask = readOutputMask(outputTensor)
                    val contentMask = rawMask.crop(
                        letterboxed.contentLeft,
                        letterboxed.contentTop,
                        letterboxed.contentWidth,
                        letterboxed.contentHeight,
                    )
                    val resized = contentMask.resizeBilinear(source.width, source.height)
                    PortraitConfidenceMask(source.width, source.height, resized.data)
                }
            }
        } finally {
            inputTensor.close()
            if (source !== bitmap && !source.isRecycled) {
                source.recycle()
            }
        }
    }

    override fun consumeSelfieFallbackWarning(): Boolean = false

    override fun close() {
        session?.close()
        session = null
        inputName = null
        outputName = null
    }

    private fun removeBgInternal(
        bitmap: Bitmap,
        smoothMask: Boolean,
        enhanceEdges: Boolean,
    ): RemoveBgResult {
        ensureInitialized()
        val currentSession = session ?: error("ONNX session not initialized")
        val currentInputName = inputName ?: error("ONNX input name not initialized")
        val currentOutputName = outputName ?: error("ONNX output name not initialized")

        val letterboxed = bitmap.toLetterboxedSquare(modelSize)
        val inputFloats = try {
            imageToNchwFloatTensor(letterboxed.bitmap, modelSize)
        } finally {
            letterboxed.bitmap.recycle()
        }

        val inputTensor = OnnxTensor.createTensor(
            environment,
            FloatBuffer.wrap(inputFloats),
            longArrayOf(1, 3, modelSize.toLong(), modelSize.toLong()),
        )

        return try {
            currentSession.run(mapOf(currentInputName to inputTensor)).use { outputs ->
                val outputTensor = outputs.get(currentOutputName).orElseThrow() as OnnxTensor
                outputTensor.use {
                    val rawMask = readOutputMask(outputTensor)
                    val contentMask = rawMask.crop(
                        letterboxed.contentLeft,
                        letterboxed.contentTop,
                        letterboxed.contentWidth,
                        letterboxed.contentHeight,
                    )

                    val confidencePercent = contentMask.calculateCertaintyPercent()

                    var mask = if (smoothMask) {
                        contentMask.blurBoxSeparable(1)
                    } else {
                        contentMask
                    }

                    mask = mask.resizeBilinear(bitmap.width, bitmap.height)

                    if (enhanceEdges) {
                        mask = refineMaskByImageEdges(bitmap, mask)
                    }

                    val cutout = applyMaskToImage(bitmap, mask)
                    RemoveBgResult(cutout, confidencePercent)
                }
            }
        } finally {
            inputTensor.close()
        }
    }

    private fun imageToNchwFloatTensor(image: Bitmap, size: Int): FloatArray {
        require(image.width == size && image.height == size) {
            "Input bitmap must be ${size}x$size, got ${image.width}x${image.height}"
        }

        val pixels = IntArray(size * size)
        image.getPixels(pixels, 0, size, 0, 0, size, size)

        val planeSize = size * size
        val out = FloatArray(planeSize * 3)

        for (i in pixels.indices) {
            val color = pixels[i]
            val r = ((color shr 16) and 0xFF) / 255f
            val g = ((color shr 8) and 0xFF) / 255f
            val b = (color and 0xFF) / 255f

            out[i] = (r - 0.5f) / 0.5f
            out[planeSize + i] = (g - 0.5f) / 0.5f
            out[planeSize * 2 + i] = (b - 0.5f) / 0.5f
        }

        return out
    }

    private fun readOutputMask(outputTensor: OnnxTensor): Mask {
        val buffer = outputTensor.floatBuffer
            ?: throw IllegalStateException("Unexpected ONNX output type")

        buffer.rewind()

        val shape = outputTensor.info.shape
        val dims = shape.filter { it > 0L }.map { it.toInt() }
        require(dims.isNotEmpty()) { "Invalid ONNX output shape: ${shape.contentToString()}" }

        val (height, width) = when (dims.size) {
            2 -> dims[0] to dims[1]
            3 -> dims[1] to dims[2]
            4 -> {
                require(dims[0] == 1) { "Batch size must be 1, got ${dims[0]}" }
                dims[2] to dims[3]
            }
            else -> throw IllegalStateException("Unsupported output shape: ${shape.contentToString()}")
        }

        val total = width * height
        require(buffer.remaining() >= total) {
            "Output buffer smaller than tensor shape"
        }

        val data = FloatArray(total) { buffer.get().coerceIn(0f, 1f) }
        return Mask(width, height, data)
    }

    private fun applyMaskToImage(
        image: Bitmap,
        mask: Mask,
    ): Bitmap {
        require(mask.width == image.width && mask.height == image.height) {
            "Mask size ${mask.width}x${mask.height} != image ${image.width}x${image.height}"
        }

        val width = image.width
        val height = image.height
        val srcPixels = IntArray(width * height)
        image.getPixels(srcPixels, 0, width, 0, 0, width, height)

        val outPixels = IntArray(srcPixels.size)
        for (i in srcPixels.indices) {
            val src = srcPixels[i]
            val srcAlpha = (src ushr 24) and 0xFF
            val maskAlpha = (mask.data[i].coerceIn(0f, 1f) * 255f).roundToInt()
            val alpha = (srcAlpha * maskAlpha / 255f).roundToInt().coerceIn(0, 255)
            outPixels[i] = (alpha shl 24) or (src and 0x00FFFFFF)
        }

        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(outPixels, 0, width, 0, 0, width, height)
        }
    }

    private fun refineMaskByImageEdges(image: Bitmap, mask: Mask): Mask {
        val width = image.width
        val height = image.height
        require(mask.width == width && mask.height == height)

        val pixels = IntArray(width * height)
        image.getPixels(pixels, 0, width, 0, 0, width, height)
        val out = mask.data.copyOf()

        for (y in 1 until height - 1) {
            val row = y * width
            for (x in 1 until width - 1) {
                val index = row + x
                val value = mask.data[index]
                if (value !in 0.12f..0.88f) continue

                val left = pixels[index - 1]
                val right = pixels[index + 1]
                val up = pixels[index - width]
                val down = pixels[index + width]
                val gradient = colorDistance(left, right) + colorDistance(up, down)

                if (gradient > 80f) {
                    val localAvg = (
                        mask.data[index - width] +
                            mask.data[index + width] +
                            mask.data[index - 1] +
                            mask.data[index + 1]
                        ) * 0.25f

                    out[index] = when {
                        localAvg > 0.58f -> (value + 0.035f).coerceAtMost(1f)
                        localAvg < 0.42f -> (value - 0.035f).coerceAtLeast(0f)
                        else -> value
                    }
                }
            }
        }

        return Mask(width, height, out)
    }

    private fun colorDistance(a: Int, b: Int): Float {
        val ar = (a shr 16) and 0xFF
        val ag = (a shr 8) and 0xFF
        val ab = a and 0xFF
        val br = (b shr 16) and 0xFF
        val bg = (b shr 8) and 0xFF
        val bb = b and 0xFF
        return (abs(ar - br) + abs(ag - bg) + abs(ab - bb)) / 3f
    }

    private data class RemoveBgResult(
        val bitmap: Bitmap,
        val confidencePercent: Int,
    )

    private data class LetterboxResult(
        val bitmap: Bitmap,
        val contentLeft: Int,
        val contentTop: Int,
        val contentWidth: Int,
        val contentHeight: Int,
    )

    private data class Mask(
        val width: Int,
        val height: Int,
        val data: FloatArray,
    ) {
        init {
            require(width > 0 && height > 0)
            require(data.size == width * height)
        }

        fun crop(x: Int, y: Int, width: Int, height: Int): Mask {
            require(width > 0 && height > 0)
            require(x >= 0 && y >= 0)
            require(x + width <= this.width) { "Crop width exceeds mask width" }
            require(y + height <= this.height) { "Crop height exceeds mask height" }

            val out = FloatArray(width * height)
            for (yy in 0 until height) {
                val srcY = y + yy
                val dstRow = yy * width
                for (xx in 0 until width) {
                    out[dstRow + xx] = data[srcY * this.width + x + xx]
                }
            }
            return Mask(width, height, out)
        }

        fun resizeBilinear(targetWidth: Int, targetHeight: Int): Mask {
            require(targetWidth > 0 && targetHeight > 0)
            if (targetWidth == width && targetHeight == height) return this

            val out = FloatArray(targetWidth * targetHeight)

            if (width <= 1 || height <= 1 || targetWidth <= 1 || targetHeight <= 1) {
                out.fill(data.firstOrNull() ?: 0f)
                return Mask(targetWidth, targetHeight, out)
            }

            val xRatio = (width - 1).toFloat() / (targetWidth - 1)
            val yRatio = (height - 1).toFloat() / (targetHeight - 1)

            for (ty in 0 until targetHeight) {
                val srcY = ty * yRatio
                val y0 = floor(srcY).toInt()
                val y1 = min(y0 + 1, height - 1)
                val wy = srcY - y0
                val dstRow = ty * targetWidth

                for (tx in 0 until targetWidth) {
                    val srcX = tx * xRatio
                    val x0 = floor(srcX).toInt()
                    val x1 = min(x0 + 1, width - 1)
                    val wx = srcX - x0

                    val top = data[y0 * width + x0] * (1f - wx) + data[y0 * width + x1] * wx
                    val bottom = data[y1 * width + x0] * (1f - wx) + data[y1 * width + x1] * wx

                    out[dstRow + tx] = (top * (1f - wy) + bottom * wy).coerceIn(0f, 1f)
                }
            }

            return Mask(targetWidth, targetHeight, out)
        }

        fun blurBoxSeparable(radius: Int): Mask {
            if (radius <= 0) return this

            val temp = FloatArray(width * height)
            val out = FloatArray(width * height)
            val kernel = radius * 2 + 1

            for (y in 0 until height) {
                val row = y * width
                var sum = 0f
                for (kx in -radius..radius) {
                    sum += data[row + kx.coerceIn(0, width - 1)]
                }
                for (x in 0 until width) {
                    temp[row + x] = sum / kernel
                    val removeX = (x - radius).coerceIn(0, width - 1)
                    val addX = (x + radius + 1).coerceIn(0, width - 1)
                    sum += data[row + addX] - data[row + removeX]
                }
            }

            for (x in 0 until width) {
                var sum = 0f
                for (ky in -radius..radius) {
                    sum += temp[ky.coerceIn(0, height - 1) * width + x]
                }
                for (y in 0 until height) {
                    out[y * width + x] = (sum / kernel).coerceIn(0f, 1f)
                    val removeY = (y - radius).coerceIn(0, height - 1)
                    val addY = (y + radius + 1).coerceIn(0, height - 1)
                    sum += temp[addY * width + x] - temp[removeY * width + x]
                }
            }

            return Mask(width, height, out)
        }

        fun calculateCertaintyPercent(): Int {
            var sum = 0f
            for (value in data) {
                sum += abs(value - 0.5f) * 2f
            }
            return ((sum / max(1, data.size)) * 100f).roundToInt().coerceIn(0, 100)
        }
    }

    private fun Bitmap.toLetterboxedSquare(size: Int): LetterboxResult {
        val scale = min(size.toFloat() / width, size.toFloat() / height)
        val scaledWidth = max(1, (width * scale).roundToInt())
        val scaledHeight = max(1, (height * scale).roundToInt())
        val contentLeft = (size - scaledWidth) / 2
        val contentTop = (size - scaledHeight) / 2

        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(0xFF808080.toInt())

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(
            this,
            Rect(0, 0, width, height),
            RectF(
                contentLeft.toFloat(),
                contentTop.toFloat(),
                (contentLeft + scaledWidth).toFloat(),
                (contentTop + scaledHeight).toFloat(),
            ),
            paint,
        )

        return LetterboxResult(
            bitmap = output,
            contentLeft = contentLeft,
            contentTop = contentTop,
            contentWidth = scaledWidth,
            contentHeight = scaledHeight,
        )
    }
}

fun Context.loadBitmapFromUri(
    uri: Uri,
    maxDimension: Int = DEFAULT_MAX_DIMENSION,
): Bitmap {
    require(maxDimension > 0) { "maxDimension must be > 0" }

    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
        try {
            val source = ImageDecoder.createSource(contentResolver, uri)
            val decoded = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false

                val width = info.size.width
                val height = info.size.height
                if (width > 0 && height > 0) {
                    val sampleSize = calculateInSampleSize(width, height, maxDimension)
                    if (sampleSize > 1) {
                        decoder.setTargetSize(max(1, width / sampleSize), max(1, height / sampleSize))
                    }
                }
            }
            return decoded.ensureArgb8888CopyIfNeeded()
        } catch (_: Throwable) {
        }
    }

    contentResolver.openInputStream(uri)?.use { stream ->
        val bytes = stream.readBytes()
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

        require(bounds.outWidth > 0 && bounds.outHeight > 0) {
            "Invalid image bounds: ${bounds.outWidth}x${bounds.outHeight}"
        }

        val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
        val decoded = BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inSampleSize = sampleSize
            },
        ) ?: throw IllegalArgumentException("Unable to decode image uri: $uri")

        val orientation = runCatching {
            ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        return decoded.applyExifOrientation(orientation).ensureArgb8888CopyIfNeeded()
    }

    throw IllegalArgumentException("Unable to open image uri: $uri")
}

private fun Bitmap.applyExifOrientation(orientation: Int): Bitmap {
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.postRotate(90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.postRotate(270f)
            matrix.postScale(-1f, 1f)
        }
        else -> return this
    }

    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

private fun Bitmap.ensureArgb8888CopyIfNeeded(): Bitmap {
    if (config == Bitmap.Config.ARGB_8888 && !isRecycled) {
        return this
    }

    return copy(Bitmap.Config.ARGB_8888, false)
        ?: throw IllegalStateException("Unable to convert bitmap to ARGB_8888")
}

private fun calculateInSampleSize(
    width: Int,
    height: Int,
    maxDimension: Int,
): Int {
    if (width <= 0 || height <= 0 || maxDimension <= 0) return 1

    var sampleSize = 1
    while (width / sampleSize > maxDimension || height / sampleSize > maxDimension) {
        sampleSize *= 2
    }
    return max(1, sampleSize)
}

private fun Context.copyAssetToCache(assetName: String): File {
    val safeName = assetName.substringAfterLast('/')
    val target = File(cacheDir, "onnx_models/$safeName")

    if (target.exists() && target.length() > 0) {
        return target
    }

    target.parentFile?.mkdirs()
    val tempFile = File(cacheDir, "onnx_models/.tmp.$safeName.${System.currentTimeMillis()}")

    assets.open(assetName).use { input ->
        tempFile.outputStream().use { output ->
            input.copyTo(output)
        }
    }

    if (!tempFile.renameTo(target)) {
        tempFile.delete()
        if (!target.exists() || target.length() == 0L) {
            throw IllegalStateException("Failed to stage model asset $assetName")
        }
    }

    return target
}
