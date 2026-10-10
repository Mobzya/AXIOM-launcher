# AXIOM — development with VS Code on Linux

## 1. Install the prerequisites
You need JDK 17, Android SDK command-line tools, SDK Platform 36 and platform-tools. Android Studio is optional.

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
sdkmanager --licenses
sdkmanager 'platforms;android-36' 'build-tools;35.0.0' 'platform-tools'
```

## 2. Build in VS Code
Open this repo in VS Code and press **Ctrl+Shift+B**. The build task runs `bash build-linux.sh`. The script uses installed Gradle or downloads Gradle 8.13 from its official distribution.

## 3. Deploy to your Redmi
Enable USB debugging on the phone and authorize the computer. Then:

```bash
adb devices -l
bash build-linux.sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n dev.axiom.launcher/.MainActivity
```

VS Code tasks can build, install, list devices and show crash logs.

## 4. APK from GitHub
GitHub Actions builds on pushes to main and from the manual **Run workflow** button. After a successful run, download **Actions → AXIOM Debug APK → Artifacts → AXIOM-debug-APK**.

## Notes
This prototype still requires compilation and testing on a real device. Debug APKs use environment-dependent signatures; to change signing keys you may have to uninstall AXIOM first (only its app data will be lost). HyperOS may disable full-screen system gestures when a third-party HOME app is selected.

## Testing 0.2
- Tap search: the compact dock should expand, show keyboard and list matches below.
- Swipe down from an empty portion of the home screen: App Atlas appears.
- Scroll and use the Atlas filters; swipe up on its header or tap × to close.
- Settings (small top-right gear): Off / Quiet / Lively ambience and refresh apps.
- If the debug APK can't update your existing copy due to signature mismatch, use `adb uninstall dev.axiom.launcher` before reinstalling; AXIOM settings will reset.
