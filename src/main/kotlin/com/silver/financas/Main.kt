package com.silver.financas

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import com.silver.financas.data.Calc
import com.silver.financas.data.Store
import com.silver.financas.ui.App
import com.silver.financas.ui.FinanceViewModel
import com.silver.financas.ui.Nav
import com.silver.financas.util.money
import kotlinx.coroutines.delay
import java.awt.Dimension
import java.time.YearMonth

fun main() {
    val store = Store()
    val vm = FinanceViewModel(store)
    val nav = Nav()

    application {
        val icon = painterResource("icon.png")
        val tray = rememberTrayState()
        val windowState = rememberWindowState(size = DpSize(1280.dp, 820.dp), position = WindowPosition.PlatformDefault)

        Tray(icon = icon, state = tray, tooltip = "SilverFinanças")

        // alertas de contas a pagar / limites ao abrir
        LaunchedEffect(Unit) {
            delay(2500)
            if (!vm.prefs.alertsEnabled) return@LaunchedEffect
            val txns = store.dao.txnsList()
            val lines = Calc.bills(store.dao.cardsList(), txns, days = 3).map { "${it.title}: ${it.detail} (${money(it.amount)})" }.toMutableList()
            if (vm.prefs.budgetAlerts) {
                val cats = store.dao.categoriesList().associateBy { it.id }
                for (b in store.dao.budgetsList()) {
                    val spent = Calc.spentInCategory(txns, cats, b.categoryId, YearMonth.now())
                    if (b.limitCents > 0 && spent * 100 / b.limitCents >= 80)
                        lines += "${cats[b.categoryId]?.name ?: "Categoria"}: ${spent * 100 / b.limitCents}% do limite"
                }
            }
            if (lines.isNotEmpty()) {
                tray.sendNotification(
                    Notification(
                        if (lines.size == 1) "SilverFinanças" else "SilverFinanças — ${lines.size} alertas",
                        lines.take(4).joinToString("\n"),
                        Notification.Type.Warning
                    )
                )
            }
        }

        Window(
            onCloseRequest = { store.flush(); exitApplication() },
            title = "SilverFinanças",
            icon = icon,
            state = windowState,
            onKeyEvent = {
                if (it.type == KeyEventType.KeyUp && it.key == Key.Escape) nav.popBackStack() else false
            }
        ) {
            remember { window.minimumSize = Dimension(900, 600); 0 }
            App(vm, nav)
        }
    }
}
