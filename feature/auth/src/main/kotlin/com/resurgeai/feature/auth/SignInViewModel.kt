package com.resurgeai.feature.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resurgeai.core.data.auth.AuthRepository
import com.resurgeai.core.data.auth.SignInResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SignInUiState(
    val loading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class SignInViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(SignInUiState())
    val state: StateFlow<SignInUiState> = _state.asStateFlow()

    /** [activityContext] is needed by Credential Manager to show its UI; it isn't retained. */
    fun signIn(activityContext: Context) {
        if (_state.value.loading) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val result = authRepository.signInWithGoogle(activityContext)
            _state.update {
                when (result) {
                    // Session state takes over navigation on success; keep the spinner until it does.
                    SignInResult.Success -> it
                    SignInResult.Cancelled -> it.copy(loading = false)
                    is SignInResult.Failure -> it.copy(loading = false, error = result.message)
                }
            }
        }
    }
}
