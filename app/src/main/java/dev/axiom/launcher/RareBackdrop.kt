package dev.axiom.launcher

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.min

/**
 * Dramatic monochrome backdrop, drawn BEHIND the app search and all controls.
 * Rendering work is bounded and only occurs during ultra-rare episodes.
 */
internal fun DrawScope.drawRareBackdrop(ep: CosmicEpisode) {
    if (ep.kind !in listOf(CosmicEventKind.ECLIPSE,
            CosmicEventKind.COSMIC_WEB,CosmicEventKind.PHASE_TIDE)) return
    val alpha=ep.alpha
    if(alpha<=.002f)return
    val fg=AX_WHITE
    val background=AX_BLACK
    val mid=Offset(size.width*.5f,size.height*.47f)
    val unit=min(size.width,size.height)
    // Strong contrast change: 35% gray-wash in dark mode, 22% in white.
    val wash=if(AxiomColors.palette.light) Color.Black else Color.White
    drawRect(wash.copy(alpha=alpha*
        if(AxiomColors.palette.light) .25f else .34f))
    drawRect(brush=Brush.radialGradient(
        colors=listOf(
            wash.copy(alpha=alpha*.20f),
            wash.copy(alpha=alpha*.02f),
            Color.Transparent
        ),
        center=mid,radius=unit*1.35f
    ))
    when(ep.kind){
        CosmicEventKind.ECLIPSE -> {
            // A large occluding disk with several moving diffraction rings.
            val center=Offset(ep.x*size.width,ep.y*size.height)
            val radius=unit*(.15f+.028f*sin(ep.age*.11f))
            for (j in 4 downTo 1){
                val rr=radius*(1f+j*.24f)
                drawCircle(fg.copy(alpha=alpha*(.06f+j*.032f)),
                    rr,center,style=Stroke((.6f+j*.17f).dp.toPx()))
            }
            drawCircle(background.copy(alpha=1f),radius,center)
            drawCircle(fg.copy(alpha=alpha*.85f),radius*1.06f,
                center,style=Stroke(1.5.dp.toPx()))
            for(i in 0..27) {
                val phi=i*2f*PI.toFloat()/28f+ep.age*.013f
                val d=radius*(1.14f+(i%5)*.042f)
                val a=center+Offset(cos(phi)*d,sin(phi)*d)
                val b=center+Offset(cos(phi)*(d+unit*.045f),
                    sin(phi)*(d+unit*.045f))
                drawLine(fg.copy(alpha=alpha*(.18f+(i%4)*.06f)),
                    a,b,.65.dp.toPx())
            }
        }
        CosmicEventKind.COSMIC_WEB -> {
            // A deep network of filaments converging on an emerging graph.
            val cols=7
            val rows=11
            val points=Array(rows*cols){idx->
                val x=idx%cols
                val y=idx/cols
                val jitter=sin(idx*1.83f+ep.age*.10f)*.026f
                Offset(size.width*((x+.5f)/cols+jitter),
                    size.height*((y+.5f)/rows+
                        cos(idx*.83f+ep.age*.07f)*.018f))
            }
            for(y in 0 until rows)for(x in 0 until cols){
                val idx=y*cols+x
                val p=points[idx]
                if(x+1<cols)drawLine(fg.copy(alpha=alpha*.32f),
                    p,points[idx+1],.55.dp.toPx())
                if(y+1<rows)drawLine(fg.copy(alpha=alpha*.27f),
                    p,points[idx+cols],.55.dp.toPx())
                if(y+1<rows && x+1<cols && (x+y)%3==0)
                    drawLine(fg.copy(alpha=alpha*.15f),
                        p,points[idx+cols+1],.55.dp.toPx())
                drawCircle(fg.copy(alpha=alpha*(.35f+.3f*
                    (1f+sin(ep.age*.23f+idx))/2f)),
                    if(idx%7==0)1.8.dp.toPx() else .9.dp.toPx(),p)
            }
        }
        CosmicEventKind.PHASE_TIDE -> {
            // The WHOLE sky takes on a flowing, interfering wave lattice.
            for(layer in 0..16){
                val path=Path()
                for(i in 0..56){
                    val x=i/56f
                    val u=x*PI.toFloat()*2f
                    val y=(layer+.5f)/17f+
                        .058f*sin(u*(1.5f+layer*.075f)+ep.age*.35f)+
                        .035f*sin(u*4f-ep.age*.23f+layer*.4f)
                    val p=Offset(x*size.width,y*size.height)
                    if(i==0)path.moveTo(p.x,p.y)
                    else path.lineTo(p.x,p.y)
                }
                drawPath(path,fg.copy(alpha=alpha*(.15f+
                    .17f*sin(layer*.19f).let { it*it })),
                    style=Stroke(.55.dp.toPx()))
            }
            val center=Offset(ep.x*size.width,ep.y*size.height)
            for(j in 0..5){
                val r=unit*(.11f+j*.065f+.02f*sin(ep.age*.24f+j))
                drawCircle(fg.copy(alpha=alpha*.25f),r,center,
                    style=Stroke(.65.dp.toPx()))
            }
        }
        else -> Unit
    }
}
