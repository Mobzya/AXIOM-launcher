package dev.axiom.launcher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Separate full-screen mathematical sandbox. It never blocks launching apps. */
@Composable
internal fun ScienceLabScreen(lab: ScienceLab, onBack: () -> Unit) {
    LaunchedEffect(lab) {
        var previous = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            lab.step(((now-previous).coerceIn(0L,50_000_000L))/1_000_000_000f)
            previous = now
        }
    }
    Column(Modifier.fillMaxSize().background(AX_BLACK)
        .padding(start = 20.dp,end = 20.dp,top = 12.dp,bottom = 10.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment = Alignment.CenterVertically) {
            Text("‹",fontSize = 32.sp,color = AX_WHITE,
                modifier = Modifier.clickable(onClick = onBack)
                    .padding(end = 15.dp,start = 6.dp))
            Column(Modifier.weight(1f)) {
                Text("AXIOM / SCIENCE LAB",color = AX_GRAY,fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,letterSpacing = 1.sp)
                Text("Живые эксперименты",color = AX_WHITE,fontSize = 23.sp,
                    fontWeight = FontWeight.Light)
            }
            Text("↻",color = AX_WHITE,fontSize = 24.sp,
                modifier = Modifier.clickable { lab.reseed() }.padding(11.dp))
        }
        Spacer(Modifier.height(15.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            LabKind.entries.forEach { kind ->
                val selected = lab.kind == kind
                Text(kind.name.lowercase(),fontSize = 12.sp,
                    color = if (selected) AX_BLACK else AX_WHITE,
                    modifier = Modifier.background(
                        if(selected) AX_WHITE else AX_SURFACE,
                        RoundedCornerShape(11.dp)
                    ).border(1.dp,AX_BORDER,RoundedCornerShape(11.dp))
                        .clickable { lab.select(kind) }
                        .padding(horizontal = 11.dp,vertical = 10.dp))
            }
        }
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().weight(1f)
            .background(AX_SURFACE, RoundedCornerShape(18.dp))
            .border(1.dp,AX_BORDER,RoundedCornerShape(18.dp))) {
            Canvas(Modifier.fillMaxSize()
                .pointerInput(lab.kind) {
                    detectTapGestures { pos ->
                        if(lab.kind == LabKind.LIFE) {
                            lab.toggleCell(
                                (pos.x/size.width*lab.columns).toInt(),
                                (pos.y/size.height*lab.rows).toInt()
                            )
                        }
                    }
                }
                .padding(8.dp)) { renderExperiment(lab) }
        }
        Spacer(Modifier.height(16.dp))
        Text(lab.formula(),color = AX_WHITE,
            fontFamily = FontFamily.Monospace,fontSize = 13.sp,
            maxLines = 3, lineHeight = 19.sp)
        Spacer(Modifier.height(9.dp))
        Text("Параметр A",color = AX_GRAY,fontSize = 11.sp)
        Slider(value = lab.parameter,onValueChange = lab::tune,
            modifier = Modifier.fillMaxWidth().height(35.dp))
        Text("Параметр B",color = AX_GRAY,fontSize = 11.sp)
        Slider(value = lab.secondParameter,onValueChange = lab::tuneSecondary,
            modifier = Modifier.fillMaxWidth().height(35.dp))
        Text(
            when(lab.kind) {
                LabKind.NEURAL -> "Однослойная сеть вычисляет градиент и изменяет веса."
                LabKind.LIFE -> "Коснись поля, чтобы переключить клетку."
                LabKind.JULIA -> "Множество Жюлиа: изменение действительной и мнимой части."
                else -> "Меняй коэффициенты и наблюдай реальные вычисления."
            },
            color = AX_GRAY,fontSize = 11.sp,lineHeight = 16.sp
        )
        Spacer(Modifier.height(8.dp))
    }
}

private fun DrawScope.renderExperiment(lab: ScienceLab) {
    val ink = AX_WHITE
    val w=size.width
    val h=size.height
    val t=lab.time
    val a=lab.parameter
    val b=lab.secondParameter
    val middle=Offset(w*.5f,h*.5f)
    drawLine(ink.copy(alpha=.12f),Offset(w*.05f,middle.y),
        Offset(w*.95f,middle.y),.8.dp.toPx())
    drawLine(ink.copy(alpha=.12f),Offset(middle.x,h*.08f),
        Offset(middle.x,h*.92f),.8.dp.toPx())
    when(lab.kind) {
        LabKind.LIFE -> {
            val cellW=w/lab.columns
            val cellH=h/lab.rows
            lab.cells.forEachIndexed { index, active ->
                if (active) {
                    drawRect(ink.copy(alpha=.80f),
                        Offset((index%lab.columns)*cellW,(index/lab.columns)*cellH),
                        androidx.compose.ui.geometry.Size(cellW*.87f,cellH*.87f))
                }
            }
        }
        LabKind.JULIA -> {
            val cr=-.8f+a*.8f
            val ci=-.3f+b*.6f
            val step=7.dp.toPx()
            val nx=(w/step).toInt().coerceAtMost(84)
            val ny=(h/step).toInt().coerceAtMost(100)
            for(y in 0 until ny)for(x in 0 until nx) {
                val xx=(x.toDouble()/nx-.5)*3
                val yy=(y.toDouble()/ny-.5)*3*h/w
                val iter=juliaIterations(xx,yy,cr.toDouble(),ci.toDouble(),24)
                if(iter>3) drawRect(ink.copy(alpha=(iter/24f*.72f).coerceIn(0f,.72f)),
                    Offset(x*step,y*step),androidx.compose.ui.geometry.Size(step*.93f,step*.93f))
            }
        }
        LabKind.NEURAL -> {
            val nodes=listOf(Offset(.16f,.31f),Offset(.16f,.68f),Offset(.56f,.5f),Offset(.88f,.5f))
            val weights=listOf(lab.neural.w1,lab.neural.w2,1.0)
            for(i in 0..2) {
                val from=nodes[if(i==2)2 else i]
                val to=nodes[if(i==2)3 else 2]
                val p=Offset(from.x*w,from.y*h)
                val q=Offset(to.x*w,to.y*h)
                drawLine(ink.copy(alpha=(.24+weights[i].coerceIn(-1.0,1.0)*.28)
                    .coerceIn(.08,.75).toFloat()),p,q,1.5.dp.toPx())
                val progress=(t*(.2f+i*.04f)+i*.3f)%1f
                drawCircle(ink.copy(alpha=.93f),3.dp.toPx(),p+(q-p)*progress)
            }
            for(i in nodes.indices) {
                val c=Offset(nodes[i].x*w,nodes[i].y*h)
                drawCircle(ink.copy(alpha=.76f),12.dp.toPx(),c,style=Stroke(1.dp.toPx()))
            }
            val err=lab.neural.error.toFloat()
            drawCircle(ink.copy(alpha=.5f), (8+err*60).dp.toPx(),
                Offset(w*.88f,h*.5f),style=Stroke(.8.dp.toPx()))
        }
        LabKind.LORENZ -> {
            val path=lorenzPath(4.0+a.toDouble()*18.0)
            val poly=Path()
            path.forEachIndexed { i, pair ->
                val x=(pair.first.toFloat()+24f)/48f*w
                val y=(pair.second.toFloat()+2f)/60f*h
                if(i==0)poly.moveTo(x,y) else poly.lineTo(x,y)
            }
            drawPath(poly,ink.copy(alpha=.68f),style=Stroke(.8.dp.toPx()))
        }
        LabKind.PENDULUM -> {
            val length=.3f+a*2.5f
            val angle=pendulumAngle(t,length,.3f+b*1.7f)
            val anchor=Offset(w*.5f,h*.21f)
            val r=min(w,h)*.5f
            val end=anchor+Offset(sin(angle)*r,cos(angle)*r)
            drawLine(ink.copy(alpha=.72f),anchor,end,1.dp.toPx())
            drawCircle(ink.copy(alpha=.8f),9.dp.toPx(),end)
            drawCircle(ink.copy(alpha=.95f),3.dp.toPx(),anchor)
        }
        LabKind.PCA, LabKind.ATTENTION -> {
            val theta=(a*PI).toFloat()
            for(i in 0..39) {
                val x=((i*37%43)/43f-.5f)*w*.8f
                val y=((i*53%47)/47f-.5f)*h*.7f
                val rotated=if(lab.kind==LabKind.PCA)
                    Offset(x*cos(theta)-y*sin(theta),x*sin(theta)+y*cos(theta))
                    else Offset(x,y)
                val opacity=if(lab.kind==LabKind.ATTENTION)
                    ((sin(i*b+t)*.5f+.5f)*.75f) else .35f
                drawCircle(ink.copy(alpha=opacity),2.3.dp.toPx(),middle+rotated)
            }
        }
        LabKind.REACTION, LabKind.DIFFUSION -> {
            for(y in 0..23)for(x in 0..34) {
                val u=x/34f
                val v=y/24f
                val pattern=sin((u*17f+t*(.23f+a)))*cos(v*(12f+b*15f)-t*.14f)
                val q=if(lab.kind==LabKind.DIFFUSION) sin((u+v+t*.02f)*19f)
                    else pattern
                if(q>.15f)drawCircle(ink.copy(alpha=(q*.4f).coerceIn(0f,.5f)),
                    2.2.dp.toPx(),Offset(u*w,v*h))
            }
        }
        else -> {
            val poly=Path()
            for(i in 0..128) {
                val x=i/128f
                val wx=x*12.56f
                val y=when(lab.kind) {
                    LabKind.FOURIER -> sin(wx)+a*sin(3*wx)
                    LabKind.GRADIENT -> {
                        val d=x*5f-(1+a*3)
                        (d*d/6f).coerceAtMost(3f)
                    }
                    else -> waveValue(wx,t,.2f+a*1.8f,1+b*7f)
                }
                val point=Offset(x*w,h*.5f-y*h*.13f)
                if(i==0)poly.moveTo(point.x,point.y)
                else poly.lineTo(point.x,point.y)
            }
            drawPath(poly,ink.copy(alpha=.86f),style=Stroke(1.2.dp.toPx()))
        }
    }
}
