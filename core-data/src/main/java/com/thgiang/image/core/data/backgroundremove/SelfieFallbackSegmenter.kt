package com.thgiang.image.core.data.backgroundremove

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import java.io.File
import java.nio.ByteBuffer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.coroutineContext

/**
 * Bundled selfie segmenter used when Play-services Subject Segmentation fails
 * (native crash, RemoteException, OOM inside GMS, missing module, etc.).
 *
 * Requires [libxeno_native.so] (MediaPipe). Release builds exclude x86 copies for
 * Play 16KB policy — use ARM device/emulator or debug APK with x86 libs included.
 */
object SelfieFallbackSegmenter {

    private val selfieSegmenter by lazy {
        val options = SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
            .build()
        Segmentation.getClient(options)
    }

    /** True when MediaPipe native lib was packaged for this device ABI. */
    fun isNativeLibraryAvailable(context: Context): Boolean {
        val lib = File(context.applicationInfo.nativeLibraryDir, "libxeno_native.so")
        return lib.exists()
    }

    fun isSupported(context: Context): Boolean = isNativeLibraryAvailable(context)

    /** Load native model once on a tiny bitmap — speeds up first real removal on slow emulators. */
    suspend fun warmUp(context: Context) {
        if (!isSupported(context)) return
        MlKitDebugDiagnostics.logStage(context, "selfie_warmup", "loading MediaPipe model…")
        val start = System.currentTimeMillis()
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        try {
            process(context, InputImage.fromBitmap(bitmap, 0), logHeartbeat = false)
            MlKitDebugDiagnostics.logStage(
                context, "selfie_warmup", "ready", System.currentTimeMillis() - start,
            )
        } finally {
            bitmap.recycle()
        }
    }

    suspend fun process(context: Context, input: InputImage): ByteBuffer =
        process(context, input, logHeartbeat = true)

    private suspend fun process(
        context: Context,
        input: InputImage,
        logHeartbeat: Boolean,
    ): ByteBuffer {
        if (!isSupported(context)) {
            error(MlKitDeviceSupport.ERROR_DEVICE_UNSUPPORTED)
        }
        val start = System.currentTimeMillis()
        var heartbeat: Job? = null
        if (logHeartbeat && MlKitDeviceSupport.isDebugBuild(context)) {
            heartbeat = CoroutineScope(coroutineContext).launch {
                while (isActive) {
                    delay(5_000)
                    MlKitDebugDiagnostics.logStage(
                        context,
                        "selfie_heartbeat",
                        "MediaPipe still running… ${System.currentTimeMillis() - start}ms",
                    )
                }
            }
        }
        return try {
            selfieSegmenter.process(input).await().buffer
        } catch (e: UnsatisfiedLinkError) {
            error(MlKitDeviceSupport.ERROR_DEVICE_UNSUPPORTED)
        } catch (e: LinkageError) {
            error(MlKitDeviceSupport.ERROR_DEVICE_UNSUPPORTED)
        } finally {
            heartbeat?.cancel()
        }
    }

    fun close() {
        runCatching { selfieSegmenter.close() }
    }
}
