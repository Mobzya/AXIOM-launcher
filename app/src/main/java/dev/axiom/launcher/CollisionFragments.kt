package dev.axiom.launcher

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Physical fragments carry mass and participate in all pairwise gravity,
 * collisions and reaccretion. Brief dust trails are VISUAL ONLY.
 */
internal data class ImpactFragments(val pieces: List<CelestialBody>) {
    val totalMass: Float get() = pieces.sumOf { it.mass.toDouble() }.toFloat()
    val momentumX: Float get() = pieces.sumOf { (it.mass*it.vx).toDouble() }.toFloat()
    val momentumY: Float get() = pieces.sumOf { (it.mass*it.vy).toDouble() }.toFloat()
}

/** Deterministic shattering conserves linear momentum and total mass.
 * Shards have finite lifetimes and cannot recursively shatter.
 */
internal fun splitAsteroid(body: CelestialBody, nextId: Int, capacity: Int)
    : ImpactFragments {
    if (!body.mass.isFinite() || body.mass < .9f || capacity < 2)
        return ImpactFragments(listOf(body))
    val count = capacity.coerceIn(2, 4)
    val weights = if (count == 2) listOf(.60f,.40f)
        else if(count == 3) listOf(.50f,.30f,.20f)
        else listOf(.42f,.28f,.18f,.12f)
    val angle=body.rotation
    val kicks=weights.indices.map { k ->
        val theta=angle+k*6.2831853f/count
        (.057f*cos(theta)) to (.057f*sin(theta))
    }
    val kickX=kicks.indices.sumOf { (weights[it]*kicks[it].first).toDouble() }.toFloat()
    val kickY=kicks.indices.sumOf { (weights[it]*kicks[it].second).toDouble() }.toFloat()
    return ImpactFragments(weights.indices.map { i ->
        val direction=kicks[i]
        body.copy(
            id=if(i==0)body.id else nextId+i-1,
            kind=35+(body.id+i*7).mod(24),
            mass=body.mass*weights[i],
            x=body.x + .012f*cos(angle+i*6.2831853f/count),
            y=body.y + .012f*sin(angle+i*6.2831853f/count),
            vx=body.vx+direction.first-kickX,
            vy=body.vy+direction.second-kickY,
            age=0f,
            lifetime=max(body.lifetime-body.age,38f),
            sizeFactor=if(i==0) .91f else .76f
        )
    })
}

/** Normalize collision/contact radii. Unlike a fixed threshold, radius scales
 * sublinearly with mass and respects real visual body size.
 */
internal fun contactSize(body: CelestialBody): Float = structureRadius(body)

/** Reflect only when heading OUT of the field: newly spawned bodies may
 * still enter from outside corners, even with reflective walls active.
 */
internal fun reflectAtWalls(body: CelestialBody, restitution: Float = .78f)
    : CelestialBody {
    val radius=contactSize(body)*.65f
    var x=body.x;var y=body.y;var vx=body.vx;var vy=body.vy
    val e=restitution.coerceIn(0f,1f)
    if (x < radius && vx < 0f) {x=radius;vx=-vx*e}
    if (x > 1f-radius && vx > 0f) {x=1f-radius;vx=-vx*e}
    if (y < radius && vy < 0f) {y=radius;vy=-vy*e}
    if (y > 1f-radius && vy > 0f) {y=1f-radius;vy=-vy*e}
    return if(x==body.x&&y==body.y&&vx==body.vx&&vy==body.vy)
        body else body.copy(x=x,y=y,vx=vx,vy=vy)
}

internal data class ImpactDust(
    val x: Float,val y: Float,val vx: Float,val vy: Float,
    val age: Float=0f,val lifetime: Float=2.8f
)

/** Debris animation decoupled from quadratic real body physics. */
internal fun driftDust(p: ImpactDust,dt: Float): ImpactDust =
    p.copy(x=p.x+p.vx*dt,y=p.y+p.vy*dt,
        vx=p.vx*(1f-.28f*dt),vy=p.vy*(1f-.28f*dt),
        age=p.age+dt)
