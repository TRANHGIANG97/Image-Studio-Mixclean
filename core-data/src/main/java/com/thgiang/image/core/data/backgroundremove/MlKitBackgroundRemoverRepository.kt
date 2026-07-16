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
 * Selfie Segmentation (bundled) is used as fallback when Play-services Subject Segmentation fails.
 */
class MlKitBackgroundRemoverRepository(
    private val context: Context,
    private val maxDecodeSize: Int = 2048,
    private val createDisplayCopy: Boolean = true,
    @Suppress("unused") private val alphaSmoothingRadius: Float = 1.5f
) : BackgroundRemoverRepository {

    private companion object {
        /** ML Kit recommends ≥512×512; default upscale target for quality. */
        private const val MIN_PRE_UPSCALE_PIXELS_DEFAULT = 500_000
        private const val MIN_PRE_UPSCALE_PIXELS_LOW_RAM = 262_144 // 512×512
        private const val SUBJECT_MODULE_INSTALL_TIMEOUT_MS = 15_000L
        private const val TAG = "MlKitRemover"
        private val SUBJECT_RETRY_SCALES = floatArrayOf(1f, 0.75f, 0.5f)
    }

    private val minPreUpscalePixels: Int
        get() = if (maxDecodeSize <= 1536) MIN_PRE_UPSCALE_PIXELS_LOW_RAM else MIN_PRE_UPSCALE_PIXELS_DEFAULT

    @Volatile
    private var selfieFallbackUsedInLastOperation: Boolean = false

    @Volatile
    private var playServicesUpdateRecommended: Boolean = false

    @Volatile
    private var subjectModuleUnavailableInLastOperation: Boolean = false

    private val segmenterMutex = Mutex()

    private val segmenter: SubjectSegmenter by lazy {
        // Only enable confidence mask — foreground bitmap doubles GMS native memory use.
        val options = SubjectSegmenterOptions.Builder()
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
        resetOperationFlags()
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
        resetOperationFlags()
        validateBitmap(bitmap)
        Log.d(TAG, "getForegroundBitmap: input=${bitmap.width}x${bitmap.height} hasAlpha=${bitmap.hasAlpha()}")
        getForegroundBitmapInternal(bitmap)
    }

    override suspend fun getPortraitConfidenceMask(bitmap: Bitmap): Result<PortraitConfidenceMask> =
        runCatching {
            resetOperationFlags()
            validateBitmap(bitmap)
            val mask = withContext(Dispatchers.Default) {
                runSegmentationMask(bitmap)
            }
            PortraitConfidenceMask(mask.width, mask.height, mask.data.copyOf())
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

    override fun consumePlayServicesUpdateRecommended(): Boolean {
        val recommended = playServicesUpdateRecommended
        playServicesUpdateRecommended = false
        return recommended
    }

    private fun resetOperationFlags() {
        selfieFallbackUsedInLastOperation = false
        playServicesUpdateRecommended = false
        subjectModuleUnavailableInLastOperation = false
    }

    private fun markPlayServicesUpdateRecommended(reason: Throwable? = null) {
        playServicesUpdateRecommended = true
        Log.w(TAG, "Recommend Google Play services update", reason)
    }

    private suspend fun getForegroundBitmapInternal(bitmap: Bitmap): Bitmap = withContext(Dispatchers.Default) {
        val pixels = bitmap.width * bitmap.height
        val needsUpscale = pixels < minPreUpscalePixels
        Log.d(TAG, "getForegroundBitmapInternal: pixels=$pixels needsUpscale=$needsUpscale (threshold=$minPreUpscalePixels)")
        val processedBitmap = if (needsUpscale) {
            val scale = sqrt(minPreUpscalePixels.toFloat() / pixels)
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

        val mask = runSegmentationMask(processedBitmap)

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

    private suspend fun runSegmentationMask(bitmap: Bitmap): Mask {
        var lastError: Throwable? = null
        for (scale in SUBJECT_RETRY_SCALES) {
            val workBitmap = if (scale >= 0.99f) {
                bitmap
            } else {
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).toInt().coerceAtLeast(64),
                    (bitmap.height * scale).toInt().coerceAtLeast(64),
                    true,
                )
            }
            val ownsWorkBitmap = workBitmap !== bitmap
            try {
                val mlStart = System.currentTimeMillis()
                val input = InputImage.fromBitmap(workBitmap, 0)
                val result = processSafely(input)
                val buffer = result.foregroundConfidenceMask
                    ?: error("Foreground confidence mask not available")
                Log.d(
                    TAG,
                    "SubjectSegmenter OK scale=$scale size=${workBitmap.width}x${workBitmap.height} " +
                        "took ${System.currentTimeMillis() - mlStart}ms",
                )
                val mask = readMask(buffer, workBitmap.width, workBitmap.height)
                return if (scale < 0.99f) {
                    mask.resizeBilinear(bitmap.width, bitmap.height)
                } else {
                    mask
                }
            } catch (e: Throwable) {
                lastError = e
                Log.w(
                    TAG,
                    "SubjectSegmenter failed scale=$scale size=${workBitmap.width}x${workBitmap.height}",
                    e,
                )
            } finally {
                if (ownsWorkBitmap && !workBitmap.isRecycled) workBitmap.recycle()
            }
        }

        return runSelfieFallbackMask(bitmap, lastError)
    }

    private suspend fun runSelfieFallbackMask(bitmap: Bitmap, priorError: Throwable?): Mask {
        if (!SelfieFallbackSegmenter.isSupported) {
            if (subjectModuleUnavailableInLastOperation ||
                GooglePlayServicesHelper.isUpdateRecommended(context) ||
                GooglePlayServicesHelper.isPlayServicesRelatedFailure(priorError)
            ) {
                markPlayServicesUpdateRecommended(priorError)
            }
            throw priorError ?: error("Subject segmentation failed")
        }
        Log.w(TAG, "Falling back to SelfieSegmenter", priorError)
        selfieFallbackUsedInLastOperation = true
        if (subjectModuleUnavailableInLastOperation ||
            GooglePlayServicesHelper.isUpdateRecommended(context)
        ) {
            markPlayServicesUpdateRecommended(priorError)
        }
        val mlStart = System.currentTimeMillis()
        val input = InputImage.fromBitmap(bitmap, 0)
        val resultBuffer = SelfieFallbackSegmenter.process(input)
        Log.d(TAG, "SelfieSegmenter took ${System.currentTimeMillis() - mlStart}ms")
        return readMask(resultBuffer.asFloatBuffer(), bitmap.width, bitmap.height)
    }

    private suspend fun processSafely(input: InputImage) = segmenterMutex.withLock {
        val moduleReady = ensureSubjectSegmenterModuleAvailable()
        if (!moduleReady) {
            subjectModuleUnavailableInLastOperation = true
            markPlayServicesUpdateRecommended()
            error("SubjectSegmenter Play services module is not available")
        }
        runCatching { segmenter.process(input).await() }
            .getOrElse { error ->
                if (GooglePlayServicesHelper.isPlayServicesRelatedFailure(error)) {
                    markPlayServicesUpdateRecommended(error)
                }
                throw error
            }
    }

    private suspend fun ensureSubjectSegmenterModuleAvailable(): Boolean {
        return runCatching {
            val moduleInstallClient = ModuleInstall.getClient(context)
            val availability = moduleInstallClient.areModulesAvailable(segmenter).await()
            if (availability.areModulesAvailable()) {
                Log.d(TAG, "SubjectSegmenter module already available")
                return true
            }

            Log.d(TAG, "SubjectSegmenter module missing; requesting install")
            val installed = requestSubjectSegmenterModuleInstall(moduleInstallClient)
            Log.d(TAG, "SubjectSegmenter module install completed=$installed")
            if (!installed) {
                subjectModuleUnavailableInLastOperation = true
                markPlayServicesUpdateRecommended()
            }
            installed
        }.getOrElse { e ->
            Log.e(TAG, "SubjectSegmenter module availability/install check failed", e)
            subjectModuleUnavailableInLastOperation = true
            markPlayServicesUpdateRecommended(e)
            false
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
