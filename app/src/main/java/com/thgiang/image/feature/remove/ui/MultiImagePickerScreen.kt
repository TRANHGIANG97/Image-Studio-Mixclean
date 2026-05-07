package com.thgiang.image.feature.remove.ui

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.thgiang.image.core.design.components.GradientPrimaryButton
import com.thgiang.image.core.design.theme.ImageDesign
import com.thgiang.image.R
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MAX_SELECTION = 20

// ─────────────────────────────────────────────────────────────────────────────
// Public entry‑point
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiImagePickerScreen(
    onImagesSelected: (List<Uri>) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current

    // ── Permission state ──────────────────────────────────────────────────────
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        Manifest.permission.READ_MEDIA_IMAGES
    else
        Manifest.permission.READ_EXTERNAL_STORAGE

    var hasPermission by remember {
        mutableStateOf(
            context.checkSelfPermission(permission) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    // ── Image loading state ───────────────────────────────────────────────────
    // selectedUris is NOT remembered with a stable key, so every recomposition
    // from a fresh navigation entry starts with an empty list – satisfying the
    // "clear selection on re‑open" requirement.
    var selectedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var images by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    // Load images whenever permission is granted
    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            isLoading = true
            images = loadImagesFromMediaStore(context)
            isLoading = false
        }
    }

    // Request permission on first compose if not already granted
    LaunchedEffect(Unit) {
        if (!hasPermission) {
            permissionLauncher.launch(permission)
        }
    }

    // ── UI ────────────────────────────────────────────────────────────────────
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = ImageDesign.surfaces.base
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ImageDesign.gradients.appBackground)
        ) {
            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent
                        ),
                            navigationIcon = {
                                IconButton(onClick = onCancel) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.go_back))
                                }
                            },
                        title = {
                            Column {
                                Text(
                                    text = stringResource(R.string.multi_select_images),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                if (selectedUris.isNotEmpty()) {
                                    Text(
                                        text = stringResource(R.string.gallery_picker_title, selectedUris.size, MAX_SELECTION),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ImageDesign.semantic.aiAccent
                                    )
                                }
                            }
                        },
                        actions = {
                            if (selectedUris.isNotEmpty()) {
                                TextButton(
                                    onClick = { selectedUris = emptyList() }
                                ) {
                                    Text(
                                        stringResource(R.string.multi_clear_all),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            TextButton(
                                onClick = { onImagesSelected(selectedUris) },
                                enabled = selectedUris.isNotEmpty()
                            ) {
                                Text(
                                    stringResource(R.string.select),
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (selectedUris.isNotEmpty())
                                        ImageDesign.semantic.aiAccent
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                )
                            }
                        }
                    )
                },
                bottomBar = {
                    // Confirm bar – visible only when something is selected
                    if (selectedUris.isNotEmpty()) {
                        Surface(
                            tonalElevation = 3.dp,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .navigationBarsPadding()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onCancel,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(ImageDesign.radii.medium)
                                ) { Text(stringResource(R.string.multi_cancel)) }

                                GradientPrimaryButton(
                                    text = stringResource(R.string.gallery_picker_confirm, selectedUris.size),
                                    onClick = { onImagesSelected(selectedUris) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                when {
                    !hasPermission -> PermissionDeniedView(
                        onRequestAgain = { permissionLauncher.launch(permission) },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )

                    isLoading -> Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = ImageDesign.semantic.aiAccent)
                    }

                    images.isEmpty() -> Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Default.PhotoLibrary,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                stringResource(R.string.picker_no_photos),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    else -> LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentPadding = PaddingValues(
                            start = 2.dp, end = 2.dp, top = 2.dp,
                            // Extra bottom padding when confirm bar is visible
                            bottom = if (selectedUris.isNotEmpty()) 80.dp else 2.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(images, key = { it.toString() }) { uri ->
                            val isSelected = selectedUris.contains(uri)
                            val order = if (isSelected) selectedUris.indexOf(uri) + 1 else 0

                            PickerImageItem(
                                uri = uri,
                                isSelected = isSelected,
                                selectionOrder = order,
                                canSelectMore = selectedUris.size < MAX_SELECTION,
                                onToggle = {
                                    selectedUris = if (isSelected) {
                                        selectedUris - uri
                                    } else if (selectedUris.size < MAX_SELECTION) {
                                        selectedUris + uri
                                    } else {
                                        selectedUris
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Image grid item
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PickerImageItem(
    uri: Uri,
    isSelected: Boolean,
    selectionOrder: Int,
    canSelectMore: Boolean,
    onToggle: () -> Unit
) {
    val accentColor = ImageDesign.semantic.aiAccent

    val scale by animateFloatAsState(
        targetValue = if (isSelected) 0.92f else 1f,
        animationSpec = tween(150),
        label = "itemScale"
    )

    val overlayAlpha by animateFloatAsState(
        targetValue = if (isSelected) 0.35f else 0f,
        animationSpec = tween(150),
        label = "overlayAlpha"
    )

    val borderColor by animateColorAsState(
        targetValue = if (isSelected) accentColor else Color.Transparent,
        animationSpec = tween(150),
        label = "borderColor"
    )

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .scale(scale)
            .clip(RoundedCornerShape(if (isSelected) 8.dp else 0.dp))
            .border(
                width = if (isSelected) 2.5.dp else 0.dp,
                color = borderColor,
                shape = RoundedCornerShape(if (isSelected) 8.dp else 0.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = isSelected || canSelectMore,
                onClick = onToggle
            )
    ) {
        AsyncImage(
            model = uri,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Dimming overlay when selected
        if (isSelected) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(accentColor.copy(alpha = overlayAlpha))
            )
        }

        // Dimming overlay when max reached and this one is not selected
        if (!canSelectMore && !isSelected) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = 0.5f))
            )
        }

        // Selection badge (top-right corner)
        Box(
            modifier = Modifier
                .padding(5.dp)
                .size(22.dp)
                .align(Alignment.TopEnd)
        ) {
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(accentColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = selectionOrder.toString(),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.25f), CircleShape)
                        .border(1.5.dp, Color.White.copy(alpha = 0.75f), CircleShape)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Permission denied state
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PermissionDeniedView(
    onRequestAgain: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                Icons.Default.PhotoLibrary,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.permission_required),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.picker_permission_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Button(
                onClick = onRequestAgain,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ImageDesign.semantic.aiAccent
                ),
                shape = RoundedCornerShape(ImageDesign.radii.medium)
            ) {
                Text(stringResource(R.string.grant_permission), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MediaStore query (runs on IO dispatcher)
// ─────────────────────────────────────────────────────────────────────────────

private suspend fun loadImagesFromMediaStore(context: Context): List<Uri> =
    withContext(Dispatchers.IO) {
        val list = mutableListOf<Uri>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED
        )
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        context.contentResolver.query(
            collection, projection, null, null, sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                list.add(
                    ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                    )
                )
            }
        }
        list
    }
