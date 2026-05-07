package com.thgiang.image.feature.home.ui

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.thgiang.image.R
import com.thgiang.image.feature.home.viewmodel.GalleryViewModel
import com.thgiang.image.core.data.gallery.GalleryRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiGalleryPickerBottomSheet(
    onDismiss: () -> Unit,
    onImagesSelected: (List<Uri>) -> Unit
) {
    val context = LocalContext.current
    val viewModel: GalleryViewModel = viewModel()
    val images by viewModel.images.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val selectedUris by viewModel.selectedUris.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val currentTab by viewModel.currentTab.collectAsState()
    val selectedAlbum by viewModel.selectedAlbum.collectAsState()

    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.loadData()
        } else {
            onDismiss()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.clearSelection()
        }
    }


    LaunchedEffect(Unit) {
        permissionLauncher.launch(permission)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp, start = 16.dp, end = 16.dp)
                .fillMaxHeight(0.85f)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(
                        R.string.gallery_picker_title,
                        selectedUris.size,
                        GalleryViewModel.MAX_SELECTION
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.action_close)
                    )
                }
            }

            // Tabs
            TabRow(
                selectedTabIndex = currentTab,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary,
                divider = {}
            ) {
                Tab(
                    selected = currentTab == 0,
                    onClick = { viewModel.setTab(0) },
                    text = { Text(stringResource(R.string.gallery_tab_select)) }
                )
                Tab(
                    selected = currentTab == 1,
                    onClick = { viewModel.setTab(1) },
                    text = { Text(stringResource(R.string.gallery_tab_album)) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Body
            Box(modifier = Modifier.weight(1f)) {
                when (currentTab) {
                    0 -> RecentTabContent(
                        images = images,
                        selectedUris = selectedUris,
                        isLoading = isLoading,
                        selectedAlbum = selectedAlbum,
                        onImageClick = { viewModel.toggleSelection(it) },
                        onClearFilter = { viewModel.clearAlbumFilter() }
                    )
                    1 -> AlbumTabContent(
                        albums = albums,
                        onAlbumClick = { viewModel.selectAlbum(it) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Bottom Buttons
            Button(
                onClick = { onImagesSelected(selectedUris.toList()) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = selectedUris.isNotEmpty() && !isLoading,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = stringResource(
                        R.string.gallery_picker_confirm,
                        selectedUris.size
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun RecentTabContent(
    images: List<GalleryRepository.GalleryImage>,
    selectedUris: Set<Uri>,
    isLoading: Boolean,
    selectedAlbum: GalleryRepository.GalleryAlbum?,
    onImageClick: (Uri) -> Unit,
    onClearFilter: () -> Unit
) {
    Column {
        if (selectedAlbum != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .clickable { onClearFilter() }
            ) {
                Text(
                    text = selectedAlbum.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (images.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "No images found", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(images, key = { it.id }) { image ->
                    val isSelected = image.uri in selectedUris
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clickable { onImageClick(image.uri) }
                    ) {
                        AsyncImage(
                            model = image.uri,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )

                        if (isSelected) {
                            val selectedIndex = selectedUris.indexOf(image.uri) + 1
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.4f))
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .size(22.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                                    .border(1.5.dp, Color.White, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = selectedIndex.toString(),
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AlbumTabContent(
    albums: List<GalleryRepository.GalleryAlbum>,
    onAlbumClick: (GalleryRepository.GalleryAlbum) -> Unit
) {
    if (albums.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = "No albums found", style = MaterialTheme.typography.bodyMedium)
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(albums) { album ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAlbumClick(album) }
                ) {
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .background(Color.LightGray, RoundedCornerShape(12.dp))
                    ) {
                        AsyncImage(
                            model = album.coverUri,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.05f), RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = album.count.toString(),
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = album.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
