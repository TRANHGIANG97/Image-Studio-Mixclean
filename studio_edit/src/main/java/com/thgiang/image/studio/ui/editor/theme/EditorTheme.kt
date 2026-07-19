package com.thgiang.image.studio.ui.editor.theme

import com.thgiang.image.studio.ui.editor.model.*

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.thgiang.image.core.design.theme.ImageTheme

@Composable
fun EditorTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context.findActivity() ?: return@SideEffect
            val window = activity.window
            WindowCompat.setDecorFitsSystemWindows(window, false)

            // Show status bar and navigation bar (keep them visible)
            val controller = WindowCompat.getInsetsController(window, view)
            controller.show(WindowInsetsCompat.Type.systemBars())

            // Light theme — status bar and navigation bar use dark icons/buttons
            controller.isAppearanceLightStatusBars = true
            controller.isAppearanceLightNavigationBars = true

            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()

            if (Build.VERSION.SDK_INT >= 29) {
                window.isNavigationBarContrastEnforced = false
            }
        }
    }

    ImageTheme(darkTheme = darkTheme) {
        val tokens = EditorTokens()
        CompositionLocalProvider(
            LocalEditorTokens provides tokens
        ) {
            MaterialTheme(
                colorScheme = MaterialTheme.colorScheme,
                shapes = MaterialTheme.shapes.copy(
                    small = RoundedCornerShape(tokens.cornerSmall),
                    medium = RoundedCornerShape(tokens.cornerMedium),
                    large = RoundedCornerShape(tokens.cornerLarge),
                    extraLarge = RoundedCornerShape(tokens.cornerXLarge)
                ),
                typography = MaterialTheme.typography,
                content = content
            )
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
