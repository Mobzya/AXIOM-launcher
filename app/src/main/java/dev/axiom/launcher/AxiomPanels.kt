package dev.axiom.launcher

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
        if (results.isEmpty()) {
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
        Box(
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
internal fun AxiomAtlas(
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
    val state = rememberLazyListState()
    LaunchedEffect(filter, query) {
        if (filtered.isNotEmpty()) state.scrollToItem(0)
    }
    Column(
        Modifier.fillMaxSize().background(AX_PANEL)
            .padding(start = 23.dp, end = 23.dp, top = 18.dp)
    ) {
        var upwards by remember { mutableStateOf(0f) }
        Row(
            Modifier.fillMaxWidth()
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { upwards = 0f },
                        onVerticalDrag = { change, amount ->
                            upwards += amount
                            change.consume()
                        },
                        onDragEnd = { if (upwards < -53f) onBack() }
                    )
                }
                .padding(vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("APP ATLAS", fontSize = 13.sp, letterSpacing = 3.sp,
                    fontFamily = FontFamily.Monospace, color = AX_WHITE)
                Spacer(Modifier.height(6.dp))
                Text("${filtered.size} приложений  /  свайп вверх для закрытия",
                    fontSize = 11.sp, color = AX_GRAY)
            }
            Text("×", color = AX_WHITE, fontSize = 27.sp,
                modifier = Modifier.clickable(onClick = onBack)
                    .padding(horizontal = 10.dp))
        }
        Spacer(Modifier.height(20.dp))
        BasicTextField(
            value = query, onValueChange = onQuery, singleLine = true,
            cursorBrush = SolidColor(AX_WHITE),
            textStyle = TextStyle(color = AX_WHITE, fontSize = 16.sp),
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(17.dp))
                .background(Color(0xFF171717))
                .border(1.dp, Color(0xFF3E3E3E), RoundedCornerShape(17.dp))
                .padding(horizontal = 17.dp, vertical = 16.dp),
            decorationBox = { child ->
                Box {
                    if (query.isEmpty()) Text("Фильтр по названию", color = AX_GRAY,
                        fontSize = 15.sp)
                    child()
                }
            }
        )
        Spacer(Modifier.height(17.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            items(listOf("Все", "Избранное", "Системные", "Сторонние")) { c ->
                AxiomChip(c, selected = filter == c) { onFilter(c) }
            }
        }
        Spacer(Modifier.height(20.dp))
        if (filtered.isEmpty()) {
            Text("Здесь пока пусто", color = AX_GRAY, fontSize = 14.sp,
                modifier = Modifier.padding(vertical = 20.dp))
        } else {
            LazyColumn(Modifier.fillMaxWidth().weight(1f), state = state) {
                items(filtered, key = { "${it.packageName}/${it.className}" }) { app ->
                    AxiomAppRow(app, favorites, onOpen, onStar)
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
    ambientLevel: Int,
    onAmbientLevel: (Int) -> Unit,
    scenePreset: String,
    onScenePreset: (String) -> Unit,
    entropy: Boolean,
    onEntropy: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().background(AX_PANEL)
            .padding(start = 24.dp, end = 24.dp, top = 27.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("AXIOM / STUDIO", color = AX_WHITE,
                fontSize = 13.sp, letterSpacing = 2.5.sp,
                fontFamily = FontFamily.Monospace)
            Text("×", color = AX_WHITE, fontSize = 28.sp,
                modifier = Modifier.clickable(onClick = onBack)
                    .padding(horizontal = 8.dp))
        }
        Spacer(Modifier.height(38.dp))
        Text("ФИЗИКА", color = AX_DARK, fontSize = 10.sp,
            letterSpacing = 2.8.sp, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(12.dp))
        Text("Интенсивность движения", color = AX_WHITE, fontSize = 17.sp)
        Spacer(Modifier.height(8.dp))
        Text("Интерфейс движется по пружинным законам;\n"
                + "космическая сцена живёт отдельно от элементов управления.",
            color = AX_GRAY, fontSize = 13.sp, lineHeight = 20.sp)
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Покой", "Тихо", "Живее").forEachIndexed { i, name ->
                AxiomChip(name, ambientLevel == i) { onAmbientLevel(i) }
            }
        }
        Spacer(Modifier.height(34.dp))
        Text("КОСМИЧЕСКАЯ СРЕДА", color = AX_DARK, fontSize = 10.sp,
            letterSpacing = 2.8.sp, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(13.dp))
        val scenes = listOf(
            "AUTO" to "Авто",
            "ORBIT" to "Орбиты",
            "ATTRACTOR" to "Аттрактор",
            "NEURAL" to "Нейросеть",
            "WAVE" to "Волны"
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(scenes) { (id, title) ->
                AxiomChip(title, scenePreset == id) { onScenePreset(id) }
            }
        }
        Spacer(Modifier.height(23.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Контролируемая случайность",
                    color = AX_WHITE, fontSize = 15.sp)
                Spacer(Modifier.height(6.dp))
                Text("Лёгкий дрейф объектов, без скачков\nили перестановки приложений",
                    color = AX_GRAY, fontSize = 12.sp, lineHeight = 18.sp)
            }
            Switch(
                checked = entropy, onCheckedChange = onEntropy,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.Black,
                    checkedTrackColor = Color.White,
                    uncheckedThumbColor = Color(0xFFAAAAAA),
                    uncheckedTrackColor = Color(0xFF333333),
                    uncheckedBorderColor = Color(0xFF666666)
                )
            )
        }
        Spacer(Modifier.height(40.dp))
        Text("БИБЛИОТЕКА", color = AX_DARK, fontSize = 10.sp,
            letterSpacing = 2.8.sp, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(13.dp))
        Text("Обновить список приложений ↗",
            color = AX_WHITE, fontSize = 15.sp,
            modifier = Modifier.clickable(onClick = onRefresh)
                .padding(vertical = 14.dp))
        Spacer(Modifier.weight(1f))
        Text("AXIOM 0.3 / GRAVITY", color = AX_DARK, fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 28.dp))
    }
}
