package dev.axiom.launcher

import kotlin.math.sqrt

internal enum class FrontProfile(
    val duration: Float, val velocity: Float, val heating: Double, val color: Long
) {
    BLAST(7f, .085f, .00042, 0xfff4b477),
    COMPRESSION(8f, -.047f, .00062, 0xff83d6d5),
    OUTFLOW(9f, .11f, .00027, 0xffbba6ef);

    fun radius(progress: Float, maximum: Float): Float = when (this) {
        BLAST -> maximum * (.03f + .97f * sqrt(progress))
        COMPRESSION -> maximum * (1f - .94f * progress)
        OUTFLOW -> maximum * (.03f + .97f * progress)
    }

    fun angularStrength(alignment: Float): Float =
        if (this == OUTFLOW) ((alignment - .86f) / .14f).coerceIn(0f, 1f) else 1f
}

internal val CosmicEventKind.frontProfile: FrontProfile? get() = when (this) {
    CosmicEventKind.BLAST_SHELL -> FrontProfile.BLAST
    CosmicEventKind.REVERSE_SHOCK -> FrontProfile.COMPRESSION
    CosmicEventKind.BIPOLAR_OUTFLOW -> FrontProfile.OUTFLOW
    else -> null
}
