package com.resurgeai.core.data.progress

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Backs up encrypted progress photos once there's a connection, retrying (with backoff) until all are up. */
@HiltWorker
class ProgressUploadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: ProgressRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        if (runCatching { repository.uploadPending() }.getOrDefault(false)) Result.success() else Result.retry()

    companion object {
        const val NAME = "progress_upload"
    }
}
