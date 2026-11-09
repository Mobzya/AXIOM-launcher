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
            put("lifeMode",prefs.getString("life_mode",LifeMode.EMERGENCE.name))
            put("lifePace",prefs.getFloat("life_pace",1f).toDouble())
            put("rareRate",prefs.getString("rare_rate",CatastropheRate.NATURAL.name))
            put("quantumCoupling",prefs.getFloat("quantum_coupling",1f).toDouble())
            put("quantumDephasing",prefs.getFloat("quantum_dephasing",.06f).toDouble())
            put("scenePool",JSONArray((prefs.getStringSet("scene_pool",null)
                ?: CosmicPreset.entries.map {it.name}.toSet()).sorted()))
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
        val pace=json.optDouble("lifePace",1.0).toFloat()
        val coupling=json.optDouble("quantumCoupling",1.0).toFloat()
        val dephasing=json.optDouble("quantumDephasing",.06).toFloat()
        require(pace.isFinite() && coupling.isFinite() && dephasing.isFinite()) {"Некорректные параметры симуляции"}
        val pool=json.optJSONArray("scenePool")
        require(pool==null || pool.length()<=100) {"Слишком много сценариев"}
        val poolNames=if(pool==null)CosmicPreset.entries.map {it.name}.toSet() else
            (0 until pool.length()).mapNotNull {i ->
                CosmicPreset.entries.firstOrNull {it.name==pool.optString(i)}?.name
            }.toSet().ifEmpty {CosmicPreset.entries.map {it.name}.toSet()}
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
            .putString("life_mode",LifeMode.entries.firstOrNull {it.name==json.optString("lifeMode")}?.name ?: LifeMode.EMERGENCE.name)
            .putFloat("life_pace",pace.coerceIn(.5f,3f))
            .putString("rare_rate",CatastropheRate.entries.firstOrNull {it.name==json.optString("rareRate")}?.name ?: CatastropheRate.NATURAL.name)
            .putFloat("quantum_coupling",coupling.coerceIn(.1f,3f))
            .putFloat("quantum_dephasing",dephasing.coerceIn(0f,2f))
            .putStringSet("scene_pool",poolNames)
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
    world.configureCosmos(rare=prefs.getBoolean("rare_events",true),
        rate=CatastropheRate.entries.firstOrNull {it.name==prefs.getString("rare_rate",null)} ?: CatastropheRate.NATURAL,
        hourly=prefs.getBoolean("hourly_scenes",false),
        pool=CosmicPreset.entries.filter {prefs.getStringSet("scene_pool",null)?.contains(it.name) ?: true}.toSet(),
        clockWall=prefs.getBoolean("clock_wall",true),accent=prefs.getBoolean("scene_accent",true),
        quantumOn=prefs.getBoolean("quantum_enabled",true),coupling=prefs.getFloat("quantum_coupling",1f),
        decoherence=prefs.getFloat("quantum_dephasing",.06f),thermalBalance=prefs.getBoolean("radiative_climate",true))
    world.configureCivilization(CivilizationConfig(
        mode=LifeMode.entries.firstOrNull {it.name==prefs.getString("life_mode",null)} ?: LifeMode.EMERGENCE,
        colonization=prefs.getBoolean("life_colonization",true),defense=prefs.getBoolean("life_defense",true),
        routes=prefs.getBoolean("life_routes",true),pace=prefs.getFloat("life_pace",1f)))
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
        .putBoolean("radiative_climate",world.radiativeClimate)
        .putBoolean("rare_events",world.rareCatastrophes)
        .putString("rare_rate",world.catastropheRate.name)
        .putBoolean("hourly_scenes",world.hourlyScenes)
        .putStringSet("scene_pool",world.scenePool.map {it.name}.toSet())
        .putBoolean("clock_wall",world.clockWallsEnabled)
        .putBoolean("scene_accent",world.matchSceneAccent)
        .putBoolean("quantum_enabled",world.quantumEnabled)
        .putFloat("quantum_coupling",world.quantumCoupling)
        .putFloat("quantum_dephasing",world.quantumDephasing)
        .putString("life_mode",world.civilizationConfig.mode.name)
        .putFloat("life_pace",world.civilizationConfig.pace)
        .putBoolean("life_colonization",world.civilizationConfig.colonization)
        .putBoolean("life_defense",world.civilizationConfig.defense)
        .putBoolean("life_routes",world.civilizationConfig.routes)
        .putInt("max_bodies",world.maxBodies)
        .putFloat("gravity",world.gravity).apply()
}

private val observationDefaults = mapOf("auto_events" to true,"orbit_trails" to true,
    "velocity_vectors" to false,"reduced_motion" to false,
    "ambient_math" to false,"orbit_learning" to true,
    "rare_events" to true,"hourly_scenes" to false,"clock_wall" to true,
    "scene_accent" to true,"quantum_enabled" to true,"life_colonization" to true,
    "life_defense" to true,"life_routes" to true,"radiative_climate" to true)
