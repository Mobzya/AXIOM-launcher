#!/usr/bin/env bash
set -uo pipefail
mkdir -p ui-artifacts
if [[ ${AXIOM_VERIFY_ONLY:-false} != true ]]; then
    gradle --no-daemon :app:connectedDebugAndroidTest
    ui_result=$?
    adb pull /sdcard/Download/axiom-ui ui-artifacts/ || true
    adb logcat -d -s AndroidRuntime:E AXIOM_UI:I > ui-artifacts/android-runtime.log || true
    if (( ui_result != 0 )); then exit "$ui_result"; fi
fi
if [[ $# == 0 ]]; then exit 0; fi

# The optimized artifact and debug instrumentation can have different test keys.
set -e
if adb shell pm path dev.axiom.launcher | rg -q '^package:'; then
    adb uninstall dev.axiom.launcher
fi
adb install "$1"
adb logcat -c
adb shell am start -W -n dev.axiom.launcher/.MainActivity | tee ui-artifacts/optimized-start.txt
rg -q '^Status: ok' ui-artifacts/optimized-start.txt
sleep 3
adb shell pidof dev.axiom.launcher
adb exec-out screencap -p > ui-artifacts/optimized-home.png
adb shell dumpsys gfxinfo dev.axiom.launcher > ui-artifacts/optimized-frame-timing.txt
adb logcat -d -s AndroidRuntime:E > ui-artifacts/optimized-runtime.log
if rg -q 'FATAL EXCEPTION' ui-artifacts/optimized-runtime.log; then
    cat ui-artifacts/optimized-runtime.log
    exit 1
fi
