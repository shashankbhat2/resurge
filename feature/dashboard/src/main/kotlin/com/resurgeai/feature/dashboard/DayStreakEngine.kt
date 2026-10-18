package com.resurgeai.feature.dashboard

import android.util.Log
import com.resurgeai.core.data.di.ApplicationScope
import com.resurgeai.core.data.food.MealsRepository
import com.resurgeai.core.data.habits.HabitsRepository
import com.resurgeai.core.data.session.SessionRepository
import com.resurgeai.core.data.streak.DayStreakRepository
import com.resurgeai.core.model.DayGoals
import com.resurgeai.core.model.DayInputs
import com.resurgeai.core.model.DayItem
import com.resurgeai.core.model.DayResult
import com.resurgeai.core.model.DayState
import com.resurgeai.core.model.DayStatus
import com.resurgeai.core.model.DayStreakMath
import com.resurgeai.core.model.SessionState
import com.resurgeai.core.model.StreakSummary
import com.resurgeai.core.model.Targets
import com.resurgeai.core.model.WeekSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Today's health numbers, pushed by Today after each Health Connect read. [ready]: readable at all. */
data class HealthToday(val date: LocalDate, val ready: Boolean, val sleepMinutes: Int?, val steps: Long?)

data class DayStreakState(
    /** Habits, the profile and today's health have all loaded, so a met day is real. */
    val loaded: Boolean = false,
    val day: LocalDate = LocalDate.now(),
    val goals: DayGoals = DayGoals(),
    val targets: Targets = Targets(),
    /** Today's goals, what's left first. */
    val today: List<DayItem> = emptyList(),
    val streak: StreakSummary = StreakSummary(0, 0, emptyMap(), freezeLeftThisWeek = true),
    val week: WeekSummary = WeekSummary(LocalDate.now(), 0, DayGoals().weeklyDays, 0),
    val habits: List<TodayHabit> = emptyList(),
    /** Health Connect is readable, so sleep and steps can be chosen. */
    val health: Boolean = false,
) {
    val done: Int get() = today.count { it.done == true }
    val total: Int get() = today.size
    val todayMet: Boolean get() = total > 0 && done == total
}

/**
 * The day streak for the whole app (Today's card and the header flame read the same numbers). It
 * combines today's habits, meals, water and health with the saved days, and settles each day once
 * it's past yesterday's grace period, judged by the goals chosen at the time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class DayStreakEngine @Inject constructor(
    session: SessionRepository,
    habitsRepository: HabitsRepository,
    meals: MealsRepository,
    private val repository: DayStreakRepository,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val clock = MutableStateFlow(Instant.now())
    private val health = MutableStateFlow<HealthToday?>(null)
    /** Yesterday's sleep and steps from its daily summary (today's come live from [health]). */
    private val yesterdayHealth = MutableStateFlow<Pair<LocalDate, Pair<Int?, Long?>>?>(null)
    private val settleLock = Mutex()
    private var settledFor: LocalDate? = null

    private val historyStart = LocalDate.now().minusDays(HISTORY_DAYS)
    private val profile = session.session.filterIsInstance<SessionState.Ready>().map { it.profile }.distinctUntilChanged()

    private val dayData = combine(
        todayHabits(session, habitsRepository, clock),
        meals.meals(LocalDate.now().minusDays(2)).catch { emit(emptyList()) }.map { list -> list.groupingBy { it.date }.eachCount() },
        meals.water(LocalDate.now().minusDays(2)).catch { emit(emptyMap()) },
    ) { (day, habits), mealCounts, water -> DayData(day, habits, mealCounts, water) }

    val state: StateFlow<DayStreakState> = combine(
        dayData,
        profile,
        repository.results(historyStart).catch { emit(emptyList()) },
        health,
        yesterdayHealth,
    ) { data, profile, saved, health, yesterday ->
        val goals = profile.dayGoals
        val targets = profile.targets
        val ready = health?.ready == true
        val today = data.day
        val yesterdayDate = today.minusDays(1)

        val todayItems = DayStreakMath.items(
            inputs(data, today, health?.takeIf { it.date == today }?.let { it.sleepMinutes to it.steps }, ready),
            goals, targets, isToday = true,
        )
        val yesterdayItems = DayStreakMath.items(
            inputs(data, yesterdayDate, yesterday?.takeIf { it.first == yesterdayDate }?.second, ready),
            goals, targets, isToday = false,
        )
        val yesterdayOpen = DayStreakMath.yesterdayOpen(yesterdayItems)
        val yesterdayResult = if (yesterdayOpen) null else DayStreakMath.settle(yesterdayDate, yesterdayItems)
        // Saved days stop at the day before yesterday; yesterday is always judged live.
        val results = saved.filter { it.date.isBefore(yesterdayDate) } + listOfNotNull(yesterdayResult)
        val todayMet = todayItems.isNotEmpty() && todayItems.all { it.done == true }
        val streak = DayStreakMath.streak(results, today, todayMet, yesterdayDate, yesterdayOpen)
        DayStreakState(
            loaded = health != null,
            day = today,
            goals = goals,
            targets = targets,
            today = todayItems.filter { it.done != true } + todayItems.filter { it.done == true },
            streak = streak,
            week = DayStreakMath.week(streak, today, goals.weeklyDays),
            habits = data.habits,
            health = ready,
        )
    }.stateIn(scope, SharingStarted.Eagerly, DayStreakState())

    init {
        // Settle past days once a day, as soon as everything they need has loaded.
        scope.launch {
            state.map { if (it.loaded) it.day else null }.distinctUntilChanged().collect { day ->
                if (day != null) runCatching { settle(day) }.onFailure { Log.w(TAG, "Settling days failed", it) }
            }
        }
    }

    /** Re-reads the habit day, so it rolls over at the reset time. */
    fun tick() {
        clock.value = Instant.now()
    }

    fun updateHealth(today: HealthToday) {
        health.value = today
    }

    private data class DayData(
        val day: LocalDate,
        val habits: List<TodayHabit>,
        val meals: Map<LocalDate, Int>,
        val water: Map<LocalDate, Int>,
    )

    private fun inputs(data: DayData, date: LocalDate, health: Pair<Int?, Long?>?, ready: Boolean) = DayInputs(
        date = date,
        habits = data.habits.filter { !it.habit.createdDate.isAfter(date) }.map { it.habit to (it.stats.days[date] ?: DayState.NONE) },
        sleepMinutes = health?.first,
        steps = health?.second,
        meals = data.meals[date] ?: 0,
        waterMl = data.water[date] ?: 0,
        health = ready,
    )

    /**
     * Saves every unsaved day from the first habit (at most [HISTORY_DAYS] back) to the day before
     * yesterday, and loads yesterday's health summary.
     */
    private suspend fun settle(today: LocalDate) = settleLock.withLock {
        val s = state.value
        val yesterday = today.minusDays(1)
        val summaries = repository.summaries(minOf(yesterday, historyStart))
        yesterdayHealth.value = yesterday to (summaries[yesterday] ?: (null to null))
        if (settledFor == today) return@withLock
        val first = s.habits.minOfOrNull { it.habit.createdDate } ?: return@withLock run { settledFor = today }
        val from = maxOf(first, historyStart)
        val to = today.minusDays(2)
        if (from.isAfter(to)) return@withLock run { settledFor = today }
        val saved = state.value.streak.days.filterValues { it != DayStatus.NONE }.keys
        val missing = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.filter { it !in saved }.toList()
        if (missing.isNotEmpty()) {
            val mealCounts = repository.mealCounts(missing.first())
            val water = repository.water(missing.first())
            val data = DayData(today, s.habits, mealCounts, water)
            val results = missing.mapNotNull { date ->
                val items = DayStreakMath.items(inputs(data, date, summaries[date], s.health), s.goals, s.targets, isToday = false)
                DayStreakMath.settle(date, items)
            }
            repository.save(results)
        }
        settledFor = today
    }

    private companion object {
        const val TAG = "DayStreak"
        const val HISTORY_DAYS = 120L
    }
}
