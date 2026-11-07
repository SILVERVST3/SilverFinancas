package com.silver.financas.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.silver.financas.data.*
import com.silver.financas.util.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

/** Mesma API do ViewModel do Android, adaptada para o Windows. */
class FinanceViewModel(val store: Store) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val dao = store.dao
    val repo = Repository(dao)
    val prefs = Prefs(store.dir)

    private fun <T> state(flow: Flow<List<T>>): StateFlow<List<T>> =
        flow.stateIn(scope, SharingStarted.Eagerly, emptyList())

    val accounts = state(dao.accounts())
    val cards = state(dao.cards())
    val categories = state(dao.categories())
    val txns = state(dao.txns())
    val budgets = state(dao.budgets())
    val rules = state(dao.rules())

    val month = MutableStateFlow(YearMonth.now())

    var hide by mutableStateOf(prefs.hideOnStart)
    var themeMode by mutableStateOf(prefs.themeMode)
    var message by mutableStateOf<String?>(null)

    init {
        runBlocking {
            repo.seedIfNeeded()
            repo.extendFixed()
        }
    }

    private fun io(block: suspend () -> Unit) = scope.launch {
        try { withContext(Dispatchers.IO) { block() } } catch (e: Exception) { message = "Erro: ${e.message}" }
    }
    private fun say(s: String) { scope.launch { message = s } }

    fun setTheme(m: Int) { themeMode = m; prefs.themeMode = m }

    fun addTxn(d: TxnDraft) = io { repo.addTransaction(d, cards.value) }
    fun updateTxn(original: Txn, edited: Txn, scope: EditScope) = io { repo.updateTransaction(original, edited, scope, cards.value) }
    fun deleteTxn(t: Txn, scope: EditScope) = io { repo.deleteTransaction(t, scope) }
    fun togglePaid(t: Txn) = io { repo.setPaid(t, !t.paid) }
    suspend fun getTxn(id: Long): Txn? = dao.txn(id)

    fun saveAccount(a: Account) = io { dao.upsertAccount(a) }
    fun deleteAccount(a: Account) = io { if (!repo.deleteOrArchiveAccount(a)) say("A conta tem lançamentos e foi arquivada.") }
    fun adjustBalance(a: Account, current: Long, wanted: Long) = io { repo.adjustBalance(a, current, wanted) }

    fun saveCard(c: CreditCard) = io { dao.upsertCard(c) }
    fun deleteCard(c: CreditCard) = io { if (!repo.deleteOrArchiveCard(c)) say("O cartão tem lançamentos e foi arquivado.") }
    fun payInvoice(card: CreditCard, month: Int, accountId: Long, amount: Long, date: LocalDate) =
        io { repo.payInvoice(card, month, accountId, amount, date) }

    fun saveCategory(c: Category) = io { dao.upsertCategory(c) }
    fun deleteCategory(c: Category) = io { repo.deleteCategory(c) }
    fun saveBudget(b: Budget) = io { dao.upsertBudget(b) }
    fun deleteBudget(b: Budget) = io { dao.deleteBudget(b) }

    fun suggestCategory(description: String, income: Boolean): Long? =
        repo.suggestCategory(description, rules.value, categories.value, income)

    fun importRows(rows: List<ImportRow>, accountId: Long?, card: CreditCard?, positiveIsExpense: Boolean, done: () -> Unit) =
        scope.launch {
            try {
                withContext(Dispatchers.IO) { repo.importRows(rows, accountId, card, positiveIsExpense) }
                message = "${rows.count { it.selected }} lançamentos importados"
                done()
            } catch (e: Exception) { message = "Erro ao importar: ${e.message}" }
        }

    fun exportBackup(file: File) = io { file.writeText(Backup.export(dao), Charsets.UTF_8); say("Backup salvo em ${file.name}") }
    fun exportCsv(file: File) = io { file.writeText(Backup.exportCsv(dao), Charsets.UTF_8); say("Planilha salva em ${file.name}") }
    fun restoreBackup(file: File) = io { Backup.restore(dao, file.readText(Charsets.UTF_8)); say("Backup restaurado") }
}
