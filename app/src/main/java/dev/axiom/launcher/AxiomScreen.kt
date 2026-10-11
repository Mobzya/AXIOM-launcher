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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.Composable
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
            window.statusBarColor = if (currentPalette.light)
                android.graphics.Color.WHITE else android.graphics.Color.BLACK
            @Suppress("DEPRECATION")
            window.navigationBarColor = if (currentPalette.light)
                android.graphics.Color.WHITE else android.graphics.Color.BLACK
            WindowCompat.getInsetsController(window, window.decorView)
                .isAppearanceLightStatusBars = currentPalette.light
            WindowCompat.getInsetsController(window, window.decorView)
                .isAppearanceLightNavigationBars = currentPalette.light
        }
    }
    var apps by remember { mutableStateOf(emptyList<InstalledApp>()) }
    var refresh by remember { mutableIntStateOf(0) }
    var mode by remember { mutableStateOf(AxiomMode.HOME) }
    var query by remember { mutableStateOf("") }
    var atlasQuery by remember { mutableStateOf("") }
    var atlasFilter by remember { mutableStateOf("Все") }
    val world = remember { CosmicWorld() }
    val lab = remember { ScienceLab() }
    var terminalInitial by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        world.updateGravity(prefs.getFloat("gravity", 1f))
        world.updateMaximumBodies(prefs.getInt("max_bodies",23))
        world.updateWalls(prefs.getBoolean("wall_mode",false))
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
            world.updateGravity(prefs.getFloat("gravity", 1f))
            themeMode = modeFromPreference(prefs.getString("theme_mode", "DARK"))
            sunStart=prefs.getInt("day_start",7*60).coerceIn(0,1439)
            sunEnd=prefs.getInt("night_start",19*60).coerceIn(0,1439)
            world.updateMaximumBodies(prefs.getInt("max_bodies",23))
            world.updateWalls(prefs.getBoolean("wall_mode",false))
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
    BackHandler(mode != AxiomMode.HOME) { close() }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
            .background(AX_BLACK)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding()
            .pointerInput(world, mode) {
                // Ambient gestures must never steal touches from search or overlays.
                if (mode == AxiomMode.HOME) {
                    detectTapGestures(
                        onTap = { point ->
                            world.interact(
                                point.x / size.width.toFloat().coerceAtLeast(1f),
                                point.y / size.height.toFloat().coerceAtLeast(1f)
                            )
                        },
                        onDoubleTap = { point ->
                            val x = point.x / size.width.toFloat().coerceAtLeast(1f)
                            val y = point.y / size.height.toFloat().coerceAtLeast(1f)
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
                            } else world.spawnFromCorner()
                        }
                    )
                }
            }
            .pointerInput(world, mode) {
                if (mode == AxiomMode.HOME) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { pos ->
                            world.startBrush(
                                pos.x / size.width.toFloat().coerceAtLeast(1f),
                                pos.y / size.height.toFloat().coerceAtLeast(1f))
                        },
                        onDrag = { change, _ ->
                            world.moveBrush(
                                change.position.x / size.width.toFloat().coerceAtLeast(1f),
                                change.position.y / size.height.toFloat().coerceAtLeast(1f))
                            change.consume()
                        },
                        onDragEnd = { world.endBrush() },
                        onDragCancel = { world.endBrush() }
                    )
                }
            }
            .pointerInput(mode) {
                if (mode == AxiomMode.HOME) {
                    detectHorizontalDragGestures { change, amount ->
                        world.shiftPerspective(
                            amount / size.width.toFloat().coerceAtLeast(1f) * 2f
                        )
                        change.consume()
                    }
                }
            }
            // Parent-level gesture detector; swiping empty space opens the atlas.
            .pointerInput(mode) {
                if (mode == AxiomMode.HOME) {
                    var dragged = 0f
                    detectVerticalDragGestures(
                        onDragStart = { dragged = 0f },
                        onVerticalDrag = { change, distance ->
                            dragged += distance
                            if (distance > 0) change.consume()
                        },
                        onDragEnd = {
                            if (dragged > 55f) atlas()
                        }
                    )
                }
            }
    ) {
        val searching = mode == AxiomMode.SEARCH
        val suggestionLimit = (maxHeight * .44f).coerceAtMost(350.dp)
        val restTop = (maxHeight - 57.dp) / 2f
        val activeTop = (maxHeight * .115f).coerceAtLeast(25.dp)
        val dockTop by animateDpAsState(
            targetValue = if (searching) activeTop else restTop,
            animationSpec = spring(
                dampingRatio = .83f,
                stiffness = Spring.StiffnessLow
            ),
            label = "physical dock translation"
        )

        // Atlas/terminal/settings cover the background: stop all physics drawing
        // and frame updates while those screens are visible to protect scroll latency.
        if (mode == AxiomMode.HOME || mode == AxiomMode.SEARCH) {
            CosmicEnvironment(
                world = world,
                interacting = mode != AxiomMode.HOME,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Tapping a nearby entity opens a tiny, self-dismissing scientific inspector.
        val inspected = world.selectedBody
        if (mode == AxiomMode.HOME && inspected != null) {
            Column(
                Modifier.align(Alignment.BottomStart)
                    .padding(start = 23.dp, bottom = 56.dp)
                    .background(AX_SURFACE.copy(alpha = .92f), RoundedCornerShape(14.dp))
                    .border(1.dp, AX_BORDER, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 11.dp)
            ) {
                Text("OBJECT  /  ${inspected.id}", color = AX_GRAY,
                    fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                Text("m = ${"%.2f".format(java.util.Locale.US, inspected.mass)}   " +
                    "z = ${"%.2f".format(java.util.Locale.US, inspected.depth)}",
                    color = AX_WHITE, fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace)
                Text("v = ${"%.3f".format(java.util.Locale.US,
                    kotlin.math.hypot(inspected.vx, inspected.vy))}",
                    color = AX_GRAY, fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace)
            }
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
            TerminalCorner(
                modifier = Modifier.align(Alignment.TopStart)
                    .padding(top = 11.dp, start = 19.dp),
                onClick = {
                    terminalInitial = ""
                    keyboard?.hide()
                    focus.clearFocus()
                    mode = AxiomMode.TERMINAL
                }
            )
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
                maximumBodies = world.maxBodies,
                onMaximumBodies = {
                    world.updateMaximumBodies(it)
                    prefs.edit().putInt("max_bodies",world.maxBodies).apply()
                },
                wallsEnabled = world.wallsEnabled,
                onWallsEnabled = {
                    world.updateWalls(it)
                    prefs.edit().putBoolean("wall_mode",it).apply()
                },
                gravity = world.gravity,
                onGravity = {
                    world.updateGravity(it)
                    prefs.edit().putFloat("gravity", world.gravity).apply()
                },
                onResetUniverse = { world.reset() },
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
                timeScale = world.timeScale,
                onTimeScale = { world.updateTimeScale(it) },
                onBinary = { world.spawnBinary() },
                onRewind = { world.rewind(3) },
                onExport = { exportSettings.launch("axiom-settings.json") },
                onImport = { importSettings.launch(arrayOf("application/json",
                    "text/plain")) },
                onProfiler = { meter.toggle() },
                profilerEnabled = meter.enabled,
                profilerSummary = meter.summary,
                onSurprise = { world.triggerRandomEvent() },
                currentEpisode = world.episode.kind.name,
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
                currentEpisode = world.episode.kind.name,
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

/** Small, accessible terminal entry point. Kept away from the centered dock. */
@Composable
private fun TerminalCorner(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier.size(48.dp)
            .clip(RoundedCornerShape(15.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Открыть терминал AXIOM" }
            .border(1.dp, AX_BORDER, RoundedCornerShape(15.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(">_", color = AX_WHITE, fontFamily = FontFamily.Monospace,
            fontSize = 16.sp, letterSpacing = 0.sp)
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
    query: String,
    onQuery: (String) -> Unit,
    focusRequester: FocusRequester,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    onSubmit: () -> Unit
) {
    val height by animateDpAsState(
        targetValue = if (expanded) 70.dp else 57.dp,
        animationSpec = spring(
            dampingRatio = .86f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "search elevation"
    )
    val shape = RoundedCornerShape(24.dp)
    val halo by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(560),
        label = "search luminous halo"
    )
    val scale by animateFloatAsState(
        targetValue = if (expanded) 1.018f else 1f,
        animationSpec = spring(dampingRatio = .82f,
            stiffness = Spring.StiffnessMediumLow),
        label = "search volume"
    )
    val colorEdge = Brush.horizontalGradient(
        listOf(AX_WHITE.copy(alpha = .15f + .22f * halo),
            AX_WHITE.copy(alpha = .37f + .26f * halo),
            AX_WHITE.copy(alpha = .13f + .31f * halo))
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
            .background(Brush.verticalGradient(
                listOf(AX_GLASS, AX_SURFACE.copy(alpha = .90f))))
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
                "Поиск",
                modifier = Modifier.weight(1f),
                fontSize = 15.sp,
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
