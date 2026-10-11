package dev.axiom.launcher

/** Three very rare possibilities per 4096 eligible cinematic event rolls.
 * The draw loop never performs random checks: eligibility is sampled once.
 */
internal fun rareCosmicEvent(roll: Int): CosmicEventKind? =
    when (roll) {
        0 -> CosmicEventKind.ECLIPSE
        1 -> CosmicEventKind.COSMIC_WEB
        2 -> CosmicEventKind.PHASE_TIDE
        else -> null
    }
