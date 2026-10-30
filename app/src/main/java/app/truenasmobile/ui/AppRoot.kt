package app.truenasmobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.truenasmobile.net.CertInfo
import app.truenasmobile.ui.screens.AlertsScreen
import app.truenasmobile.ui.screens.AppDetailScreen
import app.truenasmobile.ui.screens.AppsScreen
import app.truenasmobile.ui.screens.AutoUpdateScreen
import app.truenasmobile.ui.screens.CatalogScreen
import app.truenasmobile.ui.screens.EditAppScreen
import app.truenasmobile.ui.screens.InstallScreen
import app.truenasmobile.ui.screens.YamlScreen
import app.truenasmobile.ui.screens.DashboardScreen
import app.truenasmobile.ui.screens.LogsScreen
import app.truenasmobile.ui.screens.MoreScreen
import app.truenasmobile.ui.screens.PoolDetailScreen
import app.truenasmobile.ui.screens.PowerScreen
import app.truenasmobile.ui.screens.ServerEditScreen
import app.truenasmobile.ui.screens.ServersScreen
import app.truenasmobile.ui.screens.ServicesScreen
import app.truenasmobile.ui.screens.SettingsScreen
import app.truenasmobile.ui.screens.SharesScreen
import app.truenasmobile.ui.screens.ShareEditScreen
import app.truenasmobile.ui.screens.DatasetDetailScreen
import app.truenasmobile.ui.screens.ProtectionScreen
import app.truenasmobile.ui.screens.NetworkScreen
import app.truenasmobile.ui.screens.UsersScreen
import app.truenasmobile.ui.screens.UserEditScreen
import app.truenasmobile.ui.screens.ApiKeysScreen
import app.truenasmobile.ui.screens.CertificatesScreen
import app.truenasmobile.ui.screens.VirtualizationScreen
import app.truenasmobile.ui.screens.ReportingScreen
import app.truenasmobile.ui.screens.UpdateScreen
import app.truenasmobile.ui.screens.BootEnvScreen
import app.truenasmobile.ui.screens.JobsScreen
import app.truenasmobile.ui.screens.AuditScreen
import app.truenasmobile.ui.screens.ShellScreen
import app.truenasmobile.ui.screens.SnapshotsScreen
import app.truenasmobile.ui.screens.StorageScreen
import app.truenasmobile.ui.theme.AppColors

enum class Tab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    Dashboard("Dashboard", Icons.Outlined.Dashboard, Icons.Filled.Dashboard),
    Storage("Storage", Icons.Outlined.Storage, Icons.Filled.Storage),
    Apps("Apps", Icons.Outlined.Apps, Icons.Filled.Apps),
    Alerts("Alerts", Icons.Outlined.Notifications, Icons.Filled.Notifications),
    More("Menu", Icons.Outlined.Menu, Icons.Filled.Menu),
}

sealed interface Route {
    data class PoolDetail(val name: String) : Route
    data class Snapshots(val dataset: String) : Route
    data class AppDetail(val name: String) : Route
    data class Logs(val app: String, val containerId: String, val title: String) : Route
    data object Catalog : Route
    data object AutoUpdate : Route
    data class Install(val name: String, val train: String) : Route
    data class EditApp(val name: String) : Route
    data class Yaml(val appName: String?) : Route
    data object Services : Route
    data object Shares : Route
    data class ShareEdit(val kind: String, val id: Int?) : Route
    data class DatasetDetail(val id: String) : Route
    data object Protection : Route
    data object Network : Route
    data object Users : Route
    data class UserEdit(val id: Int?) : Route
    data object ApiKeys : Route
    data object Certificates : Route
    data object Virtualization : Route
    data object Reporting : Route
    data object Update : Route
    data object BootEnvs : Route
    data object Jobs : Route
    data object Audit : Route
    data class Shell(val appName: String?, val containerId: String?, val title: String) : Route
    data object Power : Route
    data object Servers : Route
    data class ServerEdit(val id: String?) : Route
    data object Settings : Route
}

class Nav(
    val push: (Route) -> Unit,
    val back: () -> Unit,
    val selectTab: (Tab) -> Unit,
)

class Chrome(
    val vm: AppViewModel,
    val nav: Nav,
    val snackbar: SnackbarHostState,
    val bottomBar: @Composable () -> Unit,
)

val LocalChrome = staticCompositionLocalOf<Chrome> { error("Chrome not provided") }

@Composable
fun AppRoot(vm: AppViewModel, openAlertsSignal: Int) {
    val active by vm.active.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    if (active == null) {
        // First run: nothing to manage until a server is added.
        val nav = Nav(push = {}, back = {}, selectTab = {})
        CompositionLocalProvider(LocalChrome provides Chrome(vm, nav, snackbar, bottomBar = {})) {
            ServerEditScreen(vm, existingId = null, firstRun = true)
        }
        return
    }

    var tab by remember { mutableStateOf(Tab.Dashboard) }
    val stack = remember { mutableStateListOf<Route>() }
    val nav = remember {
        Nav(
            push = { stack.add(it) },
            back = { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) },
            selectTab = {
                stack.clear()
                tab = it
            },
        )
    }
    LaunchedEffect(openAlertsSignal) {
        if (openAlertsSignal > 0) nav.selectTab(Tab.Alerts)
    }
    BackHandler(enabled = stack.isNotEmpty() || tab != Tab.Dashboard) {
        if (stack.isNotEmpty()) {
            nav.back()
        } else {
            tab = Tab.Dashboard
        }
    }

    val alerts by vm.alerts.collectAsStateWithLifecycle()
    val activeAlerts = alerts?.count { !it.dismissed } ?: 0
    val wide = LocalConfiguration.current.screenWidthDp >= 600

    val bottomBar: @Composable () -> Unit = {
        if (!wide) {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = t == tab,
                        onClick = { nav.selectTab(t) },
                        icon = { TabIcon(t, t == tab, if (t == Tab.Alerts) activeAlerts else 0) },
                        label = { Text(t.label, maxLines = 1) },
                    )
                }
            }
        }
    }

    CompositionLocalProvider(LocalChrome provides Chrome(vm, nav, snackbar, bottomBar)) {
        Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (wide && stack.isEmpty()) {
                NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Spacer(Modifier.height(12.dp))
                    Tab.entries.forEach { t ->
                        NavigationRailItem(
                            selected = t == tab,
                            onClick = { nav.selectTab(t) },
                            icon = { TabIcon(t, t == tab, if (t == Tab.Alerts) activeAlerts else 0) },
                            label = { Text(t.label) },
                        )
                    }
                }
            }
            Box(Modifier.weight(1f)) {
                val top = stack.lastOrNull()
                // Keeps each screen's scroll position when you go into a detail screen and back.
                val saveable = rememberSaveableStateHolder()
                AnimatedContent(
                    targetState = top ?: tab,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "screen",
                ) { target ->
                    saveable.SaveableStateProvider(target.toString()) {
                    when (target) {
                        Tab.Dashboard -> DashboardScreen()
                        Tab.Storage -> StorageScreen()
                        Tab.Apps -> AppsScreen()
                        Tab.Alerts -> AlertsScreen()
                        Tab.More -> MoreScreen()
                        is Route.PoolDetail -> PoolDetailScreen(target.name)
                        is Route.Snapshots -> SnapshotsScreen(target.dataset)
                        is Route.AppDetail -> AppDetailScreen(target.name)
                        is Route.Logs -> LogsScreen(target.app, target.containerId, target.title)
                        Route.Catalog -> CatalogScreen()
                        Route.AutoUpdate -> AutoUpdateScreen()
                        is Route.Install -> InstallScreen(target.name, target.train)
                        is Route.EditApp -> EditAppScreen(target.name)
                        is Route.Yaml -> YamlScreen(target.appName)
                        Route.Services -> ServicesScreen()
                        Route.Shares -> SharesScreen()
                        is Route.ShareEdit -> ShareEditScreen(target.kind, target.id)
                        is Route.DatasetDetail -> DatasetDetailScreen(target.id)
                        Route.Protection -> ProtectionScreen()
                        Route.Network -> NetworkScreen()
                        Route.Users -> UsersScreen()
                        is Route.UserEdit -> UserEditScreen(target.id)
                        Route.ApiKeys -> ApiKeysScreen()
                        Route.Certificates -> CertificatesScreen()
                        Route.Virtualization -> VirtualizationScreen()
                        Route.Reporting -> ReportingScreen()
                        Route.Update -> UpdateScreen()
                        Route.BootEnvs -> BootEnvScreen()
                        Route.Jobs -> JobsScreen()
                        Route.Audit -> AuditScreen()
                        is Route.Shell -> ShellScreen(target.appName, target.containerId, target.title)
                        Route.Power -> PowerScreen()
                        Route.Servers -> ServersScreen()
                        is Route.ServerEdit -> ServerEditScreen(vm, existingId = target.id, firstRun = false)
                        Route.Settings -> SettingsScreen()
                        else -> DashboardScreen()
                    }
                    }
                }
            }
        }
        ConnectionDialogs(vm)
    }
}

@Composable
private fun TabIcon(t: Tab, selected: Boolean, badge: Int) {
    val icon = if (selected) t.selectedIcon else t.icon
    if (badge > 0) {
        BadgedBox(badge = { Badge { Text(if (badge > 99) "99+" else badge.toString()) } }) {
            Icon(icon, contentDescription = t.label)
        }
    } else {
        Icon(icon, contentDescription = t.label)
    }
}

/**
 * Standard screen frame: top bar (with back arrow on detail screens), connection banner,
 * bottom navigation on the main tabs, and snackbars.
 */
@Composable
fun ScreenScaffold(
    title: String,
    subtitle: String? = null,
    showBack: Boolean = false,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
    fab: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val chrome = LocalChrome.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (subtitle != null) {
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (showBack) {
                        IconButton(onClick = chrome.nav.back) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = { if (!showBack) chrome.bottomBar() },
        snackbarHost = { SnackbarHost(chrome.snackbar) },
        floatingActionButton = fab,
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            ConnectionBanner(chrome.vm, chrome.nav)
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) { content() }
        }
    }
}

@Composable
private fun ConnectionBanner(vm: AppViewModel, nav: Nav) {
    val conn by vm.conn.collectAsStateWithLifecycle()
    val active by vm.active.collectAsStateWithLifecycle()
    val status = AppColors.status
    when (val c = conn) {
        ConnState.Connecting -> Column(Modifier.fillMaxWidth()) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(
                "Connecting to ${active?.displayName ?: "server"}…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        is ConnState.Retrying -> BannerRow(
            text = if (c.inSeconds > 0) "${c.message} Retrying in ${c.inSeconds}s." else "${c.message} Reconnecting…",
            color = status.warn,
            actionLabel = "Retry now",
            onAction = vm::reconnect,
        )
        is ConnState.Failed -> BannerRow(
            text = c.message,
            color = status.bad,
            actionLabel = if (c.authProblem) "Edit server" else "Retry",
            onAction = {
                if (c.authProblem) nav.push(Route.ServerEdit(active?.id)) else vm.reconnect()
            },
        )
        else -> Unit
    }
}

@Composable
private fun BannerRow(text: String, color: androidx.compose.ui.graphics.Color, actionLabel: String, onAction: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        TextButton(onClick = onAction) { Text(actionLabel) }
    }
}

@Composable
private fun ConnectionDialogs(vm: AppViewModel) {
    val conn by vm.conn.collectAsStateWithLifecycle()
    when (val c = conn) {
        is ConnState.Untrusted -> TrustDialog(c.info, onTrust = { vm.trustCertificate(c.info) }, onCancel = vm::rejectCertificate)
        is ConnState.NeedsOtp -> OtpDialog(c.error, onSubmit = vm::submitOtp, onCancel = vm::cancelOtp)
        else -> Unit
    }
}

@Composable
fun TrustDialog(info: CertInfo, onTrust: () -> Unit, onCancel: () -> Unit) {
    val status = AppColors.status
    AlertDialog(
        onDismissRequest = onCancel,
        icon = { Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = if (info.changed) status.bad else status.warn) },
        title = { Text(if (info.changed) "Certificate changed" else "Trust this server?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (info.changed) {
                        "The server is using a different certificate than the one you approved. That's expected after you renew or replace it in TrueNAS. If you didn't, someone may be intercepting the connection."
                    } else if (info.selfSigned) {
                        "The server uses a self-signed certificate (TrueNAS does this by default), so your phone can't vouch for it. Check the fingerprint matches the one in TrueNAS under Credentials → Certificates, then trust it. You'll only be asked again if it changes."
                    } else {
                        "Your phone doesn't trust this server's certificate: ${info.reason}. Trust it only if you recognise it."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text("SHA-256 fingerprint", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(info.fingerprint, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                Text("Issued to: ${info.subject}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Expires: ${Format.date(info.notAfterMillis)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onTrust) { Text("Trust", fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

@Composable
fun OtpDialog(error: String?, onSubmit: (String) -> Unit, onCancel: () -> Unit) {
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Two-factor code") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Enter the 6-digit code from your authenticator app.", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = code,
                    onValueChange = { v ->
                        code = v.filter { it.isDigit() }.take(8)
                        if (code.length == 6) onSubmit(code)
                    },
                    singleLine = true,
                    isError = error != null,
                    supportingText = if (error != null) {
                        { Text(error) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    textStyle = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSubmit(code) }, enabled = code.length >= 6) { Text("Verify") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

/** Content padding for scrolling lists inside [ScreenScaffold]. */
val ListPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp)
