package com.thgiang.image.studio.ui.editor.components.label

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thgiang.image.studio.R
import com.thgiang.image.studio.ui.editor.theme.EditorTokens
import com.thgiang.image.studio.util.FontDownloader
import com.thgiang.image.studio.util.FontManifestEntry
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FontPickerBottomSheet(
    visible: Boolean,
    fonts: List<FontManifestEntry>,
    selectedFamilySlug: String,
    tokens: EditorTokens,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    if (!visible) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val filtered = remember(fonts, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) fonts
        else fonts.filter { font ->
            font.name.lowercase().contains(q) ||
                font.familySlug.lowercase().contains(q) ||
                font.aliases.any { it.lowercase().contains(q) }
        }
    }

    val otherCategory = stringResource(R.string.studio_label_font_category_other)

    val grouped = remember(filtered, otherCategory) {
        filtered.groupBy { it.style.ifBlank { otherCategory } }
    }

    val orderedCategories = remember(grouped) {
        (FontDownloader.fontCategories + grouped.keys)
            .distinct()
            .filter { grouped.containsKey(it) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.studio_label_font_picker_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = tokens.textPrimary,
            )

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                placeholder = { Text(stringResource(R.string.studio_label_font_search)) },
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = tokens.textSecondary)
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )

            if (fonts.isEmpty()) {
                Text(
                    text = stringResource(R.string.studio_label_font_loading),
                    modifier = Modifier.padding(top = 16.dp),
                    color = tokens.textSecondary,
                    fontSize = 13.sp,
                )
                return@Column
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                orderedCategories.forEach { category ->
                    val categoryFonts = grouped[category].orEmpty()
                    if (categoryFonts.isEmpty()) return@forEach

                    item(key = "header-$category") {
                        Text(
                            text = category,
                            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = tokens.textSecondary,
                        )
                    }

                    items(categoryFonts, key = { it.id }) { font ->
                        FontPickerRow(
                            font = font,
                            isSelected = selectedFamilySlug.equals(font.familySlug, ignoreCase = true),
                            tokens = tokens,
                            onClick = {
                                onSelect(font.familySlug)
                                scope.launch { FontDownloader.getTypeface(context, font.familySlug) }
                                onDismiss()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FontPickerRow(
    font: FontManifestEntry,
    isSelected: Boolean,
    tokens: EditorTokens,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    var previewFamily by remember(font.familySlug) { mutableStateOf<FontFamily?>(null) }

    LaunchedEffect(font.familySlug) {
        val tf = FontDownloader.getTypeface(context, font.familySlug)
        previewFamily = tf?.let { FontFamily(it) }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) tokens.accentSoft else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = font.name,
            fontSize = 15.sp,
            fontFamily = previewFamily,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) tokens.accent else tokens.textPrimary,
            modifier = Modifier.weight(1f),
        )
        if (isSelected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = tokens.accent,
            )
        }
    }
    HorizontalDivider(color = tokens.borderSubtle.copy(alpha = 0.5f))
}

@Composable
internal fun FontFamilyTrigger(
    fonts: List<FontManifestEntry>,
    selectedFamilySlug: String,
    tokens: EditorTokens,
    onClick: () -> Unit,
) {
    val selectedName = fonts.firstOrNull {
        it.familySlug.equals(selectedFamilySlug, ignoreCase = true)
    }?.name ?: selectedFamilySlug

    val context = LocalContext.current
    var previewFamily by remember(selectedFamilySlug) { mutableStateOf<FontFamily?>(null) }
    LaunchedEffect(selectedFamilySlug) {
        val tf = FontDownloader.getTypeface(context, selectedFamilySlug)
        previewFamily = tf?.let { FontFamily(it) }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, tokens.borderSubtle, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.studio_label_font_family),
                fontSize = 11.sp,
                color = tokens.textSecondary,
            )
            Text(
                text = selectedName,
                fontSize = 15.sp,
                fontFamily = previewFamily,
                fontWeight = FontWeight.Medium,
                color = tokens.textPrimary,
            )
        }
        Text(text = "▾", color = tokens.textSecondary, fontSize = 14.sp)
    }
}
