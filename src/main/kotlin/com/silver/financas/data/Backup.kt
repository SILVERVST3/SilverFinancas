package com.silver.financas.data

import com.silver.financas.util.br
import com.silver.financas.util.toDate
import org.json.JSONArray
import org.json.JSONObject

object Backup {

    suspend fun export(dao: FinanceDao): String {
        val root = JSONObject()
        root.put("app", "SilverFinancas")
        root.put("version", 1)
        root.put("accounts", JSONArray().apply {
            dao.accountsList().forEach {
                put(JSONObject().put("id", it.id).put("name", it.name).put("type", it.type.name)
                    .put("initialBalance", it.initialBalance).put("color", it.color)
                    .put("includeInTotal", it.includeInTotal).put("archived", it.archived))
            }
        })
        root.put("cards", JSONArray().apply {
            dao.cardsList().forEach {
                put(JSONObject().put("id", it.id).put("name", it.name).put("limitCents", it.limitCents)
                    .put("closingDay", it.closingDay).put("dueDay", it.dueDay)
                    .put("paymentAccountId", it.paymentAccountId ?: JSONObject.NULL)
                    .put("color", it.color).put("archived", it.archived))
            }
        })
        root.put("categories", JSONArray().apply {
            dao.categoriesList().forEach {
                put(JSONObject().put("id", it.id).put("name", it.name).put("kind", it.kind.name)
                    .put("color", it.color).put("icon", it.icon).put("parentId", it.parentId ?: JSONObject.NULL))
            }
        })
        root.put("transactions", JSONArray().apply {
            dao.txnsList().forEach {
                put(JSONObject().put("id", it.id).put("type", it.type.name).put("amount", it.amount)
                    .put("description", it.description).put("date", it.date)
                    .put("categoryId", it.categoryId ?: JSONObject.NULL)
                    .put("accountId", it.accountId ?: JSONObject.NULL)
                    .put("toAccountId", it.toAccountId ?: JSONObject.NULL)
                    .put("cardId", it.cardId ?: JSONObject.NULL)
                    .put("invoiceMonth", it.invoiceMonth ?: JSONObject.NULL)
                    .put("paid", it.paid).put("recurrence", it.recurrence.name)
                    .put("frequency", it.frequency?.name ?: JSONObject.NULL)
                    .put("groupId", it.groupId ?: JSONObject.NULL)
                    .put("installmentNumber", it.installmentNumber).put("installmentTotal", it.installmentTotal)
                    .put("tags", it.tags).put("notes", it.notes).put("attachment", it.attachment ?: JSONObject.NULL))
            }
        })
        root.put("budgets", JSONArray().apply {
            dao.budgetsList().forEach { put(JSONObject().put("id", it.id).put("categoryId", it.categoryId).put("limitCents", it.limitCents)) }
        })
        root.put("rules", JSONArray().apply {
            dao.rulesList().forEach { put(JSONObject().put("keyword", it.keyword).put("categoryId", it.categoryId)) }
        })
        root.put("groups", JSONArray().apply {
            dao.groupsList().forEach { put(JSONObject().put("groupId", it.groupId).put("active", it.active)) }
        })
        return root.toString(1)
    }

    private fun JSONObject.optLongOrNull(k: String): Long? = if (isNull(k) || !has(k)) null else getLong(k)
    private fun JSONObject.optIntOrNull(k: String): Int? = if (isNull(k) || !has(k)) null else getInt(k)
    private fun JSONObject.optStrOrNull(k: String): String? = if (isNull(k) || !has(k)) null else getString(k)
    private inline fun JSONArray.objects(block: (JSONObject) -> Unit) { for (i in 0 until length()) block(getJSONObject(i)) }

    suspend fun restore(dao: FinanceDao, json: String) {
        val root = JSONObject(json)
        require(root.optString("app") == "SilverFinancas") { "Arquivo não é um backup do SilverFinanças" }
        val accounts = mutableListOf<Account>()
        root.optJSONArray("accounts")?.objects {
            accounts += Account(it.getLong("id"), it.getString("name"), AccountType.valueOf(it.getString("type")),
                it.getLong("initialBalance"), it.getInt("color"), it.optBoolean("includeInTotal", true), it.optBoolean("archived"))
        }
        val cards = mutableListOf<CreditCard>()
        root.optJSONArray("cards")?.objects {
            cards += CreditCard(it.getLong("id"), it.getString("name"), it.getLong("limitCents"), it.getInt("closingDay"),
                it.getInt("dueDay"), it.optLongOrNull("paymentAccountId"), it.getInt("color"), it.optBoolean("archived"))
        }
        val cats = mutableListOf<Category>()
        root.optJSONArray("categories")?.objects {
            cats += Category(it.getLong("id"), it.getString("name"), CategoryKind.valueOf(it.getString("kind")),
                it.getInt("color"), it.optString("icon", "other"), it.optLongOrNull("parentId"))
        }
        val txns = mutableListOf<Txn>()
        root.optJSONArray("transactions")?.objects {
            txns += Txn(
                id = it.getLong("id"), type = TxType.valueOf(it.getString("type")), amount = it.getLong("amount"),
                description = it.optString("description"), date = it.getLong("date"),
                categoryId = it.optLongOrNull("categoryId"), accountId = it.optLongOrNull("accountId"),
                toAccountId = it.optLongOrNull("toAccountId"), cardId = it.optLongOrNull("cardId"),
                invoiceMonth = it.optIntOrNull("invoiceMonth"), paid = it.optBoolean("paid", true),
                recurrence = Recurrence.valueOf(it.optString("recurrence", "NONE")),
                frequency = it.optStrOrNull("frequency")?.let { f -> Frequency.valueOf(f) },
                groupId = it.optStrOrNull("groupId"),
                installmentNumber = it.optInt("installmentNumber"), installmentTotal = it.optInt("installmentTotal"),
                tags = it.optString("tags"), notes = it.optString("notes"), attachment = it.optStrOrNull("attachment")
            )
        }
        val budgets = mutableListOf<Budget>()
        root.optJSONArray("budgets")?.objects { budgets += Budget(it.getLong("id"), it.getLong("categoryId"), it.getLong("limitCents")) }
        val rules = mutableListOf<CategoryRule>()
        root.optJSONArray("rules")?.objects { rules += CategoryRule(it.getString("keyword"), it.getLong("categoryId")) }
        val groups = mutableListOf<RecurringGroup>()
        root.optJSONArray("groups")?.objects { groups += RecurringGroup(it.getString("groupId"), it.optBoolean("active", true)) }

        (dao as? MemoryDao)?.replaceAll(accounts, cards, cats, txns, budgets, rules, groups) ?: run {
            dao.clearTxns(); dao.clearBudgets(); dao.clearRules(); dao.clearGroups()
            dao.clearCards(); dao.clearAccounts(); dao.clearCategories()
            dao.insertAccounts(accounts); dao.insertCards(cards); dao.insertCategories(cats)
            dao.insertTxns(txns); dao.insertBudgets(budgets); dao.insertRules(rules); dao.insertGroups(groups)
        }
    }

    suspend fun exportCsv(dao: FinanceDao): String {
        val accounts = dao.accountsList().associateBy { it.id }
        val cards = dao.cardsList().associateBy { it.id }
        val cats = dao.categoriesList().associateBy { it.id }
        val sb = StringBuilder("﻿Data;Tipo;Descrição;Categoria;Conta/Cartão;Valor;Pago;Parcela;Tags;Observações\n")
        fun esc(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
        for (t in dao.txnsList().sortedBy { it.date }) {
            val tipo = when (t.type) {
                TxType.INCOME -> "Receita"; TxType.EXPENSE -> "Despesa"
                TxType.TRANSFER -> "Transferência"; TxType.INVOICE_PAYMENT -> "Pagamento de fatura"
            }
            val origem = t.cardId?.let { cards[it]?.name } ?: t.accountId?.let { accounts[it]?.name } ?: ""
            val destino = t.toAccountId?.let { " → " + (accounts[it]?.name ?: "") } ?: ""
            val sign = if (t.type == TxType.EXPENSE || t.type == TxType.INVOICE_PAYMENT) "-" else ""
            val valor = sign + String.format(java.util.Locale("pt", "BR"), "%.2f", t.amount / 100.0)
            val parcela = if (t.recurrence == Recurrence.INSTALLMENT) "${t.installmentNumber}/${t.installmentTotal}" else ""
            sb.append(listOf(t.date.toDate().br(), tipo, esc(t.description), esc(t.categoryId?.let { cats[it]?.name } ?: ""),
                esc(origem + destino), valor, if (t.paid) "Sim" else "Não", parcela, esc(t.tags), esc(t.notes)).joinToString(";"))
            sb.append('\n')
        }
        return sb.toString()
    }
}
