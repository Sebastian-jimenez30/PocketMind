package com.pocketmind.presentation.accounts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketmind.shared.domain.model.CurrencyCode
import com.pocketmind.shared.domain.model.FinancialAccount
import com.pocketmind.shared.domain.model.FinancialAccountType
import com.pocketmind.shared.domain.model.Money
import com.pocketmind.shared.domain.model.CreditCardProfile
import com.pocketmind.shared.domain.model.SavingsProductType
import com.pocketmind.shared.domain.model.SavingsProfile
import com.pocketmind.shared.domain.model.LoanProfile
import com.pocketmind.shared.domain.usecase.ManualFinanceUseCases
import com.pocketmind.shared.domain.usecase.GetFinancialAccountUseCase
import com.pocketmind.shared.domain.usecase.SaveFinancialAccountUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import java.time.LocalDate
import java.time.ZoneId
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
    val creditLimit: String = "",
    val annualRate: String = "",
    val closingDay: String = "",
    val paymentDay: String = "",
    val debtInstallments: String = "1",
    val debtFirstPaymentDate: String = "",
    val savingsType: SavingsProductType = SavingsProductType.SIMPLE,
    val maturityDate: String = "",
    val savingsOpenedAtEpochMillis: Long? = null,
    val monthlyPayment: String = "",
    val loanOpenedAtEpochMillis: Long? = null,
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
    private val manualFinance: ManualFinanceUseCases,
) : ViewModel() {
    private val accountId: String? = savedStateHandle["accountId"]
    private val _uiState = MutableStateFlow(AccountEditorUiState(accountId = accountId))
    val uiState: StateFlow<AccountEditorUiState> = _uiState.asStateFlow()

    init {
        accountId?.let { id ->
            viewModelScope.launch {
                getAccount(id)?.let { account ->
                    val card = manualFinance.getCreditCardProfile(id)
                    val savings = manualFinance.getSavingsProfile(id)
                    val loan = manualFinance.getLoanProfile(id)
                    _uiState.update {
                        it.copy(
                            name = account.name,
                            balance = account.openingBalance.minorUnits.toString(),
                            type = account.type,
                            creditLimit = card?.creditLimit?.minorUnits?.toString().orEmpty(),
                            closingDay = card?.statementClosingDay?.toString().orEmpty(),
                            debtInstallments = card?.openingDebtInstallmentCount?.toString() ?: "1",
                            debtFirstPaymentDate = card?.openingDebtFirstPaymentAtEpochMillis?.toLocalDateText().orEmpty(),
                            savingsType = savings?.type ?: SavingsProductType.SIMPLE,
                            maturityDate = savings?.maturityAtEpochMillis?.toLocalDateText().orEmpty(),
                            savingsOpenedAtEpochMillis = savings?.openedAtEpochMillis,
                            monthlyPayment = loan?.monthlyPayment?.minorUnits?.toString().orEmpty(),
                            loanOpenedAtEpochMillis = loan?.openedAtEpochMillis,
                            paymentDay = card?.paymentDueDay?.toString()
                                ?: loan?.paymentDueDay?.toString().orEmpty(),
                            annualRate = ((card?.annualInterestBasisPoints
                                ?: savings?.annualYieldBasisPoints
                                ?: loan?.annualInterestBasisPoints)?.div(100.0))?.toString().orEmpty(),
                        )
                    }
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
        val annualRateBasisPoints = state.annualRate.replace(",", ".").toDoubleOrNull()
            ?.takeIf { it >= 0 }
            ?.times(100)
            ?.toInt()
            ?: 0
        if (state.type == FinancialAccountType.LOAN) {
            val monthlyPayment = state.monthlyPayment.toLongOrNull()
            val paymentDay = state.paymentDay.toIntOrNull()
            if (monthlyPayment == null || monthlyPayment < 0 || paymentDay !in 1..31) {
                _uiState.update { it.copy(error = "Completa la cuota y el día de pago con valores válidos.") }
                return
            }
        }
        if (state.type == FinancialAccountType.CREDIT_CARD) {
            val limit = state.creditLimit.toLongOrNull()
            val closingDay = state.closingDay.toIntOrNull()
            val paymentDay = state.paymentDay.toIntOrNull()
            val debtInstallments = state.debtInstallments.toIntOrNull()
            val debtFirstPayment = state.debtFirstPaymentDate.parseLocalDate()
            if (
                limit == null || limit <= 0 ||
                closingDay !in 1..31 || paymentDay !in 1..31 ||
                debtInstallments !in 1..60 ||
                (balance > 0 && debtFirstPayment == null)
            ) {
                _uiState.update { it.copy(error = "Completa cupo, corte y pago con valores válidos.") }
                return
            }
        }
        val maturity = state.maturityDate.parseLocalDate()
        if (state.type == FinancialAccountType.SAVINGS &&
            state.savingsType == SavingsProductType.TERM_DEPOSIT &&
            maturity == null
        ) {
            _uiState.update { it.copy(error = "Agrega la fecha de vencimiento del CDT en formato dd/mm/aaaa.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            runCatching {
                val id = state.accountId ?: UUID.randomUUID().toString()
                saveAccount(
                    FinancialAccount(
                        id = id,
                        name = state.name.trim(),
                        type = state.type,
                        currency = CurrencyCode.COP,
                        openingBalance = Money(balance, CurrencyCode.COP),
                    ),
                )
                when (state.type) {
                    FinancialAccountType.CREDIT_CARD -> manualFinance.saveCreditCardProfile(
                        CreditCardProfile(
                            accountId = id,
                            creditLimit = Money(state.creditLimit.toLong(), CurrencyCode.COP),
                            annualInterestBasisPoints = annualRateBasisPoints,
                            statementClosingDay = state.closingDay.toInt(),
                            paymentDueDay = state.paymentDay.toInt(),
                            openingDebtInstallmentCount = state.debtInstallments.toInt(),
                            openingDebtFirstPaymentAtEpochMillis = state.debtFirstPaymentDate.parseLocalDate()
                                ?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli(),
                        ),
                    )
                    FinancialAccountType.SAVINGS -> manualFinance.saveSavingsProfile(
                        SavingsProfile(
                            accountId = id,
                            type = state.savingsType,
                            annualYieldBasisPoints = if (state.savingsType == SavingsProductType.SIMPLE) {
                                0
                            } else {
                                annualRateBasisPoints
                            },
                            openedAtEpochMillis = state.savingsOpenedAtEpochMillis ?: System.currentTimeMillis(),
                            maturityAtEpochMillis = maturity?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli(),
                        ),
                    )
                    FinancialAccountType.LOAN -> manualFinance.saveLoanProfile(
                        LoanProfile(
                            accountId = id,
                            annualInterestBasisPoints = annualRateBasisPoints,
                            monthlyPayment = Money(state.monthlyPayment.toLong(), CurrencyCode.COP),
                            paymentDueDay = state.paymentDay.toInt(),
                            openedAtEpochMillis = state.loanOpenedAtEpochMillis ?: System.currentTimeMillis(),
                        ),
                    )
                    else -> Unit
                }
            }.onSuccess { _uiState.update { it.copy(isSaving = false, saved = true) } }
                .onFailure { _uiState.update { it.copy(isSaving = false, error = "No pudimos guardar el producto. Inténtalo de nuevo.") } }
        }
    }
}

private fun String.parseLocalDate(): LocalDate? = runCatching {
    val parts = trim().split("/")
    LocalDate.of(parts[2].toInt(), parts[1].toInt(), parts[0].toInt())
}.getOrNull()

private fun Long.toLocalDateText(): String {
    val date = java.time.Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()
    return "%02d/%02d/%04d".format(date.dayOfMonth, date.monthValue, date.year)
}
