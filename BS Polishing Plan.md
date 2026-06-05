# Controller-Support fuer BS Android

## Summary

- Arbeitsbereich ist ausschliesslich `D:\Coding\BS Android`; keine Aenderungen in anderen Projekten oder Backups.
- Controller-Support wird phasenweise umgesetzt. Nach jeder Phase prueft FreeClaude die Ergebnisse ein zweites Mal, aktualisiert `ANDROID_PORT_LOG.md` und diesen Plan, und stoppt dann fuer Freigabe/Kontextbereinigung.
- Eine Release-APK wird erst nach endgueltiger Endabnahme durch Codex gebaut; Drive-Upload erfolgt erst danach durch FreeClaude.
- Beim App-Start prueft der Launcher auf Controller und fragt bei erkanntem Controller jedes Mal per Controller-bedienbarem Ja/Nein-Panel, ob dieser verwendet werden soll.

## Key Changes

- Launcher:
  - In `BermudaLauncherActivity.kt` eine `ControllerDetector`-Logik einbauen: gueltige Geraete sind `SOURCE_GAMEPAD`, `SOURCE_JOYSTICK` oder `SOURCE_DPAD`, `deviceId >= 0`, nicht virtuell.
  - Bei gueltigem Import und erkanntem Controller ein fullscreen Prompt-Panel im bestehenden Launcher-Stil anzeigen.
  - Panel-Fokus: Ja/Nein fokussierbar; DPAD links/rechts wechselt Auswahl, A/DPAD_CENTER/ENTER bestaetigt, B/BACK waehlt Nein.
  - Auswahl wird bei jedem App-Start erneut abgefragt, nicht als Opt-in gespeichert.
  - `startGame(controllerEnabled: Boolean)` setzt Intent-Extra `controller_enabled`.

- Android Overlay/Settings:
  - Neues `ControllerConfig` mit eigenem `controller_config.json`, Schema v1.
  - Persistente Defaults: `A=jump`, `X=run`, `B=weapon`, `Y=use`, `START=menu/ESC`, `SELECT=inventory`, `L1=quick_load`, `R1=quick_save`, `L3=status`.
  - DPAD wird nicht remappbar gespeichert.
  - `BermudaActivity` liest `controller_enabled`, laedt ControllerConfig und ruft `nativeSetControllerConfig(enabled, mapping, dpadDoubleTapRunEnabled)` auf.
  - Controller-Modus im `TouchOverlayController`: keine Gameplay-Touchbuttons anzeigen, Schloss sichtbar lassen, Zahnrad wie bisher ueber Unlock erreichbar.
  - `TouchOverlaySettingsDialog` erhaelt einen Controller-Icon-Button.
  - Ohne Controller zeigt der Button eine Meldung; mit Controller oeffnet er ein `ControllerMappingDialog`.
  - Mapping-Dialog: Aktionen links, Buttons rechts; Controller- und Touch-bedienbar; neue Button-Wahl tauscht bestehende Belegungen automatisch.

- Native Input:
  - `systemstub_sdl.cpp` erhaelt eine remappbare Controller-Action-Schicht statt fest verdrahteter Button-Logik.
  - JNI in `android_main.cpp`: `nativeSetControllerConfig(jboolean enabled, jstring mapping, jboolean dpadDoubleTapRunEnabled)` mit Pending-State vor Game-Start.
  - Gameplay-Actions: `jump -> UP`, `run -> SHIFT`, `weapon -> SPACE`, `use -> ENTER`, `menu -> ESCAPE`, `inventory -> TAB`, `quick_load -> load`, `quick_save -> save`, `status -> CTRL`.
  - In Hauptmenue und Save/Load-Menue gilt fest: `A/DPAD_CENTER = bestaetigen`, `B = zurueck`; DPAD bewegt Highlight/Slot-Auswahl.
  - `dpadDoubleTapRunEnabled` gilt auch fuer Controller-DPAD links/rechts.
  - Linker Stick darf zusaetzlich navigieren/bewegen; rechter Stick bleibt ungenutzt.

## Phased Workflow

- Phase 1: ✅ ERLEDIGT (2026-05-19) — Launcher-Erkennung, Controller-Prompt und Intent-Weitergabe implementiert; FreeClaude-Zweitkontrolle durchgefuehrt; Log und Plan aktualisiert.
- Phase 2: ✅ ERLEDIGT (2026-05-19) — ControllerConfig, Store, Settings-Button und Remap-Dialog implementiert; FreeClaude-Zweitkontrolle fand 3 Issues (dpadDoubleTapRun hart false, "—"-Sentinel, Propagation fehlt) — alle gefixt; Log und Plan aktualisiert.
- Phase 3: ✅ ERLEDIGT (2026-05-19) — JNI und native SDL-Controller-Mapping inklusive Menue-Kontext und DPAD-Double-Tap-Run implementiert; FreeClaude-Zweitkontrolle fand 2 Issues (rechter Stick steuerte mit, JSON-Parser \r-Inkonsistenz) — alle gefixt; Log und Plan aktualisiert.
- Phase 4: ✅ ERLEDIGT (2026-05-19) — Integrationstests (Build + Emulator-Smoke), FreeClaude-Zweitkontrolle aller Diffs (keine Issues), ANDROID_PORT_LOG.md und Plan aktualisiert. Voll-Integrationstest mit physischem Controller ausstehend (kein Geraet verfuegbar).
- Phase 5: ERLEDIGT (2026-05-19) - Codex-Endabnahme durchgefuehrt, Blocker gefixt, `assembleDebug`, `git diff --check`, `assembleRelease` und APK-Asset-Check bestanden. FreeClaude darf genau die freigegebene Release-APK ins Drive hochladen und Link, Datei-ID, Groesse und Zeitpunkt im Log dokumentieren.

## Test Plan

- Pro Phase mindestens `./gradlew.bat :app:assembleDebug` oder gezielter Compile-Check, wenn Native-Code betroffen ist zusaetzlich voller Build.
- Vor jedem Stop: `git diff --check`, relevante manuelle Pruefpunkte dokumentieren, FreeClaude-Zweitkontrolle abwarten.
- Final vor APK: `./gradlew.bat :app:assembleRelease`, APK-Asset-Check ohne `assets/BERMUDA/*`, Geraetetest mit Controller.
- Szenarien: Launcher-Prompt mit/ohne Controller, Ja/Nein-Verhalten, Overlay-Ausblendung, Defaults, Remapping mit Button-Tausch, Persistenz, Hauptmenue, Save/Load-Highlighting, A/B-Menuebedienung, DPAD-Double-Tap-Run.

## Assumptions

- Controller-Ja/Nein wird bei jedem Start mit angeschlossenem Controller erneut gezeigt.
- Button-Zuordnungen bleiben persistent.
- FreeClaude arbeitet ebenfalls ausschliesslich in `D:\Coding\BS Android`.
- Kein Drive-Upload vor finaler Endabnahme; Release-APK wurde nach Codex-Endabnahme erstellt.

## Phase 5 Codex-Endabnahme

- Codex fand und fixte vor der Freigabe:
  - falsche native Menue-State-Konstanten (`kStateMenu1=5`, `kStateMenu2=6` statt 6/7),
  - Controller-Achsen aktiv trotz User-Auswahl "No",
  - native Controller-Konfiguration wurde bei "No" nicht explizit deaktiviert,
  - Quick Save/Load konnten durch Button-Up vor dem naechsten Frame verloren gehen,
  - Settings-Button pruefte Start-Opt-in statt aktuellen Controller-Anschluss,
  - Mapping-Dialog kann jetzt physische Controller-Buttons lernen; Long-Press bleibt als Touch-Fallback fuer die Button-Liste.
- Verifikation:
  - `.\gradlew.bat :app:assembleDebug`: BUILD SUCCESSFUL
  - `git diff --check`: keine Whitespace-Fehler, nur CRLF-Warnungen
  - `.\gradlew.bat :app:assembleRelease`: BUILD SUCCESSFUL
  - APK-Asset-Check: keine `assets/BERMUDA/*`, nur `assets/dexopt/*` und `assets/soundfont/*`
- Freigegebene APK:
  - `D:\Coding\BS Android\android\app\build\outputs\apk\release\app-release.apk`
  - Groesse: `19185511` Bytes
  - Zeitstempel: `2026-05-19 18:14:17`

## Funktionaler Abschluss

- Nutzer-Geraetetest nach Drive-Upload bestaetigt: Controller-Support funktioniert.
- Der Controller-Polishing-Plan ist damit als abgearbeitet und funktionell markiert.

## Nachgelagerter Feinschliff

- Inventar-Kontext aus der festen A/B-Menuebehandlung entfernt, damit im Ingame-Inventar wieder das Gameplay-Mapping gilt: Y/Use bestaetigt.
- Video- und Titelbild-Kontext ergaenzt: A bestaetigt/ueberspringt Videos und den Titelbild-Zwischenschirm.
- Controller-Konfiguration wird jetzt schon vor `Game::init()` an den SDL-Stub uebergeben, damit auch die ersten Startvideos per Controller uebersprungen werden koennen.
- Verifikation:
  - `.\gradlew.bat :app:assembleDebug`: BUILD SUCCESSFUL
  - `git diff --check`: keine Whitespace-Fehler, nur CRLF-Warnungen
  - `.\gradlew.bat :app:assembleRelease`: BUILD SUCCESSFUL
  - APK-Asset-Check: keine `assets/BERMUDA/*`, nur `assets/dexopt/*` und `assets/soundfont/*`
- Neue lokale Release-APK:
  - `D:\Coding\BS Android\android\app\build\outputs\apk\release\app-release.apk`
  - Groesse: `19185511` Bytes
  - Zeitstempel: `2026-05-19 18:30:10`
