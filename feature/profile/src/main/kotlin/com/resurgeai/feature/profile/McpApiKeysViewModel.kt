package com.resurgeai.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resurgeai.core.data.mcp.McpApiKeysRepository
import com.resurgeai.core.model.McpApiKey
import com.resurgeai.core.model.NewMcpApiKey
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class McpApiKeysUiState(
    val keys: List<McpApiKey> = emptyList(),
    val loading: Boolean = true,
    /** A create or revoke is in flight. */
    val busy: Boolean = false,
    val error: String? = null,
    /** The key just made; shown once, then dropped. */
    val created: NewMcpApiKey? = null,
)

@HiltViewModel
class McpApiKeysViewModel @Inject constructor(
    private val repository: McpApiKeysRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(McpApiKeysUiState())
    val state: StateFlow<McpApiKeysUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            runCatching { repository.list() }
                .onSuccess { keys -> _state.update { it.copy(keys = keys, loading = false, error = null) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.message) } }
        }
    }

    fun create(name: String) {
        if (name.isBlank() || _state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            runCatching { repository.create(name) }
                .onSuccess { made -> _state.update { it.copy(busy = false, created = made, keys = listOf(made.info) + it.keys) } }
                .onFailure { e -> _state.update { it.copy(busy = false, error = e.message) } }
        }
    }

    /** Forgets the plain-text key once the person has copied it. */
    fun dismissCreated() {
        _state.update { it.copy(created = null) }
    }

    fun revoke(id: String) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            runCatching { repository.revoke(id) }
                .onSuccess { _state.update { s -> s.copy(busy = false, keys = s.keys.filterNot { it.id == id }) } }
                .onFailure { e -> _state.update { it.copy(busy = false, error = e.message) } }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
