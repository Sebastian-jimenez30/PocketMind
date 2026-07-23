package com.pocketmind.data.repository

import com.pocketmind.shared.domain.model.CurrencyCode
import com.pocketmind.shared.domain.model.DashboardSummary
import com.pocketmind.shared.domain.model.Money
import com.pocketmind.shared.domain.repository.DashboardRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Temporary local data source used while Room and Supabase are not connected.
 * It is deliberately isolated behind [DashboardRepository] so it can be replaced safely.
 */
class PreviewDashboardRepository @Inject constructor() : DashboardRepository {
    override fun observeSummary(): Flow<DashboardSummary> = flowOf(
        DashboardSummary(
            availableBalance = Money(2_450_000, CurrencyCode.COP),
            monthlyIncome = Money(4_800_000, CurrencyCode.COP),
            monthlyExpense = Money(1_920_000, CurrencyCode.COP),
        ),
    )
}
