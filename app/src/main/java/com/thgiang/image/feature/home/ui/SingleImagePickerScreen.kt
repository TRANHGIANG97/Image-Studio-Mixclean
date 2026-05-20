package com.thgiang.image.feature.home.ui

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import com.thgiang.image.core.design.components.BackgroundRemovalLoadingOverlay
import com.thgiang.image.core.design.components.GradientPrimaryButton
import com.thgiang.image.core.design.theme.ImageDesign
import com.thgiang.image.feature.common.media.loadPickerDemoSampleUris
import com.thgiang.image.feature.common.media.loadDownloadImageUris
import com.thgiang.image.feature.common.media.loadPickerImageUris
import com.thgiang.image.R
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val DOWNLOAD_ALBUM_ID = "__download_images__"

data class MediaAlbum(
    val id: String,
    val name: String,
    val coverUri: Uri,
    var count: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SingleImagePickerScreen(
    onImageSelected: (Uri) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val refreshScope = rememberCoroutineScope()

    val galleryPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        Manifest.permission.READ_MEDIA_IMAGES
    else
        Manifest.permission.READ_EXTERNAL_STORAGE

    val cameraPermission = Manifest.permission.CAMERA

    var hasGalleryPermission by remember {
        mutableStateOf(
            context.checkSelfPermission(galleryPermission) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    var hasCameraPermission by remember {
        mutableStateOf(
            context.checkSelfPermission(cameraPermission) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val galleryPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasGalleryPermission = granted }

    val cameraPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }
    val cameraCaptureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            tempCameraUri?.let { onImageSelected(it) }
        }
    }

    fun launchCamera() {
        if (!hasCameraPermission) {
            cameraPermLauncher.launch(cameraPermission)
            return
        }
        val cacheDir = context.cacheDir
        val file = File(cacheDir, "single_capture_${System.currentTimeMillis()}.jpg").apply {
            parentFile?.mkdirs()
        }
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        uri.also { u ->
            tempCameraUri = u
            cameraCaptureLauncher.launch(u)
        }
    }

    var images by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var albums by remember { mutableStateOf<List<MediaAlbum>>(emptyList()) }
    var demoSampleUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var selectedAlbum by remember { mutableStateOf<MediaAlbum?>(null) }
    var albumImages by remember { mutableStateOf<List<Uri>>(emptyList()) }

    suspend fun refreshGallery(showLoader: Boolean) {
        if (hasGalleryPermission) {
            if (showLoader) isLoading = true
            images = loadGalleryImages(context)
            albums = loadAlbums(context)
            selectedAlbum?.let { album ->
                albumImages = loadAlbumImages(context, album.id)
            }
            isLoading = false
        }
    }

    LaunchedEffect(hasGalleryPermission) {
        refreshGallery(showLoader = true)
    }

    LaunchedEffect(Unit) {
        demoSampleUris = loadPickerDemoSampleUris(context)
    }

    LaunchedEffect(selectedAlbum) {
        selectedAlbum?.let { album ->
            isLoading = true
            albumImages = loadAlbumImages(context, album.id)
            isLoading = false
        }
    }

    DisposableEffect(lifecycleOwner, hasGalleryPermission, selectedAlbum) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && hasGalleryPermission) {
                refreshScope.launch {
                    refreshGallery(showLoader = images.isEmpty() && albums.isEmpty())
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        if (!hasGalleryPermission) {
            galleryPermLauncher.launch(galleryPermission)
        }
    }

    BackHandler {
        if (selectedAlbum != null) {
            selectedAlbum = null
        } else {
            onCancel()
        }
    }

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
                    Column {
                        TopAppBar(
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                            navigationIcon = {
                                IconButton(onClick = {
                                    if (selectedAlbum != null) selectedAlbum = null else onCancel()
                                }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.go_back))
                                }
                            },
                            title = {
                                Text(
                                    text = selectedAlbum?.name ?: stringResource(R.string.picker_title),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            actions = {
                                IconButton(onClick = ::launchCamera) {
                                    Icon(
                                        Icons.Default.CameraAlt,
                                        contentDescription = stringResource(R.string.cd_capture_photo),
                                        tint = ImageDesign.semantic.aiAccent
                                    )
                                }
                            }
                        )
                        if (selectedAlbum == null && hasGalleryPermission) {
                            TabRow(
                                selectedTabIndex = selectedTabIndex,
                                containerColor = Color.Transparent,
                                contentColor = ImageDesign.semantic.aiAccent,
                                indicator = { tabPositions ->
                                    if (selectedTabIndex < tabPositions.size) {
                                        TabRowDefaults.SecondaryIndicator(
                                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                                            color = ImageDesign.semantic.aiAccent
                                        )
                                    }
                                }
                            ) {
                                Tab(
                                    selected = selectedTabIndex == 0,
                                    onClick = { selectedTabIndex = 0 },
                                    text = { Text(stringResource(R.string.picker_tab_all), fontWeight = FontWeight.SemiBold) },
                                    selectedContentColor = ImageDesign.semantic.aiAccent,
                                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Tab(
                                    selected = selectedTabIndex == 1,
                                    onClick = { selectedTabIndex = 1 },
                                    text = { Text(stringResource(R.string.picker_tab_albums), fontWeight = FontWeight.SemiBold) },
                                    selectedContentColor = ImageDesign.semantic.aiAccent,
                                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    when {
                        !hasGalleryPermission -> PermissionView(
                            onRequest = { galleryPermLauncher.launch(galleryPermission) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        )

                        isLoading -> BackgroundRemovalLoadingOverlay(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            message = stringResource(R.string.loading_image)
                        )

                        selectedAlbum != null -> {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentPadding = PaddingValues(2.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                items(albumImages, key = { it.toString() }) { uri ->
                                    GalleryImageCell(
                                        uri = uri,
                                        onClick = { handleImageSelection(context, uri, onImageSelected) }
                                    )
                                }
                            }
                        }

                        selectedTabIndex == 0 -> {
                            val displayUris = if (selectedAlbum == null) {
                                demoSampleUris + images
                            } else {
                                images
                            }

                            if (displayUris.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    EmptyGalleryView(onOpenCamera = ::launchCamera)
                                }
                            } else {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(3),
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                    contentPadding = PaddingValues(2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    item {
                                        CameraCell(onClick = ::launchCamera)
                                    }
                                    items(displayUris, key = { it.toString() }) { uri ->
                                        GalleryImageCell(
                                            uri = uri,
                                            badgeText = if (demoSampleUris.contains(uri)) "\u004d\u1ea9u" else null,
                                            onClick = { handleImageSelection(context, uri, onImageSelected) }
                                        )
                                    }
                                }
                            }
                        }

                        selectedTabIndex == 1 -> {
                            if (albums.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(stringResource(R.string.picker_no_albums), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            } else {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(2),
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                    contentPadding = PaddingValues(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    items(albums, key = { it.id }) { album ->
                                        AlbumCell(album = album, onClick = { selectedAlbum = album })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraCell(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(0.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.CameraAlt,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = ImageDesign.semantic.aiAccent
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.picker_camera_item),
                style = MaterialTheme.typography.labelSmall,
                color = ImageDesign.semantic.aiAccent,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun GalleryImageCell(
    uri: Uri,
    onClick: () -> Unit,
    badgeText: String? = null
) {
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(150),
        label = "cellScale"
    )

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .scale(scale)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        AsyncImage(
            model = uri,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        if (badgeText != null) {
            Text(
                text = badgeText,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .background(Color(0xFFFF9800).copy(alpha = 0.92f))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall.copy(
                    color = Color.White,
                    fontSize = 8.sp
                ),
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

private fun handleImageSelection(
    context: Context,
    uri: Uri,
    onImageSelected: (Uri) -> Unit
) {
    onImageSelected(uri)
}

@Composable
private fun AlbumCell(album: MediaAlbum, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .aspectRatio(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = album.coverUri,
                contentDescription = album.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = album.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = stringResource(R.string.picker_photo_count, album.count),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PermissionView(onRequest: () -> Unit, modifier: Modifier = Modifier) {
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
                text = stringResource(R.string.picker_permission_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.picker_permission_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onRequest,
                colors = ButtonDefaults.buttonColors(containerColor = ImageDesign.semantic.aiAccent),
                shape = RoundedCornerShape(ImageDesign.radii.medium)
            ) {
                Text(stringResource(R.string.grant_permission), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun EmptyGalleryView(onOpenCamera: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(32.dp)
    ) {
        Icon(
            Icons.Default.PhotoLibrary,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(R.string.picker_no_photos),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        GradientPrimaryButton(
            text = stringResource(R.string.picker_capture_btn),
            onClick = onOpenCamera
        )
    }
}

private suspend fun loadGalleryImages(context: Context): List<Uri> =
    withContext(Dispatchers.IO) { loadPickerImageUris(context) }

private suspend fun loadAlbums(context: Context): List<MediaAlbum> =
    withContext(Dispatchers.IO) {
        val albumMap = mutableMapOf<String, MediaAlbum>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        )
        context.contentResolver.query(
            collection,
            projection,
            null,
            null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            val bucketNameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val bucketId = cursor.getString(bucketIdCol) ?: continue
                val bucketName = cursor.getString(bucketNameCol) ?: "Unknown"
                val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

                if (albumMap.containsKey(bucketId)) {
                    albumMap[bucketId]?.count = (albumMap[bucketId]?.count ?: 0) + 1
                } else {
                    albumMap[bucketId] = MediaAlbum(bucketId, bucketName, uri, 1)
                }
            }
        }

        val downloadUris = loadDownloadImageUris(context)
        if (downloadUris.isNotEmpty()) {
            albumMap[DOWNLOAD_ALBUM_ID] = MediaAlbum(
                id = DOWNLOAD_ALBUM_ID,
                name = "Download",
                coverUri = downloadUris.first(),
                count = downloadUris.size
            )
        }

        albumMap.values.toList()
    }

private suspend fun loadAlbumImages(context: Context, bucketId: String): List<Uri> =
    withContext(Dispatchers.IO) {
        if (bucketId == DOWNLOAD_ALBUM_ID) {
            return@withContext loadDownloadImageUris(context)
        }

        val list = mutableListOf<Uri>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Images.Media._ID
        )
        val selection = "${MediaStore.Images.Media.BUCKET_ID} = ?"
        val selectionArgs = arrayOf(bucketId)

        context.contentResolver.query(
            collection,
            projection,
            selection,
            selectionArgs,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext()) {
                list.add(
                    ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        cursor.getLong(idCol)
                    )
                )
            }
        }
        list
    }




