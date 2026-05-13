#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <cstring>
#include <optional>

#include "image_processor.h"

#define LOG_TAG "PortraitJNI"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

// ─── RAII guard for AndroidBitmap_lockPixels ──────────────────────────────────
// Rule of Five: destructor + move ctor + move assign + delete copy ctor/assign.
// std::optional<BitmapGuard> is used for nullable/deferred cases.
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

    // Move constructor
    BitmapGuard(BitmapGuard&& o) noexcept
        : env_(o.env_), bitmap_(o.bitmap_), pixels_(o.pixels_), locked_(o.locked_) {
        o.bitmap_ = nullptr; o.pixels_ = nullptr; o.locked_ = false;
    }

    // Move assignment
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

// ─── Helpers ──────────────────────────────────────────────────────────────────

/** Returns true and logs if a pending JNI exception exists (clears it). */
bool checkException(JNIEnv* env, const char* ctx) {
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        LOGE("JNI exception at: %s", ctx);
        return true;
    }
    return false;
}

/** Validate that a bitmap is RGBA_8888 and return its info. */
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

// ─── JNI entry point ──────────────────────────────────────────────────────────
// JNI name must match:
//   package  : com.thgiang.image.core.util.processors
//   class    : PortraitProcessor
//   method   : nativeApplyPortrait
extern "C"
JNIEXPORT jobject JNICALL
Java_com_thgiang_image_core_util_processors_PortraitProcessor_nativeApplyPortrait(
        JNIEnv*  env,
        jclass   /*clazz*/,
        jobject  srcBitmap,
        jobject  fgBitmap,   // nullable
        jfloat   blurRadius,
        jfloat   darkenAlpha,
        jboolean vignette) {

    if (!env || !srcBitmap) {
        LOGE("nativeApplyPortrait: null env or srcBitmap");
        return nullptr;
    }

    // FIX: Use 32 slots (was 16) to safely accommodate all local refs created below.
    if (env->PushLocalFrame(32) != 0) {
        LOGE("nativeApplyPortrait: PushLocalFrame(32) failed");
        return nullptr;
    }

    // ── Validate & lock source bitmap ────────────────────────────────────────
    AndroidBitmapInfo srcInfo;
    if (!getBitmapInfo(env, srcBitmap, srcInfo, "src")) {
        env->PopLocalFrame(nullptr);
        return nullptr;
    }
    const int width  = static_cast<int>(srcInfo.width);
    const int height = static_cast<int>(srcInfo.height);

    BitmapGuard srcGuard(env, srcBitmap);
    if (!srcGuard.isLocked()) {
        LOGE("nativeApplyPortrait: lockPixels(src) failed");
        env->PopLocalFrame(nullptr);
        return nullptr;
    }
    const auto* srcPixels = static_cast<const uint8_t*>(srcGuard.getPixels());

    // ── Validate & lock optional foreground bitmap ────────────────────────────
    const uint8_t* fgPixels = nullptr;
    std::optional<BitmapGuard> fgGuard;

    if (fgBitmap != nullptr) {
        AndroidBitmapInfo fgInfo;
        if (!getBitmapInfo(env, fgBitmap, fgInfo, "fg")) {
            env->PopLocalFrame(nullptr);
            return nullptr;
        }
        if (static_cast<int>(fgInfo.width) != width ||
            static_cast<int>(fgInfo.height) != height) {
            LOGE("nativeApplyPortrait: fg size %dx%d != src %dx%d",
                 fgInfo.width, fgInfo.height, width, height);
            env->PopLocalFrame(nullptr);
            return nullptr;
        }
        fgGuard.emplace(env, fgBitmap);
        if (!fgGuard->isLocked()) {
            LOGE("nativeApplyPortrait: lockPixels(fg) failed");
            env->PopLocalFrame(nullptr);
            return nullptr;
        }
        fgPixels = static_cast<const uint8_t*>(fgGuard->getPixels());
    }

    // ── Process ───────────────────────────────────────────────────────────────
    uint8_t* result = processPortrait(srcPixels, fgPixels,
                                      width, height,
                                      blurRadius, darkenAlpha,
                                      static_cast<bool>(vignette));
    if (!result) {
        LOGE("nativeApplyPortrait: processPortrait returned nullptr");
        env->PopLocalFrame(nullptr);
        return nullptr;
    }

    // ── Create output Bitmap via JNI reflection ───────────────────────────────
    const jclass  bmpClass    = env->FindClass("android/graphics/Bitmap");
    const jclass  cfgClass    = env->FindClass("android/graphics/Bitmap$Config");
    if (checkException(env, "FindClass") || !bmpClass || !cfgClass) {
        freePortraitResult(result);
        env->PopLocalFrame(nullptr);
        return nullptr;
    }

    const jmethodID createBitmap = env->GetStaticMethodID(
        bmpClass, "createBitmap",
        "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");
    const jfieldID  argb8888    = env->GetStaticFieldID(
        cfgClass, "ARGB_8888", "Landroid/graphics/Bitmap$Config;");
    if (checkException(env, "GetID") || !createBitmap || !argb8888) {
        freePortraitResult(result);
        env->PopLocalFrame(nullptr);
        return nullptr;
    }

    const jobject config    = env->GetStaticObjectField(cfgClass, argb8888);
    const jobject outBitmap = env->CallStaticObjectMethod(
        bmpClass, createBitmap, width, height, config);
    if (checkException(env, "createBitmap") || !outBitmap) {
        freePortraitResult(result);
        env->PopLocalFrame(nullptr);
        return nullptr;
    }

    // ── Copy native buffer → Bitmap pixels ───────────────────────────────────
    void* outPixels = nullptr;
    if (AndroidBitmap_lockPixels(env, outBitmap, &outPixels) != ANDROID_BITMAP_RESULT_SUCCESS) {
        LOGE("nativeApplyPortrait: lockPixels(out) failed");
        freePortraitResult(result);
        env->PopLocalFrame(nullptr);
        return nullptr;
    }
    std::memcpy(outPixels, result, static_cast<size_t>(width) * height * 4u);
    AndroidBitmap_unlockPixels(env, outBitmap);

    freePortraitResult(result); // Always freed here; no leak possible.

    // PopLocalFrame promotes outBitmap to the caller's frame.
    return env->PopLocalFrame(outBitmap);
}
