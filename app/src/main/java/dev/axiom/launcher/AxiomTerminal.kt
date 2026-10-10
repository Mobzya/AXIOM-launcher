package dev.axiom.launcher

import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

private data class TerminalLine(val value: String, val command: Boolean = false)

@Composable
internal fun AxiomTerminal(
    initial: String,
    apps: List<InstalledApp>,
    gravity: Float,
    bodyCount: Int,
    currentEpisode: String,
    onEvent: (CosmicEventKind) -> Unit,
    onSurprise: () -> Unit,
    onDepth: (Float) -> Unit,
    onGravity: (Float) -> Unit,
    onReset: () -> Unit,
    onSpawn: () -> Unit,
    onLaunch: (InstalledApp) -> Unit,
    onOpenAtlas: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("axiom_terminal", Context.MODE_PRIVATE) }
    var command by remember { mutableStateOf("") }
    var lines by remember {
        mutableStateOf(listOf(
            TerminalLine("AXIOM CONSOLE   /   LOCAL MODE"),
            TerminalLine("Используй help для списка команд. Shell Android не запускается.")
        ))
    }
    val state = rememberLazyListState()
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    fun append(text: String) {
        lines = (lines + TerminalLine(text)).takeLast(46)
    }
    fun execute(input: String) {
        val raw = input.trim().removePrefix(">").trim()
        if (raw.isBlank()) return
        lines = (lines + TerminalLine("axiom $  $raw", command = true)).takeLast(46)
        when (val parsed = parseAxCommand(raw)) {
            is AxCommand.Output -> append(parsed.value)
            is AxCommand.Open -> {
                val matches = searchApps(apps, parsed.name, emptySet())
                if (matches.isEmpty()) append("Приложение не найдено: ${parsed.name}")
                else {
                    append("Открываю ${matches.first().label}...")
                    onLaunch(matches.first())
                }
            }
            is AxCommand.Find -> {
                val matches = searchApps(apps, parsed.name, emptySet()).take(9)
                append(if (matches.isEmpty()) "Совпадений нет"
                    else matches.joinToString("\n") { " ↳ ${it.label}" })
            }
            is AxCommand.Event -> {
                if (parsed.kind == null) onSurprise() else onEvent(parsed.kind)
                append("Сценарий: ${parsed.kind?.name ?: "случайный"}  ✓")
            }
            is AxCommand.Depth -> {
                onDepth(parsed.delta)
                append("Сдвиг перспективы: ${parsed.delta} ✓")
            }
            is AxCommand.Gravity -> {
                onGravity(parsed.value)
                append("G = ${"%.2f".format(Locale.US, parsed.value)}  ✓")
            }
            is AxCommand.Calculate -> {
                val result = runCatching { arithmetic(parsed.expression) }
                append(result.fold(
                    onSuccess = { "= ${"%.7g".format(Locale.US, it)}" },
                    onFailure = { "Ошибка вычисления: ${it.message ?: "синтаксис"}" }
                ))
            }
            is AxCommand.Note -> {
                val old = prefs.getString("notes", "") ?: ""
                prefs.edit().putString("notes",
                    (old.lines().filter { it.isNotBlank() } + parsed.value)
                        .takeLast(10).joinToString("\n")).apply()
                append("Заметка сохранена локально ✓")
            }
            AxCommand.Notes -> append(
                (prefs.getString("notes", "") ?: "").ifBlank { "Заметок пока нет" }
            )
            AxCommand.Clear -> lines = emptyList()
            AxCommand.Home -> onBack()
            AxCommand.Apps -> onOpenAtlas()
            AxCommand.Physics -> append(
                "UNIVERSE / LIVE\nТела: $bodyCount\nГравитация: " +
                "${"%.2f".format(Locale.US, gravity)}\n" +
                "Событие: $currentEpisode\\n" +
                "Глубинные слои · 60 Гц · мягкая гравитация"
            )
            AxCommand.Spawn -> {
                onSpawn()
                append("Новый объект добавлен в физическую среду ✓")
            }
            AxCommand.Reset -> {
                onReset()
                append("Вселенная перезапущена ✓")
            }
            AxCommand.Clock -> append(
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            )
            AxCommand.Battery -> {
                val battery = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                val amount = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                append(if (amount in 0..100) "Батарея: $amount%" else "Данные батареи недоступны")
            }
            is AxCommand.AndroidSettings -> {
                val action = when (parsed.page) {
                    "wifi" -> Settings.ACTION_WIFI_SETTINGS
                    "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
                    "display" -> Settings.ACTION_DISPLAY_SETTINGS
                    "notifications" -> Settings.ACTION_APP_NOTIFICATION_SETTINGS
                    else -> Settings.ACTION_SETTINGS
                }
                val safeAction = if (action == Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    Settings.ACTION_SETTINGS else action
                runCatching {
                    context.startActivity(Intent(safeAction)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }.onFailure { append("Не удалось открыть настройки устройства") }
            }
        }
        command = ""
    }

    LaunchedEffect(Unit) {
        delay(100)
        focus.requestFocus()
        keyboard?.show()
    }
    LaunchedEffect(initial) {
        if (initial.isNotBlank()) execute(initial)
    }
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) state.animateScrollToItem(lines.lastIndex)
    }

    Column(
        Modifier.fillMaxSize()
            .background(Color(0xFC050505))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding()
            .padding(horizontal = 24.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("AXIOM / TERMINAL", color = AX_WHITE,
                fontSize = 13.sp, fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp)
            Text("×", color = AX_WHITE, fontSize = 29.sp,
                modifier = Modifier.clickable(onClick = onBack).padding(horizontal = 8.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text("SIMULATED COMMAND INTERFACE  ·  NO ROOT",
            color = AX_DARK, fontSize = 9.sp,
            fontFamily = FontFamily.Monospace, letterSpacing = 1.sp)
        Spacer(Modifier.height(20.dp))
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f),
            state = state,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            itemsIndexed(lines) { index, line ->
                Text(line.value,
                    color = if (line.command) AX_WHITE else Color(0xFFBBBBBB),
                    fontSize = 12.sp, lineHeight = 20.sp,
                    fontFamily = FontFamily.Monospace)
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("help", "events", "physics", "spawn").forEach { c ->
                Text(
                    c, color = Color(0xFFAFAFAF), fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.clickable { execute(c) }
                        .padding(horizontal = 6.dp, vertical = 9.dp)
                )
            }
        }
        Row(
            Modifier.fillMaxWidth()
                .border(1.dp, Color(0xFF555555), RoundedCornerShape(18.dp))
                .background(Color(0xFF161616), RoundedCornerShape(18.dp))
                .padding(horizontal = 17.dp, vertical = 17.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("$", color = AX_WHITE, fontSize = 17.sp,
                fontFamily = FontFamily.Monospace)
            Spacer(Modifier.padding(horizontal = 7.dp))
            BasicTextField(
                value = command,
                onValueChange = { command = it.take(180) },
                singleLine = true,
                modifier = Modifier.weight(1f).focusRequester(focus),
                cursorBrush = SolidColor(AX_WHITE),
                textStyle = TextStyle(
                    fontSize = 15.sp, fontFamily = FontFamily.Monospace,
                    color = AX_WHITE
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { execute(command) }),
                decorationBox = { field ->
                    Box {
                        if (command.isEmpty()) Text("Команда...", color = AX_GRAY,
                            fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                        field()
                    }
                }
            )
            Text("↵", color = AX_WHITE, fontSize = 22.sp,
                modifier = Modifier.clickable { execute(command) }
                    .padding(start = 10.dp))
        }
        Spacer(Modifier.height(17.dp))
    }
}
