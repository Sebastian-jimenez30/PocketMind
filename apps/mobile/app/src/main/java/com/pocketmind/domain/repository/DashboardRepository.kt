package com.pocketmind.domain.repository

import com.pocketmind.domain.model.DashboardSummary
import kotlinx.coroutines.flow.Flow

/** Source of the aggregated financial information shown on the home dashboard. */
interface DashboardRepository {
    fun observeSummary(): Flow<DashboardSummary>
}
