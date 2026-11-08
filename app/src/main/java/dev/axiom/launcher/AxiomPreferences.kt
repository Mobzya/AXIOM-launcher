package dev.axiom.launcher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** User-defined local collections; no Usage Access, contacts or cloud sync. */
internal object AppSpaces {
    private val ids = setOf("work", "tools")
    fun load(context: Context, id: String): Set<String> {
        require(id in ids)
        return context.getSharedPreferences("axiom_prefs", Context.MODE_PRIVATE)
            .getStringSet("space_$id", emptySet())?.toSet() ?: emptySet()
    }
    fun toggle(context: Context, id: String, pkg: String): Set<String> {
        val next = load(context, id).let { if (pkg in it) it - pkg else it + pkg }
        context.getSharedPreferences("axiom_prefs", Context.MODE_PRIVATE)
            .edit().putStringSet("space_$id", next).apply()
        return next
    }
}

/**
 * Small export format limited to AXIOM configuration. Does not include device
 * identifiers or installed-app inventory. Import validates data and caps sizes.
 */
internal object AxiomBackup {
    fun export(context: Context): String {
        val prefs = context.getSharedPreferences("axiom_prefs", Context.MODE_PRIVATE)
        return JSONObject().apply {
            put("format", "axiom-config")
            put("version", 1)
            put("gravity", prefs.getFloat("gravity", 1f).toDouble())
            put("themeMode", prefs.getString("theme_mode", "DARK"))
            put("dayStart", prefs.getInt("day_start",7*60))
            put("nightStart", prefs.getInt("night_start",19*60))
            put("maxBodies", prefs.getInt("max_bodies",23))
            put("walls", prefs.getBoolean("wall_mode",false))
            put("matterMode", prefs.getString("matter_mode", MatterMode.BALANCED.name))
            put("cosmicPreset",prefs.getString("cosmic_preset",CosmicPreset.SOLAR_SYSTEM.name))
            for ((key,default) in observationDefaults) put(key,prefs.getBoolean(key,default))
            put("favorites", JSONArray(
                prefs.getStringSet("favorites", emptySet())?.sorted() ?: emptyList<String>()
            ))
            put("work", JSONArray(AppSpaces.load(context, "work").sorted()))
            put("tools", JSONArray(AppSpaces.load(context, "tools").sorted()))
        }.toString(2)
    }

    fun import(context: Context, text: String): Int {
        require(text.length <= 400_000) { "Слишком большой файл настроек" }
        val json = JSONObject(text)
        require(json.optString("format") == "axiom-config") {
            "Файл не относится к настройкам AXIOM"
        }
        require(json.optInt("version") == 1) { "Неподдерживаемая версия формата" }
        fun packages(key: String): Set<String> {
            val array = json.optJSONArray(key)
                ?: error("Повреждённый раздел настроек: $key")
            require(array.length() <= 300) { "Слишком много элементов" }
            return (0 until array.length()).map {
                require(array.get(it) is String) { "Неверный формат пакета" }
                val name = array.getString(it)
                require(name.length in 2..200 && name.all { ch ->
                    ch.isLetterOrDigit() || ch in "._-"
                }) { "Некорректное имя приложения" }
                name
            }.toSet()
        }
        val favorites = packages("favorites")
        val work = packages("work")
        val tools = packages("tools")
        val theme = modeFromPreference(json.optString("themeMode", "DARK"))
        val g = json.optDouble("gravity", 1.0).toFloat()
        require(g.isFinite()) { "Некорректная гравитация" }
        context.getSharedPreferences("axiom_prefs", Context.MODE_PRIVATE)
            .edit()
            .putFloat("gravity", g.coerceIn(.25f, 2.2f))
            .putString("theme_mode", theme.name)
            .putInt("day_start",json.optInt("dayStart",7*60).coerceIn(0,1439))
            .putInt("night_start",json.optInt("nightStart",19*60).coerceIn(0,1439))
            .putInt("max_bodies",json.optInt("maxBodies",23).coerceIn(12,64))
            .putBoolean("wall_mode",json.optBoolean("walls",false))
            .putString("matter_mode",matterModeFromPreference(
                json.optString("matterMode",MatterMode.BALANCED.name)).name)
            .putString("cosmic_preset",CosmicPreset.entries.firstOrNull {
                it.name==json.optString("cosmicPreset")
            }?.name ?: CosmicPreset.SOLAR_SYSTEM.name)
            .also { editor -> for((key,default) in observationDefaults)
                editor.putBoolean(key,json.optBoolean(key,default)) }
            .putStringSet("favorites", favorites)
            .putStringSet("space_work", work)
            .putStringSet("space_tools", tools)
            .apply()
        return favorites.size + work.size + tools.size
    }
}

internal fun loadObservation(prefs: android.content.SharedPreferences, world: CosmicWorld) {
    world.configureObservation(
        events=prefs.getBoolean("auto_events",true),
        trails=prefs.getBoolean("orbit_trails",true),
        vectors=prefs.getBoolean("velocity_vectors",false),
        motion=prefs.getBoolean("reduced_motion",false),
        maths=prefs.getBoolean("ambient_math",false),
        ml=prefs.getBoolean("orbit_learning",true))
}

internal fun saveObservation(prefs: android.content.SharedPreferences, world: CosmicWorld) {
    prefs.edit().putBoolean("auto_events",world.automaticEvents)
        .putBoolean("orbit_trails",world.showTrails)
        .putBoolean("velocity_vectors",world.showVectors)
        .putBoolean("reduced_motion",world.reducedMotion)
        .putBoolean("ambient_math",world.ambientMath)
        .putBoolean("orbit_learning",world.learningEnabled)
        .putBoolean("wall_mode",world.wallsEnabled)
        .putString("cosmic_preset",world.preset.name)
        .putString("matter_mode",world.matterMode.name)
        .putInt("max_bodies",world.maxBodies)
        .putFloat("gravity",world.gravity).apply()
}

private val observationDefaults = mapOf("auto_events" to true,"orbit_trails" to true,
    "velocity_vectors" to false,"reduced_motion" to false,
    "ambient_math" to false,"orbit_learning" to true)
