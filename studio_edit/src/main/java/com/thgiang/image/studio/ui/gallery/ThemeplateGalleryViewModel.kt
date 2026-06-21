package com.thgiang.image.studio.ui.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thgiang.image.core.domain.model.template.CloudCategory
import com.thgiang.image.studio.data.CloudTemplateRemoteRepository
import com.thgiang.image.studio.data.RemoteTemplateRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class ThemeplateGalleryViewModel @Inject constructor(
    private val cloudTemplateRepository: CloudTemplateRemoteRepository,
) : ViewModel() {

    private val _categories = MutableStateFlow<List<CloudCategory>>(fallbackCategories())
    val categories: StateFlow<List<CloudCategory>> = _categories.asStateFlow()

    private val _remoteTemplates = MutableStateFlow<List<RemoteTemplateRow>>(emptyList())
    val remoteTemplates: StateFlow<List<RemoteTemplateRow>> = _remoteTemplates.asStateFlow()

    private val _loadingRemoteTemplates = MutableStateFlow(false)
    val loadingRemoteTemplates: StateFlow<Boolean> = _loadingRemoteTemplates.asStateFlow()

    init {
        loadCategories()
    }

    fun loadTemplatesForCategory(categoryId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _loadingRemoteTemplates.value = true
            runCatching {
                cloudTemplateRepository.fetchTemplatesForCategory(categoryId)
            }.onSuccess { remoteItems ->
                _remoteTemplates.value = remoteItems
            }.onFailure { error ->
                android.util.Log.e(TAG, "Failed to load remote templates", error)
                _remoteTemplates.value = emptyList()
            }
            _loadingRemoteTemplates.value = false
        }
    }

    private fun loadCategories() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                cloudTemplateRepository.fetchCategories()
            }.onSuccess { remoteCategories ->
                val filtered = remoteCategories.filter { it.order > 0 }
                if (filtered.isNotEmpty()) {
                    _categories.value = filtered
                    loadTemplatesForCategory(filtered.first().id)
                } else {
                    loadFallbackData()
                }
            }.onFailure { error ->
                android.util.Log.e(TAG, "Failed to load remote categories", error)
                loadFallbackData()
            }
        }
    }

    private fun loadFallbackData() {
        val fallback = fallbackCategories()
        _categories.value = fallback
        loadTemplatesForCategory(fallback.firstOrNull()?.id.orEmpty())
    }

    private fun mergeWithFallbackCategories(remoteCategories: List<CloudCategory>): List<CloudCategory> {
        val fallback = fallbackCategories()
        val fallbackIds = fallback.map { it.id }.toSet()
        val defaultCategoryIds = setOf("professional", "cosmetics", "digital_life", "selfie_food")
        return buildList {
            addAll(fallback)
            addAll(
                remoteCategories.filterNot { remote ->
                    remote.id in fallbackIds || canonicalCategoryId(remote.name) in defaultCategoryIds
                }
            )
        }
    }

    private fun fallbackCategories(): List<CloudCategory> = listOf(
        CloudCategory("professional", "Chuyên nghiệp", 0),
        CloudCategory("cosmetics", "Mẫu Mỹ Phẩm", 1),
        CloudCategory("digital_life", "Đời sống số", 2),
        CloudCategory("selfie_food", "Mê ăn uống", 3),
    )

    private fun canonicalCategoryId(name: String): String? {
        val normalized = Normalizer.normalize(name, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")
            .trim()

        return when {
            "chuyen nghiep" in normalized -> "professional"
            "my pham" in normalized -> "cosmetics"
            "doi song so" in normalized -> "digital_life"
            "me an uong" in normalized || "dam me an uong" in normalized -> "selfie_food"
            else -> null
        }
    }

    private companion object {
        const val TAG = "ThemeplateGalleryVM"
    }
}
