package com.thgiang.image.studio.ui.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thgiang.image.core.domain.model.template.CloudCategory
import com.thgiang.image.core.domain.model.template.TemplateCategorySlug
import com.thgiang.image.studio.data.CloudTemplateRemoteRepository
import com.thgiang.image.studio.data.RemoteTemplateRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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

    /** True when the last template fetch failed — the grid shows a retry state instead of an empty grid. */
    private val _templatesLoadFailed = MutableStateFlow(false)
    val templatesLoadFailed: StateFlow<Boolean> = _templatesLoadFailed.asStateFlow()

    private var lastRequestedCategoryId: String? = null

    init {
        loadCategories()
    }

    fun loadTemplatesForCategory(categoryId: String) {
        lastRequestedCategoryId = categoryId
        viewModelScope.launch(Dispatchers.IO) {
            _loadingRemoteTemplates.value = true
            _templatesLoadFailed.value = false
            runCatching {
                cloudTemplateRepository.fetchTemplatesForCategory(categoryId)
            }.onSuccess { remoteItems ->
                _remoteTemplates.value = remoteItems
            }.onFailure { error ->
                android.util.Log.e(TAG, "Failed to load remote templates", error)
                _remoteTemplates.value = emptyList()
                _templatesLoadFailed.value = true
            }
            _loadingRemoteTemplates.value = false
        }
    }

    fun retryLoadTemplates() {
        lastRequestedCategoryId?.let { loadTemplatesForCategory(it) }
    }

    private fun loadCategories() {
        viewModelScope.launch(Dispatchers.IO) {
            _loadingRemoteTemplates.value = true
            runCatching {
                cloudTemplateRepository.fetchCategories()
            }.onSuccess { remoteCategories ->
                val candidates = remoteCategories.filter { it.order > 0 }
                if (applyCategoriesWithTemplates(candidates)) return@onSuccess
                loadFallbackData()
            }.onFailure { error ->
                android.util.Log.e(TAG, "Failed to load remote categories", error)
                loadFallbackData()
            }
            _loadingRemoteTemplates.value = false
        }
    }

    private suspend fun applyCategoriesWithTemplates(
        candidates: List<CloudCategory>,
    ): Boolean {
        val resolved = resolveCategoriesWithTemplates(candidates) ?: return false
        val (categories, firstTemplates, firstCategoryId) = resolved
        _categories.value = categories
        _remoteTemplates.value = firstTemplates
        _templatesLoadFailed.value = false
        lastRequestedCategoryId = firstCategoryId
        return true
    }

    private suspend fun resolveCategoriesWithTemplates(
        candidates: List<CloudCategory>,
    ): Triple<List<CloudCategory>, List<RemoteTemplateRow>, String>? {
        if (candidates.isEmpty()) return null

        val nonEmpty = coroutineScope {
            candidates.map { category ->
                async {
                    val templates = runCatching {
                        cloudTemplateRepository.fetchTemplatesForCategory(category.id)
                    }.getOrDefault(emptyList())
                    category to templates
                }
            }.awaitAll()
        }.filter { (_, templates) -> templates.isNotEmpty() }

        if (nonEmpty.isEmpty()) return null

        val categories = nonEmpty.map { (category, _) -> category }
        val firstCategory = nonEmpty.first()
        return Triple(categories, firstCategory.second, firstCategory.first.id)
    }

    private suspend fun loadFallbackData() {
        if (!applyCategoriesWithTemplates(fallbackCategories())) {
            _categories.value = emptyList()
            _remoteTemplates.value = emptyList()
            lastRequestedCategoryId = null
        }
    }

    private fun mergeWithFallbackCategories(remoteCategories: List<CloudCategory>): List<CloudCategory> {
        val fallback = fallbackCategories()
        val fallbackIds = fallback.map { it.id }.toSet()
        val defaultCategoryIds = setOf("professional", "cosmetics", "digital_life", "selfie_food")
        return buildList {
            addAll(fallback)
            addAll(
                remoteCategories.filterNot { remote ->
                    remote.id in fallbackIds || TemplateCategorySlug.slugFromCategoryName(remote.name) != null
                }
            )
        }
    }

    private fun fallbackCategories(): List<CloudCategory> = listOf(
        CloudCategory("professional", "Thời trang", 0),
        CloudCategory("cosmetics", "Mẫu Mỹ Phẩm", 1),
        CloudCategory("digital_life", "Đời sống số", 2),
        CloudCategory("selfie_food", "Mê ăn uống", 3),
    )

    private companion object {
        const val TAG = "ThemeplateGalleryVM"
    }
}
