package app.truenasmobile.work

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.truenasmobile.MainActivity
import app.truenasmobile.R
import app.truenasmobile.api.Alert
import app.truenasmobile.api.AlertLevel
import app.truenasmobile.data.Connector
import app.truenasmobile.data.Store
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.withTimeout

/**
 * Every ~15 minutes (when the phone has a network), signs in to the active server, reads its
 * alerts and posts a notification for new ones at or above the chosen level. Works whenever the
 * phone can reach the server: at home, or away over a VPN such as Tailscale or WireGuard.
 */
class AlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val store = Store(applicationContext)
        val settings = store.settings()
        if (!settings.notifyAlerts) return Result.success()
        val profile = store.activeServer() ?: return Result.success()
        val secret = profile.secret() ?: return Result.success()

        val outcome = try {
            withTimeout(30_000) { Connector.open(profile, secret) }
        } catch (e: Exception) {
            return Result.success() // server not reachable right now; try again next round
        }
        val api = (outcome as? Connector.Outcome.Ok)?.api ?: run {
            (outcome as? Connector.Outcome.NeedsOtp)?.api?.rpc?.close()
            return Result.success() // 2FA accounts can't be checked in the background
        }
        try {
            val alerts = withTimeout(30_000) { api.alerts() }
            val active = alerts.filter { !it.dismissed }
            val seen = store.seenAlerts(profile.id)
            val fresh = active.filter { it.uuid !in seen && it.level.rank >= settings.notifyLevel.minRank }
            if (fresh.isNotEmpty()) notify(applicationContext, profile.displayName, fresh)
            store.setSeenAlerts(profile.id, active.map { it.uuid }.toSet())
        } catch (_: Exception) {
        } finally {
            api.rpc.close()
        }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "alert-check"
        const val CHANNEL_ID = "alerts"

        fun schedule(context: Context, enabled: Boolean) {
            val wm = WorkManager.getInstance(context)
            if (!enabled) {
                wm.cancelUniqueWork(WORK_NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<AlertWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.alerts_channel),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = context.getString(R.string.alerts_channel_desc) }
            nm.createNotificationChannel(channel)
        }

        fun canNotify(context: Context): Boolean =
            Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

        @SuppressLint("MissingPermission")
        fun notify(context: Context, serverName: String, alerts: List<Alert>) {
            if (!canNotify(context)) return
            ensureChannel(context)
            val open = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_OPEN_ALERTS, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val nm = NotificationManagerCompat.from(context)
            try {
                for (alert in alerts.take(5)) {
                    val title = "${alert.level.label}: $serverName"
                    val n = NotificationCompat.Builder(context, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_stat_alert)
                        .setContentTitle(title)
                        .setContentText(alert.text.lineSequence().firstOrNull() ?: alert.klass)
                        .setStyle(NotificationCompat.BigTextStyle().bigText(alert.text))
                        .setPriority(
                            if (alert.level.rank >= AlertLevel.CRITICAL.rank) NotificationCompat.PRIORITY_HIGH
                            else NotificationCompat.PRIORITY_DEFAULT,
                        )
                        .setCategory(NotificationCompat.CATEGORY_STATUS)
                        .setContentIntent(open)
                        .setAutoCancel(true)
                        .build()
                    nm.notify(alert.uuid.hashCode(), n)
                }
                if (alerts.size > 5) {
                    val n = NotificationCompat.Builder(context, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_stat_alert)
                        .setContentTitle(serverName)
                        .setContentText("${alerts.size - 5} more new alerts")
                        .setContentIntent(open)
                        .setAutoCancel(true)
                        .build()
                    nm.notify("more".hashCode(), n)
                }
            } catch (_: SecurityException) {
            }
        }
    }
}
