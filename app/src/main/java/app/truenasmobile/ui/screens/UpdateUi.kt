package app.truenasmobile.ui.screens

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.truenasmobile.data.Settings
import app.truenasmobile.ui.components.SmallSpinner
import app.truenasmobile.ui.components.SwitchRow
import app.truenasmobile.ui.theme.AppColors
import app.truenasmobile.update.Release
import app.truenasmobile.update.UpdateStatus
import app.truenasmobile.update.Updater
import app.truenasmobile.work.AlertWorker

private fun Context.openInstallPermission() {
    runCatching { startActivity(Updater.installPermissionIntent(this)) }
}

/** Offers a new version of the app, then shows the download until Android takes over. */
@Composable
fun UpdateDialog() {
    val context = LocalContext.current
    val prompt by Updater.prompt.collectAsStateWithLifecycle()
    val status by Updater.status.collectAsStateWithLifecycle()
    val release = prompt ?: return
    var needPermission by remember(release) { mutableStateOf(false) }
    // Back from Android's "Install unknown apps" screen: carry on if it was allowed.
    LifecycleResumeEffect(needPermission) {
        if (needPermission && Updater.canInstall(context)) {
            needPermission = false
            Updater.update(context, release)
        }
        onPauseOrDispose { }
    }
    val s = status
    val downloading = s as? UpdateStatus.Downloading
    val working = downloading != null || s is UpdateStatus.Installing

    AlertDialog(
        onDismissRequest = { if (working) Updater.prompt.value = null else Updater.dismiss(context, release) },
        icon = { Icon(Icons.Outlined.SystemUpdate, contentDescription = null) },
        title = { Text(if (working) "Updating to ${release.version}" else "Update to ${release.version}?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when {
                    needPermission -> Text(
                        "To update itself, TrueNAS Mobile needs permission to install apps. On the next screen, turn on " +
                            "\"Allow from this source\", then come back.",
                    )
                    downloading != null -> {
                        Text(downloading.progress?.let { "Downloading… ${(it * 100).toInt()}%" } ?: "Downloading…")
                        val p = downloading.progress
                        if (p != null) LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                        else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    s is UpdateStatus.Installing -> Text(
                        "Installing. If Android asks, tap Update. The app closes while it updates; your servers and settings stay.",
                    )
                    else -> {
                        Text(
                            "You have version ${Updater.currentVersion}.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        ReleaseNotes(release)
                        if (s is UpdateStatus.Failed) Text(s.message, color = AppColors.status.bad, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            when {
                needPermission -> TextButton(onClick = { context.openInstallPermission() }) { Text("Open settings") }
                working -> TextButton(onClick = { Updater.prompt.value = null }) { Text("Hide") }
                else -> TextButton(onClick = {
                    if (Updater.canInstall(context)) Updater.update(context, release) else needPermission = true
                }) { Text(if (s is UpdateStatus.Failed) "Try again" else "Update") }
            }
        },
        dismissButton = {
            if (!working) TextButton(onClick = { Updater.dismiss(context, release) }) { Text("Later") }
        },
    )
}

@Composable
private fun ReleaseNotes(release: Release) {
    if (release.notes.isBlank()) return
    Column(
        Modifier
            .heightIn(max = 220.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text("What's new", style = MaterialTheme.typography.titleSmall)
        Text(release.notes, style = MaterialTheme.typography.bodyMedium)
    }
}

/** The "App updates" part of Settings. */
@Composable
fun AppUpdatesCard(settings: Settings, onChange: (Settings) -> Unit) {
    val context = LocalContext.current
    val status by Updater.status.collectAsStateWithLifecycle()
    var canInstall by remember { mutableStateOf(Updater.canInstall(context)) }
    LifecycleResumeEffect(Unit) {
        canInstall = Updater.canInstall(context)
        onPauseOrDispose { }
    }
    // Background installs that need a tap arrive as a notification, so ask for that permission too.
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val s = status

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("TrueNAS Mobile ${Updater.currentVersion}", style = MaterialTheme.typography.titleSmall)
                val (line, bad) = when (s) {
                    UpdateStatus.Idle -> "New versions come from GitHub Releases" to false
                    UpdateStatus.Checking -> "Checking for updates…" to false
                    UpdateStatus.UpToDate -> "You have the latest version" to false
                    is UpdateStatus.Available -> "Version ${s.release.version} is available" to false
                    is UpdateStatus.Downloading -> "Downloading ${s.release.version}… ${s.progress?.let { "${(it * 100).toInt()}%" } ?: ""}".trim() to false
                    is UpdateStatus.Installing -> "Installing ${s.release.version}…" to false
                    is UpdateStatus.Failed -> s.message to true
                }
                Text(
                    line,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (bad) AppColors.status.bad else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            val offer = (s as? UpdateStatus.Available)?.release ?: (s as? UpdateStatus.Failed)?.release
            when {
                offer != null -> Button(onClick = { Updater.prompt.value = offer }) { Text("Update") }
                s is UpdateStatus.Checking || s is UpdateStatus.Downloading || s is UpdateStatus.Installing -> SmallSpinner()
                else -> OutlinedButton(onClick = { Updater.checkNow(context) }) { Text("Check") }
            }
        }
        (s as? UpdateStatus.Downloading)?.progress?.let { p ->
            LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
        }
        SwitchRow(
            title = "Check for updates automatically",
            subtitle = "When you open the app and once a day.",
            checked = settings.checkUpdates,
        ) { on -> onChange(settings.copy(checkUpdates = on, autoInstallUpdates = settings.autoInstallUpdates && on)) }
        SwitchRow(
            title = "Install updates automatically",
            subtitle = "Downloads and installs new versions in the background. Android asks you to confirm the first one.",
            checked = settings.autoInstallUpdates,
            enabled = settings.checkUpdates,
        ) { on ->
            onChange(settings.copy(autoInstallUpdates = on))
            if (on && !canInstall) context.openInstallPermission()
            if (on && Build.VERSION.SDK_INT >= 33 && !AlertWorker.canNotify(context)) {
                notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (settings.autoInstallUpdates && !canInstall) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Allow TrueNAS Mobile to install apps so it can update itself.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.status.warn,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { context.openInstallPermission() }) { Text("Allow") }
            }
        }
    }
}
