package com.thgiang.image.feature.editor.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AutoAwesomeMotion
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.thgiang.image.R
import java.io.File

// ─── Data Models ────────────────────────────────────────────────────────────

private data class QuickToolItem(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

private data class ShareTarget(
    val label: String,
    val iconRes: Int? = null,
    val iconVector: ImageVector? = null,
    val packageName: String? = null,
    val action: (Context, Uri?) -> Unit
)

// ─── Main Composable ─────────────────────────────────────────────────────────

@Composable
fun SaveSuccessScreen(
    savedImageUri: Uri?,
    savedImageFile: File?,
    onBack: () -> Unit,
    onGoHome: () -> Unit,
    onCreateNew: () -> Unit,
    onOpenRemoveBg: () -> Unit,
    onBatchRemove: () -> Unit,
    onOpenEffects: () -> Unit,
    onOpenBackgroundPresets: () -> Unit,
    onOpenStudio: () -> Unit,
    onOpenMagic: () -> Unit,
) {
    val context = LocalContext.current
    var showGoHomeConfirm by remember { mutableStateOf(false) }
    var showImagePreview by remember { mutableStateOf(false) }
    val imageAspectRatio = remember(savedImageUri, savedImageFile) {
        readImageAspectRatio(context, savedImageUri, savedImageFile)
    }

    // Consume back so EditorScreen underneath does not show "unsaved changes".
    BackHandler {
        when {
            showImagePreview -> showImagePreview = false
            else -> showGoHomeConfirm = true
        }
    }

    val quickTools = listOf(
        QuickToolItem(
            label = stringResource(R.string.home_dock_pick_image),
            icon = Icons.Outlined.Image,
            onClick = onOpenRemoveBg
        ),
        QuickToolItem(
            label = stringResource(R.string.home_dock_batch),
            icon = Icons.Outlined.AutoAwesomeMotion,
            onClick = onBatchRemove
        ),
        QuickToolItem(
            label = stringResource(R.string.home_dock_effects),
            icon = Icons.Outlined.AutoFixHigh,
            onClick = onOpenEffects
        ),
        QuickToolItem(
            label = stringResource(R.string.home_background_presets),
            icon = Icons.Outlined.Wallpaper,
            onClick = onOpenBackgroundPresets
        ),
        QuickToolItem(
            label = stringResource(R.string.home_dock_studio),
            icon = Icons.Outlined.Palette,
            onClick = onOpenStudio
        ),
        QuickToolItem(
            label = stringResource(R.string.home_dock_magic),
            icon = Icons.Outlined.AutoAwesome,
            onClick = onOpenMagic
        ),
    )

    val shareTargets = buildShareTargets(context, savedImageFile)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // ── Top Bar ───────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.save_success_back),
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Text(
                    text = stringResource(R.string.save_success_title),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
                IconButton(onClick = { showGoHomeConfirm = true }) {
                    Icon(
                        imageVector = Icons.Filled.Home,
                        contentDescription = stringResource(R.string.save_success_home),
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            // ── Thumbnail + Nút Tạo mới ───────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SavedImagePreviewCard(
                    savedImageUri = savedImageUri,
                    imageAspectRatio = imageAspectRatio,
                    onClick = { showImagePreview = true }
                )

                // Nút Tạo mới
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onCreateNew,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF3B7A)
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.save_success_create_new),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Section: Thử các công cụ khác ────────────────────────────────
            Text(
                text = stringResource(R.string.save_success_try_other_tools),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(quickTools) { tool ->
                    QuickToolChip(tool = tool)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f)
            )

            Spacer(modifier = Modifier.height(16.dp))



            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(shareTargets) { target ->
                    ShareTargetItem(
                        target = target,
                        savedImageFile = savedImageFile,
                        context = context
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showImagePreview && savedImageUri != null) {
        SavedImageFullPreviewDialog(
            imageUri = savedImageUri,
            onDismiss = { showImagePreview = false }
        )
    }

    if (showGoHomeConfirm) {
        AlertDialog(
            onDismissRequest = { showGoHomeConfirm = false },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.save_success_confirm_home_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.save_success_confirm_home_message),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showGoHomeConfirm = false
                        onGoHome()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(100)
                ) {
                    Text(
                        text = stringResource(R.string.save_success_confirm_home_confirm),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showGoHomeConfirm = false }) {
                    Text(stringResource(R.string.exit_dialog_cancel))
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }
}

@Composable
private fun SavedImagePreviewCard(
    savedImageUri: Uri?,
    imageAspectRatio: Float,
    onClick: () -> Unit,
) {
    val cardShape = RoundedCornerShape(12.dp)
    val cardModifier = Modifier
        .widthIn(min = 88.dp, max = 140.dp)
        .heightIn(min = 88.dp, max = 160.dp)
        .aspectRatio(imageAspectRatio.coerceIn(0.45f, 2.2f))
        .clip(cardShape)
        .background(Color(0xFFF0F0F0))
        .border(1.dp, Color(0xFFE0E0E0), cardShape)
        .clickable(enabled = savedImageUri != null, onClick = onClick)

    Box(
        modifier = cardModifier,
        contentAlignment = Alignment.Center
    ) {
        if (savedImageUri != null) {
            AsyncImage(
                model = savedImageUri,
                contentDescription = stringResource(R.string.save_success_tap_to_preview),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp),
                contentScale = ContentScale.Fit
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.ZoomIn,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        } else {
            Icon(
                imageVector = Icons.Outlined.Image,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = Color(0xFFCCCCCC)
            )
        }
    }
}

@Composable
private fun SavedImageFullPreviewDialog(
    imageUri: Uri,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.94f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = imageUri,
                contentDescription = stringResource(R.string.save_success_saved_image),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 56.dp),
                contentScale = ContentScale.Fit
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.save_success_preview_close),
                    tint = Color.White
                )
            }
        }
    }
}

private fun readImageAspectRatio(
    context: Context,
    uri: Uri?,
    file: File?,
): Float {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    when {
        file != null && file.exists() -> BitmapFactory.decodeFile(file.absolutePath, options)
        uri != null -> runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            }
        }
    }
    val width = options.outWidth
    val height = options.outHeight
    return if (width > 0 && height > 0) width.toFloat() / height else 1f
}

// ─── Quick Tool Chip ─────────────────────────────────────────────────────────

@Composable
private fun QuickToolChip(tool: QuickToolItem) {
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val scale = if (isPressed) 0.92f else 1f

    Column(
        modifier = Modifier
            .scale(scale)
            .width(72.dp)
            .clickable(interactionSource = interaction, indication = null) { tool.onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = tool.icon,
                contentDescription = tool.label,
                modifier = Modifier.size(26.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = tool.label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                lineHeight = 13.sp,
                textAlign = TextAlign.Center
            ),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ─── Share Target Item ───────────────────────────────────────────────────────

@Composable
private fun ShareTargetItem(
    target: ShareTarget,
    savedImageFile: File?,
    context: Context
) {
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val scale = if (isPressed) 0.88f else 1f

    Column(
        modifier = Modifier
            .scale(scale)
            .width(52.dp)
            .clickable(interactionSource = interaction, indication = null) {
                val uri = savedImageFile?.let {
                    runCatching {
                        FileProvider.getUriForFile(context, "com.thgiang.image.fileprovider", it)
                    }.getOrNull()
                }
                target.action(context, uri)
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (target.iconVector != null) {
                Icon(
                    imageVector = target.iconVector,
                    contentDescription = target.label,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = target.label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.sp,
                textAlign = TextAlign.Center
            ),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ─── Share Targets Builder ───────────────────────────────────────────────────

private fun buildShareTargets(context: Context, savedImageFile: File?): List<ShareTarget> {
    val mimeType = if (savedImageFile?.extension == "png") "image/png" else "image/jpeg"

    val targets = mutableListOf<ShareTarget>()

    // Share chung (luôn có)
    targets.add(ShareTarget(
        label = context.getString(R.string.save_success_share),
        iconVector = Icons.Filled.Share,
        action = { ctx, uri ->
            if (uri == null) return@ShareTarget
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(Intent.createChooser(intent, context.getString(R.string.save_success_share_with)))
        }
    ))

    return targets
}
