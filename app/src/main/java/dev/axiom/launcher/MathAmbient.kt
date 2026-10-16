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

    // A dedicated visual signature for the less common mathematical
    // families; do not recycle one of the twelve generic animations.
    // All paths are deterministically keyed by the lesson and its seed.
    val signature = when (moment.kind) {
        MathKind.LORENZ -> 12
        MathKind.PENDULUM -> 13
        MathKind.MANDELBROT, MathKind.JULIA -> 14
        MathKind.L_SYSTEM -> 15
        MathKind.MARKOV, MathKind.KALMAN -> 16
        MathKind.GAME_THEORY -> 17
        MathKind.REACTION_DIFFUSION -> 18
        MathKind.OPTICAL_FLOW -> 19
        MathKind.VORONOI -> 20
        MathKind.MANIFOLD, MathKind.PCA -> 21
        MathKind.MONTE_CARLO -> 22
        MathKind.FFT -> 23
        MathKind.BEZIER -> 24
        MathKind.KMEANS -> 25
        else -> moment.kind.ordinal % 12
    }
    when (signature) {
        12 -> { // Lorenz attractor: two lobes rather than another orbit
            val path = Path()
            for (i in 0..135) {
                val v = i / 135f
                val theta = v * 11.3f + wave*.15f
                val lobe = if (sin(theta*.49f) >= 0) 1f else -1f
                val radius = (.14f + .24f*(.5f+.5f*sin(theta*1.91f)))
                val x = px+scale*(1f+lobe*.50f+cos(theta)*radius)
                val y = py+scale*(.50f+sin(theta)*radius*1.15f)
                if(i==0)path.moveTo(x,y) else path.lineTo(x,y)
            }
            drawPath(path,AX_WHITE.copy(alpha=.45f*a),style=Stroke(.8.dp.toPx()))
        }
        13 -> { // Pendulum: pivots, arc and swinging actual bob
            val pivot = Offset(px+scale,py+scale*.12f)
            val length=scale*.98f
            val angle=sin(wave*1.5f)*.67f
            for(i in 0..22){
                val phi=-.85f+i*.077f
                val p=pivot+Offset(sin(phi)*length,cos(phi)*length)
                drawCircle(AX_WHITE.copy(alpha=.07f*a),.65.dp.toPx(),p)
            }
            val bob=pivot+Offset(sin(angle)*length,cos(angle)*length)
            drawLine(AX_WHITE.copy(alpha=.57f*a),pivot,bob,1.05.dp.toPx())
            drawCircle(AX_WHITE.copy(alpha=.90f*a),4.dp.toPx(),bob)
            drawCircle(AX_WHITE.copy(alpha=.55f*a),1.5.dp.toPx(),pivot)
        }
        14 -> { // Mandelbrot / Julia escape contours with actual iteration
            val julia=moment.kind==MathKind.JULIA
            for(i in 0..27)for(j in 0..15){
                val x=(i/27f-.5f)*2.6f
                val y=(j/15f-.5f)*1.7f
                var zr=if(julia)x else 0f
                var zi=if(julia)y else 0f
                val cr=if(julia)-.72f else x
                val ci=if(julia).27f else y
                var n=0
                while(n<14&&zr*zr+zi*zi<5f){
                    val nr=zr*zr-zi*zi+cr
                    zi=2f*zr*zi+ci
                    zr=nr;n++
                }
                if(n>=11 || (n+moment.seed).mod(4)==0)
                    drawCircle(AX_WHITE.copy(alpha=a*(.09f+n*.018f)),
                        .7.dp.toPx(),Offset(px+i/27f*scale*2f,
                            py+j/15f*scale))
            }
        }
        15 -> { // L-system: slowly growing, asymmetric recursive branching
            fun branch(x:Float,y:Float,angle:Float,len:Float,depth:Int){
                if(depth==0 || len<1.5f)return
                val end=Offset(x+cos(angle)*len,y-sin(angle)*len)
                drawLine(AX_WHITE.copy(alpha=a*(.13f+depth*.095f)),
                    Offset(x,y),end,(.35f+depth*.1f).dp.toPx())
                val tilt=.27f+moment.seed.mod(7)*.014f
                branch(end.x,end.y,angle+tilt,len*.69f,depth-1)
                branch(end.x,end.y,angle-.49f,len*.62f,depth-1)
            }
            branch(px+scale,py+scale*.99f,
                PI.toFloat()*.5f+sin(wave*.3f)*.09f,
                scale*.31f,5)
        }
        16 -> { // Markov / Kalman: changing probability-state graph
            val pts=List(5){i->
                val t=i*2f*PI.toFloat()/5f
                center+Offset(cos(t)*scale*.83f,sin(t)*scale*.51f)
            }
            for(i in pts.indices) {
                val j=(i+2)%pts.size
                drawLine(AX_WHITE.copy(alpha=.25f*a),pts[i],pts[j],.8.dp.toPx())
                val t=(moment.age*.21f+i*.17f)%1f
                drawCircle(AX_WHITE.copy(alpha=.72f*a),2.dp.toPx(),
                    pts[i]+(pts[j]-pts[i])*t)
                drawCircle(AX_WHITE.copy(alpha=.5f*a),2.6.dp.toPx(),pts[i])
            }
        }
        17 -> { // Game theory: payoff matrix and moving mixed-strategy point
            for(i in 0..2){
                drawLine(AX_WHITE.copy(alpha=.27f*a),
                    Offset(px+i*scale*.65f,py),Offset(px+i*scale*.65f,py+scale),
                    .7.dp.toPx())
                drawLine(AX_WHITE.copy(alpha=.27f*a),
                    Offset(px,py+i*scale*.5f),Offset(px+scale*1.3f,py+i*scale*.5f),
                    .7.dp.toPx())
            }
            val u=(.5f+.44f*sin(wave))
            val v=(.5f+.43f*cos(wave*.83f))
            drawCircle(AX_WHITE.copy(alpha=.9f*a),3.4.dp.toPx(),
                Offset(px+u*scale*1.3f,py+v*scale))
        }
        18 -> { // Turing pattern from interacting travelling modes
            for(i in 0..31)for(j in 0..13){
                val u=i*.34f;val v=j*.44f
                val f=sin(u+sin(v*.7f+wave*.34f))+
                    cos(v+cos(u*.61f-wave*.21f))
                if(f>.47f)
                    drawCircle(AX_WHITE.copy(alpha=a*(.16f+f*.12f)),
                        1.3.dp.toPx(),Offset(px+i*scale/16f,py+j*scale/13f))
            }
        }
        19 -> { // Optical flow: distinct vector field with moving heads
            for(i in 0..10)for(j in 0..5){
                val x=px+i*scale*.18f
                val y=py+j*scale*.17f
                val angle=sin(i*.6f+wave*.4f)-cos(j*.7f-wave*.3f)
                val dx=cos(angle)*scale*.10f
                val dy=sin(angle)*scale*.10f
                drawLine(AX_WHITE.copy(alpha=.32f*a),Offset(x,y),
                    Offset(x+dx,y+dy),.7.dp.toPx())
                drawCircle(AX_WHITE.copy(alpha=.70f*a),1.dp.toPx(),
                    Offset(x+dx,y+dy))
            }
        }
        20 -> { // Voronoi: cells hinted with weighted nearest points
            val pts=listOf(Offset(.15f,.22f),Offset(.83f,.15f),
                Offset(.37f,.83f),Offset(1.42f,.63f))
            for(i in 0..24)for(j in 0..12){
                val x=i/24f*1.7f
                val y=j/12f
                val nearest=pts.indices.minByOrNull { k ->
                    val d=pts[k]-Offset(x,y)
                    d.x*d.x+d.y*d.y
                } ?: 0
                if((i+j+nearest)%3==0)
                    drawCircle(AX_WHITE.copy(alpha=a*(.13f+nearest*.04f)),
                        .65.dp.toPx(),Offset(px+x*scale,py+y*scale))
            }
            pts.forEach{p->drawCircle(AX_WHITE.copy(alpha=.83f*a),
                2.3.dp.toPx(),Offset(px,py)+p*scale)}
        }
        21 -> { // Manifold embedding: warped lattice rather than rotating axes
            for(j in 0..6){
                val path=Path()
                for(i in 0..42) {
                    val u=i/42f
                    val x=px+u*2f*scale
                    val y=py+scale*(j/6f*.79f+
                        .11f*sin(u*9f+j*.58f+wave*.27f))
                    if(i==0)path.moveTo(x,y)else path.lineTo(x,y)
                }
                drawPath(path,AX_WHITE.copy(alpha=(.14f+j*.025f)*a),
                    style=Stroke(.65.dp.toPx()))
            }
        }
        22 -> { // Monte Carlo: quasi random cloud and sample histogram
            for(i in 0..96){
                val v=((i*73+moment.seed).mod(97))/97f
                val w=((i*37+moment.seed*3).mod(101))/101f
                if(v*v+w*w <= 1f)
                    drawCircle(AX_WHITE.copy(alpha=.19f*a),
                        .9.dp.toPx(),Offset(px+v*scale*1.8f,py+w*scale))
            }
            drawCircle(AX_WHITE.copy(alpha=.24f*a),scale,
                Offset(px,py),style=Stroke(.7.dp.toPx()))
        }
        23 -> { // FFT butterflies
            for(stage in 0..3)for(i in 0..7){
                val p=Offset(px+stage*scale*.48f,
                    py+i*scale*.13f)
                val other=i xor (1 shl stage.coerceAtMost(2))
                val q=Offset(px+(stage+1)*scale*.48f,
                    py+other*scale*.13f)
                drawLine(AX_WHITE.copy(alpha=.25f*a),p,q,.7.dp.toPx())
            }
        }
        24 -> { // Bezier: control points + animated De Casteljau
            val p0=Offset(px,py+scale)
            val p1=Offset(px+scale*.3f,py-scale*.26f)
            val p2=Offset(px+scale*1.5f,py+scale*1.15f)
            val p3=Offset(px+scale*2f,py+scale*.2f)
            val pts=listOf(p0,p1,p2,p3)
            for(i in 0..2)drawLine(AX_WHITE.copy(alpha=.18f*a),
                pts[i],pts[i+1],.7.dp.toPx())
            val path=Path()
            for(i in 0..48){
                val t=i/48f;val q=1f-t
                val p=p0*(q*q*q)+p1*(3f*q*q*t)+
                    p2*(3f*q*t*t)+p3*(t*t*t)
                if(i==0)path.moveTo(p.x,p.y)else path.lineTo(p.x,p.y)
            }
            drawPath(path,AX_WHITE.copy(alpha=.52f*a),style=Stroke(1.dp.toPx()))
            val t=(.5f+.48f*sin(wave*.46f))
            val q=1f-t
            val now=p0*(q*q*q)+p1*(3f*q*q*t)+
                p2*(3f*q*t*t)+p3*(t*t*t)
            drawCircle(AX_WHITE.copy(alpha=.9f*a),2.5.dp.toPx(),now)
        }
        25 -> { // KMeans: moving cluster centroids
            val centroids=listOf(Offset(.2f,.19f),Offset(1.4f,.25f),
                Offset(.86f,.83f))
            for(i in 0..48) {
                val c=i%3
                val t=i*2.39996f
                val spread=(.12f+(i%11)*.016f)*scale
                val p=Offset(px,py)+centroids[c]*scale+
                    Offset(cos(t+wave*.11f)*spread,
                        sin(t+wave*.11f)*spread)
                drawCircle(AX_WHITE.copy(alpha=.13f*a),1.25.dp.toPx(),p)
            }
            centroids.forEach{p->drawCircle(AX_WHITE.copy(alpha=.88f*a),
                3.1.dp.toPx(),Offset(px,py)+p*scale)}
        }
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
