#include <android/log.h>
#include <algorithm>
#include <cstdint>
#include <jni.h>
#include <vector>

#define LOG_TAG "MaskFusion"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static inline float clamp01(float v) {
    if (v < 0.0f) return 0.0f;
    if (v > 1.0f) return 1.0f;
    return v;
}

static void circular_erode(
    const float* src,
    float* dst,
    int width,
    int height,
    int radius
) {
    if (radius <= 0) {
        const int size = width * height;
        for (int i = 0; i < size; ++i) {
            dst[i] = clamp01(src[i]);
        }
        return;
    }

    const int r2 = radius * radius;
    for (int y = 0; y < height; ++y) {
        const int y0 = std::max(0, y - radius);
        const int y1 = std::min(height - 1, y + radius);

        for (int x = 0; x < width; ++x) {
            const int x0 = std::max(0, x - radius);
            const int x1 = std::min(width - 1, x + radius);
            float minValue = 1.0f;

            for (int yy = y0; yy <= y1; ++yy) {
                const int dy = yy - y;
                const int dy2 = dy * dy;
                const int row = yy * width;

                for (int xx = x0; xx <= x1; ++xx) {
                    const int dx = xx - x;
                    if (dx * dx + dy2 <= r2) {
                        const float v = clamp01(src[row + xx]);
                        if (v < minValue) {
                            minValue = v;
                        }
                    }
                }
            }

            dst[y * width + x] = minValue;
        }
    }
}

static inline float smoothstep(float edge0, float edge1, float x) {
    if (edge0 == edge1) {
        return x >= edge1 ? 1.0f : 0.0f;
    }

    float t = (x - edge0) / (edge1 - edge0);
    t = clamp01(t);
    return t * t * (3.0f - 2.0f * t);
}

static inline float fuse_alpha(
    float modAlpha,
    float coreAlpha,
    float mlkitThreshold,
    float modnetWeakThreshold
) {
    modAlpha = clamp01(modAlpha);
    coreAlpha = clamp01(coreAlpha);

    if (coreAlpha < mlkitThreshold || modAlpha >= modnetWeakThreshold) {
        return modAlpha;
    }

    const float coreStrength = smoothstep(mlkitThreshold, 1.0f, coreAlpha);
    const float weakness = 1.0f - smoothstep(0.0f, modnetWeakThreshold, modAlpha);
    const float blend = clamp01(coreStrength * weakness);
    const float target = std::max(modAlpha, coreAlpha);
    return clamp01(modAlpha * (1.0f - blend) + target * blend);
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_thgiang_image_core_data_backgroundremove_AdaptiveHybridBackgroundRemoverRepository_nativeFuseMasks(
    JNIEnv* env,
    jobject,
    jfloatArray modnet_mask,
    jfloatArray mlkit_mask,
    jint width,
    jint height,
    jint erode_radius,
    jfloat mlkit_threshold,
    jfloat modnet_weak_threshold
) {
    if (modnet_mask == nullptr || mlkit_mask == nullptr) {
        LOGE("Null mask array");
        return nullptr;
    }

    if (width <= 0 || height <= 0) {
        LOGE("Invalid size: %d x %d", width, height);
        return nullptr;
    }

    const int64_t size64 = static_cast<int64_t>(width) * static_cast<int64_t>(height);
    if (size64 <= 0 || size64 > 100000000) {
        LOGE("Invalid mask element count: %lld", static_cast<long long>(size64));
        return nullptr;
    }

    const jsize size = static_cast<jsize>(size64);
    const jsize modLen = env->GetArrayLength(modnet_mask);
    const jsize mlLen = env->GetArrayLength(mlkit_mask);
    if (modLen != size || mlLen != size) {
        LOGE("Mask length mismatch: expected=%d mod=%d ml=%d", size, modLen, mlLen);
        return nullptr;
    }

    if (width > 768 || height > 768) {
        LOGE("Resolution %dx%d exceeds fusion cap, returning ModNet mask", width, height);

        jfloatArray result = env->NewFloatArray(size);
        if (result == nullptr || env->ExceptionCheck()) {
            return nullptr;
        }

        std::vector<float> tmp(size);
        env->GetFloatArrayRegion(modnet_mask, 0, size, tmp.data());
        if (env->ExceptionCheck()) {
            return nullptr;
        }

        env->SetFloatArrayRegion(result, 0, size, tmp.data());
        if (env->ExceptionCheck()) {
            return nullptr;
        }

        return result;
    }

    std::vector<float> modnet(size);
    std::vector<float> mlkit(size);
    std::vector<float> core(size);
    std::vector<float> fused(size);

    env->GetFloatArrayRegion(modnet_mask, 0, size, modnet.data());
    if (env->ExceptionCheck()) {
        LOGE("Failed to read ModNet mask");
        return nullptr;
    }

    env->GetFloatArrayRegion(mlkit_mask, 0, size, mlkit.data());
    if (env->ExceptionCheck()) {
        LOGE("Failed to read ML Kit mask");
        return nullptr;
    }

    circular_erode(mlkit.data(), core.data(), width, height, std::max(0, static_cast<int>(erode_radius)));

    const float safeMlkitThreshold = clamp01(mlkit_threshold);
    const float safeModnetWeakThreshold = clamp01(modnet_weak_threshold);
    for (jsize i = 0; i < size; ++i) {
        fused[i] = fuse_alpha(modnet[i], core[i], safeMlkitThreshold, safeModnetWeakThreshold);
    }

    jfloatArray result = env->NewFloatArray(size);
    if (result == nullptr || env->ExceptionCheck()) {
        LOGE("Failed to allocate result array");
        return nullptr;
    }

    env->SetFloatArrayRegion(result, 0, size, fused.data());
    if (env->ExceptionCheck()) {
        LOGE("Failed to write result array");
        return nullptr;
    }

    return result;
}
