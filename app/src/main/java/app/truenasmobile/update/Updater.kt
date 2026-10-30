package app.truenasmobile.update

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.pm.PackageInfoCompat
import app.truenasmobile.BuildConfig
import app.truenasmobile.MainActivity
import app.truenasmobile.R
import app.truenasmobile.data.Store
import app.truenasmobile.work.AlertWorker
import java.io.File
import java.io.IOException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

sealed interface UpdateStatus {
    data object Idle : UpdateStatus
    data object Checking : UpdateStatus
    data object UpToDate : UpdateStatus
    data class Available(val release: Release) : UpdateStatus
    data class Downloading(val release: Release, val progress: Float?) : UpdateStatus
    /** Handed to Android; it may be waiting for the user to confirm. */
    data class Installing(val release: Release) : UpdateStatus
    data class Failed(val message: String, val release: Release?) : UpdateStatus
}

/**
 * Updates this app from its GitHub Releases: checks for a newer build, downloads the APK and
 * installs it with Android's PackageInstaller. Every release is signed with the same key, so a
 * new version installs over the old one and keeps the saved servers.
 *
 * The first update asks the user to confirm (and to allow installs from this app). On Android 12
 * and newer, later updates can then install without asking, because this app installed itself.
 */
object Updater {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _status = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val status: StateFlow<UpdateStatus> = _status.asStateFlow()

    /** A newer version to offer in a dialog. */
    val prompt = MutableStateFlow<Release?>(null)

    /** Whether the app is on screen (set by MainActivity), so the install prompt can open directly. */
    @Volatile var inForeground = false

    const val CHANNEL_ID = "updates"
    private const val CHECK_EVERY_MS = 6 * 60 * 60 * 1000L
    private const val NOTIFY_ID = 4201

    val currentVersion: String get() = BuildConfig.VERSION_NAME
    val currentBuild: Int get() = BuildConfig.VERSION_CODE

    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    // ------------------------------------------------------------------ checking

    /** When the app opens: checks now and then (if turned on) and offers a new version once. */
    fun checkOnLaunch(context: Context) {
        val store = Store(context.applicationContext)
        if (!store.settings().checkUpdates) return
        if (System.currentTimeMillis() - store.lastUpdateCheck < CHECK_EVERY_MS) {
            return
        }
        check(context, interactive = false)
    }

    /** "Check for updates" button, or a tap on the update notification: always checks and offers what it finds. */
    fun checkNow(context: Context) = check(context, interactive = true)

    private fun check(context: Context, interactive: Boolean) {
        val app = context.applicationContext
        val busy = _status.value
        if (busy is UpdateStatus.Checking || busy is UpdateStatus.Downloading || busy is UpdateStatus.Installing) return
        _status.value = UpdateStatus.Checking
        scope.launch {
            val store = Store(app)
            try {
                val release = latest()
                store.lastUpdateCheck = System.currentTimeMillis()
                if (release != null && Releases.isNewer(release, currentBuild)) {
                    _status.value = UpdateStatus.Available(release)
                    if (interactive || store.dismissedUpdate != release.build) prompt.value = release
                } else {
                    _status.value = UpdateStatus.UpToDate
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _status.value = if (interactive) UpdateStatus.Failed("Couldn't check for updates. ${friendly(e)}", null) else UpdateStatus.Idle
            }
        }
    }

    /** The newest release on GitHub, or null if there isn't one with an APK. */
    suspend fun latest(): Release? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(Releases.LATEST_URL)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "TrueNAS-Mobile/$currentVersion")
            .build()
        http.newCall(request).execute().use { resp ->
            if (resp.code == 404) return@use null
            if (!resp.isSuccessful) throw IOException("GitHub answered with error ${resp.code}.")
            Releases.parse(JSONObject(resp.body?.string() ?: "{}"))
        }
    }

    /** "Later": don't offer this version again automatically (Settings still shows it). */
    fun dismiss(context: Context, release: Release) {
        Store(context.applicationContext).dismissedUpdate = release.build
        prompt.value = null
    }

    // ------------------------------------------------------------------ installing

    /** Android 8+ asks the user once to allow this app to install apps. */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun installPermissionIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    /** Downloads [release] and installs it. Progress shows in [status]. */
    fun update(context: Context, release: Release) {
        val busy = _status.value
        if (busy is UpdateStatus.Downloading || busy is UpdateStatus.Installing) return
        val app = context.applicationContext
        scope.launch { downloadAndInstall(app, release) }
    }

    /** Also used by the background worker. Returns false if it failed (the reason is in [status]). */
    suspend fun downloadAndInstall(context: Context, release: Release): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = download(context, release)
            install(context, release, file)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _status.value = UpdateStatus.Failed(friendly(e), release)
            false
        }
    }

    private fun download(context: Context, release: Release): File {
        _status.value = UpdateStatus.Downloading(release, null)
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "truenas-mobile-${release.version}.apk")
        val request = Request.Builder().url(release.apkUrl).header("User-Agent", "TrueNAS-Mobile/$currentVersion").build()
        http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("The download failed (error ${resp.code}).")
            val body = resp.body ?: throw IOException("The download was empty.")
            val total = body.contentLength().takeIf { it > 0 } ?: release.apkSize.takeIf { it > 0 }
            var done = 0L
            var shown = -1
            body.byteStream().use { input ->
                file.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        done += n
                        if (total != null) {
                            val percent = (done * 100 / total).toInt()
                            if (percent != shown) {
                                shown = percent
                                _status.value = UpdateStatus.Downloading(release, (done.toFloat() / total).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
            }
        }
        if (release.apkSize > 0 && file.length() != release.apkSize) {
            file.delete()
            throw IOException("The download was incomplete.")
        }
        // Only ever install a newer build of this same app.
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageArchiveInfo(file.path, 0)
            ?: throw IOException("The downloaded file isn't a valid app.")
        if (info.packageName != context.packageName) throw IOException("The downloaded file is a different app.")
        if (PackageInfoCompat.getLongVersionCode(info) <= currentBuild) throw IOException("The download isn't newer than this version.")
        return file
    }

    private fun install(context: Context, release: Release, file: File) {
        _status.value = UpdateStatus.Installing(release)
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(file.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            file.inputStream().use { input ->
                session.openWrite("base.apk", 0, file.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            Store(context).pendingUpdate = release.version
            val intent = Intent(context, InstallReceiver::class.java).setAction(InstallReceiver.ACTION_STATUS)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            session.commit(PendingIntent.getBroadcast(context, id, intent, flags).intentSender)
        }
        file.delete() // Android has its own copy now
    }

    /** Called by [InstallReceiver] with Android's answer. */
    internal fun onInstallResult(context: Context, status: Int, message: String?, confirm: Intent?) {
        val release = (_status.value as? UpdateStatus.Installing)?.release
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                if (confirm == null) return
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (inForeground) {
                    try {
                        context.startActivity(confirm)
                        return
                    } catch (_: Exception) {
                    }
                }
                // In the background (or if Android blocked it): a notification to finish the update.
                notify(
                    context,
                    "Update ready to install",
                    "Tap to install TrueNAS Mobile ${release?.version ?: ""}".trim(),
                    PendingIntent.getActivity(context, 1, confirm, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
                )
            }
            PackageInstaller.STATUS_SUCCESS -> _status.value = UpdateStatus.Idle
            PackageInstaller.STATUS_FAILURE_ABORTED -> {
                // The user said no on Android's prompt; let them try again.
                Store(context).pendingUpdate = null
                _status.value = release?.let { UpdateStatus.Available(it) } ?: UpdateStatus.Idle
            }
            else -> {
                Store(context).pendingUpdate = null
                val reason = when (status) {
                    PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                        "Android couldn't install over this version (${message ?: "conflict"}). Install the APK from the Releases page instead."
                    PackageInstaller.STATUS_FAILURE_STORAGE -> "There isn't enough free space on the phone."
                    PackageInstaller.STATUS_FAILURE_BLOCKED -> "Android blocked the update. Check that installs from TrueNAS Mobile are allowed."
                    else -> "The update didn't install${message?.let { " ($it)" } ?: ""}."
                }
                _status.value = UpdateStatus.Failed(reason, release)
            }
        }
    }

    /** After an update this app started: say it's done, with a tap to open the new version. */
    internal fun onUpdated(context: Context) {
        val store = Store(context)
        val version = store.pendingUpdate ?: return
        store.pendingUpdate = null
        val open = PendingIntent.getActivity(
            context,
            2,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        notify(context, "TrueNAS Mobile updated", "Now on version ${BuildConfig.VERSION_NAME.ifBlank { version }}. Tap to open.", open)
    }

    /** From the background check: a new version is out (when it isn't installed automatically). */
    internal fun notifyAvailable(context: Context, release: Release) {
        val open = PendingIntent.getActivity(
            context,
            3,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_SHOW_UPDATE, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        notify(context, "TrueNAS Mobile ${release.version} is available", "Tap to update.", open)
    }

    @SuppressLint("MissingPermission")
    private fun notify(context: Context, title: String, text: String, tap: PendingIntent) {
        if (!AlertWorker.canNotify(context)) return
        ensureChannel(context)
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_update)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFY_ID, n)
        } catch (_: SecurityException) {
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "App updates", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "New versions of TrueNAS Mobile" },
        )
    }

    private fun friendly(e: Exception): String = when (e) {
        is UnknownHostException -> "No internet connection."
        is java.net.SocketTimeoutException -> "GitHub took too long to answer."
        else -> e.message ?: "Something went wrong."
    }
}
