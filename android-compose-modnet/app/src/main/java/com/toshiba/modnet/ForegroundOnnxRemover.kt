package com.toshiba.modnet

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer
import kotlin.math.exp
import kotlin.math.max

class ForegroundOnnxRemover(
    private val context: Context,
) : AutoCloseable {
    private companion object {
        private const val TAG = "ForegroundOnnxRemover"
    }

    private val environment: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var session: OrtSession? = null
    private var inputName: String? = null
    private var currentAssetName: String? = null

    fun getMask(
        bitmap: Bitmap,
        assetName: String,
        inputSize: Int,
    ): Pair<Mask, Int>? {
        if (!initialize(assetName)) return null

        val currentSession = session ?: return null
        val currentInputName = inputName ?: return null
        val source = bitmap.ensureArgb8888CopyIfNeeded()
        val letterboxed = source.toLetterboxedSquare(inputSize)
        val isBiRefNet = assetName.contains("birefnet", ignoreCase = true)
        val input = imageToNchwFloatTensor(letterboxed.bitmap, inputSize, isBiRefNet)
        val tensor = OnnxTensor.createTensor(
            environment,
            FloatBuffer.wrap(input),
            longArrayOf(1, 3, inputSize.toLong(), inputSize.toLong()),
        )

        return try {
            currentSession.run(mapOf(currentInputName to tensor)).use { outputs ->
                val outputTensor = outputs.get(0) as? OnnxTensor ?: return null
                Log.d(TAG, "$assetName output: ${outputTensor.info.shape.contentToString()}")
                val rawMask = readOutputMask(outputTensor, useSigmoidOnly = isBiRefNet)
                val content = rawMask.crop(
                    letterboxed.contentLeft,
                    letterboxed.contentTop,
                    letterboxed.contentWidth,
                    letterboxed.contentHeight,
                )
                val mask = content.resizeBilinear(source.width, source.height).blurBoxSeparable(1)
                mask to mask.calculateCertaintyPercent()
            }
        } finally {
            tensor.close()
            letterboxed.bitmap.recycle()
            if (source !== bitmap && !source.isRecycled) source.recycle()
        }
    }

    override fun close() {
        session?.close()
        session = null
        inputName = null
        currentAssetName = null
    }

    private fun initialize(assetName: String): Boolean {
        if (session != null && currentAssetName == assetName) return true
        close()
        if (context.assets.list("")?.contains(assetName) != true) return false

        val options = OrtSession.SessionOptions().apply {
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            setIntraOpNumThreads(max(1, Runtime.getRuntime().availableProcessors() / 2))
        }
        return try {
            val bytes = context.assets.open(assetName).use { it.readBytes() }
            val created = environment.createSession(bytes, options)
            session = created
            inputName = created.inputInfo.keys.firstOrNull()
            currentAssetName = assetName
            true
        } finally {
            options.close()
        }
    }

    private fun imageToNchwFloatTensor(image: Bitmap, size: Int, isBiRefNet: Boolean): FloatArray {
        val pixels = IntArray(size * size)
        image.getPixels(pixels, 0, size, 0, 0, size, size)
        val plane = size * size
        val out = FloatArray(plane * 3)
        for (i in pixels.indices) {
            val color = pixels[i]
            val r = ((color shr 16) and 0xFF).toFloat()
            val g = ((color shr 8) and 0xFF).toFloat()
            val b = (color and 0xFF).toFloat()
            if (isBiRefNet) {
                out[i] = r
                out[plane + i] = g
                out[plane * 2 + i] = b
            } else {
                out[i] = (r / 255f - 0.485f) / 0.229f
                out[plane + i] = (g / 255f - 0.456f) / 0.224f
                out[plane * 2 + i] = (b / 255f - 0.406f) / 0.225f
            }
        }
        return out
    }

    private fun readOutputMask(outputTensor: OnnxTensor, useSigmoidOnly: Boolean): Mask {
        val buffer = outputTensor.floatBuffer ?: error("Foreground model output is not float")
        buffer.rewind()
        val dims = outputTensor.info.shape.filter { it > 0L }.map { it.toInt() }
        require(dims.size >= 2) { "Unsupported foreground output shape: ${outputTensor.info.shape.contentToString()}" }

        val height = dims[dims.size - 2]
        val width = dims[dims.size - 1]
        require(width >= 8 && height >= 8) {
            "Foreground output is not spatial mask: ${outputTensor.info.shape.contentToString()}"
        }
        val total = width * height
        require(buffer.remaining() >= total) { "Foreground output buffer smaller than tensor shape" }

        val raw = FloatArray(total)
        buffer.get(raw)
        return Mask(width, height, normalizePrediction(raw, useSigmoidOnly))
    }

    private fun normalizePrediction(raw: FloatArray, useSigmoidOnly: Boolean): FloatArray {
        if (useSigmoidOnly) {
            return FloatArray(raw.size) { sigmoid(raw[it]) }
        }
        var minValue = Float.POSITIVE_INFINITY
        var maxValue = Float.NEGATIVE_INFINITY
        for (value in raw) {
            if (value < minValue) minValue = value
            if (value > maxValue) maxValue = value
        }

        val range = maxValue - minValue
        if (range > 1e-6f) {
            return FloatArray(raw.size) { ((raw[it] - minValue) / range).coerceIn(0f, 1f) }
        }
        return FloatArray(raw.size) { sigmoid(raw[it]) }
    }

    private fun sigmoid(value: Float): Float {
        return (1f / (1f + exp(-value))).coerceIn(0f, 1f)
    }
}
