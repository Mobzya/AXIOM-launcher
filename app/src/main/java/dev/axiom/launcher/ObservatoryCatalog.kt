package dev.axiom.launcher

internal enum class ObservatoryObject(
    val title: String,
    val material: MatterMaterial,
    val type: StellarClass = StellarClass.PLANET,
    val mass: Float = 1.5f,
    val temperature: Float = 290f,
    val sizeFactor: Float = 1f
) {
    ROCKY("Каменный мир", MatterMaterial.ROCK),
    IRON("Железный мир", MatterMaterial.IRON, mass = 2.2f, sizeFactor = .78f),
    ICE("Ледяной мир", MatterMaterial.ICE, mass = .8f, temperature = 115f),
    OCEAN("Океанида", MatterMaterial.WATER, mass = 1.2f, temperature = 278f),
    LAVA("Лавовый мир", MatterMaterial.MAGMA, temperature = 1850f),
    CARBON("Углеродный мир", MatterMaterial.CARBON, temperature = 510f, sizeFactor = .88f),
    CRYSTAL("Кристальная луна", MatterMaterial.CRYSTAL, mass = .35f, temperature = 210f),
    SULFUR("Серный вулкан", MatterMaterial.SULFUR, mass = .65f, temperature = 820f),
    ORGANIC("Органический мир", MatterMaterial.ORGANICS, mass = 1.1f, temperature = 185f),
    GAS_GIANT("Газовый гигант", MatterMaterial.METHANE, mass = 9f, temperature = 220f, sizeFactor = 1.4f),
    ICE_GIANT("Ледяной гигант", MatterMaterial.AMMONIA, mass = 5f, temperature = 105f, sizeFactor = 1.25f),
    COMET("Ядро кометы", MatterMaterial.ICE, mass = .14f, temperature = 85f, sizeFactor = .65f),
    RED_DWARF("Красный карлик", MatterMaterial.HYDROGEN, StellarClass.STAR, 2.9f, 2900f, .78f),
    BLUE_GIANT("Голубой гигант", MatterMaterial.HYDROGEN, StellarClass.STAR, 16f, 8200f, 1.22f),
    QUASAR("Квазар", MatterMaterial.PLASMA, StellarClass.QUASAR, 10f, 6800f),
    NEBULA("Туманность", MatterMaterial.METHANE, StellarClass.CLOUD, 3f, 145f, 1.5f)
}

internal enum class EventFrequency(val title: String, val minimum: Float, val spread: Float) {
    OFF("Выкл.", 0f, 0f),
    RARE("Редко", 75f, 35f),
    NORMAL("Обычно", 28f, 20f),
    OFTEN("Часто", 9f, 8f);

    fun nextDelay(fraction: Float): Float =
        if (this == OFF) Float.POSITIVE_INFINITY else minimum + spread * fraction
}

internal fun eventFrequencyFromPreference(value: String?): EventFrequency =
    EventFrequency.entries.firstOrNull { it.name == value } ?: EventFrequency.NORMAL

internal enum class SpaceEvent(
    val kind: CosmicEventKind,
    val title: String,
    val detail: String,
    val weight: Int = 4
) {
    METEORS(CosmicEventKind.METEORS, "Метеорный дождь", "Горячие осколки входят в систему", 12),
    COMET(CosmicEventKind.COMET_FLYBY, "Пролёт кометы", "Ледяное ядро теряет летучие вещества рядом со звездой", 8),
    NOVA(CosmicEventKind.NOVA, "Новая звезда", "Выброс горячего газа из окрестностей светила", 7),
    SUPERNOVA(CosmicEventKind.SUPERNOVA, "Сверхновая", "Расширяющийся фронт передаёт импульс окружающему веществу", 3),
    LENS(CosmicEventKind.SINGULARITY, "Гравитационная аномалия", "Усиление притяжения вокруг массивного центра", 4),
    DISRUPTION(CosmicEventKind.TIDAL_DISRUPTION, "Приливной разрыв", "Неравномерное притяжение растягивает близкие тела", 4),
    PULSAR(CosmicEventKind.PULSAR, "Пульсар", "Вращающиеся импульсы разгоняют газ", 7),
    JET(CosmicEventKind.QUASAR_JET, "Джеты квазара", "Встречные выбросы плазмы", 6),
    FLARE(CosmicEventKind.MAGNETAR_FLARE, "Вспышка магнетара", "Серия направленных импульсов", 5),
    WIND(CosmicEventKind.SOLAR_WIND, "Звёздный ветер", "Регулярные выбросы горячего газа", 7),
    SHOCK(CosmicEventKind.COSMIC_SHOCK, "Ударная волна", "Фронт разгоняет частицы по мере распространения", 8),
    HEAT(CosmicEventKind.THERMAL_SPIKE, "Тепловой скачок", "Относительное движение превращается в тепло", 5),
    FREEZE(CosmicEventKind.CRYO_TIDE, "Криогенный прилив", "Быстрое охлаждение и конденсация", 6),
    IONIZATION(CosmicEventKind.IONIZATION_FRONT, "Ионизация", "Горячая плазма передаёт энергию водороду", 5),
    PLASMA(CosmicEventKind.PLASMA_STORM, "Плазменный шторм", "Внутренняя энергия превращается в движение", 5),
    ACCRETION(CosmicEventKind.ACCRETION_BURST, "Аккреция", "Холодная пыль быстрее собирается в сгустки", 6),
    NURSERY(CosmicEventKind.STELLAR_NURSERY, "Звёздные ясли", "Газовое облако охлаждается и уплотняется", 5),
    CRYSTAL(CosmicEventKind.CRYSTALLIZATION, "Кристаллизация", "Остывание плотного вещества", 5),
    RECONNECTION(CosmicEventKind.MAGNETIC_RECONNECTION, "Разрыв магнитного поля", "Пересоединение поля разгоняет плазму вдоль меняющейся оси", 4);

    companion object {
        private val byKind = entries.associateBy { it.kind }
        val totalWeight = entries.sumOf { it.weight }

        fun titleOf(kind: CosmicEventKind): String = byKind[kind]?.title ?: kind.name

        fun fromRoll(roll: Int): CosmicEventKind {
            var remainder = roll
            for (event in entries) {
                remainder -= event.weight
                if (remainder < 0) return event.kind
            }
            return entries.last().kind
        }
    }
}
