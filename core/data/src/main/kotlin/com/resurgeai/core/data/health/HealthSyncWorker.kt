package com.resurgeai.core.data.health

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.resurgeai.core.healthconnect.HealthConnectManager
import com.resurgeai.core.healthconnect.HealthPermissions
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Periodic background sync. Health Connect only allows background reads when the user granted
 * READ_HEALTH_DATA_IN_BACKGROUND; otherwise syncing happens while the app is open.
 */
@HiltWorker
class HealthSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val sync: HealthSyncRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result =
        if (sync.syncRecent().isSuccess) Result.success() else Result.retry()

    companion object {
        private const val WORK_NAME = "health-sync"

        /** Schedules the daily job if background reads are granted; cancels it otherwise. */
        suspend fun schedule(context: Context, healthConnect: HealthConnectManager) {
            val workManager = WorkManager.getInstance(context)
            if (HealthPermissions.BACKGROUND !in healthConnect.grantedPermissions()) {
                workManager.cancelUniqueWork(WORK_NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<HealthSyncWorker>(12, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .build(),
                )
                .build()
            workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
