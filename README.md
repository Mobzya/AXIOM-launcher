# AXIOM 0.5 — Emergent Universe

An experimental strictly monochrome Android HOME launcher. Designed for Redmi Note 13 Pro 4G.

## UX (preserved)
The homescreen stays nearly empty: centered translucent search with soft white halo; suggested apps **only after typing**. Swipe down on empty space to enter Niagara-inspired large-text app index with fast alphabet scrub. Top-right settings includes console, gravity control and a trigger for one random event.

## One living universe, but many emergent stories
- **Depth:** three layers of sparse stars, parallax, depth-scaled animated vector sprites.
- **Cross-screen travel:** objects originate beyond random screen corners, drift, bend under softened inter-body gravity, and depart; there is no mandatory central attractor, no hard wall bounces.
- **Procedural sprites:** comet streaks, wireframe asteroids, orbital satellites, neural clusters, traveling mathematical sine fragments.
- **Automatic episodes** every ~24–72 seconds after the initial event: meteor shower, neural propagation, Fourier harmonics, spacetime gravitational lens. Each produces its **own geometry and physics**, rather than simply displaying a formula.
- **Very rare singularity:** approximately 1% probability per automatically selected episode; a black event horizon, faint accretion disk and lensed trajectories, present briefly then gone. For testing, console command `event blackhole` can intentionally launch it.
- **Interactions:** tap empty space for a shockwave and momentum impulse; double tap to launch a comet from a corner; long press for a surprise event; horizontal drag for parallax depth, downward swipe for full app index.
- **Performance:** maximum 16 simultaneous bodies, O(n²) softened gravity only for that small set; fixed 60Hz physics at home and 30Hz behind the UI, vector graphics with no network textures and no heavy blur. This is a frame-rate target, not a measured guarantee on the Redmi.

## Terminal
Open through Settings → Command Console or use `> help` then Search from the central dock.

Try:
```text
events
event meteor
event neural
event wave
event lens
event blackhole
event
depth 0.5
gravity 1.1
spawn
physics
calc (5+3)^2
```
This is a SAFE command palette, not a Linux shell, and cannot execute arbitrary shell commands.

## Build and install
JDK 17 + Android SDK 36 + Gradle 8.13. GitHub Actions tests and produces the debug APK.
```bash
bash build-linux.sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
**No root, factory reset or firmware replacement.** Debug APK signatures may vary; if an update signature fails, switch to Xiaomi launcher and uninstall AXIOM only (resets AXIOM's preferences), then install.

## Limitations
Prototype only: real frame times, battery consumption, touch gestures on HyperOS and visual quality need real device validation. The physics is stylized, not an astronomical N-body accuracy solver.
