package com.abizer_r.quickedit.ui.magicBrush.components
 
import com.abizer_r.quickedit.R

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Path as AndroidPath
import android.os.Build
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val TAG = "MagicEraserCanvas"
private const val ERROR_DISPLAY_MS = 3_000L
private data class ScaledDisplayBitmap(val bitmap: Bitmap, val image: ImageBitmap)
private const val CHECKERBOARD_TILE_DP = 8
private const val BRUSH_FILL_ALPHA = 0.2f
private const val BRUSH_STROKE_ALPHA = 0.9f
private const val BRUSH_STROKE_WIDTH = 2.5f
private const val BRUSH_CENTER_DOT_RADIUS = 3f
private const val TOUCH_DOT_RADIUS = 6f
private const val OFFSET_LINE_STROKE = 1.5f
private val BRUSH_COLOR = Color(0xFFFF2D55) // Màu hồng đỏ nổi bật
private val OFFSET_DASH_INTERVALS = floatArrayOf(10f, 10f)
private const val MAX_BRUSH_BITMAP_PX = 500f
private const val MIN_BRUSH_BITMAP_PX = 1f

fun androidx.compose.ui.graphics.Path.toAndroidPath(): AndroidPath {
    val out = AndroidPath()
    val iter = iterator()
    while (iter.hasNext()) {
        val seg = iter.next()
        val p = seg.points
        when (seg.type) {
            PathSegment.Type.Move       -> out.moveTo(p[0], p[1])
            PathSegment.Type.Line       -> out.lineTo(p[0], p[1])
            PathSegment.Type.Quadratic  -> out.quadTo(p[0], p[1], p[2], p[3])
            PathSegment.Type.Cubic      -> out.cubicTo(p[0], p[1], p[2], p[3], p[4], p[5])
            PathSegment.Type.Conic      -> /* bo qua */ Unit
            PathSegment.Type.Close      -> out.close()
            PathSegment.Type.Done       -> Unit
        }
    }
    return out
}

private class DrawState {
    val path: Path = Path()
    // Lưu các nét vẽ đã hoàn thành nhưng chưa được merge vào bitmap để tránh hiện tượng nhấp nháy
    val pendingPaths = mutableStateListOf<Pair<Path, Stroke>>()
    var pathVersion by mutableIntStateOf(0)
    var touchPosition by mutableStateOf<Offset?>(null)

    fun reset() {
        path.reset()
        pathVersion++
        touchPosition = null
    }

    fun clearPending() {
        pendingPaths.clear()
        pathVersion++
    }

    fun moveTo(x: Float, y: Float) { path.moveTo(x, y); pathVersion++ }
    fun lineTo(x: Float, y: Float) { path.lineTo(x, y); pathVersion++ }
    fun updateTouch(offset: Offset?) { touchPosition = offset }
}

@Composable
fun MagicEraserCanvas(
    modifier: Modifier = Modifier,
    currentBitmap: Bitmap,
    brushSizeDp: Dp,
    scaleX: Float,
    scaleY: Float,
    offsetDistanceDp: Dp = 0.dp,
    isProcessing: Boolean = false,
    enabled: Boolean = true,
    onDrawEnd: (scaledPath: AndroidPath, brushSizeBitmapPx: Float) -> Unit,
    onError: ((String) -> Unit)? = null,
) {
    if (currentBitmap.isRecycled) {
        LaunchedEffect(Unit) { onError?.invoke("Anh da bi huy, khong the ve.") }
        return
    }
    if (scaleX <= 0f || scaleY <= 0f) {
        LaunchedEffect(Unit) { onError?.invoke("Ty le scale khong hop le.") }
        return
    }

    val mContext = androidx.compose.ui.platform.LocalContext.current
    val density = LocalDensity.current
    val brushSizePx = remember(brushSizeDp, density) { with(density) { brushSizeDp.toPx() } }
    val offsetDistancePx = remember(offsetDistanceDp, density) { with(density) { offsetDistanceDp.toPx() } }

    val drawState = remember { DrawState() }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val errorJobRef = remember { object { var job: Job? = null } }
    val scope = rememberCoroutineScope()
    val onDrawEndRef = rememberUpdatedState(onDrawEnd)
    val onErrorRef = rememberUpdatedState(onError)

    // Cache anh da scale — chi chay lai khi bitmap thay doi (sau stroke end)
    val bitmapGenId = remember(currentBitmap) {
        if (Build.VERSION.SDK_INT >= 29) currentBitmap.generationId
        else System.identityHashCode(currentBitmap)
    }

    val scaledDisplay by produceState<ScaledDisplayBitmap?>(
        initialValue = null,
        key1 = currentBitmap,
        key2 = canvasSize,
        key3 = bitmapGenId,
    ) {
        if (currentBitmap.isRecycled || canvasSize.width <= 0 || canvasSize.height <= 0) {
            value = null
            return@produceState
        }
        var scaled: Bitmap? = null
        val oldImageBitmap = value // Lưu lại ảnh cũ
        try {
            scaled = withContext(Dispatchers.Default) {
                Bitmap.createScaledBitmap(currentBitmap, canvasSize.width, canvasSize.height, true)
            }
            value = ScaledDisplayBitmap(scaled, scaled.asImageBitmap())
        } catch (e: kotlinx.coroutines.CancellationException) {
            scaled?.recycle() // Chỉ recycle ảnh mới nếu coroutine bị hủy giữa chừng
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi scale bitmap", e)
            scaled?.recycle() // Xử lý rác nếu có lỗi
            value = null
        }
        // TUYỆT ĐỐI KHÔNG dùng khối finally để recycle 'scaled' ở đây
    }
    val displayImage = scaledDisplay?.image

    // Xóa pending paths khi ảnh mới đã thực sự được scale và hiển thị xong
    LaunchedEffect(displayImage) {
        if (displayImage != null) {
            drawState.clearPending()
        }
    }

    // THÊM DisposableEffect để dọn dẹp khi composable bị khỏi cây UI
    DisposableEffect(scaledDisplay) {
        onDispose {
            scaledDisplay?.bitmap?.let { if (!it.isRecycled) it.recycle() }
        }
    }

    fun showError(message: String) {
        errorJobRef.job?.cancel()
        errorMessage = message
        errorJobRef.job = scope.launch {
            delay(ERROR_DISPLAY_MS)
            errorMessage = null
        }
        onErrorRef.value?.invoke(message)
    }

    val strokeStyle = remember(brushSizePx) {
        Stroke(width = brushSizePx, cap = StrokeCap.Round, join = StrokeJoin.Round)
    }

    fun finalizePath() {
        if (drawState.path.isEmpty || canvasSize.width <= 0 || canvasSize.height <= 0) return
        try {
            if (currentBitmap.isRecycled) error(mContext.getString(com.abizer_r.quickedit.R.string.error_recycled_bitmap))
            
            // Lưu vào pending paths để hiển thị trong lúc chờ ViewModel xử lý
            val currentPathCopy = Path().apply { addPath(drawState.path) }
            drawState.pendingPaths.add(currentPathCopy to strokeStyle)

            val rawPath = drawState.path.toAndroidPath().apply {
                transform(Matrix().apply { setScale(scaleX, scaleY) })
            }
            val defensiveCopy = AndroidPath(rawPath)
            val geometricScale = sqrt(scaleX * scaleY)
            val brushSizeBitmapPx = (brushSizePx * geometricScale)
                .coerceIn(MIN_BRUSH_BITMAP_PX, MAX_BRUSH_BITMAP_PX)
            
            onDrawEndRef.value(defensiveCopy, brushSizeBitmapPx)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Loi xu ly net ve", e)
            showError("Loi khi xoa: ${e.message ?: "loi khong xac dinh"}")
        } finally {
            drawState.reset()
        }
    }

    val checkerboardBrush = rememberCheckerboardBrush(density)

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { canvasSize = it }
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        val pointerId = down.id
                        drawState.moveTo(
                            down.position.x,
                            down.position.y - offsetDistancePx
                        )
                        drawState.updateTouch(down.position)

                        var event: PointerEvent
                        do {
                            event = awaitPointerEvent()
                            // Khong reset drawState nua, cho phep ve ke ca khi isProcessing = true
                            val change = event.changes.firstOrNull { it.id == pointerId }
                            if (change != null && change.pressed) {
                                change.consume()
                                drawState.updateTouch(change.position)
                                drawState.lineTo(
                                    change.position.x,
                                    change.position.y - offsetDistancePx
                                )
                            } else break
                        } while (event.changes.any { it.pressed })

                        drawState.updateTouch(null)
                        finalizePath()
                    }
                },
        ) {
            // Doc pathVersion de Canvas ve lai khi path thay doi
            @Suppress("UNUSED_VARIABLE") val version = drawState.pathVersion

            if (canvasSize.width > 0 && displayImage != null) {
                // 1. Ve anh
                drawImage(
                    image = displayImage!!,
                    dstOffset = IntOffset.Zero,
                    dstSize = canvasSize
                )

                // 3. Ve cac net xoa dang cho xử lý (Pending Paths)
                drawState.pendingPaths.forEach { (path, style) ->
                    drawPath(
                        path = path,
                        color = Color.Black,
                        style = style,
                        blendMode = BlendMode.Clear,
                    )
                }

                // 4. Ve net xoa hien tai (Current Stroke)
                if (!drawState.path.isEmpty) {
                    drawPath(
                        path = drawState.path,
                        color = Color.Black,
                        style = strokeStyle,
                        blendMode = BlendMode.Clear,
                    )
                }

                // 5. Brush indicator
                drawState.touchPosition?.let { touch ->
                    drawBrushIndicator(touch, brushSizePx, offsetDistancePx, size)
                }
            }
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
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}

private fun DrawScope.drawBrushIndicator(
    touchRaw: Offset,
    brushSizePx: Float,
    offsetDistancePx: Float,
    canvasSize: Size,
) {
    val adjusted = Offset(touchRaw.x, touchRaw.y - offsetDistancePx)
    val drawCenter = Offset(
        x = adjusted.x.coerceIn(0f, canvasSize.width),
        y = adjusted.y.coerceIn(0f, canvasSize.height),
    )
    val radius = brushSizePx / 2f

    // 1. Ve vung hieu luc cua co (Brush area)
    drawCircle(
        color = BRUSH_COLOR.copy(alpha = BRUSH_FILL_ALPHA),
        radius = radius,
        center = drawCenter,
        style = Fill
    )
    drawCircle(
        color = BRUSH_COLOR.copy(alpha = BRUSH_STROKE_ALPHA),
        radius = radius,
        center = drawCenter,
        style = Stroke(width = BRUSH_STROKE_WIDTH)
    )
    
    // 2. Ve tam co (Brush center dot)
    // Vien trang cho tam co de noi bat
    drawCircle(Color.White, BRUSH_CENTER_DOT_RADIUS + 1f, drawCenter)
    drawCircle(BRUSH_COLOR, BRUSH_CENTER_DOT_RADIUS, drawCenter)

    if (offsetDistancePx > 0f) {
        // 3. Duong noi giua diem cham va co
        drawLine(
            color = Color.White.copy(alpha = 0.5f),
            start = touchRaw,
            end = drawCenter,
            strokeWidth = OFFSET_LINE_STROKE,
            pathEffect = PathEffect.dashPathEffect(OFFSET_DASH_INTERVALS),
        )
        
        // 4. Diem cham cua ngon tay (Touch dot)
        // Vien trang cho diem cham
        drawCircle(Color.White, TOUCH_DOT_RADIUS + 1.5f, touchRaw)
        drawCircle(BRUSH_COLOR, TOUCH_DOT_RADIUS, touchRaw)
    }
}

@Composable
private fun rememberCheckerboardBrush(density: Density): ShaderBrush {
    val bmp = remember(density) { createCheckerboardBitmap(density) }
    // Do not recycle — ImageShader may still reference this bitmap during disposal.
    return remember(bmp) {
        ShaderBrush(ImageShader(bmp.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
    }
}

private fun createCheckerboardBitmap(density: Density): Bitmap {
    val tilePx = with(density) { 8.dp.toPx().roundToInt().coerceAtLeast(1) }
    val size = tilePx * 2
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    val paint = android.graphics.Paint().apply { isAntiAlias = false
        color = android.graphics.Color.WHITE }
    canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
    paint.color = android.graphics.Color.LTGRAY
    canvas.drawRect(0f, 0f, tilePx.toFloat(), tilePx.toFloat(), paint)
    canvas.drawRect(tilePx.toFloat(), tilePx.toFloat(), size.toFloat(), size.toFloat(), paint)
    return bmp
}
