package com.resurgeai.app.progress

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.resurgeai.app.MainActivity
import com.resurgeai.app.R
import com.resurgeai.app.notifications.ResurgeMessagingService
import com.resurgeai.core.data.progress.PhotoKeys
import com.resurgeai.core.data.progress.ProgressRepository
import com.resurgeai.core.data.progress.ReelRepository
import com.resurgeai.core.model.PhotoKeyState
import com.resurgeai.core.model.ProgressReel
import com.resurgeai.core.model.ReelKind
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Progress reels arrive on their own, like a photos app's memories: every morning an inexact alarm
 * (no exact-alarm permission) starts [ReelWorker], which makes a reel if one is due, downloads its
 * photos so it plays at once, then notifies. The notification never shows a photo or a weight.
 */
object ReelReminder {
    const val CHANNEL = "progress_reels"
    const val OPEN_REEL = "progress_reel"
    const val EXTRA_REEL = "reel"
    private const val ACTION_FIRE = "com.resurgeai.app.REEL_CHECK"
    private const val REQUEST = 4401
    private val AT: LocalTime = LocalTime.of(9, 30)

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL, "Snapshot reels", NotificationManager.IMPORTANCE_DEFAULT)
            .apply {
                description = "When a new reel is ready"
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun schedule(context: Context, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val pending = PendingIntent.getBroadcast(
            context,
            REQUEST,
            Intent(context, ReelReminderReceiver::class.java).setAction(ACTION_FIRE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val local = now.atZone(zone)
        var at = local.toLocalDate().atTime(AT).atZone(zone)
        if (!at.isAfter(local)) at = at.plusDays(1)
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toInstant().toEpochMilli(), pending)
    }

    internal fun isFire(intent: Intent?) = intent?.action == ACTION_FIRE

    fun runCheck(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            ReelWorker.NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<ReelWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build(),
        )
    }

    internal fun notify(context: Context, reel: ProgressReel) {
        val open = PendingIntent.getActivity(
            context,
            reel.id.hashCode(),
            Intent(context, MainActivity::class.java)
                .putExtra(ResurgeMessagingService.EXTRA_OPEN, OPEN_REEL)
                .putExtra(EXTRA_REEL, reel.id)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val (title, text) = copy(reel)
        val public = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("New reel ready")
            .build()
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(REQUEST, notification) }
    }

    internal fun copy(reel: ProgressReel): Pair<String, String> {
        val days = ChronoUnit.DAYS.between(reel.from, reel.to)
        val span = if (days >= 14) "${days / 7}-week" else "$days-day"
        return when (reel.kind) {
            ReelKind.GOAL -> "You reached your goal" to "Watch your reel"
            ReelKind.MILESTONE -> "New milestone" to "Watch your reel"
            ReelKind.REGULAR -> "Your $span reel is ready" to "Tap to watch"
        }
    }
}

@HiltWorker
class ReelWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val reels: ReelRepository,
    private val photos: ProgressRepository,
    private val keys: PhotoKeys,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        // Without the key on this phone the reel couldn't play here; another phone of theirs makes it.
        if (runCatching { keys.refresh() }.getOrNull() != PhotoKeyState.READY) return Result.success()
        val reel = runCatching { reels.check() }.onFailure { Log.w(TAG, "Reel check failed", it) }.getOrElse { return Result.retry() }
            ?: return Result.success()
        runCatching { photos.prefetch(reel.frameIds) }.onFailure { Log.w(TAG, "Prefetch failed", it) }
        ReelReminder.notify(applicationContext, reel)
        return Result.success()
    }

    companion object {
        const val NAME = "progress_reel_check"
        private const val TAG = "ReelWorker"
    }
}

/** Runs the morning check, and re-arms it after reboot, app update, or time and timezone changes. */
class ReelReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (ReelReminder.isFire(intent)) ReelReminder.runCheck(context)
        ReelReminder.schedule(context)
    }
}
