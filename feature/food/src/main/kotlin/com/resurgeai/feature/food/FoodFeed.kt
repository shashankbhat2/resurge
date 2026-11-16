package com.resurgeai.feature.food

import com.resurgeai.core.model.Meal
import com.resurgeai.core.model.MealType
import java.time.LocalDate

/** Filter, range and sort for the Food tab. Pure so tests don't need Android. */
object FoodFeed {
    /** Inclusive window loaded from Firestore (today and the 29 days before). */
    const val HISTORY_DAYS = 29L

    enum class Range(val label: String) {
        TODAY("Today"),
        YESTERDAY("Yesterday"),
        LAST_7("7 days"),
        LAST_30("30 days"),
        CUSTOM("Custom"),
    }

    enum class Sort(val label: String) {
        NEWEST("Newest"),
        OLDEST("Oldest"),
        HIGH_KCAL("High kcal"),
        LOW_KCAL("Low kcal"),
    }

    data class Filters(
        val range: Range = Range.LAST_7,
        val customFrom: LocalDate? = null,
        val customTo: LocalDate? = null,
        /** Empty means every type. */
        val types: Set<MealType> = emptySet(),
        val query: String = "",
        val sort: Sort = Sort.NEWEST,
    )

    fun bounds(today: LocalDate, filters: Filters): Pair<LocalDate, LocalDate> = when (filters.range) {
        Range.TODAY -> today to today
        Range.YESTERDAY -> {
            val y = today.minusDays(1)
            y to y
        }
        Range.LAST_7 -> today.minusDays(6) to today
        Range.LAST_30 -> today.minusDays(HISTORY_DAYS) to today
        Range.CUSTOM -> {
            val a = filters.customFrom ?: today.minusDays(6)
            val b = filters.customTo ?: today
            minOf(a, b) to maxOf(a, b)
        }
    }

    fun matchesQuery(meal: Meal, query: String): Boolean {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return true
        if (meal.title.lowercase().contains(q)) return true
        return meal.items.any { it.name.lowercase().contains(q) }
    }

    fun days(meals: List<Meal>, today: LocalDate, filters: Filters): List<FoodDay> {
        val (from, to) = bounds(today, filters)
        val filtered = meals.asSequence()
            .filter { it.date in from..to }
            .filter { filters.types.isEmpty() || it.type in filters.types }
            .filter { matchesQuery(it, filters.query) }
            .toList()
        val grouped = filtered.groupBy { it.date }
        val dates = when (filters.sort) {
            Sort.OLDEST -> grouped.keys.sorted()
            else -> grouped.keys.sortedDescending()
        }
        return dates.map { date ->
            val list = grouped.getValue(date)
            val ordered = when (filters.sort) {
                Sort.NEWEST -> list.sortedByDescending { it.loggedAt }
                Sort.OLDEST -> list.sortedBy { it.loggedAt }
                Sort.HIGH_KCAL -> list.sortedByDescending { it.totals.kcal }
                Sort.LOW_KCAL -> list.sortedBy { it.totals.kcal }
            }
            FoodDay(date, ordered)
        }
    }

    fun toggleType(current: Set<MealType>, type: MealType): Set<MealType> {
        val next = if (type in current) current - type else current + type
        return if (next.size == MealType.entries.size) emptySet() else next
    }
}
