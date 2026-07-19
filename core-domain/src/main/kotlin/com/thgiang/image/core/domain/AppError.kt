package com.thgiang.image.core.domain
sealed class AppError(val userMessage: String) {
    data class ImageNotFound(val message: String = "Cannot read image file") : AppError(message)
    data class ProcessingFailed(val message: String = "Background removal failed") : AppError(message)
    data class ImageTooLarge(val message: String = "Image is too large to process") : AppError(message)
    data class OutOfMemory(val message: String = "Not enough memory") : AppError(message)
    data class SaveFailed(val message: String = "Failed to save image") : AppError(message)
    data class MlKitError(val message: String = "AI processing error") : AppError(message)
    data class Unknown(val message: String = "Something went wrong") : AppError(message)

    companion object {
        fun from(throwable: Throwable): AppError = when {
            throwable is OutOfMemoryError -> OutOfMemory("Not enough memory to process image")
            throwable.message?.contains("INSUFFICIENT_HEAP", ignoreCase = true) == true ->
                OutOfMemory("Not enough memory. Close other apps and try again.")
            throwable.message?.contains("too large", ignoreCase = true) == true -> ImageTooLarge()
            throwable is java.io.IOException -> when {
                throwable.message?.contains("foreground", ignoreCase = true) == true -> MlKitError()
                throwable.message?.contains("file name", ignoreCase = true) == true -> ImageNotFound()
                else -> ProcessingFailed(throwable.localizedMessage ?: "Processing failed")
            }
            else -> Unknown(throwable.localizedMessage ?: "Something went wrong")
        }
    }
}




