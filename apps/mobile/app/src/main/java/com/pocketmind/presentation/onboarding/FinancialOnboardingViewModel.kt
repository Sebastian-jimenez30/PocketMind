package com.pocketmind.presentation.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketmind.shared.domain.model.CurrencyCode
import com.pocketmind.shared.domain.model.Debt
import com.pocketmind.shared.domain.model.FinancialAccount
import com.pocketmind.shared.domain.model.FinancialAccountType
import com.pocketmind.shared.domain.model.FinancialSetup
import com.pocketmind.shared.domain.model.IncomeSource
import com.pocketmind.shared.domain.model.Money
import com.pocketmind.shared.domain.model.Recurrence
import com.pocketmind.shared.domain.model.RecurringObligation
import com.pocketmind.shared.domain.model.SavingsPlan
import com.pocketmind.shared.domain.model.SavingsPlanType
import com.pocketmind.shared.domain.usecase.ObserveFinancialSetupCompletedUseCase
import com.pocketmind.shared.domain.usecase.SaveInitialFinancialSetupUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FinancialOnboardingUiState(
    val step: Int = 0,
    val accountName: String = "",
    val accountBalance: String = "",
    val accountType: FinancialAccountType = FinancialAccountType.BANK_ACCOUNT,
    val incomeName: String = "",
    val incomeAmount: String = "",
    val incomeRecurrence: Recurrence = Recurrence.MONTHLY,
    val debtName: String = "",
    val debtBalance: String = "",
    val debtInstallment: String = "",
    val debtInterestRate: String = "",
    val debtDueDay: String = "",
    val savingsName: String = "",
    val savingsAmount: String = "",
    val savingsMonthlyContribution: String = "",
    val savingsType: SavingsPlanType = SavingsPlanType.FLEXIBLE,
    val savingsTargetAmount: String = "",
    val savingsAnnualYield: String = "",
    val obligationName: String = "",
    val obligationAmount: String = "",
    val obligationDueDay: String = "",
    val isSaving: Boolean = false,
    val isAlreadyCompleted: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class FinancialOnboardingViewModel @Inject constructor(
    observeFinancialSetupCompleted: ObserveFinancialSetupCompletedUseCase,
    private val saveInitialFinancialSetup: SaveInitialFinancialSetupUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(FinancialOnboardingUiState())
    val uiState: StateFlow<FinancialOnboardingUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            observeFinancialSetupCompleted().collect { completed ->
                _uiState.update { it.copy(isAlreadyCompleted = completed) }
            }
        }
    }

    fun previous() = _uiState.update { it.copy(step = (it.step - 1).coerceAtLeast(0), error = null) }
    fun next() = _uiState.update { it.copy(step = (it.step + 1).coerceAtMost(LAST_STEP), error = null) }
    fun update(transform: (FinancialOnboardingUiState) -> FinancialOnboardingUiState) = _uiState.update(transform)

    fun save() {
        val state = _uiState.value
        val setup = state.toSetupOrNull()
        if (setup == null) {
            _uiState.update { it.copy(error = "Revisa los valores ingresados antes de continuar.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            runCatching { saveInitialFinancialSetup(setup) }
                .onFailure { _uiState.update { current -> current.copy(isSaving = false, error = "No pudimos guardar tu punto de partida. Inténtalo de nuevo.") } }
                .onSuccess { _uiState.update { it.copy(isSaving = false) } }
        }
    }

    private fun FinancialOnboardingUiState.toSetupOrNull(): FinancialSetup? {
        val account = accountName.trim().takeIf(String::isNotEmpty)?.let { name ->
            accountBalance.toMoneyOrNull()?.let { amount ->
                FinancialAccount(UUID.randomUUID().toString(), name, accountType, CurrencyCode.COP, amount)
            }
        }
        if (accountName.isNotBlank() && account == null) return null

        val income = incomeName.trim().takeIf(String::isNotEmpty)?.let { name ->
            incomeAmount.toMoneyOrNull()?.let { amount ->
                IncomeSource(UUID.randomUUID().toString(), name, amount, incomeRecurrence)
            }
        }
        if (incomeName.isNotBlank() && income == null) return null

        val debt = debtName.trim().takeIf(String::isNotEmpty)?.let { name ->
            debtBalance.toMoneyOrNull()?.let { balance ->
                val installment = debtInstallment.takeIf(String::isNotBlank)?.toMoneyOrNull()
                    ?: if (debtInstallment.isBlank()) null else return@let null
                val dueDay = debtDueDay.takeIf(String::isNotBlank)?.toIntOrNull() ?: 1
                if (dueDay !in 1..31) return@let null
                val annualRate = debtInterestRate.takeIf(String::isNotBlank)?.toBasisPointsOrNull()
                    ?: if (debtInterestRate.isBlank()) null else return@let null
                Debt(
                    UUID.randomUUID().toString(),
                    name,
                    balance,
                    interestRateAnnualBasisPoints = annualRate,
                    installmentAmount = installment,
                    dueDayOfMonth = dueDay,
                )
            }
        }
        if (debtName.isNotBlank() && debt == null) return null

        val savings = savingsName.trim().takeIf(String::isNotEmpty)?.let { name ->
            savingsAmount.toNonNegativeMoneyOrNull()?.let { amount ->
                val contribution = savingsMonthlyContribution.takeIf(String::isNotBlank)?.toMoneyOrNull()
                    ?: if (savingsMonthlyContribution.isBlank()) null else return@let null
                val target = savingsTargetAmount.takeIf(String::isNotBlank)?.toMoneyOrNull()
                    ?: if (savingsTargetAmount.isBlank()) null else return@let null
                val annualYield = savingsAnnualYield.takeIf(String::isNotBlank)?.toBasisPointsOrNull()
                    ?: if (savingsAnnualYield.isBlank()) null else return@let null
                SavingsPlan(
                    UUID.randomUUID().toString(),
                    name,
                    savingsType,
                    amount,
                    targetAmount = target,
                    monthlyContribution = contribution,
                    annualYieldBasisPoints = annualYield,
                )
            }
        }
        if (savingsName.isNotBlank() && savings == null) return null

        val obligation = obligationName.trim().takeIf(String::isNotEmpty)?.let { name ->
            obligationAmount.toMoneyOrNull()?.let { amount ->
                val dueDay = obligationDueDay.takeIf(String::isNotBlank)?.toIntOrNull() ?: 1
                if (dueDay !in 1..31) return@let null
                RecurringObligation(UUID.randomUUID().toString(), name, amount, Recurrence.MONTHLY, dueDay)
            }
        }
        if (obligationName.isNotBlank() && obligation == null) return null

        val savingsAccount = savings?.let {
            FinancialAccount(
                id = it.id,
                name = it.name,
                type = com.pocketmind.shared.domain.model.FinancialAccountType.SAVINGS,
                currency = CurrencyCode.COP,
                openingBalance = it.currentAmount,
            )
        }
        val debtAccount = debt?.let {
            FinancialAccount(
                id = it.id,
                name = it.name,
                type = com.pocketmind.shared.domain.model.FinancialAccountType.LOAN,
                currency = CurrencyCode.COP,
                openingBalance = it.outstandingBalance,
            )
        }
        return FinancialSetup(
            accounts = listOfNotNull(account, savingsAccount, debtAccount),
            incomeSources = listOfNotNull(income),
            debts = listOfNotNull(debt),
            savingsPlans = listOfNotNull(savings),
            recurringObligations = listOfNotNull(obligation),
        )
    }

    private fun String.toMoneyOrNull(): Money? = toLongOrNull()?.takeIf { it > 0 }?.let { Money(it, CurrencyCode.COP) }
    private fun String.toNonNegativeMoneyOrNull(): Money? = toLongOrNull()?.takeIf { it >= 0 }?.let { Money(it, CurrencyCode.COP) }
    private fun String.toBasisPointsOrNull(): Int? = toDoubleOrNull()?.takeIf { it >= 0 }?.times(100)?.toInt()

    private companion object {
        const val LAST_STEP = 5
    }
}
