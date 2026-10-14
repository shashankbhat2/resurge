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

data class WeeklyArea(val area: String, val trend: String, val note: String)

data class WeeklyDetails(
    val summary: String,
    val wins: List<String>,
    val areas: List<WeeklyArea>,
    val focus: List<String>,
    val weekStart: String?,
    val weekEnd: String?,
    /** A note on the week's habits and streaks (empty when none are set up). */
    val habits: String = "",
)

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

data class CoachThread(val id: String, val title: String, val updatedAt: Instant?, val preview: String?)

enum class TurnRole { USER, COACH }

/** A displayable message in a thread (tool plumbing and context turns are filtered out). */
data class CoachTurn(
    val id: String,
    val role: TurnRole,
    val text: String,
    val model: CoachModel?,
    /** True for system notices like "couldn't reply" (not a real model reply). */
    val notice: Boolean,
)

/** Events while a chat reply streams in. */
sealed interface ChatEvent {
    data class Thread(val threadId: String) : ChatEvent
    data class Delta(val text: String) : ChatEvent
    data class UsingTool(val name: String) : ChatEvent
    data class Done(val threadId: String, val text: String, val refused: Boolean) : ChatEvent
    data class Failed(val message: String) : ChatEvent
}
