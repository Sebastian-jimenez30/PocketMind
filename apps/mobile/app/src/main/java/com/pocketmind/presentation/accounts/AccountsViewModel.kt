package com.pocketmind.presentation.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketmind.shared.domain.model.FinancialAccount
import com.pocketmind.shared.domain.model.FinancialAccountType
import com.pocketmind.shared.domain.model.Money
import com.pocketmind.shared.domain.model.TransactionType
import com.pocketmind.shared.domain.model.calculateCreditCardOverview
import com.pocketmind.shared.domain.model.calculateSavingsProjection
import com.pocketmind.shared.domain.model.calculateLoanOverview
import com.pocketmind.shared.domain.usecase.ManualFinanceUseCases
import com.pocketmind.shared.domain.repository.TransactionRepository
import com.pocketmind.shared.domain.usecase.ObserveActiveFinancialAccountsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ProductListItem(
    val account: FinancialAccount,
    val currentAmount: Money,
)

@HiltViewModel
class AccountsViewModel @Inject constructor(
    observeAccounts: ObserveActiveFinancialAccountsUseCase,
    transactionRepository: TransactionRepository,
    manualFinance: ManualFinanceUseCases,
) : ViewModel() {
    val accounts: StateFlow<List<ProductListItem>> = combine(
        observeAccounts(),
        transactionRepository.observeAll(),
        manualFinance.observeCreditCardProfiles(),
        manualFinance.observeInstallmentPurchases(),
        manualFinance.observeCreditCardPayments(),
        manualFinance.observeSavingsProfiles(),
        manualFinance.observeSavingsMovements(),
        manualFinance.observeLoanProfiles(),
        manualFinance.observeLoanPayments(),
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val accounts = values[0] as List<FinancialAccount>
        @Suppress("UNCHECKED_CAST")
        val transactions = values[1] as List<com.pocketmind.shared.domain.model.FinancialTransaction>
        @Suppress("UNCHECKED_CAST")
        val cards = values[2] as List<com.pocketmind.shared.domain.model.CreditCardProfile>
        @Suppress("UNCHECKED_CAST")
        val purchases = values[3] as List<com.pocketmind.shared.domain.model.InstallmentPurchase>
        @Suppress("UNCHECKED_CAST")
        val payments = values[4] as List<com.pocketmind.shared.domain.model.CreditCardPayment>
        @Suppress("UNCHECKED_CAST")
        val savings = values[5] as List<com.pocketmind.shared.domain.model.SavingsProfile>
        @Suppress("UNCHECKED_CAST")
        val savingsMovements = values[6] as List<com.pocketmind.shared.domain.model.SavingsMovement>
        @Suppress("UNCHECKED_CAST")
        val loanProfiles = values[7] as List<com.pocketmind.shared.domain.model.LoanProfile>
        @Suppress("UNCHECKED_CAST")
        val loanPayments = values[8] as List<com.pocketmind.shared.domain.model.LoanPayment>
        accounts.map { account ->
            val amount = when (account.type) {
                FinancialAccountType.CREDIT_CARD -> cards.firstOrNull { it.accountId == account.id }?.let { profile ->
                    calculateCreditCardOverview(profile, account.openingBalance, purchases, payments).currentDebt
                } ?: account.openingBalance
                FinancialAccountType.SAVINGS -> savings.firstOrNull { it.accountId == account.id }?.let { profile ->
                    calculateSavingsProjection(
                        profile,
                        account.openingBalance,
                        savingsMovements,
                        System.currentTimeMillis(),
                    ).currentBalance
                } ?: account.openingBalance
                FinancialAccountType.LOAN -> loanProfiles.firstOrNull { it.accountId == account.id }?.let { profile ->
                    calculateLoanOverview(
                        profile,
                        account.openingBalance,
                        loanPayments,
                        System.currentTimeMillis(),
                    ).currentDebt
                } ?: account.openingBalance
                else -> {
                    val accountTransactions = transactions.filter { it.accountId == account.id }
                    val income = accountTransactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount.minorUnits }
                    val expense = accountTransactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount.minorUnits }
                    val outgoing = accountTransactions.filter { it.type == TransactionType.TRANSFER }.sumOf { it.amount.minorUnits }
                    val incoming = transactions.filter {
                        it.type == TransactionType.TRANSFER && it.relatedAccountId == account.id
                    }.sumOf { it.amount.minorUnits }
                    Money(account.openingBalance.minorUnits + income - expense - outgoing + incoming, account.currency)
                }
            }
            ProductListItem(account, amount)
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )
}
