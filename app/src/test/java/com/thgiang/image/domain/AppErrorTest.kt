package com.thgiang.image.domain

import com.thgiang.image.core.domain.AppError
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

/**
 * Unit tests for [AppError.from].
 */
class AppErrorTest {

    @Test
    fun from_outOfMemoryError_returnsOutOfMemory() {
        val error = AppError.from(OutOfMemoryError())
        assertEquals(AppError.OutOfMemory::class, error::class)
        assertEquals("Not enough memory to process image", error.userMessage)
    }

    @Test
    fun from_ioExceptionWithTooLarge_returnsImageTooLarge() {
        val error = AppError.from(IOException("image is too large"))
        assertEquals(AppError.ImageTooLarge::class, error::class)
    }

    @Test
    fun from_ioExceptionWithForeground_returnsMlKitError() {
        val error = AppError.from(IOException("foreground failed"))
        assertEquals(AppError.MlKitError::class, error::class)
    }

    @Test
    fun from_ioExceptionWithFileName_returnsImageNotFound() {
        val error = AppError.from(IOException("file name invalid"))
        assertEquals(AppError.ImageNotFound::class, error::class)
    }

    @Test
    fun from_ioExceptionGeneric_returnsProcessingFailed() {
        val error = AppError.from(IOException("read failed"))
        assertEquals(AppError.ProcessingFailed::class, error::class)
        assertEquals("read failed", error.userMessage)
    }

    @Test
    fun from_ioExceptionWithNullMessage_returnsProcessingFailedWithDefault() {
        val error = AppError.from(IOException())
        assertEquals(AppError.ProcessingFailed::class, error::class)
        assertEquals("Processing failed", error.userMessage)
    }

    @Test
    fun from_runtimeException_returnsUnknown() {
        val error = AppError.from(RuntimeException("unexpected"))
        assertEquals(AppError.Unknown::class, error::class)
        assertEquals("unexpected", error.userMessage)
    }

    @Test
    fun from_throwableWithNullMessage_returnsUnknownWithDefault() {
        val error = AppError.from(RuntimeException())
        assertEquals(AppError.Unknown::class, error::class)
        assertEquals("Something went wrong", error.userMessage)
    }
}
