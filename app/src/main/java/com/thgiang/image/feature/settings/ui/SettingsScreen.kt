package com.thgiang.image.feature.settings.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.thgiang.image.R
import com.thgiang.image.core.design.components.GlassSurface
import com.thgiang.image.core.design.components.ShimmerGradientButton
import com.thgiang.image.core.design.components.StatusChip
import com.thgiang.image.core.design.theme.ImageDesign
import android.content.res.Resources
import android.os.Build
import java.util.Locale

// ── Entry point ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    isDarkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    selectedLanguage: String,
    onLanguageChange: (String) -> Unit,
    preferredRemovalQuality: String = "standard",
    onPreferredRemovalQualityChange: (String) -> Unit = {},
    isHomePreviewEnabled: Boolean = false,
    onHomePreviewEnabledChange: (Boolean) -> Unit = {},
    isPremium: Boolean = false,
    onOpenPro: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val aiAccent = ImageDesign.semantic.aiAccent
    val surfaceColor = MaterialTheme.colorScheme.onSurface
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant

    var languageSheetOpen by remember { mutableStateOf(false) }
    var licensesDialogOpen by remember { mutableStateOf(false) }

    val selectedLanguageLabel = remember(selectedLanguage) {
        languageLabel(selectedLanguage, context)
    }

    // Staggered entrance for sections
    var sectionsVisible by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        repeat(5) { i ->
            sectionsVisible = i + 1
            kotlinx.coroutines.delay(60)
        }
    }

    val currentDensity = androidx.compose.ui.platform.LocalDensity.current
    val scaledDensity = remember(currentDensity) {
        androidx.compose.ui.unit.Density(
            density = currentDensity.density * 0.8f,
            fontScale = currentDensity.fontScale * 0.8f
        )
    }

    CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides scaledDensity) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(ImageDesign.gradients.appBackground)
                .verticalScroll(rememberScrollState())
        ) {
            // Responsive horizontal padding
            val horizontalPadding = 16.dp
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding)
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // ── Header ────────────────────────────────────────────────
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                color = surfaceColor
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusChip(
                    text = stringResource(R.string.settings_app_preferences),
                    tone = aiAccent
                )
                if (isPremium) {
                    Spacer(modifier = Modifier.width(8.dp))
                    StatusChip(
                        text = "PRO",
                        tone = ImageDesign.semantic.warning
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ── Section 1: Appearance ─────────────────────────────────
            AnimatedVisibility(
                visible = sectionsVisible >= 1,
                enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 4 }
            ) {
                Column {
                    SettingsGroup(title = stringResource(R.string.settings_section_appearance)) {


                        SettingsItemRow(
                            icon = Icons.Rounded.Language,
                            iconTint = aiAccent,
                            iconBackground = aiAccent.copy(alpha = 0.12f),
                            title = stringResource(R.string.language_title),
                            subtitle = stringResource(R.string.language_description),
                            onClick = { languageSheetOpen = true }
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = selectedLanguageLabel,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = mutedColor
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Icon(
                                    Icons.Rounded.ChevronRight,
                                    contentDescription = null,
                                    tint = mutedColor.copy(alpha = 0.5f)
                                )
                            }
                        }

                        Divider(
                            modifier = Modifier.padding(start = 72.dp),
                            color = surfaceColor.copy(alpha = 0.06f)
                        )

                        SettingsItemRow(
                            icon = Icons.Rounded.AutoAwesome,
                            iconTint = aiAccent,
                            iconBackground = aiAccent.copy(alpha = 0.12f),
                            title = stringResource(R.string.home_preview_title),
                            subtitle = stringResource(R.string.home_preview_description),
                            onClick = { onHomePreviewEnabledChange(!isHomePreviewEnabled) }
                        ) {
                            Switch(
                                checked = isHomePreviewEnabled,
                                onCheckedChange = onHomePreviewEnabledChange,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = aiAccent,
                                    checkedTrackColor = aiAccent.copy(alpha = 0.3f),
                                    uncheckedThumbColor = mutedColor,
                                    uncheckedTrackColor = mutedColor.copy(alpha = 0.12f)
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            // ── Section 2: AI Engine ──────────────────────────────────
            AnimatedVisibility(
                visible = sectionsVisible >= 2,
                enter = fadeIn(tween(300, delayMillis = 50)) + slideInVertically(tween(300, delayMillis = 50)) { it / 4 }
            ) {
                Column {
                    SettingsGroup(title = stringResource(R.string.settings_section_ai)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(ImageDesign.semantic.warning.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Rounded.AutoAwesome,
                                        contentDescription = null,
                                        tint = ImageDesign.semantic.warning
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.settings_removal_quality),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = surfaceColor
                                    )
                                    Text(
                                        text = stringResource(R.string.settings_removal_quality_summary),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = mutedColor
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Custom animated segmented control
                            SegmentedQualityControl(
                                selected = preferredRemovalQuality,
                                onSelect = onPreferredRemovalQualityChange,
                                isPremium = isPremium,
                                accentColor = aiAccent
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            // ── Section 3: About ──────────────────────────────────────
            AnimatedVisibility(
                visible = sectionsVisible >= 3,
                enter = fadeIn(tween(300, delayMillis = 100)) + slideInVertically(tween(300, delayMillis = 100)) { it / 4 }
            ) {
                Column {
                    SettingsGroup(title = stringResource(R.string.settings_section_about)) {
                        SettingsItemRow(
                            icon = Icons.Rounded.Info,
                            iconTint = aiAccent,
                            iconBackground = aiAccent.copy(alpha = 0.12f),
                            title = stringResource(R.string.licenses_title),
                            subtitle = stringResource(R.string.settings_app_version_label, "v1.0.0"),
                            onClick = { licensesDialogOpen = true }
                        ) {
                            Icon(
                                Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = mutedColor.copy(alpha = 0.5f)
                            )
                        }

                        Divider(
                            modifier = Modifier.padding(start = 72.dp),
                            color = surfaceColor.copy(alpha = 0.06f)
                        )

                        SettingsItemRow(
                            icon = Icons.Rounded.Security,
                            iconTint = aiAccent,
                            iconBackground = aiAccent.copy(alpha = 0.12f),
                            title = "Privacy Policy",
                            subtitle = null,
                            onClick = { /* TODO: open privacy URL */ }
                        ) {
                            Icon(
                                Icons.Rounded.OpenInNew,
                                contentDescription = null,
                                tint = mutedColor.copy(alpha = 0.4f),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Divider(
                            modifier = Modifier.padding(start = 72.dp),
                            color = surfaceColor.copy(alpha = 0.06f)
                        )

                        SettingsItemRow(
                            icon = Icons.Rounded.Description,
                            iconTint = aiAccent,
                            iconBackground = aiAccent.copy(alpha = 0.12f),
                            title = "Terms of Service",
                            subtitle = null,
                            onClick = { /* TODO: open terms URL */ }
                        ) {
                            Icon(
                                Icons.Rounded.OpenInNew,
                                contentDescription = null,
                                tint = mutedColor.copy(alpha = 0.4f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }



            // ── Footer padding ────────────────────────────────────────
            Spacer(modifier = Modifier.height(56.dp))
        }
    }
}

    // ── Language Bottom Sheet ─────────────────────────────────────────
    if (languageSheetOpen) {
        LanguageBottomSheet(
            currentLanguage = selectedLanguage,
            currentLabel = selectedLanguageLabel,
            onLanguageSelected = { code ->
                languageSheetOpen = false
                onLanguageChange(code)
            },
            onDismiss = { languageSheetOpen = false }
        )
    }

    // ── Licenses Dialog ───────────────────────────────────────────────
    if (licensesDialogOpen) {
        val noticeText = remember {
            try {
                context.assets.open("NOTICE").bufferedReader().use { it.readText() }
            } catch (_: Exception) {
                ""
            }
        }
        AlertDialog(
            onDismissRequest = { licensesDialogOpen = false },
            title = { Text(stringResource(R.string.licenses_title)) },
            text = {
                Text(
                    text = noticeText,
                    modifier = Modifier
                        .fillMaxHeight(0.6f)
                        .verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodySmall
                )
            },
            confirmButton = {
                TextButton(onClick = { licensesDialogOpen = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        )
    }
}

// ── Shared layout primitives ──────────────────────────────────────────

@Composable
fun SettingsGroup(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
        )
        GlassSurface(modifier = Modifier.fillMaxWidth()) {
            Column(content = content)
        }
    }
}

@Composable
fun SettingsItemRow(
    icon: ImageVector,
    iconTint: Color,
    iconBackground: Color,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(iconBackground),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        content()
    }
}

// ── Segmented quality control ─────────────────────────────────────────

@Composable
private fun SegmentedQualityControl(
    selected: String,
    onSelect: (String) -> Unit,
    isPremium: Boolean,
    accentColor: Color
) {
    val isStandard = selected == "standard"
    val isPro = selected == "pro"
    val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurface = MaterialTheme.colorScheme.onSurface

    val standardBg by animateColorAsState(
        targetValue = if (isStandard) surfaceColor else Color.Transparent,
        animationSpec = tween(200),
        label = "standardBg"
    )
    val proBg by animateColorAsState(
        targetValue = if (isPro) surfaceColor else Color.Transparent,
        animationSpec = tween(200),
        label = "proBg"
    )
    val standardContent by animateColorAsState(
        targetValue = if (isStandard) accentColor else onSurface,
        animationSpec = tween(200),
        label = "standardContent"
    )
    val proContent by animateColorAsState(
        targetValue = if (isPro) ImageDesign.semantic.warning
        else if (!isPremium) onSurface.copy(alpha = 0.4f)
        else onSurface,
        animationSpec = tween(200),
        label = "proContent"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(trackColor)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Standard option
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(standardBg)
                .clickable { onSelect("standard") }
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.quality_standard),
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (isStandard) FontWeight.Bold else FontWeight.Normal
                ),
                color = standardContent
            )
        }

        // Pro option
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(proBg)
                .clickable(enabled = isPremium) {
                    if (isPremium) onSelect("pro")
                }
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!isPremium) {
                    Icon(
                        Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = proContent,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = if (isPremium) stringResource(R.string.quality_pro)
                    else stringResource(R.string.quality_pro_locked),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = if (isPro) FontWeight.Bold else FontWeight.Normal
                    ),
                    color = proContent
                )
                if (!isPremium) {
                    Spacer(modifier = Modifier.width(4.dp))
                    // "PRO" badge
                    Text(
                        text = "PRO",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = ImageDesign.semantic.warning
                    )
                }
            }
        }
    }
}



// ── Language bottom sheet ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageBottomSheet(
    currentLanguage: String,
    currentLabel: String,
    onLanguageSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    val accentColor = ImageDesign.semantic.aiAccent
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.select_language_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            val languages = listOf(
                "system" to systemLanguageLabel(context),
                "en" to stringResource(R.string.language_english),
                "vi" to stringResource(R.string.language_vietnamese),
                "ja" to stringResource(R.string.language_japanese),
                "es" to stringResource(R.string.language_spanish),
                "fr" to stringResource(R.string.language_french),
                "de" to stringResource(R.string.language_german),
                "it" to stringResource(R.string.language_italian),
                "pt-BR" to stringResource(R.string.language_portuguese_br),
                "ko" to stringResource(R.string.language_korean),
                "zh-CN" to stringResource(R.string.language_chinese_simplified),
                "zh-TW" to stringResource(R.string.language_chinese_traditional),
                "ar" to stringResource(R.string.language_arabic),
                "id" to stringResource(R.string.language_indonesian),
                "hi-IN" to stringResource(R.string.language_hindi),
                "th" to stringResource(R.string.language_thai),
                "tr-TR" to stringResource(R.string.language_turkish),
                "pl" to stringResource(R.string.language_polish)
            )

            languages.forEach { (code, label) ->
                val isSelected = currentLanguage == code
                ListItem(
                    headlineContent = {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    modifier = Modifier.clickable { onLanguageSelected(code) },
                    trailingContent = {
                        if (isSelected) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = null,
                                tint = accentColor
                            )
                        }
                    }
                )
            }
        }
    }
}

// ── Utilities ─────────────────────────────────────────────────────────

private fun languageLabel(code: String, context: android.content.Context): String = when (code) {
    "vi" -> context.getString(R.string.language_vietnamese)
    "ja" -> context.getString(R.string.language_japanese)
    "en" -> context.getString(R.string.language_english)
    "es" -> context.getString(R.string.language_spanish)
    "fr" -> context.getString(R.string.language_french)
    "de" -> context.getString(R.string.language_german)
    "it" -> context.getString(R.string.language_italian)
    "pt-BR" -> context.getString(R.string.language_portuguese_br)
    "ko" -> context.getString(R.string.language_korean)
    "zh-CN" -> context.getString(R.string.language_chinese_simplified)
    "zh-TW" -> context.getString(R.string.language_chinese_traditional)
    "ar" -> context.getString(R.string.language_arabic)
    "id" -> context.getString(R.string.language_indonesian)
    "hi-IN" -> context.getString(R.string.language_hindi)
    "th" -> context.getString(R.string.language_thai)
    "tr-TR" -> context.getString(R.string.language_turkish)
    "pl" -> context.getString(R.string.language_polish)
    else -> systemLanguageLabel(context)
}

private fun systemLanguageLabel(context: android.content.Context): String {
    val locale = systemLocale()
    val config = android.content.res.Configuration().apply { setLocale(locale) }
    return context.createConfigurationContext(config).getString(R.string.language_system)
}

private fun systemLocale(): Locale {
    val systemConfig = Resources.getSystem().configuration
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        systemConfig.locales[0]
    } else {
        @Suppress("DEPRECATION")
        systemConfig.locale
    }
}
