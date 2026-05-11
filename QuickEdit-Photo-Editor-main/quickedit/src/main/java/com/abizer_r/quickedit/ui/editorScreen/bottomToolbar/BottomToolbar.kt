package com.abizer_r.quickedit.ui.editorScreen.bottomToolbar

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abizer_r.quickedit.R
import com.abizer_r.quickedit.theme.QuickEditTheme
// ToolBarBackgroundColor removed
import com.abizer_r.quickedit.utils.ImmutableList
import com.abizer_r.quickedit.ui.editorScreen.bottomToolbar.state.BottomToolbarEvent
import com.abizer_r.quickedit.ui.editorScreen.bottomToolbar.state.BottomToolbarItem
import com.abizer_r.quickedit.ui.transformableViews.base.TransformableTextBoxState
import com.abizer_r.quickedit.utils.defaultTextColor
import com.abizer_r.quickedit.utils.drawMode.DrawModeUtils
import com.abizer_r.quickedit.utils.editorScreen.EditorScreenUtils
import com.abizer_r.quickedit.utils.textMode.TextModeUtils
import java.util.UUID

val TOOLBAR_HEIGHT_SMALL = 48.dp
val TOOLBAR_HEIGHT_MEDIUM = 64.dp
val TOOLBAR_HEIGHT_LARGE = 88.dp
val TOOLBAR_HEIGHT_EXTRA_LARGE = 104.dp

@Composable
fun BottomToolBarStatic(
    modifier: Modifier,
    toolbarItems: ImmutableList<BottomToolbarItem>,
    toolbarHeight: Dp = TOOLBAR_HEIGHT_MEDIUM,
    selectedItem: BottomToolbarItem = BottomToolbarItem.NONE,
    showColorPickerIcon: Boolean = true,
    selectedColor: Color = Color.White,
    onEvent: (BottomToolbarEvent) -> Unit
) {

    val scrollState = rememberScrollState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(toolbarHeight)
            .background(MaterialTheme.colorScheme.surface)
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(modifier = Modifier.width(16.dp))
        toolbarItems.items.forEachIndexed { index, mToolbarItem ->
            ToolbarItem(
                toolbarItem = mToolbarItem,
                selectedColor = selectedColor,
                showColorPickerIcon = showColorPickerIcon,
                isSelected = mToolbarItem == selectedItem,
                onEvent = onEvent
            )
            Spacer(modifier = Modifier.width(12.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
    }
}


@Composable
fun ToolbarItem(
    modifier: Modifier = Modifier,
    selectedColor: Color,
    showColorPickerIcon: Boolean,
    toolbarItem: BottomToolbarItem,
    isSelected: Boolean,
    onEvent: (BottomToolbarEvent) -> Unit
) {
    val labelTextStyle = MaterialTheme.typography.bodySmall.copy(color = defaultTextColor())

    val commonPaddingModifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)

    if (toolbarItem is BottomToolbarItem.ColorItem) {
        ColorToolbarItem(
            modifier = modifier.then(commonPaddingModifier),
            selectedColor = selectedColor,
            showColorPickerIcon = showColorPickerIcon,
            colorItem = toolbarItem,
            labelTextStyle = labelTextStyle,
            onEvent = onEvent
        )
        return
    }
    var columnModifier = modifier.clickable {
        onEvent(BottomToolbarEvent.OnItemClicked(toolbarItem))
    }
    if (isSelected) {
        columnModifier = columnModifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f))
            .padding((0.5).dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
    }
    columnModifier = columnModifier.then(commonPaddingModifier)


    val (imageVector, labelText) = when (toolbarItem) {

        is BottomToolbarItem.CropMode -> Pair(
            Icons.Outlined.Crop,
            stringResource(id = R.string.crop)
        )
        is BottomToolbarItem.DrawMode -> Pair(
            Icons.Outlined.Brush,
            stringResource(id = R.string.draw)

        )
        is BottomToolbarItem.TextMode -> Pair(
            Icons.Default.TextFields,
            stringResource(id = R.string.text)
        )
        is BottomToolbarItem.EffectsMode -> Pair(
            ImageVector.vectorResource(id = com.abizer_r.quickedit.R.drawable.ic_effects),
            stringResource(id = R.string.effects)
        )
        is BottomToolbarItem.BorderMode -> Pair(
            ImageVector.vectorResource(id = com.abizer_r.quickedit.R.drawable.ic_border),
            stringResource(id = R.string.border)
        )
        is BottomToolbarItem.StudioMode -> Pair(
            Icons.Default.AutoFixHigh,
            stringResource(id = R.string.studio)
        )
        is BottomToolbarItem.RemoveBg -> Pair(
            Icons.Outlined.Person,
            stringResource(id = R.string.remove_bg)
        )
        is BottomToolbarItem.MagicBrush -> Pair(
            Icons.Outlined.Brush,
            stringResource(id = R.string.tool_magic_brush)
        )
        is BottomToolbarItem.BackgroundMode -> Pair(
            Icons.Outlined.Collections,
            stringResource(id = R.string.background)
        )

        is BottomToolbarItem.EraserTool -> Pair(
            ImageVector.vectorResource(id = R.drawable.ic_eraser),
            stringResource(id = R.string.eraser)
        )
        is BottomToolbarItem.MosaicTool -> Pair(
            androidx.compose.material.icons.Icons.Default.Grain,
            stringResource(id = R.string.mosaic)
        )




        is BottomToolbarItem.ShapeTool -> Pair(
            Icons.Outlined.Category,
            stringResource(id = R.string.shape)
        )

        is BottomToolbarItem.BrushTool -> Pair(
            ImageVector.vectorResource(id = R.drawable.ic_stylus_note),
            stringResource(id = R.string.brush)
        )

        is BottomToolbarItem.PanItem -> Pair(
            Icons.Outlined.PanTool,
            stringResource(id = R.string.zoom)
        )

        is BottomToolbarItem.TextFormat -> Pair(
            ImageVector.vectorResource(id = R.drawable.outline_custom_typography_24),
            stringResource(id = R.string.format)
        )

        is BottomToolbarItem.TextFontFamily -> Pair(
            ImageVector.vectorResource(id = R.drawable.outline_serif_24),
            stringResource(id = R.string.font)
        )

        else -> Pair(
            Icons.Default.AddCircleOutline,
            ""
        )
    }


    Column(
        modifier = columnModifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        val verticalPaddingBeforeSize = if (labelText.isBlank()) 4.dp else 0.dp
        val imageSize = if (labelText.isBlank()) 32.dp else 28.dp
        Image(
            modifier = Modifier
                .padding(vertical = verticalPaddingBeforeSize)
                .size(imageSize),
            contentDescription = null,
            imageVector = imageVector,
            colorFilter = ColorFilter.tint(
                color = MaterialTheme.colorScheme.onBackground
            )
        )
        Spacer(modifier = Modifier.size(
            if (labelText.isNotBlank()) 4.dp else 0.dp
        ))

        if (labelText.isNotBlank()) {
            Text(
                style = labelTextStyle,
                text = labelText,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
fun ColorToolbarItem(
    modifier: Modifier = Modifier,
    selectedColor: Color,
    showColorPickerIcon: Boolean,
    colorItem: BottomToolbarItem.ColorItem,
    labelTextStyle: TextStyle,
    onEvent: (BottomToolbarEvent) -> Unit
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        if (showColorPickerIcon) {
            Image(
                bitmap = ImageBitmap.imageResource(id = R.drawable.ic_color_picker),
                contentDescription = null,
                modifier = Modifier
                    .size(26.dp)
                    .clickable {
                        onEvent(BottomToolbarEvent.OnItemClicked(colorItem))
                    }
            )
        } else {
            Image(
                painter = ColorPainter(selectedColor),
                contentDescription = null,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(color = MaterialTheme.colorScheme.onBackground)
                    .padding(1.dp)
                    .clip(CircleShape)
                    .size(26.dp)
                    .clickable {
                        onEvent(BottomToolbarEvent.OnItemClicked(colorItem))
                    }
            )
        }
        

        Spacer(modifier = Modifier.size(4.dp))

        Text(
            style = labelTextStyle,
            text = stringResource(id = R.string.color)
        )
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun EditorScreen_BottomToolbar() {
    QuickEditTheme {
        val itemsList = EditorScreenUtils.getDefaultBottomToolbarItemsList()
        BottomToolBarStatic(
            modifier = Modifier.fillMaxWidth(),
            toolbarItems = ImmutableList(itemsList),
            onEvent = {}
        )
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun DrawMode_BottomToolbar() {
    QuickEditTheme {
        val itemsList = DrawModeUtils.getDefaultBottomToolbarItemsList()
        BottomToolBarStatic(
            modifier = Modifier.fillMaxWidth(),
            toolbarItems = ImmutableList(itemsList),
            showColorPickerIcon = true,
            selectedColor = Color.White,
            selectedItem = itemsList[DrawModeUtils.DEFAULT_SELECTED_INDEX],
            onEvent = {}
        )
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun TextMode_BottomToolbar() {
    QuickEditTheme {
        val itemsList = TextModeUtils.getBottomToolbarItemsList(
            selectedViewState = TransformableTextBoxState(
                id = UUID.randomUUID().toString(),
                text = "hello",
                textColor = Color.White,
                textFont = 8.sp,
            )
        )
        BottomToolBarStatic(
            modifier = Modifier.fillMaxWidth(),
            toolbarItems = ImmutableList(itemsList),
            showColorPickerIcon = true,
            selectedColor = Color.White,
            onEvent = {}
        )
    }
}