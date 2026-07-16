package com.thgiang.image.studio.ui.editor.canvas.text

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.thgiang.image.studio.R
import com.thgiang.image.studio.ui.editor.model.EditorLayer
import com.thgiang.image.studio.ui.editor.model.TextRunOps
import com.thgiang.image.studio.ui.editor.model.isLabelLayer
import com.thgiang.image.studio.ui.editor.model.withTextSpans

/** Visible caret while inline-editing (matches text edit frame accent). */
private val InlineCaretBrush = SolidColor(Color(0xFF7C3AED))

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

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = if (layer.textForm.isActive) Alignment.Center else Alignment.TopCenter,
    ) {
        if (isInlineEditing) {
            val annotated = remember(
                layer.text,
                layer.textSpans,
                layer.fontWeight,
                layer.fontStyle,
                layer.textColorArgb,
                layer.underline,
                layer.linethrough,
                inlineTextDraft.text,
            ) {
                val displayLayer = if (inlineTextDraft.text == layer.text) {
                    layer
                } else {
                    val reflowed = TextRunOps.reflow(
                        TextRunOps.effectiveSpans(layer),
                        layer.text,
                        inlineTextDraft.text,
                    )
                    layer.withTextSpans(reflowed)
                }
                TextRunOps.toAnnotatedString(displayLayer)
            }
            val styledDraft = remember(annotated, inlineTextDraft.selection, inlineTextDraft.composition) {
                TextFieldValue(
                    annotatedString = annotated,
                    selection = inlineTextDraft.selection,
                    composition = inlineTextDraft.composition,
                )
            }
            BasicTextField(
                value = styledDraft,
                onValueChange = { next ->
                    onInlineTextDraftChange(
                        TextFieldValue(
                            text = next.text,
                            selection = next.selection,
                            composition = next.composition,
                        ),
                    )
                },
                textStyle = textStyle,
                singleLine = false,
                maxLines = 6,
                cursorBrush = InlineCaretBrush,
                onTextLayout = onTextLayout,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                decorationBox = { innerTextField ->
                    Box(modifier = Modifier.wrapContentSize(unbounded = true)) {
                        if (inlineTextDraft.text.isEmpty()) {
                            Text(
                                text = stringResource(R.string.studio_text_default_placeholder),
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
            // Always use DocumentRenderPipeline so preview metrics match export (I6).
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = paddingX, vertical = paddingY),
            ) {
                drawIntoCanvas { composeCanvas ->
                    com.thgiang.image.studio.ui.editor.document.render.DocumentRenderPipeline.drawTextLayer(
                        canvas = composeCanvas.nativeCanvas,
                        context = context,
                        layer = layer,
                        left = 0f,
                        top = 0f,
                        width = size.width,
                        height = size.height,
                        renderScale = templateScale,
                    )
                }
            }
        }
    }
}
