package app.truenasmobile.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.truenasmobile.api.App
import app.truenasmobile.api.AutoUpdate
import app.truenasmobile.ui.ConnState
import app.truenasmobile.ui.LocalChrome
import app.truenasmobile.ui.Route
import app.truenasmobile.ui.ScreenScaffold
import app.truenasmobile.ui.components.AppIcon
import app.truenasmobile.ui.components.CardTitle
import app.truenasmobile.ui.components.LoadingBox
import app.truenasmobile.ui.components.ScheduleEditor
import app.truenasmobile.ui.components.SectionCard
import app.truenasmobile.ui.components.SmallSpinner
import app.truenasmobile.ui.components.SwitchRow
import app.truenasmobile.ui.theme.AppColors

/** Settings for updating apps automatically on the server. */
@Composable
fun AutoUpdateScreen() {
    val vm = LocalChrome.current.vm
    val conn by vm.conn.collectAsStateWithLifecycle()
    val saved by vm.autoUpdate.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    LaunchedEffect(conn == ConnState.Connected) {
        if (conn == ConnState.Connected) vm.loadAutoUpdate()
    }
    ScreenScaffold(title = "Automatic updates", showBack = true) {
        val config = saved
        val installed = apps
        if (config == null || installed == null) LoadingBox() else AutoUpdateForm(config, installed)
    }
}

@Composable
private fun AutoUpdateForm(saved: AutoUpdate, installed: List<App>) {
    val chrome = LocalChrome.current
    val vm = chrome.vm
    val busy by vm.busy.collectAsStateWithLifecycle()
    var draft by remember(saved) { mutableStateOf(saved) }
    val saving = busy.containsKey("autoupdate")
    val running = busy.containsKey("autoupdate:run")
    val sorted = remember(installed) { installed.sortedBy { it.title.lowercase() } }
    val names = installed.map { it.name }
    val noneTicked = !draft.includeNew && draft.only.none { it in names }
    val canSave = draft != saved && !saving && !(draft.enabled && noneTicked) && (saved.id != null || draft.enabled)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard(Modifier.fillMaxWidth()) {
            Column {
                SwitchRow(
                    "Update apps automatically",
                    "Your server checks on a schedule and updates apps that have a new version. It runs on the server, so it works even when your phone is off.",
                    draft.enabled,
                ) { draft = draft.copy(enabled = it) }
            }
        }

        if (saved.edited) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Warning, contentDescription = null, tint = AppColors.status.warn, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "The command of this cron job was changed on the server. Saving here replaces it.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        if (draft.enabled) {
            SectionCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CardTitle("When", Icons.Outlined.Schedule)
                    ScheduleEditor(draft.schedule) { draft = draft.copy(schedule = it) }
                    Text(
                        "Apps restart while they update, so pick a time you're not using them.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SectionCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    CardTitle("Which apps", Icons.Outlined.Apps) {
                        val count = installed.count { draft.covers(it.name) }
                        Text("$count of ${installed.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    SwitchRow(
                        "Include apps installed later",
                        if (draft.includeNew) "New apps update automatically too. Untick any app you want to update yourself."
                        else "Only the apps ticked below update automatically.",
                        draft.includeNew,
                    ) { draft = draft.withIncludeNew(it, names) }
                    Spacer(Modifier.height(4.dp))
                    sorted.forEach { app ->
                        AppTick(app, draft) { on -> draft = draft.withApp(app.name, on) }
                    }
                    if (noneTicked) {
                        Text("Tick at least one app.", style = MaterialTheme.typography.bodySmall, color = AppColors.status.bad)
                    }
                }
            }

            SectionCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CardTitle("Options", Icons.Outlined.Tune)
                    SwitchRow(
                        "Update custom apps",
                        "Custom (Docker Compose) apps get the newest images their compose file asks for, then restart.",
                        draft.customImages,
                    ) { draft = draft.copy(customImages = it) }
                    SwitchRow(
                        "Snapshot app folders first",
                        "Takes a snapshot of the folders an app uses before updating it, so you can go back. These snapshots aren't deleted automatically.",
                        draft.snapshot,
                    ) { draft = draft.copy(snapshot = it) }
                }
            }
        }

        Button(
            onClick = {
                val done = when {
                    !draft.enabled -> "Automatic updates are off"
                    saved.id == null || !saved.enabled -> "Automatic updates are on: ${draft.schedule.describe().replaceFirstChar { it.lowercase() }}"
                    else -> "Saved"
                }
                vm.saveAutoUpdate(draft, done)
            },
            enabled = canSave,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (saving) {
                SmallSpinner()
                Spacer(Modifier.width(8.dp))
            }
            Text(if (saved.id == null) "Turn on automatic updates" else "Save")
        }

        if (saved.active) {
            OutlinedButton(onClick = vm::runAutoUpdateNow, enabled = !running && draft == saved, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("Update now")
            }
            TextButton(onClick = { chrome.nav.push(Route.Jobs) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("See update progress in Jobs")
            }
        }

        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.Autorenew, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "TrueNAS has no automatic app updates of its own, so this is saved on the server as a cron job named " +
                    "\"${AutoUpdate.DESCRIPTION}\" (System › Advanced Settings › Cron Jobs in the web UI). " +
                    "If the root account has an email address, TrueNAS emails you a list of what was updated.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun AppTick(app: App, draft: AutoUpdate, onChange: (Boolean) -> Unit) {
    val ticked = draft.covers(app.name)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onChange(!ticked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = ticked, onCheckedChange = onChange)
        AppIcon(app.iconUrl, app.title, 32.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(app.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val note = when {
                app.customApp && !draft.customImages -> "Custom app · turn on \"Update custom apps\""
                app.customApp -> "Custom app"
                app.hasUpdate -> "Update available"
                else -> null
            }
            if (note != null) {
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (app.hasUpdate && !app.customApp) AppColors.status.info else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
