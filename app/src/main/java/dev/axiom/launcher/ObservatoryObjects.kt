package dev.axiom.launcher

internal enum class LaunchStyle(val title: String) {
    ORBIT("По орбите"), RETROGRADE("Обратно"), REST("Без скорости"), ESCAPE("Пролёт")
}

internal enum class BodyRecipe(val title: String, val material: MatterMaterial,
    val mass: Float, val temperature: Float, val classification: StellarClass,
    val description: String) {
    ROCKY("Каменный мир", MatterMaterial.ROCK, 1f, 290f, StellarClass.PLANET,
        "Железное ядро, силикатная мантия и каменная кора"),
    IRON("Металлическое ядро", MatterMaterial.IRON, 1.8f, 420f, StellarClass.PLANET,
        "Плотный железо-никелевый мир с проводящим веществом"),
    ICE("Ледяной карлик", MatterMaterial.ICE, .12f, 110f, StellarClass.PLANET,
        "Холодная оболочка; нагрев превращает лёд в воду"),
    OCEAN("Океанический мир", MatterMaterial.WATER, .7f, 310f, StellarClass.PLANET,
        "Вода и лёд над каменным ядром; вязкие столкновения"),
    GIANT("Газовый гигант", MatterMaterial.METHANE, 3f, 160f, StellarClass.PLANET,
        "Водород, гелий и летучая оболочка над малым ядром"),
    VOLCANIC("Вулканический мир", MatterMaterial.MAGMA, 1.2f, 2200f, StellarClass.PLANET,
        "Магма остывает в базальт; ударное тепло меняет состав"),
    CRYSTAL("Кристальный мир", MatterMaterial.CRYSTAL, .35f, 180f, StellarClass.PLANET,
        "Холодная минералогия и упругая твёрдая структура"),
    ORGANIC("Углеродный мир", MatterMaterial.ORGANICS, .45f, 260f, StellarClass.PLANET,
        "Органика и углерод; разложение при сильном нагреве"),
    DWARF_STAR("Красный карлик", MatterMaterial.HYDROGEN, 4f, 3100f, StellarClass.STAR,
        "Малое холодное светило с газовой оболочкой"),
    SUN("Звезда", MatterMaterial.HYDROGEN, 12f, 5800f, StellarClass.STAR,
        "Горячий водород и гелий; ионизация расходует тепло"),
    PLASMA("Плазменное ядро", MatterMaterial.PLASMA, 16f, 8500f, StellarClass.QUASAR,
        "Компактное горячее скопление; выбросы расходуют его энергию"),
    CLOUD("Молекулярное облако", MatterMaterial.AMMONIA, .6f, 90f, StellarClass.CLOUD,
        "Разреженная смесь летучих веществ, льда и органики")
}
