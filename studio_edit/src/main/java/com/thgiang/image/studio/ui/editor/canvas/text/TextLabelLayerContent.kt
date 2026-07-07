package com.thgiang.image.studio.ui.editor.canvas.text

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.thgiang.image.studio.ui.editor.mapper.EditorTextStyleMapper
import com.thgiang.image.studio.ui.editor.mapper.TextElevationMapper.drawTextElevation
import com.thgiang.image.studio.ui.editor.mapper.TextFormLayoutEngine.drawTextForm
import com.thgiang.image.studio.ui.editor.mapper.hasShape3DDepth
import com.thgiang.image.studio.ui.editor.mapper.supportsTextElevation
import com.thgiang.image.studio.ui.editor.model.EditorLayer
import com.thgiang.image.studio.ui.editor.model.appliesTextElevation
import com.thgiang.image.studio.ui.editor.model.isLabelLayer

@Composable
internal fun TextLabelLayerContent(
    layer: EditorLayer,
    templateScale: Float,
    textStyle: TextStyle,
    displaySize: DpSize,
    paddingX: Dp,
    paddingY: Dp,
    isInlineEditing: Boolean,
    inlineTextDraft: TextFieldValue,
    onInlineTextDraftChange: (TextFieldValue) -> Unit,
    focusRequester: FocusRequester,
    inlineEditHadFocus: Boolean,
    onInlineEditHadFocus: (Boolean) -> Unit,
    onCommitInlineEdit: () -> Unit,
    onTextLayout: (TextLayoutResult) -> Unit,
    textLayoutResult: TextLayoutResult? = null,
    modifier: Modifier = Modifier,
) {
    if (!layer.isLabelLayer || (layer.text.isBlank() && !isInlineEditing)) return

    val context = LocalContext.current
    val canDrawTextElevation = layer.supportsTextElevation &&
        layer.appearance.hasShape3DDepth(templateScale) &&
        layer.appearance.appliesTextElevation()

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                if (canDrawTextElevation && !layer.textForm.isActive) {
                    drawTextElevation(
                        layer = layer,
                        renderScale = templateScale,
                        context = context,
                    )
                }
            },
        contentAlignment = if (layer.textForm.isActive) Alignment.Center else Alignment.TopCenter,
    ) {
        if (isInlineEditing) {
            BasicTextField(
                value = inlineTextDraft,
                onValueChange = onInlineTextDraftChange,
                textStyle = textStyle,
                singleLine = false,
                maxLines = 6,
                onTextLayout = onTextLayout,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                decorationBox = { innerTextField ->
                    Box(modifier = Modifier.wrapContentSize(unbounded = true)) {
                        if (inlineTextDraft.text.isEmpty()) {
                            Text(
                                text = "Nhập chữ...",
                                style = textStyle.copy(color = textStyle.color.copy(alpha = 0.35f))
                            )
                        }
                        innerTextField()
                    }
                },
                modifier = Modifier
                    .then(
                        if ((textLayoutResult?.lineCount ?: 1) > 1) {
                            Modifier.width(displaySize.width)
                        } else {
                            Modifier.widthIn(min = 60.dp)
                        }
                    )
                    .focusRequester(focusRequester)
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            onInlineEditHadFocus(true)
                        }
                    }
                    .padding(horizontal = paddingX, vertical = paddingY),
            )
        } else if (layer.text.isNotBlank()) {
            if (layer.textForm.isActive) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawBehind {
                            if (canDrawTextElevation) {
                                drawTextElevation(
                                    layer = layer,
                                    renderScale = templateScale,
                                    context = context,
                                )
                            }
                        }
                        .padding(horizontal = paddingX, vertical = paddingY),
                ) {
                    drawTextForm(
                        layer = layer,
                        renderScale = templateScale,
                        context = context,
                        gradientLeft = 0f,
                        gradientTop = 0f,
                        gradientWidth = size.width,
                        gradientHeight = size.height,
                    )
                }
            } else {
                val displayText = EditorTextStyleMapper.applyTextTransform(layer.text, layer.textTransform)
                Text(
                    text = displayText,
                    style = textStyle,
                    onTextLayout = onTextLayout,
                    overflow = TextOverflow.Visible,
                    softWrap = true,
                    modifier = Modifier
                        .width(displaySize.width)
                        .then(
                            if (layer.textBackgroundColorArgb != null) {
                                Modifier.background(Color(layer.textBackgroundColorArgb))
                            } else {
                                Modifier
                            },
                        )
                        .padding(horizontal = paddingX, vertical = paddingY),
                )
            }
        }
    }
}
