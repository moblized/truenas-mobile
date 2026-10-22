package app.truenasmobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.truenasmobile.api.ApiKey
import app.truenasmobile.api.LocalGroup
import app.truenasmobile.api.LocalUser
import app.truenasmobile.api.Schema
import app.truenasmobile.api.apiKeys
import app.truenasmobile.api.certificates
import app.truenasmobile.api.createApiKey
import app.truenasmobile.api.currentUsername
import app.truenasmobile.api.deleteApiKey
import app.truenasmobile.api.deleteGroup
import app.truenasmobile.api.deleteUser
import app.truenasmobile.api.localGroups
import app.truenasmobile.api.localUsers
import app.truenasmobile.api.saveGroup
import app.truenasmobile.api.saveUser
import app.truenasmobile.api.shellChoices
import app.truenasmobile.ui.Format
import app.truenasmobile.ui.ListPadding
import app.truenasmobile.ui.LocalChrome
import app.truenasmobile.ui.Route
import app.truenasmobile.ui.ScreenScaffold
import app.truenasmobile.ui.components.ConfirmDialog
import app.truenasmobile.ui.components.FolderPicker
import app.truenasmobile.ui.components.ItemRow
import app.truenasmobile.ui.components.LoadedBox
import app.truenasmobile.ui.components.StatusChip
import app.truenasmobile.ui.components.SwitchRow
import app.truenasmobile.ui.components.rememberLoad
import app.truenasmobile.ui.theme.AppColors

private const val ADMIN_GROUP = "builtin_administrators"

private data class Accounts(val users: List<LocalUser>, val groups: List<LocalGroup>)

// ====================================================================== users & groups

@Composable
fun UsersScreen() {
    val chrome = LocalChrome.current
    val loaded = rememberLoad("accounts") { Accounts(it.localUsers(), it.localGroups()) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showBuiltin by rememberSaveable { mutableStateOf(false) }
    var editingGroup by remember { mutableStateOf<LocalGroup?>(null) }
    var newGroup by remember { mutableStateOf(false) }

    ScreenScaffold(
        title = "Users and groups",
        showBack = true,
        fab = {
            ExtendedFloatingActionButton(
                onClick = { if (tab == 0) chrome.nav.push(Route.UserEdit(null)) else newGroup = true },
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text(if (tab == 0) "Add user" else "Add group") },
            )
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            PrimaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Users") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Groups") })
            }
            LoadedBox(loaded) { acc ->
                val adminGid = acc.groups.firstOrNull { it.name == ADMIN_GROUP }?.id
                LazyColumn(contentPadding = ListPadding, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                    item {
                        FilterChip(selected = showBuiltin, onClick = { showBuiltin = !showBuiltin }, label = { Text("Show built-in") }, modifier = Modifier.padding(top = 8.dp))
                    }
                    if (tab == 0) {
                        items(acc.users.filter { showBuiltin || !it.builtin || it.username == "root" || it.username == "truenas_admin" }, key = { "u${it.id}" }) { u ->
                            val admin = adminGid != null && adminGid in u.groupIds
                            ItemRow(
                                title = u.username,
                                subtitle = listOfNotNull(
                                    u.fullName.takeIf { it.isNotBlank() && it != u.username },
                                    "UID ${u.uid}",
                                    if (u.smb) "SMB" else null,
                                    if (u.twoFactor) "2FA" else null,
                                ).joinToString(" · "),
                                onClick = if (u.immutable) null else ({ chrome.nav.push(Route.UserEdit(u.id)) }),
                                leading = { Icon(if (admin) Icons.Outlined.AdminPanelSettings else Icons.Outlined.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                trailing = {
                                    when {
                                        u.locked -> StatusChip("Locked", AppColors.status.warn)
                                        admin -> StatusChip("Admin", AppColors.status.info)
                                        u.builtin -> StatusChip("Built-in", AppColors.status.idle)
                                    }
                                },
                            )
                        }
                    } else {
                        items(acc.groups.filter { showBuiltin || !it.builtin || it.name == ADMIN_GROUP }, key = { "g${it.id}" }) { g ->
                            val members = acc.users.filter { it.id in g.userIds }.map { it.username }
                            ItemRow(
                                title = g.name,
                                subtitle = listOfNotNull(
                                    "GID ${g.gid}",
                                    if (g.smb) "SMB" else null,
                                    members.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "no members",
                                ).joinToString(" · "),
                                onClick = if (g.builtin) null else ({ editingGroup = g }),
                                leading = { Icon(Icons.Outlined.Group, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                trailing = { if (g.builtin) StatusChip("Built-in", AppColors.status.idle) },
                            )
                        }
                    }
                }
            }
        }
    }

    val acc = loaded.value
    if (acc != null && (newGroup || editingGroup != null)) {
        GroupDialog(editingGroup, acc.users, onDone = {
            newGroup = false
            editingGroup = null
            loaded.reload()
        })
    }
}

@Composable
private fun GroupDialog(existing: LocalGroup?, users: List<LocalUser>, onDone: () -> Unit) {
    val vm = LocalChrome.current.vm
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var smb by remember { mutableStateOf(existing?.smb ?: true) }
    var members by remember { mutableStateOf(existing?.userIds?.toSet() ?: emptySet()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val valid = name.isNotBlank() && name.all { it.isLetterOrDigit() || it in "-_." }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(if (existing == null) "New group" else "Edit ${existing.name}") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(name, { name = it.trim() }, label = { Text("Name") }, singleLine = true, isError = name.isNotEmpty() && !valid, modifier = Modifier.fillMaxWidth())
                SwitchRow("Use for SMB permissions", null, smb) { smb = it }
                Text("Members", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    users.filter { !it.builtin || it.username == "root" || it.username == "truenas_admin" }.forEach { u ->
                        FilterChip(selected = u.id in members, onClick = { members = if (u.id in members) members - u.id else members + u.id }, label = { Text(u.username) })
                    }
                }
                if (existing != null) {
                    TextButton(onClick = { confirmDelete = true }) { Text("Delete group", color = AppColors.status.bad) }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                vm.act("group", "Saving", "Group saved", after = onDone) { a, _ -> a.saveGroup(existing?.id, name, smb, members.toList()) }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Cancel") } },
    )
    if (confirmDelete && existing != null) {
        ConfirmDialog(
            title = "Delete group ${existing.name}?",
            text = "Users in it are kept.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = { vm.act("group", "Deleting", "Group deleted", after = onDone) { a, _ -> a.deleteGroup(existing.id) } },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
fun UserEditScreen(id: Int?) {
    val loaded = rememberLoad("user-edit", id) { a ->
        val acc = Accounts(a.localUsers(), a.localGroups())
        Triple(acc, acc.users.firstOrNull { it.id == id }, a.shellChoices(emptyList()))
    }
    ScreenScaffold(title = if (id == null) "New user" else "Edit user", showBack = true) {
        LoadedBox(loaded) { (acc, user, shells) -> UserForm(acc, user, shells) }
    }
}

@Composable
private fun UserForm(acc: Accounts, existing: LocalUser?, shells: List<Pair<String, String>>) {
    val chrome = LocalChrome.current
    val vm = chrome.vm
    val busy by vm.busy.collectAsStateWithLifecycle()
    val adminGroup = acc.groups.firstOrNull { it.name == ADMIN_GROUP }
    var username by remember { mutableStateOf(existing?.username ?: "") }
    var fullName by remember { mutableStateOf(existing?.fullName ?: "") }
    var email by remember { mutableStateOf(existing?.email ?: "") }
    var password by remember { mutableStateOf("") }
    var reveal by remember { mutableStateOf(false) }
    var groups by remember { mutableStateOf(existing?.groupIds?.toSet() ?: emptySet()) }
    var smb by remember { mutableStateOf(existing?.smb ?: true) }
    var shell by remember { mutableStateOf(existing?.shell ?: shells.firstOrNull { it.first.endsWith("zsh") }?.first ?: shells.firstOrNull()?.first ?: "") }
    var home by remember { mutableStateOf(existing?.home?.takeIf { it != "/var/empty" } ?: "") }
    var locked by remember { mutableStateOf(existing?.locked ?: false) }
    var sshKey by remember { mutableStateOf(existing?.sshKey ?: "") }
    var pickHome by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val working = busy.containsKey("user")
    val creating = existing == null
    val usernameOk = username.isNotBlank() && username.length <= 32 && username.first().let { it.isLetter() || it == '_' } &&
        username.all { it.isLetterOrDigit() || it in "_-." } && (!creating || acc.users.none { it.username == username })
    val passwordOk = !creating || password.isNotEmpty() || !smb
    val isAdmin = adminGroup != null && adminGroup.id in groups

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = username, onValueChange = { username = it.trim().lowercase() }, label = { Text("Username") },
            enabled = creating, isError = creating && username.isNotEmpty() && !usernameOk, singleLine = true,
            supportingText = if (creating) {
                { Text("Lowercase letters, numbers, - _ .") }
            } else {
                null
            },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(fullName, { fullName = it }, label = { Text("Full name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(email, { email = it.trim() }, label = { Text("Email (optional)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            value = password, onValueChange = { password = it },
            label = { Text(if (creating) "Password" else "New password") },
            placeholder = if (!creating) {
                { Text("Leave empty to keep it") }
            } else {
                null
            },
            isError = !passwordOk,
            supportingText = if (!passwordOk) {
                { Text("SMB users need a password") }
            } else {
                null
            },
            visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                Row {
                    IconButton(onClick = {
                        password = Schema.generatePassword(16)
                        reveal = true
                    }) { Icon(Icons.Outlined.Key, contentDescription = "Generate") }
                    IconButton(onClick = { reveal = !reveal }) { Icon(if (reveal) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, contentDescription = null) }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        if (adminGroup != null) {
            SwitchRow("Administrator", "Full access to the TrueNAS web UI and this app.", isAdmin) { on ->
                groups = if (on) groups + adminGroup.id else groups - adminGroup.id
            }
        }
        SwitchRow("SMB access", "Can sign in to SMB shares.", smb) { smb = it }
        Text("Groups", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            acc.groups.filter { (!it.builtin || it.name in setOf("apps", "docker")) && it.id != existing?.primaryGroupId && it.name != ADMIN_GROUP }.forEach { g ->
                FilterChip(selected = g.id in groups, onClick = { groups = if (g.id in groups) groups - g.id else groups + g.id }, label = { Text(g.name) })
            }
        }
        OutlinedTextField(
            value = home, onValueChange = { home = it.trim() }, label = { Text("Home folder (optional)") },
            trailingIcon = { TextButton(onClick = { pickHome = true }) { Text("Browse") } },
            supportingText = { Text("Pick a dataset; a folder named after the user is made inside it.") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        if (shells.isNotEmpty()) {
            Text("Shell", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                shells.forEach { (path, label) -> FilterChip(selected = shell == path, onClick = { shell = path }, label = { Text(label) }) }
            }
        }
        OutlinedTextField(
            sshKey, { sshKey = it }, label = { Text("SSH public key (optional)") }, minLines = 2,
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), modifier = Modifier.fillMaxWidth(),
        )
        if (!creating) SwitchRow("Locked", "Can't sign in while locked.", locked) { locked = it }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            if (!creating && existing?.builtin != true) {
                TextButton(onClick = { confirmDelete = true }, enabled = !working) { Text("Delete user", color = AppColors.status.bad) }
            }
            Spacer(Modifier.weight(1f))
            Button(enabled = !working && passwordOk && (!creating || usernameOk), onClick = {
                // TrueNAS makes a folder named after the user inside the chosen one.
                val createHome = home.isNotBlank() && creating
                val homePath = home.trimEnd('/')
                vm.act("user", "Saving", if (creating) "User $username created" else "User saved", after = {}) { a, _ ->
                    a.saveUser(
                        existing?.id, username, fullName, email, password.ifEmpty { null }, groups.toList(), smb,
                        shell.ifBlank { null }, homePath.ifBlank { null }, createHome, locked, sshKey,
                    )
                    chrome.nav.back()
                }
            }) { Text(if (working) "Saving…" else "Save") }
        }
        Spacer(Modifier.height(48.dp))
    }
    if (pickHome) {
        FolderPicker("Home folder location", home.ifBlank { null }, forApps = false, onPick = {
            home = it
            pickHome = false
        }, onDismiss = { pickHome = false })
    }
    if (confirmDelete && existing != null) {
        var deleteGroup by remember { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = AppColors.status.bad) },
            title = { Text("Delete ${existing.username}?") },
            text = {
                Column {
                    Text("Their files are kept.", style = MaterialTheme.typography.bodyMedium)
                    SwitchRow("Also delete their own group", null, deleteGroup) { deleteGroup = it }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.act("user", "Deleting", "User deleted", after = {}) { a, _ ->
                        a.deleteUser(existing.id, deleteGroup)
                        chrome.nav.back()
                    }
                }) { Text("Delete", color = AppColors.status.bad, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

// ====================================================================== API keys

@Composable
fun ApiKeysScreen() {
    val vm = LocalChrome.current.vm
    val loaded = rememberLoad("apikeys") { a -> a.apiKeys() to (a.currentUsername() ?: "") }
    var creating by remember { mutableStateOf(false) }
    var newKey by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<ApiKey?>(null) }
    ScreenScaffold(
        title = "API keys",
        showBack = true,
        fab = {
            ExtendedFloatingActionButton(onClick = { creating = true }, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("Add key") })
        },
    ) {
        LoadedBox(loaded) { (keys, _) ->
            LazyColumn(contentPadding = ListPadding, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                item {
                    Text(
                        "Keys let scripts, apps and this app sign in without a password. Each key acts as the user it belongs to.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(4.dp),
                    )
                }
                items(keys, key = { it.id }) { k ->
                    val expired = k.expiresMillis?.let { it < System.currentTimeMillis() } ?: false
                    ItemRow(
                        title = k.name,
                        subtitle = listOfNotNull(
                            k.username,
                            k.createdMillis?.let { "created ${Format.date(it)}" },
                            k.expiresMillis?.let { "expires ${Format.date(it)}" } ?: "never expires",
                        ).joinToString(" · "),
                        leading = { Icon(Icons.Outlined.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (k.revoked) StatusChip("Revoked", AppColors.status.bad) else if (expired) StatusChip("Expired", AppColors.status.warn)
                                IconButton(onClick = { deleting = k }) { Icon(Icons.Outlined.Delete, contentDescription = "Delete") }
                            }
                        },
                    )
                }
            }
        }
    }
    if (creating) {
        val me = loaded.value?.second ?: ""
        var name by remember { mutableStateOf("") }
        var user by remember { mutableStateOf(me) }
        var days by remember { mutableStateOf<Int?>(null) }
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text("New API key") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Name, e.g. Home Assistant") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(user, { user = it.trim() }, label = { Text("For user") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text("Expires", style = MaterialTheme.typography.bodyMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(null to "Never", 30 to "30 days", 90 to "90 days", 365 to "1 year").forEach { (d, l) ->
                            FilterChip(selected = days == d, onClick = { days = d }, label = { Text(l) })
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank() && user.isNotBlank(), onClick = {
                    creating = false
                    val expires = days?.let { System.currentTimeMillis() + it * 86_400_000L }
                    vm.act("apikey", "Creating", null, after = loaded.reload) { a, _ -> newKey = a.createApiKey(name.trim(), user, expires) }
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { creating = false }) { Text("Cancel") } },
        )
    }
    newKey?.let { key ->
        val clipboard = LocalClipboardManager.current
        AlertDialog(
            onDismissRequest = {},
            icon = { Icon(Icons.Outlined.Key, contentDescription = null) },
            title = { Text("Copy your key now") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("It won't be shown again.", style = MaterialTheme.typography.bodyMedium)
                    Text(key, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { clipboard.setText(AnnotatedString(key)) }) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("  Copy")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { newKey = null }) { Text("Done") } },
        )
    }
    deleting?.let { k ->
        ConfirmDialog(
            title = "Delete key ${k.name}?",
            text = "Anything using it stops working. If this app signs in with it, you'll need a new one.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = { vm.act("apikey", "Deleting", "Key deleted", after = loaded.reload) { a, _ -> a.deleteApiKey(k.id) } },
            onDismiss = { deleting = null },
        )
    }
}

// ====================================================================== certificates

@Composable
fun CertificatesScreen() {
    val loaded = rememberLoad("certs") { it.certificates() }
    ScreenScaffold(title = "Certificates", showBack = true) {
        LoadedBox(loaded) { certs ->
            LazyColumn(contentPadding = ListPadding, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                item { Spacer(Modifier.height(4.dp)) }
                items(certs, key = { it.id }) { c ->
                    ItemRow(
                        title = c.name,
                        subtitle = listOfNotNull(
                            c.commonName,
                            c.kind + if (c.acme) " (ACME)" else "",
                            c.untilText?.let { "valid until $it" },
                            c.sans.takeIf { it.isNotEmpty() }?.joinToString(", ")?.let { "for $it" },
                            c.keyInfo,
                        ).joinToString("\n"),
                        leading = { Icon(Icons.Outlined.Verified, contentDescription = null, tint = if (c.expired) AppColors.status.bad else MaterialTheme.colorScheme.primary) },
                        trailing = { if (c.expired) StatusChip("Expired", AppColors.status.bad) },
                    )
                }
                item {
                    Text(
                        "To add or renew certificates, use the web UI.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(4.dp),
                    )
                }
            }
        }
    }
}
