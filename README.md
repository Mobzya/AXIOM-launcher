# AXIOM Launcher — 0.3 Gravity

An experimental, strictly monochrome Android HOME launcher for Redmi Note 13 Pro 4G.

## Design
- In rest: **one translucent search dock precisely centered vertically**, and a small settings control in the upper-right corner.
- Tap search: dock springs toward the upper portion of the available screen while the on-screen keyboard opens. A semi-transparent app menu unfolds smoothly **below** the dock.
- While the input is blank: show up to four favorites or fallback apps; while typing: fuzzy prefix/substr/transliteration matches, up to six results.
- Swipe down across empty home-screen space: open App Atlas with alphabetical scrolling, search, favorites and filters.
- Four astrophysical / mathematical scenes: **orbital mechanics, Lorenz attractor, neural graph and Fourier harmonics**. Auto crossfades between them slowly; manual scene selection supported.
- Persistent options: ambient motion Off / Quiet / Lively, scene selection and smooth decorative drift ("controlled entropy").
- All controls stay perfectly predictable; uncertainty is restricted to deterministic slow variation in decorative elements.
- Pure black (#000000), white and neutral gray only. No neon, blue tints or app icon grids on the home screen.

## Technical
Kotlin, Jetpack Compose, package `dev.axiom.launcher`, version 0.3.0 (versionCode 3).
Spring-driven translation and sizing; slow crossfades and minimal draw operations. The scene dims without resetting its animation phase when opening search.

## Compile
Use Linux with Java 17, Android SDK 36, Gradle 8.13 or GitHub Actions.

```bash
bash build-linux.sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

[GitHub Actions](../../actions) tests and compiles the app, then provides artifact `AXIOM-debug-APK`.

**This is still a prototype**: test gestures, typing and smoothness on the actual Redmi. System gestures are controlled by HyperOS and may not work with a third-party home launcher; AXIOM's own gestures are separate. This project does not modify firmware or require wiping the phone. If Android reports a signing-key mismatch when updating a previous debug APK, uninstall AXIOM only and reinstall the new APK (favorites and AXIOM preferences reset).

