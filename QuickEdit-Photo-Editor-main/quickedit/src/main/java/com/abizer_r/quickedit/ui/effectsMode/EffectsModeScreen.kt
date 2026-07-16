@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.abizer_r.quickedit.ui.effectsMode

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.*
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abizer_r.quickedit.R
import com.thgiang.image.studio.ui.editor.theme.EditorTheme
// ToolBarBackgroundColor removed from imports
import com.abizer_r.quickedit.utils.defaultErrorToast
import com.abizer_r.quickedit.ui.common.AnimatedToolbarContainer
import com.abizer_r.quickedit.ui.common.LoadingView
import com.abizer_r.quickedit.ui.common.StablePreviewStage
import com.abizer_r.quickedit.ui.common.bottomToolbarModifier
import com.abizer_r.quickedit.ui.common.topToolbarModifier
import com.abizer_r.quickedit.ui.editorScreen.bottomToolbar.TOOLBAR_HEIGHT_EXTRA_LARGE
import com.abizer_r.quickedit.ui.editorScreen.bottomToolbar.TOOLBAR_HEIGHT_SMALL
import com.abizer_r.quickedit.ui.editorScreen.topToolbar.TextModeTopToolbar
import com.abizer_r.quickedit.ui.effectsMode.effectsPreview.EffectItem
import com.abizer_r.quickedit.ui.effectsMode.effectsPreview.EffectsPreviewListFullWidth
import com.abizer_r.quickedit.utils.effectsMode.EffectsModeUtils
import com.abizer_r.quickedit.utils.other.anim.AnimUtils
import com.abizer_r.quickedit.utils.other.bitmap.ImmutableBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun EffectsModeScreen(
    modifier: Modifier = Modifier,
    immutableBitmap: ImmutableBitmap,
    onDoneClicked: (bitmap: Bitmap) -> Unit,
    onBackPressed: () -> Unit
) {
    val context = LocalContext.current
    val lifeCycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    val viewModel: EffectsModeViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle(
        lifecycleOwner = lifeCycleOwner
    )

    val bitmap = immutableBitmap.bitmap
    val currentBitmap = state.filteredBitmap ?: bitmap

    val topToolbarHeight =  TOOLBAR_HEIGHT_SMALL
    val bottomToolbarHeight = TOOLBAR_HEIGHT_EXTRA_LARGE

    var toolbarVisible by remember { mutableStateOf(false) }

    LaunchedEffect(key1 = bitmap) {
        withContext(Dispatchers.IO) {
            toolbarVisible = true
            delay(AnimUtils.TOOLBAR_EXPAND_ANIM_DURATION_FAST.toLong())
            EffectsModeUtils.getEffectsPreviewList(context, bitmap).onEach {
                viewModel.addToEffectList(
                    effectItems = it,
                    selectInitialBitmap = state.effectsList.isEmpty(),
                )
            }.collect()

        }
    }

    val onCloseClickedLambda = remember<() -> Unit> { {
        coroutineScope.launch(Dispatchers.Main) {
            toolbarVisible = false
            delay(AnimUtils.TOOLBAR_COLLAPSE_ANIM_DURATION_FAST.toLong())
            onBackPressed()
        }
    }}

    BackHandler {
        onCloseClickedLambda()
    }

    val onDoneClickedLambda = remember<() -> Unit>(state.selectedEffectIndex, state.effectsList.size) {
        {
            coroutineScope.launch(Dispatchers.Main) {
                toolbarVisible = false
                delay(AnimUtils.TOOLBAR_COLLAPSE_ANIM_DURATION_FAST.toLong())
                val recipe = viewModel.selectedRecipe()
                val fullRes = runCatching {
                    withContext(Dispatchers.Default) {
                        EffectsModeUtils.applyFullResolution(context, bitmap, recipe)
                    }
                }.getOrElse { bitmap }
                onDoneClicked(fullRes)
            }
            Unit
        }
    }

    val onEffectItemClicked = remember<(Int, EffectItem) -> Unit> {{ index, effectItem ->
        viewModel.selectEffect(index)
    }}


    ConstraintLayout(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .imePadding()
    ) {
        val (topToolBar, imageBox, effectsPreviewList, navBarZone) = createRefs()

        // Inviolable Zone for System Navigation Keys
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                .constrainAs(navBarZone) {
                    bottom.linkTo(parent.bottom)
                    width = Dimension.matchParent
                    height = Dimension.wrapContent
                }
                .navigationBarsPadding()
        )

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
        val aspectRatio = bitmap.let {
            bitmap.width.toFloat() / bitmap.height.toFloat()
        }
        StablePreviewStage(
            modifier = Modifier
                .constrainAs(imageBox) {
                    top.linkTo(topToolBar.bottom)
                    bottom.linkTo(effectsPreviewList.top)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                    width = Dimension.fillToConstraints
                    height = Dimension.fillToConstraints
                }
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            aspectRatio = aspectRatio
        ) {
            Image(
                modifier = Modifier.fillMaxSize(),
                bitmap = currentBitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit
            )
        }


        AnimatedToolbarContainer(
            toolbarVisible = toolbarVisible,
            modifier = Modifier.constrainAs(effectsPreviewList) {
                bottom.linkTo(navBarZone.top)
                width = Dimension.matchParent
                height = Dimension.wrapContent
            }
        ) {
            if (state.effectsList.isEmpty()) {
                LoadingView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(bottomToolbarHeight)
                        .background(MaterialTheme.colorScheme.surface),
                    progressBarSize = 36.dp,
                    progressBarStrokeWidth = 3.dp
                )
            } else {
                EffectsPreviewListFullWidth(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surface)
//                        .padding(vertical = 12.dp)
                    ,
                    toolbarHeight = bottomToolbarHeight,
                    effectsList = state.effectsList,
                    selectedIndex = state.selectedEffectIndex,
                    onItemClicked = onEffectItemClicked
                )
            }
        }

    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun Preview_EffectsModeScreen() {
    EditorTheme {
        EffectsModeScreen(
            immutableBitmap = ImmutableBitmap(
                ImageBitmap.imageResource(id = R.drawable.placeholder_image_3).asAndroidBitmap()
            ),
            onDoneClicked = {},
            onBackPressed = {}
        )
    }
}
