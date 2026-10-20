package com.resurgeai.app.scale

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
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
import com.resurgeai.app.MainActivity
import com.resurgeai.app.R
import com.resurgeai.app.notifications.ResurgeMessagingService
import com.resurgeai.core.model.UnitSystem
import com.resurgeai.core.model.WeightMath
import com.resurgeai.core.scale.ScaleOutcome
import java.util.Locale

/**
 * Smart scale notifications: a quiet one while listening (the weigh-in service's), and one per
 * reading with Undo, or "Was this you?" when it looks like someone else's.
 * Weights stay off the lock screen.
 */
object ScaleNotifications {
    const val CHANNEL_LISTENING = "scale_listening"
    const val CHANNEL_READINGS = "scale_readings"
    const val OPEN_WEIGHT = "weight"
    const val OPEN_SMART_SCALE = "smart_scale"

    const val LISTENING_ID = 4501
    const val READING_ID = 4502

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_LISTENING, "Listening for your scale", NotificationManager.IMPORTANCE_LOW)
                .apply {
                    description = "Shown while Resurge picks up a reading from your smart scale"
                    setShowBadge(false)
                },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_READINGS, "Weigh-ins from your scale", NotificationManager.IMPORTANCE_DEFAULT)
                .apply {
                    description = "Each weigh-in your smart scale logs, with undo"
                    lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                },
        )
    }

    fun listening(context: Context, title: String, text: String): Notification =
        NotificationCompat.Builder(context, CHANNEL_LISTENING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openApp(context, OPEN_WEIGHT))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

    @SuppressLint("MissingPermission") // checked by canNotify
    fun outcome(context: Context, outcome: ScaleOutcome, units: UnitSystem) {
        val weight = weightText(outcome.kg, units)
        val builder = NotificationCompat.Builder(context, CHANNEL_READINGS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openApp(context, OPEN_WEIGHT))
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(context, CHANNEL_READINGS)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle("New weigh-in from your scale")
                    .build(),
            )
        when (outcome) {
            is ScaleOutcome.Saved -> builder
                .setContentTitle("Logged $weight")
                .setContentText("From your ${outcome.scale.model}")
                .setTimeoutAfter(SAVED_TIMEOUT_MS)
                .addAction(0, "Undo", ScaleActionReceiver.undo(context, outcome))
            is ScaleOutcome.Unusual -> builder
                .setContentTitle("$weight. Was this you?")
                .setContentText("It's a way off your recent ${weightText(outcome.recentKg, units)}, so it isn't logged yet.")
                .addAction(0, "Log it", ScaleActionReceiver.confirm(context, outcome))
                .addAction(0, "Not me", ScaleActionReceiver.dismiss(context))
            is ScaleOutcome.Repeat -> return
        }
        if (canNotify(context)) runCatching { NotificationManagerCompat.from(context).notify(READING_ID, builder.build()) }
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun cancelReading(context: Context) = NotificationManagerCompat.from(context).cancel(READING_ID)

    fun weightText(kg: Double, units: UnitSystem): String =
        if (units == UnitSystem.IMPERIAL) "%.1f lb".format(Locale.getDefault(), kg / WeightMath.KG_PER_LB)
        else "%.1f kg".format(Locale.getDefault(), kg)

    fun openApp(context: Context, open: String): PendingIntent = PendingIntent.getActivity(
        context,
        open.hashCode(),
        Intent(context, MainActivity::class.java)
            .putExtra(ResurgeMessagingService.EXTRA_OPEN, open)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Long enough to notice and undo; after that it's just in the log. */
    private const val SAVED_TIMEOUT_MS = 30 * 60_000L
}
