package dev.axiom.launcher

import kotlin.math.pow

/**
 * AXIOM's terminal is a SAFE command palette, NOT a Linux shell.
 * Never executes processes, arbitrary scripts, root or ADB commands.
 */
internal sealed class AxCommand {
    data class Output(val value: String) : AxCommand()
    data class Open(val name: String) : AxCommand()
    data class Find(val name: String) : AxCommand()
    data class Gravity(val value: Float) : AxCommand()
    data class Event(val kind: CosmicEventKind?) : AxCommand()
    data class Model(val kind: MathKind?) : AxCommand()
    data class Lab(val kind: LabKind?) : AxCommand()
    data class TimeScale(val speed: Float) : AxCommand()
    data class Rewind(val seconds: Int) : AxCommand()
    data class Theme(val mode: AxiomThemeMode) : AxCommand()
    data object SpawnBinary : AxCommand()
    data object Field : AxCommand()
    data object Timeline : AxCommand()
    data class Depth(val delta: Float) : AxCommand()
    data class Calculate(val expression: String) : AxCommand()
    data class Note(val value: String) : AxCommand()
    data object Notes : AxCommand()
    data object Clear : AxCommand()
    data object Home : AxCommand()
    data object Apps : AxCommand()
    data object Physics : AxCommand()
    data object Metrics : AxCommand()
    data object Spawn : AxCommand()
    data object Reset : AxCommand()
    data object Clock : AxCommand()
    data object Battery : AxCommand()
    data class AndroidSettings(val page: String) : AxCommand()
}

internal fun parseAxCommand(input: String): AxCommand {
    val text = input.trim().removePrefix(">").trim()
    if (text.isEmpty()) return AxCommand.Output("Введите команду. help — список возможностей.")
    val parts = text.split(Regex("\\s+"), limit = 2)
    val command = parts.first().lowercase()
    val arg = parts.getOrElse(1) { "" }.trim()
    return when (command) {
        "help", "?", "man" -> AxCommand.Output(
            "help · ls · find <имя> · open <имя>\n" +
            "calc <выражение> · gravity <0.25..2.2>\n" +
            "physics · fps · spawn · binary · reset · battery · clock\n" +
            "speed <0|.25|.5|1|2> · pause · resume · rewind [1..8]\n" +
            "lab <wave|neural|life|julia|lorenz|pendulum|...>\n" +
            "theme <dark|light|auto> · field · event · depth\n" +
            "note <текст> · notes · wifi · bluetooth\n" +
            "display · notifications · clear · exit"
        )
        "ls", "apps", "atlas" -> AxCommand.Apps
        "labs", "experiments" -> AxCommand.Output(
            "Лаборатории: " + LabKind.entries.joinToString(", ") {
                it.name.lowercase()
            } + "\nПример: lab neural"
        )
        "lab", "portal", "experiment" -> {
            val kind = if(arg.isBlank()) null else LabKind.entries.firstOrNull {
                it.name.equals(arg, ignoreCase=true)
            }
            if(arg.isNotBlank() && kind == null)
                AxCommand.Output("Неизвестный эксперимент. Введи labs.")
            else AxCommand.Lab(kind)
        }
        "speed", "timescale" -> {
            val v = arg.replace(',', '.').toFloatOrNull()
            if (v == null || v !in 0f..2f || !v.isFinite())
                AxCommand.Output("Укажи скорость 0..2. Пример: speed 0.5")
            else AxCommand.TimeScale(v)
        }
        "pause", "freeze" -> AxCommand.TimeScale(0f)
        "play", "resume" -> AxCommand.TimeScale(1f)
        "rewind" -> {
            val seconds = if(arg.isBlank()) 3 else arg.toIntOrNull()
            if(seconds == null || seconds !in 1..8)
                AxCommand.Output("Повтор доступен на 1–8 секунд")
            else AxCommand.Rewind(seconds)
        }
        "theme", "appearance" -> {
            val next = when(arg.lowercase()){
                "dark", "black", "night" -> AxiomThemeMode.DARK
                "light", "white", "day" -> AxiomThemeMode.LIGHT
                "auto", "automatic" -> AxiomThemeMode.AUTO
                else -> return AxCommand.Output("Пример: theme auto | dark | light")
            }
            AxCommand.Theme(next)
        }
        "binary", "double-star" -> AxCommand.SpawnBinary
        "field", "brush" -> AxCommand.Field
        "models", "maths" -> AxCommand.Output(
            "${MathKind.entries.size} визуализаций: " + MathKind.entries.joinToString(" · ") {
                it.name.lowercase()
            } + "\nЗапуск: model attention или model для случайной."
        )
        "model", "study", "visualize" -> {
            if (arg.isBlank()) AxCommand.Model(null)
            else {
                val kind = MathKind.entries.firstOrNull {
                    it.name.equals(arg, ignoreCase = true)
                }
                if (kind == null) AxCommand.Output("Неизвестная модель. Введи models.")
                else AxCommand.Model(kind)
            }
        }
        "timeline", "history" -> AxCommand.Timeline
        "events", "scenarios" -> AxCommand.Output(
            "Сценарии: meteor, lens, nova, pulsar, wormhole, nebula, " +
            "encounter, filament, radiation, ring, reaction, blackhole, " +
            "eclipse, web, phasetide, mega, merger, void, caustic (крайне редкие), " +
            "echo, capture, jet, resonance, nursery, darkflow, geometry, flare, dualring, cascade. " +
            "Введи event <сценарий> или event для случайного."
        )
        "event", "scenario" -> {
            val kind = when (arg.lowercase()) {
                "meteor", "meteors", "comet" -> CosmicEventKind.METEORS
                "neural", "ml", "network" -> return AxCommand.Model(MathKind.BACKPROP)
                "wave", "fourier", "math" -> return AxCommand.Model(MathKind.FOURIER)
                "lens", "gravity" -> CosmicEventKind.LENS
                "nova", "supernova" -> CosmicEventKind.NOVA
                "pulsar" -> CosmicEventKind.PULSAR
                "wormhole", "portal" -> CosmicEventKind.WORMHOLE
                "nebula", "cloud" -> CosmicEventKind.NEBULA
                "encounter", "binary" -> CosmicEventKind.ENCOUNTER
                "filament", "thread" -> CosmicEventKind.FILAMENT
                "radiation", "burst" -> CosmicEventKind.RADIATION
                "ring", "ringwave" -> CosmicEventKind.RING_WAVE
                "reaction" -> CosmicEventKind.REACTION
                "eclipse", "occultation" -> CosmicEventKind.ECLIPSE
                "web", "cosmicweb" -> CosmicEventKind.COSMIC_WEB
                "phasetide", "phase", "tide" -> CosmicEventKind.PHASE_TIDE
                "echo" -> CosmicEventKind.ECHO_SHELL
                "capture" -> CosmicEventKind.TIDAL_CAPTURE
                "jet", "polarjet" -> CosmicEventKind.POLAR_JET
                "resonance" -> CosmicEventKind.ORBITAL_RESONANCE
                "nursery" -> CosmicEventKind.STELLAR_NURSERY
                "darkflow" -> CosmicEventKind.DARK_FLOW
                "geometry" -> CosmicEventKind.GEOMETRY_SHIFT
                "flare" -> CosmicEventKind.SOLAR_FLARE
                "dualring" -> CosmicEventKind.DUAL_RING
                "cascade" -> CosmicEventKind.PARTICLE_CASCADE
                "mega", "megastructure" -> CosmicEventKind.MEGASTRUCTURE
                "merger", "galaxycollision" -> CosmicEventKind.GALAXY_COLLISION
                "void", "voidbloom" -> CosmicEventKind.VOID_BLOOM
                "caustic" -> CosmicEventKind.GRAVITATIONAL_CAUSTIC
                "blackhole", "singularity", "bh" -> CosmicEventKind.SINGULARITY
                "quiet", "stop" -> CosmicEventKind.QUIET
                "" -> null
                else -> return AxCommand.Output("Неизвестное событие. Введи events.")
            }
            AxCommand.Event(kind)
        }
        "depth", "parallax" -> {
            if (arg.isBlank()) AxCommand.Output("Пример: depth 0.5")
            else {
                val delta = arg.replace(',', '.').toFloatOrNull()
                if (delta == null || delta !in -1f..1f)
                    AxCommand.Output("Глубина: число от -1 до 1.")
                else AxCommand.Depth(delta)
            }
        }
        "find", "search" -> if (arg.isEmpty()) AxCommand.Output("Пример: find telegram")
            else AxCommand.Find(arg)
        "open", "run" -> if (arg.isEmpty()) AxCommand.Output("Пример: open firefox")
            else AxCommand.Open(arg)
        "g", "gravity" -> {
            val n = arg.replace(',', '.').toFloatOrNull()
            if (n == null || n !in .25f..2.2f)
                AxCommand.Output("Допустимая гравитация: от 0.25 до 2.20")
            else AxCommand.Gravity(n)
        }
        "calc", "math", "=", "fx" -> if (arg.isEmpty())
            AxCommand.Output("Пример: calc (3+5)^2 / 4")
            else AxCommand.Calculate(arg)
        "note" -> if (arg.isEmpty()) AxCommand.Output("Пример: note идея для AXIOM")
            else AxCommand.Note(arg.take(500))
        "notes" -> AxCommand.Notes
        "clear", "cls" -> AxCommand.Clear
        "exit", "home", "quit", "cd" -> AxCommand.Home
        "physics", "status" -> AxCommand.Physics
        "fps", "metrics", "profile" -> AxCommand.Metrics
        "spawn", "create" ->
            if (arg.lowercase() in listOf("binary","pair","double"))
                AxCommand.SpawnBinary else AxCommand.Spawn
        "reset", "restart" -> AxCommand.Reset
        "clock", "date" -> AxCommand.Clock
        "time" -> if(arg.isBlank()) AxCommand.Clock
                  else parseAxCommand("speed $arg")
        "battery" -> AxCommand.Battery
        "wifi", "bluetooth", "display", "notifications", "settings" ->
            AxCommand.AndroidSettings(command)
        "sudo", "su", "sh", "bash", "rm", "chmod", "apt", "pacman" ->
            AxCommand.Output("AXIOM — безопасная палитра команд, не оболочка Linux. " +
                "Команды ОС намеренно не исполняются.")
        else -> AxCommand.Output("Неизвестная команда: ${parts.first()}. Введи help.")
    }
}

/** Small bounded recursive descent arithmetic parser, no eval / subprocesses. */
internal fun arithmetic(input: String): Double {
    require(input.length <= 140) { "Слишком длинное выражение" }
    require(input.all { it.isDigit() || it in " .,+-*/^()%" }) { "Недопустимый символ" }
    class Parser(private val s: String) {
        var p = 0
        fun skip() { while (p < s.length && s[p].isWhitespace()) p++ }
        fun eat(c: Char): Boolean {
            skip()
            if (p < s.length && s[p] == c) { p++; return true }
            return false
        }
        fun expr(): Double {
            var value = product()
            while (true) {
                value = when {
                    eat('+') -> value + product()
                    eat('-') -> value - product()
                    else -> return value
                }
            }
        }
        fun product(): Double {
            var value = power()
            while (true) {
                value = when {
                    eat('*') -> value * power()
                    eat('/') -> {
                        val divisor = power()
                        require(divisor != 0.0) { "Деление на ноль" }
                        value / divisor
                    }
                    else -> return value
                }
            }
        }
        fun power(): Double {
            val base = unary()
            return if (eat('^')) base.pow(power()) else base
        }
        fun unary(): Double {
            if (eat('+')) return unary()
            if (eat('-')) return -unary()
            if (eat('(')) {
                val inner = expr()
                require(eat(')')) { "Не закрыта скобка" }
                return inner
            }
            skip()
            val start = p
            while (p < s.length && (s[p].isDigit() || s[p] == '.')) p++
            require(start < p) { "Ожидается число" }
            return s.substring(start, p).toDouble()
        }
        fun finish(): Double {
            val result = expr()
            skip()
            require(p == s.length) { "Некорректный синтаксис" }
            require(result.isFinite()) { "Бесконечный результат" }
            return result
        }
    }
    return Parser(input.replace(',', '.')).finish()
}
