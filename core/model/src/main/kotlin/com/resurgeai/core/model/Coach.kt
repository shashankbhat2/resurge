package com.resurgeai.core.model

import java.time.Instant

/** Chat model choice shown as the Fast / Smart switch. */
enum class CoachModel(val wire: String, val label: String) {
    FAST("fast", "Fast"),
    SMART("smart", "Smart");

    companion object {
        fun fromWire(value: String?): CoachModel? = entries.firstOrNull { it.wire == value }
    }
}

enum class InsightKind { ACTIVITY, SLEEP, WEEKLY }

data class InsightHighlight(val label: String, val value: String)

/** One area of the week: training, weight, food, sleep (older reviews: activity, heart). [value] is the week's figure. */
data class WeeklyArea(val area: String, val trend: String, val note: String, val value: String = "")

data class WeeklyDetails(
    val summary: String,
    val wins: List<String>,
    val areas: List<WeeklyArea>,
    val focus: List<String>,
    val weekStart: String?,
    val weekEnd: String?,
    /** A note on the week's habits and streaks (empty when none are set up). */
    val habits: String = "",
    /** Each day of the week, first to last: a good day (true), missed (false) or not settled (null). Empty in older reviews. */
    val days: List<Boolean?> = emptyList(),
    val goodDays: Int? = null,
    /** Good days the week asked for. */
    val target: Int? = null,
) {
    /** Won, lost, or null when the review doesn't say. */
    val won: Boolean? get() = if (goodDays != null && target != null) goodDays >= target else null
}

/** An insight written by the backend (per finished activity/sleep, or the Saturday weekly review). */
data class CoachInsight(
    val id: String,
    val kind: InsightKind,
    val category: String?,
    val title: String,
    val body: String,
    val highlights: List<InsightHighlight>,
    val suggestion: String?,
    val createdAt: Instant,
    val weekly: WeeklyDetails?,
    /** The user dismissed it from Today (or opened it from there). New insights start unseen. */
    val seen: Boolean = false,
)

data class CoachThread(
    val id: String,
    val title: String,
    val updatedAt: Instant?,
    val preview: String?,
    /** The insight the chat was opened from, if any. */
    val insightId: String? = null,
    val insightTitle: String? = null,
)

/** A change to the plan the coach offered: tapping it rebuilds the week with [change] for review. */
data class PlanChangeOffer(val change: String, val label: String)

enum class TurnRole { USER, COACH }

/** A displayable message in a thread (tool plumbing and context turns are filtered out). */
data class CoachTurn(
    val id: String,
    val role: TurnRole,
    val text: String,
    val model: CoachModel?,
    /** True for system notices like "couldn't reply" (not a real model reply). */
    val notice: Boolean,
    val offer: PlanChangeOffer? = null,
)

/** Events while a chat reply streams in. */
sealed interface ChatEvent {
    data class Thread(val threadId: String) : ChatEvent
    data class Delta(val text: String) : ChatEvent
    data class UsingTool(val name: String) : ChatEvent
    data class Done(val threadId: String, val text: String, val refused: Boolean) : ChatEvent
    data class Failed(val message: String) : ChatEvent
}
