package com.thgiang.image.studio.ui.editor

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thgiang.image.core.data.backgroundremove.BackgroundRemoverRepository
import com.thgiang.image.core.data.save.ImageSaveRepository
import com.thgiang.image.core.util.processors.PortraitProcessor
import com.thgiang.image.core.util.processors.ProcessorUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class EditorTool {
    REPLACE,
    LAYOUT,
    ROTATE,
    SHADOW,
    TRANSPARENCY,
    CROP
}

enum class CropRatio(val label: String, val width: Float, val height: Float) {
    RATIO_1_1("1:1", 1f, 1f),
    RATIO_3_4("3:4", 3f, 4f),
    RATIO_4_3("4:3", 4f, 3f)
}

data class EditorUiState(
    val productImageUri: Uri? = null,
    val foregroundCacheUri: Uri? = null,
    val foregroundBitmap: Bitmap? = null,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotation: Float = 0f,
    val flippedH: Boolean = false,
    val flippedV: Boolean = false,
    val shadowIntensity: Float = 0.3f,
    val alpha: Float = 1f,
    val selectedTool: EditorTool = EditorTool.LAYOUT,
    val cropRatio: CropRatio = CropRatio.RATIO_1_1,
    val isProcessing: Boolean = false,
    val isBackgroundRemoved: Boolean = false,
    val isExporting: Boolean = false,
    val exportResult: Uri? = null,
    val originalImageUri: Uri? = null,
    val contentWidth: Float = 0f,
    val contentHeight: Float = 0f
)


@HiltViewModel
class ThemeplateEditorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backgroundRemoverRepository: BackgroundRemoverRepository,
    private val imageSaveRepository: ImageSaveRepository
) : ViewModel() {

    companion object {
        private const val TAG = "ThemeplateEditorVM"
    }

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    override fun onCleared() {
        super.onCleared()
        _uiState.value.foregroundBitmap?.let {
            if (!it.isRecycled) it.recycle()
        }
    }

    fun setProductImage(uri: Uri) {
        // Recycle previous foreground bitmap to free memory
        _uiState.value.foregroundBitmap?.let {
            if (!it.isRecycled) it.recycle()
        }
        _uiState.value = _uiState.value.copy(
            productImageUri = uri,
            originalImageUri = uri,
            foregroundBitmap = null,
            foregroundCacheUri = null,
            isProcessing = true,
            isBackgroundRemoved = false,
            offsetX = 0f,
            offsetY = 0f,
            scaleX = 1f,
            scaleY = 1f,
            rotation = 0f,
            flippedH = false,
            flippedV = false,
            contentWidth = 0f,
            contentHeight = 0f
        )
        removeBackground(uri)
    }

    private fun removeBackground(uri: Uri) {
        viewModelScope.launch {
            try {
                val decoded = ProcessorUtils.decodeBitmapFromUri(context, uri)
                if (decoded == null) {
                    Log.w(TAG, "removeBackground: decode failed for $uri")
                    _uiState.value = _uiState.value.copy(isProcessing = false)
                    return@launch
                }

                val foreground = withContext(Dispatchers.Default) {
                    backgroundRemoverRepository.getForegroundBitmap(decoded).getOrNull()
                }
                decoded.recycle()

                if (foreground == null) {
                    Log.w(TAG, "removeBackground: getForegroundBitmap returned null")
                    _uiState.value = _uiState.value.copy(isProcessing = false)
                    return@launch
                }

                val cachedUri = withContext(Dispatchers.IO) {
                    imageSaveRepository.cacheBitmap(foreground).getOrNull()
                }
                foreground.recycle()

                if (cachedUri == null) {
                    Log.w(TAG, "removeBackground: cacheBitmap returned null")
                    // Still show the foreground bitmap even if caching fails
                    _uiState.value = _uiState.value.copy(isProcessing = false, foregroundBitmap = foreground)
                    return@launch
                }

                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    isBackgroundRemoved = true,
                    foregroundBitmap = foreground,
                    foregroundCacheUri = cachedUri
                )
                Log.d(TAG, "removeBackground: success, cachedUri=$cachedUri")
            } catch (e: Exception) {
                Log.e(TAG, "removeBackground failed", e)
                _uiState.value = _uiState.value.copy(isProcessing = false)
            }
        }
    }

    fun exportFinalImage(
        templateAssetPath: String,
        onSuccess: (Uri) -> Unit,
        onError: (String) -> Unit
    ) {
        if (_uiState.value.isExporting) return

        _uiState.value = _uiState.value.copy(isExporting = true)
        viewModelScope.launch {
            try {
                val state = _uiState.value
                val resultUri = withContext(Dispatchers.IO) {
                    performExport(templateAssetPath, state)
                }
                resultUri?.let {
                    _uiState.value = _uiState.value.copy(
                        isExporting = false,
                        exportResult = it
                    )
                    onSuccess(it)
                } ?: run {
                    _uiState.value = _uiState.value.copy(isExporting = false)
                    onError("Export failed")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isExporting = false)
                onError(e.message ?: "Export error")
            }
        }
    }

    private suspend fun performExport(
        templateAssetPath: String,
        state: EditorUiState
    ): Uri? {
        val foregroundUri = state.foregroundCacheUri ?: return null

        val template = loadTemplateFromAssets(templateAssetPath) ?: return null
        val foreground = ProcessorUtils.decodeBitmapFromUri(context, foregroundUri) ?: run {
            template.recycle()
            return null
        }

        val result = Bitmap.createBitmap(
            template.width, template.height, Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        // 1. Draw template background
        canvas.drawBitmap(template, 0f, 0f, paint)

        // 2. Calculate foreground drawing rect (centered + user offset + per-axis scale)
        val baseFitScale = minOf(
            template.width.toFloat() / foreground.width,
            template.height.toFloat() / foreground.height
        )
        val fitScaleX = baseFitScale * state.scaleX
        val fitScaleY = baseFitScale * state.scaleY
        val drawW = foreground.width * fitScaleX
        val drawH = foreground.height * fitScaleY
        val baseX = (template.width - drawW) / 2f
        val baseY = (template.height - drawH) / 2f
        val dx = baseX + state.offsetX
        val dy = baseY + state.offsetY

        // 3. Draw native C++ blurred shadow (FastBoxBlur via JNI/NEON)
        if (state.shadowIntensity > 0.05f) {
            val shadow = PortraitProcessor.applyBlurOnly(
                bitmap = foreground,
                blurRadius = state.shadowIntensity * 15f
            )
            if (shadow != null) {
                val shadowPaint = Paint().apply {
                    isAntiAlias = true
                    isFilterBitmap = true
                    alpha = (state.shadowIntensity * 140).toInt().coerceIn(0, 255)
                }
                canvas.save()
                canvas.translate(dx + 12f, dy + 12f)
                canvas.scale(
                    fitScaleX * (if (state.flippedH) -1f else 1f),
                    fitScaleY * (if (state.flippedV) -1f else 1f)
                )
                canvas.rotate(state.rotation, shadow.width / 2f, shadow.height / 2f)
                val sdX = if (state.flippedH) -shadow.width.toFloat() else 0f
                val sdY = if (state.flippedV) -shadow.height.toFloat() else 0f
                canvas.drawBitmap(shadow, sdX, sdY, shadowPaint)
                canvas.restore()
                shadow.recycle()
            }
        }

        // 4. Draw foreground product (with flip + rotation)
        paint.alpha = (state.alpha * 255).toInt()
        canvas.save()
        canvas.translate(dx, dy)
        canvas.scale(
            fitScaleX * (if (state.flippedH) -1f else 1f),
            fitScaleY * (if (state.flippedV) -1f else 1f)
        )
        canvas.rotate(state.rotation, foreground.width / 2f, foreground.height / 2f)
        val fgOffX = if (state.flippedH) -foreground.width.toFloat() else 0f
        val fgOffY = if (state.flippedV) -foreground.height.toFloat() else 0f
        canvas.drawBitmap(foreground, fgOffX, fgOffY, paint)
        canvas.restore()

        // Cleanup intermediates
        template.recycle()
        foreground.recycle()

        // Save to gallery
        return saveBitmapToGallery(result)
    }

    private fun loadTemplateFromAssets(assetPath: String): Bitmap? {
        return try {
            context.assets.open(assetPath).use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun saveBitmapToGallery(bitmap: Bitmap): Uri? {
        return try {
            val fileName = "Studio_${System.currentTimeMillis()}.png"
            val resolver = context.contentResolver

            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MixClean")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val uri = resolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            ) ?: return null

            resolver.openOutputStream(uri)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                outputStream.flush()
            } ?: return null

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val pendingOff = ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }
                resolver.update(uri, pendingOff, null, null)
            }

            bitmap.recycle()
            uri
        } catch (e: Exception) {
            null
        }
    }

    fun updateOffset(dx: Float, dy: Float) {
        Log.d(TAG, "updateOffset: dx=$dx dy=$dy")
        val state = _uiState.value
        _uiState.value = state.copy(
            offsetX = state.offsetX + dx,
            offsetY = state.offsetY + dy
        )
    }

    fun updateScaleUniform(factor: Float) {
        val state = _uiState.value
        _uiState.value = state.copy(
            scaleX = (state.scaleX * factor).coerceIn(0.3f, 3f),
            scaleY = (state.scaleY * factor).coerceIn(0.3f, 3f)
        )
    }

    fun updateScaleX(value: Float) {
        _uiState.value = _uiState.value.copy(
            scaleX = value.coerceIn(0.3f, 3f)
        )
    }

    fun updateScaleY(value: Float) {
        _uiState.value = _uiState.value.copy(
            scaleY = value.coerceIn(0.3f, 3f)
        )
    }

    fun updateContentSize(width: Float, height: Float) {
        if (width <= 0f || height <= 0f) return
        if (width == _uiState.value.contentWidth && height == _uiState.value.contentHeight) return
        Log.d(TAG, "updateContentSize: w=$width h=$height")
        _uiState.value = _uiState.value.copy(
            contentWidth = width,
            contentHeight = height
        )
    }

    fun updateRotation(degrees: Float) {
        _uiState.value = _uiState.value.copy(
            rotation = (_uiState.value.rotation + degrees) % 360f
        )
    }

    fun setRotation(degrees: Float) {
        _uiState.value = _uiState.value.copy(
            rotation = degrees % 360f
        )
    }

    fun setScale(value: Float) {
        val clamped = value.coerceIn(0.2f, 5f)
        _uiState.value = _uiState.value.copy(
            scaleX = clamped,
            scaleY = clamped
        )
    }

    fun flipHorizontal() {
        _uiState.value = _uiState.value.copy(flippedH = !_uiState.value.flippedH)
    }

    fun flipVertical() {
        _uiState.value = _uiState.value.copy(flippedV = !_uiState.value.flippedV)
    }

    fun updateShadowIntensity(intensity: Float) {
        _uiState.value = _uiState.value.copy(
            shadowIntensity = intensity.coerceIn(0f, 1f)
        )
    }

    fun updateAlpha(alpha: Float) {
        _uiState.value = _uiState.value.copy(
            alpha = alpha.coerceIn(0.1f, 1f)
        )
    }

    fun selectTool(tool: EditorTool) {
        _uiState.value = _uiState.value.copy(selectedTool = tool)
    }

    fun selectCropRatio(ratio: CropRatio) {
        _uiState.value = _uiState.value.copy(cropRatio = ratio)
    }
}
