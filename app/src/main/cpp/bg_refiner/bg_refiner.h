#ifndef BG_REFINER_H
#define BG_REFINER_H

#include <cstdint>

/**
 * Refine foreground alpha channel using erosion + color-guided feathering + edge blur.
 *
 * Operates in-place on fgPixels.
 *
 * @param fgPixels   RGBA_8888 foreground pixel buffer (ML Kit result) — alpha is refined in-place
 * @param origPixels RGBA_8888 original image pixel buffer (color guidance)
 * @param width      Image width in pixels
 * @param height     Image height in pixels
 */
void refineForegroundAlpha(uint8_t* fgPixels, const uint8_t* origPixels,
                           int width, int height);

#endif // BG_REFINER_H
