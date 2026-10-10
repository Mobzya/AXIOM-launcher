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
            val array = json.optJSONArray(key) ?: JSONArray()
            require(array.length() <= 300) { "Слишком много элементов" }
            return (0 until array.length()).map { array.getString(it) }
                .filter { it.length in 2..200 && it.all { ch ->
                    ch.isLetterOrDigit() || ch in "._-"
                } }.toSet()
        }
        val favorites = packages("favorites")
        val work = packages("work")
        val tools = packages("tools")
        val g = json.optDouble("gravity", 1.0).toFloat()
        require(g.isFinite()) { "Некорректная гравитация" }
        context.getSharedPreferences("axiom_prefs", Context.MODE_PRIVATE)
            .edit()
            .putFloat("gravity", g.coerceIn(.25f, 2.2f))
            .putStringSet("favorites", favorites)
            .putStringSet("space_work", work)
            .putStringSet("space_tools", tools)
            .apply()
        return favorites.size + work.size + tools.size
    }
}
