package com.resurgeai.feature.dashboard

import android.os.SystemClock
import android.util.Log
import com.resurgeai.core.data.di.ApplicationScope
import com.resurgeai.core.data.fitness.WorkoutsRepository
import com.resurgeai.core.data.food.MealsRepository
import com.resurgeai.core.data.plan.PlanRepository
import com.resurgeai.core.data.scores.DayScoreRecord
import com.resurgeai.core.data.scores.DayScoresRepository
import com.resurgeai.core.data.session.SessionRepository
import com.resurgeai.core.data.weight.WeightRepository
import com.resurgeai.core.healthconnect.HealthConnectAvailability
import com.resurgeai.core.healthconnect.HealthConnectManager
import com.resurgeai.core.healthconnect.HealthDataSource
import com.resurgeai.core.healthconnect.HealthPermissions
import com.resurgeai.core.model.EffortInputs
import com.resurgeai.core.model.FuelInputs
import com.resurgeai.core.model.HealthMetric
import com.resurgeai.core.model.Meal
import com.resurgeai.core.model.PlanMatch
import com.resurgeai.core.model.RecoveryInputs
import com.resurgeai.core.model.Score
import com.resurgeai.core.model.ScoreKind
import com.resurgeai.core.model.ScoreMath
import com.resurgeai.core.model.SessionState
import com.resurgeai.core.model.UserProfile
import com.resurgeai.core.model.Workout
import com.resurgeai.core.model.WorkoutPlan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

/** A day's three scores. */
data class DayScores(val recovery: Score, val effort: Score, val fuel: Score) {
    operator fun get(kind: ScoreKind): Score = when (kind) {
        ScoreKind.RECOVERY -> recovery
        ScoreKind.EFFORT -> effort
        ScoreKind.FUEL -> fuel
    }
}

/** Every scored day from the start of last month to today, oldest first. */
data class ScoreHistory(
    val loaded: Boolean = false,
    val today: LocalDate = LocalDate.now(),
    val days: List<DayScores> = emptyList(),
    /** Heart rate variability can be read (it's an optional permission). */
    val hrvAllowed: Boolean = true,
) {
    val latest: DayScores? get() = days.lastOrNull { it.recovery.date == today }

    fun of(kind: ScoreKind): List<Score> = days.map { it[kind] }
}

/** What Health Connect said over the scored window plus four weeks of history before it. */
private data class HealthWindow(
    val ready: Boolean,
    /** Minutes asleep per night, by the day it ended. */
    val asleep: Map<LocalDate, Int>,
    /** Bedtime per night as minutes after noon the day before, so midnight doesn't wrap. */
    val bedtimes: Map<LocalDate, Int>,
    val restingHr: Map<LocalDate, Double>,
    val hrv: Map<LocalDate, Double>,
    val steps: Map<LocalDate, Long>,
    val device: List<Workout>,
    val hrvAllowed: Boolean,
)

/**
 * Recovery, Effort and Fuel for each day, worked out on the device from Health Connect, workouts,
 * meals and water. Health is read again on [refresh] (throttled); meals, water and workouts are live.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@javax.inject.Singleton
class ScoresEngine @javax.inject.Inject constructor(
    session: SessionRepository,
    private val healthConnect: HealthConnectManager,
    private val healthData: HealthDataSource,
    private val workouts: WorkoutsRepository,
    meals: MealsRepository,
    weights: WeightRepository,
    private val plans: PlanRepository,
    private val saved: DayScoresRepository,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val clock = MutableStateFlow(Instant.now())
    private val health = MutableStateFlow<HealthWindow?>(null)
    private val readLock = Mutex()
    private var readAt = 0L

    private val profile = session.session.filterIsInstance<SessionState.Ready>().map { it.profile }.distinctUntilChanged()

    private val plan = profile.map { it.activePlanId }.distinctUntilChanged().map { id ->
        id?.let { runCatching { plans.plan(it) }.onFailure { e -> Log.w(TAG, "Plan read failed", e) }.getOrNull() }
    }

    private val weightKg = weights.weighIns().map { it.lastOrNull()?.kg }.catch { emit(null) }

    private val logged = combine(
        meals.meals(firstDay(LocalDate.now()).minusDays(1)).catch { emit(emptyList()) },
        meals.water(firstDay(LocalDate.now())).catch { emit(emptyMap()) },
        workouts.records(firstDay(LocalDate.now()).minusDays(HISTORY).atStartOfDay(ZoneId.systemDefault()).toInstant()).catch { emit(emptyList()) },
    ) { m, w, r -> Triple(m, w, r) }

    val state: StateFlow<ScoreHistory> = combine(profile, plan, weightKg, logged, combine(health, clock) { h, c -> h to c }) { p, pl, kg, (m, water, records), (h, now) ->
        if (h == null) ScoreHistory(loaded = false)
        else build(p, pl, kg, m, water, WorkoutsRepository.merge(h.device, records), h, now)
    }
        .catch { Log.w(TAG, "Scores failed", it); emit(ScoreHistory(loaded = true)) }
        .stateIn(scope, SharingStarted.Eagerly, ScoreHistory())

    /** What was last saved per day, so only changed days are written. */
    private val written = mutableMapOf<LocalDate, Map<String, Any?>>()

    init {
        // The coach and weekly review read the last two weeks; save days whose scores changed.
        scope.launch {
            state.collect { h ->
                if (!h.loaded || h.days.isEmpty()) return@collect
                val changed = h.days.takeLast(SAVED_DAYS)
                    .map { DayScoreRecord(it.recovery.date, it.recovery, it.effort, it.fuel) }
                    .filter { written[it.date] != it.toMap() }
                if (changed.isEmpty()) return@collect
                runCatching { saved.save(changed) }
                    .onSuccess { changed.forEach { written[it.date] = it.toMap() } }
                    .onFailure { Log.w(TAG, "Saving scores failed", it) }
            }
        }
    }

    /** Reads Health Connect again unless it was read in the last few minutes; always re-checks the clock. */
    fun refresh(force: Boolean = false) {
        clock.value = Instant.now()
        scope.launch {
            readLock.withLock {
                val now = SystemClock.elapsedRealtime()
                if (!force && health.value != null && now - readAt < MIN_READ_INTERVAL_MS) return@withLock
                health.value = runCatching { readHealth() }.onFailure { Log.w(TAG, "Health read failed", it) }.getOrNull()
                    ?: health.value ?: HealthWindow(false, emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptyList(), false)
                readAt = now
            }
        }
    }

    private suspend fun readHealth(): HealthWindow = coroutineScope {
        val today = LocalDate.now(zone)
        val start = firstDay(today).minusDays(HISTORY)
        val days = (today.toEpochDay() - start.toEpochDay() + 1).toInt()
        if (healthConnect.availability() != HealthConnectAvailability.AVAILABLE) {
            return@coroutineScope HealthWindow(false, emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptyList(), false)
        }
        val granted = healthConnect.grantedPermissions()
        if (granted.none { it in HealthPermissions.READ }) {
            return@coroutineScope HealthWindow(false, emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptyList(), false)
        }
        val from = start.atStartOfDay(zone).toInstant()
        val to = Instant.now()
        val sleep = async { healthData.sleepSessions(from, to) }
        val rhr = async { runCatching { healthData.restingHeartRateByDay(from, to, zone) }.getOrDefault(emptyMap()) }
        val hrv = async { runCatching { healthData.hrvByDay(from, to, zone) }.getOrDefault(emptyMap()) }
        val steps = async { healthData.readSeries(HealthMetric.STEPS, days, zone) }
        val device = async { runCatching { workouts.deviceWorkouts(from, to) }.getOrDefault(emptyList()) }

        val nights = sleep.await().groupBy { it.end.atZone(zone).toLocalDate() }
        HealthWindow(
            ready = true,
            asleep = nights.mapValues { (_, s) -> s.sumOf { it.asleep.toMinutes() }.toInt() },
            bedtimes = nights.mapNotNull { (date, s) ->
                // The night's main sleep: naps don't move the bedtime.
                val main = s.filter { it.duration.toHours() >= 3 }.minByOrNull { it.start } ?: return@mapNotNull null
                val bed = LocalDateTime.ofInstant(main.start, zone)
                val noon = date.minusDays(1).atTime(12, 0)
                date to java.time.Duration.between(noon, bed).toMinutes().toInt()
            }.toMap(),
            restingHr = rhr.await(),
            hrv = hrv.await(),
            steps = steps.await().points.mapNotNull { p -> p.value?.let { p.date to it.toLong() } }.toMap(),
            device = device.await(),
            hrvAllowed = HealthPermissions.HRV in granted,
        )
    }

    private fun build(
        profile: UserProfile,
        plan: WorkoutPlan?,
        weightKg: Double?,
        meals: List<Meal>,
        water: Map<LocalDate, Int>,
        workouts: List<Workout>,
        h: HealthWindow,
        now: Instant,
    ): ScoreHistory {
        val today = LocalDate.ofInstant(now, zone)
        val first = firstDay(today)
        val targets = profile.targets
        val goal = profile.majorGoal
        val planStart = goal?.startDate
        val proteinTarget = ScoreMath.proteinTarget(weightKg ?: goal?.startKg, goal?.type)
        val mealsGoal = profile.dayGoals.meals.takeIf { it > 0 } ?: 3
        val minuteOfDay = LocalDateTime.ofInstant(now, zone).let { it.hour * 60 + it.minute }
        val byDay = workouts.groupBy { it.date(zone) }
        val mealsByDay = meals.groupBy { it.date }

        fun normal(values: Map<LocalDate, Double>, date: LocalDate): Double? =
            (1..HISTORY).mapNotNull { values[date.minusDays(it)] }.takeIf { it.size >= 3 }?.average()

        val loads = mutableMapOf<LocalDate, Double>()
        fun loadOn(date: LocalDate): Double = loads.getOrPut(date) {
            val rest = normal(h.restingHr, date) ?: h.restingHr[date]
            byDay[date].orEmpty().sumOf { w ->
                ScoreMath.load(w.duration.toMinutes().toDouble(), w.metrics.rpe, w.metrics.avgHr, rest)
            }
        }

        val out = mutableListOf<DayScores>()
        var previous: com.resurgeai.core.model.Score? = null
        var date = first
        while (!date.isAfter(today)) {
            val history = (1..HISTORY).count { h.asleep.containsKey(date.minusDays(it)) || h.restingHr.containsKey(date.minusDays(it)) }
            val bedAvg = (1..7L).mapNotNull { h.bedtimes[date.minusDays(it)] }.takeIf { it.size >= 3 }?.average()
            val recovery = ScoreMath.recovery(
                RecoveryInputs(
                    date = date,
                    asleepMin = h.asleep[date],
                    sleepGoalMin = targets.sleepMinutes,
                    restingHr = h.restingHr[date],
                    restingHrNormal = normal(h.restingHr, date),
                    hrv = h.hrv[date],
                    hrvNormal = normal(h.hrv, date),
                    bedtimeShiftMin = h.bedtimes[date]?.let { b -> bedAvg?.let { abs(b - it).roundToInt() } },
                    yesterdayEffort = previous?.value,
                    yesterdayBand = previous?.band,
                    historyDays = history,
                ),
            )
            val session = plan?.days?.firstOrNull { it.day == date.dayOfWeek }
                ?.takeIf { !it.rest && (planStart == null || !date.isBefore(planStart)) }
            val dayWorkouts = byDay[date].orEmpty()
            val done = session?.let { s ->
                val match = dayWorkouts.filter { PlanMatch.matches(s, it) }
                when {
                    match.isEmpty() -> 0.0
                    (s.durationMin ?: 0) > 0 && match.sumOf { it.duration.toMinutes() } < (s.durationMin ?: 0) / 2 -> 0.5
                    else -> 1.0
                }
            } ?: 0.0
            val workoutDays = (1..28).map { date.minusDays(it.toLong()) }.filter { byDay[it]?.isNotEmpty() == true }
            val effort = ScoreMath.effort(
                EffortInputs(
                    date = date,
                    load = loadOn(date),
                    loadNormal = workoutDays.takeIf { it.size >= 3 }?.map(::loadOn)?.average(),
                    workoutMin = dayWorkouts.sumOf { it.duration.toMinutes() }.toInt(),
                    steps = h.steps[date],
                    stepsGoal = targets.steps,
                    session = session,
                    sessionDone = done,
                    recovery = recovery.value,
                    soFar = date == today && minuteOfDay < EFFORT_DAY_END,
                    healthReady = h.ready,
                ),
            )
            val eaten = mealsByDay[date].orEmpty()
            val fuel = ScoreMath.fuel(
                FuelInputs(
                    date = date,
                    kcal = eaten.sumOf { it.totals.kcal },
                    kcalTarget = targets.calories,
                    proteinG = eaten.sumOf { it.totals.protein },
                    proteinTarget = proteinTarget,
                    meals = eaten.size,
                    mealsGoal = mealsGoal,
                    waterMl = water[date] ?: 0,
                    waterTarget = targets.waterMl,
                    dayShare = if (date == today) ScoreMath.dayShare(minuteOfDay) else 1.0,
                ),
            )
            out += DayScores(recovery, effort, fuel)
            previous = effort
            date = date.plusDays(1)
        }
        return ScoreHistory(loaded = true, today = today, days = out, hrvAllowed = h.hrvAllowed)
    }

    private companion object {
        const val TAG = "Scores"
        /** Days of history behind each day's normals. */
        const val HISTORY = 28L
        const val MIN_READ_INTERVAL_MS = 5 * 60_000L
        /** Days of scores kept in Firestore for the coach. */
        const val SAVED_DAYS = 14
        /** Effort counts as "so far" until 21:00. */
        const val EFFORT_DAY_END = 21 * 60

        /** The first scored day: the start of last month, so Month can compare with the one before. */
        fun firstDay(today: LocalDate): LocalDate = today.withDayOfMonth(1).minusMonths(1)
    }
}
