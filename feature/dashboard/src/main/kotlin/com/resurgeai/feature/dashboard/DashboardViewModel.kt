package com.resurgeai.feature.dashboard

import kotlinx.coroutines.flow.Flow
import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resurgeai.core.data.health.HealthSyncRepository
import com.resurgeai.core.data.health.HealthSyncWorker
import com.resurgeai.core.data.health.ActivityEventSync
import com.resurgeai.core.data.health.ActivityEventWorker
import com.resurgeai.core.data.coach.CoachRepository
import com.resurgeai.core.model.CoachInsight
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import com.resurgeai.core.data.habits.HabitsRepository
import com.resurgeai.core.data.prefs.PreferencesRepository
import com.resurgeai.core.model.HabitStatus
import com.resurgeai.core.model.HabitStreaks
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import com.resurgeai.core.model.WeightMath
import com.resurgeai.core.data.weight.WeightRepository
import com.resurgeai.core.data.plan.PlanRepository
import com.resurgeai.core.model.WorkoutPlan
import com.resurgeai.core.data.fitness.WorkoutsRepository
import com.resurgeai.core.data.food.MealsRepository
import com.resurgeai.core.model.Meal
import com.resurgeai.core.model.Nutrition
import com.resurgeai.core.model.Workout
import java.time.Instant
import com.resurgeai.core.data.session.SessionRepository
import com.resurgeai.core.data.user.UserRepository
import com.resurgeai.core.model.DayGoals
import com.resurgeai.core.healthconnect.HealthConnectAvailability
import com.resurgeai.core.healthconnect.HealthConnectManager
import com.resurgeai.core.healthconnect.HealthDataSource
import com.resurgeai.core.healthconnect.HealthMath
import com.resurgeai.core.healthconnect.HealthPermissions
import com.resurgeai.core.model.DailyHealth
import com.resurgeai.core.model.HealthMetric
import com.resurgeai.core.model.SessionState
import com.resurgeai.core.model.Targets
import com.resurgeai.core.model.UserProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

enum class PermissionState { UNKNOWN, NONE, PARTIAL, ALL }

data class DashboardUiState(
    val profile: UserProfile? = null,
    val availability: HealthConnectAvailability? = null,
    val permissions: PermissionState = PermissionState.UNKNOWN,
    val loading: Boolean = false,
    val today: DailyHealth? = null,
    val sparklines: Map<HealthMetric, List<Double?>> = emptyMap(),
    /** Sun..Sat, true when that day counts as an exercise day. */
    val exerciseWeek: List<Boolean> = List(7) { false },
    val todayIndex: Int = HealthMath.dayOfWeekIndex(LocalDate.now()),
    val error: String? = null,
    /** Most recent coach insight (activity, sleep or weekly), or null before the first one. */
    val latestInsight: CoachInsight? = null,
    /** Recent insights, newest first, for the timeline. */
    val insights: List<CoachInsight> = emptyList(),
    /** Meals logged today and yesterday. */
    val meals: List<Meal> = emptyList(),
    /** Workouts (device + logged) from yesterday onwards, newest first. */
    val workouts: List<Workout> = emptyList(),
    val waterMl: Int = 0,
    /** Habit day being shown (rolls over at the user's reset time, not midnight). */
    val habitDay: LocalDate = LocalDate.now(),
    /** Active habits with streak stats; null until the first read. */
    val habits: List<TodayHabit>? = null,
    /** The day's one celebration: open once per habit day when everything today is first done. */
    val showDayComplete: Boolean = false,
    /** The day streak, shared with the header flame. */
    val streak: DayStreakState = DayStreakState(),
    /** Insights dismissed on this device (also marked seen on the server). */
    val dismissedInsights: Set<String> = emptySet(),
    /** Weight trend for the Today tile; null before the first weigh-in. */
    val weight: WeightGlance? = null,
    /** Health Connect weigh-ins that could be imported; null when there's nothing to offer. */
    val weightImport: Int? = null,
    val importingWeight: Boolean = false,
    /** The plan the person is following, for today's session; null when there's none. */
    val plan: WorkoutPlan? = null,
    /** Today's list collapsed to a row of boxes (remembered on this device). */
    val compactList: Boolean = false,
    /** Recovery, Effort and Fuel by day. */
    val scores: ScoreHistory = ScoreHistory(),
) {
    /** The latest insight, pinned above the tide until it's seen. */
    val pinnedInsight: CoachInsight? get() = latestInsight?.takeIf { !it.seen && it.id !in dismissedInsights }

    val healthReady: Boolean get() = availability == HealthConnectAvailability.AVAILABLE &&
        (permissions == PermissionState.PARTIAL || permissions == PermissionState.ALL)


    /** Eaten on the day being shown (the Health Connect day, else the calendar day). */
    val eaten: Nutrition get() {
        val day = today?.date ?: LocalDate.now()
        return meals.filter { it.date == day }.fold(Nutrition.Zero) { acc, m -> acc + m.totals }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
/** The Today weight tile: trend now, its change over the last 7 days, and 14 days of trend. */
data class WeightGlance(val kg: Double, val weekChangeKg: Double?, val spark: List<Double>)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    sessionRepository: SessionRepository,
    private val healthConnect: HealthConnectManager,
    private val healthData: HealthDataSource,
    private val healthSync: HealthSyncRepository,
    private val activityEvents: ActivityEventSync,
    private val coachRepository: CoachRepository,
    private val meals: MealsRepository,
    private val habitsRepository: HabitsRepository,
    private val preferences: PreferencesRepository,
    private val workouts: WorkoutsRepository,
    private val weights: WeightRepository,
    private val streakEngine: DayStreakEngine,
    private val users: UserRepository,
    private val plans: PlanRepository,
    private val scoresEngine: ScoresEngine,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {
    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    val permissionContract = healthConnect.permissionContract()
    val permissionsToRequest: Set<String> get() = healthConnect.permissionsToRequest()

    fun installIntent() = healthConnect.installIntent()

    private val deviceWorkouts = MutableStateFlow<List<Workout>>(emptyList())

    private fun feedStart(): Instant = LocalDate.now().minusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()

    /** Ticks on resume so the habit day rolls over at the reset time. */
    private val clock = MutableStateFlow(Instant.now())

    /** Marks a habit done for the habit day, or clears it again (undo). */
    fun toggleHabit(habitId: String) {
        val s = _state.value
        val item = s.habits?.firstOrNull { it.habit.id == habitId } ?: return
        habitsRepository.setStatus(s.habitDay, habitId, if (item.done) null else HabitStatus.DONE)
    }

    /** Marks the pinned insight seen: it leaves the top of Today and joins the feed. */
    fun setCompactList(on: Boolean) {
        _state.update { it.copy(compactList = on) }
        viewModelScope.launch { preferences.setTodayListCompact(on) }
    }

    fun dismissInsight(id: String) {
        _state.update { it.copy(dismissedInsights = it.dismissedInsights + id) }
        coachRepository.markSeen(id)
        viewModelScope.launch { preferences.dismissInsight(id) }
    }

    /** Imports Health Connect's weight history; [alsoWrite] keeps saving new weigh-ins there too. */
    fun importWeights(alsoWrite: Boolean) {
        val uid = _state.value.profile?.uid ?: return
        _state.update { it.copy(importingWeight = true) }
        viewModelScope.launch {
            runCatching { weights.importFromHealthConnect() }.onFailure { Log.w(TAG, "Weight import failed", it) }
            preferences.setWeightWriteToHealthConnect(alsoWrite)
            preferences.setWeightImportSettled(uid)
            _state.update { it.copy(importingWeight = false, weightImport = null) }
        }
    }

    fun dismissWeightImport() {
        val uid = _state.value.profile?.uid ?: return
        _state.update { it.copy(weightImport = null) }
        viewModelScope.launch { preferences.setWeightImportSettled(uid) }
    }

    /** Permission needed to save weigh-ins to Health Connect. */
    val weightWritePermission: String = HealthPermissions.WRITE_WEIGHT

    fun dismissDayComplete() {
        val day = _state.value.streak.day
        _state.update { it.copy(showDayComplete = false) }
        viewModelScope.launch { preferences.setHighTideShown(day) }
    }

    /** Saves what a day needs for the streak. Settled days keep their result. */
    fun saveDayGoals(goals: DayGoals) {
        val uid = _state.value.profile?.uid ?: return
        viewModelScope.launch { runCatching { users.updateDayGoals(uid, goals) }.onFailure { Log.w(TAG, "Saving day goals failed", it) } }
    }

    private var loadJob: Job? = null
    private var lastLoadedAt = 0L

    init {
        viewModelScope.launch {
            sessionRepository.session.collect { session ->
                _state.update { it.copy(profile = (session as? SessionState.Ready)?.profile) }
            }
        }
        viewModelScope.launch {
            sessionRepository.session
                .map { (it as? SessionState.Ready)?.profile?.activePlanId }
                .distinctUntilChanged()
                .collect { id ->
                    val plan = id?.let { runCatching { plans.plan(it) }.onFailure { e -> Log.w(TAG, "Plan read failed", e) }.getOrNull() }
                    _state.update { it.copy(plan = plan) }
                }
        }
        viewModelScope.launch {
            scoresEngine.state.collect { s -> _state.update { it.copy(scores = s) } }
        }
        viewModelScope.launch {
            preferences.todayListCompact.collect { on -> _state.update { it.copy(compactList = on) } }
        }
        viewModelScope.launch {
            preferences.dismissedInsights.collect { ids -> _state.update { it.copy(dismissedInsights = it.dismissedInsights + ids) } }
        }
        viewModelScope.launch {
            coachRepository.insights(limit = 12)
                .catch { Log.w(TAG, "Insights listener failed", it) }
                .collect { list -> _state.update { it.copy(latestInsight = list.firstOrNull(), insights = list) } }
        }
        val yesterday = LocalDate.now().minusDays(1)
        viewModelScope.launch {
            meals.meals(yesterday)
                .catch { Log.w(TAG, "Meals listener failed", it) }
                .collect { list -> _state.update { it.copy(meals = list) } }
        }
        viewModelScope.launch {
            meals.water(LocalDate.now())
                .catch { Log.w(TAG, "Water listener failed", it) }
                .collect { byDay -> _state.update { it.copy(waterMl = byDay[LocalDate.now()] ?: 0) } }
        }
        viewModelScope.launch {
            todayHabits(sessionRepository, habitsRepository, clock)
                .catch { Log.w(TAG, "Habits listener failed", it); emit(LocalDate.now() to emptyList()) }
                .collect { (day, list) -> _state.update { it.copy(habitDay = day, habits = list) } }
        }
        viewModelScope.launch {
            streakEngine.state.collect { s -> _state.update { it.copy(streak = s) } }
        }
        viewModelScope.launch {
            streakEngine.state.map { s -> s.day.takeIf { s.loaded && s.todayMet } }
                .distinctUntilChanged()
                .collect { day ->
                    if (day != null && preferences.highTideShownOn() != day) _state.update { it.copy(showDayComplete = true) }
                }
        }
        viewModelScope.launch {
            weights.weighIns()
                .catch { Log.w(TAG, "Weights listener failed", it); emit(emptyList()) }
                .collect { list ->
                    val today = LocalDate.now()
                    val trend = WeightMath.trend(list, today)
                    val glance = trend.lastOrNull()?.let { last ->
                        WeightGlance(
                            kg = last.kg,
                            weekChangeKg = if (trend.size > 7) WeightMath.change(trend, today.minusDays(7), today) else null,
                            spark = trend.takeLast(14).map { it.kg },
                        )
                    }
                    _state.update { it.copy(weight = glance) }
                }
        }
        viewModelScope.launch {
            combine(deviceWorkouts, workouts.records(feedStart()).catch { emit(emptyList()) }) { device, records ->
                WorkoutsRepository.merge(device, records)
            }.collect { list -> _state.update { it.copy(workouts = list) } }
        }
    }

    /** Called on resume. Skips if data was loaded very recently unless [force]. */
    fun refresh(force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        clock.value = Instant.now()
        streakEngine.tick()
        scoresEngine.refresh(force)
        if (!force && loadJob?.isActive == true) return
        if (!force && now - lastLoadedAt < MIN_REFRESH_INTERVAL_MS && _state.value.today != null) return
        loadJob = viewModelScope.launch { load() }
    }

    fun onPermissionsResult(@Suppress("UNUSED_PARAMETER") granted: Set<String>) = refresh(force = true)

    private suspend fun load() {
        val availability = healthConnect.availability()
        _state.update { it.copy(availability = availability, error = null) }
        val noHealth = HealthToday(LocalDate.now(), ready = false, sleepMinutes = null, steps = null)
        if (availability != HealthConnectAvailability.AVAILABLE) return streakEngine.updateHealth(noHealth)

        val granted = healthConnect.grantedPermissions()
        val permissionState = when {
            granted.containsAll(HealthPermissions.READ) -> PermissionState.ALL
            granted.any { it in HealthPermissions.READ } -> PermissionState.PARTIAL
            else -> PermissionState.NONE
        }
        _state.update { it.copy(permissions = permissionState) }
        if (permissionState == PermissionState.NONE) return streakEngine.updateHealth(noHealth)

        _state.update { it.copy(loading = true) }
        try {
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val weekStart = HealthMath.weekStart(today)
            coroutineScope {
                val day = async { healthData.readDay(today, zone) }
                val week = async { HealthMath.exerciseDays(healthData.exerciseThisWeek(zone), zone) }
                val series = SPARKLINE_METRICS.map { m -> async { m to healthData.readSeries(m, 7, zone) } }
                val device = async { runCatching { workouts.deviceWorkouts(feedStart(), Instant.now()) }.getOrDefault(emptyList()) }
                val activeDays = week.await()
                deviceWorkouts.value = device.await()
                val read = day.await()
                streakEngine.updateHealth(HealthToday(today, ready = true, sleepMinutes = read.asleepDuration?.toMinutes()?.toInt(), steps = read.steps))
                _state.update {
                    it.copy(
                        loading = false,
                        today = day.await(),
                        exerciseWeek = (0 until 7).map { i -> weekStart.plusDays(i.toLong()) in activeDays },
                        todayIndex = HealthMath.dayOfWeekIndex(today),
                        sparklines = series.awaitAll().associate { (m, s) -> m to s.points.map { p -> p.value } },
                    )
                }
            }
            lastLoadedAt = SystemClock.elapsedRealtime()
            offerWeightImport()

            // Upload daily aggregates for the coach, and (re)schedule background sync.
            viewModelScope.launch {
                healthSync.syncRecent(zone)
                HealthSyncWorker.schedule(appContext, healthConnect)
                // Post any finished workouts/sleep for insights; keep checking in the background.
                runCatching { activityEvents.syncFinished(zone = zone) }.onFailure { Log.w(TAG, "Event sync failed", it) }
                ActivityEventWorker.schedule(appContext, healthConnect)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read Health Connect", e)
            streakEngine.updateHealth(HealthToday(LocalDate.now(), ready = true, sleepMinutes = null, steps = null))
            _state.update { it.copy(loading = false, error = "Couldn't read Health Connect.") }
        }
    }

    /** Offers the one-time weight import while Health Connect has weigh-ins and it hasn't been settled. */
    private suspend fun offerWeightImport() {
        val uid = _state.value.profile?.uid ?: return
        if (preferences.weightImportSettled(uid).first()) return
        val count = weights.importableCount()
        _state.update { it.copy(weightImport = count.takeIf { n -> n > 0 }) }
    }

    private companion object {
        const val TAG = "Dashboard"
        const val MIN_REFRESH_INTERVAL_MS = 30_000L
        val SPARKLINE_METRICS = listOf(
            HealthMetric.STEPS, HealthMetric.CALORIES_BURNED,
            HealthMetric.SLEEP, HealthMetric.HEART_RATE,
        )
    }
}

/** Habit history for the streak and the weekly goal. */
private const val HABIT_HISTORY_DAYS = 400L

/**
 * The current habit day (it rolls over at the user's reset time, re-checked whenever [clock] ticks)
 * with each active habit and its streak stats. Shared by Today and the header's weekly-goal flame.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun todayHabits(
    sessionRepository: SessionRepository,
    habitsRepository: HabitsRepository,
    clock: Flow<Instant>,
): Flow<Pair<LocalDate, List<TodayHabit>>> {
    val settings = sessionRepository.session
        .filterIsInstance<SessionState.Ready>()
        .map { it.profile.habitSettings }
        .distinctUntilChanged()
    return combine(settings, clock) { s, now -> HabitStreaks.habitDay(now, ZoneId.systemDefault(), s.resetTime) }
        .distinctUntilChanged()
        .flatMapLatest { day ->
            combine(habitsRepository.habits(), habitsRepository.days(day.minusDays(HABIT_HISTORY_DAYS))) { habits, days ->
                day to habits.filterNot { it.archived }.sortedBy { it.order }.map { habit ->
                    val log = days.mapNotNull { (date, entries) -> entries[habit.id]?.let { date to it } }.toMap()
                    TodayHabit(habit, HabitStreaks.compute(habit.createdDate, day, log))
                }
            }
        }
}
