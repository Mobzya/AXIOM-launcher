package dev.axiom.launcher

internal enum class EventCadence(val title: String, val minimum: Float, val spread: Float) {
    QUIET("Тихо", 140f, 60f),
    RARE("Редко", 75f, 40f),
    NORMAL("Обычно", 32f, 16f),
    OFTEN("Часто", 12f, 10f),
    ACTIVE("Бурно", 6f, 5f);

    fun wait(fraction: Float): Float = minimum + spread * fraction
    val interval: String get() = "${minimum.toInt()}–${(minimum + spread).toInt()} с"
}

internal fun eventCadenceFromPreference(value: String?): EventCadence =
    EventCadence.entries.firstOrNull { it.name == value } ?: EventCadence.NORMAL
