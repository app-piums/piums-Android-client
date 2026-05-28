package com.piums.cliente.ui.screens.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piums.cliente.data.auth.GoogleSignInCancelled
import com.piums.cliente.data.auth.GoogleSignInHelper
import com.piums.cliente.data.auth.GoogleSignInNoCredential
import com.piums.cliente.data.auth.OAuthWebLoginHelper
import com.piums.cliente.data.repository.AuthRepository
import com.piums.cliente.utils.LoginRateLimiter
import com.piums.cliente.utils.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val isLoading: Boolean = false,
    val error: String?     = null,
    val success: Boolean   = false
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val googleSignInHelper: GoogleSignInHelper,
    private val oAuthWebLoginHelper: OAuthWebLoginHelper,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    private val rateLimiter = LoginRateLimiter(appContext)

    fun login(email: String, password: String) {
        rateLimiter.shouldBlock()?.let { msg ->
            _state.update { it.copy(error = msg) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            authRepository.login(email, password)
                .onSuccess {
                    rateLimiter.reset()
                    _state.update { it.copy(isLoading = false, success = true) }
                }
                .onFailure { e ->
                    rateLimiter.recordFailure()
                    _state.update { it.copy(isLoading = false, error = e.toUserMessage()) }
                }
        }
    }

    fun register(nombre: String, email: String, password: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            authRepository.register(nombre, email, password)
                .onSuccess { _state.update { it.copy(isLoading = false, success = true) } }
                .onFailure { e -> _state.update { it.copy(isLoading = false, error = e.toUserMessage()) } }
        }
    }

    fun loginWithGoogle(context: Context) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            val firebaseIdToken = runCatching {
                googleSignInHelper.getFirebaseIdToken(context)
            }.getOrElse { e ->
                val msg = when (e) {
                    is GoogleSignInCancelled    -> null
                    is GoogleSignInNoCredential -> e.message
                    else                        -> e.toUserMessage()
                }
                _state.update { it.copy(isLoading = false, error = msg) }
                return@launch
            }

            authRepository.loginWithGoogle(firebaseIdToken)
                .onSuccess { _state.update { it.copy(isLoading = false, success = true) } }
                .onFailure { e -> _state.update { it.copy(isLoading = false, error = e.toUserMessage()) } }
        }
    }

    fun loginWithOAuth(context: Context, provider: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            val jwt = runCatching {
                oAuthWebLoginHelper.login(context, provider)
            }.getOrElse { e ->
                _state.update { it.copy(isLoading = false, error = e.toUserMessage()) }
                return@launch
            }

            authRepository.loginWithOAuth(jwt)
                .onSuccess { _state.update { it.copy(isLoading = false, success = true) } }
                .onFailure { e -> _state.update { it.copy(isLoading = false, error = e.toUserMessage()) } }
        }
    }

    fun forgotPassword(email: String, onDone: () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            authRepository.forgotPassword(email)
                .onSuccess { _state.update { it.copy(isLoading = false) }; onDone() }
                .onFailure { e -> _state.update { it.copy(isLoading = false, error = e.toUserMessage()) } }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }
}
