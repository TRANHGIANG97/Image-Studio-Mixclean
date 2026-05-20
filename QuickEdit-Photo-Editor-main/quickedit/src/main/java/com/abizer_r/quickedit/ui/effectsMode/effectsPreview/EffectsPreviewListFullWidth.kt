package com.abizer_r.quickedit.ui.effectsMode.effectsPreview

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abizer_r.quickedit.R
import com.abizer_r.quickedit.theme.QuickEditTheme
import com.abizer_r.quickedit.ui.editorScreen.bottomToolbar.TOOLBAR_HEIGHT_EXTRA_LARGE

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EffectsPreviewListFullWidth(
    modifier: Modifier = Modifier,
    toolbarHeight: Dp = TOOLBAR_HEIGHT_EXTRA_LARGE,
    effectsList: List<EffectItem>,
    selectedIndex: Int,
    onItemClicked: (position: Int, effectItem: EffectItem) -> Unit
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .height(toolbarHeight)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(
            count = effectsList.size,
            key = { effectsList[it].id }
        ) { index ->
            val effectItem = effectsList[index]
            EffectPreview(
                modifier = Modifier.animateItem(),
                effectItem = effectItem,
                isSelected = index == selectedIndex,
                onClick = { clicked -> onItemClicked(index, clicked) }
            )
        }
    }
}

@Composable
fun EffectPreview(
    modifier: Modifier = Modifier,
    effectItem: EffectItem,
    isSelected: Boolean,
    selectedBorderWidth: Dp = 1.5.dp,
    selectedBorderColor: Color = MaterialTheme.colorScheme.primary,
    clipShape: Shape = RoundedCornerShape(22.dp),
    onClick: (effectItem: EffectItem) -> Unit
) {
    Surface(
        modifier = modifier
            .size(width = 100.dp, height = 132.dp)
            .clickable { onClick(effectItem) },
        shape = clipShape,
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.56f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
        },
        tonalElevation = if (isSelected) 8.dp else 2.dp,
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) selectedBorderWidth + 0.75.dp else 1.dp,
            color = if (isSelected) {
                selectedBorderColor
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            }
        )
    ) {
        Column(
            modifier = Modifier
                .padding(5.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (isSelected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                        } else {
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    modifier = Modifier.fillMaxSize(),
                    bitmap = effectItem.previewBitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop
                )
            }

            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        if (isSelected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        } else {
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                        }
                    )
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                text = effectItem.label,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    fontSize = 10.sp
                )
            )
        }
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun Selected_EffectPreviewItem() {
    QuickEditTheme {
        val bitmap = BitmapFactory.decodeResource(
            androidx.compose.ui.platform.LocalContext.current.resources,
            R.drawable.placeholder_image_3
        )
        EffectPreview(
            modifier = Modifier,
            effectItem = EffectItem(
                ogBitmap = bitmap,
                previewBitmap = bitmap,
                label = "Dummy Effect"
            ),
            isSelected = true,
            onClick = {}
        )
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun Unselected_EffectPreviewItem() {
    QuickEditTheme {
        val bitmap = BitmapFactory.decodeResource(
            androidx.compose.ui.platform.LocalContext.current.resources,
            R.drawable.placeholder_image_3
        )
        EffectPreview(
            modifier = Modifier,
            effectItem = EffectItem(
                ogBitmap = bitmap,
                previewBitmap = bitmap,
                label = "Dummy Effect"
            ),
            isSelected = false,
            onClick = {}
        )
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun Preview_EffectsPreviewList() {
    val bitmap = BitmapFactory.decodeResource(
        androidx.compose.ui.platform.LocalContext.current.resources,
        R.drawable.placeholder_image_3
    )
    val mEffectsList = listOf(
        EffectItem(
            ogBitmap = bitmap,
            previewBitmap = bitmap,
            label = "original"
        ),
        EffectItem(
            ogBitmap = bitmap,
            previewBitmap = bitmap,
            label = "greyscale"
        ),
        EffectItem(
            ogBitmap = bitmap,
            previewBitmap = bitmap,
            label = "poppy dogs"
        )
    )
    QuickEditTheme {
        EffectsPreviewListFullWidth(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface)
                .padding(vertical = 10.dp),
            effectsList = mEffectsList,
            selectedIndex = 0,
            onItemClicked = { _, _ -> }
        )
    }
}
