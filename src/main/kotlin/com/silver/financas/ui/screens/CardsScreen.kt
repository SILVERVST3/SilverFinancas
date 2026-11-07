package com.silver.financas.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.silver.financas.ui.Nav
import com.silver.financas.data.*
import com.silver.financas.ui.FinanceViewModel
import com.silver.financas.ui.components.*
import com.silver.financas.ui.openTxn
import com.silver.financas.ui.theme.ExpenseRed
import com.silver.financas.ui.theme.IncomeGreen
import com.silver.financas.util.br
import com.silver.financas.util.key
import com.silver.financas.util.label
import com.silver.financas.util.toYearMonth
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardsScreen(vm: FinanceViewModel, nav: Nav) {
    val cards by vm.cards.collectAsState()
    val txns by vm.txns.collectAsState()
    var creating by remember { mutableStateOf(false) }
    val today = LocalDate.now()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Cartões de crédito") }, navigationIcon = { BackButton(nav) }) },
        floatingActionButton = { FloatingActionButton(onClick = { creating = true }) { Icon(Icons.Filled.Add, "Novo cartão") } }
    ) { p ->
        LazyColumn(contentPadding = PaddingValues(top = p.calculateTopPadding(), bottom = 96.dp)) {
            if (cards.isEmpty()) item { EmptyState("Nenhum cartão. Toque em + para cadastrar.") }
            items(cards, key = { it.id }) { c ->
                val inv = remember(c, txns) { Calc.invoice(c, Calc.invoiceMonthFor(c, today), txns) }
                val used = remember(c, txns) { Calc.cardUsed(c, txns) }
                SectionCard {
                    Column(Modifier.clickable { nav.navigate("card/${c.id}") }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBubble(Icons.Filled.CreditCard, Color(c.color))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(c.name + if (c.archived) " (arquivado)" else "", fontWeight = FontWeight.SemiBold)
                                Text("Fecha dia ${c.closingDay} · vence dia ${c.dueDay}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row {
                            LabelValue("Fatura atual (${inv.status.label.lowercase()})", inv.total, modifier = Modifier.weight(1f))
                            LabelValue("Limite disponível", c.limitCents - used, IncomeGreen, Modifier.weight(1f))
                        }
                        if (c.limitCents > 0) {
                            Spacer(Modifier.height(8.dp)); ProgressLine(used.toFloat() / c.limitCents, Color(c.color))
                        }
                    }
                }
            }
        }
    }
    if (creating) CardDialog(vm, null) { creating = false }
}

@Composable
fun CardDialog(vm: FinanceViewModel, card: CreditCard?, onDismiss: () -> Unit) {
    val accounts by vm.accounts.collectAsState()
    var name by remember { mutableStateOf(card?.name ?: "") }
    var limit by remember { mutableLongStateOf(card?.limitCents ?: 0L) }
    var closing by remember { mutableStateOf((card?.closingDay ?: 1).toString()) }
    var due by remember { mutableStateOf((card?.dueDay ?: 10).toString()) }
    var accountId by remember { mutableStateOf(card?.paymentAccountId) }
    var color by remember { mutableIntStateOf(card?.color ?: 0xFF8A05BE.toInt()) }
    var archived by remember { mutableStateOf(card?.archived ?: false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (card == null) "Novo cartão" else "Editar cartão") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nome (ex.: Nubank Roxinho)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                MoneyField(limit, { limit = it }, "Limite")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(closing, { closing = it.filter { c -> c.isDigit() }.take(2) }, label = { Text("Dia fechamento") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(due, { due = it.filter { c -> c.isDigit() }.take(2) }, label = { Text("Dia vencimento") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                DropdownField("Conta para pagamento", accounts.filter { !it.archived }, accounts.firstOrNull { it.id == accountId }, { it.name }, { accountId = it?.id }, noneLabel = "Nenhuma")
                if (card != null) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Arquivado", Modifier.weight(1f)); Switch(archived, { archived = it })
                }
                Text("Cor", style = MaterialTheme.typography.labelLarge)
                ColorPicker(color) { color = it }
                err?.let { Text(it, color = ExpenseRed) }
                if (card != null) TextButton(onClick = { confirmDelete = true }) { Text("Excluir cartão", color = ExpenseRed) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val cd = closing.toIntOrNull() ?: 0
                val dd = due.toIntOrNull() ?: 0
                if (name.isBlank()) { err = "Informe o nome"; return@TextButton }
                if (cd !in 1..31 || dd !in 1..31) { err = "Dias devem estar entre 1 e 31"; return@TextButton }
                val c = (card ?: CreditCard(name = name, limitCents = 0, closingDay = cd, dueDay = dd, color = color)).copy(
                    name = name.trim(), limitCents = limit, closingDay = cd, dueDay = dd,
                    paymentAccountId = accountId, color = color, archived = archived
                )
                vm.saveCard(c); onDismiss()
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
    if (confirmDelete && card != null) {
        ConfirmDialog("Excluir cartão", "Se o cartão tiver lançamentos, ele será arquivado em vez de excluído.",
            onConfirm = { vm.deleteCard(card); onDismiss() }, onDismiss = { confirmDelete = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardDetailScreen(vm: FinanceViewModel, nav: Nav, cardId: Long) {
    val cards by vm.cards.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val cats by vm.categories.collectAsState()
    val txns by vm.txns.collectAsState()
    val card = cards.firstOrNull { it.id == cardId }
    val l = rememberLookup(cats, accounts, cards)
    var month by remember(card?.id) { mutableStateOf(card?.let { Calc.invoiceMonthFor(it, LocalDate.now()).toYearMonth() } ?: YearMonth.now()) }
    var editing by remember { mutableStateOf(false) }
    var paying by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(card?.name ?: "Cartão") },
                navigationIcon = { BackButton(nav) },
                actions = { IconButton(onClick = { editing = true }) { Icon(Icons.Filled.Edit, "Editar") } }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { nav.openTxn("CARD", cardId = cardId) }) { Icon(Icons.Filled.Add, "Nova despesa no cartão") }
        }
    ) { p ->
        if (card == null) { Box(Modifier.padding(p)) { EmptyState("Cartão não encontrado") }; return@Scaffold }
        val inv = remember(card, txns, month) { Calc.invoice(card, month.key(), txns) }
        LazyColumn(contentPadding = PaddingValues(top = p.calculateTopPadding(), bottom = 96.dp)) {
            item { MonthSelector(month, { month = it }, Modifier.padding(horizontal = 8.dp)) }
            item {
                SectionCard {
                    Text("Fatura de ${month.label().lowercase()} · ${inv.status.label}",
                        style = MaterialTheme.typography.labelLarge,
                        color = when (inv.status) { InvoiceStatus.OVERDUE -> ExpenseRed; InvoiceStatus.PAID -> IncomeGreen; else -> MaterialTheme.colorScheme.primary })
                    MoneyText(inv.total, style = MaterialTheme.typography.headlineMedium, weight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text("Fecha em ${inv.closingDate.br()} · vence em ${inv.dueDate.br()}", style = MaterialTheme.typography.bodySmall)
                    if (inv.paid > 0) Row {
                        Text("Pago: ", style = MaterialTheme.typography.bodySmall)
                        MoneyText(inv.paid, style = MaterialTheme.typography.bodySmall, color = IncomeGreen)
                        Text("  ·  falta: ", style = MaterialTheme.typography.bodySmall)
                        MoneyText(inv.remaining, style = MaterialTheme.typography.bodySmall)
                    }
                    if (inv.remaining > 0) {
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = { paying = true }) { Text("Pagar fatura") }
                    }
                }
            }
            if (inv.items.isEmpty()) item { EmptyState("Nenhuma compra nesta fatura") }
            items(inv.items, key = { it.id }) { t -> TxnRow(t, l, onClick = { nav.openTxn(id = t.id) }) }
            val payments = txns.filter { it.type == TxType.INVOICE_PAYMENT && it.cardId == card.id && it.invoiceMonth == month.key() }
            if (payments.isNotEmpty()) {
                item { Text("Pagamentos", Modifier.padding(16.dp, 12.dp, 16.dp, 0.dp), style = MaterialTheme.typography.labelLarge) }
                items(payments, key = { "p" + it.id }) { t -> TxnRow(t, l, onClick = { nav.openTxn(id = t.id) }) }
            }
        }
    }

    if (editing && card != null) CardDialog(vm, card) { editing = false }
    if (paying && card != null) {
        val inv = Calc.invoice(card, month.key(), txns)
        PayInvoiceDialog(vm, card, inv, accounts.filter { !it.archived }) { paying = false }
    }
}

@Composable
private fun PayInvoiceDialog(vm: FinanceViewModel, card: CreditCard, inv: Invoice, accounts: List<Account>, onDismiss: () -> Unit) {
    var amount by remember { mutableLongStateOf(inv.remaining) }
    var accountId by remember { mutableStateOf(card.paymentAccountId ?: accounts.firstOrNull()?.id) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pagar fatura") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MoneyField(amount, { amount = it }, "Valor pago")
                DropdownField("Pagar com a conta", accounts, accounts.firstOrNull { it.id == accountId }, { it.name }, { accountId = it?.id })
                DateField("Data do pagamento", date, { date = it })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val acc = accountId ?: return@TextButton
                if (amount <= 0) return@TextButton
                vm.payInvoice(card, inv.month, acc, amount, date); onDismiss()
            }) { Text("Confirmar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
