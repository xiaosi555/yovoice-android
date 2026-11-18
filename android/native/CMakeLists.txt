cmake_minimum_required(VERSION 3.22.1)
project(yovoice_android_native LANGUAGES C CXX)

# 以固定上游提交构建 audio.cpp C ABI，避免依赖桌面端可执行文件。
include(FetchContent)
set(FETCHCONTENT_UPDATES_DISCONNECTED ON)

set(AUDIOCPP_BUILD_C_API ON CACHE BOOL "" FORCE)
set(AUDIOCPP_BUILD_C_API_STATIC OFF CACHE BOOL "" FORCE)
set(AUDIOCPP_DEPLOYMENT_BUILD ON CACHE BOOL "" FORCE)
set(AUDIOCPP_BUILD_NATIVE_MODEL_MANAGER OFF CACHE BOOL "" FORCE)

# 只编译 Yovoice 模型目录实际使用的模型族，避免把上游 140+ 个无关模型都塞入 APK。
# Gradium Phonon、HTDemucs 6-stem 与 BS-RoFormer 使用 audio.cpp 对应的官方别名。
set(AUDIOCPP_MODEL_SET custom CACHE STRING "" FORCE)
# Android 编译语音合成/克隆、ASR、六轨分离、音频转 MIDI 与文本音乐生成模型族。
# qwen3_asr 的强制对齐器依赖由 audio.cpp CMake 模型依赖图自动加入。
set(AUDIOCPP_MODELS
    "audio8_tts;qwen3_asr;demucs;muscriptor;stable_audio"
    CACHE STRING "" FORCE)

# Android 首轮只构建 ARM64 CPU 后端；关闭桌面 GPU、OpenMP 与宿主机 ISA 探测。
set(ENGINE_ENABLE_CUDA OFF CACHE BOOL "" FORCE)
set(ENGINE_ENABLE_HIP OFF CACHE BOOL "" FORCE)
set(ENGINE_ENABLE_VULKAN OFF CACHE BOOL "" FORCE)
set(ENGINE_ENABLE_WEBGPU OFF CACHE BOOL "" FORCE)
set(ENGINE_ENABLE_SYCL OFF CACHE BOOL "" FORCE)
set(ENGINE_ENABLE_METAL OFF CACHE BOOL "" FORCE)
set(ENGINE_ENABLE_RKNN OFF CACHE BOOL "" FORCE)
set(ENGINE_ENABLE_OPENMP OFF CACHE BOOL "" FORCE)
set(ENGINE_ENABLE_NATIVE_CPU OFF CACHE BOOL "" FORCE)
set(ENGINE_ENABLE_LLAMAFILE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(ENGINE_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(ENGINE_BUILD_EXTENDED_TESTS OFF CACHE BOOL "" FORCE)
set(ENGINE_BUILD_MODEL_TESTS OFF CACHE BOOL "" FORCE)
set(ENGINE_BUILD_WARMBENCH OFF CACHE BOOL "" FORCE)

FetchContent_Declare(
    audio_cpp
    GIT_REPOSITORY https://github.com/0xShug0/audio.cpp.git
    GIT_TAG a6e32214e762aca6126eb7206a5497f11d064303
    GIT_SHALLOW FALSE
    GIT_SUBMODULES external/audio.cpp-server-frontends
    GIT_SUBMODULES_RECURSE TRUE
)
FetchContent_MakeAvailable(audio_cpp)

if (NOT TARGET audiocpp)
    message(FATAL_ERROR "audio.cpp did not define the expected audiocpp C ABI target")
endif()

# JNI bridge: expose a minimal native runtime probe to Kotlin.
add_library(yovoice_jni SHARED yovoice_jni.cpp)
target_include_directories(yovoice_jni PRIVATE "${audio_cpp_SOURCE_DIR}/include")
target_compile_features(yovoice_jni PRIVATE cxx_std_17)
find_library(ANDROID_LOG_LIB log REQUIRED)
# audio.cpp itself calls Android logging APIs; linking log only to the JNI
# wrapper is insufficient because the undefined symbol is in libaudiocpp.so.
target_link_libraries(audiocpp PUBLIC ${ANDROID_LOG_LIB})
target_link_libraries(yovoice_jni PRIVATE audiocpp ${ANDROID_LOG_LIB})
set_target_properties(yovoice_jni PROPERTIES
    LIBRARY_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/bin"
)
