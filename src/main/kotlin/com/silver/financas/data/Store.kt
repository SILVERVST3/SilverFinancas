package com.silver.financas.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Armazena tudo em %APPDATA%\SilverFinancas\dados.json — no mesmo formato do backup do celular.
 */
class Store {
    val dir: File = File(System.getenv("APPDATA") ?: System.getProperty("user.home"), "SilverFinancas").apply { mkdirs() }
    val file = File(dir, "dados.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val saves = Channel<Unit>(Channel.CONFLATED)
    @Volatile private var loading = true

    val dao = MemoryDao { if (!loading) saves.trySend(Unit) }

    init {
        runBlocking {
            if (file.exists()) {
                try {
                    Backup.restore(dao, file.readText(Charsets.UTF_8))
                } catch (e: Exception) {
                    // arquivo corrompido: guarda uma cópia e começa do zero
                    file.copyTo(File(dir, "dados-com-erro-${System.currentTimeMillis()}.json"), overwrite = true)
                }
            }
        }
        loading = false
        scope.launch {
            for (u in saves) {
                delay(300)
                saveNow()
            }
        }
    }

    suspend fun saveNow() {
        val json = Backup.export(dao)
        val tmp = File(dir, "dados.tmp")
        tmp.writeText(json, Charsets.UTF_8)
        if (file.exists()) file.copyTo(File(dir, "dados.bak"), overwrite = true)
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    fun flush() = runBlocking { saveNow() }
}
