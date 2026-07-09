package com.thgiang.image.studio.ui.editor.theme

import com.thgiang.image.studio.ui.editor.model.*

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ── Premium Clean Light — Design Palette ─────────────────────────────────
/**
 * Centralized color palette for studio_edit.
 * Philosophy: Canva / Photoroom / CapCut-style "Premium Clean Light".
 * Background: #EBEBEB, Surfaces: #FFFFFF, Accent: #2563EB
 */
object EditorColorPalette {
    // ── Module Surfaces ─────────────────────────────────
    val ModuleBackground  = Color(0xFFEBEBEB)   // workspace bg — xám sáng nhạt
    val CanvasArea        = Color(0xFFEBEBEB)
    val WorkspaceBackground = Color(0xFFEBEBEB)
    val PanelSurface      = Color(0xFFFFFFFF)   // panel / sheet trắng tinh
    val PanelElevated     = Color(0xFFF8F8F8)   // row / card nền nhạt hơn
    val GlassOverlay      = Color(0xFFFFFFFF)   // top-bar / toolbar — trắng đặc
    val Artboard          = Color(0xFFFFFFFF)   // artboard card bao template

    // ── Accent ──────────────────────────────────────────
    val AccentBlue   = Color(0xFF2563EB)         // xanh hiện đại
    val AccentSoft   = Color(0x1A2563EB)         // 10% accent — pill selected bg
    val AccentCyan   = Color(0xFF1473E6)         // tương thích cũ
    val AccentPurple = Color(0xFF7C3AED)
    val Destructive  = Color(0xFFEF4444)

    // ── Text ────────────────────────────────────────────
    val TextPrimary   = Color(0xFF161616)
    val TextSecondary = Color(0xFF6B7280)
    val TextMuted     = Color(0xFF9CA3AF)
    val TextDisabled  = Color(0xFF9CA3AF)

    // ── Borders ─────────────────────────────────────────
    val BorderSubtle  = Color(0xFFE0E0E0)
    val BorderDefault = Color(0xFFD1D5DB)
    val BorderStrong  = Color(0xFFB0B7C3)

    // ── QuickEdit compatibility — old names → new values ─
    val EditorAccent        = AccentBlue
    val EditorAccentVariant = AccentPurple
    val BackgroundColor_Dark         = Color(0xFF0B0F19)
    val ColorOnBackground_Dark       = TextPrimary
    val ToolBarBackgroundColor_Dark  = Color(0xFF111827)
    val BackgroundColor_Light        = Color(0xFFFDFDFD)
    val ColorOnBackground_Light      = Color(0xFF1A1A1A)
    val ToolBarBackgroundColor_Light = Color(0xFFFFFFFF)
    val DarkPanel  = PanelSurface
    val DarkerGray = CanvasArea

    // ── Tool-specific ───────────────────────────────────
    val ToolIconActive   = AccentBlue
    val ToolIconInactive = TextSecondary
    val ToolBadge        = Destructive
    val SliderTrack      = Color(0xFFEEEEEE)
    val SliderTrackActive = AccentBlue
    val SliderThumb      = Color(0xFFFFFFFF)

    // ── Canvas ──────────────────────────────────────────
    val CanvasGridLine     = Color(0xFFDCDCDC)
    val CanvasGuideLine    = AccentBlue.copy(alpha = 0.4f)
    val CanvasCheckerLight = Color(0xFFF2F2F2)
    val CanvasCheckerDark  = Color(0xFFE1E1E1)

    // ── Layer Panel ─────────────────────────────────────
    val LayerRowBackground = Color(0xFFF4F8FF)
    val LayerRowSelected   = AccentBlue.copy(alpha = 0.10f)
    val LayerDragHandle    = TextSecondary

    // ── Histogram ───────────────────────────────────────
    val HistogramRed       = Color(0xFFEF4444)
    val HistogramGreen     = Color(0xFF22C55E)
    val HistogramBlue      = Color(0xFF3B82F6)
    val HistogramLuminance = Color(0xFF9FB1CC)
}

// ── Motion Tokens — Canva-grade physics-based animation specs ─────────────
/**
 * Centralized animation specs for studio_edit.
 * Philosophy: springs for anything that moves or resizes (translate / scale /
 * expand); short tweens ONLY for pure opacity fades — opacity has no mass.
 *
 * The spring factories are generic because AnimatedVisibility transitions are
 * typed (FiniteAnimationSpec<IntOffset> for slides, <IntSize> for expand/shrink,
 * <Float> for scale) — the type parameter is inferred at the call site.
 */
object MotionTokens {
    /** Panels sliding/expanding in and out — soft, near-critically damped. */
    fun <T> springPanel(): SpringSpec<T> = spring(
        dampingRatio = 0.85f,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** Selection scale, chips, tab indicators — playful overshoot. */
    fun <T> springEmphasized(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium,
    )

    /** Release / settle after gesture — fast with a slight bounce. */
    fun <T> springSettle(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessHigh,
    )

    /** Opacity-only fades — physics not needed. */
    val fadeQuick: TweenSpec<Float> = tween(durationMillis = 120)
    val fadeDefault: TweenSpec<Float> = tween(durationMillis = 180)
}

// ── Editor Design Tokens ──────────────────────────────────────────────────
@Immutable
data class EditorTokens(
    // ── Surfaces ────────────────────────────────────────
    val moduleBackground: Color = EditorColorPalette.ModuleBackground,
    val canvasBackground: Color = EditorColorPalette.CanvasArea,
    val artboard: Color        = EditorColorPalette.Artboard,
    val surfaceBase: Color     = EditorColorPalette.PanelSurface,
    val surfaceElevated: Color = EditorColorPalette.PanelElevated,
    val surfaceFloating: Color = EditorColorPalette.PanelSurface,
    val glassBackground: Color = EditorColorPalette.GlassOverlay,

    // ── Accent ──────────────────────────────────────────
    val accent: Color        = EditorColorPalette.AccentBlue,
    val accentSoft: Color    = EditorColorPalette.AccentSoft,
    val accentVariant: Color = EditorColorPalette.AccentPurple,
    val destructive: Color   = EditorColorPalette.Destructive,

    // ── Text ────────────────────────────────────────────
    val textPrimary: Color   = EditorColorPalette.TextPrimary,
    val textSecondary: Color = EditorColorPalette.TextSecondary,
    val textMuted: Color     = EditorColorPalette.TextMuted,
    val textDisabled: Color  = EditorColorPalette.TextDisabled,

    // ── Borders ─────────────────────────────────────────
    val borderSubtle: Color  = EditorColorPalette.BorderSubtle,
    val borderDefault: Color = EditorColorPalette.BorderDefault,
    val borderStrong: Color  = EditorColorPalette.BorderStrong,

    // ── Canvas ──────────────────────────────────────────
    val canvasGridLine: Color     = EditorColorPalette.CanvasGridLine,
    val canvasGuideLine: Color    = EditorColorPalette.CanvasGuideLine,
    val canvasCheckerLight: Color = EditorColorPalette.CanvasCheckerLight,
    val canvasCheckerDark: Color  = EditorColorPalette.CanvasCheckerDark,

    // ── Panels ──────────────────────────────────────────
    val panelMinWidth: Dp    = 280.dp,
    val panelMaxWidth: Dp    = 320.dp,
    val panelHeaderHeight: Dp = 48.dp,

    // ── Tool icons ──────────────────────────────────────
    val toolIconSize: Dp          = 24.dp,
    val toolIconActiveTint: Color = EditorColorPalette.ToolIconActive,
    val toolIconInactiveAlpha: Float = 0.55f,

    // ── Sliders ─────────────────────────────────────────
    val sliderTrackHeight: Dp  = 4.dp,
    val sliderThumbRadius: Dp  = 8.dp,
    val sliderThumbColor: Color = EditorColorPalette.SliderThumb,

    // ── Layer panel ─────────────────────────────────────
    val layerRowHeight: Dp    = 56.dp,
    val layerThumbnailSize: Dp = 48.dp,

    // ── Histogram ───────────────────────────────────────
    val histogramHeight: Dp = 64.dp,

    // ── Shape corners ───────────────────────────────────
    val cornerSmall: Dp  = 8.dp,
    val cornerMedium: Dp = 12.dp,
    val cornerLarge: Dp  = 16.dp,
    val cornerXLarge: Dp = 24.dp,

    // ── Motion durations (ms) ───────────────────────────
    val durationQuick: Int   = 120,
    val durationDefault: Int = 200,
    val durationSlow: Int    = 350,

    // ── Spacing (4dp grid) ──────────────────────────────
    val spacing2x: Dp  = 2.dp,
    val spacing4x: Dp  = 4.dp,
    val spacing8x: Dp  = 8.dp,
    val spacing12x: Dp = 12.dp,
    val spacing16x: Dp = 16.dp,
    val spacing24x: Dp = 24.dp,
    val spacing32x: Dp = 32.dp,
)

val LocalEditorTokens = staticCompositionLocalOf { EditorTokens() }
