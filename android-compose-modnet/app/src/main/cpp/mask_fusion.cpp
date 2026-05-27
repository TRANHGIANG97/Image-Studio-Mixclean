#include <android/log.h>
#include <algorithm>
#include <cstdint>
#include <jni.h>
#include <vector>

#define LOG_TAG "MaskFusionLab"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

static inline float clamp01(float v) {
    if (v < 0.0f) return 0.0f;
    if (v > 1.0f) return 1.0f;
    return v;
}

// ============================================================
// Summed Area Table (Integral Image) — O(1) per box sum query
// Được dùng bởi Guided Filter để tính boxFilter cực nhanh.
// ============================================================
static void build_sat(const float* src, double* sat, int width, int height) {
    // sat kích thước (width+1)*(height+1), sat[0..] = 0 (border padding)
    const int w1 = width + 1;
    for (int y = 0; y <= height; ++y) sat[y * w1] = 0.0;
    for (int x = 0; x <= width; ++x) sat[x] = 0.0;
    for (int y = 1; y <= height; ++y) {
        double row_sum = 0.0;
        for (int x = 1; x <= width; ++x) {
            row_sum += static_cast<double>(src[(y - 1) * width + (x - 1)]);
            sat[y * w1 + x] = row_sum + sat[(y - 1) * w1 + x];
        }
    }
}

// Lấy tổng giá trị trong hình chữ nhật [x0,x1)×[y0,y1) từ SAT
static inline double sat_sum(const double* sat, int w1, int x0, int y0, int x1, int y1) {
    return sat[y1 * w1 + x1] - sat[y0 * w1 + x1] - sat[y1 * w1 + x0] + sat[y0 * w1 + x0];
}

// Box filter một mảng float dùng SAT — O(1) per pixel, mọi bán kính
static void box_filter_sat(const float* src, float* dst, int width, int height, int radius) {
    const int w1 = width + 1;
    std::vector<double> sat((w1) * (height + 1), 0.0);
    build_sat(src, sat.data(), width, height);
    for (int y = 0; y < height; ++y) {
        const int y0 = std::max(0, y - radius);
        const int y1 = std::min(height, y + radius + 1);
        for (int x = 0; x < width; ++x) {
            const int x0 = std::max(0, x - radius);
            const int x1 = std::min(width, x + radius + 1);
            const int count = (y1 - y0) * (x1 - x0);
            dst[y * width + x] = count > 0
                ? static_cast<float>(sat_sum(sat.data(), w1, x0, y0, x1, y1) / count)
                : src[y * width + x];
        }
    }
}

// ============================================================
// Guided Filter (He et al. 2013) — Edge-aware mask refinement
// Lọc mask p theo ảnh guidance I (grayscale), giữ sharp edges,
// loại bỏ noise micro-pixel ở vùng biên.
// radius: vùng ảnh hưởng (px), epsilon: độ "cứng" của cạnh
// ============================================================
static void guided_filter_gray(
    const float* guide,   // ảnh grayscale guidance [0,1]
    const float* p,       // mask cần refine [0,1]
    float* q,             // output mask refined [0,1]
    int width, int height,
    int radius, float epsilon
) {
    const int size = width * height;
    std::vector<float> mean_I(size), mean_p(size);
    std::vector<float> mean_Ip(size), mean_II(size);
    std::vector<float> Ip(size), II(size);

    for (int i = 0; i < size; ++i) {
        Ip[i] = guide[i] * p[i];
        II[i] = guide[i] * guide[i];
    }

    box_filter_sat(guide,      mean_I.data(),  width, height, radius);
    box_filter_sat(p,          mean_p.data(),  width, height, radius);
    box_filter_sat(Ip.data(),  mean_Ip.data(), width, height, radius);
    box_filter_sat(II.data(),  mean_II.data(), width, height, radius);

    std::vector<float> a(size), b(size);
    for (int i = 0; i < size; ++i) {
        const float var_I = mean_II[i] - mean_I[i] * mean_I[i];
        a[i] = (mean_Ip[i] - mean_I[i] * mean_p[i]) / (var_I + epsilon);
        b[i] = mean_p[i] - a[i] * mean_I[i];
    }

    std::vector<float> mean_a(size), mean_b(size);
    box_filter_sat(a.data(), mean_a.data(), width, height, radius);
    box_filter_sat(b.data(), mean_b.data(), width, height, radius);

    for (int i = 0; i < size; ++i) {
        q[i] = clamp01(mean_a[i] * guide[i] + mean_b[i]);
    }
}

// ============================================================
// Multi-pass separable box blur (3 passes ≈ Gaussian σ≈1.7)
// Chỉ blur trong vùng allowed, không làm lem mask ra ngoài.
// Dùng sliding window O(n) thay vì SAT để nhanh hơn với radius nhỏ.
// ============================================================
static void multipass_blur_in_allowed(
    const float* alpha_in,
    const float* allowed,
    float* out,
    int width, int height,
    int passes = 3
) {
    const int size = width * height;
    std::vector<float> buf_a(size), buf_b(size);
    // Khởi tạo: copy alpha_in vào buf_a, zero vùng ngoài allowed
    for (int i = 0; i < size; ++i) {
        buf_a[i] = allowed[i] > 0.5f ? alpha_in[i] : 0.0f;
    }

    const int radius = 1; // 3×3 kernel per pass
    for (int pass = 0; pass < passes; ++pass) {
        const float* src = (pass % 2 == 0) ? buf_a.data() : buf_b.data();
        float*       dst = (pass % 2 == 0) ? buf_b.data() : buf_a.data();

        // Horizontal pass (sliding window)
        std::vector<float> temp(size);
        for (int y = 0; y < height; ++y) {
            const int row = y * width;
            float sum = 0.0f;
            int count = 0;
            for (int kx = -radius; kx <= radius; ++kx) {
                const int x = kx;
                if (x >= 0 && x < width) {
                    const int idx = row + x;
                    if (allowed[idx] > 0.5f) { sum += src[idx]; ++count; }
                }
            }
            for (int x = 0; x < width; ++x) {
                const int idx = row + x;
                temp[idx] = (allowed[idx] > 0.5f && count > 0) ? sum / count : 0.0f;
                // Slide window: add next, remove prev
                const int add_x = x + radius + 1;
                const int rem_x = x - radius;
                if (add_x < width) {
                    const int ai = row + add_x;
                    if (allowed[ai] > 0.5f) { sum += src[ai]; ++count; }
                }
                if (rem_x >= 0) {
                    const int ri = row + rem_x;
                    if (allowed[ri] > 0.5f) { sum -= src[ri]; --count; }
                }
            }
        }

        // Vertical pass (sliding window)
        for (int x = 0; x < width; ++x) {
            float sum = 0.0f;
            int count = 0;
            for (int ky = -radius; ky <= radius; ++ky) {
                const int y = ky;
                if (y >= 0 && y < height) {
                    const int idx = y * width + x;
                    if (allowed[idx] > 0.5f) { sum += temp[idx]; ++count; }
                }
            }
            for (int y = 0; y < height; ++y) {
                const int idx = y * width + x;
                dst[idx] = (allowed[idx] > 0.5f && count > 0) ? clamp01(sum / count) : 0.0f;
                const int add_y = y + radius + 1;
                const int rem_y = y - radius;
                if (add_y < height) {
                    const int ai = add_y * width + x;
                    if (allowed[ai] > 0.5f) { sum += temp[ai]; ++count; }
                }
                if (rem_y >= 0) {
                    const int ri = rem_y * width + x;
                    if (allowed[ri] > 0.5f) { sum -= temp[ri]; --count; }
                }
            }
        }
    }

    // Kết quả ở buf_a hay buf_b tuỳ số pass
    const float* final_buf = (passes % 2 == 0) ? buf_a.data() : buf_b.data();
    for (int i = 0; i < size; ++i) {
        out[i] = allowed[i] > 0.5f ? final_buf[i] : 0.0f;
    }
}



static void circular_erode(const float* src, float* dst, int width, int height, int radius) {
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
            float min_value = 1.0f;
            for (int yy = y0; yy <= y1; ++yy) {
                const int dy = yy - y;
                const int row = yy * width;
                for (int xx = x0; xx <= x1; ++xx) {
                    const int dx = xx - x;
                    if (dx * dx + dy * dy <= r2) {
                        min_value = std::min(min_value, clamp01(src[row + xx]));
                    }
                }
            }
            dst[y * width + x] = min_value;
        }
    }
}

// Nở rộng (Dilate) mask: lấy MAX trong vùng lân cận bán kính R → mở rộng vùng foreground.
static void circular_dilate(const float* src, float* dst, int width, int height, int radius) {
    if (radius <= 0) {
        const int size = width * height;
        for (int i = 0; i < size; ++i) dst[i] = clamp01(src[i]);
        return;
    }
    const int r2 = radius * radius;
    for (int y = 0; y < height; ++y) {
        const int y0 = std::max(0, y - radius);
        const int y1 = std::min(height - 1, y + radius);
        for (int x = 0; x < width; ++x) {
            const int x0 = std::max(0, x - radius);
            const int x1 = std::min(width - 1, x + radius);
            float max_value = 0.0f;
            for (int yy = y0; yy <= y1; ++yy) {
                const int dy = yy - y;
                const int row = yy * width;
                for (int xx = x0; xx <= x1; ++xx) {
                    const int dx = xx - x;
                    if (dx * dx + dy * dy <= r2)
                        max_value = std::max(max_value, clamp01(src[row + xx]));
                }
            }
            dst[y * width + x] = max_value;
        }
    }
}

// Trả về 0.0-1.0: mức độ giống màu cây/cỏ/lá.
// Dùng 4 signature để bắt cả xanh đậm, xanh non, vàng-xanh, xanh olive.
static float vegetation_greenness(jint color) {
    const int r = (color >> 16) & 0xFF;
    const int g = (color >> 8) & 0xFF;
    const int b = color & 0xFF;

    if (g < 45) return 0.0f;
    if (g > 235 && r > 210 && b > 210) return 0.0f;
    if (g < r - 30 && g < b - 30) return 0.0f;

    float score = 0.0f;
    const int maxRB = std::max(r, b);

    // Classic green foliage: G dominates strongly
    if (g > r && g > b && g > 60) {
        float excess = static_cast<float>(g - maxRB);
        float dom = std::min(1.0f, excess / 55.0f);
        score = std::max(score, dom * 0.78f);
    }

    // Yellow-green grass: R≈G, both > B
    if (g > b + 8 && r > b + 10 && std::abs(r - g) < 65 && g > 65) {
        float yg = std::min(1.0f, (std::min(r, g) - b) / 45.0f);
        score = std::max(score, yg * 0.72f);
    }

    // Dark green foliage: low RGB, G slightly leading
    if (r + g + b < 220 && g > r && g > b && g > 40) {
        score = std::max(score, 0.55f);
    }

    // Very strong green → high confidence végétation
    if (g > r + 22 && g > b + 18 && g > 70) {
        score = std::max(score, 0.88f);
    }

    // Olive/brownish tint: R and G close, both above B, low saturation
    if (g > b + 5 && r > b + 5 && std::abs(r - g) < 35 && g > 55 && g < 180) {
        score = std::max(score, 0.45f);
    }

    return std::min(1.0f, score);
}

static void mark_fillable_holes_strict_plus(
    const float* closed_hard,
    const float* hard_core,
    const float* allowed,
    uint8_t* fill,
    int width,
    int height,
    int* out_component_count,
    int* out_filled_count
) {
    const int size = width * height;
    std::vector<uint8_t> candidate(size, 0);
    std::vector<uint8_t> visited(size, 0);
    std::vector<int> queue(size);

    for (int i = 0; i < size; ++i) {
        candidate[i] = closed_hard[i] > 0.5f && hard_core[i] < 0.5f && allowed[i] > 0.5f ? 1 : 0;
    }

    const int max_area = std::max(64, static_cast<int>(size * 0.025f));
    int component_count = 0;
    int filled_count = 0;

    for (int start = 0; start < size; ++start) {
        if (!candidate[start] || visited[start]) continue;

        int head = 0;
        int tail = 0;
        queue[tail++] = start;
        visited[start] = 1;
        component_count++;

        int min_x = width;
        int max_x = 0;
        int min_y = height;
        int max_y = 0;
        bool touches_border = false;
        int border_contact = 0;

        while (head < tail) {
            const int idx = queue[head++];
            const int y = idx / width;
            const int x = idx - y * width;
            min_x = std::min(min_x, x);
            max_x = std::max(max_x, x);
            min_y = std::min(min_y, y);
            max_y = std::max(max_y, y);
            if (x <= 1 || y <= 1 || x >= width - 2 || y >= height - 2) {
                touches_border = true;
                border_contact++;
            }

            auto push = [&](int n) {
                if (n < 0 || n >= size || !candidate[n] || visited[n]) return;
                visited[n] = 1;
                queue[tail++] = n;
            };

            if (x > 0) push(idx - 1);
            if (x < width - 1) push(idx + 1);
            if (y > 0) push(idx - width);
            if (y < height - 1) push(idx + width);
        }

        const int comp_w = max_x - min_x + 1;
        const int comp_h = max_y - min_y + 1;
        const float center_y = ((min_y + max_y) * 0.5f) / static_cast<float>(height);

        // Cho phép component chạm border nhưng mỏng (< 8px hoặc ít border_contact)
        bool border_thin = touches_border &&
            (comp_w < 8 || comp_h < 8 || border_contact < std::max(5, tail / 3));
        bool large_gap = comp_w > width * 0.18f && comp_h > height * 0.24f;
        bool too_big = tail > max_area &&
            !(comp_w < width * 0.10f || comp_h < height * 0.22f);

        bool should_fill =
            (!touches_border || border_thin) &&
            center_y > 0.25f &&
            !large_gap &&
            !too_big;

        if (should_fill) {
            for (int i = 0; i < tail; ++i) {
                fill[queue[i]] = 1;
            }
            filled_count++;
        }
    }

    if (out_component_count) *out_component_count = component_count;
    if (out_filled_count) *out_filled_count = filled_count;
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_toshiba_modnet_NativeMaskFusion_nativeFuseMasks(
    JNIEnv* env,
    jobject,
    jfloatArray modnet_mask,
    jfloatArray core_mask,
    jint width,
    jint height,
    jint erode_radius,
    jfloat core_threshold,
    jfloat modnet_weak_threshold
) {
    if (modnet_mask == nullptr || core_mask == nullptr || width <= 0 || height <= 0) {
        LOGE("Invalid fusion input");
        return nullptr;
    }

    const int64_t size64 = static_cast<int64_t>(width) * static_cast<int64_t>(height);
    if (size64 <= 0 || size64 > 100000000) {
        LOGE("Invalid mask size");
        return nullptr;
    }

    const jsize size = static_cast<jsize>(size64);
    if (env->GetArrayLength(modnet_mask) != size || env->GetArrayLength(core_mask) != size) {
        LOGE("Mask length mismatch");
        return nullptr;
    }

    std::vector<float> modnet(size);
    std::vector<float> core_raw(size);
    std::vector<float> core_eroded(size);  // Co vào: chỉ giữ phần RUỘT sâu
    std::vector<float> core_dilated(size); // Nở ra: tạo vùng an toàn cho gradient U2Netp
    std::vector<float> fused(size);

    env->GetFloatArrayRegion(modnet_mask, 0, size, modnet.data());
    if (env->ExceptionCheck()) return nullptr;
    env->GetFloatArrayRegion(core_mask, 0, size, core_raw.data());
    if (env->ExceptionCheck()) return nullptr;

    // Co 5px → phần ruột đặc chắc (ML Kit bảo đảm lõi không bị lủng lỗ)
    circular_erode(core_raw.data(), core_eroded.data(), width, height, 5);
    // Nở 20px → vùng an toàn chứa toàn bộ chi tiết biên, tóc tơ. Mọi thứ ngoài vùng này sẽ bị xóa.
    circular_dilate(core_raw.data(), core_dilated.data(), width, height, 20);

    for (jsize i = 0; i < size; ++i) {
        float mod_alpha    = clamp01(modnet[i]);
        float eroded_alpha = clamp01(core_eroded[i]);
        float dilated_alpha= clamp01(core_dilated[i]);

        // Kỹ thuật Kẹp Mặt Nạ (Mask Clamping) cực kỳ tinh giản và chặt chẽ:
        // - Giá trị fusion không bao giờ nhỏ hơn eroded_alpha (đảm bảo ruột đặc, vá lỗi thủng của U2Netp).
        // - Giá trị fusion không bao giờ lớn hơn dilated_alpha (xóa sạch false positive ở nền xa).
        // - Nằm trong vùng chuyển tiếp: fused_val LẤY ĐÚNG TRỊ SỐ CỦA U2Netp (mod_alpha).
        // Nhờ vậy, KHÔNG có sự pha trộn trọng số, KHÔNG có double edge, KHÔNG có vết xám do chia/nhân.
        float fused_val = std::max(mod_alpha, eroded_alpha);
        fused_val = std::min(fused_val, dilated_alpha);

        fused[i] = clamp01(fused_val);
    }

    jfloatArray result = env->NewFloatArray(size);
    if (result == nullptr || env->ExceptionCheck()) return nullptr;
    env->SetFloatArrayRegion(result, 0, size, fused.data());
    if (env->ExceptionCheck()) return nullptr;
    return result;
}

// Blur 3x3 chỉ trong vùng allowed, không làm lem mask ra ngoài.
static void box_blur_in_allowed(
    const float* alpha,
    const float* allowed,
    float* out,
    int width,
    int height
) {
    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            const int idx = y * width + x;
            if (allowed[idx] < 0.5f) {
                out[idx] = 0.0f;
                continue;
            }

            float sum = 0.0f;
            int count = 0;
            for (int yy = std::max(0, y - 1); yy <= std::min(height - 1, y + 1); ++yy) {
                const int row = yy * width;
                for (int xx = std::max(0, x - 1); xx <= std::min(width - 1, x + 1); ++xx) {
                    const int n = row + xx;
                    if (allowed[n] > 0.5f) {
                        sum += alpha[n];
                        ++count;
                    }
                }
            }

            out[idx] = count > 0 ? clamp01(sum / static_cast<float>(count)) : 0.0f;
        }
    }
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_toshiba_modnet_NativeMaskFusion_nativeRefineCoreStrict(
    JNIEnv* env,
    jobject,
    jintArray source_pixels,
    jfloatArray detail_mask,
    jfloatArray core_mask,
    jint width,
    jint height,
    jint close_radius,
    jboolean green_suppress
) {
    if (
        source_pixels == nullptr ||
        detail_mask == nullptr ||
        core_mask == nullptr ||
        width <= 0 ||
        height <= 0
    ) {
        LOGE("Invalid core-first refine input");
        return nullptr;
    }

    const int64_t size64 = static_cast<int64_t>(width) * static_cast<int64_t>(height);
    if (size64 <= 0 || size64 > 100000000) {
        LOGE("Invalid core-first refine mask size");
        return nullptr;
    }

    const jsize size = static_cast<jsize>(size64);
    if (
        env->GetArrayLength(source_pixels) != size ||
        env->GetArrayLength(detail_mask) != size ||
        env->GetArrayLength(core_mask) != size
    ) {
        LOGE("Core-first refine length mismatch");
        return nullptr;
    }

    // === Allocations ===
    std::vector<jint> pixels(size);
    std::vector<float> detail(size);
    std::vector<float> core(size);

    // Morphological masks (all float, 0.0 or 1.0 for binary ops)
    std::vector<float> hardCore(size);     // core > 0.30
    std::vector<float> softCore(size);     // core > 0.08
    std::vector<float> allowedTemp(size);
    std::vector<float> allowed(size);      // dilate(softCore, r=6)
    std::vector<float> closedHTemp(size);
    std::vector<float> closedHard(size);   // close(hardCore, r=adaptive)
    std::vector<float> innerHTemp(size);
    std::vector<float> innerHard(size);    // erode(hardCore, r=2)
    std::vector<float> edgeBand(size);     // allowed && !innerHard

    std::vector<float> alpha(size);
    std::vector<float> blurred(size);
    std::vector<uint8_t> fill(size, 0);

    // === Fetch arrays ===
    env->GetIntArrayRegion(source_pixels, 0, size, pixels.data());
    if (env->ExceptionCheck()) return nullptr;
    env->GetFloatArrayRegion(detail_mask, 0, size, detail.data());
    if (env->ExceptionCheck()) return nullptr;
    env->GetFloatArrayRegion(core_mask, 0, size, core.data());
    if (env->ExceptionCheck()) return nullptr;

    // Conservative path: keep ML Kit as the base and only touch a thin edge band.
    // If this path later proves too different from core, Kotlin will fall back to raw ML Kit.
    {
        std::vector<float> hardCoreLocal(size, 0.0f);
        std::vector<float> softCoreLocal(size, 0.0f);
        std::vector<float> allowed(size, 0.0f);
        std::vector<float> edgeBand(size, 0.0f);
        std::vector<float> alpha(size, 0.0f);
        std::vector<float> blurred(size, 0.0f);

        int edge_count = 0;
        int green_suppressed = 0;
        int green_rgb_decontam = 0;

        for (jsize i = 0; i < size; ++i) {
            hardCoreLocal[i] = core[i] > 0.28f ? 1.0f : 0.0f;
            softCoreLocal[i] = core[i] > 0.08f ? 1.0f : 0.0f;
        }

        // Fix Bug 3: Giảm adaptive dilate max từ 8→4px — ít background bleeding hơn
        // @ 1536px: 1536/300≈5.1 → min(4,5)=4px (chặt hơn, ít white fuzz)
        // @ 768px: 768/300≈2.5 → max(2,2.5)=3px (không đổi nhiều)
        const int adapt_r = std::max(2, std::min(4, std::max(width, height) / 300));
        circular_dilate(softCoreLocal.data(), allowed.data(), width, height, adapt_r);


        for (jsize i = 0; i < size; ++i) {
            edgeBand[i] = (allowed[i] > 0.5f && hardCoreLocal[i] < 0.5f) ? 1.0f : 0.0f;
        }

        for (jsize i = 0; i < size; ++i) {
            const float c = core[i];
            const float d = detail[i];

            float value = 0.0f;
            if (allowed[i] < 0.5f || c <= 0.025f) {
                value = 0.0f;
            } else if (c >= 0.70f) {
                value = std::max(c, d * 0.94f);
            } else if (c >= 0.30f) {
                value = std::min(std::max(c * 0.94f, d * 0.82f), c + 0.05f);
                ++edge_count;
            } else if (c >= 0.10f) {
                value = std::min(std::max(c * 0.88f, d * 0.72f), c + 0.03f);
                ++edge_count;
            } else {
                value = c * 0.70f;
                ++edge_count;
            }

            if (green_suppress && edgeBand[i] > 0.5f) {
                const float veg = vegetation_greenness(pixels[i]);
                if (veg > 0.35f) {
                    if (c < 0.16f) {
                        value = 0.0f;
                    } else {
                        value = std::min(value, c * (0.28f + (1.0f - veg) * 0.35f));
                    }
                    ++green_suppressed;
                    if (veg > 0.55f) {
                        ++green_rgb_decontam;
                    }
                }
            }

            alpha[i] = clamp01(value);
        }

        // Tầng 4: 3-pass separable blur ≈ Gaussian σ≈1.7 — mượt hơn single 3×3 box
        multipass_blur_in_allowed(alpha.data(), allowed.data(), blurred.data(), width, height, 3);

        for (jsize i = 0; i < size; ++i) {
            const float c = core[i];
            if (allowed[i] < 0.5f) {
                blurred[i] = 0.0f;
            } else if (c >= 0.65f) {
                blurred[i] = std::max(blurred[i], c);
            } else if (c < 0.22f) {
                blurred[i] = std::min(blurred[i], c + 0.03f);
            } else {
                blurred[i] = std::min(blurred[i], c + 0.05f);
            }
            blurred[i] = clamp01(blurred[i]);
        }

        LOGD(
            "Core-first strict-plus-soft: edgeR=%.4f greenSupp=%.4f greenRgb=%.4f",
            static_cast<double>(edge_count) / static_cast<double>(size),
            static_cast<double>(green_suppressed) / static_cast<double>(size),
            static_cast<double>(green_rgb_decontam) / static_cast<double>(size)
        );

        jfloatArray result = env->NewFloatArray(size);
        if (result == nullptr || env->ExceptionCheck()) return nullptr;
        env->SetFloatArrayRegion(result, 0, size, blurred.data());
        if (env->ExceptionCheck()) return nullptr;
        return result;
    }

    // === Adaptive close radius ===
    const int maxSide = std::max(width, height);
    const int adaptiveClose = std::max(3, std::min(8, maxSide / 96));
    (void)close_radius;  // kept for API compat, replaced by adaptive

    // === Build morphological masks ===
    for (jsize i = 0; i < size; ++i) {
        core[i] = clamp01(core[i]);
        detail[i] = clamp01(detail[i]);
        hardCore[i] = core[i] > 0.30f ? 1.0f : 0.0f;
        softCore[i] = core[i] > 0.08f ? 1.0f : 0.0f;
    }

    // allowed = dilate(softCore, r=6) — vùng an toàn hẹp quanh ML Kit
    circular_dilate(softCore.data(), allowedTemp.data(), width, height, 6);
    for (int i = 0; i < size; ++i) allowed[i] = allowedTemp[i];

    // closedHard = close(hardCore, r=adaptiveClose)
    circular_dilate(hardCore.data(), closedHTemp.data(), width, height, adaptiveClose);
    circular_erode(closedHTemp.data(), closedHard.data(), width, height, adaptiveClose);

    // innerHard = erode(hardCore, r=2)
    circular_erode(hardCore.data(), innerHTemp.data(), width, height, 2);
    circular_erode(innerHTemp.data(), innerHard.data(), width, height, 1);

    // edgeBand = allowed && !innerHard
    for (jsize i = 0; i < size; ++i) {
        edgeBand[i] = (allowed[i] > 0.5f && innerHard[i] < 0.5f) ? 1.0f : 0.0f;
    }

    // === Fill holes ===
    int componentCount = 0;
    int filledComponents = 0;
    mark_fillable_holes_strict_plus(
        closedHard.data(), hardCore.data(), allowed.data(),
        fill.data(), width, height,
        &componentCount, &filledComponents
    );

    // === Alpha assignment per zone (vegetation-aware) ===
    int filledPixels = 0;
    int greenSuppressed = 0;
    int edgePixels = 0;
    for (jsize i = 0; i < size; ++i) {
        const float c = core[i];
        const float d = detail[i];

        float value;
        if (c <= 0.025f || allowed[i] < 0.5f) {
            value = 0.0f;
        } else if (fill[i]) {
            value = 1.0f;
            ++filledPixels;
        } else if (innerHard[i] > 0.5f) {
            // Ruột đặc: bảo toàn foreground, không đụng vegetation suppression
            value = std::max(0.98f, std::max(c, d));
        } else if (hardCore[i] > 0.5f) {
            value = std::max(0.94f, std::max(c, d));
            // Vegetation guard: nếu hardCore nhưng màu cây/cỏ rất mạnh → ML Kit có thể sai
            if (green_suppress) {
                float greenness = vegetation_greenness(pixels[i]);
                if (greenness > 0.55f && c < 0.72f) {
                    float penalty = 1.0f - (greenness - 0.45f) * 0.55f;
                    value = std::max(0.78f, value * penalty);
                    ++greenSuppressed;
                }
            }
        } else if (edgeBand[i] > 0.5f) {
            ++edgePixels;
            if (green_suppress) {
                float greenness = vegetation_greenness(pixels[i]);
                if (greenness > 0.30f) {
                    // Edge band + vegetation → đây là nền cây/cỏ, không phải subject
                    if (c < 0.15f || greenness > 0.72f) {
                        value = 0.0f;
                    } else if (c < 0.32f) {
                        value = c * 0.12f;
                    } else {
                        value = c * (1.0f - greenness * 0.85f);
                    }
                    ++greenSuppressed;
                } else {
                    // Normal edge: blend detail + core
                    if (d < 0.30f) {
                        value = c * 0.75f;
                    } else {
                        value = std::min(std::max(c * 0.85f, d), c + 0.08f);
                    }
                }
            } else {
                if (d < 0.30f) {
                    value = c * 0.75f;
                } else {
                    value = std::min(std::max(c * 0.85f, d), c + 0.08f);
                }
            }
        } else {
            // softCore nhưng ngoài hardCore và edgeBand → weak
            value = c * 0.75f;
        }

        alpha[i] = clamp01(value);
    }

    // === Post-blur chỉ trong vùng allowed ===
    box_blur_in_allowed(alpha.data(), allowed.data(), blurred.data(), width, height);

    // === Clamp sau blur + vegetation re-suppress ===
    int postBlurGreenSuppressed = 0;
    for (jsize i = 0; i < size; ++i) {
        const float c = core[i];
        if (allowed[i] < 0.5f) {
            blurred[i] = 0.0f;
        } else if (fill[i] || innerHard[i] > 0.5f) {
            blurred[i] = std::max(blurred[i], alpha[i]);
        } else if (softCore[i] < 0.5f) {
            blurred[i] = std::min(blurred[i], c + 0.08f);
        } else if (edgeBand[i] > 0.5f) {
            blurred[i] = std::min(blurred[i], c + 0.08f);
            // Vegetation re-suppress: blur có thể lan alpha từ lân cận vào vùng đã suppressed
            if (green_suppress && blurred[i] > 0.04f) {
                float greenness = vegetation_greenness(pixels[i]);
                if (greenness > 0.35f) {
                    blurred[i] = c < 0.25f ? 0.0f : std::min(blurred[i], c * 0.22f);
                    ++postBlurGreenSuppressed;
                }
            }
        }
        blurred[i] = clamp01(blurred[i]);
    }

    // === Debug logging ===
    float filledRatio = static_cast<float>(filledPixels) / static_cast<float>(size);
    float edgeRatio = static_cast<float>(edgePixels) / static_cast<float>(size);
    float greenRatio = static_cast<float>(greenSuppressed) / static_cast<float>(size);
    float postBlurGreenRatio = static_cast<float>(postBlurGreenSuppressed) / static_cast<float>(size);
    LOGD(
        "Core-first strict-plus: filledComp=%d filledR=%.4f edgeR=%.4f greenSupp=%.4f postBlurGreen=%.4f adaptiveClose=%d",
        filledComponents,
        static_cast<double>(filledRatio),
        static_cast<double>(edgeRatio),
        static_cast<double>(greenRatio),
        static_cast<double>(postBlurGreenRatio),
        adaptiveClose
    );

    jfloatArray result = env->NewFloatArray(size);
    if (result == nullptr || env->ExceptionCheck()) return nullptr;
    env->SetFloatArrayRegion(result, 0, size, blurred.data());
    if (env->ExceptionCheck()) return nullptr;
    return result;
}

// ============================================================
// Tầng 2: Guided Filter Refine — JNI entry point
// Nhận ảnh gốc ARGB pixels, mask float [0,1], chạy Guided Filter
// với luminance grayscale làm guidance image.
// radius: 8-16 là dải tốt; epsilon: 1e-4 đến 1e-3
// ============================================================
extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_toshiba_modnet_NativeMaskFusion_nativeGuidedFilterRefine(
    JNIEnv* env,
    jobject,
    jintArray source_pixels,
    jfloatArray mask_data,
    jint width,
    jint height,
    jint radius,
    jfloat epsilon
) {
    if (source_pixels == nullptr || mask_data == nullptr || width <= 0 || height <= 0) {
        LOGE("Invalid guided filter input");
        return nullptr;
    }

    const int64_t size64 = static_cast<int64_t>(width) * static_cast<int64_t>(height);
    if (size64 <= 0 || size64 > 100000000LL) {
        LOGE("Guided filter: invalid mask size");
        return nullptr;
    }
    const jsize size = static_cast<jsize>(size64);

    if (env->GetArrayLength(source_pixels) != size || env->GetArrayLength(mask_data) != size) {
        LOGE("Guided filter: array length mismatch");
        return nullptr;
    }

    std::vector<jint>  pixels(size);
    std::vector<float> mask(size);
    std::vector<float> guide(size);
    std::vector<float> refined(size);

    env->GetIntArrayRegion(source_pixels, 0, size, pixels.data());
    if (env->ExceptionCheck()) return nullptr;
    env->GetFloatArrayRegion(mask_data, 0, size, mask.data());
    if (env->ExceptionCheck()) return nullptr;

    // Chuyển ARGB → luminance grayscale [0,1] làm guidance
    // Dùng BT.601 luma: Y = 0.299R + 0.587G + 0.114B
    for (jsize i = 0; i < size; ++i) {
        const jint color = pixels[i];
        const float r = static_cast<float>((color >> 16) & 0xFF) / 255.0f;
        const float g = static_cast<float>((color >>  8) & 0xFF) / 255.0f;
        const float b = static_cast<float>( color        & 0xFF) / 255.0f;
        guide[i] = 0.299f * r + 0.587f * g + 0.114f * b;
    }

    // Clamp radius để đảm bảo không quá lớn gây chậm
    const int safe_radius = std::max(4, std::min(radius, 24));
    const float safe_epsilon = std::max(1e-5f, std::min(epsilon, 1e-1f));

    guided_filter_gray(guide.data(), mask.data(), refined.data(),
                       width, height, safe_radius, safe_epsilon);

    // Fix Bug 2: Bảo vệ hard regions — GF chỉ được touch vùng transition [0.06, 0.94]
    // Vùng hard foreground (>0.94): giữ nguyên mask gốc — tránh GF xoá mất chủ thể
    // Vùng hard background (<0.06): giữ nguyên 0 — tránh GF tạo white fuzz ở nền
    // Blend mượt dần vào trong vùng transition để không tạo bậc thang mới
    static const float HARD_BG  = 0.06f;
    static const float HARD_FG  = 0.94f;
    static const float BLEND_BW = 0.08f;  // blend bandwidth: 0.06..0.14 và 0.86..0.94
    for (jsize i = 0; i < size; ++i) {
        const float orig = mask[i];
        if (orig <= HARD_BG) {
            // Nền chắc: không cho GF tạo bất kỳ artifact nào
            refined[i] = 0.0f;
        } else if (orig >= HARD_FG) {
            // Foreground chắc: giữ nguyên, không để GF xoá
            refined[i] = orig;
        } else if (orig < HARD_BG + BLEND_BW) {
            // Transition gần nền: blend từ orig → GF
            const float t = (orig - HARD_BG) / BLEND_BW;
            refined[i] = clamp01(orig * (1.0f - t) + refined[i] * t);
        } else if (orig > HARD_FG - BLEND_BW) {
            // Transition gần foreground: blend từ GF → orig
            const float t = (orig - (HARD_FG - BLEND_BW)) / BLEND_BW;
            refined[i] = clamp01(refined[i] * (1.0f - t) + orig * t);
        }
        // else: vùng transition thuần [0.14, 0.86] → dùng GF result nguyên bản
        refined[i] = clamp01(refined[i]);
    }

    LOGD("Guided filter: %dx%d r=%d eps=%.1e", width, height, safe_radius,
         static_cast<double>(safe_epsilon));

    jfloatArray result = env->NewFloatArray(size);
    if (result == nullptr || env->ExceptionCheck()) return nullptr;
    env->SetFloatArrayRegion(result, 0, size, refined.data());
    if (env->ExceptionCheck()) return nullptr;
    return result;
}



