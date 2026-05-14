#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>

#include "bg_refiner.h"

#define LOG_TAG "BgRefinerJNI"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)

namespace {

// ─── RAII guard for AndroidBitmap_lockPixels (from portrait/native_portrait.cpp) ──
class BitmapGuard {
public:
    BitmapGuard(JNIEnv* env, jobject bitmap)
        : env_(env), bitmap_(bitmap), pixels_(nullptr), locked_(false) {
        if (bitmap_ &&
            AndroidBitmap_lockPixels(env_, bitmap_, &pixels_) == ANDROID_BITMAP_RESULT_SUCCESS) {
            locked_ = true;
        }
    }

    ~BitmapGuard() {
        if (locked_ && bitmap_) {
            AndroidBitmap_unlockPixels(env_, bitmap_);
        }
    }

    BitmapGuard(BitmapGuard&& o) noexcept
        : env_(o.env_), bitmap_(o.bitmap_), pixels_(o.pixels_), locked_(o.locked_) {
        o.bitmap_ = nullptr; o.pixels_ = nullptr; o.locked_ = false;
    }

    BitmapGuard& operator=(BitmapGuard&& o) noexcept {
        if (this != &o) {
            if (locked_ && bitmap_) AndroidBitmap_unlockPixels(env_, bitmap_);
            env_ = o.env_; bitmap_ = o.bitmap_; pixels_ = o.pixels_; locked_ = o.locked_;
            o.bitmap_ = nullptr; o.pixels_ = nullptr; o.locked_ = false;
        }
        return *this;
    }

    BitmapGuard(const BitmapGuard&)            = delete;
    BitmapGuard& operator=(const BitmapGuard&) = delete;

    bool  isLocked()   const { return locked_; }
    void* getPixels()  const { return pixels_; }

private:
    JNIEnv* env_;
    jobject bitmap_;
    void*   pixels_;
    bool    locked_;
};

bool checkException(JNIEnv* env, const char* ctx) {
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        LOGE("JNI exception at: %s", ctx);
        return true;
    }
    return false;
}

bool getBitmapInfo(JNIEnv* env, jobject bitmap,
                   AndroidBitmapInfo& info, const char* name) {
    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS) {
        LOGE("getBitmapInfo(%s): getInfo failed", name);
        return false;
    }
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGE("getBitmapInfo(%s): format %d is not RGBA_8888", name, info.format);
        return false;
    }
    return true;
}

} // anonymous namespace

// ─── JNI ─────────────────────────────────────────────────────────────────────
// Package: com.thgiang.image.core.data.backgroundremove
// Class:   BackgroundRefinerNative
// Method:  nativeRefineForeground
extern "C"
JNIEXPORT jboolean JNICALL
Java_com_thgiang_image_core_data_backgroundremove_BackgroundRefinerNative_nativeRefineForeground(
        JNIEnv*  env,
        jclass   /*clazz*/,
        jobject  foregroundBitmap,
        jobject  originalBitmap) {

    if (!env || !foregroundBitmap || !originalBitmap) {
        LOGE("nativeRefineForeground: null args");
        return JNI_FALSE;
    }

    if (env->PushLocalFrame(16) != 0) {
        LOGE("nativeRefineForeground: PushLocalFrame failed");
        return JNI_FALSE;
    }

    // Validate foreground
    AndroidBitmapInfo fgInfo;
    if (!getBitmapInfo(env, foregroundBitmap, fgInfo, "foreground")) {
        env->PopLocalFrame(nullptr);
        return JNI_FALSE;
    }

    // Validate original
    AndroidBitmapInfo origInfo;
    if (!getBitmapInfo(env, originalBitmap, origInfo, "original")) {
        env->PopLocalFrame(nullptr);
        return JNI_FALSE;
    }

    // Dimensions must match
    if (fgInfo.width != origInfo.width || fgInfo.height != origInfo.height) {
        LOGE("nativeRefineForeground: size mismatch fg=%dx%d orig=%dx%d",
             fgInfo.width, fgInfo.height, origInfo.width, origInfo.height);
        env->PopLocalFrame(nullptr);
        return JNI_FALSE;
    }

    const int width  = static_cast<int>(fgInfo.width);
    const int height = static_cast<int>(fgInfo.height);

    // Lock both bitmaps
    BitmapGuard fgGuard(env, foregroundBitmap);
    if (!fgGuard.isLocked()) {
        LOGE("nativeRefineForeground: lockPixels(foreground) failed");
        env->PopLocalFrame(nullptr);
        return JNI_FALSE;
    }

    BitmapGuard origGuard(env, originalBitmap);
    if (!origGuard.isLocked()) {
        LOGE("nativeRefineForeground: lockPixels(original) failed");
        env->PopLocalFrame(nullptr);
        return JNI_FALSE;
    }

    auto* fgPixels   = static_cast<uint8_t*>(fgGuard.getPixels());
    auto* origPixels = static_cast<uint8_t*>(origGuard.getPixels());

    LOGI("nativeRefineForeground %dx%d", width, height);

    // Process in-place on fgPixels
    refineForegroundAlpha(fgPixels, origPixels, width, height);

    env->PopLocalFrame(nullptr);
    return JNI_TRUE;
}
