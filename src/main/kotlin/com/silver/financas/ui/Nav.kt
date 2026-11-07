package com.silver.financas.ui

import androidx.compose.runtime.mutableStateListOf

/** Navegação simples por "rotas" (mesmos nomes usados no app Android). */
class Nav {
    private val stack = mutableStateListOf("home")
    val current: String get() = stack.last()
    val tab: String get() = stack.first()

    fun navigate(route: String) { stack.add(route) }

    fun popBackStack(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    fun selectTab(route: String) { stack.clear(); stack.add(route) }
}

/** Abre o formulário: type = INCOME / EXPENSE / TRANSFER / CARD / SCAN ; id > 0 para editar */
fun Nav.openTxn(type: String = "EXPENSE", id: Long = 0, cardId: Long = 0) =
    navigate("txn?type=$type&id=$id&card=$cardId")

fun queryOf(route: String): Map<String, String> =
    route.substringAfter('?', "").split('&').filter { '=' in it }
        .associate { it.substringBefore('=') to it.substringAfter('=') }
