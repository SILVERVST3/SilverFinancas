package com.silver.financas.util

import com.silver.financas.data.ImportRow
import java.nio.charset.Charset

/** Lê extratos bancários em OFX ou CSV (Nubank, Inter, Itaú, BB, Caixa, Bradesco, Santander...). */
object StatementParser {

    fun decode(bytes: ByteArray): String {
        val utf = String(bytes, Charsets.UTF_8)
        return if (utf.contains('�')) String(bytes, Charset.forName("ISO-8859-1")) else utf
    }

    fun parse(bytes: ByteArray): List<ImportRow> {
        val text = decode(bytes).removePrefix("﻿")
        return if (text.contains("<OFX", ignoreCase = true) || text.contains("<STMTTRN", ignoreCase = true))
            parseOfx(text) else parseCsv(text)
    }

    // ---------------------------------------------------------------- OFX
    fun parseOfx(text: String): List<ImportRow> {
        val rows = mutableListOf<ImportRow>()
        val blocks = Regex("<STMTTRN>(.*?)(</STMTTRN>|(?=<STMTTRN>)|(?=</BANKTRANLIST>))",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)).findAll(text)
        for (b in blocks) {
            val body = b.groupValues[1]
            fun tag(name: String): String? =
                Regex("<$name>([^<\\r\\n]*)", RegexOption.IGNORE_CASE).find(body)?.groupValues?.get(1)?.trim()
            val amount = tag("TRNAMT")?.let { parseMoney(it) } ?: continue
            val dateRaw = tag("DTPOSTED") ?: continue
            val date = parseDateFlexible(dateRaw.take(8)) ?: continue
            val desc = listOfNotNull(tag("MEMO"), tag("NAME")).firstOrNull { it.isNotBlank() } ?: "Lançamento"
            rows += ImportRow(date, cleanDesc(desc), amount, tag("FITID"))
        }
        return rows
    }

    // ---------------------------------------------------------------- CSV
    private fun splitLine(line: String, sep: Char): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && quoted && i + 1 < line.length && line[i + 1] == '"' -> { cur.append('"'); i++ }
                c == '"' -> quoted = !quoted
                c == sep && !quoted -> { out += cur.toString().trim(); cur.clear() }
                else -> cur.append(c)
            }
            i++
        }
        out += cur.toString().trim()
        return out
    }

    fun parseCsv(text: String): List<ImportRow> {
        val lines = text.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()
        val sample = lines.take(5).joinToString("\n")
        val sep = listOf(';', ',', '\t').maxByOrNull { s -> sample.count { it == s } } ?: ';'
        val first = splitLine(lines[0], sep).map { normalize(it) }

        var dateCol = first.indexOfFirst { it.startsWith("data") || it == "date" || it.contains("dt ") }
        var descCol = first.indexOfFirst {
            it.contains("descri") || it.contains("historico") || it.contains("lancamento") ||
                it == "title" || it.contains("titulo") || it.contains("estabelecimento") || it == "description" || it.contains("memo")
        }
        var valCol = first.indexOfFirst { it.contains("valor") || it == "amount" || it == "value" || it.contains("quantia") }
        val creditCol = first.indexOfFirst { it.contains("credito") || it == "entrada" }
        val debitCol = first.indexOfFirst { it.contains("debito") || it == "saida" }
        val hasHeader = dateCol >= 0 || valCol >= 0 || descCol >= 0

        val dataLines = if (hasHeader) lines.drop(1) else lines
        if (!hasHeader) {
            val cols = splitLine(dataLines.first(), sep)
            dateCol = cols.indexOfFirst { parseDateFlexible(it) != null }
            valCol = cols.indexOfLast { parseMoney(it) != null && parseDateFlexible(it) == null }
            descCol = cols.indices.firstOrNull { it != dateCol && it != valCol && parseMoney(cols[it]) == null } ?: -1
        }

        val rows = mutableListOf<ImportRow>()
        for (line in dataLines) {
            val c = splitLine(line, sep)
            val date = c.getOrNull(dateCol)?.let { parseDateFlexible(it) } ?: continue
            val amount: Long = when {
                valCol >= 0 -> c.getOrNull(valCol)?.let { parseMoney(it) }
                creditCol >= 0 || debitCol >= 0 -> {
                    val cr = c.getOrNull(creditCol)?.let { parseMoney(it) } ?: 0L
                    val db = c.getOrNull(debitCol)?.let { parseMoney(it) } ?: 0L
                    cr - kotlin.math.abs(db)
                }
                else -> null
            } ?: continue
            val desc = c.getOrNull(descCol)?.takeIf { it.isNotBlank() } ?: "Lançamento"
            rows += ImportRow(date, cleanDesc(desc), amount)
        }
        return rows
    }

    private fun cleanDesc(s: String) = s.replace("\\s+".toRegex(), " ").trim().take(120)
}
