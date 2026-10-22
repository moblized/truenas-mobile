package app.truenasmobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.truenasmobile.api.CatalogApp
import app.truenasmobile.ui.ConnState
import app.truenasmobile.ui.ListPadding
import app.truenasmobile.ui.LocalChrome
import app.truenasmobile.ui.Route
import app.truenasmobile.ui.ScreenScaffold
import app.truenasmobile.ui.components.AppIcon
import app.truenasmobile.ui.components.EmptyState
import app.truenasmobile.ui.components.LoadingBox
import app.truenasmobile.ui.components.StatusChip
import app.truenasmobile.ui.theme.AppColors

@Composable
fun CatalogScreen() {
    val chrome = LocalChrome.current
    val vm = chrome.vm
    val conn by vm.conn.collectAsStateWithLifecycle()
    val catalog by vm.catalog.collectAsStateWithLifecycle()
    val error by vm.catalogError.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var train by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(conn) { if (conn == ConnState.Connected) vm.loadCatalog() }

    val all = catalog
    ScreenScaffold(
        title = "Install an app",
        subtitle = all?.let { "${it.size} apps in the catalog" },
        showBack = true,
        actions = {
            TextButton(onClick = { chrome.nav.push(Route.Yaml(null)) }) {
                Icon(Icons.Outlined.Code, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("YAML")
            }
        },
    ) {
        LazyColumn(contentPadding = ListPadding, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search apps") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, contentDescription = "Clear") }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }
            when {
                all == null && error != null -> item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        EmptyState(Icons.Outlined.CloudOff, "Couldn't load the catalog", error)
                        TextButton(onClick = { vm.loadCatalog(force = true) }) { Text("Try again") }
                    }
                }
                all == null -> item { LoadingBox() }
                all.isEmpty() -> item {
                    EmptyState(
                        Icons.Outlined.Storefront,
                        "The catalog is empty",
                        "Open Apps → Discover Apps in the TrueNAS web UI once so the server downloads its catalog, then come back.",
                    )
                }
                else -> {
                    val trains = all.map { it.train }.distinct().sorted()
                    val categories = all.flatMap { it.categories }.groupingBy { it }.eachCount()
                        .entries.sortedByDescending { it.value }.map { it.key }
                    item {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilterChip(selected = category == null, onClick = { category = null }, label = { Text("All") })
                            categories.forEach { c ->
                                FilterChip(
                                    selected = category == c,
                                    onClick = { category = if (category == c) null else c },
                                    label = { Text(prettyCategory(c)) },
                                )
                            }
                        }
                    }
                    if (trains.size > 1) {
                        item {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                FilterChip(selected = train == null, onClick = { train = null }, label = { Text("All trains") })
                                trains.forEach { t ->
                                    FilterChip(
                                        selected = train == t,
                                        onClick = { train = if (train == t) null else t },
                                        label = { Text(t.replaceFirstChar { it.uppercase() }) },
                                    )
                                }
                            }
                        }
                    }
                    val q = query.trim()
                    val shown = all.filter { app ->
                        (category == null || category in app.categories) &&
                            (train == null || app.train == train) &&
                            (q.isEmpty() || app.title.contains(q, true) || app.name.contains(q, true) || app.description.contains(q, true))
                    }
                    if (shown.isEmpty()) item { EmptyState(Icons.Outlined.Search, "No apps match") }
                    items(shown, key = { "${it.train}/${it.name}" }) { app ->
                        CatalogRow(app) { chrome.nav.push(Route.Install(app.name, app.train)) }
                    }
                }
            }
        }
    }
}

private fun prettyCategory(c: String): String =
    c.replace('-', ' ').replace('_', ' ').split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }

@Composable
private fun CatalogRow(app: CatalogApp, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app.iconUrl, app.title, 48.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    app.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (app.installed) {
                    Spacer(Modifier.width(8.dp))
                    StatusChip("Installed", AppColors.status.good)
                } else if (app.train != "stable") {
                    Spacer(Modifier.width(8.dp))
                    StatusChip(app.train.replaceFirstChar { it.uppercase() }, AppColors.status.idle)
                }
            }
            if (app.description.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    app.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            app.latestHumanVersion?.let {
                Text(
                    "v$it",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}
