# AXIOM Launcher — 0.1 Prototype

A minimalist scientific launcher for Android / Redmi Note 13 Pro 4G, built using Kotlin and Jetpack Compose.

## Included
- Android HOME launcher registration
- Mostly empty monochrome home screen with slow, deterministic orbital animation
- On-device search for installed launchable apps (including Russian → Latin transliteration)
- Launch applications, open App Atlas by swiping down or tapping its label
- App filters: all, favorites, system, third-party
- Favorites and ambient-motion preference saved locally
- Glass-inspired translucent panels and spring animations

## Not included yet
No machine learning, command execution, widget hosting, notification badges, true backdrop blur or integration with system recents. App rows currently use initials instead of icons. Xiaomi HyperOS may restrict full-screen system gestures with third-party launchers.

## Build and test
Use Linux with Java 17, Android SDK Platform 36 and VS Code. Run `bash build-linux.sh` (downloads Gradle 8.13 if absent) or use VS Code **Run Build Task**.
The output is `app/build/outputs/apk/debug/app-debug.apk`.

Install via:
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n dev.axiom.launcher/.MainActivity
```

Alternatively open **Actions → AXIOM Debug APK** on GitHub; a successful run publishes the artifact `AXIOM-debug-APK`.

See [README-VSCODE.md](README-VSCODE.md) for setup.

**Safety:** This is a prototype, not a verified release. Test as an ordinary app before changing the default HOME app. No root, wipe or bootloader unlock is required.
