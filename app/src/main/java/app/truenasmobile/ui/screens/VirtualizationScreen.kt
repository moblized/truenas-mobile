package app.truenasmobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.truenasmobile.api.Guest
import app.truenasmobile.api.GuestAction
import app.truenasmobile.api.GuestKind
import app.truenasmobile.api.guestAction
import app.truenasmobile.api.guests
import app.truenasmobile.ui.Format
import app.truenasmobile.ui.ListPadding
import app.truenasmobile.ui.LocalChrome
import app.truenasmobile.ui.ScreenScaffold
import app.truenasmobile.ui.components.ConfirmDialog
import app.truenasmobile.ui.components.EmptyState
import app.truenasmobile.ui.components.ItemRow
import app.truenasmobile.ui.components.LoadedBox
import app.truenasmobile.ui.components.SmallSpinner
import app.truenasmobile.ui.components.StatusChip
import app.truenasmobile.ui.components.rememberLoad
import app.truenasmobile.ui.theme.AppColors

@Composable
fun VirtualizationScreen() {
    val vm = LocalChrome.current.vm
    val loaded = rememberLoad("guests") { it.guests() }
    val busy by vm.busy.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf<Pair<Guest, GuestAction>?>(null) }

    fun run(g: Guest, action: GuestAction) {
        val verb = when (action) {
            GuestAction.START -> "Starting"
            GuestAction.STOP -> "Shutting down"
            GuestAction.RESTART -> "Restarting"
            GuestAction.POWER_OFF -> "Powering off"
        }
        vm.act("guest:${g.id}", verb, null, after = loaded.reload) { a, p -> a.guestAction(g, action, p) }
    }

    ScreenScaffold(title = "Virtualization", subtitle = "Virtual machines and containers", showBack = true) {
        LoadedBox(loaded) { list ->
            LazyColumn(contentPadding = ListPadding, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                item { Spacer(Modifier.height(4.dp)) }
                if (list.isEmpty()) {
                    item { EmptyState(Icons.Outlined.Computer, "No virtual machines or containers", "Create them in the web UI; you can start and stop them here.") }
                }
                items(list, key = { "${it.kind}-${it.id}" }) { g ->
                    var menu by remember { mutableStateOf(false) }
                    val working = busy["guest:${g.id}"]
                    ItemRow(
                        title = g.name,
                        subtitle = listOfNotNull(
                            g.kind.label,
                            g.os,
                            g.cpus,
                            g.memoryBytes?.let { Format.bytes(it) },
                            if (g.autostart) "starts on boot" else null,
                            working,
                        ).joinToString(" · "),
                        leading = {
                            Icon(
                                if (g.kind == GuestKind.VM) Icons.Outlined.Computer else Icons.Outlined.ViewInAr,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StatusChip(
                                    Format.title(g.state),
                                    when (g.state) {
                                        "RUNNING" -> AppColors.status.good
                                        "ERROR", "ABORTED" -> AppColors.status.bad
                                        "STOPPED" -> AppColors.status.idle
                                        else -> AppColors.status.info
                                    },
                                )
                                when {
                                    working != null -> SmallSpinner(Modifier.padding(12.dp))
                                    !g.running -> IconButton(onClick = { run(g, GuestAction.START) }) {
                                        Icon(Icons.Outlined.PlayArrow, contentDescription = "Start", tint = AppColors.status.good)
                                    }
                                    else -> Box {
                                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "Actions") }
                                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                            DropdownMenuItem(text = { Text("Shut down") }, onClick = {
                                                menu = false
                                                confirm = g to GuestAction.STOP
                                            })
                                            DropdownMenuItem(text = { Text("Restart") }, onClick = {
                                                menu = false
                                                confirm = g to GuestAction.RESTART
                                            })
                                            DropdownMenuItem(text = { Text("Power off", color = AppColors.status.bad) }, onClick = {
                                                menu = false
                                                confirm = g to GuestAction.POWER_OFF
                                            })
                                        }
                                    }
                                }
                            }
                        },
                    )
                }
            }
        }
    }
    confirm?.let { (g, action) ->
        ConfirmDialog(
            title = when (action) {
                GuestAction.STOP -> "Shut down ${g.name}?"
                GuestAction.RESTART -> "Restart ${g.name}?"
                GuestAction.POWER_OFF -> "Power off ${g.name}?"
                GuestAction.START -> "Start ${g.name}?"
            },
            text = when (action) {
                GuestAction.POWER_OFF -> "Like pulling the plug: unsaved work inside it is lost."
                else -> "It's asked to shut down cleanly first."
            },
            confirmLabel = when (action) {
                GuestAction.STOP -> "Shut down"
                GuestAction.RESTART -> "Restart"
                GuestAction.POWER_OFF -> "Power off"
                GuestAction.START -> "Start"
            },
            destructive = action == GuestAction.POWER_OFF,
            onConfirm = { run(g, action) },
            onDismiss = { confirm = null },
        )
    }
}
