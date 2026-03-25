cmake_minimum_required(VERSION 3.22.1)
project("monochrome_native")

# ── ProjectM Visualizer ──────────────────────────────────────────────────
add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/../../../third_party/projectm
    ${CMAKE_CURRENT_BINARY_DIR}/projectm)

add_library(monochrome_visualizer SHARED
    projectm_bridge.cpp
    projectm_jni.cpp
    audio_ring_buffer.cpp
)

target_include_directories(monochrome_visualizer PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/../../../third_party/projectm/src
)

target_link_libraries(monochrome_visualizer
    android
    log
    EGL
    GLESv3
    projectm
)

# ── DSP Engine ───────────────────────────────────────────────────────────
add_library(monochrome_dsp SHARED
    dsp/dsp_engine.cpp
    dsp/dsp_jni.cpp
)

target_include_directories(monochrome_dsp PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/dsp
    ${CMAKE_CURRENT_SOURCE_DIR}/dsp/util
    ${CMAKE_CURRENT_SOURCE_DIR}/dsp/snapins
)

target_compile_options(monochrome_dsp PRIVATE
    -O3 -ffast-math -fno-exceptions -fno-rtti
    # ARM NEON SIMD for float processing on arm64
    $<$<STREQUAL:${ANDROID_ABI},arm64-v8a>:-march=armv8-a+simd>
)

target_link_libraries(monochrome_dsp
    android
    log
)
