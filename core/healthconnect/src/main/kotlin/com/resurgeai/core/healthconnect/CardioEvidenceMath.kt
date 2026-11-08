package com.resurgeai.core.healthconnect

import com.resurgeai.core.model.CardioEvidenceInterval
import com.resurgeai.core.model.CardioMethod
import com.resurgeai.core.model.CardioPointsEngine
import com.resurgeai.core.model.DataSource
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.time.Instant

/** All pages or an exception: incomplete sensor reads must never become a complete score. */
internal suspend fun <T> readCardioPages(read: suspend (String?) -> Pair<List<T>, String?>): List<T> {
    val result = mutableListOf<T>()
    var token: String? = null
    val seen = mutableSetOf<String>()
    do {
        currentCoroutineContext().ensureActive()
        val (records, next) = read(token)
        result += records
        check(next == null || seen.add(next)) { "Health Connect repeated a page token" }
        token = next
    } while (token != null)
    return result
}

/** Pure normalization also used by tests; no interpolation across missing minutes. */
object CardioEvidenceMath {
    fun cadence(start: Instant, end: Instant, samples: List<Pair<Instant, Double>>, source: DataSource, id: String): List<CardioEvidenceInterval> {
        val sorted = samples.filter { it.first >= start && it.first < end && it.second.isFinite() && it.second >= 0 }
            .sortedWith(compareBy<Pair<Instant, Double>> { it.first }.thenBy { it.second }).distinctBy { it.first }
        return sorted.mapIndexedNotNull { index, (time, rate) ->
            val until = minOf(end, time.plusSeconds(60), sorted.getOrNull(index + 1)?.first ?: end)
            if (until <= time) null else CardioEvidenceInterval(time, until, CardioPointsEngine.cadenceIntensity(rate), CardioMethod.CADENCE, source, id)
        }
    }

    fun steps(start: Instant, end: Instant, count: Long, source: DataSource, id: String): CardioEvidenceInterval? {
        val ms = end.toEpochMilli() - start.toEpochMilli()
        if (ms !in 1..60_000 || count < 0) return null
        return CardioEvidenceInterval(start, end, CardioPointsEngine.cadenceIntensity(count * 60_000.0 / ms), CardioMethod.STEP_RATE_ESTIMATE, source, id)
    }
}
