package app.truenasmobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.truenasmobile.api.App
import app.truenasmobile.api.AppContainer
import app.truenasmobile.api.AppStat
import app.truenasmobile.ui.AppAction
import app.truenasmobile.ui.Format
import app.truenasmobile.ui.ListPadding
import app.truenasmobile.ui.LocalChrome
import app.truenasmobile.ui.Route
import app.truenasmobile.ui.ScreenScaffold
import app.truenasmobile.ui.components.AppIcon
import app.truenasmobile.ui.components.CardTitle
import app.truenasmobile.ui.components.ConfirmDialog
import app.truenasmobile.ui.components.EmptyState
import app.truenasmobile.ui.components.SectionCard
import app.truenasmobile.ui.components.SectionHeader
import app.truenasmobile.ui.components.SmallSpinner
import app.truenasmobile.ui.components.StatusChip
import app.truenasmobile.ui.theme.AppColors

@Composable
fun AppDetailScreen(name: String) {
    val chrome = LocalChrome.current
    val vm = chrome.vm
    val apps by vm.apps.collectAsStateWithLifecycle()
    val stats by vm.appStats.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val server by vm.active.collectAsStateWithLifecycle()
    val app = apps?.firstOrNull { it.name == name }
    var confirm by remember { mutableStateOf<AppAction?>(null) }
    var showNotes by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var rollbackChoices by remember { mutableStateOf<List<String>?>(null) }
    val uri = LocalUriHandler.current
    val scope = rememberCoroutineScope()

    ScreenScaffold(
        title = app?.title ?: name,
        subtitle = app?.let { "${appStateLabel(it.state)} · v${it.humanVersion}" },
        showBack = true,
        actions = {
            if (app != null) {
                IconButton(onClick = {
                    chrome.nav.push(if (app.customApp) Route.Yaml(app.name) else Route.EditApp(app.name))
                }) { Icon(Icons.Outlined.Edit, contentDescription = "Edit settings") }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (!app.customApp) {
                            DropdownMenuItem(
                                text = { Text("Roll back…") },
                                leadingIcon = { Icon(Icons.Outlined.History, contentDescription = null) },
                                onClick = {
                                    menu = false
                                    scope.launch {
                                        val versions = vm.rollbackVersions(app.name)
                                        if (versions.isEmpty()) vm.showMessage("No earlier version to roll back to") else rollbackChoices = versions
                                    }
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Delete app", color = AppColors.status.bad) },
                            leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = AppColors.status.bad) },
                            onClick = {
                                menu = false
                                deleting = true
                            },
                        )
                    }
                }
            }
        },
    ) {
        if (app == null) {
            EmptyState(Icons.Outlined.Info, if (apps == null) "Loading…" else "App not found")
        } else {
            val busyLabel = busy["app:${app.name}"]
            val host = server?.endpoint?.bareHost
            LazyColumn(contentPadding = ListPadding, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
                item { Spacer(Modifier.height(4.dp)) }
                item { HeaderCard(app, busyLabel) }
                item {
                    ActionButtons(
                        app = app,
                        busy = busyLabel != null,
                        onAction = { action -> if (action == AppAction.START) vm.appAction(app, action) else confirm = action },
                    )
                }
                if (app.portals.isNotEmpty()) {
                    item {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            app.portals.forEach { (label, url) ->
                                OutlinedButton(onClick = {
                                    runCatching { uri.openUri(portalUrl(url, host)) }
                                }) {
                                    Icon(Icons.Outlined.OpenInBrowser, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                                    Text("Open $label")
                                }
                            }
                        }
                    }
                }
                val st = stats[app.name]
                if (app.isRunning && st != null) item { StatsCard(st) }
                if (app.containers.isNotEmpty()) {
                    item { SectionHeader("Containers") }
                    items(app.containers, key = { it.id }) { c ->
                        ContainerRow(c) {
                            chrome.nav.push(Route.Logs(app.name, c.id, c.serviceName.ifBlank { app.title }))
                        }
                    }
                }
                app.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                    item {
                        SectionCard(Modifier.fillMaxWidth(), onClick = { showNotes = !showNotes }) {
                            Column {
                                CardTitle("Notes", Icons.Outlined.Info) {
                                    Text(if (showNotes) "Hide" else "Show", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                                }
                                if (showNotes) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(notes.trim(), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (deleting && app != null) {
        DeleteAppDialog(
            title = app.title,
            custom = app.customApp,
            onDelete = { removeData -> vm.deleteApp(app, removeData) { chrome.nav.back() } },
            onDismiss = { deleting = false },
        )
    }
    rollbackChoices?.let { versions ->
        if (app != null) {
            AlertDialog(
                onDismissRequest = { rollbackChoices = null },
                title = { Text("Roll back ${app.title}") },
                text = {
                    Column {
                        Text(
                            "Goes back to an earlier version you had installed, restoring its data snapshot if there is one.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        versions.forEach { v ->
                            TextButton(onClick = {
                                rollbackChoices = null
                                vm.rollbackApp(app, v)
                            }) { Text("Version $v") }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { rollbackChoices = null }) { Text("Cancel") } },
            )
        }
    }

    val current = app
    val action = confirm
    if (current != null && action != null) {
        val (title, text, label) = when (action) {
            AppAction.STOP -> Triple("Stop ${current.title}?", "Its containers will shut down until you start it again.", "Stop")
            AppAction.RESTART -> Triple("Restart ${current.title}?", "The app is redeployed: containers are recreated with the current settings.", "Restart")
            AppAction.UPGRADE -> Triple(
                "Update ${current.title}?",
                "Updates ${current.humanVersion} to ${current.latestVersion ?: "the latest version"}. The app restarts when it's done.",
                "Update",
            )
            AppAction.PULL_IMAGES -> Triple("Pull new images?", "Downloads newer images for ${current.title} and redeploys it.", "Update")
            AppAction.START -> Triple("Start ${current.title}?", "", "Start")
        }
        ConfirmDialog(
            title = title,
            text = text,
            confirmLabel = label,
            destructive = action == AppAction.STOP,
            onConfirm = { vm.appAction(current, action) },
            onDismiss = { confirm = null },
        )
    }
}

@Composable
private fun DeleteAppDialog(title: String, custom: Boolean, onDelete: (Boolean) -> Unit, onDismiss: () -> Unit) {
    var removeData by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = AppColors.status.bad) },
        title = { Text("Delete $title?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("The app and its containers are removed. Folders you chose yourself are never touched.", style = MaterialTheme.typography.bodyMedium)
                if (!custom) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = removeData, onCheckedChange = { removeData = it })
                        Text("Also delete the data TrueNAS created for it (Automatic storage). This can't be undone.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onDelete(removeData)
                onDismiss()
            }) { Text("Delete", color = AppColors.status.bad, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Portal links often point at 0.0.0.0; swap in the server's address. */
private fun portalUrl(url: String, host: String?): String {
    if (host == null) return url
    return url.replace("://0.0.0.0", "://$host").replace("://localhost", "://$host").replace("://127.0.0.1", "://$host")
}

@Composable
private fun HeaderCard(app: App, busyLabel: String?) {
    SectionCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app.iconUrl, app.title, 56.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusChip(busyLabel ?: appStateLabel(app.state), if (busyLabel != null) AppColors.status.info else appStateColor(app))
                    if (busyLabel != null) {
                        Spacer(Modifier.width(8.dp))
                        SmallSpinner()
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    buildString {
                        append("Version ${app.humanVersion}")
                        if (app.upgradeAvailable && app.latestVersion != null) append(" → ${app.latestVersion} available")
                        else if (app.imageUpdatesAvailable) append(" · newer images available")
                        if (app.customApp) append(" · custom app")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (app.hasUpdate) AppColors.status.info else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                app.description?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun ActionButtons(app: App, busy: Boolean, onAction: (AppAction) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (app.isRunning || app.state == "DEPLOYING") {
            ActionButton("Stop", Icons.Outlined.Stop, enabled = !busy, tonal = false) { onAction(AppAction.STOP) }
            ActionButton("Restart", Icons.Outlined.RestartAlt, enabled = !busy, tonal = true) { onAction(AppAction.RESTART) }
        } else {
            ActionButton("Start", Icons.Outlined.PlayArrow, enabled = !busy && app.state != "STOPPING", primary = true) { onAction(AppAction.START) }
        }
        if (app.upgradeAvailable) {
            ActionButton("Update", Icons.Outlined.SystemUpdateAlt, enabled = !busy, tonal = true) { onAction(AppAction.UPGRADE) }
        } else if (app.imageUpdatesAvailable) {
            ActionButton("Pull images", Icons.Outlined.SystemUpdateAlt, enabled = !busy, tonal = true) { onAction(AppAction.PULL_IMAGES) }
        }
    }
}

@Composable
private fun ActionButton(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    tonal: Boolean = false,
    primary: Boolean = false,
    onClick: () -> Unit,
) {
    val content: @Composable () -> Unit = {
        Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(label)
    }
    when {
        primary -> Button(onClick = onClick, enabled = enabled) { content() }
        tonal -> FilledTonalButton(onClick = onClick, enabled = enabled) { content() }
        else -> OutlinedButton(onClick = onClick, enabled = enabled) { content() }
    }
}

@Composable
private fun StatsCard(st: AppStat) {
    val s = AppColors.status
    SectionCard(Modifier.fillMaxWidth()) {
        Column {
            CardTitle("Live usage", Icons.Outlined.Speed)
            Spacer(Modifier.height(10.dp))
            Row {
                Stat("CPU", "${st.cpuPercent}%", Modifier.weight(1f))
                Stat("Memory", Format.bytes(st.memory), Modifier.weight(1f))
                Stat("Down", Format.bitsPerSec(st.rxBytesPerSec.toDouble()), Modifier.weight(1f), s.rx)
                Stat("Up", Format.bitsPerSec(st.txBytesPerSec.toDouble()), Modifier.weight(1f), s.tx)
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier, color: androidx.compose.ui.graphics.Color? = null) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = color ?: MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

@Composable
private fun ContainerRow(c: AppContainer, onLogs: () -> Unit) {
    val stateColor = when (c.state) {
        "running" -> AppColors.status.good
        "exited", "dead" -> AppColors.status.bad
        "starting", "created", "restarting" -> AppColors.status.info
        else -> AppColors.status.idle
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onLogs)
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.serviceName.ifBlank { c.id.take(12) }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                StatusChip(c.state.replaceFirstChar { it.uppercase() }, stateColor)
            }
            Text(
                c.image,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (c.ports.isNotEmpty()) {
                Text(
                    "Ports " + c.ports.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        TextButton(onClick = onLogs) {
            Icon(Icons.Outlined.Code, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text("Logs")
        }
    }
}
