package dev.axiom.launcher

internal enum class EventCadence(val title: String, val minimum: Float, val spread: Float) {
    RARE("Редко", 75f, 40f),
    NORMAL("Обычно", 32f, 16f),
    OFTEN("Часто", 12f, 10f);

    fun wait(fraction: Float): Float = minimum + spread * fraction
}

internal fun eventCadenceFromPreference(value: String?): EventCadence =
    EventCadence.entries.firstOrNull { it.name == value } ?: EventCadence.NORMAL
