cmake_minimum_required(VERSION 3.22.1)
project("nexstream_whisper" LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Disable x86 SIMD that isn't available on Android ARM targets
set(GGML_AVX        OFF CACHE BOOL "" FORCE)
set(GGML_AVX2       OFF CACHE BOOL "" FORCE)
set(GGML_AVX512     OFF CACHE BOOL "" FORCE)
set(GGML_FMA        OFF CACHE BOOL "" FORCE)
set(GGML_F16C       OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS     OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES  OFF CACHE BOOL "" FORCE)

# armeabi-v7a: disable NEON to avoid ggml_vec_dot_f16 crash on chips that
# lack the fp16 vector arithmetic extension (__ARM_FEATURE_FP16_VECTOR_ARITHMETIC)
if(ANDROID_ABI STREQUAL "armeabi-v7a")
    set(GGML_NEON OFF CACHE BOOL "" FORCE)
endif()

set(WHISPER_DIR ${CMAKE_CURRENT_SOURCE_DIR}/whisper)

if(EXISTS "${WHISPER_DIR}/CMakeLists.txt")
    add_subdirectory(${WHISPER_DIR} whisper_build EXCLUDE_FROM_ALL)
else()
    message(STATUS "whisper.cpp not found locally — fetching via FetchContent")
    include(FetchContent)
    FetchContent_Declare(
        whisper
        GIT_REPOSITORY https://github.com/ggerganov/whisper.cpp.git
        GIT_TAG        master
        GIT_SHALLOW    TRUE
    )
    FetchContent_MakeAvailable(whisper)
    set(WHISPER_DIR ${whisper_SOURCE_DIR})
endif()

# ── JNI bridge ────────────────────────────────────────────────────────────────
add_library(nexwhisper SHARED jni_whisper.cpp)

target_include_directories(nexwhisper PRIVATE
    ${WHISPER_DIR}/include
    ${WHISPER_DIR}
)
target_link_libraries(nexwhisper
    whisper
    android
    log
)
