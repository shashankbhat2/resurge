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
import com.resurgeai.core.data.food.MealsRepository
import com.resurgeai.core.data.prefs.PreferencesRepository
import com.resurgeai.core.model.MealReminders
import com.resurgeai.core.model.MealType
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
 * Breakfast, lunch and dinner reminders: one inexact alarm per meal (no exact-alarm permission).
 * When one fires it only notifies if that meal isn't logged yet today, then re-arms itself.
 * The notification offers Scan (straight to the camera) and Type.
 */
object MealReminder {
    const val CHANNEL = "meals"
    private const val ACTION_FIRE = "com.resurgeai.app.MEAL_REMINDER"
    private const val EXTRA_MEAL = "meal"
    const val OPEN_SCAN = "food_scan"
    const val OPEN_TYPE = "food_type"
    const val OPEN_FOOD = "food"

    private val meals = listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER)
    private fun request(type: MealType) = 4300 + type.ordinal

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL, "Meal reminders", NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = "A nudge at breakfast, lunch and dinner when the meal isn't logged yet" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun scheduleAll(context: Context, reminders: MealReminders, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()) {
        meals.forEach { schedule(context, it, reminders, now, zone) }
    }

    fun schedule(context: Context, type: MealType, reminders: MealReminders, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val pending = pendingIntent(context, type)
        alarms.cancel(pending)
        val time = reminders.time(type) ?: return
        val local = now.atZone(zone)
        var at = local.toLocalDate().atTime(time).atZone(zone)
        if (!at.isAfter(local)) at = at.plusDays(1)
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toInstant().toEpochMilli(), pending)
    }

    private fun pendingIntent(context: Context, type: MealType): PendingIntent = PendingIntent.getBroadcast(
        context,
        request(type),
        Intent(context, MealReminderReceiver::class.java).setAction(ACTION_FIRE).putExtra(EXTRA_MEAL, type.wire),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    internal fun firedMeal(intent: Intent?): MealType? =
        if (intent?.action == ACTION_FIRE) intent.getStringExtra(EXTRA_MEAL)?.let(MealType::fromWire) else null

    private fun open(context: Context, type: MealType, target: String, offset: Int) = PendingIntent.getActivity(
        context,
        request(type) * 10 + offset,
        Intent(context, MainActivity::class.java)
            .putExtra(ResurgeMessagingService.EXTRA_OPEN, target)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    internal fun notify(context: Context, type: MealType) {
        val (title, text) = when (type) {
            MealType.BREAKFAST -> "Logged breakfast?" to "Snap your plate or type what you had."
            MealType.LUNCH -> "What's for lunch?" to "A quick photo keeps today's calories and protein on track."
            else -> "Log your dinner" to "Scan it or type it in to close out the day."
        }
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open(context, type, OPEN_FOOD, 0))
            .addAction(0, "Scan", open(context, type, OPEN_SCAN, 1))
            .addAction(0, "Type", open(context, type, OPEN_TYPE, 2))
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(request(type), notification) }
    }
}

/** Fires a meal reminder, and re-arms all of them after reboot / time or timezone changes. */
@AndroidEntryPoint
class MealReminderReceiver : BroadcastReceiver() {
    @Inject lateinit var meals: MealsRepository
    @Inject lateinit var preferences: PreferencesRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent?) {
        val result = goAsync()
        scope.launch {
            try {
                val reminders = preferences.mealRemindersMirror() ?: MealReminders()
                val fired = MealReminder.firedMeal(intent)
                if (fired != null) {
                    val today = LocalDate.now(ZoneId.systemDefault())
                    val logged = runCatching { meals.hasMeal(today, fired) }.getOrDefault(false)
                    if (!logged && reminders.time(fired) != null) MealReminder.notify(context, fired)
                    MealReminder.schedule(context, fired, reminders)
                } else {
                    MealReminder.scheduleAll(context, reminders)
                }
            } finally {
                result.finish()
            }
        }
    }
}
