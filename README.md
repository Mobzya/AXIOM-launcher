# AXIOM Launcher — 0.2 Monochrome

Minimalist Android home launcher for Redmi Note 13 Pro 4G, written in Kotlin and Jetpack Compose.

## What changed in 0.2
- Strict black, white and neutral gray UI (no navy or blue shades)
- Quiet home screen: just a translucent search dock, upper-right settings control and a sparse ambient astronomy scene
- Dock grows gently on tap and opens the keyboard; matching applications appear immediately **below** the dock
- Swipe **down on empty home-screen space** to open App Atlas; scroll/search/filter all installed launchable apps
- Swipe **up on the Atlas header** or tap × to close; Android Back closes panels
- Slow monochrome orbits, occasional fly-bys, transient gradient-descent formulas and a tiny ML graph
- Ambient levels: Off / Quiet / Lively; ambient animation stops during search or menus
- Simplified scene rendering using 2 GPU-drawn ellipses instead of constructing hundreds of path points on every frame
- Favorites and ambient settings persist locally, refresh of installed apps on return to launcher
- Existing package name `dev.axiom.launcher`; versionCode 2

## Compile and install
Use JDK 17, Android SDK 36 and Gradle 8.13 with VS Code, or download the debug APK from GitHub Actions (a successful workflow produces the `AXIOM-debug-APK` artifact).

```bash
bash build-linux.sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

**Important:** GitHub Actions debug builds may use a different signing key from your previous build. If Android reports `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, uninstall **only AXIOM** then install the new APK. This clears AXIOM favorites/settings but does **not** wipe your phone.

No root, bootloader unlock or factory reset needed. Xiaomi HyperOS may restrict gesture navigation when a third-party HOME app is selected. No machine learning inference, system-wide blur or widget hosting is implemented yet.

**Testing:** Search and UI behavior still require validation on the actual Redmi; GitHub Actions verifies compilation and search unit tests.
