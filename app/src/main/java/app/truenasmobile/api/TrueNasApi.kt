package app.truenasmobile.api

import app.truenasmobile.net.RpcClient
import app.truenasmobile.net.RpcException
import app.truenasmobile.net.int
import app.truenasmobile.net.jsonOrNull
import app.truenasmobile.net.obj
import app.truenasmobile.net.str
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

/**
 * The TrueNAS calls the app uses. Works with SCALE 25.04 (Fangtooth), 25.10 (Goldeye) and 26+:
 * where a method was renamed between releases, the newer name is tried first.
 */
class TrueNasApi(val rpc: RpcClient) {

    // ---------- Login ----------

    suspend fun loginApiKey(username: String, apiKey: String): LoginResult {
        if (username.isNotBlank()) {
            val r = try {
                rpc.call(
                    "auth.login_ex",
                    listOf(mapOf("mechanism" to "API_KEY_PLAIN", "username" to username.trim(), "api_key" to apiKey.trim())),
                )
            } catch (e: RpcException) {
                if (!e.isMethodNotFound) throw e
                null
            }
            if (r is JSONObject) {
                val result = loginResponse(r)
                if (result !is LoginResult.Failed) return result
                // A key created for another user (or a legacy key) still works with the older call.
                return if (legacyApiKey(apiKey)) LoginResult.Success else result
            }
        }
        return if (legacyApiKey(apiKey)) {
            LoginResult.Success
        } else {
            LoginResult.Failed("The API key was rejected. Check that it's correct and hasn't been revoked.")
        }
    }

    private suspend fun legacyApiKey(apiKey: String): Boolean = try {
        rpc.call("auth.login_with_api_key", listOf(apiKey.trim())) == true
    } catch (e: RpcException) {
        false
    }

    suspend fun loginPassword(username: String, password: String): LoginResult {
        val r = try {
            rpc.call(
                "auth.login_ex",
                listOf(mapOf("mechanism" to "PASSWORD_PLAIN", "username" to username.trim(), "password" to password)),
            )
        } catch (e: RpcException) {
            if (!e.isMethodNotFound) throw e
            val ok = rpc.call("auth.login", listOf(username.trim(), password)) == true
            return if (ok) LoginResult.Success else LoginResult.Failed("Wrong username or password.")
        }
        return (r as? JSONObject)?.let(::loginResponse) ?: LoginResult.Failed("Unexpected login response.")
    }

    suspend fun loginOtp(code: String): LoginResult {
        val r = rpc.call(
            "auth.login_ex_continue",
            listOf(mapOf("mechanism" to "OTP_TOKEN", "otp_token" to code.trim())),
        )
        return (r as? JSONObject)?.let(::loginResponse) ?: LoginResult.Failed("Unexpected login response.")
    }

    suspend fun loginToken(token: String): Boolean = try {
        val r = rpc.call("auth.login_ex", listOf(mapOf("mechanism" to "TOKEN_PLAIN", "token" to token)))
        (r as? JSONObject)?.str("response_type") == "SUCCESS"
    } catch (e: RpcException) {
        false
    }

    /** A reusable session token, so reconnecting doesn't ask for a 2FA code again. */
    suspend fun generateToken(ttlSeconds: Int = 12 * 3600): String? = try {
        rpc.call("auth.generate_token", listOf(ttlSeconds, emptyMap<String, Any>(), false)) as? String
    } catch (e: Exception) {
        null
    }

    private fun loginResponse(r: JSONObject): LoginResult = when (r.str("response_type")) {
        "SUCCESS" -> LoginResult.Success
        "OTP_REQUIRED" -> LoginResult.OtpRequired
        "EXPIRED" -> LoginResult.Failed("This account's password or key has expired.")
        "REDIRECT" -> LoginResult.Failed("This is a standby controller. Connect to the active controller instead.")
        "DENIED" -> LoginResult.Failed("This account doesn't have API access.")
        else -> LoginResult.Failed("Wrong username, password or API key.")
    }

    // ---------- System ----------

    suspend fun systemInfo(): SystemInfo =
        Parse.systemInfo(rpc.call("system.info") as? JSONObject ?: JSONObject())

    suspend fun rebootRequiredReasons(): List<String> = try {
        val r = rpc.call("system.reboot.info") as? JSONObject
        r?.optJSONArray("reboot_required_reasons")?.let { arr ->
            (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.str("reason") }
        } ?: emptyList()
    } catch (e: RpcException) {
        emptyList()
    }

    /** Starts a reboot. The connection drops shortly after. */
    suspend fun reboot(reason: String) {
        rpc.call("system.reboot", listOf(reason.ifBlank { "Requested from TrueNAS Mobile" }))
    }

    suspend fun shutdown(reason: String) {
        rpc.call("system.shutdown", listOf(reason.ifBlank { "Requested from TrueNAS Mobile" }))
    }

    // ---------- Storage ----------

    suspend fun pools(): List<Pool> = Parse.list(rpc.call("pool.query")).map(Parse::pool)

    suspend fun datasets(): List<Dataset> {
        val options = mapOf(
            "extra" to mapOf(
                "flat" to true,
                "properties" to listOf("used", "available", "mountpoint", "compressratio", "type"),
                "retrieve_user_props" to false,
            ),
        )
        return Parse.list(rpc.call("pool.dataset.query", listOf(emptyList<Any>(), options), timeoutMs = 90_000))
            .map(Parse::dataset)
            .sortedBy { it.id }
    }

    suspend fun snapshots(dataset: String): List<Snapshot> {
        val filters = listOf(listOf("dataset", "=", dataset))
        val raw = callFirst(
            "pool.snapshot.query" to listOf(filters),
            "zfs.snapshot.query" to listOf(filters),
            timeoutMs = 90_000,
        )
        return Parse.list(raw).map(Parse::snapshot).sortedByDescending { it.createTxg }
    }

    suspend fun createSnapshot(dataset: String, name: String, recursive: Boolean) {
        val data = mapOf("dataset" to dataset, "name" to name, "recursive" to recursive)
        callFirst("pool.snapshot.create" to listOf(data), "zfs.snapshot.create" to listOf(data))
    }

    suspend fun deleteSnapshot(id: String) {
        callFirst("pool.snapshot.delete" to listOf(id), "zfs.snapshot.delete" to listOf(id))
    }

    suspend fun disks(): List<Disk> = coroutineScope {
        val temps = async {
            try {
                Parse.temperatures(rpc.call("disk.temperatures", timeoutMs = 45_000))
            } catch (e: Exception) {
                emptyMap()
            }
        }
        val list = Parse.list(rpc.call("disk.query"))
        val t = temps.await()
        list.map { Parse.disk(it, t) }.sortedWith(compareBy({ it.pool == null }, { it.pool }, { it.name.length }, { it.name }))
    }

    /** "START", "STOP" or "PAUSE". */
    suspend fun scrub(pool: String, action: String, onProgress: (JobProgress) -> Unit = {}) {
        runJob("pool.scrub.scrub", listOf(pool, action), onProgress, waitForCompletion = action != "START")
    }

    // ---------- Apps ----------

    suspend fun apps(): List<App> = Parse.list(rpc.call("app.query")).map(Parse::app).sortedBy { it.title.lowercase() }

    suspend fun startApp(name: String, onProgress: (JobProgress) -> Unit) =
        runJob("app.start", listOf(name), onProgress)

    suspend fun stopApp(name: String, onProgress: (JobProgress) -> Unit) =
        runJob("app.stop", listOf(name), onProgress)

    suspend fun restartApp(name: String, onProgress: (JobProgress) -> Unit) =
        runJob("app.redeploy", listOf(name), onProgress)

    suspend fun upgradeApp(name: String, onProgress: (JobProgress) -> Unit) =
        runJob("app.upgrade", listOf(name), onProgress)

    /** For custom apps: pull newer images and redeploy. */
    suspend fun pullImages(name: String, onProgress: (JobProgress) -> Unit) =
        runJob("app.pull_images", listOf(name, mapOf("redeploy" to true)), onProgress)

    // ---------- App catalog ----------

    /** Catalog apps (without the heavy README/screenshots), most popular first. */
    suspend fun availableApps(): List<CatalogApp> {
        val options = mapOf(
            "select" to listOf(
                "name", "title", "description", "icon_url", "train", "categories", "installed",
                "recommended", "popularity_rank", "latest_version", "latest_human_version",
            ),
        )
        return Parse.list(rpc.call("app.available", listOf(emptyList<Any>(), options), timeoutMs = 90_000))
            .map(Schema::catalogApp)
            .filter { it.name.isNotBlank() }
            .sortedWith(compareBy<CatalogApp>({ it.popularity ?: Int.MAX_VALUE }, { it.title.lowercase() }))
    }

    suspend fun appCategories(): List<String> = try {
        (rpc.call("app.categories") as? JSONArray)?.let { a -> (0 until a.length()).map { a.optString(it) } }
            ?.filter { it.isNotBlank() }?.sorted() ?: emptyList()
    } catch (e: RpcException) {
        emptyList()
    }

    /** Install questions and default values for the newest version of a catalog app. */
    suspend fun catalogAppDetails(name: String, train: String): CatalogAppDetails {
        val r = rpc.call("catalog.get_app_details", listOf(name, mapOf("train" to train)), timeoutMs = 90_000) as? JSONObject
            ?: throw RpcException(-32001, "The catalog didn't return details for $name")
        return Schema.details(r, train) ?: throw RpcException(-32001, "No installable version of $name was found")
    }

    /** Installs a catalog app. Pulling images can take a while; progress is reported along the way. */
    suspend fun installApp(
        appName: String,
        catalogApp: String,
        train: String,
        version: String,
        values: Map<String, Any?>,
        onProgress: (JobProgress) -> Unit,
    ) {
        runJob(
            "app.create",
            listOf(
                mapOf(
                    "custom_app" to false,
                    "catalog_app" to catalogApp,
                    "app_name" to appName,
                    "train" to train,
                    "version" to version,
                    "values" to values,
                ),
            ),
            onProgress,
            timeoutMs = 60 * 60_000,
        )
    }

    // ---------- Alerts ----------

    suspend fun alerts(): List<Alert> = Parse.list(rpc.call("alert.list")).map(Parse::alert)
        .sortedWith(compareByDescending<Alert> { it.level.rank }.thenByDescending { it.datetimeMillis ?: 0 })

    suspend fun dismissAlert(uuid: String) {
        rpc.call("alert.dismiss", listOf(uuid))
    }

    suspend fun restoreAlert(uuid: String) {
        rpc.call("alert.restore", listOf(uuid))
    }

    // ---------- Services & shares ----------

    suspend fun services(): List<ServiceEntry> = Parse.list(rpc.call("service.query")).map(Parse::service)

    suspend fun setServiceRunning(service: String, run: Boolean, onProgress: (JobProgress) -> Unit = {}): Boolean {
        val verb = if (run) "START" else "STOP"
        val options = mapOf("silent" to false)
        return try {
            runJob("service.control", listOf(verb, service, options), onProgress) != false
        } catch (e: RpcException) {
            if (!e.isMethodNotFound) throw e
            // TrueNAS 25.04
            rpc.call(if (run) "service.start" else "service.stop", listOf(service, options), timeoutMs = 150_000) != false
        }
    }

    suspend fun setServiceEnabled(service: ServiceEntry, enable: Boolean) {
        rpc.call("service.update", listOf(service.id, mapOf("enable" to enable)))
    }

    suspend fun smbShares(): List<SmbShare> = Parse.list(rpc.call("sharing.smb.query")).map(Parse::smbShare)

    suspend fun nfsShares(): List<NfsShare> = Parse.list(rpc.call("sharing.nfs.query")).map(Parse::nfsShare)

    suspend fun setSmbEnabled(id: Int, enabled: Boolean) {
        rpc.call("sharing.smb.update", listOf(id, mapOf("enabled" to enabled)))
    }

    suspend fun setNfsEnabled(id: Int, enabled: Boolean) {
        rpc.call("sharing.nfs.update", listOf(id, mapOf("enabled" to enabled)))
    }

    // ---------- Events ----------

    /** Returns the subscription id, or null if the server doesn't offer that event. */
    suspend fun subscribe(event: String): String? = try {
        rpc.call("core.subscribe", listOf(event), timeoutMs = 15_000) as? String
    } catch (e: Exception) {
        null
    }

    suspend fun unsubscribe(id: String) {
        try {
            rpc.call("core.unsubscribe", listOf(id), timeoutMs = 10_000)
        } catch (_: Exception) {
        }
    }

    // ---------- Jobs ----------

    /**
     * Calls a job method (it answers with a job id right away) and polls core.get_jobs until the
     * job finishes, reporting progress. Throws with the job's error message if it fails.
     */
    suspend fun runJob(
        method: String,
        params: List<Any?>,
        onProgress: (JobProgress) -> Unit = {},
        waitForCompletion: Boolean = true,
        timeoutMs: Long = 20 * 60_000,
    ): Any? {
        val started = rpc.call(method, params, timeoutMs = 120_000)
        val jobId = (started as? Number)?.toInt() ?: return started
        if (!waitForCompletion) return jobId
        val deadline = System.currentTimeMillis() + timeoutMs
        var wait = 400L
        while (System.currentTimeMillis() < deadline) {
            val jobs = rpc.call("core.get_jobs", listOf(listOf(listOf("id", "=", jobId))), timeoutMs = 30_000) as? JSONArray
            val job = jobs?.optJSONObject(0)
            if (job != null) {
                val progress = job.obj("progress")
                onProgress(JobProgress(progress?.int("percent"), progress?.str("description")))
                when (job.str("state")) {
                    "SUCCESS" -> return job.opt("result").jsonOrNull()
                    "FAILED", "ABORTED" -> {
                        val err = job.str("error")
                            ?: job.obj("exc_info")?.str("repr")
                            ?: "The task failed"
                        throw RpcException(-32001, cleanJobError(err))
                    }
                }
            }
            delay(wait)
            wait = (wait * 3 / 2).coerceAtMost(2_000)
        }
        throw RpcException(-32001, "Still running on the server. Check back in a minute.")
    }

    private fun cleanJobError(err: String): String =
        err.lineSequence().firstOrNull { it.isNotBlank() }?.trim()
            ?.removePrefix("[EFAULT] ")?.removePrefix("[EINVAL] ")?.removePrefix("[ENOENT] ")
            ?: err

    /** Tries each method in turn, moving on only when the server says the method doesn't exist. */
    private suspend fun callFirst(vararg candidates: Pair<String, List<Any?>>, timeoutMs: Long = 60_000): Any? {
        var last: RpcException? = null
        for ((method, params) in candidates) {
            try {
                return rpc.call(method, params, timeoutMs)
            } catch (e: RpcException) {
                if (!e.isMethodNotFound) throw e
                last = e
            }
        }
        throw last ?: RpcException(RpcException.METHOD_NOT_FOUND, "Not supported by this TrueNAS version")
    }
}
