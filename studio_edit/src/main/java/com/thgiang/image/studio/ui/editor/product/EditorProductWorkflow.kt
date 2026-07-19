package com.thgiang.image.studio.ui.editor.product
import com.thgiang.image.studio.ui.editor.*
import com.thgiang.image.studio.ui.editor.label.factory.*

import android.content.Context
import com.thgiang.image.studio.ui.editor.model.*
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.unit.IntSize
import com.thgiang.image.core.data.backgroundremove.BackgroundRemoverRepository
import com.thgiang.image.core.data.save.ImageSaveRepository
import com.thgiang.image.core.util.processors.OpaqueContentBounds
import com.thgiang.image.core.util.processors.ProcessorUtils
import com.thgiang.image.studio.R
import com.thgiang.image.studio.ui.editor.load.EditorTemplateLoader
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ProductImageResult {
    data class Ready(
        val product: EditorProduct,
        val opaqueBounds: OpaqueContentBounds? = null,
    ) : ProductImageResult
    data class Failed(val message: String?) : ProductImageResult
}

sealed interface SampleObjectResult {
    data class Ready(
        val product: EditorProduct,
        val opaqueBounds: OpaqueContentBounds? = null,
    ) : SampleObjectResult
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
        val existing = replaceLayerId?.let { id -> currentLayers.firstOrNull { it.id == id } }
        val keepSample = existing?.product?.isSample == true
        val processingLayer = EditorLayer(
            id = processingId,
            product = EditorProduct(
                originalUriString = uri.toString(),
                processing = true,
                isSample = keepSample,
            ),
        )
        val newLayers = if (existing != null) {
            currentLayers.map {
                if (it.id == replaceLayerId) {
                    it.copy(
                        product = EditorProduct(
                            originalUriString = uri.toString(),
                            processing = true,
                            isSample = keepSample,
                        )
                    )
                } else {
                    it
                }
            }
        } else {
            currentLayers + processingLayer
        }
        return ProcessingLayerUpdate(processingId, newLayers)
    }

    fun buildSampleProcessingLayer(processingId: String): EditorLayer =
        EditorLayer(id = processingId, product = EditorProduct(processing = true, isSample = true))

    suspend fun processUserImage(uri: Uri, removeBg: Boolean = true): ProductImageResult {
        val decoded = withContext(Dispatchers.Default) {
            ProcessorUtils.decodeBitmapFromUri(context, uri)
        } ?: return ProductImageResult.Failed(null)

        if (!removeBg) {
            return try {
                val opaqueBounds = withContext(Dispatchers.Default) {
                    ProcessorUtils.findOpaqueContentBounds(decoded)
                }
                val cachedUri = withContext(Dispatchers.IO) {
                    imageSaveRepository.cacheBitmap(decoded).getOrNull()
                } ?: return ProductImageResult.Failed(null)

                ProductImageResult.Ready(
                    product = EditorProduct(
                        originalUriString = uri.toString(),
                        foregroundUriString = cachedUri.toString(),
                        isBackgroundRemoved = false,
                        baseWidth = decoded.width,
                        baseHeight = decoded.height,
                        processing = false,
                    ),
                    opaqueBounds = opaqueBounds,
                )
            } catch (e: Exception) {
                ProductImageResult.Failed(e.message ?: context.getString(R.string.studio_error_process_image))
            } finally {
                decoded.recycle()
            }
        }

        return try {
            val foreground = withContext(Dispatchers.Default) {
                backgroundRemoverRepository.getForegroundBitmap(decoded).getOrNull()
            } ?: return ProductImageResult.Failed(null)

            try {
                val opaqueBounds = withContext(Dispatchers.Default) {
                    ProcessorUtils.findOpaqueContentBounds(foreground)
                }
                val cachedUri = withContext(Dispatchers.IO) {
                    imageSaveRepository.cacheBitmap(foreground).getOrNull()
                } ?: return ProductImageResult.Failed(null)

                // Keep the logical layer size tied to the original decoded image.
                // The foreground bitmap can be tighter after background removal, which
                // would otherwise make the layer render shorter than the template slot.
                ProductImageResult.Ready(
                    product = EditorProduct(
                        originalUriString = uri.toString(),
                        foregroundUriString = cachedUri.toString(),
                        isBackgroundRemoved = true,
                        baseWidth = decoded.width.coerceAtLeast(foreground.width).coerceAtLeast(0),
                        baseHeight = decoded.height.coerceAtLeast(foreground.height).coerceAtLeast(0),
                        processing = false,
                    ),
                    opaqueBounds = opaqueBounds,
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

            val decodedBitmap = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(cachedUri)?.use { input ->
                    BitmapFactory.decodeStream(input)
                }
            }

            val baseWidth: Int
            val baseHeight: Int
            val opaqueBounds = if (decodedBitmap != null) {
                baseWidth = decodedBitmap.width.coerceAtLeast(0)
                baseHeight = decodedBitmap.height.coerceAtLeast(0)
                withContext(Dispatchers.Default) {
                    ProcessorUtils.findOpaqueContentBounds(decodedBitmap)
                }.also {
                    decodedBitmap.recycle()
                }
            } else {
                baseWidth = 0
                baseHeight = 0
                null
            }

            if (baseWidth <= 0 || baseHeight <= 0) {
                return SampleObjectResult.Failed(context.getString(R.string.studio_error_load_sample_product))
            }

            SampleObjectResult.Ready(
                product = EditorProduct(
                    originalUriString = cachedUri.toString(),
                    foregroundUriString = cachedUri.toString(),
                    isBackgroundRemoved = true,
                    baseWidth = baseWidth,
                    baseHeight = baseHeight,
                    processing = false,
                    isSample = true,
                ),
                opaqueBounds = opaqueBounds,
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

            val decodedBitmap = withContext(Dispatchers.IO) {
                templateLoader.openLocalAssetStream(stickerPath)?.use { input ->
                    BitmapFactory.decodeStream(input)
                }
            }

            val stickerWidth: Int
            val stickerHeight: Int
            val opaqueBounds = if (decodedBitmap != null) {
                stickerWidth = decodedBitmap.width.coerceAtLeast(1)
                stickerHeight = decodedBitmap.height.coerceAtLeast(1)
                withContext(Dispatchers.Default) {
                    ProcessorUtils.findOpaqueContentBounds(decodedBitmap)
                }.also {
                    decodedBitmap.recycle()
                }
            } else {
                stickerWidth = 512
                stickerHeight = 512
                null
            }

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
                    opaqueBounds = opaqueBounds,
                ),
            )
        } catch (e: Exception) {
            StickerResult.Failure(context.getString(R.string.studio_error_unknown))
        }
    }
}
