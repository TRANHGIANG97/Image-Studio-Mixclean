#include "bg_refiner.h"

#include <android/log.h>
#include <algorithm>
#include <cmath>
#include <cstring>
#include <vector>

#ifdef _OPENMP
#include <omp.h>
#endif

#define LOG_TAG "BgRefiner"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

// ─── Constants ────────────────────────────────────────────────────────────────
constexpr int   ERODE_RADIUS    = 1;
constexpr float TRI_BACKGROUND  = 0.05f;

// ─── 1D separable Gaussian blur on float array ───────────────────────────────
// Mirrors Kotlin MlKitBackgroundRemoverRepository.gaussianBlur()
void gaussianBlur(const std::vector<float>& input,
                  std::vector<float>& output,
                  int width, int height, float radius) {
    if (radius <= 0.0f || input.empty()) {
        if (output.data() != input.data())
            std::memcpy(output.data(), input.data(), sizeof(float) * input.size());
        return;
    }

    const float sigma = radius / 2.0f;
    int kernelSize = static_cast<int>(std::ceil(radius * 3.0f));
    if (kernelSize % 2 == 0) ++kernelSize;
    if (kernelSize < 1) kernelSize = 1;

    std::vector<float> kernel(kernelSize);
    const int center = kernelSize / 2;
    float sum = 0.0f;
    for (int i = 0; i < kernelSize; ++i) {
        const float x = static_cast<float>(i - center);
        kernel[i] = std::exp(-(x * x) / (2.0f * sigma * sigma));
        sum += kernel[i];
    }
    const float invSum = 1.0f / sum;
    for (int i = 0; i < kernelSize; ++i) kernel[i] *= invSum;

    // Horizontal pass
    std::vector<float> horizontal(static_cast<size_t>(width) * height);
    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            float acc = 0.0f;
            for (int k = 0; k < kernelSize; ++k) {
                const int sx = std::max(0, std::min(width - 1, x + k - center));
                acc += input[y * width + sx] * kernel[k];
            }
            horizontal[y * width + x] = acc;
        }
    }

    // Vertical pass
    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            float acc = 0.0f;
            for (int k = 0; k < kernelSize; ++k) {
                const int sy = std::max(0, std::min(height - 1, y + k - center));
                acc += horizontal[sy * width + x] * kernel[k];
            }
            output[y * width + x] = acc;
        }
    }
}

// ─── Min-filter erosion + joint bilateral filter feathering ──────────────────
// Mirrors Kotlin MlKitBackgroundRemoverRepository.erodeAndFeatherAlpha()
void erodeAndFeatherAlpha(const std::vector<float>& alpha,
                          std::vector<float>& result,
                          int width, int height,
                          const uint8_t* guidancePixels) {
    if (width <= 0 || height <= 0) return;

    // Pass 1: min-filter erosion
    std::vector<float> eroded(static_cast<size_t>(width) * height);
    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            float minA = alpha[y * width + x];
            const int y0 = std::max(0, y - ERODE_RADIUS);
            const int y1 = std::min(height - 1, y + ERODE_RADIUS);
            const int x0 = std::max(0, x - ERODE_RADIUS);
            const int x1 = std::min(width - 1, x + ERODE_RADIUS);
            for (int ny = y0; ny <= y1; ++ny) {
                for (int nx = x0; nx <= x1; ++nx) {
                    minA = std::min(minA, alpha[ny * width + nx]);
                }
            }
            eroded[y * width + x] = minA;
        }
    }

    // Pass 2: feather transition via joint bilateral filter (7×7 kernel)
    constexpr int HALF_K = 3;
    const float sigmaS = std::max(1.5f, std::min(width, height) / 200.0f);
    constexpr float sigmaR = 0.15f;
    constexpr float invSigmaR2 = -0.5f / (sigmaR * sigmaR * 255.0f * 255.0f);

    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            const int idx = y * width + x;
            const float origA = alpha[idx];
            const float erodedA = eroded[idx];

            // Definite FG or BG → keep eroded
            if (origA < TRI_BACKGROUND || erodedA >= origA - 0.01f) {
                result[idx] = erodedA;
                continue;
            }

            // Center color (RGBA byte order: R=0, G=1, B=2)
            const uint8_t* c = guidancePixels + idx * 4;
            const int cR = c[0];
            const int cG = c[1];
            const int cB = c[2];

            float wSum = 0.0f;
            float totalW = 0.0f;
            const int y0 = std::max(0, y - HALF_K);
            const int y1 = std::min(height - 1, y + HALF_K);
            const int x0 = std::max(0, x - HALF_K);
            const int x1 = std::min(width - 1, x + HALF_K);

            for (int ny = y0; ny <= y1; ++ny) {
                const int dy = ny - y;
                for (int nx = x0; nx <= x1; ++nx) {
                    const int dx = nx - x;
                    const uint8_t* p = guidancePixels + (ny * width + nx) * 4;
                    const int dr = cR - p[0];
                    const int dg = cG - p[1];
                    const int db = cB - p[2];
                    const float rangeW = std::exp(invSigmaR2 * static_cast<float>(dr*dr + dg*dg + db*db));
                    const float spatialW = std::exp(-static_cast<float>(dx*dx + dy*dy) / (2.0f * sigmaS * sigmaS));
                    const float weight = spatialW * rangeW;

                    wSum += eroded[ny * width + nx] * weight;
                    totalW += weight;
                }
            }

            result[idx] = (totalW > 0.0f) ? (wSum / totalW) : erodedA;
        }
    }
}

// ─── Edge-aware blur: Sobel gradient detection + Gaussian blur + blend ───────
// Mirrors Kotlin MlKitBackgroundRemoverRepository.applyEdgeBlurToAlpha()
void applyEdgeBlurToAlpha(uint8_t* fgPixels, int width, int height) {
    if (width < 3 || height < 3) return;

    const size_t count = static_cast<size_t>(width) * height;
    std::vector<float> alpha(count);

    // Extract alpha from RGBA
    for (size_t i = 0; i < count; ++i) {
        alpha[i] = fgPixels[i * 4 + 3] / 255.0f;
    }

    // Blur radius: ~2.5% of short side
    const float blurRadius = std::max(8.0f, std::min(static_cast<float>(std::min(width, height)) / 40.0f, 50.0f));

    // Gaussian blur the alpha
    std::vector<float> blurred(count);
    gaussianBlur(alpha, blurred, width, height, blurRadius);

    // Edge mask via Sobel gradient
    std::vector<float> edgeWeight(count, 0.0f);
    for (int y = 1; y < height - 1; ++y) {
        for (int x = 1; x < width - 1; ++x) {
            const int idx = y * width + x;
            const float gx = std::abs(alpha[idx + 1] - alpha[idx - 1]);
            const float gy = std::abs(alpha[idx + width] - alpha[idx - width]);
            const float grad = std::min(1.0f, std::sqrt(gx * gx + gy * gy));
            // Boost gradient at transition pixels
            const bool isTransition = alpha[idx] > 0.05f && alpha[idx] < 0.95f;
            edgeWeight[idx] = std::max(grad, isTransition ? 0.3f : 0.0f);
        }
    }

    // Dilate edge mask
    std::vector<float> dilatedWeight(count);
    gaussianBlur(edgeWeight, dilatedWeight, width, height, blurRadius * 0.8f);

    // Blend: edge → blurred alpha, interior → original alpha
    for (size_t i = 0; i < count; ++i) {
        const float blend = std::min(1.0f, std::max(0.0f, dilatedWeight[i]));
        const float a = alpha[i];
        const bool isEdge = a > 0.05f && a < 0.95f;
        const float effectiveBlend = isEdge ? blend : 0.0f;
        const float result = a * (1.0f - effectiveBlend) + blurred[i] * effectiveBlend;
        fgPixels[i * 4 + 3] = static_cast<uint8_t>(std::min(255, std::max(0, static_cast<int>(result * 255.0f))));
    }
}

} // anonymous namespace

// ─── Public API ───────────────────────────────────────────────────────────────

void refineForegroundAlpha(uint8_t* fgPixels, const uint8_t* origPixels,
                           int width, int height) {
    if (!fgPixels || !origPixels || width <= 0 || height <= 0) {
        LOGE("refineForegroundAlpha: invalid params (fg=%p orig=%p %dx%d)",
             fgPixels, origPixels, width, height);
        return;
    }

    const size_t count = static_cast<size_t>(width) * height;

    LOGI("refineForegroundAlpha %dx%d", width, height);

    // Extract alpha from RGBA foreground (byte index 3)
    std::vector<float> alpha(count);
    for (size_t i = 0; i < count; ++i) {
        alpha[i] = fgPixels[i * 4 + 3] / 255.0f;
    }

    // Step 1: Erosion + feathering
    std::vector<float> refined(count);
    erodeAndFeatherAlpha(alpha, refined, width, height, origPixels);

    // Write refined alpha back to foreground
    for (size_t i = 0; i < count; ++i) {
        const int newAlpha = static_cast<int>(std::min(1.0f, std::max(0.0f, refined[i])) * 255.0f);
        fgPixels[i * 4 + 3] = static_cast<uint8_t>(std::min(255, std::max(0, newAlpha)));
    }

    // Step 2: Edge-aware blur on the refined alpha
    applyEdgeBlurToAlpha(fgPixels, width, height);
}
