package app.truenasmobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.truenasmobile.data.AuthMethod
import app.truenasmobile.data.Connector
import app.truenasmobile.data.ServerProfile
import app.truenasmobile.net.Endpoint
import app.truenasmobile.ui.AppViewModel
import app.truenasmobile.ui.ListPadding
import app.truenasmobile.ui.LocalChrome
import app.truenasmobile.ui.OtpDialog
import app.truenasmobile.ui.Route
import app.truenasmobile.ui.ScreenScaffold
import app.truenasmobile.ui.ServerTest
import app.truenasmobile.ui.TrustDialog
import app.truenasmobile.ui.components.ConfirmDialog
import app.truenasmobile.ui.theme.AppColors

// ---------------------------------------------------------------- Server list

@Composable
fun ServersScreen() {
    val chrome = LocalChrome.current
    val vm = chrome.vm
    val servers by vm.servers.collectAsStateWithLifecycle()
    val active by vm.active.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf<ServerProfile?>(null) }

    ScreenScaffold(
        title = "Servers",
        showBack = true,
        fab = {
            ExtendedFloatingActionButton(
                onClick = { chrome.nav.push(Route.ServerEdit(null)) },
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("Add server") },
            )
        },
    ) {
        LazyColumn(contentPadding = ListPadding, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
            item { Spacer(Modifier.height(8.dp)) }
            items(servers, key = { it.id }) { server ->
                val isActive = server.id == active?.id
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (isActive) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)
                        .clickable { vm.switchServer(server.id) }
                        .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (isActive) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                        contentDescription = if (isActive) "In use" else "Use this server",
                        tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(server.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            buildString {
                                append(server.endpoint?.webUrl ?: server.address)
                                append(if (server.auth == AuthMethod.API_KEY) " · API key" else " · ${server.username}")
                                if (server.certPin != null) append(" · trusted cert")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = { chrome.nav.push(Route.ServerEdit(server.id)) }) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Edit")
                    }
                    IconButton(onClick = { deleting = server }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Remove")
                    }
                }
            }
        }
    }

    deleting?.let { server ->
        ConfirmDialog(
            title = "Remove ${server.displayName}?",
            text = "The saved address and credentials are deleted from this phone. Nothing changes on the server.",
            confirmLabel = "Remove",
            destructive = true,
            onConfirm = { vm.deleteServer(server.id) },
            onDismiss = { deleting = null },
        )
    }
}

// ---------------------------------------------------------------- Add / edit server

@Composable
fun ServerEditScreen(vm: AppViewModel, existingId: String?, firstRun: Boolean) {
    val chrome = LocalChrome.current
    val servers by vm.servers.collectAsStateWithLifecycle()
    val existing = remember(existingId) { servers.firstOrNull { it.id == existingId } }
    val test by vm.serverTest.collectAsStateWithLifecycle()

    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var address by rememberSaveable { mutableStateOf(existing?.address ?: "") }
    var auth by rememberSaveable { mutableStateOf(existing?.auth ?: AuthMethod.API_KEY) }
    var username by rememberSaveable { mutableStateOf(existing?.username ?: "truenas_admin") }
    var secret by rememberSaveable { mutableStateOf("") }
    var showSecret by remember { mutableStateOf(false) }
    var certPin by rememberSaveable { mutableStateOf(existing?.certPin) }
    var draft by remember { mutableStateOf<ServerProfile?>(null) }
    var formError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) { onDispose { vm.cancelTest() } }

    val endpoint = Endpoint.parse(address)
    val insecureKey = endpoint != null && !endpoint.secure && auth == AuthMethod.API_KEY
    val keepsSecret = existing != null && existing.auth == auth && secret.isEmpty()

    fun start(pin: String?) {
        formError = null
        when {
            endpoint == null -> formError = "Enter the server's address, like 192.168.1.10"
            insecureKey -> formError = Connector.INSECURE_KEY_MESSAGE
            auth == AuthMethod.PASSWORD && username.isBlank() -> formError = "Enter your TrueNAS username"
            secret.isEmpty() && !keepsSecret ->
                formError = if (auth == AuthMethod.API_KEY) "Paste your API key" else "Enter your password"
            else -> {
                val d = vm.draftProfile(existing, name, address, auth, username, secret, pin)
                if (d == null) {
                    formError = "Enter the password or API key"
                } else {
                    draft = d
                    vm.testServer(d)
                }
            }
        }
    }

    fun saveDraft(d: ServerProfile) {
        vm.saveServer(d, makeActive = true)
        vm.cancelTest()
        if (!firstRun) chrome.nav.back()
    }

    LaunchedEffect(test) {
        val d = draft
        if (test is ServerTest.Success && d != null) saveDraft(d)
    }

    ScreenScaffold(
        title = when {
            firstRun -> "Connect to TrueNAS"
            existing == null -> "Add server"
            else -> "Edit ${existing.displayName}"
        },
        showBack = !firstRun,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (firstRun) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Dns, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Manage your TrueNAS SCALE server from your phone: pools, apps, alerts, services and more. Works with TrueNAS 25.04 and newer.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            OutlinedTextField(
                value = address,
                onValueChange = { address = it.trim() },
                label = { Text("Server address") },
                placeholder = { Text("192.168.1.10 or truenas.local") },
                supportingText = {
                    Text(
                        when {
                            address.isBlank() -> "Uses HTTPS unless you type http://. Add :port if you changed it."
                            endpoint == null -> "That doesn't look like an address"
                            else -> "Connects to ${endpoint.webUrl}"
                        },
                    )
                },
                isError = address.isNotBlank() && endpoint == null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name (optional)") },
                placeholder = { Text(endpoint?.bareHost ?: "Home NAS") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Sign in with", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = auth == AuthMethod.API_KEY,
                    onClick = { auth = AuthMethod.API_KEY },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text("API key") }
                SegmentedButton(
                    selected = auth == AuthMethod.PASSWORD,
                    onClick = { auth = AuthMethod.PASSWORD },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text("Password") }
            }

            OutlinedTextField(
                value = username,
                onValueChange = { username = it.trim() },
                label = { Text("Username") },
                supportingText = {
                    Text(
                        if (auth == AuthMethod.API_KEY) "The account the key belongs to (often truenas_admin or root)"
                        else "A TrueNAS account with admin rights",
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = secret,
                onValueChange = { secret = it },
                label = { Text(if (auth == AuthMethod.API_KEY) "API key" else "Password") },
                placeholder = { if (keepsSecret) Text("Unchanged") },
                leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { showSecret = !showSecret }) {
                        Icon(if (showSecret) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, contentDescription = if (showSecret) "Hide" else "Show")
                    }
                },
                visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
                supportingText = {
                    Text(
                        if (auth == AuthMethod.API_KEY) {
                            "In the TrueNAS web UI: account icon (top right) → API Keys → Add. Copy the key; it's shown only once."
                        } else {
                            "Two-factor codes are supported. Saved encrypted on this phone."
                        },
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )

            if (insecureKey) {
                Text(Connector.INSECURE_KEY_MESSAGE, style = MaterialTheme.typography.bodySmall, color = AppColors.status.bad)
            }

            val running = test is ServerTest.Running
            Button(
                onClick = { start(certPin) },
                enabled = !running,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                if (running) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(12.dp))
                    Text("Connecting…")
                } else {
                    Text(if (existing == null) "Connect" else "Save and reconnect")
                }
            }

            val error = formError ?: (test as? ServerTest.Failed)?.message
            if (error != null) {
                Text(error, style = MaterialTheme.typography.bodyMedium, color = AppColors.status.bad)
                val d = draft
                if (test is ServerTest.Failed && d != null) {
                    TextButton(onClick = { saveDraft(d) }) { Text("Save anyway (connect later)") }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    when (val t = test) {
        is ServerTest.Untrusted -> TrustDialog(
            info = t.info,
            onTrust = {
                certPin = t.info.fingerprint
                start(t.info.fingerprint)
            },
            onCancel = vm::cancelTest,
        )
        is ServerTest.NeedsOtp -> OtpDialog(t.error, onSubmit = vm::testOtp, onCancel = vm::cancelTest)
        else -> Unit
    }
}
