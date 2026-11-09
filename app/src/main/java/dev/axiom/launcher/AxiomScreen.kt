package dev.axiom.launcher

import android.content.Context
import android.app.Activity
import androidx.core.view.WindowCompat
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.withContext
import kotlin.math.max

internal val AX_BLACK get() = AxiomColors.palette.background
internal val AX_WHITE get() = AxiomColors.palette.ink
internal val AX_GRAY get() = AxiomColors.palette.muted
internal val AX_DARK get() = AxiomColors.palette.faint
internal val AX_BORDER get() = AxiomColors.palette.border
internal val AX_GLASS get() = AxiomColors.palette.glass
internal val AX_PANEL get() = AxiomColors.palette.panel
internal val AX_SURFACE get() = AxiomColors.palette.surface
internal val AX_TILE get() = AxiomColors.palette.tile
internal val AX_DIVIDER get() = AxiomColors.palette.divider
internal val AX_HINT get() = AxiomColors.palette.hint

internal enum class AxiomMode { HOME, SEARCH, ATLAS, SETTINGS, TERMINAL, LAB }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AxiomScreen(resumeVersion: Int, meter: FrameMeter) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("axiom_prefs", Context.MODE_PRIVATE) }
    var themeMode by remember {
        mutableStateOf(modeFromPreference(prefs.getString("theme_mode", "DARK")))
    }
    var sunStart by remember { mutableIntStateOf(
        prefs.getInt("day_start",7*60).coerceIn(0,1439)) }
    var sunEnd by remember { mutableIntStateOf(
        prefs.getInt("night_start",19*60).coerceIn(0,1439)) }
    val currentPalette = AxiomColors.palette
    LaunchedEffect(themeMode,sunStart,sunEnd) {
        while (true) {
            AxiomColors.apply(themeMode,
                schedule=ThemeSchedule(sunStart,sunEnd))
            if (themeMode != AxiomThemeMode.AUTO) break
            // No background service; refresh automatic light/dark when UI is alive.
            delay(30_000)
        }
    }
    SideEffect {
        (context as? Activity)?.window?.let { window ->
            @Suppress("DEPRECATION")
            window.statusBarColor = currentPalette.background.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = currentPalette.background.toArgb()
            WindowCompat.getInsetsController(window, window.decorView)
                .isAppearanceLightStatusBars = currentPalette.light
            WindowCompat.getInsetsController(window, window.decorView)
                .isAppearanceLightNavigationBars = currentPalette.light
        }
    }
    var apps by remember { mutableStateOf(emptyList<InstalledApp>()) }
    var refresh by remember { mutableIntStateOf(0) }
    var mode by remember { mutableStateOf(AxiomMode.HOME) }
    var observatoryOpen by remember { mutableStateOf(false) }
    val sceneScope=rememberCoroutineScope()
    var loadingScene by remember {mutableStateOf(false)}
    val observatorySheet = rememberModalBottomSheetState(skipPartiallyExpanded=true)
    var query by remember { mutableStateOf("") }
    var atlasQuery by remember { mutableStateOf("") }
    var atlasFilter by remember { mutableStateOf("Все") }
    val world = remember {
        CosmicWorld(
            initialMatterMode = matterModeFromPreference(
                prefs.getString("matter_mode", MatterMode.BALANCED.name)),
            initialPreset = CosmicPreset.entries.firstOrNull {
                it.name == prefs.getString("cosmic_preset",
                    CosmicPreset.SOLAR_SYSTEM.name)
            } ?: CosmicPreset.SOLAR_SYSTEM,
            initialGravity = prefs.getFloat("gravity",1f)
        )
    }
    val lab = remember { ScienceLab() }
    var terminalInitial by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        loadObservation(prefs,world)
        world.updateGravity(prefs.getFloat("gravity", 1f))
        world.updateMaximumBodies(prefs.getInt("max_bodies",23))
        world.updateWalls(prefs.getBoolean("wall_mode",false))
        world.updateMatterMode(matterModeFromPreference(
            prefs.getString("matter_mode", MatterMode.BALANCED.name)))
    }
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(world,world.hourlyScenes,lifecycle) {
        if(world.hourlyScenes)lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while(true) {
                val hour=ZonedDateTime.now().truncatedTo(ChronoUnit.HOURS).toEpochSecond()/3600
                if(withContext(Dispatchers.Default){world.checkSceneHour(hour)})saveObservation(prefs,world)
                delay((3_600_000-System.currentTimeMillis()%3_600_000).coerceIn(500,15_000))
            }
        }
    }
    LaunchedEffect(world.preset,world.matchSceneAccent,AxiomColors.palette.light) {
        AxiomColors.applyScene(world.preset,world.matchSceneAccent)
    }
    var favorites by remember {
        mutableStateOf(prefs.getStringSet("favorites", emptySet())?.toSet() ?: emptySet())
    }

    val exportSettings = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                it.write(AxiomBackup.export(context))
            } ?: error("Нет доступа для записи")
        }.onSuccess {
            Toast.makeText(context, "Настройки AXIOM сохранены", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(context, "Не удалось экспортировать", Toast.LENGTH_SHORT).show()
        }
    }
    val uninstallApp = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // The user may accept or cancel. Read Android's authoritative package
        // inventory afterwards rather than pretending that removal succeeded.
        AppCatalogCache.invalidate()
        AppIconStore.clear()
        refresh++
    }
    fun requestUninstall(app: InstalledApp) {
        if (!canRequestUninstall(app, context.packageName)) {
            Toast.makeText(context, "Системное приложение защищено",
                Toast.LENGTH_SHORT).show()
            return
        }
        runCatching {
            uninstallApp.launch(uninstallConfirmationIntent(app))
        }.onFailure {
            Toast.makeText(context, "Не удалось открыть удаление",
                Toast.LENGTH_SHORT).show()
        }
    }
    val importSettings = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) runCatching {
            val input = context.contentResolver.openInputStream(uri)
                ?: error("Нет доступа к файлу")
            val json = input.bufferedReader().use { reader ->
                val buffer = CharArray(4096)
                val text = StringBuilder()
                while (true) {
                    val n = reader.read(buffer)
                    if (n < 0) break
                    require(text.length + n <= 400_000) { "Файл настроек слишком большой" }
                    text.append(buffer, 0, n)
                }
                text.toString()
            }
            AxiomBackup.import(context, json)
        }.onSuccess {
            favorites = prefs.getStringSet("favorites", emptySet())?.toSet() ?: emptySet()
            loadObservation(prefs,world)
        world.updateGravity(prefs.getFloat("gravity", 1f))
            themeMode = modeFromPreference(prefs.getString("theme_mode", "DARK"))
            sunStart=prefs.getInt("day_start",7*60).coerceIn(0,1439)
            sunEnd=prefs.getInt("night_start",19*60).coerceIn(0,1439)
            world.updateMaximumBodies(prefs.getInt("max_bodies",23))
            world.updateWalls(prefs.getBoolean("wall_mode",false))
            world.updateMatterMode(matterModeFromPreference(
                prefs.getString("matter_mode", MatterMode.BALANCED.name)))
            world.loadPreset(CosmicPreset.entries.firstOrNull {
                it.name == prefs.getString("cosmic_preset",
                    CosmicPreset.DEEP_SPACE.name)
            } ?: CosmicPreset.DEEP_SPACE)
            refresh++
            Toast.makeText(context, "Настройки AXIOM восстановлены", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(context, "Файл настроек не подходит", Toast.LENGTH_SHORT).show()
        }
    }

    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val searchFocus = remember { FocusRequester() }
    val choices = remember(apps, favorites, query) {
        if (query.isBlank() || query.startsWith(">") || query.startsWith("!") ||
            query.startsWith("=")) emptyList()
        else searchApps(apps, query, favorites).take(6)
    }

    LaunchedEffect(resumeVersion, refresh) {
        val latest = withContext(Dispatchers.IO) { AppCatalogCache.load(context) }
        // Keeping the same snapshot avoids rebuilding the entire Atlas on resume.
        if (apps != latest) apps = latest
    }
    LaunchedEffect(mode) {
        if (mode == AxiomMode.SEARCH) {
            delay(120)
            searchFocus.requestFocus()
            keyboard?.show()
        }
    }
    fun close() {
        observatoryOpen = false
        mode = AxiomMode.HOME
        query = ""
        atlasQuery = ""
        terminalInitial = ""
        keyboard?.hide()
        focus.clearFocus()
    }
    fun atlas() {
        keyboard?.hide()
        focus.clearFocus()
        mode = AxiomMode.ATLAS
    }
    fun openApp(app: InstalledApp) {
        try {
            dev.axiom.launcher.openApp(context, app)
            AppRecents.record(context, app.packageName)
            close()
        } catch (_: Exception) {
            Toast.makeText(context, "Не удалось запустить ${app.label}", Toast.LENGTH_SHORT).show()
            refresh++
        }
    }
    fun star(pkg: String) {
        favorites = if (pkg in favorites) favorites - pkg else favorites + pkg
        prefs.edit().putStringSet("favorites", favorites).apply()
    }
    val inlineResult = remember(query) {
        val input = query.trim()
        when {
            input.startsWith("=") -> runCatching {
                "= " + "%.9g".format(java.util.Locale.US,
                    arithmetic(input.removePrefix("=").trim()))
            }.getOrElse { "Проверь математическое выражение" }
            input.startsWith(">") || input.startsWith("!") ->
                "↵ Открыть командную консоль"
            else -> null
        }
    }
    BackHandler(observatoryOpen || mode != AxiomMode.HOME) {
        if (observatoryOpen) observatoryOpen = false else close()
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
            .background(AX_BLACK)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding()
            .pointerInput(world, mode, observatoryOpen) {
                // Ambient gestures must never steal touches from search or overlays.
                if (mode == AxiomMode.HOME && !observatoryOpen) {
                    detectTapGestures(
                        onTap = { point ->
                            UniverseViewport(size.width.toFloat(),size.height.toFloat())
                                .let {if(world.wallsEnabled)it.screenPoint(point.x,point.y) else it.normalized(point.x,point.y)}?.let { (x,y) -> world.interact(x,y) }
                        },
                        onDoubleTap = { point ->
                            val location = UniverseViewport(size.width.toFloat(),size.height.toFloat())
                                .let {if(world.wallsEnabled)it.screenPoint(point.x,point.y) else it.normalized(point.x,point.y)} ?: return@detectTapGestures
                            val (x,y) = location
                            val math = world.mathMoment
                            if (math.active && x in (math.x-.06f)..(math.x+.43f) &&
                                y in (math.y-.06f)..(math.y+.34f)) {
                                lab.select(when(math.kind) {
                                    MathKind.BACKPROP, MathKind.ATTENTION,
                                    MathKind.TRANSFORMER, MathKind.SOFTMAX ->
                                        LabKind.NEURAL
                                    MathKind.LIFE, MathKind.L_SYSTEM -> LabKind.LIFE
                                    MathKind.JULIA, MathKind.MANDELBROT -> LabKind.JULIA
                                    MathKind.LORENZ -> LabKind.LORENZ
                                    MathKind.PENDULUM -> LabKind.PENDULUM
                                    MathKind.PCA -> LabKind.PCA
                                    MathKind.FOURIER -> LabKind.FOURIER
                                    else -> LabKind.WAVE
                                })
                                mode = AxiomMode.LAB
                            } else world.disturb(x, y)
                        }
                    )
                }
            }
            .pointerInput(world, mode, observatoryOpen) {
                if (mode == AxiomMode.HOME && !observatoryOpen) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { pos ->
                            UniverseViewport(size.width.toFloat(),size.height.toFloat())
                                .let {if(world.wallsEnabled)it.screenPoint(pos.x,pos.y) else it.normalized(pos.x,pos.y)}?.let { (x,y) -> world.startBrush(x,y) }
                        },
                        onDrag = { change, _ ->
                            UniverseViewport(size.width.toFloat(),size.height.toFloat())
                                .let {if(world.wallsEnabled)it.screenPoint(change.position.x,change.position.y) else it.normalized(change.position.x,change.position.y)}?.let { (x,y) -> world.moveBrush(x,y) }
                            change.consume()
                        },
                        onDragEnd = { world.endBrush() },
                        onDragCancel = { world.endBrush() }
                    )
                }
            }
            // Parent-level gesture detector; swiping empty space opens the atlas.
            .pointerInput(mode,observatoryOpen) {
                if (mode == AxiomMode.HOME && !observatoryOpen) {
                    var dragged = 0f
                    detectVerticalDragGestures(
                        onDragStart = { dragged = 0f },
                        onVerticalDrag = { change, distance ->
                            dragged += distance
                            change.consume()
                        },
                        onDragEnd = {
                            if (kotlin.math.abs(dragged) > 55f) atlas()
                        }
                    )
                }
            }
    ) {
        val searching = mode == AxiomMode.SEARCH
        val suggestionLimit = (maxHeight * .44f).coerceAtMost(350.dp)
        val compactHome = maxHeight < 520.dp
        val restTop = if(compactHome) (maxHeight-162.dp).coerceAtLeast(90.dp)
            else maxHeight-196.dp
        val activeTop = (maxHeight * .115f).coerceAtLeast(78.dp)
        val dockTop by animateDpAsState(
            targetValue = if (searching) activeTop else restTop,
            animationSpec = if(world.reducedMotion) tween(0) else spring(
                dampingRatio = .95f,
                stiffness = Spring.StiffnessLow
            ),
            label = "physical dock translation"
        )

        // Atlas/terminal/settings cover the background: stop all physics drawing
        // and frame updates while those screens are visible to protect scroll latency.
        if (mode == AxiomMode.HOME || mode == AxiomMode.SEARCH) {
            CosmicEnvironment(
                world = world,
                interacting = mode != AxiomMode.HOME || observatoryOpen,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (mode == AxiomMode.HOME && !observatoryOpen) {
            if (!compactHome) Column(Modifier.align(Alignment.TopStart).padding(start=27.dp,top=82.dp)) {
                ObservatoryClock(world)
                Spacer(Modifier.height(10.dp))
                LivingHomeBadge(world)
            }
            val favoriteApps = apps.filter { it.packageName in favorites }.take(5)
            if(!compactHome && favoriteApps.isNotEmpty()) Row(Modifier.align(Alignment.BottomCenter)
                .padding(bottom=211.dp),horizontalArrangement=Arrangement.spacedBy(17.dp)) {
                favoriteApps.forEach { app ->
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp))
                        .clickable {openApp(app)}.semantics {contentDescription=app.label},
                        contentAlignment=Alignment.Center) { AxiomAppIcon(app,34.dp) }
                }
            }
            val inspected=world.bodies.firstOrNull {it.id==world.selectedBody?.id}
            if(inspected!=null) Column(Modifier.align(Alignment.BottomStart)
                .padding(start=25.dp,bottom=273.dp)
                .clip(RoundedCornerShape(14.dp)).background(AX_GLASS)
                .border(1.dp,AX_BORDER,RoundedCornerShape(14.dp)).padding(12.dp)) {
                Text("ТЕЛО №${inspected.id}",color=AX_ACCENT,fontSize=10.sp,letterSpacing=1.sp)
                Text("m = ${number(inspected.mass.toDouble())} · ${number(inspected.temperature.toDouble(),0)} K",
                    color=AX_WHITE,fontSize=12.sp)
            }
            HomeNavigation(Modifier.align(Alignment.BottomCenter)
                .padding(horizontal=24.dp).padding(bottom=if(compactHome) 12.dp else 28.dp),
                onApps=::atlas,onCosmos={observatoryOpen=true},onTerminal={
                    terminalInitial="";mode=AxiomMode.TERMINAL
                })
            if(!compactHome) Text(world.preset.title,color=AX_GRAY.copy(alpha=.7f),fontSize=10.sp,
                letterSpacing=.8.sp,modifier=Modifier.align(Alignment.BottomCenter).padding(bottom=113.dp))
        }
        if (mode == AxiomMode.HOME || mode == AxiomMode.SEARCH) {
            Column(
                modifier = Modifier.fillMaxWidth()
                    // Defer the animated position to the layout phase, not composition.
                    .offset { IntOffset(0, dockTop.roundToPx()) }
                    .padding(horizontal = 24.dp)
            ) {
                CentralSearchDock(
                    expanded = searching,
                    reducedMotion = world.reducedMotion,
                    query = query,
                    onQuery = { query = it },
                    focusRequester = searchFocus,
                    onOpen = { mode = AxiomMode.SEARCH },
                    onClose = ::close,
                    onSubmit = {
                        if (query.trimStart().startsWith(">") ||
                            query.trimStart().startsWith("!")) {
                            terminalInitial = query.trim().removePrefix(">")
                                .removePrefix("!").trim()
                            keyboard?.hide()
                            focus.clearFocus()
                            mode = AxiomMode.TERMINAL
                        } else if (query.trimStart().startsWith("=")) {
                            val result = inlineResult
                            if (result != null && result.startsWith("= ")) {
                                val clipboard = context.getSystemService(
                                    Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText(
                                    "AXIOM result", result.removePrefix("= ")))
                                Toast.makeText(context, "Результат скопирован",
                                    Toast.LENGTH_SHORT).show()
                            }
                        } else choices.firstOrNull()?.let(::openApp)
                    }
                )

                AnimatedVisibility(
                    visible = searching && query.isNotBlank(),
                    enter = fadeIn(animationSpec = androidx.compose.animation.core.tween(260)) +
                        expandVertically(
                            expandFrom = Alignment.Top,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessLow
                            )
                        ),
                    exit = fadeOut(animationSpec = androidx.compose.animation.core.tween(130)) +
                        shrinkVertically(
                            shrinkTowards = Alignment.Top,
                            animationSpec = androidx.compose.animation.core.tween(170)
                        )
                ) {
                    SearchSuggestions(
                        results = choices,
                        favorites = favorites,
                        maxHeight = suggestionLimit,
                        showHint = query.isBlank(),
                        specialResult = inlineResult,
                        onOpen = ::openApp,
                        onStar = ::star,
                        onRemove = ::requestUninstall,
                        onAtlas = ::atlas
                    )
                }
            }
        }

        if (mode == AxiomMode.HOME || mode == AxiomMode.SEARCH) {
            Column(Modifier.align(Alignment.TopStart).padding(start=27.dp,top=21.dp)) {
                Text("A X I O M",color=AX_WHITE,fontSize=14.sp,fontFamily=FontFamily.Monospace)
                Text("ДОМ С ПРИТЯЖЕНИЕМ",color=AX_GRAY,fontSize=8.sp,letterSpacing=1.4.sp)
            }
        }

        if (mode == AxiomMode.HOME || mode == AxiomMode.SEARCH) {
            SettingsCorner(
                Modifier.align(Alignment.TopEnd).padding(top = 11.dp, end = 19.dp)
            ) {
                keyboard?.hide()
                focus.clearFocus()
                mode = AxiomMode.SETTINGS
            }
        }

        if (mode == AxiomMode.HOME && observatoryOpen) {
            ModalBottomSheet(onDismissRequest={observatoryOpen=false},sheetState=observatorySheet,
                containerColor=AX_PANEL,contentColor=AX_WHITE,
                scrimColor=AX_BLACK.copy(alpha=.36f),shape=RoundedCornerShape(topStart=28.dp,topEnd=28.dp)) {
                UniverseControlPanel(world=world,onPersist={saveObservation(prefs,world)},
                    onScene={next ->
                        if(!loadingScene) {
                            loadingScene=true;observatoryOpen=false
                            sceneScope.launch {
                                try {
                                    withContext(Dispatchers.Default){world.loadPreset(next)}
                                    saveObservation(prefs,world)
                                } finally {loadingScene=false}
                            }
                        }
                    },onLab={choice ->
                        lab.select(choice);observatoryOpen=false;mode=AxiomMode.LAB
                    },onDismiss={observatoryOpen=false})
            }
        }

        if(loadingScene)Text("Готовим сцену…",color=AX_GRAY,fontSize=12.sp,
            modifier=Modifier.align(Alignment.BottomCenter).padding(bottom=24.dp))

        if (mode == AxiomMode.ATLAS) {
            AxiomAtlas(
                apps = apps,
                favorites = favorites,
                query = atlasQuery,
                onQuery = { atlasQuery = it },
                filter = atlasFilter,
                onFilter = { atlasFilter = it },
                onBack = ::close,
                onOpen = ::openApp,
                onStar = ::star,
                onRemove = ::requestUninstall,
                metrics = if (meter.enabled) meter.summary else null
            )
        }
        if (mode == AxiomMode.SETTINGS) {
            AxiomSettings(
                themeMode = themeMode,
                onThemeMode = { next ->
                    themeMode = next
                    AxiomColors.apply(next,schedule=ThemeSchedule(sunStart,sunEnd))
                    prefs.edit().putString("theme_mode", next.name).apply()
                },
                sunriseMinutes = sunStart,
                sunsetMinutes = sunEnd,
                onSunriseMinutes = {
                    sunStart=it.coerceIn(0,1439)
                    prefs.edit().putInt("day_start",sunStart).apply()
                },
                onSunsetMinutes = {
                    sunEnd=it.coerceIn(0,1439)
                    prefs.edit().putInt("night_start",sunEnd).apply()
                },
                onObservatory = {mode=AxiomMode.HOME;observatoryOpen=true},
                onSelectHome = {
                    runCatching {
                        context.startActivity(android.content.Intent(
                            android.provider.Settings.ACTION_HOME_SETTINGS
                        ))
                    }.onFailure {
                        context.startActivity(android.content.Intent(
                            android.provider.Settings.ACTION_SETTINGS
                        ))
                    }
                },
                onMath = {
                    lab.select(LabKind.WAVE)
                    mode = AxiomMode.LAB
                },
                onExport = { exportSettings.launch("axiom-settings.json") },
                onImport = { importSettings.launch(arrayOf("application/json",
                    "text/plain")) },
                onProfiler = { meter.toggle() },
                profilerEnabled = meter.enabled,
                profilerSummary = meter.summary,
                onTerminal = {
                    terminalInitial = ""
                    mode = AxiomMode.TERMINAL
                },
                onRefresh = {
                    AppCatalogCache.invalidate()
                    refresh++
                    close()
                },
                onBack = ::close
            )
        }
        if (mode == AxiomMode.LAB) {
            ScienceLabScreen(lab = lab, onBack = ::close)
        }
        if (mode == AxiomMode.TERMINAL) {
            AxiomTerminal(
                initial = terminalInitial,
                apps = apps,
                gravity = world.gravity,
                bodyCount = world.bodies.size,
                currentEpisode = world.episode.kind.name +
                    " / " + world.episode.intensity.label,
                eventHistory = world.eventHistory,
                onModel = { world.summonMath(it) },
                onLab = { choice ->
                    lab.select(choice ?: LabKind.WAVE)
                    keyboard?.hide()
                    focus.clearFocus()
                    mode = AxiomMode.LAB
                },
                onTimeScale = { world.updateTimeScale(it) },
                onRewind = { world.rewind(it) },
                onSpawnBinary = { world.spawnBinary() },
                onThemeMode = { next ->
                    themeMode = next
                    AxiomColors.apply(next,schedule=ThemeSchedule(sunStart,sunEnd))
                    prefs.edit().putString("theme_mode", next.name).apply()
                },
                onProfile = { meter.toggle() },
                onEvent = { world.trigger(it) },
                onSurprise = { world.triggerRandomEvent() },
                onDepth = { world.shiftPerspective(it) },
                onGravity = {
                    world.updateGravity(it)
                    prefs.edit().putFloat("gravity", world.gravity).apply()
                },
                onReset = { world.reset() },
                onSpawn = { world.spawnFromCorner() },
                onLaunch = ::openApp,
                onOpenAtlas = ::atlas,
                onBack = ::close
            )
        }
    }
}

@Composable
private fun SettingsCorner(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier.size(48.dp).clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Настройки AXIOM" },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(21.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.width * .275f
            drawCircle(AX_WHITE.copy(alpha = .86f), radius, center,
                style = Stroke(width = 1.2.dp.toPx()))
            drawCircle(AX_WHITE.copy(alpha = .86f), radius = 1.5.dp.toPx(), center)
            for (i in 0 until 8) {
                val radians = i.toFloat() * kotlin.math.PI.toFloat() / 4f
                val x = kotlin.math.cos(radians)
                val y = kotlin.math.sin(radians)
                drawLine(AX_WHITE.copy(alpha = .84f),
                    start = Offset(center.x + x * radius * 1.22f,
                        center.y + y * radius * 1.22f),
                    end = Offset(center.x + x * radius * 1.56f,
                        center.y + y * radius * 1.56f),
                    strokeWidth = 1.15.dp.toPx())
            }
        }
    }
}

@Composable
private fun CentralSearchDock(
    expanded: Boolean,
    reducedMotion: Boolean,
    query: String,
    onQuery: (String) -> Unit,
    focusRequester: FocusRequester,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    onSubmit: () -> Unit
) {
    val height by animateDpAsState(
        targetValue = if (expanded) 70.dp else 57.dp,
        animationSpec = if(reducedMotion) tween(0) else spring(
            dampingRatio = .95f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "search elevation"
    )
    val shape = RoundedCornerShape(24.dp)
    val halo by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(if(reducedMotion) 0 else 420),
        label = "search luminous halo"
    )
    val scale by animateFloatAsState(
        targetValue = if (expanded && !reducedMotion) 1.008f else 1f,
        animationSpec = if(reducedMotion) tween(0) else spring(dampingRatio = .95f,
            stiffness = Spring.StiffnessMediumLow),
        label = "search volume"
    )
    // Subtle, directional iridescence: gradients are calculated only when
    // the palette or focus state changes, not in every particle draw.
    val cyan = if (AxiomColors.palette.light)
        Color(0xff46879b) else Color(0xff83cbe0)
    val violet = if (AxiomColors.palette.light)
        Color(0xff8c6fb2) else Color(0xffb6a1eb)
    val colorEdge = Brush.horizontalGradient(
        listOf(cyan.copy(alpha=.36f+.35f*halo),
            AX_WHITE.copy(alpha=.20f+.25f*halo),
            violet.copy(alpha=.40f+.35f*halo))
    )
    Row(
        Modifier.fillMaxWidth()
            .height(height)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .drawBehind {
                val spread = 13.dp.toPx()
                drawRoundRect(
                    brush = Brush.radialGradient(
                        colors = listOf(AX_WHITE.copy(alpha = .10f * halo),
                            AX_WHITE.copy(alpha = .03f * halo),
                            Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(
                            size.width * .5f, size.height * .5f),
                        radius = size.width * .72f
                    ),
                    topLeft = androidx.compose.ui.geometry.Offset(-spread, -spread),
                    size = androidx.compose.ui.geometry.Size(
                        size.width + 2f * spread, size.height + 2f * spread),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                        35.dp.toPx(), 35.dp.toPx())
                )
            }
            .clip(shape)
            .background(Brush.linearGradient(
                listOf(
                    cyan.copy(alpha=if(expanded) .19f else .10f),
                    AX_GLASS,
                    AX_SURFACE.copy(alpha=.98f),
                    violet.copy(alpha=if(expanded) .16f else .09f)
                )))
            .border(BorderStroke(1.dp, colorEdge), shape)
            .then(if (expanded) Modifier else Modifier.clickable(onClick = onOpen))
            .padding(horizontal = 21.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(Modifier.size(19.dp)) {
            drawCircle(AX_WHITE.copy(alpha = .9f),
                radius = size.width * .295f,
                center = Offset(size.width * .39f, size.height * .39f),
                style = Stroke(1.3.dp.toPx()))
            drawLine(
                AX_WHITE.copy(alpha = .9f),
                start = Offset(size.width * .62f, size.height * .62f),
                end = Offset(size.width * .91f, size.height * .91f),
                strokeWidth = 1.3.dp.toPx()
            )
        }
        Spacer(Modifier.size(16.dp))
        if (!expanded) {
            Text(
                "Найти приложение или вычислить…",
                modifier = Modifier.weight(1f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = .4.sp,
                color = AX_HINT
            )
        } else {
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
                textStyle = TextStyle(
                    fontSize = 17.sp,
                    color = AX_WHITE,
                    fontWeight = FontWeight.Normal
                ),
                cursorBrush = SolidColor(AX_WHITE),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                decorationBox = { content ->
                    Box {
                        if (query.isEmpty()) {
                            Text("Что открыть?", color = AX_GRAY, fontSize = 16.sp)
                        }
                        content()
                    }
                }
            )
            Text("×", color = AX_GRAY, fontSize = 24.sp,
                modifier = Modifier.clickable(onClick = onClose)
                    .padding(start = 12.dp))
        }
    }
}
