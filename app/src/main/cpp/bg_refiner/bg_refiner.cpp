#include "bg_refiner.h"

#include <android/log.h>
#include <algorithm>
#include <cmath>
#include <cstring>
#include <vector>
#include <queue>
#include <random>

#ifdef _OPENMP
#include <omp.h>
#endif

#define LOG_TAG "BgRefiner"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

// ─── Constants ────────────────────────────────────────────────────────────────
constexpr int   ERODE_RADIUS    = 2;
constexpr float TRI_BACKGROUND  = 0.03f;
constexpr float TRI_FOREGROUND  = 0.97f;
constexpr float FEATHER_SIGMA_R = 0.22f;
constexpr int   HALF_K          = 4;

// ─── Gamma LUT (256 entries) ──────────────────────────────────────────────────
// Pre-computed toLinear and toSRGB to avoid pow() in hot loops
struct GammaLUT {
    float toLinear[256];
    uint8_t toSRGB[1024]; // 10-bit input: 0..1023 maps to 0..1.0

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
        idx = std::max(0, std::min(1023, idx));
        return toSRGB[idx];
    }
};
static const GammaLUT GAMMA;

// ─── 1D separable Gaussian blur ───────────────────────────────────────────────
void gaussianBlur(const std::vector<float>& input,
                  std::vector<float>& output,
                  int width, int height, float radius) {
    if (radius <= 0.0f || input.empty()) {
        if (output.data() != input.data())
            std::memcpy(output.data(), input.data(), sizeof(float) * input.size());
        return;
    }

    const float sigma = radius / 2.0f;
    int kernelSize = static_cast<int>(std::ceil(6.0f * sigma)); // ~3*radius
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

    std::vector<float> horizontal(static_cast<size_t>(width) * height);

#ifdef _OPENMP
#pragma omp parallel for schedule(static)
#endif
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

#ifdef _OPENMP
#pragma omp parallel for schedule(static)
#endif
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

// ─── True Jump Flood Distance Transform ───────────────────────────────────────
void sampleBackgroundColorJFA(const uint8_t* origPixels,
                               const std::vector<float>& alpha,
                               int width, int height,
                               std::vector<float>& bgLinR,
                               std::vector<float>& bgLinG,
                               std::vector<float>& bgLinB) {
    const size_t count = static_cast<size_t>(width) * height;
    bgLinR.resize(count);
    bgLinG.resize(count);
    bgLinB.resize(count);

    // Downscale for JFA (1/4 resolution) to save memory/compute
    const int dw = std::max(1, width / 4);
    const int dh = std::max(1, height / 4);
    const float scaleX = static_cast<float>(width) / dw;
    const float scaleY = static_cast<float>(height) / dh;

    // Ping-pong buffers to avoid race condition
    std::vector<int> nearestIdxA(dw * dh, -1);
    std::vector<int> nearestIdxB(dw * dh, -1);
    std::vector<float> nearestDistA(dw * dh, 1e9f);
    std::vector<float> nearestDistB(dw * dh, 1e9f);

    // Initialize seeds on downscaled grid
    for (int y = 0; y < height; y += 4) {
        for (int x = 0; x < width; x += 4) {
            int idx = y * width + x;
            if (alpha[idx] < 0.3f) {
                int dx = x / 4;
                int dy = y / 4;
                if (dx < dw && dy < dh) {
                    int didx = dy * dw + dx;
                    nearestIdxA[didx] = idx;
                    nearestDistA[didx] = 0.0f;
                }
            }
        }
    }

    std::vector<int>* currIdx = &nearestIdxA;
    std::vector<int>* nextIdx = &nearestIdxB;
    std::vector<float>* currDist = &nearestDistA;
    std::vector<float>* nextDist = &nearestDistB;

    // True Jump Flood Algorithm
    for (int step = std::max(dw, dh) / 2; step > 0; step /= 2) {
#ifdef _OPENMP
#pragma omp parallel for schedule(static)
#endif
        for (int dy = 0; dy < dh; ++dy) {
            for (int dx = 0; dx < dw; ++dx) {
                int didx = dy * dw + dx;
                int bestIdx = (*currIdx)[didx];
                float bestDist = (*currDist)[didx];
                float curX = dx * scaleX;
                float curY = dy * scaleY;

                for (int oy = -1; oy <= 1; ++oy) {
                    for (int ox = -1; ox <= 1; ++ox) {
                        int ndx = dx + ox * step;
                        int ndy = dy + oy * step;
                        if (ndx < 0 || ndx >= dw || ndy < 0 || ndy >= dh) continue;

                        int ndidx = ndy * dw + ndx;
                        int candidateIdx = (*currIdx)[ndidx];
                        if (candidateIdx >= 0) {
                            int cx = candidateIdx % width;
                            int cy = candidateIdx / width;
                            float dist = (curX - cx) * (curX - cx) + (curY - cy) * (curY - cy);
                            if (dist < bestDist) {
                                bestDist = dist;
                                bestIdx = candidateIdx;
                            }
                        }
                    }
                }
                (*nextIdx)[didx] = bestIdx;
                (*nextDist)[didx] = bestDist;
            }
        }
        std::swap(currIdx, nextIdx);
        std::swap(currDist, nextDist);
    }

    // Step 3: Upsample and fill background colors
#ifdef _OPENMP
#pragma omp parallel for schedule(static)
#endif
    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            int idx = y * width + x;
            int dx = static_cast<int>(x / scaleX);
            int dy = static_cast<int>(y / scaleY);
            int didx = std::min(dh - 1, std::max(0, dy)) * dw +
                       std::min(dw - 1, std::max(0, dx));

            int srcIdx = (*currIdx)[didx];
            if (srcIdx >= 0) {
                bgLinR[idx] = GAMMA.srgbToLinear(origPixels[srcIdx * 4 + 0]);
                bgLinG[idx] = GAMMA.srgbToLinear(origPixels[srcIdx * 4 + 1]);
                bgLinB[idx] = GAMMA.srgbToLinear(origPixels[srcIdx * 4 + 2]);
            } else {
                // Fallback
                bgLinR[idx] = GAMMA.srgbToLinear(origPixels[idx * 4 + 0]);
                bgLinG[idx] = GAMMA.srgbToLinear(origPixels[idx * 4 + 1]);
                bgLinB[idx] = GAMMA.srgbToLinear(origPixels[idx * 4 + 2]);
            }
        }
    }
}

// ─── Min-filter erosion + adaptive joint bilateral filter ────────────────────
void erodeAndFeatherAlpha(const std::vector<float>& alpha,
                          std::vector<float>& result,
                          int width, int height,
                          const uint8_t* guidancePixels) {
    if (width <= 0 || height <= 0) return;

    // Pass 1: min-filter erosion
    std::vector<float> eroded(static_cast<size_t>(width) * height);
#ifdef _OPENMP
#pragma omp parallel for schedule(static)
#endif
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

    // Pass 2: adaptive joint bilateral filter
    const float sigmaS = std::max(2.0f, std::min(width, height) / 150.0f);
    const float sigmaR = FEATHER_SIGMA_R;
    const float invSigmaR2 = -0.5f / (sigmaR * sigmaR * 255.0f * 255.0f);

#ifdef _OPENMP
#pragma omp parallel for schedule(static)
#endif
    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            const int idx = y * width + x;
            const float origA = alpha[idx];
            const float erodedA = eroded[idx];

            if (origA < TRI_BACKGROUND || erodedA >= origA - 0.005f) {
                result[idx] = erodedA;
                continue;
            }
            if (origA > TRI_FOREGROUND) {
                result[idx] = origA;
                continue;
            }

            const uint8_t* c = guidancePixels + idx * 4;
            const int cR = c[0], cG = c[1], cB = c[2];

            // Fast local variance (3x3 window)
            float colorVar = 0.0f;
            const int vy0 = std::max(0, y - 2);
            const int vy1 = std::min(height - 1, y + 2);
            const int vx0 = std::max(0, x - 2);
            const int vx1 = std::min(width - 1, x + 2);
            for (int vy = vy0; vy <= vy1; ++vy) {
                for (int vx = vx0; vx <= vx1; ++vx) {
                    const uint8_t* p = guidancePixels + (vy * width + vx) * 4;
                    colorVar += std::abs(cR - p[0]) + std::abs(cG - p[1]) + std::abs(cB - p[2]);
                }
            }
            colorVar /= ((vy1 - vy0 + 1) * (vx1 - vx0 + 1));

            float adaptiveSigmaS = (colorVar > 50.0f) ? sigmaS * 0.6f : sigmaS;
            float invSigmaS2 = -0.5f / (adaptiveSigmaS * adaptiveSigmaS);

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
                    const float spatialW = std::exp(invSigmaS2 * static_cast<float>(dx*dx + dy*dy));
                    const float weight = spatialW * rangeW;

                    wSum += eroded[ny * width + nx] * weight;
                    totalW += weight;
                }
            }

            result[idx] = (totalW > 0.0f) ? (wSum / totalW) : erodedA;
        }
    }
}

// ─── Edge-aware blur with background color desaturation ───────────────────────
void applyEdgeBlurToAlpha(uint8_t* fgPixels, const uint8_t* origPixels,
                          int width, int height,
                          const std::vector<float>& bgLinR,
                          const std::vector<float>& bgLinG,
                          const std::vector<float>& bgLinB) {
    if (width < 3 || height < 3) return;

    const size_t count = static_cast<size_t>(width) * height;
    std::vector<float> alpha(count);
    std::vector<float> fgLinR(count), fgLinG(count), fgLinB(count);

#ifdef _OPENMP
#pragma omp parallel for schedule(static)
#endif
    for (size_t i = 0; i < count; ++i) {
        alpha[i] = fgPixels[i * 4 + 3] / 255.0f;
        fgLinR[i] = GAMMA.srgbToLinear(fgPixels[i * 4 + 0]);
        fgLinG[i] = GAMMA.srgbToLinear(fgPixels[i * 4 + 1]);
        fgLinB[i] = GAMMA.srgbToLinear(fgPixels[i * 4 + 2]);
    }

    const float blurRadius = std::max(8.0f, std::min(static_cast<float>(std::min(width, height)) / 25.0f, 48.0f));

    std::vector<float> blurredAlpha(count);
    gaussianBlur(alpha, blurredAlpha, width, height, blurRadius);

    // Edge mask
    std::vector<float> edgeWeight(count, 0.0f);
    for (int y = 1; y < height - 1; ++y) {
        for (int x = 1; x < width - 1; ++x) {
            const int idx = y * width + x;
            const float gx = std::abs(alpha[idx + 1] - alpha[idx - 1]);
            const float gy = std::abs(alpha[idx + width] - alpha[idx - width]);
            const float grad = std::min(1.0f, std::sqrt(gx * gx + gy * gy));
            const bool isTransition = alpha[idx] > 0.03f && alpha[idx] < 0.97f;
            edgeWeight[idx] = std::max(grad, isTransition ? 0.5f : 0.0f);
        }
    }

    std::vector<float> dilatedWeight(count);
    gaussianBlur(edgeWeight, dilatedWeight, width, height, blurRadius * 0.7f);

    // Blend with LUT-based gamma correction
#ifdef _OPENMP
#pragma omp parallel for schedule(static)
#endif
    for (size_t i = 0; i < count; ++i) {
        const float blend = std::min(1.0f, std::max(0.0f, dilatedWeight[i]));
        const float a = alpha[i];
        const bool isEdge = a > 0.03f && a < 0.97f;
        const float effectiveBlend = isEdge ? blend : 0.0f;

        const float newA = a * (1.0f - effectiveBlend) + blurredAlpha[i] * effectiveBlend;
        fgPixels[i * 4 + 3] = static_cast<uint8_t>(std::min(255.0f, std::max(0.0f, newA * 255.0f)));

        if (isEdge && effectiveBlend > 0.1f) {
            const float desatStrength = effectiveBlend * 0.7f;
            const float outR = fgLinR[i] * (1.0f - desatStrength) + bgLinR[i] * desatStrength;
            const float outG = fgLinG[i] * (1.0f - desatStrength) + bgLinG[i] * desatStrength;
            const float outB = fgLinB[i] * (1.0f - desatStrength) + bgLinB[i] * desatStrength;

            fgPixels[i * 4 + 0] = GAMMA.linearToSRGB(outR);
            fgPixels[i * 4 + 1] = GAMMA.linearToSRGB(outG);
            fgPixels[i * 4 + 2] = GAMMA.linearToSRGB(outB);
        }
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

    std::vector<float> alpha(count);
#ifdef _OPENMP
#pragma omp parallel for schedule(static)
#endif
    for (size_t i = 0; i < count; ++i) {
        alpha[i] = fgPixels[i * 4 + 3] / 255.0f;
    }

    // Fast JFA background sampling
    std::vector<float> bgLinR, bgLinG, bgLinB;
    sampleBackgroundColorJFA(origPixels, alpha, width, height, bgLinR, bgLinG, bgLinB);

    // Step 1: Erosion + adaptive feathering
    std::vector<float> refined(count);
    erodeAndFeatherAlpha(alpha, refined, width, height, origPixels);

#ifdef _OPENMP
#pragma omp parallel for schedule(static)
#endif
    for (size_t i = 0; i < count; ++i) {
        const int newAlpha = static_cast<int>(std::min(1.0f, std::max(0.0f, refined[i])) * 255.0f);
        fgPixels[i * 4 + 3] = static_cast<uint8_t>(std::min(255, std::max(0, newAlpha)));
    }

    // Step 2: Edge-aware blur + color fringe suppression
    applyEdgeBlurToAlpha(fgPixels, origPixels, width, height, bgLinR, bgLinG, bgLinB);
}
