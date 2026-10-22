package app.truenasmobile.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.truenasmobile.api.EnumOption
import app.truenasmobile.api.Question
import app.truenasmobile.api.Schema
import app.truenasmobile.ui.AppFormData
import app.truenasmobile.ui.ConnState
import app.truenasmobile.ui.FormLoad
import app.truenasmobile.ui.LocalChrome
import app.truenasmobile.ui.Route
import app.truenasmobile.ui.ScreenScaffold
import app.truenasmobile.ui.components.AppIcon
import app.truenasmobile.ui.components.EmptyState
import app.truenasmobile.ui.components.FolderPicker
import app.truenasmobile.ui.components.LoadingBox
import app.truenasmobile.ui.components.SectionCard
import app.truenasmobile.ui.components.SmallSpinner
import app.truenasmobile.ui.theme.AppColors
import kotlinx.coroutines.launch

// ================================================================== entry points

@Composable
fun InstallScreen(name: String, train: String) {
    val vm = LocalChrome.current.vm
    val conn by vm.conn.collectAsStateWithLifecycle()
    LaunchedEffect(name, train, conn) {
        if (conn == ConnState.Connected) vm.loadInstallForm(name, train)
    }
    FormHost(title = name, retry = { vm.loadInstallForm(name, train) }) { d -> !d.editing && d.catalogName == name && d.train == train }
}

@Composable
fun EditAppScreen(appName: String) {
    val vm = LocalChrome.current.vm
    val conn by vm.conn.collectAsStateWithLifecycle()
    LaunchedEffect(appName, conn) {
        if (conn == ConnState.Connected) vm.loadEditForm(appName)
    }
    FormHost(title = appName, retry = { vm.loadEditForm(appName) }) { d -> d.editing && d.appName == appName }
}

@Composable
private fun FormHost(title: String, retry: () -> Unit, matches: (AppFormData) -> Boolean) {
    val vm = LocalChrome.current.vm
    val state by vm.appForm.collectAsStateWithLifecycle()
    when (val s = state) {
        is FormLoad.Ready -> if (matches(s.data)) {
            AppForm(s.data)
        } else {
            ScreenScaffold(title = title, showBack = true) { LoadingBox() }
        }
        is FormLoad.Failed -> ScreenScaffold(title = title, showBack = true) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                EmptyState(Icons.Outlined.ErrorOutline, "Couldn't load $title", s.message)
                TextButton(onClick = retry) { Text("Try again") }
            }
        }
        else -> ScreenScaffold(title = title, subtitle = "Loading settings…", showBack = true) { LoadingBox() }
    }
}

// ================================================================== form

/** What every field needs: the values, how to change them, errors, and what to leave out. */
private class FormCtx(
    val values: Map<String, Any?>,
    val update: (List<Any>, Any?) -> Unit,
    val errors: Map<List<Any>, String>,
    val showErrors: Boolean,
    val enabled: Boolean,
    val editing: Boolean,
    val skip: Set<List<Any>> = emptySet(),
    val usedPorts: Set<Int> = emptySet(),
    val originalPorts: Set<Int> = emptySet(),
) {
    fun copy(skip: Set<List<Any>>) = FormCtx(values, update, errors, showErrors, enabled, editing, skip, usedPorts, originalPorts)
}

@Composable
private fun AppForm(form: AppFormData) {
    val chrome = LocalChrome.current
    val vm = chrome.vm
    val apps by vm.apps.collectAsStateWithLifecycle()
    val run by vm.install.collectAsStateWithLifecycle()
    val usedPorts by vm.usedPorts.collectAsStateWithLifecycle()
    val uri = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val taken = remember(apps) { apps?.map { it.name }?.toSet() ?: emptySet() }
    var appName by remember(form) { mutableStateOf(form.appName) }
    var values by remember(form) { mutableStateOf(form.values) }
    var showErrors by remember(form) { mutableStateOf(false) }
    var advanced by remember(form) { mutableStateOf(false) }
    val openGroups = remember(form) { mutableStateMapOf<String, Boolean>() }

    val questions = form.schema.questions
    val layout = remember(values) { Schema.layout(questions, values) }
    val problems = remember(values) { Schema.validate(questions, values) }
    val nameProblem = if (form.editing) null else Schema.appNameProblem(appName, taken)
    val current = run?.takeIf { it.appName == appName }
    val working = current?.running == true
    val originalPorts = remember(form) { if (form.editing) collectPorts(Schema.layout(questions, form.values), form.values) else emptySet() }

    LaunchedEffect(current?.done) {
        val r = current
        if (r != null && r.done) {
            vm.clearInstall()
            if (form.editing) {
                chrome.nav.back()
            } else {
                chrome.nav.back() // install form
                chrome.nav.back() // catalog
                chrome.nav.push(Route.AppDetail(r.appName))
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    val ctx = FormCtx(
        values = values,
        update = { path, v -> values = Schema.setAt(values, path, v) as Map<String, Any?> },
        errors = problems.associate { it.path to it.message },
        showErrors = showErrors,
        enabled = !working,
        editing = form.editing,
        skip = layout.claimed,
        usedPorts = usedPorts,
        originalPorts = originalPorts,
    )

    fun submit() {
        showErrors = true
        if (nameProblem != null || problems.isNotEmpty()) {
            // Open whatever holds the problem, then show the list at the top.
            if (problems.any { p -> layout.claimed.none { c -> p.path.take(c.size) == c } }) advanced = true
            problems.forEach { p ->
                val top = p.path.firstOrNull() as? String
                questions.firstOrNull { it.variable == top }?.group?.let { openGroups[it] = true }
            }
            scope.launch { listState.animateScrollToItem(0) }
            return
        }
        vm.submitAppForm(form, appName, values)
    }

    ScreenScaffold(
        title = if (form.editing) "Edit ${form.title}" else "Install ${form.title}",
        subtitle = form.versionLabel,
        showBack = true,
        fab = {
            ExtendedFloatingActionButton(
                onClick = { if (!working) submit() },
                icon = {
                    when {
                        working -> SmallSpinner()
                        form.editing -> Icon(Icons.Outlined.Save, contentDescription = null)
                        else -> Icon(Icons.Outlined.CloudDownload, contentDescription = null)
                    }
                },
                text = { Text(if (working) (if (form.editing) "Saving…" else "Installing…") else if (form.editing) "Save" else "Install") },
            )
        },
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                SectionCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(form.iconUrl, form.title, 48.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(form.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            if (form.description.isNotBlank()) {
                                Text(
                                    form.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        if (form.home != null) {
                            IconButton(onClick = { runCatching { uri.openUri(form.home) } }) {
                                Icon(Icons.Outlined.OpenInBrowser, contentDescription = "Project website")
                            }
                        }
                    }
                }
            }

            if (current != null && (current.running || current.error != null)) {
                item { StatusCard(current.label, current.error, form.editing) }
            }
            if (showErrors && (problems.isNotEmpty() || nameProblem != null)) {
                item {
                    val lines = listOfNotNull(nameProblem?.let { "App name: $it" }) + problems.take(8).map { "${it.label}: ${it.message}" }
                    StatusCard(
                        label = "",
                        error = "Fix these first:\n" + lines.joinToString("\n") { "• $it" } +
                            if (problems.size > 8) "\n…and ${problems.size - 8} more" else "",
                        editing = form.editing,
                    )
                }
            }

            if (!form.editing) {
                item {
                    OutlinedTextField(
                        value = appName,
                        onValueChange = { appName = it.lowercase().filter { c -> c.isLetterOrDigit() || c == '-' }.take(40) },
                        label = { Text("App name") },
                        supportingText = {
                            Text(if (showErrors && nameProblem != null) nameProblem else "Lowercase letters, numbers and hyphens.")
                        },
                        isError = showErrors && nameProblem != null,
                        enabled = !working,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (layout.needsInput.isNotEmpty()) {
                item {
                    EasySection("Needs your input", Icons.Outlined.Edit, "These have no default, so the app can't start without them.") {
                        layout.needsInput.forEach { p -> QuestionField(p.q, p.path, ctx.copy(skip = emptySet()), showGenerate = true) }
                    }
                }
            }

            if (layout.storage.isNotEmpty() || layout.storageLists.isNotEmpty()) {
                item {
                    EasySection("Storage", Icons.Outlined.Storage, "Where the app keeps its files. \"Automatic\" is the easy choice; pick a folder to use data you already have.") {
                        layout.storage.forEach { p -> StorageEntry(p.q, p.path, ctx) }
                        layout.storageLists.forEach { p -> StorageList(p.q, p.path, ctx) }
                    }
                }
            }

            if (layout.ports.isNotEmpty()) {
                item {
                    EasySection("Network", Icons.Outlined.Lan, null) {
                        layout.ports.forEach { p -> PortField(p.q, p.path, ctx) }
                    }
                }
            }

            layout.gpu?.let { g ->
                item {
                    EasySection("Hardware acceleration", Icons.Outlined.Memory, "Lets the app use your GPU, for example for video transcoding.") {
                        QuestionList(g.q.schema.attrs, g.path, ctx.copy(skip = emptySet()))
                    }
                }
            }

            // Everything else, grouped like the web UI, out of the way.
            val groups = form.schema.grouped().mapNotNull { (group, qs) ->
                val shown = qs.filter { renderable(it, listOf(it.variable), ctx) }
                if (shown.isEmpty()) null else group to shown
            }
            if (groups.isNotEmpty()) {
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { advanced = !advanced }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Advanced settings", style = MaterialTheme.typography.titleMedium)
                            Text(
                                groups.joinToString(" · ") { it.first.name.removeSuffix(" Configuration") },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Icon(if (advanced) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null)
                    }
                }
            }
            if (advanced) {
                items(groups) { (group, qs) ->
                    val open = openGroups[group.name] == true
                    SectionCard(Modifier.fillMaxWidth().animateContentSize()) {
                        Column {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { openGroups[group.name] = !open },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                val hasError = showErrors && problems.any { p -> qs.any { it.variable == p.path.firstOrNull() } }
                                Text(
                                    group.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (hasError) AppColors.status.bad else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null)
                            }
                            if (open) {
                                Spacer(Modifier.height(12.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) { QuestionList(qs, emptyList(), ctx) }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun collectPorts(layout: Schema.Layout, values: Map<*, *>): Set<Int> =
    layout.ports.mapNotNull { p -> (Schema.getAt(values, p.path + "port_number") as? Number)?.toInt() }.toSet()

@Composable
private fun EasySection(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, hint: String?, content: @Composable () -> Unit) {
    SectionCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            if (hint != null) {
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            content()
        }
    }
}

@Composable
private fun StatusCard(label: String, error: String?, editing: Boolean) {
    val color = if (error != null) AppColors.status.bad else MaterialTheme.colorScheme.primary
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.10f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (error != null) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(error, style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(
                if (editing) "The app restarts with the new settings when this finishes."
                else "Downloading the app can take a few minutes. You can leave this screen; it keeps going on the server.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ================================================================== storage

private val TYPE_LABELS = mapOf(
    "ix_volume" to "Automatic",
    "host_path" to "A folder I choose",
    "cifs" to "SMB share",
    "nfs" to "NFS share",
    "tmpfs" to "In memory",
    "temporary" to "Temporary",
    "anonymous" to "Container only",
    "volume" to "Docker volume",
)

/** The folder field inside a storage entry (it moves into the ACL settings when ACL is on). */
private fun hostPathField(entry: Map<*, *>?): List<Any> {
    val hp = entry?.get("host_path_config") as? Map<*, *>
    return if (hp?.get("acl_enable") == true) listOf("host_path_config", "acl", "path") else listOf("host_path_config", "path")
}

@Composable
private fun StorageEntry(q: Question, path: List<Any>, ctx: FormCtx) {
    @Suppress("UNCHECKED_CAST")
    val entry = Schema.getAt(ctx.values, path) as? Map<*, *>
    val typeQ = q.schema.attrs.first { it.variable == "type" }
    val type = entry?.get("type") as? String
    val options = typeQ.schema.enum ?: emptyList()
    val folderPath = path + hostPathField(entry)
    val folder = Schema.getAt(ctx.values, folderPath) as? String
    var picking by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    val folderError = if (ctx.showErrors) ctx.errors[folderPath] else null
    val immutableType = ctx.editing && typeQ.schema.immutable

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(BorderStroke(1.dp, if (folderError != null) AppColors.status.bad else MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(q.label.ifBlank { prettyVariable(q.variable) }, style = MaterialTheme.typography.titleSmall)
        q.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }

        val main = options.filter { it.value == "ix_volume" || it.value == "host_path" }
        val other = options - main.toSet()
        main.forEach { opt ->
            val selected = type == opt.value
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = ctx.enabled && !immutableType) {
                        ctx.update(path + "type", opt.value)
                        if (opt.value == "host_path" && folder.isNullOrBlank()) picking = true
                    }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected, onClick = null, enabled = ctx.enabled && !immutableType)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text((opt.value as? String)?.let { TYPE_LABELS[it] } ?: opt.description, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (opt.value == "ix_volume") "TrueNAS makes a dataset for it. Easiest."
                        else "Use a folder or dataset that's already on your server.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (other.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                other.forEach { opt ->
                    FilterChip(
                        selected = type == opt.value,
                        onClick = { ctx.update(path + "type", opt.value) },
                        enabled = ctx.enabled && !immutableType,
                        label = { Text((opt.value as? String)?.let { TYPE_LABELS[it] } ?: opt.description.substringBefore(" (")) },
                    )
                }
            }
        }

        if (type == "host_path") {
            FolderRow(folder, folderError, ctx.enabled) { picking = true }
        }

        // Settings for the chosen type (SMB server, ACLs…), minus what's shown above.
        val configVar = when (type) {
            "host_path" -> "host_path_config"
            "ix_volume" -> "ix_volume_config"
            "cifs" -> "cifs_config"
            "nfs" -> "nfs_config"
            "tmpfs" -> "tmpfs_config"
            else -> null
        }
        val configQ = q.schema.attrs.firstOrNull { it.variable == configVar }
        val sub = ctx.copy(skip = setOf(path + "type", folderPath))
        val rest = q.schema.attrs.filter { it.variable != "type" && it.variable != configVar }
        val needsConfig = type == "cifs" || type == "nfs"
        val hasMore = (configQ != null && renderable(configQ, path + configQ.variable, sub)) || rest.any { renderable(it, path + it.variable, sub) }
        if (hasMore) {
            if (needsConfig || more) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
                    if (configQ != null) QuestionList(configQ.schema.attrs, path + configQ.variable, sub)
                    QuestionList(rest, path, sub)
                }
            }
            if (!needsConfig) {
                TextButton(onClick = { more = !more }) { Text(if (more) "Fewer options" else "More options (permissions…)") }
            }
        }
    }

    if (picking) {
        FolderPicker(
            title = "Choose a folder for ${q.label.ifBlank { "this storage" }.removeSuffix(" Storage")}",
            start = folder,
            forApps = true,
            onPick = { picked ->
                @Suppress("UNCHECKED_CAST")
                val updated = Schema.useHostPath(entry, "")
                ctx.update(path, Schema.setAt(updated, hostPathField(updated), picked))
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun FolderRow(folder: String?, error: String?, enabled: Boolean, onPick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(enabled = enabled, onClick = onPick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Folder, contentDescription = null, tint = if (error != null) AppColors.status.bad else MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(10.dp))
        Text(
            if (folder.isNullOrBlank()) "Choose a folder…" else folder,
            style = MaterialTheme.typography.bodyMedium,
            color = if (folder.isNullOrBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(if (folder.isNullOrBlank()) "Browse" else "Change", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
    }
    if (error != null) Text("Choose a folder", color = AppColors.status.bad, style = MaterialTheme.typography.bodySmall)
}

/** "Additional storage": extra folders the app can see, added with the folder picker. */
@Composable
private fun StorageList(q: Question, path: List<Any>, ctx: FormCtx) {
    val itemQ = q.schema.items.first()
    val items = Schema.getAt(ctx.values, path) as? List<*> ?: emptyList<Any?>()
    var picking by remember { mutableStateOf(false) }
    var mountFor by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Int?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(q.label.ifBlank { "Additional storage" }, style = MaterialTheme.typography.titleSmall)
        Text(
            "Extra folders the app can see, like your media library.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        items.forEachIndexed { i, raw ->
            val item = raw as? Map<*, *>
            val itemPath = path + i
            val type = item?.get("type") as? String
            val source = when (type) {
                "host_path" -> Schema.getAt(item, hostPathField(item)) as? String
                "ix_volume" -> "Automatic dataset"
                else -> type?.let { TYPE_LABELS[it] } ?: type
            }
            val mount = item?.get("mount_path") as? String
            val hasError = ctx.showErrors && ctx.errors.keys.any { it.take(itemPath.size) == itemPath }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .border(BorderStroke(1.dp, if (hasError) AppColors.status.bad else Color.Transparent), RoundedCornerShape(12.dp))
                    .clickable(enabled = ctx.enabled) { editing = i }
                    .padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(source.takeUnless { it.isNullOrBlank() } ?: "No folder chosen", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        buildString {
                            append("→ ")
                            append(mount.takeUnless { it.isNullOrBlank() } ?: "path not set")
                            if (item?.get("read_only") == true) append(" · read-only")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (hasError) AppColors.status.bad else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { ctx.update(path, items.filterIndexed { idx, _ -> idx != i }) }, enabled = ctx.enabled) {
                    Icon(Icons.Outlined.Close, contentDescription = "Remove")
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { picking = true }, enabled = ctx.enabled) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add a folder")
            }
            TextButton(onClick = {
                ctx.update(path, items.toList() + Schema.defaultFor(itemQ))
                editing = items.size
            }, enabled = ctx.enabled) { Text("Other storage") }
        }
    }

    if (picking) {
        FolderPicker(
            title = "Choose a folder to add",
            start = null,
            forApps = false,
            onPick = { picked ->
                picking = false
                mountFor = picked
            },
            onDismiss = { picking = false },
        )
    }
    mountFor?.let { picked ->
        MountDialog(picked, onAdd = { mount, readOnly ->
            @Suppress("UNCHECKED_CAST")
            var item = Schema.useHostPath(Schema.defaultFor(itemQ) as? Map<*, *>, picked)
            item = Schema.setAt(item, listOf("mount_path"), mount) as Map<String, Any?>
            item = Schema.setAt(item, listOf("read_only"), readOnly) as Map<String, Any?>
            ctx.update(path, items.toList() + item)
            mountFor = null
        }, onDismiss = { mountFor = null })
    }
    editing?.let { i ->
        if (i < items.size) {
            ItemDialog(itemQ, path + i, ctx, onDismiss = { editing = null })
        } else {
            editing = null
        }
    }
}

@Composable
private fun MountDialog(hostPath: String, onAdd: (String, Boolean) -> Unit, onDismiss: () -> Unit) {
    val base = hostPath.trimEnd('/').substringAfterLast('/').lowercase().replace(' ', '_')
    var mount by remember { mutableStateOf("/media/$base") }
    var readOnly by remember { mutableStateOf(false) }
    val valid = mount.startsWith("/") && mount.length > 1
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Where should the app see it?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(hostPath, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = mount,
                    onValueChange = { mount = it.trim() },
                    label = { Text("Path inside the app") },
                    supportingText = { Text("You'll point the app at this path, e.g. as a library folder.") },
                    singleLine = true,
                    isError = !valid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Read-only", style = MaterialTheme.typography.bodyLarge)
                        Text("The app can read but not change these files.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = readOnly, onCheckedChange = { readOnly = it })
                }
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(mount, readOnly) }, enabled = valid) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Edits one list entry (storage or otherwise) in a dialog. */
@Composable
private fun ItemDialog(itemQ: Question, itemPath: List<Any>, ctx: FormCtx, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(itemQ.label.ifBlank { "Item" }) },
        text = {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val sub = ctx.copy(skip = emptySet())
                if (Schema.isStorage(itemQ)) {
                    StorageEntry(itemQ, itemPath, sub)
                    QuestionList(itemQ.schema.attrs.filter { it.variable in setOf("mount_path", "read_only") }, itemPath, sub)
                } else if (itemQ.schema.isDict) {
                    QuestionList(itemQ.schema.attrs, itemPath, sub)
                } else {
                    QuestionField(itemQ.copy(label = itemQ.label.ifBlank { "Value" }), itemPath, sub)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

// ================================================================== ports

@Composable
private fun PortField(q: Question, path: List<Any>, ctx: FormCtx) {
    val port = Schema.getAt(ctx.values, path) as? Map<*, *>
    val numberQ = q.schema.attrs.first { it.variable == "port_number" }
    val modeQ = q.schema.attrs.firstOrNull { it.variable == "bind_mode" }
    val mode = port?.get("bind_mode") as? String
    val published = modeQ == null || mode == "published"
    val number = (port?.get("port_number") as? Number)?.toInt()
    val error = if (ctx.showErrors) ctx.errors[path + "port_number"] else null
    val conflict = published && number != null && number in ctx.usedPorts && number !in ctx.originalPorts
    var more by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(q.label.ifBlank { prettyVariable(q.variable) }, style = MaterialTheme.typography.bodyLarge)
                Text(
                    when {
                        !published && mode == "exposed" -> "Only other apps can reach it"
                        !published -> "Not reachable"
                        else -> "Open it at your server's address on this port"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (published) {
                OutlinedTextField(
                    value = number?.toString() ?: "",
                    onValueChange = { t -> ctx.update(path + "port_number", t.filter(Char::isDigit).take(5).toLongOrNull()) },
                    singleLine = true,
                    isError = error != null || conflict,
                    enabled = ctx.enabled,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(110.dp),
                )
            }
        }
        if (conflict) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = AppColors.status.warn, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Port $number is already used by another app", style = MaterialTheme.typography.bodySmall, color = AppColors.status.warn)
            }
        }
        if (error != null) Text(error, style = MaterialTheme.typography.bodySmall, color = AppColors.status.bad)
        val rest = q.schema.attrs.filter { it.variable != "port_number" && it.variable != "bind_mode" }
        val hasRest = modeQ != null || rest.any { renderable(it, path + it.variable, ctx.copy(skip = emptySet())) }
        if (hasRest) {
            TextButton(onClick = { more = !more }, contentPadding = PaddingValues(horizontal = 0.dp)) {
                Text(if (more) "Fewer options" else "Port options")
            }
            if (more) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (modeQ != null) QuestionField(modeQ, path + "bind_mode", ctx.copy(skip = emptySet()))
                    QuestionList(rest, path, ctx.copy(skip = emptySet()))
                }
            }
        }
    }
}

// ================================================================== generic fields

/** True if the question (or something inside it) would show anything right now. */
private fun renderable(q: Question, path: List<Any>, ctx: FormCtx): Boolean {
    if (q.schema.hidden || path in ctx.skip) return false
    val parent = Schema.getAt(ctx.values, path.dropLast(1)) as? Map<*, *> ?: if (path.size == 1) ctx.values else null
    if (!Schema.visible(q.schema.showIf, parent)) return false
    if (q.schema.isDict) return q.schema.attrs.any { renderable(it, path + it.variable, ctx) }
    return true
}

@Composable
private fun QuestionList(questions: List<Question>, parentPath: List<Any>, ctx: FormCtx) {
    questions.forEach { q ->
        val path = parentPath + q.variable
        if (renderable(q, path, ctx)) QuestionField(q, path, ctx)
    }
}

@Composable
private fun QuestionField(q: Question, path: List<Any>, ctx: FormCtx, showGenerate: Boolean = false) {
    val s = q.schema
    val value = Schema.getAt(ctx.values, path)
    val error = if (ctx.showErrors) ctx.errors[path] else null
    val enabled = ctx.enabled && s.editable && !(ctx.editing && s.immutable)
    val label = q.label.ifBlank { prettyVariable(q.variable) } + if (s.required && !s.isBoolean && !s.isDict && !s.isList) " *" else ""
    when {
        Schema.isStorage(q) -> StorageEntry(q, path, ctx)
        Schema.isStorageList(q) -> StorageList(q, path, ctx)
        Schema.isPort(q) -> PortField(q, path, ctx)
        s.isDict -> DictField(q, path, ctx)
        s.isList -> ListField(q, path, value as? List<*> ?: emptyList<Any?>(), ctx)
        s.enum != null -> EnumField(label, q.description, s.enum, value, error, enabled) { ctx.update(path, it) }
        s.isBoolean -> SwitchField(label, q.description, value == true, enabled) { ctx.update(path, it) }
        s.isNumber -> OutlinedTextField(
            value = (value as? Number)?.toLong()?.toString() ?: "",
            onValueChange = { t ->
                val clean = t.filterIndexed { i, c -> c.isDigit() || (i == 0 && c == '-') }
                ctx.update(path, if (clean.isEmpty() || clean == "-") null else clean.toLongOrNull())
            },
            label = { Text(label) },
            supportingText = fieldHelp(error, q.description, rangeHint(s.min, s.max)),
            isError = error != null,
            enabled = enabled,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        s.type in setOf("hostpath", "hostpathdirectory", "hostpathfile") -> HostPathField(label, q, value?.toString() ?: "", error, enabled) { ctx.update(path, it) }
        else -> TextField(label, q, value?.toString() ?: "", error, enabled, showGenerate) { t ->
            ctx.update(path, if (t.isEmpty() && s.nullable) null else t)
        }
    }
}

private fun rangeHint(min: Long?, max: Long?): String? = when {
    min != null && max != null -> "$min–$max"
    min != null -> "At least $min"
    max != null -> "At most $max"
    else -> null
}

private fun prettyVariable(v: String): String =
    v.replace('_', ' ').replace(Regex("([a-z])([A-Z])"), "$1 $2").replaceFirstChar { it.uppercase() }

private fun fieldHelp(error: String?, description: String?, extra: String? = null): (@Composable () -> Unit)? {
    val text = error ?: listOfNotNull(description, extra).joinToString(" · ").ifBlank { null } ?: return null
    return { Text(text, maxLines = 3, overflow = TextOverflow.Ellipsis) }
}

@Composable
private fun TextField(label: String, q: Question, value: String, error: String?, enabled: Boolean, showGenerate: Boolean, onChange: (String) -> Unit) {
    val s = q.schema
    var reveal by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = if (s.type == "ipaddr") {
            { Text("192.168.1.20") }
        } else {
            null
        },
        supportingText = fieldHelp(error, q.description),
        isError = error != null,
        enabled = enabled,
        singleLine = s.type != "text",
        minLines = if (s.type == "text") 3 else 1,
        visualTransformation = if (s.private && !reveal) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = if (s.private) {
            {
                Row {
                    if (showGenerate || value.isEmpty()) {
                        IconButton(onClick = {
                            onChange(Schema.generatePassword())
                            reveal = true
                        }, enabled = enabled) { Icon(Icons.Outlined.Key, contentDescription = "Generate a strong password") }
                    }
                    IconButton(onClick = { reveal = !reveal }) {
                        Icon(if (reveal) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, contentDescription = if (reveal) "Hide" else "Show")
                    }
                }
            }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = when {
                s.private -> KeyboardType.Password
                s.type == "uri" || s.type == "path" -> KeyboardType.Uri
                else -> KeyboardType.Text
            },
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun HostPathField(label: String, q: Question, value: String, error: String?, enabled: Boolean, onChange: (String) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = { Text("/mnt/pool/folder") },
        supportingText = fieldHelp(error, q.description),
        isError = error != null,
        enabled = enabled,
        singleLine = true,
        trailingIcon = {
            IconButton(onClick = { picking = true }, enabled = enabled) { Icon(Icons.Outlined.FolderOpen, contentDescription = "Browse") }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        modifier = Modifier.fillMaxWidth(),
    )
    if (picking) {
        FolderPicker(
            title = label.removeSuffix(" *"),
            start = value,
            forApps = true,
            onPick = {
                onChange(it)
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun SwitchField(label: String, description: String?, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

/** Few short choices show as chips; longer lists open a picker with search. */
@Composable
private fun EnumField(
    label: String,
    description: String?,
    options: List<EnumOption>,
    value: Any?,
    error: String?,
    enabled: Boolean,
    onChange: (Any?) -> Unit,
) {
    val chips = options.size in 2..4 && options.all { shortLabel(it).length <= 22 }
    if (chips) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = if (error != null) AppColors.status.bad else MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { opt ->
                    FilterChip(
                        selected = Schema.same(opt.value, value),
                        onClick = { onChange(opt.value) },
                        enabled = enabled,
                        label = { Text(shortLabel(opt)) },
                    )
                }
            }
            val help = error ?: description
            if (help != null) {
                Text(help, style = MaterialTheme.typography.bodySmall, color = if (error != null) AppColors.status.bad else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
        return
    }
    var open by remember { mutableStateOf(false) }
    val selected = options.firstOrNull { Schema.same(it.value, value) }
    val outline = if (error != null) AppColors.status.bad else MaterialTheme.colorScheme.outline
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clip(RoundedCornerShape(4.dp))
                .border(BorderStroke(1.dp, outline), RoundedCornerShape(4.dp))
                .clickable(enabled = enabled && options.isNotEmpty()) { open = true }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodySmall, color = if (error != null) outline else MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    when {
                        options.isEmpty() -> "Nothing available on this server"
                        selected != null -> selected.description
                        value == null || value == "" -> "Choose…"
                        else -> value.toString()
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.Outlined.UnfoldMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val help = error ?: description
        if (help != null) {
            Text(
                help,
                style = MaterialTheme.typography.bodySmall,
                color = if (error != null) AppColors.status.bad else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp),
            )
        }
    }
    if (open) {
        ChoiceDialog(label.removeSuffix(" *"), options, value, onPick = {
            onChange(it)
            open = false
        }, onDismiss = { open = false })
    }
}

/** "Publish port on the host for external access" → "Publish port"; "None" stays. */
private fun shortLabel(o: EnumOption): String =
    o.description.substringBefore(" (").substringBefore(" - ").let { if (it.length > 28) it.substringBefore(" on ").substringBefore(" for ") else it }

@Composable
private fun ChoiceDialog(title: String, options: List<EnumOption>, value: Any?, onPick: (Any?) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val shown = if (query.isBlank()) options else options.filter {
        it.description.contains(query.trim(), true) || it.value?.toString()?.contains(query.trim(), true) == true
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (options.size > 10) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search") },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                    )
                }
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(shown.size) { i ->
                        val opt = shown[i]
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onPick(opt.value) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = Schema.same(opt.value, value), onClick = { onPick(opt.value) })
                            Text(opt.description, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun DictField(q: Question, path: List<Any>, ctx: FormCtx) {
    if (q.label.isBlank()) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) { QuestionList(q.schema.attrs, path, ctx) }
        return
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column {
            Text(q.label, style = MaterialTheme.typography.titleSmall)
            q.description?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
        QuestionList(q.schema.attrs, path, ctx)
    }
}

@Composable
private fun ListField(q: Question, path: List<Any>, items: List<*>, ctx: FormCtx) {
    val itemQ = q.schema.items.firstOrNull() ?: return
    var editing by remember { mutableStateOf<Int?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(q.label.ifBlank { prettyVariable(q.variable) }, style = MaterialTheme.typography.titleSmall)
                q.description?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
            TextButton(onClick = {
                ctx.update(path, items.toList() + Schema.defaultFor(itemQ))
                if (itemQ.schema.isDict) editing = items.size
            }, enabled = ctx.enabled) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add")
            }
        }
        items.forEachIndexed { i, item ->
            val itemPath = path + i
            if (itemQ.schema.isDict) {
                val hasError = ctx.showErrors && ctx.errors.keys.any { it.take(itemPath.size) == itemPath }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .border(BorderStroke(1.dp, if (hasError) AppColors.status.bad else Color.Transparent), RoundedCornerShape(12.dp))
                        .clickable(enabled = ctx.enabled) { editing = i }
                        .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        summary(item as? Map<*, *>, itemQ).ifBlank { "${itemQ.label.ifBlank { "Item" }} ${i + 1}" },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { ctx.update(path, items.filterIndexed { idx, _ -> idx != i }) }, enabled = ctx.enabled) {
                        Icon(Icons.Outlined.Close, contentDescription = "Remove")
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { QuestionField(itemQ.copy(label = itemQ.label.ifBlank { "Value" }), itemPath, ctx.copy(skip = emptySet())) }
                    IconButton(onClick = { ctx.update(path, items.filterIndexed { idx, _ -> idx != i }) }, enabled = ctx.enabled) {
                        Icon(Icons.Outlined.Close, contentDescription = "Remove")
                    }
                }
            }
        }
    }
    editing?.let { i ->
        if (i < items.size) ItemDialog(itemQ, path + i, ctx, onDismiss = { editing = null }) else editing = null
    }
}

/** One-line summary of a list entry, like "TZ = Europe/Berlin". */
private fun summary(item: Map<*, *>?, itemQ: Question): String {
    if (item == null) return ""
    val name = item["name"] ?: item["key"] ?: item["host_device"] ?: item["mount_path"]
    val value = item["value"] ?: item["container_device"] ?: item["host_path"]
    return when {
        name != null && value != null -> "$name = $value"
        name != null -> name.toString()
        else -> itemQ.schema.attrs.mapNotNull { a -> item[a.variable]?.takeIf { it != "" && it !is Map<*, *> && it !is List<*> }?.toString() }.take(3).joinToString(" · ")
    }
}
