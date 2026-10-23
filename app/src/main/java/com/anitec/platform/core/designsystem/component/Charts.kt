package com.anitec.platform.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.anitec.platform.core.designsystem.AniTecChartPalette

/** One labelled value of a chart. */
data class ChartPoint(val label: String, val value: Int, val color: Color? = null)

/** Chart text for TalkBack, which cannot read a canvas: "Healthy 5, Observation 2". */
fun List<ChartPoint>.spokenSummary(): String = joinToString(", ") { "${it.label} ${it.value}" }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChartLegend(points: List<ChartPoint>, colors: List<Color>, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        points.forEachIndexed { index, point ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(colors[index], CircleShape))
                Spacer(Modifier.width(6.dp))
                Text("${point.label} (${point.value})", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun colorsFor(points: List<ChartPoint>): List<Color> =
    points.mapIndexed { index, point -> point.color ?: AniTecChartPalette[index % AniTecChartPalette.size] }

/** Doughnut chart with a legend underneath; draws nothing but the legend when every value is zero. */
@Composable
fun DonutChart(points: List<ChartPoint>, modifier: Modifier = Modifier, centerText: String? = null) {
    val colors = colorsFor(points)
    val total = points.sumOf { it.value }
    val track = MaterialTheme.colorScheme.outlineVariant
    val surface = MaterialTheme.colorScheme.surface
    Column(modifier = modifier.fillMaxWidth().semantics { contentDescription = points.spokenSummary() }, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(180.dp)) {
                val stroke = size.minDimension * 0.19f
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                if (total == 0) {
                    drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                } else {
                    var start = -90f
                    points.forEachIndexed { index, point ->
                        if (point.value > 0) {
                            val sweep = 360f * point.value / total
                            // The thin surface-colored gap between slices mirrors the web's 3px border.
                            drawArc(colors[index], start, sweep, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                            drawArc(surface, start, 1.2f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                            start += sweep
                        }
                    }
                }
            }
            if (centerText != null) {
                Text(centerText, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.tertiary)
            }
        }
        Spacer(Modifier.height(12.dp))
        ChartLegend(points, colors)
    }
}

/**
 * Horizontal bars, one row per point: label, bar in its own palette color, value. Rows read better than
 * columns on a phone, where six category names do not fit side by side.
 */
@Composable
fun BarChart(points: List<ChartPoint>, modifier: Modifier = Modifier) {
    val colors = colorsFor(points)
    val max = (points.maxOfOrNull { it.value } ?: 0).coerceAtLeast(1)
    Column(
        modifier = modifier.fillMaxWidth().semantics { contentDescription = points.spokenSummary() },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        points.forEachIndexed { index, point ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    point.label,
                    modifier = Modifier.width(96.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Box(Modifier.weight(1f).height(18.dp)) {
                    if (point.value > 0) {
                        Box(
                            Modifier
                                .fillMaxWidth(point.value.toFloat() / max)
                                .height(18.dp)
                                .background(colors[index], RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)),
                        )
                    }
                }
                Text(
                    point.value.toString(),
                    modifier = Modifier.width(32.dp).padding(start = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

/** Filled line chart of one series; the labelled values are listed under the plot. */
@Composable
fun LineChart(points: List<ChartPoint>, modifier: Modifier = Modifier) {
    val max = (points.maxOfOrNull { it.value } ?: 0).coerceAtLeast(1)
    val green = AniTecChartPalette[0]
    val line = MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier = modifier.fillMaxWidth().semantics { contentDescription = points.spokenSummary() },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Canvas(Modifier.fillMaxWidth().height(140.dp)) {
            val slot = size.width / points.size.coerceAtLeast(1)
            val xs = points.indices.map { slot * it + slot / 2 }
            val ys = points.map { size.height - (size.height - 16.dp.toPx()) * it.value / max - 8.dp.toPx() }
            drawLine(line, Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), strokeWidth = 2f)
            if (points.isNotEmpty()) {
                val path = Path().apply {
                    moveTo(xs.first(), ys.first())
                    for (i in 1 until xs.size) lineTo(xs[i], ys[i])
                }
                val fill = Path().apply {
                    addPath(path)
                    lineTo(xs.last(), size.height - 1f)
                    lineTo(xs.first(), size.height - 1f)
                    close()
                }
                drawPath(fill, green.copy(alpha = 0.18f))
                drawPath(path, green, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                xs.indices.forEach { drawCircle(green, 5.dp.toPx(), Offset(xs[it], ys[it])) }
            }
        }
        points.forEach { point ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(point.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(point.value.toString(), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
