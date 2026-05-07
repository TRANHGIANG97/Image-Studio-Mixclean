#include <jni.h>
#include <android/bitmap.h>
#include <vector>
#include <algorithm>
#include <cmath>

static inline int clamp255(float v) {
    if (v < 0.f) return 0;
    if (v > 255.f) return 255;
    return static_cast<int>(v + 0.5f);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_thgiang_image_feature_home_ui_components_NativeSeamlessBlender_nativeSeamlessBlend(
        JNIEnv *env,
        jobject,
        jobject srcBitmap,
        jobject dstBitmap,
        jobject maskBitmap,
        jobject outBitmap
) {
    AndroidBitmapInfo srcInfo{}, dstInfo{}, maskInfo{}, outInfo{};
    if (AndroidBitmap_getInfo(env, srcBitmap, &srcInfo) != ANDROID_BITMAP_RESULT_SUCCESS) return;
    if (AndroidBitmap_getInfo(env, dstBitmap, &dstInfo) != ANDROID_BITMAP_RESULT_SUCCESS) return;
    if (AndroidBitmap_getInfo(env, maskBitmap, &maskInfo) != ANDROID_BITMAP_RESULT_SUCCESS) return;
    if (AndroidBitmap_getInfo(env, outBitmap, &outInfo) != ANDROID_BITMAP_RESULT_SUCCESS) return;

    if (srcInfo.format != ANDROID_BITMAP_FORMAT_RGBA_8888 ||
        dstInfo.format != ANDROID_BITMAP_FORMAT_RGBA_8888 ||
        maskInfo.format != ANDROID_BITMAP_FORMAT_RGBA_8888 ||
        outInfo.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        return;
    }
    if (srcInfo.width != dstInfo.width || srcInfo.height != dstInfo.height ||
        srcInfo.width != maskInfo.width || srcInfo.height != maskInfo.height ||
        srcInfo.width != outInfo.width || srcInfo.height != outInfo.height) {
        return;
    }

    uint32_t *srcPx = nullptr;
    uint32_t *dstPx = nullptr;
    uint32_t *maskPx = nullptr;
    uint32_t *outPx = nullptr;

    if (AndroidBitmap_lockPixels(env, srcBitmap, reinterpret_cast<void **>(&srcPx)) != ANDROID_BITMAP_RESULT_SUCCESS) return;
    if (AndroidBitmap_lockPixels(env, dstBitmap, reinterpret_cast<void **>(&dstPx)) != ANDROID_BITMAP_RESULT_SUCCESS) {
        AndroidBitmap_unlockPixels(env, srcBitmap);
        return;
    }
    if (AndroidBitmap_lockPixels(env, maskBitmap, reinterpret_cast<void **>(&maskPx)) != ANDROID_BITMAP_RESULT_SUCCESS) {
        AndroidBitmap_unlockPixels(env, srcBitmap);
        AndroidBitmap_unlockPixels(env, dstBitmap);
        return;
    }
    if (AndroidBitmap_lockPixels(env, outBitmap, reinterpret_cast<void **>(&outPx)) != ANDROID_BITMAP_RESULT_SUCCESS) {
        AndroidBitmap_unlockPixels(env, srcBitmap);
        AndroidBitmap_unlockPixels(env, dstBitmap);
        AndroidBitmap_unlockPixels(env, maskBitmap);
        return;
    }

    const int w = static_cast<int>(srcInfo.width);
    const int h = static_cast<int>(srcInfo.height);
    const int n = w * h;
    std::vector<uint8_t> mask(n, 0);
    std::vector<float> prev(n), curr(n);

    for (int i = 0; i < n; ++i) {
        const uint32_t m = maskPx[i];
        const int ma = (m >> 24) & 0xFF;
        mask[i] = ma > 20 ? 1 : 0;
        outPx[i] = dstPx[i];
    }

    auto getC = [](uint32_t p, int c) -> int {
        if (c == 0) return p & 0xFF;
        if (c == 1) return (p >> 8) & 0xFF;
        return (p >> 16) & 0xFF;
    };
    auto setC = [](uint32_t p, int c, int v) -> uint32_t {
        const uint32_t uv = static_cast<uint32_t>(v & 0xFF);
        if (c == 0) return (p & 0xFFFFFF00u) | uv;
        if (c == 1) return (p & 0xFFFF00FFu) | (uv << 8);
        return (p & 0xFF00FFFFu) | (uv << 16);
    };

    for (int c = 0; c < 3; ++c) {
        for (int i = 0; i < n; ++i) prev[i] = static_cast<float>(getC(dstPx[i], c));

        const int iterations = 180;
        for (int it = 0; it < iterations; ++it) {
            curr = prev;
            for (int y = 1; y < h - 1; ++y) {
                for (int x = 1; x < w - 1; ++x) {
                    const int idx = y * w + x;
                    if (!mask[idx]) continue;

                    const int l = idx - 1, r = idx + 1, u = idx - w, d = idx + w;
                    const float srcCenter = static_cast<float>(getC(srcPx[idx], c));
                    const float div = 4.f * srcCenter
                                      - static_cast<float>(getC(srcPx[l], c))
                                      - static_cast<float>(getC(srcPx[r], c))
                                      - static_cast<float>(getC(srcPx[u], c))
                                      - static_cast<float>(getC(srcPx[d], c));

                    const float xl = mask[l] ? prev[l] : static_cast<float>(getC(dstPx[l], c));
                    const float xr = mask[r] ? prev[r] : static_cast<float>(getC(dstPx[r], c));
                    const float xu = mask[u] ? prev[u] : static_cast<float>(getC(dstPx[u], c));
                    const float xd = mask[d] ? prev[d] : static_cast<float>(getC(dstPx[d], c));

                    curr[idx] = (xl + xr + xu + xd + div) * 0.25f;
                }
            }
            prev.swap(curr);
        }

        for (int i = 0; i < n; ++i) {
            if (!mask[i]) continue;
            outPx[i] = setC(outPx[i], c, clamp255(prev[i]));
        }
    }

    for (int i = 0; i < n; ++i) {
        const uint32_t a = dstPx[i] & 0xFF000000u;
        outPx[i] = (outPx[i] & 0x00FFFFFFu) | a;
    }

    AndroidBitmap_unlockPixels(env, srcBitmap);
    AndroidBitmap_unlockPixels(env, dstBitmap);
    AndroidBitmap_unlockPixels(env, maskBitmap);
    AndroidBitmap_unlockPixels(env, outBitmap);
}
