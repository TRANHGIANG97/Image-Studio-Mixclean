package com.thgiang.image.core.design.theme
import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    // High-tech AI neon (dark mode)
    primary = Color(0xFF44E8FF),
    onPrimary = Color(0xFF001018),
    primaryContainer = Color(0xFF0F2A3B),
    onPrimaryContainer = Color(0xFFC9F7FF),
    secondary = Color(0xFF7E88FF),
    onSecondary = Color(0xFF0A0C25),
    tertiary = Color(0xFF4DFFC7),
    onTertiary = Color(0xFF002116),
    background = Color(0xFF060A14),
    onBackground = Color(0xFFEAF2FF),
    surface = Color(0xFF0E1524),
    onSurface = Color(0xFFEAF2FF),
    surfaceVariant = Color(0xFF1A2336),
    onSurfaceVariant = Color(0xFF9FB1CC),
    error = Color(0xFFFF7A8A)
)

private val LightColorScheme = lightColorScheme(
    // Minimal luxury (light mode)
    primary = Color(0xFF1E1A16),
    onPrimary = Color(0xFFFFFBF5),
    primaryContainer = Color(0xFFF2E8D8),
    onPrimaryContainer = Color(0xFF342A1E),
    secondary = Color(0xFF8A6A3E),
    onSecondary = Color.White,
    tertiary = Color(0xFFC7A46A),
    onTertiary = Color(0xFF2A1C06),
    background = Color(0xFFFCF8F2),
    onBackground = Color(0xFF1B1A18),
    surface = Color(0xFFFFFCF8),
    onSurface = Color(0xFF1D1B19),
    surfaceVariant = Color(0xFFF4EEE3),
    onSurfaceVariant = Color(0xFF6A6156),
    error = Color(0xFFB14A3A)
)

@Composable
fun ImageTheme(
    darkTheme: Boolean,
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val semanticColors = if (darkTheme) {
        ImageSemanticColors(
            success = Color(0xFF43F1B4),
            warning = Color(0xFFFFA862),
            aiAccent = Color(0xFF44E8FF)
        )
    } else {
        ImageSemanticColors(
            success = Color(0xFF2E8B57),
            warning = Color(0xFFC27B2E),
            aiAccent = Color(0xFFB68A4B)
        )
    }
    val surfacePalette = if (darkTheme) {
        ImageSurfacePalette(
            base = Color(0xFF060A14),
            elevated = Color(0xFF0E1524),
            floating = Color(0xFF1A2336),
            glass = Color(0xFF89D9FF).copy(alpha = 0.10f)
        )
    } else {
        ImageSurfacePalette(
            base = Color(0xFFFCF8F2),
            elevated = Color(0xFFFFFCF8),
            floating = Color(0xFFF4EEE3),
            glass = Color.White.copy(alpha = 0.84f)
        )
    }
    val gradients = if (darkTheme) {
        ImageGradients(
            appBackground = Brush.linearGradient(
                colors = listOf(
                    Color(0xFF060A14),
                    Color(0xFF0A1323),
                    Color(0xFF101B31)
                )
            ),
            hero = Brush.linearGradient(
                colors = listOf(
                    Color(0xFF44E8FF).copy(alpha = 0.30f),
                    Color(0xFF7E88FF).copy(alpha = 0.26f),
                    Color(0xFF4DFFC7).copy(alpha = 0.20f)
                )
            ),
            cta = Brush.linearGradient(
                colors = listOf(
                    Color(0xFF44E8FF),
                    Color(0xFF7E88FF)
                )
            )
        )
    } else {
        ImageGradients(
            appBackground = Brush.linearGradient(
                colors = listOf(
                    Color(0xFFFCF8F2),
                    Color(0xFFF6F0E6)
                )
            ),
            hero = Brush.linearGradient(
                colors = listOf(
                    Color(0xFFB68A4B).copy(alpha = 0.22f),
                    Color(0xFFE5D3B3).copy(alpha = 0.42f)
                )
            ),
            cta = Brush.linearGradient(
                colors = listOf(
                    Color(0xFF1E1A16),
                    Color(0xFF8A6A3E)
                )
            )
        )
    }

    CompositionLocalProvider(
        LocalImageSemanticColors provides semanticColors,
        LocalImageSurfacePalette provides surfacePalette,
        LocalImageGradients provides gradients,
        LocalImageRadii provides ImageRadii(medium = 16.dp, large = 24.dp),
        LocalImageMotion provides ImageMotion(quick = 160, medium = 280, slow = 420)
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = Shapes(
                small = RoundedCornerShape(12.dp),
                medium = RoundedCornerShape(16.dp),
                large = RoundedCornerShape(24.dp),
                extraLarge = RoundedCornerShape(24.dp)
            ),
            content = content
        )
    }
}



