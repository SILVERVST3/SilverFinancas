package com.silver.financas.util

import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.security.MessageDigest
import java.util.Properties

/** Abre a janela "Abrir arquivo" do Windows. */
fun pickFile(title: String, extensions: List<String> = emptyList()): File? {
    val d = FileDialog(null as Frame?, title, FileDialog.LOAD)
    if (extensions.isNotEmpty()) {
        d.file = extensions.joinToString(";") { "*.$it" }   // filtro no Windows
        d.setFilenameFilter { _, name -> extensions.any { name.lowercase().endsWith(".$it") } }
    }
    d.isVisible = true
    val name = d.file ?: return null
    return File(d.directory, name)
}

/** Abre a janela "Salvar como" do Windows. */
fun saveFile(title: String, defaultName: String): File? {
    val d = FileDialog(null as Frame?, title, FileDialog.SAVE)
    d.file = defaultName
    d.isVisible = true
    val name = d.file ?: return null
    val ext = defaultName.substringAfterLast('.', "")
    val fixed = if (ext.isNotEmpty() && !name.lowercase().endsWith(".$ext")) "$name.$ext" else name
    return File(d.directory, fixed)
}

fun openExternally(file: File) {
    runCatching { if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(file) }
}

fun sha256(s: String): String =
    MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

/** Preferências gravadas em %APPDATA%\SilverFinancas\config.properties */
class Prefs(dir: File) {
    private val file = File(dir, "config.properties")
    private val p = Properties().apply { if (file.exists()) file.inputStream().use { load(it) } }
    private fun save() = file.outputStream().use { p.store(it, "SilverFinancas") }
    private fun int(k: String, d: Int) = p.getProperty(k)?.toIntOrNull() ?: d
    private fun bool(k: String, d: Boolean) = p.getProperty(k)?.toBooleanStrictOrNull() ?: d
    private fun set(k: String, v: Any?) { if (v == null) p.remove(k) else p.setProperty(k, v.toString()); save() }

    var themeMode: Int get() = int("theme", 0); set(v) = set("theme", v)
    var hideOnStart: Boolean get() = bool("hide", false); set(v) = set("hide", v)
    var alertsEnabled: Boolean get() = bool("alerts", true); set(v) = set("alerts", v)
    var budgetAlerts: Boolean get() = bool("budget_alerts", true); set(v) = set("budget_alerts", v)
    /** hash SHA-256 do PIN; null = sem bloqueio */
    var pinHash: String? get() = p.getProperty("pin"); set(v) = set("pin", v)
}
