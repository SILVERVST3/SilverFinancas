package com.silver.financas.data

import com.silver.financas.util.dayInMonth
import com.silver.financas.util.key
import com.silver.financas.util.toYearMonth
import java.time.LocalDate
import java.time.YearMonth

enum class InvoiceStatus(val label: String) {
    OPEN("Aberta"), CLOSED("Fechada"), PAID("Paga"), OVERDUE("Vencida"), FUTURE("Futura")
}

data class Invoice(
    val card: CreditCard,
    val month: Int,                 // aaaamm do vencimento
    val items: List<Txn>,
    val total: Long,
    val paid: Long,
    val closingDate: LocalDate,
    val dueDate: LocalDate,
    val status: InvoiceStatus
) {
    val remaining: Long get() = (total - paid).coerceAtLeast(0)
}

data class MonthSummary(val income: Long, val expense: Long) {
    val result: Long get() = income - expense
}

data class BillAlert(val title: String, val detail: String, val amount: Long, val date: LocalDate, val txn: Txn? = null, val invoice: Invoice? = null)

object Calc {

    /** Mês (aaaamm) de vencimento da fatura em que uma compra feita em [date] cai. */
    fun invoiceMonthFor(card: CreditCard, date: LocalDate): Int {
        val ym = YearMonth.from(date)
        val closeYm = if (date.dayOfMonth < card.closingDay) ym else ym.plusMonths(1)
        val dueYm = if (card.dueDay > card.closingDay) closeYm else closeYm.plusMonths(1)
        return dueYm.key()
    }

    fun dueDate(card: CreditCard, month: Int): LocalDate = dayInMonth(month.toYearMonth(), card.dueDay)

    fun closingDate(card: CreditCard, month: Int): LocalDate {
        val dueYm = month.toYearMonth()
        val closeYm = if (card.dueDay > card.closingDay) dueYm else dueYm.minusMonths(1)
        return dayInMonth(closeYm, card.closingDay)
    }

    fun invoice(card: CreditCard, month: Int, txns: List<Txn>, today: LocalDate = LocalDate.now()): Invoice {
        val items = txns.filter { it.type == TxType.EXPENSE && it.cardId == card.id && it.invoiceMonth == month }
            .sortedByDescending { it.date }
        val refunds = txns.filter { it.type == TxType.INCOME && it.cardId == card.id && it.invoiceMonth == month }
        val total = items.sumOf { it.amount } - refunds.sumOf { it.amount }
        val paid = txns.filter { it.type == TxType.INVOICE_PAYMENT && it.cardId == card.id && it.invoiceMonth == month }
            .sumOf { it.amount }
        val closing = closingDate(card, month)
        val due = dueDate(card, month)
        val openClosing = closingDate(card, invoiceMonthFor(card, today))
        val status = when {
            total > 0 && paid >= total -> InvoiceStatus.PAID
            total <= 0 && today.isAfter(closing) -> InvoiceStatus.PAID
            today.isAfter(due) -> InvoiceStatus.OVERDUE
            !today.isBefore(closing) -> InvoiceStatus.CLOSED
            closing.isAfter(openClosing) -> InvoiceStatus.FUTURE
            else -> InvoiceStatus.OPEN
        }
        return Invoice(card, month, items + refunds, total, paid, closing, due, status)
    }

    fun cardUsed(card: CreditCard, txns: List<Txn>): Long {
        var used = 0L
        for (t in txns) if (t.cardId == card.id) when (t.type) {
            TxType.EXPENSE -> used += t.amount
            TxType.INCOME -> used -= t.amount
            TxType.INVOICE_PAYMENT -> used -= t.amount
            else -> {}
        }
        return used.coerceAtLeast(0)
    }

    /** Saldo de uma conta. realized=true considera só pagos até hoje. */
    fun accountBalance(a: Account, txns: List<Txn>, upTo: LocalDate, includeUnpaid: Boolean): Long {
        val limit = upTo.toEpochDay()
        var b = a.initialBalance
        for (t in txns) {
            if (t.date > limit) continue
            if (!includeUnpaid && !t.paid) continue
            when (t.type) {
                TxType.INCOME -> if (t.accountId == a.id && t.cardId == null) b += t.amount
                TxType.EXPENSE -> if (t.accountId == a.id && t.cardId == null) b -= t.amount
                TxType.TRANSFER -> {
                    if (t.accountId == a.id) b -= t.amount
                    if (t.toAccountId == a.id) b += t.amount
                }
                TxType.INVOICE_PAYMENT -> if (t.accountId == a.id) b -= t.amount
            }
        }
        return b
    }

    fun totalBalance(accounts: List<Account>, txns: List<Txn>, upTo: LocalDate, includeUnpaid: Boolean): Long =
        accounts.filter { it.includeInTotal && !it.archived }
            .sumOf { accountBalance(it, txns, upTo, includeUnpaid) }

    /** Saldo previsto ao fim do mês: contas (incluindo não pagos) menos faturas em aberto que vencem até lá. */
    fun forecast(accounts: List<Account>, cards: List<CreditCard>, txns: List<Txn>, ym: YearMonth): Long {
        val end = ym.atEndOfMonth()
        val base = totalBalance(accounts, txns, end, includeUnpaid = true)
        var openInvoices = 0L
        for (c in cards) {
            val months = txns.filter { it.cardId == c.id && it.invoiceMonth != null }
                .mapNotNull { it.invoiceMonth }.distinct().filter { it <= ym.key() }
            for (m in months) {
                val inv = invoice(c, m, txns)
                openInvoices += inv.remaining
            }
        }
        return base - openInvoices
    }

    fun inMonth(t: Txn, ym: YearMonth): Boolean {
        val d = LocalDate.ofEpochDay(t.date)
        return d.year == ym.year && d.monthValue == ym.monthValue
    }

    fun monthSummary(txns: List<Txn>, ym: YearMonth): MonthSummary {
        var inc = 0L; var exp = 0L
        for (t in txns) {
            if (!inMonth(t, ym)) continue
            when (t.type) {
                TxType.INCOME -> if (t.cardId == null) inc += t.amount else exp -= t.amount
                TxType.EXPENSE -> exp += t.amount
                else -> {}
            }
        }
        return MonthSummary(inc, exp)
    }

    fun topCategoryId(catId: Long?, cats: Map<Long, Category>): Long? {
        if (catId == null) return null
        val c = cats[catId] ?: return catId
        return c.parentId ?: c.id
    }

    /** Total por categoria-mãe no mês para o tipo informado. Chave null = sem categoria. */
    fun byCategory(txns: List<Txn>, cats: Map<Long, Category>, ym: YearMonth, type: TxType): List<Pair<Long?, Long>> {
        val map = LinkedHashMap<Long?, Long>()
        for (t in txns) {
            if (t.type != type || !inMonth(t, ym)) continue
            if (type == TxType.INCOME && t.cardId != null) continue
            val key = topCategoryId(t.categoryId, cats)
            map[key] = (map[key] ?: 0L) + t.amount
        }
        return map.entries.map { it.key to it.value }.sortedByDescending { it.second }
    }

    fun spentInCategory(txns: List<Txn>, cats: Map<Long, Category>, categoryId: Long, ym: YearMonth): Long =
        txns.filter { it.type == TxType.EXPENSE && inMonth(it, ym) && topCategoryId(it.categoryId, cats) == categoryId }
            .sumOf { it.amount } +
            // limites para subcategorias específicas
            (if (cats[categoryId]?.parentId != null)
                txns.filter { it.type == TxType.EXPENSE && inMonth(it, ym) && it.categoryId == categoryId }.sumOf { it.amount }
            else 0L)

    /** Contas a pagar: despesas não pagas vencidas ou para os próximos [days] dias + faturas. */
    fun bills(cards: List<CreditCard>, txns: List<Txn>, today: LocalDate = LocalDate.now(), days: Long = 7): List<BillAlert> {
        val until = today.plusDays(days).toEpochDay()
        val from = today.minusDays(60).toEpochDay()
        val list = mutableListOf<BillAlert>()
        for (t in txns) {
            if (t.type == TxType.EXPENSE && t.cardId == null && !t.paid && t.date in from..until) {
                val d = LocalDate.ofEpochDay(t.date)
                val detail = when {
                    d.isBefore(today) -> "Vencida"
                    d == today -> "Vence hoje"
                    d == today.plusDays(1) -> "Vence amanhã"
                    else -> "Vence em ${d.dayOfMonth}/${d.monthValue}"
                }
                list += BillAlert(t.description, detail, t.amount, d, txn = t)
            }
        }
        for (c in cards.filter { !it.archived }) {
            val months = txns.filter { it.cardId == c.id }.mapNotNull { it.invoiceMonth }.distinct()
            for (m in months) {
                val inv = invoice(c, m, txns, today)
                if (inv.remaining <= 0) continue
                if (inv.dueDate.toEpochDay() in from..until && !today.isBefore(inv.closingDate)) {
                    val detail = when {
                        inv.dueDate.isBefore(today) -> "Fatura vencida"
                        inv.dueDate == today -> "Fatura vence hoje"
                        else -> "Fatura vence em ${inv.dueDate.dayOfMonth}/${inv.dueDate.monthValue}"
                    }
                    list += BillAlert("Fatura ${c.name}", detail, inv.remaining, inv.dueDate, invoice = inv)
                }
            }
        }
        return list.sortedBy { it.date }
    }
}
