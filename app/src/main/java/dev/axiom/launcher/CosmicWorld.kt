package dev.axiom.launcher

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Single cinematic world; scenarios are temporary events, not user-facing modes. */
internal enum class CosmicEventKind { QUIET, METEORS, NEURAL, HARMONICS, LENS, SINGULARITY, NOVA, PULSAR, WORMHOLE, NEBULA, ENCOUNTER, FILAMENT, RADIATION, RING_WAVE, REACTION,
    ECLIPSE, COSMIC_WEB, PHASE_TIDE,
    ECHO_SHELL, TIDAL_CAPTURE, POLAR_JET, ORBITAL_RESONANCE,
    STELLAR_NURSERY, DARK_FLOW, GEOMETRY_SHIFT, SOLAR_FLARE,
    DUAL_RING, PARTICLE_CASCADE,
    MEGASTRUCTURE, GALAXY_COLLISION, VOID_BLOOM, GRAVITATIONAL_CAUSTIC,
    COMET_SWARM, ACCRETION_BURST, CRYSTALLIZATION, PLASMA_STORM, QUASAR_JET,
    COSMIC_SHOCK, THERMAL_SPIKE, CRYO_TIDE, MAGNETAR_FLARE,
    SOLAR_WIND, TIDAL_PULSE, IONIZATION_FRONT,
    GAMMA_BURST, DISTANT_CLUSTER_BURST, COLOSSAL_METEOR_STREAM,
    KILONOVA, SUPERNOVA, QUANTUM_COHERENCE, MICROLENSING }

internal enum class CosmicEventIntensity(
    val label: String, val heatFactor: Double, val radius: Float
) {
    FLICKER("Слабое", .82, .21f),
    NORMAL("Обычное", 1.55, .31f),
    MAJOR("Сильное", 3.15, .44f),
    CATASTROPHIC("Катастрофическое", 6.8, .60f)
}

internal data class CosmicEpisode(
    val kind: CosmicEventKind = CosmicEventKind.QUIET,
    val age: Float = 0f,
    val duration: Float = 1f,
    val x: Float = .5f,
    val y: Float = .37f,
    val serial: Int = 0,
    val intensity: CosmicEventIntensity = CosmicEventIntensity.NORMAL,
    val axis:Float = 0f
) {
    val progress: Float get() = (age / duration).coerceIn(0f, 1f)
    val alpha: Float get() {
        val t = progress
        return (min(1f, t * 5f) * min(1f, (1f - t) * 5f)).coerceIn(0f, 1f)
    }
}

internal data class CelestialBody(
    val id: Int,
    val kind: Int,
    val mass: Float,
    val depth: Float,
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val rotation: Float = 0f,
    val age: Float = 0f,
    val lifetime: Float = 58f,
    val sizeFactor: Float = 1f,
    val material: MatterMaterial = MatterMaterial.ROCK,
    val temperature: Float = 300f,
    val stellarClass: StellarClass = StellarClass.PLANET,
    val nature:CelestialNature = CelestialNature.ORDINARY,
    val spinAngularMomentum:Double = 0.0,
    val climateFactor:Float = 1f
)

internal data class GrainRenderPair(
    val previous: FloatArray,
    val current: FloatArray,
    val committedAtNanos: Long,
    val intervalNanos: Long = 16_666_667L,
    val bodyPrevious:List<CelestialBody> = emptyList(),
    val bodyCurrent:List<CelestialBody> = emptyList()
)

internal data class SpaceRipple(val x: Float, val y: Float, val age: Float = 0f)

internal data class CosmicRemnant(
    val kind: CosmicEventKind, val x: Float, val y: Float,
    val age: Float = 0f, val lifetime: Float = 38f
)

internal class CosmicWorld(
    seed: Int = 42,
    initialMatterMode: MatterMode = MatterMode.BALANCED,
    initialPreset: CosmicPreset = CosmicPreset.DEEP_SPACE,
    initialGravity: Float = 1f
) {
    private val random = Random(seed)
    var preset by mutableStateOf(initialPreset)
        private set
    // The old cosmetic/bonded matter field remains for older API compatibility.
    // Only sand is integrated and rendered by the active cosmic simulation.
    val matter = MatterField(Random(seed + 109))
    val sand = AstronomicalUniverse()
    // A single immutable snapshot avoids torn old/current reads on the UI.
    // The UI can draw at the native refresh rate while physics runs at a
    // bounded 30/60 Hz on a separate worker.
    var grainFrames by mutableStateOf(GrainRenderPair(
        FloatArray(0), FloatArray(0), System.nanoTime()))
        private set
    val grainFrame: FloatArray get() = grainFrames.current
    var physicsCostMs by mutableFloatStateOf(0f)
        private set
    private var publishedIds = IntArray(0)
    private fun publishGrains() {
        val sameParticles = publishedIds.size==sand.grains.size &&
            sand.grains.indices.all { publishedIds[it]==sand.grains[it].id }
        if (!sameParticles) publishedIds = IntArray(sand.grains.size) {sand.grains[it].id}
        val next = sand.renderFrame(bodies.filter {it.nature==CelestialNature.BLACK_HOLE}.map {it.id}.toSet())
        val old = grainFrames.current
        val now = System.nanoTime()
        val interval = (now-grainFrames.committedAtNanos).coerceIn(8_333_333L,100_000_000L)
        if(civilization.ships!=shipFrames.current) {
            val shipInterval=(now-shipFrames.committedAtNanos).coerceIn(8_333_333L,400_000_000L)
            shipFrames=ShipRenderPair(shipFrames.current,civilization.ships,now,shipInterval)
        }
        grainFrames = GrainRenderPair(
            if (sameParticles && old.size == next.size) old else next,
            next,now,interval,
            if(sameParticles)grainFrames.bodyCurrent else emptyList(),sand.summarizeBodies(bodies)
        )
    }
    var matterMode by mutableStateOf(initialMatterMode)
        private set
    @Synchronized
    fun updateMatterMode(value: MatterMode) {
        if (matterMode == value) return
        matterMode = value
        sand.updateMode(value)
        // In the curated orbital scenes detail mode actually changes the
        // number of physically independent grains, not just an upper bound.
        reset()
    }
    var gravity by mutableFloatStateOf(if(initialGravity.isFinite()) initialGravity.coerceIn(.25f,2.2f) else 1f)
        private set
    private val impactMoments = HashMap<Long, Float>()
    private fun newBodyId(): Int = idSequence++
    var bodies by mutableStateOf<List<CelestialBody>>(initialBodies())
        private set
    var ripples by mutableStateOf<List<SpaceRipple>>(emptyList())
        private set
    var previousBodies by mutableStateOf(bodies)
        private set
    var remnants by mutableStateOf<List<CosmicRemnant>>(emptyList())
        private set
    var mathPinned by mutableStateOf(false)
        private set
    var elapsed by mutableFloatStateOf(0f)
        private set
    var episode by mutableStateOf(CosmicEpisode())
        private set
    var depthShift by mutableFloatStateOf(0f)
        private set
    private var idSequence = 50
    private var eventSerial = 0
    // Rare external arrivals: a fresh spherical GROUP of grains, not sprites.
    private var spawnAt = 46f
    private var eventAt = 28f
    private var lastBurstAt = -100f
    private val tunneled = mutableSetOf<Int>()
    private var nextMathAt = 13f
    var mathMoment by mutableStateOf(MathMoment())
        private set
    var selectedBody by mutableStateOf<CelestialBody?>(null)
        private set
    var eventHistory by mutableStateOf<List<String>>(emptyList())
        private set
    private var inspectAge = 0f
    private var pinnedFor = 0f

    // Only world simulation time changes. The app picker/keyboard remain at full speed.
    var timeScale by mutableFloatStateOf(
        if (initialPreset == CosmicPreset.DEEP_SPACE) 1f else .5f)
        private set
    var brushPoint by mutableStateOf<SpaceRipple?>(null)
        private set
    var brushMass by mutableFloatStateOf(0f)
        private set
    private var brushStartX = 0f
    private var brushStartY = 0f
    private var brushPrevX = 0f
    private var brushPrevY = 0f
    private var brushGestureDistance = 0f
    private var brushInitialBodyCount = 0
    private val replayBuffer = ArrayDeque<List<CelestialBody>>(32)
    private val particleReplay = ArrayDeque<AstronomicalUniverse.Snapshot>(32)
    private var replayTimer = 0f
    private var accumulator = 0.0
    var automaticEvents by mutableStateOf(true)
        private set
    var showTrails by mutableStateOf(true)
        private set
    var showVectors by mutableStateOf(false)
        private set
    var reducedMotion by mutableStateOf(false)
        private set
    var ambientMath by mutableStateOf(false)
        private set
    var learningEnabled by mutableStateOf(true)
        private set
    var learning by mutableStateOf(LearningReading())
        private set
    private var learner = GravityLearner()
    private var observationBefore = emptyList<CelestialBody>()
    private var observationTime = 0f
    var telemetry by mutableStateOf(UniverseTelemetry())
        private set
    var trails by mutableStateOf<Map<Int, List<OrbitPoint>>>(emptyMap())
        private set
    private var observationTimer = 0f
    private data class ReplayMeta(val time: Float, val episode: CosmicEpisode,
        val idSequence: Int, val eventAt: Float, val spawnAt: Float,
        val life:CivilizationSimulation.Snapshot,val quantum:QuantumExperiment.Snapshot,
        val radiation:Map<Int,Float>,val doseSerial:Int,val exposed:Set<Int>,
        val waves:Int,val waveAt:Float)
    private val replayMeta = ArrayDeque<ReplayMeta>(32)

    @Synchronized
    fun configureObservation(events: Boolean = automaticEvents,
        trails: Boolean = showTrails, vectors: Boolean = showVectors,
        motion: Boolean = reducedMotion, maths: Boolean = ambientMath,
        ml: Boolean = learningEnabled) {
        automaticEvents = events; showTrails = trails; showVectors = vectors
        reducedMotion = motion; ambientMath = maths; learningEnabled = ml
        if (!trails) this.trails = emptyMap()
        if (!ml) { learner = GravityLearner(); learning = LearningReading() }
    }

    private fun observeWorld(h: Float) {
        observationTimer += h
        if (observationTimer < .12f) return
        observationTimer = 0f
        val current = sand.summarizeBodies(bodies)
        if (learningEnabled && observationBefore.isNotEmpty()) {
            learner.observe(observationBefore, current, elapsed-observationTime)
            learning = learner.reading
        }
        observationBefore = current
        observationTime = elapsed
        quantumReading=quantum.reading
        val totals = sand.totals()
        telemetry = UniverseTelemetry(sand.grains.size, totals.mass,
            totals.kinetic, totals.internal, sand.totalEnergy(gravity),
            sand.reactions, sand.phaseTransitions,
            sand.grains.count { it.owner < 0 },sand.radiatedHeat,sand.externalRadiationEnergy)
        if (showTrails) trails = current.associate { body ->
            body.id to ((trails[body.id] ?: emptyList()) +
                OrbitPoint(body.x,body.y)).takeLast(160)
        }
    }
    private var physicsFrameNumber = 0L
    private var cascadeBudget = 0
    var maxBodies by mutableIntStateOf(23)
        private set
    var wallsEnabled by mutableStateOf(false)
        private set
    var dust by mutableStateOf<List<ImpactDust>>(emptyList())
        private set
    val ecosystem=CivilizationSimulation(Random(seed+725))
    var civilizationConfig by mutableStateOf(CivilizationConfig())
        private set
    var civilization by mutableStateOf(CivilizationReading())
        private set
    var shipFrames by mutableStateOf(ShipRenderPair())
        private set
    private var lifeTimer=0f
    private val quantum=QuantumExperiment(Random(seed+251))
    var quantumReading by mutableStateOf(quantum.reading)
        private set
    var quantumEnabled by mutableStateOf(true)
        private set
    var quantumCoupling by mutableFloatStateOf(1f)
        private set
    var quantumDephasing by mutableFloatStateOf(.06f)
        private set
    var radiativeClimate by mutableStateOf(true)
        private set
    var rareCatastrophes by mutableStateOf(true)
        private set
    var catastropheRate by mutableStateOf(CatastropheRate.NATURAL)
        private set
    private val extremes=ExtremeEventDirector(Random(seed+5129))
    var hourlyScenes by mutableStateOf(false)
        private set
    var scenePool by mutableStateOf(CosmicPreset.entries.toSet())
        private set
    var matchSceneAccent by mutableStateOf(true)
        private set
    private val hourCycle=HourlySceneCycle()
    var sceneRevision by mutableIntStateOf(0)
        private set
    var clockWallsEnabled by mutableStateOf(true)
        private set
    private var viewport=UniverseViewport(1f,1f)
    private var rootX=0f;private var rootY=0f;private var displayDensity=1f
    private var clockField:GlyphDistanceField?=null
    private var clockLeft=0f;private var clockTop=0f
    private var clockObstacle:ClockObstacle?=null
    private val radiationDose=HashMap<Int,Float>()
    private var doseSerial=-1
    private val exposedBodies=HashSet<Int>()
    private var meteorWaves=0
    private var nextMeteorWave=0f
    var accretionLight by mutableStateOf<Map<Int,Float>>(emptyMap())
        private set

    @Synchronized
    fun updateViewport(width:Float,height:Float,x:Float=0f,y:Float=0f,density:Float=1f) {
        if(width<=0 || height<=0 || !width.isFinite() || !height.isFinite())return
        viewport=UniverseViewport(width,height);rootX=x;rootY=y
        displayDensity=density.coerceIn(.5f,8f);alignClock()
    }
    private fun alignClock() {
        clockObstacle=clockField?.let { ClockObstacle(it,
            (clockLeft-rootX-viewport.left)/viewport.side,
            (clockTop-rootY-viewport.top)/viewport.side,1f/viewport.side,1.3f*displayDensity/viewport.side) }
    }
    @Synchronized
    fun updateClock(field:GlyphDistanceField?,left:Float=0f,top:Float=0f) {
        clockField=field;clockLeft=left;clockTop=top;alignClock()
    }
    @Synchronized
    fun configureCosmos(rare:Boolean=rareCatastrophes,rate:CatastropheRate=catastropheRate,
        hourly:Boolean=hourlyScenes,pool:Set<CosmicPreset> = scenePool,
        clockWall:Boolean=clockWallsEnabled,accent:Boolean=matchSceneAccent,
        quantumOn:Boolean=quantumEnabled,coupling:Float=quantumCoupling,
        decoherence:Float=quantumDephasing,thermalBalance:Boolean=radiativeClimate) {
        rareCatastrophes=rare;catastropheRate=rate;hourlyScenes=hourly
        scenePool=pool.ifEmpty {setOf(preset)};clockWallsEnabled=clockWall;matchSceneAccent=accent
        quantumEnabled=quantumOn;radiativeClimate=thermalBalance
        if(coupling.isFinite())quantumCoupling=coupling.coerceIn(.1f,3f)
        if(decoherence.isFinite())quantumDephasing=decoherence.coerceIn(0f,2f)
    }
    @Synchronized
    fun configureCivilization(config:CivilizationConfig) {
        civilizationConfig=config.copy(pace=if(config.pace.isFinite())config.pace.coerceIn(.5f,3f) else 1f)
        if(config.mode==LifeMode.OFF){ecosystem.clear();shipFrames=ShipRenderPair()}
        else if(config.mode==LifeMode.SEEDED && civilization.colonies.isEmpty())ecosystem.seedFavorable(bodies,radiationDose)
        civilization=ecosystem.reading
    }
    @Synchronized
    fun seedCivilization(bodyId:Int):Boolean {
        if(civilizationConfig.mode==LifeMode.OFF)return false
        val body=bodies.firstOrNull {it.id==bodyId} ?: return false
        val result=ecosystem.seed(body,bodies,(radiationDose[body.id] ?: 0f).toDouble())
        civilization=ecosystem.reading
        return result
    }
    @Synchronized
    fun measureQuantum():Int {val result=quantum.measure();quantumReading=quantum.reading;return result}
    @Synchronized
    fun superposeQuantum(){quantum.resetSuperposition();quantumReading=quantum.reading}
    @Synchronized
    fun checkSceneHour(bucket:Long):Boolean {
        val decision=hourCycle.check(bucket,preset,scenePool.toList().sortedBy {it.ordinal},hourlyScenes) ?: return false
        val paused=timeScale==0f
        loadPreset(decision.preset)
        if(paused)timeScale=0f
        return true
    }
    private fun resetSystems() {
        ecosystem.clear();civilization=CivilizationReading();shipFrames=ShipRenderPair();lifeTimer=0f
        quantum.restore(QuantumExperiment().snapshot());quantumReading=quantum.reading
        radiationDose.clear();doseSerial=-1;exposedBodies.clear()
        meteorWaves=0;nextMeteorWave=0f;accretionLight=emptyMap()
    }
    private fun advanceSystems(h:Float) {
        var captured=false
        val holes=bodies.filter {it.nature==CelestialNature.BLACK_HOLE}
        var current=bodies
        val light=accretionLight.mapValues {it.value*kotlin.math.exp(-h*1.6f)}.filterValues {it>.0001f}.toMutableMap()
        for(hole in holes) {
            val result=sand.accrete(hole.id,horizonRadius(hole.mass))
            if(result.consumedMass>0) {
                captured=true
                val intrinsic=current.filter {it.id in result.removedOwners && it.nature==CelestialNature.BLACK_HOLE}
                    .sumOf {it.spinAngularMomentum}
                current=current.map {if(it.id==hole.id)it.copy(spinAngularMomentum=it.spinAngularMomentum+result.spin+intrinsic) else it}
                light[hole.id]=(light[hole.id] ?: 0f)+result.consumedMass.toFloat()
            }
        }
        if(captured)bodies=sand.summarizeBodies(current)
        accretionLight=light
        if(doseSerial!=episode.serial){doseSerial=episode.serial;exposedBodies.clear()}
        for(id in radiationDose.keys.toList()) {
            val value=radiationDose.getValue(id)*kotlin.math.exp(-h*.025f)
            if(value<.001f)radiationDose.remove(id) else radiationDose[id]=value
        }
        for(body in bodies) {
            if(body.id in exposedBodies)continue
            val dose=radiationExposure(episode,body.x,body.y)
            if(dose>0) {
                radiationDose[body.id]=(radiationDose[body.id] ?: 0f)+dose*episode.intensity.heatFactor.toFloat()
                exposedBodies.add(body.id)
            }
        }
        if(quantumEnabled)quantum.step(h,quantumCoupling,quantumDephasing)
        if(meteorWaves>0 && elapsed>=nextMeteorWave) {
            spawnStreamWave();meteorWaves--;nextMeteorWave=elapsed+.85f
        }
        lifeTimer+=h
        if(lifeTimer>=.05f) {
            val duration=lifeTimer;lifeTimer=0f
            var latest=sand.summarizeBodies(bodies)
            if(radiativeClimate) {
                sand.exchangeStellarRadiation(latest,duration)
                latest=sand.summarizeBodies(bodies)
            }
            val detonations=ecosystem.step(duration,latest,gravity,
                if(learningEnabled && learning.samples>=24)learning.gravity.toFloat().coerceIn(.25f,2.2f) else 1f,
                radiationDose,civilizationConfig,learningEnabled,
                if(wallsEnabled)viewport.screenBounds() else SpaceBounds(-2f,-2f,3f,3f),
                if(wallsEnabled && clockWallsEnabled)clockObstacle else null,bounce=wallsEnabled)
            for(bomb in detonations)sand.disruptBody(bomb.bodyId,bomb.energy)
            if(detonations.isNotEmpty())bodies=sand.summarizeBodies(bodies)
            civilization=ecosystem.reading
        }
    }
    private fun spawnStreamWave() {
        repeat(2) {
            if(bodies.size>=maxBodies || sand.grains.size+12>matterMode.maxParticles)return
            val body=CelestialBody(idSequence++,1,.08f+random.nextFloat()*.42f,1f,
                .04f+random.nextFloat()*.92f,
                if(wallsEnabled)viewport.screenBounds().top+.025f else -.08f,
                (random.nextFloat()-.5f)*.1f,.11f+random.nextFloat()*.13f,
                lifetime=3600f,material=if(random.nextBoolean())MatterMaterial.IRON else MatterMaterial.ICE,
                temperature=180f,nature=CelestialNature.ASTEROID)
            sand.seed(body,12);bodies=bodies+body
        }
    }

    private fun seedStartingMatter() {
        sand.clear()
        sand.updateMode(matterMode)
        bodies.forEach { body ->
            sand.seed(body, presetGrainCount(preset, body, matterMode))
        }
        when (preset) {
            CosmicPreset.SATURN_RINGS -> sand.seedOrbitalDisk(
                .50f, .49f, 8f,
                if (matterMode == MatterMode.DETAILED) 1050 else 355,
                .063f, .165f,gravity=gravity)
            CosmicPreset.PROTOPLANETARY -> sand.seedOrbitalDisk(
                .50f, .50f, 10f,
                if (matterMode == MatterMode.DETAILED) 1800 else 475,
                .085f, .38f,rocky=true,gravity=gravity)
            CosmicPreset.BLACK_HOLE_NURSERY -> sand.seedOrbitalDisk(
                .5f,.5f,18f,if(matterMode==MatterMode.DETAILED)1100 else 360,
                .035f,.15f,rocky=true,gravity=gravity)
            CosmicPreset.WHITE_DWARF_RELIC -> sand.seedOrbitalDisk(
                .5f,.5f,3f,if(matterMode==MatterMode.DETAILED)650 else 260,
                .08f,.32f,rocky=true,gravity=gravity)
            CosmicPreset.COLD_NURSERY -> sand.seedOrbitalDisk(
                .5f,.5f,5f,if (matterMode == MatterMode.DETAILED) 1100 else 360,
                .29f,.40f,gravity=gravity)
            else -> Unit
        }
        previousBodies = bodies
        publishGrains()
        if(civilizationConfig.mode!=LifeMode.OFF &&
            (civilizationConfig.mode==LifeMode.SEEDED || preset==CosmicPreset.LIVING_SYSTEM || preset==CosmicPreset.ASTEROID_CORRIDOR)) {
            ecosystem.seedFavorable(if(preset==CosmicPreset.ASTEROID_CORRIDOR)
                bodies.filterNot {CivilizationSimulation.isAsteroid(it)} else bodies,
                radiationDose,if(preset==CosmicPreset.ASTEROID_CORRIDOR)1 else 2)
            civilization=ecosystem.reading
        }
        observeWorld(.12f)
    }

    /** Changing a scene resets ONLY the little toy cosmos, never launcher
     * settings, pinned apps or system state. Every preset has its own grains. */
    @Synchronized
    fun loadPreset(value: CosmicPreset) {
        preset = value
        reset()
    }
    init { seedStartingMatter() }
    @Synchronized
    fun updateMaximumBodies(value: Int) {
        maxBodies=value.coerceIn(12,64)
        // Existing matter is retained. A lower limit only restricts new creation.
    }
    @Synchronized
    fun updateWalls(enabled: Boolean) {wallsEnabled=enabled}

    var lastEncounter by mutableStateOf("none")
        private set

    private fun initialBodies(): List<CelestialBody> {
        if (preset != CosmicPreset.DEEP_SPACE) return presetBodies(preset,gravity)
        // Deliberately no permanent massive object in the center.
        return listOf(
            CelestialBody(1, 2, 1.0f, 1.35f, .18f, .19f, .006f, .012f, .1f,
                lifetime = 3600f, material = MatterMaterial.ROCK),
            CelestialBody(2, 3, .7f, 2.2f, .83f, .28f, -.008f, .003f, .7f,
                lifetime = 3600f, material = MatterMaterial.HYDROGEN,
                temperature = 3400f, stellarClass = StellarClass.STAR),
            CelestialBody(3, 4, 1.2f, .95f, .76f, .77f, -.015f, -.007f, 1.5f,
                lifetime = 3600f, material = MatterMaterial.SILICATE),
            CelestialBody(4, 5, .4f, 2.7f, .23f, .63f, .013f, -.005f, 2.4f,
                lifetime = 3600f, material = MatterMaterial.ICE, temperature = 160f,
                stellarClass = StellarClass.CLOUD),
            CelestialBody(5, 6, 2.1f, 1.12f, .38f, .80f, .015f, -.009f, .3f,
                lifetime = 3600f, material = MatterMaterial.IRON),
            CelestialBody(6, 8, 1.7f, 1.50f, .73f, .44f, -.014f, -.005f, 2f,
                lifetime = 3600f, material = MatterMaterial.PLASMA,
                temperature = 5400f, stellarClass = StellarClass.QUASAR)
        )
    }

    /**
     * Interactive observatory action: an EXTERNAL user creates a genuine
     * collection of gravitational grains, no invisible centre or prefab.
     * All normal physics, capture and destruction applies immediately.
     */
    @Synchronized
    fun createObject(material: MatterMaterial, mass: Float,
        px: Float = .68f, py: Float = .38f,
        launch: LaunchStyle = LaunchStyle.ORBIT,
        recipe: BodyRecipe? = null): Boolean {
        if (bodies.size >= maxBodies || sand.grains.size >= matterMode.maxParticles)
            return false
        if (!mass.isFinite() || !px.isFinite() || !py.isFinite()) return false
        val m = mass.coerceIn(.04f,24f)
        val classType = recipe?.classification ?: when (material) {
            MatterMaterial.HYDROGEN -> StellarClass.STAR
            MatterMaterial.PLASMA -> StellarClass.QUASAR
            MatterMaterial.AMMONIA, MatterMaterial.METHANE -> StellarClass.CLOUD
            else -> StellarClass.PLANET
        }
        val core = bodies.maxByOrNull { it.mass }
        val x = px.coerceIn(.06f,.94f)
        val y = py.coerceIn(.06f,.94f)
        val dx = x - (core?.x ?: .5f)
        val dy = y - (core?.y ?: .5f)
        val r = max(hypot(dx,dy),.09f)
        // Tangential orbit relative to the actual dominant body.
        val speed = OrbitMechanics.circularSpeed((core?.mass ?: 2f)+m,r,gravity)
        val multiplier = when (launch) {
            LaunchStyle.ORBIT -> 1f
            LaunchStyle.RETROGRADE -> -1f
            LaunchStyle.REST -> 0f
            LaunchStyle.ESCAPE -> 1.5f
        }
        val body = CelestialBody(
            idSequence++, if (classType == StellarClass.STAR) 12 else 2,
            m, 1f, x, y,
            (core?.vx ?: 0f) -dy/r*speed*multiplier,
            (core?.vy ?: 0f) +dx/r*speed*multiplier,
            lifetime = 3600f,
            material = material,
            temperature = recipe?.temperature ?: when (classType) {
                StellarClass.STAR -> 5300f
                StellarClass.QUASAR -> 6400f
                StellarClass.CLOUD -> 175f
                StellarClass.PLANET -> if (material == MatterMaterial.MAGMA)
                    2100f else 290f
            },
            stellarClass = classType,
            nature = recipe?.nature ?: CelestialNature.ORDINARY,
            sizeFactor = when(recipe?.nature) {
                CelestialNature.NEUTRON_STAR -> .25f
                CelestialNature.WHITE_DWARF -> .5f
                else -> (.65f+.20f*sqrt(m)).coerceIn(.6f,1.7f)
            }
        )
        val requested = when {
            m < .20f -> 15
            m < 1f -> 45
            m < 4f -> 86
            else -> 180
        }
        val n = min(requested, matterMode.maxParticles - sand.grains.size)
        if (n < 8) return false
        sand.seed(body,n)
        bodies = bodies + body
        previousBodies = bodies
        publishGrains()
        return true
    }

    @Synchronized
    fun applyObservatoryImpulse(x: Float, y: Float,
        strength: Float): Boolean {
        if (sand.grains.isEmpty()) return false
        sand.perturb(x.coerceIn(0f,1f), y.coerceIn(0f,1f),
            strength.coerceIn(.02f,.30f))
        bodies = sand.summarizeBodies(bodies)
        publishGrains()
        return true
    }

    @Synchronized
    fun spawnGiant(kind: Int = 97) {
        if (bodies.size >= maxBodies || bodies.any { it.kind in 97..104 }) return
        val chosen=kind.coerceIn(97,104)
        val giantBody = CelestialBody(
            idSequence++,chosen,14f+random.nextFloat()*18f,
            1.6f,.2f+random.nextFloat()*.6f,
            .22f+random.nextFloat()*.56f,
            (random.nextFloat()-.5f)*.015f,
            (random.nextFloat()-.5f)*.015f,
            lifetime=3600f,sizeFactor=1.7f,
            material=MatterMaterial.PLASMA,temperature=6800f,
            stellarClass=StellarClass.QUASAR)
        val particles = if (matterMode == MatterMode.DETAILED) 220 else 80
        if (sand.grains.size + particles <= matterMode.maxParticles) {
            bodies += giantBody
            sand.seed(giantBody, particles)
            publishGrains()
        }
    }

    /**
     * Explicit reproducible device stress scene. Adds real grains from outside
     * only on deliberate user input, never from impact or scheduled events.
     */
    @Synchronized
    fun populateStressScene(target: Int = 4600) {
        updateMatterMode(MatterMode.DETAILED)
        val goal = target.coerceIn(500, matterMode.maxParticles)
        if (sand.grains.size >= goal) return
        val slots = (maxBodies - bodies.size).coerceAtLeast(1)
        val per = kotlin.math.ceil((goal-sand.grains.size).toDouble()/slots)
            .toInt().coerceAtLeast(1)
        var added = 0
        while (sand.grains.size < goal && added < slots) {
            val n = min(per, goal - sand.grains.size)
            val theta = 2.0 * PI * added / slots
            val px = (.50 + .32 * cos(theta)).toFloat()
            val py = (.50 + .30 * sin(theta)).toFloat()
            val vx = (-.008 * sin(theta)).toFloat()
            val vy = (.008 * cos(theta)).toFloat()
            val body = CelestialBody(
                idSequence++, 2 + added % 24, n / 80f, 1f,
                px, py, vx, vy, lifetime = 3600f,
                material = if (added % 3 == 0)
                    MatterMaterial.ICE else MatterMaterial.ROCK
            )
            sand.seed(body, n)
            bodies += body
            added++
        }
        publishGrains()
    }

    @Synchronized
    fun updateGravity(value: Float) {
        if (value.isFinite()) {
            gravity = value.coerceIn(.25f, 2.2f)
            learner = GravityLearner(); learning = LearningReading()
            observationBefore = emptyList()
        }
    }

    /** Spawns just outside one screen corner, with a velocity toward the scene. */
    @Synchronized
    fun spawnFromCorner(burst: Boolean = false, physicalCluster: Boolean = false) {
        if (bodies.size >= maxBodies || sand.grains.size >= matterMode.maxParticles) return
        val corner = random.nextInt(4)
        val spread = random.nextFloat() * .17f - .085f
        val (x, y) = when (corner) {
            0 -> -.09f to -.07f
            1 -> 1.09f to -.07f
            2 -> -.09f to 1.07f
            else -> 1.09f to 1.07f
        }
        val targetX = .26f + random.nextFloat() * .48f + spread
        val targetY = .19f + random.nextFloat() * .59f
        val dx = targetX - x
        val dy = targetY - y
        val length = max(hypot(dx, dy), .05f)
        val speed = if (burst) .17f + random.nextFloat() * .12f
                    else if (physicalCluster) .040f + random.nextFloat() * .037f
                    else .048f + random.nextFloat() * .085f
        val depth = .65f + random.nextFloat() * 2.6f
        val giant = !burst && bodies.none { it.kind in 97..104 } &&
            random.nextInt(720) == 0
        val chosenKind = if (physicalCluster)
            listOf(2, 3, 4, 5, 6, 7, 12, 15)[random.nextInt(8)]
            else if (burst) 1 else if (giant) 97 + random.nextInt(8)
            else 1 + random.nextInt(96)
        val chosenClass = if (physicalCluster) StellarClass.PLANET
            else stellarClassFromKind(chosenKind)
        // Distinct physical scales: tiny moonlets, rocky planets, giants
        // and extremely rare titans. No display-only huge sprites.
        val scaleRoll = random.nextInt(100)
        val mass = when {
            giant -> 14f + random.nextFloat()*18f
            physicalCluster && scaleRoll < 16 ->
                .08f + random.nextFloat()*.17f
            physicalCluster && scaleRoll < 88 ->
                .65f + random.nextFloat()*1.45f
            physicalCluster -> 3f + random.nextFloat()*5f
            scaleRoll < 19 -> .025f + random.nextFloat()*.13f
            scaleRoll < 73 -> .25f + random.nextFloat()*1.6f
            scaleRoll < 96 -> 2f + random.nextFloat()*4f
            else -> 8f + random.nextFloat()*12f
        }
        val body = CelestialBody(
            id = idSequence++,
            kind = chosenKind,
            mass = mass,
            depth = depth,
            x = x, y = y, vx = dx / length * speed,
            vy = dy / length * speed, rotation = random.nextFloat() * 6.28f,
            lifetime = 3600f,
            sizeFactor = if (giant) 1.7f else
                (.55f + .20f*sqrt(mass.coerceAtLeast(.02f))).coerceIn(.5f,2.0f),
            material = when (chosenClass) {
                StellarClass.QUASAR -> MatterMaterial.PLASMA
                StellarClass.STAR -> MatterMaterial.HYDROGEN
                StellarClass.CLOUD -> MatterMaterial.METHANE
                StellarClass.PLANET -> if (physicalCluster)
                    listOf(MatterMaterial.ROCK, MatterMaterial.IRON,
                        MatterMaterial.ICE, MatterMaterial.SILICATE,
                        MatterMaterial.CARBON)[random.nextInt(5)]
                    else materialForKind(chosenKind)
            },
            temperature = when (chosenClass) {
                StellarClass.QUASAR -> 5200f
                StellarClass.STAR -> 3200f
                StellarClass.CLOUD -> 190f
                StellarClass.PLANET -> 180f + random.nextFloat() * 920f
            },
            stellarClass = chosenClass
        )
        bodies = bodies + body
        // Retain capacity for the full body limit instead of exhausting
        // the free-grain budget after only a handful of arrivals.
        val requested = when {
            physicalCluster -> 72 + random.nextInt(34)
            giant -> if (matterMode == MatterMode.DETAILED) 190 else 48
            mass < .16f -> 10
            mass > 7f -> if (matterMode == MatterMode.DETAILED) 145 else 30
            mass > 3f -> if (matterMode == MatterMode.DETAILED) 90 else 25
            else -> 18
        }
        sand.seed(body, minOf(requested,
            matterMode.maxParticles - sand.grains.size))
        publishGrains()
    }

    @Synchronized
    fun trigger(
        kind: CosmicEventKind,
        intensity: CosmicEventIntensity = CosmicEventIntensity.NORMAL
    ) {
        // Mathematical diagrams and cinematic events occupy separate time slots.
        // An event replaces any math lesson rather than rendering across its text.
        if (kind != CosmicEventKind.QUIET) {
            mathMoment = MathMoment()
            mathPinned = false
            pinnedFor = 0f
            nextMathAt = elapsed + 7f
        }
        if (kind == CosmicEventKind.QUIET) {
            episode = CosmicEpisode()
            eventAt = elapsed + 32f
            return
        }
        eventSerial++
        tunneled.clear()
        eventHistory = (eventHistory +
            (kind.name + " / " + intensity.label)).takeLast(10)
        // Anchor active physics to REAL matter rather than an empty corner.
        // Solar phenomena prefer hot gas, tides prefer a compact massive core.
        val selectedSource = when (kind) {
            CosmicEventKind.SOLAR_WIND, CosmicEventKind.IONIZATION_FRONT,
            CosmicEventKind.MAGNETAR_FLARE, CosmicEventKind.PULSAR,
            CosmicEventKind.QUASAR_JET, CosmicEventKind.SOLAR_FLARE ->
                bodies.filter { it.stellarClass == StellarClass.STAR ||
                    it.stellarClass == StellarClass.QUASAR }
                    .maxByOrNull { it.mass }
                    ?: bodies.maxByOrNull { it.mass }
            else -> bodies.maxByOrNull { it.mass }
        }
        episode = CosmicEpisode(
            kind = kind, age = 0f,
            duration = when (kind) {
                CosmicEventKind.SINGULARITY -> 17f
                CosmicEventKind.NEURAL -> 16f
                CosmicEventKind.HARMONICS -> 17f
                CosmicEventKind.LENS -> 13f
                CosmicEventKind.METEORS -> 10f
                CosmicEventKind.NOVA -> 13f
                CosmicEventKind.PULSAR -> 17f
                CosmicEventKind.WORMHOLE -> 16f
                CosmicEventKind.NEBULA -> 18f
                CosmicEventKind.ENCOUNTER -> 14f
                CosmicEventKind.FILAMENT -> 16f
                CosmicEventKind.RADIATION -> 12f
                CosmicEventKind.RING_WAVE -> 14f
                CosmicEventKind.REACTION -> 11f
                CosmicEventKind.ECLIPSE -> 25f
                CosmicEventKind.COSMIC_WEB -> 28f
                CosmicEventKind.PHASE_TIDE -> 23f
                CosmicEventKind.MEGASTRUCTURE -> 34f
                CosmicEventKind.GALAXY_COLLISION -> 35f
                CosmicEventKind.VOID_BLOOM -> 36f
                CosmicEventKind.GRAVITATIONAL_CAUSTIC -> 32f
                CosmicEventKind.PARTICLE_CASCADE -> 13f
                CosmicEventKind.STELLAR_NURSERY -> 21f
                CosmicEventKind.ECHO_SHELL -> 12f
                CosmicEventKind.TIDAL_CAPTURE -> 16f
                CosmicEventKind.POLAR_JET -> 14f
                CosmicEventKind.ORBITAL_RESONANCE -> 18f
                CosmicEventKind.DARK_FLOW -> 17f
                CosmicEventKind.GEOMETRY_SHIFT -> 17f
                CosmicEventKind.SOLAR_FLARE -> 14f
                CosmicEventKind.DUAL_RING -> 17f
                CosmicEventKind.COMET_SWARM -> 13f
                CosmicEventKind.ACCRETION_BURST -> 14f
                CosmicEventKind.CRYSTALLIZATION -> 21f
                CosmicEventKind.PLASMA_STORM -> 12f
                CosmicEventKind.QUASAR_JET -> 15f
                CosmicEventKind.COSMIC_SHOCK -> 10f
                CosmicEventKind.THERMAL_SPIKE -> 12f
                CosmicEventKind.CRYO_TIDE -> 15f
                CosmicEventKind.MAGNETAR_FLARE -> 16f
                CosmicEventKind.SOLAR_WIND -> 14f
                CosmicEventKind.TIDAL_PULSE -> 13f
                CosmicEventKind.IONIZATION_FRONT -> 12f
                CosmicEventKind.GAMMA_BURST -> 12f
                CosmicEventKind.DISTANT_CLUSTER_BURST -> 24f
                CosmicEventKind.COLOSSAL_METEOR_STREAM -> 22f
                CosmicEventKind.KILONOVA -> 18f
                CosmicEventKind.SUPERNOVA -> 16f
                CosmicEventKind.QUANTUM_COHERENCE -> 22f
                CosmicEventKind.MICROLENSING -> 18f
                else -> 1f
            },
            // Pulsars and quasars act on actual existing hot bodies, not
            // hidden attractors. All ejection impulses consume particle heat.
            x = selectedSource?.x ?: .5f,
            y = selectedSource?.y ?: .5f,
            serial = eventSerial,
            intensity = intensity,
            axis = random.nextFloat()*2f*PI.toFloat()
        )
        if(kind==CosmicEventKind.GAMMA_BURST || kind==CosmicEventKind.DISTANT_CLUSTER_BURST || kind==CosmicEventKind.KILONOVA) {
            // External cosmic radiation enters from a real screen edge; it has
            // an explicit energy ledger rather than an invented gravitating mass.
            val bounds=viewport.screenBounds()
            val target=bodies.firstOrNull {it.stellarClass==StellarClass.PLANET}
            val sx=bounds.left-.16f;val sy=bounds.top+.1f
            episode=episode.copy(x=sx,y=sy,axis=kotlin.math.atan2((target?.y ?: .5f)-sy,(target?.x ?: .5f)-sx))
        }
        if(kind==CosmicEventKind.COLOSSAL_METEOR_STREAM){meteorWaves=16;nextMeteorWave=elapsed}
        if(kind==CosmicEventKind.QUANTUM_COHERENCE)quantum.resetSuperposition()
        // The next event starts roughly one minute after this one begins,
        // not one minute *after* its end. Event durations stay much shorter.
        eventAt = elapsed + 32f + random.nextFloat() * 16f
        if (kind == CosmicEventKind.METEORS) {
            // All six templates can witness incoming matter. In curated
            // orbits keep the external impact small unless the user selects
            // MAJOR/CATASTROPHIC intensity explicitly.
            val practical = if (preset == CosmicPreset.DEEP_SPACE ||
                preset == CosmicPreset.PROTOPLANETARY ||
                intensity == CosmicEventIntensity.CATASTROPHIC)
                intensity else CosmicEventIntensity.FLICKER
            spawnMeteorShower(practical)
        }
    }

    /** Two or three FAST, HOT, actual granular meteorite clusters arrive
     * from above. No cosmetic sparks, nor particles emitted by a collision. */
    @Synchronized
    private fun spawnMeteorShower(intensity: CosmicEventIntensity) {
        // Unscheduled manual METEORS defaults to the familiar 2-3 clusters.
        val waves = when (intensity) {
            CosmicEventIntensity.FLICKER -> 1
            CosmicEventIntensity.NORMAL -> 2 + random.nextInt(2)
            CosmicEventIntensity.MAJOR -> 3 + random.nextInt(2)
            CosmicEventIntensity.CATASTROPHIC -> 5
        }
        val grainsPerMeteor = when (intensity) {
            CosmicEventIntensity.FLICKER -> 12
            CosmicEventIntensity.NORMAL -> 26
            CosmicEventIntensity.MAJOR -> 34
            CosmicEventIntensity.CATASTROPHIC -> 46
        }
        repeat(waves) { k ->
            if (bodies.size >= maxBodies ||
                sand.grains.size + grainsPerMeteor >
                    matterMode.maxParticles) return@repeat
            val x = .10f + random.nextFloat() * .80f
            val y = -.055f - k * .042f
            val body = CelestialBody(
                id = idSequence++, kind = 1,
                mass = when (intensity) {
                    CosmicEventIntensity.FLICKER -> .04f
                    CosmicEventIntensity.NORMAL -> .22f + random.nextFloat()*.18f
                    CosmicEventIntensity.MAJOR -> .55f + random.nextFloat()*.5f
                    CosmicEventIntensity.CATASTROPHIC -> 1.4f +
                        random.nextFloat()*1.4f
                },
                depth = 1f, x = x, y = y,
                vx = (random.nextFloat() - .5f) * .11f,
                vy = (when (intensity) {
                    CosmicEventIntensity.FLICKER -> .09f
                    CosmicEventIntensity.NORMAL -> .20f
                    CosmicEventIntensity.MAJOR -> .27f
                    CosmicEventIntensity.CATASTROPHIC -> .36f
                }) + random.nextFloat()*.08f,
                lifetime = 3600f,
                material = MatterMaterial.SULFUR,
                temperature = 1800f +
                    intensity.heatFactor.toFloat()*650f +
                    random.nextFloat() * 950f,
                stellarClass = StellarClass.PLANET,
                nature = CelestialNature.ASTEROID
            )
            bodies += body
            sand.seed(body, grainsPerMeteor)
        }
        publishGrains()
    }

    /**
     * Only events that operate on real grains are scheduled automatically.
     * Historical names remain valid for terminal commands/old saves, but no
     * geometric-only anomalies compete with the physical simulation.
     */
    private fun chooseEvent(): CosmicEventKind {
        if(preset==CosmicPreset.LIVING_SYSTEM || preset==CosmicPreset.QUANTUM_SHORES)
            return when(random.nextInt(8)) {
                0 -> CosmicEventKind.METEORS
                1,2 -> CosmicEventKind.MICROLENSING
                3,4 -> CosmicEventKind.QUANTUM_COHERENCE
                5 -> CosmicEventKind.CRYSTALLIZATION
                else -> CosmicEventKind.QUIET
            }
        return when (random.nextInt(184)) {
        in 0..19 -> CosmicEventKind.METEORS
        in 20..35 -> CosmicEventKind.PULSAR
        in 36..49 -> CosmicEventKind.QUASAR_JET
        in 50..63 -> CosmicEventKind.SINGULARITY
        in 64..75 -> CosmicEventKind.NOVA
        in 76..87 -> CosmicEventKind.SOLAR_FLARE
        in 88..97 -> CosmicEventKind.COMET_SWARM
        in 98..105 -> CosmicEventKind.ACCRETION_BURST
        in 106..113 -> CosmicEventKind.STELLAR_NURSERY
        in 114..119 -> CosmicEventKind.PLASMA_STORM
        in 120..124 -> CosmicEventKind.PARTICLE_CASCADE
        in 125..132 -> CosmicEventKind.CRYO_TIDE
        in 133..140 -> CosmicEventKind.MAGNETAR_FLARE
        in 141..150 -> CosmicEventKind.THERMAL_SPIKE
        in 151..156 -> CosmicEventKind.COSMIC_SHOCK
        in 157..165 -> CosmicEventKind.SOLAR_WIND
        in 166..173 -> CosmicEventKind.TIDAL_PULSE
        in 174..178 -> CosmicEventKind.IONIZATION_FRONT
        in 179..180 -> CosmicEventKind.QUANTUM_COHERENCE
        181 -> CosmicEventKind.MICROLENSING
        else -> CosmicEventKind.CRYSTALLIZATION
        }
    }

    /** One-click random cosmic event, or a user-requested power level. */
    @Synchronized
    fun triggerRandomEvent(intensity: CosmicEventIntensity? = null) {
        trigger(chooseEvent(), intensity ?: chooseIntensity())
    }

    private fun chooseIntensity(): CosmicEventIntensity = when (random.nextInt(
        if(preset==CosmicPreset.LIVING_SYSTEM || preset==CosmicPreset.QUANTUM_SHORES)83 else 100)) {
        in 0..42 -> CosmicEventIntensity.FLICKER
        in 43..82 -> CosmicEventIntensity.NORMAL
        else -> CosmicEventIntensity.MAJOR
    }

    @Synchronized
    fun summonMath(requested: MathKind? = null) {
        // An explicitly requested lesson takes priority over ongoing scenery.
        if (episode.kind != CosmicEventKind.QUIET) {
            episode = CosmicEpisode()
            eventAt = elapsed + 22f
        }
        mathPinned = false
        pinnedFor = 0f
        nextMathAt = elapsed + 23f
        mathMoment = MathMoment(
            kind = requested ?: when (preset) {
                CosmicPreset.RESONANCE, CosmicPreset.BINARY_STARS -> MathKind.FOURIER
                CosmicPreset.PROTOPLANETARY, CosmicPreset.COLD_NURSERY -> MathKind.KMEANS
                CosmicPreset.COLLISION_LAB -> MathKind.ENTROPY
                CosmicPreset.TROJAN_GARDEN -> MathKind.GRADIENT
                else -> MathKind.KALMAN
            },
            age = 0f,
            duration = 9.2f + random.nextFloat() * 3f,
            x = if (random.nextBoolean()) .08f else .51f,
            y = if (random.nextBoolean()) .15f else .65f,
            seed = random.nextInt()
        )
    }

    /**
     * Touching a live mathematical construction advances its derivation.
     * Touching elsewhere produces a local impulse.
     */
    @Synchronized
    fun interact(x: Float, y: Float) {
        val m = mathMoment
        if (m.active && x in (m.x - .04f)..(m.x + .43f) &&
            y in (m.y - .04f)..(m.y + .32f)) {
            mathPinned = true
            pinnedFor = 0f
            val next = ((m.progress * 3f).toInt() + 1).coerceAtMost(2)
            mathMoment = m.copy(age = (next / 3f + .04f) * m.duration)
            return
        }
        disturb(x, y)
    }

    @Synchronized
    fun disturb(x: Float, y: Float) {
        val bounds = if (wallsEnabled) {
            viewport.screenBounds()
        } else {
            SpaceBounds()
        }
        val px = x.coerceIn(bounds.left, bounds.right)
        val py = y.coerceIn(bounds.top, bounds.bottom)
        ripples = (ripples + SpaceRipple(px, py)).takeLast(4)
        val closest = bodies.minByOrNull { hypot(it.x - px, it.y - py) }
        selectedBody = closest?.takeIf {
            hypot(it.x - px, it.y - py) < .072f
        }
        inspectAge = 0f
        // An explicit finger impulse exchanges energy with the user, not with
        // a fabricated particle emitter. All masses and grain IDs remain.
        if (selectedBody == null) {
            sand.perturb(px, py, .045f)
        }
        // Body summaries are UI-only views, but must reflect the grain
        // velocities immediately after an intentional user impulse.
        bodies = sand.summarizeBodies(bodies)
        publishGrains()
    }

    @Synchronized
    fun inspectBody(id: Int) {
        selectedBody = bodies.firstOrNull { it.id == id }
        inspectAge = -1000f
    }

    @Synchronized
    fun updateTimeScale(next: Float) {
        require(next.isFinite())
        timeScale = when {
            next <= .01f -> 0f
            next <= .4f -> .25f
            next <= .75f -> .5f
            next <= 1.5f -> 1f
            else -> 2f
        }
    }

    @Synchronized
    fun startBrush(x: Float, y: Float) {
        val bounds = if (wallsEnabled) viewport.screenBounds() else SpaceBounds()
        brushStartX = x.coerceIn(bounds.left, bounds.right)
        brushStartY = y.coerceIn(bounds.top, bounds.bottom)
        brushPrevX = brushStartX
        brushPrevY = brushStartY
        brushGestureDistance = 0f
        brushInitialBodyCount = bodies.size
        brushMass = 1.2f
        brushPoint = SpaceRipple(brushStartX, brushStartY)
    }

    @Synchronized
    fun moveBrush(x: Float, y: Float) {
        val bounds = if (wallsEnabled) {
            viewport.screenBounds()
        } else {
            SpaceBounds()
        }
        val px = x.coerceIn(bounds.left, bounds.right)
        val py = y.coerceIn(bounds.top, bounds.bottom)
        brushGestureDistance += hypot(px - brushPrevX, py - brushPrevY)
        brushPrevX = px
        brushPrevY = py
        brushPoint = SpaceRipple(px, py)
        brushMass = (brushMass + .008f).coerceAtMost(4f)
    }

    /** A deliberate drag adds mass from outside the closed universe,
     * analogous to the separate explicit create-body command. A tap simply
     * nudges existing grains; ordinary collisions never instantiate grains.
     */
    @Synchronized
    fun endBrush() {
        val point = brushPoint ?: return
        if (brushGestureDistance > .065f && bodies.size < maxBodies &&
            sand.grains.size + 18 <= matterMode.maxParticles) {
            val dx = point.x - brushStartX
            val dy = point.y - brushStartY
            val norm = max(hypot(dx, dy), .005f)
            val speed = (norm * .65f).coerceIn(.04f, .24f)
            val newBody = CelestialBody(idSequence++, 3 + random.nextInt(20),
                .5f + random.nextFloat() * 1.5f, 1.1f,
                point.x, point.y, dx / norm * speed, dy / norm * speed,
                lifetime = 3600f)
            sand.seed(newBody, 18)
            bodies += newBody
            publishGrains()
        } else {
            sand.perturb(point.x, point.y,
                (.025f + brushGestureDistance * .20f).coerceAtMost(.085f))
            bodies = sand.summarizeBodies(bodies)
            publishGrains()
        }
        brushPoint = null
        brushMass = 0f
    }

    @Synchronized
    fun spawnBinary() {
        if (bodies.size > maxBodies-2) return
        val x = .20f + random.nextFloat() * .56f
        val y = .19f + random.nextFloat() * .56f
        val vx = .008f + random.nextFloat() * .018f
        val id = idSequence++
        val left = CelestialBody(id, 6, 1.4f, 1.1f, x-.048f, y, 0f, -vx,
            lifetime = 125f)
        val right = CelestialBody(idSequence++, 6, 1.4f, 1.1f,
            x+.048f, y, 0f, vx, lifetime = 125f)
        if (sand.grains.size + 80 > matterMode.maxParticles) return
        bodies = bodies + left + right
        sand.seed(left, 40); sand.seed(right, 40)
        publishGrains()
        lastEncounter = "binary"
    }

    /** Deterministic rewind of stored physical frames; excludes app UI state.
     * The small bounded buffer intentionally does not persist user history.
     */
    @Synchronized
    fun rewind(seconds: Int = 3): Boolean {
        if (replayBuffer.isEmpty() || particleReplay.isEmpty()) return false
        val target = elapsed-seconds.coerceIn(0,8)
        val index = replayMeta.indexOfLast { it.time <= target }.coerceAtLeast(0)
        val snapshot = replayBuffer.elementAt(index)
        val meta = replayMeta.elementAt(index)
        sand.restore(particleReplay.elementAt(index))
        bodies = snapshot
        previousBodies = snapshot
        elapsed = meta.time; episode = meta.episode; idSequence = meta.idSequence
        eventAt = meta.eventAt; spawnAt = meta.spawnAt
        ecosystem.restore(meta.life);civilization=ecosystem.reading
        quantum.restore(meta.quantum);quantumReading=quantum.reading
        radiationDose.clear();radiationDose.putAll(meta.radiation)
        doseSerial=meta.doseSerial;exposedBodies.clear();exposedBodies.addAll(meta.exposed)
        meteorWaves=meta.waves;nextMeteorWave=meta.waveAt
        shipFrames=ShipRenderPair(civilization.ships,civilization.ships,System.nanoTime())
        while (replayBuffer.size > index+1) {
            replayBuffer.removeLast(); particleReplay.removeLast(); replayMeta.removeLast()
        }
        accumulator = 0.0; replayTimer = 0f
        trails = emptyMap(); observationBefore = emptyList()
        learner = GravityLearner(); learning = LearningReading()
        mathMoment = MathMoment(); selectedBody = null
        publishGrains()
        observeWorld(.12f)
        return true
    }

    @Synchronized
    fun clearReplay() {
        replayBuffer.clear()
        particleReplay.clear()
        replayMeta.clear()
        replayTimer = 0f
    }

    @Synchronized
    fun shiftPerspective(dx: Float) {
        depthShift = (depthShift + dx).coerceIn(-1f, 1f)
    }

    @Synchronized
    fun reset() {
        resetSystems()
        sceneRevision++
        publishedIds=IntArray(0)
        bodies = initialBodies()
        previousBodies = bodies
        ripples = emptyList()
        remnants = emptyList()
        mathPinned = false
        pinnedFor = 0f
        elapsed = 0f
        accumulator = 0.0
        trails = emptyMap(); observationBefore = emptyList()
        observationTimer = 0f
        learner = GravityLearner(); learning = LearningReading()
        episode = CosmicEpisode()
        depthShift = 0f
        spawnAt = 46f
        eventAt = 28f
        lastBurstAt = -100f
        tunneled.clear()
        mathMoment = MathMoment()
        selectedBody = null
        eventHistory = emptyList()
        inspectAge = 0f
        nextMathAt = 13f
        timeScale = if (preset == CosmicPreset.DEEP_SPACE) 1f else .5f
        brushPoint = null
        brushMass = 0f
        cascadeBudget = 0
        lastEncounter = "none"
        dust=emptyList()
        matter.clear()
        seedStartingMatter()
        physicsFrameNumber = 0L
        impactMoments.clear()
        clearReplay()
    }

    /**
     * Single-authority particle simulation.
     *
     * CelestialBody values are derived centre-of-mass summaries, NOT forces.
     * No old collision fragments, arbitrary kicks, timers spawning fresh
     * objects, or lifetime-based destruction run during a physics frame.
     */
    @Synchronized
    fun step(dt: Float) {
        if (!dt.isFinite() || dt <= 0f || timeScale == 0f) return
        val startedAt = System.nanoTime()
        extremes.advance(dt.coerceAtMost(.1f),automaticEvents && rareCatastrophes,catastropheRate,
            episode.kind==CosmicEventKind.QUIET && !mathMoment.active)?.let {
            trigger(it,CosmicEventIntensity.CATASTROPHIC)
        }
        // Fixed simulation quantum: 2× really advances twice as far at 30/60Hz.
        // Bound catch-up work so a delayed frame can never block input indefinitely.
        accumulator += dt.coerceAtMost(.1f)*timeScale
        val quantum = 1.0/60.0
        var count = 0
        while (accumulator+1e-9 >= quantum && count < 12) {
            advance(quantum.toFloat())
            accumulator -= quantum
            count++
        }
        if (count > 0) publishGrains()
        physicsCostMs = ((System.nanoTime()-startedAt)/1_000_000.0).toFloat()
    }

    private fun advance(h: Float) {
        elapsed += h
        inspectAge += h
        if (mathPinned) {
            pinnedFor += h
            if (pinnedFor > 14f) mathPinned = false
        }
        if (inspectAge > 4.5f) selectedBody = null
        depthShift *= (1f - .5f * h)
        ripples = ripples.map { it.copy(age = it.age + h) }
            .filter { it.age < 1.7f }
        remnants = remnants.map { it.copy(age = it.age + h) }
            .filter { it.age < it.lifetime }

        if (mathMoment.active) {
            if (!mathPinned) {
                val age = mathMoment.age + h
                mathMoment = if (age > mathMoment.duration) MathMoment()
                else mathMoment.copy(age = age)
            }
        } else if (ambientMath && episode.kind == CosmicEventKind.QUIET &&
            elapsed >= nextMathAt) {
            summonMath()
        }

        val active = episode
        if (active.kind != CosmicEventKind.QUIET) {
            if (active.age + h >= active.duration) {
                if (active.kind in listOf(CosmicEventKind.NOVA,
                        CosmicEventKind.SINGULARITY, CosmicEventKind.PULSAR,
                        CosmicEventKind.WORMHOLE)) {
                    remnants = (remnants + CosmicRemnant(active.kind,
                        active.x, active.y)).takeLast(4)
                }
                episode = CosmicEpisode()
                // Next start was scheduled when this episode began.
            } else episode = active.copy(age = active.age + h)
        } else if (automaticEvents && !mathMoment.active && elapsed >= eventAt) {
            trigger(chooseEvent(), chooseIntensity())
        }

        // New matter enters only through a visibly incoming, independent
        // spherical cluster. This is deliberately infrequent and separate
        // from collisions, which conserve the total number of grains.
        if (automaticEvents && (preset == CosmicPreset.DEEP_SPACE ||
            preset == CosmicPreset.PROTOPLANETARY) &&
            elapsed >= spawnAt) {
            spawnAt = elapsed + 92f + random.nextFloat() * 64f
            if (bodies.size < maxBodies &&
                sand.grains.size + 105 <= matterMode.maxParticles) {
                spawnFromCorner(physicalCluster = true)
            }
        }
        val prior = bodies
        sand.step(h,gravity,episode,wallsEnabled,viewport.screenBounds(),
            if(clockWallsEnabled)clockObstacle else null)
        advanceSystems(h)
        physicsFrameNumber++
        // Grain positions are published EVERY physics frame, but 4500-grain
        // clouds do not need to rebuild the body's Compose State, object
        // ownership and hierarchy at 30Hz. This throttles expensive
        // recompositions without slowing down particle integration.
        val updateSceneMetadata = sand.grains.size < 1600 ||
            physicsFrameNumber % 4L == 0L
        if (updateSceneMetadata) {
            previousBodies = prior
            var current = sand.summarizeBodies(bodies)
            sand.relabelCaptured(current)
            val newborns = sand.discoverClumps(current, maxBodies, ::newBodyId)
            current = sand.mergeCaptureLabels(current + newborns)
            bodies = sand.summarizeBodies(current)
        }
        replayTimer += h
        val heavy = sand.grains.size >= 1800
        val snapshotInterval = if (heavy) 1f else .25f
        val historyCapacity = if (heavy) 12 else 32
        if (replayTimer >= snapshotInterval) {
            replayBuffer.addLast(bodies)
            particleReplay.addLast(sand.snapshot())
            replayMeta.addLast(ReplayMeta(elapsed,episode,idSequence,eventAt,spawnAt,ecosystem.snapshot(),quantum.snapshot(),
                radiationDose.toMap(),doseSerial,exposedBodies.toSet(),meteorWaves,nextMeteorWave))
            if (replayBuffer.size > historyCapacity) {
                replayBuffer.removeFirst(); replayMeta.removeFirst()
            }
            if (particleReplay.size > historyCapacity) particleReplay.removeFirst()
            replayTimer = 0f
        }
        observeWorld(h)
    }
}

internal data class OrbitPoint(val x: Float, val y: Float)
internal data class UniverseTelemetry(val particles: Int = 0, val mass: Double = 0.0,
    val kinetic: Double = 0.0, val heat: Double = 0.0, val energy: Double = 0.0,
    val reactions: Int = 0, val phases: Int = 0, val loose: Int = 0,
    val radiated: Double = 0.0,val externalRadiation:Double=0.0)

internal data class ShipRenderPair(val previous:List<Starship> = emptyList(),
    val current:List<Starship> = emptyList(),val committedAtNanos:Long=0L,
    val intervalNanos:Long=50_000_000L)
