# Bermuda Reborn v1.0

Android port of **Bermuda Syndrome** — a cinematic platformer / action-adventure game originally
developed by Century Interactive and published by BMG (1995). This port runs a reverse-engineered
recreation of the game engine natively on Android via SDL2, with a modern touch overlay, game
controller support, and an asset-free build that loads game data directly from device storage.

## Acknowledgements

This project would not exist without the brilliant work of **Gregory Montoir** ([cyxx](https://github.com/cyxx)),
who reverse-engineered the entire Bermuda Syndrome engine from the original Windows executable
and built the cross-platform [cyxx/bermuda](https://github.com/cyxx/bermuda) engine recreation
(with SDL2, libretro, and Emscripten backends). Merci, Greg — standing on your shoulders here.

## Features

- Full native engine via SDL2 — no emulation
- **Asset-free build** (~18 MB APK) — game data loaded via Android Storage Access Framework (SAF)
- **Context-sensitive touch overlay** — gameplay, menus, video, and inventory each get their own button set
- **Game controller support** — detects gamepads on launch with opt-in prompt and fully remappable buttons
- **Customizable layout** — drag buttons to reposition, long-press to resize, lock to save, import/export presets
- MIDI music playback via TinySoundFont
- Native cheats: God Mode, Infinite Ammo, All Weapons
- 16:9 widescreen (gameplay) with 4:3 menus/inventory/video
- Landscape, immersive fullscreen

## Getting the Game Data

This project does **not** include game assets. You need an original copy of Bermuda Syndrome.
Place these files in a folder on your device:

```
YourFolder/
├── BERMUDA.SPR
├── BERMUDA.WGP
├── SCN/-01.SCN
├── MIDI/TITLE.MID
└── ... (all other BERMUDA files)
```

On first launch, the app opens a folder picker — select the folder containing the game data.
The app validates the required files and imports them.

## Building

### Prerequisites

- **Android Studio** (Hedgehog or later) or standalone SDK
- **JDK 17+**
- **Android NDK 27.2+**
- **Android SDK 35** with build-tools

### Build Steps

```bash
cd android

# Debug build
./gradlew :app:assembleDebug

# Release build (requires signing config)
./gradlew :app:assembleRelease
```

### Signing

Create `android/keystore.properties`:

```
storeFile=/path/to/your.keystore
storePassword=yourStorePassword
keyAlias=yourKeyAlias
keyPassword=yourKeyPassword
```

Place your keystore file at the path specified in `storeFile`. The Gradle build reads
these properties to sign the release APK.

## Touch Controls

The overlay adapts to context:

| Context | Visible Buttons |
|---------|----------------|
| **Gameplay** | D-Pad, Run, Jump, Weapon, Use, Inventory, Status, Menu |
| **Video** | Skip/Cancel |
| **Menus** | D-Pad, OK, Cancel |
| **Inventory** | D-Pad, OK |
| **Armed (Gun)** | Fire replaces Run, Reload replaces Jump (icons only) |
| **Armed (Sword)** | Sword replaces Run |

### Editor Mode

Tap the lock icon to unlock the layout. In edit mode you can:
- **Drag** buttons to reposition
- **Long-press** a button to change its size and shape
- **Import/Export** presets via the settings gear

Layout changes persist. The lock button saves your layout and hides the editor.

### Settings

Tap the gear icon for:
- Global D-Pad double-tap to run
- Cheats (God Mode, Infinite Ammo, All Weapons)
- Screen mode (4:3 / 16:9 stretched)
- Reset layout to defaults
- Export/Import touch preset

## Controller Support

If a gamepad is detected on launch, you can opt in. Default mapping:

| Button | Action |
|--------|--------|
| A | Jump / Confirm |
| B | Back / Cancel |
| X | Run / Fire |
| Y | Use |
| START | Menu |
| SELECT | Inventory |

All buttons are remappable via the controller settings dialog. D-Pad navigates menus.

## Architecture

```
.
├── android/                        # Android project
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── cpp/                # Native SDL2 entry point (android_main.cpp)
│   │   │   ├── java/com/bermuda/reborn/
│   │   │   │   ├── BermudaLauncherActivity.kt   # SAF import UI
│   │   │   │   ├── BermudaActivity.kt           # SDL game activity
│   │   │   │   ├── SafImporter.kt               # File import & validation
│   │   │   │   └── touch/                       # Touch overlay subsystem
│   │   │   └── res/raw/                         # SVG icons, soundfont
│   │   └── build.gradle.kts
│   └── build.gradle.kts
├── *.cpp, *.h                      # Reverse-engineered Bermuda engine sources
├── systemstub_sdl.cpp/h            # SDL2 platform layer
├── stb_vorbis.c                    # OGG Vorbis decoder
└── ANDROID_PORT_LOG.md             # Full development log
```

The engine compiles into a single native library `libbs.so` loaded by SDL2.
Platform-specific code lives in `systemstub_sdl.cpp`. The Kotlin layer handles
touch input, controller detection, asset import, and JNI bridge to the native engine.

## Tech Stack

- **Engine:** C++ (reverse-engineered Bermuda Syndrome recreation, SDL2 2.30)
- **Android:** Kotlin, AGP 8.7, minSdk 24, targetSdk 35
- **Audio:** TinySoundFont + TinyMidiLoader (MIDI), stb_vorbis (OGG fallback)
- **Graphics:** SDL2 software rendering with YUV video playback
- **Touch:** Custom overlay with AndroidSVG icons, kotlinx.serialization for config persistence
- **Build:** CMake + Gradle, NDK 27.2

## Known Issues

- **Unlimited Ammo Cheat:** When enabled, HUD displays incorrect ammo count and reload
  animation loops. Workaround: use DPAD-Up to break out of reload. Normal gameplay
  (cheat disabled) is unaffected.

## Credits

- **Original game:** Bermuda Syndrome by Century Interactive, published by BMG (1995)
- **Engine recreation:** [Gregory Montoir](https://github.com/cyxx) — reverse-engineered from the original Windows executable
- **Android port:** Tommy Green

## License

This project is based on the archived [cyxx/bermuda](https://github.com/cyxx/bermuda)
reverse-engineered engine recreation by Gregory Montoir. The Android-specific code in `android/` and platform modifications are
provided as-is. The original Bermuda Syndrome game assets are not included and remain the
property of their respective rights holders.
