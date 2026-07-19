package com.thgiang.image.core.data.backgroundremove

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MlKitDeviceSupportTest {

    @Test
    fun isUnsupportedOrTimeout_detectsDeviceUnsupported() {
        assertTrue(
            MlKitDeviceSupport.isUnsupportedOrTimeout(
                IllegalStateException(MlKitDeviceSupport.ERROR_DEVICE_UNSUPPORTED),
            ),
        )
    }

    @Test
    fun isUnsupportedOrTimeout_detectsSegmentationTimeout() {
        assertTrue(
            MlKitDeviceSupport.isUnsupportedOrTimeout(
                IllegalStateException(MlKitDeviceSupport.ERROR_SEGMENTATION_TIMEOUT),
            ),
        )
    }

    @Test
    fun isUnsupportedOrTimeout_detectsCoroutineTimeoutMessage() {
        assertTrue(
            MlKitDeviceSupport.isUnsupportedOrTimeout(
                IllegalStateException("Timed out waiting for 45000 ms"),
            ),
        )
    }

    @Test
    fun isUnsupportedOrTimeout_ignoresLowMemory() {
        assertFalse(
            MlKitDeviceSupport.isUnsupportedOrTimeout(
                IllegalStateException(MlKitMemoryBudget.ERROR_INSUFFICIENT_HEAP),
            ),
        )
    }
}
