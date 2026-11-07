package com.silver.financas.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.silver.financas.data.Category
import com.silver.financas.ui.theme.LocalHide
import com.silver.financas.util.br
import com.silver.financas.util.label
import com.silver.financas.util.money
import com.silver.financas.util.moneyHidden
import java.time.LocalDate
import java.time.YearMonth

// ------------------------------------------------------------------ ícones
val categoryIcons: List<Pair<String, ImageVector>> = listOf(
    "food" to Icons.Filled.Restaurant,
    "market" to Icons.Filled.ShoppingCart,
    "home" to Icons.Filled.Home,
    "bills" to Icons.Filled.Receipt,
    "energy" to Icons.Filled.Bolt,
    "internet" to Icons.Filled.Wifi,
    "phone" to Icons.Filled.PhoneAndroid,
    "car" to Icons.Filled.DirectionsCar,
    "fuel" to Icons.Filled.LocalGasStation,
    "bus" to Icons.Filled.DirectionsBus,
    "health" to Icons.Filled.LocalHospital,
    "pharmacy" to Icons.Filled.LocalPharmacy,
    "education" to Icons.Filled.School,
    "leisure" to Icons.Filled.SportsEsports,
    "sports" to Icons.Filled.FitnessCenter,
    "subscriptions" to Icons.Filled.Subscriptions,
    "clothes" to Icons.Filled.Checkroom,
    "beauty" to Icons.Filled.Face,
    "travel" to Icons.Filled.Flight,
    "pets" to Icons.Filled.Pets,
    "kids" to Icons.Filled.ChildCare,
    "gift" to Icons.Filled.CardGiftcard,
    "church" to Icons.Filled.Favorite,
    "tax" to Icons.Filled.AccountBalance,
    "salary" to Icons.Filled.Payments,
    "work" to Icons.Filled.Work,
    "invest" to Icons.Filled.Savings,
    "money" to Icons.Filled.AttachMoney,
    "other" to Icons.Filled.Category
)

fun iconFor(key: String?): ImageVector = categoryIcons.firstOrNull { it.first == key }?.second ?: Icons.Filled.Category

val palette: List<Int> = listOf(
    0xFF0F7B6C, 0xFF43A047, 0xFF9CCC65, 0xFF26A69A, 0xFF29B6F6, 0xFF42A5F5, 0xFF3D5AFE, 0xFF5C6BC0,
    0xFF7E57C2, 0xFFAB47BC, 0xFFEC407A, 0xFFE57373, 0xFFE0524D, 0xFFFF8A65, 0xFFFFB300, 0xFFFDD835,
    0xFF8D6E63, 0xFFA1887F, 0xFF78909C, 0xFF90A4AE, 0xFF8A05BE, 0xFFFF7A00, 0xFFCC092F, 0xFF000000
).map { it.toInt() }

@Composable
fun IconBubble(icon: ImageVector, color: Color, size: Int = 40) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(color.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = color, modifier = Modifier.size((size * 0.55).dp)) }
}

@Composable
fun CategoryBubble(c: Category?, size: Int = 40) =
    IconBubble(iconFor(c?.icon), c?.let { Color(it.color) } ?: MaterialTheme.colorScheme.outline, size)

// ------------------------------------------------------------------ valores
@Composable
fun MoneyText(
    cents: Long,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: androidx.compose.ui.text.TextStyle = LocalTextStyle.current,
    weight: FontWeight? = null,
    prefix: String = ""
) {
    Text(prefix + moneyHidden(cents, LocalHide.current), modifier, color = color, style = style, fontWeight = weight, maxLines = 1)
}

/** Campo de valor estilo "caixa eletrônico": digita os números e os centavos se ajustam. */
@Composable
fun MoneyField(cents: Long, onChange: (Long) -> Unit, label: String, modifier: Modifier = Modifier, big: Boolean = false) {
    val text = money(cents)
    OutlinedTextField(
        value = TextFieldValue(text, TextRange(text.length)),
        onValueChange = { v ->
            val digits = v.text.filter { it.isDigit() }.take(13)
            onChange(digits.toLongOrNull() ?: 0L)
        },
        label = { Text(label) },
        singleLine = true,
        textStyle = if (big) MaterialTheme.typography.headlineSmall else LocalTextStyle.current,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier.fillMaxWidth()
    )
}

// ------------------------------------------------------------------ seletores
@Composable
fun <T> DropdownField(
    label: String,
    options: List<T>,
    selected: T?,
    optionLabel: (T) -> String,
    onSelect: (T?) -> Unit,
    modifier: Modifier = Modifier,
    noneLabel: String? = null,
    leading: (@Composable (T) -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            value = selected?.let(optionLabel) ?: (noneLabel ?: ""),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) },
            modifier = Modifier.fillMaxWidth()
        )
        Box(Modifier.matchParentSize().padding(top = 8.dp).clip(RoundedCornerShape(4.dp)).clickable { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 420.dp)) {
            if (noneLabel != null) {
                DropdownMenuItem(text = { Text(noneLabel) }, onClick = { onSelect(null); expanded = false })
            }
            options.forEach { o ->
                DropdownMenuItem(
                    text = { Text(optionLabel(o)) },
                    leadingIcon = if (leading != null) { { leading(o) } } else null,
                    onClick = { onSelect(o); expanded = false }
                )
            }
        }
    }
}

@Composable
fun DateField(label: String, date: LocalDate, onChange: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            value = date.br(), onValueChange = {}, readOnly = true, label = { Text(label) },
            trailingIcon = { Icon(Icons.Filled.DateRange, null) },
            modifier = Modifier.fillMaxWidth()
        )
        Box(Modifier.matchParentSize().padding(top = 8.dp).clickable { open = true })
    }
    if (open) CalendarDialog(date, { onChange(it); open = false }, { open = false })
}

/** Calendário simples (funciona em qualquer plataforma). */
@Composable
fun CalendarDialog(initial: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    var ym by remember { mutableStateOf(YearMonth.from(initial)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { MonthSelector(ym, { ym = it }) },
        text = {
            Column(Modifier.width(320.dp)) {
                Row {
                    listOf("D", "S", "T", "Q", "Q", "S", "S").forEach {
                        Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium)
                    }
                }
                val offset = ym.atDay(1).dayOfWeek.value % 7   // domingo = 0
                val len = ym.lengthOfMonth()
                val rows = (offset + len + 6) / 7
                for (r in 0 until rows) {
                    Row {
                        for (c in 0..6) {
                            val day = r * 7 + c - offset + 1
                            Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                                if (day in 1..len) {
                                    val d = ym.atDay(day)
                                    val sel = d == initial
                                    Box(
                                        Modifier.fillMaxSize().clip(CircleShape)
                                            .background(if (sel) MaterialTheme.colorScheme.primary else Color.Transparent)
                                            .clickable { onPick(d) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            day.toString(),
                                            color = if (sel) MaterialTheme.colorScheme.onPrimary
                                            else if (d == LocalDate.now()) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface,
                                            fontWeight = if (d == LocalDate.now()) FontWeight.Bold else null
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(LocalDate.now()) }) { Text("Hoje") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickDateChips(date: LocalDate, onChange: (LocalDate) -> Unit) {
    val today = LocalDate.now()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = date == today, onClick = { onChange(today) }, label = { Text("Hoje") })
        FilterChip(selected = date == today.minusDays(1), onClick = { onChange(today.minusDays(1)) }, label = { Text("Ontem") })
    }
}

@Composable
fun MonthSelector(month: YearMonth, onChange: (YearMonth) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        IconButton(onClick = { onChange(month.minusMonths(1)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Mês anterior") }
        Text(
            month.label(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onChange(YearMonth.now()) }.padding(8.dp)
        )
        IconButton(onClick = { onChange(month.plusMonths(1)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Próximo mês") }
    }
}

@Composable
fun ColorPicker(selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        palette.forEach { c ->
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(Color(c))
                    .border(if (c == selected) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                    .clickable { onSelect(c) }
            )
        }
    }
}

@Composable
fun IconPicker(selected: String, color: Color, onSelect: (String) -> Unit) {
    LazyVerticalGrid(columns = GridCells.Adaptive(48.dp), modifier = Modifier.heightIn(max = 220.dp)) {
        items(categoryIcons) { (key, icon) ->
            Box(
                Modifier.padding(4.dp).size(44.dp).clip(CircleShape)
                    .background(if (key == selected) color.copy(alpha = 0.3f) else Color.Transparent)
                    .clickable { onSelect(key) },
                contentAlignment = Alignment.Center
            ) { Icon(icon, key, tint = if (key == selected) color else MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

// ------------------------------------------------------------------ layout
@Composable
fun SectionCard(title: String? = null, action: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            if (title != null) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    action?.invoke()
                }
                Spacer(Modifier.height(8.dp))
            }
            content()
        }
    }
}

@Composable
fun EmptyState(text: String) {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun LabelValue(label: String, cents: Long, color: Color = Color.Unspecified, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        MoneyText(cents, color = color, style = MaterialTheme.typography.titleMedium, weight = FontWeight.SemiBold)
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String = "Excluir", onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
fun ProgressLine(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(color))
    }
}
