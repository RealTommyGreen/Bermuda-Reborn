/*
 * Bermuda Syndrome Android entry point
 */
#include <SDL.h>
#include <android/log.h>
#include <jni.h>
#include <exception>
#include <cstdio>
#include <cstring>
#include "game.h"
#include "systemstub.h"

#define BS_LOGI(...) __android_log_print(ANDROID_LOG_INFO, "BSNative", __VA_ARGS__)
#define BS_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "BSNative", __VA_ARGS__)

static Game *g_game = nullptr;
static SystemStub *g_stub = nullptr;
static uint32_t g_pendingCheatMask = 0;
static int g_pendingScreenMode = -1;

extern "C" {

JNIEXPORT void JNICALL Java_com_bermudasyndrome_android_BermudaActivity_nativeSetCheat(JNIEnv *env, jclass cls, jint cheatId, jboolean enabled) {
    uint32_t bit = (cheatId == 0) ? kCheatNoHit : (cheatId == 1) ? kCheatInfiniteAmmo : (cheatId == 2) ? kCheatAllWeapons : 0;
    if (!bit) return;
    if (enabled) {
        g_pendingCheatMask |= bit;
    } else {
        g_pendingCheatMask &= ~bit;
    }
    if (g_game) {
        g_game->setCheatMask(g_pendingCheatMask);
    }
    BS_LOGI("nativeSetCheat cheatId=%d enabled=%d mask=0x%x", cheatId, enabled, g_pendingCheatMask);
}

JNIEXPORT void JNICALL Java_com_bermudasyndrome_android_BermudaActivity_nativeSetScreenMode(JNIEnv *env, jclass cls, jint mode) {
    g_pendingScreenMode = (mode == 1) ? SCREEN_MODE_16_9 : SCREEN_MODE_4_3;
    if (g_game) {
        g_game->setScreenMode(g_pendingScreenMode);
    }
    BS_LOGI("nativeSetScreenMode mode=%d", mode);
}

}

extern "C" int SDL_main(int argc, char *argv[]) {
    setvbuf(stdout, nullptr, _IONBF, 0);
    setvbuf(stderr, nullptr, _IONBF, 0);

    const char *dataPath = ".";
    const char *savePath = ".";
    const char *musicPath = "MUSIC";
    const char *soundfontPath = "";
    bool fullscreen = true;
    int screenMode = SCREEN_MODE_DEFAULT;

    BS_LOGI("SDL_main entered argc=%d", argc);
    for (int i = 0; i < argc; ++i) {
        BS_LOGI("argv[%d]=%s", i, argv[i] ? argv[i] : "(null)");
    }

    for (int i = 1; i < argc; ++i) {
        if (strncmp(argv[i], "--datapath=", 11) == 0) {
            dataPath = argv[i] + 11;
        } else if (strncmp(argv[i], "--savepath=", 11) == 0) {
            savePath = argv[i] + 11;
        } else if (strncmp(argv[i], "--musicpath=", 12) == 0) {
            musicPath = argv[i] + 12;
        } else if (strcmp(argv[i], "--fullscreen") == 0) {
            fullscreen = true;
        } else if (strncmp(argv[i], "--soundfont=", 12) == 0) {
            soundfontPath = argv[i] + 12;
        } else if (strncmp(argv[i], "--widescreen=", 13) == 0) {
            if (strcmp(argv[i] + 13, "16:9") == 0) {
                screenMode = SCREEN_MODE_16_9;
            } else if (strcmp(argv[i] + 13, "4:3") == 0) {
                screenMode = SCREEN_MODE_4_3;
            }
        }
    }

    BS_LOGI("Parsed args datapath=%s savepath=%s musicpath=%s fullscreen=%d screenMode=%d",
            dataPath, savePath, musicPath, fullscreen ? 1 : 0, screenMode);

    try {
        g_debugMask = DBG_INFO;
        BS_LOGI("Creating SystemStub_SDL");
        g_stub = SystemStub_SDL_create();
        BS_LOGI("Creating Game");
        g_game = new Game(g_stub, dataPath, savePath, musicPath, soundfontPath);
        if (g_pendingCheatMask != 0) {
            g_game->setCheatMask(g_pendingCheatMask);
            BS_LOGI("Applied pending cheat mask=0x%x", g_pendingCheatMask);
        }
        if (g_pendingScreenMode != -1) {
            g_game->setScreenMode(g_pendingScreenMode);
            BS_LOGI("Applied pending screen mode=%d", g_pendingScreenMode);
        }
        BS_LOGI("Calling Game::init");
        g_game->init(fullscreen, screenMode);
        BS_LOGI("Game::init returned quit=%d", g_stub->_quit ? 1 : 0);

        uint32_t lastFrameTimeStamp = g_stub->getTimeStamp();
        uint32_t frame = 0;
        while (!g_stub->_quit) {
            if (frame < 10 || (frame % 300) == 0) {
                BS_LOGI("Frame %u begin", frame);
            }
            g_game->mainLoop();
            uint32_t end = lastFrameTimeStamp + kCycleDelay;
            do {
                g_stub->sleep(10);
                g_stub->processEvents();
            } while (!g_stub->_pi.fastMode && g_stub->getTimeStamp() < end);
            lastFrameTimeStamp = g_stub->getTimeStamp();
            ++frame;
        }

        BS_LOGI("Main loop finished; calling Game::fini");
        g_game->fini();
        delete g_game;
        g_game = nullptr;
        delete g_stub;
        g_stub = nullptr;
        BS_LOGI("SDL_main returning normally");
        return 0;
    } catch (const std::exception &e) {
        BS_LOGE("Unhandled C++ exception in SDL_main: %s", e.what());
    } catch (...) {
        BS_LOGE("Unhandled non-standard exception in SDL_main");
    }

    if (g_game) {
        delete g_game;
        g_game = nullptr;
    }
    if (g_stub) {
        delete g_stub;
        g_stub = nullptr;
    }
    return -1;
}
