package com.resurgeai.app.scale

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.resurgeai.core.data.user.UserRepository
import com.resurgeai.core.model.UnitSystem
import com.resurgeai.core.scale.ScaleBluetooth
import com.resurgeai.core.scale.ScaleReadiness
import com.resurgeai.core.scale.ScaleScanner
import com.resurgeai.core.scale.ScaleStore
import com.resurgeai.core.scale.WeighInEngine
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import kotlin.math.abs

/**
 * Listens closely to the paired scales for up to a minute, showing the weight as it settles,
 * and stops once a reading is logged. Started when the background scan hears a scale wake up,
 * or by hand from the widget or Smart scale settings.
 */
@AndroidEntryPoint
class WeighInService : Service() {
    @Inject lateinit var scanner: ScaleScanner
    @Inject lateinit var bluetooth: ScaleBluetooth
    @Inject lateinit var store: ScaleStore
    @Inject lateinit var engine: WeighInEngine
    @Inject lateinit var units: ScaleUnits

    enum class Trigger { SCALE_WOKE, USER }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val trigger = intent?.getStringExtra(EXTRA_TRIGGER)?.let { runCatching { Trigger.valueOf(it) }.getOrNull() } ?: Trigger.USER
        val started = runCatching {
            ServiceCompat.startForeground(
                this,
                ScaleNotifications.LISTENING_ID,
                ScaleNotifications.listening(this, "Listening for your scale", if (trigger == Trigger.USER) "Step on when you're ready." else "Hold still for a moment."),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        }.onFailure { Log.w(TAG, "Couldn't start listening in the foreground", it) }.isSuccess
        if (!started) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (job?.isActive != true) job = scope.launch { listen(trigger) }
        return START_NOT_STICKY
    }

    private suspend fun listen(trigger: Trigger) {
        try {
            val scales = store.current().scales
            if (scales.isEmpty() || bluetooth.readiness() != ScaleReadiness.READY) return
            val system = units.current()
            val names = scales.joinToString(" or ") { it.model }
            update("Listening for your $names", if (trigger == Trigger.USER) "Step on when you're ready." else "Hold still for a moment.")

            var lastShownKg: Double? = null
            withTimeoutOrNull(if (trigger == Trigger.USER) USER_WINDOW_MS else WOKE_WINDOW_MS) {
                scanner.scan(scales.map { it.address }.toSet()).transformWhile { ad ->
                    val outcome = engine.offer(ad)
                    engine.live.value?.let { live ->
                        if (lastShownKg?.let { abs(live.kg - it) < 0.05 } != true) {
                            lastShownKg = live.kg
                            update(ScaleNotifications.weightText(live.kg, system) + "…", "Hold still on your ${live.scale.model}.")
                        }
                    }
                    emit(Unit)
                    outcome == null
                }.collect {}
            }
        } finally {
            engine.clearLive()
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    @SuppressLint("MissingPermission") // checked by canNotify
    private fun update(title: String, text: String) {
        if (!ScaleNotifications.canNotify(this)) return
        runCatching {
            NotificationManagerCompat.from(this).notify(ScaleNotifications.LISTENING_ID, ScaleNotifications.listening(this, title, text))
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "WeighInService"
        private const val EXTRA_TRIGGER = "trigger"
        private const val USER_WINDOW_MS = 60_000L
        private const val WOKE_WINDOW_MS = 40_000L

        fun intent(context: Context, trigger: Trigger): Intent =
            Intent(context, WeighInService::class.java).putExtra(EXTRA_TRIGGER, trigger.name)

        fun start(context: Context, trigger: Trigger) {
            runCatching { ContextCompat.startForegroundService(context, intent(context, trigger)) }
                .onFailure { Log.w(TAG, "Couldn't start the weigh-in service", it) }
        }
    }
}

/** The user's display units, readable without a screen (falls back to metric). */
class ScaleUnits @Inject constructor(
    private val auth: FirebaseAuth,
    private val users: UserRepository,
) {
    suspend fun current(): UnitSystem {
        val uid = auth.currentUser?.uid ?: return UnitSystem.METRIC
        return withTimeoutOrNull(3_000) { runCatching { users.observeProfile(uid).first()?.units }.getOrNull() } ?: UnitSystem.METRIC
    }
}
