package dev.axiom.launcher

import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Rich, independent event behavior: all graphics remain below app controls. */
internal fun DrawScope.drawAdditionalEvent(
    ep: CosmicEpisode, unit: Float, opacity: Float
) {
    if (opacity < .002f)return
    val ink=AX_WHITE
    val center=Offset(ep.x*size.width,ep.y*size.height)
    val age=ep.age
    val alpha=opacity
    when(ep.kind){
        CosmicEventKind.ECHO_SHELL -> {
            for(j in 0..9){
                val r=unit*(.025f+(j+ep.progress*5f)*.033f)
                drawCircle(ink.copy(alpha=alpha*(.28f-j*.022f)
                    .coerceAtLeast(.02f)),r,center,
                    style=Stroke(.6.dp.toPx()))
            }
        }
        CosmicEventKind.TIDAL_CAPTURE -> {
            for(arm in 0..2){
                val path=Path()
                for(j in 0..60){
                    val u=j/60f
                    val angle=u*PI.toFloat()*6f+age*.14f+
                        arm*2f*PI.toFloat()/3f
                    val r=unit*(.02f+.30f*u)
                    val p=center+Offset(cos(angle)*r,sin(angle)*r*.7f)
                    if(j==0)path.moveTo(p.x,p.y)
                    else path.lineTo(p.x,p.y)
                }
                drawPath(path,ink.copy(alpha=alpha*.38f),
                    style=Stroke(.65.dp.toPx()))
            }
        }
        CosmicEventKind.POLAR_JET -> {
            for(side in listOf(-1f,1f)){
                for(k in 0..5){
                    val x=center.x+sin(age*.19f+k)*unit*.025f
                    val end=Offset(x,center.y+side*unit*(.25f+k*.045f))
                    drawLine(ink.copy(alpha=alpha*(.45f-k*.06f)),
                        center,end,.65.dp.toPx())
                }
            }
            drawCircle(ink.copy(alpha=alpha*.7f),unit*.015f,center)
        }
        CosmicEventKind.ORBITAL_RESONANCE -> {
            val p=Path()
            for(j in 0..155){
                val u=j/155f*2f*PI.toFloat()
                val r=unit*(.21f+.04f*sin(u*5f+age*.24f))
                val point=center+Offset(cos(u*3f+age*.045f)*r,
                    sin(u*4f+age*.045f)*r*.70f)
                if(j==0)p.moveTo(point.x,point.y)
                else p.lineTo(point.x,point.y)
            }
            drawPath(p,ink.copy(alpha=alpha*.38f),
                style=Stroke(.75.dp.toPx()))
        }
        CosmicEventKind.STELLAR_NURSERY -> {
            for(i in 0..69){
                val theta=i*2.39996f+age*.014f
                val r=unit*(.05f+.25f*i/70f)
                val p=center+Offset(cos(theta)*r,sin(theta)*r*.60f)
                drawCircle(ink.copy(alpha=alpha*
                    (.15f+(i%7)*.055f)),
                    (if(i%12==0)2f else .8f).dp.toPx(),p)
            }
        }
        CosmicEventKind.DARK_FLOW -> {
            for(j in 0..11){
                val y=size.height*(j+.5f)/12f
                val wav=sin(age*.22f+j*.39f)*unit*.035f
                drawLine(ink.copy(alpha=alpha*(.13f+j*.01f)),
                    Offset(-unit*.2f,y+wav),
                    Offset(size.width+unit*.2f,y+wav+unit*.11f),
                    .8.dp.toPx())
            }
        }
        CosmicEventKind.GEOMETRY_SHIFT -> {
            for(i in 0..11){
                val x=size.width*i/11f
                val off=sin(i*.61f+age*.2f)*unit*.04f
                drawLine(ink.copy(alpha=alpha*.22f),
                    Offset(x+off,0f),Offset(x-off,size.height),
                    .55.dp.toPx())
            }
            for(j in 0..17){
                val y=size.height*j/17f
                drawLine(ink.copy(alpha=alpha*.16f),
                    Offset(0f,y),Offset(size.width,y),
                    .55.dp.toPx())
            }
        }
        CosmicEventKind.SOLAR_FLARE -> {
            for(j in 0..63){
                val theta=j*2f*PI.toFloat()/64f
                val inner=unit*.06f
                val outer=inner+unit*(.06f+.10f*
                    (1f+sin(theta*9f+age*.27f))*.5f)
                drawLine(ink.copy(alpha=alpha*(.12f+.28f*(j%4)/4f)),
                    center+Offset(cos(theta)*inner,sin(theta)*inner),
                    center+Offset(cos(theta)*outer,sin(theta)*outer),
                    .7.dp.toPx())
            }
        }
        CosmicEventKind.DUAL_RING -> {
            for(orb in 0..1){
                val c=if(orb==0)center
                    else Offset((1f-ep.x)*size.width,center.y)
                for(j in 0..4){
                    val r=unit*(.05f+j*.055f+ep.progress*.08f)
                    drawCircle(ink.copy(alpha=alpha*(.32f-j*.045f)),
                        r,c,style=Stroke(.65.dp.toPx()))
                }
            }
        }
        CosmicEventKind.PARTICLE_CASCADE -> {
            for(j in 0..95){
                val u=j/96f
                val theta=j*2.39996f+age*.12f
                val r=unit*(u*.32f+.01f)
                drawCircle(ink.copy(alpha=alpha*
                    (.12f+.52f*(1f-u))),
                    (if(j%11==0)2.2f else 1f).dp.toPx(),
                    center+Offset(cos(theta)*r,sin(theta)*r))
            }
        }
        else -> Unit
    }
}

/** Four exceptionally rare scenarios shift the ENTIRE visual field.
 * Uses strokes and rings instead of expensive blur, bitmap or GPU shaders.
 */
internal fun DrawScope.drawUltraRareBackdrop(ep: CosmicEpisode) {
    if(ep.kind !in setOf(CosmicEventKind.MEGASTRUCTURE,
            CosmicEventKind.GALAXY_COLLISION,
            CosmicEventKind.VOID_BLOOM,
            CosmicEventKind.GRAVITATIONAL_CAUSTIC))return
    val opacity=ep.alpha
    if(opacity<.002f)return
    val ink=AX_WHITE
    val bg=AX_BLACK
    val unit=min(size.width,size.height)
    val c=Offset(ep.x*size.width,ep.y*size.height)
    // On dark background become a bright fog; in white mode invert to ink.
    drawRect(ink.copy(alpha=opacity*.16f))
    when(ep.kind) {
        CosmicEventKind.MEGASTRUCTURE -> {
            for(j in 1..10){
                val r=unit*(.05f+j*.052f)
                drawCircle(bg.copy(alpha=opacity*.5f),
                    r,c,style=Stroke((j%3+1).dp.toPx()))
                drawCircle(ink.copy(alpha=opacity*(.16f+
                    .35f*(j%3)/3f)),r,c,style=Stroke(.75.dp.toPx()))
                for(k in 0..11){
                    val theta=k*PI.toFloat()/6f + j*.23f+ep.age*.008f
                    val p=c+Offset(cos(theta)*r,sin(theta)*r*.75f)
                    drawCircle(ink.copy(alpha=opacity*.47f),
                        1.dp.toPx(),p)
                }
            }
            drawCircle(bg.copy(alpha=.86f),unit*.095f,c)
        }
        CosmicEventKind.GALAXY_COLLISION -> {
            for(galaxy in 0..1){
                val origin=Offset(
                    (if(galaxy==0).18f+.20f*ep.progress else
                        .82f-.20f*ep.progress)*size.width,
                    (if(galaxy==0).36f else .64f)*size.height
                )
                for(arm in 0..2)for(j in 0..64) {
                    val u=j/64f
                    val angle=arm*2f*PI.toFloat()/3f+
                        u*PI.toFloat()*4f+ep.age*.05f*
                        if(galaxy==0)1f else -1f
                    val r=unit*(.02f+.41f*u)
                    val p=origin+Offset(cos(angle)*r,sin(angle)*r*.45f)
                    drawCircle(ink.copy(alpha=opacity*
                        (.55f-.42f*u)),.8.dp.toPx(),p)
                }
                drawCircle(ink.copy(alpha=opacity*.67f),
                    unit*.012f,origin)
            }
        }
        CosmicEventKind.VOID_BLOOM -> {
            for(k in 0..42){
                val theta=k*2f*PI.toFloat()/43f
                val radius=unit*(.06f+.37f*ep.progress)
                val p=c+Offset(cos(theta)*radius,sin(theta)*radius)
                val outer=c+Offset(cos(theta)*(radius+unit*.12f),
                    sin(theta)*(radius+unit*.12f))
                drawLine(ink.copy(alpha=opacity*.36f),
                    p,outer,.75.dp.toPx())
            }
            for(k in 0..9){
                val radius=unit*(.03f+.053f*k)
                drawCircle(ink.copy(alpha=opacity*
                    (.38f-k*.032f)),radius,c,style=Stroke(.8.dp.toPx()))
            }
            drawCircle(bg.copy(alpha=.92f),
                unit*(.03f+.37f*ep.progress),c)
            drawCircle(ink.copy(alpha=opacity*.85f),
                unit*(.03f+.37f*ep.progress),c,
                style=Stroke(1.dp.toPx()))
        }
        CosmicEventKind.GRAVITATIONAL_CAUSTIC -> {
            for(j in 0..17){
                val y=j/17f*size.height
                for(i in 0..2){
                    val path=Path()
                    for(k in 0..56){
                        val x=k/56f*size.width
                        val dy=sin(x/unit*(1.5f+i*.3f)+
                            ep.age*.15f+j*.3f)*unit*.15f
                        val px=x+sin(y/unit*5f+j*.6f)*unit*.023f
                        val py=y+dy/(1f+i)
                        if(k==0)path.moveTo(px,py)
                        else path.lineTo(px,py)
                    }
                    drawPath(path,ink.copy(alpha=opacity*
                        (.08f+i*.06f)),style=Stroke(.6.dp.toPx()))
                }
            }
        }
        else -> Unit
    }
}
