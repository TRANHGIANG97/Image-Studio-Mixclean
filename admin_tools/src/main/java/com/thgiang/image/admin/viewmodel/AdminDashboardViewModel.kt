package com.thgiang.image.admin.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.thgiang.image.admin.ui.AdminTemplateRecord
import com.thgiang.image.admin.util.TemplateValidator
import com.thgiang.image.core.domain.model.template.CloudTemplate
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject

data class DashboardState(
    val templates: List<AdminTemplateRecord> = emptyList(),
    val isLoading: Boolean = true,
    val fileToDelete: File? = null,
    val dashboardMessage: String? = null,
    val searchQuery: String = "",
    val categoryFilter: String? = null, // null = show all
    val importProgress: Float? = null // null = no import; 0..1 = progress; -1 = indeterminate
) {
    /** Unique categories from loaded templates */
    val availableCategories: List<String>
        get() = templates.map { it.template.categoryId }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()

    /** Templates filtered by search query and selected category */
    val filteredTemplates: List<AdminTemplateRecord>
        get() {
            var result = templates
            val query = searchQuery.trim().lowercase()
            if (query.isNotEmpty()) {
                result = result.filter { record ->
                    record.template.metadata.title.lowercase().contains(query) ||
                    record.template.categoryId.lowercase().contains(query) ||
                    record.template.templateId.lowercase().contains(query)
                }
            }
            categoryFilter?.let { cat ->
                result = result.filter { it.template.categoryId == cat }
            }
            return result
        }
}

@HiltViewModel
class AdminDashboardViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    init {
        loadTemplates()
    }

    // ── Template Loading ──

    fun loadTemplates() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val result = withContext(Dispatchers.IO) { readTemplatesFromDisk() }
            _state.update { it.copy(templates = result, isLoading = false) }
        }
    }

    // ── Import ──

    fun importBundle(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, importProgress = -1f) }
            val message = withContext(Dispatchers.IO) {
                runCatching {
                    val importedName = unzipBundle(uri)
                    _state.update { it.copy(importProgress = 1f) }
                    "Imported $importedName"
                }.getOrElse { error ->
                    "Import failed: ${error.message}"
                }
            }
            val result = withContext(Dispatchers.IO) { readTemplatesFromDisk() }
            _state.update { it.copy(templates = result, isLoading = false, dashboardMessage = message, importProgress = null) }
        }
    }

    // ── Delete ──

    fun setFileToDelete(file: File?) {
        _state.update { it.copy(fileToDelete = file) }
    }

    fun confirmDelete() {
        val file = _state.value.fileToDelete ?: return
        viewModelScope.launch {
            _state.update { it.copy(fileToDelete = null, isLoading = true) }
            withContext(Dispatchers.IO) {
                if (file.isDirectory) {
                    File(file.parentFile, "${file.name}.zip").takeIf { it.exists() }?.delete()
                    file.deleteRecursively()
                } else {
                    file.delete()
                }
            }
            val result = withContext(Dispatchers.IO) { readTemplatesFromDisk() }
            _state.update { it.copy(templates = result, isLoading = false) }
        }
    }

    // ── Share ──

    fun shareFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = if (file.extension.equals("zip", ignoreCase = true)) "application/zip" else "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Chia sẻ Template"))
        } catch (e: Exception) {
            android.util.Log.e("AdminDashboardVM", "Failed to share file", e)
        }
    }

    // ── Search / Filter ──

    fun setSearchQuery(query: String) {
        _state.update { it.copy(searchQuery = query) }
    }

    fun setCategoryFilter(category: String?) {
        _state.update { it.copy(categoryFilter = category) }
    }

    fun clearMessage() {
        _state.update { it.copy(dashboardMessage = null) }
    }

    // ── Private I/O ──

    private fun readTemplatesFromDisk(): List<AdminTemplateRecord> {
        val dir = context.getExternalFilesDir(null)
        val rootJsonFiles = dir?.listFiles { file -> file.isFile && file.name.endsWith(".json") }?.toList().orEmpty()
        val bundleRoot = dir?.resolve("template_bundles")
        val bundleJsonFiles = bundleRoot
            ?.listFiles { file -> file.isDirectory }
            ?.mapNotNull { bundleDir -> bundleDir.resolve("template.json").takeIf { it.exists() } }
            .orEmpty()
        val files = rootJsonFiles + bundleJsonFiles

        return files.mapNotNull { file ->
            try {
                val json = file.readText()
                val template = Gson().fromJson(json, CloudTemplate::class.java)
                val bundleDir = file.parentFile
                val bundleZip = bundleDir
                    ?.takeIf { it.parentFile?.name == "template_bundles" }
                    ?.let { File(it.parentFile, "${it.name}.zip") }
                    ?.takeIf { it.exists() }
                AdminTemplateRecord(
                    jsonFile = file,
                    template = template,
                    shareFile = bundleZip ?: file,
                    deleteTarget = bundleDir?.takeIf { it.parentFile?.name == "template_bundles" } ?: file,
                    issues = TemplateValidator.validateTemplate(template, file)
                )
            } catch (e: Exception) {
                null
            }
        }.sortedByDescending { it.jsonFile.lastModified() }
    }

    private fun unzipBundle(uri: Uri): String {
        val bundleRoot = context.getExternalFilesDir(null)?.resolve("template_bundles")
            ?: throw IllegalStateException("External files directory is not available")
        bundleRoot.mkdirs()
        val bundleDir = File(bundleRoot, "import_${System.currentTimeMillis()}")
        bundleDir.mkdirs()
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val target = File(bundleDir, entry.name).canonicalFile
                    if (!target.path.startsWith(bundleDir.canonicalPath)) {
                        throw IllegalStateException("Unsafe zip entry: ${entry.name}")
                    }
                    if (entry.isDirectory) {
                        target.mkdirs()
                    } else {
                        target.parentFile?.mkdirs()
                        FileOutputStream(target).use { output -> zip.copyTo(output) }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } ?: throw IllegalStateException("Cannot open selected zip")

        val portableJson = bundleDir.resolve("template_portable.json")
        val localJson = bundleDir.resolve("template.json")
        val sourceJson = when {
            portableJson.exists() -> portableJson
            localJson.exists() -> localJson
            else -> bundleDir.walkTopDown().firstOrNull { it.isFile && it.name.endsWith(".json") }
                ?: throw IllegalStateException("Template JSON not found")
        }
        val template = Gson().fromJson(sourceJson.readText(), CloudTemplate::class.java)
        val normalized = TemplateValidator.normalizeImportedTemplate(template, bundleDir)
        localJson.writeText(Gson().toJson(normalized))

        val zipCopy = File(bundleRoot, "${bundleDir.name}.zip")
        context.contentResolver.openInputStream(uri)?.use { input ->
            zipCopy.outputStream().use { output -> input.copyTo(output) }
        }
        return bundleDir.name
    }
}
