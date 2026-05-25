package com.thgiang.image.core.design.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.*
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import kotlin.math.roundToInt

/**
 * Color scheme for [PrecisionSlider].
 */
data class PrecisionSliderColors(
    val labelColor: Color,
    val labelActiveColor: Color,
    val valuePillBackground: Color,
    val valuePillTextColor: Color,
    val trackColor: Color,
    val trackActiveColor: Color,
    val thumbColor: Color,
    val thumbGlowColor: Color,
    val rangeLabelColor: Color,
    val borderColor: Color,
)

object PrecisionSliderDefaults {
    fun colors(
        labelColor: Color = Color(0xFFF9FAFB),
        labelActiveColor: Color = Color(0xFF22D3EE),
        valuePillBackground: Color = Color(0xFF181818),
        valuePillTextColor: Color = Color(0xFFF9FAFB),
        trackColor: Color = Color(0xFF181818),
        trackActiveColor: Color = Color(0xFF22D3EE),
        thumbColor: Color = Color.White,
        thumbGlowColor: Color = Color(0xFF22D3EE),
        rangeLabelColor: Color = Color(0xFF9FB1CC),
        borderColor: Color = Color.White.copy(alpha = 0.08f),
    ): PrecisionSliderColors = PrecisionSliderColors(
        labelColor = labelColor,
        labelActiveColor = labelActiveColor,
        valuePillBackground = valuePillBackground,
        valuePillTextColor = valuePillTextColor,
        trackColor = trackColor,
        trackActiveColor = trackActiveColor,
        thumbColor = thumbColor,
        thumbGlowColor = thumbGlowColor,
        rangeLabelColor = rangeLabelColor,
        borderColor = borderColor,
    )
}

/**
 * Adobe-style precision slider with label, value pill, and optional numeric input.
 *
 * - Dragging the slider scales up the value pill (1.12x) and shifts the label to accent color.
 * - Tapping the value pill enters numeric-edit mode.
 * - Pressing Enter or tapping outside commits the typed value.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrecisionSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueFormatter: (Float) -> String = { (it * 100).roundToInt().toString() },
    steps: Int = 0,
    colors: PrecisionSliderColors = PrecisionSliderDefaults.colors(),
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isDragging by interactionSource.collectIsDraggedAsState()
    val isActive = isDragging

    var isEditing by remember { mutableStateOf(false) }
    var editText by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    val pillScale by animateFloatAsState(
        targetValue = if (isDragging) 1.12f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 300f),
        label = "pillScale"
    )

    val labelColor = if (isActive) colors.labelActiveColor else colors.labelColor

    Column(modifier = modifier.animateContentSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = labelColor
            )

            if (isEditing) {
                Box(
                    modifier = Modifier
                        .width(64.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.valuePillBackground)
                        .border(0.5.dp, colors.borderColor, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    BasicTextField(
                        value = editText,
                        onValueChange = { newText ->
                            editText = newText.filter { it.isDigit() || it == '.' || it == '-' }
                        },
                        textStyle = TextStyle(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.valuePillTextColor,
                            textAlign = TextAlign.Center
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                commitEdit(editText, valueRange, onValueChange)
                                isEditing = false
                                keyboard?.hide()
                            }
                        ),
                        visualTransformation = VisualTransformation.None,
                        modifier = Modifier
                            .focusRequester(focusRequester)
                            .onKeyEvent { event ->
                                if (event.type == KeyEventType.KeyUp && event.key == Key.Enter) {
                                    commitEdit(editText, valueRange, onValueChange)
                                    isEditing = false
                                    keyboard?.hide()
                                    true
                                } else false
                            }
                    )
                }

                LaunchedEffect(Unit) { focusRequester.requestFocus() }
            } else {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.valuePillBackground)
                        .graphicsLayer {
                            scaleX = pillScale
                            scaleY = pillScale
                        }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            editText = valueFormatter(value)
                            isEditing = true
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = valueFormatter(value),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.valuePillTextColor
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            enabled = enabled,
            steps = steps,
            interactionSource = interactionSource,
            colors = SliderDefaults.colors(
                thumbColor = Color.Transparent,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent,
                disabledThumbColor = Color.Transparent,
                disabledActiveTrackColor = Color.Transparent,
                disabledInactiveTrackColor = Color.Transparent,
            ),
            thumb = { PrecisionThumb(isDragging, colors) },
            track = { sliderState ->
                val fraction = (sliderState.value - valueRange.start) /
                    (valueRange.endInclusive - valueRange.start)
                PrecisionTrack(fraction, colors)
            },
            modifier = Modifier.height(32.dp)
        )

        Spacer(Modifier.height(2.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = valueFormatter(valueRange.start),
                fontSize = 9.sp,
                color = colors.rangeLabelColor
            )
            Text(
                text = valueFormatter(valueRange.endInclusive),
                fontSize = 9.sp,
                color = colors.rangeLabelColor
            )
        }
    }
}

private fun commitEdit(
    text: String,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    val parsed = text.toFloatOrNull() ?: return
    onValueChange(parsed.coerceIn(range.start, range.endInclusive))
}

@Composable
private fun PrecisionTrack(
    fraction: Float,
    colors: PrecisionSliderColors,
) {
    Canvas(modifier = Modifier.fillMaxWidth().height(32.dp)) {
        val trackY = center.y
        val trackStroke = 2.dp.toPx()
        val radius = trackStroke / 2f

        drawRoundRect(
            color = colors.trackColor,
            topLeft = Offset(0f, trackY - radius),
            size = Size(size.width, trackStroke),
            cornerRadius = CornerRadius(radius, radius)
        )

        val activeW = size.width * fraction.coerceIn(0f, 1f)
        if (activeW > 0f) {
            drawRoundRect(
                color = colors.trackActiveColor,
                topLeft = Offset(0f, trackY - radius),
                size = Size(activeW, trackStroke),
                cornerRadius = CornerRadius(radius, radius)
            )
        }
    }
}

@Composable
private fun PrecisionThumb(
    isDragging: Boolean,
    colors: PrecisionSliderColors,
) {
    Canvas(Modifier.size(32.dp)) {
        val cx = center.x
        val cy = center.y
        val thumbRadius = 6.dp.toPx()

        if (isDragging) {
            drawCircle(
                color = colors.thumbGlowColor.copy(alpha = 0.2f),
                radius = 10.dp.toPx(),
                center = Offset(cx, cy)
            )
            drawCircle(
                color = colors.thumbGlowColor.copy(alpha = 0.45f),
                radius = thumbRadius + 1.dp.toPx(),
                center = Offset(cx, cy),
                style = Stroke(1.dp.toPx())
            )
        }
        drawCircle(colors.thumbColor, thumbRadius, Offset(cx, cy))
    }
}
