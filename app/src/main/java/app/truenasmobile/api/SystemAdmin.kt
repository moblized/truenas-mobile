package app.truenasmobile.api

import app.truenasmobile.net.RpcException
import app.truenasmobile.net.arr
import app.truenasmobile.net.bool
import app.truenasmobile.net.dateMillis
import app.truenasmobile.net.double
import app.truenasmobile.net.int
import app.truenasmobile.net.jsonOrNull
import app.truenasmobile.net.keyList
import app.truenasmobile.net.long
import app.truenasmobile.net.obj
import app.truenasmobile.net.objects
import app.truenasmobile.net.str
import app.truenasmobile.net.strings
import org.json.JSONArray
import org.json.JSONObject

// ====================================================================== network

data class IpAddr(val family: String, val address: String, val netmask: String?) {
    val text: String get() = if (netmask.isNullOrBlank()) address else "$address/$netmask"
}

data class NetInterface(
    val id: String,
    val name: String,
    val type: String,
    val description: String,
    val up: Boolean,
    val mac: String?,
    val mtu: Int?,
    val media: String?,
    val dhcp: Boolean,
    val addresses: List<IpAddr>,
    val members: List<String>,
)

data class NetConfig(
    val hostname: String,
    val domain: String,
    val nameservers: List<String>,
    val ipv4Gateway: String,
    val ipv6Gateway: String,
)

data class StaticRoute(val id: Int, val destination: String, val gateway: String, val description: String)

suspend fun TrueNasApi.interfaces(): List<NetInterface> = Parse.list(rpc.call("interface.query")).map { o ->
    val st = o.obj("state")
    val addrs = (st?.arr("aliases") ?: o.arr("aliases"))?.objects()?.mapNotNull { a ->
        val type = a.str("type") ?: ""
        if (type != "INET" && type != "INET6") return@mapNotNull null
        val addr = a.str("address") ?: return@mapNotNull null
        if (type == "INET6" && addr.startsWith("fe80")) return@mapNotNull null
        IpAddr(if (type == "INET") "IPv4" else "IPv6", addr, a.str("netmask"))
    } ?: emptyList()
    NetInterface(
        id = o.str("id") ?: o.str("name") ?: "",
        name = o.str("name") ?: "",
        type = o.str("type") ?: "",
        description = o.str("description") ?: "",
        up = st?.str("link_state") == "LINK_STATE_UP",
        mac = st?.str("link_address")?.takeIf { it.isNotBlank() },
        mtu = st?.int("mtu") ?: o.int("mtu"),
        media = st?.str("active_media_subtype")?.takeIf { it.isNotBlank() && it != "Unknown" },
        dhcp = o.bool("ipv4_dhcp"),
        addresses = addrs,
        members = (o.arr("bridge_members")?.strings() ?: emptyList()) + (o.arr("lag_ports")?.strings() ?: emptyList()),
    )
}.sortedWith(compareBy({ !it.up }, { it.name }))

suspend fun TrueNasApi.netConfig(): NetConfig {
    val o = rpc.call("network.configuration.config") as? JSONObject ?: JSONObject()
    return NetConfig(
        hostname = o.str("hostname") ?: "",
        domain = o.str("domain") ?: "",
        nameservers = listOfNotNull(o.str("nameserver1"), o.str("nameserver2"), o.str("nameserver3")).filter { it.isNotBlank() },
        ipv4Gateway = o.str("ipv4gateway") ?: "",
        ipv6Gateway = o.str("ipv6gateway") ?: "",
    )
}

suspend fun TrueNasApi.saveNetConfig(c: NetConfig) {
    val ns = c.nameservers + listOf("", "", "")
    rpc.call(
        "network.configuration.update",
        listOf(
            mapOf(
                "hostname" to c.hostname,
                "domain" to c.domain,
                "nameserver1" to ns[0],
                "nameserver2" to ns[1],
                "nameserver3" to ns[2],
                "ipv4gateway" to c.ipv4Gateway,
                "ipv6gateway" to c.ipv6Gateway,
            ),
        ),
        timeoutMs = 90_000,
    )
}

/** Where the server's addresses come from when the interface list has none (DHCP). */
suspend fun TrueNasApi.networkSummary(): Map<String, List<String>> = try {
    val o = rpc.call("network.general.summary") as? JSONObject
    val ips = o?.optJSONObject("ips") ?: JSONObject()
    ips.keyList().associateWith { k ->
        val e = ips.optJSONObject(k)
        (e?.arr("IPV4")?.strings() ?: emptyList()) + (e?.arr("IPV6")?.strings()?.filterNot { it.startsWith("fe80") } ?: emptyList())
    }
} catch (e: Exception) {
    emptyMap()
}

suspend fun TrueNasApi.staticRoutes(): List<StaticRoute> = Parse.list(rpc.call("staticroute.query")).map { o ->
    StaticRoute(o.int("id") ?: 0, o.str("destination") ?: "", o.str("gateway") ?: "", o.str("description") ?: "")
}

suspend fun TrueNasApi.addStaticRoute(destination: String, gateway: String, description: String) {
    rpc.call("staticroute.create", listOf(mapOf("destination" to destination, "gateway" to gateway, "description" to description)))
}

suspend fun TrueNasApi.deleteStaticRoute(id: Int) {
    rpc.call("staticroute.delete", listOf(id))
}

// ====================================================================== users & groups

data class LocalUser(
    val id: Int,
    val uid: Int,
    val username: String,
    val fullName: String,
    val email: String?,
    val builtin: Boolean,
    val immutable: Boolean,
    val smb: Boolean,
    val locked: Boolean,
    val home: String,
    val shell: String,
    val groupIds: List<Int>,
    val primaryGroupId: Int?,
    val sshKey: String?,
    val passwordDisabled: Boolean,
    val twoFactor: Boolean,
    val roles: List<String>,
)

data class LocalGroup(
    val id: Int,
    val gid: Int,
    val name: String,
    val builtin: Boolean,
    val smb: Boolean,
    val userIds: List<Int>,
    val roles: List<String>,
)

private fun JSONArray.ints(): List<Int> = (0 until length()).mapNotNull { (opt(it) as? Number)?.toInt() }

suspend fun TrueNasApi.localUsers(): List<LocalUser> =
    Parse.list(rpc.call("user.query", listOf(listOf(listOf("local", "=", true))), timeoutMs = 60_000)).map { o ->
        LocalUser(
            id = o.int("id") ?: 0,
            uid = o.int("uid") ?: 0,
            username = o.str("username") ?: "",
            fullName = o.str("full_name") ?: "",
            email = o.str("email")?.takeIf { it.isNotBlank() },
            builtin = o.bool("builtin"),
            immutable = o.bool("immutable"),
            smb = o.bool("smb"),
            locked = o.bool("locked"),
            home = o.str("home") ?: "",
            shell = o.str("shell") ?: "",
            groupIds = o.arr("groups")?.ints() ?: emptyList(),
            primaryGroupId = o.obj("group")?.int("id"),
            sshKey = o.str("sshpubkey")?.takeIf { it.isNotBlank() },
            passwordDisabled = o.bool("password_disabled"),
            twoFactor = o.bool("twofactor_auth_configured"),
            roles = o.arr("roles")?.strings() ?: emptyList(),
        )
    }.sortedWith(compareBy({ it.builtin }, { it.username }))

suspend fun TrueNasApi.localGroups(): List<LocalGroup> =
    Parse.list(rpc.call("group.query", listOf(listOf(listOf("local", "=", true))), timeoutMs = 60_000)).map { o ->
        LocalGroup(
            id = o.int("id") ?: 0,
            gid = o.int("gid") ?: 0,
            name = o.str("name") ?: o.str("group") ?: "",
            builtin = o.bool("builtin"),
            smb = o.bool("smb"),
            userIds = o.arr("users")?.ints() ?: emptyList(),
            roles = o.arr("roles")?.strings() ?: emptyList(),
        )
    }.sortedWith(compareBy({ it.builtin }, { it.name }))

suspend fun TrueNasApi.shellChoices(groupIds: List<Int>): List<Pair<String, String>> = try {
    val o = rpc.call("user.shell_choices", listOf(groupIds)) as? JSONObject ?: JSONObject()
    o.keyList().map { it to (o.str(it) ?: it) }
} catch (e: Exception) {
    listOf("/usr/bin/bash" to "bash", "/usr/bin/zsh" to "zsh", "/usr/sbin/nologin" to "nologin")
}

/** Creates a user, or updates one when [id] is given. Blank password keeps the current one. */
suspend fun TrueNasApi.saveUser(
    id: Int?,
    username: String,
    fullName: String,
    email: String?,
    password: String?,
    groupIds: List<Int>,
    smb: Boolean,
    shell: String?,
    home: String?,
    createHome: Boolean,
    locked: Boolean,
    sshKey: String?,
) {
    val data = LinkedHashMap<String, Any?>()
    if (id == null) {
        data["username"] = username
        data["group_create"] = true
    }
    data["full_name"] = fullName.ifBlank { username }
    data["email"] = email?.ifBlank { null }
    if (!password.isNullOrEmpty()) data["password"] = password
    data["groups"] = groupIds
    data["smb"] = smb
    if (!shell.isNullOrBlank()) data["shell"] = shell
    if (!home.isNullOrBlank()) {
        data["home"] = home
        if (createHome) data["home_create"] = true
    }
    data["locked"] = locked
    data["sshpubkey"] = sshKey?.ifBlank { null }
    if (id == null) rpc.call("user.create", listOf(data), timeoutMs = 90_000)
    else rpc.call("user.update", listOf(id, data), timeoutMs = 90_000)
}

suspend fun TrueNasApi.deleteUser(id: Int, deleteGroup: Boolean) {
    rpc.call("user.delete", listOf(id, mapOf("delete_group" to deleteGroup)), timeoutMs = 90_000)
}

suspend fun TrueNasApi.saveGroup(id: Int?, name: String, smb: Boolean, userIds: List<Int>?) {
    val data = LinkedHashMap<String, Any?>()
    data["name"] = name
    data["smb"] = smb
    if (userIds != null) data["users"] = userIds
    if (id == null) rpc.call("group.create", listOf(data)) else rpc.call("group.update", listOf(id, data))
}

suspend fun TrueNasApi.deleteGroup(id: Int) {
    rpc.call("group.delete", listOf(id, mapOf("delete_users" to false)))
}

// ====================================================================== API keys & certificates

data class ApiKey(
    val id: Int,
    val name: String,
    val username: String?,
    val createdMillis: Long?,
    val expiresMillis: Long?,
    val revoked: Boolean,
)

data class Certificate(
    val id: Int,
    val name: String,
    val commonName: String?,
    val kind: String,
    val issuedBy: String?,
    val untilText: String?,
    val expired: Boolean,
    val sans: List<String>,
    val keyInfo: String?,
    val acme: Boolean,
)

suspend fun TrueNasApi.apiKeys(): List<ApiKey> = Parse.list(rpc.call("api_key.query")).map { o ->
    ApiKey(
        id = o.int("id") ?: 0,
        name = o.str("name") ?: "",
        username = o.str("username"),
        createdMillis = o.dateMillis("created_at"),
        expiresMillis = o.dateMillis("expires_at"),
        revoked = o.bool("revoked"),
    )
}.sortedByDescending { it.createdMillis ?: 0 }

/** Returns the new key; it's only ever shown once. */
suspend fun TrueNasApi.createApiKey(name: String, username: String, expiresMillis: Long?): String {
    val data = LinkedHashMap<String, Any?>()
    data["name"] = name
    data["username"] = username
    if (expiresMillis != null) data["expires_at"] = mapOf("\$date" to expiresMillis)
    val r = rpc.call("api_key.create", listOf(data)) as? JSONObject
    return r?.str("key") ?: throw RpcException(-32001, "The server didn't return the new key")
}

suspend fun TrueNasApi.deleteApiKey(id: Int) {
    rpc.call("api_key.delete", listOf(id))
}

suspend fun TrueNasApi.currentUsername(): String? = try {
    (rpc.call("auth.me") as? JSONObject)?.str("pw_name")
} catch (e: Exception) {
    null
}

suspend fun TrueNasApi.certificates(): List<Certificate> = Parse.list(rpc.call("certificate.query")).map { o ->
    val type = o.str("cert_type") ?: ""
    Certificate(
        id = o.int("id") ?: 0,
        name = o.str("name") ?: "",
        commonName = o.str("common"),
        kind = when {
            o.bool("cert_type_CA") || type == "CA" -> "Certificate authority"
            o.bool("cert_type_CSR") || type == "CERTIFICATE_SIGNING_REQUEST" -> "Signing request"
            else -> "Certificate"
        },
        issuedBy = o.obj("issuer")?.str("name") ?: o.str("issuer")?.takeIf { !it.startsWith("{") },
        untilText = o.str("until"),
        expired = o.bool("expired"),
        sans = o.arr("san")?.strings() ?: emptyList(),
        keyInfo = listOfNotNull(o.str("key_type"), o.int("key_length")?.toString()).joinToString(" ").ifBlank { null },
        acme = o.obj("acme") != null,
    )
}

// ====================================================================== virtualization

enum class GuestKind(val label: String) { VM("Virtual machine"), INSTANCE("Instance"), CONTAINER("Container") }

data class Guest(
    val kind: GuestKind,
    val id: String,
    val name: String,
    val description: String,
    val state: String,
    val cpus: String?,
    val memoryBytes: Long?,
    val autostart: Boolean,
    val os: String?,
) {
    val running: Boolean get() = state == "RUNNING"
}

suspend fun TrueNasApi.guests(): List<Guest> {
    val out = mutableListOf<Guest>()
    suspend fun tryQuery(method: String, map: (JSONObject) -> Guest) {
        try {
            out += Parse.list(rpc.call(method, timeoutMs = 60_000)).map(map)
        } catch (e: RpcException) {
            // Not available on this version, or not set up (e.g. no pool chosen for instances).
        }
    }
    tryQuery("vm.query") { o ->
        val v = o.int("vcpus") ?: 1
        val c = o.int("cores") ?: 1
        val t = o.int("threads") ?: 1
        Guest(
            GuestKind.VM, (o.int("id") ?: 0).toString(), o.str("name") ?: "", o.str("description") ?: "",
            state = o.obj("status")?.str("state") ?: "STOPPED",
            cpus = "${v * c * t} vCPU",
            memoryBytes = o.long("memory")?.let { it * 1024 * 1024 },
            autostart = o.bool("autostart"),
            os = null,
        )
    }
    tryQuery("virt.instance.query") { o ->
        Guest(
            if (o.str("type") == "VM") GuestKind.VM else GuestKind.INSTANCE,
            o.str("id") ?: o.str("name") ?: "", o.str("name") ?: "", "",
            state = o.str("status") ?: "UNKNOWN",
            cpus = o.str("cpu")?.let { "$it CPU" },
            memoryBytes = o.long("memory"),
            autostart = o.bool("autostart"),
            os = o.obj("image")?.let { listOfNotNull(it.str("os"), it.str("release")).joinToString(" ").ifBlank { null } },
        ).let { g -> if (g.kind == GuestKind.VM) g.copy(id = "virt:" + g.id) else g }
    }
    if (atLeast(26, 0)) {
        tryQuery("container.query") { o ->
            Guest(
                GuestKind.CONTAINER, (o.int("id") ?: 0).toString(), o.str("name") ?: "", o.str("description") ?: "",
                state = o.obj("status")?.str("state") ?: "STOPPED",
                cpus = o.str("cpuset")?.let { "CPUs $it" },
                memoryBytes = null,
                autostart = o.bool("autostart"),
                os = null,
            )
        }
    }
    return out.sortedWith(compareBy({ !it.running }, { it.name.lowercase() }))
}

enum class GuestAction { START, STOP, RESTART, POWER_OFF }

suspend fun TrueNasApi.guestAction(g: Guest, action: GuestAction, onProgress: (JobProgress) -> Unit) {
    val virt = g.kind == GuestKind.INSTANCE || g.id.startsWith("virt:")
    val id = g.id.removePrefix("virt:")
    when {
        virt -> when (action) {
            GuestAction.START -> runJob("virt.instance.start", listOf(id), onProgress)
            GuestAction.STOP -> runJob("virt.instance.stop", listOf(id, mapOf("timeout" to -1, "force" to false)), onProgress)
            GuestAction.POWER_OFF -> runJob("virt.instance.stop", listOf(id, mapOf("timeout" to -1, "force" to true)), onProgress)
            GuestAction.RESTART -> runJob("virt.instance.restart", listOf(id, mapOf("timeout" to -1, "force" to false)), onProgress)
        }
        g.kind == GuestKind.VM -> when (action) {
            GuestAction.START -> rpc.call("vm.start", listOf(id.toInt(), mapOf("overcommit" to false)), timeoutMs = 120_000)
            GuestAction.STOP -> runJob("vm.stop", listOf(id.toInt(), mapOf("force" to false, "force_after_timeout" to true)), onProgress)
            GuestAction.POWER_OFF -> rpc.call("vm.poweroff", listOf(id.toInt()), timeoutMs = 120_000)
            GuestAction.RESTART -> runJob("vm.restart", listOf(id.toInt()), onProgress)
        }
        else -> when (action) {
            GuestAction.START -> rpc.call("container.start", listOf(id.toInt()), timeoutMs = 120_000)
            GuestAction.STOP -> runJob("container.stop", listOf(id.toInt(), mapOf("force" to false, "force_after_timeout" to true)), onProgress)
            GuestAction.POWER_OFF -> runJob("container.stop", listOf(id.toInt(), mapOf("force" to true, "force_after_timeout" to false)), onProgress)
            GuestAction.RESTART -> {
                runJob("container.stop", listOf(id.toInt(), mapOf("force" to false, "force_after_timeout" to true)), onProgress)
                rpc.call("container.start", listOf(id.toInt()), timeoutMs = 120_000)
            }
        }
    }
}

// ====================================================================== reporting

data class GraphDef(val name: String, val title: String, val unit: String, val identifiers: List<String>)

data class GraphSeries(val label: String, val points: List<Float?>)

data class GraphData(
    val name: String,
    val identifier: String?,
    val times: List<Long>,
    val series: List<GraphSeries>,
)

suspend fun TrueNasApi.graphDefs(): List<GraphDef> {
    val r = callFirst("reporting.netdata_graphs" to emptyList(), "reporting.graphs" to emptyList())
    return Parse.list(r).map { o ->
        GraphDef(
            name = o.str("name") ?: "",
            title = o.str("title") ?: o.str("name") ?: "",
            unit = o.str("vertical_label") ?: "",
            identifiers = o.arr("identifiers")?.strings() ?: emptyList(),
        )
    }
}

/** [unit] is HOUR, DAY, WEEK, MONTH or YEAR. */
suspend fun TrueNasApi.graphData(name: String, identifier: String?, unit: String): GraphData? {
    val graph = mapOf("name" to name, "identifier" to identifier)
    val query = mapOf("unit" to unit, "page" to 1, "aggregate" to true)
    val r = callFirst(
        "reporting.netdata_get_data" to listOf(listOf(graph), query),
        "reporting.get_data" to listOf(listOf(graph), query),
        timeoutMs = 90_000,
    ) as? JSONArray ?: return null
    val o = r.optJSONObject(0) ?: return null
    val legend = o.arr("legend")?.strings() ?: emptyList()
    val rows = o.arr("data") ?: JSONArray()
    val names = if (legend.firstOrNull()?.lowercase() == "time") legend.drop(1) else legend
    val times = ArrayList<Long>(rows.length())
    val cols = names.map { ArrayList<Float?>(rows.length()) }
    for (i in 0 until rows.length()) {
        val row = rows.optJSONArray(i) ?: continue
        times += ((row.opt(0) as? Number)?.toLong() ?: 0L) * 1000
        for (c in names.indices) {
            val v = row.opt(c + 1).jsonOrNull() as? Number
            cols[c] += v?.toFloat()
        }
    }
    return GraphData(name, identifier, times, names.mapIndexed { i, n -> GraphSeries(n, cols[i]) })
}

// ====================================================================== update

data class UpdateInfo(
    val currentVersion: String,
    val train: String?,
    val available: String?,
    val releaseNotesUrl: String?,
    val rebootRequired: Boolean,
    val downloading: String?,
    val error: String?,
    /** 25.10+ uses update.run; 25.04 uses update.update. */
    val newStyle: Boolean,
)

suspend fun TrueNasApi.updateInfo(): UpdateInfo {
    val current = version.ifBlank { systemInfo().version }
    try {
        val o = rpc.call("update.status", timeoutMs = 120_000) as? JSONObject ?: JSONObject()
        val st = o.obj("status")
        val nv = st?.obj("new_version")
        val dl = o.obj("update_download_progress")
        return UpdateInfo(
            currentVersion = current,
            train = st?.obj("current_version")?.str("train"),
            available = nv?.str("version"),
            releaseNotesUrl = nv?.str("release_notes_url"),
            rebootRequired = false,
            downloading = dl?.let { "${it.double("percent")?.toInt() ?: 0}% ${it.str("description") ?: ""}".trim() },
            error = if (o.str("code") == "ERROR") o.obj("error")?.str("reason") else null,
            newStyle = true,
        )
    } catch (e: RpcException) {
        if (!e.isMethodNotFound) throw e
    }
    val o = rpc.call("update.check_available", timeoutMs = 120_000) as? JSONObject ?: JSONObject()
    val status = o.str("status")
    return UpdateInfo(
        currentVersion = current,
        train = null,
        available = if (status == "AVAILABLE") o.str("version") else null,
        releaseNotesUrl = o.str("release_notes_url"),
        rebootRequired = status == "REBOOT_REQUIRED",
        downloading = null,
        error = if (status == "HA_UNAVAILABLE") "High availability isn't working right now" else null,
        newStyle = false,
    )
}

suspend fun TrueNasApi.applyUpdate(newStyle: Boolean, reboot: Boolean, onProgress: (JobProgress) -> Unit) {
    runJob(if (newStyle) "update.run" else "update.update", listOf(mapOf("reboot" to reboot)), onProgress, timeoutMs = 90 * 60_000)
}

// ====================================================================== boot environments

data class BootEnv(
    val id: String,
    val active: Boolean,
    val activated: Boolean,
    val createdMillis: Long?,
    val used: Long?,
    val keep: Boolean,
    val canActivate: Boolean,
)

suspend fun TrueNasApi.bootEnvironments(): List<BootEnv> = Parse.list(rpc.call("boot.environment.query")).map { o ->
    BootEnv(
        id = o.str("id") ?: "",
        active = o.bool("active"),
        activated = o.bool("activated"),
        createdMillis = o.dateMillis("created"),
        used = o.long("used_bytes"),
        keep = o.bool("keep"),
        canActivate = o.bool("can_activate", default = true),
    )
}.sortedByDescending { it.createdMillis ?: 0 }

suspend fun TrueNasApi.activateBootEnv(id: String) {
    rpc.call("boot.environment.activate", listOf(mapOf("id" to id)))
}

suspend fun TrueNasApi.keepBootEnv(id: String, keep: Boolean) {
    rpc.call("boot.environment.keep", listOf(mapOf("id" to id, "value" to keep)))
}

suspend fun TrueNasApi.deleteBootEnv(id: String) {
    rpc.call("boot.environment.destroy", listOf(mapOf("id" to id)), timeoutMs = 120_000)
}

// ====================================================================== jobs

data class JobInfo(
    val id: Int,
    val method: String,
    val state: String,
    val percent: Int?,
    val description: String?,
    val error: String?,
    val startedMillis: Long?,
    val finishedMillis: Long?,
    val abortable: Boolean,
) {
    val running: Boolean get() = state == "RUNNING" || state == "WAITING"
}

suspend fun TrueNasApi.jobs(limit: Int = 80): List<JobInfo> {
    val r = rpc.call("core.get_jobs", listOf(emptyList<Any>(), mapOf("order_by" to listOf("-id"), "limit" to limit)), timeoutMs = 60_000)
    return Parse.list(r).map { o ->
        JobInfo(
            id = o.int("id") ?: 0,
            method = o.str("method") ?: "",
            state = o.str("state") ?: "",
            percent = o.obj("progress")?.int("percent"),
            description = o.obj("progress")?.str("description")?.takeIf { it.isNotBlank() } ?: o.str("description"),
            error = o.str("error")?.lineSequence()?.firstOrNull { it.isNotBlank() },
            startedMillis = o.dateMillis("time_started"),
            finishedMillis = o.dateMillis("time_finished"),
            abortable = o.bool("abortable"),
        )
    }
}

suspend fun TrueNasApi.runningJobCount(): Int = try {
    (rpc.call("core.get_jobs", listOf(listOf(listOf("state", "=", "RUNNING")), mapOf("count" to true))) as? Number)?.toInt() ?: 0
} catch (e: Exception) {
    0
}

suspend fun TrueNasApi.abortJob(id: Int) {
    rpc.call("core.job_abort", listOf(id))
}

/** "pool.dataset.unlock" → "Pool dataset unlock" */
fun prettyMethod(m: String): String {
    val known = mapOf(
        "app.create" to "Install app", "app.update" to "Update app settings", "app.upgrade" to "Update app",
        "app.delete" to "Delete app", "app.start" to "Start app", "app.stop" to "Stop app", "app.redeploy" to "Restart app",
        "app.pull_images" to "Pull app images", "app.rollback" to "Roll back app",
        "pool.scrub.scrub" to "Scrub", "pool.dataset.lock" to "Lock dataset", "pool.dataset.unlock" to "Unlock dataset",
        "update.run" to "System update", "update.update" to "System update", "update.download" to "Download update",
        "replication.run" to "Replication", "cloudsync.sync" to "Cloud sync", "cloud_backup.sync" to "TrueCloud backup",
        "rsynctask.run" to "Rsync", "service.control" to "Service", "vm.stop" to "Stop VM", "vm.restart" to "Restart VM",
        "catalog.sync" to "Refresh app catalog", "docker.update" to "Apps settings", "disk.wipe" to "Wipe disk",
        "pool.create" to "Create pool", "pool.export" to "Export pool", "pool.replace" to "Replace disk",
        "system.reboot" to "Restart", "system.shutdown" to "Shut down",
    )
    return known[m] ?: m.replace('.', ' ').replace('_', ' ').replaceFirstChar { it.uppercase() }
}

// ====================================================================== audit

data class AuditEvent(
    val id: String,
    val service: String,
    val event: String,
    val username: String,
    val address: String,
    val success: Boolean,
    val atMillis: Long?,
    val details: String,
)

suspend fun TrueNasApi.auditEvents(services: List<String>, limit: Int = 150): List<AuditEvent> {
    val r = rpc.call(
        "audit.query",
        listOf(
            mapOf(
                "services" to services,
                "query-filters" to emptyList<Any>(),
                "query-options" to mapOf("order_by" to listOf("-message_timestamp"), "limit" to limit),
            ),
        ),
        timeoutMs = 90_000,
    )
    return Parse.list(r).map { o ->
        val data = o.opt("event_data").jsonOrNull()
        AuditEvent(
            id = o.str("audit_id") ?: "${o.long("message_timestamp")}-${o.str("event")}",
            service = o.str("service") ?: "",
            event = o.str("event") ?: "",
            username = o.str("username") ?: "",
            address = o.str("address") ?: "",
            success = o.bool("success", default = true),
            atMillis = o.long("message_timestamp")?.let { it * 1000 } ?: o.dateMillis("timestamp"),
            details = when (data) {
                is JSONObject -> describeAudit(data)
                null -> ""
                else -> data.toString()
            },
        )
    }
}

private fun describeAudit(d: JSONObject): String {
    d.str("description")?.let { return it }
    val method = d.str("method")
    val params = d.opt("params")
    return when {
        method != null -> method + (params?.toString()?.take(300)?.let { " $it" } ?: "")
        else -> d.toString(2).take(1200)
    }
}

// ====================================================================== shell

/** A short-lived token for the web shell connection. */
suspend fun TrueNasApi.shellToken(): String =
    rpc.call("auth.generate_token", listOf(300, emptyMap<String, Any>(), false)) as? String
        ?: throw RpcException(-32001, "The server didn't give a shell token")

suspend fun TrueNasApi.resizeShell(id: String, cols: Int, rows: Int) {
    try {
        rpc.call("core.resize_shell", listOf(id, cols, rows), timeoutMs = 10_000)
    } catch (_: Exception) {
    }
}
