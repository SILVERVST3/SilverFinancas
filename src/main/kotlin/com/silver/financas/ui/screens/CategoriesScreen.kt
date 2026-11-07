package com.silver.financas.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.silver.financas.ui.Nav
import com.silver.financas.data.*
import com.silver.financas.ui.FinanceViewModel
import com.silver.financas.ui.components.*
import com.silver.financas.ui.theme.ExpenseRed
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(vm: FinanceViewModel, nav: Nav) {
    val cats by vm.categories.collectAsState()
    var kind by remember { mutableStateOf(CategoryKind.EXPENSE) }
    var editing by remember { mutableStateOf<Category?>(null) }
    var creating by remember { mutableStateOf(false) }
    val list = remember(cats, kind) {
        cats.filter { it.kind == kind && it.parentId == null }.flatMap { p -> listOf(p) + cats.filter { it.parentId == p.id } }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Categorias") }, navigationIcon = { BackButton(nav) }) },
        floatingActionButton = { FloatingActionButton(onClick = { creating = true }) { Icon(Icons.Filled.Add, "Nova categoria") } }
    ) { p ->
        Column(Modifier.padding(top = p.calculateTopPadding())) {
            TabRow(selectedTabIndex = if (kind == CategoryKind.EXPENSE) 0 else 1) {
                Tab(kind == CategoryKind.EXPENSE, { kind = CategoryKind.EXPENSE }, text = { Text("Despesas") })
                Tab(kind == CategoryKind.INCOME, { kind = CategoryKind.INCOME }, text = { Text("Receitas") })
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                items(list, key = { it.id }) { c ->
                    ListItem(
                        headlineContent = { Text(if (c.parentId != null) "› ${c.name}" else c.name) },
                        leadingContent = { CategoryBubble(c) },
                        modifier = Modifier.clickable { editing = c }.padding(start = if (c.parentId != null) 28.dp else 0.dp)
                    )
                }
            }
        }
    }
    if (creating || editing != null) {
        CategoryDialog(vm, editing, kind, cats) { creating = false; editing = null }
    }
}

@Composable
private fun CategoryDialog(vm: FinanceViewModel, cat: Category?, kind: CategoryKind, all: List<Category>, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(cat?.name ?: "") }
    var color by remember { mutableIntStateOf(cat?.color ?: palette[(all.size) % palette.size]) }
    var icon by remember { mutableStateOf(cat?.icon ?: "other") }
    var parentId by remember { mutableStateOf(cat?.parentId) }
    var confirmDelete by remember { mutableStateOf(false) }
    val k = cat?.kind ?: kind
    val parents = all.filter { it.kind == k && it.parentId == null && it.id != cat?.id }
    val hasChildren = cat != null && all.any { it.parentId == cat.id }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (cat == null) "Nova categoria" else "Editar categoria") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nome") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (!hasChildren) DropdownField("Subcategoria de", parents, parents.firstOrNull { it.id == parentId }, { it.name }, { parentId = it?.id }, noneLabel = "Nenhuma (categoria principal)")
                Text("Cor", style = MaterialTheme.typography.labelLarge)
                ColorPicker(color) { color = it }
                Text("Ícone", style = MaterialTheme.typography.labelLarge)
                IconPicker(icon, Color(color)) { icon = it }
                if (cat != null) TextButton(onClick = { confirmDelete = true }) { Text("Excluir categoria", color = ExpenseRed) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) return@TextButton
                vm.saveCategory((cat ?: Category(name = name, kind = k, color = color)).copy(name = name.trim(), color = color, icon = icon, parentId = parentId))
                onDismiss()
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
    if (confirmDelete && cat != null) {
        ConfirmDialog("Excluir categoria", "Os lançamentos desta categoria ficarão sem categoria.",
            onConfirm = { vm.deleteCategory(cat); onDismiss() }, onDismiss = { confirmDelete = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsScreen(vm: FinanceViewModel, nav: Nav) {
    val cats by vm.categories.collectAsState()
    val budgets by vm.budgets.collectAsState()
    val txns by vm.txns.collectAsState()
    val month by vm.month.collectAsState()
    val catMap = remember(cats) { cats.associateBy { it.id } }
    var editing by remember { mutableStateOf<Budget?>(null) }
    var creating by remember { mutableStateOf(false) }

    val rows = remember(budgets, cats, txns, month) {
        budgets.mapNotNull { b -> catMap[b.categoryId]?.let { Triple(b, it, Calc.spentInCategory(txns, catMap, it.id, month)) } }
    }
    val totalLimit = rows.sumOf { it.first.limitCents }
    val totalSpent = rows.sumOf { it.third }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Limites de gastos") }, navigationIcon = { BackButton(nav) }) },
        floatingActionButton = { FloatingActionButton(onClick = { creating = true }) { Icon(Icons.Filled.Add, "Novo limite") } }
    ) { p ->
        LazyColumn(contentPadding = PaddingValues(top = p.calculateTopPadding(), bottom = 96.dp)) {
            item { MonthSelector(month, { vm.month.value = it }, Modifier.padding(horizontal = 8.dp)) }
            item {
                SectionCard {
                    Row {
                        LabelValue("Gasto nas categorias", totalSpent, modifier = Modifier.weight(1f))
                        LabelValue("Limite total", totalLimit, modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                    ProgressLine(if (totalLimit > 0) totalSpent.toFloat() / totalLimit else 0f, MaterialTheme.colorScheme.primary)
                }
            }
            if (rows.isEmpty()) item { EmptyState("Defina quanto quer gastar por mês em cada categoria. Toque em +.") }
            items(rows, key = { it.first.id }) { (b, c, spent) ->
                Box(Modifier.clickable { editing = b }.padding(horizontal = 16.dp)) { BudgetRow(c, spent, b.limitCents) }
            }
        }
    }
    if (creating || editing != null) {
        BudgetDialog(vm, editing, cats.filter { it.kind == CategoryKind.EXPENSE }, budgets) { creating = false; editing = null }
    }
}

@Composable
private fun BudgetDialog(vm: FinanceViewModel, budget: Budget?, cats: List<Category>, existing: List<Budget>, onDismiss: () -> Unit) {
    val options = cats.filter { c -> budget?.categoryId == c.id || existing.none { it.categoryId == c.id } }
        .let { l -> l.filter { it.parentId == null }.flatMap { p -> listOf(p) + l.filter { it.parentId == p.id } } }
    var catId by remember { mutableStateOf(budget?.categoryId ?: options.firstOrNull()?.id) }
    var limit by remember { mutableLongStateOf(budget?.limitCents ?: 0L) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (budget == null) "Novo limite mensal" else "Editar limite") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DropdownField("Categoria", options, options.firstOrNull { it.id == catId },
                    { if (it.parentId != null) "   › ${it.name}" else it.name }, { catId = it?.id },
                    leading = { CategoryBubble(it, 28) })
                MoneyField(limit, { limit = it }, "Limite por mês")
                if (budget != null) TextButton(onClick = { vm.deleteBudget(budget); onDismiss() }) { Text("Excluir limite", color = ExpenseRed) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val c = catId ?: return@TextButton
                vm.saveBudget((budget ?: Budget(categoryId = c, limitCents = limit)).copy(categoryId = c, limitCents = limit)); onDismiss()
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
