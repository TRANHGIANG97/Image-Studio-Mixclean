#include "neon_kernels.h"
#include <cstring>

#ifdef __aarch64__

namespace neon {

// ─── darkenRow ────────────────────────────────────────────────────────────────
// Multiplies RGB (not Alpha) of each pixel by `factor/255`.
// Uses NEON vld4_u8 to de-interleave RGBA into separate channel vectors.
void darkenRow(uint8_t* row, int width, uint8_t factor) {
    const uint8x8_t fac = vdup_n_u8(factor);
    int x = 0;

    // Handle unaligned head pixels (scalar)
    while (x < width && (reinterpret_cast<uintptr_t>(row + x * 4) & 7u) != 0) {
        uint8_t* p = row + x * 4;
        p[0] = div255(static_cast<uint32_t>(p[0]) * factor);
        p[1] = div255(static_cast<uint32_t>(p[1]) * factor);
        p[2] = div255(static_cast<uint32_t>(p[2]) * factor);
        ++x;
    }

    // SIMD: process 8 pixels per iteration
    const int simdEnd = x + ((width - x) & ~7);
    for (; x < simdEnd; x += 8) {
        uint8x8x4_t pix = vld4_u8(row + x * 4);
        const uint16x8_t r16 = vmull_u8(pix.val[0], fac);
        const uint16x8_t g16 = vmull_u8(pix.val[1], fac);
        const uint16x8_t b16 = vmull_u8(pix.val[2], fac);
        pix.val[0] = div255_u16(r16);
        pix.val[1] = div255_u16(g16);
        pix.val[2] = div255_u16(b16);
        // val[3] (alpha) unchanged
        vst4_u8(row + x * 4, pix);
    }

    // Scalar tail
    for (; x < width; ++x) {
        uint8_t* p = row + x * 4;
        p[0] = div255(static_cast<uint32_t>(p[0]) * factor);
        p[1] = div255(static_cast<uint32_t>(p[1]) * factor);
        p[2] = div255(static_cast<uint32_t>(p[2]) * factor);
    }
}

// ─── compositeRow ─────────────────────────────────────────────────────────────
// Porter-Duff "src-over":  out = fg + bg * (1 - fg.alpha)
// FIX: NEON path uses vmovn_u16 which saturates on overflow — safe.
//      Scalar path uses addClamp() for explicit saturation.
void compositeRow(uint8_t* dst, const uint8_t* fg, int width) {
    int x = 0;

    // Unaligned head (scalar)
    while (x < width && ((reinterpret_cast<uintptr_t>(dst + x * 4) & 7u) != 0 ||
                         (reinterpret_cast<uintptr_t>(fg  + x * 4) & 7u) != 0)) {
        const uint8_t a = fg[x * 4 + 3];
        if (a == 0)   { ++x; continue; }
        if (a == 255) { std::memcpy(dst + x * 4, fg + x * 4, 4); ++x; continue; }
        const uint8_t inv = static_cast<uint8_t>(255u - a);
        dst[x*4+0] = addClamp(fg[x*4+0], div255(static_cast<uint32_t>(dst[x*4+0]) * inv));
        dst[x*4+1] = addClamp(fg[x*4+1], div255(static_cast<uint32_t>(dst[x*4+1]) * inv));
        dst[x*4+2] = addClamp(fg[x*4+2], div255(static_cast<uint32_t>(dst[x*4+2]) * inv));
        dst[x*4+3] = addClamp(a,          div255(static_cast<uint32_t>(dst[x*4+3]) * inv));
        ++x;
    }

    // SIMD: process 8 pixels per iteration
    const int simdEnd = x + ((width - x) & ~7);
    for (; x < simdEnd; x += 8) {
        const uint8x8x4_t f = vld4_u8(fg  + x * 4);
        const uint8x8x4_t b = vld4_u8(dst + x * 4);

        const uint16x8_t alpha    = vmovl_u8(f.val[3]);
        const uint16x8_t invAlpha = vsubq_u16(vdupq_n_u16(255), alpha);

        // out_c = fg_c + bg_c*(1-alpha)/255
        // vqadd_u8 saturates on overflow, which safely handles uint8 arithmetic.
        uint8x8x4_t out;
        out.val[0] = vqadd_u8(f.val[0], div255_u16(vmulq_u16(vmovl_u8(b.val[0]), invAlpha)));
        out.val[1] = vqadd_u8(f.val[1], div255_u16(vmulq_u16(vmovl_u8(b.val[1]), invAlpha)));
        out.val[2] = vqadd_u8(f.val[2], div255_u16(vmulq_u16(vmovl_u8(b.val[2]), invAlpha)));
        out.val[3] = vqadd_u8(f.val[3], div255_u16(vmulq_u16(vmovl_u8(b.val[3]), invAlpha)));
        vst4_u8(dst + x * 4, out);
    }

    // Scalar tail
    for (; x < width; ++x) {
        const uint8_t a = fg[x * 4 + 3];
        if (a == 0)   continue;
        if (a == 255) { std::memcpy(dst + x * 4, fg + x * 4, 4); continue; }
        const uint8_t inv = static_cast<uint8_t>(255u - a);
        dst[x*4+0] = addClamp(fg[x*4+0], div255(static_cast<uint32_t>(dst[x*4+0]) * inv));
        dst[x*4+1] = addClamp(fg[x*4+1], div255(static_cast<uint32_t>(dst[x*4+1]) * inv));
        dst[x*4+2] = addClamp(fg[x*4+2], div255(static_cast<uint32_t>(dst[x*4+2]) * inv));
        dst[x*4+3] = addClamp(a,          div255(static_cast<uint32_t>(dst[x*4+3]) * inv));
    }
}

} // namespace neon

#endif // __aarch64__
