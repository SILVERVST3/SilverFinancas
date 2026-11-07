package com.silver.financas.util

import java.text.NumberFormat
import java.text.Normalizer
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

val BR: Locale = Locale("pt", "BR")
private val currency: NumberFormat = NumberFormat.getCurrencyInstance(BR)

fun money(cents: Long): String = currency.format(cents / 100.0).replace(' ', ' ')

fun moneyHidden(cents: Long, hide: Boolean): String = if (hide) "R$ •••••" else money(cents)

/** "1234,56" / "1.234,56" / "-1234.56" / "R$ 10" -> centavos */
fun parseMoney(raw: String): Long? {
    var s = raw.trim().replace("R$", "").replace(" ", "").replace(" ", "")
    if (s.isEmpty()) return null
    var negative = false
    if (s.startsWith("(") && s.endsWith(")")) { negative = true; s = s.substring(1, s.length - 1) }
    if (s.startsWith("-")) { negative = true; s = s.substring(1) }
    if (s.endsWith("-")) { negative = true; s = s.dropLast(1) }
    if (s.startsWith("+")) s = s.substring(1)
    val lastComma = s.lastIndexOf(',')
    val lastDot = s.lastIndexOf('.')
    s = when {
        // formato brasileiro: 1.234,56
        lastComma > lastDot -> s.replace(".", "").replace(',', '.')
        // formato americano 1,234.56 / 1234.56
        lastDot > lastComma && lastComma >= 0 -> s.replace(",", "")
        lastDot >= 0 -> {
            val dots = s.count { it == '.' }
            val decimals = s.length - lastDot - 1
            if (dots > 1 || decimals == 3) s.replace(".", "") else s   // "1.234" = milhar
        }
        else -> s
    }
    val v = s.toBigDecimalOrNull() ?: return null
    val cents = v.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).toLong()
    return if (negative) -cents else cents
}

private val dayFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy", BR)
private val shortFmt = DateTimeFormatter.ofPattern("dd MMM", BR)

fun LocalDate.br(): String = format(dayFmt)
fun LocalDate.short(): String = format(shortFmt)
fun Long.toDate(): LocalDate = LocalDate.ofEpochDay(this)

fun YearMonth.label(): String =
    month.getDisplayName(TextStyle.FULL, BR).replaceFirstChar { it.uppercase() } + " " + year

fun YearMonth.shortLabel(): String =
    month.getDisplayName(TextStyle.SHORT, BR).replace(".", "").replaceFirstChar { it.uppercase() } +
        "/" + (year % 100).toString().padStart(2, '0')

fun YearMonth.key(): Int = year * 100 + monthValue
fun Int.toYearMonth(): YearMonth = YearMonth.of(this / 100, this % 100)

fun LocalDate.weekdayLabel(): String =
    dayOfWeek.getDisplayName(TextStyle.FULL, BR).replaceFirstChar { it.uppercase() }

fun dayInMonth(ym: YearMonth, day: Int): LocalDate = ym.atDay(day.coerceIn(1, ym.lengthOfMonth()))

/** minúsculas, sem acentos, sem números/símbolos */
fun normalize(s: String): String {
    val n = Normalizer.normalize(s.lowercase(BR), Normalizer.Form.NFD)
        .replace("\\p{M}+".toRegex(), "")
    return n.replace("[^a-z ]".toRegex(), " ").replace("\\s+".toRegex(), " ").trim()
}

fun parseDateFlexible(raw: String): LocalDate? {
    val s = raw.trim().take(10)
    val patterns = listOf("dd/MM/yyyy", "yyyy-MM-dd", "dd-MM-yyyy", "dd.MM.yyyy", "yyyy/MM/dd")
    for (p in patterns) {
        try { return LocalDate.parse(s, DateTimeFormatter.ofPattern(p)) } catch (_: Exception) {}
    }
    try { return LocalDate.parse(raw.trim().take(8), DateTimeFormatter.ofPattern("dd/MM/yy")) } catch (_: Exception) {}
    try { return LocalDate.parse(raw.trim().take(8), DateTimeFormatter.BASIC_ISO_DATE) } catch (_: Exception) {}
    return null
}
