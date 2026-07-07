package com.thgiang.image.feature.home.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thgiang.image.R
import com.thgiang.image.core.domain.model.template.TemplateCategorySlug
import com.thgiang.image.core.domain.usecase.BorderStyleMapper
import com.thgiang.image.core.domain.usecase.CacheBitmapUseCase
import com.thgiang.image.core.domain.usecase.ComposeStyledBitmapUseCase
import com.thgiang.image.core.domain.usecase.ProcessImageWithStyleUseCase
import com.thgiang.image.core.model.BorderPresetStyle
import com.thgiang.image.core.model.HomeStyleRequest
import com.thgiang.image.core.model.PresetStyle
import com.thgiang.image.core.model.QuickToolAction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.thgiang.image.studio.data.CloudTemplateRemoteRepository
import com.thgiang.image.studio.model.StudioThemeplate
import com.thgiang.image.studio.model.StudioThemeplateSection
import com.thgiang.image.studio.model.StudioThemeplates
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.update


@HiltViewModel
class HomeViewModel @Inject constructor(
    private val application: Application,
    private val processImageWithStyleUseCase: ProcessImageWithStyleUseCase,
    private val composeStyledBitmapUseCase: ComposeStyledBitmapUseCase,
    private val cacheBitmapUseCase: CacheBitmapUseCase,
    private val draftManager: com.thgiang.image.feature.editor.model.DraftManager,
    private val appOpenAdManager: com.thgiang.image.core.ad.AppOpenAdManager,
    private val cloudTemplateRepository: CloudTemplateRemoteRepository,
) : ViewModel() {

    @Volatile
    private var useProQualityForRemoval: Boolean = false

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _selectedQuickTool = MutableStateFlow<QuickToolAction?>(null)
    val selectedQuickTool: StateFlow<QuickToolAction?> = _selectedQuickTool.asStateFlow()

    private var thicknessUpdateJob: Job? = null

    private val _messages = MutableSharedFlow<String>()
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            draftManager.migrateOldProject()
        }
        loadCloudTemplates()
    }

    fun onPresetSelected(style: PresetStyle) {
        _uiState.value = _uiState.value.copy(
            selectedPresetStyle = style,
            pendingStyleRequest = HomeStyleRequest.Background(style)
        )
    }

    fun onBorderPresetSelected(style: BorderPresetStyle) {
        val (renderMode, widthPx) = BorderStyleMapper.map(style)
        _uiState.value = _uiState.value.copy(
            selectedBorderPresetStyle = style,
            selectedPresetStyle = null,
            currentBorderThickness = widthPx,
            pendingStyleRequest = HomeStyleRequest.Border(
                style = style,
                renderMode = renderMode,
                borderWidthPx = widthPx
            )
        )
    }

    fun onQuickToolSelected(action: QuickToolAction) {
        _selectedQuickTool.value = action
    }

    fun consumeQuickTool() {
        _selectedQuickTool.value = null
    }

    fun consumePendingEditorUri() {
        _uiState.value = _uiState.value.copy(pendingEditorUri = null)
    }

    val pendingStyleRequest: HomeStyleRequest?
        get() = _uiState.value.pendingStyleRequest

    fun onImagePicked(uri: Uri?) {
        if (uri == null) {
            _uiState.value = _uiState.value.copy(pendingStyleRequest = null)
            return
        }

        val pendingRequest = _uiState.value.pendingStyleRequest
        if (pendingRequest != null) {
            processStyledImage(uri, pendingRequest)
            return
        }
    }

    fun setUseProQuality(usePro: Boolean) {
        useProQualityForRemoval = usePro
    }

    fun isAdDismissedRecently(): Boolean {
        return appOpenAdManager.wasAdDismissedRecently()
    }

    fun reset() {
        _uiState.value = _uiState.value.copy(
            selectedImageUri = null,
            processedImageUri = null,
            processedHasAlpha = false,
            progress = 0,
            isPresetFlowProcessing = false,
            pendingStyleRequest = null,
            selectedPresetStyle = null,
            selectedBorderPresetStyle = null,
            currentBorderThickness = null,
            pureForegroundUri = null
        )
    }

    private fun processStyledImage(uri: Uri, request: HomeStyleRequest) {
        _uiState.value = _uiState.value.copy(
            selectedImageUri = uri,
            processedImageUri = null,
            processedHasAlpha = false,
            isProcessing = true,
            isPresetFlowProcessing = true,
            progress = 10,
            pendingStyleRequest = null
        )

        viewModelScope.launch {
            val result = processImageWithStyleUseCase(uri, request) { progress ->
                _uiState.value = _uiState.value.copy(progress = progress)
            }

            result.fold(
                onSuccess = { output ->
                    _uiState.value = _uiState.value.copy(
                        processedImageUri = output.processedUri,
                        pureForegroundUri = output.foregroundUri,
                        processedHasAlpha = false,
                        progress = 100,
                        isProcessing = false,
                        isPresetFlowProcessing = false,
                        pendingEditorUri = if (output.openEditorAfter) output.processedUri else null
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        isPresetFlowProcessing = false
                    )
                    emitMessage(error.message ?: application.getString(R.string.edit_image_failed))
                }
            )
        }
    }

    fun onBorderThicknessChanged(thickness: Int) {
        val current = _uiState.value
        val pureUri = current.pureForegroundUri ?: return
        val style = current.selectedBorderPresetStyle ?: return

        _uiState.value = current.copy(currentBorderThickness = thickness)

        thicknessUpdateJob?.cancel()
        thicknessUpdateJob = viewModelScope.launch(Dispatchers.IO) {
            delay(250)

            val bitmap = application.contentResolver.openInputStream(pureUri)?.use { inputStream ->
                android.graphics.BitmapFactory.decodeStream(inputStream)
            }

            if (bitmap != null) {
                val request = HomeStyleRequest.Border(
                    style = style,
                    renderMode = BorderStyleMapper.map(style).first,
                    borderWidthPx = thickness
                )
                val composed = composeStyledBitmapUseCase(bitmap, request).getOrNull()
                bitmap.recycle()

                if (composed != null) {
                    val newCachedUri = cacheBitmapUseCase(composed).getOrNull()
                    composed.recycle()
                    if (newCachedUri != null) {
                        _uiState.value = _uiState.value.copy(
                            processedImageUri = newCachedUri
                        )
                        return@launch
                    }
                }
            }
            emitMessage(application.getString(R.string.edit_image_failed))
        }
    }

    private fun emitMessage(message: String) {
        viewModelScope.launch {
            _messages.emit(message)
        }
    }

    /** Force refresh từ server, bỏ qua cache. Gọi khi user pull-to-refresh. */
    fun refreshTemplates() {
        cloudTemplateRepository.invalidateCache()
        loadCloudTemplates()
    }

    /** Gọi khi quay lại màn hình Home — chỉ tải từ server nếu chưa có dữ liệu trong phiên này. */
    fun ensureTemplatesLoaded() {
        val state = _uiState.value
        val hasData = state.cosmeticsTemplates.isNotEmpty() ||
            state.professionalTemplates.isNotEmpty() ||
            state.otherSections.isNotEmpty()
        if (!hasData && !state.isLoadingTemplates) {
            loadCloudTemplates()
        }
    }

    private fun loadCloudTemplates() {
        _uiState.update { it.copy(isLoadingTemplates = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val categories = runCatching {
                    cloudTemplateRepository.fetchCategories()
                }.getOrDefault(emptyList())

                val cosmeticsId = TemplateCategorySlug.resolveCategoryId(
                    TemplateCategorySlug.COSMETICS,
                    categories,
                )
                val professionalId = TemplateCategorySlug.resolveCategoryId(
                    TemplateCategorySlug.PROFESSIONAL,
                    categories,
                )

                val cosmeticsJob = async { fetchRemoteTemplates(cosmeticsId) }
                val professionalJob = async { fetchRemoteTemplates(professionalId) }

                // Tải song song toàn bộ các danh mục khác có cấu hình slug
                val otherCategories = categories.filter {
                    it.id != cosmeticsId && it.id != professionalId && it.slug?.isNotBlank() == true
                }
                val otherJobs = otherCategories.map { category ->
                    category to async { fetchRemoteTemplates(category.id) }
                }

                val cosmetics = cosmeticsJob.await()
                val professional = professionalJob.await()

                val otherSections = otherJobs.map { (category, job) ->
                    com.thgiang.image.studio.model.StudioThemeplateSection(
                        id = category.id,
                        titleResId = when (category.id) {
                            "digital_life" -> com.thgiang.image.studio.R.string.themeplate_professional_digital_life
                            "selfie_food" -> com.thgiang.image.studio.R.string.themeplate_professional_food_selfie
                            else -> 0
                        },
                        titleString = category.name,
                        themeplates = job.await()
                    )
                }.filter { it.themeplates.isNotEmpty() }

                _uiState.update {
                    it.copy(
                        cosmeticsTemplates = cosmetics,
                        professionalTemplates = professional,
                        otherSections = otherSections,
                        isLoadingTemplates = false
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("HomeVM", "Failed to load cloud templates", e)
                _uiState.update { it.copy(isLoadingTemplates = false) }
            }
        }
    }

    /**
     * Lấy danh sách template theo categoryId thông qua [CloudTemplateRemoteRepository].
     * Map RemoteTemplateRow → StudioThemeplate để hiển thị trên màn hình Home.
     */
    private fun fetchRemoteTemplates(categoryId: String): List<StudioThemeplate> {
        return runCatching {
            cloudTemplateRepository.fetchTemplatesForCategory(categoryId).map { row ->
                val objectSourceAssetPath = row.cloudTemplate.layers
                    .firstOrNull { it.type.equals("PLACEHOLDER_OBJECT", ignoreCase = true) }
                    ?.payload
                    ?.let { payload -> payload.imageUrl ?: payload.defaultImageUrl }
                StudioThemeplate(
                    id = row.id,
                    titleResId = com.thgiang.image.studio.R.string.themeplate_professional_watch,
                    assetPath = row.thumbnailUrl,
                    backgroundAssetPath = row.thumbnailUrl,
                    objectSourceAssetPath = objectSourceAssetPath,
                    accentColor = androidx.compose.ui.graphics.Color(0xFF7C4DFF),
                    category = categoryId,
                    titleString = row.title,
                    isPremium = row.isPremium,
                )
            }
        }.onFailure { e ->
            android.util.Log.e("HomeVM", "fetchRemoteTemplates failed for $categoryId", e)
        }.getOrDefault(emptyList())
    }
}
