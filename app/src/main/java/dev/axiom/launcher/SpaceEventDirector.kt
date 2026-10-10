package dev.axiom.launcher

import kotlin.random.Random

/**
 * Manual "surprise" weighted toward ordinary scenes.
 * Automatic events are scheduled independently inside CosmicWorld.
 * Black holes remain rare even here, unless explicitly requested by console.
 */
internal fun CosmicWorld.triggerRandomEvent() {
    val kind = when (Random.nextInt(100)) {
        in 0..34 -> CosmicEventKind.METEORS
        in 35..61 -> CosmicEventKind.NEURAL
        in 62..82 -> CosmicEventKind.HARMONICS
        in 83..98 -> CosmicEventKind.LENS
        else -> CosmicEventKind.SINGULARITY
    }
    trigger(kind)
}
