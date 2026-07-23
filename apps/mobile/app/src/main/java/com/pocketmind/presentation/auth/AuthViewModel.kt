package com.pocketmind.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketmind.data.auth.AuthOperationResult
import com.pocketmind.data.auth.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AuthMode { SIGN_IN, SIGN_UP, RECOVERY }

data class AuthUiState(
    val mode: AuthMode = AuthMode.SIGN_IN,
    val email: String = "",
    val password: String = "",
    val passwordConfirmation: String = "",
    val isLoading: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
    val isAuthenticated: Boolean = false,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.update { it.copy(isAuthenticated = authRepository.hasActiveSession()) }
        }
    }

    fun updateEmail(value: String) = _uiState.update { it.copy(email = value, message = null) }
    fun updatePassword(value: String) = _uiState.update { it.copy(password = value, message = null) }
    fun updatePasswordConfirmation(value: String) = _uiState.update { it.copy(passwordConfirmation = value, message = null) }

    fun changeMode(mode: AuthMode) {
        _uiState.update { it.copy(mode = mode, password = "", passwordConfirmation = "", message = null, isError = false) }
    }

    fun submit() {
        val state = _uiState.value
        AuthInputValidator.validate(state)?.let { error ->
            _uiState.update { it.copy(message = error, isError = true) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, message = null, isError = false) }
            val result = when (state.mode) {
                AuthMode.SIGN_IN -> authRepository.signIn(state.email.trim(), state.password)
                AuthMode.SIGN_UP -> authRepository.signUp(state.email.trim(), state.password)
                AuthMode.RECOVERY -> authRepository.sendPasswordRecovery(state.email.trim())
            }
            handle(result, state.mode)
        }
    }

    fun signInWithGoogle() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, message = null, isError = false) }
            handle(authRepository.signInWithGoogle(), AuthMode.SIGN_IN)
        }
    }

    private fun handle(result: AuthOperationResult, mode: AuthMode) {
        _uiState.update { state ->
            when (result) {
                AuthOperationResult.Success -> when (mode) {
                    AuthMode.SIGN_IN -> state.copy(isLoading = false, isAuthenticated = true)
                    AuthMode.SIGN_UP -> state.copy(
                        isLoading = false,
                        message = "Revisa tu correo para confirmar tu cuenta antes de iniciar sesión.",
                        isError = false,
                    )
                    AuthMode.RECOVERY -> state.copy(
                        isLoading = false,
                        message = "Te enviamos instrucciones para restablecer tu contraseña.",
                        isError = false,
                    )
                }
                AuthOperationResult.ExternalFlowStarted -> state.copy(
                    isLoading = false,
                    message = "Completa el acceso con Google en el navegador para continuar.",
                    isError = false,
                )
                is AuthOperationResult.Failure -> state.copy(
                    isLoading = false,
                    message = result.message,
                    isError = true,
                )
            }
        }
    }

}
