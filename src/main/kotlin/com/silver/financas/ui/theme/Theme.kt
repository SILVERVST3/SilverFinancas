package com.silver.financas.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

val Green = Color(0xFF0F7B6C)
val IncomeGreen = Color(0xFF2E9E5B)
val ExpenseRed = Color(0xFFE0524D)
val TransferBlue = Color(0xFF3D7BD9)
val Silver = Color(0xFFB8C0C6)

private val Light = lightColorScheme(
    primary = Green,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFEDE7),
    onPrimaryContainer = Color(0xFF00201B),
    secondary = Color(0xFF5E6B73),
    secondaryContainer = Color(0xFFE3E7EA),
    background = Color(0xFFF4F6F8),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE9EDF0),
    error = ExpenseRed
)

private val Dark = darkColorScheme(
    primary = Color(0xFF5FD3BE),
    onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF005045),
    onPrimaryContainer = Color(0xFFCFEDE7),
    secondary = Silver,
    secondaryContainer = Color(0xFF3A4247),
    background = Color(0xFF111416),
    surface = Color(0xFF1A1E21),
    surfaceVariant = Color(0xFF2A3034),
    error = Color(0xFFFF8A80)
)

/** Ocultar valores (ícone de olho) */
val LocalHide = compositionLocalOf { false }

@Composable
fun SilverTheme(mode: Int, content: @Composable () -> Unit) {
    val dark = when (mode) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
