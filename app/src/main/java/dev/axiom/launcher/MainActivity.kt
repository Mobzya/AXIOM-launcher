package dev.axiom.launcher

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Void = Color(0xFF080A0D)
private val Ink = Color(0xFFE0E5EC)
private val Muted = Color(0xFF8B96A5)
private val Edge = Color(0xFF45515F)
private val Glass = Color(0xE51C222C) // Translucency, not a real backdrop blur.

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(8, 10, 13)
        window.navigationBarColor = android.graphics.Color.rgb(8, 10, 13)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = Void, surface = Glass, onSurface = Ink)) {
                AxiomHome()
            }
        }
    }
}

@Composable
private fun AxiomHome() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("axiom_prefs", Context.MODE_PRIVATE) }
    var allApps by remember { mutableStateOf(emptyList<InstalledApp>()) }
    var refresh by remember { mutableStateOf(0) }
    var motion by remember { mutableStateOf(prefs.getBoolean("motion", true)) }
    var favoritePackages by remember {
        mutableStateOf(prefs.getStringSet("favorites", emptySet())?.toSet() ?: emptySet())
    }
    var searchOpen by remember { mutableStateOf(false) }
    var drawerOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("Все") }
    var time by remember { mutableStateOf(clockText()) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val searchFocus = remember { FocusRequester() }

    LaunchedEffect(refresh) {
        allApps = withContext(Dispatchers.IO) { findLaunchableApps(context) }
    }
    LaunchedEffect(Unit) {
        while (true) { time = clockText(); delay(15000) }
    }
    LaunchedEffect(searchOpen) {
        if (searchOpen) {
            delay(150)
            searchFocus.requestFocus()
            keyboard?.show()
        }
    }

    fun collapse() {
        searchOpen = false
        drawerOpen = false
        settingsOpen = false
        query = ""
        keyboard?.hide()
        focus.clearFocus()
    }
    BackHandler(searchOpen || drawerOpen || settingsOpen) { collapse() }

    fun launch(app: InstalledApp) {
        try {
            openApp(context, app)
            collapse()
        } catch (_: Exception) {
            Toast.makeText(context, "Не удалось запустить ${app.label}", Toast.LENGTH_SHORT).show()
            refresh += 1
        }
    }

    val results = remember(allApps, query, favoritePackages) {
        searchApps(allApps, query, favoritePackages)
    }
    val catalog = remember(allApps, filter, favoritePackages) {
        when (filter) {
            "Избранное" -> allApps.filter { it.packageName in favoritePackages }
            "Системные" -> allApps.filter { it.system }
            "Сторонние" -> allApps.filterNot { it.system }
            else -> allApps
        }
    }

    Box(Modifier.fillMaxSize().background(Void)) {
        OrbitScene(motionEnabled = motion)
        Column(
            modifier = Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(top = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("AXIOM   /   001", color = Muted, fontSize = 10.sp,
                    letterSpacing = 2.sp, fontFamily = FontFamily.Monospace)
                Text("SETTINGS", color = Muted, fontSize = 10.sp,
                    letterSpacing = 1.sp, modifier = Modifier.clickable {
                        collapse(); settingsOpen = true
                    })
            }

            Box(
                Modifier.weight(1f).fillMaxWidth()
                    .pointerInput(searchOpen, drawerOpen, settingsOpen) {
                        var totalDrag = 0f
                        detectVerticalDragGestures(
                            onDragStart = { totalDrag = 0f },
                            onVerticalDrag = { change, amount ->
                                totalDrag += amount
                                change.consume()
                            },
                            onDragEnd = {
                                if (totalDrag > 65f) {
                                    collapse()
                                    drawerOpen = true
                                } else if (totalDrag < -65f) collapse()
                            }
                        )
                    }
            ) {
                Column(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 30.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(time, fontWeight = FontWeight.Light, color = Ink,
                        fontSize = 43.sp, letterSpacing = 1.sp)
                    Text("∇L(θ)  ·  0.001  /  OBSERVATORY", color = Muted,
                        fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp)
                }
            }

            AnimatedVisibility(
                visible = searchOpen || drawerOpen || settingsOpen,
                enter = if (motion) fadeIn(tween(210)) + slideInVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ), initialOffsetY = { it / 4 }
                ) else EnterTransition.None,
                exit = if (motion) fadeOut(tween(150)) + slideOutVertically(
                    animationSpec = tween(180), targetOffsetY = { it / 6 }
                ) else ExitTransition.None
            ) {
                when {
                    settingsOpen -> GlassPanel {
                        Text("PREFERENCES", color = Ink, fontSize = 12.sp,
                            letterSpacing = 2.sp)
                        Spacer(Modifier.height(20.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                Text("Ambient motion", color = Ink, fontSize = 15.sp)
                                Text("Медленные орбиты и частицы",
                                    color = Muted, fontSize = 12.sp)
                            }
                            Switch(checked = motion, onCheckedChange = {
                                motion = it
                                prefs.edit().putBoolean("motion", it).apply()
                            })
                        }
                        Spacer(Modifier.height(16.dp))
                        Text("Обновить приложения ↻", color = Ink,
                            fontSize = 14.sp,
                            modifier = Modifier.clickable {
                                refresh += 1
                                collapse()
                            }.padding(vertical = 10.dp))
                        Text("Закрыть ×", color = Muted, fontSize = 13.sp,
                            modifier = Modifier.clickable { collapse() }
                                .padding(vertical = 10.dp))
                    }

                    drawerOpen -> GlassPanel {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("APP ATLAS", color = Ink,
                                fontSize = 12.sp, letterSpacing = 2.sp)
                            Text("↑ Закрыть", color = Muted, fontSize = 12.sp,
                                modifier = Modifier.clickable { collapse() })
                        }
                        Spacer(Modifier.height(15.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Все", "Избранное", "Системные", "Сторонние")
                                .forEach { chip ->
                                    Text(
                                        chip,
                                        color = if (filter == chip) Ink else Muted,
                                        fontSize = 10.sp, maxLines = 1,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (filter == chip) Color(0xFF303944)
                                                else Color.Transparent
                                            )
                                            .clickable { filter = chip }
                                            .padding(horizontal = 7.dp, vertical = 9.dp)
                                    )
                                }
                        }
                        Spacer(Modifier.height(10.dp))
                        AppRows(catalog, favoritePackages,
                            onLaunch = ::launch, onFavorite = { pkg ->
                                favoritePackages =
                                    if (pkg in favoritePackages) favoritePackages - pkg
                                    else favoritePackages + pkg
                                prefs.edit().putStringSet(
                                    "favorites", favoritePackages
                                ).apply()
                            })
                    }

                    else -> GlassPanel {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("⌕", color = Ink, fontSize = 29.sp)
                            Spacer(Modifier.size(12.dp))
                            BasicTextField(
                                value = query,
                                onValueChange = { query = it },
                                singleLine = true,
                                cursorBrush = androidx.compose.ui.graphics.SolidColor(Ink),
                                textStyle = TextStyle(
                                    color = Ink, fontSize = 18.sp,
                                    fontWeight = FontWeight.Light
                                ),
                                keyboardOptions = KeyboardOptions(
                                    imeAction = ImeAction.Search
                                ),
                                modifier = Modifier.weight(1f)
                                    .focusRequester(searchFocus),
                                decorationBox = { inner ->
                                    Box {
                                        if (query.isEmpty()) {
                                            Text("Search anything...", color = Muted,
                                                fontSize = 18.sp)
                                        }
                                        inner()
                                    }
                                }
                            )
                            Text("×", color = Muted, fontSize = 23.sp,
                                modifier = Modifier.clickable { collapse() }
                                    .padding(start = 12.dp))
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("${results.size} RESULTS", color = Muted,
                            fontSize = 10.sp, letterSpacing = 2.sp)
                        Spacer(Modifier.height(7.dp))
                        AppRows(results.take(12), favoritePackages,
                            onLaunch = ::launch, onFavorite = { pkg ->
                                favoritePackages =
                                    if (pkg in favoritePackages) favoritePackages - pkg
                                    else favoritePackages + pkg
                                prefs.edit().putStringSet(
                                    "favorites", favoritePackages
                                ).apply()
                            })
                    }
                }
            }

            Spacer(Modifier.height(15.dp))
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(21.dp))
                    .background(Glass)
                    .border(1.dp, Edge.copy(alpha = .64f),
                        RoundedCornerShape(21.dp))
                    .clickable { collapse(); searchOpen = true }
                    .padding(horizontal = 20.dp, vertical = 19.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("⌕", color = Ink, fontSize = 26.sp)
                Spacer(Modifier.size(13.dp))
                Text("Поиск приложений и команд", color = Muted,
                    fontSize = 14.sp, fontWeight = FontWeight.Light)
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 15.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Text("↓  APP ATLAS", color = Muted,
                    fontSize = 11.sp, letterSpacing = 2.sp,
                    modifier = Modifier.clickable { collapse(); drawerOpen = true })
            }
        }
    }
}

@Composable
private fun GlassPanel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Glass)
            .border(1.dp, Edge.copy(alpha = .7f), RoundedCornerShape(26.dp))
            .padding(18.dp),
        content = content
    )
}

@Composable
private fun AppRows(
    apps: List<InstalledApp>,
    favoritePackages: Set<String>,
    onLaunch: (InstalledApp) -> Unit,
    onFavorite: (String) -> Unit
) {
    if (apps.isEmpty()) {
        Text("Пока нет подходящих приложений", color = Muted,
            fontSize = 13.sp, modifier = Modifier.padding(vertical = 18.dp))
        return
    }
    LazyColumn(Modifier.fillMaxWidth().height(330.dp)) {
        items(apps, key = { it.packageName + "/" + it.className }) { app ->
            Row(
                Modifier.fillMaxWidth().height(56.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(35.dp).clip(RoundedCornerShape(11.dp))
                        .background(Color(0xFF303842)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(app.label.take(1).uppercase(),
                        color = Ink, fontSize = 16.sp)
                }
                Text(app.label, color = Ink, fontSize = 15.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                        .clickable { onLaunch(app) }
                        .padding(start = 13.dp, top = 16.dp, bottom = 16.dp))
                Text(if (app.packageName in favoritePackages) "★" else "☆",
                    color = Muted, fontSize = 23.sp,
                    modifier = Modifier.clickable {
                        onFavorite(app.packageName)
                    }.padding(start = 12.dp, end = 4.dp))
            }
        }
    }
}

private fun clockText(): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
