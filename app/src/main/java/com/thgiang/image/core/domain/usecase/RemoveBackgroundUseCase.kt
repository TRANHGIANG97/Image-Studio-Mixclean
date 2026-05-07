package com.thgiang.image.core.domain.usecase

import android.net.Uri
import com.thgiang.image.core.data.backgroundremove.BackgroundRemovalOutput
import com.thgiang.image.core.data.backgroundremove.BackgroundRemoverRepository
import javax.inject.Inject

class RemoveBackgroundUseCase @Inject constructor(
    private val repository: BackgroundRemoverRepository
) {
    suspend operator fun invoke(imageUri: Uri): Result<BackgroundRemovalOutput> {
        return repository.removeBackground(imageUri)
    }
}
