#include "image_processor.h"
#include "neon_kernels.h"

#include <android/log.h>
#include <algorithm>
#include <cmath>
#include <cstring>
#include <memory>
#include <vector>

#ifdef _OPENMP
#include <omp.h>
#endif

#define LOG_TAG "PortraitProcessor"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

constexpr int    MAX_BLUR_RADIUS  = 25;
constexpr int    MIN_DIMENSION    = 1;
constexpr int    MAX_DIMENSION    = 16384;
constexpr size_t MAX_BUFFER_BYTES = 512ULL * 1024 * 1024; // 512 MB

// ─── FastBoxBlur ──────────────────────────────────────────────────────────────
// Two-pass (horizontal then vertical) sliding-window box blur.
// Complexity O(width*height), memory O(width*height * 3 ints).
// Only RGB channels are blurred; Alpha is preserved.
class FastBoxBlur {
public:
    static void apply(uint8_t* pixels, int width, int height, int radius) {
        if (radius < 1 || width < MIN_DIMENSION || height < MIN_DIMENSION) return;

        const int wm  = width  - 1;
        const int hm  = height - 1;
        const int wh  = width  * height;
        const int div = radius * 2 + 1;

        // Intermediate sums from horizontal pass (not yet divided)
        std::vector<int> r(wh), g(wh), b(wh);

        // ── Horizontal pass ──────────────────────────────────────────────────
        for (int y = 0; y < height; ++y) {
            int rsum = 0, gsum = 0, bsum = 0;
            const int yi = y * width;

            // Seed the window
            for (int i = -radius; i <= radius; ++i) {
                const int x = std::max(0, std::min(wm, i));
                const uint8_t* p = pixels + (yi + x) * 4;
                rsum += p[0]; gsum += p[1]; bsum += p[2];
            }

            for (int x = 0; x < width; ++x) {
                // Store AVERAGE (divided by div), not raw sum.
                // The vertical pass will also divide by div — total divisor = div*div = correct.
                r[yi + x] = rsum / div;
                g[yi + x] = gsum / div;
                b[yi + x] = bsum / div;

                const int xOut = std::max(0,  x - radius);
                const int xIn  = std::min(wm, x + radius + 1);
                const uint8_t* pOut = pixels + (yi + xOut) * 4;
                const uint8_t* pIn  = pixels + (yi + xIn)  * 4;
                rsum += pIn[0] - pOut[0];
                gsum += pIn[1] - pOut[1];
                bsum += pIn[2] - pOut[2];
            }
        }

        // ── Vertical pass (writes divided result directly to pixels) ─────────
        for (int x = 0; x < width; ++x) {
            int rsum = 0, gsum = 0, bsum = 0;

            // Seed the window
            for (int i = -radius; i <= radius; ++i) {
                const int y = std::max(0, std::min(hm, i));
                rsum += r[y * width + x];
                gsum += g[y * width + x];
                bsum += b[y * width + x];
            }

            for (int y = 0; y < height; ++y) {
                const int idx = (y * width + x) * 4;
                pixels[idx + 0] = static_cast<uint8_t>(rsum / div);
                pixels[idx + 1] = static_cast<uint8_t>(gsum / div);
                pixels[idx + 2] = static_cast<uint8_t>(bsum / div);
                // pixels[idx + 3] = Alpha unchanged

                const int yOut = std::max(0,  y - radius);
                const int yIn  = std::min(hm, y + radius + 1);
                rsum += r[yIn * width + x] - r[yOut * width + x];
                gsum += g[yIn * width + x] - g[yOut * width + x];
                bsum += b[yIn * width + x] - b[yOut * width + x];
            }
        }
    }
};

// ─── VignetteProcessor ────────────────────────────────────────────────────────
// darkenAlpha semantic:
//   0.0 = no darkening at all (image unchanged)
//   1.0 = maximum darkening (center ~25% brightness, edges ~0%)
//
// FIX: The original code had the semantic INVERTED:
//   baseDark = darkenAlpha * 255 → then pixel *= baseDark/255
//   At darkenAlpha=0.16: pixel *= 0.16 → 84% darkened! WRONG.
//
// Correct approach:
//   brightnessFactor = (1 - darkenAlpha) → 0%=1.0 (full bright), 100%=0.0 (black)
//   For vignette: additionally darken edges relative to center, scaled by darkenAlpha
//   At center (dist=0): factor = 1 - darkenAlpha * 0.6
//   At edge   (dist=1): factor = 1 - darkenAlpha * (0.6 + 0.4) = 1 - darkenAlpha
//   Both scale to 0 at darkenAlpha=0 → no darkening anywhere ✓
class VignetteProcessor {
public:
    static void apply(uint8_t* pixels, int width, int height,
                      float darkenAlpha, bool enableVignette) {
        // Early-exit: no darkening at 0%
        if (darkenAlpha <= 0.0f) return;

        const float alpha = std::min(darkenAlpha, 1.0f);

        if (!enableVignette) {
            applyUniform(pixels, width, height, alpha);
        } else {
            applyVignette(pixels, width, height, alpha);
        }
    }

private:
    // Uniform darkening: darkenAlpha=0→no change, darkenAlpha=1→fully black
    static void applyUniform(uint8_t* pixels, int width, int height, float alpha) {
        // brightness = 1 - alpha  →  factor in [0..255]
        const uint8_t factor = static_cast<uint8_t>((1.0f - alpha) * 255.0f + 0.5f);
#ifdef _OPENMP
#pragma omp parallel for schedule(static)
#endif
        for (int y = 0; y < height; ++y) {
            neon::darkenRow(pixels + y * width * 4, width, factor);
        }
    }

    static void applyVignette(uint8_t* pixels, int width, int height, float alpha) {
        const float cx  = width  * 0.5f;
        const float cy  = height * 0.5f;
        const float maxDistSq    = cx * cx + cy * cy;
        const float invMaxDistSq = (maxDistSq > 0.0f) ? 1.0f / maxDistSq : 0.0f;

        // Build LUT indexed by normalised dist²
        // Brightness at pixel = 1 - (centerDarken + dist * edgeExtra)
        // centerDarken = alpha * 0.82  (center keeps 18% brightness at 100% alpha)
        // edgeExtra    = alpha * 0.18  (edges get additional darkening to reach 0%)
        constexpr float CENTER_DARKEN = 0.82f;
        constexpr float EDGE_EXTRA    = 0.18f;
        constexpr int   LUT_SIZE      = 1024;

        uint8_t distSqLUT[LUT_SIZE];
        for (int i = 0; i < LUT_SIZE; ++i) {
            const float normDistSq = i / static_cast<float>(LUT_SIZE - 1);
            const float dist       = std::sqrt(normDistSq); // 0=center, 1=corner
            const float totalDarken = alpha * (CENTER_DARKEN + dist * EDGE_EXTRA);
            const float brightness  = std::max(0.0f, 1.0f - totalDarken);
            distSqLUT[i] = static_cast<uint8_t>(brightness * 255.0f + 0.5f);
        }

#ifdef _OPENMP
#pragma omp parallel for schedule(static)
#endif
        for (int y = 0; y < height; ++y) {
            uint8_t* row    = pixels + y * width * 4;
            const float dy  = static_cast<float>(y) - cy;
            const float dy2 = dy * dy * invMaxDistSq;

            for (int x = 0; x < width; ++x) {
                const float dx      = static_cast<float>(x) - cx;
                const float normDSq = std::min(dx * dx * invMaxDistSq + dy2, 1.0f);
                const int   lutIdx  = static_cast<int>(normDSq * (LUT_SIZE - 1));
                const uint8_t factor = distSqLUT[lutIdx];

                uint8_t* p = row + x * 4;
                p[0] = neon::div255(static_cast<uint32_t>(p[0]) * factor);
                p[1] = neon::div255(static_cast<uint32_t>(p[1]) * factor);
                p[2] = neon::div255(static_cast<uint32_t>(p[2]) * factor);
            }
        }
    }
};


// ─── CompositeProcessor ───────────────────────────────────────────────────────
class CompositeProcessor {
public:
    static void apply(uint8_t* dst, const uint8_t* fg, int width, int height) {
#ifdef _OPENMP
#pragma omp parallel for schedule(static)
#endif
        for (int y = 0; y < height; ++y) {
            neon::compositeRow(dst + y * width * 4,
                               fg  + y * width * 4,
                               width);
        }
    }
};

} // anonymous namespace

// ─── Public API ───────────────────────────────────────────────────────────────

uint8_t* processPortrait(const uint8_t* srcPixels,
                         const uint8_t* fgPixels,
                         int width, int height,
                         float blurRadius,
                         float darkenAlpha,
                         bool vignette) {
    if (!srcPixels) {
        LOGE("processPortrait: srcPixels is null");
        return nullptr;
    }
    if (width < MIN_DIMENSION || height < MIN_DIMENSION ||
        width > MAX_DIMENSION || height > MAX_DIMENSION) {
        LOGE("processPortrait: invalid dimensions %dx%d", width, height);
        return nullptr;
    }

    const size_t bufSize = static_cast<size_t>(width) * height * 4u;
    if (bufSize > MAX_BUFFER_BYTES) {
        LOGE("processPortrait: buffer too large (%zu MB)", bufSize >> 20);
        return nullptr;
    }

    // Allocate result buffer (caller / JNI layer must free via freePortraitResult)
    auto* result = new (std::nothrow) uint8_t[bufSize];
    if (!result) {
        LOGE("processPortrait: OOM allocating %dx%d buffer", width, height);
        return nullptr;
    }
    std::memcpy(result, srcPixels, bufSize);

    // 1. Blur (RGB only, alpha preserved)
    const int blurR = static_cast<int>(
        std::max(0.0f, std::min(blurRadius, static_cast<float>(MAX_BLUR_RADIUS))));
    if (blurR > 0) {
        FastBoxBlur::apply(result, width, height, blurR);
    }

    // 2. Darken / Vignette  (FIX: skipped when darkenAlpha <= 0)
    VignetteProcessor::apply(result, width, height, darkenAlpha, vignette);

    // 3. Composite foreground (optional)
    if (fgPixels) {
        CompositeProcessor::apply(result, fgPixels, width, height);
    }

    LOGI("processPortrait: done %dx%d blur=%d darken=%.2f vignette=%d",
         width, height, blurR, darkenAlpha, static_cast<int>(vignette));

    return result;
}

void freePortraitResult(uint8_t* ptr) {
    delete[] ptr;
}
