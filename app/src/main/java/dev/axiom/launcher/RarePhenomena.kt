package dev.axiom.launcher

/** Exactly seven possibilities out of 16,384 automatic event checks.
 * Three classic phenomena and four truly exceptional events.
 * Manual event commands can still preview them at any time.
 */
internal fun rareCosmicEvent(roll: Int): CosmicEventKind? =
    when(roll) {
        0 -> CosmicEventKind.ECLIPSE
        1 -> CosmicEventKind.COSMIC_WEB
        2 -> CosmicEventKind.PHASE_TIDE
        4096 -> CosmicEventKind.MEGASTRUCTURE
        8192 -> CosmicEventKind.GALAXY_COLLISION
        12288 -> CosmicEventKind.VOID_BLOOM
        16383 -> CosmicEventKind.GRAVITATIONAL_CAUSTIC
        else -> null
    }
