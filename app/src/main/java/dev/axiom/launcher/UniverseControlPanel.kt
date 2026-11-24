package dev.axiom.launcher

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

private enum class ObservatoryTab(val title: String) {
    SCENES("Сцены"), MATTER("Материя"), EVENTS("События")
}

private val observatoryAccent: Color
    get() = if (AxiomColors.palette.light) Color(0xff236680) else Color(0xffa0def1)

@Composable
internal fun UniverseControlPanel(
    grainCount: Int,
    maxGrains: Int,
    preset: CosmicPreset,
    frequency: EventFrequency,
    activeEvent: CosmicEventKind,
    speed: Float,
    onPreset: (CosmicPreset) -> Unit,
    onFrequency: (EventFrequency) -> Unit,
    onSpawn: suspend (ObservatoryObject, Float) -> Boolean,
    onPerturb: (Float) -> Unit,
    onEvent: (SpaceEvent?, CosmicEventIntensity) -> Unit,
    onTime: (Float) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var tab by remember { mutableStateOf(ObservatoryTab.SCENES) }
    var objectType by remember { mutableStateOf(ObservatoryObject.ROCKY) }
    var mass by remember { mutableFloatStateOf(objectType.mass) }
    var impulse by remember { mutableFloatStateOf(.1f) }
    var intensity by remember { mutableStateOf(CosmicEventIntensity.NORMAL) }
    var selectedEvent by remember { mutableStateOf<SpaceEvent?>(SpaceEvent.SHOCK) }
    var lastSpeed by remember { mutableFloatStateOf(if (speed > 0f) speed else 1f) }
    var message by remember { mutableStateOf("") }
    var spawning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val accent = observatoryAccent
    val frame = RoundedCornerShape(27.dp)
    val angle by animateFloatAsState(tab.ordinal * 95f + if (speed == 0f) 0f else 28f,
        spring(dampingRatio = .70f, stiffness = 180f), label = "observatory dial")

    Column(modifier.fillMaxWidth().clip(frame)
        .background(Brush.linearGradient(listOf(
            AX_PANEL.copy(alpha = .985f), AX_SURFACE.copy(alpha = .985f), AX_PANEL.copy(alpha = .985f))))
        .border(1.dp, accent.copy(alpha = .34f), frame)
        .padding(horizontal = 15.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OrbitDial(Modifier.size(37.dp), accent) { angle }
            Column(Modifier.weight(1f)) {
                Text("Обсерватория", color = AX_WHITE, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text("$grainCount / $maxGrains частиц", color = AX_GRAY,
                    fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            }
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(13.dp))
                .clickable(role = Role.Button, onClick = onDismiss)
                .semantics { contentDescription = "Закрыть обсерваторию" },
                contentAlignment = Alignment.Center) {
                Text("×", color = AX_WHITE, fontSize = 25.sp)
            }
        }
        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            for (choice in ObservatoryTab.entries) {
                ControlChoice(choice.title, tab == choice, Modifier.weight(1f)) {
                    tab = choice
                    message = ""
                }
            }
        }
        key(tab) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                when (tab) {
                    ObservatoryTab.SCENES -> {
                        item {
                            val speeds = listOf(.25f to "¼×", .5f to "½×", 1f to "1×", 2f to "2×")
                            Row(Modifier.fillMaxWidth().selectableGroup(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                ControlChoice(if (speed == 0f) "▶" else "Ⅱ", speed == 0f,
                                    Modifier.weight(1f).semantics {
                                        contentDescription = if (speed == 0f) "Продолжить" else "Пауза"
                                    }) {
                                    if (speed > 0f) {
                                        lastSpeed = speed
                                        onTime(0f)
                                    } else onTime(lastSpeed)
                                }
                                speeds.forEach { (value, label) ->
                                    ControlChoice(label, speed == value, Modifier.weight(1f)) {
                                        lastSpeed = value
                                        onTime(value)
                                    }
                                }
                            }
                        }
                        items(CosmicPreset.entries, key = { it.name }) { scene ->
                            ObservatoryRow(scene.title, scene.details, preset == scene) { onPreset(scene) }
                        }
                    }
                    ObservatoryTab.MATTER -> {
                        item {
                            ControlHeading("Масса · ${String.format(Locale.US, "%.2f", mass)}")
                            Slider(mass, { mass = it }, valueRange = .06f..24f,
                                modifier = Modifier.semantics { contentDescription = "Масса объекта" },
                                colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent))
                            Action("Создать · ${objectType.title}", enabled = !spawning) {
                                spawning = true
                                val type = objectType
                                val chosenMass = mass
                                scope.launch {
                                    try {
                                        message = if (onSpawn(type, chosenMass)) "Объект добавлен"
                                            else "Достигнут лимит тел или частиц"
                                    } finally {
                                        spawning = false
                                    }
                                }
                            }
                        }
                        items(ObservatoryObject.entries.chunked(2)) { pair ->
                            Row(Modifier.fillMaxWidth().selectableGroup(),
                                horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                pair.forEach { choice ->
                                    ControlChoice(choice.title, objectType == choice, Modifier.weight(1f)) {
                                        objectType = choice
                                        mass = choice.mass
                                        message = ""
                                    }
                                }
                            }
                        }
                        item {
                            ControlHeading("Сила импульса · ${String.format(Locale.US, "%.2f", impulse)}")
                            Slider(impulse, { impulse = it }, valueRange = .025f.. .28f,
                                modifier = Modifier.semantics { contentDescription = "Сила импульса" },
                                colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent))
                            Action("Импульс в центре") { onPerturb(impulse) }
                        }
                    }
                    ObservatoryTab.EVENTS -> {
                        item {
                            ControlHeading("Автоматические события")
                            Row(Modifier.fillMaxWidth().selectableGroup(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                EventFrequency.entries.forEach { choice ->
                                    ControlChoice(choice.title, choice == frequency, Modifier.weight(1f)) {
                                        onFrequency(choice)
                                    }
                                }
                            }
                            ControlHeading("Мощность · ${intensity.label}")
                            Slider(intensity.ordinal.toFloat(), {
                                intensity = CosmicEventIntensity.entries[kotlin.math.round(it).toInt()]
                            }, valueRange = 0f..3f, steps = 2,
                                modifier = Modifier.semantics { contentDescription = "Мощность события" },
                                colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent))
                            Action("Запустить · ${selectedEvent?.title ?: "Случайное событие"}") {
                                onEvent(selectedEvent, intensity)
                            }
                        }
                        item {
                            ObservatoryRow("Случайное событие", "Тип выберет симуляция",
                                selectedEvent == null) { selectedEvent = null }
                        }
                        items(SpaceEvent.entries, key = { it.name }) { event ->
                            ObservatoryRow(event.title, event.detail, selectedEvent == event) { selectedEvent = event }
                        }
                    }
                }
            }
        }
        val status = when {
            message.isNotEmpty() -> message
            speed == 0f && activeEvent != CosmicEventKind.QUIET -> "Пауза · ${SpaceEvent.titleOf(activeEvent)}"
            speed == 0f -> "Симуляция на паузе"
            activeEvent != CosmicEventKind.QUIET -> SpaceEvent.titleOf(activeEvent)
            else -> preset.title
        }
        Text(status, color = accent, fontFamily = FontFamily.Monospace, fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 3.dp, vertical = 2.dp))
    }
}

@Composable
internal fun OrbitDial(modifier: Modifier, ink: Color, angle: () -> Float) {
    Canvas(modifier) {
        val line = 1.dp.toPx()
        val unit = size.minDimension
        rotate(-25f) {
            drawOval(ink, Offset(center.x - unit * .44f, center.y - unit * .24f),
                Size(unit * .88f, unit * .48f), alpha = .48f, style = Stroke(line))
            val theta = angle() * (Math.PI.toFloat() / 180f)
            drawCircle(ink, unit * .07f,
                center + Offset(cos(theta) * unit * .44f, sin(theta) * unit * .24f))
        }
        rotate(29f) {
            drawOval(ink, Offset(center.x - unit * .35f, center.y - unit * .25f),
                Size(unit * .70f, unit * .50f), alpha = .32f, style = Stroke(line))
        }
        drawCircle(ink, unit * .12f, alpha = .22f)
        drawCircle(ink, unit * .065f)
    }
}

@Composable
private fun ControlHeading(label: String) {
    Text(label, color = AX_GRAY, fontSize = 12.sp,
        modifier = Modifier.padding(start = 2.dp, top = 7.dp, bottom = 3.dp))
}

@Composable
private fun ControlChoice(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val accent = observatoryAccent
    val shape = RoundedCornerShape(10.dp)
    Box(modifier.heightIn(min = 44.dp).clip(shape)
        .background(if (selected) accent.copy(alpha = .14f) else AX_TILE)
        .border(1.dp, if (selected) accent.copy(alpha = .62f) else AX_BORDER, shape)
        .selectable(selected, role = Role.Tab, onClick = onClick)
        .padding(horizontal = 3.dp, vertical = 5.dp), contentAlignment = Alignment.Center) {
        Text(label, color = if (selected) accent else AX_WHITE, fontSize = 12.sp,
            textAlign = TextAlign.Center)
    }
}

@Composable
private fun ObservatoryRow(title: String, detail: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(11.dp)
    val accent = observatoryAccent
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(shape)
        .background(if (selected) accent.copy(alpha = .10f) else AX_TILE)
        .border(1.dp, if (selected) accent.copy(alpha = .42f) else AX_BORDER, shape)
        .selectable(selected, role = Role.RadioButton, onClick = onClick)
        .padding(horizontal = 11.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = if (selected) accent else AX_WHITE, fontSize = 13.sp,
                fontWeight = FontWeight.Medium)
            Text(detail, color = AX_GRAY, fontSize = 11.sp)
        }
        if (selected) Canvas(Modifier.size(9.dp)) { drawCircle(accent) }
    }
}

@Composable
private fun Action(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val accent = observatoryAccent
    val shape = RoundedCornerShape(11.dp)
    Box(Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(shape)
        .background(accent.copy(alpha = if (enabled) .18f else .08f))
        .border(1.dp, accent.copy(alpha = .46f), shape)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .padding(horizontal = 8.dp, vertical = 5.dp), contentAlignment = Alignment.Center) {
        Text(label, color = accent, fontSize = 13.sp, textAlign = TextAlign.Center,
            fontWeight = FontWeight.Medium)
    }
}
