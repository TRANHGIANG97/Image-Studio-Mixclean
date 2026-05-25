package com.abizer_r.quickedit.ui.magicBrush.components

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Shader
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abizer_r.quickedit.ui.common.LoadingView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.max

private const val TAG = "MosaicCanvas"
private const val MAX_BLOCK_SIZE_AUTO = 256
private const val ERROR_DISPLAY_MS = 3000L

@Composable
fun MosaicDrawingCanvas(
    modifier: Modifier = Modifier,
    originalBitmap: Bitmap,
    pixelBlockSize: Int = 30,
    brushSizeDp: Dp = 24.dp,
    offsetDistanceDp: Dp = 0.dp, // Sẽ được kiểm soát bởi UI của bạn
    loadingColor: Color = Color.Red,
    onDrawEnd: (Path, Bitmap) -> Unit, // Gọi callback khi vẽ xong
    onError: ((String) -> Unit)? = null
) {
    if (originalBitmap.isRecycled) {
        LaunchedEffect(Unit) { onError?.invoke("Ảnh gốc đã bị hủy.") }
        return
    }

    val density = LocalDensity.current
    val brushSizePx = remember(brushSizeDp, density) { with(density) { brushSizeDp.toPx() } }
    val brushRadius = brushSizePx / 2
    val offsetDistancePx = remember(offsetDistanceDp, density) { with(density) { offsetDistanceDp.toPx() } }

    val accumulatedPath = remember { Path() }
    var currentPath by remember { mutableStateOf(Path()) }
    var touchPosition by remember { mutableStateOf<Offset?>(null) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    var mosaicBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isGenerating by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val composeOriginalImage = remember(originalBitmap) { originalBitmap.asImageBitmap() }

    LaunchedEffect(errorMessage) {
        val currentMsg = errorMessage ?: return@LaunchedEffect
        delay(ERROR_DISPLAY_MS)
        if (errorMessage == currentMsg) {
            errorMessage = null
        }
    }

    LaunchedEffect(originalBitmap, pixelBlockSize) {
        isGenerating = true
        errorMessage = null

        // Lưu bitmap cũ để recycle sau
        val oldBitmap = mosaicBitmap
        
        val result = withContext(Dispatchers.Default) {
            createMosaicBitmapWithFallback(originalBitmap, pixelBlockSize)
        }

        when (result) {
            is MosaicResult.Success -> {
                mosaicBitmap = result.bitmap

                if (result.wasFallback) {
                    val msg = "RAM thấp. Đã tự động giảm độ chi tiết Mosaic xuống ${result.actualBlockSize}px."
                    Log.w(TAG, msg)
                    errorMessage = msg
                    onError?.invoke(msg)
                }
            }
            is MosaicResult.Error -> {
                Log.e(TAG, result.message)
                errorMessage = result.message
                onError?.invoke(result.message)
                if (mosaicBitmap == null && !originalBitmap.isRecycled) {
                    mosaicBitmap = originalBitmap
                }
            }
        }
        isGenerating = false
    }

    DisposableEffect(Unit) {
        onDispose {
            mosaicBitmap = null
        }
    }

    val mosaicBrush = remember(mosaicBitmap, canvasSize) {
        mosaicBitmap?.let { bmp ->
            if (bmp.isRecycled || canvasSize.width == 0) return@let null
            val shader = BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            val matrix = Matrix().apply {
                setScale(
                    canvasSize.width.toFloat() / bmp.width,
                    canvasSize.height.toFloat() / bmp.height
                )
            }
            shader.setLocalMatrix(matrix)
            ShaderBrush(shader)
        }
    }

    val strokeStyle = remember(brushSizePx) {
        Stroke(width = brushSizePx, cap = StrokeCap.Round, join = StrokeJoin.Round)
    }

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { canvasSize = it }
                .pointerInput(offsetDistancePx) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()

                        val pointerId = down.id
                        touchPosition = down.position
                        val start = Offset(down.position.x, down.position.y - offsetDistancePx)
                        currentPath.moveTo(start.x, start.y)

                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointerId }

                            when {
                                change == null -> continue
                                change.pressed -> {
                                    touchPosition = change.position
                                    val pos = Offset(
                                        change.position.x,
                                        change.position.y - offsetDistancePx
                                    )
                                    currentPath.lineTo(pos.x, pos.y)
                                    change.consume()
                                }
                                else -> break
                            }
                        } while (event.changes.any { it.pressed })

                        // Commit path
                        touchPosition = null
                        accumulatedPath.addPath(currentPath)
                        
                        // Callback to ViewModel to save state
                        val pathCopy = Path().apply { addPath(accumulatedPath) }
                        mosaicBitmap?.let { onDrawEnd(pathCopy, it) }
                        
                        currentPath = Path() // Trigger recomposition
                    }
                }
        ) {
            // 1. Vẽ Ảnh gốc làm nền
            if (canvasSize.width > 0) {
                drawImage(image = composeOriginalImage, dstSize = canvasSize)
            }

            // 2. Vẽ hiệu ứng Mosaic sắc nét theo Path
            mosaicBrush?.let { brush ->
                drawPath(path = accumulatedPath, brush = brush, style = strokeStyle)
                drawPath(path = currentPath, brush = brush, style = strokeStyle)
            }

            // 3. Vẽ UI con trỏ bù trừ
            touchPosition?.let { touch ->
                if (offsetDistancePx > 0) {
                    val drawPos = Offset(touch.x, touch.y - offsetDistancePx)
                    drawLine(color = Color.White.copy(alpha = 0.8f), start = touch, end = drawPos, strokeWidth = 4f)
                    drawCircle(color = Color.Red, radius = 10f, center = touch)
                    drawCircle(color = Color.White, radius = brushRadius, center = drawPos, style = Stroke(width = 4f))
                    drawCircle(color = Color.Red.copy(alpha = 0.8f), radius = brushRadius - 2f, center = drawPos, style = Stroke(width = 3f))
                } else {
                    // Nếu không dùng offset, chỉ vẽ vòng tròn tại ngón tay
                    drawCircle(color = Color.White, radius = brushRadius, center = touch, style = Stroke(width = 4f))
                    drawCircle(color = Color.Red.copy(alpha = 0.8f), radius = brushRadius - 2f, center = touch, style = Stroke(width = 3f))
                }
            }
        }

        if (isGenerating) {
            LoadingView(
                modifier = Modifier.fillMaxSize(),
                progressBarSize = 96.dp,
                progressBarColor = loadingColor
            )
        }

        errorMessage?.let { msg ->
            Text(
                text = msg,
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

// -------------------------------------------------------------------------
// THUẬT TOÁN TẠO MOSAIC Ô VUÔNG SIÊU SẮC NÉT
// -------------------------------------------------------------------------

private sealed class MosaicResult {
    data class Success(val bitmap: Bitmap, val wasFallback: Boolean = false, val actualBlockSize: Int = 0) : MosaicResult()
    data class Error(val message: String) : MosaicResult()
}

private fun createMosaicBitmapWithFallback(original: Bitmap, desiredBlockSize: Int): MosaicResult {
    if (desiredBlockSize <= 1) return MosaicResult.Success(original, false, 1)
    var blockSize = desiredBlockSize.coerceAtLeast(1)
    var lastError: String? = null
    while (blockSize <= MAX_BLOCK_SIZE_AUTO) {
        try {
            val bitmap = createSingleMosaic(original, blockSize)
            return MosaicResult.Success(bitmap, blockSize != desiredBlockSize, blockSize)
        } catch (e: OutOfMemoryError) {
            blockSize *= 2
        } catch (e: IllegalArgumentException) {
            return MosaicResult.Error("Lỗi tham số: ${e.message}")
        }
    }
    return MosaicResult.Error(lastError ?: "Không đủ RAM.")
}

/**
 * Thuật toán khắc phục triệt để lỗi "nhòe thành khối màu" trên các thiết bị Android tùy biến.
 */
@Throws(OutOfMemoryError::class, IllegalArgumentException::class)
private fun createSingleMosaic(original: Bitmap, pixelBlockSize: Int): Bitmap {
    val width = original.width
    val height = original.height
    val scaledWidth = max(1, width / pixelBlockSize)
    val scaledHeight = max(1, height / pixelBlockSize)
    
    // Bước 1: Thu nhỏ ảnh (Dùng filter = true để lấy màu trung bình chính xác của khối)
    val tinyBitmap = Bitmap.createScaledBitmap(original, scaledWidth, scaledHeight, true)
    
    // Bước 2: Phóng to thủ công bằng Canvas
    // Tuyệt đối KHÔNG dùng Bitmap.createScaledBitmap vì hệ thống Android thường ép bật bộ lọc làm mờ.
    val mosaicBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(mosaicBitmap)
    
    // Ép buộc hệ thống TẮT MỌI TÍNH NĂNG LÀM MỜ, giữ các cạnh ô vuông sắc lẹm!
    val paint = android.graphics.Paint().apply {
        isAntiAlias = false
        isFilterBitmap = false 
        isDither = false
    }
    
    val srcRect = android.graphics.Rect(0, 0, scaledWidth, scaledHeight)
    val dstRect = android.graphics.Rect(0, 0, width, height)
    
    canvas.drawBitmap(tinyBitmap, srcRect, dstRect, paint)
    
    // Dọn dẹp ảnh tí hon
    tinyBitmap.recycle()
    
    return mosaicBitmap
}
