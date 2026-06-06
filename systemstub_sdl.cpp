/*
 * Bermuda Syndrome engine rewrite
 * Copyright (C) 2007-2011 Gregory Montoir
 */

#include <SDL.h>
#ifdef __EMSCRIPTEN__
#include <emscripten.h>
#endif
#include "game.h"
#include "mixer.h"
#include "scaler.h"
#include "screenshot.h"
#include "systemstub.h"
#include <cstring>
#include <cstdlib>

enum {
	kSoundSampleRate = 22050,
	kSoundSampleSize = 4096,
	kVideoSurfaceDepth = 32,
	kJoystickCommitValue = 16384,
	kDoubleTapWindowMs = 280,
};

// Set by android_main.cpp after Game::init so menu context detection works.
extern int *g_gameStatePtr;
extern Game *g_game;

// Controller action names matching the Android-side ControllerConfig actions.
enum ControllerAction {
	kActionJump,
	kActionRun,
	kActionWeapon,
	kActionUse,
	kActionMenu,
	kActionInventory,
	kActionQuickLoad,
	kActionQuickSave,
	kActionStatus,
	kActionCount
};

static const char *kActionNames[kActionCount] = {
	"jump", "run", "weapon", "use", "menu", "inventory", "quick_load", "quick_save", "status"
};

static int actionIndexByName(const char *name) {
	for (int i = 0; i < kActionCount; ++i) {
		if (strcmp(name, kActionNames[i]) == 0) return i;
	}
	return -1;
}

static SDL_GameControllerButton actionToButton[kActionCount] = {
	SDL_CONTROLLER_BUTTON_A,       // jump
	SDL_CONTROLLER_BUTTON_X,       // run
	SDL_CONTROLLER_BUTTON_B,       // weapon
	SDL_CONTROLLER_BUTTON_Y,       // use
	SDL_CONTROLLER_BUTTON_START,   // menu
	SDL_CONTROLLER_BUTTON_BACK,    // inventory
	SDL_CONTROLLER_BUTTON_LEFTSHOULDER,  // quick_load
	SDL_CONTROLLER_BUTTON_RIGHTSHOULDER, // quick_save
	SDL_CONTROLLER_BUTTON_LEFTSTICK,     // status
};

static SDL_GameControllerButton buttonByName(const char *name) {
	if (strcmp(name, "A") == 0) return SDL_CONTROLLER_BUTTON_A;
	if (strcmp(name, "B") == 0) return SDL_CONTROLLER_BUTTON_B;
	if (strcmp(name, "X") == 0) return SDL_CONTROLLER_BUTTON_X;
	if (strcmp(name, "Y") == 0) return SDL_CONTROLLER_BUTTON_Y;
	if (strcmp(name, "START") == 0) return SDL_CONTROLLER_BUTTON_START;
	if (strcmp(name, "SELECT") == 0) return SDL_CONTROLLER_BUTTON_BACK;
	if (strcmp(name, "L1") == 0) return SDL_CONTROLLER_BUTTON_LEFTSHOULDER;
	if (strcmp(name, "R1") == 0) return SDL_CONTROLLER_BUTTON_RIGHTSHOULDER;
	if (strcmp(name, "L3") == 0) return SDL_CONTROLLER_BUTTON_LEFTSTICK;
	return SDL_CONTROLLER_BUTTON_INVALID;
}

// Game states (mirrored from game.h so we don't need the full header).
enum {
	kStGame    = 1,  // kStateGame
	kStBag     = 2,  // kStateBag
	kStDialogue = 3, // kStateDialogue
	kStBitmap  = 4,  // kStateBitmap
	kStMenu1   = 5,  // kStateMenu1
	kStMenu2   = 6,  // kStateMenu2
};

static bool isMenuState() {
	if (!g_gameStatePtr) return false;
	int s = *g_gameStatePtr;
	return s == kStMenu1 || s == kStMenu2;
}

static bool isDialogueState() {
	if (!g_gameStatePtr) return false;
	return *g_gameStatePtr == kStDialogue;
}

static bool isBitmapState() {
	if (!g_gameStatePtr) return false;
	return *g_gameStatePtr == kStBitmap;
}

struct SystemStub_SDL : SystemStub {
	Mixer *_mixer;
#if SDL_VERSION_ATLEAST(2, 0, 0)
	SDL_Window *_window;
	SDL_Renderer *_renderer;
	SDL_Texture *_gameTexture;
	SDL_Texture *_videoTexture;
	SDL_Texture *_backgroundTexture;
	SDL_GameController *_controller;
#else
	SDL_Surface *_screen;
	SDL_Overlay *_yuv;
#endif
	SDL_PixelFormat *_fmt;
	uint32_t *_gameBuffer;
	uint16_t *_videoBuffer;
	uint32_t _pal[256];
	int _screenW, _screenH;
	int _videoW, _videoH;
	int _widescreenW, _widescreenH;
	bool _fullScreenDisplay;
	int _soundSampleRate;
	const uint8_t *_iconData;
	int _iconSize;
	int _screenshot;
	bool _widescreen;
	bool _stretchGameplay;
	bool _controllerEnabled;
	bool _dpadDoubleTapRunEnabled;
	bool _controllerVideoMode;
	bool _videoPlaybackActive;
	uint32_t _lastDpadLeftTime;
	uint32_t _lastDpadRightTime;
	bool _dpadLeftWasDouble;
	bool _dpadRightWasDouble;

	SystemStub_SDL() :
#if SDL_VERSION_ATLEAST(2, 0, 0)
		_window(0), _renderer(0), _gameTexture(0), _videoTexture(0), _backgroundTexture(0),
#else
		_screen(0), _yuv(0),
#endif
		_fmt(0),
		_gameBuffer(0), _videoBuffer(0),
		_iconData(0), _iconSize(0),
		_stretchGameplay(false),
		_controllerEnabled(false),
		_dpadDoubleTapRunEnabled(false),
		_controllerVideoMode(false),
		_videoPlaybackActive(false),
		_lastDpadLeftTime(0),
		_lastDpadRightTime(0),
		_dpadLeftWasDouble(false),
		_dpadRightWasDouble(false) {
		_screenshot = 1;
		if (0) {
			_mixer = Mixer_SDL_create(this);
		} else {
			_mixer = Mixer_Software_create(this);
		}
	}
	virtual ~SystemStub_SDL() {
		delete _mixer;
	}

	virtual void init(const char *title, int w, int h, bool fullscreen, int screenMode);
	virtual void destroy();
	virtual void setIcon(const uint8_t *data, int size);
	virtual void showCursor(bool show);
	virtual void setPalette(const uint8_t *pal, int n);
	virtual void fillRect(int x, int y, int w, int h, uint8_t color);
	virtual void copyRect(int x, int y, int w, int h, const uint8_t *buf, int pitch, bool transparent);
	virtual void darkenRect(int x, int y, int w, int h);
	virtual void copyRectWidescreen(int w, int h, const uint8_t *buf, int pitch);
	virtual void clearWidescreen();
	virtual void updateScreen();
	virtual void setYUV(bool flag, int w, int h);
	virtual uint8_t *lockYUV(int *pitch);
	virtual void unlockYUV();
	virtual void processEvents();
	virtual void sleep(int duration);
	virtual uint32_t getTimeStamp();
	virtual void lockAudio();
	virtual void unlockAudio();
	virtual void startAudio(AudioCallback callback, void *param);
	virtual void stopAudio();
	virtual int getOutputSampleRate();
	virtual Mixer *getMixer() { return _mixer; }
	virtual void setStretchGameplay(bool stretch) { _stretchGameplay = stretch; }
	virtual void setVideoPlaybackActive(bool active) { _controllerVideoMode = active; _videoPlaybackActive = active; }
	virtual int getTouchInputContext() const {
		if (_videoPlaybackActive) return TOUCH_INPUT_CONTEXT_VIDEO;
		if (isBitmapState()) return TOUCH_INPUT_CONTEXT_BITMAP_CONFIRM;
		if (isMenuState() || isDialogueState()) return TOUCH_INPUT_CONTEXT_MENU;
		return TOUCH_INPUT_CONTEXT_GAMEPLAY;
	}

	void setControllerConfig(bool enabled, const char *mappingJson, bool dpadDoubleTapRun);
	void updateMousePosition(int x, int y);
	void handleEvent(const SDL_Event &ev, bool &paused);
	void handleControllerButton(const SDL_Event &ev);
	void handleControllerAxis(const SDL_Event &ev);
	void applyAction(int action, bool pressed);
	void performControlAction(int action, bool pressed) override;
	void setTouchDirectionMask(uint8_t dirMask) override;
	int getControlState() const override;
	void setFullscreen(bool fullscreen);
#if SDL_VERSION_ATLEAST(2, 0, 0)
	void getAndroidOutputSize(int *w, int *h) const;
	SDL_Rect getAndroidAspectRect(int srcW, int srcH) const;
#endif
};

SystemStub *SystemStub_SDL_create() {
	return new SystemStub_SDL();
}

#ifdef __EMSCRIPTEN__
static int eventHandler(void *userdata, SDL_Event *ev) {
	bool paused;
	((SystemStub_SDL *)userdata)->handleEvent(*ev, paused);
	return 0;
}
#endif

// ---- Controller config parser ----
// Expects a JSON object like {"A":"jump","X":"run",...}
// This is a minimal zero-allocation parser that handles the exact format produced by kotlinx.serialization.
void SystemStub_SDL::setControllerConfig(bool enabled, const char *mappingJson, bool dpadDoubleTapRun) {
	_controllerEnabled = enabled;
	_dpadDoubleTapRunEnabled = dpadDoubleTapRun;
	if (!enabled || !mappingJson || !mappingJson[0]) {
		_dpadLeftWasDouble = false;
		_dpadRightWasDouble = false;
		return;
	}

	// Reset to current defaults
	// parse top-level JSON object: {"key":"value",...}
	const char *p = mappingJson;
	while (*p && *p != '{') ++p;
	if (*p != '{') return;
	++p;

	while (*p) {
		while (*p == ' ' || *p == '\n' || *p == '\t' || *p == '\r' || *p == ',') ++p;
		if (*p == '}') break;
		if (*p != '"') { ++p; continue; }
		++p;
		const char *btnStart = p;
		while (*p && *p != '"') ++p;
		int btnLen = p - btnStart;
		if (*p != '"') break;
		++p;
		while (*p && *p != ':') ++p;
		if (*p != ':') break;
		++p;
		while (*p == ' ' || *p == '\n' || *p == '\t' || *p == '\r') ++p;
		if (*p != '"') { ++p; continue; }
		++p;
		const char *actStart = p;
		while (*p && *p != '"') ++p;
		int actLen = p - actStart;
		if (*p != '"') break;
		++p;

		char btnName[32] = {0};
		char actName[32] = {0};
		int bl = btnLen < 31 ? btnLen : 31;
		int al = actLen < 31 ? actLen : 31;
		memcpy(btnName, btnStart, bl);
		memcpy(actName, actStart, al);

		SDL_GameControllerButton btn = buttonByName(btnName);
		int actIdx = actionIndexByName(actName);
		if (btn != SDL_CONTROLLER_BUTTON_INVALID && actIdx >= 0) {
			actionToButton[actIdx] = btn;
		}
	}
}

// ---- Action application ----
// Maps a gameplay action to PlayerInput fields according to the plan:
//   jump -> UP       run -> SHIFT    weapon -> SPACE   use -> ENTER
//   menu -> ESCAPE   inventory -> TAB  quick_load -> load  quick_save -> save
//   status -> CTRL
void SystemStub_SDL::applyAction(int action, bool pressed) {
	switch (action) {
	case kActionJump:
		_pi.jumpButtonAction = pressed;
		break;
	case kActionRun:
		_pi.runAction = pressed;
		break;
	case kActionWeapon:
		_pi.weaponToggleAction = pressed;
		break;
	case kActionUse:
		_pi.enter = pressed;
		break;
	case kActionMenu:
		_pi.escape = pressed;
		break;
	case kActionInventory:
		_pi.tab = pressed;
		break;
	case kActionQuickLoad:
		if (pressed) _pi.load = true;
		break;
	case kActionQuickSave:
		if (pressed) _pi.save = true;
		break;
	case kActionStatus:
		_pi.ctrl = pressed;
		break;
	}
}

void SystemStub_SDL::performControlAction(int action, bool pressed) {
	switch (action) {
	case CONTROL_ACTION_RUN:
		_pi.runAction = pressed;
		break;
	case CONTROL_ACTION_JUMP_BUTTON:
		_pi.jumpButtonAction = pressed;
		break;
	case CONTROL_ACTION_WEAPON_TOGGLE:
		if (pressed) _pi.weaponToggleAction = true;
		break;
	case CONTROL_ACTION_USE:
		_pi.enter = pressed;
		break;
	case CONTROL_ACTION_MENU_BACK:
		_pi.escape = pressed;
		break;
	case CONTROL_ACTION_RELOAD:
		_pi.reloadAction = pressed;
		break;
	}
}

void SystemStub_SDL::setTouchDirectionMask(uint8_t dirMask) {
	const uint8_t touchDirections = PlayerInput::DIR_UP | PlayerInput::DIR_DOWN | PlayerInput::DIR_LEFT | PlayerInput::DIR_RIGHT;
	_pi.dirMask = (_pi.dirMask & ~touchDirections) | (dirMask & touchDirections);
}

int SystemStub_SDL::getControlState() const {
	if (g_game) {
		return g_game->engineControlState();
	}
	return 0;
}

// ---- Controller event handlers ----

void SystemStub_SDL::handleControllerAxis(const SDL_Event &ev) {
	if (!_controller || !_controllerEnabled) return;
	switch (ev.caxis.axis) {
	case SDL_CONTROLLER_AXIS_LEFTX:
		if (ev.caxis.value < -kJoystickCommitValue) {
			_pi.dirMask |= PlayerInput::DIR_LEFT;
		} else {
			_pi.dirMask &= ~PlayerInput::DIR_LEFT;
		}
		if (ev.caxis.value > kJoystickCommitValue) {
			_pi.dirMask |= PlayerInput::DIR_RIGHT;
		} else {
			_pi.dirMask &= ~PlayerInput::DIR_RIGHT;
		}
		break;
	case SDL_CONTROLLER_AXIS_LEFTY:
		if (ev.caxis.value < -kJoystickCommitValue) {
			_pi.dirMask |= PlayerInput::DIR_UP;
		} else {
			_pi.dirMask &= ~PlayerInput::DIR_UP;
		}
		if (ev.caxis.value > kJoystickCommitValue) {
			_pi.dirMask |= PlayerInput::DIR_DOWN;
		} else {
			_pi.dirMask &= ~PlayerInput::DIR_DOWN;
		}
		break;
	}
}

void SystemStub_SDL::handleControllerButton(const SDL_Event &ev) {
	if (!_controller || !_controllerEnabled) return;
	const bool pressed = ev.cbutton.state == SDL_PRESSED;
	SDL_GameControllerButton btn = (SDL_GameControllerButton)ev.cbutton.button;

	// During videos and the title bitmap, controller confirm is fixed to A.
	if (_controllerVideoMode || isBitmapState()) {
		switch (btn) {
		case SDL_CONTROLLER_BUTTON_A:
			_pi.enter = pressed;
			return;
		case SDL_CONTROLLER_BUTTON_DPAD_UP:
		case SDL_CONTROLLER_BUTTON_DPAD_DOWN:
		case SDL_CONTROLLER_BUTTON_DPAD_LEFT:
		case SDL_CONTROLLER_BUTTON_DPAD_RIGHT:
			break;
		default:
			return;
		}
	}

	// In main/save menus: fixed A=confirm, B=back, DPAD=nav; remapping is ignored.
	if (isMenuState()) {
		switch (btn) {
		case SDL_CONTROLLER_BUTTON_A:
		case SDL_CONTROLLER_BUTTON_DPAD_UP:
		case SDL_CONTROLLER_BUTTON_DPAD_DOWN:
		case SDL_CONTROLLER_BUTTON_DPAD_LEFT:
		case SDL_CONTROLLER_BUTTON_DPAD_RIGHT:
			// Handled below via DPAD section
			break;
		case SDL_CONTROLLER_BUTTON_B:
			_pi.escape = pressed;
			return;
		default:
			return; // ignore unmapped buttons in menus
		}
	}

	// DPAD direction handling (works in both menu and gameplay contexts).
	switch (btn) {
	case SDL_CONTROLLER_BUTTON_DPAD_UP:
		if (pressed) _pi.dirMask |= PlayerInput::DIR_UP;
		else        _pi.dirMask &= ~PlayerInput::DIR_UP;
		return;
	case SDL_CONTROLLER_BUTTON_DPAD_DOWN:
		if (pressed) _pi.dirMask |= PlayerInput::DIR_DOWN;
		else        _pi.dirMask &= ~PlayerInput::DIR_DOWN;
		return;
	case SDL_CONTROLLER_BUTTON_DPAD_LEFT:
		if (pressed) {
			_pi.dirMask |= PlayerInput::DIR_LEFT;
			if (_dpadDoubleTapRunEnabled && !isMenuState()) {
				uint32_t now = SDL_GetTicks();
				if (now - _lastDpadLeftTime < kDoubleTapWindowMs && !_dpadLeftWasDouble) {
					_pi.shift = true;
					_dpadLeftWasDouble = true;
				} else {
					_dpadLeftWasDouble = false;
				}
				_lastDpadLeftTime = now;
			}
		} else {
			_pi.dirMask &= ~PlayerInput::DIR_LEFT;
			if (_dpadLeftWasDouble) {
				_pi.shift = false;
				_dpadLeftWasDouble = false;
			}
		}
		return;
	case SDL_CONTROLLER_BUTTON_DPAD_RIGHT:
		if (pressed) {
			_pi.dirMask |= PlayerInput::DIR_RIGHT;
			if (_dpadDoubleTapRunEnabled && !isMenuState()) {
				uint32_t now = SDL_GetTicks();
				if (now - _lastDpadRightTime < kDoubleTapWindowMs && !_dpadRightWasDouble) {
					_pi.shift = true;
					_dpadRightWasDouble = true;
				} else {
					_dpadRightWasDouble = false;
				}
				_lastDpadRightTime = now;
			}
		} else {
			_pi.dirMask &= ~PlayerInput::DIR_RIGHT;
			if (_dpadRightWasDouble) {
				_pi.shift = false;
				_dpadRightWasDouble = false;
			}
		}
		return;
	default:
		break;
	}

	// In menu context: A = confirm (enter).
	if (isMenuState()) {
		if (btn == SDL_CONTROLLER_BUTTON_A) {
			_pi.enter = pressed;
		}
		return;
	}

	// Gameplay context: use the remappable action mapping.
	for (int i = 0; i < kActionCount; ++i) {
		if (btn == actionToButton[i]) {
			applyAction(i, pressed);
			return;
		}
	}
}

// ---- Original SystemStub_SDL methods ----

void SystemStub_SDL::init(const char *title, int w, int h, bool fullscreen, int screenMode) {
	SDL_Init(SDL_INIT_VIDEO | SDL_INIT_AUDIO | SDL_INIT_GAMECONTROLLER);
	SDL_ShowCursor(SDL_DISABLE);
	_quit = false;
	memset(&_pi, 0, sizeof(_pi));

	_soundSampleRate = 0;
	_mixer->open();

	_widescreen = false;
	switch (screenMode) {
	case SCREEN_MODE_DEFAULT: {
			SDL_DisplayMode dm;
			if (SDL_GetDesktopDisplayMode(0, &dm) == 0) {
				_widescreen = ((dm.w / (float)dm.h) >= (16 / 9.f));
			}
		}
		break;
	case SCREEN_MODE_4_3:
		_widescreen = false;
		break;
	case SCREEN_MODE_16_9:
		_widescreen = true;
		break;
	}

#if SDL_VERSION_ATLEAST(2, 0, 0)
	int windowW = w;
	int windowH = h;
	if (_widescreen) {
		windowW = windowH * 16 / 9;
		_widescreenW = windowW;
		_widescreenH = windowH;
	} else {
		_widescreenW = 0;
		_widescreenH = 0;
	}
	_window = SDL_CreateWindow(title, SDL_WINDOWPOS_CENTERED, SDL_WINDOWPOS_CENTERED, windowW, windowH, 0);
	if (_widescreen) {
		SDL_GetWindowSize(_window, &_widescreenW, &_widescreenH);
	}
	if (_iconData) {
		SDL_RWops *rw = SDL_RWFromConstMem(_iconData, _iconSize);
		SDL_Surface *icon = SDL_LoadBMP_RW(rw, 1);
		if (icon) {
			SDL_SetWindowIcon(_window, icon);
			SDL_FreeSurface(icon);
		}
	}
	_renderer = SDL_CreateRenderer(_window, -1, SDL_RENDERER_ACCELERATED);
#ifdef __ANDROID__
	SDL_RenderSetLogicalSize(_renderer, 0, 0);
#else
	if (_widescreen) {
		SDL_RenderSetLogicalSize(_renderer, _widescreenW, _widescreenH);
	} else {
		SDL_RenderSetLogicalSize(_renderer, w, h);
	}
#endif

	static const uint32_t pfmt = SDL_PIXELFORMAT_RGB888; //SDL_PIXELFORMAT_RGB565;
	_fmt = SDL_AllocFormat(pfmt);
	_gameTexture = SDL_CreateTexture(_renderer, pfmt, SDL_TEXTUREACCESS_STREAMING, w, h);
	if (_widescreen) {
		_backgroundTexture = SDL_CreateTexture(_renderer, pfmt, SDL_TEXTUREACCESS_STREAMING, w, h);
	}

	SDL_GameControllerAddMappingsFromFile("gamecontrollerdb.txt");
	_controller = 0;
	for (int i = 0; i < SDL_NumJoysticks(); ++i) {
		if (SDL_IsGameController(i)) {
			_controller = SDL_GameControllerOpen(i);
			break;
		}
	}
#else
	SDL_WM_SetCaption(title, NULL);
#endif

	_screenW = w;
	_screenH = h;

	const int bufferSize = _screenW * _screenH;
	_gameBuffer = (uint32_t *)calloc(bufferSize, sizeof(uint32_t));
	if (!_gameBuffer) {
		error("SystemStub_SDL::init() Unable to allocate offscreen buffer");
	}
	memset(_pal, 0, sizeof(_pal));

	_videoW = _videoH = 0;

	_fullScreenDisplay = false;
	setFullscreen(fullscreen);

#ifdef __EMSCRIPTEN__
	emscripten_SDL_SetEventHandler(eventHandler, this);
#endif
}

void SystemStub_SDL::destroy() {
	_mixer->close();

#if SDL_VERSION_ATLEAST(2, 0, 0)
	if (_gameTexture) {
		SDL_DestroyTexture(_gameTexture);
		_gameTexture = 0;
	}
	if (_videoTexture) {
		SDL_DestroyTexture(_videoTexture);
		_videoTexture = 0;
	}
	if (_backgroundTexture) {
		SDL_DestroyTexture(_backgroundTexture);
		_backgroundTexture = 0;
	}
	if (_fmt) {
		SDL_FreeFormat(_fmt);
		_fmt = 0;
	}
	SDL_DestroyRenderer(_renderer);
	SDL_DestroyWindow(_window);

	if (_controller) {
		SDL_GameControllerClose(_controller);
	}
#else
	if (_screen) {
		// free()'d by SDL_Quit()
		_screen = 0;
	}
#endif
	if (_gameBuffer) {
		free(_gameBuffer);
		_gameBuffer = 0;
	}
	SDL_Quit();
}

void SystemStub_SDL::setIcon(const uint8_t *data, int size) {
	_iconData = data;
	_iconSize = size;
}

void SystemStub_SDL::showCursor(bool show) {
	SDL_ShowCursor(show ? SDL_ENABLE : SDL_DISABLE);
}

void SystemStub_SDL::setPalette(const uint8_t *pal, int n) {
	assert(n <= 256);
	_pal[0] = SDL_MapRGB(_fmt, 0, 0, 0); pal += 4;
	for (int i = 1; i < n; ++i) {
		_pal[i] = SDL_MapRGB(_fmt, pal[2], pal[1], pal[0]);
		pal += 4;
	}
}

static bool clipRect(int screenW, int screenH, int &x, int &y, int &w, int &h) {
	if (x < 0) {
		x = 0;
	}
	if (x + w > screenW) {
		w = screenW - x;
	}
	if (y < 0) {
		y = 0;
	}
	if (y + h > screenH) {
		h = screenH - y;
	}
	return (w > 0 && h > 0);
}

void SystemStub_SDL::fillRect(int x, int y, int w, int h, uint8_t color) {
	if (!clipRect(_screenW, _screenH, x, y, w, h)) return;

	const uint32_t fillColor = _pal[color];
	uint32_t *p = _gameBuffer + y * _screenW + x;
	while (h--) {
		for (int i = 0; i < w; ++i) {
			p[i] = fillColor;
		}
		p += _screenW;
	}
}

void SystemStub_SDL::copyRect(int x, int y, int w, int h, const uint8_t *buf, int pitch, bool transparent) {
	if (!clipRect(_screenW,  _screenH, x, y, w, h)) return;

	uint32_t *p = _gameBuffer + y * _screenW + x;
	buf += h * pitch;
	while (h--) {
		buf -= pitch;
		for (int i = 0; i < w; ++i) {
			if (!transparent || buf[i] != 0) {
				p[i] = _pal[buf[i]];
			}
		}
		p += _screenW;
	}
}

void SystemStub_SDL::darkenRect(int x, int y, int w, int h) {
	if (!clipRect(_screenW, _screenH, x, y, w, h)) return;

	const uint32_t redBlueMask = _fmt->Rmask | _fmt->Bmask;
	const uint32_t greenMask = _fmt->Gmask;

	uint32_t *p = _gameBuffer + y * _screenW + x;
	while (h--) {
		for (int i = 0; i < w; ++i) {
			uint32_t color = ((p[i] & redBlueMask) >> 1) & redBlueMask;
			color |= ((p[i] & greenMask) >> 1) & greenMask;
			p[i] = color;
		}
		p += _screenW;
	}
}

static void blur_h(int radius, const uint32_t *src, int srcPitch, int w, int h, const SDL_PixelFormat *fmt, uint32_t *dst, int dstPitch) {

	const int count = 2 * radius + 1;

	for (int y = 0; y < h; ++y) {

		uint32_t r = 0;
		uint32_t g = 0;
		uint32_t b = 0;

		uint32_t color;

		for (int x = -radius; x <= radius; ++x) {
			color = src[MAX(x, 0)];
			r += (color & fmt->Rmask) >> fmt->Rshift;
			g += (color & fmt->Gmask) >> fmt->Gshift;
			b += (color & fmt->Bmask) >> fmt->Bshift;
		}
		dst[0] = ((r / count) << fmt->Rshift) | ((g / count) << fmt->Gshift) | ((b / count) << fmt->Bshift);

		for (int x = 1; x < w; ++x) {
			color = src[MIN(x + radius, w - 1)];
			r += (color & fmt->Rmask) >> fmt->Rshift;
			g += (color & fmt->Gmask) >> fmt->Gshift;
			b += (color & fmt->Bmask) >> fmt->Bshift;

			color = src[MAX(x - radius - 1, 0)];
			r -= (color & fmt->Rmask) >> fmt->Rshift;
			g -= (color & fmt->Gmask) >> fmt->Gshift;
			b -= (color & fmt->Bmask) >> fmt->Bshift;

			dst[x] = ((r / count) << fmt->Rshift) | ((g / count) << fmt->Gshift) | ((b / count) << fmt->Bshift);
		}

		src += srcPitch;
		dst += dstPitch;
	}
}

static void blur_v(int radius, const uint32_t *src, int srcPitch, int w, int h, const SDL_PixelFormat *fmt, uint32_t *dst, int dstPitch) {

	const int count = 2 * radius + 1;

	for (int x = 0; x < w; ++x) {

		uint32_t r = 0;
		uint32_t g = 0;
		uint32_t b = 0;

		uint32_t color;

		for (int y = -radius; y <= radius; ++y) {
			color = src[MAX(y, 0) * srcPitch];
			r += (color & fmt->Rmask) >> fmt->Rshift;
			g += (color & fmt->Gmask) >> fmt->Gshift;
			b += (color & fmt->Bmask) >> fmt->Bshift;
		}
		dst[0] = ((r / count) << fmt->Rshift) | ((g / count) << fmt->Gshift) | ((b / count) << fmt->Bshift);

		for (int y = 1; y < h; ++y) {
			color = src[MIN(y + radius, h - 1) * srcPitch];
			r += (color & fmt->Rmask) >> fmt->Rshift;
			g += (color & fmt->Gmask) >> fmt->Gshift;
			b += (color & fmt->Bmask) >> fmt->Bshift;

			color = src[MAX(y - radius - 1, 0) * srcPitch];
			r -= (color & fmt->Rmask) >> fmt->Rshift;
			g -= (color & fmt->Gmask) >> fmt->Gshift;
			b -= (color & fmt->Bmask) >> fmt->Bshift;

			dst[y * dstPitch] = ((r / count) << fmt->Rshift) | ((g / count) << fmt->Gshift) | ((b / count) << fmt->Bshift);
		}

		++src;
		++dst;
	}
}

void SystemStub_SDL::copyRectWidescreen(int w, int h, const uint8_t *buf, int bufPitch) {
#ifdef __ANDROID__
	return;
#endif
	if (_widescreen) {
		void *ptr = 0;
		int dstPitch = 0;
		if (SDL_LockTexture(_backgroundTexture, 0, &ptr, &dstPitch) == 0) {

			uint32_t *src = (uint32_t *)malloc(w * h * sizeof(uint32_t));
			uint32_t *tmp = (uint32_t *)malloc(w * h * sizeof(uint32_t));
			uint32_t *dst = (uint32_t *)ptr;
			if (src && tmp) {
				buf += h * bufPitch;
				for (int y = 0; y < h; ++y) {
					buf -= bufPitch;
					for (int x = 0; x < w; ++x) {
						src[y * w + x] = _pal[buf[x]];
					}
				}
				static const int radius = 16;
				blur_h(radius, src, w, w, h, _fmt, tmp, w);
				blur_v(radius, tmp, w, w, h, _fmt, dst, dstPitch / sizeof(uint32_t));
			}
			free(src);
			free(tmp);

			SDL_UnlockTexture(_backgroundTexture);
		}
	}
}

void SystemStub_SDL::clearWidescreen() {
#ifdef __ANDROID__
	return;
#endif
	if (_widescreen) {
		void *dst = 0;
		int dstPitch = 0;
		if (SDL_LockTexture(_backgroundTexture, 0, &dst, &dstPitch) == 0) {
			assert((dstPitch & 3) == 0);
			const uint32_t color = _pal[0]; // palette #0 is black
			for (int y = 0; y < _screenH; ++y) {
				for (int x = 0; x < _screenW; ++x) {
					((uint32_t *)dst)[x] = color;
				}
				dst = (uint8_t *)dst + dstPitch;
			}
			SDL_UnlockTexture(_backgroundTexture);
		}
	}
}

#if SDL_VERSION_ATLEAST(2, 0, 0)
void SystemStub_SDL::getAndroidOutputSize(int *w, int *h) const {
	if (SDL_GetRendererOutputSize(_renderer, w, h) != 0 || *w <= 0 || *h <= 0) {
		SDL_GetWindowSize(_window, w, h);
	}
}

SDL_Rect SystemStub_SDL::getAndroidAspectRect(int srcW, int srcH) const {
	int outputW = srcW;
	int outputH = srcH;
	getAndroidOutputSize(&outputW, &outputH);

	const int scaledW = outputH * srcW / srcH;
	SDL_Rect r;
	if (scaledW <= outputW) {
		r.w = scaledW;
		r.h = outputH;
		r.x = (outputW - r.w) / 2;
		r.y = 0;
	} else {
		r.w = outputW;
		r.h = outputW * srcH / srcW;
		r.x = 0;
		r.y = (outputH - r.h) / 2;
	}
	return r;
}
#endif

void SystemStub_SDL::updateScreen() {
#if SDL_VERSION_ATLEAST(2, 0, 0)
	SDL_RenderClear(_renderer);
	// background graphics (left/right borders)
#ifndef __ANDROID__
	if (_widescreen) {
		SDL_RenderCopy(_renderer, _backgroundTexture, 0, 0);
	}
#endif
	// game graphics
	SDL_UpdateTexture(_gameTexture, NULL, _gameBuffer, _screenW * sizeof(uint32_t));
#ifdef __ANDROID__
	SDL_Rect r;
	if (_stretchGameplay) {
		getAndroidOutputSize(&r.w, &r.h);
		r.x = 0;
		r.y = 0;
	} else {
		r = getAndroidAspectRect(_screenW, _screenH);
	}
#else
	SDL_Rect r;
	r.w = _screenW;
	r.h = _screenH;
	if (_widescreen) {
		r.x = (_widescreenW - r.w) / 2;
		r.y = (_widescreenH - r.h) / 2;
	} else {
		r.x = 0;
		r.y = 0;
	}
#endif
	SDL_RenderCopy(_renderer, _gameTexture, NULL, &r);
	// display
	SDL_RenderPresent(_renderer);
#else
	if (SDL_LockSurface(_screen) == 0) {
		for (int y = 0; y < _screenH; ++y) {
			uint8_t *dst = (uint8_t *)_screen->pixels + y * _screen->pitch;
			memcpy(dst, _gameBuffer + y * _screenW, _screenW * sizeof(uint32_t));
		}
		SDL_UnlockSurface(_screen);
		SDL_UpdateRect(_screen, 0, 0, 0, 0);
	}
#endif
}

void SystemStub_SDL::setYUV(bool flag, int w, int h) {
	if (flag) {
#ifndef __EMSCRIPTEN__
#if SDL_VERSION_ATLEAST(2, 0, 0)
		if (!_videoTexture) {
			_videoTexture = SDL_CreateTexture(_renderer, SDL_PIXELFORMAT_UYVY, SDL_TEXTUREACCESS_STREAMING, w, h);
		}
		if (!_videoBuffer) {
			_videoBuffer = (uint16_t *)malloc(w * h * sizeof(uint16_t));
		}
#else
		if (!_yuv) {
			_yuv = SDL_CreateYUVOverlay(w, h, SDL_UYVY_OVERLAY, _screen);
		}
#endif
#endif
		_videoW = w;
		_videoH = h;
	} else {
#ifndef __EMSCRIPTEN__
#if SDL_VERSION_ATLEAST(2, 0, 0)
		if (_videoTexture) {
			SDL_DestroyTexture(_videoTexture);
			_videoTexture = 0;
		}
		if (_videoBuffer) {
			free(_videoBuffer);
			_videoBuffer = 0;
		}
#else
		if (_yuv) {
			SDL_FreeYUVOverlay(_yuv);
			_yuv = 0;
		}
#endif
#endif
	}
}

uint8_t *SystemStub_SDL::lockYUV(int *pitch) {
#ifndef __EMSCRIPTEN__
#if SDL_VERSION_ATLEAST(2, 0, 0)
	*pitch = _videoW * sizeof(uint16_t);
	return (uint8_t *)_videoBuffer;
#else
	if (_yuv && SDL_LockYUVOverlay(_yuv) == 0) {
		*pitch = _yuv->pitches[0];
		return _yuv->pixels[0];
	}
	return 0;
#endif
#else
	return 0;
#endif
}

void SystemStub_SDL::unlockYUV() {
#ifndef __EMSCRIPTEN__
#if SDL_VERSION_ATLEAST(2, 0, 0)
	SDL_RenderClear(_renderer);
#ifndef __ANDROID__
	if (_widescreen && _backgroundTexture) {
		SDL_RenderCopy(_renderer, _backgroundTexture, 0, 0);
	}
#endif
	if (_videoBuffer) {
		SDL_UpdateTexture(_videoTexture, NULL, _videoBuffer, _videoW * sizeof(uint16_t));
	}
#ifdef __ANDROID__
	SDL_Rect r = getAndroidAspectRect(_videoW, _videoH);
	SDL_RenderCopy(_renderer, _videoTexture, NULL, &r);
#else
	SDL_RenderCopy(_renderer, _videoTexture, NULL, NULL);
#endif
	SDL_RenderPresent(_renderer);
#else
	if (_yuv) {
		SDL_UnlockYUVOverlay(_yuv);
		SDL_Rect r;
		if (_yuv->w * 2 <= _screenW && _yuv->h * 2 <= _screenH) {
			r.w = _yuv->w * 2;
			r.h = _yuv->h * 2;
		} else {
			r.w = _yuv->w;
			r.h = _yuv->h;
		}
		r.x = (_screenW - r.w) / 2;
		r.y = (_screenH - r.h) / 2;
		SDL_DisplayYUVOverlay(_yuv, &r);
	}
#endif
#endif
}

void SystemStub_SDL::processEvents() {
	bool paused = false;
	while (!_quit) {
		SDL_Event ev;
		while (SDL_PollEvent(&ev)) {
			handleEvent(ev, paused);
		}
		if (paused) {
			SDL_Delay(100);
		} else {
			break;
		}
	}
}

void SystemStub_SDL::updateMousePosition(int x, int y) {
#if SDL_VERSION_ATLEAST(2, 0, 0) && defined(__ANDROID__)
	SDL_Rect r;
	if (_stretchGameplay) {
		getAndroidOutputSize(&r.w, &r.h);
		r.x = 0;
		r.y = 0;
	} else {
		r = getAndroidAspectRect(_screenW, _screenH);
	}
	x = (x - r.x) * _screenW / r.w;
	y = (y - r.y) * _screenH / r.h;
#else
	if (_widescreen) {
		x -= (_widescreenW - _screenW) / 2;
		y -= (_widescreenH - _screenH) / 2;
	}
#endif
	_pi.mouseX = x;
	_pi.mouseY = y;
}

void SystemStub_SDL::handleEvent(const SDL_Event &ev, bool &paused) {
	switch (ev.type) {
	case SDL_QUIT:
		_quit = true;
		break;
#if SDL_VERSION_ATLEAST(2, 0, 0)
	case SDL_WINDOWEVENT:
		switch (ev.window.event) {
		case SDL_WINDOWEVENT_FOCUS_GAINED:
		case SDL_WINDOWEVENT_FOCUS_LOST:
			paused = (ev.window.event == SDL_WINDOWEVENT_FOCUS_LOST);
			SDL_PauseAudio(paused);
			break;
		}
		break;
	case SDL_CONTROLLERAXISMOTION:
		handleControllerAxis(ev);
		break;
	case SDL_CONTROLLERBUTTONDOWN:
	case SDL_CONTROLLERBUTTONUP:
		handleControllerButton(ev);
		break;
#else
	case SDL_ACTIVEEVENT:
		if (ev.active.state & SDL_APPINPUTFOCUS) {
			paused = ev.active.gain == 0;
			SDL_PauseAudio(paused ? 1 : 0);
		}
		break;
#endif
	case SDL_KEYUP:
		switch (ev.key.keysym.sym) {
		case SDLK_LEFT:
			_pi.dirMask &= ~PlayerInput::DIR_LEFT;
			break;
		case SDLK_RIGHT:
			_pi.dirMask &= ~PlayerInput::DIR_RIGHT;
			break;
		case SDLK_UP:
			_pi.dirMask &= ~PlayerInput::DIR_UP;
			break;
		case SDLK_DOWN:
			_pi.dirMask &= ~PlayerInput::DIR_DOWN;
			break;
		case SDLK_RETURN:
			_pi.enter = false;
			break;
		case SDLK_SPACE:
			_pi.space = false;
			break;
		case SDLK_RSHIFT:
		case SDLK_LSHIFT:
			_pi.shift = false;
			break;
		case SDLK_RCTRL:
		case SDLK_LCTRL:
			_pi.ctrl = false;
			break;
		case SDLK_TAB:
			_pi.tab = false;
			break;
		case SDLK_ESCAPE:
			_pi.escape = false;
			break;
		case SDLK_c: {
				// capture game screen buffer (no support for video YUYV buffer)
				char name[32];
				snprintf(name, sizeof(name), "screenshot-%03d.tga", _screenshot);
				saveTGA(name, (const uint8_t *)_gameBuffer, _screenW, _screenH);
				++_screenshot;
				debug(DBG_INFO, "Written '%s'", name);
			}
			break;
		case SDLK_f:
			_pi.fastMode = !_pi.fastMode;
			break;
		case SDLK_s:
			_pi.save = true;
			break;
		case SDLK_l:
			_pi.load = true;
			break;
		case SDLK_w:
			setFullscreen(!_fullScreenDisplay);
			break;
		case SDLK_KP_PLUS:
		case SDLK_PAGEUP:
			_pi.stateSlot = 1;
			break;
		case SDLK_KP_MINUS:
		case SDLK_PAGEDOWN:
			_pi.stateSlot = -1;
			break;
		default:
			break;
		}
		break;
	case SDL_KEYDOWN:
	switch (ev.key.keysym.sym) {
		case SDLK_LEFT:
			_pi.dirMask |= PlayerInput::DIR_LEFT;
			break;
		case SDLK_RIGHT:
			_pi.dirMask |= PlayerInput::DIR_RIGHT;
			break;
		case SDLK_UP:
			_pi.dirMask |= PlayerInput::DIR_UP;
			break;
		case SDLK_DOWN:
			_pi.dirMask |= PlayerInput::DIR_DOWN;
			break;
		case SDLK_RETURN:
			_pi.enter = true;
			break;
		case SDLK_SPACE:
			_pi.space = true;
			break;
		case SDLK_RSHIFT:
		case SDLK_LSHIFT:
			_pi.shift = true;
			break;
		case SDLK_RCTRL:
		case SDLK_LCTRL:
			_pi.ctrl = true;
			break;
		case SDLK_TAB:
			_pi.tab = true;
			break;
		case SDLK_ESCAPE:
			_pi.escape = true;
			break;
		default:
			break;
		}
		break;
	case SDL_MOUSEBUTTONDOWN:
		if (ev.button.button == SDL_BUTTON_LEFT) {
			_pi.leftMouseButton = true;
		} else if (ev.button.button == SDL_BUTTON_RIGHT) {
			_pi.rightMouseButton = true;
		}
		updateMousePosition(ev.button.x, ev.button.y);
		break;
	case SDL_MOUSEBUTTONUP:
		if (ev.button.button == SDL_BUTTON_LEFT) {
			_pi.leftMouseButton = false;
		} else if (ev.button.button == SDL_BUTTON_RIGHT) {
			_pi.rightMouseButton = false;
		}
		updateMousePosition(ev.button.x, ev.button.y);
		break;
	case SDL_MOUSEMOTION:
		updateMousePosition(ev.motion.x, ev.motion.y);
		break;
	default:
		break;
	}
}

void SystemStub_SDL::sleep(int duration) {
	SDL_Delay(duration);
}

uint32_t SystemStub_SDL::getTimeStamp() {
	return SDL_GetTicks();
}

void SystemStub_SDL::lockAudio() {
	SDL_LockAudio();
}

void SystemStub_SDL::unlockAudio() {
	SDL_UnlockAudio();
}

void SystemStub_SDL::startAudio(AudioCallback callback, void *param) {
	SDL_AudioSpec desired, obtained;
	memset(&desired, 0, sizeof(desired));
	desired.freq = kSoundSampleRate;
	desired.format = AUDIO_S16SYS;
	desired.channels = 2;
	desired.samples = kSoundSampleSize;
	desired.callback = callback;
	desired.userdata = param;
	if (SDL_OpenAudio(&desired, &obtained) == 0) {
		_soundSampleRate = obtained.freq;
		SDL_PauseAudio(0);
	} else {
		error("SystemStub_SDL::startAudio() Unable to open sound device");
	}
}

void SystemStub_SDL::stopAudio() {
	SDL_CloseAudio();
}

int SystemStub_SDL::getOutputSampleRate() {
	return _soundSampleRate;
}

void SystemStub_SDL::setFullscreen(bool fullscreen) {
#if SDL_VERSION_ATLEAST(2, 0, 0)
	if (_fullScreenDisplay != fullscreen) {
		SDL_SetWindowFullscreen(_window, fullscreen ? SDL_WINDOW_FULLSCREEN_DESKTOP : 0);
		_fullScreenDisplay = fullscreen;
	}
#else
	_screen = SDL_SetVideoMode(_screenW, _screenH, kVideoSurfaceDepth, fullscreen ? SDL_FULLSCREEN : 0);
	if (_screen) {
		_fmt = _screen->format;
	}
#endif
}
