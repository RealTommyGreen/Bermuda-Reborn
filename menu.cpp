
#include "file.h"
#include "game.h"
#include "systemstub.h"

static const char *kSaveThumbnailFileNameFormat = "%s/bermuda.%03d.thumb";
static const int kSaveThumbnailPathSize = 1024;
static const uint32_t kSaveThumbnailMagic = 0x54485342; // BSHT

static uint8_t findBestMatchingMenuColor(const uint8_t *src, int color) {
	uint8_t bestColor = 0;
	int bestSum = -1;
	const int r =  color        & 0xFF;
	const int g = (color >>  8) & 0xFF;
	const int b = (color >> 16) & 0xFF;
	for (int i = 0; i < 256; ++i) {
		const int dr = r - src[2];
		const int dg = g - src[1];
		const int db = b - src[0];
		const int sum = dr * dr + dg * dg + db * db;
		if (bestSum == -1 || sum < bestSum) {
			bestSum = sum;
			bestColor = i;
		}
		src += 4;
	}
	return bestColor;
}

static int getTextWidth(const char *s) {
	int w = 0;
	for (; *s; ++s) {
		w += Game::_fontCharWidth[(uint8_t)*s] + 1;
	}
	return w;
}

static void drawMenuChar(uint8_t *dst, int dstPitch, const uint16_t *fontData, int c, uint8_t color) {
	const int offset = c * 16;
	for (int y = 0; y < 16; ++y) {
		const uint16_t chr = fontData[offset + y];
		for (int b = 0; b < 16; ++b) {
			if (chr & (1 << b)) {
				dst[-y * dstPitch + b] = color;
			}
		}
	}
}

static void drawMenuText(SystemStub *stub, int x, int y, const char *s, uint8_t color) {
	const int w = MAX(1, getTextWidth(s));
	static const int h = 16;
	uint8_t *textBuffer = (uint8_t *)calloc(w * h, 1);
	if (!textBuffer) {
		return;
	}
	int textX = 0;
	for (; *s; ++s) {
		const int chr = (uint8_t)*s;
		drawMenuChar(textBuffer + (h - 1) * w + textX, w, Game::_fontData, chr, color);
		textX += Game::_fontCharWidth[chr] + 1;
	}
	stub->copyRect(x, y, w, h, textBuffer, w, true);
	free(textBuffer);
}

static void drawThumbnailFrame(SystemStub *stub, int x, int y, int w, int h, uint8_t color) {
	stub->fillRect(x - 1, y - 1, w + 2, 1, color);
	stub->fillRect(x - 1, y + h, w + 2, 1, color);
	stub->fillRect(x - 1, y - 1, 1, h + 2, color);
	stub->fillRect(x + w, y - 1, 1, h + 2, color);
}

void Game::initMenu(int num) {
	discardTransientMenuObjects();
	_menuOption = -1;
	_menuHighlight = -1;
	_menuObjectCount = _sceneObjectsCount;
	_menuObjectMotion = _sceneObjectMotionsCount;
	_menuObjectFrames = _sceneObjectFramesCount;
	_menuLastMouseX = _stub->_pi.mouseX;
	_menuLastMouseY = _stub->_pi.mouseY;
	_menuMouseTrackingInitialized = true;
	const int animationsCount = _animationsCount;
	(void)animationsCount;
	const int state = _loadDataState;
	char name[32];
	snprintf(name, sizeof(name), "..\\menu\\menu%d.wgp", num);
	loadWGP(name);
	snprintf(name, sizeof(name), "..\\menu\\menu%d.mov", num);
	loadMOV(name);
	_loadDataState = state;
	assert(_menuObjectMotion + 1 == _sceneObjectMotionsCount);
	assert(animationsCount + 1 == _animationsCount);
	_stub->setPalette(_bitmapBuffer0 + kOffsetBitmapPalette, 256);
	_stub->copyRectWidescreen(kGameScreenWidth, kGameScreenHeight, _bitmapBuffer1.bits, _bitmapBuffer1.pitch);
	_stub->showCursor(true);
}

void Game::discardTransientMenuObjects() {
	int removed = 0;
	for (int i = 0; i < _sceneObjectsCount; ++i) {
		SceneObject *so = &_sceneObjectsTable[i];
		if (strcmp(so->name, "MENU") != 0) {
			continue;
		}
		so->state = 0;
		snprintf(so->name, sizeof(so->name), "_MENU_STALE_%d", i);
		so->className[0] = 0;
		memset(so->varsTable, 0, sizeof(so->varsTable));
		++removed;
	}
	while (_sceneObjectsCount > 0 && strncmp(_sceneObjectsTable[_sceneObjectsCount - 1].name, "_MENU_STALE_", 12) == 0) {
		--_sceneObjectsCount;
	}
	if (removed != 0) {
		warning("Discarded %d stale transient MENU object(s)", removed);
	}
}

void Game::finiMenu() {
	if (_currentSceneWgp[0]) {
		--_animationsCount;
		_sceneObjectsCount = _menuObjectCount;
		_sceneObjectMotionsCount = _menuObjectMotion;
		_sceneObjectFramesCount = _menuObjectFrames;
		const int state = _loadDataState;
		loadWGP(_currentSceneWgp);
		_loadDataState = state;
		_stub->setPalette(_bitmapBuffer0 + kOffsetBitmapPalette, 256);
		_stub->copyRectWidescreen(kGameScreenWidth, kGameScreenHeight, _bitmapBuffer1.bits, _bitmapBuffer1.pitch);
	}
	_stub->showCursor(false);
}

void Game::handleMenu() {
	if (_menuHighlight != -1) {
		SceneObjectFrame *sof = &_sceneObjectFramesTable[_sceneObjectMotionsTable[_menuObjectMotion].firstFrameIndex + _menuHighlight];
		const int yPos = _bitmapBuffer1.h + 1 - sof->hdr.yPos - sof->hdr.h;
		copyBufferToBuffer(sof->hdr.xPos, yPos, sof->hdr.w, sof->hdr.h, &_bitmapBuffer3, &_bitmapBuffer1);
	}
	const int xCursor = _stub->_pi.mouseX;
	const int yCursor = _stub->_pi.mouseY;
	const bool mouseClicked = _stub->_pi.leftMouseButton;
	const bool mouseMoved = !_menuMouseTrackingInitialized || xCursor != _menuLastMouseX || yCursor != _menuLastMouseY;
	if (mouseMoved || mouseClicked) {
		for (int frame = 0; frame < _sceneObjectMotionsTable[_menuObjectMotion].count; ++frame) {
			SceneObjectFrame *sof = &_sceneObjectFramesTable[_sceneObjectMotionsTable[_menuObjectMotion].firstFrameIndex + frame];
			if (xCursor >= sof->hdr.xPos && xCursor < sof->hdr.xPos + sof->hdr.w && yCursor >= sof->hdr.yPos && yCursor < sof->hdr.yPos + sof->hdr.h) {
				_menuHighlight = frame;
				if (mouseClicked) {
					_menuOption = frame;
				}
			}
		}
	}
	if (mouseClicked) {
		_stub->_pi.leftMouseButton = false;
	}
	_menuLastMouseX = xCursor;
	_menuLastMouseY = yCursor;
	_menuMouseTrackingInitialized = true;

	if (_state == kStateMenu1) {
		// vertical layout
		if (_stub->_pi.dirMask & PlayerInput::DIR_UP) {
			_stub->_pi.dirMask &= ~PlayerInput::DIR_UP;
			--_menuHighlight;
			if (_menuHighlight < 0) {
				_menuHighlight = 0;
			}
		}
		if (_stub->_pi.dirMask & PlayerInput::DIR_DOWN) {
			_stub->_pi.dirMask &= ~PlayerInput::DIR_DOWN;
			++_menuHighlight;
			if (_menuHighlight >= _sceneObjectMotionsTable[_menuObjectMotion].count) {
				_menuHighlight = _sceneObjectMotionsTable[_menuObjectMotion].count - 1;
			}
		}
	} else if (_state == kStateMenu2) {
		// horizontal layout
		if (_stub->_pi.dirMask & PlayerInput::DIR_LEFT) {
			_stub->_pi.dirMask &= ~PlayerInput::DIR_LEFT;
			--_menuHighlight;
			if (_menuHighlight < 0) {
				_menuHighlight = 0;
			}
		}
		if (_stub->_pi.dirMask & PlayerInput::DIR_RIGHT) {
			_stub->_pi.dirMask &= ~PlayerInput::DIR_RIGHT;
			++_menuHighlight;
			if (_menuHighlight >= _sceneObjectMotionsTable[_menuObjectMotion].count) {
				_menuHighlight = _sceneObjectMotionsTable[_menuObjectMotion].count - 1;
			}
		}
	}

	if (_stub->_pi.enter) {
		_stub->_pi.enter = false;
		_menuOption = _menuHighlight;
	}

	if (_menuHighlight != -1) {
		SceneObjectFrame *sof = &_sceneObjectFramesTable[_sceneObjectMotionsTable[_menuObjectMotion].firstFrameIndex + _menuHighlight];
		sof->decode(sof->data, _tempDecodeBuffer);
		const int yPos = _bitmapBuffer1.h + 1 - sof->hdr.yPos - sof->hdr.h;
		drawObject(sof->hdr.xPos, yPos, _tempDecodeBuffer, &_bitmapBuffer1);
	}

	_stub->copyRect(0, 0, kGameScreenWidth, kGameScreenHeight, _bitmapBuffer1.bits, _bitmapBuffer1.pitch);
}

void Game::handleSlotMenu(bool loadMode) {
	if (_stub->_pi.escape || _stub->_pi.rightMouseButton) {
		_stub->_pi.escape = false;
		_stub->_pi.rightMouseButton = false;
		_menuSlotMode = 0;
		drawSlotMenu(loadMode);
		return;
	}

	if (_stub->_pi.dirMask & PlayerInput::DIR_UP) {
		_stub->_pi.dirMask &= ~PlayerInput::DIR_UP;
		if (_menuSlotSelection >= 2) {
			_menuSlotSelection -= 2;
		}
	}
	if (_stub->_pi.dirMask & PlayerInput::DIR_DOWN) {
		_stub->_pi.dirMask &= ~PlayerInput::DIR_DOWN;
		if (_menuSlotSelection + 2 < kMenuSaveSlotCount) {
			_menuSlotSelection += 2;
		}
	}
	if (_stub->_pi.dirMask & PlayerInput::DIR_LEFT) {
		_stub->_pi.dirMask &= ~PlayerInput::DIR_LEFT;
		if ((_menuSlotSelection & 1) != 0) {
			--_menuSlotSelection;
		}
	}
	if (_stub->_pi.dirMask & PlayerInput::DIR_RIGHT) {
		_stub->_pi.dirMask &= ~PlayerInput::DIR_RIGHT;
		if ((_menuSlotSelection & 1) == 0 && _menuSlotSelection + 1 < kMenuSaveSlotCount) {
			++_menuSlotSelection;
		}
	}

	static const int x0 = 122;
	static const int y0 = 120;
	static const int slotW = 190;
	static const int slotH = 54;
	static const int gapX = 16;
	static const int gapY = 5;
	if (_stub->_pi.leftMouseButton) {
		_stub->_pi.leftMouseButton = false;
		for (int i = 0; i < kMenuSaveSlotCount; ++i) {
			const int col = i & 1;
			const int row = i >> 1;
			const int x = x0 + col * (slotW + gapX);
			const int y = y0 + row * (slotH + gapY);
			if (_stub->_pi.mouseX >= x && _stub->_pi.mouseX < x + slotW &&
				_stub->_pi.mouseY >= y && _stub->_pi.mouseY < y + slotH) {
				_menuSlotSelection = i;
				_stub->_pi.enter = true;
				break;
			}
		}
	}

	if (_stub->_pi.enter) {
		_stub->_pi.enter = false;
		const int slot = kMenuSaveSlotBase + _menuSlotSelection;
		if (loadMode) {
			if (loadGameStateSlot(slot, true)) {
				_menuSlotMode = 0;
				_nextState = kStateGame;
			}
		} else {
			if (saveGameStateSlot(slot)) {
				_menuSlotMode = 0;
				_nextState = kStateGame;
			}
		}
	}

	drawSlotMenu(loadMode);
}

void Game::drawSlotMenu(bool loadMode) {
	memcpy(_bitmapBuffer1.bits, _bitmapBuffer3.bits, _bitmapBuffer1.pitch * (_bitmapBuffer1.h + 1));
	_stub->copyRect(0, 0, kGameScreenWidth, kGameScreenHeight, _bitmapBuffer1.bits, _bitmapBuffer1.pitch);

	const uint8_t bgColor = findBestMatchingMenuColor(_bitmapBuffer0 + kOffsetBitmapPalette, 0x111820);
	const uint8_t slotColor = findBestMatchingMenuColor(_bitmapBuffer0 + kOffsetBitmapPalette, 0x263848);
	const uint8_t selectedColor = findBestMatchingMenuColor(_bitmapBuffer0 + kOffsetBitmapPalette, 0xA86A2A);
	const uint8_t textColor = findBestMatchingMenuColor(_bitmapBuffer0 + kOffsetBitmapPalette, 0xCAF6FF);
	const uint8_t dimTextColor = findBestMatchingMenuColor(_bitmapBuffer0 + kOffsetBitmapPalette, 0x6D8EA0);

	_stub->darkenRect(96, 64, 448, 364);
	_stub->fillRect(106, 76, 428, 340, bgColor);
	drawMenuText(_stub, 238, 100, loadMode ? "RESTORE GAME" : "SAVE GAME", textColor);

	static const int x0 = 122;
	static const int y0 = 120;
	static const int slotW = 190;
	static const int slotH = 54;
	static const int gapX = 16;
	static const int gapY = 5;
	uint8_t thumbnail[kSaveThumbnailWidth * kSaveThumbnailHeight];
	for (int i = 0; i < kMenuSaveSlotCount; ++i) {
		const int col = i & 1;
		const int row = i >> 1;
		const int x = x0 + col * (slotW + gapX);
		const int y = y0 + row * (slotH + gapY);
		const int slot = kMenuSaveSlotBase + i;
		const bool present = hasSaveStateSlot(slot);
		char label[32];
		snprintf(label, sizeof(label), "SLOT %d", i + 1);
		_stub->fillRect(x, y, slotW, slotH, i == _menuSlotSelection ? selectedColor : slotColor);
		if (present && loadSlotThumbnail(slot, thumbnail, kSaveThumbnailWidth)) {
			_stub->copyRect(x + 5, y + 3, kSaveThumbnailWidth, kSaveThumbnailHeight, thumbnail, kSaveThumbnailWidth);
			drawThumbnailFrame(_stub, x + 5, y + 3, kSaveThumbnailWidth, kSaveThumbnailHeight, textColor);
		} else {
			_stub->fillRect(x + 5, y + 3, kSaveThumbnailWidth, kSaveThumbnailHeight, bgColor);
			drawThumbnailFrame(_stub, x + 5, y + 3, kSaveThumbnailWidth, kSaveThumbnailHeight, dimTextColor);
		}
		drawMenuText(_stub, x + 78, y + 9, label, present || !loadMode ? textColor : dimTextColor);
		drawMenuText(_stub, x + 78, y + 28, present ? "USED" : "EMPTY", present || !loadMode ? textColor : dimTextColor);
	}

}

void Game::captureSaveThumbnail() {
	memcpy(_saveThumbnailPalette, _bitmapBuffer0 + kOffsetBitmapPalette, sizeof(_saveThumbnailPalette));
	for (int y = 0; y < kSaveThumbnailHeight; ++y) {
		const int srcY = y * (kGameScreenHeight - 1) / (kSaveThumbnailHeight - 1);
		const uint8_t *src = _bitmapBuffer1.bits + srcY * _bitmapBuffer1.pitch;
		uint8_t *dst = _saveThumbnail + y * kSaveThumbnailWidth;
		for (int x = 0; x < kSaveThumbnailWidth; ++x) {
			const int srcX = x * (kGameScreenWidth - 1) / (kSaveThumbnailWidth - 1);
			dst[x] = src[srcX];
		}
	}
}

bool Game::saveSlotThumbnail(int slot) {
	char filePath[kSaveThumbnailPathSize];
	snprintf(filePath, sizeof(filePath), kSaveThumbnailFileNameFormat, _savePath, slot);
	File f;
	if (!f.open(filePath, "wb")) {
		warning("Unable to save game state thumbnail to file '%s'", filePath);
		return false;
	}
	f.writeUint32LE(kSaveThumbnailMagic);
	f.write(_saveThumbnailPalette, sizeof(_saveThumbnailPalette));
	f.write(_saveThumbnail, sizeof(_saveThumbnail));
	return !f.ioErr();
}

bool Game::loadSlotThumbnail(int slot, uint8_t *dst, int pitch) {
	char filePath[kSaveThumbnailPathSize];
	snprintf(filePath, sizeof(filePath), kSaveThumbnailFileNameFormat, _savePath, slot);
	File f;
	if (!f.open(filePath, "rb")) {
		return false;
	}
	uint8_t palette[256 * 4];
	uint8_t row[kSaveThumbnailWidth];
	if (f.size() == 4 + sizeof(palette) + kSaveThumbnailWidth * kSaveThumbnailHeight) {
		if (f.readUint32LE() != kSaveThumbnailMagic) {
			return false;
		}
		if (f.read(palette, sizeof(palette)) != sizeof(palette)) {
			return false;
		}
	} else {
		memset(palette, 0, sizeof(palette));
		memcpy(palette, _bitmapBuffer0 + kOffsetBitmapPalette, sizeof(palette));
	}
	for (int y = 0; y < kSaveThumbnailHeight; ++y) {
		if (f.read(row, sizeof(row)) != sizeof(row)) {
			return false;
		}
		for (int x = 0; x < kSaveThumbnailWidth; ++x) {
			const uint8_t *p = palette + row[x] * 4;
			const int color = p[2] | (p[1] << 8) | (p[0] << 16);
			dst[y * pitch + x] = findBestMatchingMenuColor(_bitmapBuffer0 + kOffsetBitmapPalette, color);
		}
	}
	return true;
}

void Game::setFloatingStatus(const char *message) {
	snprintf(_floatingStatusText, sizeof(_floatingStatusText), "%s", message);
	_floatingStatusTicks = 36;
}

void Game::drawFloatingStatus() {
	if (_floatingStatusTicks <= 0 || _floatingStatusText[0] == 0) {
		return;
	}
	const uint8_t bgColor = findBestMatchingMenuColor(_bitmapBuffer0 + kOffsetBitmapPalette, 0x111820);
	const uint8_t textColor = findBestMatchingMenuColor(_bitmapBuffer0 + kOffsetBitmapPalette, 0xCAF6FF);
	const int w = getTextWidth(_floatingStatusText) + 20;
	_stub->fillRect(8, 8, w, 24, bgColor);
	drawThumbnailFrame(_stub, 8, 8, w, 24, textColor);
	drawMenuText(_stub, 18, 13, _floatingStatusText, textColor);
	--_floatingStatusTicks;
}
