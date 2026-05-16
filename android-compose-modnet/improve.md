package com.toshiba.modnet

import android.content.ComponentCallbacks2
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.WorkerThread
import androidx.exifinterface.media.ExifInterface
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.Closeable
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// ==================== Data Classes ====================

data class RemoveBgResult(
val bitmap: Bitmap,
val maskCertaintyPercent: Int,
val metrics: InferenceMetrics,
)

data class InferenceMetrics(
val totalMs: Long,
val preprocessMs: Long,
val inferenceMs: Long,
val postprocessMs: Long,
val memoryUsedMb: Long,
val tensorShape: String,
val executionProvider: String,
)

data class RemoveBgOptions(
val smoothMask: Boolean = true,
val enhanceEdges: Boolean = true,
val blurRadius: Int = 2,
val recycleTemporaryBitmaps: Boolean = true,
val targetMaskResolution: Int = 256,
val timeoutMs: Long = 30_000,
)

enum class ExecutionProvider {
NNAPI, CPU, UNKNOWN
}

// ==================== Bitmap Pool (Fixed) ====================

class BitmapPool(private val maxPoolSize: Int = 6) : Closeable {
private val pool = ArrayDeque<PooledBitmap>()
private val lock = Any()
private val activeCount = AtomicInteger(0)
private val closed = AtomicBoolean(false)

    data class PooledBitmap(
        val bitmap: Bitmap,
        val width: Int,
        val height: Int,
        val config: Bitmap.Config,
    )
    
    fun obtain(width: Int, height: Int, config: Bitmap.Config = Bitmap.Config.ARGB_8888): Bitmap {
        check(!closed.get()) { "BitmapPool is closed" }
        require(width > 0 && height > 0) { "Invalid dimensions: ${width}x$height" }
        
        synchronized(lock) {
            // Clean up recycled bitmaps first
            val iterator = pool.iterator()
            while (iterator.hasNext()) {
                val pooled = iterator.next()
                if (pooled.bitmap.isRecycled) {
                    iterator.remove()
                    continue
                }
                // EXACT match only - fix bug trả bitmap lớn hơn
                if (pooled.width == width && pooled.height == height && pooled.config == config) {
                    iterator.remove()
                    activeCount.incrementAndGet()
                    return pooled.bitmap
                }
            }
        }
        
        activeCount.incrementAndGet()
        return Bitmap.createBitmap(width, height, config)
    }
    
    fun recycle(bitmap: Bitmap) {
        if (closed.get() || bitmap.isRecycled) {
            if (!bitmap.isRecycled) bitmap.recycle()
            activeCount.decrementAndGet()
            return
        }
        
        synchronized(lock) {
            if (pool.size >= maxPoolSize) {
                bitmap.recycle()
                activeCount.decrementAndGet()
                return
            }
            
            pool.add(PooledBitmap(bitmap, bitmap.width, bitmap.height, bitmap.config))
        }
        activeCount.decrementAndGet()
    }
    
    fun release(bitmap: Bitmap) {
        if (!bitmap.isRecycled) {
            bitmap.recycle()
        }
        activeCount.decrementAndGet()
    }
    
    fun trimToSize(maxSize: Int) {
        synchronized(lock) {
            while (pool.size > maxSize) {
                pool.removeFirstOrNull()?.bitmap?.recycle()
            }
        }
    }
    
    fun clear() {
        synchronized(lock) {
            pool.forEach { it.bitmap.recycle() }
            pool.clear()
        }
    }
    
    override fun close() {
        if (closed.compareAndSet(false, true)) {
            clear()
        }
    }
    
    val stats: String get() = "active=${activeCount.get()}, pooled=${pool.size}"
}

// ==================== Tensor Factory (Fixed) ====================

class TensorFactory(
private val env: OrtEnvironment,
private val modelSize: Int,
) : Closeable {
private val closed = AtomicBoolean(false)
private val bufferPool = ArrayDeque<ByteBuffer>()
private val lock = Any()

    // Pre-calculate buffer size: 1 * 3 * H * W * 4 bytes
    private val bufferSize = 1 * 3 * modelSize * modelSize * 4
    
    fun createTensor(data: FloatArray): OnnxTensor {
        check(!closed.get()) { "TensorFactory is closed" }
        require(data.size == 3 * modelSize * modelSize) {
            "Data size ${data.size} != expected ${3 * modelSize * modelSize}"
        }
        
        // Get or create direct buffer
        val buffer = synchronized(lock) {
            bufferPool.removeFirstOrNull()?.also { it.clear() }
        } ?: ByteBuffer.allocateDirect(bufferSize).order(ByteOrder.nativeOrder())
        
        val floatBuffer = buffer.asFloatBuffer()
        floatBuffer.put(data)
        floatBuffer.rewind()
        
        return OnnxTensor.createTensor(
            env,
            floatBuffer,
            longArrayOf(1, 3, modelSize.toLong(), modelSize.toLong())
        )
    }
    
    fun recycleBuffer(tensor: OnnxTensor) {
        tensor.close()
        // Note: ONNX tensor closes underlying buffer, so we can't truly pool it
        // In production, use lower-level API if available
    }
    
    override fun close() {
        if (closed.compareAndSet(false, true)) {
            synchronized(lock) {
                bufferPool.clear()
            }
        }
    }
}

// ==================== Main Class (Fixed) ====================

class BackgroundRemover private constructor(
private val context: Context,
private val modelAssetPath: String,
private val modelSize: Int,
private val maxConcurrentJobs: Int,
val dispatcher: CoroutineDispatcher,
) : Closeable {

    companion object {
        private const val TAG = "BackgroundRemover"
        private const val DEFAULT_MODEL_SIZE = 512
        private const val DEFAULT_MODEL_ASSET = "modnet_matte_512_fp16.ort"
        private const val DEFAULT_MAX_CONCURRENT = 1 // Fixed: giảm xuống 1 để tránh OOM
        
        private val modelCache = ConcurrentHashMap<String, File>()
        
        suspend fun create(
            context: Context,
            modelAssetPath: String = DEFAULT_MODEL_ASSET,
            modelSize: Int = DEFAULT_MODEL_SIZE,
            maxConcurrentJobs: Int = DEFAULT_MAX_CONCURRENT,
            dispatcher: CoroutineDispatcher = Dispatchers.Default,
            warmup: Boolean = true,
        ): BackgroundRemover {
            require(modelSize > 0) { "modelSize must be > 0" }
            require(maxConcurrentJobs > 0) { "maxConcurrentJobs must be > 0" }
            
            val instance = BackgroundRemover(
                context = context.applicationContext,
                modelAssetPath = modelAssetPath,
                modelSize = modelSize,
                maxConcurrentJobs = maxConcurrentJobs,
                dispatcher = dispatcher,
            )
            
            withContext(dispatcher) {
                instance.initialize()
                if (warmup) {
                    instance.warmup()
                }
            }
            
            return instance
        }
    }
    
    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val sessionLock = Any()
    private val closed = AtomicBoolean(false)
    private val semaphore = Semaphore(maxConcurrentJobs)
    
    @Volatile
    private var session: OrtSession? = null
    
    private var executionProvider = ExecutionProvider.UNKNOWN
    
    // Resource pools
    val bitmapPool = BitmapPool()
    private val tensorFactory by lazy { TensorFactory(env, modelSize) }
    
    // Metrics
    private val inferenceCount = AtomicInteger(0)
    
    // Memory pressure handling
    private val memoryPressureListener = object : ComponentCallbacks2 {
        override fun onTrimMemory(level: Int) {
            when (level) {
                ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
                ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                    Log.w(TAG, "Critical memory pressure, clearing pools")
                    bitmapPool.trimToSize(0)
                    System.gc()
                }
                ComponentCallbacks2.TRIM_MEMORY_MODERATE -> {
                    Log.i(TAG, "Moderate memory pressure, trimming pools")
                    bitmapPool.trimToSize(bitmapPool.maxPoolSize / 2)
                }
            }
        }
        
        override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {}
        override fun onLowMemory() {
            bitmapPool.trimToSize(0)
        }
    }
    
    init {
        context.registerComponentCallbacks(memoryPressureListener)
    }
    
    private fun initialize() {
        val startTime = System.currentTimeMillis()
        val modelFile = getOrLoadModel(modelAssetPath)
        
        val options = OrtSession.SessionOptions().apply {
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            setIntraOpNumThreads(max(1, Runtime.getRuntime().availableProcessors() / 2))
            
            // Try NNAPI first
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    addNnapi()
                    executionProvider = ExecutionProvider.NNAPI
                    Log.i(TAG, "Using NNAPI execution provider")
                } catch (e: Exception) {
                    Log.w(TAG, "NNAPI not available, falling back to CPU", e)
                    executionProvider = ExecutionProvider.CPU
                }
            } else {
                executionProvider = ExecutionProvider.CPU
            }
        }
        
        try {
            val createdSession = env.createSession(modelFile.absolutePath, options)
            
            synchronized(sessionLock) {
                if (closed.get()) {
                    createdSession.close()
                    throw IllegalStateException("BackgroundRemover closed during initialization")
                }
                session = createdSession
            }
            
            Log.i(TAG, "Session initialized in ${System.currentTimeMillis() - startTime}ms " +
                    "with provider=$executionProvider")
        } catch (t: Throwable) {
            synchronized(sessionLock) {
                session?.close()
                session = null
            }
            throw t
        } finally {
            options.close()
        }
    }
    
    private fun getOrLoadModel(assetName: String): File {
        // Check cache first
        modelCache[assetName]?.let { cached ->
            if (cached.exists() && cached.length() > 0) {
                return cached
            }
            modelCache.remove(assetName)
        }
        
        val safeName = assetName.substringAfterLast('/').ifBlank { assetName }
        val target = File(context.cacheDir, "onnx_models/$safeName")
        
        // Atomic copy with temp file
        if (!target.exists() || target.length() == 0L) {
            target.parentFile?.mkdirs()
            val tempFile = File(context.cacheDir, "onnx_models/.tmp.$safeName.${System.currentTimeMillis()}")
            
            try {
                context.assets.open(assetName).use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                
                // Atomic rename
                if (!tempFile.renameTo(target)) {
                    tempFile.delete()
                    if (!target.exists()) {
                        throw IllegalStateException("Failed to move model file")
                    }
                }
            } catch (e: Exception) {
                tempFile.delete()
                throw e
            }
        }
        
        modelCache[assetName] = target
        return target
    }
    
    private fun warmup() {
        var dummyBitmap: Bitmap? = null
        try {
            dummyBitmap = Bitmap.createBitmap(modelSize, modelSize, Bitmap.Config.ARGB_8888)
            val dummyData = FloatArray(3 * modelSize * modelSize) { 0.5f }
            
            val currentSession = getSession()
            val currentInputName = getInputName()
            val currentOutputName = getOutputName()
            
            val buffer = ByteBuffer.allocateDirect(dummyData.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
            buffer.put(dummyData)
            buffer.rewind()
            
            OnnxTensor.createTensor(
                env,
                buffer,
                longArrayOf(1, 3, modelSize.toLong(), modelSize.toLong())
            ).use { tensor ->
                currentSession.run(mapOf(currentInputName to tensor)).use { outputs ->
                    outputs.get(currentOutputName)
                }
            }
            
            Log.i(TAG, "Warmup completed")
        } catch (e: Exception) {
            Log.w(TAG, "Warmup failed", e)
        } finally {
            dummyBitmap?.recycle()
        }
    }
    
    // Thread-safe session access
    private fun getSession(): OrtSession = synchronized(sessionLock) {
        if (closed.get()) throw IllegalStateException("BackgroundRemover is closed")
        session ?: throw IllegalStateException("ONNX session not initialized")
    }
    
    private fun getInputName(): String = synchronized(sessionLock) {
        if (closed.get()) throw IllegalStateException("BackgroundRemover is closed")
        session?.inputInfo?.keys?.firstOrNull()
            ?: throw IllegalStateException("ONNX model has no input")
    }
    
    private fun getOutputName(): String = synchronized(sessionLock) {
        if (closed.get()) throw IllegalStateException("BackgroundRemover is closed")
        session?.outputInfo?.keys?.firstOrNull()
            ?: throw IllegalStateException("ONNX model has no output")
    }
    
    // ==================== Public API ====================
    
    suspend fun removeBg(
        source: Bitmap,
        options: RemoveBgOptions = RemoveBgOptions(),
    ): RemoveBgResult = withContext(dispatcher) {
        coroutineContext.ensureActive()
        
        semaphore.acquire()
        try {
            withTimeout(options.timeoutMs) {
                removeBgInternal(source, options)
            }
        } catch (e: TimeoutCancellationException) {
            throw BackgroundRemoverException("Inference timeout after ${options.timeoutMs}ms", e)
        } catch (e: CancellationException) {
            throw e
        } finally {
            semaphore.release()
        }
    }
    
    @WorkerThread
    private suspend fun removeBgInternal(
        source: Bitmap,
        options: RemoveBgOptions,
    ): RemoveBgResult {
        check(!closed.get()) { "BackgroundRemover is already closed" }
        coroutineContext.ensureActive()
        
        val totalStart = System.currentTimeMillis()
        val runtime = Runtime.getRuntime()
        val memBefore = runtime.totalMemory() - runtime.freeMemory()
        
        val currentSession = getSession()
        val currentInputName = getInputName()
        val currentOutputName = getOutputName()
        
        require(!source.isRecycled) { "Source bitmap is recycled" }
        require(source.width > 0 && source.height > 0) { "Invalid bitmap dimensions" }
        
        var workingBitmap: Bitmap? = null
        var letterbox: LetterboxResult? = null
        
        try {
            // ==================== PREPROCESS ====================
            val preprocessStart = System.currentTimeMillis()
            
            workingBitmap = source.ensureArgb8888CopyIfNeeded(bitmapPool)
            coroutineContext.ensureActive()
            
            letterbox = workingBitmap.toLetterbox(modelSize, bitmapPool)
            coroutineContext.ensureActive()
            
            val inputTensorData = imageToNchwFloatTensor(letterbox.bitmap, modelSize)
            coroutineContext.ensureActive()
            
            val preprocessMs = System.currentTimeMillis() - preprocessStart
            
            // ==================== INFERENCE ====================
            val inferenceStart = System.currentTimeMillis()
            
            val rawMask: Mask = synchronized(sessionLock) {
                coroutineContext.ensureActive()
                
                val buffer = ByteBuffer.allocateDirect(inputTensorData.size * 4)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer()
                buffer.put(inputTensorData)
                buffer.rewind()
                
                OnnxTensor.createTensor(
                    env,
                    buffer,
                    longArrayOf(1, 3, modelSize.toLong(), modelSize.toLong())
                ).use { inputTensor ->
                    coroutineContext.ensureActive()
                    
                    currentSession.run(mapOf(currentInputName to inputTensor)).use { outputs ->
                        coroutineContext.ensureActive()
                        
                        val value = outputs.get(currentOutputName).orElseThrow()
                        val outputTensor = value as? OnnxTensor
                            ?: throw IllegalStateException("Unexpected ONNX output")
                        
                        readOutputMask(outputTensor)
                    }
                }
            }
            
            val inferenceMs = System.currentTimeMillis() - inferenceStart
            
            // ==================== POSTPROCESS ====================
            val postprocessStart = System.currentTimeMillis()
            
            var mask = rawMask.crop(
                x = letterbox.contentX,
                y = letterbox.contentY,
                width = letterbox.contentWidth,
                height = letterbox.contentHeight,
            ).resizeBilinear(
                targetWidth = workingBitmap.width,
                targetHeight = workingBitmap.height,
            )
            coroutineContext.ensureActive()
            
            if (options.enhanceEdges) {
                mask = refineMaskByImageEdges(workingBitmap, mask)
                coroutineContext.ensureActive()
            }
            
            if (options.smoothMask && options.blurRadius > 0) {
                val scaleFactor = max(1, min(workingBitmap.width, workingBitmap.height) / options.targetMaskResolution)
                mask = if (scaleFactor > 1) {
                    val smallWidth = workingBitmap.width / scaleFactor
                    val smallHeight = workingBitmap.height / scaleFactor
                    mask.resizeBilinear(smallWidth, smallHeight)
                        .blurBoxSeparable(options.blurRadius)
                        .resizeBilinear(workingBitmap.width, workingBitmap.height)
                } else {
                    mask.blurBoxSeparable(options.blurRadius)
                }
                coroutineContext.ensureActive()
            }
            
            val output = applyMaskToImage(workingBitmap, mask, bitmapPool)
            val certainty = rawMask.calculateCertaintyPercent()
            
            val postprocessMs = System.currentTimeMillis() - postprocessStart
            val totalMs = System.currentTimeMillis() - totalStart
            
            // Update metrics
            inferenceCount.incrementAndGet()
            val memAfter = runtime.totalMemory() - runtime.freeMemory()
            
            val metrics = InferenceMetrics(
                totalMs = totalMs,
                preprocessMs = preprocessMs,
                inferenceMs = inferenceMs,
                postprocessMs = postprocessMs,
                memoryUsedMb = (memAfter - memBefore) / (1024 * 1024),
                tensorShape = "1x3x${modelSize}x${modelSize}",
                executionProvider = executionProvider.name,
            )
            
            Log.d(TAG, "Inference #${inferenceCount.get()}: ${totalMs}ms " +
                    "(pre=${preprocessMs}, inf=${inferenceMs}, post=${postprocessMs}), " +
                    "mem=${metrics.memoryUsedMb}MB")
            
            return RemoveBgResult(
                bitmap = output,
                maskCertaintyPercent = certainty,
                metrics = metrics,
            )
            
        } finally {
            // Guaranteed cleanup
            if (options.recycleTemporaryBitmaps) {
                workingBitmap?.let { wb ->
                    if (wb !== source && !wb.isRecycled) {
                        bitmapPool.recycle(wb)
                    }
                }
                letterbox?.bitmap?.let { lb ->
                    if (!lb.isRecycled) {
                        bitmapPool.recycle(lb)
                    }
                }
            }
        }
    }
    
    // ==================== Background Composition ====================
    
    fun addBackground(
        foreground: Bitmap,
        backgroundColor: Int,
        pool: BitmapPool? = null,
    ): Bitmap {
        val width = foreground.width
        val height = foreground.height
        
        val outBitmap = pool?.obtain(width, height) 
            ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outBitmap)
        
        canvas.drawColor(backgroundColor)
        
        val paint = Paint().apply { isAntiAlias = true }
        canvas.drawBitmap(foreground, 0f, 0f, paint)
        
        return outBitmap
    }
    
    // ==================== Resource Management ====================
    
    override fun close() {
        if (closed.compareAndSet(false, true)) {
            context.unregisterComponentCallbacks(memoryPressureListener)
            
            synchronized(sessionLock) {
                session?.close()
                session = null
            }
            // Không đóng env - shared singleton
            
            bitmapPool.close()
            tensorFactory.close()
        }
    }
    
    val stats: String
        get() = "Inferences: ${inferenceCount.get()}, Pool: ${bitmapPool.stats}, Provider: $executionProvider"
    
    // ==================== Private Helpers ====================
    
    private fun imageToNchwFloatTensor(image: Bitmap, size: Int): FloatArray {
        require(image.width == size && image.height == size) {
            "Input bitmap must be ${size}x$size, got ${image.width}x${image.height}"
        }
        
        val pixels = IntArray(size * size)
        image.getPixels(pixels, 0, size, 0, 0, size, size)
        
        val plane = size * size
        val out = FloatArray(plane * 3)
        
        for (i in pixels.indices) {
            val color = pixels[i]
            val r = ((color shr 16) and 0xFF) / 255f
            val g = ((color shr 8) and 0xFF) / 255f
            val b = (color and 0xFF) / 255f
            
            out[i] = (r - 0.5f) / 0.5f
            out[plane + i] = (g - 0.5f) / 0.5f
            out[plane * 2 + i] = (b - 0.5f) / 0.5f
        }
        
        return out
    }
    
    private fun readOutputMask(outputTensor: OnnxTensor): Mask {
        val buffer = outputTensor.floatBuffer
            ?: throw IllegalStateException("Unexpected ONNX output type")
        
        buffer.rewind()
        
        val shape = outputTensor.info.shape
        val dims = shape.filter { it > 0L }.map { it.toInt() }
        
        val total = dims.fold(1) { acc, v -> acc * v }
        require(total > 0) { "Invalid ONNX output shape: ${shape.contentToString()}" }
        require(buffer.remaining() >= total) {
            "Output buffer smaller than tensor shape"
        }
        
        val (height, width) = when (dims.size) {
            2 -> dims[0] to dims[1]
            3 -> dims[1] to dims[2]
            4 -> {
                require(dims[0] == 1) { "Batch size must be 1, got ${dims[0]}" }
                dims[2] to dims[3]
            }
            else -> throw IllegalStateException("Unsupported shape: ${shape.contentToString()}")
        }
        
        val expected = width * height
        val skip = max(0, total - expected)
        
        repeat(skip) { buffer.get() }
        
        val data = FloatArray(expected) { buffer.get().coerceIn(0f, 1f) }
        return Mask(width, height, data)
    }
    
    private data class LetterboxResult(
        val bitmap: Bitmap,
        val contentX: Int,
        val contentY: Int,
        val contentWidth: Int,
        val contentHeight: Int,
    )
    
    private fun Bitmap.toLetterbox(size: Int, pool: BitmapPool? = null): LetterboxResult {
        val scale = min(
            size.toFloat() / width,
            size.toFloat() / height,
        )
        
        val scaledWidth = max(1, (width * scale).roundToInt())
        val scaledHeight = max(1, (height * scale).roundToInt())
        
        val output = pool?.obtain(size, size) 
            ?: Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        
        val canvas = Canvas(output)
        canvas.drawARGB(0, 0, 0, 0)
        
        val offsetX = (size - scaledWidth) / 2
        val offsetY = (size - scaledHeight) / 2
        
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        
        canvas.drawBitmap(
            this,
            Rect(0, 0, width, height),
            RectF(
                offsetX.toFloat(),
                offsetY.toFloat(),
                (offsetX + scaledWidth).toFloat(),
                (offsetY + scaledHeight).toFloat(),
            ),
            paint,
        )
        
        return LetterboxResult(
            bitmap = output,
            contentX = offsetX,
            contentY = offsetY,
            contentWidth = scaledWidth,
            contentHeight = scaledHeight,
        )
    }
    
    private fun applyMaskToImage(
        image: Bitmap,
        mask: Mask,
        pool: BitmapPool? = null,
    ): Bitmap {
        require(mask.width == image.width && mask.height == image.height) {
            "Mask size ${mask.width}x${mask.height} != image ${image.width}x${image.height}"
        }
        
        val width = image.width
        val height = image.height
        
        val srcPixels = IntArray(width * height)
        image.getPixels(srcPixels, 0, width, 0, 0, width, height)
        
        val outBitmap = pool?.obtain(width, height) 
            ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val outPixels = IntArray(srcPixels.size)
        
        for (i in srcPixels.indices) {
            val src = srcPixels[i]
            val srcAlpha = (src ushr 24) and 0xFF
            val maskAlpha = (mask.data[i].coerceIn(0f, 1f) * 255f).roundToInt()
            
            val alpha = (srcAlpha * maskAlpha / 255f).roundToInt().coerceIn(0, 255)
            outPixels[i] = (alpha shl 24) or (src and 0x00FFFFFF)
        }
        
        outBitmap.setPixels(outPixels, 0, width, 0, 0, width, height)
        return outBitmap
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
                val m = mask.data[index]
                
                if (m !in 0.12f..0.88f) continue
                
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
                        localAvg > 0.58f -> (m + 0.035f).coerceAtMost(1f)
                        localAvg < 0.42f -> (m - 0.035f).coerceAtLeast(0f)
                        else -> m
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
            
            // Horizontal
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
            
            // Vertical
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
            for (v in data) sum += abs(v - 0.5f) * 2f
            return ((sum / max(1, data.size)) * 100f).roundToInt().coerceIn(0, 100)
        }
    }
}

// ==================== Exception ====================

class BackgroundRemoverException(message: String, cause: Throwable? = null) : Exception(message, cause)

// ==================== Extensions (Fixed) ====================

fun Context.loadBitmapFromUri(
uri: Uri,
maxDimension: Int = 2048,
pool: BitmapPool? = null,
): Bitmap {
require(maxDimension > 0) { "maxDimension must be > 0" }

    // Stream-based decode without loading entire file
    contentResolver.openInputStream(uri)?.use { stream ->
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        
        // Mark stream to allow reset
        if (stream.markSupported()) {
            stream.mark(Int.MAX_VALUE)
        }
        
        BitmapFactory.decodeStream(stream, null, bounds)
        
        if (stream.markSupported()) {
            stream.reset()
        }
        
        require(bounds.outWidth > 0 && bounds.outHeight > 0) {
            "Invalid image bounds: ${bounds.outWidth}x${bounds.outHeight}"
        }
        
        val sampleSize = calculateInSampleSize(
            width = bounds.outWidth,
            height = bounds.outHeight,
            maxDimension = maxDimension,
        )
        
        val decodeOptions = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inSampleSize = sampleSize
            
            // Safe inBitmap usage
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB && pool != null) {
                try {
                    val reuseWidth = bounds.outWidth / sampleSize
                    val reuseHeight = bounds.outHeight / sampleSize
                    inBitmap = pool.obtain(reuseWidth, reuseHeight, Bitmap.Config.ARGB_8888)
                } catch (e: Exception) {
                    // Fallback: don't use inBitmap
                    inBitmap = null
                }
            }
        }
        
        val decoded = if (stream.markSupported()) {
            stream.reset()
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        } else {
            // Fallback: reopen stream
            contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            }
        } ?: throw IllegalArgumentException("Unable to decode image uri: $uri")
        
        // Handle inBitmap failure
        if (decodeOptions.inBitmap != null && decoded == null) {
            // Retry without inBitmap
            decodeOptions.inBitmap = null
            val retryDecoded = contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            } ?: throw IllegalArgumentException("Unable to decode image uri: $uri")
            
            return retryDecoded.applyExifOrientation(uri, this, pool)
        }
        
        return decoded.applyExifOrientation(uri, this, pool)
    } ?: throw IllegalArgumentException("Unable to open image uri: $uri")
}

private fun Bitmap.applyExifOrientation(
uri: Uri,
context: Context,
pool: BitmapPool? = null,
): Bitmap {
val orientation = context.contentResolver.openInputStream(uri)?.use { stream ->
runCatching {
ExifInterface(stream).getAttributeInt(
ExifInterface.TAG_ORIENTATION,
ExifInterface.ORIENTATION_NORMAL,
)
}.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
} ?: ExifInterface.ORIENTATION_NORMAL

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
        else -> return ensureArgb8888CopyIfNeeded(pool)
    }
    
    val transformed = Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
        .ensureArgb8888CopyIfNeeded(pool)
    
    if (transformed !== this && !isRecycled) {
        pool?.recycle(this) ?: recycle()
    }
    
    return transformed
}

private fun Bitmap.ensureArgb8888CopyIfNeeded(pool: BitmapPool? = null): Bitmap {
if (config == Bitmap.Config.ARGB_8888 && !isRecycled) {
return this
}

    val copy = copy(Bitmap.Config.ARGB_8888, false)
        ?: throw IllegalStateException("Unable to convert bitmap to ARGB_8888")
    
    if (this !== copy && !isRecycled) {
        pool?.recycle(this) ?: recycle()
    }
    
    return copy
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