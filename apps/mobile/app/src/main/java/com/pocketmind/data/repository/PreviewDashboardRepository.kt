package com.pocketmind.data.repository

import com.pocketmind.domain.model.DashboardSummary
import com.pocketmind.domain.repository.DashboardRepository
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
            availableBalanceCop = 2_450_000,
            monthlyIncomeCop = 4_800_000,
            monthlyExpenseCop = 1_920_000,
        ),
    )
}
