package app.truenasmobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FolderShared
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.truenasmobile.api.NfsShareFull
import app.truenasmobile.api.SmbShareFull
import app.truenasmobile.api.deleteNfsShare
import app.truenasmobile.api.deleteSmbShare
import app.truenasmobile.api.nfsSharesFull
import app.truenasmobile.api.saveNfsShare
import app.truenasmobile.api.saveSmbShare
import app.truenasmobile.api.smbPurposes
import app.truenasmobile.api.smbSharesFull
import app.truenasmobile.ui.ListPadding
import app.truenasmobile.ui.LocalChrome
import app.truenasmobile.ui.Route
import app.truenasmobile.ui.ScreenScaffold
import app.truenasmobile.ui.components.FolderPicker
import app.truenasmobile.ui.components.ItemRow
import app.truenasmobile.ui.components.LoadedBox
import app.truenasmobile.ui.components.SectionHeader
import app.truenasmobile.ui.components.SmallSpinner
import app.truenasmobile.ui.components.SwitchRow
import app.truenasmobile.ui.components.rememberLoad
import app.truenasmobile.ui.theme.AppColors

private data class AllShares(val smb: List<SmbShareFull>, val nfs: List<NfsShareFull>)

@Composable
fun SharesScreen() {
    val chrome = LocalChrome.current
    val vm = chrome.vm
    val loaded = rememberLoad("shares") { AllShares(it.smbSharesFull(), it.nfsSharesFull()) }
    val services by vm.services.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    val smbRunning = services?.firstOrNull { it.service == "cifs" }?.running
    val nfsRunning = services?.firstOrNull { it.service == "nfs" }?.running

    ScreenScaffold(
        title = "Shares",
        showBack = true,
        fab = {
            ExtendedFloatingActionButton(
                onClick = { adding = true },
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("Add share") },
            )
        },
    ) {
        LoadedBox(loaded) { shares ->
            LazyColumn(contentPadding = ListPadding, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                item { ServiceHint("SMB · Windows, Mac, phones", smbRunning, "cifs") }
                if (shares.smb.isEmpty()) item { EmptyHint("No SMB shares yet") }
                items(shares.smb, key = { "smb-${it.id}" }) { s ->
                    ItemRow(
                        title = s.name,
                        subtitle = listOfNotNull(s.path, s.comment.ifBlank { null }, if (s.readOnly) "read-only" else null).joinToString(" · "),
                        onClick = { chrome.nav.push(Route.ShareEdit("smb", s.id)) },
                        trailing = {
                            if (busy.containsKey("smb:${s.id}")) SmallSpinner(Modifier.padding(12.dp))
                            else Switch(checked = s.enabled, onCheckedChange = { on ->
                                vm.act("smb:${s.id}", "Saving", null, after = loaded.reload) { a, _ ->
                                    a.saveSmbShare(s.id, s.name, s.path, s.comment, on, s.readOnly, null)
                                }
                            })
                        },
                    )
                }
                item { ServiceHint("NFS · Linux and other servers", nfsRunning, "nfs") }
                if (shares.nfs.isEmpty()) item { EmptyHint("No NFS shares yet") }
                items(shares.nfs, key = { "nfs-${it.id}" }) { s ->
                    ItemRow(
                        title = s.path.substringAfterLast('/').ifBlank { s.path },
                        subtitle = listOfNotNull(
                            s.path,
                            s.comment.ifBlank { null },
                            (s.networks + s.hosts).takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "everyone",
                            if (s.readOnly) "read-only" else null,
                        ).joinToString(" · "),
                        onClick = { chrome.nav.push(Route.ShareEdit("nfs", s.id)) },
                        trailing = {
                            if (busy.containsKey("nfs:${s.id}")) SmallSpinner(Modifier.padding(12.dp))
                            else Switch(checked = s.enabled, onCheckedChange = { on ->
                                vm.act("nfs:${s.id}", "Saving", null, after = loaded.reload) { a, _ ->
                                    a.saveNfsShare(s.id, s.path, s.comment, on, s.readOnly, s.networks, s.hosts, s.maprootUser)
                                }
                            })
                        },
                    )
                }
            }
        }
    }

    if (adding) {
        AlertDialog(
            onDismissRequest = { adding = false },
            icon = { Icon(Icons.Outlined.FolderShared, contentDescription = null) },
            title = { Text("What kind of share?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = {
                        adding = false
                        chrome.nav.push(Route.ShareEdit("smb", null))
                    }) { Text("SMB, for Windows, Mac and phones") }
                    TextButton(onClick = {
                        adding = false
                        chrome.nav.push(Route.ShareEdit("nfs", null))
                    }) { Text("NFS, for Linux and other servers") }
                }
            },
            confirmButton = { TextButton(onClick = { adding = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ServiceHint(title: String, running: Boolean?, service: String) {
    val vm = LocalChrome.current.vm
    val services by vm.services.collectAsStateWithLifecycle()
    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
        SectionHeader(title, Modifier.weight(1f))
        if (running == false) {
            val svc = services?.firstOrNull { it.service == service }
            TextButton(onClick = { svc?.let { vm.setServiceRunning(it, true) } }) { Text("Service is off · Turn on", color = AppColors.status.warn) }
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
}

/** Add or edit a share. [kind] is "smb" or "nfs"; [id] null adds one. */
@Composable
fun ShareEditScreen(kind: String, id: Int?) {
    val loaded = rememberLoad("share-edit", kind, id) { a ->
        Triple(
            if (kind == "smb" && id != null) a.smbSharesFull().firstOrNull { it.id == id } else null,
            if (kind == "nfs" && id != null) a.nfsSharesFull().firstOrNull { it.id == id } else null,
            if (kind == "smb") a.smbPurposes() else emptyList(),
        )
    }
    ScreenScaffold(title = if (id == null) "New ${kind.uppercase()} share" else "Edit ${kind.uppercase()} share", showBack = true) {
        LoadedBox(loaded) { (smb, nfs, purposes) ->
            if (kind == "smb") SmbForm(smb, purposes) else NfsForm(nfs)
        }
    }
}

@Composable
private fun SmbForm(existing: SmbShareFull?, purposes: List<Pair<String, String>>) {
    val chrome = LocalChrome.current
    val vm = chrome.vm
    var path by remember { mutableStateOf(existing?.path ?: "") }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var nameTouched by remember { mutableStateOf(existing != null) }
    var comment by remember { mutableStateOf(existing?.comment ?: "") }
    var readOnly by remember { mutableStateOf(existing?.readOnly ?: false) }
    var enabled by remember { mutableStateOf(existing?.enabled ?: true) }
    var purpose by remember { mutableStateOf(existing?.purpose ?: purposes.firstOrNull { it.first == "DEFAULT_SHARE" }?.first ?: purposes.firstOrNull()?.first) }
    var picking by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val badChars = "%<>*?|/\\+=;:\",[]"
    val nameOk = name.isNotBlank() && name.none { it in badChars } && name.length <= 80
    val valid = path.startsWith("/mnt/") && nameOk

    FormColumn {
        PathPickRow(path) { picking = true }
        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it
                nameTouched = true
            },
            label = { Text("Share name") },
            supportingText = { Text("What people see on the network.") },
            isError = name.isNotEmpty() && !nameOk,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(value = comment, onValueChange = { comment = it }, label = { Text("Description (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (purposes.size > 1) {
            Text("Purpose", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                purposes.forEach { (k, label) ->
                    FilterChip(selected = purpose == k, onClick = { purpose = k }, label = { Text(label.removeSuffix(" parameters")) })
                }
            }
        }
        SwitchRow("Read-only", "People can open files but not change them.", readOnly) { readOnly = it }
        SwitchRow("Enabled", null, enabled) { enabled = it }
        SaveRow(
            canSave = valid,
            onSave = {
                vm.act("share-save", "Saving", if (existing == null) "Share $name created" else "Share saved", after = {}) { a, _ ->
                    a.saveSmbShare(existing?.id, name.trim(), path, comment, enabled, readOnly, purpose)
                    chrome.nav.back()
                }
            },
            onDelete = if (existing != null) ({ confirmDelete = true }) else null,
        )
    }
    if (picking) {
        FolderPicker("Folder to share", path.ifBlank { null }, forApps = false, onPick = {
            path = it
            if (!nameTouched) name = it.trimEnd('/').substringAfterLast('/')
            picking = false
        }, onDismiss = { picking = false })
    }
    if (confirmDelete && existing != null) {
        DeleteShareDialog(existing.name, onDismiss = { confirmDelete = false }) {
            vm.act("share-save", "Deleting", "Share deleted", after = {}) { a, _ ->
                a.deleteSmbShare(existing.id)
                chrome.nav.back()
            }
        }
    }
}

@Composable
private fun NfsForm(existing: NfsShareFull?) {
    val chrome = LocalChrome.current
    val vm = chrome.vm
    var path by remember { mutableStateOf(existing?.path ?: "") }
    var comment by remember { mutableStateOf(existing?.comment ?: "") }
    var readOnly by remember { mutableStateOf(existing?.readOnly ?: false) }
    var enabled by remember { mutableStateOf(existing?.enabled ?: true) }
    var networks by remember { mutableStateOf(existing?.networks ?: emptyList()) }
    var hosts by remember { mutableStateOf(existing?.hosts ?: emptyList()) }
    var maproot by remember { mutableStateOf(existing?.maprootUser ?: "") }
    var newNetwork by remember { mutableStateOf("") }
    var newHost by remember { mutableStateOf("") }
    var picking by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    FormColumn {
        PathPickRow(path) { picking = true }
        OutlinedTextField(value = comment, onValueChange = { comment = it }, label = { Text("Description (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text("Who can connect", style = MaterialTheme.typography.titleSmall)
        Text(
            if (networks.isEmpty() && hosts.isEmpty()) "Everyone on your network. Add networks or hosts to limit it." else "Only these:",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ChipList(networks) { networks = networks - it }
        AddRow(newNetwork, { newNetwork = it }, "Network, e.g. 192.168.1.0/24") {
            if (newNetwork.isNotBlank()) networks = networks + newNetwork.trim()
            newNetwork = ""
        }
        ChipList(hosts) { hosts = hosts - it }
        AddRow(newHost, { newHost = it }, "Host name or IP") {
            if (newHost.isNotBlank()) hosts = hosts + newHost.trim()
            newHost = ""
        }
        OutlinedTextField(
            value = maproot, onValueChange = { maproot = it.trim() }, label = { Text("Map root to user (optional)") },
            supportingText = { Text("e.g. root, so remote root keeps full access.") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        SwitchRow("Read-only", null, readOnly) { readOnly = it }
        SwitchRow("Enabled", null, enabled) { enabled = it }
        SaveRow(
            canSave = path.startsWith("/mnt/"),
            onSave = {
                vm.act("share-save", "Saving", if (existing == null) "NFS share created" else "Share saved", after = {}) { a, _ ->
                    a.saveNfsShare(existing?.id, path, comment, enabled, readOnly, networks, hosts, maproot.ifBlank { null })
                    chrome.nav.back()
                }
            },
            onDelete = if (existing != null) ({ confirmDelete = true }) else null,
        )
    }
    if (picking) {
        FolderPicker("Folder to share", path.ifBlank { null }, forApps = false, onPick = {
            path = it
            picking = false
        }, onDismiss = { picking = false })
    }
    if (confirmDelete && existing != null) {
        DeleteShareDialog(existing.path, onDismiss = { confirmDelete = false }) {
            vm.act("share-save", "Deleting", "Share deleted", after = {}) { a, _ ->
                a.deleteNfsShare(existing.id)
                chrome.nav.back()
            }
        }
    }
}

@Composable
private fun FormColumn(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        content()
        Spacer(Modifier.height(48.dp))
    }
}

@Composable
private fun PathPickRow(path: String, onPick: () -> Unit) {
    OutlinedTextField(
        value = path,
        onValueChange = {},
        readOnly = true,
        label = { Text("Folder") },
        placeholder = { Text("Choose a dataset or folder") },
        trailingIcon = { TextButton(onClick = onPick) { Text(if (path.isBlank()) "Browse" else "Change") } },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ChipList(items: List<String>, onRemove: (String) -> Unit) {
    if (items.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { item ->
            FilterChip(
                selected = true,
                onClick = { onRemove(item) },
                label = { Text(item) },
                trailingIcon = { Icon(Icons.Outlined.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp)) },
            )
        }
    }
}

@Composable
private fun AddRow(value: String, onChange: (String) -> Unit, label: String, onAdd: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onAdd, enabled = value.isNotBlank()) { Icon(Icons.Outlined.Add, contentDescription = "Add") }
    }
}

@Composable
private fun SaveRow(canSave: Boolean, onSave: () -> Unit, onDelete: (() -> Unit)?) {
    val vm = LocalChrome.current.vm
    val busy by vm.busy.collectAsStateWithLifecycle()
    val working = busy.containsKey("share-save")
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        if (onDelete != null) {
            TextButton(onClick = onDelete, enabled = !working) {
                Icon(Icons.Outlined.Delete, contentDescription = null, tint = AppColors.status.bad, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Delete", color = AppColors.status.bad)
            }
        }
        Spacer(Modifier.weight(1f))
        if (working) SmallSpinner(Modifier.padding(end = 12.dp))
        androidx.compose.material3.Button(onClick = onSave, enabled = canSave && !working) { Text("Save") }
    }
}

@Composable
private fun DeleteShareDialog(name: String, onDismiss: () -> Unit, onDelete: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete share $name?") },
        text = { Text("Only the share is removed. The files stay where they are.") },
        confirmButton = {
            TextButton(onClick = {
                onDelete()
                onDismiss()
            }) { Text("Delete", color = AppColors.status.bad, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
