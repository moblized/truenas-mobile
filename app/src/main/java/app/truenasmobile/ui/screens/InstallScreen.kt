package app.truenasmobile.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.truenasmobile.api.CatalogAppDetails
import app.truenasmobile.api.EnumOption
import app.truenasmobile.api.Question
import app.truenasmobile.api.Schema
import app.truenasmobile.ui.ConnState
import app.truenasmobile.ui.DetailsState
import app.truenasmobile.ui.LocalChrome
import app.truenasmobile.ui.Route
import app.truenasmobile.ui.ScreenScaffold
import app.truenasmobile.ui.components.AppIcon
import app.truenasmobile.ui.components.EmptyState
import app.truenasmobile.ui.components.LoadingBox
import app.truenasmobile.ui.components.SectionCard
import app.truenasmobile.ui.components.SmallSpinner
import app.truenasmobile.ui.theme.AppColors
import kotlinx.coroutines.launch

@Composable
fun InstallScreen(name: String, train: String) {
    val chrome = LocalChrome.current
    val vm = chrome.vm
    val conn by vm.conn.collectAsStateWithLifecycle()
    val state by vm.installDetails.collectAsStateWithLifecycle()

    LaunchedEffect(name, train, conn) {
        if (conn == ConnState.Connected) vm.loadInstallDetails(name, train)
    }

    when (val s = state) {
        is DetailsState.Ready -> if (s.details.name == name && s.details.train == train) {
            InstallForm(s.details)
        } else {
            ScreenScaffold(title = name, showBack = true) { LoadingBox() }
        }
        is DetailsState.Failed -> ScreenScaffold(title = name, showBack = true) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                EmptyState(Icons.Outlined.ErrorOutline, "Couldn't load $name", s.message)
                TextButton(onClick = { vm.loadInstallDetails(name, train) }) { Text("Try again") }
            }
        }
        DetailsState.Loading -> ScreenScaffold(title = name, subtitle = "Loading install options…", showBack = true) { LoadingBox() }
    }
}

/** Everything the field composables need: the current values, how to change them, and errors. */
private class FormCtx(
    val values: Map<String, Any?>,
    val update: (List<Any>, Any?) -> Unit,
    val errors: Map<List<Any>, String>,
    val showErrors: Boolean,
    val enabled: Boolean,
)

@Composable
private fun InstallForm(details: CatalogAppDetails) {
    val chrome = LocalChrome.current
    val vm = chrome.vm
    val apps by vm.apps.collectAsStateWithLifecycle()
    val run by vm.install.collectAsStateWithLifecycle()
    val uri = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val taken = remember(apps) { apps?.map { it.name }?.toSet() ?: emptySet() }
    var appName by remember(details.name) { mutableStateOf(Schema.suggestName(details.name, taken)) }
    var values by remember(details) { mutableStateOf(details.defaults) }
    var showErrors by remember(details) { mutableStateOf(false) }
    val expanded = remember(details) { mutableStateMapOf<String, Boolean>() }

    val groups = remember(details) { details.schema.grouped() }
    val problems = remember(values, details) { Schema.validate(details.schema.questions, values) }
    val nameProblem = Schema.appNameProblem(appName, taken)
    val current = run?.takeIf { it.appName == appName || it.running }
    val installing = current?.running == true

    // Leave the install screen for the new app once it's done.
    LaunchedEffect(current?.done) {
        val r = current
        if (r != null && r.done) {
            vm.clearInstall()
            chrome.nav.back()
            chrome.nav.back()
            chrome.nav.push(Route.AppDetail(r.appName))
        }
    }

    @Suppress("UNCHECKED_CAST")
    val ctx = FormCtx(
        values = values,
        update = { path, v -> values = Schema.setAt(values, path, v) as Map<String, Any?> },
        errors = problems.associate { it.path to it.message },
        showErrors = showErrors,
        enabled = !installing,
    )

    fun submit() {
        showErrors = true
        if (nameProblem != null || problems.isNotEmpty()) {
            // Open the sections that hold errors and jump to the top where they're listed.
            problems.forEach { p ->
                val top = p.path.firstOrNull() as? String
                details.schema.questions.firstOrNull { it.variable == top }?.group?.let { expanded[it] = true }
            }
            scope.launch { listState.animateScrollToItem(0) }
            return
        }
        vm.installApp(details, appName, values)
    }

    ScreenScaffold(
        title = "Install ${details.title}",
        subtitle = "Version ${details.humanVersion} · ${details.train}",
        showBack = true,
        fab = {
            ExtendedFloatingActionButton(
                onClick = { if (!installing) submit() },
                icon = { if (installing) SmallSpinner() else Icon(Icons.Outlined.CloudDownload, contentDescription = null) },
                text = { Text(if (installing) "Installing…" else "Install") },
            )
        },
    ) {
        LazyColumn(
            state = listState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                SectionCard(Modifier.fillMaxWidth()) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppIcon(details.iconUrl, details.title, 52.dp)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(details.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Version ${details.humanVersion}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (details.home != null) {
                                IconButton(onClick = { runCatching { uri.openUri(details.home) } }) {
                                    Icon(Icons.Outlined.OpenInBrowser, contentDescription = "Project website")
                                }
                            }
                        }
                        if (details.description.isNotBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Text(details.description, style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "The defaults work for most setups. Change only what you need, then tap Install.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Progress, server errors, or the list of fields to fix.
            if (current != null && (current.running || current.error != null)) {
                item { InstallStatusCard(current.label, current.error) }
            }
            if (showErrors && (problems.isNotEmpty() || nameProblem != null)) {
                item {
                    val lines = listOfNotNull(nameProblem?.let { "App name: $it" }) + problems.take(8).map { "${it.label}: ${it.message}" }
                    InstallStatusCard(
                        label = "",
                        error = "Fix these before installing:\n" + lines.joinToString("\n") { "• $it" } +
                            if (problems.size > 8) "\n…and ${problems.size - 8} more" else "",
                    )
                }
            }

            item {
                OutlinedTextField(
                    value = appName,
                    onValueChange = { appName = it.lowercase().filter { c -> c.isLetterOrDigit() || c == '-' }.take(40) },
                    label = { Text("App name *") },
                    supportingText = {
                        Text(
                            if (showErrors && nameProblem != null) nameProblem
                            else "Names the app on the server. Lowercase letters, numbers and hyphens.",
                        )
                    },
                    isError = showErrors && nameProblem != null,
                    enabled = !installing,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            items(groups) { (group, questions) ->
                val open = expanded[group.name] ?: (group == groups.firstOrNull()?.first)
                val groupHasError = showErrors && problems.any { p ->
                    val top = p.path.firstOrNull() as? String
                    questions.any { it.variable == top }
                }
                SectionCard(Modifier.fillMaxWidth().animateContentSize()) {
                    Column {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { expanded[group.name] = !open },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    group.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (groupHasError) AppColors.status.bad else MaterialTheme.colorScheme.onSurface,
                                )
                                if (!open && group.description != null) {
                                    Text(
                                        group.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = if (open) "Collapse" else "Expand")
                        }
                        if (open) {
                            Spacer(Modifier.height(12.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                QuestionList(questions, emptyList(), ctx)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InstallStatusCard(label: String, error: String?) {
    val s = AppColors.status
    val color = if (error != null) s.bad else MaterialTheme.colorScheme.primary
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CloudDownload, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(label, style = MaterialTheme.typography.bodyMedium)
            }
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(
                "Pulling images can take a few minutes. You can leave this screen; the install keeps going on the server.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ------------------------------------------------------------------ fields

@Composable
private fun QuestionList(questions: List<Question>, parentPath: List<Any>, ctx: FormCtx) {
    val siblings = (if (parentPath.isEmpty()) ctx.values else Schema.getAt(ctx.values, parentPath)) as? Map<*, *>
    questions.forEach { q ->
        if (!q.schema.hidden && Schema.visible(q.schema.showIf, siblings)) {
            QuestionField(q, parentPath + q.variable, ctx)
        }
    }
}

@Composable
private fun QuestionField(q: Question, path: List<Any>, ctx: FormCtx) {
    val s = q.schema
    val value = Schema.getAt(ctx.values, path)
    val error = if (ctx.showErrors) ctx.errors[path] else null
    val label = q.label.ifBlank { prettyVariable(q.variable) } + if (s.required && !s.isBoolean && !s.isDict && !s.isList) " *" else ""
    when {
        s.isDict -> DictField(q, path, ctx)
        s.isList -> ListField(q, path, value as? List<*> ?: emptyList<Any?>(), ctx)
        s.enum != null -> EnumField(label, q.description, s.enum, value, error, ctx.enabled) { ctx.update(path, it) }
        s.isBoolean -> SwitchField(label, q.description, value == true, ctx.enabled) { ctx.update(path, it) }
        s.isNumber -> OutlinedTextField(
            value = (value as? Number)?.toLong()?.toString() ?: "",
            onValueChange = { t ->
                val clean = t.filterIndexed { i, c -> c.isDigit() || (i == 0 && c == '-') }
                ctx.update(path, if (clean.isEmpty() || clean == "-") null else clean.toLongOrNull())
            },
            label = { Text(label) },
            supportingText = fieldHelp(error, q.description, rangeHint(s.min, s.max)),
            isError = error != null,
            enabled = ctx.enabled,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        else -> TextField(label, q, value?.toString() ?: "", error, ctx.enabled) { t ->
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
    return { Text(text, maxLines = if (error != null) 3 else 4, overflow = TextOverflow.Ellipsis) }
}

@Composable
private fun TextField(label: String, q: Question, value: String, error: String?, enabled: Boolean, onChange: (String) -> Unit) {
    val s = q.schema
    var reveal by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = when {
            s.isPath -> {
                { Text("/mnt/pool/dataset") }
            }
            s.type == "ipaddr" -> {
                { Text("192.168.1.20") }
            }
            else -> null
        },
        supportingText = fieldHelp(error, q.description),
        isError = error != null,
        enabled = enabled,
        singleLine = s.type != "text",
        minLines = if (s.type == "text") 3 else 1,
        visualTransformation = if (s.private && !reveal) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = if (s.private) {
            {
                IconButton(onClick = { reveal = !reveal }) {
                    Icon(if (reveal) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, contentDescription = if (reveal) "Hide" else "Show")
                }
            }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = when {
                s.private -> KeyboardType.Password
                s.type == "uri" -> KeyboardType.Uri
                s.isPath -> KeyboardType.Uri
                else -> KeyboardType.Text
            },
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SwitchField(label: String, description: String?, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

/** A field that opens a list to choose from (long lists like time zones get a search box). */
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
    var open by remember { mutableStateOf(false) }
    val selected = options.firstOrNull { Schema.same(it.value, value) }
    val outline = when {
        error != null -> AppColors.status.bad
        else -> MaterialTheme.colorScheme.outline
    }
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
                maxLines = 4,
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
    val attrs = q.schema.attrs
    if (attrs.none { !it.schema.hidden }) return
    if (q.label.isBlank()) {
        // Unlabelled dicts are just containers; show their fields inline.
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) { QuestionList(attrs, path, ctx) }
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
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
        }
        QuestionList(attrs, path, ctx)
    }
}

@Composable
private fun ListField(q: Question, path: List<Any>, items: List<*>, ctx: FormCtx) {
    val itemQ = q.schema.items.firstOrNull() ?: return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(q.label.ifBlank { prettyVariable(q.variable) }, style = MaterialTheme.typography.titleSmall)
                q.description?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
            }
            TextButton(
                onClick = { ctx.update(path, items.toList() + Schema.defaultFor(itemQ)) },
                enabled = ctx.enabled,
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add")
            }
        }
        if (items.isEmpty()) {
            Text("None", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
        items.forEachIndexed { i, _ ->
            val itemPath = path + i
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(start = 12.dp, top = 4.dp, bottom = 12.dp, end = 4.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${itemQ.label.ifBlank { "Item" }} ${i + 1}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { ctx.update(path, items.filterIndexed { idx, _ -> idx != i }) }, enabled = ctx.enabled) {
                            Icon(Icons.Outlined.Close, contentDescription = "Remove")
                        }
                    }
                    Column(Modifier.padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (itemQ.schema.isDict) {
                            QuestionList(itemQ.schema.attrs, itemPath, ctx)
                        } else {
                            QuestionField(itemQ.copy(label = itemQ.label.ifBlank { "Value" }), itemPath, ctx)
                        }
                    }
                }
            }
        }
    }
}
