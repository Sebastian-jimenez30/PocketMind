package com.pocketmind.shared.domain.usecase

import com.pocketmind.shared.domain.model.DashboardSummary
import com.pocketmind.shared.domain.repository.DashboardRepository
import kotlinx.coroutines.flow.Flow

/** Exposes dashboard data without coupling UI code to a data source. */
class ObserveDashboardSummaryUseCase(
    private val dashboardRepository: DashboardRepository,
) {
    operator fun invoke(): Flow<DashboardSummary> = dashboardRepository.observeSummary()
}
