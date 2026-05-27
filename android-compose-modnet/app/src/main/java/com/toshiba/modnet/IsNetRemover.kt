package com.toshiba.modnet

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

class IsNetRemover(
    private val context: Context,
    private val inputSize: Int = 512,
) : AutoCloseable {
    private companion object {
        private const val TAG = "IsNetRemover"
    }

    private val environment: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var session: OrtSession? = null
    private var inputName: String? = null
    private var assetName: String? = null

    val isAvailable: Boolean
        get() = true

    fun initializeIfAvailable(modelAsset: String): Boolean {
        if (session != null && assetName == modelAsset) return true
        
        session?.close()
        session = null
        inputName = null
        
        val options = OrtSession.SessionOptions().apply {
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            try {
                addNnapi()
                Log.d(TAG, "NNAPI Hardware Acceleration activated successfully!")
            } catch (e: Exception) {
                Log.w(TAG, "NNAPI not supported on this device, falling back to CPU", e)
            }
            setIntraOpNumThreads(max(1, Runtime.getRuntime().availableProcessors() / 2))
        }
        return try {
            val bytes = context.assets.open(modelAsset).use { it.readBytes() }
            val created = environment.createSession(bytes, options)
            session = created
            inputName = created.inputInfo.keys.firstOrNull()
            assetName = modelAsset
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model $modelAsset", e)
            false
        } finally {
            options.close()
        }
    }

    fun getCoreMask(bitmap: Bitmap, modelAsset: String, inputSize: Int): IsNetMaskResult? {
        if (!initializeIfAvailable(modelAsset)) return null

        val currentSession = session ?: return null
        val currentInputName = inputName ?: return null
        val source = bitmap.ensureArgb8888CopyIfNeeded()
        val letterboxed = source.toLetterboxedSquare(inputSize)
        val input = imageToNchwFloatTensor(letterboxed.bitmap, inputSize)
        val tensor = OnnxTensor.createTensor(
            environment,
            FloatBuffer.wrap(input),
            longArrayOf(1, 3, inputSize.toLong(), inputSize.toLong()),
        )

        return try {
            currentSession.run(mapOf(currentInputName to tensor)).use { outputs ->
                val outputTensor = outputs.get(0) as? OnnxTensor ?: return null
                val outputSummary = outputTensor.info.shape.contentToString()
                Log.d(TAG, "IS-Net output: $outputSummary")
                val rawMask = readOutputMask(outputTensor)
                val content = rawMask.crop(
                    letterboxed.contentLeft,
                    letterboxed.contentTop,
                    letterboxed.contentWidth,
                    letterboxed.contentHeight,
                )
                val mask = IsNetMaskCleaner.clean(
                    content.resizeBilinear(source.width, source.height)
                )
                if (mask.foregroundCoverage(0.5f) < 0.003f) return null
                IsNetMaskResult(mask, mask.calculateCertaintyPercent(), assetName ?: "isnet", outputSummary)
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
    }

    private fun imageToNchwFloatTensor(image: Bitmap, size: Int): FloatArray {
        val pixels = IntArray(size * size)
        image.getPixels(pixels, 0, size, 0, 0, size, size)
        val plane = size * size
        val out = FloatArray(plane * 3)
        for (i in pixels.indices) {
            val color = pixels[i]
            val r = ((color shr 16) and 0xFF).toFloat()
            val g = ((color shr 8) and 0xFF).toFloat()
            val b = (color and 0xFF).toFloat()
            out[i] = (r - 128f) / 256f
            out[plane + i] = (g - 128f) / 256f
            out[plane * 2 + i] = (b - 128f) / 256f
        }
        return out
    }

    private fun readOutputMask(outputTensor: OnnxTensor): Mask {
        val buffer = outputTensor.floatBuffer ?: error("IS-Net output is not float")
        buffer.rewind()
        val dims = outputTensor.info.shape.filter { it > 0L }.map { it.toInt() }
        require(dims.size >= 2) { "Unsupported IS-Net output shape: ${outputTensor.info.shape.contentToString()}" }

        val height: Int
        val width: Int
        when (dims.size) {
            2 -> {
                height = dims[0]
                width = dims[1]
            }
            3 -> {
                height = dims[1]
                width = dims[2]
            }
            else -> {
                height = dims[dims.size - 2]
                width = dims[dims.size - 1]
            }
        }

        val total = width * height
        require(buffer.remaining() >= total) { "IS-Net output buffer smaller than tensor shape" }

        val raw = FloatArray(total)
        buffer.get(raw)
        return Mask(width, height, normalizePrediction(raw))
    }

    private fun normalizePrediction(raw: FloatArray): FloatArray {
        // Loại bỏ hoàn toàn nhiễu nền/bóng râm bằng cách nâng ngưỡng cắt lên 0.08f,
        // đồng thời dùng Smoothstep nới rộng từ [0.08f, 0.50f] để bảo toàn chi tiết tóc siêu mảnh và giữ độ chuyển tiếp mượt mà.
        val edgeMin = 0.08f
        val edgeMax = 0.50f
        return FloatArray(raw.size) {
            val v = raw[it].coerceIn(0f, 1f)
            if (v < edgeMin) {
                0.0f
            } else if (v > edgeMax) {
                1.0f
            } else {
                val t = (v - edgeMin) / (edgeMax - edgeMin)
                t * t * (3.0f - 2.0f * t)
            }
        }
    }
}

data class IsNetMaskResult(
    val mask: Mask,
    val confidencePercent: Int,
    val modelAsset: String,
    val outputSummary: String,
)
