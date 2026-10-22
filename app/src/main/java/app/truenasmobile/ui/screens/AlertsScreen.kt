package app.truenasmobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.truenasmobile.api.Alert
import app.truenasmobile.api.AlertLevel
import app.truenasmobile.ui.Format
import app.truenasmobile.ui.ListPadding
import app.truenasmobile.ui.LocalChrome
import app.truenasmobile.ui.ScreenScaffold
import app.truenasmobile.ui.components.ConfirmDialog
import app.truenasmobile.ui.components.EmptyState
import app.truenasmobile.ui.components.LoadingBox
import app.truenasmobile.ui.components.SmallSpinner

@Composable
fun AlertsScreen() {
    val vm = LocalChrome.current.vm
    val alerts by vm.alerts.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    var showDismissed by rememberSaveable { mutableStateOf(false) }
    var confirmAll by remember { mutableStateOf(false) }

    val all = alerts
    val active = all?.filter { !it.dismissed } ?: emptyList()
    val dismissed = all?.filter { it.dismissed } ?: emptyList()
    val shown = if (showDismissed) dismissed else active

    ScreenScaffold(
        title = "Alerts",
        subtitle = all?.let { if (active.isEmpty()) "All clear" else "${active.size} active" },
        actions = {
            if (!showDismissed && active.size > 1) {
                TextButton(onClick = { confirmAll = true }, enabled = !busy.containsKey("alerts:all")) { Text("Dismiss all") }
            }
        },
    ) {
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
            LazyColumn(contentPadding = ListPadding, verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                item {
                    SingleChoiceSegmentedButtonRow(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                    ) {
                        SegmentedButton(
                            selected = !showDismissed,
                            onClick = { showDismissed = false },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        ) { Text("Active (${active.size})") }
                        SegmentedButton(
                            selected = showDismissed,
                            onClick = { showDismissed = true },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        ) { Text("Dismissed (${dismissed.size})") }
                    }
                }
                when {
                    all == null -> item { LoadingBox() }
                    shown.isEmpty() -> item {
                        if (showDismissed) {
                            EmptyState(Icons.Outlined.Info, "Nothing dismissed")
                        } else {
                            EmptyState(Icons.Outlined.CheckCircle, "No active alerts", "Your server hasn't reported any problems.")
                        }
                    }
                    else -> items(shown, key = { it.uuid }) { alert ->
                        AlertRow(
                            alert = alert,
                            working = busy.containsKey("alert:${alert.uuid}"),
                            onToggle = { if (alert.dismissed) vm.restoreAlert(alert) else vm.dismissAlert(alert) },
                        )
                    }
                }
            }
        }
    }

    if (confirmAll) {
        ConfirmDialog(
            title = "Dismiss all alerts?",
            text = "They move to Dismissed. TrueNAS raises them again if the problem is still there later.",
            confirmLabel = "Dismiss all",
            onConfirm = vm::dismissAllAlerts,
            onDismiss = { confirmAll = false },
        )
    }
}

@Composable
private fun AlertRow(alert: Alert, working: Boolean, onToggle: () -> Unit) {
    val color = alertColor(alert.level)
    val icon = when {
        alert.level.rank >= AlertLevel.ERROR.rank -> Icons.Outlined.ErrorOutline
        alert.level.rank >= AlertLevel.WARNING.rank -> Icons.Outlined.WarningAmber
        else -> Icons.Outlined.Info
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Box(
            Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(color),
        )
        Column(Modifier.padding(start = 12.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(alert.level.label, style = MaterialTheme.typography.labelLarge, color = color)
                Spacer(Modifier.weight(1f))
                Text(Format.ago(alert.datetimeMillis), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(6.dp))
            Text(alert.text, style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                if (working) {
                    SmallSpinner(Modifier.padding(12.dp))
                } else {
                    TextButton(onClick = onToggle) { Text(if (alert.dismissed) "Restore" else "Dismiss") }
                }
            }
        }
    }
}
