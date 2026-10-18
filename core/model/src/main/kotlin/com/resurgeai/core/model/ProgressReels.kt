package com.resurgeai.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** How often a progress reel is made. Three days is the shortest; four weeks is the default. */
enum class ReelInterval(val days: Int, val label: String) {
    DAYS_3(3, "Every 3 days"),
    DAYS_5(5, "Every 5 days"),
    WEEK_1(7, "Every week"),
    WEEKS_2(14, "Every 2 weeks"),
    WEEKS_3(21, "Every 3 weeks"),
    WEEKS_4(28, "Every 4 weeks"),
    WEEKS_6(42, "Every 6 weeks"),
    WEEKS_8(56, "Every 8 weeks");

    companion object {
        val DEFAULT = WEEKS_4
        fun fromDays(days: Int?): ReelInterval = entries.firstOrNull { it.days == days } ?: DEFAULT
    }
}

enum class ReelKind(val wire: String) {
    /** On the chosen interval. */
    REGULAR("regular"),

    /** The weight trend crossed a milestone toward the goal. */
    MILESTONE("milestone"),

    /** The goal was reached: the whole journey. */
    GOAL("goal");

    companion object {
        fun fromWire(value: String?): ReelKind = entries.firstOrNull { it.wire == value } ?: REGULAR
    }
}

/** A made reel: which photos it plays, in order, and how many of them are new since the last one. */
data class ProgressReel(
    val id: String,
    val kind: ReelKind,
    val createdAt: Instant,
    val from: LocalDate,
    val to: LocalDate,
    val frameIds: List<String>,
    val newFrames: Int,
    val milestoneKg: Double? = null,
    val watched: Boolean = false,
)

/** Reel settings and the scheduler's memory, shared across the user's phones. */
data class ReelSettings(
    val enabled: Boolean = true,
    val interval: ReelInterval = ReelInterval.DEFAULT,
    /** The last due date that was handled, with a reel or skipped. */
    val lastDue: LocalDate? = null,
    /** The last milestone (kg toward the goal) that got a reel. */
    val lastMilestoneKg: Double? = null,
    /** Start date of the goal whose "reached" reel was made, so it's made once per goal. */
    val goalReelFor: LocalDate? = null,
)

/** What the daily check decided. */
sealed interface ReelDecision {
    /** Nothing due, or due but not ready; [handledDue] is the due date to record as handled, if any. */
    data class Skip(val handledDue: LocalDate?) : ReelDecision

    data class Make(
        val kind: ReelKind,
        val frames: List<ProgressPhoto>,
        val newFrames: Int,
        val handledDue: LocalDate?,
        val milestoneKg: Double? = null,
    ) : ReelDecision
}

object ReelPlanner {
    const val MAX_FRAMES = 12
    const val MIN_PHOTOS = 3
    /** A milestone or goal reel this close to a regular one takes its place. */
    const val MERGE_DAYS = 3L

    /** The next regular due date: the last handled one (or the anchor) plus the interval. */
    fun nextDue(anchor: LocalDate, settings: ReelSettings): LocalDate =
        (settings.lastDue ?: anchor).plusDays(settings.interval.days.toLong())

    /**
     * The latest due date on or before [today], stepping by the interval, so a phone that was off
     * for a while handles one due date instead of a backlog. Null when nothing is due yet.
     */
    fun dueOnOrBefore(anchor: LocalDate, settings: ReelSettings, today: LocalDate): LocalDate? {
        var due = nextDue(anchor, settings)
        if (due.isAfter(today)) return null
        val step = settings.interval.days.toLong()
        while (!due.plusDays(step).isAfter(today)) due = due.plusDays(step)
        return due
    }

    /**
     * The daily decision. A reel needs [MIN_PHOTOS] photos in all and at least one taken since the
     * last reel; otherwise a due date passes quietly. Goal and milestone reels come whenever they
     * happen, and stand in for a regular reel due within [MERGE_DAYS].
     */
    fun decide(
        today: LocalDate,
        anchor: LocalDate?,
        photos: List<ProgressPhoto>,
        lastReelAt: Instant?,
        settings: ReelSettings,
        milestoneKg: Double? = null,
        goalReached: LocalDate? = null,
        zone: ZoneId = ZoneId.systemDefault(),
    ): ReelDecision {
        if (!settings.enabled || anchor == null) return ReelDecision.Skip(null)
        val due = dueOnOrBefore(anchor, settings, today)
        val ready = photos.size >= MIN_PHOTOS && photos.any { lastReelAt == null || it.takenAt.isAfter(lastReelAt) }
        val special = when {
            goalReached != null && goalReached != settings.goalReelFor -> ReelKind.GOAL
            milestoneKg != null && (settings.lastMilestoneKg == null || milestoneKg > settings.lastMilestoneKg + 1e-6) -> ReelKind.MILESTONE
            else -> null
        }
        if (!ready) return ReelDecision.Skip(due)
        val kind = special ?: if (due != null) ReelKind.REGULAR else return ReelDecision.Skip(null)
        // A special reel also covers a regular one that's due now or within a few days.
        val handled = due ?: nextDue(anchor, settings).takeIf { special != null && ChronoUnit.DAYS.between(today, it) <= MERGE_DAYS }
        val frames = frames(photos, lastReelAt, zone = zone)
        if (frames.size < 2) return ReelDecision.Skip(due)
        val newFrames = frames.count { lastReelAt == null || it.takenAt.isAfter(lastReelAt) }
        return ReelDecision.Make(kind, frames, newFrames, handled, if (kind == ReelKind.MILESTONE) milestoneKg else null)
    }

    /**
     * Up to [max] frames, oldest first: always the first photo; every photo since the last reel (the
     * newest if there are too many); and the journey in between at most one a week, evenly spaced.
     */
    fun frames(photos: List<ProgressPhoto>, lastReelAt: Instant?, max: Int = MAX_FRAMES, zone: ZoneId = ZoneId.systemDefault()): List<ProgressPhoto> {
        val sorted = photos.sortedBy { it.takenAt }
        if (sorted.size <= 2) return sorted
        val first = sorted.first()
        val recent = sorted.drop(1).filter { lastReelAt == null || it.takenAt.isAfter(lastReelAt) }
        val newOnes = if (lastReelAt == null) listOf(sorted.last()) else recent.takeLast(max - 1)
        val older = sorted.drop(1) - newOnes.toSet()
        // One a week through the middle, newest of each week.
        val weekly = older.groupBy { ChronoUnit.WEEKS.between(first.date(zone), it.date(zone)) }.values.map { it.last() }
        val room = (max - 1 - newOnes.size).coerceAtLeast(0)
        val middle = spread(weekly, room)
        return (listOf(first) + middle + newOnes).distinctBy { it.id }.sortedBy { it.takenAt }
    }

    /** [count] items picked evenly across [items], keeping their order. */
    internal fun <T> spread(items: List<T>, count: Int): List<T> {
        if (count <= 0 || items.isEmpty()) return emptyList()
        if (items.size <= count) return items
        if (count == 1) return listOf(items[items.size / 2])
        return (0 until count).map { i -> items[(i * (items.size - 1).toDouble() / (count - 1)).toInt()] }.distinct()
    }
}
