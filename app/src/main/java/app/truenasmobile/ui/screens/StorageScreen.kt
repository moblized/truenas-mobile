package app.truenasmobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.truenasmobile.api.Dataset
import app.truenasmobile.api.Disk
import app.truenasmobile.ui.Format
import app.truenasmobile.ui.ListPadding
import app.truenasmobile.ui.LocalChrome
import app.truenasmobile.ui.Route
import app.truenasmobile.ui.ScreenScaffold
import app.truenasmobile.ui.components.EmptyState
import app.truenasmobile.ui.components.LoadingBox
import app.truenasmobile.ui.components.StatusChip
import app.truenasmobile.ui.theme.AppColors

@Composable
fun StorageScreen() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var creating by remember { mutableStateOf(false) }
    ScreenScaffold(
        title = "Storage",
        fab = {
            if (tab == 1) {
                ExtendedFloatingActionButton(
                    onClick = { creating = true },
                    icon = { Icon(Icons.Outlined.CreateNewFolder, contentDescription = null) },
                    text = { Text("Add dataset") },
                )
            }
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            PrimaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                listOf("Pools", "Datasets", "Disks").forEachIndexed { i, label ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label) })
                }
            }
            Box(Modifier.weight(1f)) {
                when (tab) {
                    0 -> PoolsTab()
                    1 -> DatasetsTab()
                    else -> DisksTab()
                }
            }
        }
    }
    if (creating) CreateDatasetDialog(parent = null, onDismiss = { creating = false })
}

@Composable
private fun PoolsTab() {
    val chrome = LocalChrome.current
    val vm = chrome.vm
    val pools by vm.pools.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
        val list = pools
        LazyColumn(contentPadding = ListPadding, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
            when {
                list == null -> item { LoadingBox() }
                list.isEmpty() -> item { EmptyState(Icons.Outlined.Storage, "No pools", "Create a pool in the TrueNAS web UI.") }
                else -> {
                    item { Spacer(Modifier.height(8.dp)) }
                    items(list, key = { it.name }) { pool ->
                        PoolSummaryCard(pool) { chrome.nav.push(Route.PoolDetail(pool.name)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DatasetsTab() {
    val chrome = LocalChrome.current
    val vm = chrome.vm
    LaunchedEffect(Unit) { vm.loadDatasets() }
    val datasets by vm.datasets.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
        val all = datasets
        LazyColumn(contentPadding = ListPadding, modifier = Modifier.fillMaxSize()) {
            if (all == null) {
                item { LoadingBox() }
                return@LazyColumn
            }
            if (all.size > 8) {
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Filter datasets") },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                    )
                }
            }
            val shown = if (query.isBlank()) all else all.filter { it.id.contains(query.trim(), ignoreCase = true) }
            if (shown.isEmpty()) {
                item { EmptyState(Icons.Outlined.Folder, if (all.isEmpty()) "No datasets" else "Nothing matches \"$query\"") }
            }
            items(shown, key = { it.id }) { ds ->
                DatasetRow(ds, indent = query.isBlank()) { chrome.nav.push(Route.DatasetDetail(ds.id)) }
            }
        }
    }
}

@Composable
private fun DatasetRow(ds: Dataset, indent: Boolean, onClick: () -> Unit) {
    val status = AppColors.status
    val total = (ds.used ?: 0) + (ds.available ?: 0)
    val fraction = if (total > 0) (ds.used ?: 0).toFloat() / total else 0f
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(start = (8 + if (indent) ds.depth * 18 else 0).dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = when {
            ds.locked -> Icons.Outlined.Lock
            ds.type == "VOLUME" -> Icons.Outlined.ViewInAr
            ds.depth == 0 -> Icons.Outlined.Storage
            else -> Icons.Outlined.Folder
        }
        Icon(
            icon,
            contentDescription = null,
            tint = if (ds.locked) status.warn else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (indent) ds.shortName else ds.id,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (ds.depth == 0) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                buildString {
                    append("${Format.bytes(ds.used)} used · ${Format.bytes(ds.available)} free")
                    if (ds.type == "VOLUME") append(" · zvol")
                    if (ds.encrypted) append(if (ds.locked) " · locked" else " · encrypted")
                    ds.compressRatio?.takeIf { it != "1.00x" && it != "1.00" }?.let { append(" · $it") }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier
                    .fillMaxWidth(0.6f)
                    .height(3.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction.coerceIn(0f, 1f))
                        .height(3.dp)
                        .background(usageColor(fraction)),
                )
            }
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Details", tint = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun DisksTab() {
    val vm = LocalChrome.current.vm
    LaunchedEffect(Unit) { vm.loadDisks() }
    val disks by vm.disks.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
        val list = disks
        LazyColumn(contentPadding = ListPadding, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
            when {
                list == null -> item { LoadingBox() }
                list.isEmpty() -> item { EmptyState(Icons.Outlined.Album, "No disks found") }
                else -> {
                    item { Spacer(Modifier.height(8.dp)) }
                    items(list, key = { it.name }) { DiskRow(it) }
                }
            }
        }
    }
}

@Composable
private fun DiskRow(disk: Disk) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (disk.isSolidState) Icons.Outlined.SdStorage else Icons.Outlined.Album,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(disk.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                Text(
                    Format.bytes(disk.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                listOfNotNull(disk.model?.trim()?.takeIf { it.isNotEmpty() }, disk.serial.takeIf { it.isNotBlank() }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                disk.pool?.let { "Pool: $it" } ?: "Not in a pool",
                style = MaterialTheme.typography.labelMedium,
                color = if (disk.pool != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            )
        }
        disk.temperature?.let { t -> StatusChip(Format.temp(t), tempColor(t, disk.isSolidState)) }
    }
}
