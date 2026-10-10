package dev.axiom.launcher

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Android activity icons loaded off the UI thread. Proprietary Xiaomi icon
 * packs aren't guaranteed to expose theme substitutions through PackageManager.
 */
internal object AppIconStore {
    private val cache = object : LruCache<String, Bitmap>(6 * 1024 * 1024) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int = bitmap.byteCount
    }

    private fun key(app: InstalledApp): String = "${app.packageName}/${app.className}"

    @Synchronized fun peek(app: InstalledApp): Bitmap? = cache.get(key(app))

    @Suppress("DEPRECATION")
    fun load(context: Context, app: InstalledApp): Bitmap? {
        peek(app)?.let { return it }
        val pm = context.packageManager
        val icon = try {
            pm.getActivityIcon(ComponentName(app.packageName, app.className))
        } catch (_: PackageManager.NameNotFoundException) {
            try { pm.getApplicationIcon(app.packageName) }
            catch (_: PackageManager.NameNotFoundException) { return null }
        } catch (_: RuntimeException) {
            // Some OEM launchable activities don't expose their own drawable.
            try { pm.getApplicationIcon(app.packageName) }
            catch (_: RuntimeException) { return null }
        }
        return runCatching {
            val size = 112
            val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = AndroidCanvas(output)
            val previous = icon.bounds
            icon.setBounds(0, 0, size, size)
            icon.draw(canvas)
            icon.bounds = previous
            synchronized(this) { cache.put(key(app), output) }
            output
        }.getOrNull()
    }

    @Synchronized fun clear() = cache.evictAll()
}

@Composable
internal fun AxiomAppIcon(app: InstalledApp, size: Dp = 36.dp) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = AppIconStore.peek(app),
        key1 = app.packageName, key2 = app.className) {
        value = withContext(Dispatchers.IO) { AppIconStore.load(context, app) }
    }
    val shape = RoundedCornerShape(10.dp)
    val icon = bitmap
    if (icon != null) {
        Image(
            bitmap = icon.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(size).clip(shape)
        )
    } else {
        Box(
            Modifier.size(size).clip(shape)
                .background(AX_TILE)
                .border(1.dp, AX_BORDER.copy(alpha = .65f), shape),
            contentAlignment = Alignment.Center
        ) {
            Text(app.label.firstOrNull()?.uppercase() ?: "·",
                color = AX_WHITE, fontSize = 15.sp,
                fontWeight = FontWeight.Light)
        }
    }
}
