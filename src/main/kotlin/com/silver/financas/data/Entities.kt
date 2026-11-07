package com.silver.financas.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class AccountType(val label: String) {
    CHECKING("Conta corrente"),
    SAVINGS("Poupança"),
    DIGITAL("Conta digital"),
    BUSINESS("Conta PJ"),
    WALLET("Carteira / dinheiro"),
    INVESTMENT("Investimento"),
    OTHER("Outros")
}

enum class TxType { INCOME, EXPENSE, TRANSFER, INVOICE_PAYMENT }

enum class Recurrence { NONE, FIXED, INSTALLMENT }

enum class Frequency(val label: String) {
    WEEKLY("Semanal"),
    BIWEEKLY("Quinzenal"),
    MONTHLY("Mensal"),
    BIMONTHLY("Bimestral"),
    QUARTERLY("Trimestral"),
    SEMIANNUAL("Semestral"),
    YEARLY("Anual")
}

enum class CategoryKind { INCOME, EXPENSE }

@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: AccountType = AccountType.CHECKING,
    val initialBalance: Long = 0,     // centavos
    val color: Int,
    val includeInTotal: Boolean = true,
    val archived: Boolean = false
)

@Entity(tableName = "cards")
data class CreditCard(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val limitCents: Long,
    val closingDay: Int,
    val dueDay: Int,
    val paymentAccountId: Long? = null,
    val color: Int,
    val archived: Boolean = false
)

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: CategoryKind,
    val color: Int,
    val icon: String = "other",
    val parentId: Long? = null
)

/** Lançamento. Valores sempre positivos, em centavos. Datas em epochDay. */
@Entity(tableName = "transactions", indices = [Index("date"), Index("groupId")])
data class Txn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: TxType,
    val amount: Long,
    val description: String,
    val date: Long,
    val categoryId: Long? = null,
    val accountId: Long? = null,
    val toAccountId: Long? = null,
    val cardId: Long? = null,
    val invoiceMonth: Int? = null,      // aaaamm do vencimento da fatura
    val paid: Boolean = true,
    val recurrence: Recurrence = Recurrence.NONE,
    val frequency: Frequency? = null,
    val groupId: String? = null,
    val installmentNumber: Int = 0,
    val installmentTotal: Int = 0,
    val tags: String = "",
    val notes: String = "",
    val attachment: String? = null
)

@Entity(tableName = "budgets")
data class Budget(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long,
    val limitCents: Long
)

/** Regra aprendida: palavra-chave da descrição -> categoria */
@Entity(tableName = "rules")
data class CategoryRule(
    @PrimaryKey val keyword: String,
    val categoryId: Long
)

/** Grupo de lançamento fixo (recorrente) — active=true continua gerando meses à frente */
@Entity(tableName = "recurring_groups")
data class RecurringGroup(
    @PrimaryKey val groupId: String,
    val active: Boolean = true
)
