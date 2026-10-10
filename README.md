# AXIOM 0.7 — Fluid Foundations

Minimalist, strictly black-and-white Android HOME launcher with a living
procedural mathematical universe (Redmi Note 13 Pro 4G / HyperOS).

## What changed
### Phase 1: responsive search and navigation
- **Fluid Index** replaces the old heavy app atlas: stable lazy-list keys,
  explicit heading/row content types, no scroll-state reads in all rows.
- Private local **Recents**, Favorites, **Work** and **Tools** filters.
  Long-press an app in the index to assign it to a workspace or view Android app info.
- Alphabet rail provides direct letter navigation and fine group scrubbing.
- PackageManager lookup cached for up to five minutes; explicit Refresh clears it.
- Search stays in the center when inactive, blooms in white on focus, shows results
  only after typing, and moves with spring physics.
- Search shortcuts: `=2*(3+4)` displays a calculator result and Enter copies it;
  `>help` or `!note hello` opens the safe built-in terminal.

### Phase 2: continuous motion and consequence-based space
- World physics integrates at fixed 60/30 Hz and **interpolates positions** for
  smooth display rendering at higher refresh rates (device frame rate varies).
- Existing softened n-body gravity, collisions and depth parallax remain.
- Nova fronts interact with orbiting bodies, wormholes actually transfer some
  objects, slow close encounters can merge, and exceptional events leave drifting
  remnants rather than vanishing without consequence.
- Pure monochrome vector graphics; no heavy full-screen blur or animated video.
- Universe stops updating while Atlas, Settings and Terminal are open.

### Phase 3: mathematical visualizations
- 16 ambient ML/math demonstrations already present, now with **glyph-aware
  transformations**: common equation fragments remain steady while terms are
  removed/inserted and the shared suffix glides into place.
- Tap a currently appearing mathematical visualization to pin/advance its
  equation; Settings → Math Lab starts a new visualization.
- This is *authored mathematical demonstration*, **not** neural-network
  inference, training, or a general computer algebra system.

### Phase 4: useful launcher utilities
- Safe terminal for local app search, launch, expressions, notes, scenes, gravity,
  math models, and an optional `fps` profiling command.
- Settings supports JSON backup/restore of favorites, Work/Tools collections
  and physics preferences using Android's document chooser.
- Optional **in-app frame meter** shows P50/P95/P99 window frame durations over
  the catalog so profiling can start **without a Linux computer or root**.

## How to install
The GitHub Actions build publishes **two different test artifacts**:
1. `AXIOM-debug-APK`: fast-to-build developer/debug APK.
2. `AXIOM-optimized-test-APK`: R8-minified, non-debuggable, **test-key-signed**
   APK for a more meaningful scrolling comparison.

Use the **optimized-test APK** when evaluating scroll smoothness. Neither
build is a production release and neither measures real Redmi frame times
inside CI. See [performance/README.md](performance/README.md).

### Local build on Linux (optional)
JDK 17, Android SDK Platform 36, Gradle 8.13:
```bash
bash build-linux.sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Safety and limitations
No root, bootloader unlock, factory reset, cloud data collection or privileged
shell access. HyperOS can restrict system navigation gestures for third-party
HOME apps. App-specific Baseline Profiles and actual on-device Macrobenchmarks
still require an Android emulator/device and are **not yet generated**.

GitHub Actions' debug/test signatures may differ across runners. If you get
`INSTALL_FAILED_UPDATE_INCOMPATIBLE`, temporarily select the stock launcher
and uninstall **only AXIOM**, then install the newer package. Doing so resets
AXIOM preferences; the JSON export/import can restore them.
