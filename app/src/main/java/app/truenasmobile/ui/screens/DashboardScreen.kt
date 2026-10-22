package app.truenasmobile.ui.screens

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeveloperBoard
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.truenasmobile.api.AlertLevel
import app.truenasmobile.api.App
import app.truenasmobile.api.AppStat
import app.truenasmobile.api.Pool
import app.truenasmobile.api.Realtime
import app.truenasmobile.api.SystemInfo
import app.truenasmobile.ui.Format
import app.truenasmobile.ui.ListPadding
import app.truenasmobile.ui.LocalChrome
import app.truenasmobile.ui.Route
import app.truenasmobile.ui.ScreenScaffold
import app.truenasmobile.ui.Tab
import app.truenasmobile.ui.components.AppIcon
import app.truenasmobile.ui.components.CardTitle
import app.truenasmobile.ui.components.DualSparkline
import app.truenasmobile.ui.components.LegendDot
import app.truenasmobile.ui.components.SectionCard
import app.truenasmobile.ui.components.SegmentBar
import app.truenasmobile.ui.components.Sparkline
import app.truenasmobile.ui.components.StatusChip
import app.truenasmobile.ui.components.UsageBar
import app.truenasmobile.ui.theme.AppColors

@Composable
fun DashboardScreen() {
    val chrome = LocalChrome.current
    val vm = chrome.vm
    val nav = chrome.nav
    val active by vm.active.collectAsStateWithLifecycle()
    val system by vm.system.collectAsStateWithLifecycle()
    val realtime by vm.realtime.collectAsStateWithLifecycle()
    val cpuHistory by vm.cpuHistory.collectAsStateWithLifecycle()
    val rxHistory by vm.rxHistory.collectAsStateWithLifecycle()
    val txHistory by vm.txHistory.collectAsStateWithLifecycle()
    val pools by vm.pools.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val stats by vm.appStats.collectAsStateWithLifecycle()
    val alerts by vm.alerts.collectAsStateWithLifecycle()
    val rebootReasons by vm.rebootReasons.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()

    ScreenScaffold(
        title = system?.hostname ?: active?.displayName ?: "Dashboard",
        subtitle = system?.let { versionLabel(it.version) },
    ) {
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 330.dp),
                contentPadding = ListPadding,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    HealthCard(
                        system = system,
                        pools = pools,
                        activeAlerts = alerts?.filter { !it.dismissed },
                        onAlerts = { nav.selectTab(Tab.Alerts) },
                    )
                }
                if (rebootReasons.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SectionCard(onClick = { nav.push(Route.Power) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.RestartAlt, contentDescription = null, tint = AppColors.status.warn)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Restart required", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        rebootReasons.joinToString("\n"),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
                item { CpuCard(realtime, cpuHistory, system) }
                item { MemoryCard(realtime, system) }
                item { NetworkCard(realtime, rxHistory, txHistory) }
                item { DiskIoCard(realtime) }
                items(pools ?: emptyList(), key = { "pool-${it.name}" }) { pool ->
                    PoolSummaryCard(pool) { nav.push(Route.PoolDetail(pool.name)) }
                }
                if (apps != null) {
                    item { AppsSummaryCard(apps ?: emptyList(), stats) { nav.selectTab(Tab.Apps) } }
                }
            }
        }
    }
}

@Composable
private fun HealthCard(
    system: SystemInfo?,
    pools: List<Pool>?,
    activeAlerts: List<app.truenasmobile.api.Alert>?,
    onAlerts: () -> Unit,
) {
    val s = AppColors.status
    val badPools = pools?.count { !it.healthy || it.status != "ONLINE" } ?: 0
    val critical = activeAlerts?.count { it.level.rank >= AlertLevel.CRITICAL.rank } ?: 0
    val warnings = activeAlerts?.count { it.level.rank in AlertLevel.WARNING.rank until AlertLevel.CRITICAL.rank } ?: 0
    val (icon, color, headline) = when {
        pools == null && system == null -> Triple(Icons.Outlined.CheckCircle, s.idle, "Loading…")
        badPools > 0 -> Triple(Icons.Outlined.ErrorOutline, s.bad, if (badPools == 1) "A pool needs attention" else "$badPools pools need attention")
        critical > 0 -> Triple(Icons.Outlined.ErrorOutline, s.bad, if (critical == 1) "1 critical alert" else "$critical critical alerts")
        warnings > 0 -> Triple(Icons.Outlined.WarningAmber, s.warn, if (warnings == 1) "1 warning" else "$warnings warnings")
        else -> Triple(Icons.Outlined.CheckCircle, s.good, "Everything looks healthy")
    }
    SectionCard(Modifier.fillMaxWidth(), onClick = if ((activeAlerts?.size ?: 0) > 0) onAlerts else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(36.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(headline, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (system != null) {
                    val uptime = system.uptimeSeconds.toLong() + (System.currentTimeMillis() - system.fetchedAt) / 1000
                    Text(
                        "Up ${Format.duration(uptime)} · ${Format.bytes(system.physmem)} RAM${if (system.ecc) " (ECC)" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val alertCount = activeAlerts?.size ?: 0
            // Mention alerts here only when the headline is about something else.
            if (alertCount > 0 && (badPools > 0 || critical + warnings == 0)) {
                StatusChip(if (alertCount == 1) "1 alert" else "$alertCount alerts", if (critical > 0) s.bad else s.info)
            }
        }
    }
}

@Composable
private fun CpuCard(rt: Realtime?, history: List<Float>, system: SystemInfo?) {
    val s = AppColors.status
    SectionCard(Modifier.fillMaxWidth()) {
        Column {
            CardTitle("CPU", Icons.Outlined.DeveloperBoard) {
                rt?.cpuTemp?.let { t ->
                    val c = when {
                        t >= 88 -> s.bad
                        t >= 75 -> s.warn
                        else -> s.good
                    }
                    StatusChip(Format.temp(t), c)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                rt?.let { Format.percent(it.cpuPercent) } ?: "—",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Sparkline(
                history,
                s.cpu,
                Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(vertical = 6.dp),
                maxValue = 100f,
            )
            if (system != null) {
                Text(
                    "${system.cpuModel.trim()} · ${system.cores} threads",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (system.loadavg.size >= 3) {
                    Text(
                        "Load ${system.loadavg.take(3).joinToString(" · ") { String.format(java.util.Locale.US, "%.2f", it) }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun MemoryCard(rt: Realtime?, system: SystemInfo?) {
    val s = AppColors.status
    val primary = MaterialTheme.colorScheme.primary
    SectionCard(Modifier.fillMaxWidth()) {
        Column {
            CardTitle("Memory", Icons.Outlined.Memory)
            Spacer(Modifier.height(6.dp))
            val total = rt?.memTotal?.takeIf { it > 0 } ?: system?.physmem ?: 0
            Text(
                if (rt != null) Format.bytes(rt.memServices + rt.arcSize) else "—",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "in use of ${Format.bytes(total)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            if (rt != null && total > 0) {
                SegmentBar(
                    listOf(
                        rt.memServices.toFloat() / total to primary,
                        rt.arcSize.toFloat() / total to s.arc,
                    ),
                )
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LegendDot(primary, "Services", Format.bytes(rt.memServices))
                    LegendDot(s.arc, "ZFS cache", Format.bytes(rt.arcSize) + (rt.arcHitPercent?.let { "  ·  $it% hits" } ?: ""))
                    LegendDot(MaterialTheme.colorScheme.surfaceContainerHighest, "Free", Format.bytes(rt.memFree))
                }
            } else {
                UsageBar(0f, primary)
            }
        }
    }
}

@Composable
private fun NetworkCard(rt: Realtime?, rx: List<Float>, tx: List<Float>) {
    val s = AppColors.status
    SectionCard(Modifier.fillMaxWidth()) {
        Column {
            CardTitle("Network", Icons.Outlined.Lan)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text("↓ ", color = s.rx, style = MaterialTheme.typography.titleLarge)
                Text(rt?.let { Format.bitsPerSec(it.rxTotal) } ?: "—", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(18.dp))
                Text("↑ ", color = s.tx, style = MaterialTheme.typography.titleLarge)
                Text(rt?.let { Format.bitsPerSec(it.txTotal) } ?: "—", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
            DualSparkline(
                rx,
                s.rx,
                tx,
                s.tx,
                Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(vertical = 6.dp),
            )
            val up = rt?.interfaces?.filter { it.up } ?: emptyList()
            Text(
                if (up.isEmpty()) "No active links" else up.joinToString(" · ") { i ->
                    if (i.speedMbps > 0) "${i.name} ${linkSpeed(i.speedMbps)}" else i.name
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun linkSpeed(mbps: Double): String =
    if (mbps >= 1000) "${Format.number(mbps / 1000).removeSuffix(".00")} Gb/s" else "${mbps.toInt()} Mb/s"

@Composable
private fun DiskIoCard(rt: Realtime?) {
    val s = AppColors.status
    SectionCard(Modifier.fillMaxWidth()) {
        Column {
            CardTitle("Disk activity", Icons.Outlined.SwapVert)
            Spacer(Modifier.height(10.dp))
            Row {
                Column(Modifier.weight(1f)) {
                    Text("Read", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(rt?.let { Format.bytesPerSec(it.diskReadBps) } ?: "—", style = MaterialTheme.typography.titleMedium, color = s.rx)
                }
                Column(Modifier.weight(1f)) {
                    Text("Write", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(rt?.let { Format.bytesPerSec(it.diskWriteBps) } ?: "—", style = MaterialTheme.typography.titleMedium, color = s.tx)
                }
                Column(Modifier.weight(1f)) {
                    Text("Busy", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(rt?.let { Format.percent(it.diskBusy) } ?: "—", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
fun PoolSummaryCard(pool: Pool, onClick: () -> Unit) {
    SectionCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Column {
            CardTitle("Pool", Icons.Outlined.Storage) {
                StatusChip(Format.title(pool.status), poolColor(pool))
            }
            Spacer(Modifier.height(4.dp))
            Text(pool.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            UsageBar(pool.usedFraction, usageColor(pool.usedFraction))
            Spacer(Modifier.height(6.dp))
            Text(
                "${Format.bytes(pool.allocated)} of ${Format.bytes(pool.size)} used · ${(pool.usedFraction * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ScanLine(pool)
        }
    }
}

@Composable
fun ScanLine(pool: Pool) {
    val scan = pool.scan ?: return
    val what = if (scan.function == "RESILVER") "Resilver" else "Scrub"
    val text = when {
        scan.running && scan.paused -> "$what paused at ${scan.percentage?.toInt() ?: 0}%"
        scan.running -> buildString {
            append("$what running · ${scan.percentage?.toInt() ?: 0}%")
            scan.secondsLeft?.takeIf { it > 0 }?.let { append(" · ${Format.duration(it)} left") }
        }
        scan.state == "FINISHED" ->
            "Last $what ${Format.ago(scan.endMillis).ifBlank { "finished" }} · ${scan.errors ?: 0} errors"
        scan.state == "CANCELED" -> "Last ${what.lowercase()} was cancelled"
        else -> return
    }
    Spacer(Modifier.height(4.dp))
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if ((scan.errors ?: 0) > 0) AppColors.status.bad else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun AppsSummaryCard(apps: List<App>, stats: Map<String, AppStat>, onClick: () -> Unit) {
    val running = apps.count { it.isRunning }
    val updates = apps.count { it.hasUpdate }
    SectionCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Column {
            CardTitle("Apps", Icons.Outlined.Apps) {
                if (updates > 0) StatusChip("$updates updates", AppColors.status.info)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "$running running · ${apps.size - running} stopped",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            val busiest = apps.filter { it.isRunning }
                .sortedByDescending { stats[it.name]?.cpuPercent ?: 0 }
                .take(4)
            if (busiest.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    busiest.forEach { app ->
                        val st = stats[app.name]
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppIcon(app.iconUrl, app.title, 26.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(app.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (st != null) {
                                Text(
                                    "${st.cpuPercent}% · ${Format.bytes(st.memory)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
