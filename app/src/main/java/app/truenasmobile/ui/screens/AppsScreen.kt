package app.truenasmobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.truenasmobile.api.App
import app.truenasmobile.api.AppLinks
import app.truenasmobile.api.AppStat
import app.truenasmobile.ui.AppAction
import app.truenasmobile.ui.ConnState
import app.truenasmobile.ui.Format
import app.truenasmobile.ui.ListPadding
import app.truenasmobile.ui.LocalChrome
import app.truenasmobile.ui.Route
import app.truenasmobile.ui.ScreenScaffold
import app.truenasmobile.ui.components.AppIcon
import app.truenasmobile.ui.components.ConfirmDialog
import app.truenasmobile.ui.components.EmptyState
import app.truenasmobile.ui.components.ItemRow
import app.truenasmobile.ui.components.LoadingBox
import app.truenasmobile.ui.components.SmallSpinner
import app.truenasmobile.ui.components.StatusChip
import app.truenasmobile.ui.theme.AppColors

private enum class AppFilter(val label: String) { ALL("All"), RUNNING("Running"), STOPPED("Stopped"), UPDATES("Updates") }

@Composable
fun AppsScreen() {
    val chrome = LocalChrome.current
    val vm = chrome.vm
    val apps by vm.apps.collectAsStateWithLifecycle()
    val stats by vm.appStats.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val links by vm.appLinks.collectAsStateWithLifecycle()
    val auto by vm.autoUpdate.collectAsStateWithLifecycle()
    val conn by vm.conn.collectAsStateWithLifecycle()
    LaunchedEffect(conn == ConnState.Connected) {
        if (conn == ConnState.Connected) vm.loadAutoUpdate()
    }
    val server by vm.active.collectAsStateWithLifecycle()
    val uri = LocalUriHandler.current
    fun openLink(url: String) {
        runCatching { uri.openUri(url) }.onFailure { vm.showMessage("Couldn't open $url") }
    }
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf(AppFilter.ALL) }
    var query by rememberSaveable { mutableStateOf("") }
    var confirmStop by remember { mutableStateOf<App?>(null) }
    var confirmUpdateAll by remember { mutableStateOf(false) }

    val list = apps
    val updates = list?.count { it.hasUpdate } ?: 0
    val running = list?.count { it.isRunning } ?: 0

    ScreenScaffold(
        title = "Apps",
        subtitle = list?.let { "$running of ${it.size} running" },
        actions = {
            if (updates > 0) TextButton(onClick = { confirmUpdateAll = true }) { Text("Update all") }
        },
        fab = {
            ExtendedFloatingActionButton(
                onClick = { chrome.nav.push(Route.Catalog) },
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("Install app") },
            )
        },
    ) {
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
            LazyColumn(contentPadding = ListPadding, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                if (list == null) {
                    item { LoadingBox() }
                    return@LazyColumn
                }
                if (list.isEmpty()) {
                    item { EmptyState(Icons.Outlined.Apps, "No apps installed yet", "Tap Install app to browse the TrueNAS catalog.") }
                    return@LazyColumn
                }
                item {
                    val a = auto
                    ItemRow(
                        title = "Automatic updates",
                        subtitle = when {
                            a == null -> "Checking…"
                            a.active -> a.summary()
                            else -> "Off · tap to set up"
                        },
                        onClick = { chrome.nav.push(Route.AutoUpdate) },
                        leading = {
                            Icon(
                                Icons.Outlined.Autorenew,
                                contentDescription = null,
                                tint = if (a?.active == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(end = 8.dp)) },
                    )
                }
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AppFilter.entries.forEach { f ->
                            val count = when (f) {
                                AppFilter.ALL -> list.size
                                AppFilter.RUNNING -> running
                                AppFilter.STOPPED -> list.count { it.isStopped }
                                AppFilter.UPDATES -> updates
                            }
                            if (f != AppFilter.UPDATES || count > 0) {
                                FilterChip(
                                    selected = filter == f,
                                    onClick = { filter = f },
                                    label = { Text("${f.label} $count") },
                                )
                            }
                        }
                    }
                }
                if (list.size > 10) {
                    item {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("Search apps") },
                            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                            singleLine = true,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 4.dp),
                        )
                    }
                }
                val shown = list.filter { app ->
                    val matchesFilter = when (filter) {
                        AppFilter.ALL -> true
                        AppFilter.RUNNING -> app.isRunning
                        AppFilter.STOPPED -> app.isStopped
                        AppFilter.UPDATES -> app.hasUpdate
                    }
                    matchesFilter && (query.isBlank() || app.title.contains(query.trim(), true) || app.name.contains(query.trim(), true))
                }
                if (shown.isEmpty()) item { EmptyState(Icons.Outlined.Apps, "No apps here") }
                items(shown, key = { it.name }) { app ->
                    AppRow(
                        app = app,
                        stat = stats[app.name],
                        busyLabel = busy["app:${app.name}"],
                        onClick = { chrome.nav.push(Route.AppDetail(app.name)) },
                        onOpen = AppLinks.resolve(app.portals, links[app.name], server?.endpoint?.bareHost).firstOrNull()
                            ?.takeIf { app.isRunning }
                            ?.let { link -> { openLink(link.url) } },
                        onStart = { vm.appAction(app, AppAction.START) },
                        onStop = { confirmStop = app },
                    )
                }
            }
        }
    }

    confirmStop?.let { app ->
        ConfirmDialog(
            title = "Stop ${app.title}?",
            text = "Its containers will shut down until you start it again.",
            confirmLabel = "Stop",
            destructive = true,
            onConfirm = { vm.appAction(app, AppAction.STOP) },
            onDismiss = { confirmStop = null },
        )
    }
    if (confirmUpdateAll) {
        ConfirmDialog(
            title = "Update $updates apps?",
            text = "Each app is updated in turn and restarts when its update finishes. Catalog apps take a snapshot of their data first if you set that up.",
            confirmLabel = "Update all",
            onConfirm = vm::updateAllApps,
            onDismiss = { confirmUpdateAll = false },
        )
    }
}

@Composable
private fun AppRow(
    app: App,
    stat: AppStat?,
    busyLabel: String?,
    onClick: () -> Unit,
    onOpen: (() -> Unit)?,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onClick)
            .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app.iconUrl, app.title)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    app.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (app.hasUpdate) {
                    Spacer(Modifier.width(8.dp))
                    StatusChip("Update", AppColors.status.info)
                }
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    busyLabel ?: appStateLabel(app.state),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (busyLabel != null) MaterialTheme.colorScheme.primary else appStateColor(app),
                )
                val detail = when {
                    busyLabel != null -> null
                    app.isRunning && stat != null -> "CPU ${stat.cpuPercent}% · ${Format.bytes(stat.memory)}"
                    else -> app.humanVersion.takeIf { it.isNotBlank() }?.let { "v$it" }
                }
                if (detail != null) {
                    Text(
                        "  ·  $detail",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (onOpen != null && busyLabel == null) {
            IconButton(onClick = onOpen) {
                Icon(Icons.Outlined.OpenInBrowser, contentDescription = "Open ${app.title}", tint = MaterialTheme.colorScheme.primary)
            }
        }
        when {
            busyLabel != null -> SmallSpinner(Modifier.padding(15.dp))
            app.isRunning || app.state == "DEPLOYING" -> IconButton(onClick = onStop) {
                Icon(Icons.Outlined.Stop, contentDescription = "Stop ${app.title}")
            }
            else -> IconButton(onClick = onStart) {
                Icon(Icons.Outlined.PlayArrow, contentDescription = "Start ${app.title}", tint = AppColors.status.good, modifier = Modifier.size(26.dp))
            }
        }
    }
}
