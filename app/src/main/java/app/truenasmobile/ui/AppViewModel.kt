package app.truenasmobile.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.truenasmobile.api.Alert
import app.truenasmobile.api.App
import app.truenasmobile.api.AppSchema
import app.truenasmobile.api.AppStat
import app.truenasmobile.api.AutoUpdate
import app.truenasmobile.api.autoUpdate
import app.truenasmobile.api.runAutoUpdateNow
import app.truenasmobile.api.saveAutoUpdate
import app.truenasmobile.api.CatalogApp
import app.truenasmobile.api.Dataset
import app.truenasmobile.api.Disk
import app.truenasmobile.api.JobProgress
import app.truenasmobile.api.LoginResult
import app.truenasmobile.api.NfsShare
import app.truenasmobile.api.Parse
import app.truenasmobile.api.Pool
import app.truenasmobile.api.Realtime
import app.truenasmobile.api.Schema
import app.truenasmobile.api.ServiceEntry
import app.truenasmobile.api.SmbShare
import app.truenasmobile.api.Snapshot
import app.truenasmobile.api.SystemInfo
import app.truenasmobile.api.TrueNasApi
import app.truenasmobile.api.runningJobCount
import app.truenasmobile.api.Yaml
import app.truenasmobile.data.AuthMethod
import app.truenasmobile.data.Connector
import app.truenasmobile.data.SecretBox
import app.truenasmobile.data.ServerProfile
import app.truenasmobile.data.Settings
import app.truenasmobile.data.Store
import app.truenasmobile.update.UpdateWorker
import app.truenasmobile.net.CertInfo
import app.truenasmobile.net.DisconnectedException
import app.truenasmobile.net.Endpoint
import app.truenasmobile.net.RpcEvent
import app.truenasmobile.net.str
import app.truenasmobile.work.AlertWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import org.json.JSONObject

sealed interface ConnState {
    data object Idle : ConnState
    data object Connecting : ConnState
    data object Connected : ConnState
    data class Retrying(val message: String, val inSeconds: Int) : ConnState
    data class Failed(val message: String, val authProblem: Boolean) : ConnState
    data class Untrusted(val info: CertInfo) : ConnState
    data class NeedsOtp(val error: String?) : ConnState
}

/** State of the "Test & save" check on the server setup screen. */
sealed interface ServerTest {
    data object Idle : ServerTest
    data object Running : ServerTest
    data class Untrusted(val info: CertInfo) : ServerTest
    data class NeedsOtp(val error: String?) : ServerTest
    data class Failed(val message: String) : ServerTest
    data class Success(val hostname: String, val version: String) : ServerTest
}

enum class AppAction(val verb: String, val doneVerb: String) {
    START("Starting", "started"),
    STOP("Stopping", "stopped"),
    RESTART("Restarting", "restarted"),
    UPGRADE("Updating", "updated"),
    PULL_IMAGES("Pulling images for", "updated"),
}

sealed interface FormLoad {
    data object Loading : FormLoad
    data class Ready(val data: AppFormData) : FormLoad
    data class Text(val text: String) : FormLoad
    data class Failed(val message: String) : FormLoad
}

/** What the install / edit form shows and starts from. */
data class AppFormData(
    val editing: Boolean,
    val appName: String,
    val title: String,
    val iconUrl: String?,
    val description: String,
    val home: String?,
    val versionLabel: String,
    val schema: AppSchema,
    val values: Map<String, Any?>,
    val catalogName: String?,
    val train: String?,
    val version: String?,
)

/** An install in progress (or just finished/failed) from the install screen. */
data class InstallRun(
    val appName: String,
    val label: String,
    val error: String? = null,
    val done: Boolean = false,
) {
    val running: Boolean get() = !done && error == null
}

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val store = Store(application)

    private val _servers = MutableStateFlow(store.servers())
    val servers: StateFlow<List<ServerProfile>> = _servers.asStateFlow()
    private val _active = MutableStateFlow(store.activeServer())
    val active: StateFlow<ServerProfile?> = _active.asStateFlow()
    private val _settings = MutableStateFlow(store.settings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    /** Automatic app updates (a cron job on the server); null until loaded. */
    val autoUpdate = MutableStateFlow<AutoUpdate?>(null)

    /** The user's own web addresses for apps on the active server: app name → link label → URL. */
    val appLinks = MutableStateFlow<Map<String, Map<String, String>>>(emptyMap())

    init {
        viewModelScope.launch {
            _active.collect { p -> appLinks.value = p?.let { store.appLinks(it.id) } ?: emptyMap() }
        }
    }

    val conn = MutableStateFlow<ConnState>(ConnState.Idle)
    val system = MutableStateFlow<SystemInfo?>(null)
    val realtime = MutableStateFlow<Realtime?>(null)
    val cpuHistory = MutableStateFlow<List<Float>>(emptyList())
    val rxHistory = MutableStateFlow<List<Float>>(emptyList())
    val txHistory = MutableStateFlow<List<Float>>(emptyList())
    val pools = MutableStateFlow<List<Pool>?>(null)
    val datasets = MutableStateFlow<List<Dataset>?>(null)
    val disks = MutableStateFlow<List<Disk>?>(null)
    val apps = MutableStateFlow<List<App>?>(null)
    val appStats = MutableStateFlow<Map<String, AppStat>>(emptyMap())
    val alerts = MutableStateFlow<List<Alert>?>(null)
    val services = MutableStateFlow<List<ServiceEntry>?>(null)
    val smbShares = MutableStateFlow<List<SmbShare>?>(null)
    val nfsShares = MutableStateFlow<List<NfsShare>?>(null)
    val rebootReasons = MutableStateFlow<List<String>>(emptyList())
    val snapshotsDataset = MutableStateFlow<String?>(null)
    val snapshots = MutableStateFlow<List<Snapshot>?>(null)
    val logLines = MutableStateFlow<List<String>>(emptyList())
    val logStatus = MutableStateFlow<String?>(null)
    val serverTest = MutableStateFlow<ServerTest>(ServerTest.Idle)
    val catalog = MutableStateFlow<List<CatalogApp>?>(null)
    val catalogError = MutableStateFlow<String?>(null)
    val appForm = MutableStateFlow<FormLoad>(FormLoad.Loading)
    val yaml = MutableStateFlow<FormLoad>(FormLoad.Loading)
    val usedPorts = MutableStateFlow<Set<Int>>(emptySet())
    val install = MutableStateFlow<InstallRun?>(null)

    /** Operations in progress, keyed like "app:jellyfin" → "Stopping… 40%". */
    val busy = MutableStateFlow<Map<String, String>>(emptyMap())
    /** Jobs running on the server right now (like the web UI's task manager badge). */
    val runningJobs = MutableStateFlow(0)
    val refreshing = MutableStateFlow(false)

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val messages: SharedFlow<String> = _messages

    private var api: TrueNasApi? = null
    private var sessionJob: Job? = null
    private var stopJob: Job? = null
    private var lastError = ""
    private var otpWaiter: CompletableDeferred<String?>? = null

    /** Session tokens (memory only) so a reconnect doesn't need a fresh 2FA code. */
    private val tokens = mutableMapOf<String, String>()
    private val debouncers = mutableMapOf<String, Job>()

    // ------------------------------------------------------------------ lifecycle

    fun onForeground() {
        stopJob?.cancel()
        stopJob = null
        ensureSession()
    }

    /** Keep the connection a little while in case the user comes straight back. */
    fun onBackground() {
        stopJob?.cancel()
        stopJob = viewModelScope.launch {
            delay(20_000)
            endSession()
            conn.value = ConnState.Idle
        }
    }

    fun reconnect() {
        endSession()
        ensureSession()
    }

    private fun ensureSession() {
        if (sessionJob?.isActive == true) return
        val profile = _active.value
        if (profile == null) {
            conn.value = ConnState.Idle
            return
        }
        sessionJob = viewModelScope.launch { runSession(profile) }
    }

    private fun endSession() {
        sessionJob?.cancel()
        sessionJob = null
        otpWaiter?.complete(null)
        otpWaiter = null
        api?.rpc?.close()
        api = null
        stopLogsInternal()
    }

    private fun clearData() {
        system.value = null
        realtime.value = null
        cpuHistory.value = emptyList()
        rxHistory.value = emptyList()
        txHistory.value = emptyList()
        pools.value = null
        datasets.value = null
        disks.value = null
        apps.value = null
        appStats.value = emptyMap()
        autoUpdate.value = null
        alerts.value = null
        services.value = null
        smbShares.value = null
        nfsShares.value = null
        rebootReasons.value = emptyList()
        snapshots.value = null
        snapshotsDataset.value = null
        busy.value = emptyMap()
        catalog.value = null
        catalogError.value = null
        install.value = null
    }

    private suspend fun runSession(profile: ServerProfile) {
        var attempt = 0
        var otpError: String? = null
        while (true) {
            conn.value = if (attempt == 0) ConnState.Connecting else ConnState.Retrying(lastError, 0)
            val secret = profile.secret()
            if (secret == null) {
                conn.value = ConnState.Failed("The saved password or key couldn't be read. Edit the server and enter it again.", true)
                return
            }
            when (val outcome = Connector.open(profile, secret, tokens[profile.id])) {
                is Connector.Outcome.Ok -> {
                    attempt = 0
                    runConnected(outcome.api)
                    lastError = lastError.ifBlank { "Connection lost" }
                }
                is Connector.Outcome.NeedsOtp -> {
                    val sessionApi = outcome.api
                    conn.value = ConnState.NeedsOtp(otpError)
                    val waiter = CompletableDeferred<String?>()
                    otpWaiter = waiter
                    val code = waiter.await()
                    otpWaiter = null
                    if (code == null) {
                        sessionApi.rpc.close()
                        conn.value = ConnState.Failed("Sign-in cancelled.", true)
                        return
                    }
                    conn.value = ConnState.Connecting
                    val result = try {
                        sessionApi.loginOtp(code)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        LoginResult.Failed(Connector.friendly(e))
                    }
                    if (result == LoginResult.Success) {
                        otpError = null
                        sessionApi.generateToken()?.let { tokens[profile.id] = it }
                        attempt = 0
                        runConnected(sessionApi)
                        lastError = lastError.ifBlank { "Connection lost" }
                    } else {
                        sessionApi.rpc.close()
                        otpError = "That code didn't work. Try the current one."
                        continue // straight back to a fresh sign-in and code prompt
                    }
                }
                is Connector.Outcome.Untrusted -> {
                    conn.value = ConnState.Untrusted(outcome.info)
                    return
                }
                is Connector.Outcome.Failed -> {
                    if (outcome.authProblem) {
                        tokens.remove(profile.id)
                        conn.value = ConnState.Failed(outcome.message, true)
                        return
                    }
                    lastError = outcome.message
                }
            }
            attempt++
            val wait = minOf(30, 1 shl minOf(attempt, 5))
            for (s in wait downTo 1) {
                conn.value = ConnState.Retrying(lastError, s)
                delay(1000)
            }
        }
    }

    private suspend fun runConnected(sessionApi: TrueNasApi) {
        api = sessionApi
        lastError = ""
        conn.value = ConnState.Connected
        try {
            coroutineScope {
                launch { sessionApi.rpc.events.collect { onEvent(it) } }
                launch {
                    for (event in listOf("reporting.realtime", "app.stats", "alert.list", "app.query", "pool.query", "service.query", "core.get_jobs")) {
                        sessionApi.subscribe(event)
                    }
                }
                launch { refreshAllInternal() }
                launch {
                    while (true) {
                        delay(60_000)
                        load(pools) { it.pools() }
                    }
                }
                val err = sessionApi.rpc.closed.await()
                lastError = err?.let { Connector.friendly(it) } ?: "Connection closed"
                coroutineContext.cancelChildren()
            }
        } finally {
            if (api === sessionApi) api = null
            sessionApi.rpc.close()
            if (logKey != null) {
                stopLogsInternal()
                logStatus.value = "Connection lost. Tap restart to reopen the logs."
            }
        }
    }

    // ------------------------------------------------------------------ events

    private fun onEvent(e: RpcEvent) {
        val c = e.collection
        when {
            c == "reporting.realtime" -> (e.fields as? JSONObject)?.let(Parse::realtime)?.let(::pushRealtime)
            c == "app.stats" -> appStats.value = Parse.appStats(e.fields)
            c == "alert.list" -> debounce("alerts") { load(alerts) { it.alerts() }; markAlertsSeen() }
            c == "app.query" -> debounce("apps") { load(apps) { it.apps(hostIp()) } }
            c == "pool.query" -> debounce("pools") { load(pools) { it.pools() } }
            c == "service.query" -> debounce("services") { load(services) { it.services() } }
            c == "core.get_jobs" -> debounce("jobs") { load(runningJobs) { it.runningJobCount() } }
            c.startsWith("app.container_log_follow") -> onLogEvent(e)
        }
    }

    private fun pushRealtime(r: Realtime) {
        realtime.value = r
        cpuHistory.update { (it + r.cpuPercent.toFloat()).takeLast(HISTORY) }
        rxHistory.update { (it + r.rxTotal.toFloat()).takeLast(HISTORY) }
        txHistory.update { (it + r.txTotal.toFloat()).takeLast(HISTORY) }
    }

    private fun debounce(key: String, block: suspend () -> Unit) {
        debouncers[key]?.cancel()
        debouncers[key] = viewModelScope.launch {
            delay(600)
            block()
        }
    }

    // ------------------------------------------------------------------ loading

    private suspend fun <T> load(target: MutableStateFlow<T>, report: Boolean = false, block: suspend (TrueNasApi) -> T) {
        val a = api ?: return
        try {
            target.value = block(a)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (report && a.rpc.isOpen) toast(Connector.friendly(e))
        }
    }

    private suspend fun refreshAllInternal(): Unit = coroutineScope {
        launch { load(system) { it.systemInfo() } }
        launch { load(pools) { it.pools() } }
        launch { load(apps) { it.apps(hostIp()) } }
        launch {
            load(alerts) { it.alerts() }
            markAlertsSeen()
        }
        launch { load(services) { it.services() } }
        launch { load(rebootReasons) { it.rebootRequiredReasons() } }
        launch { load(runningJobs) { it.runningJobCount() } }
        if (datasets.value != null) launch { load(datasets) { it.datasets() } }
        if (disks.value != null) launch { load(disks) { it.disks() } }
        if (smbShares.value != null) launch { load(smbShares) { it.smbShares() } }
        if (nfsShares.value != null) launch { load(nfsShares) { it.nfsShares() } }
        val ds = snapshotsDataset.value
        if (ds != null) launch { load(snapshots) { it.snapshots(ds) } }
    }

    /** Pull-to-refresh. */
    fun refresh() {
        if (api == null) {
            reconnect()
            return
        }
        viewModelScope.launch {
            refreshing.value = true
            try {
                refreshAllInternal()
            } finally {
                refreshing.value = false
            }
        }
    }

    fun loadDatasets(force: Boolean = false) {
        if (!force && datasets.value != null) return
        viewModelScope.launch { load(datasets, report = force) { it.datasets() } }
    }

    fun loadDisks(force: Boolean = false) {
        if (!force && disks.value != null) return
        viewModelScope.launch { load(disks, report = force) { it.disks() } }
    }

    fun loadShares(force: Boolean = false) {
        viewModelScope.launch {
            if (force || smbShares.value == null) load(smbShares, report = force) { it.smbShares() }
            if (force || nfsShares.value == null) load(nfsShares, report = force) { it.nfsShares() }
        }
    }

    fun loadSnapshots(dataset: String) {
        if (snapshotsDataset.value != dataset) {
            snapshots.value = null
            snapshotsDataset.value = dataset
        }
        viewModelScope.launch { load(snapshots, report = true) { it.snapshots(dataset) } }
    }

    // ------------------------------------------------------------------ app catalog

    private fun hostIp(): String? = _active.value?.endpoint?.bareHost

    fun loadCatalog(force: Boolean = false) {
        if (!force && catalog.value != null) return
        val a = api ?: return
        catalogError.value = null
        viewModelScope.launch {
            try {
                catalog.value = a.availableApps()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                catalogError.value = Connector.friendly(e)
            }
        }
    }

    private var formKey: String? = null

    /** Loads the install form for a catalog app. */
    fun loadInstallForm(name: String, train: String) {
        val key = "install:$train/$name"
        if (formKey == key && appForm.value is FormLoad.Ready) return
        formKey = key
        appForm.value = FormLoad.Loading
        val a = api ?: run {
            appForm.value = FormLoad.Failed("Not connected to the server")
            return
        }
        viewModelScope.launch {
            appForm.value = try {
                val d = a.catalogAppDetails(name, train)
                val taken = apps.value?.map { it.name }?.toSet() ?: emptySet()
                usedPorts.value = a.usedPorts()
                FormLoad.Ready(
                    AppFormData(
                        editing = false,
                        appName = Schema.suggestName(d.name, taken),
                        title = d.title,
                        iconUrl = d.iconUrl,
                        description = d.description,
                        home = d.home,
                        versionLabel = "Version ${d.humanVersion} · ${d.train}",
                        schema = d.schema,
                        values = Schema.fillGeneratedSecrets(d.schema.questions, d.defaults),
                        catalogName = d.name,
                        train = d.train,
                        version = d.version,
                    ),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                FormLoad.Failed(Connector.friendly(e))
            }
        }
    }

    /** Loads an installed app's current settings into the same form. */
    fun loadEditForm(appName: String) {
        val key = "edit:$appName"
        formKey = key
        appForm.value = FormLoad.Loading
        val a = api ?: run {
            appForm.value = FormLoad.Failed("Not connected to the server")
            return
        }
        viewModelScope.launch {
            appForm.value = try {
                val e = a.appForEdit(appName)
                usedPorts.value = a.usedPorts()
                if (e.schema.questions.isEmpty()) {
                    FormLoad.Failed("This app's settings can't be edited here. Use the web UI.")
                } else {
                    FormLoad.Ready(
                        AppFormData(
                            editing = true,
                            appName = appName,
                            title = e.app.title,
                            iconUrl = e.app.iconUrl,
                            description = e.app.description ?: "",
                            home = null,
                            versionLabel = "Version ${e.app.humanVersion}",
                            schema = e.schema,
                            values = Schema.withDefaults(
                                e.schema.questions,
                                Schema.onlyQuestions(e.schema.questions, e.values),
                            ),
                            catalogName = null,
                            train = null,
                            version = null,
                        ),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                FormLoad.Failed(Connector.friendly(e))
            }
        }
    }

    /** Installs (or saves changes to) the app in the form. Progress shows in [install]. */
    fun submitAppForm(form: AppFormData, appName: String, values: Map<String, Any?>) {
        val a = api ?: return toast("Not connected to the server")
        if (install.value?.running == true) return
        val verb = if (form.editing) "Saving" else "Installing"
        install.value = InstallRun(appName, "$verb…")
        val clean = Schema.forSubmit(form.schema.questions, values)
        viewModelScope.launch {
            try {
                val progress: (JobProgress) -> Unit = { p ->
                    val head = progressLabel(verb, p)
                    val detail = p.description?.trim()?.takeIf { it.isNotEmpty() && !it.equals(verb, true) }
                    install.value = InstallRun(appName, if (detail != null) "$head\n$detail" else head)
                }
                if (form.editing) {
                    a.updateApp(appName, clean, progress)
                    toast("${form.title} saved and restarted")
                } else {
                    a.installApp(appName, form.catalogName!!, form.train!!, form.version!!, clean, progress)
                    toast("${form.title} installed as $appName")
                    catalog.update { list ->
                        list?.map { if (it.name == form.catalogName && it.train == form.train) it.copy(installed = true) else it }
                    }
                }
                install.value = InstallRun(appName, "Done", done = true)
                load(apps) { it.apps(hostIp()) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                install.value = InstallRun(appName, "", error = Schema.describeErrors(form.schema.questions, jobError(e, form.editing)))
            }
        }
    }

    private fun jobError(e: Exception, editing: Boolean): String =
        if (e is DisconnectedException) {
            if (editing) "Lost the connection while saving. The change may still finish; check the app in a minute."
            else "Lost the connection while installing. The install may still finish; check the Apps tab in a minute."
        } else {
            Connector.friendly(e)
        }

    fun clearInstall() {
        if (install.value?.running != true) install.value = null
    }

    // Custom apps (Docker Compose YAML)

    fun loadYaml(appName: String) {
        yaml.value = FormLoad.Loading
        val a = api ?: run {
            yaml.value = FormLoad.Failed("Not connected to the server")
            return
        }
        viewModelScope.launch {
            yaml.value = try {
                FormLoad.Text(Yaml.write(a.appComposeConfig(appName)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                FormLoad.Failed(Connector.friendly(e))
            }
        }
    }

    fun submitYaml(appName: String, text: String, editing: Boolean) {
        val a = api ?: return toast("Not connected to the server")
        if (install.value?.running == true) return
        val verb = if (editing) "Saving" else "Installing"
        install.value = InstallRun(appName, "$verb…")
        viewModelScope.launch {
            try {
                val progress: (JobProgress) -> Unit = { p -> install.value = InstallRun(appName, progressLabel(verb, p)) }
                if (editing) a.updateCustomApp(appName, text, progress) else a.installCustomApp(appName, text, progress)
                toast(if (editing) "$appName saved and restarted" else "$appName installed")
                install.value = InstallRun(appName, "Done", done = true)
                load(apps) { it.apps(hostIp()) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                install.value = InstallRun(appName, "", error = jobError(e, editing))
            }
        }
    }

    fun deleteApp(app: App, removeData: Boolean, onDone: () -> Unit) {
        perform("app:${app.name}", "Deleting", "${app.title} deleted", after = { a -> apps.value = a.apps(hostIp()) }) { a, p ->
            a.deleteApp(app.name, removeData, p)
            appLinks.value[app.name]?.keys?.toList()?.forEach { setAppLink(app.name, it, null) }
            onDone()
        }
    }

    // Automatic app updates

    /** Loads the automatic-update settings from the server (kept in [autoUpdate]). */
    fun loadAutoUpdate() {
        val a = api ?: return
        viewModelScope.launch {
            try {
                autoUpdate.value = a.autoUpdate()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Leave it as it was; the screen offers a retry.
            }
        }
    }

    fun saveAutoUpdate(config: AutoUpdate, done: String, after: () -> Unit = {}) {
        perform("autoupdate", "Saving", done, after = { after() }) { a, _ ->
            autoUpdate.value = a.saveAutoUpdate(config)
        }
    }

    /** Includes or leaves out one app, when automatic updates are on. */
    fun setAppAutoUpdate(app: App, on: Boolean) {
        val current = autoUpdate.value ?: return
        if (!current.active) return
        saveAutoUpdate(
            current.withApp(app.name, on),
            if (on) "${app.title} will update automatically" else "${app.title} won't update automatically",
        )
    }

    fun runAutoUpdateNow() {
        val id = autoUpdate.value?.id ?: return
        perform("autoupdate:run", "Starting", "Checking for app updates on the server. Progress shows under Jobs.") { a, _ ->
            a.runAutoUpdateNow(id)
        }
    }

    /** Opens the app's link [label] at [url] instead of the address TrueNAS reports (null goes back to it). */
    fun setAppLink(appName: String, label: String, url: String?) {
        val serverId = _active.value?.id ?: return
        val all = appLinks.value.toMutableMap()
        val forApp = all[appName].orEmpty().toMutableMap()
        if (url == null) forApp.remove(label) else forApp[label] = url
        if (forApp.isEmpty()) all.remove(appName) else all[appName] = forApp
        appLinks.value = all
        store.saveAppLinks(serverId, all)
    }

    suspend fun rollbackVersions(appName: String): List<String> = api?.rollbackVersions(appName) ?: emptyList()

    fun rollbackApp(app: App, version: String) {
        perform("app:${app.name}", "Rolling back", "${app.title} rolled back to $version", after = { a -> apps.value = a.apps(hostIp()) }) { a, p ->
            a.rollbackApp(app.name, version, p)
        }
    }

    // Screens that load their own data

    /** Runs [block] with the live connection. Throws when not connected. */
    suspend fun <T> withApi(block: suspend (TrueNasApi) -> T): T = block(requireApi())

    /** An action from a screen: shows progress under [key], a message when done or failed, then [after]. */
    fun act(
        key: String,
        verb: String,
        done: String?,
        after: () -> Unit = {},
        block: suspend (TrueNasApi, (JobProgress) -> Unit) -> Unit,
    ) = perform(key, verb, done, after = { after() }, block = block)

    // Folder picker

    private fun requireApi(): TrueNasApi = api ?: throw DisconnectedException("Not connected to the server")

    suspend fun listDirs(path: String) = requireApi().listDirs(path)

    suspend fun mkdir(path: String) = requireApi().mkdir(path)

    suspend fun createDataset(name: String, forApps: Boolean) {
        requireApi().createDataset(name, if (forApps) "APPS" else "GENERIC")
        datasets.value?.let { loadDatasets(force = true) }
    }

    private fun markAlertsSeen() {
        val profile = _active.value ?: return
        val list = alerts.value ?: return
        store.setSeenAlerts(profile.id, list.filter { !it.dismissed }.map { it.uuid }.toSet())
    }

    // ------------------------------------------------------------------ actions

    private fun toast(msg: String) {
        _messages.tryEmit(msg)
    }

    fun showMessage(msg: String) = toast(msg)

    private fun setBusy(key: String, label: String?) {
        busy.update { if (label == null) it - key else it + (key to label) }
    }

    private fun progressLabel(verb: String, p: JobProgress): String =
        buildString {
            append(verb)
            append('…')
            p.percent?.takeIf { it in 1..99 }?.let { append(" $it%") }
        }

    /** Runs an action against the server with a busy marker, an error toast and a follow-up refresh. */
    private fun perform(
        key: String,
        verb: String,
        done: String?,
        after: suspend (TrueNasApi) -> Unit = {},
        block: suspend (TrueNasApi, (JobProgress) -> Unit) -> Unit,
    ) {
        val a = api
        if (a == null) {
            toast("Not connected to the server")
            return
        }
        if (busy.value.containsKey(key)) return
        viewModelScope.launch {
            setBusy(key, "$verb…")
            try {
                block(a) { p -> setBusy(key, progressLabel(verb, p)) }
                if (done != null) toast(done)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toast(Connector.friendly(e))
            } finally {
                setBusy(key, null)
                try {
                    after(a)
                } catch (_: Exception) {
                }
            }
        }
    }

    fun appAction(app: App, action: AppAction) {
        perform(
            key = "app:${app.name}",
            verb = action.verb,
            done = "${app.title} ${action.doneVerb}",
            after = { a -> apps.value = a.apps(hostIp()) },
        ) { a, progress ->
            when (action) {
                AppAction.START -> a.startApp(app.name, progress)
                AppAction.STOP -> a.stopApp(app.name, progress)
                AppAction.RESTART -> a.restartApp(app.name, progress)
                AppAction.UPGRADE -> a.upgradeApp(app.name, progress)
                AppAction.PULL_IMAGES -> a.pullImages(app.name, progress)
            }
        }
    }

    /** Updates apps one at a time, so the server isn't pulling and redeploying everything at once. */
    fun updateAllApps() {
        val a = api ?: return toast("Not connected to the server")
        val list = apps.value?.filter { it.hasUpdate && !busy.value.containsKey("app:${it.name}") } ?: return
        if (list.isEmpty()) return
        list.forEach { setBusy("app:${it.name}", "Waiting to update…") }
        viewModelScope.launch {
            var done = 0
            val failed = mutableListOf<String>()
            for (app in list) {
                val key = "app:${app.name}"
                try {
                    if (app.upgradeAvailable) {
                        setBusy(key, "${AppAction.UPGRADE.verb}…")
                        a.upgradeApp(app.name) { p -> setBusy(key, progressLabel(AppAction.UPGRADE.verb, p)) }
                    } else {
                        setBusy(key, "${AppAction.PULL_IMAGES.verb}…")
                        a.pullImages(app.name) { p -> setBusy(key, progressLabel(AppAction.PULL_IMAGES.verb, p)) }
                    }
                    done++
                } catch (e: CancellationException) {
                    list.forEach { setBusy("app:${it.name}", null) }
                    throw e
                } catch (e: Exception) {
                    failed += app.title
                } finally {
                    setBusy(key, null)
                }
            }
            toast(
                if (failed.isEmpty()) "Updated $done apps"
                else "Updated $done of ${list.size}. Failed: ${failed.joinToString()}",
            )
            load(apps) { it.apps(hostIp()) }
        }
    }

    fun scrub(pool: Pool, action: String) {
        val verb = when (action) {
            "START" -> "Starting scrub"
            "PAUSE" -> "Pausing scrub"
            else -> "Stopping scrub"
        }
        val done = when (action) {
            "START" -> "Scrub started on ${pool.name}"
            "PAUSE" -> "Scrub paused"
            else -> "Scrub stopped"
        }
        perform("pool:${pool.name}", verb, done, after = { a -> pools.value = a.pools() }) { a, p ->
            a.scrub(pool.name, action, p)
        }
    }

    fun dismissAlert(alert: Alert) {
        perform("alert:${alert.uuid}", "Dismissing", null, after = { a -> alerts.value = a.alerts() }) { a, _ ->
            a.dismissAlert(alert.uuid)
        }
    }

    fun restoreAlert(alert: Alert) {
        perform("alert:${alert.uuid}", "Restoring", null, after = { a -> alerts.value = a.alerts() }) { a, _ ->
            a.restoreAlert(alert.uuid)
        }
    }

    fun dismissAllAlerts() {
        val active = alerts.value?.filter { !it.dismissed } ?: return
        perform("alerts:all", "Dismissing", "Dismissed ${active.size} alerts", after = { a -> alerts.value = a.alerts() }) { a, _ ->
            active.forEach { a.dismissAlert(it.uuid) }
        }
    }

    fun setServiceRunning(svc: ServiceEntry, run: Boolean) {
        val name = Format.serviceName(svc.service)
        perform(
            "svc:${svc.service}",
            if (run) "Starting" else "Stopping",
            null,
            after = { a -> services.value = a.services() },
        ) { a, p ->
            val ok = a.setServiceRunning(svc.service, run, p)
            if (!ok) toast(if (run) "$name didn't start. Check its settings in the web UI." else "$name didn't stop")
        }
    }

    fun setServiceEnabled(svc: ServiceEntry, enable: Boolean) {
        perform("svcboot:${svc.service}", "Saving", null, after = { a -> services.value = a.services() }) { a, _ ->
            a.setServiceEnabled(svc, enable)
        }
    }

    fun setSmbEnabled(share: SmbShare, enabled: Boolean) {
        perform("smb:${share.id}", "Saving", null, after = { a -> smbShares.value = a.smbShares() }) { a, _ ->
            a.setSmbEnabled(share.id, enabled)
        }
    }

    fun setNfsEnabled(share: NfsShare, enabled: Boolean) {
        perform("nfs:${share.id}", "Saving", null, after = { a -> nfsShares.value = a.nfsShares() }) { a, _ ->
            a.setNfsEnabled(share.id, enabled)
        }
    }

    fun createSnapshot(dataset: String, name: String, recursive: Boolean) {
        perform("snap:$dataset", "Taking snapshot", "Snapshot $name created", after = { a ->
            if (snapshotsDataset.value == dataset) snapshots.value = a.snapshots(dataset)
        }) { a, _ ->
            a.createSnapshot(dataset, name, recursive)
        }
    }

    fun deleteSnapshot(snapshot: Snapshot) {
        perform("snapdel:${snapshot.id}", "Deleting", "Snapshot deleted", after = { a ->
            if (snapshotsDataset.value == snapshot.dataset) snapshots.value = a.snapshots(snapshot.dataset)
        }) { a, _ ->
            a.deleteSnapshot(snapshot.id)
        }
    }

    fun power(reboot: Boolean, reason: String) {
        perform("power", if (reboot) "Restarting" else "Shutting down", if (reboot) "Restarting the server…" else "Shutting down the server…") { a, _ ->
            if (reboot) a.reboot(reason) else a.shutdown(reason)
        }
    }

    // ------------------------------------------------------------------ logs

    private var logSubId: String? = null
    private var logKey: String? = null
    private var logFlushJob: Job? = null
    private val logBuffer = ArrayList<String>()

    fun startLogs(appName: String, containerId: String, tail: Int = 500) {
        stopLogsInternal()
        logLines.value = emptyList()
        val a = api
        if (a == null) {
            logStatus.value = "Not connected"
            return
        }
        logStatus.value = "Opening log stream…"
        val arg = JSONObject()
            .put("app_name", appName)
            .put("container_id", containerId)
            .put("tail_lines", tail)
            .toString()
        val key = "app.container_log_follow:$arg"
        logKey = key
        logFlushJob = viewModelScope.launch {
            val id = a.subscribe(key)
            if (logKey != key) {
                id?.let { a.unsubscribe(it) }
                return@launch
            }
            if (id == null) {
                logStatus.value = "This server couldn't open the log stream."
                return@launch
            }
            logSubId = id
            logStatus.value = null
            while (true) {
                delay(250)
                val batch = synchronized(logBuffer) {
                    if (logBuffer.isEmpty()) null else ArrayList(logBuffer).also { logBuffer.clear() }
                }
                if (batch != null) logLines.update { (it + batch).takeLast(MAX_LOG_LINES) }
            }
        }
    }

    private fun onLogEvent(e: RpcEvent) {
        if (logKey == null) return
        if (e.msg == "unsubscribed") {
            val reason = (e.fields as? JSONObject)?.str("reason")
            logStatus.value = reason?.let { "Log stream ended: $it" } ?: "Log stream ended"
            return
        }
        val f = e.fields as? JSONObject ?: return
        val data = f.str("data")?.trimEnd('\n', '\r') ?: return
        val ts = f.str("timestamp")
        val time = ts?.let { Regex("""(\d{2}:\d{2}:\d{2})""").find(it)?.value }
        val line = if (time != null) "$time  $data" else data
        synchronized(logBuffer) { logBuffer.add(line) }
    }

    fun stopLogs() = stopLogsInternal()

    private fun stopLogsInternal() {
        val id = logSubId
        val a = api
        logSubId = null
        logKey = null
        logFlushJob?.cancel()
        logFlushJob = null
        synchronized(logBuffer) { logBuffer.clear() }
        if (id != null && a != null) viewModelScope.launch { a.unsubscribe(id) }
    }

    // ------------------------------------------------------------------ sign-in prompts

    fun submitOtp(code: String) {
        otpWaiter?.complete(code)
    }

    fun cancelOtp() {
        otpWaiter?.complete(null)
    }

    fun trustCertificate(info: CertInfo) {
        val profile = _active.value ?: return
        saveServer(profile.copy(certPin = info.fingerprint), makeActive = true)
    }

    fun rejectCertificate() {
        conn.value = ConnState.Failed("The certificate wasn't trusted, so the app didn't connect.", false)
    }

    // ------------------------------------------------------------------ servers

    fun saveServer(profile: ServerProfile, makeActive: Boolean) {
        val list = _servers.value.toMutableList()
        val i = list.indexOfFirst { it.id == profile.id }
        if (i >= 0) list[i] = profile else list.add(profile)
        store.saveServers(list)
        _servers.value = list
        val wasActive = _active.value?.id == profile.id
        if (makeActive || wasActive) {
            store.activeId = profile.id
            if (!wasActive) clearData()
            _active.value = profile
            reconnect()
        }
        AlertWorker.schedule(getApplication<Application>(), _settings.value.notifyAlerts)
    }

    fun switchServer(id: String) {
        val profile = _servers.value.firstOrNull { it.id == id } ?: return
        if (profile.id == _active.value?.id) return
        endSession()
        store.activeId = id
        clearData()
        _active.value = profile
        ensureSession()
    }

    fun deleteServer(id: String) {
        val list = _servers.value.filter { it.id != id }
        store.saveServers(list)
        store.forgetServer(id)
        _servers.value = list
        tokens.remove(id)
        if (_active.value?.id == id) {
            endSession()
            clearData()
            val next = list.firstOrNull()
            store.activeId = next?.id
            _active.value = next
            if (next != null) ensureSession() else conn.value = ConnState.Idle
        }
    }

    /**
     * Builds a profile from the setup form. The secret is kept from the existing profile
     * when the field is left empty while editing.
     */
    fun draftProfile(
        existing: ServerProfile?,
        name: String,
        address: String,
        auth: AuthMethod,
        username: String,
        secret: String,
        certPin: String?,
    ): ServerProfile? {
        val enc = when {
            secret.isNotEmpty() -> SecretBox.encrypt(secret)
            existing != null && existing.auth == auth -> existing.secretEnc
            else -> return null
        }
        val addressChanged = existing != null && existing.endpoint != Endpoint.parse(address)
        return ServerProfile(
            id = existing?.id ?: UUID.randomUUID().toString(),
            name = name.trim(),
            address = address.trim(),
            auth = auth,
            username = username.trim(),
            secretEnc = enc,
            certPin = if (addressChanged) certPin.takeIf { it != existing?.certPin } else certPin,
        )
    }

    private var testApi: TrueNasApi? = null
    private var testProfile: ServerProfile? = null
    private var testJob: Job? = null

    fun testServer(profile: ServerProfile, otpError: String? = null) {
        cancelTest()
        testProfile = profile
        serverTest.value = ServerTest.Running
        testJob = viewModelScope.launch {
            val secret = profile.secret()
            if (secret == null) {
                serverTest.value = ServerTest.Failed("Enter the password or API key.")
                return@launch
            }
            when (val outcome = Connector.open(profile, secret)) {
                is Connector.Outcome.Ok -> finishTest(outcome.api)
                is Connector.Outcome.NeedsOtp -> {
                    testApi = outcome.api
                    serverTest.value = ServerTest.NeedsOtp(otpError)
                }
                is Connector.Outcome.Untrusted -> serverTest.value = ServerTest.Untrusted(outcome.info)
                is Connector.Outcome.Failed -> serverTest.value = ServerTest.Failed(outcome.message)
            }
        }
    }

    fun testOtp(code: String) {
        val a = testApi ?: return
        serverTest.value = ServerTest.Running
        testJob = viewModelScope.launch {
            val r = try {
                a.loginOtp(code)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LoginResult.Failed(Connector.friendly(e))
            }
            if (r == LoginResult.Success) {
                a.generateToken()?.let { token -> testProfile?.let { tokens[it.id] = token } }
                finishTest(a)
            } else {
                a.rpc.close()
                testApi = null
                // The server wants a fresh sign-in after a wrong code.
                val p = testProfile
                if (p != null) testServer(p, "That code didn't work. Try the current one.")
                else serverTest.value = ServerTest.Failed("That code didn't work.")
            }
        }
    }

    private suspend fun finishTest(a: TrueNasApi) {
        try {
            val info = a.systemInfo()
            serverTest.value = ServerTest.Success(info.hostname, info.version)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            serverTest.value = ServerTest.Failed(Connector.friendly(e))
        } finally {
            a.rpc.close()
            testApi = null
        }
    }

    fun cancelTest() {
        testJob?.cancel()
        testJob = null
        testApi?.rpc?.close()
        testApi = null
        serverTest.value = ServerTest.Idle
    }

    // ------------------------------------------------------------------ settings

    fun updateSettings(s: Settings) {
        store.saveSettings(s)
        _settings.value = s
        AlertWorker.schedule(getApplication<Application>(), s.notifyAlerts)
        UpdateWorker.schedule(getApplication<Application>(), s.checkUpdates)
    }

    override fun onCleared() {
        endSession()
        cancelTest()
        super.onCleared()
    }

    companion object {
        const val HISTORY = 90
        const val MAX_LOG_LINES = 3000
    }
}
