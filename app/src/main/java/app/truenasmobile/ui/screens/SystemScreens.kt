package app.truenasmobile.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.truenasmobile.api.AuditEvent
import app.truenasmobile.api.BootEnv
import app.truenasmobile.api.JobInfo
import app.truenasmobile.api.abortJob
import app.truenasmobile.api.activateBootEnv
import app.truenasmobile.api.applyUpdate
import app.truenasmobile.api.auditEvents
import app.truenasmobile.api.bootEnvironments
import app.truenasmobile.api.deleteBootEnv
import app.truenasmobile.api.jobs
import app.truenasmobile.api.keepBootEnv
import app.truenasmobile.api.prettyMethod
import app.truenasmobile.api.updateInfo
import app.truenasmobile.ui.Format
import app.truenasmobile.ui.ListPadding
import app.truenasmobile.ui.LocalChrome
import app.truenasmobile.ui.Route
import app.truenasmobile.ui.ScreenScaffold
import app.truenasmobile.ui.components.ConfirmDialog
import app.truenasmobile.ui.components.EmptyState
import app.truenasmobile.ui.components.ItemRow
import app.truenasmobile.ui.components.LoadedBox
import app.truenasmobile.ui.components.SectionCard
import app.truenasmobile.ui.components.SmallSpinner
import app.truenasmobile.ui.components.StatusChip
import app.truenasmobile.ui.components.rememberLoad
import app.truenasmobile.ui.theme.AppColors
import kotlinx.coroutines.delay

// ====================================================================== update

@Composable
fun UpdateScreen() {
    val chrome = LocalChrome.current
    val vm = chrome.vm
    val loaded = rememberLoad("update") { it.updateInfo() }
    val busy by vm.busy.collectAsStateWithLifecycle()
    val uri = LocalUriHandler.current
    var confirm by remember { mutableStateOf(false) }
    val progress = busy["update"]

    ScreenScaffold(title = "Update", showBack = true) {
        LoadedBox(loaded) { u ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SectionCard(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Installed", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(versionLabel(u.currentVersion), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        u.train?.let { Text("Train: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                SectionCard(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        when {
                            u.error != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = AppColors.status.bad)
                                Text("  ${u.error}", style = MaterialTheme.typography.bodyMedium)
                            }
                            u.rebootRequired -> {
                                Text("An update was installed. Restart to finish.", style = MaterialTheme.typography.titleSmall)
                                Button(onClick = { chrome.nav.push(Route.Power) }) { Text("Restart…") }
                            }
                            u.available != null -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.SystemUpdateAlt, contentDescription = null, tint = AppColors.status.info)
                                    Text("  ${versionLabel(u.available)} is available", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                }
                                u.releaseNotesUrl?.let { url ->
                                    TextButton(onClick = { runCatching { uri.openUri(url) } }) { Text("Read the release notes") }
                                }
                                if (progress != null) {
                                    Text(progress, style = MaterialTheme.typography.bodyMedium)
                                    LinearProgressIndicator(Modifier.fillMaxWidth())
                                    Text("You can leave this screen. The server restarts if you chose that.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    Button(onClick = { confirm = true }) { Text("Download and install") }
                                }
                            }
                            else -> Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = AppColors.status.good)
                                Text("  You're up to date", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                        u.downloading?.let { Text("Downloading: $it", style = MaterialTheme.typography.bodySmall) }
                        TextButton(onClick = loaded.reload, enabled = !loaded.loading) { Text(if (loaded.loading) "Checking…" else "Check again") }
                    }
                }
                Text(
                    "Before big updates, make sure your apps and data are backed up. The old version stays as a boot environment you can go back to.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    val u = loaded.value
    if (confirm && u?.available != null) {
        var reboot by remember { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Install ${versionLabel(u.available)}?") },
            text = {
                Column {
                    Text("Apps, shares and VMs go offline while the server restarts.", style = MaterialTheme.typography.bodyMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = reboot, onCheckedChange = { reboot = it })
                        Text("Restart automatically when it's done")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    vm.act("update", "Updating", if (reboot) "Update installed. Restarting…" else "Update installed. Restart to finish.", after = loaded.reload) { a, p ->
                        a.applyUpdate(u.newStyle, reboot, p)
                    }
                }) { Text("Install") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}

// ====================================================================== boot environments

@Composable
fun BootEnvScreen() {
    val vm = LocalChrome.current.vm
    val loaded = rememberLoad("bootenv") { it.bootEnvironments() }
    val busy by vm.busy.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf<BootEnv?>(null) }
    var activating by remember { mutableStateOf<BootEnv?>(null) }
    ScreenScaffold(title = "Boot environments", subtitle = "Installed TrueNAS versions", showBack = true) {
        LoadedBox(loaded) { envs ->
            LazyColumn(contentPadding = ListPadding, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                item {
                    Text(
                        "Each update keeps the previous version here. If an update causes trouble, activate an older one and restart.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(4.dp),
                    )
                }
                items(envs, key = { it.id }) { b ->
                    var menu by remember { mutableStateOf(false) }
                    ItemRow(
                        title = b.id,
                        subtitle = listOfNotNull(b.createdMillis?.let { Format.date(it) }, b.used?.let { Format.bytes(it) }, if (b.keep) "kept" else null).joinToString(" · "),
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                when {
                                    b.active && b.activated -> StatusChip("Running", AppColors.status.good)
                                    b.active -> StatusChip("Running now", AppColors.status.good)
                                    b.activated -> StatusChip("Next boot", AppColors.status.info)
                                }
                                if (busy.containsKey("be:${b.id}")) {
                                    SmallSpinner(Modifier.padding(12.dp))
                                } else {
                                    Box {
                                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "Actions") }
                                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                            if (!b.activated && b.canActivate) {
                                                DropdownMenuItem(text = { Text("Use on next boot") }, onClick = {
                                                    menu = false
                                                    activating = b
                                                })
                                            }
                                            DropdownMenuItem(text = { Text(if (b.keep) "Allow automatic removal" else "Keep (don't remove automatically)") }, onClick = {
                                                menu = false
                                                vm.act("be:${b.id}", "Saving", null, after = loaded.reload) { a, _ -> a.keepBootEnv(b.id, !b.keep) }
                                            })
                                            if (!b.active && !b.activated) {
                                                DropdownMenuItem(text = { Text("Delete", color = AppColors.status.bad) }, onClick = {
                                                    menu = false
                                                    deleting = b
                                                })
                                            }
                                        }
                                    }
                                }
                            }
                        },
                    )
                }
            }
        }
    }
    activating?.let { b ->
        ConfirmDialog(
            title = "Boot ${b.id} next time?",
            text = "The server keeps running as it is until you restart it.",
            confirmLabel = "Use on next boot",
            onConfirm = { vm.act("be:${b.id}", "Activating", "${b.id} will be used after a restart", after = loaded.reload) { a, _ -> a.activateBootEnv(b.id) } },
            onDismiss = { activating = null },
        )
    }
    deleting?.let { b ->
        ConfirmDialog(
            title = "Delete ${b.id}?",
            text = "You won't be able to go back to this version.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = { vm.act("be:${b.id}", "Deleting", "Deleted", after = loaded.reload) { a, _ -> a.deleteBootEnv(b.id) } },
            onDismiss = { deleting = null },
        )
    }
}

// ====================================================================== jobs

@Composable
fun JobsScreen() {
    val vm = LocalChrome.current.vm
    val loaded = rememberLoad("jobs") { it.jobs() }
    val running by vm.runningJobs.collectAsStateWithLifecycle()
    var detail by remember { mutableStateOf<JobInfo?>(null) }
    var onlyActive by rememberSaveable { mutableStateOf(false) }

    // Keep it live while something is running.
    LaunchedEffect(running) {
        while (running > 0) {
            delay(2_000)
            loaded.reload()
        }
    }
    ScreenScaffold(title = "Jobs", subtitle = if (running > 0) "$running running" else "Recent tasks on the server", showBack = true) {
        LoadedBox(loaded) { jobs ->
            val shown = if (onlyActive) jobs.filter { it.running } else jobs
            LazyColumn(contentPadding = ListPadding, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                item {
                    FilterChip(selected = onlyActive, onClick = { onlyActive = !onlyActive }, label = { Text("Running only") }, modifier = Modifier.padding(top = 8.dp))
                }
                if (shown.isEmpty()) item { EmptyState(Icons.Outlined.CheckCircle, "Nothing running") }
                items(shown, key = { it.id }) { j -> JobRow(j) { detail = j } }
            }
        }
    }
    detail?.let { j ->
        AlertDialog(
            onDismissRequest = { detail = null },
            title = { Text(prettyMethod(j.method)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${Format.title(j.state)} · #${j.id}", style = MaterialTheme.typography.bodyMedium)
                    j.startedMillis?.let { Text("Started ${Format.dateTime(it)}", style = MaterialTheme.typography.bodySmall) }
                    j.finishedMillis?.let { Text("Finished ${Format.dateTime(it)}", style = MaterialTheme.typography.bodySmall) }
                    j.description?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    j.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = AppColors.status.bad) }
                    Text(j.method, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton(onClick = { detail = null }) { Text("Close") } },
            dismissButton = if (j.running && j.abortable) {
                {
                    TextButton(onClick = {
                        detail = null
                        vm.act("job:${j.id}", "Stopping", "Job stopped", after = loaded.reload) { a, _ -> a.abortJob(j.id) }
                    }) { Text("Stop job", color = AppColors.status.bad) }
                }
            } else {
                null
            },
        )
    }
}

@Composable
private fun JobRow(j: JobInfo, onClick: () -> Unit) {
    val s = AppColors.status
    Column(
        Modifier
            .fillMaxWidth()
            .padding(0.dp),
    ) {
        ItemRow(
            title = prettyMethod(j.method),
            subtitle = listOfNotNull(
                j.description?.takeIf { j.running },
                j.error,
                j.startedMillis?.let { Format.ago(it) },
            ).joinToString(" · "),
            onClick = onClick,
            trailing = {
                StatusChip(
                    when (j.state) {
                        "SUCCESS" -> "Done"
                        "FAILED" -> "Failed"
                        "ABORTED" -> "Stopped"
                        "RUNNING" -> j.percent?.takeIf { it in 1..99 }?.let { "$it%" } ?: "Running"
                        "WAITING" -> "Waiting"
                        else -> Format.title(j.state)
                    },
                    when (j.state) {
                        "SUCCESS" -> s.good
                        "FAILED" -> s.bad
                        "RUNNING", "WAITING" -> s.info
                        else -> s.idle
                    },
                )
            },
        )
        if (j.running && j.percent != null) {
            LinearProgressIndicator(progress = { (j.percent ?: 0) / 100f }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        }
    }
}

// ====================================================================== audit

@Composable
fun AuditScreen() {
    var services by rememberSaveable { mutableStateOf(listOf("MIDDLEWARE", "SMB", "SUDO")) }
    var query by rememberSaveable { mutableStateOf("") }
    val loaded = rememberLoad("audit", services) { it.auditEvents(services) }
    var detail by remember { mutableStateOf<AuditEvent?>(null) }
    ScreenScaffold(title = "Audit log", subtitle = "Who did what, and when", showBack = true) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("MIDDLEWARE" to "TrueNAS", "SMB" to "SMB", "SUDO" to "sudo").forEach { (k, l) ->
                    FilterChip(selected = k in services, onClick = {
                        services = if (k in services) (services - k).ifEmpty { listOf(k) } else services + k
                    }, label = { Text(l) })
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Filter by user, event or address") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
            LoadedBox(loaded) { events ->
                val q = query.trim()
                val shown = if (q.isEmpty()) events else events.filter {
                    it.username.contains(q, true) || it.event.contains(q, true) || it.address.contains(q, true) || it.details.contains(q, true)
                }
                LazyColumn(contentPadding = ListPadding, verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxSize()) {
                    item { Spacer(Modifier.height(4.dp)) }
                    items(shown, key = { it.id }) { e ->
                        ItemRow(
                            title = "${Format.title(e.event)} · ${e.username.ifBlank { "system" }}",
                            subtitle = listOfNotNull(
                                e.atMillis?.let { Format.dateTime(it) },
                                e.address.ifBlank { null },
                                e.details.take(140).ifBlank { null },
                            ).joinToString(" · "),
                            onClick = { detail = e },
                            trailing = { if (!e.success) Icon(Icons.Outlined.ErrorOutline, contentDescription = "Failed", tint = AppColors.status.bad, modifier = Modifier.size(20.dp)) },
                        )
                    }
                }
            }
        }
    }
    detail?.let { e ->
        AlertDialog(
            onDismissRequest = { detail = null },
            title = { Text(Format.title(e.event)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${e.service} · ${if (e.success) "succeeded" else "failed"}", style = MaterialTheme.typography.bodyMedium)
                    Text("${e.username} from ${e.address}", style = MaterialTheme.typography.bodySmall)
                    e.atMillis?.let { Text(Format.dateTime(it), style = MaterialTheme.typography.bodySmall) }
                    Text(e.details, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
            },
            confirmButton = { TextButton(onClick = { detail = null }) { Text("Close") } },
        )
    }
}
