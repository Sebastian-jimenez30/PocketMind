package com.pocketmind.shared.domain.repository

import com.pocketmind.shared.domain.model.DashboardSummary
import kotlinx.coroutines.flow.Flow

/** Platform-agnostic source for the financial dashboard aggregate. */
interface DashboardRepository {
    fun observeSummary(): Flow<DashboardSummary>
}
