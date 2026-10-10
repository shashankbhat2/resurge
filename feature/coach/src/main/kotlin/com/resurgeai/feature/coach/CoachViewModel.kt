package com.resurgeai.feature.coach

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resurgeai.core.data.coach.CoachRepository
import com.resurgeai.core.model.ChatEvent
import com.resurgeai.core.model.CoachInsight
import com.resurgeai.core.model.CoachModel
import com.resurgeai.core.model.CoachThread
import com.resurgeai.core.model.CoachTurn
import com.resurgeai.core.model.TurnRole
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class CoachTab(val label: String) { INSIGHTS("Insights"), CHAT("Chat") }

/** A reply being generated: the user's message (until it's saved) and the text streamed so far. */
data class PendingReply(
    val userText: String,
    val streamedText: String = "",
    val usingTool: String? = null,
    /** Number of saved turns when the message was sent; later turns belong to this exchange. */
    val baseline: Int,
    val done: Boolean = false,
)

data class CoachUiState(
    val tab: CoachTab = CoachTab.INSIGHTS,
    val insights: List<CoachInsight> = emptyList(),
    val insightsLoaded: Boolean = false,
    val threads: List<CoachThread> = emptyList(),
    val threadId: String? = null,
    val turns: List<CoachTurn> = emptyList(),
    val pending: PendingReply? = null,
    val model: CoachModel = CoachModel.FAST,
    val showHistory: Boolean = false,
    val error: String? = null,
) {
    val sending: Boolean get() = pending != null && !pending.done

    /**
     * Saved turns to show. While a reply is in flight only the turns from before it are shown; the
     * screen draws the pending user message and the live streamed text after them instead, so
     * partially-saved tool rounds never appear twice.
     */
    val visibleTurns: List<CoachTurn> get() = pending?.let { turns.take(it.baseline) } ?: turns
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CoachViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: CoachRepository,
) : ViewModel() {
    private val initialTab = savedStateHandle.get<String>(ARG_TAB)
        ?.let { arg -> CoachTab.entries.firstOrNull { it.name.equals(arg, ignoreCase = true) } }
        ?: CoachTab.INSIGHTS

    private val _state = MutableStateFlow(CoachUiState(tab = initialTab))
    val state: StateFlow<CoachUiState> = _state.asStateFlow()

    private val threadId = MutableStateFlow<String?>(null)
    private var sendJob: Job? = null

    init {
        viewModelScope.launch {
            repository.insights()
                .catch { _state.update { it.copy(insightsLoaded = true) } }
                .collect { list -> _state.update { it.copy(insights = list, insightsLoaded = true) } }
        }
        viewModelScope.launch {
            repository.threads().catch { emit(emptyList()) }.collect { list ->
                _state.update { s ->
                    // Open the most recent conversation on first load.
                    if (s.threadId == null && s.pending == null && list.isNotEmpty() && threadId.value == null) {
                        threadId.value = list.first().id
                    }
                    s.copy(threads = list)
                }
            }
        }
        viewModelScope.launch {
            threadId
                .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repository.turns(id) }
                .catch { emit(emptyList()) }
                .collect { turns -> _state.update { it.copy(turns = turns).settled() } }
        }
        viewModelScope.launch { threadId.collect { id -> _state.update { it.copy(threadId = id) } } }
    }

    fun selectTab(tab: CoachTab) = _state.update { it.copy(tab = tab, showHistory = false) }

    fun selectModel(model: CoachModel) = _state.update { it.copy(model = model) }

    fun toggleHistory() = _state.update { it.copy(showHistory = !it.showHistory) }

    fun openThread(id: String) {
        if (_state.value.sending) return
        threadId.value = id
        _state.update { it.copy(showHistory = false, pending = null, error = null, tab = CoachTab.CHAT) }
    }

    fun newChat() {
        if (_state.value.sending) return
        threadId.value = null
        _state.update { it.copy(showHistory = false, pending = null, error = null, tab = CoachTab.CHAT, turns = emptyList()) }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    /** Sends a message (from either tab — Insights jumps to Chat) and streams the reply. */
    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _state.value.sending) return
        val current = _state.value
        _state.update {
            it.copy(
                tab = CoachTab.CHAT,
                showHistory = false,
                error = null,
                pending = PendingReply(userText = trimmed, baseline = current.turns.size),
            )
        }
        sendJob = viewModelScope.launch {
            repository.send(current.threadId, trimmed, current.model).collect { event ->
                when (event) {
                    is ChatEvent.Thread -> if (threadId.value != event.threadId) threadId.value = event.threadId
                    is ChatEvent.Delta -> _state.update { s ->
                        s.copy(pending = s.pending?.copy(streamedText = s.pending.streamedText + event.text, usingTool = null))
                    }
                    is ChatEvent.UsingTool -> _state.update { s -> s.copy(pending = s.pending?.copy(usingTool = event.name)) }
                    is ChatEvent.Done -> _state.update { s -> s.copy(pending = s.pending?.copy(done = true)).settled() }
                    is ChatEvent.Failed -> _state.update { s -> s.copy(pending = null, error = event.message) }
                }
            }
        }
    }

    /** Drops the pending reply once the finished exchange (including the reply) has been saved. */
    private fun CoachUiState.settled(): CoachUiState {
        val p = pending ?: return this
        val saved = turns.drop(p.baseline)
        val complete = p.done && saved.any { it.role == TurnRole.USER } && saved.any { it.role == TurnRole.COACH }
        return if (complete) copy(pending = null) else this
    }

    companion object {
        /** Must match the property name in the navigation route. */
        const val ARG_TAB = "tab"
    }
}
