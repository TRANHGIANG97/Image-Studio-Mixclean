package com.thgiang.image.studio.ui.editor.product

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.unit.IntSize
import com.thgiang.image.core.data.backgroundremove.BackgroundRemoverRepository
import com.thgiang.image.core.data.save.ImageSaveRepository
import com.thgiang.image.core.util.processors.ProcessorUtils
import com.thgiang.image.studio.R
import com.thgiang.image.studio.ui.editor.EditorLayer
import com.thgiang.image.studio.ui.editor.EditorLayerFactory
import com.thgiang.image.studio.ui.editor.EditorProduct
import com.thgiang.image.studio.ui.editor.SampleObjectCacheManager
import com.thgiang.image.studio.ui.editor.load.EditorTemplateLoader
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ProductImageResult {
    data class Ready(val product: EditorProduct) : ProductImageResult
    data class Failed(val message: String?) : ProductImageResult
}

sealed interface SampleObjectResult {
    data class Ready(val product: EditorProduct) : SampleObjectResult
    data object NotFound : SampleObjectResult
    data class Failed(val message: String) : SampleObjectResult
}

sealed interface StickerResult {
    data class Ready(val layer: EditorLayer) : StickerResult
    data class Failure(val message: String) : StickerResult
}

data class ProcessingLayerUpdate(
    val processingId: String,
    val layers: List<EditorLayer>,
)

@Singleton
class EditorProductWorkflow @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backgroundRemoverRepository: BackgroundRemoverRepository,
    private val imageSaveRepository: ImageSaveRepository,
    private val sampleObjectCacheManager: SampleObjectCacheManager,
    private val templateLoader: EditorTemplateLoader,
    private val layerFactory: EditorLayerFactory,
) {
    fun newProcessingId(): String = UUID.randomUUID().toString()

    fun buildProcessingLayerUpdate(
        uri: Uri,
        replaceLayerId: String?,
        currentLayers: List<EditorLayer>,
    ): ProcessingLayerUpdate {
        val processingId = replaceLayerId ?: newProcessingId()
        val processingLayer = EditorLayer(
            id = processingId,
            product = EditorProduct(originalUriString = uri.toString(), processing = true),
        )
        val newLayers = if (replaceLayerId != null && currentLayers.any { it.id == replaceLayerId }) {
            currentLayers.map {
                if (it.id == replaceLayerId) processingLayer.copy(viewport = it.viewport) else it
            }
        } else {
            currentLayers + processingLayer
        }
        return ProcessingLayerUpdate(processingId, newLayers)
    }

    fun buildSampleProcessingLayer(processingId: String): EditorLayer =
        EditorLayer(id = processingId, product = EditorProduct(processing = true, isSample = true))

    suspend fun processUserImage(uri: Uri): ProductImageResult {
        val decoded = withContext(Dispatchers.Default) {
            ProcessorUtils.decodeBitmapFromUri(context, uri)
        } ?: return ProductImageResult.Failed(null)

        return try {
            val foreground = withContext(Dispatchers.Default) {
                backgroundRemoverRepository.getForegroundBitmap(decoded).getOrNull()
            } ?: return ProductImageResult.Failed(null)

            try {
                val cachedUri = withContext(Dispatchers.IO) {
                    imageSaveRepository.cacheBitmap(foreground).getOrNull()
                } ?: return ProductImageResult.Failed(null)

                // Keep the logical layer size tied to the original decoded image.
                // The foreground bitmap can be tighter after background removal, which
                // would otherwise make the layer render shorter than the template slot.
                ProductImageResult.Ready(
                    EditorProduct(
                        originalUriString = uri.toString(),
                        foregroundUriString = cachedUri.toString(),
                        isBackgroundRemoved = true,
                        baseWidth = decoded.width.coerceAtLeast(foreground.width).coerceAtLeast(0),
                        baseHeight = decoded.height.coerceAtLeast(foreground.height).coerceAtLeast(0),
                        processing = false,
                    ),
                )
            } finally {
                foreground.recycle()
            }
        } catch (e: Exception) {
            ProductImageResult.Failed(e.message ?: context.getString(R.string.studio_error_process_image))
        } finally {
            decoded.recycle()
        }
    }

    suspend fun loadSampleObject(assetPath: String): SampleObjectResult {
        return try {
            val cachedUri = sampleObjectCacheManager.getOrExtract(assetPath)
                ?: return SampleObjectResult.NotFound

            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(cachedUri).use { input ->
                    BitmapFactory.decodeStream(input, null, options)
                }
            }

            val baseSize = IntSize(options.outWidth.coerceAtLeast(0), options.outHeight.coerceAtLeast(0))
            SampleObjectResult.Ready(
                EditorProduct(
                    originalUriString = cachedUri.toString(),
                    foregroundUriString = cachedUri.toString(),
                    isBackgroundRemoved = true,
                    baseWidth = baseSize.width,
                    baseHeight = baseSize.height,
                    processing = false,
                    isSample = true,
                ),
            )
        } catch (e: Exception) {
            SampleObjectResult.Failed(context.getString(R.string.studio_error_load_sample_product))
        }
    }

    suspend fun buildStickerLayer(assetPath: String, templateSize: IntSize): StickerResult {
        return try {
            val stickerPath = if (assetPath.startsWith("http://") || assetPath.startsWith("https://")) {
                assetPath
            } else {
                "file:///android_asset/$assetPath"
            }

            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            val decoded = templateLoader.openLocalAssetStream(stickerPath)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
                options.outWidth > 0 && options.outHeight > 0
            } == true

            val stickerWidth = if (decoded) options.outWidth.coerceAtLeast(1) else 512
            val stickerHeight = if (decoded) options.outHeight.coerceAtLeast(1) else 512
            val maxStickerDim = maxOf(stickerWidth, stickerHeight).toFloat()
            val targetSize = if (templateSize.width > 0 && templateSize.height > 0) {
                minOf(templateSize.width, templateSize.height) * 0.28f
            } else {
                maxStickerDim * 0.35f
            }
            val initialScale = (targetSize / maxStickerDim).coerceIn(0.15f, 1.4f)

            StickerResult.Ready(
                layerFactory.createStickerLayer(
                    stickerPath = stickerPath,
                    stickerWidth = stickerWidth,
                    stickerHeight = stickerHeight,
                    initialScale = initialScale,
                ),
            )
        } catch (e: Exception) {
            StickerResult.Failure(context.getString(R.string.studio_error_unknown))
        }
    }
}
