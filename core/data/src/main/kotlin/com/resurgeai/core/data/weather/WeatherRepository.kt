package com.resurgeai.core.data.weather

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.resurgeai.core.model.DayWeather
import com.resurgeai.core.model.Weather
import com.resurgeai.core.model.WeatherKind
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Weather for Today from Open-Meteo (no key, no account): now, plus the last [PAST_DAYS] days so
 * earlier days in the strip have theirs too. Uses the approximate location only, rounded to about
 * 10 km before it leaves the phone, and remembers the last place so a cold start needn't wait for a fix.
 */
@Singleton
class WeatherRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val prefs by lazy { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    private val lock = Mutex()
    private var cached: Weather? = null
    private var fetchedAt = 0L

    fun hasPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** The weather, or null without the location permission or a connection. Fetched at most every half hour. */
    suspend fun weather(force: Boolean = false): Weather? = lock.withLock {
        val now = SystemClock.elapsedRealtime()
        if (!force && cached != null && now - fetchedAt < MAX_AGE_MS) return cached
        val place = location() ?: return cached
        runCatching { withContext(Dispatchers.IO) { fetch(place.first, place.second) } }
            .onSuccess { cached = it; fetchedAt = now }
            .onFailure { Log.w(TAG, "Weather fetch failed", it) }
        cached
    }

    /** Latitude and longitude to one decimal; the last known place when there's no fresh fix. */
    private suspend fun location(): Pair<Double, Double>? {
        val fix = if (hasPermission()) runCatching { currentLocation() }.getOrNull() else null
        if (fix != null) {
            val place = round(fix.latitude) to round(fix.longitude)
            prefs.edit().putFloat(LAT, place.first.toFloat()).putFloat(LON, place.second.toFloat()).apply()
            return place
        }
        if (!hasPermission() || !prefs.contains(LAT)) return null
        return prefs.getFloat(LAT, 0f).toDouble() to prefs.getFloat(LON, 0f).toDouble()
    }

    @SuppressLint("MissingPermission")
    private suspend fun currentLocation(): Location? {
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER)
            .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        // A last known fix from the last few hours is plenty for the weather.
        providers.mapNotNull { manager.getLastKnownLocation(it) }
            .maxByOrNull { it.time }
            ?.takeIf { System.currentTimeMillis() - it.time < FIX_MAX_AGE_MS }
            ?.let { return it }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val provider = providers.firstOrNull { it != LocationManager.PASSIVE_PROVIDER } ?: return null
        return withTimeoutOrNull(FIX_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val signal = android.os.CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                manager.getCurrentLocation(provider, signal, context.mainExecutor) { cont.resume(it) }
            }
        }
    }

    private fun fetch(lat: Double, lon: Double): Weather {
        val url = URL(
            String.format(
                Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.1f&longitude=%.1f" +
                    "&current=temperature_2m,weather_code&daily=weather_code,temperature_2m_max,temperature_2m_min" +
                    "&past_days=%d&forecast_days=1&timezone=auto",
                lat, lon, PAST_DAYS,
            ),
        )
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) error("HTTP ${connection.responseCode}")
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val current = json.optJSONObject("current")
            val daily = json.getJSONObject("daily")
            val dates = daily.getJSONArray("time")
            val codes = daily.getJSONArray("weather_code")
            val highs = daily.getJSONArray("temperature_2m_max")
            val lows = daily.getJSONArray("temperature_2m_min")
            val days = (0 until dates.length()).mapNotNull { i ->
                if (codes.isNull(i) || highs.isNull(i) || lows.isNull(i)) return@mapNotNull null
                val date = LocalDate.parse(dates.getString(i))
                date to DayWeather(date, WeatherKind.fromCode(codes.getInt(i)), highs.getDouble(i), lows.getDouble(i))
            }.toMap()
            return Weather(
                nowC = current?.takeIf { !it.isNull("temperature_2m") }?.getDouble("temperature_2m"),
                nowKind = current?.takeIf { !it.isNull("weather_code") }?.getInt("weather_code")?.let(WeatherKind::fromCode),
                days = days,
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun round(degrees: Double) = Math.round(degrees * 10) / 10.0

    private companion object {
        const val TAG = "Weather"
        const val PREFS = "weather"
        const val LAT = "lat"
        const val LON = "lon"
        const val PAST_DAYS = 14
        const val MAX_AGE_MS = 30 * 60_000L
        const val FIX_MAX_AGE_MS = 6 * 60 * 60_000L
        const val FIX_TIMEOUT_MS = 5_000L
        const val TIMEOUT_MS = 10_000
    }
}
