package dev.axiom.launcher

import kotlin.math.sqrt
import kotlin.random.Random

/** Numerically bounded, scalar Kalman estimation; not a placeholder animation. */
internal data class KalmanEstimate(val mean: Float = 0f, val variance: Float = 1f)

internal fun kalmanUpdate(
    previous: KalmanEstimate, measurement: Float,
    processVariance: Float, measurementVariance: Float
): KalmanEstimate {
    val prior=previous.variance+processVariance.coerceAtLeast(.0001f)
    val k=prior/(prior+measurementVariance.coerceAtLeast(.0001f))
    return KalmanEstimate(
        previous.mean+k*(measurement-previous.mean),
        ((1f-k)*prior).coerceAtLeast(.00001f)
    )
}

/** A single Lloyd iteration: each point belongs to its closest centroid.
 * Empty clusters retain their previous center, avoiding NaNs.
 */
internal fun kmeansStep(
    points: List<Pair<Float,Float>>, centers: List<Pair<Float,Float>>
): List<Pair<Float,Float>> {
    require(centers.isNotEmpty())
    val x=FloatArray(centers.size)
    val y=FloatArray(centers.size)
    val count=IntArray(centers.size)
    for ((px,py) in points){
        val nearest=centers.indices.minByOrNull { index ->
            val dx=px-centers[index].first
            val dy=py-centers[index].second
            dx*dx+dy*dy
        } ?: continue
        x[nearest]+=px
        y[nearest]+=py
        count[nearest]++
    }
    return centers.indices.map { i ->
        if(count[i]==0) centers[i]
        else (x[i]/count[i]) to (y[i]/count[i])
    }
}

/** Seeded diffusion experiment for replayable stochastic paths. */
internal fun brownianWalk(seed: Int, count: Int, volatility: Float)
    : List<Pair<Float,Float>> {
    require(count in 1..1000)
    val rng=Random(seed)
    var x=.5f
    var y=.5f
    val points=ArrayList<Pair<Float,Float>>(count)
    val step=volatility.coerceIn(.01f,.5f)/sqrt(count.toFloat())
    repeat(count){
        x=(x+(rng.nextFloat()+rng.nextFloat()-1f)*step).coerceIn(.04f,.96f)
        y=(y+(rng.nextFloat()+rng.nextFloat()-1f)*step).coerceIn(.04f,.96f)
        points+=x to y
    }
    return points
}
