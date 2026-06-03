package com.thgiang.image.studio.ui.gallery

import androidx.lifecycle.ViewModel
import com.thgiang.image.core.domain.model.template.CloudCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class ThemeplateGalleryViewModel @Inject constructor() : ViewModel() {

    private val _categories = MutableStateFlow<List<CloudCategory>>(emptyList())
    val categories: StateFlow<List<CloudCategory>> = _categories.asStateFlow()

    init {
        loadCategories()
    }

    private fun loadCategories() {
        // TODO: In real implementation, parse from remote API or local categories.json
        val mockCategories = listOf(
            CloudCategory("professional", "Chuyên nghiệp", 0),
            CloudCategory("cosmetics", "Mẫu Mỹ Phẩm", 1),
            CloudCategory("digital_life", "Đời sống số", 2),
            CloudCategory("selfie_food", "Mê ăn uống", 3)
        )
        _categories.value = mockCategories
    }
}

