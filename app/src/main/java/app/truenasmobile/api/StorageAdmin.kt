package app.truenasmobile.api

import app.truenasmobile.net.RpcException
import app.truenasmobile.net.arr
import app.truenasmobile.net.bool
import app.truenasmobile.net.dateMillis
import app.truenasmobile.net.int
import app.truenasmobile.net.jsonOrNull
import app.truenasmobile.net.keyList
import app.truenasmobile.net.obj
import app.truenasmobile.net.str
import app.truenasmobile.net.strings
import org.json.JSONArray
import org.json.JSONObject

// ====================================================================== datasets

/** A ZFS property: its value as TrueNAS names it ("LZ4", "ON"…) and where it comes from. */
data class Prop(val value: String?, val source: String?, val parsed: Any?) {
    val inherited: Boolean get() = source == "INHERITED" || source == "DEFAULT"
}

data class DatasetDetail(
    val id: String,
    val type: String,
    val pool: String,
    val props: Map<String, Prop>,
    val encrypted: Boolean,
    val locked: Boolean,
    val encryptionRoot: String?,
    val keyFormat: String?,
    val comments: String?,
) {
    fun bytes(key: String): Long? = (props[key]?.parsed as? Number)?.toLong()
    fun value(key: String): String? = props[key]?.value
    val isVolume: Boolean get() = type == "VOLUME"
}

private fun propOf(v: Any?): Prop? = when (val x = v.jsonOrNull()) {
    is JSONObject -> Prop(x.str("value"), x.str("source"), x.opt("parsed").jsonOrNull())
    null -> null
    else -> Prop(x.toString(), null, x)
}

suspend fun TrueNasApi.datasetDetail(id: String): DatasetDetail {
    val r = rpc.call(
        "pool.dataset.query",
        listOf(listOf(listOf("id", "=", id)), mapOf("extra" to mapOf("retrieve_children" to false))),
        timeoutMs = 60_000,
    ) as? JSONArray
    val o = r?.optJSONObject(0) ?: throw RpcException(-32001, "Dataset $id not found")
    val props = LinkedHashMap<String, Prop>()
    for (k in o.keyList()) {
        val v = o.opt(k)
        if (v is JSONObject && v.has("source")) propOf(v)?.let { props[k] = it }
    }
    val comments = o.optJSONObject("comments")?.str("value") ?: o.optJSONObject("user_properties")?.optJSONObject("comments")?.str("value")
    return DatasetDetail(
        id = o.str("id") ?: id,
        type = o.str("type") ?: "FILESYSTEM",
        pool = o.str("pool") ?: id.substringBefore('/'),
        props = props,
        encrypted = o.bool("encrypted"),
        locked = o.bool("locked"),
        encryptionRoot = o.str("encryption_root"),
        keyFormat = props["key_format"]?.value,
        comments = comments?.takeIf { it.isNotBlank() },
    )
}

/** Changes dataset properties. Values follow TrueNAS's names, e.g. compression "LZ4" or "INHERIT". */
suspend fun TrueNasApi.updateDataset(id: String, changes: Map<String, Any?>) {
    rpc.call("pool.dataset.update", listOf(id, changes), timeoutMs = 90_000)
}

suspend fun TrueNasApi.createZvol(name: String, sizeBytes: Long, sparse: Boolean) {
    rpc.call(
        "pool.dataset.create",
        listOf(mapOf("name" to name, "type" to "VOLUME", "volsize" to sizeBytes, "sparse" to sparse)),
        timeoutMs = 90_000,
    )
}

suspend fun TrueNasApi.deleteDataset(id: String, recursive: Boolean) {
    rpc.call("pool.dataset.delete", listOf(id, mapOf("recursive" to recursive)), timeoutMs = 5 * 60_000)
}

suspend fun TrueNasApi.lockDataset(id: String, onProgress: (JobProgress) -> Unit) {
    runJob("pool.dataset.lock", listOf(id, mapOf("force_umount" to true)), onProgress)
}

/** Returns an error message, or null when it unlocked. */
suspend fun TrueNasApi.unlockDataset(id: String, secret: String, isHexKey: Boolean, onProgress: (JobProgress) -> Unit): String? {
    val entry = if (isHexKey) mapOf("name" to id, "key" to secret) else mapOf("name" to id, "passphrase" to secret)
    val r = runJob(
        "pool.dataset.unlock",
        listOf(id, mapOf("recursive" to true, "datasets" to listOf(entry))),
        onProgress,
    ) as? JSONObject
    val failed = r?.optJSONObject("failed") ?: return null
    if (failed.length() == 0) return null
    val first = failed.optJSONObject(failed.keys().next())
    return first?.str("error") ?: "Couldn't unlock: wrong passphrase or key?"
}

suspend fun TrueNasApi.rollbackSnapshot(id: String, destroyNewer: Boolean) {
    val options = mapOf("recursive" to destroyNewer)
    callFirst("pool.snapshot.rollback" to listOf(id, options), "zfs.snapshot.rollback" to listOf(id, options), timeoutMs = 5 * 60_000)
}

// ====================================================================== shares

data class SmbShareFull(
    val id: Int,
    val name: String,
    val path: String,
    val comment: String,
    val enabled: Boolean,
    val readOnly: Boolean,
    val browsable: Boolean,
    val purpose: String?,
    val locked: Boolean,
    /** "readonly" (25.10+) or "ro" (25.04). */
    val readOnlyKey: String,
)

data class NfsShareFull(
    val id: Int,
    val path: String,
    val comment: String,
    val enabled: Boolean,
    val readOnly: Boolean,
    val networks: List<String>,
    val hosts: List<String>,
    val maprootUser: String?,
    val mapallUser: String?,
    val locked: Boolean,
)

suspend fun TrueNasApi.smbSharesFull(): List<SmbShareFull> =
    Parse.list(rpc.call("sharing.smb.query")).map { o ->
        val key = if (o.has("readonly")) "readonly" else "ro"
        SmbShareFull(
            id = o.int("id") ?: 0,
            name = o.str("name") ?: "",
            path = o.str("path") ?: "",
            comment = o.str("comment") ?: "",
            enabled = o.bool("enabled", default = true),
            readOnly = o.bool(key),
            browsable = o.bool("browsable", default = true),
            purpose = o.str("purpose"),
            locked = o.bool("locked"),
            readOnlyKey = key,
        )
    }.sortedBy { it.name.lowercase() }

suspend fun TrueNasApi.nfsSharesFull(): List<NfsShareFull> =
    Parse.list(rpc.call("sharing.nfs.query")).map { o ->
        NfsShareFull(
            id = o.int("id") ?: 0,
            path = o.str("path") ?: o.arr("paths")?.optString(0) ?: "",
            comment = o.str("comment") ?: "",
            enabled = o.bool("enabled", default = true),
            readOnly = o.bool("ro"),
            networks = o.arr("networks")?.strings() ?: emptyList(),
            hosts = o.arr("hosts")?.strings() ?: emptyList(),
            maprootUser = o.str("maproot_user")?.takeIf { it.isNotBlank() },
            mapallUser = o.str("mapall_user")?.takeIf { it.isNotBlank() },
            locked = o.bool("locked"),
        )
    }.sortedBy { it.path }

/** SMB presets the server offers (key → friendly name). */
suspend fun TrueNasApi.smbPurposes(): List<Pair<String, String>> = try {
    val r = rpc.call("sharing.smb.presets") as? JSONObject ?: JSONObject()
    r.keyList().map { k ->
        val label = r.optJSONObject(k)?.str("verbose_name") ?: prettyConst(k)
        k to label
    }.filter { it.first != "NO_PRESET" && it.first != "LEGACY_SHARE" && it.first != "EXTERNAL_SHARE" }
} catch (e: Exception) {
    listOf("DEFAULT_SHARE" to "Default share")
}

fun prettyConst(k: String): String =
    k.removeSuffix("_SHARE").replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }

/** Creates or updates an SMB share. [id] null creates. */
suspend fun TrueNasApi.saveSmbShare(
    id: Int?,
    name: String,
    path: String,
    comment: String,
    enabled: Boolean,
    readOnly: Boolean,
    purpose: String?,
) {
    val roKey = if (atLeast(25, 10)) "readonly" else "ro"
    val data = buildMap<String, Any?> {
        put("name", name)
        put("path", path)
        put("comment", comment)
        put("enabled", enabled)
        put(roKey, readOnly)
        if (purpose != null) put("purpose", purpose)
    }
    if (id == null) rpc.call("sharing.smb.create", listOf(data), timeoutMs = 90_000)
    else rpc.call("sharing.smb.update", listOf(id, data), timeoutMs = 90_000)
}

suspend fun TrueNasApi.saveNfsShare(
    id: Int?,
    path: String,
    comment: String,
    enabled: Boolean,
    readOnly: Boolean,
    networks: List<String>,
    hosts: List<String>,
    maprootUser: String?,
) {
    val data = mapOf(
        "path" to path,
        "comment" to comment,
        "enabled" to enabled,
        "ro" to readOnly,
        "networks" to networks,
        "hosts" to hosts,
        "maproot_user" to maprootUser?.ifBlank { null },
        "maproot_group" to if (maprootUser.isNullOrBlank()) null else maprootUser,
    )
    if (id == null) rpc.call("sharing.nfs.create", listOf(data), timeoutMs = 90_000)
    else rpc.call("sharing.nfs.update", listOf(id, data), timeoutMs = 90_000)
}

suspend fun TrueNasApi.deleteSmbShare(id: Int) {
    rpc.call("sharing.smb.delete", listOf(id))
}

suspend fun TrueNasApi.deleteNfsShare(id: Int) {
    rpc.call("sharing.nfs.delete", listOf(id))
}

// ====================================================================== data protection

data class Cron(
    val minute: String = "0",
    val hour: String = "0",
    val dom: String = "*",
    val month: String = "*",
    val dow: String = "*",
) {
    fun toMap(): Map<String, Any?> = mapOf("minute" to minute, "hour" to hour, "dom" to dom, "month" to month, "dow" to dow)

    /** "Every day at 03:00", "Every Sunday at 00:00", or the raw cron. */
    fun describe(): String {
        val time = if (hour.toIntOrNull() != null && minute.toIntOrNull() != null) "%02d:%02d".format(hour.toInt(), minute.toInt()) else null
        return when {
            month == "*" && dom == "*" && dow == "*" && hour == "*" && minute.toIntOrNull() != null -> "Every hour at :%02d".format(minute.toInt())
            month == "*" && dom == "*" && dow == "*" && time != null -> "Every day at $time"
            month == "*" && dom == "*" && time != null && dow.toIntOrNull() != null -> "Every ${dayName(dow.toInt())} at $time"
            month == "*" && dow == "*" && time != null && dom.toIntOrNull() != null -> "Monthly on day $dom at $time"
            hour.startsWith("*/") && dom == "*" && dow == "*" && month == "*" -> "Every ${hour.removePrefix("*/")} hours"
            minute.startsWith("*/") && hour == "*" && dom == "*" && dow == "*" -> "Every ${minute.removePrefix("*/")} minutes"
            else -> "Cron: $minute $hour $dom $month $dow"
        }
    }

    companion object {
        fun of(o: JSONObject?): Cron = if (o == null) Cron() else Cron(
            minute = o.str("minute") ?: "0",
            hour = o.str("hour") ?: "*",
            dom = o.str("dom") ?: "*",
            month = o.str("month") ?: "*",
            dow = o.str("dow") ?: "*",
        )

        private val days = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
        fun dayName(d: Int): String = days.getOrElse(d) { "day $d" }
    }
}

data class TaskRun(val state: String?, val atMillis: Long?, val error: String?, val percent: Int?, val description: String?) {
    val running: Boolean get() = state == "RUNNING" || state == "WAITING"
    val failed: Boolean get() = state == "FAILED" || state == "ERROR"

    companion object {
        /** From a task's last job ({state, progress, time_finished, error}) or replication/snapshot "state". */
        fun of(o: JSONObject?): TaskRun? {
            if (o == null) return null
            val progress = o.obj("progress")
            return TaskRun(
                state = o.str("state"),
                atMillis = o.dateMillis("time_finished") ?: o.dateMillis("datetime") ?: o.dateMillis("time_started"),
                error = o.str("error")?.lineSequence()?.firstOrNull { it.isNotBlank() },
                percent = progress?.int("percent"),
                description = progress?.str("description"),
            )
        }
    }
}

enum class TaskKind(val label: String, val namespace: String) {
    SNAPSHOT("Periodic snapshots", "pool.snapshottask"),
    SCRUB("Scrub tasks", "pool.scrub"),
    REPLICATION("Replication", "replication"),
    CLOUD_SYNC("Cloud sync", "cloudsync"),
    CLOUD_BACKUP("TrueCloud backup", "cloud_backup"),
    RSYNC("Rsync", "rsynctask"),
}

data class ProtectionTask(
    val kind: TaskKind,
    val id: Int,
    val title: String,
    val detail: String,
    val schedule: Cron?,
    val enabled: Boolean,
    val last: TaskRun?,
    val raw: JSONObject,
)

private fun lifetime(o: JSONObject): String {
    val v = o.int("lifetime_value") ?: return ""
    val unit = (o.str("lifetime_unit") ?: "WEEK").lowercase()
    return "keep $v $unit${if (v == 1) "" else "s"}"
}

suspend fun TrueNasApi.protectionTasks(kind: TaskKind): List<ProtectionTask> {
    val list = try {
        Parse.list(rpc.call("${kind.namespace}.query", timeoutMs = 60_000))
    } catch (e: RpcException) {
        if (e.isMethodNotFound) return emptyList() else throw e
    }
    return list.map { o ->
        when (kind) {
            TaskKind.SNAPSHOT -> ProtectionTask(
                kind, o.int("id") ?: 0,
                title = o.str("dataset") ?: "",
                detail = listOfNotNull(if (o.bool("recursive")) "with children" else null, lifetime(o)).joinToString(" · "),
                schedule = Cron.of(o.obj("schedule")),
                enabled = o.bool("enabled", true),
                last = TaskRun.of(o.obj("state")),
                raw = o,
            )
            TaskKind.SCRUB -> ProtectionTask(
                kind, o.int("id") ?: 0,
                title = o.str("pool_name") ?: "Pool ${o.int("pool")}",
                detail = "Runs if the last scrub was ${o.int("threshold") ?: 35}+ days ago" + (o.str("description")?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                schedule = Cron.of(o.obj("schedule")),
                enabled = o.bool("enabled", true),
                last = null,
                raw = o,
            )
            TaskKind.REPLICATION -> ProtectionTask(
                kind, o.int("id") ?: 0,
                title = o.str("name") ?: "Replication",
                detail = "${o.arr("source_datasets")?.strings()?.joinToString() ?: ""} → ${o.str("target_dataset") ?: ""}" +
                    " · ${(o.str("direction") ?: "").lowercase()} via ${(o.str("transport") ?: "").lowercase()}",
                schedule = o.obj("schedule")?.let { Cron.of(it) },
                enabled = o.bool("enabled", true),
                last = TaskRun.of(o.obj("job")) ?: TaskRun.of(o.obj("state")),
                raw = o,
            )
            TaskKind.CLOUD_SYNC, TaskKind.CLOUD_BACKUP -> ProtectionTask(
                kind, o.int("id") ?: 0,
                title = o.str("description")?.takeIf { it.isNotBlank() } ?: o.str("path") ?: "Task",
                detail = listOfNotNull(
                    o.str("direction")?.lowercase(),
                    o.str("path"),
                    o.obj("credentials")?.str("name")?.let { "with $it" },
                ).joinToString(" · "),
                schedule = Cron.of(o.obj("schedule")),
                enabled = o.bool("enabled", true),
                last = TaskRun.of(o.obj("job")),
                raw = o,
            )
            TaskKind.RSYNC -> ProtectionTask(
                kind, o.int("id") ?: 0,
                title = o.str("desc")?.takeIf { it.isNotBlank() } ?: o.str("path") ?: "Rsync",
                detail = "${(o.str("direction") ?: "push").lowercase()} ${o.str("path") ?: ""} ↔ ${o.str("remotehost") ?: o.obj("ssh_credentials")?.str("name") ?: ""}",
                schedule = Cron.of(o.obj("schedule")),
                enabled = o.bool("enabled", true),
                last = TaskRun.of(o.obj("job")),
                raw = o,
            )
        }
    }
}

suspend fun TrueNasApi.setTaskEnabled(task: ProtectionTask, enabled: Boolean) {
    rpc.call("${task.kind.namespace}.update", listOf(task.id, mapOf("enabled" to enabled)), timeoutMs = 60_000)
}

suspend fun TrueNasApi.deleteTask(task: ProtectionTask) {
    rpc.call("${task.kind.namespace}.delete", listOf(task.id), timeoutMs = 60_000)
}

/** Starts the task now. Long transfers keep running on the server. */
suspend fun TrueNasApi.runTaskNow(task: ProtectionTask) {
    when (task.kind) {
        TaskKind.SNAPSHOT -> rpc.call("pool.snapshottask.run", listOf(task.id))
        TaskKind.SCRUB -> rpc.call("pool.scrub.run", listOf(task.raw.str("pool_name") ?: "", 0))
        TaskKind.REPLICATION -> rpc.call("replication.run", listOf(task.id))
        TaskKind.CLOUD_SYNC -> rpc.call("cloudsync.sync", listOf(task.id))
        TaskKind.CLOUD_BACKUP -> rpc.call("cloud_backup.sync", listOf(task.id))
        TaskKind.RSYNC -> rpc.call("rsynctask.run", listOf(task.id))
    }
}

suspend fun TrueNasApi.abortTask(task: ProtectionTask) {
    when (task.kind) {
        TaskKind.CLOUD_SYNC -> rpc.call("cloudsync.abort", listOf(task.id))
        TaskKind.CLOUD_BACKUP -> rpc.call("cloud_backup.abort", listOf(task.id))
        else -> {
            val jobId = task.raw.optJSONObject("job")?.int("id") ?: return
            rpc.call("core.job_abort", listOf(jobId))
        }
    }
}

suspend fun TrueNasApi.saveSnapshotTask(
    id: Int?,
    dataset: String,
    recursive: Boolean,
    lifetimeValue: Int,
    lifetimeUnit: String,
    schedule: Cron,
    enabled: Boolean,
) {
    val data = mapOf(
        "dataset" to dataset,
        "recursive" to recursive,
        "lifetime_value" to lifetimeValue,
        "lifetime_unit" to lifetimeUnit,
        "schedule" to schedule.toMap() + mapOf("begin" to "00:00", "end" to "23:59"),
        "enabled" to enabled,
    )
    if (id == null) rpc.call("pool.snapshottask.create", listOf(data + ("naming_schema" to "auto-%Y-%m-%d_%H-%M")), timeoutMs = 60_000)
    else rpc.call("pool.snapshottask.update", listOf(id, data), timeoutMs = 60_000)
}

suspend fun TrueNasApi.saveScrubTask(id: Int?, poolId: Int, thresholdDays: Int, schedule: Cron, enabled: Boolean) {
    val data = mapOf("pool" to poolId, "threshold" to thresholdDays, "schedule" to schedule.toMap(), "enabled" to enabled)
    if (id == null) rpc.call("pool.scrub.create", listOf(data)) else rpc.call("pool.scrub.update", listOf(id, data))
}
