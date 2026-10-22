package app.truenasmobile.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.truenasmobile.api.GraphDef
import app.truenasmobile.api.graphData
import app.truenasmobile.api.graphDefs
import app.truenasmobile.ui.Format
import app.truenasmobile.ui.ListPadding
import app.truenasmobile.ui.ScreenScaffold
import app.truenasmobile.ui.components.EmptyState
import app.truenasmobile.ui.components.LineChart
import app.truenasmobile.ui.components.LoadedBox
import app.truenasmobile.ui.components.LoadingBox
import app.truenasmobile.ui.components.SectionCard
import app.truenasmobile.ui.components.rememberLoad
import java.util.Locale

private enum class Category(val label: String, val graphs: List<String>) {
    CPU("CPU", listOf("cpu", "cputemp", "load")),
    MEMORY("Memory", listOf("memory", "arcsize", "arcresult", "arcactualrate", "arcrate")),
    NETWORK("Network", listOf("interface")),
    DISKS("Disks", listOf("disk", "disktemp")),
    SYSTEM("System", listOf("uptime", "processes", "upscharge", "upsruntime", "upsload", "upsvoltage", "upstemperature")),
}

private val UNITS = listOf("HOUR" to "1h", "DAY" to "1d", "WEEK" to "1w", "MONTH" to "1m", "YEAR" to "1y")

@Composable
fun ReportingScreen() {
    val defs = rememberLoad("graphdefs") { it.graphDefs() }
    var category by rememberSaveable { mutableStateOf(Category.CPU) }
    var unit by rememberSaveable { mutableStateOf("HOUR") }
    var identifier by rememberSaveable { mutableStateOf<String?>(null) }

    ScreenScaffold(title = "Reporting", subtitle = "History of how your server is doing", showBack = true) {
        LoadedBox(defs) { all ->
            val graphs = category.graphs.mapNotNull { name -> all.firstOrNull { it.name == name } }
            val ids = graphs.flatMap { it.identifiers }.distinct()
            val chosenId = identifier?.takeIf { it in ids } ?: ids.firstOrNull()
            LazyColumn(contentPadding = ListPadding, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Category.entries.forEach { c ->
                            FilterChip(selected = category == c, onClick = {
                                category = c
                                identifier = null
                            }, label = { Text(c.label) })
                        }
                    }
                }
                item {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        UNITS.forEachIndexed { i, (u, l) ->
                            SegmentedButton(selected = unit == u, onClick = { unit = u }, shape = SegmentedButtonDefaults.itemShape(i, UNITS.size)) { Text(l) }
                        }
                    }
                }
                if (ids.size > 1) {
                    item {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ids.forEach { id ->
                                FilterChip(selected = chosenId == id, onClick = { identifier = id }, label = { Text(id.substringAfterLast('|').ifBlank { id }) })
                            }
                        }
                    }
                }
                if (graphs.isEmpty()) {
                    item { EmptyState(Icons.Outlined.QueryStats, "No graphs here", "This server doesn't report anything in this category.") }
                }
                items(graphs, key = { "${it.name}-$unit-$chosenId" }) { g ->
                    val id = if (g.identifiers.isEmpty()) null else chosenId?.takeIf { it in g.identifiers } ?: g.identifiers.first()
                    GraphCard(g, id, unit)
                }
            }
        }
    }
}

@Composable
private fun GraphCard(def: GraphDef, identifier: String?, unit: String) {
    val data = rememberLoad("graph", def.name, identifier, unit) { it.graphData(def.name, identifier, unit) }
    SectionCard(Modifier.fillMaxWidth()) {
        Column {
            Text(def.title.replace("{identifier}", identifier ?: ""), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (def.unit.isNotBlank()) Text(def.unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            val d = data.value
            when {
                d != null && d.times.size > 1 -> LineChart(d.times, d.series, formatterFor(def))
                data.error != null -> Text(data.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                d != null -> Text("No data for this period yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> LoadingBox()
            }
        }
    }
}

/** Picks a readable formatter from the graph's unit label. */
private fun formatterFor(def: GraphDef): (Float) -> String {
    val u = def.unit.lowercase()
    return when {
        "%" in u || "percent" in u -> { v -> "${v.toInt()}%" }
        "celsius" in u || "°c" in u || def.name.endsWith("temp") -> { v -> Format.temp(v.toDouble()) }
        "kib/s" in u || "kilobytes/s" in u -> { v -> Format.bytesPerSec(v * 1024.0) }
        "mib/s" in u -> { v -> Format.bytesPerSec(v * 1024.0 * 1024) }
        "bytes/s" in u -> { v -> Format.bytesPerSec(v.toDouble()) }
        "kilobits/s" in u || "kb/s" in u -> { v -> Format.bitsPerSec(v * 1000.0 / 8) }
        "mb/s" in u || "mbits" in u || "megabits" in u -> { v -> Format.bitsPerSec(v * 1_000_000.0 / 8) }
        "bits/s" in u -> { v -> Format.bitsPerSec(v / 8.0) }
        "mib" in u || "megabytes" in u -> { v -> Format.bytes((v * 1024 * 1024).toLong()) }
        "gib" in u || "gigabytes" in u -> { v -> Format.bytes((v * 1024.0 * 1024 * 1024).toLong()) }
        "bytes" in u -> { v -> Format.bytes(v.toLong()) }
        "seconds" in u && def.name == "uptime" -> { v -> Format.duration(v.toLong()) }
        else -> { v -> if (v >= 100) String.format(Locale.US, "%.0f", v) else String.format(Locale.US, "%.2f", v) }
    }
}
