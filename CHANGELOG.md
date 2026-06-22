# Changelog

All notable changes to Bermuda Reborn are documented here.

## [1.0.4] - 2026-06-22

### Added
- Add first-run savegame folder selection on Android and sync native save slots with the chosen folder
- Write Java/Kotlin and native crash logs into the selected savegame folder

## [1.0.3] — 2026-06-22

### Fixed
- Reset transient weapon and touch-control state after loading savegames, preventing the touch overlay from staying in weapon mode after death and restore
- Preserve held aim direction after firing with a drawn weapon, so Jack no longer snaps back to straight-ahead aiming
- Add double-tap forward jump support to D-Pad Up and the Jump button without triggering an initial vertical jump

## [1.0.1] — 2026-06-14

### Fixed
- Touch overlay buttons now use shape-aware hit-testing (circle for round buttons, rounded-rect for oblong) instead of bounding-box detection, fixing tap misses on small or clustered buttons

## [1.0] — 2026-06-12

### Added
- Initial Android release of Bermuda Syndrome, powered by a native SDL2 engine recreation
- Asset-free build (~18 MB APK) — game data loaded via Android Storage Access Framework folder picker
- Context-sensitive touch overlay that adapts to gameplay, menus, video, and inventory contexts
- Layout editor: drag to reposition buttons, long-press to resize, lock to save
- Game controller detection on launch with opt-in prompt and fully remappable button mapping
- MIDI music playback via TinySoundFont with bundled soundfont
- Native cheats: God Mode, Infinite Ammo, All Weapons
- Custom savegame system with 10 named slots, savestate previews, and date tracking
- Quicksave and Quickload (instant single-tap save/load, separate from manual slots)
- 16:9 widescreen during gameplay, 4:3 for menus, inventory, and video
- Landscape-only, immersive fullscreen mode
- 11 custom SVG touch button icons with runtime context switching (fire/reload/sword/run/jump)
- Touch preset import/export via SAF for sharing layouts

### Fixed
- Inventory screen stretched incorrectly in 16:9 mode — now forces 4:3 like menus
- Weapon icon race condition during draw/holster transitions — engine state is cross-checked before reporting weapon drawn
- Overlay context switch lag — sync interval reduced and immediate sync triggered on any user interaction
- Crash after Quickload when savegame contained transient MENU objects — stale objects cleaned on load
- Run button auto-walk flip direction inverted — explicit left/right flip check replaced boolean comparison
- DPAD horizontal release now stops character immediately
- Menu Cancel icon now visible and functional in all menu contexts
- RESTORE GAME / SAVE GAME titles centered with dynamic text width
- System buttons (lock, settings) match default preset opacity
- Video skip button position now matches menu Cancel position for consistency

## [0.1.7] — upstream (Gregory Montoir / cyxx)

The Android port is built on the reverse-engineered Bermuda Syndrome engine recreation by Gregory Montoir ([cyxx/bermuda](https://github.com/cyxx/bermuda)).

Upstream features include:
- SDL2 backend with software rendering
- Original game logic reverse-engineered from the Windows executable
- MIDI playback and OGG Vorbis audio fallback
- Widescreen mode with blurred borders
- Save state support
- Libretro core support
- Command-line options for scaling and screen mode
