package app.truenasmobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.PendingActions
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.FolderShared
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.truenasmobile.BuildConfig
import app.truenasmobile.api.ServiceEntry
import app.truenasmobile.ui.Format
import app.truenasmobile.ui.ListPadding
import app.truenasmobile.ui.LocalChrome
import app.truenasmobile.ui.Route
import app.truenasmobile.ui.ScreenScaffold
import app.truenasmobile.ui.components.EmptyState
import app.truenasmobile.ui.components.LoadingBox
import app.truenasmobile.ui.components.NavRow
import app.truenasmobile.ui.components.SectionCard
import app.truenasmobile.ui.components.SectionHeader
import app.truenasmobile.ui.components.SmallSpinner
import app.truenasmobile.ui.theme.AppColors

@Composable
fun MoreScreen() {
    val chrome = LocalChrome.current
    val vm = chrome.vm
    val nav = chrome.nav
    val services by vm.services.collectAsStateWithLifecycle()
    val servers by vm.servers.collectAsStateWithLifecycle()
    val active by vm.active.collectAsStateWithLifecycle()
    val system by vm.system.collectAsStateWithLifecycle()
    val running by vm.runningJobs.collectAsStateWithLifecycle()
    val uri = LocalUriHandler.current

    // Same order as the TrueNAS web UI's menu.
    ScreenScaffold(title = "Menu") {
        LazyColumn(contentPadding = ListPadding, modifier = Modifier.fillMaxSize()) {
            item { SectionHeader("Storage & sharing") }
            item { NavRow(Icons.Outlined.Storage, "Storage", "Pools, datasets and disks", onClick = { nav.selectTab(app.truenasmobile.ui.Tab.Storage) }) }
            item { NavRow(Icons.Outlined.FolderShared, "Shares", "SMB and NFS shares", onClick = { nav.push(Route.Shares) }) }
            item { NavRow(Icons.Outlined.Backup, "Data protection", "Snapshot, scrub, replication and backup tasks", onClick = { nav.push(Route.Protection) }) }

            item { SectionHeader("Network & accounts") }
            item { NavRow(Icons.Outlined.Lan, "Network", "Interfaces, DNS and routes", onClick = { nav.push(Route.Network) }) }
            item { NavRow(Icons.Outlined.Group, "Users and groups", "Local accounts", onClick = { nav.push(Route.Users) }) }
            item { NavRow(Icons.Outlined.Key, "API keys", "Keys for apps and scripts", onClick = { nav.push(Route.ApiKeys) }) }
            item { NavRow(Icons.Outlined.Verified, "Certificates", "TLS certificates and expiry", onClick = { nav.push(Route.Certificates) }) }

            item { SectionHeader("Apps & virtualization") }
            item { NavRow(Icons.Outlined.Computer, "Virtualization", "Virtual machines and containers", onClick = { nav.push(Route.Virtualization) }) }

            item { SectionHeader("Monitoring") }
            item { NavRow(Icons.Outlined.QueryStats, "Reporting", "CPU, memory, network and disk history", onClick = { nav.push(Route.Reporting) }) }
            item {
                NavRow(Icons.Outlined.PendingActions, "Jobs", if (running > 0) "$running running now" else "Recent tasks", onClick = { nav.push(Route.Jobs) })
            }
            item { NavRow(Icons.Outlined.Policy, "Audit log", "Sign-ins and changes", onClick = { nav.push(Route.Audit) }) }

            item { SectionHeader("System") }
            item { NavRow(Icons.Outlined.SystemUpdateAlt, "Update", system?.let { versionLabel(it.version) }, onClick = { nav.push(Route.Update) }) }
            item {
                NavRow(
                    Icons.Outlined.Tune,
                    "Services",
                    services?.let { list -> "${list.count { it.running }} running · ${list.count { it.enable }} start on boot" },
                    onClick = { nav.push(Route.Services) },
                )
            }
            item { NavRow(Icons.Outlined.Terminal, "Shell", "Command line on the server", onClick = { nav.push(Route.Shell(null, null, "Shell")) }) }
            item { NavRow(Icons.Outlined.History, "Boot environments", "Go back to an earlier TrueNAS version", onClick = { nav.push(Route.BootEnvs) }) }
            item { NavRow(Icons.Outlined.PowerSettingsNew, "Restart or shut down", system?.hostname, onClick = { nav.push(Route.Power) }) }
            active?.endpoint?.let { ep ->
                item {
                    NavRow(Icons.Outlined.OpenInBrowser, "Open web UI", ep.webUrl, onClick = { runCatching { uri.openUri(ep.webUrl) } })
                }
            }

            item { SectionHeader("This app") }
            item {
                NavRow(
                    Icons.Outlined.Dns,
                    "Servers",
                    if (servers.size > 1) "${servers.size} saved · using ${active?.displayName}" else active?.displayName,
                    onClick = { nav.push(Route.Servers) },
                )
            }
            item { NavRow(Icons.Outlined.Settings, "Settings", "Theme, notifications", onClick = { nav.push(Route.Settings) }) }
            item {
                NavRow(
                    Icons.Outlined.Info,
                    "About",
                    "TrueNAS Mobile ${BuildConfig.VERSION_NAME} · works with TrueNAS SCALE 25.04 and newer",
                    onClick = {},
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Services

@Composable
fun ServicesScreen() {
    val vm = LocalChrome.current.vm
    val services by vm.services.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()

    ScreenScaffold(title = "Services", showBack = true) {
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
            val list = services
            LazyColumn(contentPadding = ListPadding, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                item { Spacer(Modifier.height(8.dp)) }
                when {
                    list == null -> item { LoadingBox() }
                    list.isEmpty() -> item { EmptyState(Icons.Outlined.Tune, "No services") }
                    else -> items(list.sortedWith(compareByDescending<ServiceEntry> { it.running }.thenBy { Format.serviceName(it.service) }), key = { it.service }) { svc ->
                        ServiceRow(
                            svc = svc,
                            busyLabel = busy["svc:${svc.service}"],
                            savingBoot = busy.containsKey("svcboot:${svc.service}"),
                            onRun = { vm.setServiceRunning(svc, it) },
                            onBoot = { vm.setServiceEnabled(svc, it) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ServiceRow(
    svc: ServiceEntry,
    busyLabel: String?,
    savingBoot: Boolean,
    onRun: (Boolean) -> Unit,
    onBoot: (Boolean) -> Unit,
) {
    val s = AppColors.status
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(Format.serviceName(svc.service), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    busyLabel ?: if (svc.running) "Running" else "Stopped",
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        busyLabel != null -> MaterialTheme.colorScheme.primary
                        svc.running -> s.good
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            if (busyLabel != null) {
                SmallSpinner(Modifier.padding(end = 12.dp))
            }
            Switch(checked = svc.running, onCheckedChange = onRun, enabled = busyLabel == null)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = svc.enable, onCheckedChange = onBoot, enabled = !savingBoot)
            Text("Start automatically on boot", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---------------------------------------------------------------- Power

@Composable
fun PowerScreen() {
    val vm = LocalChrome.current.vm
    val system by vm.system.collectAsStateWithLifecycle()
    val reasons by vm.rebootReasons.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var ask by remember { mutableStateOf<Boolean?>(null) } // true = restart, false = shut down
    val s = AppColors.status

    ScreenScaffold(title = "Power", subtitle = system?.hostname, showBack = true) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (reasons.isNotEmpty()) {
                SectionCard(Modifier.fillMaxWidth()) {
                    Column {
                        Text("A restart is pending", style = MaterialTheme.typography.titleSmall, color = s.warn)
                        Spacer(Modifier.height(4.dp))
                        Text(reasons.joinToString("\n"), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            PowerCard(
                icon = Icons.Outlined.RestartAlt,
                title = "Restart",
                text = "Apps, shares and VMs go offline for a few minutes. The app reconnects by itself.",
                color = s.warn,
                enabled = !busy.containsKey("power"),
            ) { ask = true }
            PowerCard(
                icon = Icons.Outlined.PowerSettingsNew,
                title = "Shut down",
                text = "Turns the server off. You'll need physical access (or IPMI) to turn it back on.",
                color = s.bad,
                enabled = !busy.containsKey("power"),
            ) { ask = false }
        }
    }

    ask?.let { reboot -> PowerDialog(reboot, onConfirm = { reason -> vm.power(reboot, reason) }, onDismiss = { ask = null }) }
}

@Composable
private fun PowerCard(icon: ImageVector, title: String, text: String, color: Color, enabled: Boolean, onClick: () -> Unit) {
    SectionCard(Modifier.fillMaxWidth(), onClick = if (enabled) onClick else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PowerDialog(reboot: Boolean, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var reason by remember { mutableStateOf(if (reboot) "Restarted from TrueNAS Mobile" else "Shut down from TrueNAS Mobile") }
    var sure by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(if (reboot) Icons.Outlined.RestartAlt else Icons.Outlined.PowerSettingsNew, contentDescription = null) },
        title = { Text(if (reboot) "Restart the server?" else "Shut down the server?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason (saved in the audit log)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = sure, onCheckedChange = { sure = it })
                    Text(
                        if (reboot) "Everything on the server will be offline for a few minutes" else "I can turn it back on myself",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(reason)
                    onDismiss()
                },
                enabled = sure && reason.isNotBlank(),
            ) {
                Text(if (reboot) "Restart" else "Shut down", color = if (sure) AppColors.status.bad else Color.Unspecified, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
