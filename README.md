# AXIOM 0.9 — Verified Physics & Clear Mathematics

AXIOM is a minimalist, monochrome, interactive Android HOME launcher. This
iteration prioritizes physical consistency, mathematical diagram readability
and safe daily use on Redmi Note 13 Pro 4G / Xiaomi HyperOS.

## Physics validation
- Pairwise **softened Newton-like attraction** has symmetric action/reaction,
  even when bodies have unequal mass. A third-law-consistent contact pressure
  replaces the old erroneous equal-acceleration repulsion.
- Approaching nearby bodies now receive a **normal collision impulse** with
  restitution 0.82. Linear momentum is conserved; collision energy does not grow.
- Very slow mergers conserve total mass and momentum; excessively massive
  mergers are disallowed rather than silently deleting mass.
- All background physics remains small and bounded (maximum 21 active bodies,
  maximum 60 simulated steps per second on HOME and 30 when interacting).
- Cinematic effects (nova, lensing, wormholes, singularities) explicitly inject
  *external* forces. They deliberately alter energy and/or momentum.
- **Important scientific limitation:** this is a stable, stylized **2D
  gravitational model** with softened forces and a visual depth layer, not a
  fully accurate 3D astronomical N-body simulation.

## Mathematically clear ambient scenes
- Exactly **one active mathematical diagram at a time**, using four screen
  placement zones away from the centered search field and corner buttons.
- Mathematical lessons never coincide with cinematic episodes: automatic
  events wait, explicitly invoked events dismiss current math, and manual
  math takes priority over events.
- Foreground sprite drawing avoids the current lesson's display area; lessons
  are hidden while using search to keep all controls and text readable.
- Long equations automatically shrink to fit the available screen width.
- Sixteen curated mathematical or ML visualizations, with staged explanations
  and glyph-based transformation animations. They demonstrate mathematics,
  **not** real training of a neural network or a full symbolic solver.

## Launcher interaction
- One translucent central search field; results appear only while typing.
- Fluid Index alphabetic list, favorites, Recents, Work and Tools filters.
- Upper-left `>_` terminal shortcut; upper-right settings icon.
- Settings → **Выбрать домашний экран** opens the system's Home selection
  interface. If a particular HyperOS regional build does not allow selecting
  third-party launchers, AXIOM cannot override Xiaomi's restriction.
- No root, bootloader changes, special Android permissions or system files
  are modified.

## Installation on Redmi / HyperOS
1. Export existing AXIOM favorites/workspaces first using AXIOM Settings →
   **Экспортировать настройки** (if upgrading from previous versions).
2. Download the `AXIOM-optimized-test-APK` artifact from a successful
   GitHub Actions run. This is R8-optimized but **signed with a temporary CI
   test key**, not production-signed.
3. Open the APK using Android's file manager and install. Confirm the Android
   install prompt. Only enable installation from that trusted file manager if
   Android requests it; turn the permission back off afterward.
4. Launch AXIOM. Open AXIOM settings → **Выбрать домашний экран**. Select
   **AXIOM** if offered and confirm. Alternative possible HyperOS paths:
   Android Settings → Home screen → Default launcher, or
   Android Settings → Apps → Manage apps → ⋮ / Other settings →
   Default apps → Launcher/Home.
5. Press Home; AXIOM should appear if your OS supports the choice.
6. If HyperOS disallows AXIOM as default, do not root or disable/remove the
   system launcher. You can still open AXIOM like an ordinary app.

## Safely restore Xiaomi Home
1. Settings → Apps → Default apps → Launcher/Home (or Settings → Home screen
   → Default launcher). Choose **System Launcher / Xiaomi System Launcher**.
2. Press Home to verify the Xiaomi desktop works.
3. Optionally uninstall AXIOM in Settings → Apps → Manage apps → AXIOM →
   Uninstall. Do **not** uninstall Xiaomi System Launcher or erase its data.

If the APK cannot update due to `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, the
CI signing key changed. First switch to Xiaomi System Launcher, export AXIOM
settings where possible, uninstall **only AXIOM**, reinstall, and import your
settings file. This has no effect on other installed Android apps.

## What was tested
Automated JVM regression tests cover symmetric forces, 2D collision momentum
and energy, long-run finite state, bounded body count, event/math exclusivity,
safe mathematical diagram zones and prior catalog/command logic. Android CI
builds debug and R8 optimized APK artifacts.

**Still requires real Redmi testing** for frame times, battery use and
regional HyperOS launcher restrictions. Do not claim guaranteed 120 FPS.
