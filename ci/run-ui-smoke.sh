#!/usr/bin/env bash
set -uo pipefail
mkdir -p ui-artifacts
gradle --no-daemon :app:connectedDebugAndroidTest
ui_result=$?
adb pull /sdcard/Download/axiom-ui ui-artifacts/ || true
adb logcat -d -s AndroidRuntime:E AXIOM_UI:I > ui-artifacts/android-runtime.log || true
exit "$ui_result"
