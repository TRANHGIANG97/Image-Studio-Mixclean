package com.toshiba.modnet

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
import java.io.ByteArrayInputStream
import java.nio.FloatBuffer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class RemoveBgResult(
    val bitmap: Bitmap,
    val confidencePercent: Int,
    val route: String = "modnet",
    val timing: MaskTiming = MaskTiming(),
    val maskPreview: Bitmap? = null,
)

data class MaskTiming(
    val totalMs: Long = 0L,
    val modNetMs: Long = 0L,
    val coreMs: Long = 0L,
    val fusionMs: Long = 0L,
)

private const val DEFAULT_MAX_DIMENSION = 2048

object BackgroundRemover : AutoCloseable {
    private const val DEFAULT_MODEL_SIZE = 512
    private const val DEFAULT_MODEL_ASSET = "modnet_matte_512_fp16.onnx"

    private val environment: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var session: OrtSession? = null
    private var inputName: String? = null
    private var outputName: String? = null
    private var modelSize: Int = DEFAULT_MODEL_SIZE
    private var currentModelAssetPath: String? = null

    init {
        try {
            System.loadLibrary("bg_refiner")
        } catch (e: Throwable) {
            android.util.Log.e("ModNetONNX", "Failed to load bg_refiner native library", e)
        }
    }

    private external fun decryptModelNative(encryptedData: ByteArray): ByteArray

    @Synchronized
    fun initialize(
        context: Context,
        modelAssetPath: String = DEFAULT_MODEL_ASSET,
        size: Int = DEFAULT_MODEL_SIZE,
    ) {
        if (session != null && currentModelAssetPath == modelAssetPath && modelSize == size) return
        close()

        modelSize = size
        val options = OrtSession.SessionOptions().apply {
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            setIntraOpNumThreads(max(1, Runtime.getRuntime().availableProcessors() / 2))
        }

        try {
            val encryptedBytes = context.assets.open(modelAssetPath).use { it.readBytes() }
            val decryptedBytes = runCatching { decryptModelNative(encryptedBytes) }.getOrNull()
            val createdSession = runCatching {
                requireNotNull(decryptedBytes) { "Unable to decrypt model bytes" }
                environment.createSession(decryptedBytes, options)
            }.getOrElse {
                environment.createSession(encryptedBytes, options)
            }
            session = createdSession
            currentModelAssetPath = modelAssetPath
            decryptedBytes?.fill(0)
            encryptedBytes.fill(0)

            inputName = createdSession.inputInfo.keys.firstOrNull()
                ?: error("ONNX model has no input")
            outputName = createdSession.outputInfo.keys.firstOrNull()
                ?: error("ONNX model has no output")
        } finally {
            options.close()
        }
    }

    fun getMask(
        bitmap: Bitmap,
        smoothMask: Boolean = true,
        enhanceEdges: Boolean = false,
    ): Pair<Mask, Int> {
        val currentSession = session ?: error("ONNX session not initialized")
        val currentInputName = inputName ?: error("ONNX input name not initialized")
        val currentOutputName = outputName ?: error("ONNX output name not initialized")

        val source = bitmap.ensureArgb8888CopyIfNeeded()
        val letterboxed = source.toLetterboxedSquare(modelSize)
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
                    var mask = if (smoothMask) contentMask.blurBoxSeparable(1) else contentMask
                    mask = mask.resizeBilinear(source.width, source.height)
                    if (enhanceEdges) {
                        mask = refineMaskByImageEdges(source, mask)
                    }
                    mask to confidencePercent
                }
            }
        } finally {
            inputTensor.close()
            if (source !== bitmap && !source.isRecycled) source.recycle()
        }
    }

    fun removeBg(
        bitmap: Bitmap,
        smoothMask: Boolean = true,
        enhanceEdges: Boolean = false,
    ): RemoveBgResult {
        val (mask, confidencePercent) = getMask(bitmap, smoothMask, enhanceEdges)
        return RemoveBgResult(
            bitmap = applyMaskToImage(bitmap.ensureArgb8888CopyIfNeeded(), mask),
            confidencePercent = confidencePercent,
            route = "modnet",
            maskPreview = mask.toPreviewBitmap(),
        )
    }

    override fun close() {
        session?.close()
        session = null
        inputName = null
        outputName = null
        currentModelAssetPath = null
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

        val dims = outputTensor.info.shape.filter { it > 0L }.map { it.toInt() }
        require(dims.isNotEmpty()) { "Invalid ONNX output shape: ${outputTensor.info.shape.contentToString()}" }
        val (height, width) = when (dims.size) {
            2 -> dims[0] to dims[1]
            3 -> dims[1] to dims[2]
            4 -> {
                require(dims[0] == 1) { "Batch size must be 1, got ${dims[0]}" }
                dims[2] to dims[3]
            }
            else -> throw IllegalStateException("Unsupported output shape: ${outputTensor.info.shape.contentToString()}")
        }

        val total = width * height
        require(buffer.remaining() >= total) { "Output buffer smaller than tensor shape" }
        return Mask(width, height, FloatArray(total) { buffer.get().coerceIn(0f, 1f) })
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

                val gradient = colorDistance(pixels[index - 1], pixels[index + 1]) +
                    colorDistance(pixels[index - width], pixels[index + width])
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
}

data class LetterboxResult(
    val bitmap: Bitmap,
    val contentLeft: Int,
    val contentTop: Int,
    val contentWidth: Int,
    val contentHeight: Int,
)

fun Bitmap.toLetterboxedSquare(size: Int): LetterboxResult {
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
        RectF(contentLeft.toFloat(), contentTop.toFloat(), (contentLeft + scaledWidth).toFloat(), (contentTop + scaledHeight).toFloat()),
        paint,
    )

    return LetterboxResult(output, contentLeft, contentTop, scaledWidth, scaledHeight)
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
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
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

fun Bitmap.ensureArgb8888CopyIfNeeded(): Bitmap {
    if (config == Bitmap.Config.ARGB_8888 && !isRecycled) return this
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
