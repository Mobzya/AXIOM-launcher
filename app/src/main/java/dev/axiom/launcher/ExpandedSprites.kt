package dev.axiom.launcher

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** 62 detailed combinations plus 8 immense space structures.
 * All artwork is drawn procedurally in the current black/white palette.
 * No bitmap loads, shader compilation or allocation of giant textures.
 */
internal fun DrawScope.drawExpandedBody(
    b: CelestialBody, center: Offset, baseRadius: Float, alpha: Float, time: Float
) {
    if(alpha<.002f)return
    val foreground=AX_WHITE
    val background=AX_BLACK
    val giant=b.kind in 97..104
    val family=if(giant)(b.kind-97)%8 else (b.kind-35)%12
    val variant=if(giant) b.kind-97 else (b.kind-35)/12
    val radius=if(giant) {
        // Giant structures occupy 35–55% of the short screen dimension.
        min(size.width,size.height) * (.17f + (variant%4)*.027f)
    } else baseRadius*(1.2f+variant*.31f)
    val phase=b.rotation+time*(.10f+variant*.014f)
    val line=.6.dp.toPx()
    val halo=foreground.copy(alpha=alpha*.14f)
    val thin=foreground.copy(alpha=alpha*.36f)
    val edge=foreground.copy(alpha=alpha*.72f)
    val core=foreground.copy(alpha=alpha*.94f)

    // Thin structural depth shells, not flat monochrome icons.
    if(!giant && family%3==0){
        drawCircle(halo,radius*1.45f,center,style=Stroke(line))
        drawCircle(halo,radius*2.0f,center,style=Stroke(line*.6f))
    }
    when(family) {
        0 -> { // Protostellar accretion disks
            for(k in 0..3){
                rotate(phase*22f+k*19f,center) {
                    val rx=radius*(2.9f-k*.35f)
                    drawOval(foreground.copy(alpha=alpha*(.15f+k*.12f)),
                        center-Offset(rx,rx*.24f),
                        Size(rx*2f,rx*.48f),style=Stroke(line))
                }
            }
            drawCircle(halo,radius*.9f,center)
            drawCircle(core,radius*.29f,center)
        }
        1 -> { // Magnetar field loops
            for(k in 0..4) {
                rotate(k*34f+phase*9f,center) {
                    drawOval(thin,
                        center-Offset(radius*2.5f,radius*.52f),
                        Size(radius*5f,radius*1.04f),style=Stroke(line))
                }
            }
            drawCircle(core,radius*.37f,center)
        }
        2 -> { // Radiating quasar with jets
            val axis=Offset(cos(phase),sin(phase))
            drawLine(halo,center-axis*radius*6f,
                center+axis*radius*6f,3.0.dp.toPx())
            drawLine(edge,center-axis*radius*4.8f,
                center+axis*radius*4.8f,line)
            drawCircle(halo,radius*1.5f,center)
            drawCircle(core,radius*.39f,center)
            for(k in 0..9){
                val phi=k*2f*PI.toFloat()/10f+phase*.2f
                drawCircle(thin,radius*.12f,
                    center+Offset(cos(phi),sin(phi))*radius*1.7f)
            }
        }
        3 -> { // Granular spiral galaxy
            for(arm in 0..2)for(j in 0..32){
                val u=j/32f
                val angle=arm*2f*PI.toFloat()/3f+u*PI.toFloat()*3.5f+phase
                val rr=radius*(.1f+u*3.1f)
                val p=center+Offset(cos(angle)*rr,sin(angle)*rr*.58f)
                drawCircle(foreground.copy(alpha=alpha*(.55f-u*.39f)),
                    (if(j%6==0)1.35f else .75f).dp.toPx(),p)
            }
            drawCircle(core,radius*.20f,center)
        }
        4 -> { // Black-ring structure and lens lines
            drawCircle(halo,radius*2.6f,center,style=Stroke(line))
            drawCircle(edge,radius*1.9f,center,style=Stroke(line*1.4f))
            drawCircle(background.copy(alpha=.90f),radius*.9f,center)
            drawCircle(edge,radius*.93f,center,style=Stroke(line))
            for(k in 0..15) {
                val theta=k*2f*PI.toFloat()/16f+phase*.17f
                drawLine(thin,
                    center+Offset(cos(theta),sin(theta))*radius*2f,
                    center+Offset(cos(theta),sin(theta))*radius*2.5f,line)
            }
        }
        5 -> { // Lattice / tensor network
            val nodes=Array(18){k->
                val x=(k%6-2.5f)*radius*.9f
                val y=(k/6-1f)*radius*1.0f
                center+Offset(x+sin(k*.8f+phase)*radius*.08f,
                    y+cos(k*.56f+phase)*radius*.08f)
            }
            nodes.forEachIndexed{k,p->
                if(k%6<5)drawLine(thin,p,nodes[k+1],line)
                if(k+6<18)drawLine(halo,p,nodes[k+6],line)
                drawCircle(if(k%5==0)core else thin,
                    (if(k%5==0)1.5f else .8f).dp.toPx(),p)
            }
        }
        6 -> { // Triple star interactions
            for(k in 0..2){
                val phi=phase*(.8f+k*.2f)+k*2f*PI.toFloat()/3f
                val p=center+Offset(cos(phi)*radius*2.1f,
                    sin(phi)*radius*1.0f)
                drawLine(halo,center,p,line)
                drawCircle(thin,radius*(.24f+k*.04f),p,
                    style=Stroke(line))
                drawCircle(core,radius*.12f,p)
            }
        }
        7 -> { // Planet/ice world, terminator and atmosphere
            drawCircle(halo,radius*1.58f,center,
                style=Stroke(line))
            drawCircle(edge,radius*.91f,center)
            val shadow=center+Offset(cos(phase*.23f),sin(phase*.23f))*
                radius*.34f
            drawCircle(background.copy(alpha=.7f),radius*.73f,shadow)
            drawCircle(thin,radius*.95f,center,style=Stroke(line))
            rotate(phase*18f,center) {
                drawOval(edge,center-Offset(radius*1.8f,radius*.35f),
                    Size(radius*3.6f,radius*.7f),style=Stroke(line))
            }
        }
        8 -> { // Shockwave shells
            for(k in 0..6){
                val r=radius*(.5f+k*.44f+.15f*sin(phase+k*.45f))
                drawCircle(foreground.copy(alpha=alpha*(.48f-k*.058f)),
                    r,center,style=Stroke(line))
            }
        }
        9 -> { // Molecular filaments and turbulent gas
            for(arm in 0..4){
                val path=Path()
                for(j in 0..28){
                    val u=j/28f
                    val phi=arm*1.25f+phase*.2f+u*9f
                    val rr=radius*(.4f+u*3.0f)
                    val p=center+Offset(cos(phi)*rr,sin(phi)*rr*.58f)
                    if(j==0)path.moveTo(p.x,p.y)
                    else path.lineTo(p.x,p.y)
                }
                drawPath(path,foreground.copy(alpha=alpha*(.15f+arm*.065f)),
                    style=Stroke(line))
            }
        }
        10 -> { // Hexagonal crystal/icy structure
            for(ring in 1..3){
                val rr=radius*ring
                for(k in 0..5) {
                    val t1=k*PI.toFloat()/3f+phase*.16f
                    val t2=(k+1)*PI.toFloat()/3f+phase*.16f
                    val p1=center+Offset(cos(t1)*rr,sin(t1)*rr)
                    val p2=center+Offset(cos(t2)*rr,sin(t2)*rr)
                    drawLine(thin,p1,p2,line)
                    if(ring==3)drawLine(halo,center,p1,line*.55f)
                }
            }
        }
        else -> { // Double helical ion streams
            for(chain in 0..1){
                val path=Path()
                for(j in 0..36){
                    val u=j/36f
                    val x=(u-.5f)*radius*6.2f
                    val y=sin(u*PI.toFloat()*5f+phase+
                        chain*PI.toFloat())*radius*.9f
                    if(j==0)path.moveTo(center.x+x,center.y+y)
                    else path.lineTo(center.x+x,center.y+y)
                }
                drawPath(path,foreground.copy(alpha=alpha*.48f),
                    style=Stroke(line))
            }
        }
    }
}
