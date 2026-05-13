#ifndef NEON_KERNELS_H
#define NEON_KERNELS_H

#include <cstdint>
#include <cstring>
#include <algorithm>

#ifdef __aarch64__
#include <arm_neon.h>
#endif

namespace neon {

// ─── Scalar div255 ────────────────────────────────────────────────────────────
// inline to avoid ODR violations when included in multiple TUs.
// Formula: (val * 257 + 257) >> 16  ≈  val / 255,  exact for val in [0, 65535].
inline uint8_t div255(uint32_t val) {
    return static_cast<uint8_t>((val * 257u + 257u) >> 16);
}

// ─── Clamped add to avoid uint8 overflow in composite ─────────────────────────
inline uint8_t addClamp(uint8_t a, uint8_t b) {
    return static_cast<uint8_t>(std::min(255u, static_cast<uint32_t>(a) + static_cast<uint32_t>(b)));
}

// ─── NEON path (arm64-v8a) ────────────────────────────────────────────────────
#ifdef __aarch64__

// FIX: Use uint32x4_t intermediate to avoid uint16 overflow.
// vmulq_n_u16(val, 257): val can be up to 65535 → 65535×257 = 16,842,495 > 65535 → OVERFLOW
// Solution: widen to 32-bit first via vmull_n_u16 on low/high halves.
inline uint8x8_t div255_u16(uint16x8_t val) {
    uint32x4_t lo = vmull_n_u16(vget_low_u16(val),  257u);
    uint32x4_t hi = vmull_n_u16(vget_high_u16(val), 257u);
    lo = vaddq_u32(lo, vdupq_n_u32(257u));
    hi = vaddq_u32(hi, vdupq_n_u32(257u));
    lo = vshrq_n_u32(lo, 16);
    hi = vshrq_n_u32(hi, 16);
    return vmovn_u16(vcombine_u16(vmovn_u32(lo), vmovn_u32(hi)));
}

// Declared here; defined in neon_kernels.cpp
void darkenRow(uint8_t* row, int width, uint8_t factor);
void compositeRow(uint8_t* dst, const uint8_t* fg, int width);

#else // ─── Scalar fallback (armeabi-v7a, x86_64) ──────────────────────────────

inline void darkenRow(uint8_t* row, int width, uint8_t factor) {
    for (int x = 0; x < width; ++x) {
        uint8_t* p = row + x * 4;
        p[0] = div255(static_cast<uint32_t>(p[0]) * factor);
        p[1] = div255(static_cast<uint32_t>(p[1]) * factor);
        p[2] = div255(static_cast<uint32_t>(p[2]) * factor);
        // p[3] (alpha) preserved intentionally
    }
}

// FIX: Use addClamp to prevent overflow in "src-over" alpha blending.
// Porter-Duff "src-over": out = fg + bg*(1-alpha)
// fg is already pre-multiplied by its alpha in ML Kit output.
inline void compositeRow(uint8_t* dst, const uint8_t* fg, int width) {
    for (int x = 0; x < width; ++x) {
        const uint8_t a = fg[x * 4 + 3];
        if (a == 0) continue;
        if (a == 255) {
            std::memcpy(dst + x * 4, fg + x * 4, 4);
            continue;
        }
        const uint8_t inv = static_cast<uint8_t>(255u - a);
        // FIX: clamp the addition result to [0, 255] to avoid uint8 wrap-around
        dst[x*4+0] = addClamp(fg[x*4+0], div255(static_cast<uint32_t>(dst[x*4+0]) * inv));
        dst[x*4+1] = addClamp(fg[x*4+1], div255(static_cast<uint32_t>(dst[x*4+1]) * inv));
        dst[x*4+2] = addClamp(fg[x*4+2], div255(static_cast<uint32_t>(dst[x*4+2]) * inv));
        dst[x*4+3] = addClamp(a,          div255(static_cast<uint32_t>(dst[x*4+3]) * inv));
    }
}

#endif // __aarch64__

} // namespace neon

#endif // NEON_KERNELS_H
