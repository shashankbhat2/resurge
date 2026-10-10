package com.resurgeai.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.resurgeai.app.MainActivity
import com.resurgeai.app.R
import com.resurgeai.core.data.coach.DeviceRegistration
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Receives coach insight notifications. In the background the system shows them from the
 * notification payload; in the foreground we post one ourselves. Tapping opens Coach › Insights.
 */
@AndroidEntryPoint
class ResurgeMessagingService : FirebaseMessagingService() {
    @Inject lateinit var devices: DeviceRegistration

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        scope.launch { devices.register(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val n = message.notification ?: return
        val notification = NotificationCompat.Builder(this, CHANNEL_INSIGHTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(n.title)
            .setContentText(n.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(n.body))
            .setContentIntent(openInsightsIntent(this))
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(this).notify(message.messageId.hashCode(), notification) }
    }

    companion object {
        const val CHANNEL_INSIGHTS = "insights"
        const val EXTRA_OPEN = "open"
        const val OPEN_INSIGHTS = "insights"

        fun createChannel(context: Context) {
            val channel = NotificationChannel(CHANNEL_INSIGHTS, "Coach insights", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Insights after workouts and sleep, and your weekly review" }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        fun openInsightsIntent(context: Context): PendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_OPEN, OPEN_INSIGHTS)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
