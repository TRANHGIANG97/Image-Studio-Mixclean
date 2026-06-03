package com.thgiang.image.admin.ui

import com.thgiang.image.core.domain.model.template.CloudTemplate
import java.io.File

/**
 * Record đại diện cho một template được lưu trên disk,
 * kèm metadata về validation và file management.
 */
data class AdminTemplateRecord(
    val jsonFile: File,
    val template: CloudTemplate,
    val shareFile: File = jsonFile,
    val deleteTarget: File = jsonFile,
    val issues: List<String> = emptyList()
)
