package com.thgiang.image.feature.home.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thgiang.image.core.data.gallery.GalleryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val repository: GalleryRepository
) : ViewModel() {

    private val _images = MutableStateFlow<List<GalleryRepository.GalleryImage>>(emptyList())
    val images = _images.asStateFlow()

    private val _albums = MutableStateFlow<List<GalleryRepository.GalleryAlbum>>(emptyList())
    val albums = _albums.asStateFlow()

    private val _selectedUris = MutableStateFlow<Set<Uri>>(emptySet())
    val selectedUris = _selectedUris.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _currentTab = MutableStateFlow(0)
    val currentTab = _currentTab.asStateFlow()

    private val _selectedAlbum = MutableStateFlow<GalleryRepository.GalleryAlbum?>(null)
    val selectedAlbum = _selectedAlbum.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        loadImages()
        loadAlbums()
    }

    fun setTab(index: Int) {
        _currentTab.value = index
    }

    fun selectAlbum(album: GalleryRepository.GalleryAlbum) {
        _selectedAlbum.value = album
        _currentTab.value = 0
        loadImages(album.id)
    }

    fun clearAlbumFilter() {
        _selectedAlbum.value = null
        loadImages()
    }

    fun loadImages(bucketId: String? = null) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                _images.value = repository.loadImages(bucketId = bucketId)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadAlbums() {
        viewModelScope.launch {
            try {
                _albums.value = repository.loadAlbums()
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun toggleSelection(uri: Uri) {
        _selectedUris.value = if (uri in _selectedUris.value) {
            _selectedUris.value - uri
        } else {
            if (_selectedUris.value.size < MAX_SELECTION) {
                _selectedUris.value + uri
            } else {
                _selectedUris.value
            }
        }
    }

    fun clearSelection() {
        _selectedUris.value = emptySet()
    }

    companion object {
        const val MAX_SELECTION = 20
    }
}
