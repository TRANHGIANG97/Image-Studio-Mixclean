package com.abizer_r.quickedit.ui.transformableViews.base

import android.content.res.Configuration
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.abizer_r.quickedit.theme.QuickEditTheme
import com.abizer_r.quickedit.ui.transformableViews.TransformableTextBox
import com.abizer_r.quickedit.utils.drawMode.pxToDp
import com.abizer_r.quickedit.utils.textMode.TextModeUtils.getDefaultEditorTextStyle

@Composable
fun TransformableBox(
    modifier: Modifier = Modifier,
    viewState: TransformableBoxState,
    showBorderOnly: Boolean = false,
    recompositionTrigger: Long = 0,
    onEvent: (TransformableBoxEvents) -> Unit,
    content: @Composable () -> Unit
) {
    val isSelected = viewState.isSelected
    val currentOnEvent by rememberUpdatedState(onEvent)
    
    // Use the trigger to ensure recomposition when the object state changes
    val _trigger = recompositionTrigger

    val posXF = viewState.positionOffset.x
    val posYF = viewState.positionOffset.y
    val safeScale = viewState.scale.takeIf { it.isFinite() && it > 0f } ?: 1f
    val safeRotation = viewState.rotation.takeIf { it.isFinite() } ?: 0f

    Box(
        modifier = Modifier
            .graphicsLayer(
                translationX = posXF,
                translationY = posYF,
                scaleX = safeScale,
                scaleY = safeScale,
                rotationZ = safeRotation
            )
            .pointerInput(viewState.id) {
                detectDragGestures { change, dragAmount ->
                    // Rotate the dragAmount back by the current rotation to match screen space
                    val rad = Math.toRadians(safeRotation.toDouble())
                    val cos = Math.cos(rad)
                    val sin = Math.sin(rad)
                    val rotatedDragAmount = Offset(
                        (dragAmount.x * cos - dragAmount.y * sin).toFloat(),
                        (dragAmount.x * sin + dragAmount.y * cos).toFloat()
                    )

                    if (rotatedDragAmount.isNearlyZero()) {
                        change.consume()
                        return@detectDragGestures
                    }

                    Log.e("TEST_TEXT_DRAG", "dragAmount=$dragAmount rotated=$rotatedDragAmount pos=${viewState.positionOffset}")
                    change.consume()
                    currentOnEvent(
                        TransformableBoxEvents.UpdateTransformation(
                            id = viewState.id,
                            dragAmount = rotatedDragAmount,
                            zoomAmount = 1f,
                            rotationChange = 0f
                        )
                    )
                }
            }
            .pointerInput(viewState.id) {
                detectTapGestures {
                    Log.e("TEST_TEXT_TAP", "tap id=${viewState.id}")
                    currentOnEvent(TransformableBoxEvents.OnTapped(viewState.id, null))
                }
            }
    ) {
        if (showBorderOnly) {
            EmptyBoxWithSize(viewState.innerBoxSize)
        } else {
            // Nội dung text
            Box(
                modifier = Modifier
                    .onGloballyPositioned {
                        val size = it.size.toSize()
                        if (viewState.innerBoxSize != size) {
                            currentOnEvent(TransformableBoxEvents.UpdateBoxBorder(viewState.id, size))
                        }
                    }
                    .then(
                        if (isSelected) Modifier.dashedBorder(
                            strokeWidthInPx = 2f / safeScale.coerceAtLeast(0.5f),
                            color = Color.White,
                            dashOnOffSizePair = Pair(8f, 6f)
                        ) else Modifier
                    )
            ) {
                content()
            }

            // Close button — trên content, chỉ khi selected
            if (isSelected) {
                CloseButton(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = (-6).dp, y = (-6).dp),
                    viewState = viewState,
                    onEvent = currentOnEvent
                )
            }
        }
    }
}

@Composable
fun EmptyBoxWithSize(boxSize: Size) {
    Box(
        modifier = Modifier
            .size(
                width = boxSize.width.pxToDp(),
                height = boxSize.height.pxToDp(),
            )
            .background(Color.Transparent)
    ) {}
}

@Composable
fun CloseButton(
    modifier: Modifier,
    viewState: TransformableBoxState,
    onEvent: (TransformableBoxEvents) -> Unit
) {
    Image(
        modifier = modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onBackground)
            .clickable {
                onEvent(TransformableBoxEvents.OnCloseClicked(viewState.id))
            },
        imageVector = Icons.Default.Close,
        contentScale = ContentScale.FillBounds,
        contentDescription = null,
        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.background)
    )
}

fun Modifier.dashedBorder(
    strokeWidthInPx: Float,
    color: Color,
    cornerRadius: CornerRadius = CornerRadius(0f),
    dashOnOffSizePair: Pair<Float, Float> = Pair(8f, 6f)
) = this.drawBehind {
    val stroke = Stroke(
        width = strokeWidthInPx,
        pathEffect = PathEffect.dashPathEffect(
            intervals = floatArrayOf(dashOnOffSizePair.first, dashOnOffSizePair.second),
            phase = 0f
        )
    )
    drawRoundRect(color, style = stroke, cornerRadius = cornerRadius)
}

private fun Offset.isNearlyZero(epsilon: Float = 0.35f): Boolean {
    return kotlin.math.abs(x) <= epsilon && kotlin.math.abs(y) <= epsilon
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun PreviewTextItem_WITH_BORDER() {
    QuickEditTheme {
        Box(modifier = Modifier.size(300.dp, 100.dp).background(MaterialTheme.colorScheme.background)) {
            TransformableTextBox(
                viewState = TransformableTextBoxState(
                    id = "",
                    text = "Hello",
                    textAlign = TextAlign.Center,
                    positionOffset = Offset(100f, 100f),
                    scale = 1f, rotation = 0f,
                    textColor = MaterialTheme.colorScheme.onBackground,
                    textFont = getDefaultEditorTextStyle().fontSize
                ),
                onEvent = {},
            )
        }
    }
}
