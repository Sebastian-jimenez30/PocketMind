package com.pocketmind.presentation.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketmind.data.auth.AuthRepository
import com.pocketmind.data.auth.AuthSessionState
import com.pocketmind.data.sync.SessionBootstrapper
import com.pocketmind.shared.domain.usecase.ObserveFinancialSetupCompletedUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AppDestination {
    RESOLVING,
    AUTH,
    ONBOARDING,
    HOME,
}

data class AppStartupUiState(
    val destination: AppDestination = AppDestination.RESOLVING,
)

/**
 * Owns the application's entry decision.
 *
 * Authentication restoration, remote bootstrap and the local onboarding flag
 * are resolved before a destination is exposed to the navigation graph.
 */
@HiltViewModel
class AppStartupViewModel @Inject constructor(
    authRepository: AuthRepository,
    private val sessionBootstrapper: SessionBootstrapper,
    private val observeFinancialSetupCompleted: ObserveFinancialSetupCompletedUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AppStartupUiState())
    val uiState: StateFlow<AppStartupUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.observeSessionState()
                .distinctUntilChanged()
                .collectLatest { sessionState ->
                    when (sessionState) {
                        AuthSessionState.RESOLVING -> show(AppDestination.RESOLVING)
                        AuthSessionState.UNAUTHENTICATED -> show(AppDestination.AUTH)
                        AuthSessionState.AUTHENTICATED -> resolveAuthenticatedDestination()
                    }
                }
        }
    }

    private suspend fun resolveAuthenticatedDestination() {
        show(AppDestination.RESOLVING)
        sessionBootstrapper.bootstrapCurrentSession()
        observeFinancialSetupCompleted()
            .distinctUntilChanged()
            .collect { completed ->
                show(
                    if (completed) {
                        AppDestination.HOME
                    } else {
                        AppDestination.ONBOARDING
                    },
                )
            }
    }

    private fun show(destination: AppDestination) {
        _uiState.update { it.copy(destination = destination) }
    }
}
