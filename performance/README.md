# Phase 1 — on-device diagnostics

The **AXIOM-optimized-test-APK** artifact is a release-optimized, R8-minified build
signed with the CI runner's **debug/test key**. It is *not* a production-signed
release and not reproducibly update-compatible with prior CI runs.

Use the optimized APK for subjective scroll assessment. `assembleDebug` is
intentionally retained for crashes and feature debugging.

When Linux + ADB is available again, capture two separate scenarios:

1. Flick a long alphabetic list rapidly and let it decelerate.
2. Scrub the side alphabet repeatedly without tapping a row.

```bash
adb shell dumpsys gfxinfo dev.axiom.launcher reset
# reproduce each scenario separately
adb shell dumpsys gfxinfo dev.axiom.launcher framestats > axiom-framestats.txt
adb shell dumpsys gfxinfo dev.axiom.launcher > axiom-gfxinfo.txt
```

For trustworthy comparisons add a Macrobenchmark on a connected device:
measure launch time, scroll FrameTimingMetric and P50/P90/P95/P99. The CI
runner does not have a physical Redmi, so no measured 120-FPS claims are made.

Android docs:
https://developer.android.com/develop/ui/compose/performance
https://developer.android.com/develop/ui/compose/lists
https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview

## AXIOM 0.9 acceptance checks
- Check physics regression suite for force symmetry, collision momentum and no concurrent lesson/episode.
- Try long mathematical formulas on the actual Redmi portrait display.
- Confirm Home chooser allows AXIOM; if blocked by HyperOS, do not bypass via root or system app deletion.
- Test Home / back / rotation / search keyboard before daily-driver use.
