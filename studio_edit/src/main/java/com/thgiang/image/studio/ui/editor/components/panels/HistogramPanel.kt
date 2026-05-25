package com.thgiang.image.studio.ui.editor.components.panels

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thgiang.image.studio.ui.editor.theme.EditorTokens
import com.thgiang.image.studio.ui.editor.theme.LocalEditorTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Container holding 256-bin histogram values for each channel.
 */
data class HistogramData(
    val red: IntArray,
    val green: IntArray,
    val blue: IntArray,
    val luminance: FloatArray
) {
    val maxCount: Int by lazy {
        max(
            max(red.maxOrNull() ?: 0, green.maxOrNull() ?: 0),
            max(blue.maxOrNull() ?: 0, (luminance.maxOrNull() ?: 0f).roundToInt())
        ).coerceAtLeast(1)
    }

    val isEmpty: Boolean get() = maxCount == 1 && (red.all { it == 0 })

    companion object {
        val EMPTY = HistogramData(
            red = IntArray(256),
            green = IntArray(256),
            blue = IntArray(256),
            luminance = FloatArray(256)
        )
    }
}

/**
 * Compute 256-bin RGB + luminance histogram from a [Bitmap].
 * Runs on the caller's dispatcher — wrap in `withContext(Dispatchers.Default)`.
 */
fun computeHistogram(bitmap: Bitmap, sampleStep: Int = 2): HistogramData {
    val red = IntArray(256)
    val green = IntArray(256)
    val blue = IntArray(256)
    val luminance = FloatArray(256)

    val w = bitmap.width
    val h = bitmap.height

    if (w <= 0 || h <= 0) return HistogramData.EMPTY

    val pixels = IntArray(w)
    for (y in 0 until h step sampleStep) {
        bitmap.getPixels(pixels, 0, w, 0, y, w, 1)
        for (x in 0 until w step sampleStep) {
            val pixel = pixels[x]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF

            red[r]++
            green[g]++
            blue[b]++

            val lum = (0.299f * r + 0.587f * g + 0.114f * b)
            val idx = lum.roundToInt().coerceIn(0, 255)
            luminance[idx]++
        }
    }

    return HistogramData(red, green, blue, luminance)
}

/**
 * Adobe-style histogram panel rendering RGB channels + luminance overlay.
 *
 * When [bitmap] is null a placeholder message is shown. Computation runs on
 * [Dispatchers.Default] and is automatically cancelled when the composable
 * leaves composition.
 */
@Composable
fun HistogramPanel(
    bitmap: Bitmap?,
    modifier: Modifier = Modifier,
    tokens: EditorTokens = LocalEditorTokens.current
) {
    var histogramData by remember { mutableStateOf(HistogramData.EMPTY) }

    LaunchedEffect(bitmap) {
        val src = bitmap
        if (src == null || src.isRecycled) {
            histogramData = HistogramData.EMPTY
            return@LaunchedEffect
        }
        histogramData = withContext(Dispatchers.Default) {
            if (!isActive) return@withContext HistogramData.EMPTY
            computeHistogram(src)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(tokens.cornerMedium))
            .background(tokens.surfaceElevated)
            .padding(12.dp)
    ) {
        Text(
            text = "Histogram",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = tokens.textPrimary
        )
        Spacer(Modifier.height(8.dp))

        if (histogramData.isEmpty) {
            Text(
                text = "No image data",
                fontSize = 10.sp,
                color = tokens.textDisabled,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            )
        } else {
            HistogramCanvas(
                data = histogramData,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(tokens.histogramHeight),
                tokens = tokens
            )

            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                HistogramLegend(
                    label = "R", color = Color(0xFFEF4444), tokens = tokens
                )
                HistogramLegend(
                    label = "G", color = Color(0xFF22C55E), tokens = tokens
                )
                HistogramLegend(
                    label = "B", color = Color(0xFF3B82F6), tokens = tokens
                )
                HistogramLegend(
                    label = "L", color = Color(0xFF9FB1CC), tokens = tokens
                )
            }
        }
    }
}

@Composable
private fun HistogramLegend(
    label: String,
    color: Color,
    tokens: EditorTokens
) {
    Row(
        modifier = Modifier.padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(modifier = Modifier.size(6.dp)) {
            drawCircle(color)
        }
        Spacer(Modifier.width(2.dp))
        Text(
            text = label,
            fontSize = 9.sp,
            color = tokens.textSecondary
        )
    }
}

@Composable
private fun HistogramCanvas(
    data: HistogramData,
    modifier: Modifier = Modifier,
    tokens: EditorTokens = LocalEditorTokens.current
) {
    val density = LocalDensity.current

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val barWidth = w / 256f
        val maxVal = data.maxCount.toFloat()
        if (maxVal <= 0f) return@Canvas

        // Background grid lines
        val gridColor = tokens.borderSubtle
        for (i in 1..3) {
            val y = h * (i / 4f)
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 0.5.dp.toPx()
            )
        }

        fun drawChannel(values: IntArray, color: Color, alpha: Float) {
            val path = Path()
            path.moveTo(0f, h)

            for (i in 0 until 256) {
                val barH = (values[i] / maxVal) * h
                val x = i * barWidth
                path.lineTo(x, h - barH)
                path.lineTo(x + barWidth, h - barH)
            }

            path.lineTo(w, h)
            path.close()

            drawPath(path, color.copy(alpha = alpha))
        }

        // Draw channels with blend-friendly alpha
        drawChannel(data.red, Color(0xFFEF4444), 0.35f)
        drawChannel(data.green, Color(0xFF22C55E), 0.35f)
        drawChannel(data.blue, Color(0xFF3B82F6), 0.35f)

        // Draw luminance as a line overlay
        val lumPath = Path()
        var first = true
        for (i in 0 until 256) {
            val barH = (data.luminance[i] / maxVal) * h
            val x = i * barWidth + barWidth / 2f
            if (first) { lumPath.moveTo(x, h - barH); first = false }
            else lumPath.lineTo(x, h - barH)
        }
        drawPath(
            lumPath,
            color = Color(0xFFF9FAFB),
            style = Stroke(width = 1.dp.toPx())
        )
    }
}
