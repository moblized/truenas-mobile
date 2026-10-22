package app.truenasmobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderSpecial
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.truenasmobile.api.DirEntry
import app.truenasmobile.data.Connector
import app.truenasmobile.ui.LocalChrome
import app.truenasmobile.ui.theme.AppColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Browse the server's pools, datasets and folders and pick one. You can also make a new
 * folder or dataset right here, so you don't have to leave the app to prepare storage.
 */
@Composable
fun FolderPicker(
    title: String,
    start: String?,
    forApps: Boolean,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val vm = LocalChrome.current.vm
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val initial = start?.takeIf { it.startsWith("/mnt/") && it.length > 5 }?.trimEnd('/') ?: ROOT
    var path by remember { mutableStateOf(initial) }
    var entries by remember { mutableStateOf<List<DirEntry>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    /** Paths known to be datasets (pool roots always are), so "New dataset" shows where it works. */
    var datasets by remember { mutableStateOf(setOf<String>()) }
    var creating by remember { mutableStateOf<Boolean?>(null) } // true = dataset, false = folder

    LaunchedEffect(path, reload) {
        entries = null
        error = null
        try {
            val list = vm.listDirs(path)
            entries = list
            datasets = datasets + list.filter { it.isDataset || path == ROOT }.map { it.path }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (path != ROOT && path == initial) {
                path = ROOT // the saved folder is gone; start from the top
            } else {
                error = Connector.friendly(e)
            }
        }
    }

    val atRoot = path == ROOT
    val inDataset = !atRoot && (path in datasets || path.count { it == '/' } == 2)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .navigationBarsPadding(),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(8.dp))
            // Breadcrumbs
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Crumb("Pools", current = atRoot) { path = ROOT }
                val parts = path.removePrefix(ROOT).trim('/').split('/').filter { it.isNotEmpty() }
                parts.forEachIndexed { i, part ->
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.outline)
                    val target = ROOT + "/" + parts.take(i + 1).joinToString("/")
                    Crumb(part, current = i == parts.lastIndex) { path = target }
                }
            }
            if (!atRoot) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AssistChip(
                        onClick = { creating = false },
                        label = { Text("New folder") },
                        leadingIcon = { Icon(Icons.Outlined.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                    if (inDataset) {
                        AssistChip(
                            onClick = { creating = true },
                            label = { Text("New dataset") },
                            leadingIcon = { Icon(Icons.Outlined.Storage, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        )
                    }
                }
            }

            Box(Modifier.weight(1f)) {
                val list = entries
                when {
                    error != null -> Column(Modifier.padding(24.dp)) {
                        Text(error ?: "", color = AppColors.status.bad, style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { reload++ }) { Text("Try again") }
                    }
                    list == null -> LoadingBox()
                    list.isEmpty() -> Text(
                        if (atRoot) "No pools found." else "No folders here. You can use this one, or make a new folder.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                    else -> LazyColumn(Modifier.fillMaxWidth()) {
                        items(list, key = { it.path }) { e ->
                            val isDataset = atRoot || e.isDataset
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { path = e.path }
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    when {
                                        atRoot -> Icons.Outlined.Storage
                                        isDataset -> Icons.Outlined.FolderSpecial
                                        else -> Icons.Outlined.Folder
                                    },
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.width(16.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(e.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        when {
                                            atRoot -> "Pool"
                                            isDataset -> "Dataset"
                                            else -> "Folder"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Open", tint = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(16.dp),
            ) {
                Text(
                    if (atRoot) "Open a pool, then the folder you want." else path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = { onPick(path) }, enabled = !atRoot) { Text("Use this folder") }
                }
            }
        }
    }

    creating?.let { dataset ->
        CreateDialog(
            dataset = dataset,
            parent = path,
            forApps = forApps,
            onCreate = { name, appsPreset ->
                scope.launch {
                    try {
                        if (dataset) {
                            vm.createDataset(path.removePrefix("$ROOT/") + "/" + name, appsPreset)
                        } else {
                            vm.mkdir("$path/$name")
                        }
                        creating = null
                        path = "$path/$name"
                        if (dataset) datasets = datasets + path
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        error = null
                        creating = null
                        vm.showMessage(Connector.friendly(e))
                    }
                }
            },
            onDismiss = { creating = null },
        )
    }
}

private const val ROOT = "/mnt"

@Composable
private fun Crumb(text: String, current: Boolean, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
        color = if (current) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = !current, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 8.dp),
    )
}

@Composable
private fun CreateDialog(
    dataset: Boolean,
    parent: String,
    forApps: Boolean,
    onCreate: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var appsPreset by remember { mutableStateOf(forApps) }
    var working by remember { mutableStateOf(false) }
    val valid = name.isNotBlank() && name.none { it == '/' || it == '@' } && !name.startsWith(".") &&
        (!dataset || name.all { it.isLetterOrDigit() || it in "-_.: " })
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (dataset) "New dataset" else "New folder") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("In $parent", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.trim() },
                    label = { Text("Name") },
                    singleLine = true,
                    isError = name.isNotEmpty() && !valid,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (dataset) {
                    Text(
                        "A dataset is a folder with its own snapshots and settings. It's the recommended way to give each app its own space.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = appsPreset, onCheckedChange = { appsPreset = it })
                        Text("Set up for apps (lets apps read and write)", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                working = true
                onCreate(name, appsPreset)
            }, enabled = valid && !working) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
