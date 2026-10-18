package dev.axiom.launcher

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
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
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

@Composable
internal fun SearchSuggestions(
    results: List<InstalledApp>,
    favorites: Set<String>,
    maxHeight: Dp,
    showHint: Boolean,
    specialResult: String? = null,
    onOpen: (InstalledApp) -> Unit,
    onStar: (String) -> Unit,
    onRemove: (InstalledApp) -> Unit,
    onAtlas: () -> Unit
) {
    var details by remember { mutableStateOf<InstalledApp?>(null) }
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier.fillMaxWidth().padding(top = 11.dp)
            .clip(shape)
            .background(AX_SURFACE.copy(alpha = .96f))
            .border(1.dp, AX_TILE, shape)
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
                    AxiomAppRow(app, favorites, onOpen, onStar,
                        onDetails = { details = app })
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
    details?.let { app ->
        val context = androidx.compose.ui.platform.LocalContext.current
        AlertDialog(
            onDismissRequest = { details = null },
            containerColor = AX_SURFACE,
            title = { Text(app.label,color=AX_WHITE) },
            text = {
                Column {
                    Text("Действия с приложением",
                        color=AX_GRAY,fontSize=13.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (canRequestUninstall(app,context.packageName))
                            "⊖  Удалить приложение…"
                        else "ⓘ  Открыть настройки приложения",
                        color=AX_WHITE,
                        modifier=Modifier.fillMaxWidth().clickable {
                            details=null
                            if(canRequestUninstall(app,context.packageName)) onRemove(app)
                            else runCatching {
                                context.startActivity(applicationDetailsIntent(app))
                            }
                        }.padding(vertical=14.dp))
                    Text("Действие подтверждается в Android.",
                        color=AX_GRAY,fontSize=11.sp)
                }
            },
            confirmButton = {
                TextButton(onClick={details=null;onOpen(app)}) {
                    Text("Открыть",color=AX_WHITE)
                }
            },
            dismissButton = {
                TextButton(onClick={details=null}) {Text("Отмена",color=AX_GRAY)}
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AxiomAppRow(
    app: InstalledApp,
    favorites: Set<String>,
    onOpen: (InstalledApp) -> Unit,
    onStar: (String) -> Unit,
    onDetails: () -> Unit
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
        AxiomAppIcon(app, 32.dp)
        Text(
            app.label,
            color = AX_WHITE,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
                .combinedClickable(
                    onClick = { onOpen(app) },
                    onLongClick = onDetails
                )
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
private fun AxiomChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(13.dp)
    Box(
        Modifier.clip(shape)
            .background(if (selected) AX_TILE else AX_SURFACE)
            .border(1.dp, if (selected) AX_DARK else AX_BORDER, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 10.dp)
    ) {
        Text(label, color = if (selected) AX_WHITE else AX_GRAY,
            fontSize = 12.sp)
    }
}

@Composable
internal fun AxiomSettings(
    themeMode: AxiomThemeMode,
    onThemeMode: (AxiomThemeMode) -> Unit,
    sunriseMinutes: Int,
    sunsetMinutes: Int,
    onSunriseMinutes: (Int) -> Unit,
    onSunsetMinutes: (Int) -> Unit,
    maximumBodies: Int,
    onMaximumBodies: (Int) -> Unit,
    matterMode: MatterMode,
    onMatterMode: (MatterMode) -> Unit,
    particleLimit: Int,
    onParticleLimit: (Int) -> Unit,
    onAddParticleBatch: () -> Unit,
    cosmicPreset: CosmicPreset,
    onCosmicPreset: (CosmicPreset) -> Unit,
    particleCount: Int,
    boundParticleCount: Int,
    totalMatterMass: Double,
    physicsCostMs: Float,
    onStressTest: () -> Unit,
    reactionCount: Int,
    planetCount: Int,
    wallsEnabled: Boolean,
    onWallsEnabled: (Boolean) -> Unit,
    gravity: Float,
    onGravity: (Float) -> Unit,
    onResetUniverse: () -> Unit,
    onSelectHome: () -> Unit,
    onMath: () -> Unit,
    timeScale: Float,
    onTimeScale: (Float) -> Unit,
    onBinary: () -> Unit,
    onRewind: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onProfiler: () -> Unit,
    profilerEnabled: Boolean,
    profilerSummary: String,
    onSurprise: () -> Unit,
    onEventIntensity: (CosmicEventIntensity) -> Unit,
    currentEpisode: String,
    onTerminal: () -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit
) {
    var expandedPhysics by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Перезапустить вселенную?") },
            text = { Text("Тела, частицы и текущие события будут сброшены. Настройки сохранятся.") },
            confirmButton = {
                TextButton(onClick = { confirmReset = false; onResetUniverse() }) {
                    Text("Перезапустить")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("Отмена") }
            }
        )
    }
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
        Spacer(Modifier.height(32.dp))
        Text("ГЛАВНЫЙ ЭКРАН", color = AX_DARK, fontSize = 10.sp,
            fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
        Text("⌂  Выбрать домашний экран", color = AX_WHITE, fontSize = 16.sp,
            modifier = Modifier.fillMaxWidth()
                .clickable(onClick = onSelectHome).padding(vertical = 18.dp))
        Text("Откроются системные настройки. На некоторых версиях HyperOS " +
            "выбор стороннего лаунчера ограничен производителем.",
            color = AX_GRAY, fontSize = 12.sp, lineHeight = 18.sp)
        Spacer(Modifier.height(24.dp))
        Text("ОФОРМЛЕНИЕ", color = AX_DARK, fontSize = 10.sp,
            fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                AxiomThemeMode.DARK to "Чёрная",
                AxiomThemeMode.LIGHT to "Белая",
                AxiomThemeMode.AUTO to "Авто"
            ).forEach { (choice, label) ->
                val selected = themeMode == choice
                val shape = RoundedCornerShape(13.dp)
                Text(
                    label,
                    color = if (selected) AX_BLACK else AX_WHITE,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f).clip(shape)
                        .background(if (selected) AX_WHITE else AX_SURFACE)
                        .border(1.dp, AX_BORDER, shape)
                        .clickable { onThemeMode(choice) }
                        .padding(vertical = 12.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
        if (themeMode == AxiomThemeMode.AUTO) {
        Spacer(Modifier.height(9.dp))
        Text("Авто: белая с ${scheduleLabel(sunriseMinutes)} до " +
            "${scheduleLabel(sunsetMinutes)}, остальное время чёрная. " +
            "Учитывается местное время устройства.",
            color = AX_GRAY, fontSize = 12.sp, lineHeight = 18.sp)
        Spacer(Modifier.height(14.dp))
        Text("Начало белой темы · ${scheduleLabel(sunriseMinutes)}",
            color = AX_WHITE, fontSize = 13.sp)
        Slider(
            value = (sunriseMinutes / 15f).coerceIn(0f,95f),
            onValueChange = {
                onSunriseMinutes(
                    (kotlin.math.round(it).toInt().coerceIn(0,95))*15)
            },
            valueRange = 0f..95f, steps = 94,
            colors = SliderDefaults.colors(
                thumbColor=AX_WHITE,activeTrackColor=AX_WHITE,
                inactiveTrackColor=AX_BORDER)
        )
        Text("Начало чёрной темы · ${scheduleLabel(sunsetMinutes)}",
            color = AX_WHITE, fontSize = 13.sp)
        Slider(
            value = (sunsetMinutes / 15f).coerceIn(0f,95f),
            onValueChange = {
                onSunsetMinutes(
                    (kotlin.math.round(it).toInt().coerceIn(0,95))*15)
            },
            valueRange = 0f..95f, steps = 94,
            colors = SliderDefaults.colors(
                thumbColor=AX_WHITE,activeTrackColor=AX_WHITE,
                inactiveTrackColor=AX_BORDER)
        )
        Text("Если время совпадает, белая тема действует круглосуточно. " +
            "Доступны интервалы через 15 минут, включая переход через полночь.",
            color=AX_GRAY,fontSize=11.sp,lineHeight=17.sp)
        }
        Spacer(Modifier.height(27.dp))
        Text("СЦЕНЫ ВСЕЛЕННОЙ", color = AX_DARK, fontSize = 10.sp,
            fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
        Spacer(Modifier.height(10.dp))
        Text("Пять физических стартовых систем. Каждая планета и кольцо состоят " +
            "из свободных песчинок; орбиты задаются лишь начальными " +
            "скоростями. Выбор перезапускает космос, не затрагивая приложения.",
            color = AX_GRAY, fontSize = 12.sp, lineHeight = 18.sp)
        Spacer(Modifier.height(9.dp))
        CosmicPreset.entries.forEach { choice ->
            val selected = cosmicPreset == choice
            val shape = RoundedCornerShape(14.dp)
            Row(
                Modifier.fillMaxWidth().padding(bottom = 7.dp)
                    .clip(shape)
                    .background(if (selected) AX_TILE else AX_SURFACE)
                    .border(if (selected) 1.5.dp else 1.dp,
                        if (selected) AX_WHITE else AX_BORDER, shape)
                    .clickable { onCosmicPreset(choice) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(choice.title, color = AX_WHITE, fontSize = 14.sp,
                        fontWeight = if (selected) FontWeight.SemiBold
                            else FontWeight.Normal)
                    Text(choice.details, color = AX_GRAY, fontSize = 11.sp,
                        lineHeight = 16.sp)
                }
                Text(if (selected) "◉" else "○",
                    color = AX_WHITE, fontSize = 20.sp)
            }
        }
        Text("В системах с орбитами время по умолчанию замедлено до 0,5×. " +
            "Кольца видны сверху — это настоящая 2D-симуляция, " +
            "а не трёхмерная модель.",
            color = AX_GRAY, fontSize = 11.sp, lineHeight = 17.sp)
        Spacer(Modifier.height(25.dp))
        Text("ФИЗИКА ВЕЩЕСТВА", color = AX_DARK, fontSize = 10.sp,
            fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
        Spacer(Modifier.height(9.dp))
        Text("Каждая песчинка движется независимо: гравитация притягивает её " +
            "к остальным, а контактное давление и удары не дают " +
            "скоплению схлопнуться в точку. Масса и число песчинок " +
            "не изменяются при столкновении. Плавление, испарение и " +
            "ионизация расходуют тепло на фазовые переходы.",
            color = AX_GRAY, fontSize = 12.sp, lineHeight = 18.sp)
        Spacer(Modifier.height(14.dp))
        listOf(
            Triple(MatterMode.BALANCED, "Умеренный",
                "До 850 самостоятельных частиц · меньше физических подшагов"),
            Triple(MatterMode.DETAILED, "Проработанный",
                "До 4800 самостоятельных частиц · адаптивная гравитация и потоки")
        ).forEach { (choice, title, description) ->
            val selected = matterMode == choice
            val shape = RoundedCornerShape(17.dp)
            Row(
                Modifier.fillMaxWidth().padding(bottom = 9.dp).clip(shape)
                    .background(if (selected) AX_TILE else AX_SURFACE)
                    .border(if (selected) 1.5.dp else 1.dp,
                        if (selected) AX_WHITE else AX_BORDER, shape)
                    .clickable { onMatterMode(choice) }
                    .padding(15.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, color = AX_WHITE, fontSize = 16.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                    Spacer(Modifier.height(3.dp))
                    Text(description, color = AX_GRAY, fontSize = 12.sp,
                        lineHeight = 18.sp)
                }
                Text(if (selected) "◉" else "○", color = AX_WHITE, fontSize = 22.sp)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("ЛИМИТ ЧАСТИЦ",
            color=AX_DARK,fontFamily=FontFamily.Monospace,
            fontSize=10.sp,letterSpacing=2.sp)
        Spacer(Modifier.height(8.dp))
        Text("%,d".format(java.util.Locale.US,particleLimit) +
            " · установленный максимум", color=AX_WHITE,fontSize=19.sp)
        // Logarithmic mapping: 100 -> 1,000,000. Higher limits are
        // ceilings, never an immediate allocation of a million objects.
        val sliderValue = ((log10(particleLimit.toDouble()) - 2.0)/4.0)
            .toFloat().coerceIn(0f,1f)
        var previewLimit by remember(particleLimit) {
            mutableStateOf(sliderValue)
        }
        Slider(
            value=previewLimit,
            onValueChange={ previewLimit=it },
            onValueChangeFinished={
                val raw=10.0.pow(2.0+previewLimit.toDouble()*4.0)
                val rounded=when {
                    raw < 1000.0 -> (raw/50.0).roundToInt()*50
                    raw < 10000.0 -> (raw/500.0).roundToInt()*500
                    raw < 100000.0 -> (raw/5000.0).roundToInt()*5000
                    else -> (raw/50000.0).roundToInt()*50000
                }
                // Reconfigure/reseed ONLY after release, not every drag
                // pixel; otherwise a low cap would reset the universe.
                onParticleLimit(rounded.coerceIn(100,1_000_000))
            }, valueRange=0f..1f
        )
        Row(Modifier.fillMaxWidth(),
            horizontalArrangement=Arrangement.spacedBy(5.dp)) {
            listOf(850,4800,20_000,100_000,1_000_000)
                .forEach { value ->
                    Box(Modifier.weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (particleLimit==value)
                            AX_TILE else AX_SURFACE)
                        .border(1.dp,AX_BORDER,RoundedCornerShape(8.dp))
                        .clickable { onParticleLimit(value) }
                        .padding(vertical=9.dp),
                        contentAlignment=Alignment.Center) {
                        Text(when(value) {
                            850 -> "850"
                            4800 -> "4,8K"
                            20000 -> "20K"
                            100000 -> "100K"
                            else -> "1M"
                        },fontSize=10.sp,color=AX_WHITE)
                    }
                }
        }
        Spacer(Modifier.height(5.dp))
        Text("Это предел, а не число частиц, которые появляются сразу. " +
            "После 10 000 физика может сильно замедляться: миллион " +
            "независимых частиц не гарантирует плавную работу на Android. " +
            "Для экономии памяти выводится не более 24 000 точек, " +
            "а дорогая перемотка отключается в крупных сценах.",
            color=AX_GRAY,fontSize=11.sp,lineHeight=16.sp)
        Text("＋  Добавить 2000 орбитальных частиц",
            color=AX_WHITE,fontSize=14.sp,
            modifier=Modifier.fillMaxWidth().clickable(onClick=onAddParticleBatch)
                .padding(vertical=12.dp))
        Spacer(Modifier.height(6.dp))
        Text("Частицы в скоплениях: $boundParticleCount · вне скоплений: $particleCount. " +
            "Все частицы имеют собственные скорость и гравитацию.", color = AX_GRAY,
            fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        Text("Общая масса: ${"%.4f".format(java.util.Locale.US, totalMatterMass)} " +
            "· количество частиц неизменно при столкновениях",
            color = AX_GRAY, fontSize = 11.sp, lineHeight = 17.sp)
        Text("Последний расчёт физики: " +
            "${"%.1f".format(java.util.Locale.US, physicsCostMs)} мс. " +
            "Цель 30 FPS: до 33 мс без гарантии для любого устройства.",
            color = AX_GRAY, fontSize = 11.sp, lineHeight = 17.sp)
        Spacer(Modifier.height(13.dp))
        Text("▸  Нагрузочный тест: 4600 песчинок",
            color = AX_WHITE, fontSize = 15.sp,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onStressTest)
                .padding(vertical = 14.dp))
        Text("Явно добавляет вещество в сцену и включает подробный режим. " +
            "События и столкновения новых песчинок не создают.",
            color = AX_GRAY, fontSize = 11.sp, lineHeight = 17.sp)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            listOf(MatterMaterial.ROCK to "Камни",
                MatterMaterial.HYDROGEN to "Газы",
                MatterMaterial.WATER to "Вода",
                MatterMaterial.PLASMA to "Плазма").forEach { (material, label) ->
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.size(7.dp).background(Color(material.argb),
                        RoundedCornerShape(8.dp)))
                    Text(label, color = AX_GRAY, fontSize = 10.sp)
                }
            }
        }
        Spacer(Modifier.height(25.dp))
        Text("▸  Тонкая настройка физики", color = AX_WHITE, fontSize = 15.sp,
            modifier = Modifier.fillMaxWidth().clickable {
                expandedPhysics = !expandedPhysics
            }.padding(vertical = 12.dp))
        Text(if (expandedPhysics) "Настройка гравитации, числа небесных тел и стен" else
            "Нажми, чтобы раскрыть гравитацию, тела и границы",
            color = AX_GRAY, fontSize = 11.sp)
        if (expandedPhysics) {
        Spacer(Modifier.height(18.dp))
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
                inactiveTrackColor = AX_BORDER
            )
        )
        Text("G = ${"%.2f".format(java.util.Locale.US, gravity)}",
            fontFamily = FontFamily.Monospace, color = AX_GRAY, fontSize = 12.sp)
        Spacer(Modifier.height(20.dp))
        Text("ПЛОТНОСТЬ И ГРАНИЦЫ", color=AX_DARK,fontSize=10.sp,
            fontFamily=FontFamily.Monospace,letterSpacing=2.sp)
        Spacer(Modifier.height(12.dp))
        Text("Максимум физических тел: $maximumBodies",
            color=AX_WHITE,fontSize=15.sp)
        Slider(
            value=maximumBodies.toFloat(),
            onValueChange={onMaximumBodies(kotlin.math.round(it).toInt())},
            valueRange=12f..64f,steps=51,
            colors=SliderDefaults.colors(
                thumbColor=AX_WHITE,activeTrackColor=AX_WHITE,
                inactiveTrackColor=AX_BORDER)
        )
        Text("Крупные тела и физические частицы рассчитываются отдельно. " +
            "Число частиц определяется выбранным режимом.",
            color=AX_GRAY,fontSize=12.sp,lineHeight=18.sp)
        Spacer(Modifier.height(13.dp))
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("Режим отражающих стен",color=AX_WHITE,fontSize=14.sp)
                Text("Тела влетают из-за экрана, а затем отскакивают " +
                    "от его границ с потерей энергии.",
                    color=AX_GRAY,fontSize=11.sp,lineHeight=17.sp)
            }
            Switch(checked=wallsEnabled,onCheckedChange=onWallsEnabled,
                colors=SwitchDefaults.colors(
                    checkedThumbColor=AX_BLACK,checkedTrackColor=AX_WHITE,
                    uncheckedThumbColor=AX_GRAY,uncheckedTrackColor=AX_TILE))
        }
        }
        Spacer(Modifier.height(22.dp))
        Text("Событие: ${currentEpisode.lowercase().replace('_', ' ')}",
            color = AX_GRAY, fontSize = 12.sp,
            fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(13.dp))
        Text("✧  Случайное космическое событие",
            color = AX_WHITE, fontSize = 15.sp,
            modifier = Modifier.fillMaxWidth()
                .clickable(onClick = onSurprise).padding(vertical = 15.dp))
        Text("Вызвать событие определённой мощности:",
            color = AX_GRAY, fontSize = 11.sp)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            CosmicEventIntensity.entries.forEach { strength ->
                Text(
                    strength.label,
                    modifier = Modifier.weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(AX_TILE)
                        .clickable { onEventIntensity(strength) }
                        .padding(vertical = 11.dp, horizontal = 3.dp),
                    color = AX_WHITE, fontSize = 10.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Слабые события происходят чаще. Катастрофические — очень редко; " +
            "их эффект ограничен запасом энергии настоящих частиц.",
            color = AX_GRAY, fontSize = 11.sp, lineHeight = 16.sp)
        Spacer(Modifier.height(12.dp))
        Text("Прикосновение создаёт волну, двойное нажатие — комету,\n" +
            "удерживание вызывает событие, горизонтальный жест меняет глубину.",
            color = AX_GRAY, fontSize = 12.sp, lineHeight = 19.sp)
        Spacer(Modifier.height(19.dp))
        Text("ВРЕМЯ МИРА", color = AX_DARK, fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,letterSpacing = 2.sp)
        Spacer(Modifier.height(11.dp))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)){
            listOf(0f to "Ⅱ", .25f to "¼", .5f to "½",
                1f to "1×", 2f to "2×").forEach { (speed,label) ->
                val shape = RoundedCornerShape(9.dp)
                Text(label,color=if(speed==timeScale) AX_BLACK else AX_WHITE,
                    fontSize=13.sp,
                    textAlign=androidx.compose.ui.text.style.TextAlign.Center,
                    modifier=Modifier.weight(1f)
                        .background(if(speed==timeScale) AX_WHITE else AX_SURFACE,shape)
                        .clickable { onTimeScale(speed) }
                        .padding(vertical=12.dp))
            }
        }
        Text("⟲  Повторить предыдущие 3 секунды",
            color=AX_WHITE,fontSize=14.sp,
            modifier=Modifier.fillMaxWidth().clickable(onClick=onRewind)
                .padding(vertical=15.dp))
        Text("⊙  Создать двойную систему",
            color=AX_WHITE,fontSize=14.sp,
            modifier=Modifier.fillMaxWidth().clickable(onClick=onBinary)
                .padding(vertical=15.dp))
        Text("Долгое удерживание + перетаскивание: поле притяжения; " +
            "отпусти после движения — запуск нового тела.",
            color=AX_GRAY,fontSize=12.sp,lineHeight=18.sp)
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
        HorizontalDivider(color = AX_DIVIDER)
        Text("↺  Перезапустить вселенную", color = AX_WHITE, fontSize = 15.sp,
            modifier = Modifier.fillMaxWidth()
                .clickable { confirmReset = true }.padding(vertical = 19.dp))
        HorizontalDivider(color = AX_DIVIDER)
        Text("⟳  Обновить список приложений", color = AX_WHITE,
            fontSize = 15.sp,
            modifier = Modifier.fillMaxWidth()
                .clickable(onClick = onRefresh).padding(vertical = 19.dp))
        HorizontalDivider(color = AX_DIVIDER)
        Text("∑  Математические порталы", color = AX_WHITE,
            fontSize = 15.sp, modifier = Modifier.fillMaxWidth()
                .clickable(onClick = onMath).padding(vertical = 17.dp))
        HorizontalDivider(color = AX_DIVIDER)
        Text("⇧  Экспортировать настройки", color = AX_WHITE,
            fontSize = 15.sp, modifier = Modifier.fillMaxWidth()
                .clickable(onClick = onExport).padding(vertical = 17.dp))
        HorizontalDivider(color = AX_DIVIDER)
        Text("⇩  Восстановить настройки", color = AX_WHITE,
            fontSize = 15.sp, modifier = Modifier.fillMaxWidth()
                .clickable(onClick = onImport).padding(vertical = 17.dp))
        HorizontalDivider(color = AX_DIVIDER)
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
        Text("AXIOM  /  LIVING MATTER · PARTICLE UNIVERSE", color = AX_DARK,
            fontFamily = FontFamily.Monospace, fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 24.dp))
    }
}
