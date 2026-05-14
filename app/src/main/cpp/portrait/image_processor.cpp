#include "image_processor.h"
#include "neon_kernels.h"

#include <android/log.h>
#include <algorithm>
#include <chrono>
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

// ─── Gamma LUT ────────────────────────────────────────────────────────────────
struct GammaLUT {
    float toLinear[256];
    uint8_t toSRGB[1024];
    GammaLUT() {
        for (int i = 0; i < 256; ++i) {
            float v = i / 255.0f;
            toLinear[i] = (v <= 0.04045f) ? (v / 12.92f)
                                          : std::pow((v + 0.055f) / 1.055f, 2.4f);
        }
        for (int i = 0; i < 1024; ++i) {
            float v = i / 1023.0f;
            if (v <= 0.0031308f) {
                toSRGB[i] = static_cast<uint8_t>(v * 12.92f * 255.0f + 0.5f);
            } else {
                toSRGB[i] = static_cast<uint8_t>(
                    ((1.055f * std::pow(v, 1.0f/2.4f) - 0.055f) * 255.0f + 0.5f));
            }
        }
    }
    inline float srgbToLinear(uint8_t c) const { return toLinear[c]; }
    inline uint8_t linearToSRGB(float v) const {
        int idx = static_cast<int>(v * 1023.0f + 0.5f);
        return toSRGB[std::max(0, std::min(1023, idx))];
    }
};
static const GammaLUT GAMMA;

// ─── FastBoxBlur ──────────────────────────────────────────────────────────────
class FastBoxBlur {
public:
    static void apply(uint8_t* pixels, int width, int height, int radius) {
        if (radius < 1 || width < MIN_DIMENSION || height < MIN_DIMENSION) return;

        const int wm  = width  - 1;
        const int hm  = height - 1;
        const int wh  = width  * height;
        const int div = radius * 2 + 1;

        std::vector<int> r(wh), g(wh), b(wh); // Non-static to ensure thread safety

        std::vector<int> xOutLut(width), xInLut(width);
        for (int x = 0; x < width; ++x) {
            xOutLut[x] = std::max(0, x - radius);
            xInLut[x]  = std::min(wm, x + radius + 1);
        }

#ifdef _OPENMP
#pragma omp parallel for schedule(static)
#endif
        for (int y = 0; y < height; ++y) {
            int rsum = 0, gsum = 0, bsum = 0;
            const int yi = y * width;

            for (int i = -radius; i <= radius; ++i) {
                const int x = std::max(0, std::min(wm, i));
                const uint8_t* p = pixels + (yi + x) * 4;
                rsum += p[0]; gsum += p[1]; bsum += p[2];
            }

            for (int x = 0; x < width; ++x) {
                r[yi + x] = rsum / div;
                g[yi + x] = gsum / div;
                b[yi + x] = bsum / div;

                const uint8_t* pOut = pixels + (yi + xOutLut[x]) * 4;
                const uint8_t* pIn  = pixels + (yi + xInLut[x])  * 4;
                rsum += pIn[0] - pOut[0];
                gsum += pIn[1] - pOut[1];
                bsum += pIn[2] - pOut[2];
            }
        }

        std::vector<int> yOutLut(height), yInLut(height);
        for (int y = 0; y < height; ++y) {
            yOutLut[y] = std::max(0, y - radius);
            yInLut[y]  = std::min(hm, y + radius + 1);
        }

        for (int x = 0; x < width; ++x) {
            int rsum = 0, gsum = 0, bsum = 0;

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

                rsum += r[yInLut[y] * width + x] - r[yOutLut[y] * width + x];
                gsum += g[yInLut[y] * width + x] - g[yOutLut[y] * width + x];
                bsum += b[yInLut[y] * width + x] - b[yOutLut[y] * width + x];
            }
        }
    }
};

// ─── VignetteProcessor ────────────────────────────────────────────────────────
class VignetteProcessor {
public:
    static void apply(uint8_t* pixels, int width, int height,
                      float darkenAlpha, bool enableVignette) {
        if (darkenAlpha <= 0.0f) return;
        const float alpha = std::min(darkenAlpha, 1.0f);
        if (!enableVignette) {
            applyUniform(pixels, width, height, alpha);
        } else {
            applyVignette(pixels, width, height, alpha);
        }
    }

private:
    static void applyUniform(uint8_t* pixels, int width, int height, float alpha) {
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

        constexpr float CENTER_DARKEN = 0.82f;
        constexpr float EDGE_EXTRA    = 0.18f;
        constexpr int   LUT_SIZE      = 1024;

        uint8_t distSqLUT[LUT_SIZE];
        for (int i = 0; i < LUT_SIZE; ++i) {
            const float normDistSq = i / static_cast<float>(LUT_SIZE - 1);
            const float dist       = std::sqrt(normDistSq);
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

// ─── Hybrid Composite ─────────────────────────────────────────────────────────
class CompositeProcessor {
public:
    static void apply(uint8_t* dst, const uint8_t* fg, int width, int height) {
        const size_t count = static_cast<size_t>(width) * height;

        bool needsGamma = false;
        // Sample ~1000 pixels uniformly to determine if gamma path is needed
        size_t step = std::max(static_cast<size_t>(1), count / 1000);
        for (size_t i = 0; i < count; i += step) {
            const uint8_t a = fg[i * 4 + 3];
            if (a > 10 && a < 245) {
                needsGamma = true;
                break;
            }
        }

        if (!needsGamma) {
#ifdef _OPENMP
#pragma omp parallel for schedule(static)
#endif
            for (int y = 0; y < height; ++y) {
                neon::compositeRow(dst + y * width * 4,
                                   fg  + y * width * 4,
                                   width);
            }
            return;
        }

        // Slow path: Gamma-correct with smoothstep for edge pixels
        std::vector<float> feather(count);
#ifdef _OPENMP
#pragma omp parallel for schedule(static)
#endif
        for (size_t i = 0; i < count; ++i) {
            float a = fg[i * 4 + 3] / 255.0f;
            feather[i] = a * a * (3.0f - 2.0f * a);
        }

#ifdef _OPENMP
#pragma omp parallel for schedule(static)
#endif
        for (int y = 0; y < height; ++y) {
            for (int x = 0; x < width; ++x) {
                const int idx = y * width + x;
                const uint8_t* f = fg + idx * 4;
                uint8_t* d = dst + idx * 4;
                const float w = feather[idx];

                if (w <= 0.0f) continue;
                if (w >= 1.0f) {
                    d[0] = f[0]; d[1] = f[1]; d[2] = f[2]; d[3] = f[3];
                    continue;
                }

                const float fgLinR = GAMMA.srgbToLinear(f[0]);
                const float fgLinG = GAMMA.srgbToLinear(f[1]);
                const float fgLinB = GAMMA.srgbToLinear(f[2]);
                const float bgLinR = GAMMA.srgbToLinear(d[0]);
                const float bgLinG = GAMMA.srgbToLinear(d[1]);
                const float bgLinB = GAMMA.srgbToLinear(d[2]);

                d[0] = GAMMA.linearToSRGB(fgLinR * w + bgLinR * (1.0f - w));
                d[1] = GAMMA.linearToSRGB(fgLinG * w + bgLinG * (1.0f - w));
                d[2] = GAMMA.linearToSRGB(fgLinB * w + bgLinB * (1.0f - w));
                d[3] = static_cast<uint8_t>(
                    (f[3] / 255.0f * w + d[3] / 255.0f * (1.0f - w)) * 255.0f + 0.5f);
            }
        }
    }
};

} // anonymous namespace

// ─── Public API — Blur-only ────────────────────────────────────────────────────

uint8_t* applyBlurOnly(const uint8_t* srcPixels,
                       int width, int height,
                       float blurRadius) {
    if (!srcPixels) {
        LOGE("applyBlurOnly: srcPixels is null");
        return nullptr;
    }
    if (width < MIN_DIMENSION || height < MIN_DIMENSION ||
        width > MAX_DIMENSION || height > MAX_DIMENSION) {
        LOGE("applyBlurOnly: invalid dimensions %dx%d", width, height);
        return nullptr;
    }

    const size_t bufSize = static_cast<size_t>(width) * height * 4u;
    if (bufSize > MAX_BUFFER_BYTES) {
        LOGE("applyBlurOnly: buffer too large (%zu MB)", bufSize >> 20);
        return nullptr;
    }

    const auto tStart = std::chrono::steady_clock::now();

    auto* result = new (std::nothrow) uint8_t[bufSize];
    if (!result) {
        LOGE("applyBlurOnly: OOM allocating %dx%d buffer", width, height);
        return nullptr;
    }
    std::memcpy(result, srcPixels, bufSize);

    const int blurR = static_cast<int>(
        std::max(0.0f, std::min(blurRadius, static_cast<float>(MAX_BLUR_RADIUS))));
    const auto tAlloc = std::chrono::steady_clock::now();

    if (blurR > 0) {
        FastBoxBlur::apply(result, width, height, blurR);
    }
    const auto tEnd = std::chrono::steady_clock::now();

    const auto ms = [](auto start, auto end) {
        return std::chrono::duration_cast<std::chrono::microseconds>(end - start).count() / 1000.0f;
    };

    LOGI("BlurOnly %dx%d blurR=%d: alloc=%.2fms blur=%.2fms total=%.2fms",
         width, height, blurR,
         ms(tStart, tAlloc), ms(tAlloc, tEnd),
         ms(tStart, tEnd));

    return result;
}

void freeBlurResult(uint8_t* ptr) {
    delete[] ptr;
}

// ─── Alpha-aware box blur (for subject blur) ─────────────────────────────────
// Unpremultiplies in linear space → blurs RGBA → premultiplies back.
// Prevents dark fringes at semi-transparent edges that occur when premultiplied
// foreground color bleeds into transparent background pixels during blur.
class AlphaAwareBoxBlur {
public:
    static void apply(uint8_t* pixels, int width, int height, int radius) {
        if (radius < 1 || width < MIN_DIMENSION || height < MIN_DIMENSION) return;

        const int wm = width  - 1;
        const int hm = height - 1;
        const int wh = width  * height;
        const int div = radius * 2 + 1;

        // Precompute sliding-window LUTs
        std::vector<int> xOutLut(width), xInLut(width);
        for (int x = 0; x < width; ++x) {
            xOutLut[x] = std::max(0, x - radius);
            xInLut[x]  = std::min(wm, x + radius + 1);
        }
        std::vector<int> yOutLut(height), yInLut(height);
        for (int y = 0; y < height; ++y) {
            yOutLut[y] = std::max(0, y - radius);
            yInLut[y]  = std::min(hm, y + radius + 1);
        }

        // Step 1: Unpremultiply RGB by alpha in linear space.
        // A semi-transparent edge pixel (R=60, A=128) is actually R=120 in true color.
        // Blurring true colors prevents dark fringe from mixing with transparent neighbors.
        for (int i = 0; i < wh; ++i) {
            uint8_t* p = pixels + i * 4;
            const float a = p[3] / 255.0f;
            if (a > 0.001f && a < 0.999f) {
                const float invA = 1.0f / a;
                float r = GAMMA.srgbToLinear(p[0]) * invA;
                float g = GAMMA.srgbToLinear(p[1]) * invA;
                float b = GAMMA.srgbToLinear(p[2]) * invA;
                p[0] = GAMMA.linearToSRGB(std::min(1.0f, r));
                p[1] = GAMMA.linearToSRGB(std::min(1.0f, g));
                p[2] = GAMMA.linearToSRGB(std::min(1.0f, b));
            }
        }

        // Step 2: Separable box blur on all 4 channels
        std::vector<int> r(wh), g(wh), b(wh), a(wh);

        // Horizontal pass
#ifdef _OPENMP
#pragma omp parallel for schedule(static)
#endif
        for (int y = 0; y < height; ++y) {
            int rsum = 0, gsum = 0, bsum = 0, asum = 0;
            const int yi = y * width;
            for (int i = -radius; i <= radius; ++i) {
                const int x = std::max(0, std::min(wm, i));
                const uint8_t* p = pixels + (yi + x) * 4;
                rsum += p[0]; gsum += p[1]; bsum += p[2]; asum += p[3];
            }
            for (int x = 0; x < width; ++x) {
                const int idx = yi + x;
                r[idx] = rsum / div;
                g[idx] = gsum / div;
                b[idx] = bsum / div;
                a[idx] = asum / div;
                const uint8_t* pOut = pixels + (yi + xOutLut[x]) * 4;
                const uint8_t* pIn  = pixels + (yi + xInLut[x])  * 4;
                rsum += pIn[0] - pOut[0];
                gsum += pIn[1] - pOut[1];
                bsum += pIn[2] - pOut[2];
                asum += pIn[3] - pOut[3];
            }
        }

        // Vertical pass + premultiply back
        for (int x = 0; x < width; ++x) {
            int rsum = 0, gsum = 0, bsum = 0, asum = 0;
            for (int i = -radius; i <= radius; ++i) {
                const int y = std::max(0, std::min(hm, i));
                const int idx = y * width + x;
                rsum += r[idx]; gsum += g[idx]; bsum += b[idx]; asum += a[idx];
            }
            for (int y = 0; y < height; ++y) {
                const int idx = (y * width + x) * 4;
                const int blurredR = rsum / div;
                const int blurredG = gsum / div;
                const int blurredB = bsum / div;
                const int blurredA = asum / div;

                // Premultiply: out = blurred * alpha / 255
                pixels[idx + 0] = neon::div255(static_cast<uint32_t>(blurredR) * static_cast<uint32_t>(blurredA));
                pixels[idx + 1] = neon::div255(static_cast<uint32_t>(blurredG) * static_cast<uint32_t>(blurredA));
                pixels[idx + 2] = neon::div255(static_cast<uint32_t>(blurredB) * static_cast<uint32_t>(blurredA));
                pixels[idx + 3] = static_cast<uint8_t>(std::min(255, std::max(0, blurredA)));

                rsum += r[yInLut[y] * width + x] - r[yOutLut[y] * width + x];
                gsum += g[yInLut[y] * width + x] - g[yOutLut[y] * width + x];
                bsum += b[yInLut[y] * width + x] - b[yOutLut[y] * width + x];
                asum += a[yInLut[y] * width + x] - a[yOutLut[y] * width + x];
            }
        }
    }
};

// ─── Public API — Subject Blur ────────────────────────────────────────────────

uint8_t* applySubjectBlur(const uint8_t* srcPixels,
                          int width, int height,
                          float blurRadius) {
    if (!srcPixels) {
        LOGE("applySubjectBlur: srcPixels is null");
        return nullptr;
    }
    if (width < MIN_DIMENSION || height < MIN_DIMENSION ||
        width > MAX_DIMENSION || height > MAX_DIMENSION) {
        LOGE("applySubjectBlur: invalid dimensions %dx%d", width, height);
        return nullptr;
    }

    const size_t bufSize = static_cast<size_t>(width) * height * 4u;
    if (bufSize > MAX_BUFFER_BYTES) {
        LOGE("applySubjectBlur: buffer too large (%zu MB)", bufSize >> 20);
        return nullptr;
    }

    const auto tStart = std::chrono::steady_clock::now();

    auto* result = new (std::nothrow) uint8_t[bufSize];
    if (!result) {
        LOGE("applySubjectBlur: OOM allocating %dx%d buffer", width, height);
        return nullptr;
    }
    std::memcpy(result, srcPixels, bufSize);

    const int blurR = static_cast<int>(
        std::max(0.0f, std::min(blurRadius, static_cast<float>(MAX_BLUR_RADIUS))));
    const auto tAlloc = std::chrono::steady_clock::now();

    if (blurR > 0) {
        AlphaAwareBoxBlur::apply(result, width, height, blurR);
    }
    const auto tEnd = std::chrono::steady_clock::now();

    const auto ms = [](auto start, auto end) {
        return std::chrono::duration_cast<std::chrono::microseconds>(end - start).count() / 1000.0f;
    };

    LOGI("SubjectBlur %dx%d blurR=%d: alloc=%.2fms alphaBlur=%.2fms total=%.2fms",
         width, height, blurR,
         ms(tStart, tAlloc), ms(tAlloc, tEnd),
         ms(tStart, tEnd));

    return result;
}

void freeSubjectBlurResult(uint8_t* ptr) {
    delete[] ptr;
}

// ─── Public API — Portrait ────────────────────────────────────────────────────

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

    const auto tStart = std::chrono::steady_clock::now();

    auto* result = new (std::nothrow) uint8_t[bufSize];
    if (!result) {
        LOGE("processPortrait: OOM allocating %dx%d buffer", width, height);
        return nullptr;
    }
    std::memcpy(result, srcPixels, bufSize);

    const int blurR = static_cast<int>(
        std::max(0.0f, std::min(blurRadius, static_cast<float>(MAX_BLUR_RADIUS))));
    const auto tAlloc = std::chrono::steady_clock::now();

    if (blurR > 0) {
        FastBoxBlur::apply(result, width, height, blurR);
    }
    const auto tBlur = std::chrono::steady_clock::now();

    VignetteProcessor::apply(result, width, height, darkenAlpha, vignette);
    const auto tDarken = std::chrono::steady_clock::now();

    if (fgPixels) {
        CompositeProcessor::apply(result, fgPixels, width, height);
    }
    const auto tEnd = std::chrono::steady_clock::now();

    const auto ms = [](auto start, auto end) {
        return std::chrono::duration_cast<std::chrono::microseconds>(end - start).count() / 1000.0f;
    };

    LOGI("Portrait %dx%d blurR=%d: alloc=%.2fms blur=%.2fms darken=%.2fms composite=%.2fms total=%.2fms",
         width, height, blurR,
         ms(tStart, tAlloc), ms(tAlloc, tBlur), ms(tBlur, tDarken), ms(tDarken, tEnd),
         ms(tStart, tEnd));

    return result;
}

void freePortraitResult(uint8_t* ptr) {
    delete[] ptr;
}
