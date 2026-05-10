package com.abizer_r.quickedit.ui.magicBrush.components

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Path as AndroidPath
import android.os.Build
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private const val TAG = "MagicEraserCanvas"
private const val ERROR_DISPLAY_MS = 3000L
private const val CHECKERBOARD_TILE_DP = 16

/**
 * Canvas cho hiệu ứng tẩy tự do (Magic Eraser) – real‑time với BlendMode.Clear.
 * Tự động fallback sang overlay checkerboard nếu thiết bị không hỗ trợ offscreen compositing.
 */
@Composable
fun MagicEraserCanvas(
    currentBitmap: Bitmap,
    brushSizeDp: Dp,
    offsetDistanceDp: Dp = 0.dp,
    isProcessing: Boolean = false,
    onDrawEnd: (scaledPath: AndroidPath, brushSizeBitmapPx: Float) -> Unit,
    onError: ((String) -> Unit)? = null
) {
    if (currentBitmap.isRecycled) {
        LaunchedEffect(Unit) {
            Log.w(TAG, "Bitmap is recycled")
            onError?.invoke("Ảnh đã bị huỷ.")
        }
        return
    }

    val density = LocalDensity.current
    val brushSizePx = with(density) { brushSizeDp.toPx() }
    val offsetDistancePx = with(density) { offsetDistanceDp.toPx() }

    var currentPath by remember { mutableStateOf(androidx.compose.ui.graphics.Path()) }
    var touchPosition by remember { mutableStateOf<Offset?>(null) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val composeImage = remember(currentBitmap) {
        if (!currentBitmap.isRecycled) currentBitmap.asImageBitmap() else null
    }

    val checkerboardBitmap = remember {
        val tilePx = with(density) { CHECKERBOARD_TILE_DP.dp.toPx().roundToInt() }
        val size = tilePx * 2
        Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bmp ->
            val cv = android.graphics.Canvas(bmp)
            val p = android.graphics.Paint()
            p.color = android.graphics.Color.WHITE
            cv.drawRect(0f, 0f, size.toFloat(), size.toFloat(), p)
            p.color = android.graphics.Color.LTGRAY
            cv.drawRect(0f, 0f, tilePx.toFloat(), tilePx.toFloat(), p)
            cv.drawRect(tilePx.toFloat(), tilePx.toFloat(), size.toFloat(), size.toFloat(), p)
        }
    }
    val checkerboardImage = remember(checkerboardBitmap) { checkerboardBitmap.asImageBitmap() }

    DisposableEffect(Unit) {
        onDispose { checkerboardBitmap.recycle() }
    }

    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            delay(ERROR_DISPLAY_MS)
            errorMessage = null
        }
    }

    val useOffscreen = remember { Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q }
    
    // Lưu các khối cần vẽ fallback cho thiết bị không hỗ trợ offscreen
    var fallbackBlocks by remember { mutableStateOf<Set<Pair<Int, Int>>>(emptySet()) }
    val pixelBlockSizePx = remember(brushSizePx) { (brushSizePx / 2f).roundToInt().coerceAtLeast(8) }

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { canvasSize = it }
                .then(
                    if (useOffscreen) {
                        Modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    } else {
                        Modifier
                    }
                )
                .pointerInput(brushSizePx, offsetDistancePx, isProcessing) {
                    if (isProcessing) return@pointerInput

                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        val pointerId = down.id
                        val startPos = if (offsetDistancePx > 0) {
                            Offset(down.position.x, down.position.y - offsetDistancePx)
                        } else down.position

                        touchPosition = down.position
                        currentPath = androidx.compose.ui.graphics.Path().apply {
                            moveTo(startPos.x, startPos.y)
                        }
                        
                        fallbackBlocks = emptySet()

                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointerId }
                            if (change != null && change.pressed) {
                                touchPosition = change.position
                                val pos = if (offsetDistancePx > 0) {
                                    Offset(change.position.x, change.position.y - offsetDistancePx)
                                } else change.position
                                currentPath.lineTo(pos.x, pos.y)
                                change.consume()

                                if (!useOffscreen && canvasSize.width > 0) {
                                    val scaleX = currentBitmap.width.toFloat() / canvasSize.width
                                    val scaleY = currentBitmap.height.toFloat() / canvasSize.height
                                    val bitmapX = pos.x * scaleX
                                    val bitmapY = pos.y * scaleY
                                    val currentBitmapPos = Offset(bitmapX, bitmapY)
                                    
                                    val brushRadiusBitmapX = (brushSizePx / 2f) * scaleX
                                    val brushRadiusBitmapY = (brushSizePx / 2f) * scaleY
                                    
                                    val centerBlockX = ((currentBitmapPos.x / pixelBlockSizePx).toInt() * pixelBlockSizePx)
                                    val centerBlockY = ((currentBitmapPos.y / pixelBlockSizePx).toInt() * pixelBlockSizePx)
                                    
                                    val blockRadiusX = (brushRadiusBitmapX / pixelBlockSizePx).toInt() + 1
                                    val blockRadiusY = (brushRadiusBitmapY / pixelBlockSizePx).toInt() + 1
                                    
                                    val newBlocks = mutableSetOf<Pair<Int, Int>>()
                                    for (dx in -blockRadiusX..blockRadiusX) {
                                        for (dy in -blockRadiusY..blockRadiusY) {
                                            val bx = centerBlockX + dx * pixelBlockSizePx
                                            val by = centerBlockY + dy * pixelBlockSizePx
                                            val blockCenterX = bx + pixelBlockSizePx / 2f
                                            val blockCenterY = by + pixelBlockSizePx / 2f
                                            val distX = blockCenterX - currentBitmapPos.x
                                            val distY = blockCenterY - currentBitmapPos.y
                                            val normalizedDist = (distX / brushRadiusBitmapX) * (distX / brushRadiusBitmapX) +
                                                (distY / brushRadiusBitmapY) * (distY / brushRadiusBitmapY)
                                            if (normalizedDist <= 1f && bx >= 0 && by >= 0) {
                                                newBlocks.add(Pair(bx, by))
                                            }
                                        }
                                    }
                                    fallbackBlocks = fallbackBlocks + newBlocks
                                }
                            } else {
                                break
                            }
                        } while (event.changes.any { it.pressed })

                        touchPosition = null
                        if (!currentPath.isEmpty && canvasSize.width > 0) {
                            val scaleX = currentBitmap.width.toFloat() / canvasSize.width
                            val scaleY = currentBitmap.height.toFloat() / canvasSize.height
                            val matrix = Matrix().apply { setScale(scaleX, scaleY) }
                            val scaledPath = AndroidPath(currentPath.asAndroidPath())
                            scaledPath.transform(matrix)
                            val brushSizeBitmapPx = brushSizePx * scaleX

                            onDrawEnd(scaledPath, brushSizeBitmapPx)
                            currentPath = androidx.compose.ui.graphics.Path()
                            fallbackBlocks = emptySet()
                        }
                    }
                }
        ) {
            if (canvasSize.width > 0 && composeImage != null) {
                // 1. Nền checkerboard
                drawImage(
                    image = checkerboardImage,
                    dstSize = canvasSize,
                    alpha = 1f
                )

                // 2. Ảnh hiện tại
                drawImage(image = composeImage, dstSize = canvasSize)

                // 3. Xoá real‑time
                if (!currentPath.isEmpty) {
                    if (useOffscreen) {
                        drawPath(
                            path = currentPath,
                            color = Color.Black,
                            style = Stroke(
                                width = brushSizePx,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            ),
                            blendMode = BlendMode.Clear
                        )
                    } else {
                        // Fallback blocky eraser
                        val scaleX = currentBitmap.width.toFloat() / canvasSize.width
                        val scaleY = currentBitmap.height.toFloat() / canvasSize.height
                        val blockSizeOnCanvasX = pixelBlockSizePx / scaleX
                        val blockSizeOnCanvasY = pixelBlockSizePx / scaleY
                        
                        fallbackBlocks.forEach { (blockX, blockY) ->
                            val canvasX = blockX / scaleX
                            val canvasY = blockY / scaleY
                            
                            drawImage(
                                image = checkerboardImage,
                                dstOffset = IntOffset(canvasX.toInt(), canvasY.toInt()),
                                dstSize = IntSize(blockSizeOnCanvasX.toInt(), blockSizeOnCanvasY.toInt()),
                                alpha = 0.9f
                            )
                        }
                    }
                }

                // 4. Preview vùng xóa
                touchPosition?.let { touch ->
                    val drawPos = if (offsetDistancePx > 0) {
                        Offset(touch.x, touch.y - offsetDistancePx)
                    } else touch

                    drawCircle(
                        color = Color.Red.copy(alpha = 0.1f),
                        radius = brushSizePx / 2f,
                        center = drawPos,
                        style = Fill
                    )

                    drawCircle(
                        color = Color.Red.copy(alpha = 0.8f),
                        radius = brushSizePx / 2f,
                        center = drawPos,
                        style = Stroke(width = 2f)
                    )
                    drawCircle(
                        color = Color.Red,
                        radius = 4f,
                        center = drawPos
                    )

                    if (offsetDistancePx > 0) {
                        drawLine(
                            color = Color.White.copy(alpha = 0.9f),
                            start = touch,
                            end = drawPos,
                            strokeWidth = 2f
                        )
                        drawCircle(color = Color.Red, radius = 5f, center = touch)
                    }
                }
            }
        }

        if (isProcessing) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.Red,
                strokeWidth = 2.dp
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
