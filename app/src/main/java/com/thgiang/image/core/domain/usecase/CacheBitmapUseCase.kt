package com.thgiang.image.core.domain.usecase

import android.graphics.Bitmap
import android.net.Uri
import com.thgiang.image.core.data.save.ImageSaveRepository
import javax.inject.Inject

class CacheBitmapUseCase @Inject constructor(
    private val imageSaveRepository: ImageSaveRepository
) {
    suspend operator fun invoke(bitmap: Bitmap): Result<Uri> {
        return imageSaveRepository.cacheBitmap(bitmap)
    }
}
