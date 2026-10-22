package app.truenasmobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.truenasmobile.api.DatasetDetail
import app.truenasmobile.api.createZvol
import app.truenasmobile.api.datasetDetail
import app.truenasmobile.api.deleteDataset
import app.truenasmobile.api.lockDataset
import app.truenasmobile.api.unlockDataset
import app.truenasmobile.api.updateDataset
import app.truenasmobile.ui.Format
import app.truenasmobile.ui.LocalChrome
import app.truenasmobile.ui.Route
import app.truenasmobile.ui.ScreenScaffold
import app.truenasmobile.ui.components.CardTitle
import app.truenasmobile.ui.components.KeyValue
import app.truenasmobile.ui.components.LoadedBox
import app.truenasmobile.ui.components.SectionCard
import app.truenasmobile.ui.components.SmallSpinner
import app.truenasmobile.ui.components.StatusChip
import app.truenasmobile.ui.components.UsageBar
import app.truenasmobile.ui.components.rememberLoad
import app.truenasmobile.ui.theme.AppColors

private const val GIB = 1024L * 1024 * 1024

@Composable
fun DatasetDetailScreen(id: String) {
    val chrome = LocalChrome.current
    val vm = chrome.vm
    val loaded = rememberLoad(id) { it.datasetDetail(id) }
    val busy by vm.busy.collectAsStateWithLifecycle()
    val all by vm.datasets.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf<Boolean?>(null) } // true = zvol
    var deleting by remember { mutableStateOf(false) }
    var unlocking by remember { mutableStateOf(false) }
    val d = loaded.value
    val children = all?.filter { it.id.startsWith("$id/") } ?: emptyList()
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.loadDatasets() }

    ScreenScaffold(
        title = id.substringAfterLast('/'),
        subtitle = id,
        showBack = true,
        actions = {
            IconButton(onClick = { chrome.nav.push(Route.Snapshots(id)) }) { Icon(Icons.Outlined.PhotoCamera, contentDescription = "Snapshots") }
            if (d != null) {
                IconButton(onClick = { editing = true }) { Icon(Icons.Outlined.Edit, contentDescription = "Edit") }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (!d.isVolume) {
                            DropdownMenuItem(text = { Text("Add dataset inside") }, leadingIcon = { Icon(Icons.Outlined.CreateNewFolder, null) }, onClick = {
                                menu = false
                                creating = false
                            })
                            DropdownMenuItem(text = { Text("Add zvol inside") }, leadingIcon = { Icon(Icons.Outlined.ViewInAr, null) }, onClick = {
                                menu = false
                                creating = true
                            })
                        }
                        if (id.contains('/')) {
                            DropdownMenuItem(
                                text = { Text("Delete", color = AppColors.status.bad) },
                                leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = AppColors.status.bad) },
                                onClick = {
                                    menu = false
                                    deleting = true
                                },
                            )
                        }
                    }
                }
            }
        },
    ) {
        LoadedBox(loaded) { ds ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                UsageCard(ds)
                if (ds.encrypted) {
                    val isRoot = ds.encryptionRoot == ds.id
                    val key = "ds:${ds.id}"
                    SectionCard(Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            CardTitle("Encryption", if (ds.locked) Icons.Outlined.Lock else Icons.Outlined.LockOpen) {
                                StatusChip(if (ds.locked) "Locked" else "Unlocked", if (ds.locked) AppColors.status.warn else AppColors.status.good)
                            }
                            Text(
                                if (isRoot) "Encrypted with a ${if (ds.keyFormat == "HEX") "key" else "passphrase"}."
                                else "Encryption is managed by ${ds.encryptionRoot ?: "its parent"}.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (isRoot) {
                                if (busy.containsKey(key)) {
                                    SmallSpinner()
                                } else if (ds.locked) {
                                    Button(onClick = { unlocking = true }) { Text("Unlock") }
                                } else if (ds.keyFormat != "HEX") {
                                    FilledTonalButton(onClick = {
                                        vm.act(key, "Locking", "${ds.id} locked", after = loaded.reload) { a, p -> a.lockDataset(ds.id, p) }
                                    }) { Text("Lock") }
                                }
                            }
                        }
                    }
                }
                SectionCard(Modifier.fillMaxWidth()) {
                    Column {
                        CardTitle("Settings", Icons.Outlined.Storage) {
                            TextButton(onClick = { editing = true }) { Text("Edit") }
                        }
                        Spacer(Modifier.height(4.dp))
                        ds.comments?.let { KeyValue("Comments", it) }
                        KeyValue("Compression", propText(ds, "compression"))
                        ds.value("compressratio")?.let { KeyValue("Ratio", it) }
                        if (!ds.isVolume) {
                            KeyValue("Quota", ds.bytes("quota")?.takeIf { it > 0 }?.let { Format.bytes(it) } ?: "None")
                            KeyValue("Access time", propText(ds, "atime"))
                            KeyValue("Record size", propText(ds, "recordsize"))
                        } else {
                            KeyValue("Size", Format.bytes(ds.bytes("volsize")))
                            KeyValue("Block size", propText(ds, "volblocksize"))
                        }
                        KeyValue("Sync", propText(ds, "sync"))
                        KeyValue("Dedup", propText(ds, "deduplication"))
                        KeyValue("Read-only", propText(ds, "readonly"))
                        ds.value("acltype")?.let { KeyValue("ACL type", it) }
                        ds.value("casesensitivity")?.let { KeyValue("Case", Format.title(it)) }
                    }
                }
                if (children.isNotEmpty()) {
                    SectionCard(Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            CardTitle("Inside this dataset", Icons.Outlined.Storage)
                            children.filter { it.id.count { c -> c == '/' } == id.count { c -> c == '/' } + 1 }.forEach { c ->
                                TextButton(onClick = { chrome.nav.push(Route.DatasetDetail(c.id)) }) {
                                    Text("${c.shortName}  ·  ${Format.bytes(c.used)}")
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(48.dp))
            }
        }
    }

    if (editing && d != null) {
        EditDatasetDialog(d, onSave = { changes ->
            vm.act("ds:${d.id}", "Saving", "Saved", after = loaded.reload) { a, _ -> a.updateDataset(d.id, changes) }
            editing = false
        }, onDismiss = { editing = false })
    }
    creating?.let { zvol ->
        if (zvol) {
            CreateZvolDialog(parent = id, onCreate = { name, size, sparse ->
                vm.act("ds:new", "Creating", "Zvol $name created", after = { vm.loadDatasets(force = true) }) { a, _ -> a.createZvol("$id/$name", size, sparse) }
                creating = null
            }, onDismiss = { creating = null })
        } else {
            CreateDatasetDialog(parent = id, onDismiss = { creating = null })
        }
    }
    if (deleting && d != null) {
        DeleteDatasetDialog(d.id, hasChildren = children.isNotEmpty(), onDelete = { recursive ->
            vm.act("ds:${d.id}", "Deleting", "${d.id} deleted", after = { vm.loadDatasets(force = true) }) { a, _ ->
                a.deleteDataset(d.id, recursive)
                chrome.nav.back()
            }
        }, onDismiss = { deleting = false })
    }
    if (unlocking && d != null) {
        UnlockDialog(d, onUnlock = { secret ->
            vm.act("ds:${d.id}", "Unlocking", null, after = loaded.reload) { a, p ->
                val err = a.unlockDataset(d.id, secret, d.keyFormat == "HEX", p)
                vm.showMessage(err ?: "${d.id} unlocked")
            }
            unlocking = false
        }, onDismiss = { unlocking = false })
    }
}

private fun propText(d: DatasetDetail, key: String): String {
    val p = d.props[key] ?: return "—"
    val v = p.value ?: return "—"
    return if (p.inherited) "$v (inherited)" else v
}

@Composable
private fun UsageCard(d: DatasetDetail) {
    val used = d.bytes("used") ?: 0
    val avail = d.bytes("available") ?: 0
    val quota = d.bytes("quota")?.takeIf { it > 0 }
    val total = quota ?: (used + avail)
    val fraction = if (total > 0) used.toFloat() / total else 0f
    SectionCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Format.bytes(used), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                Text("used · ${Format.bytes(avail)} free", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            UsageBar(fraction, usageColor(fraction))
            Text(
                listOfNotNull(
                    if (d.isVolume) "Zvol" else "Dataset",
                    quota?.let { "quota ${Format.bytes(it)}" },
                    d.value("mountpoint")?.takeIf { it.startsWith("/") },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            d.bytes("usedbysnapshots")?.takeIf { it > 0 }?.let {
                Text("Snapshots use ${Format.bytes(it)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Chip choices for a ZFS property; INHERIT means "same as the parent". */
@Composable
private fun PropChoice(label: String, options: List<Pair<String, String>>, value: String, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val all = if (options.none { it.first == value }) options + (value to value) else options
            all.forEach { (v, l) -> FilterChip(selected = value == v, onClick = { onChange(v) }, label = { Text(l) }) }
        }
    }
}

private fun current(d: DatasetDetail, key: String): String {
    val p = d.props[key] ?: return "INHERIT"
    return if (p.inherited) "INHERIT" else (p.value ?: "INHERIT").uppercase()
}

@Composable
private fun EditDatasetDialog(d: DatasetDetail, onSave: (Map<String, Any?>) -> Unit, onDismiss: () -> Unit) {
    var comments by remember { mutableStateOf(d.comments ?: "") }
    var quotaGb by remember { mutableStateOf(d.bytes("quota")?.takeIf { it > 0 }?.let { (it / GIB).toString() } ?: "") }
    var sizeGb by remember { mutableStateOf(d.bytes("volsize")?.let { (it / GIB).toString() } ?: "") }
    var compression by remember { mutableStateOf(current(d, "compression")) }
    var sync by remember { mutableStateOf(current(d, "sync")) }
    var atime by remember { mutableStateOf(current(d, "atime")) }
    var dedup by remember { mutableStateOf(current(d, "deduplication")) }
    var readonly by remember { mutableStateOf(current(d, "readonly")) }
    val quotaValid = quotaGb.isEmpty() || (quotaGb.toLongOrNull() ?: 0) >= 1
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit ${d.id.substringAfterLast('/')}") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                OutlinedTextField(value = comments, onValueChange = { comments = it }, label = { Text("Comments") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (d.isVolume) {
                    OutlinedTextField(
                        value = sizeGb, onValueChange = { sizeGb = it.filter(Char::isDigit) }, label = { Text("Size (GiB)") },
                        supportingText = { Text("Zvols can grow, not shrink.") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    OutlinedTextField(
                        value = quotaGb, onValueChange = { quotaGb = it.filter(Char::isDigit) }, label = { Text("Quota (GiB)") },
                        supportingText = { Text("Leave empty for no limit.") }, isError = !quotaValid,
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                    )
                }
                PropChoice("Compression", listOf("INHERIT" to "Inherit", "LZ4" to "LZ4", "ZSTD" to "ZSTD", "OFF" to "Off"), compression) { compression = it }
                PropChoice("Sync", listOf("INHERIT" to "Inherit", "STANDARD" to "Standard", "ALWAYS" to "Always", "DISABLED" to "Disabled"), sync) { sync = it }
                if (!d.isVolume) PropChoice("Access time", listOf("INHERIT" to "Inherit", "ON" to "On", "OFF" to "Off"), atime) { atime = it }
                PropChoice("Deduplication", listOf("INHERIT" to "Inherit", "OFF" to "Off", "ON" to "On"), dedup) { dedup = it }
                if (dedup == "ON" && current(d, "deduplication") != "ON") {
                    Text("Dedup uses a lot of memory. Only turn it on if you know you need it.", style = MaterialTheme.typography.bodySmall, color = AppColors.status.warn)
                }
                PropChoice("Read-only", listOf("INHERIT" to "Inherit", "ON" to "On", "OFF" to "Off"), readonly) { readonly = it }
            }
        },
        confirmButton = {
            TextButton(enabled = quotaValid, onClick = {
                val changes = LinkedHashMap<String, Any?>()
                if (comments != (d.comments ?: "")) changes["comments"] = comments.ifBlank { "INHERIT" }
                if (compression != current(d, "compression")) changes["compression"] = compression
                if (sync != current(d, "sync")) changes["sync"] = sync
                if (!d.isVolume && atime != current(d, "atime")) changes["atime"] = atime
                if (dedup != current(d, "deduplication")) changes["deduplication"] = dedup
                if (readonly != current(d, "readonly")) changes["readonly"] = readonly
                if (d.isVolume) {
                    val newSize = sizeGb.toLongOrNull()?.times(GIB)
                    if (newSize != null && newSize != d.bytes("volsize")) changes["volsize"] = newSize
                } else {
                    val old = d.bytes("quota")?.takeIf { it > 0 }
                    val new = quotaGb.toLongOrNull()?.times(GIB)
                    if (new != old) changes["quota"] = new ?: 0
                }
                if (changes.isEmpty()) onDismiss() else onSave(changes)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** New dataset under [parent] (or choose one when [parent] is null). */
@Composable
fun CreateDatasetDialog(parent: String?, onDismiss: () -> Unit) {
    val vm = LocalChrome.current.vm
    val datasets by vm.datasets.collectAsStateWithLifecycle()
    val parents = datasets?.filter { it.type != "VOLUME" && !it.locked }?.map { it.id } ?: emptyList()
    var where by remember { mutableStateOf(parent ?: parents.firstOrNull() ?: "") }
    var name by remember { mutableStateOf("") }
    var preset by remember { mutableStateOf("GENERIC") }
    var quotaGb by remember { mutableStateOf("") }
    var pickParent by remember { mutableStateOf(false) }
    val valid = where.isNotBlank() && name.isNotBlank() && name.all { it.isLetterOrDigit() || it in "-_.: " } && !name.startsWith(".")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New dataset") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (parent == null) {
                    OutlinedTextField(
                        value = where, onValueChange = {}, readOnly = true, label = { Text("Inside") },
                        trailingIcon = { TextButton(onClick = { pickParent = true }) { Text("Change") } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text("Inside $parent", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedTextField(value = name, onValueChange = { name = it.trim() }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("What it's for", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("GENERIC" to "General", "SMB" to "SMB share", "APPS" to "Apps", "MULTIPROTOCOL" to "SMB + NFS", "NFS" to "NFS share").forEach { (v, l) ->
                        FilterChip(selected = preset == v, onClick = { preset = v }, label = { Text(l) })
                    }
                }
                Text(
                    when (preset) {
                        "SMB" -> "Sets permissions that work well for Windows and Mac file sharing."
                        "APPS" -> "Lets apps read and write here."
                        "MULTIPROTOCOL" -> "For folders shared over both SMB and NFS."
                        "NFS" -> "Sets permissions that suit Linux NFS clients."
                        else -> "Plain dataset; set permissions later if needed."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = quotaGb, onValueChange = { quotaGb = it.filter(Char::isDigit) }, label = { Text("Quota in GiB (optional)") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                val full = "$where/$name"
                val extra = quotaGb.toLongOrNull()?.takeIf { it >= 1 }?.let { mapOf("quota" to it * GIB) } ?: emptyMap()
                vm.act("ds:new", "Creating", "Dataset $full created", after = { vm.loadDatasets(force = true) }) { a, _ -> a.createDataset(full, preset, extra) }
                onDismiss()
            }) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (pickParent) {
        AlertDialog(
            onDismissRequest = { pickParent = false },
            title = { Text("Put it inside") },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    parents.forEach { p ->
                        TextButton(onClick = {
                            where = p
                            pickParent = false
                        }) { Text(p, modifier = Modifier.padding(start = (p.count { it == '/' } * 12).dp)) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickParent = false }) { Text("Close") } },
        )
    }
}

@Composable
private fun CreateZvolDialog(parent: String, onCreate: (String, Long, Boolean) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var sizeGb by remember { mutableStateOf("") }
    var sparse by remember { mutableStateOf(true) }
    val size = sizeGb.toLongOrNull() ?: 0
    val valid = name.isNotBlank() && name.all { it.isLetterOrDigit() || it in "-_." } && size >= 1
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New zvol") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("A zvol is a virtual disk, used for VMs and iSCSI. Inside $parent.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(value = name, onValueChange = { name = it.trim() }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = sizeGb, onValueChange = { sizeGb = it.filter(Char::isDigit) }, label = { Text("Size (GiB)") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Thin provisioned", style = MaterialTheme.typography.bodyLarge)
                        Text("Only uses space as it's filled.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = sparse, onCheckedChange = { sparse = it })
                }
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onCreate(name, size * GIB, sparse) }) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun DeleteDatasetDialog(id: String, hasChildren: Boolean, onDelete: (Boolean) -> Unit, onDismiss: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    var recursive by remember { mutableStateOf(hasChildren) }
    val short = id.substringAfterLast('/')
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = AppColors.status.bad) },
        title = { Text("Delete $short?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("All files and snapshots in $id are destroyed. This can't be undone.", style = MaterialTheme.typography.bodyMedium)
                if (hasChildren) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = recursive, onCheckedChange = { recursive = it })
                        Text("Also delete the datasets inside it", style = MaterialTheme.typography.bodySmall)
                    }
                }
                OutlinedTextField(
                    value = typed, onValueChange = { typed = it.trim() },
                    label = { Text("Type $short to confirm") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = typed == short && (!hasChildren || recursive), onClick = {
                onDelete(recursive)
                onDismiss()
            }) { Text("Delete", color = AppColors.status.bad, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun UnlockDialog(d: DatasetDetail, onUnlock: (String) -> Unit, onDismiss: () -> Unit) {
    var secret by remember { mutableStateOf("") }
    val hex = d.keyFormat == "HEX"
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.LockOpen, contentDescription = null, modifier = Modifier.size(28.dp)) },
        title = { Text("Unlock ${d.id.substringAfterLast('/')}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Datasets inside it unlock too, and their shares and apps start again.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = secret, onValueChange = { secret = it }, label = { Text(if (hex) "Key (64 hex characters)" else "Passphrase") },
                    visualTransformation = PasswordVisualTransformation(), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(enabled = secret.isNotEmpty(), onClick = { onUnlock(secret) }) { Text("Unlock") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
