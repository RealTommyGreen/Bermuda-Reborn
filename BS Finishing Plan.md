# Abschlussplan Bermuda Syndrome Android Port

## Summary
- Arbeitsordner/Repo fuer alle Phasen: `D:\Coding\BS Android`.
- Ziel: Public-APK ohne Bermuda-Spielassets, mit Erststart-Importer, nativer MIDI-Musik, touchfaehigem Inventar und erweitertem Zahnrad-Menue.
- Default-Entscheidungen: User waehlt nur den Spielordner; MIDI laeuft nativ ueber bundled freie GM-SoundFont; Cheats v1 = God Mode/No Hit, Infinite Ammo, All Weapons.
- Bildmodus: Stretch gilt nur fuer aktives Gameplay. Menues, Inventar/Dialoge und Videos bleiben immer im originalen 4:3-Format.

## Claude Workflow Rules
- Jede Phase ist ein eigener Arbeitsblock. Nach Abschluss einer Phase stoppt Claude vollstaendig und wartet auf neue Anweisung.
- Nach jeder Phase muss Claude die Ergebnisse sorgfaeltig ueberpruefen: Diff lesen, relevante Tests/Builds ausfuehren, Laufzeitrisiken notieren.
- Danach aktualisiert Claude:
  - den technischen Plan, falls sich Entscheidungen oder Umsetzungdetails geaendert haben,
  - `ANDROID_PORT_LOG.md` mit konkreten Aenderungen, Tests und offenen Risiken,
  - den Worktree so, dass er in einem nachvollziehbaren, reviewbaren Zustand ist.
- Claude darf nicht direkt mit der naechsten Phase weitermachen, auch wenn sie naheliegt.
- Keine APK wird waehrend einzelner Phasen in Drive hochgeladen.
- Eine APK wird erst nach Abschluss des gesamten Plans, nach Codex-Endabnahme und ausdruecklicher finaler Freigabe ins Drive geladen.
- Codex uebernimmt am Ende die Endabnahme: finaler Diff-Review, Build-/Smoke-Test-Check, Risikoanalyse und Release-Freigabeempfehlung.

## Implementation Changes

### Phase 1: Asset-Ausgliederung und Erststart-Importer ✅ (2026-05-19)
- Neue `BermudaLauncherActivity` als MAIN/LAUNCHER; `BermudaActivity` bleibt SDL-Spielactivity.
- Launcher prueft `filesDir/imported_game/BERMUDA/.import_manifest.json`. Wenn valide: Spiel starten. Wenn nicht: Import-UI anzeigen.
- Import-UI startet `ACTION_OPEN_DOCUMENT_TREE`, nimmt persistente Leseberechtigung und validiert: `BERMUDA.SPR`, `BERMUDA.WGP`, `SCN/-01.SCN`, `MIDI/TITLE.MID`.
- Importer kopiert rekursiv aus SAF nach `filesDir/imported_game/BERMUDA`, schreibt Manifest mit Quell-URI, Importzeit, Dateianzahl und Validierungsstatus.
- `BermudaActivity.getArguments()` entfernt `AssetExtractor`; nutzt `filesDir/imported_game/BERMUDA`, `filesDir/saves`, `--musicpath=<imported>/MIDI`.
- Public-Build enthaelt keine `android/app/src/main/assets/BERMUDA` Game-Daten. Erlaubt bleiben App-eigene Assets wie `soundfont/default.sf2` und Lizenzen.

**Implementiert:**
- `SafImporter.kt` — SAF-Importer mit `validateSource()`, `import()`, `isImportValid()`, JSON-Manifest
- `BermudaLauncherActivity.kt` — Import-UI (LinearLayout), `ACTION_OPEN_DOCUMENT_TREE`, persistente URI-Permission, Hintergrund-Import-Thread
- `BermudaActivity.kt` — AssetExtractor entfernt, Pfade auf `SafImporter.IMPORT_DIR`
- `AndroidManifest.xml` — LauncherActivity = MAIN/LAUNCHER, Game-Activity exported ohne Launcher
- `app/build.gradle.kts` — `androidx.documentfile:documentfile:1.0.1` hinzugefuegt
- Assets: `android/app/src/main/assets/BERMUDA/` komplett entfernt

**Build:** `assembleDebug` SUCCESSFUL, APK ~15 MB (von ~151 MB), 3 ABIs, 0 Warnings
**Risiko:** Kein Geratetest (kein ADB-Geraet verbunden)

### Phase 2: Native MIDI Playback ✅ (2026-05-19)
- TinySoundFont/TinyMidiLoader als vendored Third-Party-Code einbinden, inklusive MIT-Lizenzdatei.
- Freie bundled GM-SoundFont unter `android/app/src/main/assets/soundfont/default.sf2` ablegen und beim Start nach `filesDir/soundfont/default.sf2` kopieren.
- Neues Android-Argument `--soundfont=<path>` ergaenzen und an Game/Mixer weiterreichen.
- `Game::playMusic()` versucht zuerst `trackXX.ogg`; wenn nicht vorhanden, wird die originale MIDI-Datei aus `MIDI/*.MID` nativ abgespielt.
- `mixer_soft.cpp` um `MixerChannel_Midi` erweitern: MIDI vollstaendig laden, TSF mit SoundFont initialisieren, TML-Events im Audio-Callback zeitbasiert abspielen, ohne Loop.
- Case-insensitive MIDI-Aufloesung sicherstellen und `telquard.mid` auf `TELQUAD.MID` abfangen.

**Implementiert:**
- `android/app/src/main/jni/third_party/tsf.h` (TinySoundFont v0.9, MIT), `tml.h` (TinyMidiLoader v0.7, ZLIB)
- `android/app/src/main/assets/soundfont/default.sf2` (TimGM6mb.sf2, 5.7 MB)
- `MixerChannel_Midi` in mixer_soft.cpp: TSF/TML-Integration, `load()`, `read()` mit `tsf_render_short()`, kein Loop
- `MixerSoftware` erweitert um `tsf *_tsf`, `setSoundFont()`, MIDI-Fallback in `playMusic()`
- `Mixer`-Interface: `virtual void setSoundFont(const char *path)`
- `Game::playMusic()` MIDI-Fallback: case-insensitive 3-Fall-Try (Original → .MID → UPPERCASE)
- `game.h`/`game.cpp`: `_soundfontPath`, Konstruktor erweitert, `#include <ctype.h>` fuer `toupper()`
- `android_main.cpp`: `--soundfont=` Parsing
- `BermudaActivity.kt`: SoundFont-Kopie aus Assets → filesDir, `--soundfont` Argument
- `CMakeLists.txt`: `BERMUDA_TSF` Define, `third_party/` Include-Pfad
- `assets/BERMUDA/MUSIC/track*.ogg` entfernt (MIDI ersetzt OGG)

**Build:** `assembleRelease` SUCCESSFUL, APK ~15 MB, 3 ABIs, 0 Warnings
**Build-Fixes (Codex):** Doppeltes `//` und stray `}` in playMusic() MIDI-Fallback korrigiert
**Risiko:** MIDI-Playback nicht auf Geraet getestet (kein ADB-Geraet verbunden)

### Phase 3: Touch-Inventar und Zahnrad-Menue ✅ (2026-05-19)
- Direkte Taps auf Spielflaeche liefern absolute Touch-Koordinaten an SDL; keine Workarounds ueber relative Overlay-Mausbuttons.
- `SystemStub_SDL::updateMousePosition()` mappt Touchpositionen auf 640x480 unter Beachtung des aktuellen Render-Rechtecks.
- Inventar-Taps auf Slots, Aktionsflaechen und Waffenflaechen in `bag.cpp` pruefen und korrigieren.
- `TouchOverlayEditDialog` verliert die per-Button-Option "Double tap left/right to run".
- `TouchOverlayConfig` bekommt globale Settings: `dpad_double_tap_run_enabled`, `cheat_god_mode`, `cheat_infinite_ammo`, `cheat_all_weapons`, `screen_mode`.
- `TouchOverlaySettingsDialog` wird zentrales Settings-Menue: Reset/Delete bleiben, dazu D-Pad-Run, Cheats und Bildmodus.
- D-Pad-Run-Logik liest globalen Config-Wert statt `TouchButtonConfig.dpadDoubleTapRun`.

**Implementiert:**
- `TouchOverlayController.kt` — `handleGameCanvasTouch()` + `isTouchOnAnyButton()` leiten Taps ausserhalb Overlay-Buttons als absolute Maus-Events an SDL weiter
- `TouchButtonModels.kt` — Schema v5, `TouchOverlayConfig` mit `dpadDoubleTapRunEnabled`, `cheatGodMode`, `cheatInfiniteAmmo`, `cheatAllWeapons`, `screenMode`
- `TouchOverlayEditDialog.kt` — `dpadRunCheckBox`, `showDpadSettings`-Param und D-Pad-Sektion entfernt
- `TouchOverlaySettingsDialog.kt` — Komplett-Rewrite: D-Pad-Run, Cheats (3 CheckBoxen), Screen-Mode (Spinner), Reset/Delete
- `TouchOverlayButtonView.kt` — `globalDpadDoubleTapRunEnabled` als Property, `maybeActivateDpadRun()` liest globalen Wert
- `bag.cpp` — Touch-Zonen unveraendert (korrekt fuer Direct-Tap)

**Build:** `assembleRelease` SUCCESSFUL, APK ~14.4 MB, 3 ABIs, 0 Warnings
**Risiko:** Kein Geratetest (kein ADB-Geraet verbunden)

### Phase 4: Native Cheats und Runtime Screen Mode ✅ (2026-05-19)
- Native JNI-Bridge in `android_main.cpp` ergaenzen, thread-sicher ueber SDL-Event oder Mutex auf `g_game`/`g_stub`.
- Cheat- und Screenmode-Aenderungen gehen aus dem Zahnrad-Menue per JNI an den Native-Core.
- Core-Cheatmask erweitern: `kCheatNoHit`, `kCheatInfiniteAmmo`, `kCheatAllWeapons`.
- God Mode haelt Schaden/Leben zurueckgesetzt; Infinite Ammo haelt Munition spielbar voll; All Weapons setzt bekannte Waffenflags aktiv.
- `SystemStub` bekommt Runtime-Methode fuer Android-Skalierung: `setScreenMode(int mode)`.
- Screen modes: `4:3` = immer aspect-correct; `16:9 stretched` = Gameplay fuellt den Android-Output.
- `Game` setzt vor jedem Frame eine Render-Policy anhand des aktuellen Zustands: `kStateGame` darf stretchen; `kStateBag`, `kStateDialogue`, `kStateMenu1`, `kStateMenu2`, `kStateBitmap` und alle YUV/Video-Ausgaben erzwingen 4:3 aspect-correct.
- Touch-Mapping verwendet immer dasselbe aktuelle Render-Rechteck, damit Koordinaten in 4:3-Menues/Videos und stretched Gameplay korrekt bleiben.
**Implementiert:**
- `game.h`: `kCheatInfiniteAmmo`, `kCheatAllWeapons`, `_screenMode`, `setCheatMask()`, `setScreenMode()`
- `game.cpp`: Cheat-Logik (InfiniteAmmo/AllWeapons pro Frame), Render-Policy (stretch nur bei kStateGame + SCREEN_MODE_16_9)
- `systemstub.h`/`systemstub_sdl.cpp`: `setStretchGameplay(bool)`, `updateScreen()` konditional stretch/aspect
- `android_main.cpp`: JNI `nativeSetCheat(cheatId, enabled)` + `nativeSetScreenMode(mode)`
- `BermudaActivity.kt`: `external` JNI-Deklarationen
- `TouchOverlaySettingsDialog.kt`: Cheat-CheckBoxen → `nativeSetCheat()`, Close → `nativeSetScreenMode()`

**Build:** `assembleRelease` SUCCESSFUL, APK ~14.35 MB, 3 ABIs, 0 Warnings
**Risiko:** Kein Geratetest (kein ADB-Geraet verbunden)

## Phase Acceptance Checks

### Nach Phase 1
- Clean public APK build mit `android/app/src/main/assets/BERMUDA` absent.
- Fresh install zeigt Importer, falscher Ordner wird abgelehnt, korrekter Ordner wird importiert, Relaunch startet ohne Picker.
- `ANDROID_PORT_LOG.md` enthaelt Importpfade, Pflichtdateien, Build-Ergebnis und Risiken.

### Nach Phase 2
- Game startet ohne OGG-Dateien und spielt MIDI fuer Title/Jungle/Cave/Water/Gameover.
- OGG-Fallback bleibt funktionsfaehig, falls `trackXX.ogg` vorhanden ist.
- Lizenzen fuer TinySoundFont/TinyMidiLoader und SoundFont sind im Projekt dokumentiert.
- Build fuer alle konfigurierten ABIs erfolgreich.

### Nach Phase 3
- Inventar laesst sich per Tap oeffnen, bedienen und schliessen.
- Overlay-Settings zeigen globale D-Pad-Run-Option, Cheats und Bildmodus.
- Per-Button-D-Pad-Run-Checkbox existiert nicht mehr.
- Touch-Overlay Reset/Delete/Edit funktionieren weiterhin.

### Nach Phase 4
- Cheats wirken sofort on the fly und lassen sich wieder deaktivieren.
- Gameplay schaltet zur Laufzeit zwischen 4:3 und 16:9 stretched.
- Hauptmenue, Save/Load-Menue, Inventar, Dialoge, Bitmap-Screens und Videos bleiben 4:3, auch wenn Stretch aktiv ist.
- Touch-Koordinaten stimmen in stretched Gameplay und forced-4:3 Zustaenden.

## Final Acceptance By Codex
- Finaler Diff-Review ueber alle Phasen.
- Pruefen, dass keine proprietaeren Spielassets im Public-Build verbleiben.
- `ANDROID_PORT_LOG.md`, technischer Plan und Worktree-Konsistenz pruefen.
- Release-Build und Debug-Build ausfuehren, soweit Umgebung es erlaubt.
- Smoke-Test-Protokoll gegen alle Phase-Akzeptanzpunkte pruefen.
- Erst nach bestandener Endabnahme und finaler Freigabe darf die APK ins Drive hochgeladen werden.
- Abschliessende Risiko- und Freigabeempfehlung geben.

## Assumptions
- "Ohne Assets" bedeutet ohne proprietaere Bermuda-Spielassets; eine freie SoundFont und deren Lizenzdatei duerfen in der APK enthalten sein.
- "Menues im Originalformat" umfasst Hauptmenue, Save/Load-Menue, Inventar und Dialoge.
- v1-Cheats bleiben klein: God Mode/No Hit, Infinite Ammo, All Weapons. Kein Level Select und kein beliebiges Item-Spawning.
- Native MIDI nutzt TinySoundFont/TinyMidiLoader wegen kleiner Integration und MIT-Lizenz; FluidSynth wird vermieden.
- Import kopiert in App-internen Speicher statt direkt aus SAF zu streamen, damit der bestehende C++-Core normale Pfade verwenden kann.
