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
    data class Calculate(val expression: String) : AxCommand()
    data class Note(val value: String) : AxCommand()
    data object Notes : AxCommand()
    data object Clear : AxCommand()
    data object Home : AxCommand()
    data object Apps : AxCommand()
    data object Physics : AxCommand()
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
            "physics · spawn · reset · battery · time\n" +
            "note <текст> · notes · wifi · bluetooth\n" +
            "display · notifications · clear · exit"
        )
        "ls", "apps", "atlas" -> AxCommand.Apps
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
        "spawn", "create" -> AxCommand.Spawn
        "reset", "restart" -> AxCommand.Reset
        "time", "date" -> AxCommand.Clock
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
