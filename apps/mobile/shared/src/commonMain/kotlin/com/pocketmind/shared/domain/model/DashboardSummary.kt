package com.pocketmind.shared.domain.model

/** Aggregated financial data displayed by a platform-specific dashboard. */
data class DashboardSummary(
    val availableBalance: Money,
    val monthlyIncome: Money,
    val monthlyExpense: Money,
) {
    init {
        require(availableBalance.currency == monthlyIncome.currency) { "Summary currencies must match." }
        require(availableBalance.currency == monthlyExpense.currency) { "Summary currencies must match." }
    }
}
