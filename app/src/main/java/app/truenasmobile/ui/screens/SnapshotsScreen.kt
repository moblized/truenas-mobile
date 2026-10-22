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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.truenasmobile.api.Snapshot
import app.truenasmobile.api.rollbackSnapshot
import app.truenasmobile.ui.theme.AppColors
import app.truenasmobile.ui.Format
import app.truenasmobile.ui.ListPadding
import app.truenasmobile.ui.LocalChrome
import app.truenasmobile.ui.ScreenScaffold
import app.truenasmobile.ui.components.ConfirmDialog
import app.truenasmobile.ui.components.EmptyState
import app.truenasmobile.ui.components.LoadingBox
import app.truenasmobile.ui.components.SmallSpinner
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SnapshotsScreen(dataset: String) {
    val vm = LocalChrome.current.vm
    LaunchedEffect(dataset) { vm.loadSnapshots(dataset) }
    val forDataset by vm.snapshotsDataset.collectAsStateWithLifecycle()
    val loaded by vm.snapshots.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val snapshots = if (forDataset == dataset) loaded else null
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Snapshot?>(null) }
    var rolling by remember { mutableStateOf<Snapshot?>(null) }
    val taking = busy["snap:$dataset"]

    ScreenScaffold(
        title = dataset.substringAfterLast('/'),
        subtitle = "Snapshots · $dataset",
        showBack = true,
        fab = {
            ExtendedFloatingActionButton(
                onClick = { if (taking == null) creating = true },
                icon = { if (taking != null) SmallSpinner() else Icon(Icons.Outlined.PhotoCamera, contentDescription = null) },
                text = { Text(if (taking != null) "Taking…" else "Snapshot now") },
            )
        },
    ) {
        LazyColumn(contentPadding = ListPadding, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
            when {
                snapshots == null -> item { LoadingBox() }
                snapshots.isEmpty() -> item {
                    EmptyState(Icons.Outlined.PhotoCamera, "No snapshots yet", "Snapshots are read-only copies of this dataset at a point in time. Take one before risky changes.")
                }
                else -> {
                    item {
                        Text(
                            "${snapshots.size} snapshots",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp),
                        )
                    }
                    items(snapshots, key = { it.id }) { snap ->
                        SnapshotRow(snap, deletingNow = busy.containsKey("snapdel:${snap.id}") || busy.containsKey("snaproll:${snap.id}"), onRollback = { rolling = snap }) { deleting = snap }
                    }
                }
            }
        }
    }

    if (creating) {
        CreateSnapshotDialog(
            onCreate = { name, recursive ->
                vm.createSnapshot(dataset, name, recursive)
                creating = false
            },
            onDismiss = { creating = false },
        )
    }
    rolling?.let { snap ->
        var destroyNewer by remember { mutableStateOf(false) }
        val newer = snapshots?.count { it.createTxg > snap.createTxg } ?: 0
        AlertDialog(
            onDismissRequest = { rolling = null },
            icon = { Icon(Icons.Outlined.History, contentDescription = null, tint = AppColors.status.warn) },
            title = { Text("Roll back to ${snap.name}?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "$dataset goes back to how it was at this snapshot. Changes made since then are lost.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (newer > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = destroyNewer, onCheckedChange = { destroyNewer = it })
                            Text("Delete the $newer newer snapshot${if (newer == 1) "" else "s"} too (required)", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = newer == 0 || destroyNewer, onClick = {
                    vm.act("snaproll:${snap.id}", "Rolling back", "Rolled back to ${snap.name}", after = { vm.loadSnapshots(dataset) }) { a, _ ->
                        a.rollbackSnapshot(snap.id, destroyNewer)
                    }
                    rolling = null
                }) { Text("Roll back", color = AppColors.status.bad) }
            },
            dismissButton = { TextButton(onClick = { rolling = null }) { Text("Cancel") } },
        )
    }
    deleting?.let { snap ->
        ConfirmDialog(
            title = "Delete snapshot?",
            text = "${snap.name} will be permanently removed. Files only kept by this snapshot will be gone.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = { vm.deleteSnapshot(snap) },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun SnapshotRow(snap: Snapshot, deletingNow: Boolean, onRollback: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(snap.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(
                    snap.createdMillis?.let { Format.dateTime(it) },
                    snap.used?.let { "${Format.bytes(it)} unique" },
                    snap.referenced?.let { "refers to ${Format.bytes(it)}" },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (deletingNow) {
            SmallSpinner(Modifier.padding(12.dp))
        } else {
            IconButton(onClick = onRollback) { Icon(Icons.Outlined.History, contentDescription = "Roll back to this snapshot") }
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, contentDescription = "Delete") }
        }
    }
}

@Composable
private fun CreateSnapshotDialog(onCreate: (String, Boolean) -> Unit, onDismiss: () -> Unit) {
    val default = remember { "manual-" + SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date()) }
    var name by remember { mutableStateOf(default) }
    var recursive by remember { mutableStateOf(false) }
    val valid = name.isNotBlank() && name.none { it.isWhitespace() || it == '@' || it == '/' }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New snapshot") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    isError = !valid,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = recursive, onCheckedChange = { recursive = it })
                    Spacer(Modifier.width(4.dp))
                    Text("Include child datasets", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(name.trim(), recursive) }, enabled = valid) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
