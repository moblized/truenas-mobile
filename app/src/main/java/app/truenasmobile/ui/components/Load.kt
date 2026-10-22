package app.truenasmobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.truenasmobile.api.Cron
import app.truenasmobile.api.TrueNasApi
import app.truenasmobile.data.Connector
import app.truenasmobile.ui.ConnState
import app.truenasmobile.ui.LocalChrome
import kotlinx.coroutines.CancellationException

/** Data a screen loads for itself, with a way to load it again. */
class Loaded<T>(val value: T?, val error: String?, val loading: Boolean, val reload: () -> Unit)

/** Loads [load] once connected (and again when [keys] change or [Loaded.reload] is called). */
@Composable
fun <T> rememberLoad(vararg keys: Any?, load: suspend (TrueNasApi) -> T): Loaded<T> {
    val vm = LocalChrome.current.vm
    val conn by vm.conn.collectAsStateWithLifecycle()
    val connected = conn == ConnState.Connected
    val keyList = keys.toList()
    var tick by remember { mutableIntStateOf(0) }
    var value by remember(keyList) { mutableStateOf<T?>(null) }
    var error by remember(keyList) { mutableStateOf<String?>(null) }
    var loading by remember(keyList) { mutableStateOf(true) }
    LaunchedEffect(connected, tick, keyList) {
        if (!connected) return@LaunchedEffect
        loading = true
        try {
            value = vm.withApi(load)
            error = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = Connector.friendly(e)
        } finally {
            loading = false
        }
    }
    return Loaded(value, error, loading) { tick++ }
}

/** Shows a spinner, an error with retry, or [content] once there's data. */
@Composable
fun <T> LoadedBox(loaded: Loaded<T>, content: @Composable (T) -> Unit) {
    val v = loaded.value
    when {
        v != null -> content(v)
        loaded.error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            EmptyState(Icons.Outlined.CloudOff, "Couldn't load this", loaded.error)
            TextButton(onClick = loaded.reload) { Text("Try again") }
        }
        else -> LoadingBox()
    }
}

/** A row with a title, optional subtitle and a switch. */
@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

/** Card-style list row used by the admin screens. */
@Composable
fun ItemRow(
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
        trailing()
    }
}

/** Pick a schedule the easy way: hourly, daily, weekly or monthly, at a chosen time. */
@Composable
fun ScheduleEditor(value: Cron, onChange: (Cron) -> Unit) {
    val mode = when {
        value.hour == "*" && value.dom == "*" && value.dow == "*" -> "HOURLY"
        value.dom == "*" && value.dow == "*" && value.month == "*" -> "DAILY"
        value.dom == "*" && value.month == "*" && value.dow.toIntOrNull() != null -> "WEEKLY"
        value.dow == "*" && value.month == "*" && value.dom.toIntOrNull() != null -> "MONTHLY"
        else -> "CUSTOM"
    }
    val hour = value.hour.toIntOrNull() ?: 0
    val minute = value.minute.toIntOrNull() ?: 0
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("HOURLY" to "Hourly", "DAILY" to "Daily", "WEEKLY" to "Weekly", "MONTHLY" to "Monthly", "CUSTOM" to "Custom").forEach { (m, label) ->
                FilterChip(selected = mode == m, onClick = {
                    onChange(
                        when (m) {
                            "HOURLY" -> Cron(minute = minute.toString(), hour = "*")
                            "DAILY" -> Cron(minute = minute.toString(), hour = hour.toString())
                            "WEEKLY" -> Cron(minute = minute.toString(), hour = hour.toString(), dow = "0")
                            "MONTHLY" -> Cron(minute = minute.toString(), hour = hour.toString(), dom = "1")
                            else -> value.copy()
                        },
                    )
                }, label = { Text(label) })
            }
        }
        when (mode) {
            "HOURLY" -> NumberRow("At minute", minute, 0..59) { onChange(value.copy(minute = it.toString())) }
            "CUSTOM" -> {
                Text("Cron fields (minute hour day month weekday)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CronField("Min", value.minute, Modifier.weight(1f)) { onChange(value.copy(minute = it)) }
                    CronField("Hour", value.hour, Modifier.weight(1f)) { onChange(value.copy(hour = it)) }
                    CronField("Day", value.dom, Modifier.weight(1f)) { onChange(value.copy(dom = it)) }
                    CronField("Mon", value.month, Modifier.weight(1f)) { onChange(value.copy(month = it)) }
                    CronField("Wkd", value.dow, Modifier.weight(1f)) { onChange(value.copy(dow = it)) }
                }
            }
            else -> {
                if (mode == "WEEKLY") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").forEachIndexed { i, d ->
                            FilterChip(selected = value.dow == i.toString(), onClick = { onChange(value.copy(dow = i.toString())) }, label = { Text(d) })
                        }
                    }
                }
                if (mode == "MONTHLY") NumberRow("On day", value.dom.toIntOrNull() ?: 1, 1..28) { onChange(value.copy(dom = it.toString())) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("At", style = MaterialTheme.typography.bodyLarge)
                    NumberBox(hour, 0..23, Modifier.width(76.dp)) { onChange(value.copy(hour = it.toString())) }
                    Text(":", style = MaterialTheme.typography.bodyLarge)
                    NumberBox(minute, 0..59, Modifier.width(76.dp)) { onChange(value.copy(minute = it.toString())) }
                }
            }
        }
        Text(value.describe(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun NumberRow(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        NumberBox(value, range, Modifier.width(76.dp), onChange)
    }
}

@Composable
fun NumberBox(value: Int, range: IntRange, modifier: Modifier = Modifier, onChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { t ->
            text = t.filter(Char::isDigit).take(4)
            text.toIntOrNull()?.takeIf { it in range }?.let(onChange)
        },
        singleLine = true,
        isError = text.toIntOrNull()?.let { it !in range } ?: true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

@Composable
private fun CronField(label: String, value: String, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.trim().ifEmpty { "*" }) },
        label = { Text(label, maxLines = 1) },
        singleLine = true,
        modifier = modifier,
    )
}
