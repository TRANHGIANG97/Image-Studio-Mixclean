package com.thgiang.image.studio.ui.editor.label.panel
import com.thgiang.image.studio.ui.editor.label.geometry.*
import com.thgiang.image.studio.ui.editor.label.panel.*

import androidx.compose.foundation.Canvas
import com.thgiang.image.studio.ui.editor.model.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thgiang.image.studio.R
import com.thgiang.image.studio.ui.editor.theme.EditorTokens

internal val defaultCreateShapes: List<ShapeType> = listOf(
    ShapeType.TEXT_ONLY,
    ShapeType.CARD,
    ShapeType.PARALLELOGRAM,
    ShapeType.PILL,
    ShapeType.TEARDROP,
    ShapeType.CIRCLE,
    ShapeType.STAR,
    ShapeType.HEXAGON,
    ShapeType.TRIANGLE,
    ShapeType.DIAMOND,
    ShapeType.LINE,
    ShapeType.ARROW,
)

/** Shape templates shown in the Shape tool → Hình tab (matches editor gallery row). */
internal val defaultShapeTabShapes: List<ShapeType> = listOf(
    ShapeType.TEXT_ONLY,
    ShapeType.CARD,
    ShapeType.PARALLELOGRAM,
    ShapeType.PILL,
    ShapeType.TEARDROP,
    ShapeType.CIRCLE,
)

@Composable
internal fun shapeLabel(shape: ShapeType): String = when (shape) {
    ShapeType.TEXT_ONLY -> stringResource(R.string.studio_label_shape_text_only)
    ShapeType.CARD -> stringResource(R.string.studio_label_shape_card)
    ShapeType.PARALLELOGRAM -> stringResource(R.string.studio_label_shape_parallelogram)
    ShapeType.PILL -> stringResource(R.string.studio_label_shape_pill)
    ShapeType.TEARDROP -> stringResource(R.string.studio_label_shape_teardrop)
    ShapeType.CIRCLE -> stringResource(R.string.studio_label_shape_circle)
    ShapeType.STAR -> stringResource(R.string.studio_label_shape_star)
    ShapeType.HEXAGON -> stringResource(R.string.studio_label_shape_hexagon)
    ShapeType.TRIANGLE -> stringResource(R.string.studio_label_shape_triangle)
    ShapeType.DIAMOND -> stringResource(R.string.studio_label_shape_diamond)
    ShapeType.LINE -> stringResource(R.string.studio_label_shape_line)
    ShapeType.ARROW -> stringResource(R.string.studio_label_shape_arrow)
    ShapeType.PATH -> stringResource(R.string.studio_label_shape_path)
    ShapeType.POLYGON -> stringResource(R.string.studio_label_shape_polygon)
}

@Composable
internal fun ShapeGallery(
    shapes: List<ShapeType>,
    selectedShape: ShapeType?,
    tokens: EditorTokens,
    onShapeSelected: (ShapeType) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    singleRow: Boolean = false,
) {
    if (singleRow) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
                .border(
                    width = 1.dp,
                    color = Color(0xFFE5E7EB),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                shapes.forEach { shape ->
                    ShapeGalleryItem(
                        shape = shape,
                        label = "",
                        isSelected = selectedShape == shape,
                        tokens = tokens,
                        onClick = { onShapeSelected(shape) },
                    )
                }
            }
        }
    } else {
        val columns = if (compact) 4 else 5
        val rows = shapes.chunked(columns)
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            rows.forEach { rowShapes ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowShapes.forEach { shape ->
                        ShapeGalleryItem(
                            shape = shape,
                            label = shapeLabel(shape),
                            isSelected = selectedShape == shape,
                            tokens = tokens,
                            onClick = { onShapeSelected(shape) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShapeGalleryItem(
    shape: ShapeType,
    label: String,
    isSelected: Boolean,
    tokens: EditorTokens,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(46.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) tokens.accentSoft else Color.Transparent)
            .border(
                width = if (isSelected) 1.5.dp else 0.dp,
                color = if (isSelected) tokens.accent else Color.Transparent,
                shape = RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 2.dp),
    ) {
        Box(
            modifier = Modifier.size(28.dp),
            contentAlignment = Alignment.Center,
        ) {
            ShapePreviewIcon(shape = shape, color = if (isSelected) tokens.accent else Color(0xFF121212))
        }
        if (label.isNotEmpty()) {
            Text(
                text = label,
                fontSize = 8.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) tokens.accent else tokens.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
internal fun ShapePreviewIcon(
    shape: ShapeType,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(36.dp)) {
        val pad = 4f
        val w = size.width - pad * 2
        val h = size.height - pad * 2
        val strokeWidth = 2.2f
        val previewColor = color.copy(alpha = 0.9f)

        when (shape) {
            ShapeType.TEXT_ONLY -> {
                val top = pad + h * 0.2f
                val boxH = h * 0.6f
                drawRoundRect(
                    color = previewColor,
                    topLeft = androidx.compose.ui.geometry.Offset(pad, top),
                    size = Size(w, boxH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(0f, 0f),
                    style = Stroke(strokeWidth),
                )
                val lineStartX = pad + w * 0.22f
                val lineEndX = pad + w * 0.78f
                val shortEndX = pad + w * 0.55f
                drawLine(
                    color = previewColor,
                    start = androidx.compose.ui.geometry.Offset(lineStartX, top + boxH * 0.38f),
                    end = androidx.compose.ui.geometry.Offset(lineEndX, top + boxH * 0.38f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = previewColor,
                    start = androidx.compose.ui.geometry.Offset(lineStartX, top + boxH * 0.62f),
                    end = androidx.compose.ui.geometry.Offset(shortEndX, top + boxH * 0.62f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }
            ShapeType.PILL -> {
                drawRoundRect(
                    color = previewColor,
                    topLeft = androidx.compose.ui.geometry.Offset(pad, pad + h * 0.25f),
                    size = Size(w, h * 0.5f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(h * 0.25f, h * 0.25f),
                )
            }
            ShapeType.CARD -> {
                drawRoundRect(
                    color = previewColor,
                    topLeft = androidx.compose.ui.geometry.Offset(pad, pad + h * 0.15f),
                    size = Size(w, h * 0.7f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(0f, 0f),
                )
            }
            ShapeType.LINE -> {
                drawLine(
                    color = previewColor,
                    start = androidx.compose.ui.geometry.Offset(pad, pad + h / 2f),
                    end = androidx.compose.ui.geometry.Offset(pad + w, pad + h / 2f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }
            else -> {
                val geometryPath = EditorShapeGeometry.composePath(
                    shapeType = shape,
                    size = Size(w, h),
                )
                drawPath(
                    path = geometryPath,
                    color = previewColor,
                )
            }
        }
    }
}
