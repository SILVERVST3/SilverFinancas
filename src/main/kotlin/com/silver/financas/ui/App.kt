package com.silver.financas.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.silver.financas.ui.screens.*
import com.silver.financas.ui.theme.LocalHide
import com.silver.financas.ui.theme.SilverTheme
import com.silver.financas.util.sha256

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", "Início", Icons.Filled.Home),
    Tab("transactions", "Lançamentos", Icons.Filled.SwapVert),
    Tab("reports", "Relatórios", Icons.Filled.PieChart),
    Tab("accounts", "Contas", Icons.Filled.AccountBalance),
    Tab("cards", "Cartões", Icons.Filled.CreditCard),
    Tab("budgets", "Limites", Icons.Filled.Savings),
    Tab("more", "Mais", Icons.Filled.Menu)
)

@Composable
fun App(vm: FinanceViewModel, nav: Nav) {
    var locked by remember { mutableStateOf(vm.prefs.pinHash != null) }
    SilverTheme(vm.themeMode) {
        if (locked) PinLock(vm) { locked = false } else MainLayout(vm, nav)
    }
}

@Composable
private fun MainLayout(vm: FinanceViewModel, nav: Nav) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) {
        vm.message?.let { snackbar.showSnackbar(it); vm.message = null }
    }
    CompositionLocalProvider(LocalHide provides vm.hide) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Row(Modifier.fillMaxSize()) {
                NavigationRail(
                    header = {
                        Text("Silver\nFinanças", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 12.dp))
                    }
                ) {
                    tabs.forEach { t ->
                        NavigationRailItem(
                            selected = nav.tab == t.route,
                            onClick = { nav.selectTab(t.route) },
                            icon = { Icon(t.icon, t.label) },
                            label = { Text(t.label) }
                        )
                    }
                }
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                    Box(Modifier.widthIn(max = 1000.dp).fillMaxSize()) {
                        val r = nav.current
                        key(r) {
                            when {
                                r == "home" -> HomeScreen(vm, nav)
                                r == "transactions" -> TransactionsScreen(vm, nav)
                                r == "reports" -> ReportsScreen(vm)
                                r == "more" -> MoreScreen(vm, nav)
                                r == "accounts" -> AccountsScreen(vm, nav)
                                r == "cards" -> CardsScreen(vm, nav)
                                r == "categories" -> CategoriesScreen(vm, nav)
                                r == "budgets" -> BudgetsScreen(vm, nav)
                                r == "import" -> ImportScreen(vm, nav)
                                r == "settings" -> SettingsScreen(vm, nav)
                                r.startsWith("card/") -> CardDetailScreen(vm, nav, r.removePrefix("card/").toLongOrNull() ?: 0L)
                                r.startsWith("txn") -> {
                                    val q = queryOf(r)
                                    TransactionEditScreen(
                                        vm, nav,
                                        type = q["type"] ?: "EXPENSE",
                                        id = q["id"]?.toLongOrNull() ?: 0L,
                                        presetCard = q["card"]?.toLongOrNull() ?: 0L
                                    )
                                }
                                else -> HomeScreen(vm, nav)
                            }
                        }
                    }
                    SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
                }
            }
        }
    }
}

@Composable
private fun PinLock(vm: FinanceViewModel, onUnlock: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    fun tryUnlock() {
        if (sha256(pin) == vm.prefs.pinHash) onUnlock() else { wrong = true; pin = "" }
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.Lock, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text("SilverFinanças está bloqueado", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                pin, { pin = it.filter { c -> c.isDigit() }.take(8); wrong = false },
                label = { Text("PIN") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                keyboardActions = KeyboardActions(onDone = { tryUnlock() }),
                isError = wrong,
                modifier = Modifier.width(240.dp)
            )
            if (wrong) Text("PIN incorreto", color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(16.dp))
            Button(onClick = { tryUnlock() }) { Text("Desbloquear") }
        }
    }
}
