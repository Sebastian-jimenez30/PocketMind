package com.pocketmind.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketmind.data.profile.ProfileRepository
import com.pocketmind.data.profile.ProfileSettings
import com.pocketmind.shared.domain.model.DashboardSummary
import com.pocketmind.shared.domain.model.CurrencyCode
import com.pocketmind.shared.domain.model.Money
import com.pocketmind.shared.domain.model.TransactionStatus
import com.pocketmind.shared.domain.model.TransactionType
import com.pocketmind.shared.domain.model.FinancialAccountType
import com.pocketmind.shared.domain.model.calculateCreditCardOverview
import com.pocketmind.shared.domain.model.calculateSavingsProjection
import com.pocketmind.shared.domain.model.calculateLoanOverview
import com.pocketmind.shared.domain.usecase.ManualFinanceUseCases
import com.pocketmind.shared.domain.repository.TransactionRepository
import com.pocketmind.shared.domain.usecase.ObserveActiveFinancialAccountsUseCase
import com.pocketmind.shared.domain.usecase.ObserveBudgetSummariesUseCase
import com.pocketmind.shared.domain.usecase.ObserveCustomCategoriesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class HomeViewModel @Inject constructor(
    observeAccounts: ObserveActiveFinancialAccountsUseCase,
    transactionRepository: TransactionRepository,
    manualFinance: ManualFinanceUseCases,
    private val profileRepository: ProfileRepository,
    observeBudgetSummaries: ObserveBudgetSummariesUseCase,
    observeCustomCategories: ObserveCustomCategoriesUseCase,
) : ViewModel() {
    private val profileName = MutableStateFlow("")

    fun refreshProfile() {
        viewModelScope.launch {
            profileName.value = runCatching {
                profileRepository.load().greetingName()
            }.getOrDefault("")
        }
    }

    val uiState: StateFlow<HomeUiState> = combine(
        observeAccounts(),
        transactionRepository.observeAll(),
        manualFinance.observeCreditCardProfiles(),
        manualFinance.observeInstallmentPurchases(),
        manualFinance.observeCreditCardPayments(),
        manualFinance.observeSavingsProfiles(),
        manualFinance.observeSavingsMovements(),
        manualFinance.observeLoanProfiles(),
        manualFinance.observeLoanPayments(),
        profileName,
        observeBudgetSummaries(),
        observeCustomCategories(),
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val accounts = values[0] as List<com.pocketmind.shared.domain.model.FinancialAccount>
        @Suppress("UNCHECKED_CAST")
        val transactions = values[1] as List<com.pocketmind.shared.domain.model.FinancialTransaction>
        @Suppress("UNCHECKED_CAST")
        val cardProfiles = values[2] as List<com.pocketmind.shared.domain.model.CreditCardProfile>
        @Suppress("UNCHECKED_CAST")
        val purchases = values[3] as List<com.pocketmind.shared.domain.model.InstallmentPurchase>
        @Suppress("UNCHECKED_CAST")
        val payments = values[4] as List<com.pocketmind.shared.domain.model.CreditCardPayment>
        @Suppress("UNCHECKED_CAST")
        val savingsProfiles = values[5] as List<com.pocketmind.shared.domain.model.SavingsProfile>
        @Suppress("UNCHECKED_CAST")
        val savingsMovements = values[6] as List<com.pocketmind.shared.domain.model.SavingsMovement>
        @Suppress("UNCHECKED_CAST")
        val loanProfiles = values[7] as List<com.pocketmind.shared.domain.model.LoanProfile>
        @Suppress("UNCHECKED_CAST")
        val loanPayments = values[8] as List<com.pocketmind.shared.domain.model.LoanPayment>
        val displayName = values[9] as String
        @Suppress("UNCHECKED_CAST")
        val budgets = values[10] as List<com.pocketmind.shared.domain.model.BudgetProgress>
        @Suppress("UNCHECKED_CAST")
        val customCategories = values[11] as List<com.pocketmind.shared.domain.model.CustomCategory>
        val posted = transactions.filter { it.status == TransactionStatus.POSTED }
        val accountOverviews = accounts.map { account ->
            val accountTransactions = posted.filter { it.accountId == account.id }
            val income = accountTransactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount.minorUnits }
            val expense = accountTransactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount.minorUnits }
            val outgoingTransfers = accountTransactions.filter { it.type == TransactionType.TRANSFER }.sumOf { it.amount.minorUnits }
            val incomingTransfers = posted.filter {
                it.type == TransactionType.TRANSFER && it.relatedAccountId == account.id
            }.sumOf { it.amount.minorUnits }
            val cardOverview = cardProfiles.firstOrNull { it.accountId == account.id }?.let { profile ->
                calculateCreditCardOverview(profile, account.openingBalance, purchases, payments)
            }
            val savingsProjection = savingsProfiles.firstOrNull { it.accountId == account.id }?.let { profile ->
                calculateSavingsProjection(profile, account.openingBalance, savingsMovements, System.currentTimeMillis())
            }
            val loanOverview = loanProfiles.firstOrNull { it.accountId == account.id }?.let { profile ->
                calculateLoanOverview(profile, account.openingBalance, loanPayments, System.currentTimeMillis())
            }
            val current = when (account.type) {
                FinancialAccountType.CREDIT_CARD -> cardOverview?.currentDebt ?: account.openingBalance
                FinancialAccountType.SAVINGS -> savingsProjection?.currentBalance
                    ?: Money(account.openingBalance.minorUnits + income - expense - outgoingTransfers + incomingTransfers, CurrencyCode.COP)
                FinancialAccountType.LOAN -> loanOverview?.currentDebt ?: account.openingBalance
                else -> Money(account.openingBalance.minorUnits + income - expense - outgoingTransfers + incomingTransfers, CurrencyCode.COP)
            }
            AccountOverview(
                account = account,
                currentBalance = current,
                income = Money(income, CurrencyCode.COP),
                expense = Money(expense, CurrencyCode.COP),
                isLiability = account.type == FinancialAccountType.CREDIT_CARD || account.type == FinancialAccountType.LOAN,
            )
        }
        val assets = accountOverviews.filterNot { it.isLiability }.sumOf { it.currentBalance.minorUnits }
        val liabilities = accountOverviews.filter { it.isLiability }.sumOf { it.currentBalance.minorUnits }
        val monthStart = java.time.LocalDate.now().withDayOfMonth(1)
            .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val monthTransactions = posted.filter { it.occurredAtEpochMillis >= monthStart }
        val transferCategories = setOf(TransactionCategoryId.DEBT_PAYMENT.name, TransactionCategoryId.SAVINGS.name)
        val monthlyIncome = monthTransactions
            .filter { it.type == TransactionType.INCOME && it.categoryId !in transferCategories }
            .sumOf { it.amount.minorUnits }
        val monthlyExpense = monthTransactions
            .filter { it.type == TransactionType.EXPENSE && it.categoryId !in transferCategories }
            .sumOf { it.amount.minorUnits }
        val summary = DashboardSummary(
            availableBalance = Money(assets - liabilities, CurrencyCode.COP),
            monthlyIncome = Money(monthlyIncome, CurrencyCode.COP),
            monthlyExpense = Money(monthlyExpense, CurrencyCode.COP),
        )
        HomeUiState.Content(
            summary = summary,
            accounts = accountOverviews,
            budgets = budgets,
            customCategories = customCategories,
            recentTransactions = posted.take(3),
            displayName = displayName,
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = HomeUiState.Loading,
        )
}

private fun ProfileSettings.greetingName(): String {
    val candidate = displayName.trim().ifBlank {
        email.substringBefore("@")
            .replace(".", " ")
            .replace("_", " ")
            .replace("-", " ")
            .trim()
    }
    return candidate.split(Regex("\\s+"))
        .filter(String::isNotBlank)
        .joinToString(" ") { word ->
            word.lowercase().replaceFirstChar(Char::titlecase)
        }
}
