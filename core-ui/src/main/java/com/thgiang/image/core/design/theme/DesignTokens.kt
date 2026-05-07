package com.thgiang.image.core.design.theme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class ImageSemanticColors(
    val success: Color,
    val warning: Color,
    val aiAccent: Color
)

@Immutable
data class ImageSurfacePalette(
    val base: Color,
    val elevated: Color,
    val floating: Color,
    val glass: Color
)

@Immutable
data class ImageGradients(
    val appBackground: Brush,
    val hero: Brush,
    val cta: Brush
)

@Immutable
data class ImageRadii(
    val medium: Dp,
    val large: Dp
)

@Immutable
data class ImageMotion(
    val quick: Int,
    val medium: Int,
    val slow: Int
)

internal val LocalImageSemanticColors = staticCompositionLocalOf {
    ImageSemanticColors(
        success = SuccessColor,
        warning = WarningColor,
        aiAccent = AiAccentColor
    )
}

internal val LocalImageSurfacePalette = staticCompositionLocalOf {
    ImageSurfacePalette(
        base = Night900,
        elevated = Night800,
        floating = Night700,
        glass = Color.White.copy(alpha = 0.08f)
    )
}

internal val LocalImageGradients = staticCompositionLocalOf {
    ImageGradients(
        appBackground = Brush.linearGradient(
            colors = listOf(Night900, Night800)
        ),
        hero = Brush.linearGradient(
            colors = listOf(AuroraBlue.copy(alpha = 0.35f), AuroraViolet.copy(alpha = 0.25f))
        ),
        cta = Brush.linearGradient(
            colors = listOf(AuroraBlue, AuroraViolet)
        )
    )
}

internal val LocalImageRadii = staticCompositionLocalOf {
    ImageRadii(
        medium = 16.dp,
        large = 24.dp
    )
}

internal val LocalImageMotion = staticCompositionLocalOf {
    ImageMotion(
        quick = 160,
        medium = 280,
        slow = 420
    )
}

/**
 * Bảng màu dark mode chỉ dùng cho trang chủ (phong cách từ thiết kế).
 * Không thay đổi theme toàn app.
 */
object HomeDarkStyle {
    val background = Color(0xFF1E1E1E)
    val surfaceElevated = Color(0xFF333333)
    val surfaceButton = Color(0xFF2C2C2C)
    val textPrimary = Color.White
    val textSecondary = Color.White.copy(alpha = 0.9f)
    val accent = Color(0xFF26C6DA)
    val border = Color.White.copy(alpha = 0.06f)
    val borderStrong = Color.White.copy(alpha = 0.08f)
    val proBadgeBg = Color(0xFF26C6DA).copy(alpha = 0.2f)
}

object ImageDesign {
    @Composable
    private fun isDarkMode(): Boolean {
        return MaterialTheme.colorScheme.background.luminance() < 0.5f
    }

    val semantic: ImageSemanticColors
        @Composable get() {
            val cs = MaterialTheme.colorScheme
            val dark = isDarkMode()
            return if (dark) {
                ImageSemanticColors(
                    success = SuccessColor,
                    warning = WarningColor,
                    aiAccent = cs.primary
                )
            } else {
                // Keep light mode semantic tokens as before.
                LocalImageSemanticColors.current
            }
        }

    val surfaces: ImageSurfacePalette
        @Composable get() {
            val dark = isDarkMode()
            return if (dark) {
                ImageSurfacePalette(
                    base = Color(0xFF0B0F19),
                    elevated = Color(0xFF111827),
                    floating = Color(0xFF1F2937),
                    glass = Color(0xFF101828).copy(alpha = 0.85f)
                )
            } else {
                // Keep light mode surfaces as before.
                LocalImageSurfacePalette.current
            }
        }

    val gradients: ImageGradients
        @Composable get() {
            val dark = isDarkMode()
            val aiAccent = MaterialTheme.colorScheme.primary
            return if (dark) {
                ImageGradients(
                    appBackground = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF040915),
                            Color(0xFF0B1326),
                            Color(0xFF111A33)
                        )
                    ),
                    hero = Brush.linearGradient(
                        colors = listOf(
                            aiAccent.copy(alpha = 0.30f),
                            Color(0xFF7C3AED).copy(alpha = 0.20f)
                        )
                    ),
                    cta = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF22D3EE),
                            Color(0xFF7C3AED)
                        )
                    )
                )
            } else {
                // Keep light mode gradients as before.
                LocalImageGradients.current
            }
        }

    val radii: ImageRadii
        @Composable get() = LocalImageRadii.current

    val motion: ImageMotion
        @Composable get() = LocalImageMotion.current
}




