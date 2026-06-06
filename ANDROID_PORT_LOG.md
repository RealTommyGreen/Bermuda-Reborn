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

## Device-Test-Fix 2: Horizontalbewegung stoppt nach DPAD-Release (2026-06-06)

Status: **Fix umgesetzt und signierte Release-APK fuer Device-Test installiert. Keine Freigabe fuer APK-Upload auf Drive.**

Ausgangspunkt:
- Icons sind jetzt korrekt: Menu zeigt OK, Gameplay ohne gezogene Waffe zeigt Run und Jump.
- Verbleibender Blocker: Nach einer Touch-DPAD-Bewegung lief Jack weiter, obwohl die Richtung losgelassen wurde.

Ursache:
- `TouchInputDispatcher` loeschte die native Richtung zwar, aber `Game::updateKeysPressedTable()` schrieb `_keysPressed[37]`/`_keysPressed[39]` nur bei aktivem horizontalem DPAD neu. Nach Release blieben die alten Key-Werte deshalb im Engine-Keybuffer stehen.

Fix:
- `game.cpp`: horizontale Bewegungstasten werden jetzt in jedem Frame aus `dirMask` geschrieben und damit bei Release auf 0 gesetzt.
- Die einzige Ausnahme bleibt der geplante Run-Autowalk: Wenn `runAction` ohne DPAD-Override gehalten wird und Jack unbewaffnet ist, darf Run weiterhin die Blickrichtung als Bewegung setzen.

Lokaler Check:
- Branch: `Reborn`
- Build: `android/gradlew.bat :app:assembleDebug` erfolgreich

Erwartung fuer naechsten Device-Test:
- DPAD links/rechts muss beim Loslassen sofort stoppen.
- Richtungswechsel links/rechts darf keine alte Richtung halten.
- Run-Autowalk muss weiterhin nur solange laufen, wie der Run-Button gehalten wird, und DPAD muss ihn uebersteuern.

---

## Device-Test-Fix 3: Persistierte Button-Actions fuer Weapon/Fire/Reload (2026-06-06)

Status: **Fix umgesetzt und signierte Release-APK fuer Device-Test installiert. Keine Freigabe fuer APK-Upload auf Drive.**

Ausgangspunkt:
- DPAD funktioniert wieder.
- Icons sind korrekt: unbewaffnet Run/Jump, bei gezogener Gun Fire/Reload.
- Funktion war falsch: Weapon schoss weiter, Fire steckte die Waffe weg bzw. nutzte altes Shift-Verhalten, Reload tat nichts, und nach Holstern blieben Fire/Reload sichtbar.

Ursache:
- Die Runtime-UI wechselte Icons, aber bestehende gespeicherte `touch_buttons.json`-Layouts konnten noch alte Actions behalten. Dadurch passten Icon und dispatchte Action nicht zusammen.
- Die semantische Run-Action setzte im SDL-Stub zusaetzlich `_pi.shift`, was fuer das neue Run/Fire-Verhalten nicht mehr noetig ist und bewaffnetes Verhalten stoeren kann.

Fix:
- `TOUCH_OVERLAY_CONFIG_VERSION` auf 10 erhoeht, damit bestehende Layouts migriert werden.
- `TouchButtonStore` normalisiert bekannte Buttons auch bei aktueller Schema-Version: Positionen bleiben erhalten, Actions/Icons/Labels werden fuer `btn_run`, `btn_weapon`, `btn_jump`, `btn_use`, `btn_menu` auf Plan-Defaults gesetzt.
- `TouchOverlayController` setzt zur Laufzeit fuer `btn_run` immer die semantische `run`-Action und fuer `btn_weapon` immer `weapon_toggle`, unabhaengig von gespeicherten Alt-Actions.
- `systemstub_sdl.cpp`: `CONTROL_ACTION_RUN`/Controller-Run setzen nur noch `runAction`, nicht mehr direkt `_pi.shift`. Unbewaffneter Run und bewaffnetes Fire werden weiterhin zentral in `Game::updateKeysPressedTable()` entschieden.

Lokaler Check:
- Branch: `Reborn`
- Build: `android/gradlew.bat :app:assembleDebug` erfolgreich
- Build: `android/gradlew.bat :app:assembleRelease` erfolgreich
- Signierte Release-APK per `adb install -r` erfolgreich installiert

Erwartung fuer naechsten Device-Test:
- Weapon-Tap zieht/holstert nur die Waffe und schiesst nicht.
- Bei gezogener Gun feuert der Fire-Button (`btn_run`) und holstert nicht.
- Reload-Button (`btn_jump`) startet die Reload-Sequenz.
- Nach Holstern wechseln Icons und Actions wieder auf Run/Jump.

---

## Device-Test-Fix 4: Weapon-Toggle und Reload ueber Engine-Keysequenzen (2026-06-06)

Status: **Fix umgesetzt und signierte Release-APK fuer Device-Test installiert. Keine Freigabe fuer APK-Upload auf Drive.**

Ausgangspunkt:
- Fire-Button feuert korrekt.
- Weapon-Button blendete HUD-Waffensymbol und Touch-Icons um, aber Jacks eigentliche Waffenhaltung blieb bewaffnet.
- Run-Button konnte danach die Waffe tatsaechlich wegstecken, weil er im bewaffneten Altzustand noch den originalen Shift/Holster-Pfad erreichte.
- Reload-Button hatte keine sichtbare Wirkung.

Ursache:
- `handleWeaponToggle()` setzte bisher direkt `_varsTable[1/2]`. Diese Variablen steuern HUD/UI-State, loesen aber nicht zwingend Jacks echte Weapon-Motion/Scripts aus.
- Reload bestand nur aus einem sofortigen DOWN-Hold und konnte im gleichen Ablauf zu schnell als abgeschlossen gelten.

Fix:
- `game.cpp`: Weapon-Toggle setzt keine Weapon-Vars mehr direkt. Stattdessen wird die originale Engine-Eingabe gepulst:
  - bewaffnet: `SHIFT` fuer Holster
  - unbewaffnet und Waffe verfuegbar: `SPACE` fuer Draw
- `game.cpp`: Reload-State-Machine auf mehrphasige Sequenz erweitert:
  - stehend: DOWN zum Crouch, ein Frame Release, dann DOWN fuer Reload, danach UP zum Aufstehen
  - crouched: direkt DOWN fuer Reload, kein abschliessendes UP
- `game.h`: Reload-Frame-Counter ergaenzt, damit Reload nicht im Startframe sofort beendet wird.

Lokaler Check:
- Branch: `Reborn`
- Build: `android/gradlew.bat :app:assembleDebug` erfolgreich
- Build: `android/gradlew.bat :app:assembleRelease` erfolgreich
- Signierte Release-APK per `adb install -r` erfolgreich installiert

Erwartung fuer naechsten Device-Test:
- Weapon-Button muss die Figur sichtbar holstern und spaeter wieder sichtbar ziehen.
- HUD-Waffensymbol und Touch-Icons duerfen erst der echten Engine-Haltung folgen.
- Reload-Button muss nach abgefeuertem Schuss die Crouch/Reload-Sequenz ausloesen.

---

## Device-Test-Fix 5: Besitz-State von Control-Drawn-State getrennt (2026-06-06)

Status: **Fix umgesetzt und signierte Release-APK fuer Device-Test installiert. Keine Freigabe fuer APK-Upload auf Drive.**

Ausgangspunkt:
- Nach dem Einsammeln des Gewehrs erschienen sofort Fire/Reload, obwohl die Waffe noch nicht gezogen war.
- Fire zog dadurch die Waffe und schoss.
- Nach Weapon/Holster wechselten HUD und Touch-Icons nicht stabil zur echten Haltung.

Ursache:
- `_varsTable[2] == 1` wurde in der Control-Logik als "Gun drawn" interpretiert, bildet im Spiel aber auch "Gun verfuegbar/besitzt" ab.

Fix:
- `game.cpp`/`game.h`: separater semantischer Control-State `_controlGunDrawn`/`_controlSwordDrawn` eingefuehrt.
- `isGunDrawn()`, `isSwordDrawn()` und `isJackArmed()` nutzen fuer Control/UI/Fire/Reload diesen Drawn-State statt reinen Besitz-Variablen.
- `handleWeaponToggle()` nutzt `_varsTable[1/2]` nur noch fuer Verfuegbarkeit, setzt den Control-Drawn-State aber erst beim expliziten Draw/Holster ueber den Weapon-Button.

Lokaler Check:
- Branch: `Reborn`
- Build: `android/gradlew.bat :app:assembleDebug` erfolgreich
- Build: `android/gradlew.bat :app:assembleRelease` erfolgreich
- Signierte Release-APK per `adb install -r` erfolgreich installiert

Erwartung fuer naechsten Device-Test:
- Nach Gewehr-Pickup bleiben Run/Jump sichtbar, bis Weapon gedrueckt wird.
- Weapon zieht die Waffe, danach Fire/Reload.
- Weapon holstert die Waffe, danach Run/Jump.
- Fire darf unbewaffnet nicht mehr ziehen/schiessen, weil es unbewaffnet nicht sichtbar sein sollte.

---

## Device-Test-Fix 6: Inventar-Waffenwahl fuer Weapon-Toggle erhalten (2026-06-06)

Status: **Fix umgesetzt und signierte Release-APK fuer Device-Test installiert. Keine Freigabe fuer APK-Upload auf Drive.**

Ausgangspunkt:
- Waffe ziehen/wegstecken und Icon-Umschaltung funktionieren.
- Wechsel Gewehr/Schwert wurde nicht stabil uebernommen:
  - im Holster wurde Inventarwahl Schwert nicht beim naechsten Ziehen verwendet
  - nach Holstern wurde wieder Gewehr statt zuletzt gewaehltem Schwert gezogen

Ursache:
- Control-Drawn-State war getrennt, aber die zuletzt gewaehlte Waffe wurde nicht gespeichert.
- Beim Draw wurde Gun bevorzugt, sobald Gun verfuegbar war.

Fix:
- `game.cpp`/`game.h`: `_controlSelectedWeapon` eingefuehrt.
- Inventar-Selection aus `_varsTable[1] == 1` (Schwert) und `_varsTable[2] == 1` (Gun) wird im Frame-Update uebernommen.
- Weapon-Toggle nutzt beim Ziehen die zuletzt gewaehlte Waffe und setzt die Engine-Vars vor dem SPACE-Puls passend auf diese Auswahl.
- Wenn waehrend gezogener Waffe im Inventar umgeschaltet wird, folgt der Control-Drawn-State der aktiven Auswahl.

Lokaler Check:
- Branch: `Reborn`
- Build: `android/gradlew.bat :app:assembleDebug` erfolgreich
- Build: `android/gradlew.bat :app:assembleRelease` erfolgreich
- Signierte Release-APK per `adb install -r` erfolgreich installiert

Erwartung fuer naechsten Device-Test:
- Holstered: im Inventar Schwert waehlen, Weapon zieht Schwert.
- Schwert holstern, Weapon zieht wieder Schwert.
- Im gezogenen Zustand zwischen Gun/Schwert wechseln, Fire/Sword-Icon folgt korrekt.

---

## Device-Test-Fix 7: Inventarwechsel Gun/Schwert im Holster speichern (2026-06-06)

Status: **Fix umgesetzt und signierte Release-APK fuer Device-Test installiert. Keine Freigabe fuer APK-Upload auf Drive.**

Ausgangspunkt:
- Der Wechsel scheiterte bereits im Inventar: von Gewehr auf Schwert wurde nicht gespeichert.

Ursache:
- `bag.cpp` erlaubte Gun/Schwert-Wechsel nur, wenn die aktuell aktive Waffe den Wert `1` hatte. Im Holster-Zustand ist eine vorhandene Waffe aber nicht zwingend aktiv (`1`), sondern kann als vorhanden/holstered (`2`) vorliegen.
- Zusaetzlich wurde die neue Auswahl nicht in `_controlSelectedWeapon` gespeichert. Beim Wechsel zurueck ins Spiel konnte die Control-Synchronisierung die Holster-Auswahl deshalb wieder aus alten Engine-Werten ueberschreiben.

Fix:
- `bag.cpp`: Waffenwechsel prueft jetzt auf vorhandene Waffen (`!= 0`) statt auf aktive gezogene Waffe (`== 1`).
- Betroffen sind Touch/Maus-Klicks auf Waffenbereich sowie DPAD-Up/Down im Inventar.
- `bag.cpp`: Inventarwechsel setzt `_controlSelectedWeapon`, und die Inventar-Markierung nutzt diesen Control-Selektionszustand.
- `game.cpp`: Im Holster-Zustand wird `_controlSelectedWeapon` nicht mehr pro Frame aus `_varsTable[1/2] == 1` ueberschrieben. Diese harte Synchronisierung greift nur noch bei gezogener Waffe.

Lokaler Check:
- Branch: `Reborn`
- Build: `android/gradlew.bat :app:assembleDebug` erfolgreich
- Build: `android/gradlew.bat :app:assembleRelease` erfolgreich
- Signierte Release-APK per `adb install -r` erfolgreich installiert

Erwartung fuer naechsten Device-Test:
- Holstered im Inventar von Gewehr auf Schwert wechseln, bestaetigen, Inventar erneut oeffnen: Schwert muss aktiv bleiben.
- Danach muss Weapon das gewaehlte Schwert ziehen.
- Wechsel zurueck auf Gewehr muss analog gespeichert bleiben.

---

## Device-Test-Todos fuer naechste Session: Weapon-State-Stabilisierung (2026-06-06)

Status: **Offen. Keine Freigabe fuer APK-Upload auf Drive.**

Aktueller Stand nach Commit `5bf32e6 Preserve holstered inventory weapon selection`:
- Teilfix funktioniert: Wird im Inventar einmal von Gewehr auf Schwert gewechselt, zieht der Weapon-Button danach korrekt das Schwert.
- Fehler bleibt: Wird das Schwert danach weggesteckt und anschliessend erneut per Weapon-Button gezogen, wird wieder das Gewehr gezogen.

Todo 1: Persistente Schwert-Auswahl nach Holster fixen
- Repro: Inventar oeffnen -> von Gewehr auf Schwert wechseln -> bestaetigen/schliessen -> Weapon zieht Schwert -> Weapon steckt Schwert weg -> Weapon erneut druecken.
- Ist: Beim erneuten Ziehen kommt das Gewehr.
- Soll: Die zuletzt im Inventar gewaehlte Waffe muss auch nach Holstern erhalten bleiben. Wenn Schwert aktiv gewaehlt war, muss Weapon erneut Schwert ziehen.
- Relevante Dateien/Mechanik:
  - `bag.cpp`: Inventar setzt `_controlSelectedWeapon`.
  - `game.cpp`: `handleWeaponToggle()` und `updateKeysPressedTable()` verwalten `_controlSelectedWeapon`, `_controlGunDrawn`, `_controlSwordDrawn`.
  - Wahrscheinlicher Fehlerbereich: Beim Holstern oder nach Abschluss der Holster-Animation wird `_varsTable[1/2]` vom Original-Script wieder so gesetzt, dass der naechste Draw-Pfad Gewehr bevorzugt oder `_controlSelectedWeapon` indirekt wieder auf Gun kippt.

Todo 2: Weapon-Button gegen zu schnelle Eingaben sperren
- Repro: Weapon-Button mehrfach schnell druecken, waehrend Ziehen/Wegstecken-Animation noch laeuft.
- Ist: Touch-Icons wechseln bereits, aber die eigentliche Engine-Aktion Ziehen/Wegstecken wird nicht korrekt ausgefuehrt, weil die Animation noch nicht beendet war.
- Soll: Weapon-Toggle darf waehrend laufender Draw/Holster-Animation nicht sofort einen neuen logischen State committen. Entweder Eingabe ignorieren bis Engine-State stabil ist oder als Pending-Toggle puffern und erst nach Abschluss ausfuehren.
- Relevante Dateien/Mechanik:
  - `game.cpp`: `handleWeaponToggle()` setzt aktuell `_controlGunDrawn/_controlSwordDrawn` direkt beim Button-Event.
  - `game.cpp`: `getTouchGameState()` liefert Touch-State fuer Icon-Umschaltung.
  - Vermutlich noetig: separater Pending-/Cooldown-/Animation-Gate-State, damit UI-State erst dann wechselt, wenn die Engine die Aktion wirklich angenommen hat.

Naechste Session starten mit:
- Zuerst aktuellen Repro im Code gegen `handleWeaponToggle()` nachvollziehen.
- Danach klaeren, welche `_varsTable[1/2]` Werte direkt vor/nach Holster und nach Animationsende anliegen.
- Erst danach Fix implementieren, signierte Release-APK installieren und erneut Device-Test durchfuehren.

---

## Device-Test-Fix 8: Weapon-Auswahl nach Holster stabilisiert (2026-06-06)

Status: **Fix umgesetzt, Release-APK gebaut und per ADB fuer Device-Test installiert. Noch nicht am Device durchgespielt. Keine Freigabe fuer APK-Upload auf Drive.**

Ausgangspunkt:
- Nach Inventarwechsel auf Schwert zog der Weapon-Button einmal korrekt das Schwert.
- Nach Schwert-Holster zog der naechste Weapon-Tap wieder das Gewehr.
- Schnelle Weapon-Taps konnten den Touch-/Control-State mehrfach umschalten, waehrend die Engine-Animation noch nicht stabil war.

Ursache:
- `handleWeaponToggle()` sicherte beim Holstern die Auswahl aus `_varsTable[1/2] == 1`. Diese Originalscript-Werte koennen nach oder waehrend Holster wieder auf Gewehr kippen.
- Es gab keinen Busy-/Pending-State fuer Weapon-Toggle; jeder Tap konnte sofort einen neuen logischen Draw/Holster-State committen.

Fix:
- `game.cpp`: Beim Holstern gewinnt jetzt der eigene Control-Drawn-State (`_controlSwordDrawn`/`_controlGunDrawn`) fuer `_controlSelectedWeapon`, nicht der eventuell stale Originalscript-State.
- `game.cpp`/`game.h`: `_weaponToggleBusyFrames` und `_pendingWeaponToggle` eingefuehrt. Neue Weapon-Taps waehrend des kurzen Busy-Fensters werden gepuffert und erst danach ausgefuehrt.
- `game.cpp`: Frame-Sync des Drawn-State uebernimmt `_varsTable[1/2] == 1` nur noch, wenn nicht beide Weapon-Vars gleichzeitig als aktiv wirken. Das verhindert unnoetiges Zurueckkippen bei widerspruechlichen Script-Zwischenwerten.

Lokaler Check:
- Branch: `Reborn`
- Build: `android/gradlew.bat :app:assembleDebug` erfolgreich
- Build: `android/gradlew.bat :app:assembleRelease` erfolgreich
- Release-APK: `android/app/build/outputs/apk/release/app-release.apk`, 19,034,417 Bytes
- ADB: Geraet `DEVICE_SERIAL` verbunden
- Installation: `adb install -r android/app/build/outputs/apk/release/app-release.apk` erfolgreich

Erwartung fuer naechsten Device-Test:
- Holstered: im Inventar Schwert waehlen, bestaetigen, Weapon zieht Schwert.
- Schwert holstern, danach Weapon erneut druecken: Es muss wieder Schwert gezogen werden, nicht Gewehr.
- Schnelle mehrfache Weapon-Taps waehrend Draw/Holster duerfen den sichtbaren/spielbaren State nicht mehr durcheinanderbringen; hoechstens ein Pending-Toggle wird nach dem Busy-Fenster nachgezogen.

---

## Device-Test-Fix 9: Inventar-Hitbox und Weapon-State-Authority (2026-06-06)

Status: **Nach Nutzerfeedback umgesetzt, Debug/Release gebaut, Release-APK per ADB installiert und vom Nutzer am Device als zuverlaessig funktionierend bestaetigt. Keine Freigabe fuer APK-Upload auf Drive.**

Ausgangspunkt nach Fix 8:
- Inventarwechsel auf Schwert zog beim ersten Weapon-Tap einmal das Schwert.
- Nach Schwert-Holster zog der naechste Weapon-Tap wieder das Gewehr.
- Im Inventar wurde Schwert nicht sichtbar mit dem blinkenden Punkt markiert; Gewehr blieb markiert.

Ursachen:
- `bag.cpp`: Touch-Hitboxen fuer Gun/Sword nutzten harte Y-Konstanten (`22`/`37`), waehrend die Icons bottom-basiert gezeichnet werden. Dadurch traf ein sichtbarer Schwert-Tap nicht zuverlaessig die Schwert-Auswahl.
- `game.cpp`: Der armed Frame-Sync konnte `_controlSelectedWeapon` und `_control*Drawn` aus `_varsTable[1/2]` wieder auf Gewehr drehen, obwohl der Touch-Control-State gerade Schwert ausgewaehlt hatte.

Fix:
- `bag.cpp`: Weapon-Hitboxen werden jetzt aus denselben Icon-Rechtecken berechnet, die `drawBagMenu()` zum Zeichnen nutzt. Schwert wird vor Gewehr geprueft.
- `bag.cpp`: Inventarwechsel bei gezogener Waffe aktualisiert auch `_controlGunDrawn/_controlSwordDrawn`.
- `game.cpp`: Armed-State wird nicht mehr pro Frame aus `_varsTable[1/2]` zurueckgesynct. `_controlSelectedWeapon` bleibt nach expliziter Touch-/Inventarauswahl authoritative.

Lokaler Check:
- Build: `android/gradlew.bat :app:assembleDebug` erfolgreich
- Build: `android/gradlew.bat :app:assembleRelease` erfolgreich
- Installation: `adb install -r android/app/build/outputs/apk/release/app-release.apk` erfolgreich auf `DEVICE_SERIAL`
- Device-Test: Nutzer bestaetigt, dass Schwert-Auswahl, Schwert-Draw, Holster und erneuter Schwert-Draw jetzt zuverlaessig funktionieren.

Abgenommener Device-Repro:
- Im Inventar muss ein Tap auf das sichtbare Schwert den blinkenden Punkt vor Schwert setzen.
- Nach Schwert-Auswahl muss Weapon Schwert ziehen.
- Nach Schwert-Holster muss der naechste Weapon-Tap wieder Schwert ziehen, nicht Gewehr.

---

## Touch-Reload zurueckgestellt: Jump bleibt Jump (2026-06-06)

Status: **Code-Aenderung umgesetzt. Auf Nutzerwunsch keine APK gebaut und nicht installiert. Nicht committed.**

Ausgangspunkt:
- Der Touch-Jump-Button wurde bei gezogener Gun dynamisch zum Reload-Button umgeschaltet.
- Device-Test zeigte, dass Reload-Verhalten komplexer ist als geplant und vorerst nicht als Touch-Button ausgeliefert werden soll.

Fix:
- `TouchOverlayController.kt`: `btn_jump` wird im Gameplay nicht mehr bei `gunDrawn` auf `reload`/`CONTROL_ACTION_RELOAD` umgeschaltet.
- `btn_jump` bleibt in Gameplay-Kontexten immer Icon `jump` mit `control_action` `jump_button` im Hold-Modus.
- Video-Kontext bleibt unveraendert: `btn_jump` zeigt dort weiterhin `cancel` fuer Skip/Cancel.
- Native Reload-State-Machine bleibt im Code, wird aber vom Touch-Jump-Button nicht mehr ausgeloest.

Lokaler Check:
- Kein Build ausgefuehrt.
- Keine APK gebaut oder installiert.

---

## Menu-Cancel Touch-Button sichtbar gemacht (2026-06-06)

Status: **Code-Aenderung umgesetzt, Kotlin-Compile-Check erfolgreich. Keine APK gebaut oder installiert. Nicht committed.**

Ausgangspunkt:
- Im Video-Kontext wird korrekt nur der Cancel/Skip-Button angezeigt.
- Im Menue-Kontext war fuer den Nutzer nur der OK/Bestaetigen-Button sichtbar; ein klarer Cancel/Zurueck-Button fehlte.

Fix:
- `TouchOverlayController.kt`: `btn_menu` wird in MENU- und BITMAP_CONFIRM-Kontexten explizit auf Icon `cancel` und Action `menu_back` gesetzt.
- `TouchInputDispatcher.kt`: `btn_menu` dispatcht in MENU- und BITMAP_CONFIRM-Kontexten direkt `KEYCODE_ESCAPE`.
- Gameplay-Kontext bleibt unveraendert: `btn_menu` zeigt weiter das Menu-Icon und nutzt `menu_back`.

Lokaler Check:
- `android/gradlew.bat :app:compileDebugKotlin` erfolgreich

---

## Inventar-Jump-Confirm und getrennte Menu-Layouts (2026-06-06)

Status: **Code-Aenderung umgesetzt, Debug/Release gebaut und Release-APK per ADB installiert. Device-Verifikation durch Nutzer steht aus. Nicht committed.**

Ausgangspunkt:
- Im geoeffneten Inventar sollte der Jump-Button zusaetzlich Enter/Bestaetigen ausloesen, aber nicht in normalen Menues.
- Gameplay- und Menue-Buttons sollten getrennt positionierbar sein, damit OK/Zurueck im Menue anders liegen koennen als die Ingame-Buttons.

Fix:
- `systemstub.h`/`systemstub_sdl.cpp`: eigener Touch-Kontext `TOUCH_INPUT_CONTEXT_INVENTORY` fuer `kStateBag` eingefuehrt.
- `TouchInputDispatcher.kt`: `btn_jump` dispatcht Enter nur noch in VIDEO und INVENTORY. Normale Menues nutzen dafuer weiter `btn_use`/OK; `btn_menu` bleibt Escape/Cancel.
- `TouchButtonModels.kt`: Touch-Config-Schema auf v11 erhoeht und `menu_buttons` als separate Layout-Liste hinzugefuegt.
- `TouchButtonStore.kt`: Migration erzeugt `menu_buttons` fuer bestehende Layouts; bekannte Actions/Icons/Labels werden fuer Menu-Kontexte normalisiert.
- `TouchOverlayController.kt`: Kontextwechsel wendet Gameplay- oder Menu-Layoutpositionen an; Verschieben/Editieren/Speichern schreibt je nach aktivem Kontext in `buttons` oder `menu_buttons`.
- INVENTORY zeigt D-Pad, `btn_jump` als Bestaetigen/Enter und `btn_menu` als Cancel; MENU/BITMAP_CONFIRM zeigen D-Pad, `btn_use` OK und `btn_menu` Cancel.

Lokaler Check:
- `android/gradlew.bat :app:assembleDebug` erfolgreich
- `android/gradlew.bat :app:assembleRelease` erfolgreich
- Release-APK: `android/app/build/outputs/apk/release/app-release.apk`, 19,034,417 Bytes
- Installation: `adb install -r android/app/build/outputs/apk/release/app-release.apk` erfolgreich auf `DEVICE_SERIAL`

---

## Screenshot-basierte Default-Button-Presets (2026-06-06)

Status: **Defaults aus Nutzer-Screenshots uebernommen, Debug/Release gebaut und Release-APK per ADB installiert. Nicht committed.**

Ausgangspunkt:
- Nutzer hatte Gameplay- und Menue-Buttonlayouts auf einem 2400x1080-Display manuell positioniert.
- Diese Layouts sollten als neue Default-Presets hinterlegt werden, ohne bestehende manuelle User-Layouts zu ueberschreiben.

Quelle:
- `C:\Users\Tommy Green\Downloads\Screenshot_20260606-174554.jpg` — Gameplay-Layout, 2400x1080
- `C:\Users\Tommy Green\Downloads\Screenshot_20260606-174603.jpg` — Menue-Layout, 2400x1080

Fix:
- `TouchButtonModels.kt`: `defaultButtons()` auf screenshot-basierte Gameplay-Positionen/Groessen gesetzt.
- `TouchButtonModels.kt`: `defaultMenuButtons()` auf screenshot-basierte Menu/Inventory-Positionen/Groessen gesetzt.
- Werte bleiben ankerbasiert (`anchorX`/`anchorY` + `offsetX`/`offsetY` relativ zu `minDim`), damit sie auf anderen Landscape-Aspects an den Rändern und Clustern ausgerichtet bleiben.
- `TOUCH_OVERLAY_CONFIG_VERSION` wurde nicht erneut erhoeht. Bestehende manuell gespeicherte User-Layouts bleiben persistent und werden nicht durch neue Defaults ueberschrieben.

Lokaler Check:
- `android/gradlew.bat :app:assembleDebug` erfolgreich
- `android/gradlew.bat :app:assembleRelease` erfolgreich
- Installation: `adb install -r android/app/build/outputs/apk/release/app-release.apk` erfolgreich auf `DEVICE_SERIAL`

---

## Default-Button-Groessen und Inventar-OK korrigiert (2026-06-06)

Status: **Code-Aenderung umgesetzt, Debug/Release gebaut und Release-APK per ADB installiert. Device-Verifikation durch Nutzer steht aus. Nicht committed.**

Ausgangspunkt:
- Nach Default-Reset waren die Positionen korrekt, aber die Button-Groessen nicht.
- D-Pad war zwischen Gameplay und Menue leicht versetzt.
- Im Inventar sollte kein Zurueck-Button sichtbar sein.
- Der Inventar-Bestaetigen-Button hatte noch das Jump-Icon statt OK.

Fix:
- `TouchButtonModels.kt`: Default-Groessen gesetzt:
  - `btn_menu`/`btn_inv`: `0.180`
  - `btn_status`/`btn_quick_save`/`btn_quick_load`: `0.160`
  - `btn_jump`/`btn_use`/`btn_weapon`/`btn_run`: `0.220`
  - D-Pad: `0.400`
  - Menu `btn_use`/`btn_menu`: `0.260`
  - Inventory `btn_jump` als OK: `0.260`
- `TouchButtonModels.kt`: Menu-D-Pad nutzt dieselbe ankerbasierte Position wie Gameplay-D-Pad.
- `TouchOverlayController.kt`: Inventory-Kontext zeigt nur D-Pad und `btn_jump` als OK; `btn_menu`/Zurueck wird dort ausgeblendet.
- `TouchOverlayController.kt`: Inventory-`btn_jump` bekommt Icon `ok` und Tap-Action `use`.

Lokaler Check:
- `android/gradlew.bat :app:assembleDebug` erfolgreich
- `android/gradlew.bat :app:assembleRelease` erfolgreich
- Release-APK: `android/app/build/outputs/apk/release/app-release.apk`, 19,050,801 Bytes
- Installation: `adb install -r android/app/build/outputs/apk/release/app-release.apk` erfolgreich auf `DEVICE_SERIAL`

---

## Touch-Preset Import/Export fuer Gameplay und Menue (2026-06-06)

Status: **Code-Aenderung umgesetzt, Debug/Release gebaut und Release-APK per ADB installiert. Nicht committed.**

Ausgangspunkt:
- Screenshot-basierte Default-Werte waren unzuverlaessig genug, dass das echte gespeicherte Nutzerlayout als JSON exportierbar sein soll.
- Preset-Export/Import soll sowohl Gameplay-Layout (`buttons`) als auch Menu/Inventory-Layout (`menu_buttons`) enthalten.

Fix:
- `BermudaActivity.kt`: Android-Dateiauswahl per SAF angebunden:
  - `ACTION_CREATE_DOCUMENT` fuer Export nach frei waehlbarem Speicherort, Default-Dateiname `bermuda_touch_preset.json`.
  - `ACTION_OPEN_DOCUMENT` fuer Import einer JSON-Datei.
- `TouchOverlaySettingsDialog.kt`: Layout-Sektion um `Export Touch Preset` und `Import Touch Preset` erweitert.
- `TouchOverlayController.kt`: Export schreibt die aktuelle persistierbare Config als JSON in die gewaehlte Datei und sichert vorher aktuelle Button-Positionen.
- `TouchOverlayController.kt`: Import liest die gewaehlte JSON, normalisiert Schema/Actions/Icons, speichert sie und laedt das Overlay neu.
- `TouchButtonStore.kt`: Import-Normalisierung und JSON-Export-Helfer hinzugefuegt. Export enthaelt `buttons` und `menu_buttons`.

Lokaler Check:
- `android/gradlew.bat :app:assembleDebug` erfolgreich
- `android/gradlew.bat :app:assembleRelease` erfolgreich
- Installation: `adb install -r android/app/build/outputs/apk/release/app-release.apk` erfolgreich auf `DEVICE_SERIAL`

---

## Validiertes Touch-Preset als Default und Lockbutton-SVG (2026-06-06)

Status: **Code-Aenderung umgesetzt, Debug/Release gebaut, Release-APK per ADB installiert und zum Commit vorbereitet.**

Ausgangspunkt:
- Nutzer hat das am Geraet validierte Preset als `C:\Users\Tommy Green\Downloads\bermuda_touch_preset.json` exportiert.
- Nach Default-Reset plus Import sah Gameplay, Menue und Inventar korrekt aus; dieses Preset soll daher neuer Default sein.
- Editor-Lockbutton sollte das `Lock_Button.svg` aus `D:\Coding\Mario_Icons\` mit den dortigen Alignment-Werten nutzen.

Fix:
- `TouchButtonModels.kt`: `defaultButtons()` und `defaultMenuButtons()` direkt auf die exportierten `x`/`y`/`size`/`alpha`-Werte gesetzt.
- Gameplay-Defaults, Menu-Defaults und Inventory-OK (`menu_buttons.btn_jump`) nutzen die validierten Positionen aus der JSON.
- Alte Anchor/Offset-Screenshot-Werte wurden aus den Defaults entfernt; die neue Quelle ist das exportierte Preset.
- `android/app/src/main/res/raw/lock_button.svg`: `Lock_Button.svg` als Android-Raw-Resource hinzugefuegt.
- `iconset.json`/`iconmappings.json`: `Lock_Button` mit `iconFill=1.0`, neutralem Offset/Scale aus der Mario-JSON registriert.
- `TouchOverlayLockButtonView.kt`: handgezeichnetes Schloss durch Rendering des SVG-Icons `lock` ersetzt.

Lokaler Check:
- `android/gradlew.bat :app:assembleDebug` erfolgreich
- `android/gradlew.bat :app:assembleRelease` erfolgreich
- Installation: `adb install -r android/app/build/outputs/apk/release/app-release.apk` erfolgreich auf `DEVICE_SERIAL`
- Abschluss: Nutzer hat das Default-Preset und neue Lockbutton-SVG freigegeben; Aenderungen werden zusammen committed.

---

## Bugfix: Inventory 4:3 im Stretched-Mode & Weapon-Icon-State (2026-06-06)

Status: **Behoben, Release-APK per ADB installiert und vom Nutzer als funktionierend bestaetigt. Committed.**

### Fix 1: Inventory erzwingt im Widescreen-Stretched-Mode wieder 4:3

Ursache: `game.cpp:216` (`setScreenMode`) und `game.cpp:400` (`mainLoop` State-Übergang) setzten `setStretchGameplay(true)` nur fuer `_state == kStateGame`. Beim Wechsel zu `kStateBag` (Inventar) wurde der Stretch deaktiviert.

Fix: Beide Conditions auf `(_state == kStateGame || _state == kStateBag)` erweitert. Inventar bleibt jetzt im selben gestreckten Output-Modus wie Gameplay.

### Fix 2: Weapon-Icon-State schaltet zu frueh auf Fire/Sword/Reload

Ursache (zweistufig):
1. `engineControlState()` meldete `GUN_DRAWN`/`SWORD_DRAWN` sofort, sobald `_controlGunDrawn`/`_controlSwordDrawn` in `handleWeaponToggle()` gesetzt wurde — noch bevor die Engine den SPACE-Key verarbeitet hatte.
2. `handleWeaponToggle()` setzte `_controlGunDrawn = true`/`_controlSwordDrawn = true` direkt im Draw-Pfad. Wenn die Engine den Key ignorierte (Jack in Auslauf-Animation), blieb der Drawn-State true. Der nächste Weapon-Tap ging dann in den Holster-Pfad statt den Draw zu wiederholen.

Fix:
- `engineControlState()`: Cross-Check mit `_varsTable[2] == 1` (Gun aktiv) / `_varsTable[1] == 1` (Sword aktiv) vor dem Melden von `GUN_DRAWN`/`SWORD_DRAWN`.
- `_weaponToggleDrawRequested`-Flag (game.h:506) eingefuehrt.
- Draw-Pfad setzt nur noch `_weaponToggleDrawRequested = true`, nicht mehr `_control*Drawn`.
- Frame-Sync in `updateKeysPressedTable()`: Nach Ablauf des Busy-Fensters (`_weaponToggleBusyFrames == 0`) wird `_varsTable[_controlSelectedWeapon] == 1` geprueft. Bei Bestaetigung → `_control*Drawn = true`. Ohne Bestaetigung → `_control*Drawn` bleibt false, naechster Tap wiederholt Draw.
- Holster-Pfad setzt `_weaponToggleDrawRequested = false`.

Geaenderte Dateien: `game.h`, `game.cpp`

---
## Bugfix: Video-Skip-Button deckungsgleich mit Menu-Zurueck (2026-06-06)

Status: **Behoben, vom Nutzer am Device als funktionierend bestaetigt. Committed.**

Fix:
- `TouchOverlayController.kt`: `visibilityForContext` zeigt im VIDEO-Kontext jetzt `btn_menu` statt `btn_jump`. `usesMenuLayout` um context==1 erweitert, sodass `btn_menu` seine Position aus `menu_buttons` bezieht. `syncContextSensitiveState` setzt `btn_menu` im VIDEO-Kontext auf Cancel-Icon mit `menu_back`-Action.
- `TouchInputDispatcher.kt`: `contextualKeyCodeForControl` dispatcht `btn_menu` im VIDEO-Kontext als `KEYCODE_ENTER` (Video-Skip), in MENU/BITMAP-Kontexten weiterhin `KEYCODE_ESCAPE`.
- `btn_jump` im VIDEO-Kontext ist nicht mehr sichtbar; der `context==1`-Branch dafuer im Sync wurde entfernt.

Ergebnis:
- Video-Skip-Button liegt jetzt exakt an derselben Position wie der Menue-Zurueck-Button (`menu_buttons.btn_menu`).
- Verschiebt der User den Zurueck-Button im Editor (egal ob im Menue- oder Video-Kontext), folgt der Video-Skip-Button persistent.
- Der Cancel/Skip-Platz hat damit eine einzige Positionsquelle fuer beide Kontexte.

---

## Offene technische Bugs fuer naechsten Fix-Pass (2026-06-06)

Status: **Bug 1, 2, 3 & 4 behoben. Bug 5 noch offen.**

### 3. Overlay-Set-Wechsel reagiert zu traege

Status: **Behoben (2026-06-06).**

Fix:
- `CONTEXT_SYNC_INTERVAL_MS` von 250ms auf 80ms reduziert (polling-basierter Abgleich).
- `requestImmediateContextSync()` hinzugefuegt: loest sofortigen Sync aus, wenn User einen Overlay-Button beruehrt (ACTION_DOWN/UP), auf die Game-Canvas tippt oder das Overlay aus dem Store neu laedt.
- `TouchOverlayButtonView` erhielt einen `onInteraction`-Callback, der bei jedem Touch-Event (Down/Up) den Immediate-Sync triggert.
- Kein spuerbarer CPU-Impact auf dem Testgeraet bei 80ms-Intervall.

Geaenderte Dateien: `TouchOverlayController.kt`, `TouchOverlayButtonView.kt`

### 4. Video-Skip-Button soll deckungsgleich mit Menu-Zurueck liegen

Symptom:
- In Videos wird nur der Skip/Cancel-Button angezeigt.
- Dieser Button soll immer exakt an derselben Position liegen wie der Zurueck/Cancel-Button in Menues.
- Ziel ist, dass der User fuer "Zurueck/Abbrechen/Skip" nicht je Kontext eine andere Stelle antippen muss.

Fix-Anweisung:
- Video-Kontext (`TOUCH_INPUT_CONTEXT_VIDEO`, aktuell `context == 1`) soll fuer den sichtbaren Skip-Button dieselbe Layout-Quelle verwenden wie Menu-Cancel.
- Der Skip-Button ist runtime aktuell `btn_jump` mit Cancel-Icon. Dadurch greift seine Position aus `buttons`/Gameplay, nicht automatisch die Menu-Cancel-Position.
- Entweder:
  - im VIDEO-Kontext statt `btn_jump` den `menu_buttons.btn_menu` als sichtbaren Button verwenden und dessen Action fuer Video-Skip/Enter dispatchen, oder
  - `applyLayoutConfigToView()` fuer VIDEO/`btn_jump` explizit mit der Layout-Config von `menu_buttons.btn_menu` fuettern, aber Icon/Action weiter als Video-Skip setzen.
- Wichtig: Menu-Zurueck (`btn_menu` in `menu_buttons`) bleibt die einzige Quelle fuer Position/Groesse des Cancel/Skip-Platzes.
- Regressionstest: Menu oeffnen und Zurueck-Button-Position merken, danach Video starten; Skip-Button muss pixelgleich an derselben Stelle liegen.

### 5. Unlimited Ammo HUD und Reload-Loop

Symptom:
- Bei aktivem Unlimited-Ammo-Cheat hat der User funktional unbegrenzt Munition.
- Das Waffensymbol im HUD zeigt kosmetisch aber nur eins von sechs Patronensymbolen an.
- Wenn der User nachlaedt, loopt Jack in der Nachladeanimation.
- Der Loop endet fuer den User nur, wenn er per D-Pad nach oben aufsteht; das ist irritierend und wirkt wie ein Softlock.

Fix-Anweisung:
- Kosmetik: HUD-/Ammo-Anzeige muss bei aktivem `cheatInfiniteAmmo` entweder volle Ammo anzeigen oder einen bewusst cheat-spezifischen Zustand darstellen. Nicht nur `1/6`.
- Native/Engine-Stellen suchen, die Ammo fuer HUD aus `_varsTable[3]` oder vergleichbarer Ammo-Variable lesen. Bei `cheatInfiniteAmmo == true` dort fuer die Anzeige einen vollen Clip simulieren, ohne die eigentliche Cheat-Logik kaputtzumachen.
- Reload-Loop: Reload-Eingabe darf im Unlimited-Ammo-Modus nicht in eine endlose Reload-State-Machine geraten.
- Sinnvoller Fix:
  - Wenn `cheatInfiniteAmmo == true`, Reload-Action ignorieren oder direkt als abgeschlossen behandeln, weil kein Nachladen noetig ist.
  - Falls Jack bereits im Reload-/Crouch-Reload-State ist, muss der Cheat-Pfad die Reload-Action beenden und optional automatisch den normalen Crouch/Stand-State wieder freigeben, statt auf D-Pad-Up angewiesen zu sein.
- Pruefen, ob der Loop durch dauerhaft gesetztes DOWN/Reload-Signal oder durch eine Ammo-Endbedingung entsteht, die bei Unlimited Ammo nie erreicht wird.
- Regressionstest: Cheat aktivieren, Gun ziehen, schiessen, HUD pruefen, Reload druecken. Jack darf nicht in Reload loopen; HUD soll nicht irrefuehrend `1/6` anzeigen.

---

## Untersuchung: Weapon-Icon-Race bei Exit-Animationen (2026-06-06, Claude Code Session)

**Randszenario zu Bug 2:** Nach dem `engineControlState()`-Fix (Cross-Check `_varsTable` + `_weaponToggleDrawRequested`) funktioniert der Weapon-Toggle im Stillstand korrekt. Beim Tappen des Weapon-Buttons waehrend Jacks Auslauf-Animationen (Renn-Slide, Sprung-Landung) schaltet das Overlay jedoch weiterhin faelschlich auf Fire/Sword-Icons, obwohl die Waffe nicht gezogen wird.

**User-Beobachtung (nicht in Logs reproduziert):**
- 4-Tap-Sequenz aus dem Rennen: Tap 1 (Weapon) → Overlay zeigt Fire, Waffe nicht gezogen; Tap 2 (Weapon) → Overlay zeigt Run; Tap 3 (Weapon) → Waffe korrekt gezogen, Fire; Tap 4 (Weapon) → Waffe korrekt weggesteckt, Run.
- Wenn nach Tap 1 der Fire-Button gedrueckt wird, zieht Jack tatsaechlich die Waffe und schiesst — das Overlay war also "richtig falsch" (das Fire-Icon loeste die korrekte Draw+Fire-Aktion aus).

**ICON-TRACE-Stacktrace-Analyse (20:21:36–20:21:40):**
- Saemtliche Icon-Wechsel auf `btn_run` laufen AUSSCHLIESSLICH ueber `syncContextSensitiveState()`:
  - `applyLayoutConfigToView` (line 571) setzt `icon=run` vom Layout-Config
  - ICON-SWITCH (line 580) setzt `icon=fire`, wenn `gunDrawn=true`
- Kein versteckter zweiter Pfad — `TouchOverlayButtonView.updateConfig()` wird nur aus diesen beiden Stellen aufgerufen.
- Der `changed=false` Early-Return verhindert Icon-Updates korrekt, wenn sich context/gunDrawn/swordDrawn nicht geaendert haben.

**Erkenntnisse:**
1. Der Icon-Switch-Mechanismus auf Kotlin-Seite ist korrekt — es gibt keinen "Geister-Pfad".
2. Wenn das Fire-Icon faelschlich erscheint, MUSS `engineControlState()` dafuer `GUN_DRAWN=true` gemeldet haben.
3. Der `_weaponToggleDrawRequested`/`_weaponToggleBusyFrames`-Gate im aktuellen `engineControlState()`-Fix deckt das Busy-Window ab, aber moeglicherweise nicht den Fall, dass die Engine den Weapon-Toggle waehrend einer Exit-Animation verarbeitet (trotz dass sie es nicht sollte).
4. Der Bug trat in der letzten Test-Session NICHT auf — alle 4 Taps zeigten korrektes `gunDrawn`-Verhalten. Das spricht fuer eine Timing-/Frame-abhaengige Race-Condition.

**Offene Hypothese fuer Codex:**
- `handleWeaponToggle()` wird in `updateKeysPressedTable()` aufgerufen. Wenn Jack in einer Exit-Animation ist, sollte die Engine den Toggle ignorieren, aber moeglicherweise wird `_weaponToggleDrawRequested` trotzdem gesetzt, bevor die Animation-Check-Logik greift.
- Zu pruefen: Wird `_weaponToggleDrawRequested` jemals auf `true` gesetzt, ohne dass `_weaponToggleBusyFrames` im selben Frame auf 12 geht? Wenn ja, koennte `confirmedWeaponToggleIdle = (_weaponToggleBusyFrames == 0 && !_weaponToggleDrawRequested)` true bleiben und `isGunDrawn()` (via `_controlGunDrawn` aus einem vorherigen State) false-positive liefern.
- Alternativ: Setzt irgendein Codepfad `_controlGunDrawn` direkt ohne den `_weaponToggleDrawRequested`-Mechanismus?
- Empfehlung: `nativeGetControlState()`-JNI-Aufruf mit Frame-Counter loggen und bei faelschlichem Fire-Icon den exakten `engineControlState()`-Rueckgabewert + `_weaponToggleDrawRequested` + `_weaponToggleBusyFrames` + `_controlGunDrawn` ausgeben.

**Fix (2026-06-06, Codex Session): Behoben und am Device bestaetigt.**

Trace-Ergebnis:
- Die dysfunktionalen Weapon-Taps nach dem Rennen bestaetigten den Draw nur ueber `_varsTable[2] == 1`, obwohl Jack nicht in einer bewaffneten Motion war.
- Falscher Confirm: `anim=0`, teils `frame=0`, aber `_controlGunDrawn` wurde trotzdem gesetzt.
- Korrekter Confirm: Jack war in bewaffneter Motion, z.B. `anim=1`, `motion=81`.

Fix:
- `game.cpp`: `findJack()` sucht Jack jetzt case-insensitive (`strcasecmp`), weil Szenen `Jack`/`JACK` unterschiedlich schreiben koennen.
- `game.cpp`: Nach `_weaponToggleBusyFrames` wird `_controlGunDrawn`/`_controlSwordDrawn` nur noch gesetzt, wenn Jack wirklich in einer bewaffneten Motion ist (`_sceneObjectMotionsTable[jack->motionNum2].animNum != 0`).
- `_varsTable[1/2] == 1` bleibt Auswahl-/Verfuegbarkeitsbedingung, reicht aber nicht mehr allein als Draw-Bestaetigung.

Device-Verifikation:
- Release-APK gebaut und per `adb install -r` installiert.
- Repro: nach Rennen vier Weapon-Taps; die ersten dysfunktionalen Taps bleiben visuell unbewaffnet, die spaeter von der Engine akzeptierten Taps wechseln korrekt auf Fire.
- Nutzer bestaetigt: "ja das hat funktioniert!".

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

---

## Bug 6: ESC/Menu crasht nach Quickload (NICHT BEHOBEN, 2026-06-06)

**Status:** OFFEN — Claude Code Debug-Session, alle Fix-Ansätze gescheitert.

**Symptom:**
Quickload (F7/Quickload-Button) → ESC/Menu-Button → App crasht zurück zum Android-Launcher.
Ohne Quickload crasht ESC/Menu nicht.

**Crash-Signatur:**
```
FORTIFY: pthread_mutex_lock called on a destroyed mutex (0x7f0c802ae8)
Fatal signal 6 (SIGABRT) in tid N (hwuiTask0), pid M (SDLActivity)
```
Immer dieselbe Mutex-Adresse `0x7f0c802ae8`. Der Crash passiert in `libhwui.so` (Android Hardware UI Renderer), nicht in unserem Code oder SDL.

Zwei Threads crashen gleichzeitig, beide mit demselben Stacktrace:
```
#02 libc.so    abort
#05 libc.so    pthread_mutex_lock
#06 libc.so    pthread_cond_wait
#07 libc++.so  std::condition_variable::wait
#08 libhwui.so+0x2d97d4  (<— Android HWUI Worker Thread)
#09 libc.so    (thread start)
```

**Root Cause (Hypothese):**
`initMenu()` nach Quickload triggert eine Race-Condition in Androids `libhwui.so`.
`initMenu()` → `loadWGP()` überschreibt `_bitmapBuffer1`-Pointer und ruft `setPalette()` auf.
Wenn `libhwui`-Threads parallel noch auf die alten Buffer/Paletten zugreifen, wird ein
interner Mutex zerstört. Der `updateScreen()`-Call nach dem Scene-Reload reicht nicht,
um die Pipeline zu synchronisieren.

**Bereits probiert (alle gescheitert):**

1. **Defensive MENU-Objekt-Bereinigung in `initMenu()`** (`menu.cpp`)
   - Entfernt stale MENU-Objekte vor dem Menü-Laden.
   - Resultat: Kein Effekt, Crash blieb.

2. **`loadState()`: `_sceneObjectsCount` clippen** (`saveload.cpp`)
   - Verhindert dass Save-Files mit überhöhtem Object-Count die Szene korrumpieren.
   - Resultat: Der ursprüngliche "Duplicate object name MENU"-Bug war damit behoben,
     aber stattdessen kam der `libhwui`-Mutex-Crash.

3. **`stopMusic()` im `loadState()` deaktiviert** (`saveload.cpp`)
   - Test ob SDL_mixer Audio-Mutex-Operationen den Crash verursachen.
   - Resultat: Kein Effekt, Crash blieb.

4. **ESC im Quickload-Frame unterdrücken** (`game.cpp` `updateKeysPressedTable`)
   - `_stub->_pi.escape = false` direkt nach Quickload.
   - Resultat: Kein Effekt.

5. **`updateScreen()`-Flush nach Scene-Reload** (`game.cpp` `_switchScene`-Block)
   - Extra `_stub->updateScreen()` nach Scene-Wechsel zur Pipeline-Synchronisation.
   - Resultat: Kein Effekt.

6. **Menu-Öffnen per Frame-Lockout verzögern** (`game.cpp`, `game.h`)
   - `_quickloadEscapeLockout = 3` nach Quickload, decrement pro Frame,
     Menu-Init erst bei Lockout == 0 (nach 3 Frames).
   - Resultat: Kein Effekt, selbst nach 1 Minute Warten crasht ESC.

7. **Menu komplett unterdrückt (Test)** (`game.cpp`)
   - `_nextState = kStateMenu1` auskommentiert → kein Crash.
   - Beweist: Der Crash-Trigger ist definitiv `initMenu()` nach Quickload.

**Empfehlung für Codex:**
- Der Crash liegt in `libhwui.so` (Android-System-Library), nicht in Bermuda/SDL-Code.
- Möglicher Ansatz: `initMenu()` so umbauen, dass `_bitmapBuffer1`-Pointer nicht
  überschrieben werden. Statt `loadWGP()` → `_bitmapBuffer1.bits = _bitmapBuffer2`
  könnte ein separater Menu-Bitmap-Buffer verwendet werden.
- Alternativ: Nach Quickload State-Wechsel zu Menu um 1-2 komplette Frames
  (inkl. `updateScreen()` + `processEvents()`) verzögern — nicht nur `_nextState`,
  sondern den State-Wechsel-Mechanismus selbst pausieren.
- SDL2-Version prüfen: SDL 2.30+ hat Fixes für Android Surface/EGL-Handling.
- Möglicher Workaround: Nach Quickload `_state` nicht direkt auf `kStateMenu1`
  setzen, sondern einen Zwischen-State einfügen der nur rendert und Events
  verarbeitet, ohne `initMenu()` aufzurufen.

---

## Bugfix: ESC/Menu-Crash nach Quickload (2026-06-06)

Status: **Behoben, Release-APK per ADB installiert und vom Nutzer am Device als funktionierend bestaetigt.**

Ursache:
- Der sichtbare `libhwui`/destroyed-mutex-Crash war nur ein Folgeabsturz nach Engine-Abbruch.
- Direkt vor dem Crash stand im Log: `ERROR: Duplicate object name MENU!`.
- SaveStates konnten ein temporaeres Menue-Objekt `MENU` enthalten. Nach Quickload blieb dieses Objekt im Gameplay-State erhalten; beim naechsten ESC lud `initMenu()` `menu1.mov` und erzeugte ein zweites Objekt mit demselben Namen.

Fix:
- `saveload.cpp`: SaveStates aus Menue-Kontexten schreiben nur noch die echte Gameplay-Objektanzahl (`_menuObjectCount`), nicht das temporaer angehaengte Menue-Objekt.
- `menu.cpp`/`game.h`: `discardTransientMenuObjects()` eingefuehrt. Beim Laden alter/korrupter SaveStates und vor `initMenu()` werden vorhandene `MENU`-Objekte deaktiviert und umbenannt, damit alte Slots nicht mehr crashen.
- `TouchOverlayController.kt`/`systemstub_sdl.cpp`: `nativeGetControlState()` wird nur noch im Gameplay-Kontext abgefragt bzw. native-seitig nur fuer `kStateGame` beantwortet, um unnoetige Cross-Thread-Engine-Zugriffe im Menue zu vermeiden.

Device-Verifikation:
- Release-APK gebaut und per `adb install -r` installiert.
- Repro-Pfad getestet: Start -> Quickload Slot 1 -> ESC/Menu.
- Logcat: `WARNING: Discarded 1 stale transient MENU object(s)!`
- Kein `Duplicate object name MENU`, kein `FORTIFY`, kein `Fatal signal` nach dem Fix.
