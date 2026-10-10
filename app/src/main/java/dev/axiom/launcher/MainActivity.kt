package dev.axiom.launcher

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val Black = Color.Black
private val White = Color(0xFFFAFAFA)
private val Muted = Color(0xFF989898)
private val Dim = Color(0xFF626262)
private val Line = Color(0xFF343434)
private val Glass = Color(0xDB171717)
private val PanelBlack = Color(0xFA080808)
private val SearchShape = RoundedCornerShape(20.dp)

private enum class Surface { Home, Search, Atlas, Settings }

class MainActivity : ComponentActivity() {
    private var resumeRevision by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.BLACK
        window.navigationBarColor = android.graphics.Color.BLACK
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = 0
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Black, surface = Black,
                    onBackground = White, onSurface = White,
                    primary = White, onPrimary = Black
                )
            ) {
                AxiomHome(resumeRevision)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeRevision++
    }
}

@Composable
private fun AxiomHome(resumeRevision: Int) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("axiom_prefs", Context.MODE_PRIVATE) }
    var apps by remember { mutableStateOf(emptyList<InstalledApp>()) }
    var revision by remember { mutableIntStateOf(0) }
    var surface by remember { mutableStateOf(Surface.Home) }
    var query by remember { mutableStateOf("") }
    var atlasQuery by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Все") }
    var ambience by remember {
        mutableIntStateOf(prefs.getInt("ambient_level", if (prefs.getBoolean("motion", true)) 1 else 0))
    }
    var favorites by remember {
        mutableStateOf(prefs.getStringSet("favorites", emptySet())?.toSet() ?: emptySet())
    }

    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val searchFocus = remember { FocusRequester() }
    val suggestions = remember(apps, query, favorites) {
        if (query.isBlank()) emptyList()
        else searchApps(apps, query, favorites).take(6)
    }

    LaunchedEffect(resumeRevision, revision) {
        apps = withContext(Dispatchers.IO) { findLaunchableApps(context) }
    }
    LaunchedEffect(surface) {
        if (surface == Surface.Search) {
            delay(110)
            searchFocus.requestFocus()
            keyboard?.show()
        }
    }

    fun closePanels() {
        surface = Surface.Home
        query = ""
        atlasQuery = ""
        keyboard?.hide()
        focusManager.clearFocus()
    }
    fun openAtlas() {
        keyboard?.hide()
        focusManager.clearFocus()
        surface = Surface.Atlas
        atlasQuery = ""
    }
    fun launch(app: InstalledApp) {
        try {
            openApp(context, app)
            closePanels()
        } catch (_: Exception) {
            Toast.makeText(context, "Не удалось открыть ${app.label}", Toast.LENGTH_SHORT).show()
            revision++
        }
    }
    fun toggleFavorite(pkg: String) {
        favorites = if (pkg in favorites) favorites - pkg else favorites + pkg
        prefs.edit().putStringSet("favorites", favorites).apply()
    }

    BackHandler(surface != Surface.Home) { closePanels() }

    Box(Modifier.fillMaxSize().background(Black)) {
        // Scene is an independent drawing node. Typing does not rebuild its geometry.
        AmbientSpace(ambience, paused = surface != Surface.Home, modifier = Modifier.fillMaxSize())

        // Full-size gesture target behind the controls. The old version only listened
        // to a spacer, so the drawer gesture failed in many parts of the screen.
        Box(
            Modifier.fillMaxSize().pointerInput(surface) {
                if (surface == Surface.Home || surface == Surface.Search) {
                    var distance = 0f
                    detectVerticalDragGestures(
                        onDragStart = { distance = 0f },
                        onVerticalDrag = { change, dragAmount ->
                            distance += dragAmount
                            if (dragAmount > 0f) change.consume()
                        },
                        onDragEnd = {
                            if (distance > 75f) openAtlas()
                        }
                    )
                }
            }
        )

        BoxWithConstraints(
            Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding()
                .padding(horizontal = 21.dp)
        ) {
            val maxSuggestionHeight = (maxHeight * .32f).coerceAtMost(258.dp)

            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SettingsButton { keyboard?.hide(); surface = Surface.Settings }
                }
                Spacer(Modifier.weight(1f))

                if (surface == Surface.Home || surface == Surface.Search) {
                    SearchDock(
                        expanded = surface == Surface.Search,
                        query = query,
                        onQuery = { query = it },
                        focusRequester = searchFocus,
                        onOpen = { surface = Surface.Search },
                        onClose = ::closePanels,
                        onSubmit = { suggestions.firstOrNull()?.let(::launch) }
                    )
                    AnimatedVisibility(
                        visible = surface == Surface.Search && query.isNotBlank(),
                        enter = fadeIn(tween(170)) + slideInVertically(
                            initialOffsetY = { -it / 9 }, animationSpec = tween(190)
                        ),
                        exit = fadeOut(tween(120))
                    ) {
                        SuggestionPanel(
                            results = suggestions,
                            favorites = favorites,
                            heightLimit = maxSuggestionHeight,
                            onOpen = ::launch,
                            onStar = ::toggleFavorite
                        )
                    }
                }
                Spacer(Modifier.height(if (surface == Surface.Search) 13.dp else 58.dp))
            }
        }

        AnimatedVisibility(
            visible = surface == Surface.Atlas,
            enter = fadeIn(tween(160)) + slideInVertically(
                initialOffsetY = { -it / 7 }, animationSpec = tween(230)
            ),
            exit = fadeOut(tween(150)) + slideOutVertically(
                targetOffsetY = { -it / 9 }, animationSpec = tween(170)
            )
        ) {
            AtlasPanel(
                apps = apps,
                favorites = favorites,
                query = atlasQuery,
                onQuery = { atlasQuery = it },
                category = category,
                onCategory = { category = it },
                onBack = ::closePanels,
                onLaunch = ::launch,
                onStar = ::toggleFavorite
            )
        }

        AnimatedVisibility(
            visible = surface == Surface.Settings,
            enter = fadeIn(tween(170)),
            exit = fadeOut(tween(140))
        ) {
            SettingsPanel(
                ambience = ambience,
                onAmbience = {
                    ambience = it
                    prefs.edit().putInt("ambient_level", it).apply()
                },
                onRefresh = {
                    revision++
                    closePanels()
                },
                onBack = ::closePanels
            )
        }
    }
}

@Composable
private fun SettingsButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // No emoji, tinted Material icons or colorful glyphs.
        Canvas(Modifier.size(21.dp)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val r = size.minDimension * .29f
            drawCircle(color = Color(0xFFDDDDDD), radius = r,
                center = Offset(cx, cy), style = Stroke(width = 1.4.dp.toPx()))
            drawCircle(color = Color(0xFFDDDDDD), radius = 1.8.dp.toPx(),
                center = Offset(cx, cy))
            for (i in 0 until 8) {
                val a = i * (Math.PI / 4.0)
                val x = kotlin.math.cos(a).toFloat()
                val y = kotlin.math.sin(a).toFloat()
                drawLine(Color(0xFFDDDDDD), Offset(cx + x * r * 1.20f, cy + y * r * 1.20f),
                    Offset(cx + x * r * 1.55f, cy + y * r * 1.55f),
                    strokeWidth = 1.4.dp.toPx())
            }
        }
    }
}

@Composable
private fun SearchDock(
    expanded: Boolean,
    query: String,
    onQuery: (String) -> Unit,
    focusRequester: FocusRequester,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    onSubmit: () -> Unit
) {
    val height by animateDpAsState(
        targetValue = if (expanded) 64.dp else 54.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "search surface height"
    )
    val edge = if (expanded) Color(0xFF555555) else Color(0xFF343434)
    val touch = if (!expanded) Modifier.clickable(onClick = onOpen) else Modifier
    Row(
        Modifier.fillMaxWidth()
            .height(height)
            .clip(SearchShape)
            .background(Glass)
            .border(1.dp, edge.copy(alpha = .85f), SearchShape)
            .then(touch)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(Modifier.size(17.dp)) {
            val rad = 5.4.dp.toPx()
            drawCircle(White.copy(alpha = .92f), radius = rad,
                center = Offset(size.width * .39f, size.height * .38f),
                style = Stroke(1.25.dp.toPx()))
            drawLine(White.copy(alpha = .92f),
                start = Offset(size.width * .64f, size.height * .63f),
                end = Offset(size.width * .91f, size.height * .91f),
                strokeWidth = 1.3.dp.toPx())
        }
        Spacer(Modifier.size(16.dp))
        if (!expanded) {
            Text(
                "Поиск приложений",
                color = Color(0xFFBCBCBC),
                fontSize = 15.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = .1.sp,
                modifier = Modifier.weight(1f)
            )
        } else {
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
                textStyle = TextStyle(
                    color = White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Normal
                ),
                singleLine = true,
                cursorBrush = SolidColor(White),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                decorationBox = { inner ->
                    Box {
                        if (query.isEmpty()) {
                            Text("Начни вводить название", color = Muted, fontSize = 15.sp)
                        }
                        inner()
                    }
                }
            )
            Text("×", color = Muted, fontSize = 24.sp,
                modifier = Modifier.clickable(onClick = onClose).padding(start = 12.dp))
        }
    }
}

@Composable
private fun SuggestionPanel(
    results: List<InstalledApp>,
    favorites: Set<String>,
    heightLimit: Dp,
    onOpen: (InstalledApp) -> Unit,
    onStar: (String) -> Unit
) {
    Column(
        Modifier.fillMaxWidth()
            .padding(top = 10.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xEF111111))
            .border(1.dp, Color(0xFF2F2F2F), RoundedCornerShape(18.dp))
            .padding(horizontal = 13.dp, vertical = 7.dp)
    ) {
        if (results.isEmpty()) {
            Text("Ничего не найдено", color = Muted, fontSize = 13.sp,
                modifier = Modifier.padding(14.dp))
        } else {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = heightLimit)) {
                items(results, key = { "${it.packageName}/${it.className}" }) { app ->
                    AppEntry(app, favorites, onOpen, onStar)
                }
            }
        }
    }
}

@Composable
private fun AppEntry(
    app: InstalledApp,
    favorites: Set<String>,
    onOpen: (InstalledApp) -> Unit,
    onStar: (String) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().height(52.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(31.dp).clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF222222))
                .border(1.dp, Color(0xFF353535), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                app.label.firstOrNull()?.uppercase() ?: "·",
                color = White, fontSize = 14.sp,
                fontWeight = FontWeight.Light
            )
        }
        Text(
            app.label,
            modifier = Modifier.weight(1f)
                .clickable { onOpen(app) }
                .padding(start = 13.dp, top = 14.dp, bottom = 14.dp),
            fontSize = 14.sp, color = White, maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            if (app.packageName in favorites) "★" else "☆",
            color = if (app.packageName in favorites) White else Dim,
            fontSize = 20.sp,
            modifier = Modifier.clickable { onStar(app.packageName) }
                .padding(start = 13.dp, end = 6.dp, top = 10.dp, bottom = 10.dp)
        )
    }
}

@Composable
private fun AtlasPanel(
    apps: List<InstalledApp>,
    favorites: Set<String>,
    query: String,
    onQuery: (String) -> Unit,
    category: String,
    onCategory: (String) -> Unit,
    onBack: () -> Unit,
    onLaunch: (InstalledApp) -> Unit,
    onStar: (String) -> Unit
) {
    val filtered = remember(apps, query, favorites, category) {
        val base = if (query.isBlank()) apps else searchApps(apps, query, favorites)
        when (category) {
            "Избранное" -> base.filter { it.packageName in favorites }
            "Системные" -> base.filter { it.system }
            "Сторонние" -> base.filterNot { it.system }
            else -> base
        }
    }
    Column(
        Modifier.fillMaxSize()
            .background(PanelBlack)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding()
            .padding(horizontal = 23.dp)
    ) {
        var upwards by remember { mutableStateOf(0f) }
        Row(
            Modifier.fillMaxWidth()
                .padding(top = 21.dp, bottom = 22.dp)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { upwards = 0f },
                        onVerticalDrag = { change, dragAmount ->
                            upwards += dragAmount
                            change.consume()
                        },
                        onDragEnd = { if (upwards < -55f) onBack() }
                    )
                },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("APP ATLAS", color = White, fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace, letterSpacing = 3.sp)
                Text("${filtered.size} приложений · свайп вверх для закрытия",
                    color = Dim, fontSize = 11.sp,
                    modifier = Modifier.padding(top = 7.dp))
            }
            Text("×", color = White, fontSize = 28.sp,
                modifier = Modifier.clickable(onClick = onBack)
                    .padding(horizontal = 10.dp))
        }
        BasicTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            cursorBrush = SolidColor(White),
            textStyle = TextStyle(color = White, fontSize = 16.sp),
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF191919))
                .border(1.dp, Line, RoundedCornerShape(16.dp))
                .padding(horizontal = 18.dp, vertical = 16.dp),
            decorationBox = { inner ->
                Box {
                    if (query.isEmpty()) {
                        Text("Фильтр по названию", color = Muted, fontSize = 15.sp)
                    }
                    inner()
                }
            }
        )
        Spacer(Modifier.height(17.dp))
        androidx.compose.foundation.lazy.LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(listOf("Все", "Избранное", "Системные", "Сторонние")) { value ->
                FilterPill(value, selected = category == value) { onCategory(value) }
            }
        }
        Spacer(Modifier.height(18.dp))
        val state = rememberLazyListState()
        LaunchedEffect(category, query) { state.scrollToItem(0) }
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f),
            state = state
        ) {
            items(filtered, key = { "${it.packageName}/${it.className}" }) { app ->
                AppEntry(app, favorites, onLaunch, onStar)
            }
        }
    }
}

@Composable
private fun FilterPill(name: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(12.dp))
            .background(if (selected) Color(0xFF333333) else Color(0xFF171717))
            .border(1.dp, if (selected) Color(0xFF737373) else Line,
                RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(name, color = if (selected) White else Muted, fontSize = 12.sp)
    }
}

@Composable
private fun SettingsPanel(
    ambience: Int,
    onAmbience: (Int) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        Modifier.fillMaxSize()
            .background(PanelBlack)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 25.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 40.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("AXIOM / SETTINGS", color = White, fontSize = 13.sp,
                letterSpacing = 2.sp, fontFamily = FontFamily.Monospace)
            Text("×", color = White, fontSize = 27.sp,
                modifier = Modifier.clickable(onClick = onBack))
        }
        Text("AMBIENCE", color = Muted, fontSize = 11.sp,
            letterSpacing = 3.sp, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(14.dp))
        Text("Живая космическая сцена", color = White, fontSize = 17.sp)
        Spacer(Modifier.height(7.dp))
        Text("Незаметные движения и редкие научные объекты.\n"
                + "Во время поиска анимации приостанавливаются.",
            color = Muted, fontSize = 13.sp, lineHeight = 20.sp)
        Spacer(Modifier.height(19.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            listOf("Выкл.", "Тихо", "Живее").forEachIndexed { index, label ->
                FilterPill(label, selected = ambience == index) { onAmbience(index) }
            }
        }
        Spacer(Modifier.height(45.dp))
        Text("LIBRARY", color = Muted, fontSize = 11.sp,
            letterSpacing = 3.sp, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(17.dp))
        Text("Обновить список приложений ↗", color = White, fontSize = 15.sp,
            modifier = Modifier.clickable(onClick = onRefresh)
                .padding(vertical = 12.dp))
        Spacer(Modifier.height(35.dp))
        Text("AXIOM 0.2  /  MONOCHROME", color = Dim, fontSize = 11.sp,
            fontFamily = FontFamily.Monospace)
    }
}
