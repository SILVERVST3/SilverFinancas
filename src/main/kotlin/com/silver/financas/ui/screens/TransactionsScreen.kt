package com.silver.financas.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.silver.financas.ui.Nav
import com.silver.financas.data.*
import com.silver.financas.ui.FinanceViewModel
import com.silver.financas.ui.components.*
import com.silver.financas.ui.openTxn
import com.silver.financas.ui.theme.ExpenseRed
import com.silver.financas.ui.theme.IncomeGreen
import com.silver.financas.util.normalize
import com.silver.financas.util.weekdayLabel
import java.time.LocalDate

private enum class Filter(val label: String) {
    ALL("Todos"), INCOME("Receitas"), EXPENSE("Despesas"), TRANSFER("Transferências"), UNPAID("Não pagos"), CARD("Cartão")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(vm: FinanceViewModel, nav: Nav) {
    val accounts by vm.accounts.collectAsState()
    val cards by vm.cards.collectAsState()
    val cats by vm.categories.collectAsState()
    val txns by vm.txns.collectAsState()
    val month by vm.month.collectAsState()
    val l = rememberLookup(cats, accounts, cards)

    var filter by remember { mutableStateOf(Filter.ALL) }
    var accountFilter by remember { mutableStateOf<Account?>(null) }
    var categoryFilter by remember { mutableStateOf<Category?>(null) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    val list = remember(txns, month, filter, accountFilter, categoryFilter, query, cats) {
        val q = normalize(query)
        txns.filter { t ->
            (q.isNotEmpty() || Calc.inMonth(t, month)) &&
                when (filter) {
                    Filter.ALL -> true
                    Filter.INCOME -> t.type == TxType.INCOME
                    Filter.EXPENSE -> t.type == TxType.EXPENSE
                    Filter.TRANSFER -> t.type == TxType.TRANSFER || t.type == TxType.INVOICE_PAYMENT
                    Filter.UNPAID -> !t.paid
                    Filter.CARD -> t.cardId != null
                } &&
                (accountFilter == null || t.accountId == accountFilter?.id || t.toAccountId == accountFilter?.id) &&
                (categoryFilter == null || t.categoryId == categoryFilter?.id || l.cats[t.categoryId ?: -1L]?.parentId == categoryFilter?.id) &&
                (q.isEmpty() || normalize(t.description + " " + t.tags + " " + t.notes).contains(q))
        }
    }
    val summary = remember(txns, month) { Calc.monthSummary(txns, month) }
    val forecast = remember(accounts, cards, txns, month) { Calc.forecast(accounts, cards, txns, month) }
    val grouped = remember(list) { list.groupBy { it.date }.toSortedMap(reverseOrder()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (searching) OutlinedTextField(
                        query, { query = it }, singleLine = true, placeholder = { Text("Buscar descrição, #tag…") },
                        modifier = Modifier.fillMaxWidth()
                    ) else Text("Lançamentos")
                },
                actions = {
                    IconButton(onClick = { searching = !searching; if (!searching) query = "" }) {
                        Icon(if (searching) Icons.Filled.Close else Icons.Filled.Search, "Buscar")
                    }
                    IconButton(onClick = { vm.hide = !vm.hide }) {
                        Icon(if (vm.hide) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Ocultar valores")
                    }
                }
            )
        },
        floatingActionButton = { AddFab(nav) }
    ) { p ->
        LazyColumn(contentPadding = PaddingValues(top = p.calculateTopPadding(), bottom = 96.dp)) {
            item {
                if (query.isEmpty()) MonthSelector(month, { vm.month.value = it }, Modifier.padding(horizontal = 8.dp))
                else Text("Resultados em todos os meses", Modifier.padding(16.dp), style = MaterialTheme.typography.labelLarge)
            }
            item {
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Row(Modifier.padding(14.dp)) {
                        LabelValue("Receitas", summary.income, IncomeGreen, Modifier.weight(1f))
                        LabelValue("Despesas", summary.expense, ExpenseRed, Modifier.weight(1f))
                    }
                    Row(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
                        LabelValue("Balanço do mês", summary.result, if (summary.result >= 0) IncomeGreen else ExpenseRed, Modifier.weight(1f))
                        LabelValue("Saldo previsto", forecast, modifier = Modifier.weight(1f))
                    }
                }
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Filter.entries.forEach { f ->
                        FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) })
                    }
                }
            }
            item {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DropdownField("Conta", accounts, accountFilter, { it.name }, { accountFilter = it }, Modifier.weight(1f), noneLabel = "Todas")
                    DropdownField("Categoria", cats.sortedBy { it.kind }, categoryFilter, { it.name }, { categoryFilter = it }, Modifier.weight(1f), noneLabel = "Todas")
                }
            }
            if (list.isEmpty()) item { EmptyState("Nenhum lançamento por aqui. Toque em + para adicionar.") }
            grouped.forEach { (day, dayItems) ->
                item(key = "h$day") {
                    val d = LocalDate.ofEpochDay(day)
                    val dayTotal = dayItems.sumOf {
                        when (it.type) { TxType.INCOME -> it.amount; TxType.EXPENSE -> -it.amount; else -> 0L }
                    }
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${d.dayOfMonth.toString().padStart(2, '0')}/${d.monthValue.toString().padStart(2, '0')} · ${d.weekdayLabel()}",
                            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f)
                        )
                        MoneyText(dayTotal, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(dayItems, key = { it.id }) { t ->
                    TxnRow(t, l, onClick = { nav.openTxn(id = t.id) }, onTogglePaid = { vm.togglePaid(t) })
                }
            }
        }
    }
}
