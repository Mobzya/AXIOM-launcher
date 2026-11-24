package dev.axiom.launcher

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.*

internal val AX_ACCENT get() = AxiomColors.sceneAccent ?: if (AxiomColors.palette.light) Color(0xff326d72) else Color(0xffa3d5cf)
internal val AX_LILAC get() = if (AxiomColors.palette.light) Color(0xff745584) else Color(0xffc6b5df)
internal fun number(value: Double, digits: Int = 2) = "% .${digits}f".format(Locale.US,value).trim()

@Composable
internal fun UniverseControlPanel(world: CosmicWorld, onPersist: () -> Unit,
    onScene: (CosmicPreset) -> Unit, onLab: (LabKind) -> Unit, onDismiss: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxWidth().fillMaxHeight(.88f)) {
        Row(Modifier.padding(horizontal=22.dp, vertical = 3.dp).fillMaxWidth(),
            verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Обсерватория",color=AX_WHITE,fontSize=23.sp,
                    fontWeight=FontWeight.Light)
                Text(world.preset.title, color=AX_GRAY, fontSize=12.sp)
            }
            TextButton(onClick=onDismiss) { Text("Готово",color=AX_ACCENT) }
        }
        ObservatoryStatus(world, Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        Row(Modifier.padding(horizontal=18.dp),horizontalArrangement=Arrangement.spacedBy(5.dp)) {
            val speeds = listOf(0f to "Ⅱ", .25f to "¼×", .5f to "½×", 1f to "1×", 2f to "2×")
            speeds.forEach { (speed,label) ->
                Choice(label,world.timeScale==speed,Modifier.weight(1f),
                    description=if(speed==0f) "Пауза" else "Скорость времени $label") {
                    world.updateTimeScale(speed)
                }
            }
        }
        Row(Modifier.padding(horizontal=18.dp,vertical=12.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            listOf("Сцены","Объекты","Жизнь","Наука","Среда").forEachIndexed { index,label ->
                Choice(label,tab==index,Modifier.weight(1f)) { tab=index }
            }
        }
        HorizontalDivider(color=AX_DIVIDER)
        Crossfade(tab,animationSpec=tween(if(world.reducedMotion) 0 else 220),label="observatory pages",
            modifier=Modifier.weight(1f)) { page ->
            if(page==0)ScenarioPage(world,onScene)
            else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                when(page) {
                    1 -> CreationPage(world)
                    2 -> CivilizationPage(world,onPersist)
                    3 -> ObservationPage(world,onLab,onPersist)
                    else -> EnvironmentPage(world,onPersist)
                }
                Spacer(Modifier.height(18.dp))
            }
        }
    }
}

@Composable
private fun ObservatoryStatus(world: CosmicWorld, modifier: Modifier) {
    val paused = world.timeScale == 0f
    val accent = AX_ACCENT
    val episode by remember(world) { derivedStateOf { world.episode.kind } }
    val active = episode != CosmicEventKind.QUIET
    val travel by animateFloatAsState(if (paused) 0f else 1f,
        tween(if (world.reducedMotion) 0 else 260), label = "observatory status")
    Row(modifier.fillMaxWidth().background(AX_SURFACE, RoundedCornerShape(12.dp))
        .padding(horizontal = 12.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Canvas(Modifier.size(29.dp).clearAndSetSemantics { }) {
            val width = 1.1.dp.toPx()
            val radius = size.minDimension * .39f
            drawCircle(accent.copy(alpha = .23f), radius, style = Stroke(width))
            val phase = if (active && !world.reducedMotion) world.episode.progress else travel
            val angle = phase * 2f * PI.toFloat() - PI.toFloat() / 2f
            drawArc(accent.copy(alpha = .75f), -90f, phase * 360f, false,
                androidx.compose.ui.geometry.Offset(center.x - radius, center.y - radius),
                androidx.compose.ui.geometry.Size(radius * 2, radius * 2), style = Stroke(width, cap = StrokeCap.Round))
            drawCircle(accent, 2.dp.toPx(), center + Offset(cos(angle), sin(angle)) * radius)
        }
        val status = when {
            paused -> "На паузе"
            active -> manualPhenomena[episode] ?: extremePhenomena.firstOrNull { it.kind == episode }?.title ?: "Событие в космосе"
            else -> "Наблюдение"
        }
        Text(status, color = if (active && !paused) accent else AX_WHITE, fontSize = 12.sp,
            modifier = Modifier.weight(1f), maxLines = 1)
        Text("${world.telemetry.particles} частиц",color=AX_GRAY,fontSize=11.sp,fontFamily=FontFamily.Monospace)
    }
}

private enum class SceneFilter(val title: String) {
    ALL("Все"), ORBITS("Орбиты"), ENCOUNTERS("Встречи"), EXTREMES("Аномалии"),
    NEBULAE("Туманности"), LIFE("Обитаемые")
}

private fun CosmicPreset.category(): SceneFilter = when (this) {
    CosmicPreset.COLLISION_LAB, CosmicPreset.OCEAN_IMPACT, CosmicPreset.ROGUE_ENCOUNTER,
    CosmicPreset.COMET_PASSAGE, CosmicPreset.GALAXY_PASSAGE, CosmicPreset.GIANT_GRAZE ->
        SceneFilter.ENCOUNTERS
    CosmicPreset.BLACK_HOLE_NURSERY, CosmicPreset.BLACK_HOLE_MERGER, CosmicPreset.GALACTIC_NUCLEUS,
    CosmicPreset.PULSAR_REEF, CosmicPreset.WHITE_DWARF_RELIC, CosmicPreset.HYPERGIANT_SYSTEM,
    CosmicPreset.QUANTUM_SHORES, CosmicPreset.ROCHE_PASSAGE -> SceneFilter.EXTREMES
    CosmicPreset.NEBULA_CROSSING, CosmicPreset.BLUE_NEBULA, CosmicPreset.PROTOPLANETARY -> SceneFilter.NEBULAE
    CosmicPreset.LIVING_SYSTEM, CosmicPreset.ASTEROID_CORRIDOR -> SceneFilter.LIFE
    else -> SceneFilter.ORBITS
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScenarioPage(world: CosmicWorld,onScene:(CosmicPreset)->Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(SceneFilter.ALL) }
    val scenes = remember(query, filter) {
        val term = query.trim()
        CosmicPreset.entries.filter { scene ->
            if (term.isNotEmpty()) scene.title.contains(term, ignoreCase = true) ||
                scene.details.contains(term, ignoreCase = true)
            else filter == SceneFilter.ALL || filter == scene.category()
        }
    }
    LazyVerticalGrid(columns=GridCells.Fixed(2),modifier=Modifier.fillMaxSize(),
        contentPadding=PaddingValues(20.dp),horizontalArrangement=Arrangement.spacedBy(10.dp),
        verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item(span={GridItemSpan(maxLineSpan)}) {
            Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                SectionIntro("Начальные условия", "Выбери историю и наблюдай, как её продолжит физика. Загрузка заменяет только космическую сцену.")
                OutlinedTextField(value=query,onValueChange={query=it},singleLine=true,
                    label={Text("Найти сценарий")},
                    modifier=Modifier.fillMaxWidth().semantics {contentDescription="Поиск сценария"})
                if (query.isBlank()) FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    for (category in SceneFilter.entries) {
                        Choice(category.title, filter == category) { filter = category }
                    }
                }
            }
        }
        items(scenes,key={it.name}) {preset ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(19.dp))
                .background(AX_SURFACE).border(1.dp,
                    if(world.preset==preset) AX_ACCENT.copy(alpha=.65f) else AX_BORDER,
                    RoundedCornerShape(19.dp))
                .semantics {contentDescription="Сцена: ${preset.title}. ${preset.details}"}
                .clickable(role=Role.Button) {onScene(preset)}
                .padding(13.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                ScenarioPreview(preset, world.preset == preset, world.reducedMotion,
                    Modifier.fillMaxWidth().height(82.dp).clip(RoundedCornerShape(10.dp)))
                Text(preset.title,color=AX_WHITE,fontSize=14.sp,fontWeight=FontWeight.Medium)
                Text(preset.details,color=AX_GRAY,fontSize=11.sp,lineHeight=16.sp,
                    modifier=Modifier.heightIn(min=48.dp))
                Text(if(world.preset==preset) "↺  Начать заново" else "↗  Открыть сцену",
                    color=AX_ACCENT,fontSize=11.sp)
            }
        }
        if(scenes.isEmpty())item(span={GridItemSpan(maxLineSpan)}) {
            Text("Нет подходящих сценариев",color=AX_GRAY)
        }
    }
}

@Composable
private fun ScenarioPreview(preset: CosmicPreset, selected: Boolean, reducedMotion: Boolean, modifier:Modifier) {
    val bodies = remember(preset) { presetBodies(preset) }
    val accent=AX_ACCENT; val lilac=AX_LILAC
    val selection by animateFloatAsState(if (selected) 1f else 0f, tween(if (reducedMotion) 0 else 350),
        label = "scene preview selection")
    Canvas(modifier) {
        val side=min(size.width,size.height)*.94f
        val origin=Offset((size.width-side)/2,(size.height-side)/2)
        drawRect(Brush.radialGradient(listOf(lilac.copy(alpha = .10f + .12f * selection), Color.Transparent),
            center, size.width * .61f))
        for (r in listOf(.19f,.32f,.45f)) drawCircle(accent.copy(alpha=.10f + .07f * selection),side*r,center,
            style=Stroke(1.dp.toPx()))
        if(bodies.isEmpty()) {
            repeat(16) { i ->
                val a=i*2.39996f; val r=sqrt((i+.5f)/16)*side*.43f
                drawCircle(lilac.copy(alpha=.55f),1.3.dp.toPx(),center+Offset(cos(a)*r,sin(a)*r))
            }
        }
        bodies.forEach { body ->
            val radius=(1.5f+sqrt(body.mass)*1.1f).dp.toPx().coerceAtMost(7.dp.toPx())
            val point=origin+Offset(body.x*side,body.y*side)
            val color=when(body.nature) {
                CelestialNature.BLACK_HOLE -> lilac
                CelestialNature.NEUTRON_STAR,CelestialNature.WHITE_DWARF -> Color(0xffb9d1f3)
                else -> if(body.stellarClass==StellarClass.STAR)Color(0xffe2bb87) else Color(body.material.argb)
            }
            drawCircle(color.copy(alpha=.09f),radius*2.8f,point)
            if(body.nature==CelestialNature.BLACK_HOLE) {
                drawCircle(Color(0xff080b17),radius,point)
                drawCircle(color.copy(alpha=.8f),radius*1.2f,point,style=Stroke(.8.dp.toPx()))
            } else drawCircle(color.copy(alpha=.9f),radius,point)
        }
        if(preset==CosmicPreset.SATURN_RINGS || preset==CosmicPreset.PROTOPLANETARY ||
            preset==CosmicPreset.COLD_NURSERY || preset==CosmicPreset.COUNTER_ROTATING_RINGS ||
            preset==CosmicPreset.BLUE_NEBULA) repeat(65) { i ->
            val a=i*2.39996f; val r=(.13f+(i%7)*.018f)*side
            drawCircle(accent.copy(alpha=.55f),.7.dp.toPx(),center+Offset(cos(a)*r,sin(a)*r))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreationPage(world:CosmicWorld) {
    var recipe by rememberSaveable { mutableStateOf(BodyRecipe.ROCKY) }
    var mass by rememberSaveable { mutableFloatStateOf(recipe.mass) }
    var launch by rememberSaveable { mutableStateOf(LaunchStyle.ORBIT) }
    var point by remember { mutableStateOf(Offset(.72f,.38f)) }
    var message by remember { mutableStateOf("") }
    SectionIntro("Добавь новый мир", "Состав, масса и начальная скорость определяют поведение объекта.")
    RecipeSelector(recipe, world.reducedMotion) { choice ->
        recipe = choice
        mass = choice.mass
        message = ""
    }
    Text("Масса · ${number(mass.toDouble())}",color=AX_WHITE,fontSize=14.sp)
    // Logarithmic control makes moons and large stars equally easy to tune.
    Slider(value=ln(mass),onValueChange={mass=exp(it)},valueRange=ln(.04f)..ln(recipe.maximumMass))
    Text("Точка появления · коснись карты",color=AX_GRAY,fontSize=12.sp)
    val accent=AX_ACCENT; val border=AX_BORDER
    Canvas(Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(16.dp))
        .background(AX_SURFACE).semantics { contentDescription="Карта точки появления нового объекта" }
        .pointerInput(Unit) { detectTapGestures { p ->
            val side=min(size.width,size.height).toFloat()
            point=Offset(((p.x-(size.width-side)/2)/side).coerceIn(.06f,.94f),
                ((p.y-(size.height-side)/2)/side).coerceIn(.06f,.94f))
        } }) {
        val viewport=UniverseViewport(size.width,size.height)
        drawRect(border.copy(alpha=.35f),topLeft=Offset(viewport.left,viewport.top),
            size=androidx.compose.ui.geometry.Size(viewport.side,viewport.side),style=Stroke(1.dp.toPx()))
        world.bodies.forEach { body -> drawCircle(Color(body.material.argb),
            (2f+sqrt(body.mass)).dp.toPx().coerceAtMost(7.dp.toPx()),
            Offset(viewport.x(body.x),viewport.y(body.y))) }
        val p=Offset(viewport.x(point.x),viewport.y(point.y))
        drawCircle(accent,7.dp.toPx(),p,style=Stroke(1.dp.toPx()))
        drawLine(accent,p-Offset(11.dp.toPx(),0f),p+Offset(11.dp.toPx(),0f),1.dp.toPx())
        drawLine(accent,p-Offset(0f,11.dp.toPx()),p+Offset(0f,11.dp.toPx()),1.dp.toPx())
    }
    FlowRow(horizontalArrangement=Arrangement.spacedBy(5.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
        LaunchStyle.entries.forEach { style -> Choice(style.title,launch==style) { launch=style } }
    }
    Text("Орбитальная скорость рассчитывается относительно самого массивного тела. Масса добавляется в сцену по твоей команде.",
        color=AX_GRAY,fontSize=11.sp,lineHeight=17.sp)
    Action("＋  Создать · ${recipe.title}") {
        message=if(world.createObject(recipe.material,mass,point.x,point.y,launch,recipe))
            "Объект создан. Его движение уже рассчитывает физика."
        else "Достигнут лимит тел или частиц. Выбери другую сцену или увеличь лимит в настройках."
    }
    if(message.isNotEmpty()) Text(message,color=AX_ACCENT,fontSize=12.sp,lineHeight=18.sp)
}

private enum class RecipeGroup(val title: String, val recipes: List<BodyRecipe>) {
    WORLDS("Планеты", BodyRecipe.entries.filter {
        it.classification == StellarClass.PLANET && it.nature == CelestialNature.ORDINARY && it.mass >= .3f
    }),
    MOONS("Малые тела", BodyRecipe.entries.filter {
        it.classification == StellarClass.PLANET && (it.mass < .3f || it.nature != CelestialNature.ORDINARY)
    }),
    STARS("Звёзды", BodyRecipe.entries.filter { it.classification == StellarClass.STAR }),
    PHENOMENA("Аномалии", BodyRecipe.entries.filter {
        it.classification == StellarClass.CLOUD || it.classification == StellarClass.QUASAR
    })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecipeSelector(recipe: BodyRecipe, reducedMotion: Boolean, onChoose: (BodyRecipe) -> Unit) {
    var group by rememberSaveable { mutableStateOf(RecipeGroup.entries.first { recipe in it.recipes }) }
    var expanded by remember { mutableStateOf(false) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        for (option in RecipeGroup.entries) {
            Choice(option.title, group == option) {
                group = option
                if (recipe !in option.recipes) onChoose(option.recipes.first())
            }
        }
    }
    val tint by animateColorAsState(Color(recipe.material.argb), tween(if (reducedMotion) 0 else 260),
        label = "body composition")
    Box(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(AX_SURFACE)
            .border(1.dp, AX_BORDER, RoundedCornerShape(15.dp))
            .clickable(role = Role.Button) { expanded = true }.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Canvas(Modifier.size(42.dp)) {
                val radius = size.minDimension * .31f
                drawCircle(tint.copy(alpha = .12f), radius * 1.5f)
                val compact = recipe.nature == CelestialNature.BLACK_HOLE
                drawCircle(if (compact) Color(0xff080b17) else tint.copy(alpha = .53f), radius)
                drawCircle(tint.copy(alpha = .88f), radius, style = Stroke(1.dp.toPx()))
                if (recipe.ringFraction > 0f) {
                    drawOval(tint.copy(alpha = .83f), center - Offset(radius * 1.6f, radius * .48f),
                        androidx.compose.ui.geometry.Size(radius * 3.2f, radius * .96f), style = Stroke(1.dp.toPx()))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(recipe.title, color = AX_WHITE, fontSize = 15.sp)
                Text(recipe.description, color = AX_GRAY, fontSize = 11.sp, lineHeight = 15.sp)
            }
            Text("▾", color = AX_ACCENT)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 320.dp)) {
            for (option in group.recipes) {
                DropdownMenuItem(text = { Text(option.title) }, onClick = {
                    onChoose(option)
                    expanded = false
                })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ObservationPage(world:CosmicWorld,onLab:(LabKind)->Unit,onPersist:()->Unit) {
    val t=world.telemetry
    SectionIntro("От движения к пониманию", "Измерения берутся из текущей симуляции. Единицы нормированы; это двумерная физическая модель.")
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Metric("МАССА",number(t.mass),Modifier.weight(1f))
        Metric("СВОБОДНАЯ ПЫЛЬ",t.loose.toString(),Modifier.weight(1f))
    }
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Metric("РЕАКЦИИ",t.reactions.toString(),Modifier.weight(1f))
        Metric("ФАЗОВЫЕ ПЕРЕХОДЫ",t.phases.toString(),Modifier.weight(1f))
    }
    NoteCard("Энергия и движение") {
        Text("a = −GM r / (r² + ε²)³ᐟ²",color=AX_ACCENT,fontFamily=FontFamily.Monospace,fontSize=13.sp)
        Text("K = ${number(t.kinetic,5)}   Uтепл = ${number(t.heat,5)}",color=AX_GRAY,
            fontSize=11.sp,fontFamily=FontFamily.Monospace)
        Text("Eмодели = ${number(t.energy,5)}",color=AX_WHITE,fontSize=13.sp)
        Text("Внешнее излучение +${number(t.externalRadiation,5)} · излучено ${number(t.radiated,5)}",
            color=AX_GRAY,fontSize=11.sp)
        if (t.externalFront > 0.0)
            Text("Энергия от волновых фронтов +${number(t.externalFront,5)}", color=AX_GRAY,fontSize=11.sp)
        Text("При ударах движение переходит в тепло. Стены, создание объектов, события и смена G меняют условия эксперимента.",
            color=AX_GRAY,fontSize=12.sp,lineHeight=18.sp)
    }
    val reading=world.learning
    NoteCard("ML · научиться видеть притяжение") {
        Text("aнабл ≈ β · aНьютон",color=AX_LILAC,fontFamily=FontFamily.Monospace,fontSize=14.sp)
        Text(if(!world.learningEnabled) "Обучение выключено в разделе «Среда»."
            else if(reading.samples<24) "Собираем движение… ${reading.samples} наблюдений"
            else "Оценка G / G₀ = ${number(reading.gravity,3)} · задано ${number(world.gravity.toDouble(),2)}",
            color=AX_WHITE,fontSize=13.sp)
        reading.relativeError?.let { error -> Text("Ошибка следующего прогноза · ${number(error*100,1)}%",
            color=AX_LILAC,fontSize=12.sp) }
        Text("Локальная онлайн-регрессия учится на ускорениях центров масс. Ошибка считается до обучения на новом наблюдении. Близкие столкновения исключаются; приливы и протяжённость тел могут давать расхождение с точечной моделью.",
            color=AX_GRAY,fontSize=12.sp,lineHeight=18.sp)
        Text("Орбиты рассчитывает физика. Изученная оценка G помогает автопилотам кораблей компенсировать притяжение.",color=AX_GRAY,fontSize=11.sp)
    }
    QuantumPanel(world,onPersist)
    Text("ВЫБЕРИ ТЕЛО",color=AX_GRAY,fontSize=10.sp,letterSpacing=1.5.sp)
    FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        world.bodies.forEach { body -> Choice("№${body.id}",world.selectedBody?.id==body.id) {
            world.inspectBody(body.id)
        } }
    }
    val body=world.bodies.firstOrNull { it.id==world.selectedBody?.id }
        ?: world.bodies.minByOrNull { it.mass }
    if(body!=null) {
        val primary=world.bodies.filter { it.id!=body.id }.maxByOrNull { it.mass }
        NoteCard("Тело №${body.id} · ${if(body.nature==CelestialNature.ORDINARY)materialName(body.material) else body.nature.title}") {
            if(body.nature==CelestialNature.BLACK_HOLE) {
                Text("m = ${number(body.mass.toDouble())} · rгор ≈ ${number(horizonRadius(body.mass).toDouble(),3)}",
                    color=AX_WHITE,fontSize=13.sp)
                Text("Спин накопленного вещества · ${number(body.spinAngularMomentum,5)}",
                    color=AX_LILAC,fontSize=11.sp)
                Text("Горизонт поглощения — эффективная граница этой 2D-модели. Температура Хокинга и ОТО не рассчитываются.",
                    color=AX_GRAY,fontSize=11.sp,lineHeight=17.sp)
            } else Text("m = ${number(body.mass.toDouble())} · T ≈ ${number(body.temperature.toDouble(),0)} K",
                color=AX_WHITE,fontSize=13.sp)
            if(primary!=null) {
                val orbit=OrbitMechanics.orbit(body,primary,world.gravity)
                Text("r = ${number(orbit.distance,3)} · v = ${number(orbit.speed,4)}",
                    color=AX_ACCENT,fontFamily=FontFamily.Monospace,fontSize=12.sp)
                Text("Эксцентриситет ≈ ${number(orbit.eccentricity,3)} · "+
                    if(orbit.bound) "связанная орбита" else "пролётная траектория",
                    color=AX_GRAY,fontSize=12.sp)
                Text("Мгновенная кеплерова оценка относительно №${primary.id}; при близких встречах и влиянии других тел это приближение.",
                    color=AX_GRAY,fontSize=11.sp,lineHeight=17.sp)
            }
        }
    }
    Action("∿  Открыть лабораторию Фурье") { onLab(LabKind.FOURIER) }
    Text("В резонансной сцене отношение периодов 1 : 2 : 4 связывает орбиты с частотами. В лаборатории можно исследовать сложение гармоник.",
        color=AX_GRAY,fontSize=12.sp,lineHeight=18.sp)
}

private val manualPhenomena = linkedMapOf(
    CosmicEventKind.BLAST_SHELL to "Фронт взрыва",
    CosmicEventKind.REVERSE_SHOCK to "Сходящаяся волна",
    CosmicEventKind.BIPOLAR_OUTFLOW to "Биполярный выброс",
    CosmicEventKind.COSMIC_SHOCK to "Ударная волна",
    CosmicEventKind.METEORS to "Метеорный поток",
    CosmicEventKind.CRYO_TIDE to "Радиационное охлаждение",
    CosmicEventKind.THERMAL_SPIKE to "Ударный нагрев",
    CosmicEventKind.SOLAR_WIND to "Звёздный ветер",
    CosmicEventKind.ACCRETION_BURST to "Слипание пыли",
    CosmicEventKind.TIDAL_PULSE to "Приливный импульс",
    CosmicEventKind.IONIZATION_FRONT to "Фронт ионизации",
    CosmicEventKind.MICROLENSING to "Микролинзирование",
    CosmicEventKind.PULSAR to "Импульсы пульсара",
    CosmicEventKind.QUASAR_JET to "Джеты квазара",
    CosmicEventKind.CRYSTALLIZATION to "Кристаллизация",
    CosmicEventKind.NOVA to "Вспышка новой",
    CosmicEventKind.SOLAR_FLARE to "Звёздная вспышка",
    CosmicEventKind.MAGNETAR_FLARE to "Вспышка магнетара",
    CosmicEventKind.PLASMA_STORM to "Плазменная буря",
    CosmicEventKind.STELLAR_NURSERY to "Охлаждение туманности",
    CosmicEventKind.QUANTUM_COHERENCE to "Квантовая когерентность"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EnvironmentPage(world:CosmicWorld,onPersist:()->Unit) {
    SectionIntro("События и среда", "Настрой частоту, движение и вид космоса на домашнем экране.")
    EventControls(world,onPersist)
    UniverseCyclePanel(world,onPersist)
    Toggle("Следы орбит","Показывать историю движения тел",world.showTrails) {
        world.configureObservation(trails=it); onPersist()
    }
    Toggle("Векторы скорости","Направление и величина текущего движения",world.showVectors) {
        world.configureObservation(vectors=it); onPersist()
    }
    Toggle("Спокойное движение","Отрисовка до 30 Гц и короткие переходы",world.reducedMotion) {
        world.configureObservation(motion=it); onPersist()
    }
    Toggle("Математические этюды","Редкие подсказки, подобранные к текущей сцене",world.ambientMath) {
        world.configureObservation(maths=it); onPersist()
    }
    Toggle("Обучение на орбитах","Локальная регрессия по движениям тел",world.learningEnabled) {
        world.configureObservation(ml=it); onPersist()
    }
    Toggle("Отражающие границы","Стены почти у краёв всего экрана, включая область над часами",world.wallsEnabled) {
        world.updateWalls(it); onPersist()
    }
    Toggle("Часы — физическая стена","Контур совпадает с видимыми цифрами и обновляется каждую минуту",world.clockWallsEnabled) {
        world.configureCosmos(clockWall=it);onPersist()
    }
    Toggle("Тепловой баланс миров","Нагрев от конечного запаса энергии звезды и охлаждение излучением",world.radiativeClimate) {
        world.configureCosmos(thermalBalance=it);onPersist()
    }
    Text("Сила притяжения · ${number(world.gravity.toDouble())}×",color=AX_WHITE,fontSize=14.sp)
    Slider(value=world.gravity,onValueChange={world.updateGravity(it);onPersist()},valueRange=.25f..2.2f)
    Text("Детализация · смена перезапускает сцену",color=AX_GRAY,fontSize=12.sp)
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Choice("Экономно · 850",world.matterMode==MatterMode.BALANCED,Modifier.weight(1f)) {
            world.updateMatterMode(MatterMode.BALANCED);onPersist()
        }
        Choice("Подробно · 4800",world.matterMode==MatterMode.DETAILED,Modifier.weight(1f)) {
            world.updateMatterMode(MatterMode.DETAILED);onPersist()
        }
    }
    Text("Лимит новых тел · ${world.maxBodies}",color=AX_WHITE,fontSize=14.sp)
    Slider(value=world.maxBodies.toFloat(),onValueChange={
        world.updateMaximumBodies(it.roundToInt());onPersist()
    },valueRange=12f..64f,steps=51)
    Text("Снижение лимита сохраняет уже существующее вещество.",color=AX_GRAY,fontSize=11.sp)
    Action("Нагрузочная сцена · 4600 частиц") {world.populateStressScene(4600);onPersist()}
    Text("Последний расчёт · ${number(world.physicsCostMs.toDouble(),1)} мс. Частота зависит от нагрузки и телефона.",
        color=AX_GRAY,fontSize=11.sp,lineHeight=17.sp)
    var message by remember { mutableStateOf("") }
    Action("↶  Вернуться на 3 секунды") {
        message=if(world.rewind(3)) "Восстановлено сохранённое состояние" else "Нужно немного истории движения"
    }
    Action("↺  Начать текущую сцену заново") { world.reset();message="Сцена перезапущена" }
    if (message.isNotEmpty()) Text(message,color=AX_ACCENT,fontSize=12.sp)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EventControls(world: CosmicWorld, onPersist: () -> Unit) {
    Toggle("Автоматические события","Случайные явления и внешние прибытия",world.automaticEvents) {
        world.configureObservation(events=it); onPersist()
    }
    if (world.automaticEvents) {
        Text("Обычные события · ${world.eventCadence.interval}", color = AX_WHITE, fontSize = 13.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            for (cadence in EventCadence.entries) {
                Choice(cadence.title, world.eventCadence == cadence,
                    description = "${cadence.title}, ${cadence.interval}") {
                    world.changeEventCadence(cadence)
                    onPersist()
                }
            }
        }
        Text("Интервал по времени симуляции. Следующее событие ждёт окончания текущего.",
            color = AX_GRAY, fontSize = 11.sp)
    }
    Text("Ручной запуск",color=AX_WHITE,fontSize=14.sp,fontWeight=FontWeight.Medium)
    var intensity by rememberSaveable { mutableStateOf(CosmicEventIntensity.FLICKER) }
    var message by remember { mutableStateOf("") }
    FlowRow(horizontalArrangement=Arrangement.spacedBy(5.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
        CosmicEventIntensity.entries.forEach { level -> Choice(level.label,intensity==level) {intensity=level} }
    }
    var event by rememberSaveable { mutableStateOf(CosmicEventKind.COSMIC_SHOCK) }
    var chooseEvent by remember { mutableStateOf(false) }
    val title = manualPhenomena.getValue(event)
    Box {
        OutlinedButton(onClick = { chooseEvent = true }, modifier = Modifier.fillMaxWidth()) {
            Text(title, color = AX_WHITE, modifier = Modifier.weight(1f))
            Text("▾", color = AX_ACCENT)
        }
        DropdownMenu(expanded = chooseEvent, onDismissRequest = { chooseEvent = false }) {
            for ((kind, name) in manualPhenomena) {
                DropdownMenuItem(text = { Text(name) }, onClick = {
                    event = kind
                    chooseEvent = false
                })
            }
        }
    }
    Action("Запустить событие") {
        world.trigger(event, intensity)
        message = "$title · ${intensity.label}"
    }
    if(message.isNotEmpty()) Text(message,color=AX_ACCENT,fontSize=12.sp)
    PhenomenaPanel(world,onPersist)
}

@Composable
internal fun SectionIntro(title:String,description:String) {
    Text(title,color=AX_WHITE,fontSize=23.sp,fontWeight=FontWeight.Light)
    Text(description,color=AX_GRAY,fontSize=12.sp,lineHeight=19.sp)
}

@Composable
internal fun Metric(label:String,value:String,modifier:Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(AX_SURFACE).padding(14.dp),
        verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(label,color=AX_GRAY,fontSize=9.sp,letterSpacing=.5.sp)
        Text(value,color=AX_WHITE,fontSize=23.sp,fontWeight=FontWeight.Light)
    }
}

@Composable
internal fun NoteCard(title:String,content:@Composable ColumnScope.()->Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(AX_SURFACE)
        .border(1.dp,AX_BORDER,RoundedCornerShape(18.dp)).padding(16.dp),
        verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text(title,color=AX_WHITE,fontSize=15.sp,fontWeight=FontWeight.Medium)
        content()
    }
}

@Composable
internal fun Toggle(title:String,description:String,value:Boolean,onChange:(Boolean)->Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
        .toggleable(value=value,role=Role.Switch,onValueChange=onChange).padding(vertical=7.dp),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title,color=AX_WHITE,fontSize=14.sp)
            Text(description,color=AX_GRAY,fontSize=11.sp,lineHeight=16.sp)
        }
        Switch(checked=value,onCheckedChange=null,
            colors=SwitchDefaults.colors(checkedThumbColor=AX_SURFACE,checkedTrackColor=AX_ACCENT))
    }
}

@Composable
internal fun Choice(title:String,active:Boolean,modifier:Modifier=Modifier,
    description:String=title,onClick:()->Unit) {
    val background by animateColorAsState(if(active) AX_ACCENT.copy(alpha=.15f) else AX_SURFACE,
        animationSpec=tween(180),label="choice tint")
    Box(modifier.heightIn(min=48.dp).clip(RoundedCornerShape(13.dp)).background(background)
        .border(1.dp,if(active) AX_ACCENT.copy(alpha=.65f) else AX_BORDER,RoundedCornerShape(13.dp))
        .semantics { contentDescription=description;selected=active }
        .clickable(role=Role.Button,onClick=onClick).padding(horizontal=11.dp,vertical=9.dp),
        contentAlignment=Alignment.Center) {
        Text(title,color=if(active) AX_ACCENT else AX_GRAY,fontSize=11.sp)
    }
}

@Composable
internal fun Action(title:String,onClick:()->Unit) {
    OutlinedButton(onClick=onClick,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),
        shape=RoundedCornerShape(14.dp),border=androidx.compose.foundation.BorderStroke(1.dp,AX_BORDER)) {
        Text(title,color=AX_ACCENT,fontSize=12.sp)
    }
}

internal fun materialName(material:MatterMaterial):String = when(material) {
    MatterMaterial.ROCK -> "каменное вещество"
    MatterMaterial.ICE -> "лёд"
    MatterMaterial.WATER -> "вода"
    MatterMaterial.HYDROGEN -> "водород"
    MatterMaterial.PLASMA -> "плазма"
    MatterMaterial.IRON -> "железо"
    MatterMaterial.METHANE -> "газовый мир"
    else -> material.name.lowercase(Locale.ROOT)
}
