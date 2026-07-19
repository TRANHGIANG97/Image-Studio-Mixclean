package com.thgiang.image.studio.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Loading indicator only — no opaque scrim so surrounding UI stays visible.
 * Optionally blocks pointer events on the covered area.
 */
@Composable
fun StudioLoadingOverlay(
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    blockTouches: Boolean = true,
) {
    Box(
        modifier = modifier.then(
            if (blockTouches) {
                Modifier.pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
            } else {
                Modifier
            },
        ),
        contentAlignment = Alignment.Center,
    ) {
        StudioLottieLoader(modifier = Modifier.size(size))
    }
}
