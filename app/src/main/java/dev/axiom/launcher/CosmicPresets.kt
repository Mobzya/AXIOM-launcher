package dev.axiom.launcher

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A preset is INITIAL CONDITIONS, never a prefab body or a permanent orbit.
 * Every named object is converted to real, independently integrated grains.
 * Coordinates and apparent radii are artistic (2D), not a solar-system scale.
 */
internal enum class CosmicPreset(val title: String, val details: String) {
    DEEP_SPACE("Открытый космос", "Свободные скопления, столкновения и события"),
    SOLAR_SYSTEM("Солнечная система", "Центральная звезда и пять орбитальных планет"),
    SATURN_RINGS("Кольца Сатурна", "Газовый гигант, ледяные кольца и спутники"),
    BINARY_STARS("Двойная звезда", "Два светила на общей орбите и планеты"),
    PROTOPLANETARY("Рождение планет", "Звезда и диск пыли, где формируются сгустки"),
    TITAN_SYSTEM("Титаны и карлики",
        "Гигантское светило, крошечные луны и тяжёлый газовый гигант"),
    RESONANCE("Музыка орбит", "Три мира с периодами 1 : 2 : 4; следы раскрывают резонанс"),
    COMET_PASSAGE("Возвращение кометы", "Ледяное тело на вытянутой орбите и внутренние планеты"),
    TROJAN_GARDEN("Троянский сад", "Гигант и малые тела около точек Лагранжа L₄ и L₅"),
    ROGUE_ENCOUNTER("Звезда-странник", "Пролёт второй звезды возмущает спокойную систему"),
    COLLISION_LAB("Косой удар", "Ледяной и каменный миры: обмен импульсом и ударный нагрев"),
    COLD_NURSERY("Холодная колыбель", "Малое светило, холодные планеты и диск ледяной пыли")
}

internal fun presetBodies(preset: CosmicPreset, gravity: Float = 1f): List<CelestialBody> {
    fun body(
        id: Int, x: Float, y: Float, mass: Float,
        material: MatterMaterial, classification: StellarClass =
            StellarClass.PLANET, vx: Float = 0f, vy: Float = 0f,
        temperature: Float = 280f
    ) = CelestialBody(
        id = id, kind = when (classification) {
            StellarClass.STAR -> 12
            StellarClass.QUASAR -> 99
            StellarClass.CLOUD -> 74
            StellarClass.PLANET -> 2
        }, mass = mass, depth = 1f,
        x = x, y = y, vx = vx, vy = vy,
        lifetime = 3600f, material = material,
        temperature = temperature, stellarClass = classification
    )

    // Reduced 2D model: speed scales as sqrt(G * sourceMass / distance).
    // Grain-grain forces then decide all later orbits and captures.
    fun satellites(
        starMass: Float, cx: Float, cy: Float,
        radii: List<Float>, masses: List<Float>,
        composition: List<MatterMaterial>, firstId: Int
    ): List<CelestialBody> = radii.mapIndexed { index, radius ->
        val angle = listOf(.16, 1.35, 2.56, 3.85, 5.19)[index % 5]
        val x = cx + radius * cos(angle).toFloat()
        val y = cy + radius * sin(angle).toFloat()
        // Correct the circular speed for the solver's near-field gravity.
        val orbital = OrbitMechanics.circularSpeed(starMass + masses[index], radius)
        body(firstId + index, x, y, masses[index],
            composition[index],
            vx = -orbital * sin(angle).toFloat(),
            vy = orbital * cos(angle).toFloat(),
            temperature = 150f + 110f * index)
    }

    return when (preset) {
        CosmicPreset.TITAN_SYSTEM -> {
            val titan = body(1, .50f, .49f, 32f,
                MatterMaterial.HYDROGEN, StellarClass.STAR,
                temperature = 7400f)
            listOf(titan) + satellites(32f, .50f, .49f,
                listOf(.105f, .160f, .225f, .31f, .405f),
                listOf(.035f, .085f, .32f, .80f, 3.2f),
                listOf(MatterMaterial.NICKEL, MatterMaterial.ICE,
                    MatterMaterial.BASALT, MatterMaterial.CRYSTAL,
                    MatterMaterial.AMMONIA), 2)
        }
        CosmicPreset.SOLAR_SYSTEM -> {
            val star = body(1, .50f, .50f, 12f, MatterMaterial.HYDROGEN,
                StellarClass.STAR, temperature = 5800f)
            listOf(star) + satellites(12f, .50f, .50f,
                listOf(.105f, .160f, .227f, .305f, .385f),
                listOf(.28f, .46f, .62f, .85f, 1.4f),
                listOf(MatterMaterial.IRON, MatterMaterial.SILICATE,
                    MatterMaterial.ROCK, MatterMaterial.ICE,
                    MatterMaterial.METHANE), 2)
        }
        CosmicPreset.SATURN_RINGS -> listOf(
            body(1, .50f, .49f, 8f, MatterMaterial.METHANE,
                temperature = 170f),
            body(2, .77f, .49f, .48f, MatterMaterial.ICE,
                vx = 0f, vy = .041f, temperature = 120f),
            body(3, .23f, .49f, .28f, MatterMaterial.ROCK,
                vx = 0f, vy = -.044f, temperature = 210f)
        )
        CosmicPreset.BINARY_STARS -> listOf(
            body(1, .39f, .49f, 5f, MatterMaterial.HYDROGEN,
                StellarClass.STAR, vy = -.024f, temperature = 5600f),
            body(2, .61f, .49f, 4.5f, MatterMaterial.HYDROGEN,
                StellarClass.STAR, vy = .026f, temperature = 4300f),
            body(3, .50f, .81f, .47f, MatterMaterial.SILICATE,
                vx = -.037f, temperature = 360f),
            body(4, .50f, .18f, .33f, MatterMaterial.ICE,
                vx = .038f, temperature = 160f)
        )
        CosmicPreset.PROTOPLANETARY -> listOf(
            body(1, .50f, .50f, 10f, MatterMaterial.HYDROGEN,
                StellarClass.STAR, temperature = 5400f),
            body(2, .68f, .50f, .38f, MatterMaterial.IRON,
                vy = .048f, temperature = 950f),
            body(3, .35f, .57f, .25f, MatterMaterial.ICE,
                vx = -.020f, vy = -.036f, temperature = 160f)
        )
        CosmicPreset.RESONANCE -> {
            val mass = 9f
            val radii = listOf(.115f, .115f*1.587401f, .115f*2.519842f)
            listOf(body(1,.5f,.5f,mass,MatterMaterial.HYDROGEN,
                StellarClass.STAR,temperature=4600f)) +
                satellites(mass,.5f,.5f,radii,listOf(.025f,.035f,.045f),
                    listOf(MatterMaterial.IRON,MatterMaterial.ROCK,MatterMaterial.ICE),2)
        }
        CosmicPreset.COMET_PASSAGE -> {
            val star = body(1,.5f,.5f,10f,MatterMaterial.HYDROGEN,
                StellarClass.STAR,temperature=5700f)
            // Vis-viva at apocenter: r=a(1+e), e=.68.
            val speed = sqrt(OrbitMechanics.G*10f*(2.0/.36-1.0/(.36/1.68))).toFloat()
            listOf(star, body(2,.86f,.5f,.045f,MatterMaterial.ICE,
                vy=speed,temperature=110f)) +
                satellites(10f,.5f,.5f,listOf(.13f,.22f),listOf(.10f,.15f),
                    listOf(MatterMaterial.SILICATE,MatterMaterial.METHANE),3)
        }
        CosmicPreset.TROJAN_GARDEN -> {
            val radius = .25f
            val speed = OrbitMechanics.circularSpeed(12.25f,radius)
            listOf(body(1,.5f,.5f,12f,MatterMaterial.HYDROGEN,
                StellarClass.STAR,temperature=5400f),
                body(2,.75f,.5f,.25f,MatterMaterial.METHANE,vy=speed,temperature=170f)) +
                listOf(-1,1).flatMap { sign -> (0..2).map { index ->
                    val angle = sign*(PI/3 + (index-1)*.045)
                    val r = radius+(index-1)*.004f
                    body(3+(if(sign<0) 0 else 3)+index,
                        .5f+r*cos(angle).toFloat(),.5f+r*sin(angle).toFloat(),
                        .004f,MatterMaterial.ROCK,
                        vx=-speed*sin(angle).toFloat(),vy=speed*cos(angle).toFloat(),
                        temperature=180f)
                } }
        }
        CosmicPreset.ROGUE_ENCOUNTER -> {
            listOf(body(1,.43f,.5f,8f,MatterMaterial.HYDROGEN,
                StellarClass.STAR,temperature=5200f),
                body(2,.87f,.16f,3f,MatterMaterial.HYDROGEN,
                    StellarClass.STAR,vx=-.045f,vy=.024f,temperature=3600f)) +
                satellites(8f,.43f,.5f,listOf(.12f,.23f),listOf(.15f,.20f),
                    listOf(MatterMaterial.ROCK,MatterMaterial.ICE),3)
        }
        CosmicPreset.COLLISION_LAB -> listOf(
            body(1,.32f,.482f,1.3f,MatterMaterial.ROCK,vx=.036f,temperature=310f),
            body(2,.68f,.518f,.85f,MatterMaterial.ICE,vx=-.0550588f,temperature=150f))
        CosmicPreset.COLD_NURSERY -> listOf(
            body(1,.5f,.5f,5f,MatterMaterial.HYDROGEN,
                StellarClass.STAR,temperature=3100f)) +
                satellites(5f,.5f,.5f,listOf(.14f,.25f),listOf(.08f,.13f),
                    listOf(MatterMaterial.ICE,MatterMaterial.AMMONIA),2)
        CosmicPreset.DEEP_SPACE -> emptyList()
    }.map { body ->
        val factor = if (preset == CosmicPreset.COLLISION_LAB) 1f else sqrt(gravity.coerceAtLeast(0f))
        body.copy(vx=body.vx*factor,vy=body.vy*factor)
    }
}

/** Per-body initial discretization, limited by MatterMode capacity. */
internal fun presetGrainCount(preset: CosmicPreset, body: CelestialBody,
    mode: MatterMode): Int {
    val detail = mode == MatterMode.DETAILED
    return when (preset) {
        CosmicPreset.TITAN_SYSTEM ->
            if (body.stellarClass == StellarClass.STAR)
                if (detail) 850 else 270
            else when {
                body.mass < .10f -> if (detail) 38 else 18
                body.mass < .35f -> if (detail) 96 else 40
                body.mass < 1f -> if (detail) 160 else 76
                else -> if (detail) 340 else 140
            }
        CosmicPreset.DEEP_SPACE -> if (detail) 250 else 76
        CosmicPreset.RESONANCE, CosmicPreset.COMET_PASSAGE,
        CosmicPreset.TROJAN_GARDEN, CosmicPreset.ROGUE_ENCOUNTER,
        CosmicPreset.COLLISION_LAB, CosmicPreset.COLD_NURSERY -> when {
            body.mass < .01f -> if (detail) 12 else 6
            body.mass < .1f -> if (detail) 42 else 20
            body.stellarClass == StellarClass.STAR -> if (detail) 440 else 190
            else -> if (detail) 210 else 70
        }
        CosmicPreset.SOLAR_SYSTEM ->
            if (body.stellarClass == StellarClass.STAR)
                if (detail) 460 else 220
            else if (detail) 240 else 95
        CosmicPreset.SATURN_RINGS ->
            if (body.id == 1) if (detail) 650 else 300
            else if (detail) 150 else 65
        CosmicPreset.BINARY_STARS ->
            if (body.stellarClass == StellarClass.STAR)
                if (detail) 380 else 220
            else if (detail) 190 else 75
        CosmicPreset.PROTOPLANETARY ->
            if (body.id == 1) if (detail) 430 else 210
            else if (detail) 160 else 60
    }
}

/** Actual ice/rock grain rings, with near-circular Keplerian velocities.
 * Nothing is constrained to its ring or body after spawning. */
internal fun AstronomicalUniverse.seedOrbitalDisk(
    cx: Float, cy: Float, centralMass: Float, count: Int,
    innerRadius: Float, outerRadius: Float, owner: Int = -1,
    rocky: Boolean = false, gravity: Float = 1f
) {
    if (count <= 0 || outerRadius <= innerRadius) return
    val n = minOf(count, mode.maxParticles - grains.size)
    val golden = PI * (3.0 - sqrt(5.0))
    val nBefore = grains.size
    for (i in 0 until n) {
        val f = (i + .5) / n.toDouble()
        // Uniform surface density across an annulus, with discrete gaps.
        val r = sqrt(innerRadius * innerRadius * (1 - f) +
            outerRadius * outerRadius * f)
        val a = golden * i
        val mass = if (rocky) .0018f else .0009f
        val speed = OrbitMechanics.circularSpeed(centralMass,r.toFloat(),gravity).toDouble()
        val material = if (rocky) when (i % 7) {
            0 -> MatterMaterial.IRON
            1, 2 -> MatterMaterial.ROCK
            else -> MatterMaterial.SILICATE
        } else when (i % 9) {
            0 -> MatterMaterial.ROCK
            1 -> MatterMaterial.CARBON
            else -> MatterMaterial.ICE
        }
        grains.add(SandGrain(
            // Assigned unique IDs after any previously seeded planets.
            1000000 + nBefore + i,
            cx + (r * cos(a)).toFloat(),
            cy + (r * sin(a)).toFloat(),
            (-speed * sin(a)).toFloat(),
            (speed * cos(a)).toFloat(),
            mass, mass * .000001 * (if (rocky) 430.0 else 125.0),
            material, owner
        ))
    }
}
