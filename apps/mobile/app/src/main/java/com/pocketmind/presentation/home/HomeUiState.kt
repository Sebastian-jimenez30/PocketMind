package com.pocketmind.presentation.home

import com.pocketmind.shared.domain.model.DashboardSummary
import com.pocketmind.shared.domain.model.FinancialAccount
import com.pocketmind.shared.domain.model.Money

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Content(
        val summary: DashboardSummary,
        val accounts: List<AccountOverview> = emptyList(),
    ) : HomeUiState
}

data class AccountOverview(
    val account: FinancialAccount,
    val currentBalance: Money,
    val income: Money,
    val expense: Money,
)
