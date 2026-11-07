package com.silver.financas.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.silver.financas.data.*
import com.silver.financas.ui.FinanceViewModel
import com.silver.financas.ui.components.*
import com.silver.financas.ui.theme.ExpenseRed
import com.silver.financas.ui.theme.IncomeGreen
import com.silver.financas.util.shortLabel

private enum class Report(val label: String) {
    EXPENSE_CAT("Despesas por categoria"), INCOME_CAT("Receitas por categoria"),
    IN_OUT("Receitas x despesas"), BALANCE("Evolução do saldo"), ACCOUNTS("Por conta"), TAGS("Por tag")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(vm: FinanceViewModel) {
    val accounts by vm.accounts.collectAsState()
    val cards by vm.cards.collectAsState()
    val cats by vm.categories.collectAsState()
    val txns by vm.txns.collectAsState()
    val month by vm.month.collectAsState()
    val catMap = remember(cats) { cats.associateBy { it.id } }
    var report by remember { mutableStateOf(Report.EXPENSE_CAT) }
    var range by remember { mutableIntStateOf(6) }

    Scaffold(topBar = { TopAppBar(title = { Text("Relatórios") }) }) { p ->
        LazyColumn(contentPadding = PaddingValues(top = p.calculateTopPadding(), bottom = 32.dp)) {
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Report.entries.forEach { r -> FilterChip(report == r, { report = r }, { Text(r.label) }) }
                }
            }
            item { MonthSelector(month, { vm.month.value = it }, Modifier.padding(horizontal = 8.dp)) }

            when (report) {
                Report.EXPENSE_CAT, Report.INCOME_CAT -> {
                    val type = if (report == Report.EXPENSE_CAT) TxType.EXPENSE else TxType.INCOME
                    val data = Calc.byCategory(txns, catMap, month, type)
                    val total = data.sumOf { it.second }
                    val prev = Calc.byCategory(txns, catMap, month.minusMonths(1), type).toMap()
                    item {
                        SectionCard {
                            if (data.isEmpty()) EmptyState("Sem dados neste mês")
                            else Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                DonutChart(data.map { (id, v) -> (id?.let { catMap[it] }?.let { Color(it.color) } ?: Color.Gray) to v }, Modifier.size(200.dp)) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Total", style = MaterialTheme.typography.labelMedium)
                                        MoneyText(total, weight = FontWeight.Bold, color = if (type == TxType.EXPENSE) ExpenseRed else IncomeGreen)
                                    }
                                }
                            }
                        }
                    }
                    item {
                        SectionCard {
                            data.forEach { (id, v) ->
                                val c = id?.let { catMap[it] }
                                val before = prev[id] ?: 0L
                                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    CategoryBubble(c, 32)
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(c?.name ?: "Sem categoria", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        val diff = if (before > 0) ((v - before) * 100 / before) else null
                                        Text(
                                            "${if (total > 0) v * 100 / total else 0}% do total" + (diff?.let { " · ${if (it >= 0) "+" else ""}$it% vs mês anterior" } ?: ""),
                                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    MoneyText(v, weight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }

                Report.IN_OUT -> {
                    val months = (range - 1 downTo 0).map { month.minusMonths(it.toLong()) }
                    val sums = months.map { Calc.monthSummary(txns, it) }
                    item { RangeChips(range) { range = it } }
                    item {
                        SectionCard {
                            GroupedBarChart(months.map { it.shortLabel() }, listOf(IncomeGreen to sums.map { it.income }, ExpenseRed to sums.map { it.expense }))
                            Spacer(Modifier.height(8.dp))
                            Row { Legend(IncomeGreen, "Receitas"); Spacer(Modifier.width(16.dp)); Legend(ExpenseRed, "Despesas") }
                        }
                    }
                    item {
                        SectionCard {
                            months.zip(sums).reversed().forEach { (m, s) ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    Text(m.shortLabel(), Modifier.width(64.dp))
                                    MoneyText(s.income, Modifier.weight(1f), color = IncomeGreen, style = MaterialTheme.typography.bodySmall)
                                    MoneyText(s.expense, Modifier.weight(1f), color = ExpenseRed, style = MaterialTheme.typography.bodySmall)
                                    MoneyText(s.result, Modifier.weight(1f), weight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            val avgInc = sums.sumOf { it.income } / sums.size.coerceAtLeast(1)
                            val avgExp = sums.sumOf { it.expense } / sums.size.coerceAtLeast(1)
                            HorizontalDivider(Modifier.padding(vertical = 6.dp))
                            Row {
                                LabelValue("Média de receitas", avgInc, IncomeGreen, Modifier.weight(1f))
                                LabelValue("Média de despesas", avgExp, ExpenseRed, Modifier.weight(1f))
                            }
                        }
                    }
                }

                Report.BALANCE -> {
                    val months = (range - 1 downTo 0).map { month.minusMonths(it.toLong()) }
                    val values = months.map { Calc.totalBalance(accounts, txns, it.atEndOfMonth(), includeUnpaid = true) }
                    item { RangeChips(range) { range = it } }
                    item {
                        SectionCard("Saldo no fim de cada mês") {
                            LineChart(months.map { it.shortLabel() }, values, MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(8.dp))
                            months.zip(values).reversed().forEach { (m, v) ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                    Text(m.shortLabel(), Modifier.weight(1f))
                                    MoneyText(v, weight = FontWeight.SemiBold, color = if (v < 0) ExpenseRed else Color.Unspecified)
                                }
                            }
                        }
                    }
                }

                Report.ACCOUNTS -> {
                    item {
                        SectionCard("Movimento do mês por conta") {
                            accounts.filter { !it.archived }.forEach { a ->
                                var inc = 0L; var exp = 0L
                                txns.filter { Calc.inMonth(it, month) }.forEach { t ->
                                    when {
                                        t.type == TxType.INCOME && t.accountId == a.id && t.cardId == null -> inc += t.amount
                                        t.type == TxType.EXPENSE && t.accountId == a.id && t.cardId == null -> exp += t.amount
                                        t.type == TxType.TRANSFER && t.toAccountId == a.id -> inc += t.amount
                                        (t.type == TxType.TRANSFER || t.type == TxType.INVOICE_PAYMENT) && t.accountId == a.id -> exp += t.amount
                                    }
                                }
                                Column(Modifier.padding(vertical = 6.dp)) {
                                    Text(a.name, fontWeight = FontWeight.SemiBold)
                                    Row {
                                        MoneyText(inc, Modifier.weight(1f), IncomeGreen, prefix = "+ ")
                                        MoneyText(exp, Modifier.weight(1f), ExpenseRed, prefix = "- ")
                                        MoneyText(Calc.accountBalance(a, txns, month.atEndOfMonth(), true), weight = FontWeight.SemiBold)
                                    }
                                }
                            }
                            cards.filter { !it.archived }.forEach { c ->
                                val spent = txns.filter { it.cardId == c.id && it.type == TxType.EXPENSE && Calc.inMonth(it, month) }.sumOf { it.amount }
                                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                    Text("💳 ${c.name}", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                                    MoneyText(spent, color = ExpenseRed, prefix = "- ")
                                }
                            }
                        }
                    }
                }

                Report.TAGS -> {
                    val byTag = txns.filter { Calc.inMonth(it, month) && it.tags.isNotBlank() && (it.type == TxType.EXPENSE || it.type == TxType.INCOME) }
                        .flatMap { t -> t.tags.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }.map { it to t } }
                        .groupBy({ it.first }, { it.second })
                        .map { (tag, list) ->
                            Triple(tag, list.filter { it.type == TxType.INCOME }.sumOf { it.amount }, list.filter { it.type == TxType.EXPENSE }.sumOf { it.amount })
                        }.sortedByDescending { it.third }
                    item {
                        SectionCard("Por tag") {
                            if (byTag.isEmpty()) EmptyState("Nenhum lançamento com tag neste mês")
                            byTag.forEach { (tag, inc, exp) ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    Text("#$tag", Modifier.weight(1f))
                                    if (inc > 0) MoneyText(inc, color = IncomeGreen, prefix = "+ ")
                                    Spacer(Modifier.width(10.dp))
                                    MoneyText(exp, color = ExpenseRed, prefix = "- ")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangeChips(range: Int, onChange: (Int) -> Unit) {
    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(3, 6, 12).forEach { n -> FilterChip(range == n, { onChange(n) }, { Text("$n meses") }) }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}
