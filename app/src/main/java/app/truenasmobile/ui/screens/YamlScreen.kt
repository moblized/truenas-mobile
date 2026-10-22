package app.truenasmobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.truenasmobile.api.Schema
import app.truenasmobile.ui.ConnState
import app.truenasmobile.ui.FormLoad
import app.truenasmobile.ui.LocalChrome
import app.truenasmobile.ui.Route
import app.truenasmobile.ui.ScreenScaffold
import app.truenasmobile.ui.components.EmptyState
import app.truenasmobile.ui.components.LoadingBox
import app.truenasmobile.ui.components.SmallSpinner
import app.truenasmobile.ui.theme.AppColors

private const val TEMPLATE = """services:
  myapp:
    image: nginx:latest
    restart: unless-stopped
    ports:
      - "8080:80"
    volumes:
      - /mnt/tank/apps/myapp:/data
    environment:
      TZ: America/New_York
"""

/** Install a custom app from a Docker Compose file, or edit one. [appName] is null for a new app. */
@Composable
fun YamlScreen(appName: String?) {
    val chrome = LocalChrome.current
    val vm = chrome.vm
    val conn by vm.conn.collectAsStateWithLifecycle()
    val loaded by vm.yaml.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val run by vm.install.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val editing = appName != null

    LaunchedEffect(appName, conn) {
        if (appName != null && conn == ConnState.Connected) vm.loadYaml(appName)
    }

    var name by remember { mutableStateOf(appName ?: "") }
    var text by remember { mutableStateOf(if (editing) "" else TEMPLATE) }
    var filled by remember { mutableStateOf(!editing) }
    var showErrors by remember { mutableStateOf(false) }
    LaunchedEffect(loaded) {
        val l = loaded
        if (editing && !filled && l is FormLoad.Text) {
            text = l.text
            filled = true
        }
    }

    val taken = apps?.map { it.name }?.toSet() ?: emptySet()
    val nameProblem = if (editing) null else Schema.appNameProblem(name, taken)
    val current = run?.takeIf { it.appName == name }
    val working = current?.running == true

    LaunchedEffect(current?.done) {
        val r = current
        if (r != null && r.done) {
            vm.clearInstall()
            chrome.nav.back()
            if (!editing) {
                chrome.nav.back()
                chrome.nav.push(Route.AppDetail(r.appName))
            }
        }
    }

    ScreenScaffold(
        title = if (editing) "Edit $appName" else "Install from YAML",
        subtitle = "Docker Compose",
        showBack = true,
        actions = {
            IconButton(onClick = { clipboard.getText()?.text?.let { if (it.isNotBlank()) text = it } }, enabled = !working) {
                Icon(Icons.Outlined.ContentPaste, contentDescription = "Paste from clipboard")
            }
        },
        fab = {
            if (filled) {
                ExtendedFloatingActionButton(
                    onClick = {
                        showErrors = true
                        if (!working && nameProblem == null && text.isNotBlank()) vm.submitYaml(name, text, editing)
                    },
                    icon = { if (working) SmallSpinner() else Icon(if (editing) Icons.Outlined.Save else Icons.Outlined.CloudDownload, contentDescription = null) },
                    text = { Text(if (working) "Working…" else if (editing) "Save" else "Install") },
                )
            }
        },
    ) {
        val l = loaded
        if (editing && !filled) {
            if (l is FormLoad.Failed) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    EmptyState(Icons.Outlined.ErrorOutline, "Couldn't load the compose file", l.message)
                    TextButton(onClick = { vm.loadYaml(appName!!) }) { Text("Try again") }
                }
            } else {
                LoadingBox()
            }
            return@ScreenScaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (current != null && (current.running || current.error != null)) {
                val color = if (current.error != null) AppColors.status.bad else MaterialTheme.colorScheme.primary
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(color.copy(alpha = 0.10f))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (current.error != null) {
                        Row {
                            Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(current.error, style = MaterialTheme.typography.bodyMedium)
                        }
                    } else {
                        Text(current.label, style = MaterialTheme.typography.bodyMedium)
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            }
            if (!editing) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.lowercase().filter { c -> c.isLetterOrDigit() || c == '-' }.take(40) },
                    label = { Text("App name") },
                    isError = showErrors && nameProblem != null,
                    supportingText = { Text(if (showErrors && nameProblem != null) nameProblem else "Lowercase letters, numbers and hyphens.") },
                    singleLine = true,
                    enabled = !working,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Paste a docker-compose file. Use full /mnt/… paths for volumes. The app shows up in the Apps tab like any other.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Compose YAML") },
                isError = showErrors && text.isBlank(),
                enabled = !working,
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 360.dp),
            )
            Spacer(Modifier.size(88.dp))
        }
    }
}
