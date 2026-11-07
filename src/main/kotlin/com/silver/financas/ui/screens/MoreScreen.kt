package com.silver.financas.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.silver.financas.ui.Nav
import com.silver.financas.ui.FinanceViewModel
import com.silver.financas.ui.components.IconBubble

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(vm: FinanceViewModel, nav: Nav) {
    Scaffold(topBar = { TopAppBar(title = { Text("Mais") }) }) { p ->
        Column(Modifier.padding(top = p.calculateTopPadding()).verticalScroll(rememberScrollState())) {
            val primary = MaterialTheme.colorScheme.primary
            MoreItem(Icons.Filled.AccountBalance, "Contas", "Corrente, poupança, digital, PJ, carteira", primary) { nav.navigate("accounts") }
            MoreItem(Icons.Filled.CreditCard, "Cartões de crédito", "Limites e faturas", primary) { nav.navigate("cards") }
            MoreItem(Icons.Filled.Category, "Categorias", "Crie e organize suas categorias", primary) { nav.navigate("categories") }
            MoreItem(Icons.Filled.Savings, "Limites de gastos", "Quanto gastar em cada categoria", primary) { nav.navigate("budgets") }
            MoreItem(Icons.Filled.FileDownload, "Importar extrato", "OFX ou CSV do seu banco, com categorização automática", primary) { nav.navigate("import") }
            MoreItem(Icons.Filled.Settings, "Configurações", "Tema, bloqueio, alertas, backup", primary) { nav.navigate("settings") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoreItem(icon: ImageVector, title: String, subtitle: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { IconBubble(icon, color) },
        modifier = Modifier.clickable(onClick = onClick)
    )
}
