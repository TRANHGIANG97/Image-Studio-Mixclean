package com.thgiang.image.core.data.backgroundremove

import android.graphics.Bitmap

/**
 * JNI bridge to the bg_refiner native library.
 *
 * Refines foreground alpha after ML Kit segmentation by applying:
 * - Min-filter erosion (strips background fringe)
 * - Joint bilateral filter feathering (smooths transition with color guidance)
 * - Edge-aware blur (removes aliasing at edges while preserving interior detail)
 *
 * The native library is loaded once into the process. All methods
 * operate in-place on the foreground bitmap's alpha channel.
 */
object BackgroundRefinerNative {

    private const val TAG = "BgRefinerNative"

    init {
        try {
            System.loadLibrary("bg_refiner")
        } catch (e: UnsatisfiedLinkError) {
            android.util.Log.e(TAG, "Failed to load bg_refiner native library", e)
        }
    }

    /**
     * Refine the alpha channel of [foreground] in-place using [original]
     * as color guidance for the joint bilateral filter.
     *
     * @param foreground ML Kit result bitmap (RGBA_8888) — alpha is refined in-place
     * @param original   Original image bitmap (RGBA_8888), same dimensions
     * @return true if refinement succeeded, false on error
     */
    @JvmStatic
    external fun nativeRefineForeground(
        foreground: Bitmap,
        original: Bitmap
    ): Boolean
}
