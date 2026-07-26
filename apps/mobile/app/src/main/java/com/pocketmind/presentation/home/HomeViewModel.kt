package com.pocketmind.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketmind.shared.domain.model.DashboardSummary
import com.pocketmind.shared.domain.model.CurrencyCode
import com.pocketmind.shared.domain.model.Money
import com.pocketmind.shared.domain.model.TransactionStatus
import com.pocketmind.shared.domain.model.TransactionType
import com.pocketmind.shared.domain.repository.TransactionRepository
import com.pocketmind.shared.domain.usecase.ObserveActiveFinancialAccountsUseCase
import com.pocketmind.shared.domain.usecase.ObserveDashboardSummaryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class HomeViewModel @Inject constructor(
    observeDashboardSummary: ObserveDashboardSummaryUseCase,
    observeAccounts: ObserveActiveFinancialAccountsUseCase,
    transactionRepository: TransactionRepository,
) : ViewModel() {
    val uiState: StateFlow<HomeUiState> = combine(
        observeDashboardSummary(),
        observeAccounts(),
        transactionRepository.observeAll(),
    ) { summary, accounts, transactions ->
        val posted = transactions.filter { it.status == TransactionStatus.POSTED }
        val accountOverviews = accounts.map { account ->
            val accountTransactions = posted.filter { it.accountId == account.id }
            val income = accountTransactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount.minorUnits }
            val expense = accountTransactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount.minorUnits }
            AccountOverview(
                account = account,
                currentBalance = Money(account.openingBalance.minorUnits + income - expense, CurrencyCode.COP),
                income = Money(income, CurrencyCode.COP),
                expense = Money(expense, CurrencyCode.COP),
            )
        }
        HomeUiState.Content(summary, accountOverviews)
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = HomeUiState.Loading,
        )
}
