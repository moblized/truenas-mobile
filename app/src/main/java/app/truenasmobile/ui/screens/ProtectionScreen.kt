package app.truenasmobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.truenasmobile.api.Cron
import app.truenasmobile.api.ProtectionTask
import app.truenasmobile.api.TaskKind
import app.truenasmobile.api.abortTask
import app.truenasmobile.api.deleteTask
import app.truenasmobile.api.protectionTasks
import app.truenasmobile.api.runTaskNow
import app.truenasmobile.api.saveScrubTask
import app.truenasmobile.api.saveSnapshotTask
import app.truenasmobile.api.setTaskEnabled
import app.truenasmobile.net.bool
import app.truenasmobile.net.int
import app.truenasmobile.net.str
import app.truenasmobile.ui.Format
import app.truenasmobile.ui.ListPadding
import app.truenasmobile.ui.LocalChrome
import app.truenasmobile.ui.ScreenScaffold
import app.truenasmobile.ui.components.ConfirmDialog
import app.truenasmobile.ui.components.ItemRow
import app.truenasmobile.ui.components.LoadedBox
import app.truenasmobile.ui.components.NumberBox
import app.truenasmobile.ui.components.ScheduleEditor
import app.truenasmobile.ui.components.SectionHeader
import app.truenasmobile.ui.components.SmallSpinner
import app.truenasmobile.ui.components.StatusChip
import app.truenasmobile.ui.components.SwitchRow
import app.truenasmobile.ui.components.rememberLoad
import app.truenasmobile.ui.theme.AppColors
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

@Composable
fun ProtectionScreen() {
    val vm = LocalChrome.current.vm
    val loaded = rememberLoad("protection") { a ->
        coroutineScope {
            TaskKind.entries.map { k -> async { k to runCatching { a.protectionTasks(k) }.getOrDefault(emptyList()) } }.awaitAll().toMap()
        }
    }
    val busy by vm.busy.collectAsStateWithLifecycle()
    var editingSnapshot by remember { mutableStateOf<ProtectionTask?>(null) }
    var newSnapshot by remember { mutableStateOf(false) }
    var editingScrub by remember { mutableStateOf<ProtectionTask?>(null) }
    var newScrub by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<ProtectionTask?>(null) }
    var adding by remember { mutableStateOf(false) }

    ScreenScaffold(
        title = "Data protection",
        subtitle = "Snapshots, scrubs, backups and sync",
        showBack = true,
        fab = {
            ExtendedFloatingActionButton(onClick = { adding = true }, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("Add task") })
        },
    ) {
        LoadedBox(loaded) { all ->
            LazyColumn(contentPadding = ListPadding, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                TaskKind.entries.forEach { kind ->
                    val tasks = all[kind] ?: emptyList()
                    val alwaysShow = kind == TaskKind.SNAPSHOT || kind == TaskKind.SCRUB
                    if (tasks.isNotEmpty() || alwaysShow) {
                        item(key = "h-$kind") { SectionHeader(kind.label) }
                        if (tasks.isEmpty()) {
                            item(key = "e-$kind") {
                                Text(
                                    if (kind == TaskKind.SNAPSHOT) "No automatic snapshots. Add one to protect a dataset against mistakes and ransomware."
                                    else "No scrub schedule. TrueNAS usually makes one per pool.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                                )
                            }
                        }
                        items(tasks, key = { "${it.kind}-${it.id}" }) { t ->
                            TaskRow(
                                t,
                                working = busy.containsKey("task:${t.kind}:${t.id}"),
                                onEdit = when (t.kind) {
                                    TaskKind.SNAPSHOT -> ({ editingSnapshot = t })
                                    TaskKind.SCRUB -> ({ editingScrub = t })
                                    else -> null
                                },
                                onToggle = { on ->
                                    vm.act("task:${t.kind}:${t.id}", "Saving", null, after = loaded.reload) { a, _ -> a.setTaskEnabled(t, on) }
                                },
                                onRun = {
                                    vm.act("task:${t.kind}:${t.id}", "Starting", "${t.title} started. Track it under Jobs.", after = loaded.reload) { a, _ -> a.runTaskNow(t) }
                                },
                                onAbort = {
                                    vm.act("task:${t.kind}:${t.id}", "Stopping", "Stopped", after = loaded.reload) { a, _ -> a.abortTask(t) }
                                },
                                onDelete = { deleting = t },
                            )
                        }
                    }
                }
                item {
                    Text(
                        "Replication, cloud sync, rsync and TrueCloud tasks are set up in the web UI (they need credentials and remote systems). Here you can run, stop, turn on or off and delete them.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp, start = 4.dp, end = 4.dp),
                    )
                }
            }
        }
    }

    if (adding) {
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("Add a task") },
            text = {
                Column {
                    TextButton(onClick = {
                        adding = false
                        newSnapshot = true
                    }) { Text("Automatic snapshots of a dataset") }
                    TextButton(onClick = {
                        adding = false
                        newScrub = true
                    }) { Text("Scrub schedule for a pool") }
                }
            },
            confirmButton = { TextButton(onClick = { adding = false }) { Text("Cancel") } },
        )
    }
    if (newSnapshot || editingSnapshot != null) {
        SnapshotTaskDialog(editingSnapshot, onDone = {
            newSnapshot = false
            editingSnapshot = null
            loaded.reload()
        })
    }
    if (newScrub || editingScrub != null) {
        ScrubTaskDialog(editingScrub, onDone = {
            newScrub = false
            editingScrub = null
            loaded.reload()
        })
    }
    deleting?.let { t ->
        ConfirmDialog(
            title = "Delete this task?",
            text = "${t.kind.label}: ${t.title}. Existing snapshots and backups are kept.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = { vm.act("task:${t.kind}:${t.id}", "Deleting", "Task deleted", after = loaded.reload) { a, _ -> a.deleteTask(t) } },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun TaskRow(
    t: ProtectionTask,
    working: Boolean,
    onEdit: (() -> Unit)?,
    onToggle: (Boolean) -> Unit,
    onRun: () -> Unit,
    onAbort: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val s = AppColors.status
    val last = t.last
    Column {
    ItemRow(
        title = t.title,
        subtitle = listOfNotNull(
            t.schedule?.describe() ?: if (t.kind == TaskKind.REPLICATION) "Runs after its snapshot task" else null,
            t.detail.ifBlank { null },
        ).joinToString("\n"),
        onClick = onEdit,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (working) SmallSpinner(Modifier.padding(horizontal = 8.dp)) else Switch(checked = t.enabled, onCheckedChange = onToggle)
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Run now") }, onClick = {
                            menu = false
                            onRun()
                        })
                        if (last?.running == true) {
                            DropdownMenuItem(text = { Text("Stop") }, onClick = {
                                menu = false
                                onAbort()
                            })
                        }
                        if (onEdit != null) {
                            DropdownMenuItem(text = { Text("Edit") }, onClick = {
                                menu = false
                                onEdit()
                            })
                        }
                        DropdownMenuItem(text = { Text("Delete", color = s.bad) }, onClick = {
                            menu = false
                            onDelete()
                        })
                    }
                }
            }
        },
        leading = null,
    )
    if (last != null) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusChip(
                    when {
                        last.running -> "Running"
                        last.failed -> "Failed"
                        last.state == "SUCCESS" || last.state == "FINISHED" -> "OK"
                        last.state == "PENDING" -> "Pending"
                        else -> Format.title(last.state ?: "—")
                    },
                    when {
                        last.running -> s.info
                        last.failed -> s.bad
                        last.state == "SUCCESS" || last.state == "FINISHED" -> s.good
                        else -> s.idle
                    },
                )
                androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
                Text(
                    last.atMillis?.let { "Last run ${Format.ago(it)}" } ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (last.running && last.percent != null) LinearProgressIndicator(progress = { (last.percent ?: 0) / 100f }, modifier = Modifier.fillMaxWidth())
            last.error?.takeIf { last.failed }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = s.bad, maxLines = 3) }
        }
    }
    }
}

@Composable
private fun SnapshotTaskDialog(existing: ProtectionTask?, onDone: () -> Unit) {
    val vm = LocalChrome.current.vm
    val datasets by vm.datasets.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.loadDatasets() }
    val raw = existing?.raw
    var dataset by remember { mutableStateOf(raw?.str("dataset") ?: "") }
    var recursive by remember { mutableStateOf(raw?.bool("recursive") ?: false) }
    var keep by remember { mutableStateOf(raw?.int("lifetime_value") ?: 2) }
    var unit by remember { mutableStateOf(raw?.str("lifetime_unit") ?: "WEEK") }
    var schedule by remember { mutableStateOf(existing?.schedule ?: Cron(minute = "0", hour = "0")) }
    var enabled by remember { mutableStateOf(existing?.enabled ?: true) }
    var picking by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(if (existing == null) "Automatic snapshots" else "Edit snapshot task") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 540.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(onClick = { picking = true }) { Text(dataset.ifBlank { "Choose a dataset…" }) }
                SwitchRow("Include datasets inside it", null, recursive) { recursive = it }
                Text("When", style = MaterialTheme.typography.titleSmall)
                ScheduleEditor(schedule) { schedule = it }
                Text("Keep each snapshot for", style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberBox(keep, 1..9999, Modifier.width(90.dp)) { keep = it }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("HOUR" to "Hours", "DAY" to "Days", "WEEK" to "Weeks", "MONTH" to "Months", "YEAR" to "Years").forEach { (v, l) ->
                        FilterChip(selected = unit == v, onClick = { unit = v }, label = { Text(l) })
                    }
                }
                SwitchRow("Enabled", null, enabled) { enabled = it }
            }
        },
        confirmButton = {
            TextButton(enabled = dataset.isNotBlank(), onClick = {
                vm.act("task:new", "Saving", "Snapshot task saved", after = onDone) { a, _ ->
                    a.saveSnapshotTask(existing?.id, dataset, recursive, keep, unit, schedule, enabled)
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Cancel") } },
    )
    if (picking) {
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text("Dataset") },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 440.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    (datasets ?: emptyList()).filter { !it.locked }.forEach { d ->
                        TextButton(onClick = {
                            dataset = d.id
                            picking = false
                        }) { Text(d.id, modifier = Modifier.padding(start = (d.depth * 12).dp)) }
                    }
                    if (datasets == null) SmallSpinner()
                }
            },
            confirmButton = { TextButton(onClick = { picking = false }) { Text("Close") } },
        )
    }
}

@Composable
private fun ScrubTaskDialog(existing: ProtectionTask?, onDone: () -> Unit) {
    val vm = LocalChrome.current.vm
    val pools by vm.pools.collectAsStateWithLifecycle()
    val raw = existing?.raw
    var poolId by remember { mutableStateOf(raw?.int("pool") ?: pools?.firstOrNull()?.id) }
    var threshold by remember { mutableStateOf(raw?.int("threshold") ?: 35) }
    var schedule by remember { mutableStateOf(existing?.schedule ?: Cron(minute = "0", hour = "0", dow = "0")) }
    var enabled by remember { mutableStateOf(existing?.enabled ?: true) }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(if (existing == null) "Scrub schedule" else "Edit scrub schedule") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 540.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Pool", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (pools ?: emptyList()).forEach { p -> FilterChip(selected = poolId == p.id, onClick = { poolId = p.id }, label = { Text(p.name) }) }
                }
                Text("Check when", style = MaterialTheme.typography.titleSmall)
                ScheduleEditor(schedule) { schedule = it }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Only if the last scrub was over", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    NumberBox(threshold, 0..365, Modifier.width(80.dp)) { threshold = it }
                    Text("days ago")
                }
                SwitchRow("Enabled", null, enabled) { enabled = it }
            }
        },
        confirmButton = {
            TextButton(enabled = poolId != null, onClick = {
                val id = poolId ?: return@TextButton
                vm.act("task:new", "Saving", "Scrub schedule saved", after = onDone) { a, _ -> a.saveScrubTask(existing?.id, id, threshold, schedule, enabled) }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Cancel") } },
    )
}
