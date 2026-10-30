package app.truenasmobile.update

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.truenasmobile.data.Store
import java.util.concurrent.TimeUnit

/**
 * Once a day: looks for a new version of the app. Installs it straight away when
 * "Install updates automatically" is on, otherwise posts a notification.
 */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val store = Store(applicationContext)
        val settings = store.settings()
        if (!settings.checkUpdates) return Result.success()
        val release = try {
            Updater.latest()
        } catch (e: Exception) {
            return Result.success() // offline; try again tomorrow
        } ?: return Result.success()
        store.lastUpdateCheck = System.currentTimeMillis()
        if (!Releases.isNewer(release, Updater.currentBuild)) return Result.success()

        if (settings.autoInstallUpdates && Updater.canInstall(applicationContext)) {
            if (Updater.downloadAndInstall(applicationContext, release)) return Result.success()
        }
        if (store.notifiedUpdate != release.build) {
            Updater.notifyAvailable(applicationContext, release)
            store.notifiedUpdate = release.build
        }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "app-update-check"

        fun schedule(context: Context, enabled: Boolean) {
            val wm = WorkManager.getInstance(context)
            if (!enabled) {
                wm.cancelUniqueWork(WORK_NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<UpdateWorker>(24, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
