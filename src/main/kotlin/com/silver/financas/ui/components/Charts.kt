package com.silver.financas.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.max

/** Gráfico de rosca. */
@Composable
fun DonutChart(values: List<Pair<Color, Long>>, modifier: Modifier = Modifier, center: @Composable () -> Unit = {}) {
    val total = values.sumOf { it.second }.coerceAtLeast(1L)
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.16f
            val d = size.minDimension - stroke
            val topLeft = Offset((size.width - d) / 2, (size.height - d) / 2)
            drawArc(track, 0f, 360f, false, topLeft, Size(d, d), style = Stroke(stroke))
            var start = -90f
            for ((c, v) in values) {
                val sweep = 360f * v / total
                if (sweep <= 0f) continue
                drawArc(c, start, (sweep - 1.2f).coerceAtLeast(0.5f), false, topLeft, Size(d, d), style = Stroke(stroke, cap = StrokeCap.Butt))
                start += sweep
            }
        }
        center()
    }
}

/** Barras agrupadas (ex.: receitas x despesas por mês). */
@Composable
fun GroupedBarChart(labels: List<String>, series: List<Pair<Color, List<Long>>>, modifier: Modifier = Modifier) {
    val maxV = series.flatMap { it.second }.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    val grid = MaterialTheme.colorScheme.outlineVariant
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(180.dp)) {
            val n = labels.size.coerceAtLeast(1)
            val groupW = size.width / n
            val barW = (groupW * 0.7f) / series.size.coerceAtLeast(1)
            for (i in 0..4) {
                val y = size.height * i / 4f
                drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }
            for (i in 0 until n) {
                val x0 = i * groupW + groupW * 0.15f
                series.forEachIndexed { s, (color, vals) ->
                    val v = vals.getOrElse(i) { 0L }.coerceAtLeast(0)
                    val h = size.height * v / maxV
                    drawRoundRect(
                        color, Offset(x0 + s * barW, size.height - h), Size(barW * 0.9f, h),
                        cornerRadius = CornerRadius(6f, 6f)
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            labels.forEach {
                Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** Linha (ex.: evolução do saldo). */
@Composable
fun LineChart(labels: List<String>, values: List<Long>, color: Color, modifier: Modifier = Modifier) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    val zero = MaterialTheme.colorScheme.outline
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(180.dp).padding(horizontal = 8.dp)) {
            if (values.isEmpty()) return@Canvas
            val maxV = max(values.maxOrNull() ?: 0L, 0L)
            val minV = minOf(values.minOrNull() ?: 0L, 0L)
            val range = (maxV - minV).coerceAtLeast(1L)
            fun y(v: Long) = size.height - size.height * (v - minV) / range
            for (i in 0..4) {
                val yy = size.height * i / 4f
                drawLine(grid, Offset(0f, yy), Offset(size.width, yy), strokeWidth = 1f)
            }
            drawLine(zero, Offset(0f, y(0)), Offset(size.width, y(0)), strokeWidth = 2f)
            val step = if (values.size > 1) size.width / (values.size - 1) else 0f
            val path = Path()
            values.forEachIndexed { i, v ->
                val p = Offset(i * step, y(v))
                if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            drawPath(path, color, style = Stroke(width = 6f, cap = StrokeCap.Round))
            values.forEachIndexed { i, v -> drawCircle(color, 9f, Offset(i * step, y(v))) }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            labels.forEach {
                Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
