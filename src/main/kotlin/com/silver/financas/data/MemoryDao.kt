package com.silver.financas.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Implementação do FinanceDao em memória (versão Windows).
 * Toda alteração chama [onChange], que grava o arquivo de dados.
 */
class MemoryDao(private val onChange: () -> Unit) : FinanceDao {
    private val acc = MutableStateFlow<List<Account>>(emptyList())
    private val crd = MutableStateFlow<List<CreditCard>>(emptyList())
    private val cat = MutableStateFlow<List<Category>>(emptyList())
    private val txs = MutableStateFlow<List<Txn>>(emptyList())
    private val bud = MutableStateFlow<List<Budget>>(emptyList())
    private val rul = MutableStateFlow<List<CategoryRule>>(emptyList())
    private val grp = MutableStateFlow<List<RecurringGroup>>(emptyList())
    private val lock = Any()

    private fun <T> MutableStateFlow<List<T>>.edit(f: (List<T>) -> List<T>) {
        synchronized(lock) { value = f(value) }
        onChange()
    }

    /** Insere/substitui por id; id 0 recebe um novo id. */
    private fun <T> upsertById(flow: MutableStateFlow<List<T>>, item: T, idOf: (T) -> Long, withId: (T, Long) -> T): Long {
        var newId = 0L
        flow.edit { list ->
            val id = idOf(item).takeIf { it > 0 } ?: ((list.maxOfOrNull(idOf) ?: 0L) + 1)
            newId = id
            val fixed = withId(item, id)
            val i = list.indexOfFirst { idOf(it) == id }
            if (i >= 0) list.toMutableList().also { it[i] = fixed } else list + fixed
        }
        return newId
    }

    fun replaceAll(
        a: List<Account>, c: List<CreditCard>, ca: List<Category>, t: List<Txn>,
        b: List<Budget>, r: List<CategoryRule>, g: List<RecurringGroup>
    ) {
        synchronized(lock) {
            acc.value = a; crd.value = c; cat.value = ca; txs.value = t; bud.value = b; rul.value = r; grp.value = g
        }
        onChange()
    }

    // ---- fluxos
    override fun accounts(): Flow<List<Account>> = acc.map { l -> l.sortedWith(compareBy<Account>({ it.archived }, { it.name.lowercase() })) }
    override fun cards(): Flow<List<CreditCard>> = crd.map { l -> l.sortedWith(compareBy<CreditCard>({ it.archived }, { it.name.lowercase() })) }
    override fun categories(): Flow<List<Category>> = cat.map { l -> l.sortedBy { it.name.lowercase() } }
    override fun txns(): Flow<List<Txn>> = txs.map { l -> l.sortedWith(compareByDescending<Txn> { it.date }.thenByDescending { it.id }) }
    override fun budgets(): Flow<List<Budget>> = bud
    override fun rules(): Flow<List<CategoryRule>> = rul

    // ---- listas
    override suspend fun accountsList() = acc.value
    override suspend fun cardsList() = crd.value
    override suspend fun categoriesList() = cat.value
    override suspend fun txnsList() = txs.value
    override suspend fun budgetsList() = bud.value
    override suspend fun rulesList() = rul.value
    override suspend fun groupsList() = grp.value
    override suspend fun countCategories() = cat.value.size
    override suspend fun countTxnsForAccount(id: Long) = txs.value.count { it.accountId == id || it.toAccountId == id }
    override suspend fun countTxnsForCard(id: Long) = txs.value.count { it.cardId == id }
    override suspend fun txnsInGroup(groupId: String) = txs.value.filter { it.groupId == groupId }.sortedBy { it.date }
    override suspend fun txn(id: Long) = txs.value.firstOrNull { it.id == id }

    // ---- escrita
    override suspend fun upsertAccount(a: Account) = upsertById(acc, a, { it.id }) { x, id -> x.copy(id = id) }
    override suspend fun upsertCard(c: CreditCard) = upsertById(crd, c, { it.id }) { x, id -> x.copy(id = id) }
    override suspend fun upsertCategory(c: Category) = upsertById(cat, c, { it.id }) { x, id -> x.copy(id = id) }
    override suspend fun upsertBudget(b: Budget) = upsertById(bud, b, { it.id }) { x, id -> x.copy(id = id) }
    override suspend fun upsertRule(r: CategoryRule) = rul.edit { l -> l.filter { it.keyword != r.keyword } + r }
    override suspend fun upsertGroup(g: RecurringGroup) = grp.edit { l -> l.filter { it.groupId != g.groupId } + g }
    override suspend fun insertTxn(t: Txn) = upsertById(txs, t, { it.id }) { x, id -> x.copy(id = id) }

    override suspend fun insertTxns(list: List<Txn>) {
        txs.edit { cur ->
            var next = (cur.maxOfOrNull { it.id } ?: 0L) + 1
            val byId = LinkedHashMap<Long, Txn>()
            cur.forEach { byId[it.id] = it }
            for (t in list) {
                val id = if (t.id > 0) t.id else next++
                if (id >= next) next = id + 1
                byId[id] = t.copy(id = id)
            }
            byId.values.toList()
        }
    }

    override suspend fun updateTxn(t: Txn) = txs.edit { l -> l.map { if (it.id == t.id) t else it } }
    override suspend fun updateTxns(list: List<Txn>) {
        val m = list.associateBy { it.id }
        txs.edit { l -> l.map { m[it.id] ?: it } }
    }

    private fun <T> addAll(flow: MutableStateFlow<List<T>>, items: List<T>, idOf: (T) -> Long) =
        flow.edit { l -> val ids = items.map(idOf).toSet(); l.filter { idOf(it) !in ids } + items }

    override suspend fun insertAccounts(l: List<Account>) = addAll(acc, l) { it.id }
    override suspend fun insertCards(l: List<CreditCard>) = addAll(crd, l) { it.id }
    override suspend fun insertCategories(l: List<Category>) {
        // categorias novas (id 0) recebem ids sequenciais
        cat.edit { cur ->
            var next = (cur.maxOfOrNull { it.id } ?: 0L) + 1
            val fixed = l.map { if (it.id > 0) it else it.copy(id = next++) }
            val ids = fixed.map { it.id }.toSet()
            cur.filter { it.id !in ids } + fixed
        }
    }
    override suspend fun insertBudgets(l: List<Budget>) = addAll(bud, l) { it.id }
    override suspend fun insertRules(l: List<CategoryRule>) = rul.edit { cur -> val k = l.map { it.keyword }.toSet(); cur.filter { it.keyword !in k } + l }
    override suspend fun insertGroups(l: List<RecurringGroup>) = grp.edit { cur -> val k = l.map { it.groupId }.toSet(); cur.filter { it.groupId !in k } + l }

    override suspend fun deleteAccount(a: Account) = acc.edit { l -> l.filter { it.id != a.id } }
    override suspend fun deleteCard(c: CreditCard) = crd.edit { l -> l.filter { it.id != c.id } }
    override suspend fun deleteCategory(c: Category) = cat.edit { l -> l.filter { it.id != c.id } }
    override suspend fun deleteBudget(b: Budget) = bud.edit { l -> l.filter { it.id != b.id } }
    override suspend fun deleteTxn(t: Txn) = txs.edit { l -> l.filter { it.id != t.id } }

    override suspend fun deleteGroupFrom(groupId: String, fromDate: Long) =
        txs.edit { l -> l.filterNot { it.groupId == groupId && it.date >= fromDate } }
    override suspend fun deleteGroup(groupId: String) = txs.edit { l -> l.filterNot { it.groupId == groupId } }
    override suspend fun clearCategory(id: Long) = txs.edit { l -> l.map { if (it.categoryId == id) it.copy(categoryId = null) else it } }
    override suspend fun clearParent(id: Long) = cat.edit { l -> l.map { if (it.parentId == id) it.copy(parentId = null) else it } }
    override suspend fun deleteBudgetsOf(id: Long) = bud.edit { l -> l.filter { it.categoryId != id } }
    override suspend fun deleteRulesOf(id: Long) = rul.edit { l -> l.filter { it.categoryId != id } }

    override suspend fun clearAccounts() = acc.edit { emptyList() }
    override suspend fun clearCards() = crd.edit { emptyList() }
    override suspend fun clearCategories() = cat.edit { emptyList() }
    override suspend fun clearTxns() = txs.edit { emptyList() }
    override suspend fun clearBudgets() = bud.edit { emptyList() }
    override suspend fun clearRules() = rul.edit { emptyList() }
    override suspend fun clearGroups() = grp.edit { emptyList() }
}
