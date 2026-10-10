package dev.axiom.launcher

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.floor

/** Immutable, pre-grouped indexed list; one content type per reusable row family. */
internal data class AtlasEntry(
    val key: String,
    val letter: String,
    val app: InstalledApp? = null
)

internal fun makeAtlasEntries(apps: List<InstalledApp>): List<AtlasEntry> {
    val result = ArrayList<AtlasEntry>(apps.size + 40)
    var previous = ""
    for (app in apps) {
        val letter = app.label.firstOrNull()?.uppercaseChar()?.toString() ?: "#"
        if (letter != previous) {
            result += AtlasEntry("heading:$letter:${result.size}", letter)
            previous = letter
        }
        result += AtlasEntry("app:${app.packageName}/${app.className}", letter, app)
    }
    return result
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
    val context = LocalContext.current
    val recentPackages = remember(apps) { AppRecents.list(context) }
    val filtered = remember(apps, query, favorites, filter, recentPackages) {
        val found = if (query.isBlank()) apps else searchApps(apps, query, favorites)
        if (filter == "Недавние") {
            val rank = recentPackages.withIndex().associate { it.value to it.index }
            found.filter { it.packageName in rank }
                .sortedBy { rank[it.packageName] ?: Int.MAX_VALUE }
        } else {
            val chosen = when (filter) {
                "Избранное" -> found.filter { it.packageName in favorites }
                "Системные" -> found.filter { it.system }
                "Сторонние" -> found.filterNot { it.system }
                else -> found
            }
            chosen.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
        }
    }
    val entries = remember(filtered) { makeAtlasEntries(filtered) }
    val positions = remember(entries) {
        entries.withIndex().filter { it.value.app == null }
            .associate { it.value.letter to it.index }
    }
    val letters = remember(positions) { positions.keys.toList() }
    val listState = rememberLazyListState()

    LaunchedEffect(query, filter) {
        if (entries.isNotEmpty()) listState.scrollToItem(0)
    }

    var details by remember { mutableStateOf<InstalledApp?>(null) }
    Column(Modifier.fillMaxSize().background(Color(0xFF050505))
        .padding(start = 24.dp, end = 11.dp, top = 15.dp)) {
        var headerDrag by remember { mutableStateOf(0f) }
        Row(
            Modifier.fillMaxWidth().pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { headerDrag = 0f },
                    onVerticalDrag = { change, amount ->
                        headerDrag += amount
                        change.consume()
                    },
                    onDragEnd = { if (headerDrag < -60f) onBack() }
                )
            },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("AXIOM / FLUID INDEX", color = AX_DARK, fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
                Spacer(Modifier.height(6.dp))
                Text("Приложения", color = AX_WHITE, fontSize = 31.sp,
                    fontWeight = FontWeight.Light)
            }
            Text("×", color = AX_WHITE, fontSize = 28.sp,
                modifier = Modifier.clickable(onClick = onBack)
                    .padding(start = 18.dp, end = 15.dp, top = 7.dp, bottom = 7.dp))
        }
        Spacer(Modifier.height(14.dp))
        val searchShape = RoundedCornerShape(16.dp)
        BasicTextField(
            value = query, onValueChange = onQuery, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(end = 11.dp)
                .clip(searchShape).background(Color(0xFF171717))
                .border(1.dp, Color(0xFF383838), searchShape)
                .padding(horizontal = 16.dp, vertical = 15.dp),
            cursorBrush = SolidColor(AX_WHITE),
            textStyle = TextStyle(color = AX_WHITE, fontSize = 15.sp),
            decorationBox = { content ->
                Box {
                    if (query.isEmpty()) Text("Начни вводить или выбери букву",
                        color = AX_GRAY, fontSize = 14.sp)
                    content()
                }
            }
        )
        Spacer(Modifier.height(10.dp))
        LazyRow(
            contentPadding = PaddingValues(end = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(listOf("Все", "Недавние", "Избранное", "Системные", "Сторонние"),
                key = { it }, contentType = { "filter" }) { name ->
                val selected = filter == name
                val chip = RoundedCornerShape(11.dp)
                Text(name,
                    color = if (selected) AX_WHITE else AX_GRAY,
                    fontSize = 11.sp,
                    modifier = Modifier.clip(chip)
                        .background(if (selected) Color(0xFF353535) else Color(0xFF141414))
                        .border(1.dp,
                            if (selected) Color(0xFF666666) else Color(0xFF292929), chip)
                        .clickable { onFilter(name) }
                        .padding(horizontal = 13.dp, vertical = 10.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().weight(1f)) {
            if (entries.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxHeight(),
                    contentAlignment = Alignment.Center) {
                    Text("Совпадений нет", color = AX_GRAY)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 64.dp)
                ) {
                    items(entries, key = { it.key },
                        contentType = { if (it.app == null) "letter" else "application" }
                    ) { entry ->
                        if (entry.app == null) {
                            Text(entry.letter, color = AX_DARK,
                                fontSize = 11.sp, letterSpacing = 2.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.fillMaxWidth()
                                    .height(41.dp).padding(top = 16.dp))
                        } else {
                            IndexedAppRow(
                                app = entry.app, favorite = entry.app.packageName in favorites,
                                onOpen = onOpen, onStar = onStar,
                                onDetails = { details = entry.app }
                            )
                        }
                    }
                }
                if (letters.isNotEmpty() && filter != "Недавние") {
                    FluidAlphabetRail(
                        letters = letters, positions = positions,
                        entries = entries, state = listState,
                        modifier = Modifier.width(37.dp).fillMaxHeight()
                    )
                }
            }
        }
    }

    details?.let { app ->
        AlertDialog(
            onDismissRequest = { details = null },
            containerColor = Color(0xFF161616),
            title = { Text(app.label, color = AX_WHITE) },
            text = { Text("Открыть приложение или перейти к сведениям Android.",
                color = AX_GRAY) },
            confirmButton = {
                TextButton(onClick = { details = null; onOpen(app) }) {
                    Text("Открыть", color = AX_WHITE)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    details = null
                    runCatching {
                        context.startActivity(Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${app.packageName}")
                        ))
                    }
                }) { Text("Сведения", color = AX_GRAY) }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IndexedAppRow(
    app: InstalledApp,
    favorite: Boolean,
    onOpen: (InstalledApp) -> Unit,
    onStar: (String) -> Unit,
    onDetails: () -> Unit
) {
    Row(Modifier.fillMaxWidth().height(70.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = AX_WHITE, fontSize = 24.sp, fontWeight = FontWeight.Light,
            modifier = Modifier.weight(1f)
                .combinedClickable(
                    onClick = { onOpen(app) },
                    onLongClick = onDetails
                ).padding(vertical = 18.dp))
        Text(if (favorite) "★" else "·",
            color = if (favorite) AX_WHITE else AX_DARK, fontSize = 20.sp,
            modifier = Modifier.clickable { onStar(app.packageName) }
                .padding(start = 5.dp, end = 4.dp, top = 17.dp, bottom = 17.dp))
    }
}

/** Keeps scroll-position observation isolated from the large list subtree. */
@Composable
private fun FluidAlphabetRail(
    letters: List<String>,
    positions: Map<String, Int>,
    entries: List<AtlasEntry>,
    state: LazyListState,
    modifier: Modifier = Modifier
) {
    var targetIndex by remember { mutableIntStateOf(-1) }
    var dragLetter by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(targetIndex) {
        if (targetIndex >= 0 && targetIndex < entries.size) state.scrollToItem(targetIndex)
    }
    val activeLetter by remember(entries, state) {
        derivedStateOf {
            entries.getOrNull(state.firstVisibleItemIndex)?.letter ?: letters.first()
        }
    }
    fun navigate(y: Float, x: Float, height: Float, width: Float) {
        val k = floor(y / height.coerceAtLeast(1f) * letters.size)
            .toInt().coerceIn(0, letters.lastIndex)
        val key = letters[k]
        dragLetter = key
        val start = positions[key] ?: 0
        val end = if (k < letters.lastIndex) (positions[letters[k+1]] ?: entries.size)
                  else entries.size
        // After moving left of the alphabet rail, navigate within the group.
        val fine = (-x / width.coerceAtLeast(1f)).coerceIn(0f, 1f)
        targetIndex = (start + (fine * (end - start - 1).coerceAtLeast(0)).toInt())
            .coerceIn(0, entries.lastIndex)
    }
    Box(modifier) {
        Column(Modifier.fillMaxSize().pointerInput(letters, positions) {
            detectDragGestures(
                onDragStart = { down ->
                    navigate(down.y, down.x, size.height.toFloat(), size.width.toFloat())
                },
                onDragEnd = { dragLetter = null },
                onDragCancel = { dragLetter = null }
            ) { change, _ ->
                navigate(change.position.y, change.position.x,
                    size.height.toFloat(), size.width.toFloat())
                change.consume()
            }
        }, horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            letters.forEach { letter ->
                Text(letter,
                    color = if (letter == (dragLetter ?: activeLetter)) AX_WHITE else AX_DARK,
                    fontSize = 10.sp,
                    fontWeight = if (letter == (dragLetter ?: activeLetter))
                        FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.fillMaxWidth().clickable {
                        targetIndex = positions[letter] ?: 0
                    }.padding(vertical = 3.dp),
                    maxLines = 1)
            }
        }
    }
}
