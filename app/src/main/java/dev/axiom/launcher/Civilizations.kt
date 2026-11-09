package dev.axiom.launcher

import kotlin.math.*
import kotlin.random.Random

internal enum class LifeMode(val title:String) { OFF("Без жизни"), EMERGENCE("Самозарождение"), SEEDED("Заселённые миры") }
internal data class CivilizationConfig(val mode:LifeMode=LifeMode.EMERGENCE,
    val colonization:Boolean=true,val defense:Boolean=true,val routes:Boolean=true,
    val pace:Float=1f)
internal data class PlanetClimate(val bodyId:Int,val thermal:Double,val stability:Double,
    val radiation:Double,val impact:Double) {
    val suitability get()=(thermal*stability*(1-radiation.coerceIn(0.0,1.0))*(1-impact*.7)).coerceIn(0.0,1.0)
    val habitable get()=thermal>.3 && stability>.55 && radiation<.4 && impact<.75
    fun features()=doubleArrayOf(1.0,thermal,stability,radiation.coerceAtMost(2.0),impact)
}
internal data class Colony(val id:Int,val faction:Int,val bodyId:Int,val population:Double,
    val resources:Double,val technology:Double,val age:Float=0f,val cooldown:Float=.4f)
internal enum class Mission { COLONIZE, INTERCEPT }
internal data class Starship(val id:Int,val faction:Int,val source:Int,val target:Int,
    val mission:Mission,val x:Float,val y:Float,val vx:Float,val vy:Float,
    val fuel:Float=22f,val passengers:Double=0.0,val energy:Double=0.0,val age:Float=0f,
    val aimX:Float=0f,val aimY:Float=0f,val closest:Float=Float.POSITIVE_INFINITY,
    val missX:Float=0f,val missY:Float=0f)
internal data class LifeFlash(val x:Float,val y:Float,val success:Boolean,val age:Float=0f)
internal data class Detonation(val bodyId:Int,val energy:Double,val x:Float,val y:Float)
internal data class CivilizationReading(val colonies:List<Colony> = emptyList(),
    val ships:List<Starship> = emptyList(),val flashes:List<LifeFlash> = emptyList(),
    val history:List<String> = emptyList(),val launches:Int=0,val interceptions:Int=0,
    val misses:Int=0,val extinct:Int=0,val survivalSamples:Int=0,val brier:Double?=null,
    val aimSamples:Int=0,val aimError:Double?=null,val settlements:Int=0)
internal val civilizationNames=listOf("Люмен","Нереиды","Кайрос","Астра")

/** Online logistic model. Labels describe the NEXT observed colony interval,
 * not a restatement of its input. Brier score is evaluated before training. */
internal class SurvivalLearner {
    private val weights=doubleArrayOf(-1.5,1.8,1.6,-2.5,-2.0)
    var samples=0;private set
    private var errors=0.0
    val brier get()=if(samples>=8)errors/min(samples,100).toDouble() else null
    fun predict(features:DoubleArray):Double {
        require(features.size==weights.size)
        return 1/(1+exp(-weights.indices.sumOf {weights[it]*features[it]}.coerceIn(-30.0,30.0)))
    }
    fun learn(features:DoubleArray,survived:Boolean) {
        val p=predict(features);val target=if(survived)1.0 else 0.0
        val err=p-target;errors=if(samples<100)errors+err*err else errors*.99+err*err
        for(i in weights.indices)weights[i]=(weights[i]-.045*(err*features[i]+.001*weights[i])).coerceIn(-8.0,8.0)
        samples++
    }
    data class Snapshot(val weights:DoubleArray,val samples:Int,val errors:Double)
    fun snapshot()=Snapshot(weights.copyOf(),samples,errors)
    fun restore(s:Snapshot){s.weights.copyInto(weights);samples=s.samples;errors=s.errors}
}

/** Information/agent layer over real planets. Population and spacecraft are
 * a stylised ecological model, not additional invisible gravitational masses. */
internal class CivilizationSimulation(private val random:Random=Random(725)) {
    private var colonies=emptyList<Colony>()
    private var ships=emptyList<Starship>()
    private var flashes=emptyList<LifeFlash>()
    private var history=emptyList<String>()
    private var nextId=1
    private var launches=0;private var interceptions=0;private var misses=0;private var extinct=0;private var settlements=0
    private var emergence=0f;private var studyTimer=0f
    private var aimX=0f;private var aimY=0f;private var aimSamples=0;private var aimError=0.0
    private var pending=emptyMap<Int,Pair<DoubleArray,Double>>()
    val survival=SurvivalLearner()
    val reading get()=CivilizationReading(colonies,ships,flashes,history,launches,interceptions,
        misses,extinct,survival.samples,survival.brier,aimSamples,
        if(aimSamples>0)aimError/min(aimSamples,40) else null,settlements)

    private fun note(text:String){history=(history+text).takeLast(14)}
    fun clear(){
        colonies=emptyList();ships=emptyList();flashes=emptyList();history=emptyList();nextId=1
        launches=0;interceptions=0;misses=0;extinct=0;settlements=0;emergence=0f;studyTimer=0f
        aimX=0f;aimY=0f;aimSamples=0;aimError=0.0;pending=emptyMap()
        survival.restore(SurvivalLearner().snapshot())
    }
    fun climate(body:CelestialBody,bodies:List<CelestialBody>,radiation:Double=0.0):PlanetClimate {
        if(body.stellarClass!=StellarClass.PLANET || body.nature!=CelestialNature.ORDINARY || body.material.gas || body.mass<.015f)
            return PlanetClimate(body.id,0.0,0.0,radiation,1.0)
        val thermal=(1-abs(body.temperature-292.0)/125.0).coerceIn(0.0,1.0)
        var stability=1.0;var impact=0.0
        for(other in bodies) {
            if(other.id==body.id)continue
            val distance=hypot(body.x-other.x,body.y-other.y)
            val clearance=structureRadius(body)+structureRadius(other)
            if(other.mass>body.mass*.2f)
                stability=min(stability,((distance-clearance)/(clearance*2)).coerceIn(0f,1f).toDouble())
            if(isAsteroid(other)) {
                val approach=closestApproach(body,other)
                if(approach.first in 0f..12f) impact=max(impact,
                    (1-approach.second/(clearance*1.8f)).coerceIn(0f,1f).toDouble())
                if(distance<clearance)stability=0.0
            }
            if(other.nature==CelestialNature.BLACK_HOLE && distance<horizonRadius(other.mass)*5)
                stability=0.0
        }
        // Hyperbolic ejection and violent close encounters are poor homes.
        val primary=bodies.filter {it.id!=body.id && it.mass>body.mass*2}.maxByOrNull {it.mass}
        if(primary!=null) {
            val dx=body.x-primary.x;val dy=body.y-primary.y
            val escape=sqrt(2*OrbitMechanics.G*primary.mass/hypot(dx,dy).coerceAtLeast(.001f))
            if(hypot(body.vx-primary.vx,body.vy-primary.vy)>escape*1.35)stability*=.15
        }
        return PlanetClimate(body.id,thermal,stability,radiation.coerceAtLeast(0.0),impact)
    }
    fun seed(body:CelestialBody,bodies:List<CelestialBody>,radiation:Double=0.0,
        faction:Int=colonies.size%civilizationNames.size):Boolean {
        if(colonies.size>=16 || colonies.any {it.bodyId==body.id} || !climate(body,bodies,radiation).habitable)return false
        colonies=colonies+Colony(nextId++,faction%4,body.id,18_000.0,85.0,.48)
        note("${civilizationNames[faction%4]}: первые поселения на №${body.id}")
        return true
    }
    fun seedFavorable(bodies:List<CelestialBody>,radiation:Map<Int,Float> = emptyMap(),limit:Int=2) {
        bodies.sortedByDescending {climate(it,bodies,(radiation[it.id] ?: 0f).toDouble()).suitability}
            .take(limit).forEach {seed(it,bodies,(radiation[it.id] ?: 0f).toDouble())}
    }

    fun step(dt:Float,bodies:List<CelestialBody>,gravity:Float,estimatedGravity:Float,
        radiation:Map<Int,Float>,config:CivilizationConfig,learning:Boolean,
        bounds:SpaceBounds=SpaceBounds(),clock:ClockObstacle?=null):List<Detonation> {
        if(dt<=0 || !dt.isFinite() || config.mode==LifeMode.OFF)return emptyList()
        val h=dt*config.pace.coerceIn(.5f,3f)
        val climates=bodies.associate {it.id to climate(it,bodies,(radiation[it.id] ?: 0f).toDouble())}
        emergence+=h;studyTimer+=h
        if(emergence>=18f) {
            emergence=0f
            val candidate=bodies.filter {climates[it.id]!!.habitable && colonies.none {c->c.bodyId==it.id}}
                .maxByOrNull {climates[it.id]!!.suitability}
            if(candidate!=null && colonies.isEmpty() &&
                (config.mode==LifeMode.SEEDED || random.nextFloat()<.24f))seed(candidate,bodies)
        }
        val active=ArrayList<Colony>()
        for(colony in colonies) {
            val body=bodies.firstOrNull {it.id==colony.bodyId}
            val environment=climates[colony.bodyId]
            if(body==null || environment==null){extinct++;note("${civilizationNames[colony.faction]}: мир №${colony.bodyId} потерян");continue}
            val fit=environment.suitability
            val lethal=(1-environment.thermal-colony.technology*.35).coerceAtLeast(0.0)*.1+(1-environment.stability).pow(2)*1.8+
                environment.radiation*1.2+if(environment.impact>.98) .7 else 0.0
            val capacity=max(25_000.0,body.mass*180_000.0)
            val population=(colony.population*exp((.035*fit*(1-colony.population/capacity)-lethal)*h)).coerceAtLeast(0.0)
            if(population<120) {
                extinct++;note("${civilizationNames[colony.faction]}: колония №${body.id} погибла")
                flashes=flashes+LifeFlash(body.x,body.y,false);continue
            }
            var c=colony.copy(population=population,
                resources=(colony.resources+h*(1.1+population/18_000)*fit-h*.12).coerceIn(0.0,250.0),
                technology=(colony.technology+h*.003*fit).coerceAtMost(1.0),age=colony.age+h,
                cooldown=(colony.cooldown-h).coerceAtLeast(0f))
            if(c.cooldown<=0 && ships.size<28) {
                val threat=if(config.defense) bodies.filter {isAsteroid(it) && ships.none {s->s.mission==Mission.INTERCEPT&&s.target==it.id}}
                    .map {it to closestApproach(body,it)}
                    .filter {it.second.first in .3f..12f && it.second.second<structureRadius(body)+structureRadius(it.first)*1.5f}
                    .minByOrNull {it.second.first}?.first else null
                val price=if(threat!=null)(18+threat.mass*32).coerceAtMost(110f).toDouble() else 0.0
                if(threat!=null && c.resources>=price && c.technology>.25) {
                    ships=ships+launch(c,body,threat,Mission.INTERCEPT,price,0.0,learning)
                    c=c.copy(resources=c.resources-price,cooldown=5f)
                    note("${civilizationNames[c.faction]}: перехват астероида №${threat.id}")
                } else if(config.colonization && c.resources>=48 && c.population>8_000 && c.age>12) {
                    val target=bodies.filter {it.id!=body.id && climates[it.id]!!.habitable &&
                        colonies.none {c2->c2.bodyId==it.id} && ships.none {s->s.mission==Mission.COLONIZE&&s.target==it.id}}
                        .maxByOrNull {candidate ->
                            val environment2=climates[candidate.id]!!
                            val chance=if(learning)survival.predict(environment2.features()) else environment2.suitability
                            chance/(.12+hypot(candidate.x-body.x,candidate.y-body.y))
                        }
                    if(target!=null) {
                        val passengers=min(c.population*.18,12_000.0)
                        ships=ships+launch(c,body,target,Mission.COLONIZE,48.0,passengers,learning)
                        c=c.copy(resources=c.resources-48,population=c.population-passengers,cooldown=16f)
                        note("${civilizationNames[c.faction]}: переселенцы летят к №${target.id}")
                    }
                }
            }
            active.add(c)
        }
        colonies=active
        val detonations=ArrayList<Detonation>()
        val flying=ArrayList<Starship>()
        for(ship in ships) {
            val h=dt // Evolution speed never changes the spacecraft's physical clock.
            val target=bodies.firstOrNull {it.id==ship.target}
            if(target==null || ship.age>55f){lose(ship,null,learning);continue}
            val distance=hypot(target.x-ship.x,target.y-ship.y)
            val speed=if(ship.mission==Mission.INTERCEPT).20f else .105f
            val lead=interceptTime(ship.x,ship.y,target,speed).coerceIn(0f,5f)
            val field=pointGravity(ship.x,ship.y,bodies,gravity)
            val predicted=pointGravity(target.x,target.y,bodies,estimatedGravity,target.id)
            val tx=target.x+target.vx*lead+predicted.first*lead*lead*.5f+ship.aimX
            val ty=target.y+target.vy*lead+predicted.second*lead*lead*.5f+ship.aimY
            val dx=tx-ship.x;val dy=ty-ship.y;val r=hypot(dx,dy).coerceAtLeast(.001f)
            val acceleration=if(ship.mission==Mission.INTERCEPT).16f else .065f
            val desired=if(ship.mission==Mission.COLONIZE)min(speed,sqrt(2*acceleration*distance)) else speed
            val estimate=pointGravity(ship.x,ship.y,bodies,estimatedGravity)
            var ax=(target.vx+dx/r*desired-ship.vx)/.55f-estimate.first
            var ay=(target.vy+dy/r*desired-ship.vy)/.55f-estimate.second
            if(clock!=null && clock.nearby(ship.x,ship.y,.025f)) {
                val e=.003f
                ax+=(clock.distance(ship.x+e,ship.y)-clock.distance(ship.x-e,ship.y))*.8f
                ay+=(clock.distance(ship.x,ship.y+e)-clock.distance(ship.x,ship.y-e))*.8f
            }
            val force=hypot(ax,ay);val limit=if(ship.fuel>0)acceleration else 0f
            if(force>limit && force>0){ax*=limit/force;ay*=limit/force}
            var vx=ship.vx+(field.first+ax)*h;var vy=ship.vy+(field.second+ay)*h
            var px=ship.x+vx*h;var py=ship.y+vy*h
            clock?.collide(px,py,vx,vy,.0015f)?.let {px=it.x;py=it.y;vx=it.vx;vy=it.vy}
            if(!px.isFinite() || !py.isFinite() || px<bounds.left-.3 || px>bounds.right+.3 ||
                py<bounds.top-.3 || py>bounds.bottom+.3){lose(ship,target,learning);continue}
            val rx0=ship.x-(target.x-target.vx*h);val ry0=ship.y-(target.y-target.vy*h)
            val rx1=px-target.x;val ry1=py-target.y
            val sx=rx1-rx0;val sy=ry1-ry0
            val fraction=(-(rx0*sx+ry0*sy)/(sx*sx+sy*sy).coerceAtLeast(1e-12f)).coerceIn(0f,1f)
            val missX=rx0+sx*fraction;val missY=ry0+sy*fraction
            val closest=hypot(missX,missY)
            val contact=if(ship.mission==Mission.INTERCEPT)structureRadius(target)+.002f else structureRadius(target)+.004f
            val next=ship.copy(x=px,y=py,vx=vx,vy=vy,age=ship.age+h,
                fuel=(ship.fuel-h*hypot(ax,ay)/.065f).coerceAtLeast(0f),
                closest=min(ship.closest,closest),
                missX=if(closest<ship.closest)missX else ship.missX,
                missY=if(closest<ship.closest)missY else ship.missY)
            val hitStar=bodies.any {it.id!=ship.target && it.id!=ship.source &&
                (it.stellarClass==StellarClass.STAR || it.nature==CelestialNature.BLACK_HOLE) &&
                hypot(px-it.x,py-it.y)<structureRadius(it)}
            if(hitStar){lose(next,target,learning);continue}
            if(closest<contact) {
                if(ship.mission==Mission.INTERCEPT) {
                    detonations.add(Detonation(target.id,ship.energy,px,py));interceptions++
                    if(learning)calibrate(next)
                    flashes=flashes+LifeFlash(px,py,true)
                    note("${civilizationNames[ship.faction]}: заряд достиг №${target.id}")
                } else {
                    val environment=climates[target.id]!!
                    if(environment.habitable && colonies.size<16 && colonies.none {it.bodyId==target.id}) {
                        colonies=colonies+Colony(nextId++,ship.faction,target.id,ship.passengers*.92,12.0,.35)
                        settlements++
                        note("${civilizationNames[ship.faction]}: основана колония №${target.id}")
                    } else note("${civilizationNames[ship.faction]}: высадка на №${target.id} не удалась")
                }
            } else if(ship.mission==Mission.INTERCEPT && ship.closest<.07 && closest>ship.closest+.008 && ship.age>1f) {
                lose(next,target,learning)
            } else flying.add(next)
        }
        ships=flying.take(28)
        flashes=flashes.map {it.copy(age=it.age+h)}.filter {it.age<2f}.takeLast(16)
        if(studyTimer>=5f) {
            studyTimer=0f
            if(learning) {
                for((id,observation) in pending) {
                    val colony=colonies.firstOrNull {it.id==id}
                    survival.learn(observation.first,colony!=null && colony.population>=observation.second*.98)
                }
                pending=colonies.associate {it.id to (climates[it.bodyId]!!.features() to it.population)}
            } else pending=emptyMap()
        }
        return detonations
    }

    private fun launch(colony:Colony,from:CelestialBody,target:CelestialBody,mission:Mission,
        price:Double,passengers:Double,learning:Boolean):Starship {
        launches++
        val d=hypot(target.x-from.x,target.y-from.y).coerceAtLeast(.001f)
        val nx=(target.x-from.x)/d;val ny=(target.y-from.y)/d
        fun noise()=(random.nextFloat()+random.nextFloat()+random.nextFloat()-1.5f)*.009f/(colony.technology.toFloat()+.4f)
        val errorX=if(mission==Mission.INTERCEPT) .006f+noise()-(if(learning)aimX else 0f) else 0f
        val errorY=if(mission==Mission.INTERCEPT) -.004f+noise()-(if(learning)aimY else 0f) else 0f
        val launchSpeed=if(mission==Mission.INTERCEPT).09f else .05f
        return Starship(nextId++,colony.faction,from.id,target.id,mission,
            from.x+nx*(structureRadius(from)+.007f),from.y+ny*(structureRadius(from)+.007f),
            from.vx+nx*launchSpeed,from.vy+ny*launchSpeed,passengers=passengers,
            energy=price*.000018*(.7+colony.technology),aimX=errorX,aimY=errorY)
    }
    private fun calibrate(ship:Starship) {
        aimX=(aimX+ship.missX*.18f).coerceIn(-.025f,.025f)
        aimY=(aimY+ship.missY*.18f).coerceIn(-.025f,.025f)
        val error=ship.closest.toDouble().coerceAtMost(.2)
        aimError=if(aimSamples<40)aimError+error else aimError*.975+error
        aimSamples++
    }
    private fun lose(ship:Starship,target:CelestialBody?,learning:Boolean) {
        if(ship.mission==Mission.INTERCEPT) {
            misses++;if(learning && target!=null && ship.closest.isFinite())calibrate(ship)
            note("${civilizationNames[ship.faction]}: перехватчик промахнулся")
        } else note("${civilizationNames[ship.faction]}: корабль переселенцев потерян")
        flashes=(flashes+LifeFlash(ship.x,ship.y,false)).takeLast(16)
    }
    data class Snapshot(val reading:CivilizationReading,val nextId:Int,val emergence:Float,
        val studyTimer:Float,val aimX:Float,val aimY:Float,
        val aimError:Double,val pending:Map<Int,Pair<DoubleArray,Double>>,val learner:SurvivalLearner.Snapshot)
    fun snapshot()=Snapshot(reading,nextId,emergence,studyTimer,aimX,aimY,aimError,
        pending.mapValues {it.value.first.copyOf() to it.value.second},survival.snapshot())
    fun restore(s:Snapshot) {
        val r=s.reading;colonies=r.colonies;ships=r.ships;flashes=r.flashes;history=r.history
        launches=r.launches;interceptions=r.interceptions;misses=r.misses;extinct=r.extinct;settlements=r.settlements
        nextId=s.nextId;emergence=s.emergence;studyTimer=s.studyTimer;aimX=s.aimX;aimY=s.aimY
        aimSamples=r.aimSamples;aimError=s.aimError;pending=s.pending;survival.restore(s.learner)
    }
    companion object {
        fun isAsteroid(body:CelestialBody)=body.nature in listOf(CelestialNature.ASTEROID,CelestialNature.COMET) || body.kind==1
        fun closestApproach(planet:CelestialBody,asteroid:CelestialBody):Pair<Float,Float> {
            val dx=asteroid.x-planet.x;val dy=asteroid.y-planet.y
            val vx=asteroid.vx-planet.vx;val vy=asteroid.vy-planet.vy
            val vv=vx*vx+vy*vy
            if(vv<1e-12f)return Float.POSITIVE_INFINITY to hypot(dx,dy)
            val t=-(dx*vx+dy*vy)/vv
            return t to hypot(dx+vx*t,dy+vy*t)
        }
        private fun interceptTime(x:Float,y:Float,target:CelestialBody,speed:Float):Float {
            val dx=target.x-x;val dy=target.y-y
            val a=target.vx*target.vx+target.vy*target.vy-speed*speed
            val b=2*(dx*target.vx+dy*target.vy);val c=dx*dx+dy*dy
            if(abs(a)<1e-8f)return if(abs(b)>1e-8f)(-c/b).coerceAtLeast(0f) else 0f
            val d=b*b-4*a*c
            if(d<0)return sqrt(c)/speed
            return listOf((-b-sqrt(d))/(2*a),(-b+sqrt(d))/(2*a)).filter {it>0}.minOrNull() ?: sqrt(c)/speed
        }
        private fun pointGravity(x:Float,y:Float,bodies:List<CelestialBody>,gravity:Float,skip:Int=-1):Pair<Float,Float> {
            var ax=0.0;var ay=0.0
            for(b in bodies) {
                if(b.id==skip)continue
                val dx=b.x-x;val dy=b.y-y
                val r2=dx*dx+dy*dy+OrbitMechanics.SOFTENING.pow(2)
                val f=OrbitMechanics.G*gravity*b.mass/(r2*sqrt(r2))
                ax+=f*dx;ay+=f*dy
            }
            return ax.toFloat() to ay.toFloat()
        }
    }
}
