package com.silver.financas.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.silver.financas.ui.Nav
import com.silver.financas.data.CategoryKind
import com.silver.financas.data.ImportRow
import com.silver.financas.ui.FinanceViewModel
import com.silver.financas.ui.components.*
import com.silver.financas.ui.theme.ExpenseRed
import com.silver.financas.ui.theme.IncomeGreen
import com.silver.financas.util.StatementParser
import com.silver.financas.util.br
import com.silver.financas.util.pickFile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(vm: FinanceViewModel, nav: Nav) {
    val accounts by vm.accounts.collectAsState()
    val cards by vm.cards.collectAsState()
    val cats by vm.categories.collectAsState()
    val txns by vm.txns.collectAsState()

    var toCard by remember { mutableStateOf(false) }
    var accountId by remember { mutableStateOf<Long?>(null) }
    var cardId by remember { mutableStateOf<Long?>(null) }
    var positiveIsExpense by remember { mutableStateOf(false) }
    var rows by remember { mutableStateOf<List<ImportRow>>(emptyList()) }
    var version by remember { mutableIntStateOf(0) }   // força recomposição ao editar linhas
    var fileName by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(accounts.size, cards.size) {
        if (accountId == null) accountId = accounts.firstOrNull { !it.archived }?.id
        if (cardId == null) cardId = cards.firstOrNull { !it.archived }?.id
    }

    fun recategorize() {
        rows.forEach { r -> if (r.categoryId == null) r.categoryId = vm.suggestCategory(r.description, income = (if (positiveIsExpense) -r.amount else r.amount) > 0) }
        vm.repo.markDuplicates(rows, txns, if (toCard) null else accountId, if (toCard) cardId else null)
        version++
    }

    fun pick() {
        val f = pickFile("Escolher extrato (OFX ou CSV)", listOf("ofx", "csv", "txt")) ?: return
        try {
            rows = StatementParser.parse(f.readBytes())
            fileName = f.name
            if (rows.isEmpty()) vm.message = "Nenhum lançamento encontrado no arquivo"
            recategorize()
        } catch (e: Exception) {
            vm.message = "Não consegui ler o arquivo: ${e.message}"
        }
    }

    val expenseCats = cats.filter { it.kind == CategoryKind.EXPENSE }
    val incomeCats = cats.filter { it.kind == CategoryKind.INCOME }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Importar extrato") }, navigationIcon = { BackButton(nav) }) },
        bottomBar = {
            if (rows.isNotEmpty()) Surface(tonalElevation = 3.dp) {
                key(version) {
                    Button(
                        onClick = {
                            val card = if (toCard) cards.firstOrNull { it.id == cardId } else null
                            vm.importRows(rows, if (toCard) null else accountId, card, positiveIsExpense) { nav.popBackStack() }
                        },
                        modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp)
                    ) { Text("Importar ${rows.count { it.selected }} lançamentos") }
                }
            }
        }
    ) { p ->
        LazyColumn(contentPadding = PaddingValues(top = p.calculateTopPadding(), bottom = p.calculateBottomPadding() + 16.dp)) {
            item {
                SectionCard {
                    Text(
                        "Baixe o extrato no app ou site do seu banco em formato OFX (recomendado) ou CSV e escolha o arquivo aqui. " +
                            "As categorias são sugeridas automaticamente e o app aprende com as suas escolhas. Lançamentos repetidos são desmarcados.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(!toCard, { toCard = false; positiveIsExpense = false; recategorize() }, { Text("Extrato de conta") })
                        FilterChip(toCard, { toCard = true; positiveIsExpense = true; recategorize() }, { Text("Fatura de cartão") })
                    }
                    Spacer(Modifier.height(8.dp))
                    if (toCard) DropdownField("Cartão", cards.filter { !it.archived }, cards.firstOrNull { it.id == cardId }, { it.name }, { cardId = it?.id; recategorize() })
                    else DropdownField("Conta", accounts.filter { !it.archived }, accounts.firstOrNull { it.id == accountId }, { it.name }, { accountId = it?.id; recategorize() })
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Valores positivos são despesas (comum em faturas)", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        Switch(positiveIsExpense, { positiveIsExpense = it; rows.forEach { r -> r.categoryId = null }; recategorize() })
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { pick() }) {
                        Icon(Icons.Filled.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text("Escolher arquivo OFX/CSV")
                    }
                    fileName?.let { Text("Arquivo: $it · ${rows.size} lançamentos", style = MaterialTheme.typography.labelSmall) }
                }
            }
            itemsIndexed(rows, key = { i, _ -> "$i-$version" }) { _, r ->
                val signed = if (positiveIsExpense) -r.amount else r.amount
                val income = signed > 0
                val options = if (income) incomeCats else expenseCats
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(r.selected, { r.selected = it; version++ })
                    Column(Modifier.weight(1f)) {
                        Text(r.description, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                        Text(r.date.br() + if (r.duplicate) " · já importado" else "", style = MaterialTheme.typography.bodySmall,
                            color = if (r.duplicate) ExpenseRed else MaterialTheme.colorScheme.onSurfaceVariant)
                        DropdownField("Categoria", options, options.firstOrNull { it.id == r.categoryId }, { it.name },
                            { r.categoryId = it?.id; version++ }, noneLabel = "Sem categoria")
                    }
                    Spacer(Modifier.width(8.dp))
                    MoneyText(kotlin.math.abs(signed), color = if (income) IncomeGreen else ExpenseRed, weight = FontWeight.SemiBold, prefix = if (income) "+ " else "- ")
                }
            }
        }
    }
}
