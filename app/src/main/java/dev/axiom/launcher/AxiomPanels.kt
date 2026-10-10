package dev.axiom.launcher

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.TransformOrigin
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min

@Composable
internal fun SearchSuggestions(
    results: List<InstalledApp>,
    favorites: Set<String>,
    maxHeight: Dp,
    showHint: Boolean,
    specialResult: String? = null,
    onOpen: (InstalledApp) -> Unit,
    onStar: (String) -> Unit,
    onAtlas: () -> Unit
) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier.fillMaxWidth().padding(top = 11.dp)
            .clip(shape)
            .background(Color(0xEF111111))
            .border(1.dp, Color(0xFF363636), shape)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(
            if (showHint) "БЫСТРЫЙ ДОСТУП" else "НАЙДЕННЫЕ ПРИЛОЖЕНИЯ",
            fontSize = 9.sp,
            letterSpacing = 2.6.sp,
            color = AX_DARK,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(start = 7.dp, bottom = 9.dp)
        )
        if (specialResult != null) {
            Text(specialResult, color = AX_WHITE, fontSize = 15.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 15.dp))
        } else if (results.isEmpty()) {
            Text("Нет совпадений", color = AX_GRAY, fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 14.dp))
        } else {
            val scrollHeight = ((results.size * 53).dp)
                .coerceAtMost((maxHeight - 37.dp).coerceAtLeast(60.dp))
            LazyColumn(Modifier.fillMaxWidth().height(scrollHeight)) {
                items(results, key = { "${it.packageName}/${it.className}" }) { app ->
                    AxiomAppRow(app, favorites, onOpen, onStar)
                }
            }
        }
        if (specialResult == null) Box(
            Modifier.fillMaxWidth().padding(top = 7.dp)
                .clip(RoundedCornerShape(13.dp))
                .clickable(onClick = onAtlas)
                .padding(horizontal = 7.dp, vertical = 11.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Все приложения", color = AX_GRAY, fontSize = 13.sp)
                Text("↘", color = AX_GRAY, fontSize = 16.sp)
            }
        }
    }
}

@Composable
internal fun AxiomAppRow(
    app: InstalledApp,
    favorites: Set<String>,
    onOpen: (InstalledApp) -> Unit,
    onStar: (String) -> Unit
) {
    var appeared by remember(app.packageName, app.className) { mutableStateOf(false) }
    LaunchedEffect(app.packageName, app.className) { appeared = true }
    val opacity by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(250),
        label = "app row materialize"
    )
    Row(
        Modifier.fillMaxWidth().height(53.dp).graphicsLayer { alpha = opacity },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(32.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF252525))
                .border(1.dp, Color(0xFF3A3A3A), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                app.label.firstOrNull()?.uppercase() ?: "·",
                color = AX_WHITE, fontSize = 15.sp,
                fontWeight = FontWeight.Light
            )
        }
        Text(
            app.label,
            color = AX_WHITE,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
                .clickable { onOpen(app) }
                .padding(start = 14.dp, top = 16.dp, bottom = 16.dp)
        )
        Text(
            if (app.packageName in favorites) "★" else "☆",
            color = if (app.packageName in favorites) AX_WHITE else AX_DARK,
            fontSize = 19.sp,
            modifier = Modifier.clickable { onStar(app.packageName) }
                .padding(horizontal = 10.dp, vertical = 11.dp)
        )
    }
}

@Composable
internal fun LegacyAxiomAtlas(
    apps: List<InstalledApp>,
    favorites: Set<String>,
    query: String,
    onQuery: (String) -> Unit,
    filter: String,
    onFilter: (String) -> Unit,
    onBack: () -> Unit,
    onOpen: (InstalledApp) -> Unit,
    onStar: (String) -> Unit
) {
    val filtered = remember(apps, query, favorites, filter) {
        val matched = if (query.isBlank()) apps else searchApps(apps, query, favorites)
        when (filter) {
            "Избранное" -> matched.filter { it.packageName in favorites }
            "Системные" -> matched.filter { it.system }
            "Сторонние" -> matched.filterNot { it.system }
            else -> matched
        }
    }
    val index = remember(filtered) {
        val map = linkedMapOf<String, Int>()
        filtered.forEachIndexed { i, app ->
            val letter = app.label.firstOrNull()?.uppercaseChar()?.toString() ?: "#"
            if (!map.containsKey(letter)) map[letter] = i
        }
        map
    }
    val railLetters = remember(index) {
        val letters = index.keys.toList()
        if (letters.size <= 21) letters
        else List(21) { i -> letters[(i * (letters.size - 1)) / 20] }.distinct()
    }
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var lastScrubbed by remember { mutableStateOf("") }
    var scrubDestination by remember { androidx.compose.runtime.mutableIntStateOf(-1) }
    // Coalesce alphabet scrubbing into one target per composition instead of
    // launching dozens of simultaneous scroll coroutines per pointer sample.
    LaunchedEffect(scrubDestination) {
        if (scrubDestination >= 0) state.scrollToItem(scrubDestination)
    }
    val selectedLetter = filtered.getOrNull(state.firstVisibleItemIndex)
        ?.label?.firstOrNull()?.uppercaseChar()?.toString() ?: "•"

    LaunchedEffect(query, filter) {
        if (filtered.isNotEmpty()) state.scrollToItem(0)
    }

    Box(Modifier.fillMaxSize().background(AX_PANEL)) {
        Column(Modifier.fillMaxSize().padding(start = 24.dp, end = 15.dp, top = 22.dp)) {
            var swipeAmount by remember { mutableStateOf(0f) }
            Row(
                Modifier.fillMaxWidth().pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { swipeAmount = 0f },
                        onVerticalDrag = { change, dy ->
                            swipeAmount += dy
                            change.consume()
                        },
                        onDragEnd = { if (swipeAmount < -55f) onBack() }
                    )
                },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("AXIOM / INDEX", color = AX_GRAY, fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace, letterSpacing = 2.5.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Приложения", color = AX_WHITE, fontSize = 30.sp,
                        fontWeight = FontWeight.Light, letterSpacing = -.4.sp)
                }
                Text("×", color = AX_WHITE, fontSize = 30.sp,
                    modifier = Modifier.clickable(onClick = onBack)
                        .padding(horizontal = 12.dp, vertical = 6.dp))
            }
            Spacer(Modifier.height(20.dp))
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                cursorBrush = SolidColor(AX_WHITE),
                textStyle = TextStyle(color = AX_WHITE, fontSize = 15.sp),
                modifier = Modifier.fillMaxWidth()
                    .padding(end = 8.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF181818))
                    .border(1.dp, Color(0xFF414141), RoundedCornerShape(18.dp))
                    .padding(horizontal = 17.dp, vertical = 15.dp),
                decorationBox = { child ->
                    Box {
                        if (query.isEmpty()) Text("Найти приложение...",
                            color = AX_GRAY, fontSize = 15.sp)
                        child()
                    }
                }
            )
            Spacer(Modifier.height(15.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(end = 6.dp)) {
                items(listOf("Все", "Избранное", "Системные", "Сторонние")) { c ->
                    AxiomChip(c, selected = filter == c) { onFilter(c) }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().weight(1f)) {
                if (filtered.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Нет приложений", color = AX_GRAY, fontSize = 15.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        state = state,
                        contentPadding = PaddingValues(top = 16.dp, bottom = 70.dp)
                    ) {
                        itemsIndexed(filtered,
                            key = { _, app -> "${app.packageName}/${app.className}" }
                        ) { appIndex, app ->
                            val letter = app.label.firstOrNull()?.uppercaseChar()?.toString() ?: "#"
                            val first = index[letter] == appIndex
                            Column {
                                if (first) {
                                    Text(letter, color = AX_DARK, fontSize = 11.sp,
                                        letterSpacing = 2.sp, fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(top = 16.dp, bottom = 9.dp))
                                }
                                // No per-row reads of LazyListState.layoutInfo:
                                // those were causing expensive work every scroll frame.
                                Row(
                                    Modifier.fillMaxWidth()
                                        .height(76.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        app.label, color = AX_WHITE,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        fontSize = 25.sp, fontWeight = FontWeight.Light,
                                        letterSpacing = -.35.sp,
                                        modifier = Modifier.weight(1f)
                                            .clickable { onOpen(app) }
                                            .padding(vertical = 19.dp)
                                    )
                                    Text(
                                        if (app.packageName in favorites) "★" else "·",
                                        color = if (app.packageName in favorites)
                                            AX_WHITE else AX_DARK,
                                        fontSize = 20.sp,
                                        modifier = Modifier.clickable {
                                            onStar(app.packageName)
                                        }.padding(horizontal = 12.dp, vertical = 14.dp)
                                    )
                                }
                            }
                        }
                    }
                    // Side alphabet: Niagara-inspired fast index, no icon grid.
                    Column(
                        modifier = Modifier.width(33.dp).fillMaxHeight()
                            .pointerInput(railLetters, index) {
                                detectDragGestures(
                                    onDragStart = { pos ->
                                        val i = ((pos.y / size.height.toFloat()) *
                                            railLetters.size).toInt()
                                            .coerceIn(0, railLetters.lastIndex)
                                        val ch = railLetters[i]
                                        if (ch != lastScrubbed) {
                                            lastScrubbed = ch
                                            index[ch]?.let { dest ->
                                                scrubDestination = dest
                                            }
                                        }
                                    },
                                    onDrag = { change, _ ->
                                        val i = ((change.position.y /
                                            size.height.toFloat()) * railLetters.size).toInt()
                                            .coerceIn(0, railLetters.lastIndex)
                                        val ch = railLetters[i]
                                        if (ch != lastScrubbed) {
                                            lastScrubbed = ch
                                            index[ch]?.let { dest ->
                                                scrubDestination = dest
                                            }
                                        }
                                        change.consume()
                                    }
                                )
                            },
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        railLetters.forEach { letter ->
                            Text(
                                letter, color = if (letter == selectedLetter)
                                    AX_WHITE else AX_DARK,
                                fontSize = 10.sp,
                                fontWeight = if (letter == selectedLetter)
                                    FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.clickable {
                                    index[letter]?.let { dest ->
                                        scrubDestination = dest
                                    }
                                }.padding(vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AxiomChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(13.dp)
    Box(
        Modifier.clip(shape)
            .background(if (selected) Color(0xFF363636) else Color(0xFF161616))
            .border(1.dp, if (selected) Color(0xFF777777) else Color(0xFF333333), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 10.dp)
    ) {
        Text(label, color = if (selected) AX_WHITE else AX_GRAY,
            fontSize = 12.sp)
    }
}

@Composable
internal fun AxiomSettings(
    gravity: Float,
    onGravity: (Float) -> Unit,
    onResetUniverse: () -> Unit,
    onMath: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onProfiler: () -> Unit,
    profilerEnabled: Boolean,
    profilerSummary: String,
    onSurprise: () -> Unit,
    currentEpisode: String,
    onTerminal: () -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().background(AX_PANEL)
            .verticalScroll(rememberScrollState())
            .padding(start = 26.dp, end = 26.dp, top = 27.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text("AXIOM / CONTROL", color = AX_WHITE, fontSize = 13.sp,
                letterSpacing = 2.sp, fontFamily = FontFamily.Monospace)
            Text("×", color = AX_WHITE, fontSize = 28.sp,
                modifier = Modifier.clickable(onClick = onBack).padding(horizontal = 8.dp))
        }
        Spacer(Modifier.height(46.dp))
        Text("Гравитация", color = AX_WHITE, fontSize = 23.sp,
            fontWeight = FontWeight.Light)
        Spacer(Modifier.height(8.dp))
        Text("Глубокое пространство со случайными событиями:\n" +
            "кометы, математические волны, ML-структуры и редкие аномалии.",
            fontSize = 13.sp, color = AX_GRAY, lineHeight = 20.sp)
        Spacer(Modifier.height(18.dp))
        Slider(
            value = gravity, onValueChange = onGravity,
            valueRange = .25f..2.2f,
            colors = SliderDefaults.colors(
                thumbColor = AX_WHITE,
                activeTrackColor = AX_WHITE,
                inactiveTrackColor = Color(0xFF3D3D3D)
            )
        )
        Text("G = ${"%.2f".format(java.util.Locale.US, gravity)}",
            fontFamily = FontFamily.Monospace, color = AX_GRAY, fontSize = 12.sp)
        Spacer(Modifier.height(22.dp))
        Text("Событие: ${currentEpisode.lowercase().replace('_', ' ')}",
            color = AX_GRAY, fontSize = 12.sp,
            fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(13.dp))
        Text("✧  Случайное космическое событие",
            color = AX_WHITE, fontSize = 15.sp,
            modifier = Modifier.fillMaxWidth()
                .clickable(onClick = onSurprise).padding(vertical = 15.dp))
        Spacer(Modifier.height(12.dp))
        Text("Прикосновение создаёт волну, двойное нажатие — комету,\n" +
            "удерживание вызывает событие, горизонтальный жест меняет глубину.",
            color = AX_GRAY, fontSize = 12.sp, lineHeight = 19.sp)
        Spacer(Modifier.height(20.dp))
        Text("ИНСТРУМЕНТЫ", color = AX_DARK, fontSize = 10.sp,
            letterSpacing = 3.sp, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(17.dp))
        Row(Modifier.fillMaxWidth().clickable(onClick = onTerminal)
            .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text("⌁  Командная консоль", color = AX_WHITE, fontSize = 17.sp)
            Text("↗", color = AX_GRAY, fontSize = 18.sp)
        }
        HorizontalDivider(color = Color(0xFF292929))
        Text("↺  Перезапустить вселенную", color = AX_WHITE, fontSize = 15.sp,
            modifier = Modifier.fillMaxWidth()
                .clickable(onClick = onResetUniverse).padding(vertical = 19.dp))
        HorizontalDivider(color = Color(0xFF292929))
        Text("⟳  Обновить список приложений", color = AX_WHITE,
            fontSize = 15.sp,
            modifier = Modifier.fillMaxWidth()
                .clickable(onClick = onRefresh).padding(vertical = 19.dp))
        HorizontalDivider(color = Color(0xFF292929))
        Text("∑  Математическая лаборатория", color = AX_WHITE,
            fontSize = 15.sp, modifier = Modifier.fillMaxWidth()
                .clickable(onClick = onMath).padding(vertical = 17.dp))
        HorizontalDivider(color = Color(0xFF292929))
        Text("⇧  Экспортировать настройки", color = AX_WHITE,
            fontSize = 15.sp, modifier = Modifier.fillMaxWidth()
                .clickable(onClick = onExport).padding(vertical = 17.dp))
        HorizontalDivider(color = Color(0xFF292929))
        Text("⇩  Восстановить настройки", color = AX_WHITE,
            fontSize = 15.sp, modifier = Modifier.fillMaxWidth()
                .clickable(onClick = onImport).padding(vertical = 17.dp))
        HorizontalDivider(color = Color(0xFF292929))
        Text(
            if (profilerEnabled) "◉  Остановить измерение кадров"
            else "◎  Измерять плавность списка",
            color = AX_WHITE, fontSize = 15.sp,
            modifier = Modifier.fillMaxWidth()
                .clickable(onClick = onProfiler).padding(vertical = 17.dp)
        )
        if (profilerEnabled) {
            Text(profilerSummary, color = AX_GRAY, fontSize = 12.sp,
                fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.height(36.dp))
        Text("AXIOM 0.7  /  FLUID · CONSEQUENCE · INTELLIGENCE", color = AX_DARK,
            fontFamily = FontFamily.Monospace, fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 24.dp))
    }
}
