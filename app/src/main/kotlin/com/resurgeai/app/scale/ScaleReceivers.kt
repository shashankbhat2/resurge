package com.resurgeai.app.scale

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.resurgeai.core.data.weight.WeightRepository
import com.resurgeai.core.model.WeighIn
import com.resurgeai.core.model.WeighInSource
import com.resurgeai.core.scale.ScaleControl
import com.resurgeai.core.scale.ScaleOutcome
import com.resurgeai.core.scale.ScaleScanner
import com.resurgeai.core.scale.ScaleStore
import com.resurgeai.core.scale.WeighInEngine
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

/**
 * Woken by the system's background scan when a paired scale advertises, i.e. someone just
 * stepped on it. Starts [WeighInService] to listen closely, and feeds what was heard to the
 * engine meanwhile, so a reading still lands if the service can't start.
 *
 * Also re-arms the background scan after a reboot or an app update, which both clear it.
 */
@AndroidEntryPoint
class ScaleScanReceiver : BroadcastReceiver() {
    @Inject lateinit var engine: WeighInEngine
    @Inject lateinit var store: ScaleStore
    @Inject lateinit var control: ScaleControl

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SCAN_RESULT) {
            control.syncBackgroundListening()
            return
        }
        val ads = ScaleScanner.advertisementsFrom(intent)
        if (ads.isEmpty()) return
        val result = goAsync()
        scope.launch {
            try {
                val settings = store.current()
                if (!settings.autoLog) return@launch
                val heard = ads.filter { ad -> settings.scales.any { it.address.equals(ad.address, ignoreCase = true) && it.protocol?.decode(ad) != null } }
                if (heard.isEmpty()) return@launch
                WeighInService.start(context, WeighInService.Trigger.SCALE_WOKE)
                heard.forEach { engine.offer(it) }
            } finally {
                result.finish()
            }
        }
    }

    companion object {
        const val ACTION_SCAN_RESULT = "com.resurgeai.app.SCALE_SCAN_RESULT"
        private const val REQUEST = 4510

        /** Mutable: the system fills in the scan results. */
        fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST,
            Intent(context, ScaleScanReceiver::class.java).setAction(ACTION_SCAN_RESULT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }
}

/** Undo, "Log it" and "Not me" on weigh-in notifications. */
@AndroidEntryPoint
class ScaleActionReceiver : BroadcastReceiver() {
    @Inject lateinit var weights: WeightRepository
    @Inject lateinit var engine: WeighInEngine
    @Inject lateinit var store: ScaleStore

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        ScaleNotifications.cancelReading(context)
        val kg = intent.getDoubleExtra(EXTRA_KG, Double.NaN)
        val at = Instant.ofEpochMilli(intent.getLongExtra(EXTRA_AT, 0))
        when (intent.action) {
            ACTION_UNDO -> {
                val id = intent.getStringExtra(EXTRA_ID) ?: return
                weights.delete(WeighIn(id, kg, at, source = WeighInSource.SCALE, device = intent.getStringExtra(EXTRA_MODEL)))
            }
            ACTION_CONFIRM -> {
                val address = intent.getStringExtra(EXTRA_ADDRESS) ?: return
                val result = goAsync()
                scope.launch {
                    try {
                        val scale = store.current().scales.firstOrNull { it.address == address } ?: return@launch
                        val impedance = intent.getIntExtra(EXTRA_IMPEDANCE, 0).takeIf { it > 0 }
                        engine.confirm(ScaleOutcome.Unusual(scale, kg, at, impedance, intent.getDoubleExtra(EXTRA_RECENT, kg)))
                    } finally {
                        result.finish()
                    }
                }
            }
        }
    }

    companion object {
        private const val ACTION_UNDO = "com.resurgeai.app.SCALE_UNDO"
        private const val ACTION_CONFIRM = "com.resurgeai.app.SCALE_CONFIRM"
        private const val ACTION_DISMISS = "com.resurgeai.app.SCALE_DISMISS"
        private const val EXTRA_ID = "id"
        private const val EXTRA_KG = "kg"
        private const val EXTRA_AT = "at"
        private const val EXTRA_MODEL = "model"
        private const val EXTRA_ADDRESS = "address"
        private const val EXTRA_IMPEDANCE = "impedance"
        private const val EXTRA_RECENT = "recent"

        fun undo(context: Context, saved: ScaleOutcome.Saved): PendingIntent = broadcast(
            context,
            Intent(context, ScaleActionReceiver::class.java).setAction(ACTION_UNDO)
                .putExtra(EXTRA_ID, saved.weighInId)
                .putExtra(EXTRA_KG, saved.kg)
                .putExtra(EXTRA_AT, saved.at.toEpochMilli())
                .putExtra(EXTRA_MODEL, saved.scale.model),
        )

        fun confirm(context: Context, unusual: ScaleOutcome.Unusual): PendingIntent = broadcast(
            context,
            Intent(context, ScaleActionReceiver::class.java).setAction(ACTION_CONFIRM)
                .putExtra(EXTRA_ADDRESS, unusual.scale.address)
                .putExtra(EXTRA_KG, unusual.kg)
                .putExtra(EXTRA_AT, unusual.at.toEpochMilli())
                .putExtra(EXTRA_IMPEDANCE, unusual.impedanceOhm ?: 0)
                .putExtra(EXTRA_RECENT, unusual.recentKg),
        )

        fun dismiss(context: Context): PendingIntent =
            broadcast(context, Intent(context, ScaleActionReceiver::class.java).setAction(ACTION_DISMISS))

        private fun broadcast(context: Context, intent: Intent): PendingIntent =
            PendingIntent.getBroadcast(context, intent.action.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
