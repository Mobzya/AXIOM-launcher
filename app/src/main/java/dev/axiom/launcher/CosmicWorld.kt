package dev.axiom.launcher

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
    SUPERNOVA, TIDAL_DISRUPTION, MAGNETIC_RECONNECTION, COMET_FLYBY }

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
    val sourceId: Int? = null
) {
    val progress: Float get() = (age / duration).coerceIn(0f, 1f)
    val pressureRadius: Float
        get() = .026f + (intensity.radius - .026f) *
            sqrt((age / (duration * .75f)).coerceIn(0f, 1f))
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
    val isComet: Boolean = false
)

internal data class GrainRenderPair(
    val previous: FloatArray,
    val current: FloatArray,
    val committedAtNanos: Long,
    val intervalNanos: Long = 0L
) {
    fun blendAt(frameNanos: Long): Float =
        if (intervalNanos <= 0L) 1f
        else ((frameNanos - committedAtNanos).toFloat() / intervalNanos).coerceIn(0f, 1f)
}

internal data class ObservatoryStats(
    val loose: Int = 0, val bound: Int = 0, val mass: Double = 0.0, val reactions: Int = 0
)

internal data class ImpactFlash(val x: Float, val y: Float, val energy: Float, val age: Float = 0f)

internal data class SpaceRipple(val x: Float, val y: Float, val age: Float = 0f)

internal data class CosmicRemnant(
    val kind: CosmicEventKind, val x: Float, val y: Float,
    val age: Float = 0f, val lifetime: Float = 38f
)

internal class CosmicWorld(
    seed: Int = 42,
    initialMatterMode: MatterMode = MatterMode.BALANCED,
    initialPreset: CosmicPreset = CosmicPreset.DEEP_SPACE,
    initialFrequency: EventFrequency = EventFrequency.NORMAL
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
    val particleCount: Int get() = grainFrame.size / 4

    private fun publishGrains(animate: Boolean = false) {
        val next = sand.renderFrame()
        val old = grainFrames
        val now = System.nanoTime()
        val gap = now - old.committedAtNanos
        val smooth = animate && old.current.size == next.size && gap <= 100_000_000L
        grainFrames = GrainRenderPair(
            if (smooth) old.current else next, next, now,
            if (smooth) gap.coerceIn(8_000_000L, 70_000_000L) else 0L
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
        if (preset != CosmicPreset.DEEP_SPACE || sand.grains.size > value.maxParticles) reset()
    }
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
    var elapsed by mutableStateOf(0f)
        private set
    var gravity by mutableStateOf(1f)
        private set
    var episode by mutableStateOf(CosmicEpisode())
        private set
    var depthShift by mutableStateOf(0f)
        private set
    private var idSequence = 50
    private var eventSerial = 0
    // Rare external arrivals: a fresh spherical GROUP of grains, not sprites.
    private var spawnAt = 46f
    var eventFrequency by mutableStateOf(initialFrequency)
        private set
    private var eventAt = initialFrequency.nextDelay(0f)
    var impactFlashes by mutableStateOf<List<ImpactFlash>>(emptyList())
        private set
    private var lastImpactAt = -1f

    private fun scheduleEvent() {
        eventAt = elapsed + eventFrequency.nextDelay(random.nextFloat())
    }

    @Synchronized
    fun updateEventFrequency(value: EventFrequency) {
        if (eventFrequency == value) return
        eventFrequency = value
        scheduleEvent()
    }
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
    var timeScale by mutableStateOf(
        if (initialPreset == CosmicPreset.DEEP_SPACE) 1f else .5f)
        private set
    var brushPoint by mutableStateOf<SpaceRipple?>(null)
        private set
    var brushMass by mutableStateOf(0f)
        private set
    private var brushStartX = 0f
    private var brushStartY = 0f
    private var brushPrevX = 0f
    private var brushPrevY = 0f
    private var brushGestureDistance = 0f
    private var brushInitialBodyCount = 0
    private data class ReplayFrame(
        val time: Float, val bodies: List<CelestialBody>, val particles: AstronomicalUniverse.Snapshot
    )
    private val replayBuffer = ArrayDeque<ReplayFrame>(32)
    private var replayTimer = 0f
    private var physicsFrameNumber = 0L
    private var cascadeBudget = 0
    var maxBodies by mutableStateOf(23)
        private set
    var wallsEnabled by mutableStateOf(false)
        private set
    var dust by mutableStateOf<List<ImpactDust>>(emptyList())
        private set
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
                .063f, .165f)
            CosmicPreset.PROTOPLANETARY -> sand.seedOrbitalDisk(
                .50f, .50f, 10f,
                if (matterMode == MatterMode.DETAILED) 1800 else 475,
                .085f, .38f, rocky = true)
            CosmicPreset.TWIN_GALAXIES -> bodies.forEachIndexed { index, body ->
                sand.seedOrbitalDisk(body.x, body.y, body.mass,
                    if (matterMode == MatterMode.DETAILED) 1240 else 285,
                    .054f, .195f, rocky = index == 0, vx = body.vx, vy = body.vy,
                    spin = if (index == 0) 1 else -1, spiral = true)
            }
            CosmicPreset.WHITE_DWARF -> sand.seedOrbitalDisk(
                .50f, .53f, 9f, if (matterMode == MatterMode.DETAILED) 600 else 230,
                .05f, .082f, rocky = true)
            else -> Unit
        }
        previousBodies = bodies
        publishGrains()
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
        if (bodies.size > maxBodies) {
            sand.releaseOwners(bodies.drop(maxBodies).map { it.id }.toSet())
            bodies = bodies.take(maxBodies)
            previousBodies = bodies
            clearReplay()
        }
    }
    @Synchronized
    fun updateWalls(enabled: Boolean) {wallsEnabled=enabled}

    var lastEncounter by mutableStateOf("none")
        private set

    private fun initialBodies(): List<CelestialBody> {
        if (preset != CosmicPreset.DEEP_SPACE) return presetBodies(preset)
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
        px: Float = .68f, py: Float = .38f): Boolean {
        val type = when (material) {
            MatterMaterial.HYDROGEN -> StellarClass.STAR
            MatterMaterial.PLASMA -> StellarClass.QUASAR
            MatterMaterial.AMMONIA, MatterMaterial.METHANE -> StellarClass.CLOUD
            else -> StellarClass.PLANET
        }
        val temperature = when (type) {
            StellarClass.STAR -> 5300f
            StellarClass.QUASAR -> 6400f
            StellarClass.CLOUD -> 175f
            StellarClass.PLANET -> if (material == MatterMaterial.MAGMA) 2100f else 290f
        }
        return createObject(material, type, mass, temperature, 1f, px, py)
    }

    @Synchronized
    fun createObject(objectType: ObservatoryObject, mass: Float): Boolean {
        val angle = idSequence * 2.3999632f
        return createObject(objectType.material, objectType.type, mass, objectType.temperature,
            objectType.sizeFactor, .5f + .27f * cos(angle), .5f + .27f * sin(angle),
            isComet = objectType == ObservatoryObject.COMET)
    }

    private fun createObject(
        material: MatterMaterial, classType: StellarClass, mass: Float, temperature: Float,
        sizeFactor: Float, px: Float, py: Float, isComet: Boolean = false
    ): Boolean {
        if (!mass.isFinite() || !px.isFinite() || !py.isFinite() ||
            bodies.size >= maxBodies || sand.grains.size >= matterMode.maxParticles) return false
        val m = mass.coerceIn(.04f, 24f)
        val core = bodies.maxByOrNull { it.mass }
        val x = px.coerceIn(.12f,.87f)
        val y = py.coerceIn(.12f,.87f)
        val dx = x - (core?.x ?: .5f)
        val dy = y - (core?.y ?: .5f)
        val r = max(hypot(dx,dy),.09f)
        // Tangential orbit relative to the actual dominant body.
        val speed = circularOrbitSpeed((core?.mass ?: 2f) + m, r, gravity)
            .coerceAtMost(.085f)
        val body = CelestialBody(
            idSequence++, if (classType == StellarClass.STAR) 12 else 2,
            m, 1f, x, y,
            (core?.vx ?: 0f) -dy/r*speed,
            (core?.vy ?: 0f) +dx/r*speed,
            lifetime = 3600f,
            sizeFactor = ((.65f + .20f*sqrt(m)) * sizeFactor).coerceIn(.45f, 2.25f),
            material = material,
            temperature = temperature,
            stellarClass = classType,
            isComet = isComet
        )
        val requested = when {
            m < .20f -> 15
            m < 1f -> 45
            m < 4f -> 86
            else -> 180
        }
        val n = min(requested, matterMode.maxParticles - sand.grains.size)
        if (n <= 0) return false
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
        val px = x.coerceIn(0f, 1f)
        val py = y.coerceIn(0f, 1f)
        sand.perturb(px, py, strength.coerceIn(.02f,.30f))
        ripples = (ripples + SpaceRipple(px, py)).takeLast(4)
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
    fun stats(): ObservatoryStats {
        val loose = sand.grains.count { it.owner < 0 }
        return ObservatoryStats(loose, sand.grains.size - loose, sand.totals().mass, sand.reactions)
    }

    @Synchronized
    fun updateGravity(value: Float) {
        if (value.isFinite()) gravity = value.coerceIn(.25f, 2.2f)
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
            scheduleEvent()
            return
        }
        eventSerial++
        tunneled.clear()
        eventHistory = (eventHistory +
            (SpaceEvent.titleOf(kind) + " · " + intensity.label)).takeLast(10)
        // Anchor active physics to REAL matter rather than an empty corner.
        // Solar phenomena prefer hot gas, tides prefer a compact massive core.
        val selectedSource = when (kind) {
            CosmicEventKind.SOLAR_WIND, CosmicEventKind.IONIZATION_FRONT,
            CosmicEventKind.MAGNETAR_FLARE, CosmicEventKind.PULSAR,
            CosmicEventKind.QUASAR_JET, CosmicEventKind.SOLAR_FLARE,
            CosmicEventKind.SUPERNOVA, CosmicEventKind.MAGNETIC_RECONNECTION,
            CosmicEventKind.COMET_FLYBY ->
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
                CosmicEventKind.SUPERNOVA -> 8f
                CosmicEventKind.TIDAL_DISRUPTION -> 11f
                CosmicEventKind.MAGNETIC_RECONNECTION -> 10f
                CosmicEventKind.COMET_FLYBY -> 14f
                else -> 1f
            },
            // Pulsars and quasars act on actual existing hot bodies, not
            // hidden attractors. All ejection impulses consume particle heat.
            x = selectedSource?.x ?: .5f,
            y = selectedSource?.y ?: .5f,
            serial = eventSerial,
            intensity = intensity,
            sourceId = selectedSource?.id
        )
        scheduleEvent()
        if (kind == CosmicEventKind.COMET_FLYBY) spawnComet(intensity, episode.x, episode.y)
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
                stellarClass = StellarClass.PLANET
            )
            bodies += body
            sand.seed(body, grainsPerMeteor)
        }
        publishGrains()
    }

    private fun spawnComet(intensity: CosmicEventIntensity, targetX: Float, targetY: Float) {
        val count = 24 + intensity.ordinal * 15
        if (bodies.size >= maxBodies || sand.grains.size + count > matterMode.maxParticles) return
        val fromLeft = targetX >= .5f
        val x = if (fromLeft) -.05f else 1.05f
        val y = (targetY + .19f).coerceIn(.15f, .87f)
        val dx = targetX - x
        val dy = targetY - y
        val speed = .13f + .028f * intensity.ordinal
        val distance = hypot(dx, dy)
        val comet = CelestialBody(idSequence++, 1, .12f + .14f * intensity.ordinal,
            1f, x, y, dx * speed / distance, dy * speed / distance,
            lifetime = 3600f, material = MatterMaterial.ICE, temperature = 90f, isComet = true)
        sand.seed(comet, count)
        bodies += comet
        publishGrains()
    }

    private fun chooseEvent(): CosmicEventKind =
        SpaceEvent.fromRoll(random.nextInt(SpaceEvent.totalWeight))

    /** One-click random cosmic event, or a user-requested power level. */
    @Synchronized
    fun triggerRandomEvent(intensity: CosmicEventIntensity? = null) {
        trigger(chooseEvent(), intensity ?: chooseIntensity())
    }

    private fun chooseIntensity(): CosmicEventIntensity = when (random.nextInt(100)) {
        in 0..42 -> CosmicEventIntensity.FLICKER
        in 43..82 -> CosmicEventIntensity.NORMAL
        in 83..96 -> CosmicEventIntensity.MAJOR
        else -> CosmicEventIntensity.CATASTROPHIC
    }

    @Synchronized
    fun summonMath(requested: MathKind? = null) {
        // An explicitly requested lesson takes priority over ongoing scenery.
        if (episode.kind != CosmicEventKind.QUIET) {
            episode = CosmicEpisode()
            scheduleEvent()
        }
        mathPinned = false
        pinnedFor = 0f
        nextMathAt = elapsed + 23f
        mathMoment = MathMoment(
            kind = requested ?: MathKind.entries[random.nextInt(MathKind.entries.size)],
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
        val px = x.coerceIn(0f, 1f)
        val py = y.coerceIn(0f, 1f)
        ripples = (ripples + SpaceRipple(px, py)).takeLast(4)
        val closest = bodies.minByOrNull { hypot(it.x - px, it.y - py) }
        selectedBody = closest?.takeIf {
            hypot(it.x - px, it.y - py) < .072f
        }
        inspectAge = 0f
        // An explicit finger impulse exchanges energy with the user, not with
        // a fabricated particle emitter. All masses and grain IDs remain.
        sand.perturb(px, py, .045f)
        // Body summaries are UI-only views, but must reflect the grain
        // velocities immediately after an intentional user impulse.
        bodies = sand.summarizeBodies(bodies)
        publishGrains()
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
        brushStartX = x.coerceIn(0f, 1f)
        brushStartY = y.coerceIn(0f, 1f)
        brushPrevX = brushStartX
        brushPrevY = brushStartY
        brushGestureDistance = 0f
        brushInitialBodyCount = bodies.size
        brushMass = 1.2f
        brushPoint = SpaceRipple(brushStartX, brushStartY)
    }

    @Synchronized
    fun moveBrush(x: Float, y: Float) {
        val px = x.coerceIn(0f, 1f)
        val py = y.coerceIn(0f, 1f)
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
    fun cancelBrush() {
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
        if (replayBuffer.isEmpty()) return false
        val target = elapsed - seconds.coerceIn(0, 8)
        val index = replayBuffer.indexOfLast { it.time <= target }.coerceAtLeast(0)
        val frame = replayBuffer[index]
        sand.restore(frame.particles)
        bodies = frame.bodies
        previousBodies = frame.bodies
        elapsed = frame.time
        episode = CosmicEpisode()
        impactFlashes = emptyList()
        ripples = emptyList()
        lastImpactAt = -1f
        selectedBody = null
        mathMoment = MathMoment()
        mathPinned = false
        nextMathAt = elapsed + 13f
        scheduleEvent()
        while (replayBuffer.lastIndex > index) replayBuffer.removeLast()
        replayTimer = 0f
        publishGrains()
        return true
    }

    @Synchronized
    fun clearReplay() {
        replayBuffer.clear()
        replayTimer = 0f
    }

    @Synchronized
    fun shiftPerspective(dx: Float) {
        depthShift = (depthShift + dx).coerceIn(-1f, 1f)
    }

    @Synchronized
    fun reset() {
        bodies = initialBodies()
        previousBodies = bodies
        ripples = emptyList()
        remnants = emptyList()
        mathPinned = false
        pinnedFor = 0f
        elapsed = 0f
        episode = CosmicEpisode()
        depthShift = 0f
        spawnAt = 46f
        eventAt = eventFrequency.nextDelay(0f)
        impactFlashes = emptyList()
        lastImpactAt = -1f
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
        if (timeScale == 0f || !dt.isFinite() || dt <= 0f) return
        val startedAt = System.nanoTime()
        val h = dt.coerceAtMost(1f / 30f) * timeScale
        elapsed += h
        inspectAge += h
        if (mathPinned) {
            pinnedFor += h
            if (pinnedFor > 14f) mathPinned = false
        }
        if (inspectAge > 4.5f) selectedBody = null
        depthShift *= (1f - .5f * h)
        if (ripples.isNotEmpty()) ripples = ripples.map { it.copy(age = it.age + h) }
            .filter { it.age < .8f }
        if (impactFlashes.isNotEmpty()) impactFlashes = impactFlashes
            .map { it.copy(age = it.age + h) }.filter { it.age < 1.25f }
        if (remnants.isNotEmpty()) remnants = remnants.map { it.copy(age = it.age + h) }
            .filter { it.age < it.lifetime }

        if (mathMoment.active) {
            if (!mathPinned) {
                val age = mathMoment.age + h
                mathMoment = if (age > mathMoment.duration) MathMoment()
                else mathMoment.copy(age = age)
            }
        } else if (episode.kind == CosmicEventKind.QUIET &&
            elapsed >= nextMathAt && elapsed < eventAt) {
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
            } else {
                val source = if (active.kind == CosmicEventKind.TIDAL_DISRUPTION)
                    bodies.firstOrNull { it.id == active.sourceId } else null
                episode = active.copy(age = active.age + h,
                    x = source?.x ?: active.x, y = source?.y ?: active.y)
            }
        } else if (!mathPinned && elapsed >= eventAt) {
            trigger(chooseEvent(), chooseIntensity())
        }

        // New matter enters only through a visibly incoming, independent
        // spherical cluster. This is deliberately infrequent and separate
        // from collisions, which conserve the total number of grains.
        if ((preset == CosmicPreset.DEEP_SPACE ||
            preset == CosmicPreset.PROTOPLANETARY) &&
            elapsed >= spawnAt) {
            spawnAt = elapsed + 92f + random.nextFloat() * 64f
            if (bodies.size < maxBodies &&
                sand.grains.size + 105 <= matterMode.maxParticles) {
                spawnFromCorner(physicalCluster = true)
            }
        }
        val prior = bodies
        sand.step(h, gravity, episode, wallsEnabled)
        val impact = sand.lastImpact
        if (impact != null && elapsed - lastImpactAt >= .22f) {
            impactFlashes = (impactFlashes + impact).takeLast(5)
            lastImpactAt = elapsed
        }

        physicsFrameNumber++
        val heavy = sand.grains.size >= 1600
        val labelsChanged = sand.updateOwnership(prior, maxBodies, ::newBodyId)
        if (labelsChanged != null || physicsFrameNumber % (if (heavy) 4L else 2L) == 0L) {
            previousBodies = prior
            bodies = sand.summarizeBodies(labelsChanged ?: prior)
        }
        publishGrains(animate = true)

        replayTimer += h
        val snapshotInterval = if (heavy) 1f else .25f
        val historyCapacity = if (heavy) 12 else 32
        if (replayTimer >= snapshotInterval) {
            replayBuffer.addLast(ReplayFrame(elapsed, bodies, sand.snapshot()))
            while (replayBuffer.size > historyCapacity) replayBuffer.removeFirst()
            replayTimer -= snapshotInterval
        }
        if (physicsFrameNumber % 12 == 0L) {
            physicsCostMs = ((System.nanoTime() - startedAt) / 1_000_000.0).toFloat()
        }
    }
}
