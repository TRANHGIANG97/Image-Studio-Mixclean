package com.abizer_r.quickedit.ui.magicBrush.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.abizer_r.quickedit.R
import com.abizer_r.quickedit.ui.magicBrush.MagicWandDemoState

@Composable
fun MagicWandTooltipDialog(
    demoState: MagicWandDemoState,
    onPrepareDemo: () -> Unit,
    onDismiss: (dontShowAgain: Boolean) -> Unit
) {
    var dontShowAgain by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        onPrepareDemo()
    }

    Dialog(
        onDismissRequest = { onDismiss(dontShowAgain) },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.magic_wand_tooltip_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(16.dp))

                MagicWandInteractiveDemo(
                    demoState = demoState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.magic_wand_tooltip_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.magic_wand_tooltip_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { dontShowAgain = !dontShowAgain }
                        .padding(vertical = 8.dp)
                ) {
                    Checkbox(
                        checked = dontShowAgain,
                        onCheckedChange = { dontShowAgain = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.dont_show_again),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { onDismiss(dontShowAgain) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.got_it))
                }
            }
        }
    }
}

@Composable
private fun MagicWandInteractiveDemo(
    demoState: MagicWandDemoState,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "magicWandTooltipDemo")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(6200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "demoProgress"
    )

    val touchProgress = smoothStep(stageProgress(progress, 0.04f, 0.30f))
    val selectionAlpha = stageProgress(progress, 0.34f, 0.46f) *
        (1f - stageProgress(progress, 0.54f, 0.62f))
    val erasedAlpha = stageProgress(progress, 0.52f, 0.62f) *
        (1f - stageProgress(progress, 0.94f, 1.00f))
    val outlineAlpha = stageProgress(progress, 0.48f, 0.60f) *
        (1f - stageProgress(progress, 0.90f, 0.98f))
    val tapXRatio = demoState.tapXRatio
    val tapYRatio = demoState.tapYRatio
    val handXRatio = lerpFloat(0.78f, tapXRatio, touchProgress)
    val handYRatio = lerpFloat(0.76f, tapYRatio, touchProgress)
    val sourceAlpha = (1f - erasedAlpha).coerceIn(0f, 1f)

    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
                shape = RoundedCornerShape(14.dp)
            )
    ) {
        val source = demoState.sourceBitmap
        if (source != null && !source.isRecycled) {
            Image(
                bitmap = source.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(alpha = sourceAlpha)
            )
        } else {
            Image(
                painter = painterResource(id = R.drawable.before_bird),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(alpha = sourceAlpha)
            )
        }

        val selectedBackground = demoState.selectedBackgroundBitmap
        if (selectedBackground != null && !selectedBackground.isRecycled && selectionAlpha > 0f) {
            Image(
                bitmap = selectedBackground.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(Color(0xFFFF2D55).copy(alpha = 0.62f)),
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(alpha = selectionAlpha.coerceIn(0f, 1f))
            )
        }

        val erased = demoState.erasedBitmap
        if (erased != null && !erased.isRecycled && erasedAlpha > 0f) {
            Image(
                bitmap = erased.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(alpha = erasedAlpha.coerceIn(0f, 1f))
            )
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val bitmapWidth = source?.width?.toFloat() ?: 1f
            val bitmapHeight = source?.height?.toFloat() ?: 1f
            val bitmapAspect = bitmapWidth / bitmapHeight
            val boxAspect = size.width / size.height
            val drawWidth: Float
            val drawHeight: Float
            if (boxAspect > bitmapAspect) {
                drawHeight = size.height
                drawWidth = drawHeight * bitmapAspect
            } else {
                drawWidth = size.width
                drawHeight = drawWidth / bitmapAspect
            }
            val left = (size.width - drawWidth) / 2f
            val top = (size.height - drawHeight) / 2f
            val tap = Offset(
                x = left + drawWidth * tapXRatio,
                y = top + drawHeight * tapYRatio
            )

            val touchPulse = stageProgress(progress, 0.25f, 0.42f) *
                (1f - stageProgress(progress, 0.42f, 0.54f))
            if (touchPulse > 0f) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.55f * touchPulse),
                    radius = 34f * touchPulse,
                    center = tap,
                    style = Stroke(width = 3f)
                )
                drawCircle(
                    color = Color(0xFFFF2D55).copy(alpha = 0.32f * touchPulse),
                    radius = 18f * touchPulse,
                    center = tap
                )
            }

            if (outlineAlpha > 0f) {
                val points = demoState.boundaryPoints
                if (points.isEmpty()) {
                    drawRoundRect(
                        color = Color.White.copy(alpha = outlineAlpha),
                        topLeft = Offset(left + drawWidth * 0.18f, top + drawHeight * 0.18f),
                        size = androidx.compose.ui.geometry.Size(drawWidth * 0.64f, drawHeight * 0.64f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(18f, 18f),
                        style = Stroke(
                            width = 3f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 9f), 0f)
                        )
                    )
                } else {
                    val phase = (progress * 60f).toInt()
                    points.forEachIndexed { index, point ->
                        if (((index + phase) % 18) < 10) {
                            val x = left + drawWidth * point.x
                            val y = top + drawHeight * point.y
                            drawLine(
                                color = Color.White.copy(alpha = outlineAlpha),
                                start = Offset(x - 4f, y),
                                end = Offset(x + 4f, y),
                                strokeWidth = 3f,
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }
            }
        }

        Icon(
            imageVector = Icons.Outlined.PanTool,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier
                .offset(
                    x = maxWidth * handXRatio - 22.dp,
                    y = maxHeight * handYRatio - 22.dp
                )
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f))
                .padding(9.dp)
        )

        if (demoState.isPreparing) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(30.dp),
                strokeWidth = 2.5.dp
            )
        }
    }
}

private fun stageProgress(progress: Float, start: Float, end: Float): Float {
    return ((progress - start) / (end - start)).coerceIn(0f, 1f)
}

private fun smoothStep(value: Float): Float {
    return value * value * (3f - 2f * value)
}

private fun lerpFloat(start: Float, stop: Float, fraction: Float): Float {
    return start + (stop - start) * fraction.coerceIn(0f, 1f)
}
