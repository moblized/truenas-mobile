package app.truenasmobile.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import app.truenasmobile.api.GraphSeries
import java.text.DateFormat
import java.util.Date
import kotlin.math.abs

/** Distinct, readable series colours (blue, teal, purple, amber, red, green, pink, slate). */
val SeriesColors = listOf(
    Color(0xFF2E8BD8), Color(0xFF14A38B), Color(0xFF8B5CF6), Color(0xFFE0A100),
    Color(0xFFE5484D), Color(0xFF46A758), Color(0xFFD6409F), Color(0xFF6B7A8F),
)

/**
 * Multi-series line chart with a shared y axis. Tap or touch-and-drag to read the values at a
 * point in time; the legend then shows those values instead of the latest ones.
 */
@Composable
fun LineChart(
    times: List<Long>,
    series: List<GraphSeries>,
    format: (Float) -> String,
    modifier: Modifier = Modifier,
) {
    var touchX by remember(times, series) { mutableStateOf<Float?>(null) }
    var width by remember { mutableIntStateOf(1) }
    val grid = MaterialTheme.colorScheme.outlineVariant
    val label = MaterialTheme.colorScheme.onSurfaceVariant
    val values = series.flatMap { it.points.filterNotNull() }
    val top = (values.maxOrNull() ?: 1f).let { if (it <= 0f) 1f else it }
    val max = top * 1.08f
    val min = (values.minOrNull() ?: 0f).coerceAtMost(0f)
    val n = times.size
    val index = touchX?.let { tx -> if (n < 2) null else ((tx / width.coerceAtLeast(1)) * (n - 1)).toInt().coerceIn(0, n - 1) }

    Column(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(170.dp)
                .onSizeChanged { width = it.width }
                .pointerInput(times, series) {
                    detectTapGestures(onTap = { o -> touchX = if (touchX == null) o.x else null })
                }
                .pointerInput(times, series) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { touchX = it.x },
                        onDrag = { change, _ -> touchX = change.position.x },
                    )
                },
        ) {
            if (n < 2) return@Canvas
            val w = size.width
            val h = size.height
            fun y(v: Float) = h - (v - min) / (max - min) * h
            for (i in 0..3) {
                val gy = h * i / 3f
                drawLine(grid, Offset(0f, gy), Offset(w, gy), strokeWidth = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            }
            series.forEachIndexed { si, s ->
                val path = Path()
                var started = false
                s.points.forEachIndexed { i, v ->
                    if (v == null) {
                        started = false
                    } else {
                        val x = w * i / (n - 1)
                        if (!started) path.moveTo(x, y(v)) else path.lineTo(x, y(v))
                        started = true
                    }
                }
                drawPath(path, SeriesColors[si % SeriesColors.size], style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            if (index != null) {
                val x = w * index / (n - 1)
                drawLine(label, Offset(x, 0f), Offset(x, h), strokeWidth = 1.2f)
                series.forEachIndexed { si, s ->
                    val v = s.points.getOrNull(index) ?: return@forEachIndexed
                    drawCircle(SeriesColors[si % SeriesColors.size], radius = 4.dp.toPx(), center = Offset(x, y(v)))
                }
            }
        }
        Text(
            if (index != null) DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(times[index]))
            else "peak ${format(top)}" + if (n > 1) "  ·  ${timeRange(times.first(), times.last())}" else "",
            style = MaterialTheme.typography.labelSmall,
            color = label,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            series.forEachIndexed { si, s ->
                val text = if (index != null) {
                    s.points.getOrNull(index)?.let(format) ?: "—"
                } else {
                    val last = s.points.lastOrNull { it != null }
                    val avg = s.points.filterNotNull().let { if (it.isEmpty()) null else it.average().toFloat() }
                    listOfNotNull(last?.let(format), avg?.let { "avg ${format(it)}" }).joinToString(" · ")
                }
                LegendDot(SeriesColors[si % SeriesColors.size], s.label, text)
            }
        }
    }
}

private fun timeRange(start: Long, end: Long): String {
    val span = abs(end - start)
    val f = if (span <= 36 * 3600_000L) DateFormat.getTimeInstance(DateFormat.SHORT) else DateFormat.getDateInstance(DateFormat.SHORT)
    return "${f.format(Date(start))} – ${f.format(Date(end))}"
}
