package dev.axiom.launcher

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.max

internal val AX_BLACK = Color.Black
internal val AX_WHITE = Color(0xFFF8F8F8)
internal val AX_GRAY = Color(0xFF989898)
internal val AX_DARK = Color(0xFF575757)
internal val AX_BORDER = Color(0xFF494949)
internal val AX_GLASS = Color(0xB3181818) // True translucency, no fake system-wide blur.
internal val AX_PANEL = Color(0xF5080808)

internal enum class AxiomMode { HOME, SEARCH, ATLAS, SETTINGS, TERMINAL }

@Composable
fun AxiomScreen(resumeVersion: Int) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("axiom_prefs", Context.MODE_PRIVATE) }
    var apps by remember { mutableStateOf(emptyList<InstalledApp>()) }
    var refresh by remember { mutableIntStateOf(0) }
    var mode by remember { mutableStateOf(AxiomMode.HOME) }
    var query by remember { mutableStateOf("") }
    var atlasQuery by remember { mutableStateOf("") }
    var atlasFilter by remember { mutableStateOf("Все") }
    val world = remember { CosmicWorld() }
    var terminalInitial by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        world.updateGravity(prefs.getFloat("gravity", 1f))
    }
    var favorites by remember {
        mutableStateOf(prefs.getStringSet("favorites", emptySet())?.toSet() ?: emptySet())
    }

    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val searchFocus = remember { FocusRequester() }
    val choices = remember(apps, favorites, query) {
        if (query.isBlank()) emptyList()
        else searchApps(apps, query, favorites).take(6)
    }

    LaunchedEffect(resumeVersion, refresh) {
        apps = withContext(Dispatchers.IO) { findLaunchableApps(context) }
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
    BackHandler(mode != AxiomMode.HOME) { close() }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
            .background(AX_BLACK)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding()
            .pointerInput(world) {
                detectTapGestures(
                    onTap = { point ->
                        world.disturb(
                            point.x / size.width.toFloat().coerceAtLeast(1f),
                            point.y / size.height.toFloat().coerceAtLeast(1f)
                        )
                    },
                    onDoubleTap = { world.spawnFromCorner() },
                    onLongPress = { world.triggerRandomEvent() }
                )
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

        CosmicEnvironment(
            world = world,
            interacting = mode != AxiomMode.HOME,
            modifier = Modifier.fillMaxSize()
        )

        if (mode == AxiomMode.HOME || mode == AxiomMode.SEARCH) {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .offset(y = dockTop)
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
                        if (query.trimStart().startsWith(">")) {
                            terminalInitial = query.trim().removePrefix(">").trim()
                            keyboard?.hide()
                            focus.clearFocus()
                            mode = AxiomMode.TERMINAL
                        } else {
                            choices.firstOrNull()?.let(::openApp)
                        }
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
                        onOpen = ::openApp,
                        onStar = ::star,
                        onAtlas = ::atlas
                    )
                }
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
                onStar = ::star
            )
        }
        if (mode == AxiomMode.SETTINGS) {
            AxiomSettings(
                gravity = world.gravity,
                onGravity = {
                    world.updateGravity(it)
                    prefs.edit().putFloat("gravity", world.gravity).apply()
                },
                onResetUniverse = { world.reset() },
                onSurprise = { world.triggerRandomEvent() },
                currentEpisode = world.episode.kind.name,
                onTerminal = {
                    terminalInitial = ""
                    mode = AxiomMode.TERMINAL
                },
                onRefresh = { refresh++; close() },
                onBack = ::close
            )
        }
        if (mode == AxiomMode.TERMINAL) {
            AxiomTerminal(
                initial = terminalInitial,
                apps = apps,
                gravity = world.gravity,
                bodyCount = world.bodies.size,
                currentEpisode = world.episode.kind.name,
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
        modifier = modifier.size(48.dp).clip(CircleShape).clickable(onClick = onClick),
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
        listOf(Color.White.copy(alpha = .15f + .22f * halo),
            Color.White.copy(alpha = .37f + .26f * halo),
            Color.White.copy(alpha = .13f + .31f * halo))
    )
    Row(
        Modifier.fillMaxWidth()
            .height(height)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .drawBehind {
                val spread = 13.dp.toPx()
                drawRoundRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = .10f * halo),
                            Color.White.copy(alpha = .03f * halo),
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
                listOf(Color(0xD9232323), Color(0xCE0F0F0F))))
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
                color = Color(0xFFD3D3D3)
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
