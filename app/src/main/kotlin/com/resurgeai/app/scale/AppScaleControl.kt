package com.resurgeai.app.scale

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.resurgeai.app.widget.WeightWidget
import com.resurgeai.core.data.weight.WeightChangeListener
import com.resurgeai.core.scale.ScaleControl
import com.resurgeai.core.scale.ScaleScanner
import com.resurgeai.core.scale.ScaleStore
import com.resurgeai.core.scale.WeighInEngine
import dagger.Binds
import dagger.Module
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppScaleControl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: ScaleStore,
    private val scanner: ScaleScanner,
    private val engine: WeighInEngine,
    private val units: ScaleUnits,
) : ScaleControl {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncing = Mutex()

    override fun syncBackgroundListening() {
        scope.launch {
            syncing.withLock {
                val settings = store.current()
                val target = ScaleScanReceiver.pendingIntent(context)
                // Restarting replaces the old address list; Android throttles rapid restarts, so this stays occasional.
                scanner.unwatch(target)
                val listening = settings.autoLog && settings.scales.isNotEmpty() &&
                    scanner.watch(settings.scales.map { it.address }.toSet(), target)
                if (settings.autoLog && settings.scales.isNotEmpty()) ScaleWatchWorker.schedule(context) else ScaleWatchWorker.cancel(context)
                if (!listening) Log.i(TAG, "Not listening in the background")
            }
        }
    }

    override fun weighInNow() = WeighInService.start(context, WeighInService.Trigger.USER)

    /**
     * Posts a notification for each reading, unless a screen is already showing it. Runs for
     * the life of the process, which is when readings can happen.
     */
    fun notifyOutcomes() {
        scope.launch {
            engine.outcomes.collect { outcome ->
                if (engine.uiWatching.value > 0) return@collect
                ScaleNotifications.outcome(context, outcome, units.current())
            }
        }
    }

    private companion object {
        const val TAG = "AppScaleControl"
    }
}

/**
 * The background scan doesn't survive Bluetooth being switched off and on, so it's re-armed
 * a few times a day (and on app start, boot and update).
 */
@HiltWorker
class ScaleWatchWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val control: ScaleControl,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        control.syncBackgroundListening()
        return Result.success()
    }

    companion object {
        private const val NAME = "scale-watch"

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<ScaleWatchWorker>(6, TimeUnit.HOURS).build(),
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ScaleModule {
    @Binds
    abstract fun scaleControl(impl: AppScaleControl): ScaleControl

    @Binds
    @IntoSet
    abstract fun widgetRefresh(impl: WeightWidget.Refresher): WeightChangeListener
}
