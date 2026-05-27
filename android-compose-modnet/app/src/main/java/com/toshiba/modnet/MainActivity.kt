package com.toshiba.modnet

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.toshiba.modnet.ui.theme.ModNetComposeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        return when (ev.actionMasked) {
            MotionEvent.ACTION_HOVER_ENTER,
            MotionEvent.ACTION_HOVER_MOVE,
            MotionEvent.ACTION_HOVER_EXIT -> true
            else -> super.dispatchGenericMotionEvent(ev)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ModNetComposeTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BackgroundRemoverScreen()
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
private fun BackgroundRemoverScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hybridRemover = remember(context.applicationContext) {
        HybridBackgroundRemover(context.applicationContext)
    }

    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var labResults by remember { mutableStateOf<List<FusionLabResult>>(emptyList()) }
    var compareTotalMs by remember { mutableStateOf<Long?>(null) }
    var selectedBackgrounds by remember {
        mutableStateOf(setOf(BackgroundMaskModel.U2NETP))
    }
    var selectedCores by remember {
        mutableStateOf(setOf(CoreMaskModel.ML_KIT))
    }
    var status by remember { mutableStateOf("Chon background model va core mask model de test fusion") }
    var isProcessing by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            labResults.recycleBitmaps()
            sourceBitmap?.recycleIfAlive()
            hybridRemover.close()
        }
    }

    suspend fun processUri(uri: Uri) {
        isProcessing = true
        status = "Dang khoi tao model..."
        val previousSource = sourceBitmap
        val previousResults = labResults
        sourceBitmap = null
        labResults = emptyList()
        compareTotalMs = null
        previousResults.recycleBitmaps()
        previousSource?.recycleIfAlive()

        try {
            val backgrounds = selectedBackgrounds.ifEmpty { setOf(BackgroundMaskModel.U2NETP) }
            val cores = selectedCores
            withContext(Dispatchers.IO) {
                hybridRemover.prepare(backgrounds, cores)
            }

            status = "Dang nap anh..."
            val bitmap = withContext(Dispatchers.IO) {
                context.loadBitmapFromUri(uri)
            }
            sourceBitmap = bitmap

            status = "Dang chay ${backgrounds.size} background x ${cores.size.coerceAtLeast(1)} core..."
            val compare = withContext(Dispatchers.Default) {
                hybridRemover.compareBackgrounds(bitmap, backgrounds, cores)
            }
            labResults.recycleBitmaps()
            labResults = compare.results
            compareTotalMs = compare.totalMs
            status = "Xong ${compare.results.size} ket qua"
        } catch (error: Throwable) {
            status = error.message ?: "Co loi khi xu ly anh"
        } finally {
            isProcessing = false
        }
    }

    if (showPicker) {
        SingleImagePickerScreen(
            onImageSelected = { uri ->
                showPicker = false
                scope.launch { processUri(uri) }
            },
            onCancel = { showPicker = false },
        )
        return
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Fusion Mask Lab") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = status,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            compareTotalMs?.let {
                Text(
                    text = "Total timing: ${it}ms",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { showPicker = true },
                    enabled = !isProcessing,
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Chon anh")
                }

                if (isProcessing) {
                    ElevatedButton(onClick = {}, enabled = false) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Dang chay")
                    }
                }
            }

            ModelSelectorSection(
                title = "1. Mo hinh xoa phong",
                options = BackgroundMaskModel.entries,
                selected = selectedBackgrounds,
                label = { it.label },
                enabled = !isProcessing,
                onToggle = { model ->
                    selectedBackgrounds = selectedBackgrounds.toggle(model)
                },
            )

            ModelSelectorSection(
                title = "2. Mo hinh mask/core",
                options = CoreMaskModel.entries,
                selected = selectedCores,
                label = { it.label },
                enabled = !isProcessing,
                onToggle = { model ->
                    selectedCores = selectedCores.toggle(model)
                },
            )

            PreviewCard(title = "Anh goc") {
                val bitmap = sourceBitmap
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(bitmap.safeAspectRatio())
                            .clip(RoundedCornerShape(12.dp)),
                    )
                } else {
                    EmptyState()
                }
            }

            labResults.forEach { item ->
                CompareResultCard(
                    title = item.title,
                    result = item.result,
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun <T> ModelSelectorSection(
    title: String,
    options: List<T>,
    selected: Set<T>,
    label: (T) -> String,
    enabled: Boolean,
    onToggle: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleSmall)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                val checked = selected.contains(option)
                FilterChip(
                    selected = checked,
                    enabled = enabled,
                    onClick = { onToggle(option) },
                    label = { Text(label(option)) },
                    leadingIcon = {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = null,
                            enabled = enabled,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun CompareResultCard(
    title: String,
    result: RemoveBgResult,
) {
    PreviewCard(title = title) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val maskPreview = result.maskPreview
            if (maskPreview != null) {
                Image(
                    bitmap = maskPreview.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(maskPreview.safeAspectRatio())
                        .clip(RoundedCornerShape(12.dp)),
                )
            }
            CheckerboardBackground(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(result.bitmap.safeAspectRatio())
                    .clip(RoundedCornerShape(12.dp)),
            ) {
                Image(
                    bitmap = result.bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Text(
                text = "Route: ${result.route}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "Confidence: ${result.confidencePercent}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Timing: bg=${result.timing.modNetMs}ms, core=${result.timing.coreMs}ms, fusion=${result.timing.fusionMs}ms, total=${result.timing.totalMs}ms",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PreviewCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            ),
        ) {
            Box(modifier = Modifier.padding(12.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.BrokenImage,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
    }
}

@Composable
private fun CheckerboardBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cell = 24.dp.toPx()
            val light = Color(0xFFF2F2F2)
            val dark = Color(0xFFD9D9D9)
            val rows = (size.height / cell).toInt() + 1
            val cols = (size.width / cell).toInt() + 1
            for (row in 0 until rows) {
                for (col in 0 until cols) {
                    drawRect(
                        color = if ((row + col) % 2 == 0) light else dark,
                        topLeft = androidx.compose.ui.geometry.Offset(col * cell, row * cell),
                        size = androidx.compose.ui.geometry.Size(cell, cell),
                    )
                }
            }
        }
        content()
    }
}

private fun <T> Set<T>.toggle(value: T): Set<T> {
    return if (contains(value)) this - value else this + value
}

private fun Bitmap.safeAspectRatio(): Float {
    val w = width.takeIf { it > 0 } ?: return 1f
    val h = height.takeIf { it > 0 } ?: return 1f
    val ratio = w.toFloat() / h.toFloat()
    return if (ratio.isFinite() && ratio > 0f) ratio else 1f
}

private fun List<FusionLabResult>.recycleBitmaps() {
    forEach { item ->
        item.result.maskPreview?.recycleIfAlive()
        item.result.bitmap.recycleIfAlive()
    }
}

private fun Bitmap.recycleIfAlive() {
    if (!isRecycled) {
        recycle()
    }
}
