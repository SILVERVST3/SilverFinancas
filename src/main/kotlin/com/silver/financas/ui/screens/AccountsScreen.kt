package com.silver.financas.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.silver.financas.ui.Nav
import com.silver.financas.data.Account
import com.silver.financas.data.AccountType
import com.silver.financas.data.Calc
import com.silver.financas.ui.FinanceViewModel
import com.silver.financas.ui.components.*
import com.silver.financas.ui.theme.ExpenseRed
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(vm: FinanceViewModel, nav: Nav) {
    val accounts by vm.accounts.collectAsState()
    val cards by vm.cards.collectAsState()
    val txns by vm.txns.collectAsState()
    var editing by remember { mutableStateOf<Account?>(null) }
    var creating by remember { mutableStateOf(false) }
    var showArchived by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val total = remember(accounts, txns) { Calc.totalBalance(accounts, txns, today, false) }
    val forecast = remember(accounts, cards, txns) { Calc.forecast(accounts, cards, txns, YearMonth.now()) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Contas") }, navigationIcon = { BackButton(nav) }) },
        floatingActionButton = { FloatingActionButton(onClick = { creating = true }) { Icon(Icons.Filled.Add, "Nova conta") } }
    ) { p ->
        LazyColumn(contentPadding = PaddingValues(top = p.calculateTopPadding(), bottom = 96.dp)) {
            item {
                SectionCard {
                    Row {
                        LabelValue("Saldo atual", total, modifier = Modifier.weight(1f))
                        LabelValue("Previsto fim do mês", forecast, modifier = Modifier.weight(1f))
                    }
                }
            }
            val list = accounts.filter { showArchived || !it.archived }
            items(list, key = { it.id }) { a ->
                val bal = remember(a, txns) { Calc.accountBalance(a, txns, today, false) }
                val prev = remember(a, txns) { Calc.accountBalance(a, txns, YearMonth.now().atEndOfMonth(), true) }
                ListItem(
                    headlineContent = { Text(a.name + if (a.archived) " (arquivada)" else "") },
                    supportingContent = {
                        Row {
                            Text(a.type.label + " · previsto ", style = MaterialTheme.typography.bodySmall)
                            MoneyText(prev, style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    leadingContent = { IconBubble(Icons.Filled.AccountBalance, Color(a.color)) },
                    trailingContent = { MoneyText(bal, weight = FontWeight.SemiBold, color = if (bal < 0) ExpenseRed else Color.Unspecified) },
                    modifier = Modifier.clickable { editing = a }
                )
            }
            if (accounts.any { it.archived }) item {
                TextButton(onClick = { showArchived = !showArchived }, Modifier.padding(horizontal = 8.dp)) {
                    Text(if (showArchived) "Ocultar arquivadas" else "Mostrar arquivadas")
                }
            }
        }
    }

    if (creating || editing != null) {
        val a = editing
        AccountDialog(
            vm, a, currentBalance = a?.let { Calc.accountBalance(it, txns, today, false) } ?: 0L,
            onDismiss = { creating = false; editing = null }
        )
    }
}

@Composable
private fun AccountDialog(vm: FinanceViewModel, account: Account?, currentBalance: Long, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(account?.name ?: "") }
    var type by remember { mutableStateOf(account?.type ?: AccountType.CHECKING) }
    var initial by remember { mutableLongStateOf(account?.initialBalance ?: 0L) }
    var negativeInitial by remember { mutableStateOf((account?.initialBalance ?: 0L) < 0) }
    var color by remember { mutableIntStateOf(account?.color ?: palette.first()) }
    var include by remember { mutableStateOf(account?.includeInTotal ?: true) }
    var archived by remember { mutableStateOf(account?.archived ?: false) }
    var adjusting by remember { mutableStateOf(false) }
    var wanted by remember { mutableLongStateOf(kotlin.math.abs(currentBalance)) }
    var wantedNegative by remember { mutableStateOf(currentBalance < 0) }
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (account == null) "Nova conta" else "Editar conta") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nome (ex.: Nubank, Itaú)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                DropdownField("Tipo", AccountType.entries, type, { it.label }, { type = it ?: AccountType.CHECKING })
                MoneyField(kotlin.math.abs(initial), { initial = it }, "Saldo inicial")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Saldo inicial negativo", Modifier.weight(1f)); Switch(negativeInitial, { negativeInitial = it })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Somar no saldo geral", Modifier.weight(1f)); Switch(include, { include = it })
                }
                if (account != null) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Arquivada", Modifier.weight(1f)); Switch(archived, { archived = it })
                }
                Text("Cor", style = MaterialTheme.typography.labelLarge)
                ColorPicker(color) { color = it }
                if (account != null) {
                    if (!adjusting) TextButton(onClick = { adjusting = true }) { Text("Reajustar saldo atual") }
                    else {
                        Text("Informe o saldo real da conta hoje. Será criado um lançamento de reajuste.", style = MaterialTheme.typography.bodySmall)
                        MoneyField(wanted, { wanted = it }, "Saldo atual correto")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Saldo negativo", Modifier.weight(1f)); Switch(wantedNegative, { wantedNegative = it })
                        }
                    }
                    TextButton(onClick = { confirmDelete = true }) { Text("Excluir conta", color = ExpenseRed) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) return@TextButton
                val init = kotlin.math.abs(initial) * if (negativeInitial) -1 else 1
                val a = (account ?: Account(name = name, color = color)).copy(
                    name = name.trim(), type = type, initialBalance = init, color = color,
                    includeInTotal = include, archived = archived
                )
                vm.saveAccount(a)
                if (account != null && adjusting) {
                    // reajuste considera o novo saldo inicial
                    val newCurrent = currentBalance - account.initialBalance + init
                    vm.adjustBalance(a, newCurrent, wanted * if (wantedNegative) -1 else 1)
                }
                onDismiss()
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
    if (confirmDelete && account != null) {
        ConfirmDialog("Excluir conta", "Se a conta tiver lançamentos, ela será arquivada em vez de excluída.",
            onConfirm = { vm.deleteAccount(account); onDismiss() }, onDismiss = { confirmDelete = false })
    }
}
