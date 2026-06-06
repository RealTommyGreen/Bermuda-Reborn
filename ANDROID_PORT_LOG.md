# Bermuda Syndrome Android Port — Work Log

## Subproject: `D:/Coding/Bermuda Reborn/`
## Fork: `cyxx/bermuda` (archived), merge-base: `cf6bdb6` (Dec 2019)

---

## Phase 1: Android skeleton + SDL2 (2026-05-18)

- Created `android/` project: AGP 8.7.3, Kotlin 2.0.21, compileSdk 35, minSdk 24, NDK 27.2
- SDL2 2.30.11 via FetchContent, statically linked into `libbs.so`
- 20 engine sources compiled, `Mixer_Software_create` wired, `mixer_sdl.cpp` excluded
- `android_main.cpp` wraps `Game` with CLI argument parsing
- Build: `assembleDebug` ~10.8 MB, 3 ABIs, clean

## Phase 2: Assets embedded + extractor (2026-05-18)

- 1572 BERMUDA assets in `android/app/src/main/assets/BERMUDA/` (no VFW/installer files)
- `AssetExtractor.kt`: versioned extraction from APK assets to `filesDir/bermuda_assets/BERMUDA/`
- Build: ~129.8 MB APK

## Phase 3: Audio — MIDI + OGG + stb_vorbis (2026-05-18)

- 12 MIDI tracks converted to OGG via fluidsynth + ffmpeg (44100 Hz stereo Vorbis, 27.4 MB)
- `stb_vorbis.c` (v1.22) integrated via `BERMUDA_STB_VORBIS` define
- `MixerChannel_StbVorbis` for music (75% volume, loop), `MixerChannel_Wav` for SFX (100%)
- Music path: `filesDir/bermuda_assets/BERMUDA/MUSIC/trackXX.ogg`
- Build: ~158 MB APK

## Phase 4: Touch overlay + YUV fix (2026-05-18)

- 11 Kotlin files in `com.bermudasyndrome.android.touch/`
- 9-button default preset: D-Pad, Use, Weapon, Run, Inventory, Status, Menu, LMB, RMB
- `kotlinx.serialization` for JSON-persisted config (`touch_buttons.json`, schema v1)
- YUV fix: `systemstub_sdl.cpp:500` — `_videoH = h`
- Build: ~157 MB APK

## Phase 5: SDL2 Android JNI fix (2026-05-18)

- Replaced hand-rolled Kotlin SDL layer with official `org/libsdl/app/` Java classes from SDL 2.30.11
- `BermudaActivity` extends `org.libsdl.app.SDLActivity`, calls `getLibraries() = {"bs"}`
- Touch overlay attaches to `SDLActivity.getContentView()`
- Added `.gitignore` for Gradle/CMake/build artifacts

## Phase 6: Asset-free release + SAF importer (2026-05-19)

- Removed all BERMUDA game assets from APK (asset-free build)
- `BermudaLauncherActivity` as MAIN/LAUNCHER: SAF folder picker, validation, background import
- `SafImporter.kt`: validates `BERMUDA.SPR`, `BERMUDA.WGP`, `SCN/-01.SCN`, `MIDI/TITLE.MID`
- Imports to `filesDir/imported_game/BERMUDA/`, writes `.import_manifest.json`
- `BermudaActivity` uses imported path, no `AssetExtractor`
- Build: ~15 MB Release APK

## Phase 7: Native MIDI playback (2026-05-19)

- `MixerChannel_Midi`: TinySoundFont (v0.9, MIT) + TinyMidiLoader (v0.7, ZLIB)
- Bundled `default.sf2` (TimGM6mb, 5.7 MB) in `assets/soundfont/`
- MIDI fallback when `trackXX.ogg` missing; case-insensitive filename resolution
- OGG files removed from assets (MIDI replaces them)
- Build: ~15 MB Release APK

## Phase 8: Direct-touch inventory + unified settings (2026-05-19)

- Absolute touch coords forwarded to SDL outside overlay buttons
- `TouchOverlaySettingsDialog` rewritten: global D-Pad-Run, 3 cheats, screen mode
- Per-button D-Pad-Run option removed; global toggle in `TouchOverlayConfig` (schema v5)

## Phase 9: Native cheats + runtime screen mode (2026-05-19)

- JNI bridge: `nativeSetCheat(cheatId, enabled)`, `nativeSetScreenMode(mode)`
- Cheats: God Mode (no-hit), Infinite Ammo, All Weapons
- Runtime screen modes: 4:3 aspect-correct always, 16:9 stretched (gameplay only)
- Menus, inventory, dialogues, videos remain 4:3 regardless of stretch setting

## Phase 10: Controller support (2026-05-19)

- `ControllerDeviceDetector`: detects gamepad/joystick/dpad on launch
- Launcher prompt when controller detected — opt-in on every start (not persisted)
- `ControllerConfig` (schema v1): remappable action→button mapping, persisted to `controller_config.json`
- Default mapping: A=jump, X=run, B=weapon, Y=use, START=menu, SELECT=inventory
- `ControllerMappingDialog`: controller-navigable remapping with button learning
- JNI `nativeSetControllerConfig(enabled, mapping, dpadDoubleTapRunEnabled)` via `systemstub_sdl.cpp`
- Controller disabled: axes/buttons blocked even if device plugged in
- Menus: fixed A=confirm, B=back; DPAD navigates
- Video/title context: A skips, controller config applied before `Game::init()`

## Phase 11: Package refactor + polishing (2026-06-05)

- Package renamed: `com.bermudasyndrome.android` → `com.bermuda.reborn`
- All Kotlin files, touch subsystem, and controller code in new package
- `ControllerConfigStore.kt`, `ControllerMappingDialog.kt`, `ControllerDeviceDetector.kt` added
- Launcher icons updated
- Game logic tweaks across `game.cpp`, `menu.cpp`, `bag.cpp`, `dialogue.cpp`, `mixer_soft.cpp`, `systemstub_sdl.cpp`
- Project declared functionally complete

## Phase 12: SVG touch button icons (2026-06-06)

- 11 custom SVG icons from `Bermuda_Syndrome_Icons/` (BS_Jump, BS_Run, BS_Shoot, BS_Use, BS_OK, BS_Cancel, BS_Status, BS_Menu, BS_Save, BS_Load, BS_Inventory)
- `SvgIconManager.kt` — canonical 512×512 bitmap pipeline with `androidsvg:1.4`, white `PorterDuffColorFilter(SRC_IN)`, JSON-driven config from `iconset.json` + `iconmappings.json`
- `TouchOverlayButtonView.kt` — full geometry rewrite per Overlay Icon Manual: `buttonHeight = minOf(h, w / 1.8f)`, `computeOuterShapeBounds()`, `computeIconShapeBounds()` with 0.85× circle factor, DPAD special case without shape background
- Default button sizes scaled 1.6× (0.103→0.165, 0.115→0.184) to compensate for new buttonHeight-based geometry
- 11 SVG resources + `iconset.json` + `iconmappings.json` in `res/raw/`
- OK/Cancel icons included for future menu overlay system

## Phase 13: Native Control-Grundlage — Controls.md Phase 1 (2026-06-06)

- `systemstub.h`: `CONTROL_ACTION_*` enum (RUN, JUMP_BUTTON, WEAPON_TOGGLE, USE, MENU_BACK, RELOAD), `CONTROL_STATE_*` bitmask enum (GUN_DRAWN, SWORD_DRAWN, CAN_RELOAD, RELOAD_BUSY, STATUS_VISIBLE)
- `PlayerInput` extended: `runAction`, `jumpButtonAction`, `weaponToggleAction`, `reloadAction` semantic fields
- Touch contexts split: `TOUCH_INPUT_CONTEXT_GAMEPLAY`, `VIDEO`, `BITMAP_CONFIRM`, `MENU`
- `SystemStub` base: `performControlAction(action, pressed)` + `getControlState()` virtual methods
- `systemstub_sdl.cpp`: `performControlAction` maps control actions to both semantic and raw PlayerInput fields; `getControlState` stub for Phase 2; `_videoPlaybackActive` flag; updated `getTouchInputContext()` for VIDEO/BITMAP_CONFIRM separation; `applyAction` sets semantic fields alongside raw inputs
- JNI: `nativePerformControlAction(action, pressed)` + `nativeGetControlState()` in `android_main.cpp` + `BermudaActivity.kt`
- `TouchInputDispatcher.kt`: `control_action` type handling via `dispatchControlAction()`, `controlActionByName()`, context constants updated to match C++ enum values
- `TouchButtonPresets.kt`: `control_action` in `actionMatches`

## Phase 14: Engine-Verhalten — Controls.md Phase 2 (2026-06-06)

- Statusleiste default sichtbar: `_lifeBarDisplayed = true` in `restart()`
- `Game::findJack()` — sucht `SceneObject` mit Namen "Jack" über `_sceneObjectsTable`
- Weapon-Toggle: `handleWeaponToggle()` toggled Gun/Sword über `_varsTable[2]`/`_varsTable[1]`; Gun ziehen wenn vorhanden, sonst Sword; bewaffnet holstert beide
- Run/Fire zentral: `runAction` bei unbewaffnet = `_keysPressed[16]` (SHIFT) + Auto-Walk in Jack-Blickrichtung (flip-basiert); bei bewaffnet = `_keysPressed[32]` (SPACE = Attack); D-Pad Left/Right ueberschreibt Auto-Walk-Richtung
- Jump dediziert: `jumpButtonAction` setzt `_keysPressed[38]` fuer normalen Sprung; DPAD-Up bleibt klassischer Key 38 fuer Kanten/Hochziehen
- Reload-State-Machine: `handleReloadSequence()` — stehend erst DOWN crouchen (Phase 1), dann DOWN reload (Phase 2); `finishReloadIfComplete()` erkennt Reload-Ende via `_varsTable[3] >= 4` (Ammo voll); crouched bleibt crouched, stehend steht auf
- `Game::engineControlState()` liefert Bitmask: GUN_DRAWN, SWORD_DRAWN, CAN_RELOAD, RELOAD_BUSY, STATUS_VISIBLE
- `getControlState()` in `systemstub_sdl.cpp` delegiert an `g_game->engineControlState()`
- `g_game` in `android_main.cpp` von `static` auf globale Sichtbarkeit geaendert
- Build: Debug APK sauber (~18 MB)

---

---

## Build (Release APK)

```powershell
cd "D:\Coding\Bermuda Reborn\android"
.\gradlew.bat :app:assembleRelease
# APK: .\app\build\outputs\apk\release\app-release.apk (~18 MB, asset-free)
```

## Architecture

```
android/app/src/main/java/com/bermuda/reborn/
├── BermudaLauncherActivity.kt    # MAIN — SAF import UI
├── BermudaActivity.kt            # SDL game activity (extends org.libsdl.app.SDLActivity)
├── SafImporter.kt                # SAF recursive import + validation
├── AssetExtractor.kt             # Legacy fallback (asset extraction)
├── ControllerDeviceDetector.kt   # Input device detection
└── touch/
    ├── TouchOverlayController.kt # Lifecycle, layout, touch dispatch
    ├── TouchOverlayButtonView.kt # Button rendering + interaction
    ├── TouchOverlaySettingsDialog.kt # Cheats, D-Pad-Run, screen mode, reset/delete
    ├── TouchOverlayEditDialog.kt   # Per-button editor
    ├── TouchOverlayGridView.kt     # Grid overlay for edit mode
    ├── TouchOverlayLockButtonView.kt      # Layout lock toggle
    ├── TouchOverlaySettingsButtonView.kt  # Settings gear icon
    ├── TouchButtonModels.kt       # Data model (schema v5)
    ├── TouchButtonPresets.kt      # Bermuda-specific button presets
    ├── TouchButtonStore.kt        # JSON persistence
    ├── TouchInputDispatcher.kt    # Action → Android KeyEvent
    ├── ControllerConfigStore.kt   # Controller mapping persistence
    ├── ControllerMappingDialog.kt # Controller button remap UI
    └── SvgIconManager.kt          # SVG icon loading, caching, rendering
```

## Remaining Risks

- No physical device test (no ADB device connected)
- MIDI playback with TSF/TML untested on real Android hardware
- SAF import speed on low-end devices unknown
- Controller tested via emulator smoke only
