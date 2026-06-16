package com.thgiang.image.core.data.backgroundremove

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.google.android.gms.common.moduleinstall.InstallStatusListener
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.common.moduleinstall.ModuleInstallStatusUpdate.InstallState.STATE_CANCELED
import com.google.android.gms.common.moduleinstall.ModuleInstallStatusUpdate.InstallState.STATE_COMPLETED
import com.google.android.gms.common.moduleinstall.ModuleInstallStatusUpdate.InstallState.STATE_FAILED
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenter
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import java.nio.FloatBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.ByteBuffer
import kotlin.coroutines.resume
import kotlin.math.sqrt

/**
 * Background remover backed by ML Kit Subject Segmentation.
 *
 * Subject Segmentation is attempted on every ABI, including x86 emulators.
 * Selfie Segmentation is intentionally disabled by default because it is weak
 * for objects/products and should only be used by explicitly opted-in callers.
 */
class MlKitBackgroundRemoverRepository(
    private val context: Context,
    private val maxDecodeSize: Int = 2048,
    private val createDisplayCopy: Boolean = true,
    @Suppress("unused") private val alphaSmoothingRadius: Float = 1.5f
) : BackgroundRemoverRepository {

    private companion object {
        private const val MIN_PRE_UPSCALE_PIXELS = 500_000
        private const val SUBJECT_MODULE_INSTALL_TIMEOUT_MS = 15_000L
        private const val TAG = "MlKitRemover"
    }

    @Volatile
    private var selfieFallbackUsedInLastOperation: Boolean = false

    private val segmenterMutex = Mutex()

    private val segmenter: SubjectSegmenter by lazy {
        val options = SubjectSegmenterOptions.Builder()
            .enableForegroundBitmap()
            .enableForegroundConfidenceMask()
            .build()
        SubjectSegmentation.getClient(options)
    }

    private fun isX86Emulator(): Boolean {
        val isEmulator = (android.os.Build.BRAND.startsWith("generic") && android.os.Build.DEVICE.startsWith("generic"))
                || android.os.Build.FINGERPRINT.startsWith("generic")
                || android.os.Build.FINGERPRINT.startsWith("unknown")
                || android.os.Build.HARDWARE.contains("goldfish")
                || android.os.Build.HARDWARE.contains("ranchu")
                || android.os.Build.MODEL.contains("google_sdk")
                || android.os.Build.MODEL.contains("Emulator")
                || android.os.Build.MODEL.contains("Android SDK built for x86")
                || android.os.Build.MANUFACTURER.contains("Genymotion")
                || android.os.Build.PRODUCT.contains("sdk_google")
                || android.os.Build.PRODUCT.contains("google_sdk")
                || android.os.Build.PRODUCT.contains("sdk")
                || android.os.Build.PRODUCT.contains("sdk_x86")
                || android.os.Build.PRODUCT.contains("vbox86p")
                || android.os.Build.PRODUCT.contains("emulator")
                || android.os.Build.PRODUCT.contains("simulator")

        val isX86 = android.os.Build.SUPPORTED_ABIS.any { it.contains("x86") }
        return isEmulator && isX86
    }

    override suspend fun removeBackground(imageUri: Uri): Result<BackgroundRemovalOutput> = runCatching {
        selfieFallbackUsedInLastOperation = false
        Log.d(TAG, "removeBackground: start uri=$imageUri")
        val original = BitmapDecodeUtils.loadBitmapFromUri(context, imageUri, maxDecodeSize)
            ?: error("Cannot decode selected image (OOM or invalid image)")
        Log.d(TAG, "removeBackground: decoded original=${original.width}x${original.height}")

        val foreground = runCatching {
            getForegroundBitmapInternal(original)
        }.getOrElse { e ->
            original.recycle()
            throw e
        }
        Log.d(TAG, "removeBackground: foreground ready=${foreground.width}x${foreground.height}")
        original.recycle()

        val display = if (createDisplayCopy) {
            foreground.copy(Bitmap.Config.ARGB_8888, true)
        } else {
            foreground
        }

        BackgroundRemovalOutput(
            foregroundToDisplay = display,
            foregroundToSave = foreground
        )
    }

    override suspend fun getForegroundBitmap(bitmap: Bitmap): Result<Bitmap> = runCatching {
        selfieFallbackUsedInLastOperation = false
        validateBitmap(bitmap)
        Log.d(TAG, "getForegroundBitmap: input=${bitmap.width}x${bitmap.height} hasAlpha=${bitmap.hasAlpha()}")
        getForegroundBitmapInternal(bitmap)
    }

    override suspend fun getPortraitConfidenceMask(bitmap: Bitmap): Result<PortraitConfidenceMask> =
        runCatching {
            selfieFallbackUsedInLastOperation = false
            validateBitmap(bitmap)
            val input = InputImage.fromBitmap(bitmap, 0)

            val buffer = withContext(Dispatchers.Default) {
                if (isX86Emulator() && SelfieFallbackSegmenter.isSupported) {
                    runCatching {
                        val result = processSafely(input)
                        result.foregroundConfidenceMask ?: error("Foreground confidence mask not available")
                    }.getOrElse { e ->
                        Log.w(TAG, "getPortraitConfidenceMask: SubjectSegmenter failed on x86 emulator; falling back to SelfieSegmenter", e)
                        selfieFallbackUsedInLastOperation = true
                        val buffer = SelfieFallbackSegmenter.process(input)
                        buffer.asFloatBuffer()
                    }
                } else {
                    val result = processSafely(input)
                    result.foregroundConfidenceMask ?: error("Foreground confidence mask not available")
                }
            }

            buffer.rewind()
            val count = buffer.remaining()
            val values = FloatArray(count)
            buffer.get(values)
            val w = bitmap.width
            val h = bitmap.height

            when {
                count == w * h -> PortraitConfidenceMask(w, h, values)
                else -> {
                    val side = sqrt(count.toDouble()).toInt()
                    require(side * side == count) {
                        "Unexpected mask size $count for bitmap ${w}x$h. Only square masks are supported."
                    }
                    PortraitConfidenceMask(side, side, values)
                }
            }
        }

    override fun close() {
        runCatching { segmenter.close() }
        runCatching { SelfieFallbackSegmenter.close() }
    }

    override fun consumeSelfieFallbackWarning(): Boolean {
        val used = selfieFallbackUsedInLastOperation
        selfieFallbackUsedInLastOperation = false
        return used
    }

    private suspend fun getForegroundBitmapInternal(bitmap: Bitmap): Bitmap = withContext(Dispatchers.Default) {
        val pixels = bitmap.width * bitmap.height
        val needsUpscale = pixels < MIN_PRE_UPSCALE_PIXELS
        Log.d(TAG, "getForegroundBitmapInternal: pixels=$pixels needsUpscale=$needsUpscale (threshold=$MIN_PRE_UPSCALE_PIXELS)")
        val processedBitmap = if (needsUpscale) {
            val scale = sqrt(MIN_PRE_UPSCALE_PIXELS.toFloat() / pixels)
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(bitmap.width + 1),
                (bitmap.height * scale).toInt().coerceAtLeast(bitmap.height + 1),
                true
            )
        } else {
            bitmap
        }

        val startTime = System.currentTimeMillis()
        val input = InputImage.fromBitmap(processedBitmap, 0)

        val mask = run {
            if (isX86Emulator() && SelfieFallbackSegmenter.isSupported) {
                runCatching {
                    Log.d(TAG, "getForegroundBitmapInternal: attempting SubjectSegmenter on x86 emulator")
                    val mlStart = System.currentTimeMillis()
                    val result = processSafely(input)
                    Log.d(TAG, "getForegroundBitmapInternal: ML Kit Subject Segmenter took ${System.currentTimeMillis() - mlStart}ms")
                    val buffer = result.foregroundConfidenceMask ?: error("Foreground confidence mask failed")
                    readMask(buffer, processedBitmap.width, processedBitmap.height)
                }.getOrElse { e ->
                    Log.w(TAG, "SubjectSegmenter failed on x86 emulator; falling back to SelfieSegmenter", e)
                    selfieFallbackUsedInLastOperation = true
                    val mlStart = System.currentTimeMillis()
                    val resultBuffer = SelfieFallbackSegmenter.process(input)
                    Log.d(TAG, "getForegroundBitmapInternal: ML Kit Selfie Segmenter took ${System.currentTimeMillis() - mlStart}ms")
                    val buffer = resultBuffer.asFloatBuffer()
                    readMask(buffer, processedBitmap.width, processedBitmap.height)
                }
            } else {
                Log.d(TAG, "getForegroundBitmapInternal: using SubjectSegmenter for mask")
                val mlStart = System.currentTimeMillis()
                val result = processSafely(input)
                Log.d(TAG, "getForegroundBitmapInternal: ML Kit Segmenter took ${System.currentTimeMillis() - mlStart}ms")
                val buffer = result.foregroundConfidenceMask ?: error("Foreground confidence mask failed")
                readMask(buffer, processedBitmap.width, processedBitmap.height)
            }
        }

        try {
            val maskStart = System.currentTimeMillis()
            val foreground = applyMaskToImage(processedBitmap, mask)
            Log.d(TAG, "getForegroundBitmapInternal: applyMaskToImage (including Fast Guided Filter) took ${System.currentTimeMillis() - maskStart}ms")
            Log.d(TAG, "getForegroundBitmapInternal: Total background removal took ${System.currentTimeMillis() - startTime}ms")

            if (needsUpscale) {
                if (processedBitmap !== bitmap && !processedBitmap.isRecycled) processedBitmap.recycle()
                val scaledDown = Bitmap.createScaledBitmap(foreground, bitmap.width, bitmap.height, true)
                if (scaledDown !== foreground && !foreground.isRecycled) foreground.recycle()
                Log.d(TAG, "getForegroundBitmapInternal: returning scaledDown=${scaledDown.width}x${scaledDown.height}")
                scaledDown
            } else {
                Log.d(TAG, "getForegroundBitmapInternal: returning foreground=${foreground.width}x${foreground.height}")
                foreground
            }
        } catch (e: Exception) {
            throw e
        }
    }

    private fun readMask(buffer: java.nio.FloatBuffer, bitmapWidth: Int, bitmapHeight: Int): Mask {
        buffer.rewind()
        val count = buffer.remaining()
        val values = FloatArray(count)
        buffer.get(values)
        return if (count == bitmapWidth * bitmapHeight) {
            Mask(bitmapWidth, bitmapHeight, values)
        } else {
            val side = kotlin.math.sqrt(count.toDouble()).toInt()
            require(side * side == count) { "Unexpected ML Kit mask size: $count" }
            Mask(side, side, values).resizeBilinear(bitmapWidth, bitmapHeight)
        }
    }

    private suspend fun processSafely(input: InputImage) = segmenterMutex.withLock {
        ensureSubjectSegmenterModuleAvailable()
        segmenter.process(input).await()
    }

    private suspend fun ensureSubjectSegmenterModuleAvailable() {
        runCatching {
            val moduleInstallClient = ModuleInstall.getClient(context)
            val availability = moduleInstallClient.areModulesAvailable(segmenter).await()
            if (availability.areModulesAvailable()) {
                Log.d(TAG, "SubjectSegmenter module already available")
                return
            }

            Log.d(TAG, "SubjectSegmenter module missing; requesting install")
            val installed = requestSubjectSegmenterModuleInstall(moduleInstallClient)
            Log.d(TAG, "SubjectSegmenter module install completed=$installed")
        }.onFailure { e ->
            Log.e(TAG, "SubjectSegmenter module availability/install check failed", e)
        }
    }

    private suspend fun requestSubjectSegmenterModuleInstall(
        moduleInstallClient: com.google.android.gms.common.moduleinstall.ModuleInstallClient
    ): Boolean {
        return withTimeoutOrNull(SUBJECT_MODULE_INSTALL_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                var listener: InstallStatusListener? = null

                fun finish(installed: Boolean) {
                    listener?.let { moduleInstallClient.unregisterListener(it) }
                    if (continuation.isActive) continuation.resume(installed)
                }

                listener = InstallStatusListener { update ->
                    when (update.installState) {
                        STATE_COMPLETED -> finish(true)
                        STATE_CANCELED, STATE_FAILED -> finish(false)
                    }
                }

                continuation.invokeOnCancellation {
                    listener?.let { moduleInstallClient.unregisterListener(it) }
                }

                val request = ModuleInstallRequest.newBuilder()
                    .addApi(segmenter)
                    .setListener(listener)
                    .build()

                moduleInstallClient.installModules(request)
                    .addOnSuccessListener { response ->
                        if (response.areModulesAlreadyInstalled()) {
                            finish(true)
                        } else {
                            Log.d(TAG, "SubjectSegmenter module install request accepted")
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "SubjectSegmenter module install request failed", e)
                        finish(false)
                    }
            }
        } ?: false
    }



    private fun validateBitmap(bitmap: Bitmap) {
        require(!bitmap.isRecycled) { "Bitmap is already recycled" }
        require(bitmap.config == Bitmap.Config.ARGB_8888) {
            "Bitmap config must be ARGB_8888, actual: ${bitmap.config}"
        }
    }

    private suspend fun decodeBitmapWithResize(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, this)
                }
                var sample = 1
                while (outWidth / sample > maxDecodeSize || outHeight / sample > maxDecodeSize) {
                    sample *= 2
                }
                inSampleSize = sample
                inJustDecodeBounds = false
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
        }.getOrNull()
    }
}
