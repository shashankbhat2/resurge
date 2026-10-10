package com.resurgeai.feature.coach

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

enum class CoachTab(val label: String) { INSIGHTS("Insights"), CHAT("Chat") }

enum class InsightCategory(val label: String) { SLEEP("Sleep"), ACTIVITY("Activity"), HEART("Heart"), NUTRITION("Nutrition") }

data class Insight(
    val id: String,
    val category: InsightCategory,
    val title: String,
    val body: String,
    val at: LocalDateTime,
)

enum class Author { USER, COACH }

data class ChatMessage(val id: Long, val author: Author, val text: String)

data class CoachUiState(
    val tab: CoachTab = CoachTab.INSIGHTS,
    val insights: List<Insight> = emptyList(),
    val messages: List<ChatMessage> = emptyList(),
    val replying: Boolean = false,
)

/**
 * Coach screen state. Insights and replies are placeholders until the Claude-backed coach
 * (Cloud Functions, Phase 4) is wired in; the UI contract here is what that will fill.
 */
@HiltViewModel
class CoachViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val initialTab = savedStateHandle.get<String>(ARG_TAB)
        ?.let { arg -> CoachTab.entries.firstOrNull { it.name.equals(arg, ignoreCase = true) } }
        ?: CoachTab.INSIGHTS

    private val _state = MutableStateFlow(CoachUiState(tab = initialTab, insights = placeholderInsights()))
    val state: StateFlow<CoachUiState> = _state.asStateFlow()

    private var nextId = 0L

    fun selectTab(tab: CoachTab) = _state.update { it.copy(tab = tab) }

    /** Sends a chat message. Sending from the Insights tab jumps to Chat. */
    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _state.value.replying) return
        _state.update {
            it.copy(
                tab = CoachTab.CHAT,
                messages = it.messages + ChatMessage(nextId++, Author.USER, trimmed),
                replying = true,
            )
        }
        viewModelScope.launch {
            delay(REPLY_DELAY_MS)
            _state.update {
                it.copy(
                    replying = false,
                    messages = it.messages + ChatMessage(nextId++, Author.COACH, PLACEHOLDER_REPLY),
                )
            }
        }
    }

    companion object {
        /** Must match the property name in the navigation route. */
        const val ARG_TAB = "tab"
        internal const val REPLY_DELAY_MS = 700L
        internal const val PLACEHOLDER_REPLY =
            "I'm not connected yet — soon I'll answer using your sleep, activity, heart and food data. " +
                "Try asking again once the coach is live."

        private fun placeholderInsights(): List<Insight> {
            val now = LocalDateTime.now()
            return listOf(
                Insight(
                    "sleep-debt", InsightCategory.SLEEP, "A little short on sleep",
                    "You slept 40 minutes less than your weekly average, and your resting heart rate is up 3 bpm. " +
                        "Keep today's training light and aim to be in bed by 10:30.",
                    now.withHour(7).withMinute(0),
                ),
                Insight(
                    "steps-streak", InsightCategory.ACTIVITY, "Four active days this week",
                    "You've hit 8,000+ steps on four of the last five days. One more 30-minute walk keeps the streak going.",
                    now.minusDays(1).withHour(18).withMinute(30),
                ),
                Insight(
                    "rhr-trend", InsightCategory.HEART, "Resting heart rate is trending down",
                    "Your resting heart rate has dropped 2 bpm over the past month — a good sign your fitness is improving.",
                    now.minusDays(2).withHour(8).withMinute(15),
                ),
                Insight(
                    "protein", InsightCategory.NUTRITION, "Protein looks light at dinner",
                    "Once food logging is on, I'll check whether your meals support your training. Start by scanning tonight's dinner.",
                    now.minusDays(3).withHour(20).withMinute(0),
                ),
            )
        }
    }
}
