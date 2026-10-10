package dev.axiom.launcher

import kotlin.math.hypot
import kotlin.math.sqrt

/** A softly regularized, symmetric Newton-like force in screen coordinates.
 * Depth is an artistic perspective layer, not an astronomical third dimension.
 * Because action and reaction share one force, linear momentum is preserved.
 */
internal data class Force2(val x: Float, val y: Float)

internal data class PairAcceleration(
    val first: Force2,
    val second: Force2
)

internal fun mutualGravity(
    a: CelestialBody, b: CelestialBody, g: Float
): PairAcceleration {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val r2 = dx * dx + dy * dy + .0035f
    val coupling = 1f / (1f + .55f * kotlin.math.abs(a.depth - b.depth))
    val coefficient = (.00026f * g * coupling / (r2 * sqrt(r2)))
        .coerceAtMost(1.15f)
    return PairAcceleration(
        Force2(coefficient * b.mass * dx, coefficient * b.mass * dy),
        Force2(-coefficient * a.mass * dx, -coefficient * a.mass * dy)
    )
}

/** Short-range pressure uses equal and opposite forces (not equal accelerations).
 * This prevents bodies with very different mass from violating Newton's 3rd law.
 */
internal fun contactRepulsion(
    a: CelestialBody, b: CelestialBody, range: Float = .023f
): PairAcceleration {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val d = hypot(dx, dy)
    if (d < .000001f || d >= range ||
        kotlin.math.abs(a.depth - b.depth) >= .55f) {
        return PairAcceleration(Force2(0f, 0f), Force2(0f, 0f))
    }
    val pressure = (range - d) * .8f
    val fx = pressure * dx / d
    val fy = pressure * dy / d
    return PairAcceleration(
        Force2(-fx / a.mass, -fy / a.mass),
        Force2(fx / b.mass, fy / b.mass)
    )
}

internal data class CollisionVelocities(
    val avx: Float, val avy: Float,
    val bvx: Float, val bvy: Float
)

/** Impulse collision. Conserves pair linear momentum; e<=1 dissipates energy. */
internal fun collide(
    a: CelestialBody, b: CelestialBody,
    restitution: Float = .82f
): CollisionVelocities {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val length = hypot(dx, dy)
    val empty = CollisionVelocities(a.vx, a.vy, b.vx, b.vy)
    if (length < .000001f || length > .025f ||
        kotlin.math.abs(a.depth - b.depth) > .5f) return empty
    val nx = dx / length
    val ny = dy / length
    val relative = (b.vx - a.vx) * nx + (b.vy - a.vy) * ny
    if (relative >= 0f) return empty
    val impulse = -(1f + restitution.coerceIn(0f, 1f)) * relative /
        (1f / a.mass + 1f / b.mass)
    return CollisionVelocities(
        a.vx - impulse * nx / a.mass,
        a.vy - impulse * ny / a.mass,
        b.vx + impulse * nx / b.mass,
        b.vy + impulse * ny / b.mass
    )
}
