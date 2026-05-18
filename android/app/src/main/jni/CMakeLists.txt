cmake_minimum_required(VERSION 3.22.1)
project("bs" C CXX)

# Engine source root relative to this CMakeLists:
# jni -> main -> app -> android -> repo root
set(ENGINE_ROOT "${CMAKE_CURRENT_LIST_DIR}/../../../../..")
get_filename_component(ENGINE_ROOT "${ENGINE_ROOT}" ABSOLUTE)

# ── Include SDL2 and SDL2_mixer sources ─────────────────────────
include(FetchContent)

FetchContent_Declare(
    SDL2
    URL https://github.com/libsdl-org/SDL/releases/download/release-2.30.11/SDL2-2.30.11.tar.gz
)
# Build SDL2 as static library for Android
set(SDL_SHARED OFF CACHE BOOL "Build static SDL2" FORCE)
set(SDL_STATIC ON CACHE BOOL "Build static SDL2" FORCE)

FetchContent_MakeAvailable(SDL2)

# ── Engine sources ───────────────────────────────────────────────
set(ENGINE_SOURCES
    "${ENGINE_ROOT}/avi_player.cpp"
    "${ENGINE_ROOT}/bag.cpp"
    "${ENGINE_ROOT}/decoder.cpp"
    "${ENGINE_ROOT}/dialogue.cpp"
    "${ENGINE_ROOT}/file.cpp"
    "${ENGINE_ROOT}/fs.cpp"
    "${ENGINE_ROOT}/game.cpp"
    "${ENGINE_ROOT}/menu.cpp"
    "${ENGINE_ROOT}/mixer_soft.cpp"
    "${ENGINE_ROOT}/opcodes.cpp"
    "${ENGINE_ROOT}/parser_dlg.cpp"
    "${ENGINE_ROOT}/parser_scn.cpp"
    "${ENGINE_ROOT}/random.cpp"
    "${ENGINE_ROOT}/resource.cpp"
    "${ENGINE_ROOT}/saveload.cpp"
    "${ENGINE_ROOT}/screenshot.cpp"
    "${ENGINE_ROOT}/staticres.cpp"
    "${ENGINE_ROOT}/str.cpp"
    "${ENGINE_ROOT}/systemstub_sdl.cpp"
    "${ENGINE_ROOT}/util.cpp"
    "${ENGINE_ROOT}/win16.cpp"
)

# Android-specific main (wraps the original main logic)
set(ENGINE_SOURCES ${ENGINE_SOURCES}
    "${CMAKE_CURRENT_LIST_DIR}/src/android_main.cpp"
)

# ── Shared library ───────────────────────────────────────────────
add_library(bs SHARED ${ENGINE_SOURCES})

target_include_directories(bs PRIVATE
    "${ENGINE_ROOT}"
    "${SDL2_SOURCE_DIR}/include"
)

target_compile_definitions(bs PRIVATE
    BERMUDA_POSIX
    BERMUDA_ZLIB
    BERMUDA_STB_VORBIS
)

target_compile_options(bs PRIVATE
    -g -O2 -Wall
    -Wno-unused-parameter
    -Wno-sign-compare
)

target_link_libraries(bs
    SDL2-static
    z
    log
)

# NDK 27: libc++ is at a non-standard path, add directly
if(ANDROID)
    target_link_options(bs PRIVATE -lc++_shared)
endif()

# SDL_main entry point for Android
target_link_libraries(bs SDL2main)
