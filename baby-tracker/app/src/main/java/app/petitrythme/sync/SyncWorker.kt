package app.petitrythme.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.petitrythme.BabyApp
import java.util.concurrent.TimeUnit

/** Synchro en arrière-plan (après une saisie depuis le widget, ou toutes les 15 min). */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val r = (applicationContext as BabyApp).sync.sync()
        val e = r.exceptionOrNull()
        return when {
            e == null -> Result.success()
            e is SyncException && e.code == "network" && runAttemptCount < 5 -> Result.retry()
            else -> Result.failure()
        }
    }
}

object SyncScheduler {
    private const val NOW = "sync-now"
    private const val PERIODIC = "sync-periodic"
    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Synchro dans quelques secondes (regroupe les saisies rapprochées). */
    fun requestSoon(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            NOW, ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(network).setInitialDelay(2, TimeUnit.SECONDS).build(),
        )
    }

    fun enablePeriodic(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).setConstraints(network).build(),
        )
    }

    fun disable(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(NOW)
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
    }
}
