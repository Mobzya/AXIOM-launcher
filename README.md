# AXIOM Launcher 0.4 — Luminous Gravity

A search-first, black-and-white Android HOME launcher designed for Redmi Note 13 Pro 4G.

## Main UX
- One translucent search bar centered in the idle home screen; upper-right settings control.
- On tap, search expands with a monochrome halo and springs up to make room for the keyboard.
- **Zero application suggestions on empty search**; as soon as you type, relevant apps reveal below the search bar with stable ranking, including Russian/Latin transliteration.
- Search prefix `> help` and press keyboard Search to open the terminal command palette.
- Swipe down on **empty** home-screen area for an enlarged Niagara-inspired text list with a right-side alphabet rail, inertial scrolling and center-emphasis. App Atlas offers filtering, favorites, and direct launching.

## One living universe
- Every object carries mass, velocity, acceleration, lifetime and a procedural vector sprite.
- Pairwise softened gravity, momentum-aware collisions, bounces, orbital and comet visuals, occasional procedural arrivals.
- Tap or long-press empty home-screen space to inject an impulse and generate an object.
- **No scene presets**: independent bodies influence one another in one cohesive monochrome environment. Computation capped at 60Hz on home / 30Hz during UI interaction and 11 active bodies maximum.
- Screen remains pure black. Physics affects *only* visual bodies; never shifts search fields or app buttons.

## Terminal
Open via Settings → Command Console or type `> help` in search followed by Enter.
A safe launcher command palette, **not a Linux shell**. Commands:
`help`, `ls`, `find <name>`, `open <name>`, `calc <expr>`, `physics`, `gravity <0.25..2.2>`, `spawn`, `reset`, `battery`, `time`, `note <text>`, `notes`, `wifi`, `bluetooth`, `display`, `clear`, `exit`.
It cannot run arbitrary shell commands or access privileged data.

## Build on Linux
Use JDK 17, Android SDK 36, Gradle 8.13 (or VS Code build task):

```bash
bash build-linux.sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

CI tests and produces a debug APK artifact. If Android refuses to update an older debug build due to mismatched signing keys, switch to Xiaomi's HOME app, uninstall AXIOM only (losing AXIOM-specific local preferences), then reinstall. **Do not reset or reflash your phone.**

## Status
Prototype v0.4, versionCode 4; not tested for device frame time / HyperOS gestures yet. Use GitHub Actions to verify compilation. Xiaomi controls Android recents and system gesture navigation.
