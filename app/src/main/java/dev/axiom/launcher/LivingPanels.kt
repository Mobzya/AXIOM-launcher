package dev.axiom.launcher

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CivilizationPage(world:CosmicWorld,onPersist:()->Unit) {
    val life=world.civilization;val config=world.civilizationConfig
    var message by remember {mutableStateOf("")}
    SectionIntro("Жизнь среди звёзд","Колониям нужны спокойные орбиты, подходящая температура и ресурсы. Корабли могут погибнуть; миры не защищены от катастроф.")
    FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        LifeMode.entries.forEach {mode -> Choice(mode.title,config.mode==mode) {
            world.configureCivilization(config.copy(mode=mode));onPersist()
        } }
    }
    Text("Самозарождение редко появляется на пригодном мире после спокойного периода. В сценах «Обитаемый архипелаг» и «Рубеж колонии» первые поселения уже есть.",
        color=AX_GRAY,fontSize=12.sp,lineHeight=18.sp)
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Metric("КОЛОНИИ",life.colonies.size.toString(),Modifier.weight(1f))
        Metric("КОРАБЛИ",life.ships.size.toString(),Modifier.weight(1f))
    }
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Metric("ПОПАДАНИЯ",life.interceptions.toString(),Modifier.weight(1f))
        Metric("ПРОМАХИ",life.misses.toString(),Modifier.weight(1f))
    }
    Text("Население · ${number(life.colonies.sumOf {it.population},0)} · новых поселений ${life.settlements} · потерь ${life.extinct}",
        color=AX_GRAY,fontSize=12.sp)
    Toggle("Колонизация","Переселенцы расходуют ресурсы и покидают родную планету",config.colonization) {
        world.configureCivilization(config.copy(colonization=it));onPersist()
    }
    Toggle("Планетарная защита","Перехватчики летят к прогнозируемой точке встречи с астероидом",config.defense) {
        world.configureCivilization(config.copy(defense=it));onPersist()
    }
    Toggle("Маршруты кораблей","Тонкие линии к движущимся целям",config.routes) {
        world.configureCivilization(config.copy(routes=it));onPersist()
    }
    Text("Темп развития · ${number(config.pace.toDouble(),1)}×",color=AX_WHITE,fontSize=13.sp)
    Slider(value=config.pace,onValueChange={world.configureCivilization(config.copy(pace=it));onPersist()},valueRange=.5f..3f)
    Text("Меняет рост и экономику. Полёты по-прежнему используют физическое время вселенной.",color=AX_GRAY,fontSize=11.sp,lineHeight=17.sp)
    NoteCard("ML · решения и опыт") {
        Text("Пригодность мира → вероятность выживания",color=AX_ACCENT,fontSize=13.sp)
        Text("Наблюдений: ${life.survivalSamples}"+
            (life.brier?.let {" · ошибка Брайера ${number(it,3)}"} ?: " · данных пока мало"),
            color=AX_WHITE,fontSize=12.sp)
        Text("Калибровок прицела: ${life.aimSamples}"+
            (life.aimError?.let {" · средний промах ${number(it,4)}"} ?: ""),
            color=AX_LILAC,fontSize=12.sp)
        Text("Локальная логистическая модель учится на следующем интервале жизни колонии. Автопилоты используют изученное G; перехватчики корректируют систематическую ошибку по своим полётам. Случайная ошибка, нехватка топлива и манёвр цели сохраняются.",
            color=AX_GRAY,fontSize=12.sp,lineHeight=18.sp)
        Text(if(world.learningEnabled)"Обучение включено" else "Обучение выключено; используются исходные оценки",
            color=AX_ACCENT,fontSize=11.sp)
    }
    life.colonies.forEach {colony ->
        NoteCard("${civilizationNames[colony.faction]} · мир №${colony.bodyId}") {
            Text("${number(colony.population,0)} жителей · ресурсы ${number(colony.resources,0)} / 250",
                color=AX_WHITE,fontSize=12.sp)
            LinearProgressIndicator(progress={(colony.resources/250).toFloat()},
                modifier=Modifier.fillMaxWidth(),color=AX_ACCENT,trackColor=AX_TILE)
            Text("Технология ${number(colony.technology*100,0)}%",color=AX_GRAY,fontSize=11.sp)
            Action("Найти планету") {world.inspectBody(colony.bodyId)}
        }
    }
    val candidate=world.bodies.firstOrNull {it.id==world.selectedBody?.id}
        ?: world.bodies.filter {it.stellarClass==StellarClass.PLANET}.maxByOrNull {it.mass}
    if(candidate!=null)Action("Заселить мир №${candidate.id}") {
        message=if(world.seedCivilization(candidate.id))"Первые поселенцы появились на №${candidate.id}"
            else "Мир уже занят, непригоден для жизни или жизнь выключена. Выбери другой объект во вкладке «Наука»."
    }
    if(message.isNotEmpty())Text(message,color=AX_ACCENT,fontSize=12.sp,lineHeight=18.sp)
    if(life.history.isNotEmpty()) {
        Text("БОРТОВОЙ ЖУРНАЛ",color=AX_GRAY,fontSize=10.sp,letterSpacing=1.5.sp)
        life.history.asReversed().take(10).forEach {Text(it,color=AX_GRAY,fontSize=12.sp,lineHeight=18.sp)}
    }
    Text("Население и корабли — условный слой агентов, а не отдельная гравитирующая масса. Заряды тратят ресурсы и разрушают или отклоняют реальные частицы астероида; попадание не гарантирует спасение.",
        color=AX_GRAY,fontSize=11.sp,lineHeight=17.sp)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun UniverseCyclePanel(world:CosmicWorld,onPersist:()->Unit) {
    Toggle("Новая вселенная каждый час","В начале местного часа выбирается следующая сцена и симуляция начинается заново",world.hourlyScenes) {
        world.configureCosmos(hourly=it);onPersist()
    }
    Toggle("Палитра сценария","Подбирать мягкий цвет акцента к текущей системе",world.matchSceneAccent) {
        world.configureCosmos(accent=it);onPersist()
    }
    if(world.hourlyScenes) {
        Text("Сценарии в цикле · ${world.scenePool.size}",color=AX_WHITE,fontSize=13.sp)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(5.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
            CosmicPreset.entries.forEach {preset -> Choice(preset.title,preset in world.scenePool) {
                val pool=if(preset in world.scenePool)world.scenePool-preset else world.scenePool+preset
                if(pool.isNotEmpty()){world.configureCosmos(pool=pool);onPersist()}
            } }
        }
        Text("В фоне работа останавливается. При возвращении после смены часа загрузится одна новая сцена. Пауза сохраняется; уже накопленный таймер редких катастроф не сбрасывается.",
            color=AX_GRAY,fontSize=11.sp,lineHeight=17.sp)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PhenomenaPanel(world:CosmicWorld,onPersist:()->Unit) {
    NoteCard("Редкие большие истории") {
        Toggle("Чрезвычайно редкие катастрофы","Отдельный таймер активного времени; требует включённых автоматических событий",world.rareCatastrophes) {
            world.configureCosmos(rare=it);onPersist()
        }
        FlowRow(horizontalArrangement=Arrangement.spacedBy(5.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
            CatastropheRate.entries.forEach {rate -> Choice(rate.title,world.catastropheRate==rate) {
                world.configureCosmos(rate=rate);onPersist()
            } }
        }
        Text("При обычной частоте среднее ожидание отдельного гамма-всплеска — 4 активных часа, гигантского потока — 8 часов, далёкого взрыва скопления — 18 часов. Событие может произойти раньше или позже; это вероятности, не расписание. Пауза и фон не накапливают риск.",
            color=AX_GRAY,fontSize=11.sp,lineHeight=17.sp)
    }
    Text("ВЫЗВАТЬ КАТАСТРОФУ ВРУЧНУЮ",color=AX_GRAY,fontSize=10.sp,letterSpacing=1.sp)
    var message by remember {mutableStateOf("")}
    extremePhenomena.forEach {item ->
        Action("✧  ${item.title}") {world.trigger(item.kind,CosmicEventIntensity.CATASTROPHIC);message=item.title}
        Text(item.description,color=AX_GRAY,fontSize=11.sp,lineHeight=17.sp)
    }
    Action("⌁  Гравитационное микролинзирование") {world.trigger(CosmicEventKind.MICROLENSING);message="Проекционный эффект линзы"}
    Action("ψ  Квантовая когерентность") {world.trigger(CosmicEventKind.QUANTUM_COHERENCE);message="Двухуровневый эксперимент"}
    if(message.isNotEmpty())Text(message,color=AX_LILAC,fontSize=12.sp)
}

@Composable
internal fun QuantumPanel(world:CosmicWorld,onPersist:()->Unit) {
    val q=world.quantumReading
    val probability by animateFloatAsState(q.leftProbability.toFloat(),
        animationSpec=tween(if(world.reducedMotion)0 else 130),label="Born probability")
    NoteCard("ψ · квантовые берега") {
        Toggle("Квантовый эксперимент","Видимый в квантовой сцене и во время когерентности",world.quantumEnabled) {
            world.configureCosmos(quantumOn=it);onPersist()
        }
        Text("P(лево) = ${number(q.leftProbability,3)} · P(право) = ${number(1-q.leftProbability,3)}",
            color=AX_ACCENT,fontSize=12.sp,fontFamily=FontFamily.Monospace)
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(8.dp)).background(AX_TILE)) {
            Box(Modifier.fillMaxWidth(probability.coerceIn(0f,1f)).fillMaxHeight().background(AX_LILAC))
        }
        Text("Когерентность ${number(q.coherence,3)} · измерений ${q.measurements} · слева ${q.leftResults}",
            color=AX_GRAY,fontSize=11.sp)
        Text("Связь уровней · ${number(world.quantumCoupling.toDouble(),2)}",color=AX_WHITE,fontSize=12.sp)
        Slider(value=world.quantumCoupling,onValueChange={world.configureCosmos(coupling=it);onPersist()},valueRange=.1f..3f)
        Text("Декогеренция · ${number(world.quantumDephasing.toDouble(),2)}",color=AX_WHITE,fontSize=12.sp)
        Slider(value=world.quantumDephasing,onValueChange={world.configureCosmos(decoherence=it);onPersist()},valueRange=0f..2f)
        Row(horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            Choice("Измерить",false,Modifier.weight(1f)){world.measureQuantum()}
            Choice("Суперпозиция",false,Modifier.weight(1f)){world.superposeQuantum()}
        }
        q.lastResult?.let {Text("Результат: ${if(it==0)"левый уровень" else "правый уровень"}",color=AX_LILAC,fontSize=12.sp)}
        Text("Двухуровневая модель: унитарные осцилляции Раби, дефазировка и измерение по правилу Борна. Она не заставляет планеты туннелировать и не подменяет классическую гравитацию.",
            color=AX_GRAY,fontSize=11.sp,lineHeight=17.sp)
    }
}
