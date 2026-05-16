package com.toshiba.modnet

import android.graphics.Bitmap
import android.view.MotionEvent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
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
@OptIn(ExperimentalMaterial3Api::class)
private fun BackgroundRemoverScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var resultBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var confidencePercent by remember { mutableStateOf<Int?>(null) }
    var status by remember { mutableStateOf("Chọn ảnh để chạy ONNX FP16") }
    var isProcessing by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { BackgroundRemover.close() }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            isProcessing = true
            status = "Đang khởi tạo model..."
            try {
                withContext(Dispatchers.IO) {
                    BackgroundRemover.initialize(context.applicationContext)
                }

                status = "Đang nạp ảnh..."
                val bitmap = withContext(Dispatchers.IO) {
                    context.loadBitmapFromUri(uri)
                }
                sourceBitmap = bitmap

                status = "Đang xoá phông bằng ONNX..."
                val result = withContext(Dispatchers.Default) {
                    BackgroundRemover.removeBg(
                        bitmap = bitmap,
                        smoothMask = true,
                        enhanceEdges = false,
                    )
                }
                resultBitmap = result.bitmap
                confidencePercent = result.confidencePercent
                status = "Xong"
            } catch (error: Throwable) {
                status = error.message ?: "Có lỗi khi xử lý ảnh"
            } finally {
                isProcessing = false
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("ModNet Compose") }) }
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

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { picker.launch("image/*") },
                    enabled = !isProcessing,
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Chọn ảnh")
                }

                if (isProcessing) {
                    ElevatedButton(onClick = {}, enabled = false) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Đang chạy")
                    }
                }
            }

            PreviewCard(title = "Ảnh gốc") {
                val bitmap = sourceBitmap
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat())
                            .clip(RoundedCornerShape(12.dp)),
                    )
                } else {
                    EmptyState()
                }
            }

            PreviewCard(title = "Ảnh xoá phông ONNX FP16") {
                val bitmap = resultBitmap
                if (bitmap != null) {
                    CheckerboardBackground(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat())
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Độ chắc ước lượng: ${confidencePercent ?: 0}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    EmptyState()
                }
            }
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
                    val isDark = (row + col) % 2 == 0
                    drawRect(
                        color = if (isDark) light else dark,
                        topLeft = androidx.compose.ui.geometry.Offset(col * cell, row * cell),
                        size = androidx.compose.ui.geometry.Size(cell, cell),
                    )
                }
            }
        }
        content()
    }
}
