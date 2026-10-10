package dev.axiom.launcher

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Actual small numerical experiments. All steps are local and bounded. */
internal enum class LabKind {
    WAVE, NEURAL, LIFE, JULIA, LORENZ, PENDULUM, REACTION,
    FOURIER, ATTENTION, GRADIENT, DIFFUSION, PCA
}

internal fun sigmoid(x: Double) = 1.0 / (1.0 + exp(-x.coerceIn(-35.0, 35.0)))

internal data class NeuralState(
    val w1: Double = .4, val w2: Double = -.3, val bias: Double = .05,
    val input1: Double = .75, val input2: Double = .25,
    val target: Double = .7
) {
    val output get() = sigmoid(w1*input1 + w2*input2 + bias)
    val error get() = .5 * (output-target)*(output-target)

    fun train(rate: Double): NeuralState {
        val y = output
        val delta = (y-target)*y*(1-y)
        return copy(
            w1 = w1-rate*delta*input1,
            w2 = w2-rate*delta*input2,
            bias = bias-rate*delta
        )
    }
}

internal class ScienceLab(seed: Int = 19) {
    private val rng = Random(seed)
    var kind by mutableStateOf(LabKind.WAVE)
        private set
    var parameter by mutableStateOf(.5f)
        private set
    var secondParameter by mutableStateOf(.4f)
        private set
    var time by mutableStateOf(0f)
        private set
    var neural by mutableStateOf(NeuralState())
        private set

    // 30 x 20, updated ten times per second only in the dedicated lab screen.
    val columns = 30
    val rows = 20
    var cells by mutableStateOf(BooleanArray(columns*rows) { rng.nextFloat() < .29f })
        private set
    var reactionV by mutableStateOf(FloatArray(columns*rows) { idx ->
        val x=idx%columns
        val y=idx/columns
        if (x in 12..17 && y in 7..12) .75f else 0f
    })
        private set
    private var reactionU = FloatArray(columns*rows) { idx ->
        if(reactionV[idx]>.5f) .45f else 1f
    }
    private var accumulator = 0f

    fun select(newKind: LabKind) {
        kind = newKind
        time = 0f
        accumulator = 0f
    }
    fun tune(p: Float) { parameter = p.coerceIn(0f, 1f) }
    fun tuneSecondary(p: Float) { secondParameter = p.coerceIn(0f, 1f) }

    fun step(dt: Float) {
        val h = dt.coerceIn(0f, .08f)
        if(kind !in listOf(LabKind.JULIA,LabKind.LIFE,LabKind.REACTION,
                LabKind.ATTENTION,LabKind.PCA)) time += h
        accumulator += h
        if (kind == LabKind.LIFE && accumulator >= .12f) {
            cells = lifeStep(cells, columns, rows)
            accumulator = 0f
        }
        if (kind == LabKind.NEURAL && accumulator >= .15f) {
            neural = neural.train(.02 + parameter * .75)
            accumulator = 0f
        }
        if (kind == LabKind.REACTION && accumulator >= .09f) {
            val (u,v)=grayScottStep(reactionU,reactionV,columns,rows,
                feed=.02f+parameter*.05f,kill=.046f+secondParameter*.027f)
            reactionU=u
            reactionV=v
            accumulator = 0f
        }
    }

    fun reseed() {
        cells = BooleanArray(columns*rows) { rng.nextFloat() < .26f }
        reactionV = FloatArray(columns*rows) { idx ->
            val x=idx%columns; val y=idx/columns
            if(x in 12..17 && y in 7..12) .75f else 0f
        }
        reactionU = FloatArray(columns*rows) { idx ->
            if(reactionV[idx]>.5f) .45f else 1f
        }
        neural = NeuralState()
        time = 0f
    }

    fun toggleCell(x: Int, y: Int) {
        if (x !in 0 until columns || y !in 0 until rows) return
        cells = cells.copyOf().also {
            val idx = y*columns+x
            it[idx] = !it[idx]
        }
    }

    fun formula(): String = when (kind) {
        LabKind.WAVE -> "f(x)=%.2f sin(%.2f x - t)".formatUS(
            0.2f + parameter * 1.8f, 1.0f + secondParameter * 7.0f)
        LabKind.NEURAL -> "ŷ=%.4f   L=%.5f".formatUS(neural.output, neural.error)
        LabKind.LIFE -> "cell(t+1) = B3 / S23  ·  %d alive".format(cells.count { it })
        LabKind.JULIA -> "zₙ₊₁=zₙ²+(%.2f%+.2fi)".formatUS(
            -.8f + parameter*.8f, -.3f+secondParameter*.6f)
        LabKind.LORENZ -> "dx/dt=σ(y-x)  σ=%.1f".formatUS(4.0f + parameter*18.0f)
        LabKind.PENDULUM -> "θ''=-(g/L)sin θ, L=%.2f".formatUS(.3f+parameter*2.5f)
        LabKind.REACTION -> "∂u/∂t=Dᵤ∇²u-uv²+F(1-u)"
        LabKind.FOURIER -> "f=sin(x)+%.2f sin(3x)".formatUS(parameter)
        LabKind.ATTENTION -> "softmax(QKᵀ/√d), T=%.2f".formatUS(.25f+parameter*2)
        LabKind.GRADIENT -> "∂(x-a)²/∂x = 2(x-%.2f)".formatUS(1f+parameter*3)
        LabKind.DIFFUSION -> "xₜ=√ᾱx₀+√(1-ᾱ)ε  ᾱ=%.2f".formatUS(parameter)
        LabKind.PCA -> "projection = x·v, θ=%.2f".formatUS(parameter*PI.toFloat())
    }
}

private fun String.formatUS(vararg args: Any): String =
    String.format(java.util.Locale.US, this, *args)

/** Dead neighbors count on a finite board; bounded, deterministic behavior. */
internal fun lifeStep(before: BooleanArray, width: Int, height: Int): BooleanArray {
    require(width > 0 && height > 0 && before.size == width*height)
    val after = BooleanArray(before.size)
    for (y in 0 until height) for (x in 0 until width) {
        var neighbors = 0
        for (dy in -1..1) for (dx in -1..1) {
            if (dx == 0 && dy == 0) continue
            val nx = x+dx
            val ny = y+dy
            if (nx in 0 until width && ny in 0 until height &&
                before[ny*width+nx]) neighbors++
        }
        val index = y*width+x
        after[index] = neighbors == 3 || (before[index] && neighbors == 2)
    }
    return after
}

/** The stable Julia iteration: escapes to infinity if |z| exceeds 2. */
internal fun juliaIterations(x: Double,y: Double, cr: Double, ci: Double,
    maxIterations: Int = 32): Int {
    var zr = x
    var zi = y
    for(i in 0 until maxIterations) {
        if (zr*zr+zi*zi>4.0) return i
        val nx = zr*zr-zi*zi+cr
        zi = 2*zr*zi+ci
        zr = nx
    }
    return maxIterations
}

/** Explicit Euler Lorenz attractor, small dt for numerical stability. */
internal fun lorenzPath(sigma: Double, count: Int = 340): List<Pair<Double,Double>> {
    val path = ArrayList<Pair<Double,Double>>(count)
    var x=1.0; var y=1.0; var z=1.0
    val dt=.004
    repeat(count) {
        val dx=sigma*(y-x)
        val dy=x*(28-z)-y
        val dz=x*y-(8.0/3.0)*z
        x+=dx*dt
        y+=dy*dt
        z+=dz*dt
        path += x to z
    }
    return path
}

/** Spatially varying wave approximation and damped pendulum for exploration. */
internal fun waveValue(x: Float, time: Float, amp: Float, frequency: Float): Float =
    amp*sin(x*frequency-time)
internal fun pendulumAngle(t: Float,length: Float,amplitude: Float): Float =
    amplitude*cos(sqrt(9.81f/length.coerceAtLeast(.2f))*t)*exp(-.018f*t)


/** Gray–Scott two-species finite-difference reaction–diffusion, bounded grid. */
internal fun grayScottStep(
    u: FloatArray,v: FloatArray,width: Int,height: Int,
    feed: Float,kill: Float
): Pair<FloatArray,FloatArray> {
    require(width>2 && height>2 && width*height==u.size && v.size==u.size)
    val nu=u.copyOf()
    val nv=v.copyOf()
    for(y in 1 until height-1)for(x in 1 until width-1){
        val i=y*width+x
        val lu=u[i-1]+u[i+1]+u[i-width]+u[i+width]-4*u[i]
        val lv=v[i-1]+v[i+1]+v[i-width]+v[i+width]-4*v[i]
        val uvv=u[i]*v[i]*v[i]
        nu[i]=(u[i]+.16f*lu-uvv+feed*(1f-u[i])).coerceIn(0f,1f)
        nv[i]=(v[i]+.08f*lv+uvv-(kill+feed)*v[i]).coerceIn(0f,1f)
    }
    return nu to nv
}

/** Scaled dot-product attention: normalized probabilities sum to one. */
internal fun attentionWeights(
    query: DoubleArray,keys: Array<DoubleArray>,temperature: Double
): DoubleArray {
    require(query.isNotEmpty() && keys.isNotEmpty() &&
        temperature>0 && temperature.isFinite())
    val denom=sqrt(query.size.toDouble())*temperature
    val scores=keys.map { key ->
        require(key.size==query.size)
        key.indices.sumOf { key[it]*query[it] }/denom
    }
    val largest=scores.maxOrNull() ?: 0.0
    val powers=scores.map { exp((it-largest).coerceIn(-30.0,30.0)) }
    val total=powers.sum()
    return DoubleArray(keys.size) { powers[it]/total }
}
