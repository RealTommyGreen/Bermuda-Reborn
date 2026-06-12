# Bermuda Reborn v1.0 — Android Port Log

Fork: `cyxx/bermuda` (archived), merge-base: `cf6bdb6` (Dec 2019)
Branch: `Reborn`
Release: **BermudaRebornRelease1.0.apk** on Google Drive

---

## Foundation (Phases 1–12, May–June 2026)

### Phase 1: Android skeleton + SDL2 (2026-05-18)
- Android project: AGP 8.7.3, Kotlin 2.0.21, compileSdk 35, minSdk 24, NDK 27.2
- SDL2 2.30.11 via FetchContent, statically linked into `libbs.so`
- 20 engine sources compiled, `Mixer_Software_create` wired, `mixer_sdl.cpp` excluded
- `android_main.cpp` wraps `Game` with CLI argument parsing

### Phase 2: Assets (2026-05-18)
- 1572 BERMUDA assets embedded in APK (no VFW/installer files)
- `AssetExtractor.kt`: versioned extraction to `filesDir/bermuda_assets/BERMUDA/`

### Phase 3: Audio (2026-05-18)
- 12 MIDI tracks converted to OGG via fluidsynth + ffmpeg (44100 Hz stereo Vorbis)
- `stb_vorbis.c` (v1.22) integrated via `BERMUDA_STB_VORBIS` define
- `MixerChannel_StbVorbis` for music (75% volume, loop), `MixerChannel_Wav` for SFX

### Phase 4: Touch overlay + YUV fix (2026-05-18)
- 11 Kotlin files for touch overlay subsystem (`com.bermudasyndrome.android.touch/`)
- 9-button default preset: D-Pad, Use, Weapon, Run, Inventory, Status, Menu, LMB, RMB
- `kotlinx.serialization` for JSON-persisted config (`touch_buttons.json`, schema v1)
- YUV fix: `systemstub_sdl.cpp` — `_videoH = h`

### Phase 5: SDL2 Android JNI fix (2026-05-18)
- Replaced hand-rolled SDL layer with official `org/libsdl/app/` classes
- `BermudaActivity` extends `org.libsdl.app.SDLActivity`

### Phase 6: Asset-free release + SAF importer (2026-05-19)
- Removed all game assets from APK (asset-free build, ~15 MB)
- `BermudaLauncherActivity`: SAF folder picker, validation (BERMUDA.SPR, BERMUDA.WGP, SCN/-01.SCN, MIDI/TITLE.MID)
- `SafImporter.kt`: recursive import to `filesDir/imported_game/BERMUDA/`

### Phase 7: Native MIDI playback (2026-05-19)
- `MixerChannel_Midi`: TinySoundFont + TinyMidiLoader
- Bundled `default.sf2` (TimGM6mb, 5.7 MB) in `assets/soundfont/`
- MIDI fallback when OGG missing; case-insensitive filename resolution

### Phase 8: Direct-touch inventory + unified settings (2026-05-19)
- Absolute touch coords forwarded to SDL outside overlay buttons
- `TouchOverlaySettingsDialog`: global D-Pad-Run, 3 cheats, screen mode
- Schema v5: per-button D-Pad-Run removed, global toggle

### Phase 9: Native cheats + runtime screen mode (2026-05-19)
- JNI: `nativeSetCheat(cheatId, enabled)`, `nativeSetScreenMode(mode)`
- Cheats: God Mode, Infinite Ammo, All Weapons
- Screen modes: 4:3 aspect-correct, 16:9 stretched (gameplay only; menus/inventory/video remain 4:3)

### Phase 10: Controller support (2026-05-19)
- `ControllerDeviceDetector`: detects gamepad/joystick/dpad on launch
- Launcher prompt when controller detected (opt-in per start, not persisted)
- `ControllerConfig` (schema v1): remappable action-to-button mapping
- Default: A=jump, X=run, B=weapon, Y=use, START=menu, SELECT=inventory
- Menus: A=confirm, B=back, DPAD navigates; video: A skips

### Phase 11: Package refactor (2026-06-05)
- Package: `com.bermudasyndrome.android` → `com.bermuda.reborn`
- All Kotlin files, touch subsystem, controller code in new package
- Launcher icons updated

### Phase 12: SVG touch button icons (2026-06-06)
- 11 custom SVG icons from `Bermuda_Syndrome_Icons/`
- `SvgIconManager.kt`: 512×512 bitmap pipeline, AndroidSVG, white `PorterDuffColorFilter(SRC_IN)`
- JSON-driven config from `iconset.json` + `iconmappings.json`
- Button geometry rewrite per overlay icon manual

---

## Controls Overhaul (Phases 13–17, June 2026)

All following work is defined by `Controls.md` (now deleted — all phases complete).

### Phase 13: Native control foundation
- `CONTROL_ACTION_*` enum (RUN, JUMP_BUTTON, WEAPON_TOGGLE, USE, MENU_BACK, RELOAD)
- `CONTROL_STATE_*` bitmask (GUN_DRAWN, SWORD_DRAWN, CAN_RELOAD, RELOAD_BUSY, STATUS_VISIBLE)
- `PlayerInput` extended: `runAction`, `jumpButtonAction`, `weaponToggleAction`, `reloadAction`
- Touch contexts split: GAMEPLAY, VIDEO, BITMAP_CONFIRM, MENU
- JNI: `nativePerformControlAction(action, pressed)` + `nativeGetControlState()`
- Touch dispatcher handles `control_action` type

### Phase 14: Engine behavior
- Status bar default visible (`_lifeBarDisplayed = true`)
- `Game::findJack()` — finds `SceneObject` named "Jack" via `_sceneObjectsTable`
- Weapon-Toggle: `handleWeaponToggle()` toggles Gun/Sword via engine key sequences (SPACE/SHIFT), not direct var manipulation
- Run/Fire centralized: unarmed → run in Jack's facing direction; armed → SPACE (attack). DPAD overrides direction.
- Jump dedicated: `jumpButtonAction` sets Key 38 for normal jump; DPAD-Up remains classic Key 38 for ledges/climbing
- Reload state machine: standing (crouch→reload→stand up), crouched (reload only, stay crouched)
- `Game::engineControlState()` provides bitmask for UI

### Phase 15: Touch UI, icons, config migration
- 3 new SVG icons: `bs_fire.svg`, `bs_reload.svg`, `bs_sword.svg`
- `TOUCH_OVERLAY_CONFIG_VERSION` bumped to 9
- Default buttons migrated to `control_action` type: run (hold), jump_button (hold), weapon_toggle (tap), use (tap), menu_back (tap)
- Context-sensitive UI: video shows cancel/skip only; menus show DPAD + OK + Cancel; gameplay shows all
- Runtime icon switching: Gun → fire/reload icons, Sword → sword icon, unarmed → run/jump

### Phase 16: Codex Review 1 fixes
- Weapon toggle release handling (no raw `space` leak)
- Jump/DPAD-Up separation (jump no longer sets `dirMask |= DIR_UP`)
- `btn_jump` reload action switch at runtime
- Video/menu context dispatch fixed for control_action buttons
- Reload state machine simplified to single-phase

### Phase 17: Codex Review 2 fixes
- `isJackHangingOnLedge()`: Y-stability + animation change detection
- Jump suppressed at ledge hang; DPAD-Up still climbs
- Reload: `_reloadWasCrouched` preserves crouch state after reload
- Video cancel icon (shows `cancel`, not jump icon)
- Codex final approval for device tests granted

---

## Device Test Fixes (June 2026)

All fixes built, signed, ADB-installed to a physical test device, and user-verified.

1. **DPAD + Menu OK** — DPAD deadzone releases direction immediately; native direction mask API replaces Android DPAD key events; Menu OK icon shows correctly
2. **Horizontal stop after DPAD release** — `_keysPressed[37/39]` written every frame from `dirMask`
3. **Persisted button actions** — Schema v10, known buttons normalized at load, runtime action enforcement for `btn_run`/`btn_weapon`
4. **Weapon toggle + reload via engine key sequences** — `handleWeaponToggle()` uses SPACE/SHIFT pulses; reload multi-phase with frame counter
5. **Possession vs drawn state separation** — `_controlGunDrawn`/`_controlSwordDrawn` separate from `_varsTable[1/2]` possession; icons follow drawn state, not possession
6. **Inventory weapon selection preserved** — `_controlSelectedWeapon` stored; `bag.cpp` allows weapon switch when holstered (`!= 0`, not `== 1`)
7. **Inventory Gun/Sword switch persistence** — Weapon switch in bag sets `_controlSelectedWeapon`; holster sync no longer overwrites from `_varsTable`
8. **Weapon selection after holster** — Holster saves selection from own `_control*Drawn` state; `_weaponToggleBusyFrames` + `_pendingWeaponToggle` for debounce
9. **Inventory hitbox + weapon state authority** — Hitboxes use icon draw rectangles; armed state no longer frame-synced back to Gun from vars

### Misc fixes applied during device testing
- **Touch reload postponed** — `btn_jump` stays Jump in gameplay (reload state machine kept native, not triggered by touch)
- **Menu Cancel visible** — `btn_menu` shows Cancel icon in menus, dispatches ESCAPE
- **Inventory jump confirm + separate layouts** — `TOUCH_INPUT_CONTEXT_INVENTORY` context; schema v11 with `menu_buttons` separate from `buttons`
- **Screenshot-based defaults** — Gameplay and menu button positions from user-validated 2400×1080 layout
- **Default button sizes corrected** — D-Pad 0.400, Jump/Use/Weapon/Run 0.220, Menu OK/Cancel 0.260, etc.
- **Touch preset import/export** — SAF-based JSON export/import containing both `buttons` and `menu_buttons`
- **Validated preset as default + Lockbutton SVG** — Default source is exported JSON; `Lock_Button.svg` from Mario_Icons used for editor lock
- **System button opacity** — Lock and Settings buttons at alpha 0.35 (matches default preset)
- **Edit hint bar** — "Move Buttons around freely..." text shown when layout unlocked
- **Save menu cleanup** — Removed "ENTER SELECTS ESC CANCELS" text; RESTORE GAME / SAVE GAME titles centered

---

## Bug Fixes (June 2026)

### Bug 1: Inventory 4:3 stretch + weapon icon race
- Inventory forces 4:3 in stretched mode (same condition as gameplay)
- Weapon icon: `engineControlState()` cross-checks `_varsTable` before reporting drawn; `_weaponToggleDrawRequested` flag delays drawn state until engine confirms

### Bug 2: Video skip button position
- Video skip uses `btn_menu` from `menu_buttons` layout (same position as menu Cancel)

### Bug 3: Overlay context switch lag
- Context sync interval reduced to 80ms; immediate sync on user interaction

### Bug 4: Weapon icon race during exit animations
- `findJack()` case-insensitive (`strcasecmp`); draw confirmation requires Jack in armed motion (`animNum != 0`), not just `_varsTable` state

### Bug 5: ESC/Menu crash after Quickload
- Root cause: SaveStates contained transient MENU objects causing "Duplicate object name MENU" on next `initMenu()`
- Fix: `saveload.cpp` writes only gameplay object count; `discardTransientMenuObjects()` cleans stale MENU objects on load

### Bug 6: Run button auto-walk flip direction inverted
- Root cause: `flip` is tri-state (0=right, 1=vertical, 2=left), but code checked `if (jack->flip)` treating it as boolean
- Fix: explicit `if (jack->flip == 2)` with correct LEFT/RIGHT key mapping

---

## Current State (2026-06-12)

**Project is release-ready** with one known remaining issue:

### Known issue: Unlimited Ammo HUD + Reload Loop
When `cheatInfiniteAmmo` is active:
- HUD shows 1/6 ammo even though functionally unlimited
- Reload action loops Jack in reload animation (requires DPAD-Up to break out)

This does not affect normal gameplay (cheat disabled). Fix requires:
- HUD ammo display: show full clip or cheat-specific state when `cheatInfiniteAmmo == true`
- Reload: ignore or fast-complete reload when infinite ammo active

---

## Architecture

```
android/app/src/main/java/com/bermuda/reborn/
├── BermudaLauncherActivity.kt        # MAIN — SAF import UI
├── BermudaActivity.kt                # SDL game activity
├── SafImporter.kt                    # SAF recursive import + validation
├── AssetExtractor.kt                 # Legacy fallback
├── ControllerDeviceDetector.kt       # Input device detection
└── touch/
    ├── TouchOverlayController.kt     # Lifecycle, layout, dispatch
    ├── TouchOverlayButtonView.kt     # Button rendering + interaction
    ├── TouchOverlaySettingsDialog.kt # Cheats, D-Pad-Run, screen mode
    ├── TouchOverlayEditDialog.kt     # Per-button editor
    ├── TouchOverlayGridView.kt       # Grid for edit mode
    ├── TouchOverlayLockButtonView.kt # Layout lock toggle
    ├── TouchOverlaySettingsButtonView.kt
    ├── TouchButtonModels.kt          # Data model, defaults
    ├── TouchButtonPresets.kt         # Presets
    ├── TouchButtonStore.kt           # JSON persistence + migration
    ├── TouchInputDispatcher.kt       # Action → KeyEvent dispatch
    ├── ControllerConfigStore.kt      # Controller mapping persistence
    ├── ControllerMappingDialog.kt    # Controller remap UI
    └── SvgIconManager.kt             # SVG loading + caching
```

## Build

```powershell
cd "D:\Coding\Bermuda Reborn\android"
.\gradlew.bat :app:assembleRelease
# APK: .\app\build\outputs\apk\release\app-release.apk (~18 MB, asset-free)
```
