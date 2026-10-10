# AXIOM Launcher 0.6 — Kinetic Intelligence

A minimalist monochrome Android HOME launcher, designed for Redmi Note 13 Pro 4G (Kotlin + Jetpack Compose).

## Search & catalog
- True-black, near-empty home with one centered translucent search. It expands with a subdued white halo; suggestions appear **only after typing**.
- Downward swipe on empty home space enters a Niagara-inspired large-text alphabetical app catalog.
- **Catalog performance fix:** removes O(n²) item indexing and expensive per-item LazyList geometry scans; coalesces rail scrubbing requests to prevent scroll-command pileups.
- Favorites, filters, launcher app discovery, safe command palette, and persistent gravity preferences remain.

## Restored celestial interactions
- Softer but stronger pairwise gravity (roughly 7.4× base force, smaller softening term) and depth-aware interactions, capped accelerations and velocities to avoid violent clumping.
- Up to 18 active bodies. 12 **procedural sprite families** (comets, wireframe crystals, orbital systems, neural signals, wave fragments, binary stars, gyro rings, rotating lattices, pulsars, micro-galaxies and shock rings).
- New physical cinematic encounters: **nova shockwave, pulsar, wormhole, nebula flow**, plus existing meteor showers, gravitational lens and very rare black hole (~1% of automatically selected events). The auto director no longer chooses ML/Fourier episodes.
- Objects enter from random offscreen corners; layered stars, parallax and velocity-driven trails.
- Tap a visible object to inspect its mass, speed and depth for 4.5 seconds; tap empty space for a ripple; double tap for a new body; long press for a random cinematic episode; horizontal drag adjusts parallax.

## Mathematical ambient moments (independent of events)
16 unobtrusive microvisualizations, intermittently appearing at varying locations with **three coherent, curated formula transformation frames each**. Animated graphics and the staged formulas evolve together. Current catalog:
gradient descent, backpropagation, self-attention, matrices, Fourier decomposition, Bayes, sigmoid, convolution, information entropy, eigenvalues, quadratic roots, Mandelbrot iteration, PCA, ReLU, diffusion and residual networks.

The formula transformations are **authored demonstrations**, not a general-purpose symbolic algebra system or ML inference engine. This means the equations correspond to their illustrated concept, but no on-device neural network is being trained.

Terminal commands include `models`, `model attention`, `model fourier`, `model` (random), `timeline`, `event nova`, `event pulsar`, `event wormhole`, `event nebula`, `gravity 1.5`, `physics`, `spawn`, `calc`, `find`, `open` and more. The terminal does **not** execute arbitrary Android shell commands.

## Build
JDK 17, Android SDK Platform 36, Gradle 8.13:

```bash
bash build-linux.sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Automated GitHub Actions workflow runs unit tests and produces a debug APK artifact.

**Safety:** no root, ROM, factory reset or bootloader unlock. HyperOS may restrict system-level gesture navigation with external launchers. Debug APK signing mismatches may require uninstalling **only AXIOM** before reinstalling (AXIOM's preferences will reset). Frame rates and battery consumption require testing on the Redmi; compilation cannot establish actual 120 FPS. Scenes use capped, stylized physics rather than astronomical simulation.
