package com.abizer_r.quickedit.ui.rotateMode

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import com.abizer_r.quickedit.R
import com.abizer_r.quickedit.theme.QuickEditTheme
import com.abizer_r.quickedit.ui.common.AnimatedToolbarContainer
import com.abizer_r.quickedit.ui.common.bottomToolbarModifier
import com.abizer_r.quickedit.ui.common.topToolbarModifier
import com.abizer_r.quickedit.ui.editorScreen.bottomToolbar.TOOLBAR_HEIGHT_MEDIUM
import com.abizer_r.quickedit.ui.editorScreen.bottomToolbar.TOOLBAR_HEIGHT_SMALL
import com.abizer_r.quickedit.ui.editorScreen.rememberCheckerboardBrush
import com.abizer_r.quickedit.utils.other.anim.AnimUtils
import com.abizer_r.quickedit.utils.other.bitmap.ImmutableBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun RotateModeScreen(
    modifier: Modifier = Modifier,
    immutableBitmap: ImmutableBitmap,
    onDoneClicked: (bitmap: Bitmap) -> Unit,
    onBackPressed: () -> Unit
) {
    val bitmap = immutableBitmap.bitmap

    android.util.Log.d("RotateDebug", "RotateModeScreen composed, bitmap size: ${bitmap.width}x${bitmap.height}, hasAlpha: ${bitmap.hasAlpha()}")

    var rotationDegrees by remember { mutableStateOf(0f) }
    var flippedH by remember { mutableStateOf(false) }
    var flippedV by remember { mutableStateOf(false) }
    var toolbarVisible by remember { mutableStateOf(true) }

    val coroutineScope = rememberCoroutineScope()

    val topToolbarHeight = TOOLBAR_HEIGHT_SMALL
    val bottomToolbarHeight = TOOLBAR_HEIGHT_MEDIUM

    val onCloseClicked = {
        android.util.Log.d("RotateDebug", "onCloseClicked")
        coroutineScope.launch(Dispatchers.Main) {
            toolbarVisible = false
            delay(AnimUtils.TOOLBAR_COLLAPSE_ANIM_DURATION_FAST.toLong())
            android.util.Log.d("RotateDebug", "onCloseClicked: calling onBackPressed")
            onBackPressed()
        }
        Unit
    }

    BackHandler {
        android.util.Log.d("RotateDebug", "BackHandler triggered")
        onCloseClicked()
    }

    RotateModeLayout(
        modifier = modifier,
        bitmap = bitmap,
        rotationDegrees = rotationDegrees,
        flippedH = flippedH,
        flippedV = flippedV,
        toolbarVisible = toolbarVisible,
        topToolbarHeight = topToolbarHeight,
        bottomToolbarHeight = bottomToolbarHeight,
        onRotateLeft = { rotationDegrees = (rotationDegrees - 90f) % 360f; android.util.Log.d("RotateDebug", "Rotated left: ${rotationDegrees}°") },
        onRotateRight = { rotationDegrees = (rotationDegrees + 90f) % 360f; android.util.Log.d("RotateDebug", "Rotated right: ${rotationDegrees}°") },
        onFlipH = { flippedH = !flippedH; android.util.Log.d("RotateDebug", "Flip H: ${flippedH}") },
        onFlipV = { flippedV = !flippedV; android.util.Log.d("RotateDebug", "Flip V: ${flippedV}") },
        onClose = onCloseClicked,
        onDone = {
            android.util.Log.d("RotateDebug", "onDone clicked, rotationDegrees=$rotationDegrees flippedH=$flippedH flippedV=$flippedV")
            coroutineScope.launch(Dispatchers.Main) {
                toolbarVisible = false
                delay(AnimUtils.TOOLBAR_COLLAPSE_ANIM_DURATION_FAST.toLong())
                android.util.Log.d("RotateDebug", "onDone: processing bitmap")
                val result = applyRotateFlip(bitmap, rotationDegrees, flippedH, flippedV)
                android.util.Log.d("RotateDebug", "onDone: result bitmap ${result.width}x${result.height}, calling onDoneClicked")
                onDoneClicked(result)
            }
            Unit
        }
    )
}

@Composable
private fun RotateModeLayout(
    modifier: Modifier,
    bitmap: Bitmap,
    rotationDegrees: Float,
    flippedH: Boolean,
    flippedV: Boolean,
    toolbarVisible: Boolean,
    topToolbarHeight: androidx.compose.ui.unit.Dp,
    bottomToolbarHeight: androidx.compose.ui.unit.Dp,
    onRotateLeft: () -> Unit,
    onRotateRight: () -> Unit,
    onFlipH: () -> Unit,
    onFlipV: () -> Unit,
    onClose: () -> Unit,
    onDone: () -> Unit
) {
    val checkerboardBrush = rememberCheckerboardBrush()
    val displayBitmap = bitmap.asImageBitmap()

    ConstraintLayout(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val (topToolbar, bottomToolbar, bgImage) = createRefs()

        // Top toolbar
        AnimatedToolbarContainer(
            toolbarVisible = toolbarVisible,
            modifier = topToolbarModifier(topToolbar).statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(topToolbarHeight)
                    .background(MaterialTheme.colorScheme.surface),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .size(32.dp)
                        .clickable { onClose() },
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(android.R.string.cancel),
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground)
                )

                Text(
                    text = stringResource(R.string.rotate),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Text(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .clickable { onDone() },
                    text = stringResource(android.R.string.ok),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Image preview
        val aspectRatio = remember(bitmap, rotationDegrees, flippedH, flippedV) {
            val swapped = (rotationDegrees.toInt() / 90) % 2 != 0
            val w = if (swapped) bitmap.height.toFloat() else bitmap.width.toFloat()
            val h = if (swapped) bitmap.width.toFloat() else bitmap.height.toFloat()
            w / h
        }

        Box(
            modifier = Modifier
                .constrainAs(bgImage) {
                    top.linkTo(parent.top)
                    bottom.linkTo(parent.bottom)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                    width = Dimension.wrapContent
                    height = Dimension.wrapContent
                }
                .padding(top = topToolbarHeight, bottom = bottomToolbarHeight)
                .aspectRatio(aspectRatio)
                .then(
                    if (bitmap.hasAlpha()) Modifier.background(checkerboardBrush)
                    else Modifier
                )
        ) {
            Image(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        rotationZ = rotationDegrees,
                        scaleX = if (flippedH) -1f else 1f,
                        scaleY = if (flippedV) -1f else 1f
                    ),
                bitmap = displayBitmap,
                contentScale = ContentScale.Fit,
                contentDescription = null
            )
        }

        // Bottom toolbar
        AnimatedToolbarContainer(
            toolbarVisible = toolbarVisible,
            modifier = bottomToolbarModifier(bottomToolbar)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(bottomToolbarHeight)
                    .background(MaterialTheme.colorScheme.surface)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                RotateActionButton(
                    icon = ImageVector.vectorResource(id = R.drawable.ic_rotate_left),
                    label = stringResource(R.string.rotate_left),
                    onClick = onRotateLeft
                )
                RotateActionButton(
                    icon = ImageVector.vectorResource(id = R.drawable.ic_rotate_right),
                    label = stringResource(R.string.rotate_right),
                    onClick = onRotateRight
                )
                RotateActionButton(
                    icon = ImageVector.vectorResource(id = R.drawable.ic_flip_horizontal),
                    label = stringResource(R.string.flip_horizontal),
                    onClick = onFlipH
                )
                RotateActionButton(
                    icon = ImageVector.vectorResource(id = R.drawable.ic_flip_vertical),
                    label = stringResource(R.string.flip_vertical),
                    onClick = onFlipV
                )
            }
        }
    }
}

@Composable
private fun RotateActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            modifier = Modifier
                .size(57.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClick),
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.size(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            ),
            maxLines = 1
        )
    }
}

private suspend fun applyRotateFlip(
    bitmap: Bitmap,
    rotationDegrees: Float,
    flippedH: Boolean,
    flippedV: Boolean
): Bitmap = withContext(Dispatchers.IO) {
    val matrix = android.graphics.Matrix()

    val cx = bitmap.width / 2f
    val cy = bitmap.height / 2f

    if (flippedH) matrix.preScale(-1f, 1f, cx, cy)
    if (flippedV) matrix.preScale(1f, -1f, cx, cy)

    if (rotationDegrees != 0f) {
        matrix.postRotate(rotationDegrees, cx, cy)
    }

    val rotated = android.graphics.Bitmap.createBitmap(
        bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
    )
    if (rotated !== bitmap && !bitmap.isRecycled) bitmap.recycle()
    rotated
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun PreviewRotateModeScreen() {
    QuickEditTheme {
        RotateModeLayout(
            modifier = Modifier,
            bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply {
                val canvas = android.graphics.Canvas(this)
                canvas.drawColor(android.graphics.Color.rgb(200, 200, 200))
            },
            rotationDegrees = 0f,
            flippedH = false,
            flippedV = false,
            toolbarVisible = true,
            topToolbarHeight = TOOLBAR_HEIGHT_SMALL,
            bottomToolbarHeight = TOOLBAR_HEIGHT_MEDIUM,
            onRotateLeft = {},
            onRotateRight = {},
            onFlipH = {},
            onFlipV = {},
            onClose = {},
            onDone = {}
        )
    }
}
