package com.abizer_r.quickedit.ui.magicBrush

import android.graphics.Bitmap
import android.view.MotionEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Grain
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abizer_r.quickedit.R
import com.abizer_r.quickedit.ui.common.LoadingView
import com.abizer_r.quickedit.ui.magicBrush.components.MagicEraserCanvas
import com.abizer_r.quickedit.ui.magicBrush.components.MagicWandTooltipDialog
import com.abizer_r.quickedit.utils.other.bitmap.BitmapCache
import com.abizer_r.quickedit.utils.other.bitmap.ImmutableBitmap

import kotlin.math.roundToInt

@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
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
    val showGuide by viewModel.showGuide.collectAsStateWithLifecycle()
    val showSmartEraseTooltip by viewModel.showSmartEraseTooltip.collectAsStateWithLifecycle()
    val demoState by viewModel.magicWandDemoState.collectAsStateWithLifecycle()

    val density = LocalDensity.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var showGuideDialog by remember { mutableStateOf(false) }

    var cursorOffset by remember { mutableFloatStateOf(35f) }
    var brushSize by remember { mutableFloatStateOf(13f) }



    val context = LocalContext.current
    LaunchedEffect(immutableBitmap) {
        viewModel.setBitmapCache(BitmapCache(context))
        viewModel.setInitialBitmap(immutableBitmap.bitmap)
    }

    LaunchedEffect(Unit) {
        viewModel.checkFirstLaunch()
    }

    LaunchedEffect(showGuide) {
        if (showGuide) showGuideDialog = true
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
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackPressed) {
                    Icon(Icons.Default.Close, contentDescription = null)
                }
                IconButton(
                    onClick = { viewModel.dismissGuide(); showGuideDialog = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "Help",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                IconButton(onClick = { viewModel.undo() }, enabled = canUndo, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.Undo,
                        null,
                        tint = if (canUndo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                }
                IconButton(onClick = { viewModel.redo() }, enabled = canRedo, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.Redo,
                        null,
                        tint = if (canRedo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                TextButton(
                    onClick = { currentBitmap?.let { onDoneClicked(it) } },
                    enabled = !isProcessing
                ) {
                    Text(stringResource(R.string.done), fontWeight = FontWeight.SemiBold)
                }
            }
            
            val checkerboardBrush = rememberCheckerboardBrush()

            // Image Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RectangleShape)
                    .background(checkerboardBrush)
                    .onGloballyPositioned { containerSize = it.size },
                contentAlignment = Alignment.Center
            ) {
                when (selectedTool) {

                    MagicBrushTool.BLUR -> {
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
                                com.abizer_r.quickedit.ui.magicBrush.components.MosaicDrawingCanvas(
                                    modifier = Modifier.size(drawWidthDp, drawHeightDp),
                                    originalBitmap = bmp,
                                    pixelBlockSize = 30,
                                    brushSizeDp = brushSize.dp,
                                    offsetDistanceDp = cursorOffset.dp,
                                    onDrawEnd = { path, mosaicBmp ->
                                        val brushSizePx = with(density) { brushSize.dp.toPx() }
                                        viewModel.applyBlurResult(
                                            path = path,
                                            mosaicBitmap = mosaicBmp,
                                            brushSizePx = brushSizePx,
                                            canvasWidth = drawW,
                                            canvasHeight = drawH
                                        )
                                    }
                                )
                            }
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
                                    offsetDistanceDp = cursorOffset.dp,
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
                    LoadingView(
                        modifier = Modifier.fillMaxSize(),
                        progressBarSize = 96.dp,
                        progressBarColor = MaterialTheme.colorScheme.primary
                    )
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
                val isBrushTool = selectedTool == MagicBrushTool.BLUR || selectedTool == MagicBrushTool.BRUSH_ERASE
                
                if (isBrushTool) {
                    // Hiển thị 2 Slider cho Cọ vẽ: Bù trừ và Kích thước
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Slider Bù trừ (Offset)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = stringResource(R.string.label_offset), style = MaterialTheme.typography.labelMedium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Slider(
                                    value = cursorOffset,
                                    onValueChange = { cursorOffset = it },
                                    valueRange = 0f..150f,
                                    modifier = Modifier.weight(1f),
                                    colors = SliderDefaults.colors(
                                        thumbColor = MaterialTheme.colorScheme.primary,
                                        activeTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                        inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                    )
                                )
                                Text(
                                    text = cursorOffset.toInt().toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.width(24.dp)
                                )
                            }
                        }
                        
                        // Slider Kích thước (Size)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = stringResource(R.string.label_brush_size), style = MaterialTheme.typography.labelMedium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Slider(
                                    value = brushSize,
                                    onValueChange = { brushSize = it },
                                    valueRange = 10f..150f,
                                    modifier = Modifier.weight(1f),
                                    colors = SliderDefaults.colors(
                                        thumbColor = MaterialTheme.colorScheme.primary,
                                        activeTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                        inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                    )
                                )
                                Text(
                                    text = brushSize.toInt().toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.width(24.dp)
                                )
                            }
                        }
                    }
                } else {
                    // Slider cho Magic Wand (Tolerance)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = stringResource(R.string.label_tolerance), style = MaterialTheme.typography.labelMedium)
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
                        IconButton(
                            onClick = { viewModel.forceShowSmartEraseTooltip() },
                            modifier = Modifier.size(24.dp).padding(start = 4.dp)
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = "Tooltip",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Hint text for current tool
                Text(
                    text = when (selectedTool) {
                        MagicBrushTool.SMART_ERASE -> stringResource(R.string.magic_brush_hint_smart_erase)
                        MagicBrushTool.BRUSH_ERASE -> stringResource(R.string.magic_brush_hint_brush_erase)
                        MagicBrushTool.BLUR -> stringResource(R.string.magic_brush_hint_blur)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    modifier = Modifier
                        .padding(horizontal = 24.dp, vertical = 2.dp)
                        .fillMaxWidth(),
                    textAlign = TextAlign.Start
                )

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
                        label = stringResource(R.string.label_magic_wand)
                    )

                    // Brush Erase Tool (Cọ xóa)
                    ToolButton(
                        selected = selectedTool == MagicBrushTool.BRUSH_ERASE,
                        onClick = { viewModel.selectTool(MagicBrushTool.BRUSH_ERASE) },
                        icon = Icons.Outlined.Edit,
                        label = stringResource(R.string.label_erase_brush)
                    )

                    // Blur Tool (Mờ)
                    ToolButton(
                        selected = selectedTool == MagicBrushTool.BLUR,
                        onClick = { viewModel.selectTool(MagicBrushTool.BLUR) },
                        icon = Icons.Outlined.Grain,
                        label = stringResource(R.string.effect_blur)
                    )
                }

            }
        }
    }

    if (selectedTool == MagicBrushTool.SMART_ERASE && showSmartEraseTooltip) {
        MagicWandTooltipDialog(
            demoState = demoState,
            onPrepareDemo = viewModel::prepareMagicWandDemo,
            onDismiss = { dontShowAgain ->
                viewModel.dismissSmartEraseTooltip(dontShowAgain)
            }
        )
    }

    // Guide BottomSheet
    if (showGuideDialog) {
        ModalBottomSheet(
            onDismissRequest = {
                viewModel.dismissGuide()
                showGuideDialog = false
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            GuideContent(
                onDismiss = {
                    viewModel.dismissGuide()
                    showGuideDialog = false
                }
            )
        }
    }

    BackHandler { onBackPressed() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GuideContent(onDismiss: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Title
        Text(
            text = stringResource(R.string.magic_brush_guide_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start
        )

        HorizontalDivider(color =MaterialTheme.colorScheme.outlineVariant)

        // Magic Wand guide
        GuideRow(
            icon = { Icon(ImageVector.vectorResource(id = R.drawable.ic_eraser), contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = stringResource(R.string.label_magic_wand),
            description = stringResource(R.string.magic_brush_guide_wand)
        )

        // Erase Brush guide
        GuideRow(
            icon = { Icon(Icons.Outlined.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = stringResource(R.string.label_erase_brush),
            description = stringResource(R.string.magic_brush_guide_brush)
        )

        // Blur guide
        GuideRow(
            icon = { Icon(Icons.Outlined.Grain, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = stringResource(R.string.effect_blur),
            description = stringResource(R.string.magic_brush_guide_blur)
        )

        HorizontalDivider(color =MaterialTheme.colorScheme.outlineVariant)

        // Tip
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Undo,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.magic_brush_guide_tip),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }

        // Got it button
        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = stringResource(R.string.magic_brush_got_it),
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun GuideRow(
    icon: @Composable () -> Unit,
    title: String,
    description: String
) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun rememberCheckerboardBrush(): ShaderBrush {
    val density = LocalDensity.current
    val tilePx = with(density) { 8.dp.toPx().roundToInt().coerceAtLeast(1) }
    val size = tilePx * 2
    
    val bmp = remember(tilePx) {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint().apply { isAntiAlias = false }
        
        paint.color = android.graphics.Color.WHITE
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
        
        paint.color = android.graphics.Color.parseColor("#EEEEEE") // Xám rất nhạt
        canvas.drawRect(0f, 0f, tilePx.toFloat(), tilePx.toFloat(), paint)
        canvas.drawRect(tilePx.toFloat(), tilePx.toFloat(), size.toFloat(), size.toFloat(), paint)
        bitmap
    }
    
    DisposableEffect(bmp) {
        // Do not recycle — ImageShader may still reference this bitmap during disposal.
        onDispose { }
    }
    
    return remember(bmp) {
        ShaderBrush(ImageShader(bmp.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
    }
}

@Composable
fun ToolButton(
    selected: Boolean,
    onClick: () -> Unit,
    iconId: Int? = null,
    icon: ImageVector? = null,
    label: String
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .widthIn(min = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val tintColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        if (iconId != null) {
            Icon(ImageVector.vectorResource(id = iconId), contentDescription = label, tint = tintColor)
        } else if (icon != null) {
            Icon(icon, contentDescription = label, tint = tintColor)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label, 
            style = MaterialTheme.typography.labelSmall,
            color = tintColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
