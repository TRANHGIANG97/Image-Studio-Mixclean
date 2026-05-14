#ifndef IMAGE_PROCESSOR_H
#define IMAGE_PROCESSOR_H

#include <cstdint>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Process a portrait image by applying blur, darken/vignette, and foreground composite.
 *
 * @param srcPixels  RGBA_8888 source pixel buffer (background)
 * @param fgPixels   RGBA_8888 foreground pixel buffer (can be nullptr to skip composite)
 * @param width      Image width in pixels
 * @param height     Image height in pixels
 * @param blurRadius Blur radius [0..25]. 0 = no blur.
 * @param darkenAlpha Darken strength [0..1]. 0 = no darkening.
 * @param vignette   true = radial vignette, false = uniform darken
 *
 * @return Newly allocated RGBA_8888 buffer. Caller must NOT free this directly.
 *         The buffer is owned by the JNI layer and freed after copying into a Bitmap.
 *         Returns nullptr on failure.
 */
uint8_t* processPortrait(const uint8_t* srcPixels,
                         const uint8_t* fgPixels,
                         int width, int height,
                         float blurRadius,
                         float darkenAlpha,
                         bool vignette);

/**
 * Frees a buffer returned by processPortrait().
 * Must be called if you use processPortrait() outside JNI context.
 */
void freePortraitResult(uint8_t* ptr);

/**
 * Apply blur only (no darken, no composite). Reuses the same FastBoxBlur engine.
 *
 * @param srcPixels  RGBA_8888 source pixel buffer
 * @param width      Image width in pixels
 * @param height     Image height in pixels
 * @param blurRadius Blur radius [0..25]. 0 = copy without blur.
 *
 * @return Newly allocated RGBA_8888 buffer. Caller must free via freeBlurResult().
 *         Returns nullptr on failure.
 */
uint8_t* applyBlurOnly(const uint8_t* srcPixels,
                       int width, int height,
                       float blurRadius);

/**
 * Frees a buffer returned by applyBlurOnly().
 */
void freeBlurResult(uint8_t* ptr);

/**
 * Alpha-aware subject blur: unpremultiply → blur RGBA → premultiply back.
 * Prevents dark fringes at semi-transparent edges by operating on true
 * (unpremultiplied) color values in linear space.
 *
 * @param srcPixels  RGBA_8888 source pixel buffer (foreground with alpha)
 * @param width      Image width in pixels
 * @param height     Image height in pixels
 * @param blurRadius Blur radius [0..25]. 0 = copy without blur.
 *
 * @return Newly allocated RGBA_8888 buffer. Caller must free via freeSubjectBlurResult().
 *         Returns nullptr on failure.
 */
uint8_t* applySubjectBlur(const uint8_t* srcPixels,
                          int width, int height,
                          float blurRadius);

/**
 * Frees a buffer returned by applySubjectBlur().
 */
void freeSubjectBlurResult(uint8_t* ptr);

#ifdef __cplusplus
}
#endif

#endif // IMAGE_PROCESSOR_H
