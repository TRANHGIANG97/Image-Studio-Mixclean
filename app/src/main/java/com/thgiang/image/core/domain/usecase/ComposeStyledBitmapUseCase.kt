package com.thgiang.image.core.domain.usecase

import android.graphics.Bitmap
import com.thgiang.image.core.data.save.ImageSaveRepository
import com.thgiang.image.core.model.BorderRenderMode
import com.thgiang.image.core.model.HomeStyleRequest

import javax.inject.Inject

class ComposeStyledBitmapUseCase @Inject constructor(
    private val imageSaveRepository: ImageSaveRepository
) {
    suspend operator fun invoke(
        foreground: Bitmap,
        request: HomeStyleRequest
    ): Result<Bitmap> = when (request) {
        is HomeStyleRequest.Background -> runCatching {
            com.thgiang.image.core.util.ImageEffectProcessor.applyBackground(
                foreground = foreground,
                backgroundType = com.thgiang.image.core.util.ImageEffectProcessor.BackgroundType.PRESET,
                backgroundPreset = request.style
            ) ?: throw Exception("Failed to apply preset background")
        }
        is HomeStyleRequest.Border -> when (val mode = request.renderMode) {
            is BorderRenderMode.Solid -> imageSaveRepository.applyBorderToBitmap(
                bitmap = foreground,
                borderColorArgb = mode.colorArgb,
                borderWidthPx = request.borderWidthPx
            )
            is BorderRenderMode.Gradient -> imageSaveRepository.applyGradientBorderToBitmap(
                bitmap = foreground,
                colors = mode.colors,
                borderWidthPx = request.borderWidthPx
            )
        }
    }
}
