package dev.axiom.launcher

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.*

private val factionColors=listOf(Color(0xffa6ddbd),Color(0xff9ecbdf),Color(0xffc8b2e4),Color(0xffe0c28d))

internal fun interpolateBodies(frame:GrainRenderPair,fraction:Float):List<CelestialBody> {
    if(frame.bodyCurrent.isEmpty())return emptyList()
    return frame.bodyCurrent.map {body ->
        val old=frame.bodyPrevious.firstOrNull {it.id==body.id}
        if(old==null)body else body.copy(x=old.x+(body.x-old.x)*fraction,
            y=old.y+(body.y-old.y)*fraction)
    }
}

internal fun DrawScope.drawCosmicSystems(world:CosmicWorld,viewport:UniverseViewport,
    bodies:List<CelestialBody>,alpha:Float,now:Long) {
    fun point(x:Float,y:Float)=Offset(viewport.x(x),viewport.y(y))
    val unit=viewport.side
    val path=Path()
    val light=AxiomColors.palette.light
    for(body in bodies) {
        val center=point(body.x,body.y)
        if(body.nature==CelestialNature.BLACK_HOLE) {
            val radius=horizonRadius(body.mass)*unit
            val feeding=(world.accretionLight[body.id] ?: 0f).coerceIn(0f,1f)
            val glow=if(light)Color(0xff725ca0) else Color(0xffb29bdf)
            drawCircle(Brush.radialGradient(listOf(glow.copy(alpha=(.08f+feeding*.12f)*alpha),Color.Transparent),
                center,radius*4.5f),radius*4.5f,center)
            drawCircle(Color(0xff050710).copy(alpha=alpha),radius,center)
            drawCircle(glow.copy(alpha=.65f*alpha),radius*1.11f,center,style=Stroke(.7.dp.toPx()))
            drawCircle(Color(0xfff1d1ad).copy(alpha=(.16f+feeding*.5f)*alpha),radius*1.55f,
                center,style=Stroke((.6f+feeding*.5f).dp.toPx()))
            // An optical annotation around a real collapsed mass, not hidden matter.
            drawArc(glow.copy(alpha=.22f*alpha),-35f,110f,false,
                center-Offset(radius*2.6f,radius*1.6f),
                androidx.compose.ui.geometry.Size(radius*5.2f,radius*3.2f),style=Stroke(.65.dp.toPx()))
        } else if(body.nature==CelestialNature.NEUTRON_STAR) {
            val angle=if(world.reducedMotion).6f else world.elapsed*.7f+body.id
            val radius=max(2.dp.toPx(),structureRadius(body)*unit)
            for(sign in listOf(-1f,1f)) {
                val direction=Offset(cos(angle)*sign,sin(angle)*sign)
                val perpendicular=Offset(-direction.y,direction.x)
                val tip=center+direction*unit*.22f
                path.reset();path.moveTo(center.x,center.y)
                path.lineTo((tip+perpendicular*unit*.025f).x,(tip+perpendicular*unit*.025f).y)
                path.lineTo((tip-perpendicular*unit*.025f).x,(tip-perpendicular*unit*.025f).y);path.close()
                drawPath(path,Brush.linearGradient(listOf(Color(0xffaac8ff).copy(alpha=.12f*alpha),Color.Transparent),center,tip))
            }
            drawCircle(Color(0xffc3d8ff).copy(alpha=.7f*alpha),radius*.75f,center,style=Stroke(.7.dp.toPx()))
        } else if(body.nature==CelestialNature.WHITE_DWARF) {
            drawCircle(Color(0xffd7e4ff).copy(alpha=.09f*alpha),structureRadius(body)*unit*3f,center)
        }
    }
    if(world.civilizationConfig.mode!=LifeMode.OFF) {
        val life=world.civilization
        for(colony in life.colonies) {
            val body=bodies.firstOrNull {it.id==colony.bodyId} ?: continue
            val center=point(body.x,body.y);val color=factionColors[colony.faction%4]
            val radius=structureRadius(body)*unit+3.dp.toPx()
            drawCircle(color.copy(alpha=.30f*alpha),radius,center,style=Stroke(.7.dp.toPx()))
            val lights=(ln(colony.population.coerceAtLeast(1.0))/1.3).toInt().coerceIn(3,12)
            for(k in 0 until lights) {
                val angle=k*2.39996f+colony.faction
                val location=center+Offset(cos(angle),sin(angle))*radius
                drawCircle(color.copy(alpha=.82f*alpha),.7.dp.toPx(),location)
            }
        }
        val frames=world.shipFrames
        val blend=((now-frames.committedAtNanos).toFloat()/frames.intervalNanos).coerceIn(0f,1f)
        for(ship in frames.current) {
            val old=frames.previous.firstOrNull {it.id==ship.id}
            val x=if(old==null)ship.x else old.x+(ship.x-old.x)*blend
            val y=if(old==null)ship.y else old.y+(ship.y-old.y)*blend
            val center=point(x,y)
            val color=if(ship.mission==Mission.INTERCEPT)Color(0xffeac38a) else factionColors[ship.faction%4]
            if(world.civilizationConfig.routes) {
                val target=bodies.firstOrNull {it.id==ship.target}
                if(target!=null)drawLine(color.copy(alpha=.10f*alpha),center,point(target.x,target.y),.5.dp.toPx())
            }
            val speed=hypot(ship.vx,ship.vy).coerceAtLeast(.001f)
            val direction=Offset(ship.vx/speed,ship.vy/speed)
            val perpendicular=Offset(-direction.y,direction.x)
            val tip=center+direction*2.8.dp.toPx()
            val back=center-direction*2.dp.toPx()
            path.reset();path.moveTo(tip.x,tip.y)
            path.lineTo((back+perpendicular*1.5.dp.toPx()).x,(back+perpendicular*1.5.dp.toPx()).y)
            path.lineTo((back-perpendicular*1.5.dp.toPx()).x,(back-perpendicular*1.5.dp.toPx()).y);path.close()
            if(ship.fuel>0)drawLine(Brush.linearGradient(listOf(color.copy(alpha=.4f*alpha),Color.Transparent),
                back,back-direction*9.dp.toPx()),back,back-direction*9.dp.toPx(),1.2.dp.toPx())
            drawPath(path,color.copy(alpha=.95f*alpha))
        }
        for(flash in life.flashes) {
            val progress=(flash.age/2f).coerceIn(0f,1f)
            val color=if(flash.success)Color(0xfff1cd95) else Color(0xffb8a5c2)
            val center=point(flash.x,flash.y)
            drawCircle(color.copy(alpha=(1-progress)*.15f*alpha),
                (4f+progress*20f).dp.toPx(),center)
            drawCircle(color.copy(alpha=(1-progress)*.5f*alpha),
                (2f+progress*15f).dp.toPx(),center,style=Stroke(.8.dp.toPx()))
        }
    }
    val event=world.episode
    val envelope=event.alpha*alpha
    if(event.kind==CosmicEventKind.GAMMA_BURST) {
        val start=point(event.x,event.y)
        val end=start+Offset(cos(event.axis),sin(event.axis))*event.progress*unit*3f
        val color=if(light)Color(0xff6671a3) else Color(0xffc5b9ed)
        for((width,opacity) in listOf(25f to .035f,9f to .08f,1.2f to .55f))
            drawLine(color.copy(alpha=opacity*envelope),start,end,width.dp.toPx())
    }
    if(event.kind in listOf(CosmicEventKind.DISTANT_CLUSTER_BURST,CosmicEventKind.KILONOVA,CosmicEventKind.SUPERNOVA)) {
        val center=point(event.x,event.y)
        val color=if(event.kind==CosmicEventKind.DISTANT_CLUSTER_BURST)Color(0xffb3b5e7) else Color(0xffefb78d)
        val radius=event.progress*unit*3.2f
        for(k in 0 until 5) {
            val r=radius-k*unit*.065f
            if(r>0)drawCircle(color.copy(alpha=envelope*(.15f-k*.022f)),r,center,
                style=Stroke((1f+k*.35f).dp.toPx()))
        }
    }
    if(event.kind==CosmicEventKind.MICROLENSING) {
        val source=bodies.maxByOrNull {it.mass}
        if(source!=null) {
            val center=point(source.x,source.y);val radius=unit*(.04f+.015f*sin(event.progress*PI.toFloat()))
            drawCircle(Color(0xffbed8de).copy(alpha=envelope*.27f),radius,center,style=Stroke(.8.dp.toPx()))
            drawArc(Color(0xffdec9f0).copy(alpha=envelope*.7f),-80f,75f,false,
                center-Offset(radius,radius),androidx.compose.ui.geometry.Size(radius*2,radius*2),style=Stroke(1.7.dp.toPx()))
        }
    }
    if(world.quantumEnabled && (world.preset==CosmicPreset.QUANTUM_SHORES || event.kind==CosmicEventKind.QUANTUM_COHERENCE)) {
        val q=world.quantumReading
        val a=point(.16f,.74f);val b=point(.34f,.74f)
        val color=if(light)Color(0xff5f6b9c) else Color(0xffb4c3e9)
        drawLine(color.copy(alpha=.18f*alpha),a,b,.6.dp.toPx())
        for((center,p) in listOf(a to q.leftProbability,b to 1-q.leftProbability)) {
            val radius=(4+7*sqrt(p)).toFloat().dp.toPx()
            drawCircle(color.copy(alpha=(.06+.16*p).toFloat()*alpha),radius*1.8f,center)
            drawCircle(color.copy(alpha=(.2+.6*p).toFloat()*alpha),radius,center,style=Stroke(.8.dp.toPx()))
        }
        path.reset()
        for(i in 0..48) {
            val t=i/48f
            val p=a+(b-a)*t
            val phase=if(world.reducedMotion)0f else world.elapsed*.8f
            val y=p.y-sin(t*PI.toFloat()*4-phase)*sin(t*PI.toFloat())*q.coherence.toFloat()*7.dp.toPx()
            if(i==0)path.moveTo(p.x,y) else path.lineTo(p.x,y)
        }
        drawPath(path,color.copy(alpha=.55f*alpha),style=Stroke(.7.dp.toPx()))
    }
}
