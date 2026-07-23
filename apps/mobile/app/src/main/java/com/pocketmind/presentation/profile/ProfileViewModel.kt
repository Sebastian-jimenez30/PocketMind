package com.pocketmind.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketmind.data.profile.ProfileRepository
import com.pocketmind.data.profile.ProfileSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val isLoading: Boolean = true,
    val displayName: String = "",
    val email: String = "",
    val currencyCode: String = "COP",
    val weekStartsOn: Int = 1,
    val monthlySummaryNotificationsEnabled: Boolean = false,
    val isSaving: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun updateDisplayName(value: String) = _uiState.update { it.copy(displayName = value, message = null) }
    fun updateEmail(value: String) = _uiState.update { it.copy(email = value, message = null) }
    fun selectCurrency(value: String) = _uiState.update { it.copy(currencyCode = value, message = null) }
    fun selectWeekStartsOn(value: Int) = _uiState.update { it.copy(weekStartsOn = value, message = null) }
    fun setMonthlySummaryNotifications(value: Boolean) = _uiState.update { it.copy(monthlySummaryNotificationsEnabled = value) }

    fun saveProfileAndPreferences() {
        val state = _uiState.value
        ProfileInputValidator.validateDisplayName(state.displayName)?.let { error ->
            showError(error)
            return
        }
        viewModelScope.launch {
            withSaving {
                profileRepository.save(state.toSettings())
                showMessage("Cambios guardados.")
            }
        }
    }

    fun changeEmail() {
        val email = _uiState.value.email.trim()
        ProfileInputValidator.validateEmail(email)?.let { error ->
            showError(error)
            return
        }
        viewModelScope.launch {
            withSaving {
                profileRepository.changeEmail(email)
                showMessage("Revisa tu correo para confirmar el cambio de dirección.")
            }
        }
    }

    fun changePassword(password: String, confirmation: String) {
        ProfileInputValidator.validatePassword(password, confirmation)?.let { error ->
            showError(error)
            return
        }
        viewModelScope.launch {
            withSaving {
                profileRepository.changePassword(password)
                showMessage("Contraseña actualizada.")
            }
        }
    }

    fun signOut(onComplete: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, message = null) }
            try {
                profileRepository.signOut()
                onComplete()
            } catch (exception: Exception) {
                showError(exception.toSafeMessage())
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    private fun load() {
        viewModelScope.launch {
            try {
                _uiState.value = profileRepository.load().toUiState()
            } catch (exception: Exception) {
                _uiState.update { it.copy(isLoading = false, message = exception.toSafeMessage(), isError = true) }
            }
        }
    }

    private suspend fun withSaving(block: suspend () -> Unit) {
        _uiState.update { it.copy(isSaving = true, message = null, isError = false) }
        try {
            block()
        } catch (exception: Exception) {
            showError(exception.toSafeMessage())
        } finally {
            _uiState.update { it.copy(isSaving = false) }
        }
    }

    private fun showMessage(message: String) = _uiState.update { it.copy(message = message, isError = false) }
    private fun showError(message: String) = _uiState.update { it.copy(message = message, isError = true) }
    private fun ProfileUiState.toSettings() = ProfileSettings(email, displayName, currencyCode, weekStartsOn, monthlySummaryNotificationsEnabled)
    private fun ProfileSettings.toUiState() = ProfileUiState(
        isLoading = false,
        email = email,
        displayName = displayName,
        currencyCode = currencyCode,
        weekStartsOn = weekStartsOn,
        monthlySummaryNotificationsEnabled = monthlySummaryNotificationsEnabled,
    )

    private fun Exception.toSafeMessage(): String = message?.takeIf(String::isNotBlank)
        ?: "No fue posible completar la operación. Inténtalo de nuevo."
}
