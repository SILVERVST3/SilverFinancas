package com.silver.financas.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.silver.financas.ui.FinanceViewModel
import com.silver.financas.ui.Nav
import com.silver.financas.ui.components.ConfirmDialog
import com.silver.financas.ui.components.DropdownField
import com.silver.financas.ui.components.SectionCard
import com.silver.financas.util.openExternally
import com.silver.financas.util.pickFile
import com.silver.financas.util.saveFile
import com.silver.financas.util.sha256
import java.io.File
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: FinanceViewModel, nav: Nav) {
    val prefs = vm.prefs
    var hideOnStart by remember { mutableStateOf(prefs.hideOnStart) }
    var alerts by remember { mutableStateOf(prefs.alertsEnabled) }
    var budgetAlerts by remember { mutableStateOf(prefs.budgetAlerts) }
    var hasPin by remember { mutableStateOf(prefs.pinHash != null) }
    var pin by remember { mutableStateOf("") }
    var pendingRestore by remember { mutableStateOf<File?>(null) }

    Scaffold(topBar = { TopAppBar(title = { Text("Configurações") }, navigationIcon = { BackButton(nav) }) }) { p ->
        Column(Modifier.padding(p).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            SectionCard("Aparência") {
                DropdownField("Tema", listOf(0, 1, 2), vm.themeMode, { listOf("Padrão do sistema", "Claro", "Escuro")[it] }, { vm.setTheme(it ?: 0) })
                SwitchRow("Abrir com valores ocultos", hideOnStart) { hideOnStart = it; prefs.hideOnStart = it }
            }
            SectionCard("Segurança") {
                if (hasPin) {
                    Text("O programa pede o PIN ao abrir.", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { prefs.pinHash = null; hasPin = false; vm.message = "PIN removido" }) { Text("Remover PIN") }
                } else {
                    Text("Defina um PIN (4 a 8 números) para proteger o programa ao abrir.", style = MaterialTheme.typography.bodySmall)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            pin, { pin = it.filter { c -> c.isDigit() }.take(8) }, label = { Text("Novo PIN") }, singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            modifier = Modifier.width(200.dp)
                        )
                        Button(onClick = {
                            if (pin.length >= 4) { prefs.pinHash = sha256(pin); hasPin = true; pin = ""; vm.message = "PIN definido" }
                            else vm.message = "O PIN precisa ter pelo menos 4 números"
                        }) { Text("Definir PIN") }
                    }
                }
            }
            SectionCard("Alertas") {
                SwitchRow("Avisar contas a pagar e faturas ao abrir o programa", alerts) { alerts = it; prefs.alertsEnabled = it }
                SwitchRow("Avisar quando passar de 80% de um limite", budgetAlerts) { budgetAlerts = it; prefs.budgetAlerts = it }
            }
            SectionCard("Backup e celular") {
                Text(
                    "O backup é o mesmo do app do celular: faça backup no celular e restaure aqui (ou o contrário) para levar seus dados de um para o outro.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = { saveFile("Salvar backup", "silverfinancas-backup-${LocalDate.now()}.json")?.let { vm.exportBackup(it) } }, Modifier.fillMaxWidth()) {
                    Text("Fazer backup (JSON)")
                }
                OutlinedButton(onClick = { pendingRestore = pickFile("Escolher backup", listOf("json")) }, Modifier.fillMaxWidth()) {
                    Text("Restaurar backup")
                }
                OutlinedButton(onClick = { saveFile("Exportar planilha", "silverfinancas-lancamentos-${LocalDate.now()}.csv")?.let { vm.exportCsv(it) } }, Modifier.fillMaxWidth()) {
                    Text("Exportar lançamentos (CSV / Excel)")
                }
                OutlinedButton(onClick = { openExternally(vm.store.dir) }, Modifier.fillMaxWidth()) {
                    Text("Abrir pasta dos dados")
                }
                Text(vm.store.file.absolutePath, style = MaterialTheme.typography.labelSmall)
            }
            SectionCard("Sobre") {
                Text("SilverFinanças 1.0 para Windows — controle financeiro pessoal offline.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    pendingRestore?.let { f ->
        ConfirmDialog(
            "Restaurar backup",
            "Todos os dados atuais serão substituídos pelos do arquivo ${f.name}. Continuar?",
            confirm = "Restaurar",
            onConfirm = { vm.restoreBackup(f) },
            onDismiss = { pendingRestore = null }
        )
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, Modifier.weight(1f))
        Switch(checked, onChange)
    }
}
