#!/usr/bin/env bash
set -uo pipefail
mkdir -p ui-artifacts
gradle --no-daemon :app:connectedDebugAndroidTest
ui_result=$?
adb pull /sdcard/Android/data/dev.axiom.launcher/files/screenshots ui-artifacts/ || true
adb logcat -d -s AndroidRuntime:E > ui-artifacts/android-runtime.log || true
exit "$ui_result"
