package app.truenasmobile.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.truenasmobile.data.NotifyLevel
import app.truenasmobile.data.ThemeMode
import app.truenasmobile.ui.LocalChrome
import app.truenasmobile.ui.ScreenScaffold
import app.truenasmobile.ui.components.SectionCard
import app.truenasmobile.ui.components.SectionHeader
import app.truenasmobile.work.AlertWorker

@Composable
fun SettingsScreen() {
    val vm = LocalChrome.current.vm
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.updateSettings(settings.copy(notifyAlerts = granted))
    }

    ScreenScaffold(title = "Settings", showBack = true) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionHeader("Appearance")
            SectionCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Theme", style = MaterialTheme.typography.titleSmall)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        val modes = listOf(ThemeMode.SYSTEM to "System", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark")
                        modes.forEachIndexed { i, (mode, label) ->
                            SegmentedButton(
                                selected = settings.theme == mode,
                                onClick = { vm.updateSettings(settings.copy(theme = mode)) },
                                shape = SegmentedButtonDefaults.itemShape(index = i, count = modes.size),
                            ) { Text(label) }
                        }
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        SwitchRow(
                            title = "Colours from wallpaper",
                            subtitle = "Material You dynamic colour",
                            checked = settings.dynamicColor,
                        ) { vm.updateSettings(settings.copy(dynamicColor = it)) }
                    }
                    SwitchRow(
                        title = "Pure black",
                        subtitle = "True black backgrounds in dark mode, easier on OLED screens",
                        checked = settings.pureBlack,
                        enabled = settings.theme != ThemeMode.LIGHT,
                    ) { vm.updateSettings(settings.copy(pureBlack = it)) }
                }
            }

            SectionHeader("Notifications")
            SectionCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SwitchRow(
                        title = "Alert notifications",
                        subtitle = "Checks your server about every 15 minutes and notifies you of new alerts.",
                        checked = settings.notifyAlerts && AlertWorker.canNotify(context),
                    ) { on ->
                        if (on && Build.VERSION.SDK_INT >= 33 && !AlertWorker.canNotify(context)) {
                            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            vm.updateSettings(settings.copy(notifyAlerts = on))
                        }
                    }
                    if (settings.notifyAlerts) {
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            NotifyLevel.entries.forEachIndexed { i, level ->
                                SegmentedButton(
                                    selected = settings.notifyLevel == level,
                                    onClick = { vm.updateSettings(settings.copy(notifyLevel = level)) },
                                    shape = SegmentedButtonDefaults.itemShape(index = i, count = NotifyLevel.entries.size),
                                ) { Text(level.label) }
                            }
                        }
                    }
                    Text(
                        "Your phone needs to reach the server: on your home Wi-Fi, or away through a VPN such as Tailscale or WireGuard. Accounts that sign in with a two-factor code can't be checked in the background; use an API key for that.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SectionHeader("Privacy")
            SectionCard(Modifier.fillMaxWidth()) {
                Text(
                    "Server addresses and credentials are stored only on this phone, encrypted with a key from the Android Keystore, and are excluded from cloud backups. The app talks only to your server (and loads app icons from the TrueNAS catalog).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
            )
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
