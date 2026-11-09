package dev.axiom.launcher

import kotlin.math.*
import kotlin.random.Random

internal enum class CelestialNature(val title:String) {
    ORDINARY("Мир"), BLACK_HOLE("Чёрная дыра"), NEUTRON_STAR("Нейтронная звезда"),
    WHITE_DWARF("Белый карлик"), COMET("Комета"), ASTEROID("Астероид")
}
internal fun horizonRadius(mass:Float)=(.004f+.002f*sqrt(mass.coerceAtLeast(.01f))).coerceIn(.005f,.045f)

internal enum class CatastropheRate(val title:String,val factor:Float) {
    VERY_RARE("Почти легенды",.25f), NATURAL("Очень редко",1f), CINEMATIC("Насыщенно",4f)
}
internal data class ExtremePhenomenon(val kind:CosmicEventKind,val title:String,
    val meanHours:Double,val description:String)
internal val extremePhenomena=listOf(
    ExtremePhenomenon(CosmicEventKind.GAMMA_BURST,"Гамма-всплеск",4.0,
        "Узкий луч внешнего излучения нагревает вещество и губителен для жизни"),
    ExtremePhenomenon(CosmicEventKind.DISTANT_CLUSTER_BURST,"Взрыв далёкого скопления",18.0,
        "Далёкая катастрофа: медленные световые оболочки и фронт излучения"),
    ExtremePhenomenon(CosmicEventKind.COLOSSAL_METEOR_STREAM,"Колоссальный астероидный поток",8.0,
        "Несколько волн настоящих тел; пределы частиц и числа объектов сохраняются"),
    ExtremePhenomenon(CosmicEventKind.KILONOVA,"Килоновая",10.0,
        "Мощный фронт нагрева и радиационная опасность для колоний"),
    ExtremePhenomenon(CosmicEventKind.SUPERNOVA,"Сверхновая",6.0,
        "Звёздный выброс превращает запас тепла в разлетающееся вещество")
)

/** Separate active-wall-time Poisson clock. Scene resets do not reroll it.
 * On average the natural setting produces one extreme per ~1.5 active hours;
 * individual kinds are much rarer. Paused/background time never catches up. */
internal class ExtremeEventDirector(private val random:Random) {
    var remaining:Double=sampleDelay()
        private set
    private fun sampleDelay():Double {
        val lambda=extremePhenomena.sumOf {1.0/(it.meanHours*3600)}
        return -ln((1.0-random.nextDouble()).coerceAtLeast(1e-12))/lambda
    }
    fun advance(seconds:Float,enabled:Boolean,rate:CatastropheRate,canStart:Boolean):CosmicEventKind? {
        if(!enabled || seconds<=0f || !seconds.isFinite())return null
        remaining-=seconds*rate.factor
        if(remaining>0 || !canStart)return null
        var pick=random.nextDouble()*extremePhenomena.sumOf {1.0/it.meanHours}
        var selected=extremePhenomena.last().kind
        for(item in extremePhenomena){pick-=1.0/item.meanHours;if(pick<=0){selected=item.kind;break}}
        remaining=sampleDelay()
        return selected
    }
}

internal data class QuantumReading(val leftProbability:Double=.5,val coherence:Double=1.0,
    val measurements:Int=0,val leftResults:Int=0,val lastResult:Int?=null)

/** Two-level quantum toy: exact unitary Rabi evolution of a density matrix,
 * plus an explicit dephasing channel. No macroscopic planet teleportation. */
internal class QuantumExperiment(private val random:Random=Random(251)) {
    private var x=0.0;private var y=0.0;private var z=1.0
    private var measurements=0;private var leftResults=0;private var last:Int?=null
    val reading get()=QuantumReading(((1+z)*.5).coerceIn(0.0,1.0),
        sqrt(x*x+y*y).coerceIn(0.0,1.0),measurements,leftResults,last)
    fun step(dt:Float,coupling:Float=1f,dephasing:Float=.06f) {
        if(!dt.isFinite() || dt<=0 || !coupling.isFinite() || !dephasing.isFinite())return
        val angle=dt*coupling.coerceIn(.1f,3f)
        val c=cos(angle.toDouble());val s=sin(angle.toDouble())
        val nextY=y*c-z*s;z=y*s+z*c;y=nextY
        val decay=exp(-dt*dephasing.coerceIn(0f,2f));x*=decay;y*=decay
    }
    fun measure():Int {
        val outcome=if(random.nextDouble()<reading.leftProbability)0 else 1
        z=if(outcome==0)1.0 else -1.0;x=0.0;y=0.0
        measurements++;if(outcome==0)leftResults++;last=outcome
        return outcome
    }
    fun resetSuperposition(){x=1.0;y=0.0;z=0.0;last=null}
    data class Snapshot(val x:Double,val y:Double,val z:Double,val count:Int,val left:Int,val last:Int?)
    fun snapshot()=Snapshot(x,y,z,measurements,leftResults,last)
    fun restore(s:Snapshot){x=s.x;y=s.y;z=s.z;measurements=s.count;leftResults=s.left;last=s.last}
}

internal data class ScheduleDecision(val preset:CosmicPreset,val bucket:Long)
internal class HourlySceneCycle {
    private var lastBucket:Long?=null
    fun check(bucket:Long,current:CosmicPreset,pool:List<CosmicPreset>,enabled:Boolean):ScheduleDecision? {
        if(!enabled){lastBucket=null;return null}
        val before=lastBucket;lastBucket=bucket
        if(before==null || before==bucket)return null
        val choices=pool.distinct().ifEmpty {CosmicPreset.entries.toList()}
        val index=choices.indexOf(current)
        // Missed hours advance the sequence once to the current bucket; no burst
        // of expensive scene reloads on resume or a changed wall clock.
        val steps=abs(bucket-before).coerceAtLeast(1).coerceAtMost(choices.size.toLong())
        val next=choices[((index+steps)%choices.size).toInt()]
        return ScheduleDecision(next,bucket)
    }
}

/** Dimensionless fluence of a travelling external radiation front. */
internal fun radiationExposure(event:CosmicEpisode,x:Float,y:Float):Float {
    val dx=x-event.x;val dy=y-event.y
    return when(event.kind) {
        CosmicEventKind.GAMMA_BURST -> {
            val along=dx*cos(event.axis)+dy*sin(event.axis)
            val across=abs(-dx*sin(event.axis)+dy*cos(event.axis))
            if(along<0 || along>event.progress*3f || across>.028f+along*.10f)0f
            else (1f-across/(.028f+along*.10f))*.9f
        }
        CosmicEventKind.DISTANT_CLUSTER_BURST,CosmicEventKind.KILONOVA -> {
            val distance=hypot(dx,dy)
            if(distance>event.progress*3.2f)0f else (1f/(1f+distance*.4f))*.7f
        }
        else -> 0f
    }
}
