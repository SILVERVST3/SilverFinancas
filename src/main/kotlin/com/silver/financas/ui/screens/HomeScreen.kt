package com.silver.financas.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.silver.financas.ui.Nav
import com.silver.financas.data.*
import com.silver.financas.ui.FinanceViewModel
import com.silver.financas.ui.components.*
import com.silver.financas.ui.openTxn
import com.silver.financas.ui.theme.ExpenseRed
import com.silver.financas.ui.theme.IncomeGreen
import com.silver.financas.util.label
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: FinanceViewModel, nav: Nav) {
    val accounts by vm.accounts.collectAsState()
    val cards by vm.cards.collectAsState()
    val cats by vm.categories.collectAsState()
    val txns by vm.txns.collectAsState()
    val budgets by vm.budgets.collectAsState()
    val l = rememberLookup(cats, accounts, cards)
    val today = LocalDate.now()
    val ym = YearMonth.now()

    val balance = remember(accounts, txns) { Calc.totalBalance(accounts, txns, today, includeUnpaid = false) }
    val forecast = remember(accounts, cards, txns) { Calc.forecast(accounts, cards, txns, ym) }
    val summary = remember(txns) { Calc.monthSummary(txns, ym) }
    val bills = remember(cards, txns) { Calc.bills(cards, txns) }
    val byCat = remember(txns, cats) { Calc.byCategory(txns, l.cats, ym, TxType.EXPENSE) }

    val greeting = when (LocalTime.now().hour) { in 5..11 -> "Bom dia"; in 12..17 -> "Boa tarde"; else -> "Boa noite" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(greeting, fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = { vm.hide = !vm.hide }) {
                        Icon(if (vm.hide) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Ocultar valores")
                    }
                    IconButton(onClick = { nav.navigate("settings") }) { Icon(Icons.Filled.Settings, "Configurações") }
                }
            )
        },
        floatingActionButton = { AddFab(nav) }
    ) { p ->
        LazyColumn(contentPadding = PaddingValues(top = p.calculateTopPadding(), bottom = 96.dp)) {
            // ---------- saldo
            item {
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text("Saldo geral", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f))
                        MoneyText(balance, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.headlineMedium, weight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Row {
                            Text("Previsto p/ fim de ${ym.label().substringBefore(' ').lowercase()}: ",
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
                            MoneyText(forecast, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.bodySmall, weight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(16.dp))
                        Row {
                            MiniStat("Receitas do mês", summary.income, Icons.Filled.ArrowUpward, Modifier.weight(1f))
                            MiniStat("Despesas do mês", summary.expense, Icons.Filled.ArrowDownward, Modifier.weight(1f))
                        }
                    }
                }
            }
            // ---------- atalhos
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Shortcut("Despesa", Icons.Filled.Remove, ExpenseRed) { nav.openTxn("EXPENSE") }
                    Shortcut("Receita", Icons.Filled.Add, IncomeGreen) { nav.openTxn("INCOME") }
                    Shortcut("Cartão", Icons.Filled.CreditCard, MaterialTheme.colorScheme.secondary) { nav.openTxn("CARD") }
                    Shortcut("Transferir", Icons.Filled.SwapHoriz, MaterialTheme.colorScheme.secondary) { nav.openTxn("TRANSFER") }
                    Shortcut("Recibo", Icons.Filled.PhotoCamera, MaterialTheme.colorScheme.primary) { nav.openTxn("SCAN") }
                }
            }
            // ---------- contas a pagar
            if (bills.isNotEmpty()) item {
                SectionCard("Contas a pagar") {
                    bills.take(5).forEach { b ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable {
                                if (b.txn != null) nav.openTxn(id = b.txn.id) else b.invoice?.let { nav.navigate("card/${it.card.id}") }
                            }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val late = b.date.isBefore(today)
                            IconBubble(if (b.invoice != null) Icons.Filled.CreditCard else Icons.Filled.Event, if (late) ExpenseRed else Color(0xFFFFA000), 34)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(b.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(b.detail, style = MaterialTheme.typography.bodySmall, color = if (late) ExpenseRed else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            MoneyText(b.amount, weight = FontWeight.SemiBold)
                            if (b.txn != null) {
                                IconButton(onClick = { vm.togglePaid(b.txn) }) { Icon(Icons.Filled.Check, "Marcar como pago", tint = IncomeGreen) }
                            }
                        }
                    }
                }
            }
            // ---------- contas
            item {
                SectionCard("Contas", action = { TextButton(onClick = { nav.navigate("accounts") }) { Text("Gerenciar") } }) {
                    val active = accounts.filter { !it.archived }
                    if (active.isEmpty()) Text("Nenhuma conta cadastrada")
                    active.forEach { a ->
                        val b = remember(a, txns) { Calc.accountBalance(a, txns, today, false) }
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconBubble(Icons.Filled.AccountBalance, Color(a.color), 34)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(a.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(a.type.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            MoneyText(b, weight = FontWeight.SemiBold, color = if (b < 0) ExpenseRed else Color.Unspecified)
                        }
                    }
                }
            }
            // ---------- cartões
            item {
                SectionCard("Cartões de crédito", action = { TextButton(onClick = { nav.navigate("cards") }) { Text("Gerenciar") } }) {
                    val active = cards.filter { !it.archived }
                    if (active.isEmpty()) Text("Cadastre seus cartões para acompanhar as faturas", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    active.forEach { c ->
                        val inv = remember(c, txns) { Calc.invoice(c, Calc.invoiceMonthFor(c, today), txns) }
                        val used = remember(c, txns) { Calc.cardUsed(c, txns) }
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { nav.navigate("card/${c.id}") }.padding(vertical = 6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconBubble(Icons.Filled.CreditCard, Color(c.color), 34)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(c.name)
                                    Text("Fatura ${inv.status.label.lowercase()} · vence ${inv.dueDate.dayOfMonth}/${inv.dueDate.monthValue}",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                MoneyText(inv.total, weight = FontWeight.SemiBold)
                            }
                            if (c.limitCents > 0) {
                                Spacer(Modifier.height(6.dp))
                                ProgressLine(used.toFloat() / c.limitCents, Color(c.color))
                                Row {
                                    Text("Disponível ", style = MaterialTheme.typography.labelSmall)
                                    MoneyText(c.limitCents - used, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
            // ---------- limites
            if (budgets.isNotEmpty()) item {
                SectionCard("Limites de gastos", action = { TextButton(onClick = { nav.navigate("budgets") }) { Text("Ver todos") } }) {
                    budgets.mapNotNull { b -> l.cats[b.categoryId]?.let { b to it } }
                        .map { (b, c) -> Triple(b, c, Calc.spentInCategory(txns, l.cats, c.id, ym)) }
                        .sortedByDescending { if (it.first.limitCents > 0) it.third.toFloat() / it.first.limitCents else 0f }
                        .take(4).forEach { (b, c, spent) -> BudgetRow(c, spent, b.limitCents) }
                }
            }
            // ---------- gráfico
            item {
                SectionCard("Despesas por categoria") {
                    if (byCat.isEmpty()) Text("Sem despesas neste mês", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else Row(verticalAlignment = Alignment.CenterVertically) {
                        DonutChart(
                            byCat.map { (id, v) -> (id?.let { l.cats[it] }?.let { Color(it.color) } ?: Color.Gray) to v },
                            Modifier.size(130.dp)
                        ) { MoneyText(summary.expense, style = MaterialTheme.typography.labelMedium, weight = FontWeight.Bold) }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            val total = byCat.sumOf { it.second }.coerceAtLeast(1L)
                            byCat.take(5).forEach { (id, v) ->
                                val c = id?.let { l.cats[it] }
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                                    Box(Modifier.size(10.dp).clip(CircleShape).background(c?.let { Color(it.color) } ?: Color.Gray))
                                    Spacer(Modifier.width(6.dp))
                                    Text(c?.name ?: "Sem categoria", Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                                    Text("${v * 100 / total}%", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, cents: Long, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(8.dp))
        Column {
            Text(label, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
            MoneyText(cents, color = Color.White, weight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun Shortcut(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconBubble(icon, color, 46)
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun BudgetRow(c: Category, spent: Long, limit: Long) {
    val frac = if (limit > 0) spent.toFloat() / limit else 0f
    val color = when { frac >= 1f -> ExpenseRed; frac >= 0.8f -> Color(0xFFFFA000); else -> IncomeGreen }
    Column(Modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryBubble(c, 28)
            Spacer(Modifier.width(8.dp))
            Text(c.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            MoneyText(spent, style = MaterialTheme.typography.bodySmall, weight = FontWeight.SemiBold)
            Text(" / ", style = MaterialTheme.typography.bodySmall)
            MoneyText(limit, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(4.dp))
        ProgressLine(frac, color)
        val rest = limit - spent
        Text(
            if (rest >= 0) "Restam " + com.silver.financas.util.moneyHidden(rest, com.silver.financas.ui.theme.LocalHide.current)
            else "Estourou " + com.silver.financas.util.moneyHidden(-rest, com.silver.financas.ui.theme.LocalHide.current),
            style = MaterialTheme.typography.labelSmall, color = if (rest >= 0) MaterialTheme.colorScheme.onSurfaceVariant else ExpenseRed
        )
    }
}
