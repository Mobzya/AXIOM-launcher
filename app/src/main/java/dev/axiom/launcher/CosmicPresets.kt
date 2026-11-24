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
    COLD_NURSERY("Холодная колыбель", "Малое светило, холодные планеты и диск ледяной пыли"),
    LIVING_SYSTEM("Обитаемый архипелаг", "Устойчивые миры, первые цивилизации и маршруты колонизации"),
    BLACK_HOLE_NURSERY("У края бездны", "Чёрная дыра, аккреционный диск и холодные внешние миры"),
    PULSAR_REEF("Пульсарный риф", "Нейтронная звезда и планеты под вращающимся светом полюсов"),
    WHITE_DWARF_RELIC("После звезды", "Белый карлик среди уцелевших лун и обломков"),
    ASTEROID_CORRIDOR("Рубеж колонии", "Обитаемый мир и приближающиеся астероиды: ресурсы и перехват"),
    QUANTUM_SHORES("Квантовые берега", "Спокойная система и двухуровневый квантовый эксперимент"),
    BLACK_HOLE_MERGER("Слияние бездн", "Две чёрные дыры, сближение горизонтов и световой фронт слияния"),
    GALACTIC_NUCLEUS("Сверхмассивное ядро", "Огромный горизонт, аккреционный диск и звёзды вокруг тяжёлого ядра"),
    HYPERGIANT_SYSTEM("Владения гипергиганта", "Гигантское светило и крошечные внешние миры: сравни размеры"),
    OCEAN_IMPACT("Удар по океану", "Океанический мир и ледяной спутник встречаются по касательной"),
    GALAXY_PASSAGE("Пролёт галактик", "Два ядра и встречное вращение реальных пылевых дисков"),
    NEBULA_CROSSING("За пеленой туманности", "Два газовых облака, молодая звезда и холодный спутник"),
    ROCHE_PASSAGE("Приливный разрыв", "Рыхлый ледяной мир проходит у чёрной дыры и теряет вещество"),
    DOUBLE_PLANET("Двойная планета", "Два мира обращаются вокруг общего центра масс"),
    COUNTER_ROTATING_RINGS("Встречные кольца", "Ледяной и каменный диски вращаются в разные стороны"),
    GIANT_GRAZE("Касание гигантов", "Два массивных газовых мира встречаются по касательной"),
    BLUE_NEBULA("Голубая туманность", "Горячее светило, две газовые дуги и молодые планеты"),
    FROZEN_OCEANS("Под ледяной корой", "Два подлёдных океана и металлический спутник у красного карлика")
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
        CosmicPreset.LIVING_SYSTEM, CosmicPreset.QUANTUM_SHORES -> {
            val star=body(1,.5f,.5f,9f,MatterMaterial.HYDROGEN,
                StellarClass.STAR,temperature=5000f)
            listOf(star)+satellites(9f,.5f,.5f,listOf(.12f,.26f,.44f),
                listOf(.020f,.028f,.035f),listOf(MatterMaterial.ROCK,MatterMaterial.WATER,MatterMaterial.ORGANICS),2)
                .mapIndexed {index,b ->
                    val radius=listOf(.12f,.26f,.44f)[index]
                    val temperature=278f+index*12f
                    b.copy(temperature=temperature,climateFactor=temperature/(288f*sqrt(.235f/radius)))
                }
        }
        CosmicPreset.BLACK_HOLE_NURSERY -> listOf(
            body(1,.5f,.5f,18f,MatterMaterial.IRON,StellarClass.QUASAR,temperature=3f)
                .copy(nature=CelestialNature.BLACK_HOLE))+
                satellites(18f,.5f,.5f,listOf(.23f,.36f),listOf(.2f,.5f),
                    listOf(MatterMaterial.IRON,MatterMaterial.ICE),2)
        CosmicPreset.BLACK_HOLE_MERGER -> listOf(
            body(1,.42f,.5f,16f,MatterMaterial.CARBON,StellarClass.QUASAR,vx=.018f,vy=.002f,temperature=3f)
                .copy(nature=CelestialNature.BLACK_HOLE),
            body(2,.58f,.5f,12f,MatterMaterial.CARBON,StellarClass.QUASAR,vx=-.024f,vy=-.002666667f,temperature=3f)
                .copy(nature=CelestialNature.BLACK_HOLE),
            body(3,.5f,.86f,.04f,MatterMaterial.IRON,vx=-.045f,temperature=180f))
        CosmicPreset.GALACTIC_NUCLEUS -> listOf(
            body(1,.5f,.5f,1800f,MatterMaterial.CARBON,StellarClass.QUASAR,temperature=3f)
                .copy(nature=CelestialNature.BLACK_HOLE),
            body(2,.79f,.5f,2f,MatterMaterial.HYDROGEN,StellarClass.STAR,
                vy=OrbitMechanics.circularSpeed(1802f,.29f),temperature=6000f),
            body(3,.12f,.5f,1f,MatterMaterial.HELIUM,StellarClass.STAR,
                vy=-OrbitMechanics.circularSpeed(1801f,.38f),temperature=9000f))
        CosmicPreset.HYPERGIANT_SYSTEM -> listOf(
            body(1,.5f,.5f,150f,MatterMaterial.HYDROGEN,StellarClass.STAR,temperature=14000f).copy(sizeFactor=2.5f))+
            satellites(150f,.5f,.5f,listOf(.28f,.43f),listOf(.08f,.18f),
                listOf(MatterMaterial.BASALT,MatterMaterial.ICE),2)
        CosmicPreset.PULSAR_REEF -> listOf(
            body(1,.5f,.5f,6f,MatterMaterial.NICKEL,StellarClass.STAR,temperature=18000f)
                .copy(nature=CelestialNature.NEUTRON_STAR,sizeFactor=.25f))+
                satellites(6f,.5f,.5f,listOf(.14f,.25f,.36f),listOf(.1f,.25f,.12f),
                    listOf(MatterMaterial.IRON,MatterMaterial.SILICATE,MatterMaterial.ICE),2)
        CosmicPreset.WHITE_DWARF_RELIC -> listOf(
            body(1,.5f,.5f,3f,MatterMaterial.HELIUM,StellarClass.STAR,temperature=12000f)
                .copy(nature=CelestialNature.WHITE_DWARF,sizeFactor=.5f))+
                satellites(3f,.5f,.5f,listOf(.14f,.25f),listOf(.08f,.18f),
                    listOf(MatterMaterial.BASALT,MatterMaterial.CRYSTAL),2)
        CosmicPreset.ASTEROID_CORRIDOR -> listOf(
            body(1,.5f,.50f,7f,MatterMaterial.HYDROGEN,StellarClass.STAR,temperature=4800f),
            body(2,.5f,.76f,.7f,MatterMaterial.ROCK,
                vx=-OrbitMechanics.circularSpeed(7.7f,.26f),temperature=294f),
            body(3,.24f,.76f,.04f,MatterMaterial.IRON,vx=.045f,vy=.006f,temperature=180f)
                .copy(kind=1,nature=CelestialNature.ASTEROID),
            body(4,.78f,.72f,.065f,MatterMaterial.ICE,vx=-.04f,vy=.01f,temperature=100f)
                .copy(kind=1,nature=CelestialNature.COMET),
            body(5,.73f,.36f,.5f,MatterMaterial.WATER,
                vx=.012f,vy=.034f,temperature=286f))
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
                vy=speed,temperature=110f).copy(nature=CelestialNature.COMET,kind=1)) +
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
        CosmicPreset.OCEAN_IMPACT -> listOf(
            body(1,.32f,.486f,1.35f,MatterMaterial.WATER,vx=.060f,temperature=313f),
            body(2,.68f,.514f,.90f,MatterMaterial.ICE,vx=-.090f,temperature=115f),
            body(3,.75f,.75f,.18f,MatterMaterial.SULFUR,vx=-.013f,vy=-.005f,temperature=660f)
        )
        CosmicPreset.GALAXY_PASSAGE -> listOf(
            body(1,.30f,.43f,7f,MatterMaterial.CARBON,StellarClass.QUASAR,
                vx=.029f,vy=.005f,temperature=3f).copy(nature=CelestialNature.BLACK_HOLE),
            body(2,.70f,.57f,6f,MatterMaterial.CARBON,StellarClass.QUASAR,
                vx=-.0338333f,vy=-.0058333f,temperature=3f).copy(nature=CelestialNature.BLACK_HOLE),
            body(3,.19f,.30f,.78f,MatterMaterial.HYDROGEN,StellarClass.STAR,
                vx=.047f,vy=-.027f,temperature=5300f),
            body(4,.81f,.70f,.69f,MatterMaterial.HYDROGEN,StellarClass.STAR,
                vx=-.052f,vy=.022f,temperature=6200f)
        )
        CosmicPreset.NEBULA_CROSSING -> listOf(
            body(1,.48f,.45f,4.8f,MatterMaterial.HYDROGEN,StellarClass.STAR,
                vx=.008f,temperature=7200f),
            body(2,.20f,.62f,2.1f,MatterMaterial.AMMONIA,StellarClass.CLOUD,
                vx=.029f,vy=-.010f,temperature=135f),
            body(3,.76f,.38f,2.8f,MatterMaterial.METHANE,StellarClass.CLOUD,
                vx=-.021f,vy=.016f,temperature=160f),
            body(4,.59f,.74f,.35f,MatterMaterial.ICE,vx=-.026f,temperature=115f)
        )
        CosmicPreset.ROCHE_PASSAGE -> listOf(
            body(1, .47f, .5f, 52f, MatterMaterial.CARBON, StellarClass.QUASAR, temperature = 3f)
                .copy(nature = CelestialNature.BLACK_HOLE),
            body(2, .68f, .56f, .65f, MatterMaterial.AMMONIA, vx = -.105f, vy = -.055f, temperature = 105f)
                .copy(sizeFactor = 2.8f),
            body(3, .28f, .26f, .14f, MatterMaterial.ICE, vx = .072f, vy = -.052f, temperature = 120f)
        )
        CosmicPreset.DOUBLE_PLANET -> {
            val speed = OrbitMechanics.circularSpeed(4.4f, .095f)
            listOf(
                body(1, .46f, .5f, 2.55f, MatterMaterial.SILICATE, vy = -speed * (1.85f / 4.4f), temperature = 320f),
                body(2, .555f, .5f, 1.85f, MatterMaterial.ICE, vy = speed * (2.55f / 4.4f), temperature = 235f)
                    .copy(subsurfaceOcean = true),
                body(3, .5f, .78f, .08f, MatterMaterial.NICKEL,
                    vx = -OrbitMechanics.circularSpeed(4.48f, .28f), temperature = 150f)
            )
        }
        CosmicPreset.COUNTER_ROTATING_RINGS -> listOf(
            body(1, .5f, .5f, 11f, MatterMaterial.METHANE, temperature = 170f).copy(sizeFactor = 1.28f),
            body(2, .77f, .52f, .18f, MatterMaterial.BASALT, vx = -.004f, vy = .046f, temperature = 260f)
        )
        CosmicPreset.GIANT_GRAZE -> listOf(
            body(1, .30f, .463f, 9f, MatterMaterial.METHANE, vx = .058f, temperature = 260f).copy(sizeFactor = 1.7f),
            body(2, .70f, .537f, 6f, MatterMaterial.AMMONIA, vx = -.087f, temperature = 140f).copy(sizeFactor = 1.65f),
            body(3, .30f, .77f, .16f, MatterMaterial.ICE, vx = -.022f, temperature = 120f)
        )
        CosmicPreset.BLUE_NEBULA -> listOf(
            body(1, .5f, .5f, 18f, MatterMaterial.HYDROGEN, StellarClass.STAR, temperature = 16500f),
            body(2, .26f, .66f, 2.3f, MatterMaterial.AMMONIA, StellarClass.CLOUD, vx = .036f, vy = .041f, temperature = 112f)
                .copy(sizeFactor = 2.4f),
            body(3, .73f, .29f, 1.9f, MatterMaterial.HYDROGEN, StellarClass.CLOUD, vx = -.034f, vy = -.040f, temperature = 290f)
                .copy(sizeFactor = 2.7f)
        ) + satellites(18f, .5f, .5f, listOf(.15f, .24f), listOf(.1f, .3f),
            listOf(MatterMaterial.IRON, MatterMaterial.SILICATE), 4)
        CosmicPreset.FROZEN_OCEANS -> listOf(
            body(1, .5f, .5f, 4.6f, MatterMaterial.HYDROGEN, StellarClass.STAR, temperature = 2950f)
        ) + satellites(4.6f, .5f, .5f, listOf(.14f, .26f, .41f), listOf(.44f, .7f, .16f),
            listOf(MatterMaterial.ICE, MatterMaterial.ICE, MatterMaterial.NICKEL), 2).map { candidate ->
                candidate.copy(subsurfaceOcean = candidate.id < 4, temperature = if (candidate.id < 4) 215f else 140f)
            }
        CosmicPreset.DEEP_SPACE -> emptyList()
    }.map { body ->
        val factor = if (preset == CosmicPreset.COLLISION_LAB || preset == CosmicPreset.OCEAN_IMPACT ||
            preset == CosmicPreset.GIANT_GRAZE) 1f else sqrt(gravity.coerceAtLeast(0f))
        body.copy(vx=body.vx*factor,vy=body.vy*factor)
    }
}

/** Per-body initial discretization, limited by MatterMode capacity. */
internal fun presetGrainCount(preset: CosmicPreset, body: CelestialBody,
    mode: MatterMode): Int {
    val detail = mode == MatterMode.DETAILED
    return when (preset) {
        CosmicPreset.OCEAN_IMPACT -> if (body.id < 3) (if (detail) 500 else 190)
            else (if (detail) 120 else 55)
        CosmicPreset.GALAXY_PASSAGE -> if (detail) 330 else 110
        CosmicPreset.NEBULA_CROSSING -> if (detail) 390 else 170
        CosmicPreset.GIANT_GRAZE -> if (body.mass > 1f) (if (detail) 560 else 200) else (if (detail) 60 else 24)
        CosmicPreset.BLUE_NEBULA -> if (body.stellarClass == StellarClass.CLOUD)
            (if (detail) 420 else 160) else (if (detail) 340 else 125)
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
        CosmicPreset.LIVING_SYSTEM,CosmicPreset.BLACK_HOLE_NURSERY,
        CosmicPreset.BLACK_HOLE_MERGER,CosmicPreset.GALACTIC_NUCLEUS,CosmicPreset.HYPERGIANT_SYSTEM,
        CosmicPreset.PULSAR_REEF,CosmicPreset.WHITE_DWARF_RELIC,
        CosmicPreset.ASTEROID_CORRIDOR,CosmicPreset.QUANTUM_SHORES,
        CosmicPreset.RESONANCE, CosmicPreset.COMET_PASSAGE,
        CosmicPreset.TROJAN_GARDEN, CosmicPreset.ROGUE_ENCOUNTER,
        CosmicPreset.COLLISION_LAB, CosmicPreset.COLD_NURSERY, CosmicPreset.ROCHE_PASSAGE,
        CosmicPreset.DOUBLE_PLANET, CosmicPreset.COUNTER_ROTATING_RINGS, CosmicPreset.FROZEN_OCEANS -> when {
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
    rocky: Boolean = false, gravity: Float = 1f,
    driftX: Float = 0f, driftY: Float = 0f, retrograde: Boolean = false, spiral: Boolean = false,
    totalMass: Float? = null
) {
    if (count <= 0 || outerRadius <= innerRadius) return
    val n = minOf(count, mode.maxParticles - grains.size)
    val golden = PI * (3.0 - sqrt(5.0))
    val spin = if (retrograde) -1.0 else 1.0
    val mass = if (totalMass != null && n > 0) totalMass / n else if (rocky) .0018f else .0009f
    for (i in 0 until n) {
        val f = (i + .5) / n.toDouble()
        // Uniform surface density across an annulus, with discrete gaps.
        val r = sqrt(innerRadius * innerRadius * (1 - f) +
            outerRadius * outerRadius * f)
        val a = if (spiral) spin * (3.8 * f + (i % 2) * PI + .17 * sin(golden * i))
            else golden * i
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
            nextGrainId(),
            cx + (r * cos(a)).toFloat(),
            cy + (r * sin(a)).toFloat(),
            driftX + (-spin * speed * sin(a)).toFloat(),
            driftY + (spin * speed * cos(a)).toFloat(),
            mass, mass * .000001 * (if (rocky) 430.0 else 125.0),
            material, owner
        ))
    }
}
