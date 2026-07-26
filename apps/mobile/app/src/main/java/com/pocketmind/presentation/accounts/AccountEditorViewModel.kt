package com.pocketmind.presentation.accounts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketmind.shared.domain.model.CurrencyCode
import com.pocketmind.shared.domain.model.FinancialAccount
import com.pocketmind.shared.domain.model.FinancialAccountType
import com.pocketmind.shared.domain.model.Money
import com.pocketmind.shared.domain.usecase.GetFinancialAccountUseCase
import com.pocketmind.shared.domain.usecase.SaveFinancialAccountUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AccountEditorUiState(
    val accountId: String? = null,
    val name: String = "",
    val balance: String = "",
    val type: FinancialAccountType = FinancialAccountType.BANK_ACCOUNT,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
)

@HiltViewModel
class AccountEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getAccount: GetFinancialAccountUseCase,
    private val saveAccount: SaveFinancialAccountUseCase,
) : ViewModel() {
    private val accountId: String? = savedStateHandle["accountId"]
    private val _uiState = MutableStateFlow(AccountEditorUiState(accountId = accountId))
    val uiState: StateFlow<AccountEditorUiState> = _uiState.asStateFlow()

    init {
        accountId?.let { id ->
            viewModelScope.launch {
                getAccount(id)?.let { account ->
                    _uiState.update { it.copy(name = account.name, balance = account.openingBalance.minorUnits.toString(), type = account.type) }
                }
                _uiState.update { it.copy(isLoading = false) }
            }
        } ?: _uiState.update { it.copy(isLoading = false) }
    }

    fun update(transform: (AccountEditorUiState) -> AccountEditorUiState) = _uiState.update(transform)

    fun save() {
        val state = _uiState.value
        val balance = state.balance.toLongOrNull()?.takeIf { it >= 0 }
        if (state.name.isBlank() || balance == null) {
            _uiState.update { it.copy(error = "Agrega un nombre y un saldo válido.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            runCatching {
                saveAccount(
                    FinancialAccount(
                        id = state.accountId ?: UUID.randomUUID().toString(),
                        name = state.name.trim(),
                        type = state.type,
                        currency = CurrencyCode.COP,
                        openingBalance = Money(balance, CurrencyCode.COP),
                    ),
                )
            }.onSuccess { _uiState.update { it.copy(isSaving = false, saved = true) } }
                .onFailure { _uiState.update { it.copy(isSaving = false, error = "No pudimos guardar la cuenta. Inténtalo de nuevo.") } }
        }
    }
}
