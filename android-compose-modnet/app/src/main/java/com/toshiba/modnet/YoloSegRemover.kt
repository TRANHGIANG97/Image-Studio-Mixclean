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
import kotlin.math.sqrt

class YoloSegRemover(
    private val context: Context,
    private val inputSize: Int = 640,
    private val confidenceThreshold: Float = 0.35f,
    private val maskThreshold: Float = 0.45f,
) : AutoCloseable {
    private companion object {
        private const val TAG = "YoloSegRemover"
    }

    private val environment: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var session: OrtSession? = null
    private var inputName: String? = null
    private var assetName: String? = null

    val isAvailable: Boolean
        get() = findModelAsset() != null

    fun initializeIfAvailable(): Boolean {
        if (session != null) return true
        val modelAsset = findModelAsset() ?: return false
        val options = OrtSession.SessionOptions().apply {
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            setIntraOpNumThreads(max(1, Runtime.getRuntime().availableProcessors() / 2))
        }

        return try {
            val bytes = context.assets.open(modelAsset).use { it.readBytes() }
            val created = environment.createSession(bytes, options)
            session = created
            inputName = created.inputInfo.keys.firstOrNull()
            assetName = modelAsset
            true
        } finally {
            options.close()
        }
    }

    fun getCoreMask(bitmap: Bitmap): YoloMaskResult? {
        if (!initializeIfAvailable()) return null

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
                val tensors = mutableListOf<OnnxTensor>()
                for (i in 0 until outputs.size()) {
                    val tensorValue = outputs.get(i) as? OnnxTensor ?: continue
                    tensors += tensorValue
                }

                val parsed = parseOutputs(tensors) ?: return null
                Log.d(TAG, "YOLO outputs: ${parsed.summary}")

                val detections = readDetections(parsed.detection)
                val proto = readProto(parsed.proto)
                val best = selectBestDetection(detections, proto.maskDim) ?: return null
                if (best.confidence < confidenceThreshold) return null

                val mask = buildMask(best, proto, letterboxed, source.width, source.height)
                if (mask.foregroundCoverage(maskThreshold) < 0.003f) return null

                YoloMaskResult(mask, best.confidence, best.classIndex, assetName ?: "yolo-seg", parsed.summary)
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

    private fun findModelAsset(): String? {
        val names = context.assets.list("")?.toSet().orEmpty()
        return listOf(
            "yolov8n-seg.onnx",
            "yolo-seg.onnx",
            "yolov8s-seg.onnx",
        ).firstOrNull { it in names }
    }

    private fun imageToNchwFloatTensor(image: Bitmap, size: Int): FloatArray {
        val pixels = IntArray(size * size)
        image.getPixels(pixels, 0, size, 0, 0, size, size)
        val plane = size * size
        val out = FloatArray(plane * 3)
        for (i in pixels.indices) {
            val color = pixels[i]
            out[i] = ((color shr 16) and 0xFF) / 255f
            out[plane + i] = ((color shr 8) and 0xFF) / 255f
            out[plane * 2 + i] = (color and 0xFF) / 255f
        }
        return out
    }

    private fun parseOutputs(outputs: List<OnnxTensor>): ParsedYoloOutputs? {
        var detectionTensor: OnnxTensor? = null
        var protoTensor: OnnxTensor? = null
        val summary = StringBuilder()

        for ((index, tensor) in outputs.withIndex()) {
            val shape = shapeOf(tensor)
            if (summary.isNotEmpty()) summary.append(" | ")
            summary.append("out").append(index).append("=").append(shape.joinToString(prefix = "[", postfix = "]"))

            when {
                isProtoShape(shape) && protoTensor == null -> protoTensor = tensor
                isDetectionShape(shape) && detectionTensor == null -> detectionTensor = tensor
            }
        }

        if (detectionTensor == null || protoTensor == null) {
            Log.w(TAG, "Unable to classify YOLO outputs: $summary")
            return null
        }

        return ParsedYoloOutputs(detectionTensor, protoTensor, summary.toString())
    }

    private fun shapeOf(tensor: OnnxTensor): List<Int> {
        return tensor.info.shape.filter { it > 0L }.map { it.toInt() }
    }

    private fun isProtoShape(shape: List<Int>): Boolean {
        return shape.size == 4 &&
            shape[1] in 8..128 &&
            shape[2] >= 8 &&
            shape[3] >= 8
    }

    private fun isDetectionShape(shape: List<Int>): Boolean {
        if (shape.size != 3) return false
        val a = shape[1]
        val b = shape[2]
        return (a in 5..512 && b >= 256) || (b in 5..512 && a >= 256)
    }

    private fun readDetections(tensor: OnnxTensor): DetectionTable {
        val shape = shapeOf(tensor)
        require(shape.size == 3) {
            "Unsupported YOLO detection shape: ${shape.joinToString(prefix = "[", postfix = "]")}"
        }

        val buffer = tensor.floatBuffer ?: error("YOLO detection output is not float")
        buffer.rewind()
        val raw = FloatArray(buffer.remaining())
        buffer.get(raw)

        val dimA = shape[1]
        val dimB = shape[2]
        val channelsFirst = dimA < dimB
        val channels = if (channelsFirst) dimA else dimB
        val count = if (channelsFirst) dimB else dimA
        return DetectionTable(raw, count, channels, channelsFirst)
    }

    private fun readProto(tensor: OnnxTensor): ProtoTable {
        val shape = shapeOf(tensor)
        require(shape.size == 4) {
            "Unsupported YOLO proto shape: ${shape.joinToString(prefix = "[", postfix = "]")}"
        }

        val maskDim = shape[1]
        val height = shape[2]
        val width = shape[3]
        val buffer = tensor.floatBuffer ?: error("YOLO proto output is not float")
        buffer.rewind()
        val raw = FloatArray(buffer.remaining())
        buffer.get(raw)
        return ProtoTable(raw, maskDim, width, height)
    }

    private fun selectBestDetection(table: DetectionTable, maskDim: Int): Detection? {
        val classCount = table.channels - 4 - maskDim
        if (classCount <= 0) return null

        var best: Detection? = null
        var bestRank = 0f
        for (i in 0 until table.count) {
            val cx = table.value(i, 0)
            val cy = table.value(i, 1)
            val w = table.value(i, 2)
            val h = table.value(i, 3)
            if (w <= 2f || h <= 2f) continue

            var classIndex = -1
            var confidence = 0f
            for (c in 0 until classCount) {
                val score = table.value(i, 4 + c)
                if (score > confidence) {
                    confidence = score
                    classIndex = c
                }
            }
            if (confidence < confidenceThreshold) continue

            val areaFactor = sqrt((w * h) / (inputSize * inputSize).coerceAtLeast(1).toFloat()).coerceIn(0.05f, 1.25f)
            val rank = confidence * areaFactor
            if (rank > bestRank) {
                val coeffs = FloatArray(maskDim) { table.value(i, 4 + classCount + it) }
                best = Detection(cx, cy, w, h, confidence, classIndex, coeffs)
                bestRank = rank
            }
        }
        return best
    }

    private fun buildMask(
        detection: Detection,
        proto: ProtoTable,
        letterbox: LetterboxResult,
        outWidth: Int,
        outHeight: Int,
    ): Mask {
        val logits = FloatArray(proto.width * proto.height)
        for (m in 0 until proto.maskDim) {
            val coeff = detection.coeffs[m]
            val offset = m * proto.width * proto.height
            for (i in logits.indices) {
                logits[i] += coeff * proto.data[offset + i]
            }
        }

        val boxLeft = detection.cx - detection.w / 2f
        val boxTop = detection.cy - detection.h / 2f
        val boxRight = detection.cx + detection.w / 2f
        val boxBottom = detection.cy + detection.h / 2f
        val out = FloatArray(outWidth * outHeight)

        for (y in 0 until outHeight) {
            val inputY = letterbox.contentTop + (y + 0.5f) * letterbox.contentHeight / outHeight
            if (inputY < boxTop || inputY > boxBottom) continue
            val protoY = inputY / inputSize * proto.height - 0.5f
            val y0 = floor(protoY).toInt().coerceIn(0, proto.height - 1)
            val y1 = min(y0 + 1, proto.height - 1)
            val wy = (protoY - y0).coerceIn(0f, 1f)
            val row = y * outWidth

            for (x in 0 until outWidth) {
                val inputX = letterbox.contentLeft + (x + 0.5f) * letterbox.contentWidth / outWidth
                if (inputX < boxLeft || inputX > boxRight) continue
                val protoX = inputX / inputSize * proto.width - 0.5f
                val x0 = floor(protoX).toInt().coerceIn(0, proto.width - 1)
                val x1 = min(x0 + 1, proto.width - 1)
                val wx = (protoX - x0).coerceIn(0f, 1f)
                val top = logits[y0 * proto.width + x0] * (1f - wx) + logits[y0 * proto.width + x1] * wx
                val bottom = logits[y1 * proto.width + x0] * (1f - wx) + logits[y1 * proto.width + x1] * wx
                out[row + x] = sigmoid(top * (1f - wy) + bottom * wy)
            }
        }

        return Mask(outWidth, outHeight, out).blurBoxSeparable(1)
    }

    private fun sigmoid(value: Float): Float {
        return (1f / (1f + exp(-value))).coerceIn(0f, 1f)
    }

    private data class DetectionTable(
        val data: FloatArray,
        val count: Int,
        val channels: Int,
        val channelsFirst: Boolean,
    ) {
        fun value(index: Int, channel: Int): Float {
            return if (channelsFirst) data[channel * count + index] else data[index * channels + channel]
        }
    }

    private data class ProtoTable(
        val data: FloatArray,
        val maskDim: Int,
        val width: Int,
        val height: Int,
    )

    private data class ParsedYoloOutputs(
        val detection: OnnxTensor,
        val proto: OnnxTensor,
        val summary: String,
    )

    private data class Detection(
        val cx: Float,
        val cy: Float,
        val w: Float,
        val h: Float,
        val confidence: Float,
        val classIndex: Int,
        val coeffs: FloatArray,
    )
}

data class YoloMaskResult(
    val mask: Mask,
    val confidence: Float,
    val classIndex: Int,
    val modelAsset: String,
    val outputSummary: String,
)
