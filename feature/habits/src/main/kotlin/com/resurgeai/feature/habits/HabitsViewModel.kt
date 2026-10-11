package com.resurgeai.feature.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resurgeai.core.data.habits.HabitsRepository
import com.resurgeai.core.data.session.SessionRepository
import com.resurgeai.core.model.DayState
import com.resurgeai.core.model.Habit
import com.resurgeai.core.model.HabitDraft
import com.resurgeai.core.model.HabitSettings
import com.resurgeai.core.model.HabitStats
import com.resurgeai.core.model.HabitStatus
import com.resurgeai.core.model.HabitStreaks
import com.resurgeai.core.model.SessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class HabitItem(val habit: Habit, val stats: HabitStats, val todayStatus: HabitStatus?)

data class HabitsUiState(
    val loading: Boolean = true,
    val today: LocalDate = LocalDate.now(),
    val settings: HabitSettings = HabitSettings(),
    /** Active habits in order, with streak stats. */
    val items: List<HabitItem> = emptyList(),
    val archived: List<Habit> = emptyList(),
    /** Habit id of the last swipe, for Undo. */
    val lastSwiped: String? = null,
    /** User chose to look at streaks while habits are still pending. */
    val showStreaksEarly: Boolean = false,
) {
    val pending: List<HabitItem> get() = items.filter { it.todayStatus == null }
    val doneToday: Int get() = items.count { it.todayStatus == HabitStatus.DONE }
    val showDeck: Boolean get() = !loading && pending.isNotEmpty() && !showStreaksEarly
    val canAdd: Boolean get() = items.size < HabitSettings.MAX_ACTIVE
    val yesterday: LocalDate get() = today.minusDays(1)

    fun item(id: String): HabitItem? = items.firstOrNull { it.habit.id == id }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HabitsViewModel @Inject constructor(
    sessionRepository: SessionRepository,
    private val repository: HabitsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(HabitsUiState())
    val state: StateFlow<HabitsUiState> = _state.asStateFlow()

    /** Streaks last written to Firestore per habit, so the cache is only updated on change. */
    private val cachedStreaks = mutableMapOf<String, Pair<Int, Int>>()

    init {
        val settings = sessionRepository.session
            .filterIsInstance<SessionState.Ready>()
            .map { it.profile.habitSettings }
            .distinctUntilChanged()
        // Re-evaluates "today" every 30s so the deck resets at the reset time while open.
        val ticker = flow { while (true) { emit(Instant.now()); delay(30_000) } }
        val today = combine(settings, ticker) { s, now -> s to HabitStreaks.habitDay(now, ZoneId.systemDefault(), s.resetTime) }
            .distinctUntilChanged()

        viewModelScope.launch {
            today.flatMapLatest { (s, day) ->
                combine(repository.habits(), repository.days(day.minusDays(HISTORY_DAYS))) { habits, days ->
                    build(s, day, habits, days)
                }
            }
                .catch { _state.update { it.copy(loading = false) } }
                .collect { built ->
                    _state.update { built.copy(lastSwiped = it.lastSwiped.takeIf { id -> built.item(id ?: "")?.todayStatus != null }, showStreaksEarly = it.showStreaksEarly && built.today == it.today) }
                    cacheStreaks(built.items)
                }
        }
    }

    private fun build(
        settings: HabitSettings,
        today: LocalDate,
        habits: List<Habit>,
        days: Map<LocalDate, Map<String, HabitStatus>>,
    ): HabitsUiState {
        val active = habits.filterNot { it.archived }.sortedBy { it.order }
        val items = active.map { habit ->
            val log = days.mapNotNull { (date, entries) -> entries[habit.id]?.let { date to it } }.toMap()
            HabitItem(habit, HabitStreaks.compute(habit.createdDate, today, log), log[today])
        }
        return HabitsUiState(
            loading = false,
            today = today,
            settings = settings,
            items = items,
            archived = habits.filter { it.archived },
        )
    }

    private fun cacheStreaks(items: List<HabitItem>) {
        items.forEach { item ->
            val value = item.stats.current to item.stats.best
            if (cachedStreaks[item.habit.id] != value) {
                cachedStreaks[item.habit.id] = value
                repository.cacheStreak(item.habit.id, value.first, value.second)
            }
        }
    }

    /** Swipe right (done) or left (not done) on today's card. */
    fun swipe(habitId: String, done: Boolean) {
        repository.setStatus(_state.value.today, habitId, if (done) HabitStatus.DONE else HabitStatus.MISSED)
        _state.update { it.copy(lastSwiped = habitId) }
    }

    fun undo() {
        val id = _state.value.lastSwiped ?: return
        repository.setStatus(_state.value.today, id, null)
        _state.update { it.copy(lastSwiped = null, showStreaksEarly = false) }
    }

    /** Tapping today's or yesterday's flame on the streaks list flips done / not done (grace period). */
    fun toggleDay(habitId: String, date: LocalDate) {
        val s = _state.value
        if (date != s.today && date != s.yesterday) return
        val item = s.item(habitId) ?: return
        val done = item.stats.days[date] == DayState.DONE
        repository.setStatus(date, habitId, if (done) HabitStatus.MISSED else HabitStatus.DONE)
    }

    fun showStreaks(show: Boolean) = _state.update { it.copy(showStreaksEarly = show) }

    fun add(draft: HabitDraft) {
        val s = _state.value
        if (!s.canAdd || draft.name.isBlank()) return
        repository.add(draft, (s.items.maxOfOrNull { it.habit.order } ?: -1) + 1, s.today)
    }

    fun update(habitId: String, draft: HabitDraft) {
        if (draft.name.isBlank()) return
        repository.update(habitId, draft)
    }

    /** Archives a habit; at least one active habit must remain. */
    fun archive(habitId: String): Boolean {
        if (_state.value.items.size <= 1) return false
        repository.setArchived(habitId, true)
        return true
    }

    private companion object {
        /** History used for streaks; best streak is capped by this window. */
        const val HISTORY_DAYS = 400L
    }
}
