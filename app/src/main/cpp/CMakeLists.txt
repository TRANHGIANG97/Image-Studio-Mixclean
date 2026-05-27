cmake_minimum_required(VERSION 3.22.1)
project("portraitblend")

# ── 16 KB page-size alignment (Android 15+) ────────────────────────────────────
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384")

set(CMAKE_CXX_STANDARD          17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ── Compiler flags ─────────────────────────────────────────────────────────────
# -O3              : max optimisation
# -ffast-math      : allow FP reassociation, etc.
# -fno-finite-math-only : but keep NaN/Inf semantics (prevents misoptimisation)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -fomit-frame-pointer")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -ffast-math -fno-finite-math-only")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wall -Wextra -Wno-unused-parameter")

# NEON: automatically available on arm64-v8a; -march enables SIMD intrinsics
if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -march=armv8-a+fp+simd")
endif()

# ── OpenMP (optional multi-threading) ──────────────────────────────────────────
find_package(OpenMP)

# ── System libraries ───────────────────────────────────────────────────────────
find_library(log-lib         log)
find_library(jnigraphics-lib jnigraphics)

# ══════════════════════════════════════════════════════════════════════════════
# Library 1: portraitblend (existing — Seamless/Poisson blend via JNI)
# ══════════════════════════════════════════════════════════════════════════════
add_library(
        portraitblend
        SHARED
        portrait_blend.cpp
)
target_link_libraries(
        portraitblend
        ${jnigraphics-lib}
        ${log-lib}
)

# ══════════════════════════════════════════════════════════════════════════════
# Library 2: portrait_processor (new — Portrait/Clean/Darken effects via NEON)
# ══════════════════════════════════════════════════════════════════════════════
add_library(
        portrait_processor
        SHARED
        portrait/image_processor.cpp
        portrait/neon_kernels.cpp
        portrait/native_portrait.cpp
)

target_include_directories(
        portrait_processor
        PRIVATE
        ${CMAKE_SOURCE_DIR}/portrait
)

target_link_libraries(
        portrait_processor
        ${jnigraphics-lib}
        ${log-lib}
        $<$<BOOL:${OpenMP_CXX_FOUND}>:OpenMP::OpenMP_CXX>
)

# ══════════════════════════════════════════════════════════════════════════════
# Library 3: bg_refiner (Background removal alpha refinement)
# ══════════════════════════════════════════════════════════════════════════════
add_library(
        bg_refiner
        SHARED
        bg_refiner/bg_refiner.cpp
        bg_refiner/native_refiner.cpp
        bg_refiner/onnx_decryptor.cpp
        bg_refiner/mask_fusion.cpp
)

target_include_directories(
        bg_refiner
        PRIVATE
        ${CMAKE_SOURCE_DIR}/bg_refiner
)

target_link_libraries(
        bg_refiner
        ${jnigraphics-lib}
        ${log-lib}
)
