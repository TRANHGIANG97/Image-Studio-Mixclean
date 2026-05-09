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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abizer_r.quickedit.R
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
    val isProcessing by viewModel.isProcessing.collectAsStateWithLifecycle()
    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
    val canRedo by viewModel.canRedo.collectAsStateWithLifecycle()

    var selectedToolIndex by remember { mutableIntStateOf(0) } // 0: Smart Erase, 1: Pan
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    val transformableState = rememberTransformableState { zoomChange, offsetChange, _ ->
        if (selectedToolIndex == 1) {
            scale = (scale * zoomChange).coerceIn(1f, 10f)
            offset += offsetChange * scale
        }
    }

    LaunchedEffect(immutableBitmap) {
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
                    .clip(RectangleShape),
                contentAlignment = Alignment.Center
            ) {
                // Main Image with Zoom/Pan and Touch Logic
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { containerSize = it.size }
                        .pointerInteropFilter { event ->
                            if (selectedToolIndex == 0 && event.action == MotionEvent.ACTION_DOWN) {
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

                // Processing Indicator
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
                    IconButton(
                        onClick = { viewModel.undo() },
                        enabled = canUndo,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "Undo",
                            tint = if (canUndo) MaterialTheme.colorScheme.onSurface else Color.Gray,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    
                    Box(modifier = Modifier.width(1.dp).height(24.dp).background(Color.Gray.copy(alpha = 0.5f)))
                    
                    IconButton(
                        onClick = { viewModel.redo() },
                        enabled = canRedo,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Redo,
                            contentDescription = "Redo",
                            tint = if (canRedo) MaterialTheme.colorScheme.onSurface else Color.Gray,
                            modifier = Modifier.size(24.dp)
                        )
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
                // Tolerance Slider
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.studio_intensity),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Slider(
                        value = tolerance,
                        onValueChange = { viewModel.updateTolerance(it) },
                        valueRange = 1f..150f,
                        modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
                    )
                    Text(
                        text = tolerance.toInt().toString(),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.width(30.dp)
                    )
                }

                // Tool Switcher
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Smart Erase Tool
                    IconButton(
                        onClick = { selectedToolIndex = 0 },
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selectedToolIndex == 0) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                            .size(56.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                ImageVector.vectorResource(id = R.drawable.ic_eraser),
                                contentDescription = null,
                                tint = if (selectedToolIndex == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            Text(stringResource(R.string.eraser), style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    // Pan Tool
                    IconButton(
                        onClick = { selectedToolIndex = 1 },
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selectedToolIndex == 1) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                            .size(56.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Outlined.PanTool,
                                contentDescription = null,
                                tint = if (selectedToolIndex == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            Text(stringResource(R.string.pan), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }

    BackHandler {
        onBackPressed()
    }
}
