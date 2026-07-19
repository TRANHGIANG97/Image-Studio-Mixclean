package com.thgiang.image.core.data.backgroundremove

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MlKitMemoryBudgetTest {

    @Test
    fun maxSideForFreeHeap_scalesDownWhenHeapIsTight() {
        // ~25MB free (similar to crash report) → side well below 2048
        val side = MlKitMemoryBudget.maxSideForFreeHeap(25L * 1024 * 1024, bytesPerPixel = 24L)
        assertTrue(side < 900)
        assertTrue(side >= 384)
    }

    @Test
    fun resolveMaxProcessSide_neverExceedsPolicyOrAbsoluteCap() {
        val side = MlKitMemoryBudget.resolveMaxProcessSide(
            policyCap = 2048,
            sourceWidth = 4000,
            sourceHeight = 3000,
        )
        assertTrue(side <= 1792)
    }

    @Test
    fun mlKitRefusalReason_whenHeapWouldBeExceeded_returnsReason() {
        val reason = MlKitMemoryBudget.mlKitRefusalReason(4096, 4096)
        assertNotNull(reason)
        assertTrue(reason!!.contains(MlKitMemoryBudget.ERROR_INSUFFICIENT_HEAP))
    }

    @Test
    fun canRunSubjectSegmentation_smallSize_passesOnJvm() {
        assertTrue(MlKitMemoryBudget.canRunSubjectSegmentation(384, 384))
    }

    @Test
    fun canRunSelfieSegmentation_smallSize_passesOnJvm() {
        assertTrue(MlKitMemoryBudget.canRunSelfieSegmentation(384, 384))
    }

    @Test
    fun canRunAnySegmentationAtAll_passesOnJvm() {
        assertTrue(MlKitMemoryBudget.canRunAnySegmentationAtAll())
    }

    @Test
    fun isOutOfMemory_detectsWrappedError() {
        val wrapped = RuntimeException("failed", OutOfMemoryError("allocate"))
        assertTrue(MlKitMemoryBudget.isOutOfMemory(wrapped))
    }
}
