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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.truenasmobile.api.Pool
import app.truenasmobile.api.Vdev
import app.truenasmobile.ui.Format
import app.truenasmobile.ui.ListPadding
import app.truenasmobile.ui.LocalChrome
import app.truenasmobile.ui.ScreenScaffold
import app.truenasmobile.ui.components.CardTitle
import app.truenasmobile.ui.components.ConfirmDialog
import app.truenasmobile.ui.components.EmptyState
import app.truenasmobile.ui.components.KeyValue
import app.truenasmobile.ui.components.SectionCard
import app.truenasmobile.ui.components.SectionHeader
import app.truenasmobile.ui.components.SmallSpinner
import app.truenasmobile.ui.components.StatusChip
import app.truenasmobile.ui.components.UsageBar
import app.truenasmobile.ui.theme.AppColors

@Composable
fun PoolDetailScreen(name: String) {
    val vm = LocalChrome.current.vm
    val pools by vm.pools.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val pool = pools?.firstOrNull { it.name == name }
    var confirm by remember { mutableStateOf<String?>(null) }

    ScreenScaffold(title = name, subtitle = pool?.let { Format.title(it.status) }, showBack = true) {
        if (pool == null) {
            EmptyState(Icons.Outlined.Info, if (pools == null) "Loading…" else "Pool not found")
        } else {
            PoolDetailContent(pool, busy["pool:${pool.name}"], refreshing, onRefresh = vm::refresh) { action ->
                if (action == "PAUSE") vm.scrub(pool, action) else confirm = action
            }
        }
        val current = pool
        val action = confirm
        if (current != null && action == "START") {
            ConfirmDialog(
                title = "Scrub ${current.name}?",
                text = "A scrub reads every block in the pool to find and repair errors. It runs in the background and can take hours; the pool stays usable.",
                confirmLabel = "Start scrub",
                onConfirm = { vm.scrub(current, "START") },
                onDismiss = { confirm = null },
            )
        } else if (current != null && action == "STOP") {
            ConfirmDialog(
                title = "Stop the scrub?",
                text = "It will have to start over from the beginning next time.",
                confirmLabel = "Stop",
                destructive = true,
                onConfirm = { vm.scrub(current, "STOP") },
                onDismiss = { confirm = null },
            )
        }
    }
}

@Composable
private fun PoolDetailContent(
    pool: Pool,
    busyLabel: String?,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onAction: (String) -> Unit,
) {
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
            LazyColumn(contentPadding = ListPadding, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
                item { Spacer(Modifier.height(4.dp)) }
                item { OverviewCard(pool) }
                item {
                    ScrubCard(pool, busyLabel, onAction)
                }
                pool.topology.forEach { group ->
                    item(key = "h-${group.role}") { SectionHeader(group.role) }
                    items(group.vdevs) { vdev -> VdevCard(vdev) }
                }
            }
    }
}

@Composable
private fun OverviewCard(pool: Pool) {
    SectionCard(Modifier.fillMaxWidth()) {
        Column {
            CardTitle("Health", Icons.Outlined.VerifiedUser) {
                StatusChip(Format.title(pool.status), poolColor(pool))
            }
            pool.statusDetail?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = AppColors.status.warn)
            }
            Spacer(Modifier.height(14.dp))
            UsageBar(pool.usedFraction, usageColor(pool.usedFraction), height = 10.dp)
            Spacer(Modifier.height(10.dp))
            KeyValue("Used", "${Format.bytes(pool.allocated)} (${(pool.usedFraction * 100).toInt()}%)")
            KeyValue("Free", Format.bytes(pool.free))
            KeyValue("Size", Format.bytes(pool.size))
            pool.fragmentation?.let { KeyValue("Fragmentation", if (it.endsWith("%")) it else "$it%") }
            KeyValue("Disks", pool.diskCount.toString())
        }
    }
}

@Composable
private fun ScrubCard(pool: Pool, busyLabel: String?, onAction: (String) -> Unit) {
    val scan = pool.scan
    SectionCard(Modifier.fillMaxWidth()) {
        Column {
            CardTitle(if (scan?.function == "RESILVER") "Resilver" else "Scrub", Icons.Outlined.VerifiedUser) {
                if (busyLabel != null) SmallSpinner()
            }
            Spacer(Modifier.height(8.dp))
            if (scan?.running == true) {
                LinearProgressIndicator(
                    progress = { ((scan.percentage ?: 0.0) / 100).toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
            }
            ScanLine(pool)
            if (scan == null) {
                Text("No scrub has run on this pool yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            busyLabel?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(12.dp))
            val scrubbing = scan?.running == true && scan.function != "RESILVER"
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!scrubbing) {
                    FilledTonalButton(
                        onClick = { onAction("START") },
                        enabled = busyLabel == null && scan?.running != true,
                    ) {
                        Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text("Scrub now")
                    }
                } else {
                    if (scan?.paused == true) {
                        FilledTonalButton(onClick = { onAction("START") }, enabled = busyLabel == null) {
                            Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                            Text("Resume")
                        }
                    } else {
                        OutlinedButton(onClick = { onAction("PAUSE") }, enabled = busyLabel == null) {
                            Icon(Icons.Outlined.Pause, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                            Text("Pause")
                        }
                    }
                    OutlinedButton(onClick = { onAction("STOP") }, enabled = busyLabel == null) {
                        Icon(Icons.Outlined.Stop, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text("Stop")
                    }
                }
            }
        }
    }
}

@Composable
private fun VdevCard(vdev: Vdev) {
    SectionCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (vdev.children.isEmpty()) {
                DiskLine(vdev, single = true)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.AccountTree, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${Format.title(vdev.type)} · ${vdev.children.size} disks",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    StatusChip(Format.title(vdev.status), vdevColor(vdev.status))
                }
                vdev.children.forEach { DiskLine(it, single = false) }
            }
        }
    }
}

@Composable
private fun DiskLine(v: Vdev, single: Boolean) {
    val errorColor = if (v.errors > 0) AppColors.status.bad else MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = if (single) 0.dp else 8.dp)) {
        androidx.compose.foundation.layout.Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(vdevColor(v.status)),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                v.disk ?: v.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (single) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "Read ${v.readErrors} · Write ${v.writeErrors} · Checksum ${v.checksumErrors}",
                style = MaterialTheme.typography.bodySmall,
                color = errorColor,
                fontFamily = FontFamily.Default,
            )
        }
        Text(Format.title(v.status), style = MaterialTheme.typography.labelMedium, color = vdevColor(v.status))
    }
}
