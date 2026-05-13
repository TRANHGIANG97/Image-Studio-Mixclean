package com.thgiang.image.feature.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.thgiang.image.R
import com.thgiang.image.core.design.components.GlassSurface
import com.thgiang.image.core.design.components.StatusChip
import com.thgiang.image.core.design.theme.ImageDesign

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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var languageExpanded by remember { mutableStateOf(false) }
    var licensesDialogOpen by remember { mutableStateOf(false) }

    val selectedLanguageLabel = when (selectedLanguage) {
        "vi" -> stringResource(R.string.language_vietnamese)
        "ja" -> stringResource(R.string.language_japanese)
        "en" -> stringResource(R.string.language_english)
        "es" -> stringResource(R.string.language_spanish)
        "fr" -> stringResource(R.string.language_french)
        "de" -> stringResource(R.string.language_german)
        "it" -> stringResource(R.string.language_italian)
        "pt-BR" -> stringResource(R.string.language_portuguese_br)
        "ko" -> stringResource(R.string.language_korean)
        "zh-CN" -> stringResource(R.string.language_chinese_simplified)
        "zh-TW" -> stringResource(R.string.language_chinese_traditional)
        "ar" -> stringResource(R.string.language_arabic)
        "id" -> stringResource(R.string.language_indonesian)
        "hi-IN" -> stringResource(R.string.language_hindi)
        "th" -> stringResource(R.string.language_thai)
        "tr-TR" -> stringResource(R.string.language_turkish)
        "pl" -> stringResource(R.string.language_polish)
        else -> stringResource(R.string.language_system)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ImageDesign.gradients.appBackground)
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        StatusChip(text = stringResource(R.string.settings_app_preferences), tone = ImageDesign.semantic.aiAccent)
        
        Spacer(modifier = Modifier.height(24.dp))

        SettingsSectionHeader(title = stringResource(R.string.settings_section_appearance))
        GlassSurface(modifier = Modifier.fillMaxWidth()) {
            Column {
                SettingsItemRow(
                    icon = Icons.Rounded.DarkMode,
                    iconTint = MaterialTheme.colorScheme.primary,
                    iconBackground = MaterialTheme.colorScheme.primaryContainer,
                    title = stringResource(R.string.dark_mode_title),
                    subtitle = stringResource(R.string.dark_mode_description),
                    onClick = { onDarkModeChange(!isDarkMode) }
                ) {
                    Switch(
                        checked = isDarkMode,
                        onCheckedChange = onDarkModeChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }

                Divider(modifier = Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

                Box {
                    SettingsItemRow(
                        icon = Icons.Rounded.Language,
                        iconTint = Color(0xFF10B981), // Emerald
                        iconBackground = Color(0xFFD1FAE5),
                        title = stringResource(R.string.language_title),
                        subtitle = stringResource(R.string.language_description),
                        onClick = { languageExpanded = true }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = selectedLanguageLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    DropdownMenu(
                        expanded = languageExpanded,
                        onDismissRequest = { languageExpanded = false }
                    ) {
                        ListItem(headlineContent = { Text(stringResource(R.string.current_language_menu, selectedLanguageLabel)) })
                        val langs = listOf(
                            "system" to stringResource(R.string.language_system),
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
                        langs.forEach { (code, label) ->
                            DropdownMenuItem(
                                text = { Text(if (selectedLanguage == code) "✓ $label" else label) },
                                onClick = {
                                    languageExpanded = false
                                    onLanguageChange(code)
                                }
                            )
                        }
                    }
                }

                Divider(modifier = Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

                SettingsItemRow(
                    icon = Icons.Rounded.AutoAwesome,
                    iconTint = Color(0xFF6366F1), // Indigo
                    iconBackground = Color(0xFFE0E7FF),
                    title = stringResource(R.string.home_preview_title),
                    subtitle = stringResource(R.string.home_preview_description),
                    onClick = { onHomePreviewEnabledChange(!isHomePreviewEnabled) }
                ) {
                    Switch(
                        checked = isHomePreviewEnabled,
                        onCheckedChange = onHomePreviewEnabledChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF6366F1)
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // SECTION: BỘ MÁY XỬ LÝ (AI)
        SettingsSectionHeader(title = stringResource(R.string.settings_section_ai))
        GlassSurface(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(0xFFFEF3C7)), // Amber 100
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Color(0xFFD97706)) // Amber 600
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_removal_quality),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = stringResource(R.string.settings_removal_quality_summary),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                
                // Segmented Control Custom
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(4.dp)
                ) {
                    val isStandard = preferredRemovalQuality == "standard"
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isStandard) MaterialTheme.colorScheme.surface else Color.Transparent)
                            .clickable { onPreferredRemovalQualityChange("standard") }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.quality_standard),
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = if (isStandard) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = if (isStandard) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    val isPro = preferredRemovalQuality == "pro"
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isPro) MaterialTheme.colorScheme.surface else Color.Transparent)
                            .clickable(enabled = isPremium) { if (isPremium) onPreferredRemovalQualityChange("pro") }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isPremium) stringResource(R.string.quality_pro) else stringResource(R.string.quality_pro_locked),
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = if (isPro) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = if (!isPremium) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                     else if (isPro) Color(0xFFD97706) 
                                     else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        SettingsSectionHeader(title = stringResource(R.string.settings_section_about))
        GlassSurface(modifier = Modifier.fillMaxWidth()) {
            SettingsItemRow(
                icon = Icons.Rounded.Info,
                iconTint = Color(0xFF3B82F6), // Blue 500
                iconBackground = Color(0xFFDBEAFE), // Blue 100
                title = stringResource(R.string.licenses_title),
                subtitle = stringResource(R.string.settings_app_version_label, "v1.0.0"),
                onClick = { licensesDialogOpen = true }
            ) {
                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(modifier = Modifier.height(80.dp))

        if (licensesDialogOpen) {
            val noticeText = try {
                context.assets.open("NOTICE").bufferedReader().use { it.readText() }
            } catch (_: Exception) { "" }
            AlertDialog(
                onDismissRequest = { licensesDialogOpen = false },
                title = { Text(stringResource(R.string.licenses_title)) },
                text = {
                    Text(
                        text = noticeText,
                        modifier = Modifier.fillMaxHeight(0.6f).verticalScroll(rememberScrollState()),
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
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
    )
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
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(iconBackground),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconTint)
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
        Spacer(modifier = Modifier.width(16.dp))
        content()
    }
}


