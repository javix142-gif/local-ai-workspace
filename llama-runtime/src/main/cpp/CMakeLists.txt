cmake_minimum_required(VERSION 3.31.6)

project("ai-chat" VERSION 1.0.0 LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED true)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED true)

set(CMAKE_C_FLAGS   "${CMAKE_C_FLAGS}"   CACHE STRING "" FORCE)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS}" CACHE STRING "" FORCE)

# --------------------------------------------------------------------------
# AI Chat library
# --------------------------------------------------------------------------

if(DEFINED ANDROID_ABI)
    message(STATUS "Detected Android ABI: ${ANDROID_ABI}")
    if(ANDROID_ABI STREQUAL "arm64-v8a")
        set(GGML_SYSTEM_ARCH "ARM")
        set(GGML_CPU_KLEIDIAI ON)
        # OpenMP is not part of Android's stable NDK surface. Keep the baseline
        # runtime portable; future device-specific builds can opt in explicitly.
        set(GGML_OPENMP OFF)
    elseif(ANDROID_ABI STREQUAL "x86_64")
        set(GGML_SYSTEM_ARCH "x86")
        set(GGML_CPU_KLEIDIAI OFF)
        set(GGML_OPENMP OFF)
    else()
        message(FATAL_ERROR "Unsupported ABI: ${ANDROID_ABI}")
    endif()
endif()

set(LLAMA_SRC ${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/llama.cpp)
set(LLAMA_MODELS_DIR ${LLAMA_SRC}/src/models)
file(GLOB LLAMA_MODEL_SOURCES "${LLAMA_MODELS_DIR}/*.cpp")
if(NOT EXISTS "${LLAMA_MODELS_DIR}/models.h" OR NOT LLAMA_MODEL_SOURCES)
    message(FATAL_ERROR
            "Vendored llama.cpp model sources are incomplete. Expected models.h and model implementation sources in ${LLAMA_MODELS_DIR}.")
endif()
add_subdirectory(${LLAMA_SRC} build-llama)

add_library(${CMAKE_PROJECT_NAME} SHARED
        ai_chat.cpp)

target_compile_definitions(${CMAKE_PROJECT_NAME} PRIVATE
        GGML_SYSTEM_ARCH=${GGML_SYSTEM_ARCH}
        GGML_CPU_KLEIDIAI=$<BOOL:${GGML_CPU_KLEIDIAI}>
        GGML_OPENMP=$<BOOL:${GGML_OPENMP}>
)

target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
        ${LLAMA_SRC}
        ${LLAMA_SRC}/common
        ${LLAMA_SRC}/include
        ${LLAMA_SRC}/ggml/include
        ${LLAMA_SRC}/ggml/src)

target_link_libraries(${CMAKE_PROJECT_NAME}
        llama
        llama-common
        android
        log)

# Auxiliary neural embeddings, separate from the generative GGUF engine.
add_library(local-embedding SHARED local_embedding_jni.cpp)
target_include_directories(local-embedding PRIVATE ${LLAMA_SRC}/include ${LLAMA_SRC}/ggml/include)
# ggml-cpu is a MODULE when GGML_BACKEND_DL is enabled. Link its actual official
# shared object explicitly so APK-backed native loading does not need directory scanning.
add_dependencies(local-embedding ggml-cpu)
# MODULE has no SONAME: use a basename lookup so DT_NEEDED never embeds a build path.
target_link_directories(local-embedding PRIVATE "$<TARGET_FILE_DIR:ggml-cpu>")
target_link_libraries(local-embedding llama "-l:$<TARGET_FILE_NAME:ggml-cpu>" android log)
