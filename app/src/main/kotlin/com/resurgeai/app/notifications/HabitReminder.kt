package com.resurgeai.app.notifications

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.resurgeai.app.MainActivity
import com.resurgeai.app.R
import com.resurgeai.core.data.habits.HabitsRepository
import com.resurgeai.core.data.prefs.PreferencesRepository
import com.resurgeai.core.model.HabitSettings
import com.resurgeai.core.model.HabitStreaks
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Evening habit reminder. Scheduled locally with an inexact alarm (no exact-alarm permission;
 * Android may deliver it a few minutes late). When it fires, it only notifies if habits are
 * still unlogged for the current habit day, then schedules the next one.
 */
object HabitReminder {
    const val CHANNEL = "habits"
    private const val ACTION_FIRE = "com.resurgeai.app.HABIT_REMINDER"
    private const val REQUEST = 4201
    const val OPEN_HABITS = "habits"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL, "Habit reminders", NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = "A nudge in the evening when habits are still unlogged" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun schedule(context: Context, settings: HabitSettings, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val pending = pendingIntent(context)
        alarms.cancel(pending)
        if (!settings.reminderEnabled) return
        val local = now.atZone(zone)
        var at = local.toLocalDate().atTime(settings.reminderTime).atZone(zone)
        if (!at.isAfter(local)) at = at.plusDays(1)
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toInstant().toEpochMilli(), pending)
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST,
        Intent(context, HabitReminderReceiver::class.java).setAction(ACTION_FIRE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    internal fun isFire(intent: Intent?) = intent?.action == ACTION_FIRE

    internal fun notify(context: Context, pendingCount: Int) {
        val open = PendingIntent.getActivity(
            context,
            REQUEST,
            Intent(context, MainActivity::class.java)
                .putExtra(ResurgeMessagingService.EXTRA_OPEN, OPEN_HABITS)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = if (pendingCount == 1) "1 habit is still waiting for today." else "$pendingCount habits are still waiting for today."
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Keep your streaks going")
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(REQUEST, notification) }
    }
}

/** Fires the reminder, and re-arms it after reboot / time or timezone changes. */
@AndroidEntryPoint
class HabitReminderReceiver : BroadcastReceiver() {
    @Inject lateinit var habits: HabitsRepository
    @Inject lateinit var preferences: PreferencesRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent?) {
        val result = goAsync()
        scope.launch {
            try {
                val settings = preferences.habitSettingsMirror() ?: return@launch
                if (HabitReminder.isFire(intent)) {
                    val today: LocalDate = HabitStreaks.habitDay(Instant.now(), ZoneId.systemDefault(), settings.resetTime)
                    val active = runCatching { habits.habitsOnce().filterNot { it.archived } }.getOrDefault(emptyList())
                    val logged = runCatching { habits.dayOnce(today) }.getOrDefault(emptyMap())
                    val pending = active.count { it.id !in logged }
                    if (pending > 0) HabitReminder.notify(context, pending)
                }
                HabitReminder.schedule(context, settings)
            } finally {
                result.finish()
            }
        }
    }
}
