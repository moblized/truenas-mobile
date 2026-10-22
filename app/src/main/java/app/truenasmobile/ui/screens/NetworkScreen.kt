package app.truenasmobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.truenasmobile.api.NetConfig
import app.truenasmobile.api.NetInterface
import app.truenasmobile.api.StaticRoute
import app.truenasmobile.api.addStaticRoute
import app.truenasmobile.api.deleteStaticRoute
import app.truenasmobile.api.interfaces
import app.truenasmobile.api.netConfig
import app.truenasmobile.api.networkSummary
import app.truenasmobile.api.saveNetConfig
import app.truenasmobile.api.staticRoutes
import app.truenasmobile.ui.Format
import app.truenasmobile.ui.ListPadding
import app.truenasmobile.ui.LocalChrome
import app.truenasmobile.ui.ScreenScaffold
import app.truenasmobile.ui.components.CardTitle
import app.truenasmobile.ui.components.ConfirmDialog
import app.truenasmobile.ui.components.KeyValue
import app.truenasmobile.ui.components.LoadedBox
import app.truenasmobile.ui.components.SectionCard
import app.truenasmobile.ui.components.SectionHeader
import app.truenasmobile.ui.components.StatusChip
import app.truenasmobile.ui.components.rememberLoad
import app.truenasmobile.ui.theme.AppColors

private data class NetData(
    val interfaces: List<NetInterface>,
    val config: NetConfig,
    val routes: List<StaticRoute>,
    val summary: Map<String, List<String>>,
)

@Composable
fun NetworkScreen() {
    val vm = LocalChrome.current.vm
    val loaded = rememberLoad("network") { a -> NetData(a.interfaces(), a.netConfig(), a.staticRoutes(), a.networkSummary()) }
    val realtime by vm.realtime.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }
    var addingRoute by remember { mutableStateOf(false) }
    var deletingRoute by remember { mutableStateOf<StaticRoute?>(null) }

    ScreenScaffold(title = "Network", showBack = true) {
        LoadedBox(loaded) { n ->
            LazyColumn(contentPadding = ListPadding, verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                item { Spacer(Modifier.height(4.dp)) }
                item {
                    SectionCard(Modifier.fillMaxWidth()) {
                        Column {
                            CardTitle("Global settings", Icons.Outlined.Dns) { TextButton(onClick = { editing = true }) { Text("Edit") } }
                            KeyValue("Hostname", n.config.hostname.ifBlank { "—" })
                            KeyValue("Domain", n.config.domain.ifBlank { "—" })
                            KeyValue("DNS servers", n.config.nameservers.joinToString("\n").ifBlank { "From DHCP" })
                            KeyValue("Gateway", n.config.ipv4Gateway.ifBlank { "From DHCP" })
                            if (n.config.ipv6Gateway.isNotBlank()) KeyValue("IPv6 gateway", n.config.ipv6Gateway)
                        }
                    }
                }
                item { SectionHeader("Interfaces") }
                items(n.interfaces, key = { it.id }) { i ->
                    val live = realtime?.interfaces?.firstOrNull { it.name == i.name }
                    SectionCard(Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            CardTitle(i.name, Icons.Outlined.Lan) {
                                StatusChip(if (i.up) "Up" else "Down", if (i.up) AppColors.status.good else AppColors.status.idle)
                            }
                            val addrs = i.addresses.map { it.text }.ifEmpty { n.summary[i.name] ?: emptyList() }
                            if (addrs.isNotEmpty()) {
                                Text(addrs.joinToString("\n"), style = MaterialTheme.typography.bodyLarge, fontFamily = FontFamily.Monospace)
                            }
                            Text(
                                listOfNotNull(
                                    Format.title(i.type.replace("LINK_AGGREGATION", "LAGG")),
                                    if (i.dhcp) "DHCP" else "static",
                                    i.media,
                                    i.mtu?.let { "MTU $it" },
                                    i.mac,
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (i.members.isNotEmpty()) {
                                Text("Members: ${i.members.joinToString()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (i.description.isNotBlank()) Text(i.description, style = MaterialTheme.typography.bodySmall)
                            if (live != null && live.up) {
                                Text(
                                    "↓ ${Format.bitsPerSec(live.rxBytesPerSec)}   ↑ ${Format.bitsPerSec(live.txBytesPerSec)}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
                item {
                    Text(
                        "Changing interfaces can cut off the connection, so it's done in the web UI, which tests the change and rolls it back if you can't reach the server.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionHeader("Static routes", Modifier.weight(1f))
                        TextButton(onClick = { addingRoute = true }) {
                            Icon(Icons.Outlined.Add, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Add")
                        }
                    }
                }
                if (n.routes.isEmpty()) {
                    item { Text("None", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp)) }
                }
                items(n.routes, key = { "r${it.id}" }) { r ->
                    app.truenasmobile.ui.components.ItemRow(
                        title = "${r.destination} → ${r.gateway}",
                        subtitle = r.description.ifBlank { null },
                        leading = { Icon(Icons.Outlined.Directions, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        trailing = { IconButton(onClick = { deletingRoute = r }) { Icon(Icons.Outlined.Delete, contentDescription = "Delete") } },
                    )
                }
            }
        }
    }

    val current = loaded.value
    if (editing && current != null) {
        NetConfigDialog(current.config, onSave = { c ->
            vm.act("net", "Saving", "Network settings saved", after = loaded.reload) { a, _ -> a.saveNetConfig(c) }
            editing = false
        }, onDismiss = { editing = false })
    }
    if (addingRoute) {
        var dest by remember { mutableStateOf("") }
        var gw by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addingRoute = false },
            title = { Text("Add static route") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(dest, { dest = it.trim() }, label = { Text("Destination, e.g. 10.0.0.0/24") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(gw, { gw = it.trim() }, label = { Text("Gateway") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(desc, { desc = it }, label = { Text("Description (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(enabled = dest.isNotBlank() && gw.isNotBlank(), onClick = {
                    vm.act("net", "Adding", "Route added", after = loaded.reload) { a, _ -> a.addStaticRoute(dest, gw, desc) }
                    addingRoute = false
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { addingRoute = false }) { Text("Cancel") } },
        )
    }
    deletingRoute?.let { r ->
        ConfirmDialog(
            title = "Delete route?",
            text = "${r.destination} via ${r.gateway}",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = { vm.act("net", "Deleting", "Route deleted", after = loaded.reload) { a, _ -> a.deleteStaticRoute(r.id) } },
            onDismiss = { deletingRoute = null },
        )
    }
}

@Composable
private fun NetConfigDialog(c: NetConfig, onSave: (NetConfig) -> Unit, onDismiss: () -> Unit) {
    var hostname by remember { mutableStateOf(c.hostname) }
    var domain by remember { mutableStateOf(c.domain) }
    var dns1 by remember { mutableStateOf(c.nameservers.getOrElse(0) { "" }) }
    var dns2 by remember { mutableStateOf(c.nameservers.getOrElse(1) { "" }) }
    var dns3 by remember { mutableStateOf(c.nameservers.getOrElse(2) { "" }) }
    var gw by remember { mutableStateOf(c.ipv4Gateway) }
    val hostOk = hostname.isNotBlank() && hostname.all { it.isLetterOrDigit() || it == '-' }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Global network settings") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(hostname, { hostname = it.trim() }, label = { Text("Hostname") }, isError = !hostOk, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(domain, { domain = it.trim() }, label = { Text("Domain") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(dns1, { dns1 = it.trim() }, label = { Text("DNS server 1") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(dns2, { dns2 = it.trim() }, label = { Text("DNS server 2") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(dns3, { dns3 = it.trim() }, label = { Text("DNS server 3") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(gw, { gw = it.trim() }, label = { Text("Default gateway (IPv4)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth())
                Text("Leave DNS and gateway empty to use DHCP.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(enabled = hostOk, onClick = {
                onSave(c.copy(hostname = hostname, domain = domain, nameservers = listOf(dns1, dns2, dns3).filter { it.isNotBlank() }, ipv4Gateway = gw))
            }) { Text("Save", fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
