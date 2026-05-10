package com.abizer_r.quickedit.ui.magicBrush

import android.graphics.Bitmap
import android.view.MotionEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abizer_r.quickedit.R
import com.abizer_r.quickedit.ui.magicBrush.components.MagicEraserCanvas
import com.abizer_r.quickedit.utils.other.bitmap.BitmapCache
import com.abizer_r.quickedit.utils.other.bitmap.ImmutableBitmap

import kotlin.math.roundToInt

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MagicBrushScreen(
    immutableBitmap: ImmutableBitmap,
    onBackPressed: () -> Unit,
    onDoneClicked: (Bitmap) -> Unit
) {
    val viewModel: MagicBrushViewModel = hiltViewModel()
    val currentBitmap by viewModel.currentBitmap.collectAsStateWithLifecycle()
    val tolerance by viewModel.tolerance.collectAsStateWithLifecycle()
    val selectedTool by viewModel.selectedTool.collectAsStateWithLifecycle()
    val isProcessing by viewModel.isProcessing.collectAsStateWithLifecycle()
    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
    val canRedo by viewModel.canRedo.collectAsStateWithLifecycle()

    val density = LocalDensity.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    
    var useCursorOffset by remember { mutableStateOf(false) }
    var brushSize by remember { mutableFloatStateOf(24f) }

    val transformableState = rememberTransformableState { zoomChange, offsetChange, _ ->
        if (selectedTool == MagicBrushTool.PAN) {
            scale = (scale * zoomChange).coerceIn(1f, 10f)
            offset += offsetChange * scale
        }
    }

    val context = LocalContext.current
    LaunchedEffect(immutableBitmap) {
        viewModel.setBitmapCache(BitmapCache(context))
        viewModel.setInitialBitmap(immutableBitmap.bitmap)
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(56.dp)
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackPressed) {
                    Icon(Icons.Default.Close, contentDescription = null)
                }
                Text(
                    text = stringResource(R.string.magic_brush),
                    style = MaterialTheme.typography.titleMedium
                )
                TextButton(
                    onClick = { currentBitmap?.let { onDoneClicked(it) } },
                    enabled = !isProcessing
                ) {
                    Text(stringResource(R.string.done))
                }
            }

            // Image Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RectangleShape)
                    .onGloballyPositioned { containerSize = it.size },
                contentAlignment = Alignment.Center
            ) {
                when (selectedTool) {

                    MagicBrushTool.BLUR -> {
                        currentBitmap?.let { bmp ->
                            com.abizer_r.quickedit.ui.magicBrush.components.MosaicDrawingCanvas(
                                originalBitmap = bmp,
                                pixelBlockSize = 30,
                                brushSizeDp = brushSize.dp,
                                offsetDistanceDp = if (useCursorOffset) 60.dp else 0.dp,
                                onDrawEnd = { path, mosaicBmp ->
                                    val brushSizePx = with(density) { brushSize.dp.toPx() }
                                    viewModel.applyBlurResult(
                                        path = path,
                                        mosaicBitmap = mosaicBmp,
                                        brushSizePx = brushSizePx,
                                        canvasWidth = containerSize.width,
                                        canvasHeight = containerSize.height
                                    )
                                }
                            )
                        }
                    }
                    MagicBrushTool.BRUSH_ERASE -> {
                        currentBitmap?.let { bmp ->
                            val containerAspect = containerSize.width.toFloat() / containerSize.height.toFloat()
                            val bitmapAspect = bmp.width.toFloat() / bmp.height.toFloat()
                            val (drawW, drawH) = if (containerAspect > bitmapAspect) {
                                (containerSize.height * bitmapAspect).roundToInt() to containerSize.height
                            } else {
                                containerSize.width to (containerSize.width / bitmapAspect).roundToInt()
                            }
                            val drawWidthDp = with(density) { (drawW / density.density).dp }
                            val drawHeightDp = with(density) { (drawH / density.density).dp }
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                MagicEraserCanvas(
                                    modifier = Modifier.size(drawWidthDp, drawHeightDp),
                                    currentBitmap = bmp,
                                    brushSizeDp = brushSize.dp,
                                    scaleX = bmp.width.toFloat() / drawW,
                                    scaleY = bmp.height.toFloat() / drawH,
                                    offsetDistanceDp = if (useCursorOffset) 60.dp else 0.dp,
                                    isProcessing = isProcessing,
                                    enabled = !isProcessing,
                                    onDrawEnd = { scaledPath, brushSizeBmpPx ->
                                        viewModel.applyEraseResult(scaledPath, brushSizeBmpPx)
                                    },
                                    onError = { /* error da duoc hien thi trong canvas */ }
                                )
                            }
                        }
                    }
                    else -> {
                        // Standard Image View (Erase / Pan)
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInteropFilter { event ->
                                    if (selectedTool == MagicBrushTool.SMART_ERASE && event.action == MotionEvent.ACTION_DOWN) {
                                        val bitmap = currentBitmap ?: return@pointerInteropFilter false
                                        val containerAspect = containerSize.width.toFloat() / containerSize.height.toFloat()
                                        val bitmapAspect = bitmap.width.toFloat() / bitmap.height.toFloat()
                                        val (drawW, drawH) = if (containerAspect > bitmapAspect) {
                                            (containerSize.height * bitmapAspect) to containerSize.height.toFloat()
                                        } else {
                                            containerSize.width.toFloat() to (containerSize.width / bitmapAspect)
                                        }
                                        val left = (containerSize.width - drawW) / 2
                                        val top = (containerSize.height - drawH) / 2
                                        val touchX = (event.x - offset.x) / scale
                                        val touchY = (event.y - offset.y) / scale
                                        val pixelX = ((touchX - left) * (bitmap.width / drawW)).roundToInt()
                                        val pixelY = ((touchY - top) * (bitmap.height / drawH)).roundToInt()
                                        viewModel.onMagicErase(pixelX, pixelY)
                                    }
                                    false
                                }
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offset.x,
                                    translationY = offset.y
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            currentBitmap?.let {
                                Image(
                                    bitmap = it.asImageBitmap(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }


                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Undo/Redo Buttons Overlay
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(24.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.undo() }, enabled = canUndo, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.AutoMirrored.Filled.Undo, null, tint = if (canUndo) MaterialTheme.colorScheme.onSurface else Color.Gray)
                    }
                    Box(modifier = Modifier.width(1.dp).height(24.dp).background(Color.Gray.copy(alpha = 0.5f)))
                    IconButton(onClick = { viewModel.redo() }, enabled = canRedo, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.AutoMirrored.Filled.Redo, null, tint = if (canRedo) MaterialTheme.colorScheme.onSurface else Color.Gray)
                    }
                }
            }

            // Bottom Controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                // Settings Row (Slider)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isBrushTool = selectedTool == MagicBrushTool.BLUR || selectedTool == MagicBrushTool.BRUSH_ERASE
                    Text(
                        text = if (isBrushTool) stringResource(R.string.studio_intensity) else stringResource(R.string.studio_intensity),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Slider(
                        value = if (isBrushTool) brushSize else tolerance,
                        onValueChange = { if (isBrushTool) brushSize = it else viewModel.updateTolerance(it) },
                        valueRange = if (isBrushTool) 10f..100f else 1f..150f,
                        modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
                    )

                    Text(
                        text = (if (isBrushTool) brushSize else tolerance).toInt().toString(),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.width(30.dp)
                    )

                    if (isBrushTool) {
                        IconButton(
                            onClick = { useCursorOffset = !useCursorOffset },
                            modifier = Modifier.size(32.dp).background(if (useCursorOffset) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, CircleShape)
                        ) {
                            Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // Tool Switcher
                Row(
                    modifier = Modifier.fillMaxWidth().height(72.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Erase Tool
                    ToolButton(
                        selected = selectedTool == MagicBrushTool.SMART_ERASE,
                        onClick = { viewModel.selectTool(MagicBrushTool.SMART_ERASE) },
                        iconId = R.drawable.ic_eraser,
                        label = "Magic wand"
                    )

                    // Brush Erase Tool (Cọ xóa)
                    ToolButton(
                        selected = selectedTool == MagicBrushTool.BRUSH_ERASE,
                        onClick = { viewModel.selectTool(MagicBrushTool.BRUSH_ERASE) },
                        icon = Icons.Default.Edit,
                        label = "Cọ xóa"
                    )

                    // Blur Tool (Mờ)
                    ToolButton(
                        selected = selectedTool == MagicBrushTool.BLUR,
                        onClick = { viewModel.selectTool(MagicBrushTool.BLUR) },
                        icon = Icons.Default.Grain,
                        label = "Mờ"
                    )


                    // Pan Tool
                    ToolButton(
                        selected = selectedTool == MagicBrushTool.PAN,
                        onClick = { viewModel.selectTool(MagicBrushTool.PAN) },
                        icon = Icons.Outlined.PanTool,
                        label = stringResource(R.string.pan)
                    )
                }

            }
        }
    }

    BackHandler { onBackPressed() }
}

@Composable
fun ToolButton(
    selected: Boolean,
    onClick: () -> Unit,
    iconId: Int? = null,
    icon: ImageVector? = null,
    label: String
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .size(56.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (iconId != null) {
                Icon(ImageVector.vectorResource(id = iconId), null, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            } else if (icon != null) {
                Icon(icon, null, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            }
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}
