package com.abizer_r.quickedit.ui.borderMode

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.abizer_r.quickedit.R
import com.abizer_r.quickedit.theme.QuickEditTheme
// ToolBarBackgroundColor removed from imports
import com.abizer_r.quickedit.ui.common.AnimatedToolbarContainer
import com.abizer_r.quickedit.ui.common.LoadingView
import com.abizer_r.quickedit.ui.common.bottomToolbarModifier
import com.abizer_r.quickedit.ui.common.topToolbarModifier
import com.abizer_r.quickedit.ui.drawMode.bottomToolbarExtension.CustomSliderItem
import com.abizer_r.quickedit.ui.editorScreen.bottomToolbar.TOOLBAR_HEIGHT_EXTRA_LARGE
import com.abizer_r.quickedit.ui.editorScreen.bottomToolbar.TOOLBAR_HEIGHT_SMALL
import com.abizer_r.quickedit.ui.editorScreen.topToolbar.TextModeTopToolbar
import com.abizer_r.quickedit.utils.BorderUtils
import com.abizer_r.quickedit.utils.ImmutableList
import com.abizer_r.quickedit.utils.other.anim.AnimUtils
import com.abizer_r.quickedit.utils.other.bitmap.ImmutableBitmap
import com.abizer_r.quickedit.utils.textMode.colorList.ColorListFullWidth
import com.abizer_r.quickedit.utils.textMode.colorList.SelectableColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun BorderModeScreen(
    modifier: Modifier = Modifier,
    immutableBitmap: ImmutableBitmap,
    onDoneClicked: (bitmap: Bitmap) -> Unit,
    onBackPressed: () -> Unit
) {
    val context = LocalContext.current
    val lifeCycleOwner = LocalLifecycleOwner.current

    val viewModel: BorderModeViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle(
        lifecycleOwner = lifeCycleOwner
    )

    val bitmap = immutableBitmap.bitmap
    var borderedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    
    val topToolbarHeight = TOOLBAR_HEIGHT_SMALL
    val bottomToolbarHeight = 160.dp

    var toolbarVisible by remember { mutableStateOf(false) }

    LaunchedEffect(key1 = state.borderColorArgb, key2 = state.borderThickness) {
        viewModel.setApplyingBorder(true)
        withContext(Dispatchers.IO) {
            val result = BorderUtils.applyBorderToBitmap(
                bitmap = bitmap,
                borderColorArgb = state.borderColorArgb,
                borderWidthPx = state.borderThickness.toInt(),
                previewMaxDimension = 1024
            )
            val bm = result.getOrNull()
            withContext(Dispatchers.Main) {
                borderedBitmap = bm ?: bitmap
                viewModel.setApplyingBorder(false)
            }
        }
    }

    LaunchedEffect(key1 = Unit) {
        toolbarVisible = true
    }

    val onCloseClickedLambda = remember<() -> Unit> { {
        lifeCycleOwner.lifecycleScope.launch(Dispatchers.Main) {
            toolbarVisible = false
            delay(AnimUtils.TOOLBAR_COLLAPSE_ANIM_DURATION_FAST.toLong())
            onBackPressed()
        }
    }}

    BackHandler {
        onCloseClickedLambda()
    }

    val onDoneClickedLambda = remember<() -> Unit> { {
        lifeCycleOwner.lifecycleScope.launch(Dispatchers.Main) {
            toolbarVisible = false
            viewModel.setApplyingBorder(true)
            delay(AnimUtils.TOOLBAR_COLLAPSE_ANIM_DURATION_FAST.toLong())
            
            withContext(Dispatchers.IO) {
                val finalResult = BorderUtils.applyBorderToBitmap(
                    bitmap = bitmap,
                    borderColorArgb = state.borderColorArgb,
                    borderWidthPx = state.borderThickness.toInt(),
                    previewMaxDimension = null
                )
                val finalBm = finalResult.getOrNull()
                withContext(Dispatchers.Main) {
                    if (finalBm != null) {
                        onDoneClicked(finalBm)
                    } else {
                        onBackPressed()
                    }
                }
            }
        }
    }}

    ConstraintLayout(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
    ) {
        val (topToolBar, imageBox, bottomToolBar) = createRefs()

        AnimatedToolbarContainer(
            toolbarVisible = toolbarVisible,
            modifier = topToolbarModifier(topToolBar)
        ) {
            TextModeTopToolbar(
                modifier = Modifier,
                toolbarHeight = topToolbarHeight,
                onCloseClicked = onCloseClickedLambda,
                onDoneClicked = onDoneClickedLambda
            )
        }

        val displayBitmap = borderedBitmap ?: bitmap
        val aspectRatio = displayBitmap.let {
            it.width.toFloat() / it.height.toFloat()
        }
        
        Box(
            modifier = Modifier
                .constrainAs(imageBox) {
                    top.linkTo(parent.top)
                    bottom.linkTo(parent.bottom)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                    width = Dimension.wrapContent
                    height = Dimension.wrapContent
                }
                .padding(top = topToolbarHeight, bottom = bottomToolbarHeight)
                .aspectRatio(aspectRatio)
        ) {
            Image(
                modifier = Modifier.fillMaxSize(),
                bitmap = displayBitmap.asImageBitmap(),
                contentDescription = null
            )
        }

        AnimatedToolbarContainer(
            toolbarVisible = toolbarVisible,
            modifier = bottomToolbarModifier(bottomToolBar)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(bottomToolbarHeight)
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                ColorListFullWidth(
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                    colorList = ImmutableList(com.abizer_r.quickedit.utils.ColorUtils.defaultColorList),
                    selectedColor = androidx.compose.ui.graphics.Color(state.borderColorArgb),
                    onItemClicked = { _, color ->
                        viewModel.updateBorderColor(color.toArgb())
                    }
                )
                CustomSliderItem(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    sliderValue = state.borderThickness,
                    sliderLabel = stringResource(id = R.string.width),
                    minValue = 1f,
                    maxValue = 81f,
                    onValueChange = { viewModel.updateBorderThickness(it) }
                )
            }
        }
    }
}
