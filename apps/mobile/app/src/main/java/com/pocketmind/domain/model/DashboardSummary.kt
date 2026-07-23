package com.pocketmind.domain.model

/** Read model displayed by the financial dashboard. Amounts are represented in COP. */
data class DashboardSummary(
    val availableBalanceCop: Long,
    val monthlyIncomeCop: Long,
    val monthlyExpenseCop: Long,
)
