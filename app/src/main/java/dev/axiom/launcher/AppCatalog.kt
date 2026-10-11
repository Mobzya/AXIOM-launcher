package dev.axiom.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import java.util.Locale

data class InstalledApp(
    val label: String,
    val packageName: String,
    val className: String,
    val system: Boolean
)

/** List only visible MAIN/LAUNCHER activities, not all packages. */
@Suppress("DEPRECATION")
fun findLaunchableApps(context: Context): List<InstalledApp> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0)
        .mapNotNull { info ->
            val activity = info.activityInfo ?: return@mapNotNull null
            InstalledApp(
                label = info.loadLabel(pm).toString().ifBlank { activity.packageName },
                packageName = activity.packageName,
                className = activity.name,
                system = (activity.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            )
        }
        .distinctBy { "${it.packageName}/${it.className}" }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
}

fun openApp(context: Context, app: InstalledApp) {
    val intent = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .setClassName(app.packageName, app.className)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    context.startActivity(intent)
}

private val ruLatin = mapOf(
    'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e",
    'ё' to "e", 'ж' to "zh", 'з' to "z", 'и' to "i", 'й' to "y", 'к' to "k",
    'л' to "l", 'м' to "m", 'н' to "n", 'о' to "o", 'п' to "p", 'р' to "r",
    'с' to "s", 'т' to "t", 'у' to "u", 'ф' to "f", 'х' to "h", 'ц' to "ts",
    'ч' to "ch", 'ш' to "sh", 'щ' to "shch", 'ъ' to "", 'ы' to "y", 'ь' to "",
    'э' to "e", 'ю' to "yu", 'я' to "ya"
)
private fun latin(s: String) = s.lowercase(Locale.ROOT)
    .map { ruLatin[it] ?: it.toString() }.joinToString("")

/** Stable, explainable ranking: prefix, substring and favorites. No ML yet. */
fun searchApps(apps: List<InstalledApp>, query: String, favorites: Set<String>): List<InstalledApp> {
    val q = query.trim().lowercase(Locale.ROOT)
    val qLatin = latin(q)
    return apps.mapNotNull { app ->
        val name = app.label.lowercase(Locale.ROOT)
        val nameLatin = latin(name)
        val relevance = when {
            q.isEmpty() -> 5
            name.startsWith(q) -> 0
            nameLatin.startsWith(qLatin) -> 1
            name.contains(q) -> 2
            nameLatin.contains(qLatin) -> 3
            else -> return@mapNotNull null
        }
        app to relevance
    }.sortedWith(
        compareBy<Pair<InstalledApp, Int>> { it.second }
            .thenByDescending { it.first.packageName in favorites }
            .thenBy { it.first.label.lowercase(Locale.ROOT) }
    ).map { it.first }
}


/** Avoid package-manager queries on every return from another app. */
internal object AppCatalogCache {
    private var saved: List<InstalledApp>? = null
    private var checkedAtMillis = 0L
    @Synchronized fun invalidate() {
        saved = null
        checkedAtMillis = 0L
    }

    @Synchronized fun load(context: Context): List<InstalledApp> {
        val now = android.os.SystemClock.elapsedRealtime()
        val cached = saved
        if (cached != null && now - checkedAtMillis < 5 * 60 * 1000L) {
            return cached
        }
        return findLaunchableApps(context).also {
            saved = it
            checkedAtMillis = now
        }
    }
}

/** Locally collected launcher actions only; does not require Usage Access permission. */
internal object AppRecents {
    private const val KEY = "recent_packages"
    fun list(context: Context): List<String> =
        context.getSharedPreferences("axiom_prefs", Context.MODE_PRIVATE)
            .getString(KEY, "").orEmpty().split('|').filter { it.isNotBlank() }

    fun record(context: Context, packageName: String) {
        val value = (listOf(packageName) + list(context).filterNot {
            it == packageName
        }).take(16).joinToString("|")
        context.getSharedPreferences("axiom_prefs", Context.MODE_PRIVATE)
            .edit().putString(KEY, value).apply()
    }
}
