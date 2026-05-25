package com.thgiang.image.core.design.adaptive

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier

/**
 * Adaptive shell that selects the layout strategy for the current window size.
 *
 * - **Mobile** (Compact width): vertical stack — topBar → canvas → toolPalette.
 *   Property panel slides up from bottom.
 * - **Tablet** (Medium width): topBar → Row(toolPalette left, canvas center).
 *   Property panel slides in from right.
 * - **Desktop** (Expanded width): topBar → Row(toolPalette left, canvas center, propertyPanel right).
 *   Persistent side panels.
 */
@Composable
fun AdaptiveEditorScaffold(
    windowSizeClass: WindowSizeClass,
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    toolPalette: @Composable () -> Unit = {},
    propertyPanel: @Composable () -> Unit = {},
    showPropertyPanel: Boolean = false,
    content: @Composable () -> Unit
) {
    val layoutMode = windowLayoutMode(windowSizeClass)

    CompositionLocalProvider(
        LocalWindowSizeClass provides windowSizeClass,
        LocalEditorLayoutMode provides layoutMode
    ) {
        when (layoutMode) {
            EditorLayoutMode.Mobile -> {
                MobileLayout(
                    modifier = modifier,
                    topBar = topBar,
                    toolPalette = toolPalette,
                    propertyPanel = propertyPanel,
                    showPropertyPanel = showPropertyPanel,
                    content = content
                )
            }
            EditorLayoutMode.Tablet -> {
                TabletLayout(
                    modifier = modifier,
                    topBar = topBar,
                    toolPalette = toolPalette,
                    propertyPanel = propertyPanel,
                    showPropertyPanel = showPropertyPanel,
                    content = content
                )
            }
            EditorLayoutMode.Desktop -> {
                DesktopLayout(
                    modifier = modifier,
                    topBar = topBar,
                    toolPalette = toolPalette,
                    propertyPanel = propertyPanel,
                    showPropertyPanel = showPropertyPanel,
                    content = content
                )
            }
        }
    }
}

@Composable
private fun MobileLayout(
    modifier: Modifier,
    topBar: @Composable () -> Unit,
    toolPalette: @Composable () -> Unit,
    propertyPanel: @Composable () -> Unit,
    showPropertyPanel: Boolean,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            topBar()
            Box(modifier = Modifier.weight(1f)) {
                content()
            }
            toolPalette()
        }

        AnimatedVisibility(
            visible = showPropertyPanel,
            modifier = Modifier.fillMaxSize(),
            enter = slideInVertically(tween(250)) { it } + fadeIn(tween(250)),
            exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(200))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    topBar()
                    Box(modifier = Modifier.weight(1f)) {
                        content()
                    }
                    propertyPanel()
                }
            }
        }
    }
}

@Composable
private fun TabletLayout(
    modifier: Modifier,
    topBar: @Composable () -> Unit,
    toolPalette: @Composable () -> Unit,
    propertyPanel: @Composable () -> Unit,
    showPropertyPanel: Boolean,
    content: @Composable () -> Unit
) {
    Column(modifier = modifier.fillMaxSize()) {
        topBar()
        Row(modifier = Modifier.weight(1f)) {
            toolPalette()
            Box(modifier = Modifier.weight(1f)) {
                content()
            }
            AnimatedVisibility(
                visible = showPropertyPanel,
                enter = slideInHorizontally(tween(250)) { it } + fadeIn(tween(250)),
                exit = slideOutHorizontally(tween(200)) { it } + fadeOut(tween(200))
            ) {
                propertyPanel()
            }
        }
    }
}

@Composable
private fun DesktopLayout(
    modifier: Modifier,
    topBar: @Composable () -> Unit,
    toolPalette: @Composable () -> Unit,
    propertyPanel: @Composable () -> Unit,
    showPropertyPanel: Boolean,
    content: @Composable () -> Unit
) {
    Column(modifier = modifier.fillMaxSize()) {
        topBar()
        Row(modifier = Modifier.weight(1f)) {
            toolPalette()
            Box(modifier = Modifier.weight(1f)) {
                content()
            }
            Box(modifier = Modifier.fillMaxHeight()) {
                propertyPanel()
            }
        }
    }
}
