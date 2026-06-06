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

## Phase 15: Touch-UI, Icons, Config-Migration — Controls.md Phase 3 (2026-06-06)

- 3 neue SVG-Icons aus `Bermuda_Syndrome_Icons/` importiert: `bs_fire.svg`, `bs_reload.svg`, `bs_sword.svg`
- `iconset.json` um BS_Fire/BS_Reload/BS_Sword mit vollstaendigen Icon-Metadaten erweitert
- `iconmappings.json` um `fire`→BS_Fire, `reload`→BS_Reload, `sword`→BS_Sword ergaenzt
- `TOUCH_OVERLAY_CONFIG_VERSION` auf 9 erhoeht
- Default-Buttons auf `control_action` type umgestellt:
  - `btn_run` → `run` (hold), `btn_jump` → `jump_button` (hold), `btn_weapon` → `weapon_toggle` (tap), `btn_use` → `use` (tap), `btn_menu` → `menu_back` (tap)
- Presets: `run`/`jump`/`weapon`/`menu`/`use` auf `control_action` type mit `controlActionPreset()` Helper
- TouchButtonStore-Migration: `migrateActionsForButton()` aktualisiert bekannte `btn_*` Actions auf `control_action`; `migrateIconForButton()`/`migrateLabelForButton()` updaten Icons/Labels; Positionen bleiben erhalten
- `TouchOverlayController`: `syncContextSensitiveState()` pollt alle 250ms `nativeGetTouchInputContext()` + `nativeGetControlState()`
  - Video-Kontext: nur `btn_jump` sichtbar (als Skip)
  - Menu/Bitmap-Kontext: nur D-Pad, `btn_use` (OK), `btn_menu` (Cancel), `btn_jump` sichtbar
  - Gameplay: alle Buttons sichtbar
- Runtime-Icon-Switching: Gun drawn → `btn_run` icon="fire", `btn_jump` icon="reload"; Sword drawn → `btn_run` icon="sword"; unbewaffnet → normale Icons
- Build: Debug APK sauber (~18 MB)

---

---

## Review: Controls Endabnahme vor Device-Tests (2026-06-06)

Status: **Keine Freigabe fuer Device-Tests. Fix erforderlich.**

Lokaler Check:
- Branch: `Reborn`
- Arbeitsbaum vor Review: sauber
- Build: `android/gradlew.bat :app:assembleDebug` erfolgreich

Blocker:
- `systemstub_sdl.cpp:338-349`: `CONTROL_ACTION_WEAPON_TOGGLE`, `CONTROL_ACTION_USE` und `CONTROL_ACTION_MENU_BACK` setzen auf Press rohe Inputs (`space`, `enter`, `escape`), behandeln Release aber nicht. Besonders `space` bleibt nach einem Weapon-Tap dauerhaft aktiv. Touch-Taps senden nach 120 ms Release, aber Native ignoriert diesen Pfad.
- `systemstub_sdl.cpp:333-336` und `systemstub_sdl.cpp:293-296`: dedizierter Jump setzt weiterhin `dirMask |= DIR_UP`. Damit ist `jumpButtonAction` nicht sauber von DPAD-Up getrennt; das geplante Kantenverhalten kann so nicht zuverlaessig funktionieren.
- `TouchOverlayController.kt:500-503`: bei gezogener Gun wird `btn_jump` nur auf das Reload-Icon umgeschaltet. Die Action bleibt `jump_button`; `CONTROL_ACTION_RELOAD` wird vom Touch-Overlay nie ausgeloest.
- `TouchOverlayController.kt:511-515` plus `TouchInputDispatcher.kt:22-35`: Video- und Menu-Kontext benutzen sichtbare `control_action` Buttons, aber die alte Kontext-Uebersetzung auf ENTER/ESCAPE greift nur fuer `type == "key"`. Video-Skip mit `btn_jump` dispatcht deshalb Jump statt Skip/Cancel. Im Menu ist zusaetzlich `btn_jump` sichtbar, obwohl nur D-Pad, OK und Cancel vorgesehen sind.
- `game.cpp:253-266` und `game.cpp:561-566`: Reload-State-Machine kommt bei einem Tap nur bis `_reloadPhase = 1`. Im naechsten Frame wird `_keysPressed[40]` wieder aus `dirMask` ueberschrieben; Phase 2 wird ohne erneute Reload-Action nicht erreicht. Auch das geplante Warten auf Animations-/Reload-Ende ist nicht implementiert, sondern nur `_varsTable[3] >= 4`.

Weitere Hinweise:
- `performControlAction()` sollte semantische Actions von rohen Keyboard-Feldern trennen. Rohes `space`/`enter`/`escape` nur dann setzen, wenn der jeweilige Control-Pfad genau diesen Key benoetigt und Release symmetrisch behandelt.
- Touch-Kontext sollte entweder die Button-Actions dynamisch ersetzen oder fuer Video/Menu dedizierte sichtbare Buttons mit passenden `control_action`/Key-Targets verwenden.
- Controller-Mapping muss dieselben Fixes bekommen, weil `applyAction()` aktuell weiterhin rohe Keys mit den semantischen Feldern vermischt.

Erwartung fuer Fix:
- Nach Fix erneut Build ausfuehren.
- Vor Device-Tests erneut Endabnahme durch Codex.
- Keine APK auf Drive hochladen.

---

## Phase 16: Codex-Review-Fixes Controls.md (2026-06-06)

Alle 5 Blocker aus Codex Review behoben:

- **Fix 1 — `performControlAction` Release-Handling:** `CONTROL_ACTION_WEAPON_TOGGLE` setzt nur noch `weaponToggleAction` (one-shot), kein rohes `space` mehr. `CONTROL_ACTION_USE`/`CONTROL_ACTION_MENU_BACK` setzen `enter`/`escape` symmetrisch via `= pressed`. `applyAction(kActionWeapon)` ebenfalls von `_pi.space` befreit.
- **Fix 2 — Jump/DPAD-Trennung:** `performControlAction(CONTROL_ACTION_JUMP_BUTTON)` und `applyAction(kActionJump)` setzen kein `dirMask |= DIR_UP` mehr. `jumpButtonAction` arbeitet jetzt isoliert; DPAD-Up bleibt separater Pfad.
- **Fix 3 — btn_jump RELOAD-Action:** `TouchOverlayController.syncContextSensitiveState()` schaltet jetzt nicht nur das Icon, sondern auch die `actions`-Liste um: Gun drawn → `control_action` "reload" (tap), normal → "jump_button" (hold).
- **Fix 4 — Video/Menu-Kontext:** `TouchInputDispatcher` hat neue `contextualKeyCodeForControl()` — in Video/Bitmap/Menu-Kontext dispatcht `btn_jump` jetzt `KEYCODE_ENTER` statt der control_action. `visibilityForContext` zeigt `btn_jump` nur noch im Video-Kontext, nicht mehr im Menu.
- **Fix 5 — Reload-State-Machine:** `handleReloadSequence()` auf Single-Phase vereinfacht (hält DOWN durchgehend). `updateKeysPressedTable()` schuetzt `_keysPressed[40]` vor dirMask-Ueberschreibung waehrend `_reloadPhase`. `isReloadComplete()` und `finishReloadIfComplete()` auf Single-Phase umgestellt.

Build: Debug APK sauber. Kein APK-Upload (Codex-Freigabe steht noch aus).

---

## Review 2: Controls Endabnahme nach Fixes (2026-06-06)

Status: **Weiterhin keine Freigabe fuer Device-Tests. Fix erforderlich.**

Lokaler Check:
- Branch: `Reborn`
- Build: `android/gradlew.bat :app:assembleDebug` erfolgreich

Behobene Punkte:
- Release-Handling fuer `CONTROL_ACTION_USE` und `CONTROL_ACTION_MENU_BACK` ist jetzt symmetrisch.
- `weapon_toggle` setzt kein rohes `space` mehr.
- `jumpButtonAction` setzt im SDL-Stub kein `DIR_UP` mehr.
- `btn_jump` wechselt bei Gun jetzt auf `CONTROL_ACTION_RELOAD`.
- `btn_jump` ist im Menu nicht mehr sichtbar.

Verbleibende Blocker:
- `game.cpp:532-536`: dedizierter Jump setzt weiterhin immer `_keysPressed[38] = 1`, ohne Hang-/Ledge-State zu pruefen. Damit ist die geplante Trennung "Jump-Button haelt an Kante, DPAD-Up zieht hoch" noch nicht umgesetzt, sondern nur der Input-Pfad getrennt.
- `game.cpp:253-271`: Reload haelt nur DOWN bis `_varsTable[3] >= 4` und loescht danach DOWN immer. Es gibt keine Unterscheidung zwischen stehend und bereits crouched; ein crouched Reload steht danach ggf. auf, obwohl der Plan "bleibt in Crouch" fordert. Ausserdem fehlt weiter die Erkennung des tatsaechlichen Animations-/Reload-Endes.
- `TouchOverlayController.kt:500-516` / `TouchInputDispatcher.kt:147-163`: Video-Kontext zeigt weiterhin `btn_jump` als Skip-Button, aber das Icon wird nicht auf `cancel` umgestellt. Notes fordern im Video-Kontext ausschliesslich Cancel (`bs_cancel.svg`). Der Dispatcher-Kommentar nennt Menu-Cancel, tatsaechlich wird nur `btn_jump` auf ENTER gemappt.

Erwartung fuer Fix:
- Hang-/Ledge-State in `Game` wirklich erkennen und `jumpButtonAction` in diesem Zustand nicht als dauerhaftes Key-38-Hochziehen behandeln; DPAD-Up bleibt klassischer Key-38-Pfad.
- Reload-State-Machine mit gespeicherter Ausgangshaltung (`wasCrouched`) und Abschlussbedingung ueber Jack-State/Motion oder belastbares Reload-Endsignal umsetzen.
- Video-Skip-Button sichtbar als Cancel-Icon/Cancel-Button konfigurieren, nicht als Jump-Icon.
- Danach erneut Build und Codex-Endabnahme vor Device-Tests.

---

## Phase 17: Codex-Review-2-Fixes Controls.md (2026-06-06)

Alle 3 Blocker aus Codex Review 2 behoben:

- **Fix 1 — Jump/Ledge-Detection (`game.cpp`):** `isJackHangingOnLedge()` implementiert. Prüft Y-Stabilität (y == yPrev && y > 2) und Animationswechsel (animNum != baseAnimNum). Bei Hang unterdrückt `jumpButtonAction` Key 38, DPAD-Up setzt weiterhin Key 38 für Kanten-Hochziehen. Debug-Logging für Motion-IDs zur späteren Verfeinerung.
- **Fix 2 — Reload-State-Machine (`game.cpp`):** `_reloadWasCrouched` speichert Crouch-Zustand vor Reload-Start. `finishReloadIfComplete()`: stehend → release DOWN (steht auf), crouched → behält DOWN (bleibt crouched). Motion-Logging bei Reload-Ende für spätere Motion-basierte Erkennung.
- **Fix 3 — Video-Cancel-Icon (`TouchOverlayController.kt`):** `syncContextSensitiveState()` zeigt im Video-Kontext (context==1) das `cancel`-Icon statt Jump-Icon. `iconmappings.json` um `cancel`→BS_Cancel ergänzt.

Build: Debug APK sauber (~18 MB). Weiterhin kein APK-Upload (Codex-Freigabe steht aus).

---

## Codex Final-Fix und Freigabe fuer Device-Tests (2026-06-06)

Status: **Freigabe fuer Device-Tests erteilt. Keine Freigabe fuer APK-Upload auf Drive.**

Lokaler Check:
- Branch: `Reborn`
- Build: `android/gradlew.bat :app:assembleDebug` erfolgreich

Durch Codex korrigierte Restpunkte:
- `game.cpp`: DPAD-Up gewinnt jetzt auch dann, wenn der dedizierte Jump-Button gleichzeitig gehalten wird. Der Jump-Button wird nur im erkannten Ledge-Hang unterdrueckt; DPAD-Up setzt weiterhin Key 38 fuer Climb/Pull-up.
- `game.cpp`: Reload merkt sich die Ausgangshaltung vor dem Reload ueber `_reloadWasCrouched`.
- `game.cpp`: Stehender Reload sendet nach Reload-Ende explizit UP zum Aufstehen. Crouched Reload loest DOWN, sendet kein UP und bleibt damit aus Control-Sicht crouched.
- `game.cpp`: DOWN aus dem D-Pad ueberschreibt den aktiven Reload-Hold nicht mehr, solange `_reloadPhase == 1`.

Ergebnis der Endabnahme:
- Touch-/Controller-Pfade fuer Jump, DPAD-Up, Reload, Run/Fire, Video-Cancel und Menu-Buttons sind lokal konsistent mit dem Controls.md-Plan.
- Der verbleibende Risikoanteil ist die heuristische Ledge-Erkennung (`isJackHangingOnLedge()`), weil die exakten Motion-IDs erst auf dem Geraet anhand realer Szenen bestaetigt werden koennen. Dafuer sind Debug-Logs hinterlegt.
- Device-Tests koennen jetzt mit Fokus auf Ledge-Hang, DPAD-Up-Climb, standing/crouched Reload, Video-Cancel und Menu-OK/Cancel starten.

Wichtig: Keine APK auf Drive hochladen, bis die Device-Tests bestanden sind und Codex danach die finale Plan-Freigabe erteilt.

---

## Device-Test-Fix 1: Touch-DPAD und Menu-OK (2026-06-06)

Status: **Fix umgesetzt, erneuter Device-Test erforderlich. Keine Freigabe fuer APK-Upload auf Drive.**

Ausgangspunkt:
- Video-Kontext war korrekt: nur Skip/Cancel-Button mit Cancel-Icon.
- Menu-Kontext zeigte den Bestaetigen-Button funktional korrekt, aber mit Use-Icon statt OK-Icon.
- Das vermeintlich frueh verfuegbare Gewehr war ein Testfehler durch aktivierten Alle-Waffen-Cheat und wird nicht als Bug gewertet.
- Touch-DPAD blieb nach Loslassen/Richtungswechsel haengen und blockierte damit weitere Funktionstests.

Umgesetzte Fixes:
- `TouchOverlayController.kt`: `btn_use` wird in Menu- und Bitmap-Confirm-Kontexten visuell auf `ok` umgeschaltet und ausserhalb dieser Kontexte wieder auf `use`.
- `iconmappings.json`: Mapping `ok` -> `BS_OK` ergaenzt.
- `TouchOverlayButtonView.kt`: DPAD-Bewegung in die Deadzone gibt die vorherige Richtung jetzt sofort frei.
- `TouchInputDispatcher.kt` / `BermudaActivity.kt` / `android_main.cpp` / `systemstub_sdl.cpp`: Touch-DPAD sendet Richtungen jetzt ueber eine native Richtungsmasken-API statt ueber Android-DPAD-Keyevents. Richtungswechsel und Release setzen damit direkt `PlayerInput::dirMask`.

Lokaler Check:
- Branch: `Reborn`
- Build: `android/gradlew.bat :app:assembleDebug` erfolgreich
- `git diff --check` sauber

Erwartung fuer naechsten Device-Test:
- Menu-Bestaetigen muss das OK-Icon anzeigen.
- Ohne gezogene Waffe muessen Run/Jump angezeigt werden; Fire/Reload nur bei wirklich gezogener Gun.
- DPAD links/rechts/hoch/runter muss beim Loslassen und beim Wechsel in die Deadzone sofort stoppen.
- Danach koennen die bisher blockierten Tests fuer Run, Jump, Weapon, Reload und Ledge-Verhalten fortgesetzt werden.

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
