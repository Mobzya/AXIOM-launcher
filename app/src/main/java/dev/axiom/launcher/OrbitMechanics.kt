package dev.axiom.launcher

import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sqrt

/** Shared units and softened Newtonian law for the solver, seeds and observer. */
internal object OrbitMechanics {
    const val G = .000052
    const val SOFTENING = .008

    fun circularSpeed(mass: Float, radius: Float, gravity: Float = 1f): Float {
        val r = radius.toDouble().coerceAtLeast(.00001)
        return sqrt(G * gravity * mass * r*r /
            (r*r + SOFTENING*SOFTENING).pow(1.5)).toFloat()
    }

    fun acceleration(body: CelestialBody, bodies: List<CelestialBody>): Pair<Double, Double> {
        var ax = 0.0; var ay = 0.0
        for (source in bodies) {
            if (source.id == body.id) continue
            val dx = (source.x-body.x).toDouble(); val dy = (source.y-body.y).toDouble()
            val r2 = dx*dx + dy*dy + SOFTENING*SOFTENING
            val factor = G*source.mass/(r2*sqrt(r2))
            ax += factor*dx; ay += factor*dy
        }
        return ax to ay
    }

    fun orbit(body: CelestialBody, primary: CelestialBody, gravity: Float): OrbitReading {
        val dx = (body.x-primary.x).toDouble(); val dy = (body.y-primary.y).toDouble()
        val vx = (body.vx-primary.vx).toDouble(); val vy = (body.vy-primary.vy).toDouble()
        val r = hypot(dx,dy).coerceAtLeast(.00001)
        val mu = G*gravity*(body.mass+primary.mass)
        val energy = .5*(vx*vx+vy*vy)-mu/r
        val angular = dx*vy-dy*vx
        // Osculating Kepler elements; meaningful outside the softened core.
        val e = sqrt((1.0+2.0*energy*angular*angular/(mu*mu)).coerceAtLeast(0.0))
        return OrbitReading(r, hypot(vx,vy), e, energy < 0.0, angular)
    }
}

internal data class OrbitReading(val distance: Double, val speed: Double,
    val eccentricity: Double, val bound: Boolean, val angularMomentum: Double)

internal data class LearningReading(val samples: Int = 0, val gravity: Double = 1.0,
    val relativeError: Double? = null, val excitation: Double = 0.0)

/** One-parameter online ridge regression: a_observed ≈ β a_Newton(G₀).
 * Score each new sample BEFORE fitting it. Only local simulated measurements
 * enter this model; it observes the solver and never steers the physics. */
internal class GravityLearner {
    private var xx = 1e-12
    private var xy = 1e-12
    private var errors = 0.0
    private var signals = 0.0
    private var count = 0
    val reading get() = LearningReading(count, xy/xx,
        if (count >= 24 && signals > 1e-12) sqrt(errors/signals) else null, xx)

    fun observe(before: List<CelestialBody>, after: List<CelestialBody>, dt: Float) {
        if (dt <= 0f || !dt.isFinite()) return
        for (p in before) {
            val q = after.firstOrNull { it.id == p.id } ?: continue
            if (after.any { it.id != q.id && hypot(it.x-q.x,it.y-q.y) < .065f }) continue
            val f = OrbitMechanics.acceleration(p, before)
            val ax = (q.vx-p.vx)/dt; val ay = (q.vy-p.vy)/dt
            val feature = f.first*f.first+f.second*f.second
            if (feature < 1e-10 || !ax.isFinite() || !ay.isFinite()) continue
            val beta = xy/xx
            val ex = ax-beta*f.first; val ey = ay-beta*f.second
            errors = .985*errors+ex*ex+ey*ey
            signals = .985*signals+ax*ax+ay*ay
            xx = .998*xx+feature
            xy = .998*xy+f.first*ax+f.second*ay
            count++
        }
    }
}

/** A square simulation viewport: circles and forces have identical x/y units. */
internal data class UniverseViewport(val width: Float, val height: Float) {
    val side = minOf(width,height).coerceAtLeast(1f)
    val left = (width-side)*.5f
    val top = (height-side)*.5f
    fun x(value: Float) = left+value*side
    fun y(value: Float) = top+value*side
    fun normalized(x: Float, y: Float): Pair<Float, Float>? {
        val nx = (x-left)/side; val ny = (y-top)/side
        return if (nx in 0f..1f && ny in 0f..1f) nx to ny else null
    }
}
