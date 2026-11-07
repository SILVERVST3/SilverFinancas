package com.silver.financas.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.silver.financas.ui.Nav
import com.silver.financas.data.*
import com.silver.financas.ui.components.CategoryBubble
import com.silver.financas.ui.components.IconBubble
import com.silver.financas.ui.components.MoneyText
import com.silver.financas.ui.openTxn
import com.silver.financas.ui.theme.ExpenseRed
import com.silver.financas.ui.theme.IncomeGreen
import com.silver.financas.ui.theme.TransferBlue

@Composable
fun BackButton(nav: Nav) {
    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
}

data class Lookup(
    val cats: Map<Long, Category>,
    val accounts: Map<Long, Account>,
    val cards: Map<Long, CreditCard>
)

@Composable
fun rememberLookup(cats: List<Category>, accounts: List<Account>, cards: List<CreditCard>): Lookup =
    remember(cats, accounts, cards) { Lookup(cats.associateBy { it.id }, accounts.associateBy { it.id }, cards.associateBy { it.id }) }

fun txnColor(t: Txn): Color = when (t.type) {
    TxType.INCOME -> IncomeGreen
    TxType.EXPENSE -> ExpenseRed
    TxType.TRANSFER -> TransferBlue
    TxType.INVOICE_PAYMENT -> TransferBlue
}

fun txnSubtitle(t: Txn, l: Lookup): String {
    val parts = mutableListOf<String>()
    when (t.type) {
        TxType.TRANSFER -> parts += "${l.accounts[t.accountId]?.name ?: "?"} → ${l.accounts[t.toAccountId]?.name ?: "?"}"
        TxType.INVOICE_PAYMENT -> parts += "${l.accounts[t.accountId]?.name ?: "?"} → ${l.cards[t.cardId]?.name ?: "cartão"}"
        else -> {
            t.categoryId?.let { id ->
                val c = l.cats[id]
                if (c != null) parts += (c.parentId?.let { p -> l.cats[p]?.name + " › " } ?: "") + c.name
            }
            parts += t.cardId?.let { "💳 " + (l.cards[it]?.name ?: "cartão") } ?: (l.accounts[t.accountId]?.name ?: "")
        }
    }
    when (t.recurrence) {
        Recurrence.INSTALLMENT -> parts += "${t.installmentNumber}/${t.installmentTotal}"
        Recurrence.FIXED -> parts += "fixa"
        else -> {}
    }
    if (t.tags.isNotBlank()) parts += t.tags.split(",").joinToString(" ") { "#" + it.trim() }
    return parts.filter { it.isNotBlank() }.joinToString(" · ")
}

@Composable
fun TxnRow(t: Txn, l: Lookup, onClick: () -> Unit, onTogglePaid: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (t.type) {
            TxType.TRANSFER -> IconBubble(Icons.Filled.SwapHoriz, TransferBlue)
            TxType.INVOICE_PAYMENT -> IconBubble(Icons.Filled.CreditCard, TransferBlue)
            else -> CategoryBubble(t.categoryId?.let { l.cats[it] })
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.description.ifBlank { "Sem descrição" }, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            Text(
                txnSubtitle(t, l), maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            val sign = when (t.type) { TxType.EXPENSE, TxType.INVOICE_PAYMENT -> "- "; TxType.INCOME -> "+ "; else -> "" }
            MoneyText(t.amount, color = txnColor(t), weight = FontWeight.SemiBold, prefix = sign)
            if (t.cardId == null && t.type != TxType.INVOICE_PAYMENT) {
                Row(
                    Modifier.clickable(enabled = onTogglePaid != null) { onTogglePaid?.invoke() }.padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (t.paid) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked, null,
                        Modifier.size(14.dp),
                        tint = if (t.paid) IncomeGreen else MaterialTheme.colorScheme.outline
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        if (t.paid) (if (t.type == TxType.INCOME) "recebido" else "pago")
                        else (if (t.type == TxType.INCOME) "a receber" else "a pagar"),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (t.paid) IncomeGreen else MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

@Composable
fun AddFab(nav: Nav) {
    var open by remember { mutableStateOf(false) }
    Box {
        FloatingActionButton(onClick = { open = true }, containerColor = MaterialTheme.colorScheme.primary) {
            Icon(Icons.Filled.Add, "Novo lançamento")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Despesa") },
                leadingIcon = { Icon(Icons.Filled.ArrowDownward, null, tint = ExpenseRed) },
                onClick = { open = false; nav.openTxn("EXPENSE") })
            DropdownMenuItem(
                text = { Text("Receita") },
                leadingIcon = { Icon(Icons.Filled.ArrowUpward, null, tint = IncomeGreen) },
                onClick = { open = false; nav.openTxn("INCOME") })
            DropdownMenuItem(
                text = { Text("Despesa no cartão") },
                leadingIcon = { Icon(Icons.Filled.CreditCard, null, tint = ExpenseRed) },
                onClick = { open = false; nav.openTxn("CARD") })
            DropdownMenuItem(
                text = { Text("Transferência") },
                leadingIcon = { Icon(Icons.Filled.SwapHoriz, null, tint = TransferBlue) },
                onClick = { open = false; nav.openTxn("TRANSFER") })
        }
    }
}
