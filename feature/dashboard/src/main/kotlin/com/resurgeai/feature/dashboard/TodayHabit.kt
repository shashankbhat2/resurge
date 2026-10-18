package com.resurgeai.feature.dashboard

import com.resurgeai.core.model.DayState
import com.resurgeai.core.model.Habit
import com.resurgeai.core.model.HabitStats

/** An active habit as Today shows it. */
data class TodayHabit(val habit: Habit, val stats: HabitStats) {
    val done: Boolean get() = stats.today == DayState.DONE
}
