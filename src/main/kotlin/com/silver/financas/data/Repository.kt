package com.silver.financas.data

import com.silver.financas.util.normalize
import com.silver.financas.util.toYearMonth
import com.silver.financas.util.key
import java.time.LocalDate
import java.util.UUID

/** Dados do formulário de lançamento */
data class TxnDraft(
    val type: TxType,
    val amount: Long,
    val description: String,
    val date: LocalDate,
    val categoryId: Long?,
    val accountId: Long?,
    val toAccountId: Long?,
    val cardId: Long?,
    val paid: Boolean,
    val recurrence: Recurrence,
    val frequency: Frequency,
    val count: Int,              // parcelas (INSTALLMENT) ou repetições (FIXED; 0 = sem fim)
    val amountIsTotal: Boolean,  // parcelado: valor digitado é o total
    val tags: String,
    val notes: String,
    val attachment: String?
)

enum class EditScope { ONLY_THIS, THIS_AND_NEXT, ALL }

data class ImportRow(
    val date: LocalDate,
    val description: String,
    val amount: Long,            // com sinal: + entrada, - saída
    val fitId: String? = null,
    var categoryId: Long? = null,
    var selected: Boolean = true,
    var duplicate: Boolean = false
)

class Repository(val dao: FinanceDao) {

    companion object {
        const val MONTHS_AHEAD = 12L

        fun step(base: LocalDate, f: Frequency, i: Int): LocalDate = when (f) {
            Frequency.WEEKLY -> base.plusWeeks(i.toLong())
            Frequency.BIWEEKLY -> base.plusWeeks(2L * i)
            Frequency.MONTHLY -> base.plusMonths(i.toLong())
            Frequency.BIMONTHLY -> base.plusMonths(2L * i)
            Frequency.QUARTERLY -> base.plusMonths(3L * i)
            Frequency.SEMIANNUAL -> base.plusMonths(6L * i)
            Frequency.YEARLY -> base.plusYears(i.toLong())
        }

        /** Chave usada para aprender categorias: primeiras 3 palavras significativas */
        fun ruleKey(description: String): String =
            normalize(description).split(" ").filter { it.length > 2 }.take(3).joinToString(" ")

        val defaultKeywords: List<Pair<List<String>, String>> = listOf(
            listOf("ifood", "restaurante", "lanchonete", "padaria", "pizza", "burger", "mcdonald", "bk ", "subway", "rappi", "cafe") to "Alimentação",
            listOf("mercado", "supermerc", "atacad", "assai", "carrefour", "extra", "hortifruti", "acougue") to "Mercado",
            listOf("uber", "99 ", "99app", "posto", "combust", "shell", "ipiranga", "petrobras", "estacion", "pedagio", "sem parar", "onibus", "metro") to "Transporte",
            listOf("farmacia", "drogaria", "droga", "raia", "pague menos", "hospital", "clinica", "laborat", "medic", "unimed", "hapvida") to "Saúde",
            listOf("netflix", "spotify", "prime video", "amazon prime", "disney", "hbo", "max ", "youtube", "deezer", "globoplay", "icloud", "google one") to "Assinaturas",
            listOf("aluguel", "condominio", "iptu") to "Moradia",
            listOf("energia", "celpe", "neoenergia", "enel", "cemig", "light", "compesa", "sabesp", "agua", "internet", "claro", "vivo", "tim ", "oi ", "gas ") to "Contas",
            listOf("escola", "faculdade", "curso", "livraria", "udemy", "ftsa") to "Educação",
            listOf("cinema", "ingresso", "steam", "playstation", "xbox", "bar ", "show") to "Lazer",
            listOf("renner", "riachuelo", "c&a", "shein", "zara", "centauro", "netshoes") to "Roupas",
            listOf("salario", "pagamento salario", "folha", "proventos") to "Salário",
            listOf("rendimento", "dividendo", "juros") to "Investimentos",
            listOf("pet", "petz", "cobasi", "veterin") to "Pets"
        )
    }

    // ------------------------------------------------------------------ seed
    suspend fun seedIfNeeded() {
        if (dao.countCategories() > 0) return
        val exp = listOf(
            Triple("Alimentação", 0xFFE57373.toInt(), "food"),
            Triple("Mercado", 0xFFFF8A65.toInt(), "market"),
            Triple("Moradia", 0xFF8D6E63.toInt(), "home"),
            Triple("Contas", 0xFFFFB300.toInt(), "bills"),
            Triple("Transporte", 0xFF42A5F5.toInt(), "car"),
            Triple("Saúde", 0xFF26A69A.toInt(), "health"),
            Triple("Educação", 0xFF5C6BC0.toInt(), "education"),
            Triple("Lazer", 0xFFAB47BC.toInt(), "leisure"),
            Triple("Assinaturas", 0xFFEC407A.toInt(), "subscriptions"),
            Triple("Roupas", 0xFF7E57C2.toInt(), "clothes"),
            Triple("Viagem", 0xFF29B6F6.toInt(), "travel"),
            Triple("Pets", 0xFFA1887F.toInt(), "pets"),
            Triple("Presentes", 0xFFF06292.toInt(), "gift"),
            Triple("Impostos e taxas", 0xFF78909C.toInt(), "tax"),
            Triple("Outras despesas", 0xFF90A4AE.toInt(), "other")
        )
        val inc = listOf(
            Triple("Salário", 0xFF43A047.toInt(), "salary"),
            Triple("Renda extra", 0xFF66BB6A.toInt(), "work"),
            Triple("Investimentos", 0xFF00897B.toInt(), "invest"),
            Triple("Presentes recebidos", 0xFF9CCC65.toInt(), "gift"),
            Triple("Outras receitas", 0xFF81C784.toInt(), "money")
        )
        dao.insertCategories(exp.map { Category(name = it.first, kind = CategoryKind.EXPENSE, color = it.second, icon = it.third) } +
            inc.map { Category(name = it.first, kind = CategoryKind.INCOME, color = it.second, icon = it.third) })
        if (dao.accountsList().isEmpty()) {
            dao.upsertAccount(Account(name = "Carteira", type = AccountType.WALLET, color = 0xFF0F7B6C.toInt()))
        }
    }

    // ------------------------------------------------------------------ lançamentos
    suspend fun addTransaction(d: TxnDraft, cards: List<CreditCard>) {
        val card = d.cardId?.let { id -> cards.firstOrNull { it.id == id } }
        val today = LocalDate.now()
        fun base(date: LocalDate, amount: Long, paid: Boolean) = Txn(
            type = d.type,
            amount = amount,
            description = d.description.trim(),
            date = date.toEpochDay(),
            categoryId = if (d.type == TxType.TRANSFER) null else d.categoryId,
            accountId = if (card != null) null else d.accountId,
            toAccountId = if (d.type == TxType.TRANSFER) d.toAccountId else null,
            cardId = card?.id,
            invoiceMonth = card?.let { Calc.invoiceMonthFor(it, date) },
            paid = if (card != null) true else paid,
            tags = d.tags.trim(),
            notes = d.notes.trim(),
            attachment = d.attachment
        )

        when (d.recurrence) {
            Recurrence.NONE -> dao.insertTxn(base(d.date, d.amount, d.paid))

            Recurrence.INSTALLMENT -> {
                val n = d.count.coerceIn(2, 420)
                val group = UUID.randomUUID().toString()
                val per = if (d.amountIsTotal) d.amount / n else d.amount
                val firstExtra = if (d.amountIsTotal) d.amount - per * n else 0L
                val firstInvoice = card?.let { Calc.invoiceMonthFor(it, d.date) }
                val list = (0 until n).map { i ->
                    val date = d.date.plusMonths(i.toLong())
                    base(date, per + if (i == 0) firstExtra else 0L, if (i == 0) d.paid else false).copy(
                        recurrence = Recurrence.INSTALLMENT,
                        frequency = Frequency.MONTHLY,
                        groupId = group,
                        installmentNumber = i + 1,
                        installmentTotal = n,
                        invoiceMonth = firstInvoice?.let { it.toYearMonth().plusMonths(i.toLong()).key() },
                        attachment = if (i == 0) d.attachment else null
                    )
                }
                dao.insertTxns(list)
            }

            Recurrence.FIXED -> {
                val group = UUID.randomUUID().toString()
                val horizon = today.plusMonths(MONTHS_AHEAD)
                val list = mutableListOf<Txn>()
                var i = 0
                while (true) {
                    val date = step(d.date, d.frequency, i)
                    if (d.count > 0 && i >= d.count) break
                    if (d.count == 0 && i > 0 && date.isAfter(horizon)) break
                    if (i > 1000) break
                    list += base(date, d.amount, if (i == 0) d.paid else false).copy(
                        recurrence = Recurrence.FIXED,
                        frequency = d.frequency,
                        groupId = group,
                        installmentNumber = i + 1,
                        installmentTotal = d.count,
                        attachment = if (i == 0) d.attachment else null
                    )
                    i++
                }
                dao.insertTxns(list)
                dao.upsertGroup(RecurringGroup(group, active = d.count == 0))
            }
        }
        learn(d.description, d.categoryId)
    }

    /** Gera ocorrências futuras dos lançamentos fixos sem fim. */
    suspend fun extendFixed() {
        val horizon = LocalDate.now().plusMonths(MONTHS_AHEAD)
        val cards = dao.cardsList().associateBy { it.id }
        for (g in dao.groupsList().filter { it.active }) {
            val items = dao.txnsInGroup(g.groupId)
            if (items.isEmpty()) continue
            val first = items.minByOrNull { it.installmentNumber } ?: continue
            val last = items.maxByOrNull { it.installmentNumber } ?: continue
            val freq = last.frequency ?: Frequency.MONTHLY
            val firstIndex = first.installmentNumber - 1
            val baseDate = Repository.step(LocalDate.ofEpochDay(first.date), freq, -firstIndex)
            val add = mutableListOf<Txn>()
            var i = last.installmentNumber
            while (true) {
                val date = step(baseDate, freq, i)
                if (date.isAfter(horizon) || add.size > 500) break
                val card = last.cardId?.let { cards[it] }
                add += last.copy(
                    id = 0, date = date.toEpochDay(), paid = card != null,
                    installmentNumber = i + 1, attachment = null,
                    invoiceMonth = card?.let { Calc.invoiceMonthFor(it, date) }
                )
                i++
            }
            if (add.isNotEmpty()) dao.insertTxns(add)
        }
    }

    suspend fun updateTransaction(original: Txn, edited: Txn, scope: EditScope, cards: List<CreditCard>) {
        if (original.type == TxType.INVOICE_PAYMENT) { dao.updateTxn(edited.copy(toAccountId = null, categoryId = null, paid = true)); return }
        val card = edited.cardId?.let { id -> cards.firstOrNull { it.id == id } }
        val fixed = edited.copy(
            accountId = if (card != null) null else edited.accountId,
            invoiceMonth = card?.let {
                if (original.cardId == card.id && original.date == edited.date && original.invoiceMonth != null)
                    original.invoiceMonth else Calc.invoiceMonthFor(it, LocalDate.ofEpochDay(edited.date))
            },
            paid = if (card != null) true else edited.paid
        )
        if (scope == EditScope.ONLY_THIS || original.groupId == null) {
            dao.updateTxn(fixed)
        } else {
            val group = dao.txnsInGroup(original.groupId)
            val targets = if (scope == EditScope.ALL) group else group.filter { it.date >= original.date }
            val updated = targets.map { t ->
                if (t.id == original.id) fixed else t.copy(
                    amount = fixed.amount, description = fixed.description, categoryId = fixed.categoryId,
                    accountId = fixed.accountId, toAccountId = fixed.toAccountId, cardId = fixed.cardId,
                    invoiceMonth = card?.let { c ->
                        if (t.cardId == c.id && t.invoiceMonth != null) t.invoiceMonth
                        else Calc.invoiceMonthFor(c, LocalDate.ofEpochDay(t.date))
                    },
                    tags = fixed.tags, notes = fixed.notes
                )
            }
            dao.updateTxns(updated)
        }
        learn(edited.description, edited.categoryId)
    }

    suspend fun deleteTransaction(t: Txn, scope: EditScope) {
        val g = t.groupId
        if (g == null || scope == EditScope.ONLY_THIS) {
            dao.deleteTxn(t)
        } else {
            if (scope == EditScope.ALL) dao.deleteGroup(g) else dao.deleteGroupFrom(g, t.date)
            dao.upsertGroup(RecurringGroup(g, active = false))
        }
    }

    suspend fun setPaid(t: Txn, paid: Boolean) = dao.updateTxn(t.copy(paid = paid))

    suspend fun payInvoice(card: CreditCard, month: Int, accountId: Long, amount: Long, date: LocalDate) {
        dao.insertTxn(
            Txn(
                type = TxType.INVOICE_PAYMENT, amount = amount,
                description = "Pagamento fatura ${card.name}",
                date = date.toEpochDay(), accountId = accountId, cardId = card.id,
                invoiceMonth = month, paid = true
            )
        )
    }

    suspend fun adjustBalance(account: Account, current: Long, wanted: Long) {
        val diff = wanted - current
        if (diff == 0L) return
        dao.insertTxn(
            Txn(
                type = if (diff > 0) TxType.INCOME else TxType.EXPENSE,
                amount = kotlin.math.abs(diff), description = "Reajuste de saldo",
                date = LocalDate.now().toEpochDay(), accountId = account.id, paid = true
            )
        )
    }

    // ------------------------------------------------------------------ contas / cartões / categorias
    /** Exclui se não houver lançamentos; senão arquiva. Retorna true se excluiu. */
    suspend fun deleteOrArchiveAccount(a: Account): Boolean =
        if (dao.countTxnsForAccount(a.id) == 0) { dao.deleteAccount(a); true }
        else { dao.upsertAccount(a.copy(archived = true)); false }

    suspend fun deleteOrArchiveCard(c: CreditCard): Boolean =
        if (dao.countTxnsForCard(c.id) == 0) { dao.deleteCard(c); true }
        else { dao.upsertCard(c.copy(archived = true)); false }

    suspend fun deleteCategory(c: Category) {
        dao.clearCategory(c.id)
        dao.clearParent(c.id)
        dao.deleteBudgetsOf(c.id)
        dao.deleteRulesOf(c.id)
        dao.deleteCategory(c)
    }

    // ------------------------------------------------------------------ categorização automática
    suspend fun learn(description: String, categoryId: Long?) {
        if (categoryId == null) return
        val key = ruleKey(description)
        if (key.isNotBlank()) dao.upsertRule(CategoryRule(key, categoryId))
    }

    fun suggestCategory(description: String, rules: List<CategoryRule>, categories: List<Category>, income: Boolean): Long? {
        val norm = " " + normalize(description) + " "
        if (norm.isBlank()) return null
        val valid = categories.filter { it.kind == if (income) CategoryKind.INCOME else CategoryKind.EXPENSE }.associateBy { it.id }
        val key = ruleKey(description)
        rules.firstOrNull { it.keyword == key && valid.containsKey(it.categoryId) }?.let { return it.categoryId }
        rules.filter { it.keyword.length >= 4 && norm.contains(" " + it.keyword) && valid.containsKey(it.categoryId) }
            .maxByOrNull { it.keyword.length }?.let { return it.categoryId }
        for ((words, catName) in defaultKeywords) {
            if (words.any { norm.contains(it) }) {
                valid.values.firstOrNull { it.name.equals(catName, ignoreCase = true) }?.let { return it.id }
            }
        }
        return null
    }

    // ------------------------------------------------------------------ importação
    fun markDuplicates(rows: List<ImportRow>, existing: List<Txn>, accountId: Long?, cardId: Long?) {
        val keys = existing.filter { (accountId != null && it.accountId == accountId) || (cardId != null && it.cardId == cardId) }
            .map { Triple(it.date, it.amount, normalize(it.description)) }.toHashSet()
        val fits = existing.mapNotNull { t -> Regex("FITID:(\\S+)").find(t.notes)?.groupValues?.get(1) }.toHashSet()
        for (r in rows) {
            val dup = (r.fitId != null && r.fitId in fits) ||
                Triple(r.date.toEpochDay(), kotlin.math.abs(r.amount), normalize(r.description)) in keys
            r.duplicate = dup
            r.selected = !dup
        }
    }

    suspend fun importRows(rows: List<ImportRow>, accountId: Long?, card: CreditCard?, positiveIsExpense: Boolean) {
        val list = rows.filter { it.selected && it.amount != 0L }.map { r ->
            val signed = if (positiveIsExpense) -r.amount else r.amount
            val type = if (signed < 0) TxType.EXPENSE else TxType.INCOME
            Txn(
                type = type, amount = kotlin.math.abs(signed), description = r.description,
                date = r.date.toEpochDay(), categoryId = r.categoryId,
                accountId = if (card == null) accountId else null,
                cardId = card?.id, invoiceMonth = card?.let { Calc.invoiceMonthFor(it, r.date) },
                paid = true, notes = r.fitId?.let { "FITID:$it" } ?: ""
            )
        }
        dao.insertTxns(list)
        for (r in rows.filter { it.selected }) learn(r.description, r.categoryId)
    }
}
