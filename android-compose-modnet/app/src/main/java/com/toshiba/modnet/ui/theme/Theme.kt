package com.toshiba.modnet.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF2F6B5A),
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = androidx.compose.ui.graphics.Color(0xFFB9F0DC),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF002117),
    secondary = androidx.compose.ui.graphics.Color(0xFF4B6360),
    onSecondary = androidx.compose.ui.graphics.Color.White,
    background = androidx.compose.ui.graphics.Color(0xFFF7F8F4),
    onBackground = androidx.compose.ui.graphics.Color(0xFF191C1A),
    surface = androidx.compose.ui.graphics.Color(0xFFF7F8F4),
    onSurface = androidx.compose.ui.graphics.Color(0xFF191C1A),
)

private val DarkColors = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF9AD4C0),
    onPrimary = androidx.compose.ui.graphics.Color(0xFF00382B),
    primaryContainer = androidx.compose.ui.graphics.Color(0xFF165143),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFFB9F0DC),
    secondary = androidx.compose.ui.graphics.Color(0xFFB1CCC5),
    onSecondary = androidx.compose.ui.graphics.Color(0xFF1B3532),
    background = androidx.compose.ui.graphics.Color(0xFF111413),
    onBackground = androidx.compose.ui.graphics.Color(0xFFE1E3DE),
    surface = androidx.compose.ui.graphics.Color(0xFF111413),
    onSurface = androidx.compose.ui.graphics.Color(0xFFE1E3DE),
)

@Composable
fun ModNetComposeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = androidx.compose.material3.Typography(),
        content = content,
    )
}
