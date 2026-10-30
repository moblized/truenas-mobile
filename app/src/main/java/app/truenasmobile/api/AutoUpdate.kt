package app.truenasmobile.api

import app.truenasmobile.net.bool
import app.truenasmobile.net.int
import app.truenasmobile.net.obj
import app.truenasmobile.net.str
import org.json.JSONArray
import org.json.JSONObject

/**
 * Automatic app updates. TrueNAS has no such setting, so this lives on the server as a cron job
 * (System › Advanced › Cron Jobs in the web UI) that updates apps with `midclt`. It runs on the
 * server's own schedule, so updates happen even when the phone is off or away from home, and each
 * update shows up under Jobs like one started by hand.
 *
 * The settings are read back from the job's command, so every phone (and the web UI) sees the same thing.
 */
data class AutoUpdate(
    /** The cron job's id, or null when automatic updates were never set up. */
    val id: Int? = null,
    val enabled: Boolean = false,
    val schedule: Cron = Cron(minute = "0", hour = "4"),
    /** True: every app except [skip], including apps installed later. False: only the apps in [only]. */
    val includeNew: Boolean = true,
    val skip: Set<String> = emptySet(),
    val only: Set<String> = emptySet(),
    /** Also pull newer images for custom (Docker Compose) apps, and restart them. */
    val customImages: Boolean = true,
    /** Snapshot the app's host-path storage before updating (TrueNAS's "snapshot_hostpaths"). */
    val snapshot: Boolean = false,
    /** Set when the job's command was changed outside this app and can't be read back. */
    val edited: Boolean = false,
) {
    val active: Boolean get() = id != null && enabled

    fun covers(app: String): Boolean = if (includeNew) app !in skip else app in only

    fun withApp(app: String, on: Boolean): AutoUpdate =
        if (includeNew) copy(skip = if (on) skip - app else skip + app) else copy(only = if (on) only + app else only - app)

    /** Switches between "all apps" and "only these", keeping the same apps ticked. */
    fun withIncludeNew(on: Boolean, installed: Collection<String>): AutoUpdate {
        if (on == includeNew) return this
        val ticked = installed.filter { covers(it) }.toSet()
        return if (on) copy(includeNew = true, skip = installed.toSet() - ticked) else copy(includeNew = false, only = ticked)
    }

    /** One line of POSIX shell (TrueNAS strips newlines from cron commands and runs them with sh -c, as root). */
    fun command(): String {
        val scope = when {
            !includeNew -> """,["name","in",${names(only)}]"""
            skip.isNotEmpty() -> """,["name","nin",${names(skip)}]"""
            else -> ""
        }
        val upgrades = """for a in $(midclt call app.query '[["upgrade_available","=",true]$scope]' '{"select":["name"]}' | $NAMES); """ +
            """do if midclt call -j app.upgrade "${'$'}a" '{"app_version":"latest","snapshot_hostpaths":$snapshot}' >/dev/null 2>&1; """ +
            """then echo "Updated ${'$'}a"; else echo "Could not update ${'$'}a (see Jobs)"; fi; done"""
        val images = """for a in $(midclt call app.query '[["custom_app","=",true],["image_updates_available","=",true]$scope]' '{"select":["name"]}' | $NAMES); """ +
            """do if midclt call -j app.pull_images "${'$'}a" '{"redeploy":true}' >/dev/null 2>&1; """ +
            """then echo "Updated images for ${'$'}a"; else echo "Could not update images for ${'$'}a (see Jobs)"; fi; done"""
        return if (customImages) "$upgrades; $images" else upgrades
    }

    /** What to send to cronjob.create / cronjob.update. */
    fun toCronJob(): Map<String, Any?> = mapOf(
        "enabled" to enabled,
        "description" to DESCRIPTION,
        "user" to "root",
        "command" to command(),
        "schedule" to schedule.toMap(),
        // Show output: the root user gets an email only when something was updated (or failed),
        // and the output is kept with the job under Jobs.
        "stdout" to false,
        "stderr" to false,
    )

    /** "Every day at 04:00 · all apps", for the Apps screen. */
    fun summary(): String {
        val which = when {
            !includeNew -> "${only.size} ${if (only.size == 1) "app" else "apps"}"
            skip.isEmpty() -> "all apps"
            else -> "all apps but ${skip.size}"
        }
        return "${schedule.describe()} · $which"
    }

    companion object {
        const val DESCRIPTION = "TrueNAS Mobile: update apps automatically"

        /** TrueNAS app names are lowercase letters, digits and hyphens, which keeps them safe inside the command. */
        private val APP_NAME = Regex("^[a-z0-9]([a-z0-9-]*[a-z0-9])?$")

        private const val NAMES = """python3 -c 'import json,sys;print(" ".join(a["name"] for a in json.load(sys.stdin)))'"""

        private fun names(apps: Set<String>): String =
            JSONArray(apps.filter { APP_NAME.matches(it) }.sorted()).toString()

        private val IN = Regex("""\["name","in",(\[[^\]]*])]""")
        private val NIN = Regex("""\["name","nin",(\[[^\]]*])]""")

        fun parse(job: JSONObject): AutoUpdate {
            val command = job.str("command") ?: ""
            fun list(r: Regex): Set<String>? = r.find(command)?.groupValues?.get(1)?.let { raw ->
                runCatching { JSONArray(raw).let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } }.getOrNull()
            }
            val only = list(IN)
            val skip = list(NIN)
            return AutoUpdate(
                id = job.int("id"),
                enabled = job.bool("enabled", default = true),
                schedule = Cron.of(job.obj("schedule")),
                includeNew = only == null,
                skip = skip ?: emptySet(),
                only = only ?: emptySet(),
                customImages = "app.pull_images" in command,
                snapshot = "\"snapshot_hostpaths\":true" in command,
                edited = "midclt call -j app.upgrade" !in command,
            )
        }
    }
}

/** The automatic-update cron job, or the defaults if it isn't set up yet. */
suspend fun TrueNasApi.autoUpdate(): AutoUpdate {
    val r = rpc.call("cronjob.query", listOf(listOf(listOf("description", "=", AutoUpdate.DESCRIPTION))), timeoutMs = 30_000) as? JSONArray
    return r?.optJSONObject(0)?.let { AutoUpdate.parse(it) } ?: AutoUpdate()
}

/** Creates or changes the cron job; returns it as saved. */
suspend fun TrueNasApi.saveAutoUpdate(config: AutoUpdate): AutoUpdate {
    val data = config.toCronJob()
    val saved = if (config.id != null) {
        rpc.call("cronjob.update", listOf(config.id, data), timeoutMs = 60_000)
    } else {
        rpc.call("cronjob.create", listOf(data), timeoutMs = 60_000)
    } as? JSONObject
    return saved?.let { AutoUpdate.parse(it) } ?: config
}

suspend fun TrueNasApi.deleteAutoUpdate(id: Int) {
    rpc.call("cronjob.delete", listOf(id), timeoutMs = 60_000)
}

/** Runs the update check now; it keeps going on the server and shows under Jobs. */
suspend fun TrueNasApi.runAutoUpdateNow(id: Int) {
    runJob("cronjob.run", listOf(id), waitForCompletion = false)
}
