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
 * Checks Health Connect every 15 minutes (WorkManager's minimum) for finished workouts/sleep so
 * insights arrive soon after an activity ends. Needs READ_HEALTH_DATA_IN_BACKGROUND; without it,
 * the check runs when the app is opened instead.
 */
@HiltWorker
class ActivityEventWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val events: ActivityEventSync,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = runCatching { events.syncFinished() }
        .fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })

    companion object {
        private const val WORK_NAME = "activity-events"

        suspend fun schedule(context: Context, healthConnect: HealthConnectManager) {
            val workManager = WorkManager.getInstance(context)
            if (HealthPermissions.BACKGROUND !in healthConnect.grantedPermissions()) {
                workManager.cancelUniqueWork(WORK_NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<ActivityEventWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
