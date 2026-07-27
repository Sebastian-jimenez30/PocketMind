package com.pocketmind.presentation.products

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketmind.shared.domain.model.CreditCardOverview
import com.pocketmind.shared.domain.model.CreditCardPayment
import com.pocketmind.shared.domain.model.FinancialAccount
import com.pocketmind.shared.domain.model.FinancialTransaction
import com.pocketmind.shared.domain.model.InstallmentPurchase
import com.pocketmind.shared.domain.model.SavingsMovement
import com.pocketmind.shared.domain.model.SavingsProfile
import com.pocketmind.shared.domain.model.SavingsProjection
import com.pocketmind.shared.domain.model.calculateCreditCardOverview
import com.pocketmind.shared.domain.model.calculateSavingsProjection
import com.pocketmind.shared.domain.usecase.ManualFinanceUseCases
import com.pocketmind.shared.domain.repository.TransactionRepository
import com.pocketmind.shared.domain.usecase.ObserveActiveFinancialAccountsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ProductDetailUiState(
    val account: FinancialAccount? = null,
    val cardOverview: CreditCardOverview? = null,
    val purchases: List<InstallmentPurchase> = emptyList(),
    val payments: List<CreditCardPayment> = emptyList(),
    val savingsProfile: SavingsProfile? = null,
    val savingsProjection: SavingsProjection? = null,
    val savingsMovements: List<SavingsMovement> = emptyList(),
    val transactions: List<FinancialTransaction> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class ProductDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeAccounts: ObserveActiveFinancialAccountsUseCase,
    transactionRepository: TransactionRepository,
    manualFinance: ManualFinanceUseCases,
) : ViewModel() {
    private val accountId: String = checkNotNull(savedStateHandle["accountId"])

    val uiState: StateFlow<ProductDetailUiState> = combine(
        observeAccounts(),
        transactionRepository.observeAll(),
        manualFinance.observeCreditCardProfiles(),
        manualFinance.observeInstallmentPurchases(),
        manualFinance.observeCreditCardPayments(),
        manualFinance.observeSavingsProfiles(),
        manualFinance.observeSavingsMovements(),
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val accounts = values[0] as List<FinancialAccount>
        @Suppress("UNCHECKED_CAST")
        val transactions = values[1] as List<FinancialTransaction>
        @Suppress("UNCHECKED_CAST")
        val cardProfiles = values[2] as List<com.pocketmind.shared.domain.model.CreditCardProfile>
        @Suppress("UNCHECKED_CAST")
        val purchases = values[3] as List<InstallmentPurchase>
        @Suppress("UNCHECKED_CAST")
        val payments = values[4] as List<CreditCardPayment>
        @Suppress("UNCHECKED_CAST")
        val savingsProfiles = values[5] as List<SavingsProfile>
        @Suppress("UNCHECKED_CAST")
        val savingsMovements = values[6] as List<SavingsMovement>

        val account = accounts.firstOrNull { it.id == accountId }
        val profile = cardProfiles.firstOrNull { it.accountId == accountId }
        val savings = savingsProfiles.firstOrNull { it.accountId == accountId }
        val accountPurchases = purchases.filter { it.accountId == accountId }
        val accountPayments = payments.filter { it.accountId == accountId }
        val accountSavingsMovements = savingsMovements.filter { it.accountId == accountId }
        ProductDetailUiState(
            account = account,
            cardOverview = if (account != null && profile != null) {
                calculateCreditCardOverview(profile, account.openingBalance, accountPurchases, accountPayments)
            } else {
                null
            },
            purchases = accountPurchases,
            payments = accountPayments,
            savingsProfile = savings,
            savingsProjection = if (account != null && savings != null) {
                calculateSavingsProjection(savings, account.openingBalance, accountSavingsMovements, System.currentTimeMillis())
            } else {
                null
            },
            savingsMovements = accountSavingsMovements,
            transactions = transactions.filter { it.accountId == accountId || it.relatedAccountId == accountId },
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProductDetailUiState(),
    )
}
