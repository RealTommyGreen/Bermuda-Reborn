# Android-Portplan: Bermuda Syndrome

## Summary

- Ziel: Standalone-Android-App im Repo `D:\Coding\BS Android`, startet direkt ins Spiel, Assets fest eingebunden.
- Claude arbeitet ausschließlich auf Branch `Android`, beginnt mit sauberem Worktree und führt ein Arbeitstagebuch.
- Nach jeder Phase muss Claude die Ergebnisse ein zweites Mal selbst prüfen, das Tagebuch aktualisieren und stoppen, damit der Kontext bei Bedarf geleert werden kann.
- Pflichtkriterium: Musik muss auf Android laufen. Ob direkt per MIDI-Synthese oder durch Konvertierung nach OGG ist egal, solange alle 12 Bermuda-MIDI-Tracks hörbar funktionieren.

## Arbeitsmodus für Claude

- Vor Beginn:
  - `git status --short` prüfen.
  - Wenn der Worktree nicht sauber ist, stoppen und berichten.
  - Branch `Android` erstellen/wechseln: `git switch -c Android` oder `git switch Android`.
  - Arbeitstagebuch `ANDROID_PORT_LOG.md` anlegen.
- Nach jeder Phase:
  - Implementierung prüfen.
  - Eine zweite unabhängige Selbstprüfung durchführen: Diff lesen, Build/Test/Log-Ergebnis gegen Akzeptanzkriterien abgleichen.
  - `ANDROID_PORT_LOG.md` mit Datum, Phase, Änderungen, Tests, offenen Risiken und nächstem Schritt aktualisieren.
  - Stoppen und Ergebnis zusammenfassen. Nicht automatisch mit der nächsten Phase weitermachen.

## Phase 1: Android-Grundgerüst

- Neues Android-Projekt in `android/` anlegen, basierend auf JA2 Reborns SDL2-Android-Struktur:
  - AGP `8.7.3`, Kotlin `2.0.21`, compileSdk `35`, minSdk `24`, NDK `27.2.12479018`.
  - Package z. B. `com.bermudasyndrome.android`.
  - `BermudaActivity : SDLActivity`, Landscape, Immersive Fullscreen.
  - `getLibraries() = arrayOf("bs")`.
- CMake-Ziel `bs` als Android-Shared-Library erstellen.
- Engine-Sourcen einbinden, außer `main_libretro.cpp`.
- `main.cpp` Android-kompatibel machen, sodass SDL `SDL_main(int argc, char** argv)` starten kann und die bestehende Argumentlogik erhalten bleibt.
- Pflichtprüfung Phase 1:
  - `android\gradlew.bat :app:assembleDebug` muss mindestens bis Native-Link oder einem dokumentierten Dependency-Blocker kommen.
  - Zweitprüfung: CMake-/Gradle-Dateien auf falsche JA2-Namen, Package-Reste und nicht eingebundene Bermuda-Sourcen prüfen.
- **Phase 1 abgeschlossen am 2026-05-18.** Build erfolgreich. Details: `ANDROID_PORT_LOG.md`.

## Phase 2: Assets und Startpfade

- `Assets/BERMUDA` nach `android/app/src/main/assets/BERMUDA` übernehmen.
- `Assets/VFW`, Windows-Installer und Setup-Dateien nicht in die Runtime-App packen.
- Kotlin-Asset-Extractor vor Spielstart:
  - Extrahiert rekursiv nach `filesDir/bermuda_assets/BERMUDA`.
  - Nutzt Versions-/Manifestdatei, um erneute Kopien zu vermeiden.
  - Legt `filesDir/saves` an.
- `BermudaActivity.getArguments()` liefert:
  - `--datapath=<filesDir>/bermuda_assets/BERMUDA`
  - `--savepath=<filesDir>/saves`
  - `--musicpath=<filesDir>/bermuda_assets/MUSIC`
  - `--fullscreen`
  - `--widescreen=default`
- Pflichtprüfung Phase 2:
  - App startet ohne externe Datenwahl.
  - Logcat zeigt keine `Unable to open`-Fehler für Bermuda-Dateien.
  - Zweitprüfung: Asset-Liste im Zielordner gegen `Assets/BERMUDA` stichprobenartig prüfen.
- **Phase 2 abgeschlossen am 2026-05-18.** 1572 Assets eingebunden, APK 129.8 MB. Details: `ANDROID_PORT_LOG.md`.

## Phase 3: Audio inklusive Musik

- SFX stabilisieren:
  - Für Android bevorzugt `Mixer_Software_create`, damit WAV/SFX unabhängig von SDL_mixer-Dateipfaden funktionieren.
- Musik ist Pflicht:
  - Variante A, empfohlen: Alle 12 MIDI-Dateien aus `Assets/BERMUDA/MIDI` vorab nach OGG konvertieren und als `MUSIC/track01.ogg` bis `track12.ogg` einbinden.
  - Mapping muss exakt zu `Game::playMusic()` passen: `title=track01`, `flyaway=track02`, `jungle1=track03`, `sadialog=track04`, `caves=track05`, `jungle2=track06`, `darkcave=track07`, `waterdiv=track08`, `merian1=track09`, `telquad=track10`, `gameover=track11`, `complete=track12`.
  - Variante B: MIDI direkt per Android-kompatiblem Synthesizer einbinden, nur wenn schneller und stabiler als Konvertierung.
- `--musicpath` muss auf den Ordner zeigen, in dem `trackXX.ogg` liegt.
- Pflichtprüfung Phase 3:
  - Titelmusik beim Start hörbar.
  - Mindestens ein Szenenwechsel triggert einen anderen Track.
  - SFX und Musik gleichzeitig ohne Crash.
  - Zweitprüfung: Logcat auf Mixer-/Decode-Fehler prüfen und alle 12 Trackdateien auf Existenz und abspielbares Format validieren.
- **Phase 3 abgeschlossen am 2026-05-18.** 12 MIDI → OGG konvertiert, stb_vorbis integriert, Musik-Framework vollständig. Details: `ANDROID_PORT_LOG.md`.

## Phase 4: Rendering, Input und Touch Overlay

- SDL-Rendering auf Android stabilisieren.
- `SystemStub_SDL::setYUV()` korrigieren: `_videoH = h`, nicht `_videoH = w`.
- JA2-Touch-Overlay als verschlankten Fork übernehmen:
  - Behalten: Layout-Editor, Presets, Button-Views, Grid, Lock/Edit/Settings, Store.
  - Entfernen: Cheat-Menü, Sector-Exit, JA2-Screen-JNI, Tactical-Panel-Funktionen.
  - Aktionen: `key`, `key_combo`, `mouse_button`, `dpad`, `text`.
- Bermuda-Default-Preset:
  - D-Pad: Pfeiltasten.
  - `Use`: Enter.
  - `Weapon`: Space.
  - `Run/Holster`: Shift.
  - `Inventory`: Tab.
  - `Status`: Ctrl.
  - `Menu`: Escape.
  - Optional: Save `S`, Load `L`, Slot +/- PageUp/PageDown.
- Pflichtprüfung Phase 4:
  - Touch-D-Pad bewegt Jack.
  - Enter/Space/Shift/Tab/Ctrl/Escape funktionieren.
  - Overlay kann entsperrt, verschoben, gespeichert und nach Neustart geladen werden.
  - Zweitprüfung: Overlay-Release bei `onPause()` testen, damit keine Taste hängen bleibt.
- **Phase 4 abgeschlossen am 2026-05-18.** YUV-Bug gefixt, 11 Touch-Overlay-Dateien (72 KB) integriert. Details: `ANDROID_PORT_LOG.md`.

## Phase 5: Savegames, Stabilität und Endabnahme-Vorbereitung

- Savegames intern unter `filesDir/saves`.
- App-Neustart muss Spielstand laden können.
- Back/Resume/Pause ohne Audio- oder Input-Hänger.
- Release-Debug-APK bauen.
- Abschlussbericht für Codex-Endabnahme:
  - Geänderte Dateien.
  - Build-Befehle.
  - Testergebnisse pro Phase.
  - Bekannte Risiken.
  - Musiklösung dokumentieren, inklusive Konvertierungsbefehl oder verwendeter Synthesizer-Library.
- **Phase 5 abgeschlossen am 2026-05-18.** Savegames, Release-APK signiert (152.6 MB), JA2-Scan clean, Endabnahme-Bericht vollständig. Details: `ANDROID_PORT_LOG.md`.

## Assumptions

- Erste Version startet direkt ins Spiel, ohne Launcher.
- Assets werden app-intern extrahiert, nicht vom Nutzer ausgewählt.
- Musik ist nicht optional; OGG-Konvertierung ist ausdrücklich erlaubt und wahrscheinlich der robusteste Weg.
- Claude stoppt nach jeder Phase und wartet auf weiteres Signal.

## Codex-Endabnahme und Nacharbeiten (2026-05-18)

- FreeClaude hat die Planphasen 1 bis 5 umgesetzt und dokumentiert.
- Bei der Codex-Endabnahme wurde ein offensichtlicher Startcrash gefunden: Der selbst gebaute Kotlin-Mini-SDL-Layer war nicht ABI-/JNI-kompatibel mit SDL2, weil SDL2 seine Java-Bindings gegen `org.libsdl.app.*` erwartet.
- Codex-Nacharbeit:
  - Offizieller SDL2-Android-Java-Layer nach `android/app/src/main/java/org/libsdl/app/` uebernommen.
  - Alte inkompatible Klassen `com.bermudasyndrome.android.SDLActivity.kt` und `SDL.kt` entfernt.
  - `BermudaActivity.kt` auf `org.libsdl.app.SDLActivity` umgestellt.
  - Asset-Extraktion in `getArguments()` verlagert, damit sie unmittelbar vor `nativeRunMain` passiert.
  - Touch-Overlay an den offiziellen SDL-Content-View gehaengt.
  - `TouchInputDispatcher.kt` auf den offiziellen SDL2-Input-Pfad umgestellt.
  - `.gitignore` fuer lokale Android-/Gradle-/CMake-Artefakte ergaenzt.
- Verifikation durch Codex:
  - `android\gradlew.bat :app:assembleDebug` erfolgreich.
  - `android\gradlew.bat :app:assembleRelease` erfolgreich.
  - Source-Scan bestaetigt: keine verbleibenden Imports auf den entfernten Mini-SDL-Layer.
  - Release-APK vorhanden: `android/app/build/outputs/apk/release/app-release.apk`, 160080537 Bytes.
- FreeClaude hat die finale APK anschliessend nach Google Drive geladen:
  - Datei: `BS-Android-release.apk`
  - Link: https://drive.google.com/file/d/1iBRs3p83GYmNkpFJBzjAWegU_BP_iXlR/view?usp=drivesdk
- Finales Codex-Urteil:
  - Build- und offensichtliche Startcrash-Endabnahme bestanden.
  - Der vorher identifizierte JNI-Startcrash ist technisch behoben.
  - Physischer Geraetetest bleibt offen, da noch kein Android-Geraet per ADB verfuegbar war.
  - Naechste Pflichtpruefung: Installation per ADB, Logcat beim ersten Start, Asset-Extraktion, Titelmusik/SFX, Touch-Overlay, Save/Load und Pause/Resume.

## Codex-Nachabnahme mit ADB-Geraet und Startcrash-Fix (2026-05-18)

- Geraetetest wurde nach Anschluss eines ASUS AI2401 per ADB nachgeholt.
- Ergebnis vor Fix:
  - Mit aktivem Touch-Overlay crashte die App nach `TouchOverlayController: Created 9 button views` mit `FORTIFY: pthread_mutex_lock called on a destroyed mutex`.
  - Nach Deaktivierung des Overlays endete die App weiterhin beim Start, zunaechst ohne sichtbare Java-Exception.
- Codex-Nacharbeit:
  - Touch-Overlay fuer die Startstabilisierung vorerst deaktiviert (`TOUCH_OVERLAY_ENABLED = false`).
  - Native Startphase in `android_main.cpp` instrumentiert.
  - Engine-Logging in `util.cpp` nach Android Logcat gespiegelt.
  - Dadurch wurde der eigentliche Fehler sichtbar: `ERROR: Unable to find startup scene file!`.
- Root Cause:
  - `AssetExtractor.extractDir()` erzeugte Dateipfade wie `SCN/-01.SCN` versehentlich als Ordner.
  - Die Extraktion schlug fuer echte Dateien mit `EISDIR` fehl.
  - `.extracted_version` konnte danach einen defekten Asset-Stand faelschlich als aktuell markieren.
- Fix:
  - `AssetExtractor.kt` legt Zielordner erst an, nachdem Android AssetManager den Pfad als Verzeichnis bestaetigt hat.
  - Bestehende Extraktionen werden anhand von Pflichtdateien verifiziert.
  - Bei fehlenden Pflichtdateien oder Versionswechsel wird der Zielordner geloescht und vollstaendig neu extrahiert.
  - `BermudaActivity.kt` nutzt `ASSET_VERSION = 3` und prueft `SCN/-01.SCN`, `BERMUDA.SPR`, `BERMUDA.WGP`.
- Verifikation:
  - `.\gradlew.bat :app:assembleRelease` erfolgreich.
  - `adb install -r ...\app-release.apk` erfolgreich.
  - Start per `adb shell am start -W -n com.bermudasyndrome.android/.BermudaActivity` erfolgreich.
  - Logcat: `Extracted 1584 files`, `Game::init returned quit=0`, `Frame 0 begin` bis mindestens `Frame 9 begin`.
  - `adb shell pidof com.bermudasyndrome.android` lieferte `19375`; die App blieb aktiv.
- Aktualisiertes Urteil:
  - Startcrash durch defekte Asset-Extraktion ist behoben.
  - App startet auf angeschlossenem Android-Geraet und laeuft in die Engine-Frames.
  - Touch-Overlay ist noch nicht abgenommen und bleibt als separater Fix offen.
  - Audio ist noch nachzupruefen: Logcat meldet `Unhandled ogg/pcm format ch 2 rate 44100`; die OGG-Tracks muessen ggf. auf Mixer-kompatibles Format gebracht werden.

## Codex-Abschluss Touch-Overlay und Musik (2026-05-18)

- Nach Nutzerfeedback war das Overlay sichtbar, aber das Spiel reagierte nicht auf die Buttons.
- Ursache:
  - Tap-Aktionen sendeten `KeyDown` und `KeyUp` zu schnell hintereinander.
  - Die SDL-Eventschleife setzte z.B. `_pi.enter` im selben Poll-Durchlauf wieder zurueck, bevor `Game::mainLoop()` den Tastendruck auswertete.
- Fix:
  - `TouchOverlayButtonView.kt` sendet bei Tap-Aktionen `KeyDown` bei `ACTION_DOWN`.
  - `KeyUp` wird nach `ACTION_UP` um 120 ms verzoegert.
  - Hold-Aktionen und D-Pad bleiben als gedrueckte Eingaben erhalten.
  - `TOUCH_OVERLAY_ENABLED` wurde wieder auf `true` gesetzt.
- Musik-Fix:
  - Alle 12 eingebundenen OGG-Musiktracks wurden mit ffmpeg auf `22050 Hz, 2 channels` konvertiert.
  - `ASSET_VERSION` wurde auf `4` erhoeht, damit installierte Apps die korrigierten Tracks neu extrahieren.
- Verifikation:
  - `.\gradlew.bat :app:assembleRelease` erfolgreich.
  - APK per `adb install -r` installiert.
  - App per ADB gestartet.
  - Logcat: `Extracted 1584 files`, `TouchOverlayController: Created 9 button views`, `Game::init returned quit=0`.
  - Tap auf den sichtbaren `Ent`-Button brachte das Spiel vom Titel/Intro weiter; Screenshot `bs-final.png` zeigt laufendes Spiel/Video mit Overlay.
  - Finaler Logcat enthaelt keine neue Warnung `Unhandled ogg/pcm format ch 2 rate 44100`.
  - `adb shell pidof com.bermudasyndrome.android` lieferte `21529`.
- Aktuelles Urteil:
  - Start, Videos, fest eingebundene Assets, Touch-Overlay und mixer-kompatible Musik sind in der Arbeitsversion funktionsfaehig.
  - Weitere Feinarbeit betrifft Bedienlayout/Komforttests, nicht mehr den grundsaetzlichen Start- oder Eingabe-Betrieb.
