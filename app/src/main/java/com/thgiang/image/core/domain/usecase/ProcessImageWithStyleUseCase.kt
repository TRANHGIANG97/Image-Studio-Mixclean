package com.thgiang.image.core.domain.usecase

import android.net.Uri
import com.thgiang.image.core.model.HomeStyleRequest
import javax.inject.Inject

class ProcessImageWithStyleUseCase @Inject constructor(
    private val removeBackgroundUseCase: RemoveBackgroundUseCase,
    private val cacheBitmapUseCase: CacheBitmapUseCase,
    private val composeStyledBitmapUseCase: ComposeStyledBitmapUseCase
) {
    data class Output(
        val foregroundUri: Uri?,
        val processedUri: Uri,
        val openEditorAfter: Boolean
    )

    suspend operator fun invoke(
        sourceUri: Uri,
        request: HomeStyleRequest,
        onProgress: (Int) -> Unit = {}
    ): Result<Output> {
        onProgress(10)

        val output = removeBackgroundUseCase(sourceUri).getOrElse {
            return Result.failure(it)
        }

        try {
            val pureUri = cacheBitmapUseCase(output.foregroundToDisplay).getOrNull()
            onProgress(70)

            val composed = composeStyledBitmapUseCase(output.foregroundToDisplay, request).getOrElse {
                return Result.failure(it)
            }
            try {
                val cachedUri = cacheBitmapUseCase(composed).getOrElse {
                    return Result.failure(it)
                }
                val openEditorAfter =
                    request is HomeStyleRequest.Background || request is HomeStyleRequest.Border
                onProgress(100)

                return Result.success(
                    Output(
                        foregroundUri = pureUri,
                        processedUri = cachedUri,
                        openEditorAfter = openEditorAfter
                    )
                )
            } finally {
                if (!composed.isRecycled) composed.recycle()
            }
        } finally {
            if (!output.foregroundToDisplay.isRecycled) {
                output.foregroundToDisplay.recycle()
            }
            if (output.foregroundToSave !== output.foregroundToDisplay &&
                !output.foregroundToSave.isRecycled
            ) {
                output.foregroundToSave.recycle()
            }
        }
    }
}
