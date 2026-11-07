package com.silver.financas.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.silver.financas.ui.Nav
import com.silver.financas.data.*
import com.silver.financas.ui.FinanceViewModel
import com.silver.financas.ui.components.*
import com.silver.financas.ui.theme.ExpenseRed
import com.silver.financas.ui.theme.IncomeGreen
import com.silver.financas.ui.theme.TransferBlue
import com.silver.financas.util.label
import com.silver.financas.util.toYearMonth
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.silver.financas.util.openExternally
import com.silver.financas.util.pickFile
import java.io.File
import java.time.LocalDate
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TransactionEditScreen(vm: FinanceViewModel, nav: Nav, type: String, id: Long, presetCard: Long) {
    val accounts by vm.accounts.collectAsState()
    val cards by vm.cards.collectAsState()
    val cats by vm.categories.collectAsState()
    val activeAccounts = accounts.filter { !it.archived }
    val activeCards = cards.filter { !it.archived }

    var original by remember { mutableStateOf<Txn?>(null) }
    var loaded by remember { mutableStateOf(id == 0L) }

    var txType by remember { mutableStateOf(if (type == "INCOME") TxType.INCOME else if (type == "TRANSFER") TxType.TRANSFER else TxType.EXPENSE) }
    var cardMode by remember { mutableStateOf(type == "CARD" || presetCard > 0) }
    var amount by remember { mutableLongStateOf(0L) }
    var description by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var categoryId by remember { mutableStateOf<Long?>(null) }
    var categoryTouched by remember { mutableStateOf(false) }
    var accountId by remember { mutableStateOf<Long?>(null) }
    var toAccountId by remember { mutableStateOf<Long?>(null) }
    var cardId by remember { mutableStateOf<Long?>(if (presetCard > 0) presetCard else null) }
    var paid by remember { mutableStateOf(true) }
    var recurrence by remember { mutableStateOf(Recurrence.NONE) }
    var frequency by remember { mutableStateOf(Frequency.MONTHLY) }
    var countText by remember { mutableStateOf("2") }
    var amountIsTotal by remember { mutableStateOf(true) }
    var tags by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var attachment by remember { mutableStateOf<String?>(null) }
    var reading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var scopeAction by remember { mutableStateOf<String?>(null) } // "save" | "delete"
    var confirmDelete by remember { mutableStateOf(false) }

    // carregar lançamento existente
    LaunchedEffect(id) {
        if (id > 0) {
            vm.getTxn(id)?.let { t ->
                original = t
                txType = if (t.type == TxType.INVOICE_PAYMENT) TxType.TRANSFER else t.type
                cardMode = t.cardId != null && t.type != TxType.INVOICE_PAYMENT
                amount = t.amount; description = t.description; date = LocalDate.ofEpochDay(t.date)
                categoryId = t.categoryId; categoryTouched = true
                accountId = t.accountId; toAccountId = t.toAccountId; cardId = t.cardId
                paid = t.paid; recurrence = t.recurrence; frequency = t.frequency ?: Frequency.MONTHLY
                tags = t.tags; notes = t.notes; attachment = t.attachment
            }
            loaded = true
        }
    }
    // padrões
    LaunchedEffect(activeAccounts.size, activeCards.size) {
        if (accountId == null) accountId = activeAccounts.firstOrNull()?.id
        if (cardId == null) cardId = activeCards.firstOrNull()?.id
    }

    val isIncome = txType == TxType.INCOME
    val kind = if (isIncome) CategoryKind.INCOME else CategoryKind.EXPENSE
    val catOptions = remember(cats, kind) {
        val top = cats.filter { it.kind == kind && it.parentId == null }
        top.flatMap { p -> listOf(p) + cats.filter { it.parentId == p.id } }
    }
    val catMap = remember(cats) { cats.associateBy { it.id } }

    // ---- recibo / comprovante: anexa um arquivo (imagem ou PDF) do computador
    fun attachFile() {
        val f = pickFile("Escolher recibo / comprovante", listOf("jpg", "jpeg", "png", "webp", "bmp", "pdf")) ?: return
        val dir = File(vm.store.dir, "anexos").apply { mkdirs() }
        val dest = File(dir, UUID.randomUUID().toString() + "." + f.extension.lowercase())
        runCatching { f.copyTo(dest) }.onSuccess { attachment = dest.absolutePath }
            .onFailure { vm.message = "Não consegui anexar: ${it.message}" }
    }
    LaunchedEffect(Unit) { if (type == "SCAN" && id == 0L) attachFile() }

    // ---- salvar
    fun validate(): Boolean {
        val isPayment = original?.type == TxType.INVOICE_PAYMENT
        error = when {
            amount <= 0 -> "Informe um valor"
            isPayment && accountId == null -> "Escolha a conta"
            isPayment -> null
            txType == TxType.TRANSFER && (accountId == null || toAccountId == null) -> "Escolha as contas de origem e destino"
            txType == TxType.TRANSFER && accountId == toAccountId -> "Origem e destino devem ser diferentes"
            txType != TxType.TRANSFER && cardMode && cardId == null -> "Cadastre um cartão primeiro (Mais › Cartões)"
            txType != TxType.TRANSFER && !cardMode && accountId == null -> "Escolha uma conta"
            recurrence != Recurrence.NONE && original == null && (countText.toIntOrNull() ?: -1) < (if (recurrence == Recurrence.INSTALLMENT) 2 else 0) ->
                if (recurrence == Recurrence.INSTALLMENT) "Informe 2 ou mais parcelas" else "Repetições inválidas"
            else -> null
        }
        return error == null
    }

    fun doSave(scope: EditScope) {
        val o = original
        val desc = description.ifBlank {
            when (txType) { TxType.TRANSFER -> "Transferência"; TxType.INCOME -> "Receita"; else -> catMap[categoryId ?: -1L]?.name ?: "Despesa" }
        }
        if (o == null) {
            vm.addTxn(
                TxnDraft(
                    type = txType, amount = amount, description = desc, date = date,
                    categoryId = categoryId, accountId = if (cardMode) null else accountId,
                    toAccountId = toAccountId, cardId = if (cardMode && txType != TxType.TRANSFER) cardId else null,
                    paid = paid, recurrence = recurrence, frequency = frequency,
                    count = countText.toIntOrNull() ?: 0, amountIsTotal = amountIsTotal,
                    tags = tags, notes = notes, attachment = attachment
                )
            )
        } else {
            val edited = o.copy(
                type = if (o.type == TxType.INVOICE_PAYMENT) o.type else txType,
                amount = amount, description = desc, date = date.toEpochDay(),
                categoryId = if (txType == TxType.TRANSFER) null else categoryId,
                accountId = if (cardMode) null else accountId,
                toAccountId = if (txType == TxType.TRANSFER) toAccountId else null,
                cardId = if (o.type == TxType.INVOICE_PAYMENT) o.cardId else if (cardMode) cardId else null,
                paid = paid, tags = tags.trim(), notes = notes.trim(), attachment = attachment
            )
            vm.updateTxn(o, edited, scope)
        }
        nav.popBackStack()
    }

    val title = when {
        original != null -> "Editar lançamento"
        txType == TxType.INCOME -> "Nova receita"
        txType == TxType.TRANSFER -> "Nova transferência"
        cardMode -> "Despesa no cartão"
        else -> "Nova despesa"
    }
    val accent = when (txType) { TxType.INCOME -> IncomeGreen; TxType.EXPENSE -> ExpenseRed; else -> TransferBlue }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { BackButton(nav) },
                actions = {
                    if (original != null) IconButton(onClick = {
                        if (original?.groupId != null) scopeAction = "delete" else confirmDelete = true
                    }) { Icon(Icons.Filled.Delete, "Excluir") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = accent.copy(alpha = 0.12f))
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = {
                        if (validate()) {
                            if (original?.groupId != null) scopeAction = "save" else doSave(EditScope.ONLY_THIS)
                        }
                    },
                    enabled = loaded,
                    colors = ButtonDefaults.buttonColors(containerColor = accent),
                    modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp)
                ) { Text("Salvar", fontWeight = FontWeight.SemiBold) }
            }
        }
    ) { p ->
        Column(
            Modifier.padding(p).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (original == null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(txType == TxType.EXPENSE && !cardMode, { txType = TxType.EXPENSE; cardMode = false; categoryId = null }, { Text("Despesa") })
                    FilterChip(txType == TxType.EXPENSE && cardMode, { txType = TxType.EXPENSE; cardMode = true; categoryId = null }, { Text("Cartão") })
                    FilterChip(txType == TxType.INCOME, { txType = TxType.INCOME; cardMode = false; categoryId = null }, { Text("Receita") })
                    FilterChip(txType == TxType.TRANSFER, { txType = TxType.TRANSFER; cardMode = false }, { Text("Transf.") })
                }
            } else if (original?.type == TxType.INVOICE_PAYMENT) {
                Text("Pagamento de fatura", style = MaterialTheme.typography.labelLarge, color = TransferBlue)
            }

            MoneyField(amount, { amount = it }, "Valor", big = true)

            OutlinedTextField(
                description,
                { description = it; if (!categoryTouched && txType != TxType.TRANSFER) vm.suggestCategory(it, isIncome)?.let { c -> categoryId = c } },
                label = { Text("Descrição") }, singleLine = true, modifier = Modifier.fillMaxWidth()
            )

            DateField("Data", date, { date = it }, Modifier.fillMaxWidth())
            QuickDateChips(date) { date = it }

            if (txType != TxType.TRANSFER) {
                DropdownField(
                    "Categoria", catOptions, catOptions.firstOrNull { it.id == categoryId },
                    { c -> if (c.parentId != null) "   › ${c.name}" else c.name },
                    { categoryId = it?.id; categoryTouched = true },
                    noneLabel = "Sem categoria",
                    leading = { c -> CategoryBubble(c, 28) }
                )
            }

            when {
                txType == TxType.TRANSFER || original?.type == TxType.INVOICE_PAYMENT -> {
                    DropdownField("De (origem)", activeAccounts, activeAccounts.firstOrNull { it.id == accountId }, { it.name }, { accountId = it?.id })
                    if (original?.type != TxType.INVOICE_PAYMENT)
                        DropdownField("Para (destino)", activeAccounts, activeAccounts.firstOrNull { it.id == toAccountId }, { it.name }, { toAccountId = it?.id })
                }
                cardMode -> {
                    DropdownField("Cartão de crédito", activeCards, activeCards.firstOrNull { it.id == cardId }, { it.name }, { cardId = it?.id })
                    activeCards.firstOrNull { it.id == cardId }?.let { c ->
                        val inv = original?.invoiceMonth ?: Calc.invoiceMonthFor(c, date)
                        Text("Entra na fatura de ${inv.toYearMonth().label().lowercase()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                else -> {
                    DropdownField("Conta", activeAccounts, activeAccounts.firstOrNull { it.id == accountId }, { it.name }, { accountId = it?.id })
                }
            }

            if (!cardMode && original?.type != TxType.INVOICE_PAYMENT) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when (txType) { TxType.INCOME -> "Recebido"; TxType.TRANSFER -> "Transferência realizada"; else -> "Pago" },
                        Modifier.weight(1f)
                    )
                    Switch(paid, { paid = it })
                }
            }

            // ---- repetição
            if (original == null) {
                Text("Repetir", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(recurrence == Recurrence.NONE, { recurrence = Recurrence.NONE }, { Text("Não repete") })
                    FilterChip(recurrence == Recurrence.FIXED, { recurrence = Recurrence.FIXED; countText = "0" }, { Text("Fixa") })
                    FilterChip(recurrence == Recurrence.INSTALLMENT, { recurrence = Recurrence.INSTALLMENT; countText = "2"; frequency = Frequency.MONTHLY }, { Text("Parcelada") })
                }
                if (recurrence == Recurrence.FIXED) {
                    DropdownField("Frequência", Frequency.entries, frequency, { it.label }, { frequency = it ?: Frequency.MONTHLY })
                    OutlinedTextField(
                        countText, { countText = it.filter { c -> c.isDigit() }.take(3) },
                        label = { Text("Quantidade de vezes (0 = sem fim)") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                }
                if (recurrence == Recurrence.INSTALLMENT) {
                    OutlinedTextField(
                        countText, { countText = it.filter { c -> c.isDigit() }.take(3) },
                        label = { Text("Número de parcelas") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("O valor informado é o total da compra", Modifier.weight(1f))
                        Switch(amountIsTotal, { amountIsTotal = it })
                    }
                    val n = countText.toIntOrNull() ?: 0
                    if (n >= 2 && amount > 0) {
                        val per = if (amountIsTotal) amount / n else amount
                        Row {
                            Text("$n x ", style = MaterialTheme.typography.bodySmall)
                            MoneyText(per, style = MaterialTheme.typography.bodySmall, weight = FontWeight.SemiBold)
                            Text("  ·  total ", style = MaterialTheme.typography.bodySmall)
                            MoneyText(if (amountIsTotal) amount else amount * n, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            } else if (original?.recurrence != Recurrence.NONE) {
                val o = original!!
                Text(
                    if (o.recurrence == Recurrence.INSTALLMENT) "Parcela ${o.installmentNumber} de ${o.installmentTotal}"
                    else "Lançamento fixo (${o.frequency?.label?.lowercase() ?: "mensal"})",
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary
                )
            }

            OutlinedTextField(tags, { tags = it }, label = { Text("Tags (separadas por vírgula)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(notes, { notes = it }, label = { Text("Observações") }, minLines = 2, modifier = Modifier.fillMaxWidth())

            // ---- anexo / recibo
            Text("Recibo / comprovante", style = MaterialTheme.typography.labelLarge)
            OutlinedButton(onClick = { attachFile() }) { Icon(Icons.Filled.AttachFile, null); Spacer(Modifier.width(6.dp)); Text("Anexar arquivo") }
            attachment?.let { path ->
                val file = File(path)
                val bmp = remember(path) {
                    runCatching { org.jetbrains.skia.Image.makeFromEncoded(file.readBytes()).toComposeImageBitmap() }.getOrNull()
                }
                Box {
                    if (bmp != null) {
                        Image(
                            bmp, "Recibo",
                            Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(12.dp)).clickable { openExternally(file) },
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        OutlinedButton(onClick = { openExternally(file) }, Modifier.fillMaxWidth()) { Text("Abrir anexo: ${file.name}") }
                    }
                    IconButton(onClick = { attachment = null }, Modifier.align(Alignment.TopEnd)) {
                        Icon(Icons.Filled.Close, "Remover", tint = if (bmp != null) Color.White else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            error?.let { Text(it, color = ExpenseRed) }
            Spacer(Modifier.height(24.dp))
        }
    }

    // ---- diálogos
    if (confirmDelete) {
        ConfirmDialog("Excluir lançamento", "Deseja excluir este lançamento?", onConfirm = {
            original?.let { vm.deleteTxn(it, EditScope.ONLY_THIS) }; nav.popBackStack()
        }, onDismiss = { confirmDelete = false })
    }
    scopeAction?.let { action ->
        AlertDialog(
            onDismissRequest = { scopeAction = null },
            title = { Text(if (action == "save") "Aplicar alterações em…" else "Excluir…") },
            text = {
                Column {
                    listOf(
                        EditScope.ONLY_THIS to "Somente este",
                        EditScope.THIS_AND_NEXT to "Este e os próximos",
                        EditScope.ALL to "Todos (inclusive anteriores)"
                    ).forEach { (scope, label) ->
                        TextButton(onClick = {
                            scopeAction = null
                            if (action == "save") doSave(scope)
                            else { original?.let { vm.deleteTxn(it, scope) }; nav.popBackStack() }
                        }, modifier = Modifier.fillMaxWidth()) { Text(label, Modifier.fillMaxWidth()) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { scopeAction = null }) { Text("Cancelar") } }
        )
    }
}
