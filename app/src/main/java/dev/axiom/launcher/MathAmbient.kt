package dev.axiom.launcher

import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Lightweight mathematical micro-visualizations, independent of space events. */
internal enum class MathKind {
    GRADIENT, BACKPROP, ATTENTION, MATRIX, FOURIER, BAYES, SIGMOID,
    CONVOLUTION, ENTROPY, EIGEN, QUADRATIC, MANDELBROT, PCA, RELU,
    DIFFUSION, RESIDUAL, LIFE, JULIA, LORENZ, PENDULUM,
    MARKOV, BEZIER, GAUSSIAN, LAPLACIAN, TRANSFORMER, SVD,
    SOFTMAX, OPTICAL_FLOW, REACTION_DIFFUSION, L_SYSTEM,
    KALMAN, KMEANS, MONTE_CARLO, VORONOI, FFT, HESSIAN,
    SPECTRAL_GRAPH, BROWNIAN, HOPFIELD, GAME_THEORY,
    DIJKSTRA, MANIFOLD
}

internal data class MathMoment(
    val kind: MathKind = MathKind.GRADIENT,
    val age: Float = 0f,
    val duration: Float = 0f,
    val x: Float = .18f,
    val y: Float = .2f,
    val seed: Int = 0
) {
    val active get() = duration > 0f
    val progress get() = if (duration <= 0f) 0f else (age / duration).coerceIn(0f, 1f)
    val opacity get(): Float {
        if (!active) return 0f
        return (minOf(1f, progress * 6f) *
            minOf(1f, (1f - progress) * 6f)).coerceIn(0f, 1f)
    }
}

private data class EquationSteps(
    val caption: String,
    val frames: List<String>
)

/** Shared glyphs are preserved while changing terms are removed or inserted. */
internal data class EquationMorph(
    val prefix: String, val removed: String,
    val inserted: String, val suffix: String
)

internal fun equationMorph(from: String, to: String): EquationMorph {
    val prefixLength = from.zip(to).takeWhile { it.first == it.second }.size
    val shorter = minOf(from.length, to.length)
    var suffixLength = 0
    while (suffixLength < shorter - prefixLength &&
        from[from.lastIndex - suffixLength] == to[to.lastIndex - suffixLength]) {
        suffixLength++
    }
    return EquationMorph(
        prefix = from.take(prefixLength),
        removed = from.substring(prefixLength, from.length - suffixLength),
        inserted = to.substring(prefixLength, to.length - suffixLength),
        suffix = if (suffixLength == 0) "" else from.takeLast(suffixLength)
    )
}

/** Curated, mathematically consistent steps, not arbitrary symbolic claims. */
private val formulaAtlas = mapOf(
    MathKind.GRADIENT to EquationSteps("GRADIENT DESCENT",
        listOf("L(w) = (w - 3)²", "∂L/∂w = 2(w - 3)", "w ← w - η·2(w - 3)")),
    MathKind.BACKPROP to EquationSteps("BACKPROPAGATION",
        listOf("∂L/∂w = (∂L/∂ŷ)(∂ŷ/∂w)", "ŷ = wx  ⇒  ∂ŷ/∂w = x", "∂L/∂w = (∂L/∂ŷ)·x")),
    MathKind.ATTENTION to EquationSteps("SELF-ATTENTION",
        listOf("S = QKᵀ / √d", "A = softmax(S)", "Y = AV")),
    MathKind.MATRIX to EquationSteps("MATRIX PRODUCT",
        listOf("[1  3] · [2; 4]", "1·2 + 3·4", "14")),
    MathKind.FOURIER to EquationSteps("SPECTRAL DECOMPOSITION",
        listOf("f(t) = sin(t) + ½sin(3t)", "a₁ = 1,   a₃ = ½", "peaks: ω = 1, 3")),
    MathKind.BAYES to EquationSteps("BAYESIAN INFERENCE",
        listOf("P(A|B) = P(B|A)P(A)/P(B)", "0.8 · 0.2 / 0.4", "P(A|B) = 0.4")),
    MathKind.SIGMOID to EquationSteps("ACTIVATION",
        listOf("σ(x) = 1/(1+e⁻ˣ)", "σ(0) = 1/(1+1)", "σ(0) = 0.5")),
    MathKind.CONVOLUTION to EquationSteps("CONVOLUTION",
        listOf("y = Σᵢ kᵢxᵢ", "2·1 + 1·2", "y = 4")),
    MathKind.ENTROPY to EquationSteps("INFORMATION THEORY",
        listOf("H = -Σp log₂(p)", "p = (½, ½)", "H = 1 bit")),
    MathKind.EIGEN to EquationSteps("EIGENVALUES",
        listOf("Av = λv", "A = diag(2, 3)", "λ₁ = 2,  λ₂ = 3")),
    MathKind.QUADRATIC to EquationSteps("POLYNOMIAL ROOTS",
        listOf("2x² - 8x + 6 = 0", "x² - 4x + 3 = 0", "(x-1)(x-3) = 0")),
    MathKind.MANDELBROT to EquationSteps("ITERATED COMPLEX MAP",
        listOf("zₙ₊₁ = zₙ² + c", "z₀ = 0  ⇒  z₁ = c", "z₂ = c² + c")),
    MathKind.PCA to EquationSteps("PRINCIPAL COMPONENTS",
        listOf("Σ = XᵀX / n", "Σv₁ = λ₁v₁", "z = Xv₁")),
    MathKind.RELU to EquationSteps("RECTIFIED LINEAR UNIT",
        listOf("ReLU(x) = max(0, x)", "x = -2", "ReLU(-2) = 0")),
    MathKind.DIFFUSION to EquationSteps("DIFFUSION FORWARD",
        listOf("xₜ = √ᾱₜ x₀ + √(1-ᾱₜ)ε", "ε ~ N(0, I)", "xₜ = signal + noise")),
    MathKind.RESIDUAL to EquationSteps("RESIDUAL NETWORK",
        listOf("h = F(x) + x", "F(x) = W₂σ(W₁x)", "h = W₂σ(W₁x) + x")),
    MathKind.LIFE to EquationSteps("CELLULAR AUTOMATON",
        listOf("N = Σ neighbors", "B3 / S23", "xₜ₊₁ = rule(xₜ)")),
    MathKind.JULIA to EquationSteps("JULIA FRACTAL",
        listOf("zₙ₊₁ = zₙ² + c", "c = -0.7 + 0.2i", "|z| > 2 ⇒ escape")),
    MathKind.LORENZ to EquationSteps("LORENZ SYSTEM",
        listOf("dx/dt = σ(y-x)", "dy/dt = x(ρ-z)-y", "dz/dt = xy-βz")),
    MathKind.PENDULUM to EquationSteps("PENDULUM",
        listOf("τ = -mgL sin θ", "I θ'' = τ", "θ'' = -(g/L)sin θ")),
    MathKind.MARKOV to EquationSteps("MARKOV CHAIN",
        listOf("pₜ₊₁ = pₜ P", "Pᵢⱼ ≥ 0; Σⱼ Pᵢⱼ=1", "p₂ = p₀ P²")),
    MathKind.BEZIER to EquationSteps("BEZIER CURVE",
        listOf("B(t)=(1-t)P₀+tP₁", "Q₀=(1-t)P₀+tP₁", "B₂=(1-t)Q₀+tQ₁")),
    MathKind.GAUSSIAN to EquationSteps("NORMAL DISTRIBUTION",
        listOf("Z=(X-μ)/σ", "X ~ N(μ, σ²)", "E[X]=μ, Var[X]=σ²")),
    MathKind.LAPLACIAN to EquationSteps("GRAPH LAPLACIAN",
        listOf("L = D - A", "L·1 = 0", "xᵀLx = Σedges (xᵢ-xⱼ)²")),
    MathKind.TRANSFORMER to EquationSteps("TRANSFORMER BLOCK",
        listOf("H₁ = X + Attention(X)", "H₂ = LayerNorm(H₁)", "Y = H₂ + FFN(H₂)")),
    MathKind.SVD to EquationSteps("SINGULAR VALUE DECOMP",
        listOf("A = UΣVᵀ", "AᵀA = VΣ²Vᵀ", "rank(A)=#{σᵢ>0}")),
    MathKind.SOFTMAX to EquationSteps("NORMALIZED EXPONENTIAL",
        listOf("sᵢ=e^(zᵢ)/Σⱼe^(zⱼ)", "z=(0,0)", "s=(½,½)")),
    MathKind.OPTICAL_FLOW to EquationSteps("OPTICAL FLOW",
        listOf("I(x,y,t)=const", "Iₓu + Iᵧv + Iₜ=0", "∇I·v = -Iₜ")),
    MathKind.REACTION_DIFFUSION to EquationSteps("TURING PATTERNS",
        listOf("∂u/∂t=Dᵤ∇²u + f", "∂v/∂t=Dᵥ∇²v + g", "diffusion + reaction")),
    MathKind.L_SYSTEM to EquationSteps("L-SYSTEM",
        listOf("axiom: F", "F → F[+F]F[-F]", "n ← n + 1")),
    MathKind.KALMAN to EquationSteps("KALMAN FILTER",
        listOf("K = P⁻Hᵀ(HP⁻Hᵀ+R)⁻¹", "x⁺ = x⁻ + K(z-Hx⁻)",
            "P⁺ = (I-KH)P⁻")),
    MathKind.KMEANS to EquationSteps("K-MEANS CLUSTERING",
        listOf("cᵢ = argminₖ ||xᵢ-μₖ||²", "μₖ ← mean({xᵢ:cᵢ=k})",
            "repeat until stable")),
    MathKind.MONTE_CARLO to EquationSteps("MONTE CARLO",
        listOf("E[f(X)] ≈ (1/N)Σf(Xᵢ)", "Xᵢ ~ p(x)",
            "error scales as N⁻¹ᐟ²")),
    MathKind.VORONOI to EquationSteps("VORONOI CELLS",
        listOf("Vᵢ = {x: ||x-pᵢ|| ≤ ||x-pⱼ||}",
            "bisectors divide plane", "assign nearest seed")),
    MathKind.FFT to EquationSteps("FAST FOURIER TRANSFORM",
        listOf("Xₖ = Σxₙ exp(-i2πkn/N)", "split even and odd n",
            "T(N)=2T(N/2)+O(N)")),
    MathKind.HESSIAN to EquationSteps("SECOND DERIVATIVES",
        listOf("Hᵢⱼ = ∂²f/∂xᵢ∂xⱼ", "f=x²+y²", "H=diag(2,2)")),
    MathKind.SPECTRAL_GRAPH to EquationSteps("SPECTRAL GRAPH THEORY",
        listOf("L=D-A", "Lv=λv", "λ₂ reveals connectivity")),
    MathKind.BROWNIAN to EquationSteps("RANDOM WALK",
        listOf("Xₜ₊Δt=Xₜ+σ√Δt·Z", "Z ~ N(0,1)", "Var(Xₜ)=σ²t")),
    MathKind.HOPFIELD to EquationSteps("HOPFIELD MEMORY",
        listOf("E=-½Σᵢⱼwᵢⱼsᵢsⱼ", "sᵢ ← sign(Σⱼwᵢⱼsⱼ)",
            "symmetric weights lower E")),
    MathKind.GAME_THEORY to EquationSteps("MIXED STRATEGY",
        listOf("pᵀAq = expected payoff", "p≥0, Σpᵢ=1",
            "best response maximizes pᵀAq")),
    MathKind.DIJKSTRA to EquationSteps("SHORTEST PATH",
        listOf("d(source)=0, others=∞", "d(v)←min(d(v),d(u)+w)",
            "settle smallest tentative d")),
    MathKind.MANIFOLD to EquationSteps("MANIFOLD EMBEDDING",
        listOf("local geometry: gᵢⱼ", "ds²=Σgᵢⱼdxⁱdxʲ",
            "coordinates change, ds invariant"))
)

internal fun mathSteps(kind: MathKind): List<String> = formulaAtlas.getValue(kind).frames

/**
 * Draws an animated micro-scene plus three successive derivation frames.
 * Small screen footprint; does not open panels or move launcher controls.
 */
internal fun DrawScope.drawMathMoment(moment: MathMoment, dim: Float, paint: Paint) {
    if (!moment.active || moment.opacity < .005f) return
    val lesson = formulaAtlas.getValue(moment.kind)
    val a = moment.opacity * dim * .72f
    val px = moment.x * size.width
    val py = moment.y * size.height
    val scale = minOf(size.width * .18f, size.height * .11f)
    val wave = moment.age * .6f
    val center = Offset(px + scale * .6f, py + scale * .4f)

    // Each family uses a different piece of animated geometry.
    when (moment.kind.ordinal % 12) {
        0 -> { // descent over a parabola
            val path = Path()
            for (i in 0..30) {
                val u = i / 30f
                val x = px + u * scale * 2f
                val y = py + scale * (.64f - .53f * (2f * u - 1f) * (2f * u - 1f))
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, AX_WHITE.copy(alpha = .22f * a),
                style = Stroke(.8.dp.toPx()))
            val step = (.13f + .72f * (1f - kotlin.math.exp(-moment.age * .25f)))
            val gx = px + scale * 2f * step
            val gy = py + scale * (.64f - .53f * (2f * step - 1f) * (2f * step - 1f))
            drawCircle(AX_WHITE.copy(alpha = a), 2.6.dp.toPx(), Offset(gx, gy))
        }
        1 -> { // neural graph with flowing signals
            val pts = listOf(Offset(0f,.1f),Offset(0f,.7f),
                Offset(.8f,0f),Offset(.8f,.45f),Offset(.8f,.9f),
                Offset(1.6f,.45f))
            val edges = listOf(0 to 2, 0 to 3, 1 to 3, 1 to 4,
                2 to 5, 3 to 5, 4 to 5)
            edges.forEachIndexed { i,(from,to) ->
                val p = Offset(px,py) + pts[from] * scale
                val q = Offset(px,py) + pts[to] * scale
                drawLine(AX_WHITE.copy(alpha = .2f * a), p,q,.75.dp.toPx())
                val progress = (moment.age * .13f + i * .19f) % 1f
                drawCircle(AX_WHITE.copy(alpha = .75f * a),
                    1.6.dp.toPx(), p + (q-p) * progress)
            }
            pts.forEach { p ->
                drawCircle(AX_WHITE.copy(alpha = .65f * a), 2.dp.toPx(),
                    Offset(px,py)+p*scale)
            }
        }
        2 -> { // streaming attention matrix
            for (i in 0..3) for (j in 0..3) {
                val phase = (moment.age * .4f + (i+j)*.15f)
                val brightness = .2f + .35f * (.5f + .5f * sin(phase))
                drawCircle(AX_WHITE.copy(alpha = a * brightness),
                    3.dp.toPx(), Offset(px + (i * .36f + .2f)*scale,
                        py + (j * .36f + .08f)*scale))
            }
        }
        3 -> { // moving matrix bracket / vector
            for (i in 0..2) for(j in 0..2) {
                val vx = px + scale * (.2f + i*.45f)
                val vy = py + scale * (.15f + j*.40f)
                drawCircle(AX_WHITE.copy(alpha = a * (.45f + .35f*
                    sin(wave + i + j).coerceIn(0f,1f))),
                    2.6.dp.toPx(), Offset(vx,vy))
            }
            drawLine(AX_WHITE.copy(alpha = .25f*a),
                Offset(px,py),Offset(px,py+scale),.7.dp.toPx())
            drawLine(AX_WHITE.copy(alpha = .25f*a),
                Offset(px+scale,py),Offset(px+scale,py+scale),.7.dp.toPx())
        }
        4 -> { // interference and Fourier waves
            for(k in 1..3) {
                val path = Path()
                for(i in 0..48) {
                    val u=i/48f
                    val p=Offset(px+u*scale*2f,py+scale*.4f+
                        sin(u * k * 6.28f + wave)*scale*.15f/k)
                    if(i==0)path.moveTo(p.x,p.y) else path.lineTo(p.x,p.y)
                }
                drawPath(path,AX_WHITE.copy(alpha = a * (.30f/k)),
                    style = Stroke(.7.dp.toPx()))
            }
        }
        5 -> { // concentric inference probability rings
            for(k in 1..4) {
                val r=scale*(.16f*k+.08f)
                drawCircle(AX_WHITE.copy(alpha = .09f*a + .18f*a/k),
                    r,center,style=Stroke(.75.dp.toPx()))
            }
            val phi=wave*1.2f
            drawCircle(AX_WHITE.copy(alpha = .75f*a),2.dp.toPx(),
                center+Offset(cos(phi)*scale*.65f,sin(phi)*scale*.65f))
        }
        6 -> { // sigmoid / activation curve
            val path=Path()
            for(i in 0..40) {
                val u=i/40f
                val y=1f/(1f+kotlin.math.exp((.5f-u)*11f))
                val p=Offset(px+u*scale*2f,py+scale*(.85f-y*.7f))
                if(i==0)path.moveTo(p.x,p.y) else path.lineTo(p.x,p.y)
            }
            drawPath(path,AX_WHITE.copy(alpha=.35f*a),style=Stroke(.9.dp.toPx()))
        }
        7 -> { // branching eigen / PCA / polynomial axes
            val angle=moment.age*.16f
            val r=scale*.72f
            for(k in 0..3){
                val phi=angle+k*PI.toFloat()*.5f
                drawLine(AX_WHITE.copy(alpha=.24f*a),center,
                    center+Offset(cos(phi)*r,sin(phi)*r),.8.dp.toPx())
            }
            drawCircle(AX_WHITE.copy(alpha=.68f*a),2.4.dp.toPx(),center)
        }
        8 -> { // Voronoi-style particles and nearest regions
            val seeds=listOf(Offset(.2f,.25f),Offset(1.4f,.2f),
                Offset(.75f,1.15f),Offset(1.65f,1.0f))
            for(i in 0..26)for(j in 0..12){
                val x=i/26f*scale*1.9f
                val y=j/12f*scale*1.15f
                val seed=seeds.indices.minByOrNull { k->
                    val dx=x/scale-seeds[k].x
                    val dy=y/scale-seeds[k].y
                    dx*dx+dy*dy
                } ?: 0
                if((i+j+seed)%4==0)
                    drawCircle(AX_WHITE.copy(alpha=a*(.12f+seed*.07f)),
                        .6.dp.toPx(),Offset(px+x,py+y))
            }
            seeds.forEach { p ->
                drawCircle(AX_WHITE.copy(alpha=a*.65f),
                    2.1.dp.toPx(),Offset(px,py)+p*scale)
            }
        }
        9 -> { // Fourier spectroscopy bars
            for(j in 0..12){
                val x=px+scale*(.1f+j*.15f)
                val amplitude=(.2f+.6f*kotlin.math.abs(
                    sin(j*.58f+moment.age*.16f)))
                drawLine(AX_WHITE.copy(alpha=a*.4f),
                    Offset(x,py+scale),
                    Offset(x,py+scale*(1f-amplitude)),.85.dp.toPx())
                drawCircle(AX_WHITE.copy(alpha=a*.52f),
                    1.5.dp.toPx(),Offset(x,py+scale*(1f-amplitude)))
            }
        }
        10 -> { // Stochastic Brownian diffusion traces
            for(j in 0..4){
                val path=Path()
                var y=py+scale*.12f+j*scale*.20f
                for(i in 0..25){
                    val x=px+i/25f*scale*2f
                    y+=sin(i*2.49f+j*1.38f+moment.age*.22f)*scale*.065f
                    if(i==0)path.moveTo(x,y)else path.lineTo(x,y)
                }
                drawPath(path,AX_WHITE.copy(alpha=a*.33f),
                    style=Stroke(.65.dp.toPx()))
            }
        }
        else -> { // Graph edges and signal propagation
            val nodes=List(8){i->
                val phi=i*2f*PI.toFloat()/8f
                center+Offset(cos(phi)*scale*.65f,
                    sin(phi)*scale*.55f)
            }
            nodes.forEachIndexed {i,p->
                val q=nodes[(i+3)%8]
                drawLine(AX_WHITE.copy(alpha=a*.25f),
                    p,q,.65.dp.toPx())
                drawCircle(AX_WHITE.copy(alpha=a*.67f),
                    (1.6f+.7f*sin(moment.age*.5f+i)).dp.toPx(),p)
            }
        }
    }

    // Three real mathematical steps with eased opacity between frames.
    val normalized = (moment.progress / .94f).coerceIn(0f, .999f)
    val position = normalized * 3f
    val first = position.toInt().coerceIn(0,2)
    val next = (first + 1).coerceAtMost(2)
    val blend = ((position-first-.72f)/.28f).coerceIn(0f,1f)
    val baseX = px
    val baseY = py + scale*1.25f
    // All math occupies one of four disjoint safe zones; bound each line to
    // the right edge instead of drawing offscreen on narrow Android displays.
    val availableWidth = (size.width - baseX - 16.dp.toPx())
        .coerceAtLeast(size.width * .19f)
    paint.textSize = 8.sp.toPx()
    paint.alpha = (a*.58f*255f).toInt().coerceIn(0,255)
    drawIntoCanvas { it.nativeCanvas.drawText(
        lesson.caption,baseX,baseY,paint) }
    paint.textSize = 11.sp.toPx()
    val longest = lesson.frames.maxOf { paint.measureText(it) }
    paint.textSize *= (availableWidth / longest.coerceAtLeast(1f))
        .coerceIn(.54f, 1f)
    val baseline = baseY + 17.dp.toPx()
    val oldEquation = lesson.frames[first]
    val newEquation = lesson.frames[next]
    if (first == next || blend <= .001f) {
        paint.alpha = (a*255f).toInt().coerceIn(0,255)
        drawIntoCanvas { it.nativeCanvas.drawText(
            oldEquation,baseX,baseline,paint) }
    } else {
        val fragments = equationMorph(oldEquation, newEquation)
        val prefixWidth = paint.measureText(fragments.prefix)
        val deletedWidth = paint.measureText(fragments.removed)
        val insertedWidth = paint.measureText(fragments.inserted)
        val reducedMotion = blend * blend * (3f - 2f * blend)
        // Unchanged symbols remain stable; outgoing terms drift and fade;
        // new terms settle onto the baseline. Shared suffix slides into place.
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            paint.alpha = (a*255f).toInt().coerceIn(0,255)
            native.drawText(fragments.prefix,baseX,baseline,paint)
            native.drawText(fragments.suffix,
                baseX + prefixWidth + deletedWidth * (1f-reducedMotion) +
                    insertedWidth * reducedMotion,baseline,paint)
            paint.alpha = (a*(1f-reducedMotion)*255f).toInt().coerceIn(0,255)
            native.drawText(fragments.removed,baseX+prefixWidth-
                9.dp.toPx()*reducedMotion,baseline,paint)
            paint.alpha = (a*reducedMotion*255f).toInt().coerceIn(0,255)
            native.drawText(fragments.inserted,baseX+prefixWidth+
                9.dp.toPx()*(1f-reducedMotion),baseline,paint)
        }
        if (blend in .25f.. .85f && fragments.removed.isNotBlank()) {
            drawLine(AX_WHITE.copy(alpha = a*.18f),
                Offset(baseX+prefixWidth,baseline-4.dp.toPx()),
                Offset(baseX+prefixWidth+deletedWidth*(1f-blend),
                    baseline-4.dp.toPx()), .65.dp.toPx())
        }
    }
    // Quiet underline indicates a running symbolic transformation.
    drawLine(AX_WHITE.copy(alpha = a*.23f),
        Offset(baseX,baseY+23.dp.toPx()),
        Offset(baseX + scale*2f * normalized,baseY+23.dp.toPx()),
        .7.dp.toPx())
}
