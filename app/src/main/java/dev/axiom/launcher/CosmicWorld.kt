package dev.axiom.launcher

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * A compact 2D n-body toy universe in normalized screen coordinates.
 * Softened Newtonian attraction, momentum-preserving collision impulses,
 * capped velocities, elastic wall reflections and deterministic integration.
 *
 * A single universe replaces the previous scene presets. Elements can appear
 * irregularly, but the interactive UI never moves unpredictably.
 */
internal data class CelestialBody(
    val id: Int,
    val kind: Int,
    val mass: Float,
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val rotation: Float,
    val age: Float = 0f,
    val lifetime: Float = 120f
)

internal class CosmicWorld(seed: Int = 42) {
    private val random = Random(seed)
    var bodies by mutableStateOf(createInitial())
        private set
    var elapsed by mutableStateOf(0f)
        private set
    var gravity by mutableStateOf(1.0f)
        private set
    var interactionSignal by mutableStateOf(0f)
        private set
    private var nextId = 32
    private var nextArrival = 8f

    private fun createInitial(): List<CelestialBody> {
        val result = mutableListOf(
            CelestialBody(0, 0, 8f, .5f, .42f, 0f, 0f, 0f, lifetime = 100000f)
        )
        repeat(6) { i ->
            val a = i * (2 * PI / 6) + .4
            val r = .17 + (i % 3) * .065
            val px = .5 + r * cos(a)
            val py = .42 + r * sin(a) * .82
            val speed = .072 + (i % 3) * .012
            result += CelestialBody(
                i + 1, i % 5 + 1, .6f + i % 3 * .48f,
                px.toFloat(), py.toFloat(),
                (-sin(a) * speed).toFloat(),
                (cos(a) * speed * .82).toFloat(),
                (i * .51f),
                lifetime = 170f + i * 14f
            )
        }
        return result
    }

    fun updateGravity(value: Float) { gravity = value.coerceIn(.25f, 2.2f) }

    /** Advance at fixed 60Hz steps. Called from withFrameNanos, not from draw. */
    fun step(dt: Float) {
        val h = dt.coerceIn(.005f, .034f)
        elapsed += h
        interactionSignal = (interactionSignal - h * .95f).coerceAtLeast(0f)
        val current = bodies
        val n = current.size
        val ax = FloatArray(n)
        val ay = FloatArray(n)
        val velX = FloatArray(n) { current[it].vx }
        val velY = FloatArray(n) { current[it].vy }

        // Pairwise softened forces: F = G m1 m2 r / (r² + ε²)^(3/2).
        for (i in 0 until n) {
            for (j in i + 1 until n) {
                val a = current[i]
                val b = current[j]
                val dx = b.x - a.x
                val dy = b.y - a.y
                val r2 = dx * dx + dy * dy
                val softened = r2 + .0038f
                val factor = (.00065f * gravity / (softened * sqrt(softened)))
                    .coerceAtMost(3.2f)
                ax[i] += factor * b.mass * dx
                ay[i] += factor * b.mass * dy
                ax[j] -= factor * a.mass * dx
                ay[j] -= factor * a.mass * dy

                // Soft collision: keep small objects from stacking at the center.
                val collisionDistance = if (a.kind == 0 || b.kind == 0) .043f else .027f
                if (r2 > .000001f && r2 < collisionDistance * collisionDistance) {
                    val distance = sqrt(r2)
                    val nx = dx / distance
                    val ny = dy / distance
                    val relative = (velX[j] - velX[i]) * nx +
                        (velY[j] - velY[i]) * ny
                    if (relative < 0f) {
                        val impulse = -(1f + .67f) * relative /
                            (1f / a.mass + 1f / b.mass)
                        velX[i] -= impulse * nx / a.mass
                        velY[i] -= impulse * ny / a.mass
                        velX[j] += impulse * nx / b.mass
                        velY[j] += impulse * ny / b.mass
                    }
                    val repel = (collisionDistance - distance) * 2.1f
                    ax[i] -= nx * repel
                    ay[i] -= ny * repel
                    ax[j] += nx * repel
                    ay[j] += ny * repel
                }
            }
        }

        val updated = ArrayList<CelestialBody>(n + 1)
        for (i in current.indices) {
            val b = current[i]
            // The nucleus is heavy but not pinned: it reacts subtly to other masses.
            var vx = (velX[i] + ax[i].coerceIn(-1.3f, 1.3f) * h) * .9995f
            var vy = (velY[i] + ay[i].coerceIn(-1.3f, 1.3f) * h) * .9995f
            val speed = hypot(vx, vy)
            if (speed > .35f) {
                vx *= .35f / speed
                vy *= .35f / speed
            }
            var x = b.x + vx * h
            var y = b.y + vy * h
            if (x < .07f) { x = .07f; vx = kotlin.math.abs(vx) * .79f }
            if (x > .93f) { x = .93f; vx = -kotlin.math.abs(vx) * .79f }
            if (y < .09f) { y = .09f; vy = kotlin.math.abs(vy) * .79f }
            if (y > .88f) { y = .88f; vy = -kotlin.math.abs(vy) * .79f }
            val age = b.age + h
            if (b.id == 0 || age < b.lifetime) {
                updated += b.copy(x = x, y = y, vx = vx, vy = vy,
                    rotation = b.rotation + (.18f + .08f / b.mass) * h,
                    age = age)
            }
        }
        if (elapsed > nextArrival && updated.size < 11) {
            updated += createBody(random.nextFloat() * .7f + .15f,
                random.nextFloat() * .54f + .15f)
            nextArrival = elapsed + 7.5f + random.nextFloat() * 12f
        } else if (elapsed > nextArrival) {
            nextArrival = elapsed + 9f
        }
        bodies = updated
    }

    private fun createBody(x: Float, y: Float): CelestialBody {
        val a = random.nextFloat() * 2f * PI.toFloat()
        val speed = .025f + random.nextFloat() * .07f
        return CelestialBody(
            id = nextId++, kind = 1 + random.nextInt(5),
            mass = .5f + random.nextFloat() * 1.8f,
            x = x.coerceIn(.08f, .92f), y = y.coerceIn(.10f, .86f),
            vx = cos(a) * speed, vy = sin(a) * speed,
            rotation = a, lifetime = 45f + random.nextFloat() * 100f
        )
    }

    /** Touch the empty canvas: emit a body and push neighbors away. */
    fun disturb(x: Float, y: Float) {
        val pointX = x.coerceIn(.05f, .95f)
        val pointY = y.coerceIn(.05f, .93f)
        val next = bodies.map { b ->
            val dx = b.x - pointX
            val dy = b.y - pointY
            val distance = max(hypot(dx, dy), .035f)
            val push = (.022f / (distance + .075f)).coerceAtMost(.13f)
            b.copy(vx = b.vx + dx / distance * push / sqrt(b.mass),
                vy = b.vy + dy / distance * push / sqrt(b.mass))
        }.toMutableList()
        if (next.size < 11) next += createBody(pointX, pointY)
        bodies = next
        interactionSignal = 1f
    }

    fun reset() {
        bodies = createInitial()
        elapsed = 0f
        interactionSignal = 0f
        nextArrival = 8f
    }
}
