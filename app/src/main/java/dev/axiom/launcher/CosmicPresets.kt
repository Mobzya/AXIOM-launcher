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
internal enum class SkyProfile { STARS, DUST, NEBULA, GALAXY, ANOMALY }
internal enum class CosmicPreset(
    val title: String, val details: String, val sky: SkyProfile = SkyProfile.STARS
) {
    DEEP_SPACE("Открытый космос", "Свободные скопления, столкновения и события"),
    SOLAR_SYSTEM("Солнечная система", "Центральная звезда и пять орбитальных планет"),
    SATURN_RINGS("Кольца Сатурна", "Газовый гигант, ледяные кольца и спутники"),
    BINARY_STARS("Двойная звезда", "Два светила на общей орбите и планеты"),
    PROTOPLANETARY("Рождение планет", "Звезда и диск пыли, где формируются сгустки", SkyProfile.DUST),
    TITAN_SYSTEM("Титаны и карлики",
        "Гигантское светило, крошечные луны и тяжёлый газовый гигант"),
    IMPACT_LAB("Встречное столкновение", "Два разных мира сходятся по касательной", SkyProfile.DUST),
    NEBULA_NURSERY("Туманность Ориона", "Газовые облака окружают молодые звёзды", SkyProfile.NEBULA),
    TWIN_GALAXIES("Сближение галактик", "Два вращающихся диска пыли в близком пролёте", SkyProfile.GALAXY),
    QUASAR_RIFT("Разлом квазара", "Компактное ядро и холодные спутники", SkyProfile.ANOMALY),
    OCEAN_MOONS("Океанические луны", "Газовый гигант, океан и ледяные спутники"),
    COMET_PASSAGE("Кометный коридор", "Ледяные ядра пересекают орбиты внутренних планет", SkyProfile.DUST),
    WHITE_DWARF("Пепел звезды", "Горячий карлик, обломки и кристаллические луны", SkyProfile.NEBULA)
}

internal fun presetBodies(preset: CosmicPreset): List<CelestialBody> {
    fun body(
        id: Int, x: Float, y: Float, mass: Float,
        material: MatterMaterial, classification: StellarClass =
            StellarClass.PLANET, vx: Float = 0f, vy: Float = 0f,
        temperature: Float = 280f, radiusScale: Float = 1f, isComet: Boolean = false
    ) = CelestialBody(
        id = id, kind = when (classification) {
            StellarClass.STAR -> 12
            StellarClass.QUASAR -> 99
            StellarClass.CLOUD -> 74
            StellarClass.PLANET -> 2
        }, mass = mass, depth = 1f,
        x = x, y = y, vx = vx, vy = vy,
        lifetime = 3600f, material = material,
        temperature = temperature, stellarClass = classification, sizeFactor = radiusScale, isComet = isComet
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
        val orbital = circularOrbitSpeed(starMass + masses[index], radius)
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
        CosmicPreset.IMPACT_LAB -> listOf(
            body(1, .27f, .48f, 3.8f, MatterMaterial.ICE,
                vx = .115f, temperature = 130f),
            body(2, .73f, .52f, 3.0f, MatterMaterial.MAGMA,
                vx = -.145f, temperature = 2000f),
            body(3, .60f, .73f, .33f, MatterMaterial.IRON,
                vx = -.04f, vy = -.012f)
        )
        CosmicPreset.NEBULA_NURSERY -> listOf(
            body(1, .42f, .46f, 4.6f, MatterMaterial.HYDROGEN,
                StellarClass.STAR, vy = -.018f, temperature = 6500f),
            body(2, .59f, .57f, 3.3f, MatterMaterial.HYDROGEN,
                StellarClass.STAR, vy = .025f, temperature = 4800f),
            body(3, .24f, .62f, 2.9f, MatterMaterial.AMMONIA,
                StellarClass.CLOUD, vx = .029f, temperature = 130f, radiusScale = 1.55f),
            body(4, .75f, .34f, 3.8f, MatterMaterial.METHANE,
                StellarClass.CLOUD, vx = -.022f, temperature = 165f, radiusScale = 1.65f)
        )
        CosmicPreset.TWIN_GALAXIES -> listOf(
            body(1, .29f, .40f, 9f, MatterMaterial.PLASMA,
                StellarClass.QUASAR, vx = .037f, vy = .008f, temperature = 6300f),
            body(2, .71f, .61f, 8f, MatterMaterial.PLASMA,
                StellarClass.QUASAR, vx = -.0416f, vy = -.009f, temperature = 5500f)
        )
        CosmicPreset.QUASAR_RIFT -> listOf(
            body(1, .50f, .49f, 13f, MatterMaterial.PLASMA,
                StellarClass.QUASAR, temperature = 7200f, radiusScale = .63f)
        ) + satellites(13f, .50f, .49f,
            listOf(.092f, .14f, .24f, .34f), listOf(.5f, .3f, .65f, .7f),
            listOf(MatterMaterial.CRYSTAL, MatterMaterial.ICE, MatterMaterial.SULFUR,
                MatterMaterial.CARBON), 2)
        CosmicPreset.OCEAN_MOONS -> listOf(
            body(1, .50f, .49f, 11f, MatterMaterial.AMMONIA, temperature = 130f)
        ) + satellites(11f, .50f, .49f,
            listOf(.12f, .18f, .25f, .34f), listOf(.52f, .21f, .66f, .16f),
            listOf(MatterMaterial.WATER, MatterMaterial.ICE, MatterMaterial.SULFUR,
                MatterMaterial.CRYSTAL), 2)
        CosmicPreset.COMET_PASSAGE -> listOf(
            body(1, .49f, .48f, 8f, MatterMaterial.HYDROGEN,
                StellarClass.STAR, temperature = 5100f),
            body(2, .15f, .63f, .19f, MatterMaterial.ICE,
                vx = .135f, vy = -.029f, temperature = 90f, isComet = true),
            body(3, .23f, .30f, .28f, MatterMaterial.ICE,
                vx = .106f, vy = .027f, temperature = 105f, isComet = true),
            body(4, .71f, .31f, .45f, MatterMaterial.CARBON, vy = .036f)
        )
        CosmicPreset.WHITE_DWARF -> listOf(
            body(1, .50f, .53f, 9f, MatterMaterial.HYDROGEN,
                StellarClass.STAR, temperature = 8500f, radiusScale = .57f)
        ) + satellites(9f, .50f, .53f,
            listOf(.105f, .21f, .30f), listOf(.3f, .45f, .4f),
            listOf(MatterMaterial.IRON, MatterMaterial.CRYSTAL, MatterMaterial.BASALT), 2)
        CosmicPreset.DEEP_SPACE -> emptyList()
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
        CosmicPreset.TWIN_GALAXIES -> if (detail) 410 else 125
        CosmicPreset.IMPACT_LAB -> if (body.id < 3)
            if (detail) 650 else 260
            else if (detail) 130 else 60
        CosmicPreset.NEBULA_NURSERY -> if (detail) 390 else 170
        CosmicPreset.QUASAR_RIFT, CosmicPreset.OCEAN_MOONS,
        CosmicPreset.WHITE_DWARF, CosmicPreset.COMET_PASSAGE ->
            if (body.id == 1) if (detail) 510 else 240
            else if (detail) 160 else 85
    }
}

/** Actual ice/rock grain rings, with near-circular Keplerian velocities.
 * Nothing is constrained to its ring or body after spawning. */
internal fun AstronomicalUniverse.seedOrbitalDisk(
    cx: Float, cy: Float, centralMass: Float, count: Int,
    innerRadius: Float, outerRadius: Float, owner: Int = -1,
    rocky: Boolean = false,
    vx: Float = 0f, vy: Float = 0f, spin: Int = 1, spiral: Boolean = false
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
        val a = if (spiral) spin * (3.8 * f + (i % 2) * PI +
            .20 * sin(golden * i)) else golden * i
        val mass = if (rocky) .0018f else .0009f
        val speed = circularOrbitSpeed(centralMass, r.toFloat())
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
            vx - (spin * speed * sin(a)).toFloat(),
            vy + (spin * speed * cos(a)).toFloat(),
            mass, mass * .000001 * (if (rocky) 430.0 else 125.0),
            material, owner
        ))
    }
}
