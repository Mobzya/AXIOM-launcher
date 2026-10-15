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
    PROTOPLANETARY("Рождение планет", "Звезда и диск пыли, где формируются сгустки")
}

internal fun presetBodies(preset: CosmicPreset): List<CelestialBody> {
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
        val boost = 1f + 1.55f / (1f + radius*radius / (.05f*.05f))
        val orbital = sqrt(.000052f * starMass * boost / radius) * .99f
        body(firstId + index, x, y, masses[index],
            composition[index],
            vx = -orbital * sin(angle).toFloat(),
            vy = orbital * cos(angle).toFloat(),
            temperature = 150f + 110f * index)
    }

    return when (preset) {
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
        CosmicPreset.DEEP_SPACE -> emptyList()
    }
}

/** Per-body initial discretization, limited by MatterMode capacity. */
internal fun presetGrainCount(preset: CosmicPreset, body: CelestialBody,
    mode: MatterMode): Int {
    val detail = mode == MatterMode.DETAILED
    return when (preset) {
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
    }
}

/** Actual ice/rock grain rings, with near-circular Keplerian velocities.
 * Nothing is constrained to its ring or body after spawning. */
internal fun GranularUniverse.seedOrbitalDisk(
    cx: Float, cy: Float, centralMass: Float, count: Int,
    innerRadius: Float, outerRadius: Float, owner: Int = -1,
    rocky: Boolean = false
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
        val near = 1.0 + 1.55 / (1.0 + r*r / (.05*.05))
        val speed = sqrt(.000052 * centralMass * near / r) * .99
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
